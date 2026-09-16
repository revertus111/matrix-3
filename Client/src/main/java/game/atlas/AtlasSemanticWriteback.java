package game.atlas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import game.atlas.AtlasEvidenceStore.BatchResult;
import game.atlas.AtlasEvidenceStore.EvidenceView;
import game.atlas.AtlasSchema.EvidenceRecord;

/**
 * Bounded file handoff for assistant-driven semantic mapping bundles.
 *
 * A writeback file is deliberately separate from generated Atlas structure.
 * The first line is a fingerprinted batch header and the remaining lines use
 * the existing deterministic curated-evidence JSON shape. Applying a file is
 * all-or-nothing through AtlasEvidenceStore.upsertBatch(...).
 */
public final class AtlasSemanticWriteback {

    public static final int FORMAT_VERSION = 1;
    public static final String WRITEBACK_RECORD_TYPE = "atlas-semantic-writeback";
    public static final String SNAPSHOT_RECORD_TYPE = "atlas-semantic-snapshot";
    public static final String DEFAULT_WRITEBACK_FILE = "semantic-writeback.jsonl";
    public static final String DEFAULT_SNAPSHOT_FILE = "semantic-snapshot.jsonl";

    private static final long MAX_WRITEBACK_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_LINE_CHARS = 65536;

    private final AtlasWorkspace workspace;
    private final AtlasInvestigationIndex index;
    private final AtlasEvidenceStore store;

    public AtlasSemanticWriteback(AtlasWorkspace workspace, AtlasInvestigationIndex index) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        this.workspace = workspace;
        this.index = index;
        this.store = new AtlasEvidenceStore(workspace);
    }

    public static void main(String[] args) {
        try {
            Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
            AtlasWorkspace workspace = new AtlasWorkspace(clientRoot);
            AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, workspace.defaultClassRoot());
            AtlasSemanticWriteback tool = new AtlasSemanticWriteback(workspace, index);

            if (args.length > 0 && "snapshot".equalsIgnoreCase(args[0])) {
                Path output = args.length > 1
                        ? Paths.get(args[1]).toAbsolutePath().normalize()
                        : tool.defaultSnapshotFile();
                SnapshotResult result = tool.writeSnapshot(output);
                System.out.println("Client Atlas semantic snapshot written: " + result.getPath());
                System.out.println("Records: " + result.getRecordCount());
                System.out.println("Current: " + result.getCurrentCount());
                System.out.println("Stale: " + result.getStaleCount());
                System.out.println("Orphan: " + result.getOrphanCount());
                return;
            }

            Path input = args.length > 0
                    ? Paths.get(args[0]).toAbsolutePath().normalize()
                    : tool.defaultWritebackFile();
            ApplyResult result = tool.apply(input);
            System.out.println("Client Atlas semantic writeback applied.");
            System.out.println("Bundle: " + result.getBundleId());
            System.out.println("Batch records: " + result.getBatchResult().getBatchSize());
            System.out.println("Inserted: " + result.getBatchResult().getInsertedCount());
            System.out.println("Updated: " + result.getBatchResult().getUpdatedCount());
            System.out.println("Total evidence: " + result.getBatchResult().getTotalRecordCount());
            System.out.println("Snapshot: " + result.getSnapshotPath());
        } catch (Exception ex) {
            System.err.println("Client Atlas semantic writeback failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public Path defaultWritebackFile() {
        return workspace.getWorkspaceRoot().resolve(DEFAULT_WRITEBACK_FILE);
    }

    public Path defaultSnapshotFile() {
        return workspace.getWorkspaceRoot().resolve(DEFAULT_SNAPSHOT_FILE);
    }

    /** Apply one fully validated, exact-ID semantic batch. */
    public ApplyResult apply(Path input) throws IOException {
        ParsedWriteback parsed = readWriteback(input);
        BatchResult batch = store.upsertBatch(index, parsed.clientFingerprint, parsed.records);
        SnapshotResult snapshot = writeSnapshot(defaultSnapshotFile());
        return new ApplyResult(parsed.bundleId, input.toAbsolutePath().normalize(), batch, snapshot.getPath());
    }

    /**
     * Export only curated semantic knowledge. Generated symbols/relationships
     * are intentionally absent so the snapshot remains a semantic handoff, not
     * a second structural authority.
     */
    public SnapshotResult writeSnapshot(Path output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output cannot be null");
        }
        List<EvidenceView> views = store.inspect(index);
        int current = 0;
        int stale = 0;
        int orphan = 0;
        for (EvidenceView view : views) {
            if (view.isCurrent()) {
                current++;
            }
            if (view.isStaleFingerprint()) {
                stale++;
            }
            if (!view.isSubjectPresent()) {
                orphan++;
            }
        }

        List<String> lines = new ArrayList<String>(views.size() + 1);
        lines.add(snapshotHeader(index.getMetadata().getClientFingerprint(), views.size(), current, stale, orphan));
        for (EvidenceView view : views) {
            lines.add(AtlasJson.evidence(view.getRecord()));
        }
        atomicWrite(output, lines);
        return new SnapshotResult(output.toAbsolutePath().normalize(), views.size(), current, stale, orphan);
    }

    /**
     * Deterministic helper used by verifiers and future assistant handoffs.
     * Subject existence/evidence-strength validation still occurs only when the
     * file is applied against a current Atlas index.
     */
    public static Path writeWriteback(Path output, String clientFingerprint, String bundleId,
            List<EvidenceRecord> records) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output cannot be null");
        }
        String fingerprint = requireText(clientFingerprint, "clientFingerprint");
        String bundle = requireText(bundleId, "bundleId");
        if (records == null || records.isEmpty()) {
            throw new IOException("Client Atlas semantic writeback contains no evidence records");
        }
        if (records.size() > AtlasEvidenceStore.MAX_BATCH_RECORDS) {
            throw new IOException("Client Atlas semantic writeback exceeds "
                    + AtlasEvidenceStore.MAX_BATCH_RECORDS + " records");
        }

        Set<String> subjects = new LinkedHashSet<String>();
        List<String> lines = new ArrayList<String>(records.size() + 1);
        lines.add(writebackHeader(fingerprint, bundle, records.size()));
        for (EvidenceRecord record : records) {
            if (record == null) {
                throw new IOException("Client Atlas semantic writeback contains a null record");
            }
            try {
                AtlasEvidenceStore.validateRecord(record);
            } catch (RuntimeException ex) {
                throw new IOException("Invalid Client Atlas semantic writeback record: " + ex.getMessage(), ex);
            }
            if (!fingerprint.equals(record.getClientFingerprint())) {
                throw new IOException("Semantic writeback record fingerprint mismatch for " + record.getSubjectId());
            }
            if (!subjects.add(record.getSubjectId())) {
                throw new IOException("Duplicate semantic writeback subjectId: " + record.getSubjectId());
            }
            lines.add(AtlasJson.evidence(record));
        }
        atomicWrite(output, lines);
        return output.toAbsolutePath().normalize();
    }

    private ParsedWriteback readWriteback(Path input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null");
        }
        Path normalized = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("Client Atlas semantic writeback does not exist: " + normalized);
        }
        if (Files.size(normalized) > MAX_WRITEBACK_BYTES) {
            throw new IOException("Client Atlas semantic writeback exceeds " + MAX_WRITEBACK_BYTES + " bytes: "
                    + normalized);
        }

        try (BufferedReader reader = Files.newBufferedReader(normalized, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().length() == 0) {
                throw new IOException("Client Atlas semantic writeback is empty: " + normalized);
            }
            if (headerLine.length() > MAX_LINE_CHARS) {
                throw new IOException("Client Atlas semantic writeback header is too long");
            }

            Header header;
            try {
                header = parseHeader(headerLine);
            } catch (RuntimeException ex) {
                throw new IOException("Malformed Client Atlas semantic writeback header: " + ex.getMessage(), ex);
            }
            if (!WRITEBACK_RECORD_TYPE.equals(header.recordType)) {
                throw new IOException("Not a Client Atlas semantic writeback file: " + header.recordType);
            }
            if (header.formatVersion != FORMAT_VERSION) {
                throw new IOException("Unsupported Client Atlas semantic writeback format version: "
                        + header.formatVersion);
            }
            if (header.recordCount <= 0 || header.recordCount > AtlasEvidenceStore.MAX_BATCH_RECORDS) {
                throw new IOException("Invalid Client Atlas semantic writeback record count: " + header.recordCount);
            }
            String currentFingerprint = index.getMetadata().getClientFingerprint();
            if (!currentFingerprint.equals(header.clientFingerprint)) {
                throw new IOException("Client Atlas semantic writeback fingerprint is stale: "
                        + header.clientFingerprint + " != " + currentFingerprint);
            }

            List<EvidenceRecord> records = new ArrayList<EvidenceRecord>(header.recordCount);
            Set<String> subjects = new LinkedHashSet<String>();
            String line;
            long lineNumber = 1L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().length() == 0) {
                    continue;
                }
                if (line.length() > MAX_LINE_CHARS) {
                    throw new IOException("Semantic writeback line exceeds " + MAX_LINE_CHARS
                            + " characters at line " + lineNumber);
                }
                if (records.size() >= header.recordCount) {
                    throw new IOException("Semantic writeback contains more records than its header declares");
                }
                EvidenceRecord record;
                try {
                    record = AtlasEvidenceStore.parseRecord(line);
                    AtlasEvidenceStore.validateRecord(record);
                } catch (RuntimeException ex) {
                    throw new IOException("Malformed semantic writeback evidence at line " + lineNumber
                            + ": " + ex.getMessage(), ex);
                }
                if (!header.clientFingerprint.equals(record.getClientFingerprint())) {
                    throw new IOException("Semantic writeback evidence fingerprint mismatch at line " + lineNumber);
                }
                if (!subjects.add(record.getSubjectId())) {
                    throw new IOException("Duplicate semantic writeback subjectId at line " + lineNumber
                            + ": " + record.getSubjectId());
                }
                records.add(record);
            }
            if (records.size() != header.recordCount) {
                throw new IOException("Semantic writeback header declares " + header.recordCount
                        + " records but file contains " + records.size());
            }
            return new ParsedWriteback(header.clientFingerprint, header.bundleId, records);
        }
    }

    private static String writebackHeader(String fingerprint, String bundleId, int recordCount) {
        return "{\"recordType\":" + AtlasJson.quote(WRITEBACK_RECORD_TYPE)
                + ",\"formatVersion\":" + FORMAT_VERSION
                + ",\"clientFingerprint\":" + AtlasJson.quote(fingerprint)
                + ",\"bundleId\":" + AtlasJson.quote(bundleId)
                + ",\"recordCount\":" + recordCount + "}";
    }

    private static String snapshotHeader(String fingerprint, int recordCount, int currentCount,
            int staleCount, int orphanCount) {
        return "{\"recordType\":" + AtlasJson.quote(SNAPSHOT_RECORD_TYPE)
                + ",\"formatVersion\":" + FORMAT_VERSION
                + ",\"clientFingerprint\":" + AtlasJson.quote(fingerprint)
                + ",\"recordCount\":" + recordCount
                + ",\"currentCount\":" + currentCount
                + ",\"staleCount\":" + staleCount
                + ",\"orphanCount\":" + orphanCount + "}";
    }

    private static Header parseHeader(String json) {
        return new Header(
                requiredString(json, "recordType"),
                requiredInt(json, "formatVersion"),
                requiredString(json, "clientFingerprint"),
                requiredString(json, "bundleId"),
                requiredInt(json, "recordCount"));
    }

    private static String requiredString(String json, String field) {
        int start = valueStart(json, field);
        if (start >= json.length() || json.charAt(start) != '"') {
            throw new IllegalArgumentException("field " + field + " is not a JSON string");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return builder.toString();
            }
            if (c != '\\') {
                builder.append(c);
                continue;
            }
            if (++i >= json.length()) {
                throw new IllegalArgumentException("unterminated escape in field " + field);
            }
            char escaped = json.charAt(i);
            switch (escaped) {
            case '"': builder.append('"'); break;
            case '\\': builder.append('\\'); break;
            case '/': builder.append('/'); break;
            case 'b': builder.append('\b'); break;
            case 'f': builder.append('\f'); break;
            case 'n': builder.append('\n'); break;
            case 'r': builder.append('\r'); break;
            case 't': builder.append('\t'); break;
            case 'u':
                if (i + 4 >= json.length()) {
                    throw new IllegalArgumentException("short unicode escape in field " + field);
                }
                int code = 0;
                for (int j = 1; j <= 4; j++) {
                    int digit = Character.digit(json.charAt(i + j), 16);
                    if (digit < 0) {
                        throw new IllegalArgumentException("invalid unicode escape in field " + field);
                    }
                    code = (code << 4) | digit;
                }
                builder.append((char) code);
                i += 4;
                break;
            default:
                throw new IllegalArgumentException("invalid escape in field " + field);
            }
        }
        throw new IllegalArgumentException("unterminated JSON string field " + field);
    }

    private static int requiredInt(String json, String field) {
        int start = valueStart(json, field);
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            throw new IllegalArgumentException("field " + field + " is not a non-negative integer");
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid integer field " + field, ex);
        }
    }

    private static int valueStart(String json, String field) {
        String token = "\"" + field + "\"";
        int fieldStart = json.indexOf(token);
        if (fieldStart < 0) {
            throw new IllegalArgumentException("missing field " + field);
        }
        int colon = skipWhitespace(json, fieldStart + token.length());
        if (colon >= json.length() || json.charAt(colon) != ':') {
            throw new IllegalArgumentException("missing ':' after field " + field);
        }
        return skipWhitespace(json, colon + 1);
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value.trim();
    }

    private static void atomicWrite(Path output, List<String> lines) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = normalized.resolveSibling(normalized.getFileName().toString() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        try {
            Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class Header {
        private final String recordType;
        private final int formatVersion;
        private final String clientFingerprint;
        private final String bundleId;
        private final int recordCount;

        private Header(String recordType, int formatVersion, String clientFingerprint,
                String bundleId, int recordCount) {
            this.recordType = recordType;
            this.formatVersion = formatVersion;
            this.clientFingerprint = clientFingerprint;
            this.bundleId = bundleId;
            this.recordCount = recordCount;
        }
    }

    private static final class ParsedWriteback {
        private final String clientFingerprint;
        private final String bundleId;
        private final List<EvidenceRecord> records;

        private ParsedWriteback(String clientFingerprint, String bundleId, List<EvidenceRecord> records) {
            this.clientFingerprint = clientFingerprint;
            this.bundleId = bundleId;
            this.records = records;
        }
    }

    public static final class ApplyResult {
        private final String bundleId;
        private final Path inputPath;
        private final BatchResult batchResult;
        private final Path snapshotPath;

        private ApplyResult(String bundleId, Path inputPath, BatchResult batchResult, Path snapshotPath) {
            this.bundleId = bundleId;
            this.inputPath = inputPath;
            this.batchResult = batchResult;
            this.snapshotPath = snapshotPath;
        }

        public String getBundleId() {
            return bundleId;
        }

        public Path getInputPath() {
            return inputPath;
        }

        public BatchResult getBatchResult() {
            return batchResult;
        }

        public Path getSnapshotPath() {
            return snapshotPath;
        }
    }

    public static final class SnapshotResult {
        private final Path path;
        private final int recordCount;
        private final int currentCount;
        private final int staleCount;
        private final int orphanCount;

        private SnapshotResult(Path path, int recordCount, int currentCount, int staleCount, int orphanCount) {
            this.path = path;
            this.recordCount = recordCount;
            this.currentCount = currentCount;
            this.staleCount = staleCount;
            this.orphanCount = orphanCount;
        }

        public Path getPath() {
            return path;
        }

        public int getRecordCount() {
            return recordCount;
        }

        public int getCurrentCount() {
            return currentCount;
        }

        public int getStaleCount() {
            return staleCount;
        }

        public int getOrphanCount() {
            return orphanCount;
        }
    }
}
