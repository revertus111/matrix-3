package game.atlas;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSchema.RelationshipType;
import game.atlas.AtlasSchema.SymbolKind;

/**
 * Immutable in-memory investigation index over the generated Client Atlas JSONL.
 *
 * This is a read/search acceleration layer only. AtlasScanner remains the owner
 * of discovery and generated data, and exact obfuscated IDs remain authoritative.
 */
public final class AtlasInvestigationIndex {

    private final Metadata metadata;
    private final long loadNanos;
    private final Map<String, SymbolEntry> symbolsById;
    private final Map<String, List<SymbolEntry>> symbolsByOwner;
    private final Map<String, List<SymbolEntry>> symbolsByName;
    private final Map<String, List<SymbolEntry>> symbolsByOwnerAndName;
    private final Map<String, List<RelationshipEntry>> outgoingBySource;
    private final Map<String, List<RelationshipEntry>> incomingByTarget;
    private final Map<String, List<RelationshipEntry>> constantReferrers;
    private final List<SymbolEntry> symbols;
    private final long relationshipCount;

    private AtlasInvestigationIndex(Metadata metadata, long loadNanos,
            Map<String, SymbolEntry> symbolsById,
            Map<String, List<SymbolEntry>> symbolsByOwner,
            Map<String, List<SymbolEntry>> symbolsByName,
            Map<String, List<SymbolEntry>> symbolsByOwnerAndName,
            Map<String, List<RelationshipEntry>> outgoingBySource,
            Map<String, List<RelationshipEntry>> incomingByTarget,
            Map<String, List<RelationshipEntry>> constantReferrers,
            List<SymbolEntry> symbols, long relationshipCount) {
        this.metadata = metadata;
        this.loadNanos = loadNanos;
        this.symbolsById = symbolsById;
        this.symbolsByOwner = symbolsByOwner;
        this.symbolsByName = symbolsByName;
        this.symbolsByOwnerAndName = symbolsByOwnerAndName;
        this.outgoingBySource = outgoingBySource;
        this.incomingByTarget = incomingByTarget;
        this.constantReferrers = constantReferrers;
        this.symbols = symbols;
        this.relationshipCount = relationshipCount;
    }

    public static AtlasInvestigationIndex load(AtlasWorkspace workspace, Path classRoot) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        if (classRoot == null) {
            throw new IllegalArgumentException("classRoot cannot be null");
        }

        Path normalizedClassRoot = classRoot.toAbsolutePath().normalize();
        Metadata metadata = workspace.readMetadata();
        if (metadata.getSchemaVersion() != AtlasWorkspace.SCHEMA_VERSION) {
            throw new IOException("Unsupported Client Atlas schema version: " + metadata.getSchemaVersion()
                    + " (expected " + AtlasWorkspace.SCHEMA_VERSION + "). Rebuild the Atlas index first.");
        }
        if (!workspace.isCurrent(normalizedClassRoot)) {
            throw new IOException("Client Atlas index is stale for the compiled client. Rebuild the Atlas index first.");
        }
        requireGeneratedFile(workspace.symbolsFile());
        requireGeneratedFile(workspace.relationshipsFile());

        long started = System.nanoTime();
        Builder builder = new Builder(metadata);
        builder.loadSymbols(workspace.symbolsFile());
        builder.loadRelationships(workspace.relationshipsFile());
        builder.verifyCounts();

        if (!workspace.isCurrent(normalizedClassRoot)) {
            throw new IOException("Compiled client changed while the investigation index was loading; rebuild Atlas and try again.");
        }

        return builder.build(System.nanoTime() - started);
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public long getLoadNanos() {
        return loadNanos;
    }

    public long getSymbolCount() {
        return symbols.size();
    }

    public long getRelationshipCount() {
        return relationshipCount;
    }

    public List<SymbolEntry> getSymbols() {
        return symbols;
    }

    public SymbolEntry getSymbol(String symbolId) {
        if (symbolId == null) {
            return null;
        }
        return symbolsById.get(symbolId);
    }

    public List<SymbolEntry> findByOwner(String owner) {
        return lookup(symbolsByOwner, normalizeKey(owner));
    }

    public List<SymbolEntry> findByName(String name) {
        return lookup(symbolsByName, normalizeKey(name));
    }

    public List<SymbolEntry> findByOwnerAndName(String owner, String name) {
        String ownerKey = normalizeKey(owner);
        String nameKey = normalizeKey(name);
        if (ownerKey == null || nameKey == null) {
            return Collections.emptyList();
        }
        return lookup(symbolsByOwnerAndName, ownerNameKey(ownerKey, nameKey));
    }

    public List<RelationshipEntry> outgoing(String sourceId) {
        return lookup(outgoingBySource, sourceId);
    }

    public List<RelationshipEntry> incoming(String target) {
        return lookup(incomingByTarget, target);
    }

    public List<RelationshipEntry> constantReferrers(String typedConstant) {
        return lookup(constantReferrers, typedConstant);
    }

    private static void requireGeneratedFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Client Atlas generated file does not exist: " + path + ". Rebuild Atlas first.");
        }
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String ownerNameKey(String ownerKey, String nameKey) {
        return ownerKey + '\u0000' + nameKey;
    }

    private static String simpleName(String value) {
        if (value == null) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
    }

    private static <T> List<T> lookup(Map<String, List<T>> map, String key) {
        if (key == null) {
            return Collections.emptyList();
        }
        List<T> values = map.get(key);
        return values == null ? Collections.<T>emptyList() : values;
    }

    private static final class Builder {
        private final Metadata metadata;
        private final StringPool strings = new StringPool();
        private final Map<String, SymbolEntry> symbolsById = new HashMap<String, SymbolEntry>();
        private final Map<String, List<SymbolEntry>> symbolsByOwner = new HashMap<String, List<SymbolEntry>>();
        private final Map<String, List<SymbolEntry>> symbolsByName = new HashMap<String, List<SymbolEntry>>();
        private final Map<String, List<SymbolEntry>> symbolsByOwnerAndName = new HashMap<String, List<SymbolEntry>>();
        private final Map<String, List<RelationshipEntry>> outgoingBySource = new HashMap<String, List<RelationshipEntry>>();
        private final Map<String, List<RelationshipEntry>> incomingByTarget = new HashMap<String, List<RelationshipEntry>>();
        private final Map<String, List<RelationshipEntry>> constantReferrers = new HashMap<String, List<RelationshipEntry>>();
        private final List<SymbolEntry> symbols = new ArrayList<SymbolEntry>();
        private long relationships;

        private Builder(Metadata metadata) {
            this.metadata = metadata;
        }

        private void loadSymbols(Path path) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                long lineNumber = 0L;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    SymbolEntry entry;
                    try {
                        entry = parseSymbol(line, strings);
                    } catch (RuntimeException ex) {
                        throw malformed(path, lineNumber, ex);
                    }
                    if (symbolsById.put(entry.getId(), entry) != null) {
                        throw new IOException("Duplicate Client Atlas symbol id: " + entry.getId());
                    }
                    symbols.add(entry);
                    add(symbolsByOwner, normalizeKey(entry.getOwner()), entry);
                    add(symbolsByName, normalizeKey(entry.getName()), entry);
                    String simple = simpleName(entry.getName());
                    if (simple != null && !simple.equals(entry.getName())) {
                        add(symbolsByName, normalizeKey(simple), entry);
                    }
                    add(symbolsByOwnerAndName,
                            ownerNameKey(normalizeKey(entry.getOwner()), normalizeKey(entry.getName())), entry);
                }
            }
        }

        private void loadRelationships(Path path) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                long lineNumber = 0L;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    RelationshipEntry entry;
                    try {
                        entry = parseRelationship(line, strings);
                    } catch (RuntimeException ex) {
                        throw malformed(path, lineNumber, ex);
                    }
                    relationships++;
                    add(outgoingBySource, entry.getFromId(), entry);
                    add(incomingByTarget, entry.getTarget(), entry);
                    if (entry.getType() == RelationshipType.CONSTANT) {
                        add(constantReferrers, entry.getTarget(), entry);
                    }
                }
            }
        }

        private void verifyCounts() throws IOException {
            if (symbols.size() != metadata.getSymbolCount()) {
                throw new IOException("Client Atlas symbol count changed while loading investigation index: metadata="
                        + metadata.getSymbolCount() + ", loaded=" + symbols.size());
            }
            if (relationships != metadata.getRelationshipCount()) {
                throw new IOException("Client Atlas relationship count changed while loading investigation index: metadata="
                        + metadata.getRelationshipCount() + ", loaded=" + relationships);
            }
        }

        private AtlasInvestigationIndex build(long loadNanos) {
            return new AtlasInvestigationIndex(metadata, loadNanos,
                    Collections.unmodifiableMap(symbolsById),
                    freeze(symbolsByOwner),
                    freeze(symbolsByName),
                    freeze(symbolsByOwnerAndName),
                    freeze(outgoingBySource),
                    freeze(incomingByTarget),
                    freeze(constantReferrers),
                    Collections.unmodifiableList(new ArrayList<SymbolEntry>(symbols)),
                    relationships);
        }
    }

    private static SymbolEntry parseSymbol(String json, StringPool pool) {
        String id = pool.get(JsonLine.requiredString(json, "id"));
        SymbolKind kind = SymbolKind.valueOf(JsonLine.requiredString(json, "kind"));
        String owner = pool.get(JsonLine.requiredString(json, "owner"));
        String name = pool.get(JsonLine.requiredString(json, "name"));
        String descriptor = pool.get(JsonLine.requiredString(json, "descriptor"));
        String signature = pool.get(JsonLine.optionalString(json, "signature"));
        String compiledPath = pool.get(JsonLine.optionalString(json, "compiledPath"));
        String sourcePath = pool.get(JsonLine.optionalString(json, "sourcePath"));
        int access = JsonLine.requiredInt(json, "access");
        return new SymbolEntry(id, kind, owner, name, descriptor, signature,
                compiledPath, sourcePath, access);
    }

    private static RelationshipEntry parseRelationship(String json, StringPool pool) {
        String fromId = pool.get(JsonLine.requiredString(json, "fromId"));
        RelationshipType type = RelationshipType.valueOf(JsonLine.requiredString(json, "type"));
        String target = pool.get(JsonLine.requiredString(json, "target"));
        String sourcePath = pool.get(JsonLine.optionalString(json, "sourcePath"));
        Integer sourceLine = JsonLine.optionalInt(json, "sourceLine");
        Integer opcode = JsonLine.optionalInt(json, "opcode");
        int occurrenceCount = JsonLine.requiredInt(json, "occurrenceCount");
        String detail = pool.get(JsonLine.optionalString(json, "detail"));
        return new RelationshipEntry(fromId, type, target, sourcePath,
                sourceLine == null ? -1 : sourceLine.intValue(),
                opcode == null ? -1 : opcode.intValue(), occurrenceCount, detail);
    }

    private static IOException malformed(Path path, long lineNumber, RuntimeException cause) {
        return new IOException("Malformed Client Atlas JSONL at " + path + ":" + lineNumber
                + " - " + cause.getMessage(), cause);
    }

    private static <T> void add(Map<String, List<T>> map, String key, T value) {
        if (key == null) {
            return;
        }
        List<T> values = map.get(key);
        if (values == null) {
            values = new ArrayList<T>();
            map.put(key, values);
        }
        values.add(value);
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> source) {
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(source);
    }

    public static final class SymbolEntry {
        private final String id;
        private final SymbolKind kind;
        private final String owner;
        private final String name;
        private final String descriptor;
        private final String signature;
        private final String compiledPath;
        private final String sourcePath;
        private final int access;

        private SymbolEntry(String id, SymbolKind kind, String owner, String name,
                String descriptor, String signature, String compiledPath,
                String sourcePath, int access) {
            this.id = id;
            this.kind = kind;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.compiledPath = compiledPath;
            this.sourcePath = sourcePath;
            this.access = access;
        }

        public String getId() { return id; }
        public SymbolKind getKind() { return kind; }
        public String getOwner() { return owner; }
        public String getName() { return name; }
        public String getDescriptor() { return descriptor; }
        public String getSignature() { return signature; }
        public String getCompiledPath() { return compiledPath; }
        public String getSourcePath() { return sourcePath; }
        public int getAccess() { return access; }
    }

    public static final class RelationshipEntry {
        private final String fromId;
        private final RelationshipType type;
        private final String target;
        private final String sourcePath;
        private final int sourceLine;
        private final int opcode;
        private final int occurrenceCount;
        private final String detail;

        private RelationshipEntry(String fromId, RelationshipType type, String target,
                String sourcePath, int sourceLine, int opcode, int occurrenceCount,
                String detail) {
            this.fromId = fromId;
            this.type = type;
            this.target = target;
            this.sourcePath = sourcePath;
            this.sourceLine = sourceLine;
            this.opcode = opcode;
            this.occurrenceCount = occurrenceCount;
            this.detail = detail;
        }

        public String getFromId() { return fromId; }
        public RelationshipType getType() { return type; }
        public String getTarget() { return target; }
        public String getSourcePath() { return sourcePath; }
        public boolean hasSourceLine() { return sourceLine > 0; }
        public int getSourceLine() { return sourceLine; }
        public boolean hasOpcode() { return opcode >= 0; }
        public int getOpcode() { return opcode; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public String getDetail() { return detail; }
    }

    private static final class StringPool {
        private final Map<String, String> values = new HashMap<String, String>();

        private String get(String value) {
            if (value == null) {
                return null;
            }
            String existing = values.get(value);
            if (existing != null) {
                return existing;
            }
            values.put(value, value);
            return value;
        }
    }

    /** Flat deterministic Atlas JSONL parser; no general JSON semantics are claimed. */
    private static final class JsonLine {
        private static String requiredString(String json, String field) {
            String value = optionalString(json, field);
            if (value == null) {
                throw new IllegalArgumentException("missing/null string field " + field);
            }
            return value;
        }

        private static String optionalString(String json, String field) {
            int start = valueStart(json, field);
            if (startsWith(json, start, "null")) {
                return null;
            }
            if (start >= json.length() || json.charAt(start) != '"') {
                throw new IllegalArgumentException("field " + field + " is not a JSON string");
            }
            StringBuilder decoded = null;
            int segmentStart = start + 1;
            for (int i = segmentStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    if (decoded == null) {
                        return json.substring(segmentStart, i);
                    }
                    decoded.append(json, segmentStart, i);
                    return decoded.toString();
                }
                if (c != '\\') {
                    continue;
                }
                if (decoded == null) {
                    decoded = new StringBuilder(i - segmentStart + 16);
                }
                decoded.append(json, segmentStart, i);
                if (++i >= json.length()) {
                    throw new IllegalArgumentException("unterminated escape in field " + field);
                }
                char escaped = json.charAt(i);
                switch (escaped) {
                case '"': decoded.append('"'); break;
                case '\\': decoded.append('\\'); break;
                case '/': decoded.append('/'); break;
                case 'b': decoded.append('\b'); break;
                case 'f': decoded.append('\f'); break;
                case 'n': decoded.append('\n'); break;
                case 'r': decoded.append('\r'); break;
                case 't': decoded.append('\t'); break;
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
                    decoded.append((char) code);
                    i += 4;
                    break;
                default:
                    throw new IllegalArgumentException("invalid escape in field " + field);
                }
                segmentStart = i + 1;
            }
            throw new IllegalArgumentException("unterminated JSON string field " + field);
        }

        private static int requiredInt(String json, String field) {
            Integer value = optionalInt(json, field);
            if (value == null) {
                throw new IllegalArgumentException("missing/null integer field " + field);
            }
            return value.intValue();
        }

        private static Integer optionalInt(String json, String field) {
            int start = valueStart(json, field);
            if (startsWith(json, start, "null")) {
                return null;
            }
            int end = start;
            if (end < json.length() && json.charAt(end) == '-') {
                end++;
            }
            int digits = end;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            if (end == digits) {
                throw new IllegalArgumentException("field " + field + " is not an integer");
            }
            return Integer.valueOf(json.substring(start, end));
        }

        private static int valueStart(String json, String field) {
            String token = "\"" + field + "\":";
            int start = json.indexOf(token);
            if (start < 0) {
                throw new IllegalArgumentException("missing field " + field);
            }
            return start + token.length();
        }

        private static boolean startsWith(String value, int offset, String token) {
            return offset >= 0 && offset + token.length() <= value.length()
                    && value.regionMatches(offset, token, 0, token.length());
        }
    }
}
