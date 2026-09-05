package game.atlas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

import game.atlas.AtlasInvestigationIndex.RelationshipEntry;
import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasRelationshipQueryEngine.RelationshipQueryResult;
import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSchema.RelationshipType;
import game.atlas.AtlasSchema.SymbolKind;
import game.atlas.AtlasSearchEngine.SearchResult;

/**
 * Consolidated local verification for Phase 2 Bundle 2B investigation/search.
 * It consumes the current generated index without rescanning compiled classes.
 */
public final class AtlasInvestigationVerifier {

    private static final String REPORT_FILE = "phase2-investigation-check.txt";

    private final AtlasWorkspace workspace;
    private final Path classRoot;

    public AtlasInvestigationVerifier(AtlasWorkspace workspace, Path classRoot) {
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
        Metadata metadata = workspace.readMetadata();
        require(metadata.getSchemaVersion() == AtlasWorkspace.SCHEMA_VERSION,
                "Atlas schema is not current");
        require(workspace.isCurrent(classRoot),
                "Atlas index is stale; rebuild before running the investigation check");

        long memoryBefore = usedMemory();
        AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
        long memoryAfter = usedMemory();
        require(index.getSymbolCount() == metadata.getSymbolCount(), "investigation symbol count mismatch");
        require(index.getRelationshipCount() == metadata.getRelationshipCount(),
                "investigation relationship count mismatch");

        AtlasSearchEngine search = new AtlasSearchEngine(index);
        AtlasRelationshipQueryEngine relationships = new AtlasRelationshipQueryEngine(index);

        SymbolEntry class1 = index.getSymbol("CLASS:game/Class1");
        require(class1 != null, "known Class1 symbol is missing from the current index");

        SearchResult canonical = search.search(class1.getId());
        require(canonical.isResolved() && class1.getId().equals(canonical.getResolvedSymbol().getId()),
                "exact canonical Class1 search did not resolve correctly");

        SearchResult friendly = search.search("Class1");
        require(friendly.isResolved() && class1.getId().equals(friendly.getResolvedSymbol().getId()),
                "friendly Class1 search did not resolve to the canonical class");

        SymbolEntry declaredMethod = firstDeclaredMethod(index, class1.getId());
        require(declaredMethod != null, "Class1 has no declared method available for shorthand verification");
        String memberQuery = "Class1." + declaredMethod.getName() + declaredMethod.getDescriptor();
        SearchResult member = search.search(memberQuery);
        require(member.isResolved() && declaredMethod.getId().equals(member.getResolvedSymbol().getId()),
                "owner/member shorthand did not resolve the exact known member: " + memberQuery);

        SearchResult ambiguous = findAmbiguousSearch(index, search);
        require(ambiguous != null && !ambiguous.isResolved() && ambiguous.getTotalMatches() > 1L,
                "unable to verify ambiguity-safe candidate handling");

        SearchResult fuzzy = search.search("Clas");
        require(fuzzy.getTotalMatches() > 0L && !fuzzy.isResolved(),
                "fuzzy/prefix search unexpectedly auto-resolved or returned no candidates");
        require(fuzzy.getMatches().size() <= AtlasSearchEngine.DEFAULT_LIMIT,
                "friendly search exceeded its candidate cap");

        Samples samples = findRelationshipSamples(index);
        require(samples.call != null && index.getSymbol(samples.call.getTarget()) != null,
                "no bounded internal CALLS sample was available");
        require(samples.read != null && index.getSymbol(samples.read.getTarget()) != null,
                "no bounded internal READS_FIELD sample was available");
        require(samples.write != null && index.getSymbol(samples.write.getTarget()) != null,
                "no bounded internal WRITES_FIELD sample was available");
        require(samples.type != null, "no bounded REFERENCES_TYPE sample was available");
        require(samples.constant != null, "no bounded CONSTANT sample was available");

        RelationshipQueryResult calls = relationships.query("calls " + samples.call.getFromId());
        require(contains(calls.getRelationships(), samples.call), "calls query missed its known edge");

        RelationshipQueryResult calledBy = relationships.query("called-by " + samples.call.getTarget());
        require(contains(calledBy.getRelationships(), samples.call), "called-by query missed its known edge");

        RelationshipQueryResult reads = relationships.query("reads " + samples.read.getTarget());
        require(contains(reads.getRelationships(), samples.read), "reads field query missed its known edge");

        RelationshipQueryResult writtenBy = relationships.query("written-by " + samples.write.getTarget());
        require(contains(writtenBy.getRelationships(), samples.write),
                "written-by field query missed its known edge");

        RelationshipQueryResult references = relationships.query("references " + samples.type.getTarget());
        require(contains(references.getRelationships(), samples.type),
                "references query missed its known type edge");

        RelationshipQueryResult constant = relationships.query("constant " + samples.constant.getTarget());
        require(contains(constant.getRelationships(), samples.constant),
                "constant query missed its known typed literal edge");

        RelationshipQueryResult neighbors = relationships.query(
                "neighbors " + samples.call.getFromId() + " depth=2");
        require(neighbors.getDepth() == 2, "neighborhood depth was not preserved");
        require(neighbors.getNodeCount() <= AtlasRelationshipQueryEngine.MAX_NODES,
                "neighborhood exceeded node cap");
        require(neighbors.getRelationships().size() <= AtlasRelationshipQueryEngine.MAX_EDGES,
                "neighborhood exceeded edge cap");

        String report = buildReport(index, memoryBefore, memoryAfter, canonical, friendly,
                memberQuery, member, ambiguous, fuzzy, samples, calls, calledBy,
                reads, writtenBy, references, constant, neighbors);
        Path reportPath = workspace.getWorkspaceRoot().resolve(REPORT_FILE);
        writeReport(reportPath, report);
        return new VerificationResult(metadata, report, reportPath);
    }

    private static SymbolEntry firstDeclaredMethod(AtlasInvestigationIndex index, String classId) {
        for (RelationshipEntry entry : index.outgoing(classId)) {
            if (entry.getType() != RelationshipType.DECLARES) {
                continue;
            }
            SymbolEntry symbol = index.getSymbol(entry.getTarget());
            if (symbol != null && symbol.getKind() == SymbolKind.METHOD) {
                return symbol;
            }
        }
        return null;
    }

    private static SearchResult findAmbiguousSearch(AtlasInvestigationIndex index, AtlasSearchEngine search) {
        for (SymbolEntry symbol : index.getSymbols()) {
            String name = symbol.getName();
            if (name == null || name.length() == 0) {
                continue;
            }
            List<SymbolEntry> candidates = index.findByName(name);
            if (candidates.size() > 1) {
                SearchResult result = search.search(name);
                if (result.getTotalMatches() > 1L && !result.isResolved()) {
                    return result;
                }
            }
        }
        return null;
    }

    private static Samples findRelationshipSamples(AtlasInvestigationIndex index) {
        Samples samples = new Samples();
        for (SymbolEntry symbol : index.getSymbols()) {
            for (RelationshipEntry edge : index.outgoing(symbol.getId())) {
                if (edge.getType() == RelationshipType.CALLS && samples.call == null
                        && index.getSymbol(edge.getTarget()) != null
                        && typeCountWithinCap(index.outgoing(edge.getFromId()), RelationshipType.CALLS)
                        && typeCountWithinCap(index.incoming(edge.getTarget()), RelationshipType.CALLS)) {
                    samples.call = edge;
                } else if (edge.getType() == RelationshipType.READS_FIELD && samples.read == null
                        && index.getSymbol(edge.getTarget()) != null
                        && typeCountWithinCap(index.incoming(edge.getTarget()), RelationshipType.READS_FIELD)) {
                    samples.read = edge;
                } else if (edge.getType() == RelationshipType.WRITES_FIELD && samples.write == null
                        && index.getSymbol(edge.getTarget()) != null
                        && typeCountWithinCap(index.incoming(edge.getTarget()), RelationshipType.WRITES_FIELD)) {
                    samples.write = edge;
                } else if (edge.getType() == RelationshipType.REFERENCES_TYPE && samples.type == null
                        && typeCountWithinCap(index.incoming(edge.getTarget()), RelationshipType.REFERENCES_TYPE)) {
                    samples.type = edge;
                } else if (edge.getType() == RelationshipType.CONSTANT && samples.constant == null
                        && index.constantReferrers(edge.getTarget()).size() <= AtlasRelationshipQueryEngine.MAX_EDGES) {
                    samples.constant = edge;
                }
            }
            if (samples.complete()) {
                break;
            }
        }
        return samples;
    }

    private static boolean typeCountWithinCap(List<RelationshipEntry> entries, RelationshipType type) {
        int count = 0;
        for (RelationshipEntry entry : entries) {
            if (entry.getType() == type) {
                count++;
                if (count > AtlasRelationshipQueryEngine.MAX_EDGES) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean contains(List<RelationshipEntry> relationships, RelationshipEntry expected) {
        String expectedKey = edgeKey(expected);
        for (RelationshipEntry relationship : relationships) {
            if (expectedKey.equals(edgeKey(relationship))) {
                return true;
            }
        }
        return false;
    }

    private static String edgeKey(RelationshipEntry edge) {
        return edge.getFromId() + '\u0000' + edge.getType().name() + '\u0000' + edge.getTarget();
    }

    private static String buildReport(AtlasInvestigationIndex index, long memoryBefore, long memoryAfter,
            SearchResult canonical, SearchResult friendly, String memberQuery, SearchResult member,
            SearchResult ambiguous, SearchResult fuzzy, Samples samples,
            RelationshipQueryResult calls, RelationshipQueryResult calledBy,
            RelationshipQueryResult reads, RelationshipQueryResult writtenBy,
            RelationshipQueryResult references, RelationshipQueryResult constant,
            RelationshipQueryResult neighbors) {
        StringBuilder report = new StringBuilder(4096);
        report.append("Client Atlas - Phase 2 investigation/search verification\n\n");
        report.append("PHASE 2 INVESTIGATION CHECK: PASS\n\n");
        report.append("Symbols: ").append(index.getSymbolCount()).append('\n');
        report.append("Relationships: ").append(index.getRelationshipCount()).append('\n');
        report.append("Index load time: ").append(formatMillis(index.getLoadNanos())).append(" ms\n");
        report.append("Approx used-memory delta: ").append(formatBytes(memoryAfter - memoryBefore)).append("\n\n");

        report.append("Search checks\n");
        report.append("  exact Class1: ").append(canonical.getResolvedSymbol().getId()).append('\n');
        report.append("  friendly Class1: ").append(friendly.getResolvedSymbol().getId()).append('\n');
        report.append("  member shorthand: ").append(memberQuery).append(" -> ")
                .append(member.getResolvedSymbol().getId()).append('\n');
        report.append("  ambiguous sample: ").append(ambiguous.getQuery()).append(" -> ")
                .append(ambiguous.getTotalMatches()).append(" candidates\n");
        report.append("  fuzzy `Clas`: ").append(fuzzy.getTotalMatches()).append(" matches, showing ")
                .append(fuzzy.getMatches().size()).append('\n');
        report.append("  exact search time: ").append(formatMillis(canonical.getSearchNanos())).append(" ms\n");
        report.append("  friendly search time: ").append(formatMillis(friendly.getSearchNanos())).append(" ms\n\n");

        report.append("Relationship checks\n");
        appendSample(report, "CALLS", samples.call, calls);
        appendSample(report, "CALLED_BY", samples.call, calledBy);
        appendSample(report, "READS_FIELD", samples.read, reads);
        appendSample(report, "WRITES_FIELD", samples.write, writtenBy);
        appendSample(report, "REFERENCES_TYPE", samples.type, references);
        appendSample(report, "CONSTANT", samples.constant, constant);
        report.append("  neighborhood depth: ").append(neighbors.getDepth()).append('\n');
        report.append("  neighborhood nodes: ").append(neighbors.getNodeCount()).append(" / cap ")
                .append(AtlasRelationshipQueryEngine.MAX_NODES).append('\n');
        report.append("  neighborhood relationships shown: ").append(neighbors.getRelationships().size())
                .append(" / cap ").append(AtlasRelationshipQueryEngine.MAX_EDGES)
                .append(neighbors.isTruncated() ? " (truncated)" : "").append("\n\n");

        report.append("PASS  current schema/fingerprint without rescan\n");
        report.append("PASS  in-memory index counts match metadata\n");
        report.append("PASS  canonical + friendly + owner/member search\n");
        report.append("PASS  ambiguity is surfaced instead of guessed\n");
        report.append("PASS  fuzzy results stay bounded/non-resolved\n");
        report.append("PASS  calls / called-by / reads / written-by\n");
        report.append("PASS  type references + typed constants\n");
        report.append("PASS  depth-2 neighborhood respects node/edge caps\n");
        return report.toString();
    }

    private static void appendSample(StringBuilder report, String label,
            RelationshipEntry sample, RelationshipQueryResult result) {
        report.append("  ").append(label).append(": ").append(sample.getFromId())
                .append(" -> ").append(sample.getTarget())
                .append(" | matches ").append(result.getTotalMatches())
                .append(result.isTruncated() ? " (truncated)" : "").append('\n');
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", Double.valueOf(nanos / 1000000.0D));
    }

    private static String formatBytes(long bytes) {
        boolean negative = bytes < 0L;
        double value = Math.abs((double) bytes);
        String suffix = "B";
        if (value >= 1024.0D) {
            value /= 1024.0D;
            suffix = "KiB";
        }
        if (value >= 1024.0D) {
            value /= 1024.0D;
            suffix = "MiB";
        }
        return (negative ? "-" : "") + String.format(Locale.ROOT, "%.1f", Double.valueOf(value)) + " " + suffix;
    }

    private static void writeReport(Path path, String report) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(report);
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private static final class Samples {
        private RelationshipEntry call;
        private RelationshipEntry read;
        private RelationshipEntry write;
        private RelationshipEntry type;
        private RelationshipEntry constant;

        private boolean complete() {
            return call != null && read != null && write != null && type != null && constant != null;
        }
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

        public Metadata getMetadata() { return metadata; }
        public String getReport() { return report; }
        public Path getReportPath() { return reportPath; }
    }
}
