package game.atlas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide bounded runtime trace recorder. Runtime hooks never perform disk
 * I/O; persistence/control work is owned by the daemon control thread.
 */
public final class AtlasTraceRecorder {

    public static final int MAX_EVENTS = 10000;
    public static final int MAX_DEFINITION_EVENTS = 4000;

    private static final Object LOCK = new Object();
    private static final List<TraceEvent> EVENTS = new ArrayList<TraceEvent>(MAX_EVENTS);
    private static final Set<String> UNIQUE_KEYS = new HashSet<String>(MAX_DEFINITION_EVENTS);
    private static final Map<String, Integer> CATEGORY_COUNTS = new HashMap<String, Integer>();
    private static final AtomicBoolean CONTROL_SERVER_STARTED = new AtomicBoolean(false);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private static volatile boolean active;
    private static String sessionName;
    private static String startedAtUtc;
    private static String stoppedAtUtc;
    private static String lastSavedPath;
    private static long sequence;
    private static long droppedCount;
    private static long suppressedCount;

    private AtlasTraceRecorder() {
    }

    public static void ensureRuntimeControl() {
        if (!CONTROL_SERVER_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runControlServer();
            }
        }, "ClientAtlas-TraceControl");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public static boolean isActive() {
        return active;
    }

    public static void start(String requestedName) {
        synchronized (LOCK) {
            if (active) {
                throw new IllegalStateException("Client Atlas trace is already active");
            }
            EVENTS.clear();
            UNIQUE_KEYS.clear();
            CATEGORY_COUNTS.clear();
            sequence = 0L;
            droppedCount = 0L;
            suppressedCount = 0L;
            sessionName = normalizeSessionName(requestedName);
            startedAtUtc = Instant.now().toString();
            stoppedAtUtc = null;
            lastSavedPath = null;
            active = true;
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (!active) {
                return;
            }
            active = false;
            stoppedAtUtc = Instant.now().toString();
        }
    }

    public static void record(String category, String eventType, String sourceId, String... fields) {
        recordInternal(category, eventType, sourceId, null, 0, fields);
    }

    /**
     * Records only the first occurrence of a caller-provided key per session and
     * optionally caps that category below the global buffer ceiling. Intentional
     * filtering increments suppressedCount rather than droppedCount.
     */
    public static void recordOnce(String category, String eventType, String sourceId,
            String uniqueKey, int maxCategoryEvents, String... fields) {
        if (uniqueKey == null || uniqueKey.length() == 0) {
            record(category, eventType, sourceId, fields);
            return;
        }
        recordInternal(category, eventType, sourceId, uniqueKey, maxCategoryEvents, fields);
    }

    private static void recordInternal(String category, String eventType, String sourceId,
            String uniqueKey, int maxCategoryEvents, String... fields) {
        if (!active) {
            return;
        }

        String safeCategory = limit(category == null ? "unknown" : category, 64);
        String safeEventType = limit(eventType == null ? "unknown" : eventType, 80);
        String safeSourceId = sourceId == null ? null : limit(sourceId, 240);
        String safeThread = limit(Thread.currentThread().getName(), 80);
        String[] safeFields = sanitizeFields(fields);
        String timestampUtc = Instant.now().toString();
        String safeUniqueKey = uniqueKey == null ? null
                : safeCategory + '|' + safeEventType + '|' + limit(uniqueKey, 320);

        synchronized (LOCK) {
            if (!active) {
                return;
            }

            if (safeUniqueKey != null && UNIQUE_KEYS.contains(safeUniqueKey)) {
                suppressedCount++;
                return;
            }

            Integer currentCategoryCount = CATEGORY_COUNTS.get(safeCategory);
            int categoryCount = currentCategoryCount == null ? 0 : currentCategoryCount.intValue();
            if (maxCategoryEvents > 0 && categoryCount >= maxCategoryEvents) {
                suppressedCount++;
                return;
            }

            long eventSequence = ++sequence;
            if (EVENTS.size() >= MAX_EVENTS) {
                droppedCount++;
                return;
            }

            if (safeUniqueKey != null) {
                UNIQUE_KEYS.add(safeUniqueKey);
            }
            CATEGORY_COUNTS.put(safeCategory, Integer.valueOf(categoryCount + 1));
            EVENTS.add(new TraceEvent(eventSequence, timestampUtc, safeCategory, safeEventType,
                    safeSourceId, safeThread, safeFields));
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(active, sessionName, EVENTS.size(), droppedCount, suppressedCount,
                    startedAtUtc, stoppedAtUtc, lastSavedPath);
        }
    }

    public static Path save(AtlasWorkspace workspace) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }

        SessionCopy copy;
        synchronized (LOCK) {
            if (startedAtUtc == null) {
                throw new IOException("No Client Atlas trace session exists to save");
            }
            copy = new SessionCopy(active, sessionName, startedAtUtc, stoppedAtUtc,
                    droppedCount, suppressedCount, new ArrayList<TraceEvent>(EVENTS));
        }

        workspace.ensureLayout();
        Files.createDirectories(workspace.tracesDirectory());
        String fileName = FILE_TIME.format(Instant.now()) + "-" + safeFileName(copy.sessionName) + ".trace.jsonl";
        Path output = workspace.tracesDirectory().resolve(fileName);
        Path temp = output.resolveSibling(output.getFileName().toString() + ".tmp");
        String fingerprint = resolveFingerprint(workspace);

        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(headerJson(copy, fingerprint));
            writer.newLine();
            for (TraceEvent event : copy.events) {
                writer.write(eventJson(event));
                writer.newLine();
            }
        }

        try {
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        }

        synchronized (LOCK) {
            lastSavedPath = output.toString();
        }
        return output;
    }

    private static void runControlServer() {
        AtlasWorkspace workspace = null;
        String lastRequestId = "";
        String lastError = "";
        long nextHeartbeat = 0L;

        for (;;) {
            boolean handledRequest = false;
            try {
                if (workspace == null) {
                    Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
                    workspace = new AtlasWorkspace(clientRoot);
                    workspace.ensureLayout();
                }

                AtlasTraceControl.ControlRequest request = AtlasTraceControl.readRequest(workspace);
                if (request != null && !request.getRequestId().equals(lastRequestId)) {
                    lastRequestId = request.getRequestId();
                    handledRequest = true;
                    if (request.isFresh()) {
                        handleRequest(workspace, request);
                        lastError = "";
                    } else {
                        lastError = "Ignored stale trace control request";
                    }
                }

                long now = System.currentTimeMillis();
                if (handledRequest || now >= nextHeartbeat) {
                    AtlasTraceControl.writeRuntimeStatus(workspace, lastRequestId, snapshot(), lastError);
                    nextHeartbeat = now + 1000L;
                }
            } catch (Throwable ex) {
                lastError = safeError(ex);
                if (workspace != null) {
                    try {
                        AtlasTraceControl.writeRuntimeStatus(workspace, lastRequestId, snapshot(), lastError);
                    } catch (Throwable ignored) {
                        /* Trace tooling must never affect the client. */
                    }
                }
            }

            try {
                Thread.sleep(250L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void handleRequest(AtlasWorkspace workspace, AtlasTraceControl.ControlRequest request)
            throws IOException {
        switch (request.getCommand()) {
        case START:
            start(request.getSessionName());
            break;
        case STOP:
            stop();
            break;
        case SAVE:
            save(workspace);
            break;
        case STOP_SAVE:
            stop();
            save(workspace);
            break;
        default:
            throw new IOException("Unsupported Client Atlas trace command: " + request.getCommand());
        }
    }

    private static String resolveFingerprint(AtlasWorkspace workspace) {
        try {
            Path classRoot = workspace.defaultClassRoot();
            if (Files.isDirectory(classRoot)) {
                return AtlasFingerprint.compute(classRoot);
            }
        } catch (IOException ex) {
            /* Trace save remains useful even if classes are temporarily unavailable. */
        }
        return "UNKNOWN";
    }

    private static String headerJson(SessionCopy copy, String fingerprint) {
        StringBuilder builder = new StringBuilder(416);
        builder.append('{');
        builder.append("\"recordType\":\"trace-session\"");
        builder.append(",\"formatVersion\":1");
        builder.append(",\"sessionName\":").append(AtlasJson.quote(copy.sessionName));
        builder.append(",\"startedAtUtc\":").append(AtlasJson.quote(copy.startedAtUtc));
        builder.append(",\"stoppedAtUtc\":").append(AtlasJson.quote(copy.stoppedAtUtc));
        builder.append(",\"activeAtSave\":").append(copy.activeAtSave);
        builder.append(",\"eventCount\":").append(copy.events.size());
        builder.append(",\"droppedCount\":").append(copy.droppedCount);
        builder.append(",\"suppressedCount\":").append(copy.suppressedCount);
        builder.append(",\"maxEvents\":").append(MAX_EVENTS);
        builder.append(",\"clientFingerprint\":").append(AtlasJson.quote(fingerprint));
        builder.append('}');
        return builder.toString();
    }

    private static String eventJson(TraceEvent event) {
        StringBuilder builder = new StringBuilder(384);
        builder.append('{');
        builder.append("\"recordType\":\"event\"");
        builder.append(",\"sequence\":").append(event.sequence);
        builder.append(",\"timestampUtc\":").append(AtlasJson.quote(event.timestampUtc));
        builder.append(",\"category\":").append(AtlasJson.quote(event.category));
        builder.append(",\"eventType\":").append(AtlasJson.quote(event.eventType));
        builder.append(",\"sourceId\":").append(AtlasJson.quote(event.sourceId));
        builder.append(",\"thread\":").append(AtlasJson.quote(event.threadName));
        builder.append(",\"fields\":{");
        for (int i = 0; i + 1 < event.fields.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(AtlasJson.quote(event.fields[i]));
            builder.append(':').append(AtlasJson.quote(event.fields[i + 1]));
        }
        builder.append("}}");
        return builder.toString();
    }

    private static String[] sanitizeFields(String[] fields) {
        if (fields == null || fields.length < 2) {
            return new String[0];
        }
        int pairCount = Math.min(fields.length / 2, 12);
        String[] safe = new String[pairCount * 2];
        for (int i = 0; i < pairCount; i++) {
            String key = fields[i * 2];
            String value = fields[i * 2 + 1];
            safe[i * 2] = limit(key == null ? "field" + i : key, 48);
            safe[i * 2 + 1] = limit(value == null ? "" : value, 180);
        }
        return safe;
    }

    private static String normalizeSessionName(String value) {
        String normalized = value == null ? "trace" : value.trim().replaceAll("[\\r\\n\\t]+", " ");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized.length() == 0 ? "trace" : normalized;
    }

    private static String safeFileName(String value) {
        String safe = normalizeSessionName(value).replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.length() > 60) {
            safe = safe.substring(0, 60);
        }
        return safe.length() == 0 ? "trace" : safe;
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String safeError(Throwable ex) {
        if (ex == null) {
            return "";
        }
        String message = ex.getMessage();
        if (message == null || message.length() == 0) {
            message = ex.getClass().getSimpleName();
        }
        return limit(message.replaceAll("[\\r\\n]+", " "), 240);
    }

    public static final class Snapshot {
        private final boolean active;
        private final String sessionName;
        private final long eventCount;
        private final long droppedCount;
        private final long suppressedCount;
        private final String startedAtUtc;
        private final String stoppedAtUtc;
        private final String lastSavedPath;

        private Snapshot(boolean active, String sessionName, long eventCount, long droppedCount,
                long suppressedCount, String startedAtUtc, String stoppedAtUtc, String lastSavedPath) {
            this.active = active;
            this.sessionName = sessionName;
            this.eventCount = eventCount;
            this.droppedCount = droppedCount;
            this.suppressedCount = suppressedCount;
            this.startedAtUtc = startedAtUtc;
            this.stoppedAtUtc = stoppedAtUtc;
            this.lastSavedPath = lastSavedPath;
        }

        public boolean isActive() {
            return active;
        }

        public String getSessionName() {
            return sessionName;
        }

        public long getEventCount() {
            return eventCount;
        }

        public long getDroppedCount() {
            return droppedCount;
        }

        public long getSuppressedCount() {
            return suppressedCount;
        }

        public String getStartedAtUtc() {
            return startedAtUtc;
        }

        public String getStoppedAtUtc() {
            return stoppedAtUtc;
        }

        public String getLastSavedPath() {
            return lastSavedPath;
        }
    }

    private static final class TraceEvent {
        private final long sequence;
        private final String timestampUtc;
        private final String category;
        private final String eventType;
        private final String sourceId;
        private final String threadName;
        private final String[] fields;

        private TraceEvent(long sequence, String timestampUtc, String category, String eventType,
                String sourceId, String threadName, String[] fields) {
            this.sequence = sequence;
            this.timestampUtc = timestampUtc;
            this.category = category;
            this.eventType = eventType;
            this.sourceId = sourceId;
            this.threadName = threadName;
            this.fields = fields;
        }
    }

    private static final class SessionCopy {
        private final boolean activeAtSave;
        private final String sessionName;
        private final String startedAtUtc;
        private final String stoppedAtUtc;
        private final long droppedCount;
        private final long suppressedCount;
        private final List<TraceEvent> events;

        private SessionCopy(boolean activeAtSave, String sessionName, String startedAtUtc,
                String stoppedAtUtc, long droppedCount, long suppressedCount, List<TraceEvent> events) {
            this.activeAtSave = activeAtSave;
            this.sessionName = sessionName;
            this.startedAtUtc = startedAtUtc;
            this.stoppedAtUtc = stoppedAtUtc;
            this.droppedCount = droppedCount;
            this.suppressedCount = suppressedCount;
            this.events = events;
        }
    }
}
