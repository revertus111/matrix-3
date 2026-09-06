package game.console.bosslabs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import game.ClientConsoleBridge;

/** Client-side transport for BossLabs drop authoring over the existing bridge. */
public final class BossLabsDropClientBridge {

    private static final String RESPONSE_PREFIX = "bosslabs|";
    private static final int PUBLISH_CHUNK_LENGTH = 180;
    private static final int MAX_PUBLISH_CHUNKS = 32;
    private static final int INSPECT_TIMEOUT_MS = 5000;
    private static final AtomicInteger REQUEST_SEQUENCE = new AtomicInteger(500000);
    private static final Map<Integer, Download> DOWNLOADS = new ConcurrentHashMap<Integer, Download>();
    private static final Set<Integer> PENDING_INSPECTIONS =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private static volatile Listener listener;

    private BossLabsDropClientBridge() {
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    public static void clearListener(Listener oldListener) {
        if (listener == oldListener)
            listener = null;
        DOWNLOADS.clear();
        PENDING_INSPECTIONS.clear();
    }

    public static int requestInspect(final int npcId) {
        final int requestId = nextRequestId();
        String error = ClientConsoleBridge.queueConsoleCommand("bosslabs drops inspect " + requestId + " " + npcId);
        if (error != null) {
            dispatchFailure(requestId, npcId, "Drop inspect request failed: " + error);
            return requestId;
        }
        PENDING_INSPECTIONS.add(Integer.valueOf(requestId));
        Timer timeout = new Timer(INSPECT_TIMEOUT_MS, e -> {
            if (PENDING_INSPECTIONS.remove(Integer.valueOf(requestId))) {
                DOWNLOADS.remove(Integer.valueOf(requestId));
                dispatchFailure(requestId, npcId,
                        "Drop inspection timed out. Reload Current to retry; check the Server console if it repeats.");
            }
        });
        timeout.setRepeats(false);
        timeout.start();
        return requestId;
    }

    public static int requestPublish(BossLabsDropDraftDefinition draft, boolean save) {
        int requestId = nextRequestId();
        if (draft == null) {
            dispatchFailure(requestId, -1, "No drop draft is loaded.");
            return requestId;
        }
        String validation = draft.validate();
        if (validation != null) {
            dispatchFailure(requestId, draft.getNpcId(), validation);
            return requestId;
        }

        String payload;
        try {
            payload = draft.toPayload();
        } catch (RuntimeException e) {
            dispatchFailure(requestId, draft.getNpcId(), safeMessage(e));
            return requestId;
        }

        int chunkCount = (payload.length() + PUBLISH_CHUNK_LENGTH - 1) / PUBLISH_CHUNK_LENGTH;
        if (chunkCount <= 0 || chunkCount > MAX_PUBLISH_CHUNKS) {
            dispatchFailure(requestId, draft.getNpcId(), "BossLabs drop draft is too large for one publish transaction.");
            return requestId;
        }

        String[] commands = new String[chunkCount + 2];
        commands[0] = "bosslabs drops uploadbegin " + requestId + " " + (save ? 1 : 0) + " " + chunkCount;
        for (int index = 0; index < chunkCount; index++) {
            int start = index * PUBLISH_CHUNK_LENGTH;
            int end = Math.min(payload.length(), start + PUBLISH_CHUNK_LENGTH);
            commands[index + 1] = "bosslabs drops uploadchunk " + requestId + " " + index + " "
                    + payload.substring(start, end);
        }
        commands[commands.length - 1] = "bosslabs drops uploadcommit " + requestId;

        String error = ClientConsoleBridge.queueConsoleCommands(commands);
        if (error != null)
            dispatchFailure(requestId, draft.getNpcId(), "Drop publish request failed: " + error);
        return requestId;
    }

    public static int requestApplySaved(int npcId) {
        return requestSimple("applysaved", npcId, "Apply Saved Drops request failed");
    }

    public static int requestUndo(int npcId) {
        return requestSimple("undo", npcId, "Undo Drops request failed");
    }

    public static int requestRestoreMatrix3(int npcId) {
        return requestSimple("restore", npcId, "Restore Matrix3 Drops request failed");
    }

    public static int requestDeleteSaved(int npcId) {
        return requestSimple("deletesaved", npcId, "Delete Saved Drops request failed");
    }

    private static int requestSimple(String operation, int npcId, String failure) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs drops " + operation + " " + requestId + " " + npcId, failure);
        return requestId;
    }

    public static boolean handleServerCommand(String command) {
        if (command == null || !command.startsWith(RESPONSE_PREFIX))
            return false;
        String[] parts = command.split("\\|", -1);
        if (parts.length < 2 || !parts[1].startsWith("drop-"))
            return false;

        try {
            String type = parts[1];
            if ("drop-begin".equals(type) && parts.length >= 8) {
                int requestId = parseInt(parts[2]);
                int npcId = parseInt(parts[3]);
                String source = parts[4];
                boolean saved = parseBoolean(parts[5]);
                boolean rollback = parseBoolean(parts[6]);
                int chunkCount = parseInt(parts[7]);
                if (chunkCount <= 0 || chunkCount > 256)
                    throw new IllegalArgumentException("Invalid BossLabs drop response chunk count.");
                DOWNLOADS.put(Integer.valueOf(requestId), new Download(npcId, source, saved, rollback, chunkCount));
            } else if ("drop-chunk".equals(type) && parts.length >= 6) {
                int requestId = parseInt(parts[2]);
                int npcId = parseInt(parts[3]);
                int index = parseInt(parts[4]);
                Download download = DOWNLOADS.get(Integer.valueOf(requestId));
                if (download != null && download.npcId == npcId)
                    download.setChunk(index, parts[5]);
            } else if ("drop-end".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final int npcId = parseInt(parts[3]);
                Download download = DOWNLOADS.remove(Integer.valueOf(requestId));
                PENDING_INSPECTIONS.remove(Integer.valueOf(requestId));
                if (download == null || download.npcId != npcId)
                    throw new IllegalArgumentException("BossLabs drop response is incomplete.");
                final BossLabsDropDraftDefinition draft = BossLabsDropDraftDefinition.fromPayload(download.join());
                if (draft.getNpcId() != npcId)
                    throw new IllegalArgumentException("BossLabs drop response NPC id mismatch.");
                final DropState state = new DropState(requestId, npcId, download.source, download.saved,
                        download.rollback, draft);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onDropState(state);
                    }
                });
            } else if ("drop-action".equals(type) && parts.length >= 6) {
                final int requestId = parseInt(parts[2]);
                final boolean success = parseBoolean(parts[3]);
                final int npcId = parseInt(parts[4]);
                if (!success) {
                    PENDING_INSPECTIONS.remove(Integer.valueOf(requestId));
                    DOWNLOADS.remove(Integer.valueOf(requestId));
                }
                final DropActionResult result = new DropActionResult(requestId, success, npcId, decode(parts[5]));
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onDropActionResult(result);
                    }
                });
            }
        } catch (RuntimeException e) {
            System.err.println("BossLabs failed to decode drop response: " + command);
            e.printStackTrace();
        }
        return true;
    }

    private static void queue(int requestId, String command, String failureLabel) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null)
            dispatchFailure(requestId, -1, failureLabel + ": " + error);
    }

    private static void dispatchFailure(final int requestId, final int npcId, String message) {
        final DropActionResult result = new DropActionResult(requestId, false, npcId, message);
        dispatch(new ListenerCall() {
            @Override
            public void call(Listener target) {
                target.onDropActionResult(result);
            }
        });
    }

    private static int nextRequestId() {
        int value = REQUEST_SEQUENCE.getAndIncrement();
        if (value > 0)
            return value;
        REQUEST_SEQUENCE.set(500001);
        return 500000;
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value);
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static String decode(String value) {
        if (value == null || value.length() == 0)
            return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null)
            return "BossLabs drops request failed.";
        String message = throwable.getMessage();
        return message == null || message.trim().length() == 0 ? throwable.getClass().getSimpleName() : message;
    }

    private static void dispatch(final ListenerCall call) {
        if (call == null || listener == null)
            return;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Listener target = listener;
                if (target != null)
                    call.call(target);
            }
        };
        if (SwingUtilities.isEventDispatchThread())
            runnable.run();
        else
            SwingUtilities.invokeLater(runnable);
    }

    private interface ListenerCall {
        void call(Listener target);
    }

    public interface Listener {
        void onDropState(DropState state);
        void onDropActionResult(DropActionResult result);
    }

    public static final class DropState {
        private final int requestId;
        private final int npcId;
        private final String source;
        private final boolean saved;
        private final boolean rollbackAvailable;
        private final BossLabsDropDraftDefinition draft;

        private DropState(int requestId, int npcId, String source, boolean saved, boolean rollbackAvailable,
                BossLabsDropDraftDefinition draft) {
            this.requestId = requestId;
            this.npcId = npcId;
            this.source = source;
            this.saved = saved;
            this.rollbackAvailable = rollbackAvailable;
            this.draft = draft;
        }

        public int getRequestId() { return requestId; }
        public int getNpcId() { return npcId; }
        public String getSource() { return source; }
        public boolean isSaved() { return saved; }
        public boolean isRollbackAvailable() { return rollbackAvailable; }
        public BossLabsDropDraftDefinition getDraft() { return draft; }
    }

    public static final class DropActionResult {
        private final int requestId;
        private final boolean success;
        private final int npcId;
        private final String message;

        private DropActionResult(int requestId, boolean success, int npcId, String message) {
            this.requestId = requestId;
            this.success = success;
            this.npcId = npcId;
            this.message = message;
        }

        public int getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public int getNpcId() { return npcId; }
        public String getMessage() { return message; }
    }

    private static final class Download {
        private final int npcId;
        private final String source;
        private final boolean saved;
        private final boolean rollback;
        private final String[] chunks;

        private Download(int npcId, String source, boolean saved, boolean rollback, int chunkCount) {
            this.npcId = npcId;
            this.source = source;
            this.saved = saved;
            this.rollback = rollback;
            this.chunks = new String[chunkCount];
        }

        private void setChunk(int index, String value) {
            if (index < 0 || index >= chunks.length)
                throw new IllegalArgumentException("BossLabs drop response chunk index is invalid.");
            chunks[index] = value;
        }

        private String join() {
            StringBuilder builder = new StringBuilder();
            for (String chunk : chunks) {
                if (chunk == null)
                    throw new IllegalArgumentException("BossLabs drop response is missing a chunk.");
                builder.append(chunk);
            }
            return builder.toString();
        }
    }
}
