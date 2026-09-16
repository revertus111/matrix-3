package game.atlas;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Properties;

/**
 * File-backed control/status protocol between the standalone Atlas tooling JVM
 * and the running Matrix3 client JVM.
 */
public final class AtlasTraceControl {

    public static final String CONTROL_FILE = "trace-control.properties";
    public static final String STATUS_FILE = "trace-status.properties";

    private static final long REQUEST_FRESH_MILLIS = 300000L;
    private static final long RUNTIME_FRESH_MILLIS = 5000L;

    public enum Command {
        START,
        STOP,
        SAVE,
        STOP_SAVE
    }

    private AtlasTraceControl() {
    }

    public static String queue(AtlasWorkspace workspace, Command command, String sessionName) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        workspace.ensureLayout();

        String requestId = Long.toString(System.currentTimeMillis()) + "-" + Long.toHexString(System.nanoTime());
        Properties properties = new Properties();
        properties.setProperty("requestId", requestId);
        properties.setProperty("requestedAtUtc", Instant.now().toString());
        properties.setProperty("command", command.name());
        if (sessionName != null && sessionName.trim().length() > 0) {
            properties.setProperty("sessionName", normalizeSessionName(sessionName));
        }
        writePropertiesAtomically(controlFile(workspace), properties,
                "Client Atlas trace control - generated");
        return requestId;
    }

    static ControlRequest readRequest(AtlasWorkspace workspace) throws IOException {
        Path path = controlFile(workspace);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        Properties properties = readProperties(path);
        String requestId = properties.getProperty("requestId", "").trim();
        String requestedAtUtc = properties.getProperty("requestedAtUtc", "").trim();
        String commandText = properties.getProperty("command", "").trim();
        if (requestId.length() == 0 || requestedAtUtc.length() == 0 || commandText.length() == 0) {
            return null;
        }
        try {
            Command command = Command.valueOf(commandText);
            return new ControlRequest(requestId, requestedAtUtc, command,
                    properties.getProperty("sessionName"));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid Client Atlas trace command: " + commandText, ex);
        }
    }

    public static RuntimeStatus readRuntimeStatus(AtlasWorkspace workspace) throws IOException {
        Path path = statusFile(workspace);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        Properties properties = readProperties(path);
        return new RuntimeStatus(
                properties.getProperty("heartbeatUtc"),
                properties.getProperty("lastRequestId"),
                Boolean.parseBoolean(properties.getProperty("active", "false")),
                properties.getProperty("sessionName"),
                parseLong(properties, "eventCount"),
                parseLong(properties, "droppedCount"),
                parseLong(properties, "suppressedCount"),
                properties.getProperty("startedAtUtc"),
                properties.getProperty("stoppedAtUtc"),
                properties.getProperty("lastSavedPath"),
                properties.getProperty("lastError"));
    }

    static void writeRuntimeStatus(AtlasWorkspace workspace, String lastRequestId,
            AtlasTraceRecorder.Snapshot snapshot, String lastError) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("heartbeatUtc", Instant.now().toString());
        properties.setProperty("lastRequestId", nullToEmpty(lastRequestId));
        properties.setProperty("active", Boolean.toString(snapshot.isActive()));
        properties.setProperty("sessionName", nullToEmpty(snapshot.getSessionName()));
        properties.setProperty("eventCount", Long.toString(snapshot.getEventCount()));
        properties.setProperty("droppedCount", Long.toString(snapshot.getDroppedCount()));
        properties.setProperty("suppressedCount", Long.toString(snapshot.getSuppressedCount()));
        properties.setProperty("startedAtUtc", nullToEmpty(snapshot.getStartedAtUtc()));
        properties.setProperty("stoppedAtUtc", nullToEmpty(snapshot.getStoppedAtUtc()));
        properties.setProperty("lastSavedPath", nullToEmpty(snapshot.getLastSavedPath()));
        properties.setProperty("lastError", nullToEmpty(lastError));
        writePropertiesAtomically(statusFile(workspace), properties,
                "Client Atlas trace runtime status - generated");
    }

    static Path controlFile(AtlasWorkspace workspace) {
        return workspace.getWorkspaceRoot().resolve(CONTROL_FILE);
    }

    static Path statusFile(AtlasWorkspace workspace) {
        return workspace.getWorkspaceRoot().resolve(STATUS_FILE);
    }

    private static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writePropertiesAtomically(Path path, Properties properties, String comment)
            throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, comment);
        }
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long parseLong(Properties properties, String name) {
        try {
            return Long.parseLong(properties.getProperty(name, "0"));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String normalizeSessionName(String value) {
        String normalized = value.trim().replaceAll("[\\r\\n\\t]+", " ");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized.length() == 0 ? "trace" : normalized;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static final class ControlRequest {
        private final String requestId;
        private final String requestedAtUtc;
        private final Command command;
        private final String sessionName;

        private ControlRequest(String requestId, String requestedAtUtc, Command command, String sessionName) {
            this.requestId = requestId;
            this.requestedAtUtc = requestedAtUtc;
            this.command = command;
            this.sessionName = sessionName;
        }

        String getRequestId() {
            return requestId;
        }

        Command getCommand() {
            return command;
        }

        String getSessionName() {
            return sessionName;
        }

        boolean isFresh() {
            try {
                long age = Math.abs(Instant.now().toEpochMilli() - Instant.parse(requestedAtUtc).toEpochMilli());
                return age <= REQUEST_FRESH_MILLIS;
            } catch (RuntimeException ex) {
                return false;
            }
        }
    }

    public static final class RuntimeStatus {
        private final String heartbeatUtc;
        private final String lastRequestId;
        private final boolean active;
        private final String sessionName;
        private final long eventCount;
        private final long droppedCount;
        private final long suppressedCount;
        private final String startedAtUtc;
        private final String stoppedAtUtc;
        private final String lastSavedPath;
        private final String lastError;

        private RuntimeStatus(String heartbeatUtc, String lastRequestId, boolean active,
                String sessionName, long eventCount, long droppedCount, long suppressedCount,
                String startedAtUtc, String stoppedAtUtc, String lastSavedPath, String lastError) {
            this.heartbeatUtc = heartbeatUtc;
            this.lastRequestId = lastRequestId;
            this.active = active;
            this.sessionName = sessionName;
            this.eventCount = eventCount;
            this.droppedCount = droppedCount;
            this.suppressedCount = suppressedCount;
            this.startedAtUtc = startedAtUtc;
            this.stoppedAtUtc = stoppedAtUtc;
            this.lastSavedPath = lastSavedPath;
            this.lastError = lastError;
        }

        public boolean isRuntimePresent() {
            if (heartbeatUtc == null || heartbeatUtc.length() == 0) {
                return false;
            }
            try {
                long age = Math.abs(Instant.now().toEpochMilli() - Instant.parse(heartbeatUtc).toEpochMilli());
                return age <= RUNTIME_FRESH_MILLIS;
            } catch (RuntimeException ex) {
                return false;
            }
        }

        public String getHeartbeatUtc() {
            return heartbeatUtc;
        }

        public String getLastRequestId() {
            return lastRequestId;
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

        public String getLastError() {
            return lastError;
        }
    }
}
