package game.atlas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DirectoryStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.Metadata;

/**
 * Bounded correlation of saved Client Atlas runtime traces back to the current
 * generated Atlas symbol index. Exact symbol IDs are resolved only against an
 * already-current investigation index; no semantic claims are created here.
 */
public final class AtlasTraceCorrelationEngine {

    public static final int FORMAT_VERSION = 1;
    public static final int MAX_TRACE_EVENTS = AtlasTraceRecorder.MAX_EVENTS;
    public static final int MAX_EXPORTED_EVENTS = 1000;
    public static final int MAX_UNRESOLVED_SYMBOLS = 100;

    private final AtlasInvestigationIndex index;

    public AtlasTraceCorrelationEngine(AtlasInvestigationIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        this.index = index;
    }

    public CorrelationResult correlate(Path traceFile) throws IOException {
        Path normalized = requireTraceFile(traceFile);
        TraceHeader header;
        List<CorrelatedEvent> exportedEvents = new ArrayList<CorrelatedEvent>();
        Set<String> unresolvedSources = new LinkedHashSet<String>();
        Set<String> unresolvedOwners = new LinkedHashSet<String>();
        Map<String, Long> categoryCounts = new LinkedHashMap<String, Long>();
        long totalEvents = 0L;
        long sourceEvents = 0L;
        long resolvedSourceEvents = 0L;
        long ownerEvents = 0L;
        long resolvedOwnerEvents = 0L;

        try (BufferedReader reader = Files.newBufferedReader(normalized, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null || first.trim().length() == 0) {
                throw new IOException("Client Atlas trace is empty: " + normalized);
            }
            header = TraceHeader.parse(first, normalized, 1L);

            String line;
            long lineNumber = 1L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().length() == 0) {
                    continue;
                }
                if (totalEvents >= MAX_TRACE_EVENTS) {
                    throw new IOException("Client Atlas trace exceeds supported event cap "
                            + MAX_TRACE_EVENTS + ": " + normalized);
                }

                TraceEvent event = TraceEvent.parse(line, normalized, lineNumber);
                totalEvents++;
                increment(categoryCounts, event.category);

                SymbolEntry source = null;
                if (event.sourceId != null) {
                    sourceEvents++;
                    source = index.getSymbol(event.sourceId);
                    if (source != null) {
                        resolvedSourceEvents++;
                    } else {
                        unresolvedSources.add(event.sourceId);
                    }
                }

                String ownerId = event.fields.get("ownerSymbol");
                SymbolEntry owner = null;
                if (ownerId != null && ownerId.length() > 0) {
                    ownerEvents++;
                    owner = index.getSymbol(ownerId);
                    if (owner != null) {
                        resolvedOwnerEvents++;
                    } else {
                        unresolvedOwners.add(ownerId);
                    }
                }

                if (exportedEvents.size() < MAX_EXPORTED_EVENTS) {
                    exportedEvents.add(new CorrelatedEvent(event, source, ownerId, owner));
                }
            }
        }

        Metadata metadata = index.getMetadata();
        String atlasFingerprint = metadata.getClientFingerprint();
        boolean fingerprintKnown = header.clientFingerprint != null
                && header.clientFingerprint.length() > 0
                && !"UNKNOWN".equals(header.clientFingerprint);
        boolean fingerprintMatch = fingerprintKnown
                && header.clientFingerprint.equals(atlasFingerprint);
        boolean eventCountMatch = header.eventCount == totalEvents;
        boolean sourceIdsResolved = unresolvedSources.isEmpty();
        boolean ownerIdsResolved = unresolvedOwners.isEmpty();
        boolean accepted = fingerprintMatch && eventCountMatch
                && sourceIdsResolved && ownerIdsResolved;

        String status;
        if (!fingerprintKnown) {
            status = "TRACE_FINGERPRINT_UNKNOWN";
        } else if (!fingerprintMatch) {
            status = "TRACE_FINGERPRINT_MISMATCH";
        } else if (!eventCountMatch) {
            status = "TRACE_EVENT_COUNT_MISMATCH";
        } else if (!sourceIdsResolved || !ownerIdsResolved) {
            status = "UNRESOLVED_SYMBOL_IDS";
        } else {
            status = "CURRENT";
        }

        return new CorrelationResult(normalized, metadata, header, status, accepted,
                totalEvents, exportedEvents,
                sourceEvents, resolvedSourceEvents,
                ownerEvents, resolvedOwnerEvents,
                bounded(unresolvedSources), unresolvedSources.size() > MAX_UNRESOLVED_SYMBOLS,
                bounded(unresolvedOwners), unresolvedOwners.size() > MAX_UNRESOLVED_SYMBOLS,
                categoryCounts);
    }

    public Path writeExport(CorrelationResult result, Path outputFile) throws IOException {
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

    public static Path latestTrace(AtlasWorkspace workspace) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        Path directory = workspace.tracesDirectory();
        if (!Files.isDirectory(directory)) {
            throw new IOException("Client Atlas traces directory does not exist: " + directory);
        }

        Path latest = null;
        long latestModified = Long.MIN_VALUE;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.trace.jsonl")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                long modified = Files.getLastModifiedTime(path).toMillis();
                if (latest == null || modified > latestModified
                        || (modified == latestModified
                                && path.getFileName().toString().compareTo(latest.getFileName().toString()) > 0)) {
                    latest = path;
                    latestModified = modified;
                }
            }
        }
        if (latest == null) {
            throw new IOException("No saved Client Atlas trace exists in: " + directory);
        }
        return latest.toAbsolutePath().normalize();
    }

    private static Path requireTraceFile(Path traceFile) throws IOException {
        if (traceFile == null) {
            throw new IllegalArgumentException("traceFile cannot be null");
        }
        Path normalized = traceFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("Client Atlas trace does not exist: " + normalized);
        }
        return normalized;
    }

    private static void increment(Map<String, Long> counts, String key) {
        Long value = counts.get(key);
        counts.put(key, Long.valueOf(value == null ? 1L : value.longValue() + 1L));
    }

    private static List<String> bounded(Set<String> values) {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>(Math.min(values.size(), MAX_UNRESOLVED_SYMBOLS));
        for (String value : values) {
            if (result.size() >= MAX_UNRESOLVED_SYMBOLS) {
                break;
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    public static final class CorrelationResult {
        private final Path tracePath;
        private final Metadata metadata;
        private final TraceHeader header;
        private final String status;
        private final boolean accepted;
        private final long totalEvents;
        private final List<CorrelatedEvent> events;
        private final long sourceEvents;
        private final long resolvedSourceEvents;
        private final long ownerEvents;
        private final long resolvedOwnerEvents;
        private final List<String> unresolvedSources;
        private final boolean unresolvedSourcesTruncated;
        private final List<String> unresolvedOwners;
        private final boolean unresolvedOwnersTruncated;
        private final Map<String, Long> categoryCounts;

        private CorrelationResult(Path tracePath, Metadata metadata, TraceHeader header,
                String status, boolean accepted, long totalEvents,
                List<CorrelatedEvent> events,
                long sourceEvents, long resolvedSourceEvents,
                long ownerEvents, long resolvedOwnerEvents,
                List<String> unresolvedSources, boolean unresolvedSourcesTruncated,
                List<String> unresolvedOwners, boolean unresolvedOwnersTruncated,
                Map<String, Long> categoryCounts) {
            this.tracePath = tracePath;
            this.metadata = metadata;
            this.header = header;
            this.status = status;
            this.accepted = accepted;
            this.totalEvents = totalEvents;
            this.events = Collections.unmodifiableList(new ArrayList<CorrelatedEvent>(events));
            this.sourceEvents = sourceEvents;
            this.resolvedSourceEvents = resolvedSourceEvents;
            this.ownerEvents = ownerEvents;
            this.resolvedOwnerEvents = resolvedOwnerEvents;
            this.unresolvedSources = unresolvedSources;
            this.unresolvedSourcesTruncated = unresolvedSourcesTruncated;
            this.unresolvedOwners = unresolvedOwners;
            this.unresolvedOwnersTruncated = unresolvedOwnersTruncated;
            this.categoryCounts = Collections.unmodifiableMap(new LinkedHashMap<String, Long>(categoryCounts));
        }

        public String getStatus() { return status; }
        public boolean isAccepted() { return accepted; }
        public long getTotalEvents() { return totalEvents; }
        public int getExportedEventCount() { return events.size(); }
        public boolean isEventsTruncated() { return totalEvents > events.size(); }
        public String getTraceFingerprint() { return header.clientFingerprint; }
        public String getAtlasFingerprint() { return metadata.getClientFingerprint(); }
        public long getDroppedCount() { return header.droppedCount; }
        public Path getTracePath() { return tracePath; }

        public String toJson() {
            StringBuilder builder = new StringBuilder(4096 + events.size() * 420);
            builder.append('{');
            appendString(builder, "recordType", "trace-correlation", true);
            appendNumber(builder, "formatVersion", FORMAT_VERSION);
            appendString(builder, "status", status, false);
            appendBoolean(builder, "correlationAccepted", accepted);
            appendString(builder, "tracePath", tracePath.toString(), false);
            appendString(builder, "traceSession", header.sessionName, false);
            appendString(builder, "traceFingerprint", header.clientFingerprint, false);
            appendString(builder, "atlasFingerprint", metadata.getClientFingerprint(), false);
            appendBoolean(builder, "fingerprintMatch",
                    header.clientFingerprint != null
                            && !"UNKNOWN".equals(header.clientFingerprint)
                            && header.clientFingerprint.equals(metadata.getClientFingerprint()));
            appendNumber(builder, "traceHeaderEventCount", header.eventCount);
            appendNumber(builder, "actualEventCount", totalEvents);
            appendBoolean(builder, "eventCountMatch", header.eventCount == totalEvents);
            appendNumber(builder, "droppedCount", header.droppedCount);
            appendNumber(builder, "sourceEvents", sourceEvents);
            appendNumber(builder, "resolvedSourceEvents", resolvedSourceEvents);
            appendNumber(builder, "ownerEvents", ownerEvents);
            appendNumber(builder, "resolvedOwnerEvents", resolvedOwnerEvents);
            appendNumber(builder, "exportedEventCount", events.size());
            appendBoolean(builder, "eventsTruncated", isEventsTruncated());
            appendStringMap(builder, "categoryCounts", categoryCounts);
            appendStringList(builder, "unresolvedSourceSymbols", unresolvedSources);
            appendBoolean(builder, "unresolvedSourceSymbolsTruncated", unresolvedSourcesTruncated);
            appendStringList(builder, "unresolvedOwnerSymbols", unresolvedOwners);
            appendBoolean(builder, "unresolvedOwnerSymbolsTruncated", unresolvedOwnersTruncated);
            builder.append(",\"events\":[");
            for (int i = 0; i < events.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                events.get(i).appendJson(builder);
            }
            builder.append("]}");
            return builder.toString();
        }
    }

    private static final class CorrelatedEvent {
        private final TraceEvent event;
        private final SymbolEntry source;
        private final String ownerId;
        private final SymbolEntry owner;

        private CorrelatedEvent(TraceEvent event, SymbolEntry source,
                String ownerId, SymbolEntry owner) {
            this.event = event;
            this.source = source;
            this.ownerId = ownerId;
            this.owner = owner;
        }

        private void appendJson(StringBuilder builder) {
            builder.append('{');
            appendNumberFirst(builder, "sequence", event.sequence);
            appendString(builder, "timestampUtc", event.timestampUtc, false);
            appendString(builder, "category", event.category, false);
            appendString(builder, "eventType", event.eventType, false);
            appendString(builder, "thread", event.threadName, false);
            appendString(builder, "sourceSymbol", event.sourceId, false);
            builder.append(",\"sourceResolved\":").append(source != null);
            builder.append(",\"source\":");
            appendSymbol(builder, source);
            appendString(builder, "ownerSymbol", ownerId, false);
            builder.append(",\"ownerResolved\":").append(owner != null);
            builder.append(",\"owner\":");
            appendSymbol(builder, owner);
            appendStringMap(builder, "fields", event.fields);
            builder.append('}');
        }
    }

    private static final class TraceHeader {
        private final String sessionName;
        private final String clientFingerprint;
        private final long eventCount;
        private final long droppedCount;

        private TraceHeader(String sessionName, String clientFingerprint,
                long eventCount, long droppedCount) {
            this.sessionName = sessionName;
            this.clientFingerprint = clientFingerprint;
            this.eventCount = eventCount;
            this.droppedCount = droppedCount;
        }

        private static TraceHeader parse(String json, Path path, long lineNumber) throws IOException {
            try {
                if (!"trace-session".equals(JsonLine.requiredString(json, "recordType"))) {
                    throw new IllegalArgumentException("first record is not trace-session");
                }
                long version = JsonLine.requiredLong(json, "formatVersion");
                if (version != 1L) {
                    throw new IllegalArgumentException("unsupported trace formatVersion " + version);
                }
                return new TraceHeader(
                        JsonLine.requiredString(json, "sessionName"),
                        JsonLine.requiredString(json, "clientFingerprint"),
                        JsonLine.requiredLong(json, "eventCount"),
                        JsonLine.requiredLong(json, "droppedCount"));
            } catch (RuntimeException ex) {
                throw malformed(path, lineNumber, ex);
            }
        }
    }

    private static final class TraceEvent {
        private final long sequence;
        private final String timestampUtc;
        private final String category;
        private final String eventType;
        private final String sourceId;
        private final String threadName;
        private final Map<String, String> fields;

        private TraceEvent(long sequence, String timestampUtc, String category,
                String eventType, String sourceId, String threadName,
                Map<String, String> fields) {
            this.sequence = sequence;
            this.timestampUtc = timestampUtc;
            this.category = category;
            this.eventType = eventType;
            this.sourceId = sourceId;
            this.threadName = threadName;
            this.fields = fields;
        }

        private static TraceEvent parse(String json, Path path, long lineNumber) throws IOException {
            try {
                if (!"event".equals(JsonLine.requiredString(json, "recordType"))) {
                    throw new IllegalArgumentException("record is not event");
                }
                return new TraceEvent(
                        JsonLine.requiredLong(json, "sequence"),
                        JsonLine.requiredString(json, "timestampUtc"),
                        JsonLine.requiredString(json, "category"),
                        JsonLine.requiredString(json, "eventType"),
                        JsonLine.nullableString(json, "sourceId"),
                        JsonLine.requiredString(json, "thread"),
                        JsonLine.stringObject(json, "fields"));
            } catch (RuntimeException ex) {
                throw malformed(path, lineNumber, ex);
            }
        }
    }

    private static IOException malformed(Path path, long lineNumber, RuntimeException cause) {
        return new IOException("Malformed Client Atlas trace at " + path + ":" + lineNumber
                + " - " + cause.getMessage(), cause);
    }

    private static void appendSymbol(StringBuilder builder, SymbolEntry symbol) {
        if (symbol == null) {
            builder.append("null");
            return;
        }
        builder.append('{');
        appendString(builder, "id", symbol.getId(), true);
        appendString(builder, "kind", symbol.getKind().name(), false);
        appendString(builder, "owner", symbol.getOwner(), false);
        appendString(builder, "name", symbol.getName(), false);
        appendString(builder, "descriptor", symbol.getDescriptor(), false);
        appendString(builder, "sourcePath", symbol.getSourcePath(), false);
        builder.append('}');
    }

    private static void appendStringMap(StringBuilder builder, String name, Map<String, ?> values) {
        builder.append(',').append(AtlasJson.quote(name)).append(":{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(AtlasJson.quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value instanceof Number) {
                builder.append(((Number) value).longValue());
            } else {
                builder.append(AtlasJson.quote(value == null ? null : value.toString()));
            }
            first = false;
        }
        builder.append('}');
    }

    private static void appendStringList(StringBuilder builder, String name, List<String> values) {
        builder.append(',').append(AtlasJson.quote(name)).append(":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(AtlasJson.quote(values.get(i)));
        }
        builder.append(']');
    }

    private static void appendString(StringBuilder builder, String name, String value, boolean first) {
        if (!first) {
            builder.append(',');
        }
        builder.append(AtlasJson.quote(name)).append(':').append(AtlasJson.quote(value));
    }

    private static void appendNumber(StringBuilder builder, String name, long value) {
        builder.append(',').append(AtlasJson.quote(name)).append(':').append(value);
    }

    private static void appendNumberFirst(StringBuilder builder, String name, long value) {
        builder.append(AtlasJson.quote(name)).append(':').append(value);
    }

    private static void appendBoolean(StringBuilder builder, String name, boolean value) {
        builder.append(',').append(AtlasJson.quote(name)).append(':').append(value);
    }

    /** Parser for the exact compact JSON shape written by AtlasTraceRecorder. */
    private static final class JsonLine {
        private static String requiredString(String json, String field) {
            String value = nullableString(json, field);
            if (value == null) {
                throw new IllegalArgumentException("missing/null string field " + field);
            }
            return value;
        }

        private static String nullableString(String json, String field) {
            int start = valueStart(json, field);
            if (startsWith(json, start, "null")) {
                return null;
            }
            ParseString parsed = parseString(json, start, field);
            return parsed.value;
        }

        private static long requiredLong(String json, String field) {
            int start = valueStart(json, field);
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
            return Long.parseLong(json.substring(start, end));
        }

        private static Map<String, String> stringObject(String json, String field) {
            int start = valueStart(json, field);
            if (start >= json.length() || json.charAt(start) != '{') {
                throw new IllegalArgumentException("field " + field + " is not an object");
            }
            Map<String, String> values = new LinkedHashMap<String, String>();
            int offset = start + 1;
            for (;;) {
                offset = skipWhitespace(json, offset);
                if (offset >= json.length()) {
                    throw new IllegalArgumentException("unterminated object field " + field);
                }
                if (json.charAt(offset) == '}') {
                    return Collections.unmodifiableMap(values);
                }
                ParseString key = parseString(json, offset, field);
                offset = skipWhitespace(json, key.next);
                if (offset >= json.length() || json.charAt(offset) != ':') {
                    throw new IllegalArgumentException("missing ':' in object field " + field);
                }
                offset = skipWhitespace(json, offset + 1);
                String value;
                if (startsWith(json, offset, "null")) {
                    value = null;
                    offset += 4;
                } else {
                    ParseString parsedValue = parseString(json, offset, field);
                    value = parsedValue.value;
                    offset = parsedValue.next;
                }
                if (values.size() >= 24) {
                    throw new IllegalArgumentException("too many entries in object field " + field);
                }
                values.put(key.value, value);
                offset = skipWhitespace(json, offset);
                if (offset >= json.length()) {
                    throw new IllegalArgumentException("unterminated object field " + field);
                }
                char separator = json.charAt(offset);
                if (separator == '}') {
                    return Collections.unmodifiableMap(values);
                }
                if (separator != ',') {
                    throw new IllegalArgumentException("invalid object separator in field " + field);
                }
                offset++;
            }
        }

        private static ParseString parseString(String json, int start, String field) {
            if (start >= json.length() || json.charAt(start) != '"') {
                throw new IllegalArgumentException("field " + field + " is not a JSON string");
            }
            StringBuilder decoded = new StringBuilder();
            for (int i = start + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    return new ParseString(decoded.toString(), i + 1);
                }
                if (c != '\\') {
                    decoded.append(c);
                    continue;
                }
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
            }
            throw new IllegalArgumentException("unterminated JSON string field " + field);
        }

        private static int valueStart(String json, String field) {
            String token = "\"" + field + "\":";
            int start = json.indexOf(token);
            if (start < 0) {
                throw new IllegalArgumentException("missing field " + field);
            }
            return skipWhitespace(json, start + token.length());
        }

        private static int skipWhitespace(String value, int offset) {
            while (offset < value.length() && Character.isWhitespace(value.charAt(offset))) {
                offset++;
            }
            return offset;
        }

        private static boolean startsWith(String value, int offset, String token) {
            return offset >= 0 && offset + token.length() <= value.length()
                    && value.regionMatches(offset, token, 0, token.length());
        }
    }

    private static final class ParseString {
        private final String value;
        private final int next;

        private ParseString(String value, int next) {
            this.value = value;
            this.next = next;
        }
    }
}
