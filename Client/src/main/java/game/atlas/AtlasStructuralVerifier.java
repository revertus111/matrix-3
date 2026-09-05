package game.atlas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import game.atlas.AtlasQueryEngine.QueryResult;
import game.atlas.AtlasScanner.ScanResult;
import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSchema.RelationshipType;

/**
 * Consolidated local verification/measurement pass for Phase 2 Bundle 2A.
 * This intentionally consumes the same generated Atlas files and query engine
 * rather than adding a second scanner or persistence path.
 */
public final class AtlasStructuralVerifier {

    private static final long PHASE1_RELATIONSHIP_BASELINE = 34053L;
    private static final String REPORT_FILE = "phase2-structural-check.txt";
    private static final String EXPORT_FILE = "phase2-structural-query.json";

    private final AtlasWorkspace workspace;
    private final Path classRoot;

    public AtlasStructuralVerifier(AtlasWorkspace workspace, Path classRoot) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        if (classRoot == null) {
            throw new IllegalArgumentException("classRoot cannot be null");
        }
        this.workspace = workspace;
        this.classRoot = classRoot.toAbsolutePath().normalize();
    }

    public VerificationResult run() throws IOException {
        if (!Files.isDirectory(classRoot)) {
            throw new IOException("Compiled client class directory does not exist: " + classRoot);
        }

        workspace.ensureLayout();
        PreScanState preScan = capturePreScanState();

        long scanStart = System.nanoTime();
        ScanResult scan = new AtlasScanner(workspace).scan(classRoot);
        long scanNanos = System.nanoTime() - scanStart;

        require(scan.getClassFileCount() > 0, "scan produced zero class files");
        require(scan.getSymbolCount() > 0L, "scan produced zero symbols");
        require(scan.getRelationshipCount() > PHASE1_RELATIONSHIP_BASELINE,
                "schema-v2 relationship count did not exceed the Phase 1 structural-only baseline of "
                        + PHASE1_RELATIONSHIP_BASELINE);

        Metadata metadata = workspace.readMetadata();
        require(metadata.getSchemaVersion() == AtlasWorkspace.SCHEMA_VERSION,
                "metadata schema mismatch after rebuild");
        require(workspace.isCurrent(classRoot), "rebuilt index is not current");

        GeneratedCoverage coverage = inspectGeneratedData();
        verifyCoverage(coverage);
        verifyPreservedData(preScan);

        AtlasQueryEngine queryEngine = new AtlasQueryEngine(workspace);
        String queryId = coverage.class1Present ? "CLASS:game/Class1" : coverage.firstClassId;
        require(queryId != null, "unable to select a class for exact-query regression");

        long queryStart = System.nanoTime();
        QueryResult query = queryEngine.queryExact(queryId, classRoot);
        long queryNanos = System.nanoTime() - queryStart;
        require(query.toJson().contains("\"indexCurrent\":true"),
                "exact query did not report a current index");

        if (coverage.internalCallTarget != null) {
            QueryResult incoming = queryEngine.queryExact(coverage.internalCallTarget, classRoot);
            String incomingJson = incoming.toJson();
            require(incomingJson.contains("\"type\":\"CALLS\"")
                            && incomingJson.contains("\"target\":" + AtlasJson.quote(coverage.internalCallTarget)),
                    "exact query did not expose an incoming CALLS relationship for "
                            + coverage.internalCallTarget);
        }

        Path exportPath = workspace.getWorkspaceRoot().resolve(EXPORT_FILE);
        queryEngine.writeExport(query, exportPath);
        require(Files.isRegularFile(exportPath) && Files.size(exportPath) > 0L,
                "Phase 2 exact-query export was not written");

        long symbolBytes = Files.size(workspace.symbolsFile());
        long relationshipBytes = Files.size(workspace.relationshipsFile());
        String report = buildReport(preScan, scan, metadata, coverage, scanNanos, queryId,
                queryNanos, symbolBytes, relationshipBytes, exportPath);
        Path reportPath = workspace.getWorkspaceRoot().resolve(REPORT_FILE);
        writeReport(reportPath, report);

        return new VerificationResult(metadata, report, reportPath);
    }

    private PreScanState capturePreScanState() throws IOException {
        Integer previousSchema = null;
        boolean previousCurrent = false;
        if (Files.isRegularFile(workspace.metadataFile())) {
            Metadata previous = workspace.readMetadata();
            previousSchema = Integer.valueOf(previous.getSchemaVersion());
            previousCurrent = workspace.isCurrent(classRoot);
        }
        long evidenceBytes = Files.isRegularFile(workspace.evidenceFile())
                ? Files.size(workspace.evidenceFile()) : 0L;
        DirectoryMetrics traces = measureDirectory(workspace.tracesDirectory());
        return new PreScanState(previousSchema, previousCurrent, evidenceBytes, traces);
    }

    private void verifyPreservedData(PreScanState before) throws IOException {
        long evidenceBytesAfter = Files.isRegularFile(workspace.evidenceFile())
                ? Files.size(workspace.evidenceFile()) : 0L;
        DirectoryMetrics tracesAfter = measureDirectory(workspace.tracesDirectory());
        require(before.evidenceBytes == evidenceBytesAfter,
                "evidence.jsonl changed during generated-index rebuild");
        require(before.traces.fileCount == tracesAfter.fileCount
                        && before.traces.totalBytes == tracesAfter.totalBytes,
                "trace files changed during generated-index rebuild");
    }

    private GeneratedCoverage inspectGeneratedData() throws IOException {
        GeneratedCoverage coverage = new GeneratedCoverage();
        Set<String> symbolIds = new HashSet<String>();

        try (BufferedReader reader = Files.newBufferedReader(workspace.symbolsFile(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String id = extractJsonString(line, "id");
                if (id != null) {
                    symbolIds.add(id);
                }
                if (coverage.firstClassId == null && line.contains("\"kind\":\"CLASS\"")) {
                    coverage.firstClassId = id;
                }
                if (line.contains("\"id\":\"CLASS:game/Class1\"")) {
                    coverage.class1Present = true;
                    coverage.class1LocationValid = line.contains("\"compiledPath\":\"game/Class1.class\"")
                            && line.contains("\"sourcePath\":\"src/main/java/game/Class1.java\"");
                }
                coverage.hasCompiledPathField |= line.contains("\"compiledPath\":");
                coverage.hasSourcePathField |= line.contains("\"sourcePath\":");
            }
        }

        Map<String, Integer> internalCallCounts = new HashMap<String, Integer>();
        Map<String, String> firstCallFrom = new HashMap<String, String>();
        try (BufferedReader reader = Files.newBufferedReader(workspace.relationshipsFile(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                coverage.relationshipRecordCount++;
                RelationshipType type = relationshipType(line);
                if (type != null) {
                    Long count = coverage.typeCounts.get(type);
                    coverage.typeCounts.put(type, Long.valueOf(count == null ? 1L : count.longValue() + 1L));
                }

                coverage.hasRelationshipShape |= line.contains("\"sourcePath\":")
                        && line.contains("\"sourceLine\":")
                        && line.contains("\"opcode\":")
                        && line.contains("\"occurrenceCount\":")
                        && line.contains("\"detail\":");

                if ((type == RelationshipType.DECLARES || type == RelationshipType.EXTENDS
                        || type == RelationshipType.IMPLEMENTS)
                        && line.contains("\"occurrenceCount\":1")
                        && line.contains("\"sourceLine\":null")
                        && line.contains("\"opcode\":null")) {
                    coverage.hasStructuralShape = true;
                }

                Integer sourceLine = extractJsonInteger(line, "sourceLine");
                if (sourceLine != null && sourceLine.intValue() > 0) {
                    coverage.positiveSourceLineCount++;
                }
                Integer opcode = extractJsonInteger(line, "opcode");
                if (opcode != null) {
                    coverage.opcodeEvidenceCount++;
                }
                Integer occurrences = extractJsonInteger(line, "occurrenceCount");
                if (occurrences != null && occurrences.intValue() > 1) {
                    coverage.aggregatedRelationshipCount++;
                }

                if (type == RelationshipType.LITERAL_ID) {
                    coverage.literalIdCount++;
                }

                if (type == RelationshipType.CALLS) {
                    String target = extractJsonString(line, "target");
                    if (target != null && symbolIds.contains(target)) {
                        Integer count = internalCallCounts.get(target);
                        internalCallCounts.put(target, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                        if (!firstCallFrom.containsKey(target)) {
                            firstCallFrom.put(target, extractJsonString(line, "fromId"));
                        }
                    }
                }

                recordSample(coverage, type, line);
            }
        }

        for (Map.Entry<String, Integer> entry : internalCallCounts.entrySet()) {
            if (entry.getValue().intValue() == 1) {
                coverage.internalCallTarget = entry.getKey();
                coverage.internalCallFrom = firstCallFrom.get(entry.getKey());
                break;
            }
        }
        if (coverage.internalCallTarget == null && !internalCallCounts.isEmpty()) {
            Map.Entry<String, Integer> first = internalCallCounts.entrySet().iterator().next();
            coverage.internalCallTarget = first.getKey();
            coverage.internalCallFrom = firstCallFrom.get(first.getKey());
        }

        return coverage;
    }

    private static void recordSample(GeneratedCoverage coverage, RelationshipType type, String line) {
        if (type == null) {
            return;
        }
        String fromId = extractJsonString(line, "fromId");
        String target = extractJsonString(line, "target");
        String sample = fromId + " -> " + target;
        if (type == RelationshipType.CALLS && coverage.sampleCall == null) {
            coverage.sampleCall = sample;
        } else if (type == RelationshipType.READS_FIELD && coverage.sampleRead == null) {
            coverage.sampleRead = sample;
        } else if (type == RelationshipType.WRITES_FIELD && coverage.sampleWrite == null) {
            coverage.sampleWrite = sample;
        } else if (type == RelationshipType.REFERENCES_TYPE && coverage.sampleType == null) {
            coverage.sampleType = sample;
        } else if (type == RelationshipType.CONSTANT && coverage.sampleConstant == null) {
            coverage.sampleConstant = sample;
        }
    }

    private static void verifyCoverage(GeneratedCoverage coverage) throws IOException {
        require(coverage.hasCompiledPathField && coverage.hasSourcePathField,
                "symbol records are missing compiledPath/sourcePath fields");
        require(!coverage.class1Present || coverage.class1LocationValid,
                "Class1 compiledPath/sourcePath did not resolve as expected");
        require(coverage.hasRelationshipShape,
                "relationship records are missing schema-v2 source/line/opcode/occurrence/detail fields");
        require(coverage.hasStructuralShape,
                "structural declaration relationships do not have occurrenceCount=1 and null line/opcode");
        require(count(coverage, RelationshipType.CALLS) > 0L, "no CALLS relationships were generated");
        require(count(coverage, RelationshipType.READS_FIELD) > 0L,
                "no READS_FIELD relationships were generated");
        require(count(coverage, RelationshipType.WRITES_FIELD) > 0L,
                "no WRITES_FIELD relationships were generated");
        require(count(coverage, RelationshipType.REFERENCES_TYPE) > 0L,
                "no REFERENCES_TYPE relationships were generated");
        require(count(coverage, RelationshipType.CONSTANT) > 0L,
                "no CONSTANT relationships were generated");
        require(coverage.literalIdCount == 0L,
                "LITERAL_ID relationships were generated without evidence-backed domain correlation");
        require(coverage.sampleConstant != null && coverage.sampleConstant.contains(" -> "),
                "no typed constant sample was available");
        require(coverage.internalCallTarget != null,
                "no internal CALLS target was available for incoming-query regression");
    }

    private String buildReport(PreScanState preScan, ScanResult scan, Metadata metadata,
            GeneratedCoverage coverage, long scanNanos, String queryId, long queryNanos,
            long symbolBytes, long relationshipBytes, Path exportPath) {
        StringBuilder report = new StringBuilder(4096);
        report.append("Client Atlas - Phase 2 structural verification\n\n");
        report.append("PHASE 2 STRUCTURAL CHECK: PASS\n\n");
        report.append("Pre-scan index: ");
        if (preScan.previousSchema == null) {
            report.append("none\n");
        } else {
            report.append("schema ").append(preScan.previousSchema.intValue())
                    .append(preScan.previousCurrent ? " / current" : " / rebuild required").append('\n');
        }
        report.append("Schema version: ").append(metadata.getSchemaVersion()).append('\n');
        report.append("Client fingerprint: ").append(metadata.getClientFingerprint()).append('\n');
        report.append("Class files: ").append(scan.getClassFileCount()).append('\n');
        report.append("Symbols: ").append(scan.getSymbolCount()).append('\n');
        report.append("Relationships: ").append(scan.getRelationshipCount()).append('\n');
        report.append("Scan time: ").append(formatMillis(scanNanos)).append(" ms\n");
        report.append("Exact query: ").append(queryId).append('\n');
        report.append("Query time: ").append(formatMillis(queryNanos)).append(" ms\n");
        report.append("symbols.jsonl: ").append(symbolBytes).append(" bytes (")
                .append(formatBytes(symbolBytes)).append(")\n");
        report.append("relationships.jsonl: ").append(relationshipBytes).append(" bytes (")
                .append(formatBytes(relationshipBytes)).append(")\n\n");

        report.append("Relationship counts\n");
        for (RelationshipType type : RelationshipType.values()) {
            report.append("  ").append(type.name()).append(": ").append(count(coverage, type)).append('\n');
        }
        report.append("  records with sourceLine: ").append(coverage.positiveSourceLineCount).append('\n');
        report.append("  records with opcode: ").append(coverage.opcodeEvidenceCount).append('\n');
        report.append("  aggregated occurrenceCount > 1: ")
                .append(coverage.aggregatedRelationshipCount).append('\n');
        report.append("  LITERAL_ID auto-classifications: ").append(coverage.literalIdCount).append('\n\n');

        report.append("Samples\n");
        appendSample(report, "CALLS", coverage.sampleCall);
        appendSample(report, "READS_FIELD", coverage.sampleRead);
        appendSample(report, "WRITES_FIELD", coverage.sampleWrite);
        appendSample(report, "REFERENCES_TYPE", coverage.sampleType);
        appendSample(report, "CONSTANT", coverage.sampleConstant);
        if (count(coverage, RelationshipType.DYNAMIC_CALL) == 0L) {
            report.append("  DYNAMIC_CALL: none encountered (not a failure)\n");
        }
        if (coverage.positiveSourceLineCount == 0L) {
            report.append("  NOTE: no positive source lines were present in compiled debug data.\n");
        }
        if (coverage.aggregatedRelationshipCount == 0L) {
            report.append("  NOTE: no naturally repeated same-method relationship occurred in this scan.\n");
        }
        report.append('\n');
        report.append("PASS  schema v2/current fingerprint\n");
        report.append("PASS  compiledPath/sourcePath symbol shape\n");
        report.append("PASS  schema-v2 relationship shape\n");
        report.append("PASS  CALLS + incoming exact-query path\n");
        report.append("PASS  READS_FIELD / WRITES_FIELD\n");
        report.append("PASS  REFERENCES_TYPE\n");
        report.append("PASS  typed CONSTANT relationships\n");
        report.append("PASS  no automatic LITERAL_ID promotion\n");
        report.append("PASS  evidence/traces preserved across rebuild\n");
        report.append("PASS  exact query/export regression\n");
        report.append("Export: ").append(exportPath).append('\n');
        return report.toString();
    }

    private static void appendSample(StringBuilder report, String name, String sample) {
        report.append("  ").append(name).append(": ")
                .append(sample == null ? "none" : sample).append('\n');
    }

    private static long count(GeneratedCoverage coverage, RelationshipType type) {
        Long value = coverage.typeCounts.get(type);
        return value == null ? 0L : value.longValue();
    }

    private static RelationshipType relationshipType(String line) {
        String value = extractJsonString(line, "type");
        if (value == null) {
            return null;
        }
        try {
            return RelationshipType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String extractJsonString(String line, String field) {
        String token = "\"" + field + "\":\"";
        int start = line.indexOf(token);
        if (start < 0) {
            return null;
        }
        start += token.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                value.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private static Integer extractJsonInteger(String line, String field) {
        String token = "\"" + field + "\":";
        int start = line.indexOf(token);
        if (start < 0) {
            return null;
        }
        start += token.length();
        if (line.startsWith("null", start)) {
            return null;
        }
        int end = start;
        if (end < line.length() && line.charAt(end) == '-') {
            end++;
        }
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        try {
            return Integer.valueOf(line.substring(start, end));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static DirectoryMetrics measureDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return new DirectoryMetrics(0L, 0L);
        }
        long count = 0L;
        long bytes = 0L;
        try (Stream<Path> stream = Files.walk(directory)) {
            java.util.Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isRegularFile(path)) {
                    count++;
                    bytes += Files.size(path);
                }
            }
        }
        return new DirectoryMetrics(count, bytes);
    }

    private static void writeReport(Path path, String report) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writer.write(report);
        }
    }

    private static String formatMillis(long nanos) {
        long whole = nanos / 1000000L;
        long tenths = (nanos % 1000000L) / 100000L;
        return whole + "." + tenths;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KiB";
        }
        long tenths = (bytes * 10L) / (1024L * 1024L);
        return (tenths / 10L) + "." + (tenths % 10L) + " MiB";
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private static final class PreScanState {
        private final Integer previousSchema;
        private final boolean previousCurrent;
        private final long evidenceBytes;
        private final DirectoryMetrics traces;

        private PreScanState(Integer previousSchema, boolean previousCurrent, long evidenceBytes,
                DirectoryMetrics traces) {
            this.previousSchema = previousSchema;
            this.previousCurrent = previousCurrent;
            this.evidenceBytes = evidenceBytes;
            this.traces = traces;
        }
    }

    private static final class DirectoryMetrics {
        private final long fileCount;
        private final long totalBytes;

        private DirectoryMetrics(long fileCount, long totalBytes) {
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
        }
    }

    private static final class GeneratedCoverage {
        private final EnumMap<RelationshipType, Long> typeCounts =
                new EnumMap<RelationshipType, Long>(RelationshipType.class);
        private String firstClassId;
        private boolean class1Present;
        private boolean class1LocationValid;
        private boolean hasCompiledPathField;
        private boolean hasSourcePathField;
        private boolean hasRelationshipShape;
        private boolean hasStructuralShape;
        private long relationshipRecordCount;
        private long positiveSourceLineCount;
        private long opcodeEvidenceCount;
        private long aggregatedRelationshipCount;
        private long literalIdCount;
        private String sampleCall;
        private String sampleRead;
        private String sampleWrite;
        private String sampleType;
        private String sampleConstant;
        private String internalCallTarget;
        private String internalCallFrom;
    }

    public static final class VerificationResult {
        private final Metadata metadata;
        private final String report;
        private final Path reportPath;

        private VerificationResult(Metadata metadata, String report, Path reportPath) {
            this.metadata = metadata;
            this.report = report;
            this.reportPath = reportPath;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public String getReport() {
            return report;
        }

        public Path getReportPath() {
            return reportPath;
        }
    }
}
