package game.atlas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import game.atlas.AtlasInvestigationIndex.RelationshipEntry;
import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasRelationshipQueryEngine.RelationshipQueryResult;
import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSchema.SymbolKind;
import game.atlas.AtlasSearchEngine.SearchMatch;
import game.atlas.AtlasSearchEngine.SearchResult;

/**
 * Compact machine-readable investigation package for future assistant/code use.
 *
 * The engine consumes the already-current in-memory Atlas index. It does not
 * rescan classes, create another persistence owner, infer semantics, or rename
 * obfuscated symbols.
 */
public final class AtlasAssistantExportEngine {

    public static final int FORMAT_VERSION = 1;
    public static final int MAX_CANDIDATES = 50;
    public static final int MAX_RELATIONSHIPS = 200;
    public static final int MAX_SYMBOLS = 250;

    private final AtlasInvestigationIndex index;
    private final AtlasSearchEngine search;
    private final AtlasRelationshipQueryEngine relationships;

    public AtlasAssistantExportEngine(AtlasInvestigationIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        this.index = index;
        this.search = new AtlasSearchEngine(index);
        this.relationships = new AtlasRelationshipQueryEngine(index);
    }

    public ExportResult build(String request) {
        String value = requireRequest(request);
        if (relationships.isRelationshipCommand(value)) {
            RelationshipQueryResult relationshipResult = relationships.query(value);
            return buildResult(value, "relationship-query", relationshipResult.getResolution(),
                    relationshipResult, relationshipResult.getResolvedTarget(),
                    relationshipResult.getResolvedSymbol());
        }

        SearchResult resolution = search.search(value);
        RelationshipQueryResult context = null;
        String resolvedTarget = null;
        SymbolEntry resolvedSymbol = resolution.getResolvedSymbol();
        if (resolvedSymbol != null) {
            resolvedTarget = resolvedSymbol.getId();
            context = relationships.query("neighbors " + resolvedTarget + " depth=1");
        }
        return buildResult(value, "symbol-search", resolution, context,
                resolvedTarget, resolvedSymbol);
    }

    public Path writeExport(ExportResult result, Path outputFile) throws IOException {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile cannot be null");
        }

        Path normalized = outputFile.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = normalized.resolveSibling(normalized.getFileName().toString() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(result.toJson());
            writer.newLine();
        }

        try {
            Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
        return normalized;
    }

    private ExportResult buildResult(String request, String requestType,
            SearchResult resolution, RelationshipQueryResult relationshipResult,
            String resolvedTarget, SymbolEntry resolvedSymbol) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        long candidateCount = 0L;
        boolean candidatesTruncated = false;
        if (resolution != null) {
            candidateCount = resolution.getTotalMatches();
            int shown = Math.min(MAX_CANDIDATES, resolution.getMatches().size());
            for (int i = 0; i < shown; i++) {
                SearchMatch match = resolution.getMatches().get(i);
                candidates.add(new Candidate(match.getSymbol().getId(),
                        match.getScore(), match.getReason()));
            }
            candidatesTruncated = resolution.isTruncated() || candidateCount > candidates.size();
        }

        List<RelationshipEntry> queryRelationships = relationshipResult == null
                ? Collections.<RelationshipEntry>emptyList()
                : relationshipResult.getRelationships();
        int relationshipLimit = Math.min(MAX_RELATIONSHIPS, queryRelationships.size());
        List<RelationshipEntry> exportedRelationships = new ArrayList<RelationshipEntry>(relationshipLimit);
        for (int i = 0; i < relationshipLimit; i++) {
            exportedRelationships.add(queryRelationships.get(i));
        }

        long totalRelationships = relationshipResult == null ? 0L : relationshipResult.getTotalMatches();
        boolean queryRelationshipsTruncated = relationshipResult != null && relationshipResult.isTruncated();
        boolean relationshipsTruncated = queryRelationshipsTruncated
                || queryRelationships.size() > exportedRelationships.size()
                || totalRelationships > exportedRelationships.size();

        Map<String, SymbolEntry> relevant = new LinkedHashMap<String, SymbolEntry>();
        addRelevant(relevant, resolvedSymbol);
        if (resolution != null) {
            for (SearchMatch match : resolution.getMatches()) {
                addRelevant(relevant, match.getSymbol());
            }
        }
        for (RelationshipEntry relationship : exportedRelationships) {
            addRelevant(relevant, index.getSymbol(relationship.getFromId()));
            addRelevant(relevant, index.getSymbol(relationship.getTarget()));
        }

        long relevantSymbolCount = relevant.size();
        List<SymbolEntry> exportedSymbols = new ArrayList<SymbolEntry>();
        for (SymbolEntry symbol : relevant.values()) {
            if (exportedSymbols.size() >= MAX_SYMBOLS) {
                break;
            }
            exportedSymbols.add(symbol);
        }

        String error = relationshipValidationError(relationshipResult);
        boolean resolved = error == null && (resolution != null
                ? resolution.isResolved()
                : relationshipResult != null
                        && !relationshipResult.needsExactSelection()
                        && resolvedTarget != null);

        return new ExportResult(index.getMetadata(), index.getLoadNanos(),
                index.getSymbolCount(), index.getRelationshipCount(), request, requestType,
                resolved, resolvedTarget, error, candidates, candidateCount, candidatesTruncated,
                relationshipResult == null ? null : relationshipResult.getCommand(),
                relationshipResult == null ? null : relationshipResult.getOperand(),
                relationshipResult == null ? 0 : relationshipResult.getDepth(),
                relationshipResult == null ? 0 : relationshipResult.getNodeCount(),
                totalRelationships, queryRelationships.size(), queryRelationshipsTruncated,
                exportedRelationships, relationshipsTruncated, exportedSymbols,
                relevantSymbolCount, relevantSymbolCount > exportedSymbols.size());
    }

    private static String relationshipValidationError(RelationshipQueryResult result) {
        if (result == null || result.needsExactSelection()) {
            return null;
        }
        SymbolEntry symbol = result.getResolvedSymbol();
        if (symbol == null) {
            return null;
        }
        if ("written-by".equals(result.getCommand()) && symbol.getKind() != SymbolKind.FIELD) {
            return "written-by expects a FIELD; resolved operand was "
                    + symbol.getKind().name() + ": " + symbol.getId();
        }
        if ("references".equals(result.getCommand()) && !isClassLike(symbol.getKind())) {
            return "references expects a class/interface/enum/annotation or TYPE:<internal-name>; resolved operand was "
                    + symbol.getKind().name() + ": " + symbol.getId();
        }
        return null;
    }

    private static boolean isClassLike(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.ANNOTATION;
    }

    private static void addRelevant(Map<String, SymbolEntry> relevant, SymbolEntry symbol) {
        if (symbol != null) {
            relevant.put(symbol.getId(), symbol);
        }
    }

    private static String requireRequest(String request) {
        if (request == null || request.trim().length() == 0) {
            throw new IllegalArgumentException("assistant export request cannot be empty");
        }
        return request.trim();
    }

    private static void appendCandidate(StringBuilder out, Candidate candidate) {
        out.append('{');
        out.append("\"symbolId\":").append(AtlasJson.quote(candidate.symbolId));
        out.append(",\"score\":").append(candidate.score);
        out.append(",\"reason\":").append(AtlasJson.quote(candidate.reason));
        out.append('}');
    }

    private static void appendSymbol(StringBuilder out, SymbolEntry symbol) {
        out.append('{');
        out.append("\"id\":").append(AtlasJson.quote(symbol.getId()));
        out.append(",\"kind\":").append(AtlasJson.quote(symbol.getKind().name()));
        out.append(",\"owner\":").append(AtlasJson.quote(symbol.getOwner()));
        out.append(",\"name\":").append(AtlasJson.quote(symbol.getName()));
        out.append(",\"descriptor\":").append(AtlasJson.quote(symbol.getDescriptor()));
        out.append(",\"signature\":").append(AtlasJson.quote(symbol.getSignature()));
        out.append(",\"compiledPath\":").append(AtlasJson.quote(symbol.getCompiledPath()));
        out.append(",\"sourcePath\":").append(AtlasJson.quote(symbol.getSourcePath()));
        out.append(",\"access\":").append(symbol.getAccess());
        out.append('}');
    }

    private static void appendRelationship(StringBuilder out, RelationshipEntry relationship) {
        out.append('{');
        out.append("\"fromId\":").append(AtlasJson.quote(relationship.getFromId()));
        out.append(",\"type\":").append(AtlasJson.quote(relationship.getType().name()));
        out.append(",\"target\":").append(AtlasJson.quote(relationship.getTarget()));
        out.append(",\"sourcePath\":").append(AtlasJson.quote(relationship.getSourcePath()));
        out.append(",\"sourceLine\":");
        if (relationship.hasSourceLine()) {
            out.append(relationship.getSourceLine());
        } else {
            out.append("null");
        }
        out.append(",\"opcode\":");
        if (relationship.hasOpcode()) {
            out.append(relationship.getOpcode());
        } else {
            out.append("null");
        }
        out.append(",\"occurrenceCount\":").append(relationship.getOccurrenceCount());
        out.append(",\"detail\":").append(AtlasJson.quote(relationship.getDetail()));
        out.append('}');
    }

    private static final class Candidate {
        private final String symbolId;
        private final int score;
        private final String reason;

        private Candidate(String symbolId, int score, String reason) {
            this.symbolId = symbolId;
            this.score = score;
            this.reason = reason;
        }
    }

    public static final class ExportResult {
        private final Metadata metadata;
        private final long indexLoadNanos;
        private final long indexSymbolCount;
        private final long indexRelationshipCount;
        private final String request;
        private final String requestType;
        private final boolean resolved;
        private final String resolvedTarget;
        private final String error;
        private final List<Candidate> candidates;
        private final long candidateCount;
        private final boolean candidatesTruncated;
        private final String relationshipCommand;
        private final String relationshipOperand;
        private final int depth;
        private final int nodeCount;
        private final long totalRelationshipMatches;
        private final int queryRelationshipCount;
        private final boolean queryRelationshipsTruncated;
        private final List<RelationshipEntry> relationships;
        private final boolean relationshipsTruncated;
        private final List<SymbolEntry> symbols;
        private final long relevantSymbolCount;
        private final boolean symbolsTruncated;

        private ExportResult(Metadata metadata, long indexLoadNanos,
                long indexSymbolCount, long indexRelationshipCount,
                String request, String requestType, boolean resolved, String resolvedTarget,
                String error, List<Candidate> candidates, long candidateCount, boolean candidatesTruncated,
                String relationshipCommand, String relationshipOperand, int depth, int nodeCount,
                long totalRelationshipMatches, int queryRelationshipCount,
                boolean queryRelationshipsTruncated, List<RelationshipEntry> relationships,
                boolean relationshipsTruncated, List<SymbolEntry> symbols,
                long relevantSymbolCount, boolean symbolsTruncated) {
            this.metadata = metadata;
            this.indexLoadNanos = indexLoadNanos;
            this.indexSymbolCount = indexSymbolCount;
            this.indexRelationshipCount = indexRelationshipCount;
            this.request = request;
            this.requestType = requestType;
            this.resolved = resolved;
            this.resolvedTarget = resolvedTarget;
            this.error = error;
            this.candidates = Collections.unmodifiableList(new ArrayList<Candidate>(candidates));
            this.candidateCount = candidateCount;
            this.candidatesTruncated = candidatesTruncated;
            this.relationshipCommand = relationshipCommand;
            this.relationshipOperand = relationshipOperand;
            this.depth = depth;
            this.nodeCount = nodeCount;
            this.totalRelationshipMatches = totalRelationshipMatches;
            this.queryRelationshipCount = queryRelationshipCount;
            this.queryRelationshipsTruncated = queryRelationshipsTruncated;
            this.relationships = Collections.unmodifiableList(new ArrayList<RelationshipEntry>(relationships));
            this.relationshipsTruncated = relationshipsTruncated;
            this.symbols = Collections.unmodifiableList(new ArrayList<SymbolEntry>(symbols));
            this.relevantSymbolCount = relevantSymbolCount;
            this.symbolsTruncated = symbolsTruncated;
        }

        public String getRequest() { return request; }
        public String getRequestType() { return requestType; }
        public boolean isResolved() { return resolved; }
        public String getResolvedTarget() { return resolvedTarget; }
        public String getError() { return error; }
        public long getCandidateCount() { return candidateCount; }
        public boolean isCandidatesTruncated() { return candidatesTruncated; }
        public long getTotalRelationshipMatches() { return totalRelationshipMatches; }
        public int getQueryRelationshipCount() { return queryRelationshipCount; }
        public boolean isQueryRelationshipsTruncated() { return queryRelationshipsTruncated; }
        public List<RelationshipEntry> getRelationships() { return relationships; }
        public boolean isRelationshipsTruncated() { return relationshipsTruncated; }
        public List<SymbolEntry> getSymbols() { return symbols; }
        public long getRelevantSymbolCount() { return relevantSymbolCount; }
        public boolean isSymbolsTruncated() { return symbolsTruncated; }

        public String toJson() {
            StringBuilder out = new StringBuilder(8192);
            out.append('{');
            out.append("\"assistantExportVersion\":").append(FORMAT_VERSION);
            out.append(",\"schemaVersion\":").append(metadata.getSchemaVersion());
            out.append(",\"clientFingerprint\":").append(AtlasJson.quote(metadata.getClientFingerprint()));
            out.append(",\"generatedAtUtc\":").append(AtlasJson.quote(metadata.getGeneratedAtUtc()));
            out.append(",\"scanRoot\":").append(AtlasJson.quote(metadata.getScanRoot()));
            out.append(",\"indexCurrent\":true");
            out.append(",\"indexLoadNanos\":").append(indexLoadNanos);
            out.append(",\"indexSymbolCount\":").append(indexSymbolCount);
            out.append(",\"indexRelationshipCount\":").append(indexRelationshipCount);
            out.append(",\"request\":").append(AtlasJson.quote(request));
            out.append(",\"requestType\":").append(AtlasJson.quote(requestType));
            out.append(",\"resolved\":").append(resolved);
            out.append(",\"resolvedTarget\":").append(AtlasJson.quote(resolvedTarget));
            out.append(",\"error\":").append(AtlasJson.quote(error));
            out.append(",\"candidateCount\":").append(candidateCount);
            out.append(",\"candidatesTruncated\":").append(candidatesTruncated);
            out.append(",\"candidates\":[");
            for (int i = 0; i < candidates.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendCandidate(out, candidates.get(i));
            }
            out.append(']');
            out.append(",\"relationshipCommand\":").append(AtlasJson.quote(relationshipCommand));
            out.append(",\"relationshipOperand\":").append(AtlasJson.quote(relationshipOperand));
            out.append(",\"depth\":").append(depth);
            out.append(",\"nodeCount\":").append(nodeCount);
            out.append(",\"relationshipCount\":").append(totalRelationshipMatches);
            out.append(",\"queryRelationshipCount\":").append(queryRelationshipCount);
            out.append(",\"queryRelationshipsTruncated\":").append(queryRelationshipsTruncated);
            out.append(",\"exportedRelationshipCount\":").append(relationships.size());
            out.append(",\"relationshipsTruncated\":").append(relationshipsTruncated);
            out.append(",\"relevantSymbolCount\":").append(relevantSymbolCount);
            out.append(",\"exportedSymbolCount\":").append(symbols.size());
            out.append(",\"symbolsTruncated\":").append(symbolsTruncated);
            out.append(",\"caps\":{\"candidates\":").append(MAX_CANDIDATES)
                    .append(",\"relationships\":").append(MAX_RELATIONSHIPS)
                    .append(",\"symbols\":").append(MAX_SYMBOLS).append('}');
            out.append(",\"symbols\":[");
            for (int i = 0; i < symbols.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendSymbol(out, symbols.get(i));
            }
            out.append(']');
            out.append(",\"relationships\":[");
            for (int i = 0; i < relationships.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendRelationship(out, relationships.get(i));
            }
            out.append("]}");
            return out.toString();
        }
    }
}
