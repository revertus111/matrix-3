package game.atlas;

import java.io.BufferedReader;
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
import java.util.List;

import game.atlas.AtlasSchema.Metadata;

/**
 * Exact Phase 1 lookup/export over generated Atlas JSONL.
 * Ranked/fuzzy search and deeper graph traversal belong to Phase 2.
 */
public final class AtlasQueryEngine {

    private static final int MAX_RELATIONSHIPS = 200;

    private final AtlasWorkspace workspace;

    public AtlasQueryEngine(AtlasWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        this.workspace = workspace;
    }

    public QueryResult queryExact(String symbolId, Path classRoot) throws IOException {
        String normalizedId = requireSymbolId(symbolId);
        Metadata metadata = workspace.readMetadata();
        if (metadata.getSchemaVersion() != AtlasWorkspace.SCHEMA_VERSION) {
            throw new IOException("Unsupported Client Atlas schema version: " + metadata.getSchemaVersion()
                    + " (expected " + AtlasWorkspace.SCHEMA_VERSION + ")");
        }
        if (!workspace.isCurrent(classRoot)) {
            throw new IOException("Client Atlas index is stale for the compiled client. Run `scan` before querying.");
        }

        String symbolJson = findExactSymbol(normalizedId);
        if (symbolJson == null) {
            throw new IOException("Client Atlas symbol not found: " + normalizedId);
        }

        RelationshipSelection relationships = findImmediateRelationships(normalizedId);
        return new QueryResult(metadata, normalizedId, symbolJson,
                relationships.records, relationships.totalCount);
    }

    public Path writeExport(QueryResult result, Path outputFile) throws IOException {
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
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writer.write(result.toJson());
            writer.newLine();
        }

        try {
            Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
        return normalized;
    }

    private String findExactSymbol(String symbolId) throws IOException {
        Path symbolsPath = workspace.symbolsFile();
        requireGeneratedFile(symbolsPath);
        String token = "\"id\":" + AtlasJson.quote(symbolId);

        try (BufferedReader reader = Files.newBufferedReader(symbolsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(token)) {
                    return line;
                }
            }
        }
        return null;
    }

    private RelationshipSelection findImmediateRelationships(String symbolId) throws IOException {
        Path relationshipsPath = workspace.relationshipsFile();
        requireGeneratedFile(relationshipsPath);

        String quotedId = AtlasJson.quote(symbolId);
        String fromToken = "\"fromId\":" + quotedId;
        String targetToken = "\"target\":" + quotedId;

        List<String> records = new ArrayList<String>();
        long total = 0L;
        try (BufferedReader reader = Files.newBufferedReader(relationshipsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(fromToken) || line.contains(targetToken)) {
                    total++;
                    if (records.size() < MAX_RELATIONSHIPS) {
                        records.add(line);
                    }
                }
            }
        }
        return new RelationshipSelection(records, total);
    }

    private static void requireGeneratedFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Client Atlas generated file does not exist: " + path + ". Run `scan` first.");
        }
    }

    private static String requireSymbolId(String symbolId) {
        if (symbolId == null || symbolId.trim().length() == 0) {
            throw new IllegalArgumentException("symbolId cannot be empty");
        }
        return symbolId.trim();
    }

    private static final class RelationshipSelection {
        private final List<String> records;
        private final long totalCount;

        private RelationshipSelection(List<String> records, long totalCount) {
            this.records = records;
            this.totalCount = totalCount;
        }
    }

    public static final class QueryResult {
        private final Metadata metadata;
        private final String queryId;
        private final String symbolJson;
        private final List<String> relationships;
        private final long relationshipCount;

        private QueryResult(Metadata metadata, String queryId, String symbolJson,
                List<String> relationships, long relationshipCount) {
            this.metadata = metadata;
            this.queryId = queryId;
            this.symbolJson = symbolJson;
            this.relationships = Collections.unmodifiableList(new ArrayList<String>(relationships));
            this.relationshipCount = relationshipCount;
        }

        public long getRelationshipCount() {
            return relationshipCount;
        }

        public boolean isRelationshipsTruncated() {
            return relationshipCount > relationships.size();
        }

        public String toJson() {
            StringBuilder builder = new StringBuilder(1024);
            builder.append('{');
            builder.append("\"schemaVersion\":").append(metadata.getSchemaVersion());
            builder.append(",\"clientFingerprint\":").append(AtlasJson.quote(metadata.getClientFingerprint()));
            builder.append(",\"generatedAtUtc\":").append(AtlasJson.quote(metadata.getGeneratedAtUtc()));
            builder.append(",\"queryId\":").append(AtlasJson.quote(queryId));
            builder.append(",\"indexCurrent\":true");
            builder.append(",\"relationshipCount\":").append(relationshipCount);
            builder.append(",\"relationshipsTruncated\":").append(isRelationshipsTruncated());
            builder.append(",\"symbol\":").append(symbolJson);
            builder.append(",\"relationships\":[");
            for (int i = 0; i < relationships.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(relationships.get(i));
            }
            builder.append("]}");
            return builder.toString();
        }
    }
}
