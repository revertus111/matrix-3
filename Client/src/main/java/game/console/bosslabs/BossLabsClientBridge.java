package game.console.bosslabs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import game.ClientConsoleBridge;

/**
 * Client-side BossLabs transport adapter using Matrix3's existing console
 * command packet for requests and type-99 panel messages for replies.
 */
public final class BossLabsClientBridge {

    private static final String RESPONSE_PREFIX = "bosslabs|";
    private static final int PUBLISH_CHUNK_LENGTH = 180;
    private static final int MAX_PUBLISH_CHUNKS = 28;
    private static final AtomicInteger REQUEST_SEQUENCE = new AtomicInteger(1);
    private static final Map<Integer, DefinitionDownload> DEFINITION_DOWNLOADS =
            new ConcurrentHashMap<Integer, DefinitionDownload>();
    private static final Set<Integer> TESTING_REQUESTS =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private static volatile Listener listener;
    private static volatile TestingListener testingListener;

    private BossLabsClientBridge() {
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    public static void clearListener(Listener oldListener) {
        if (listener == oldListener) {
            listener = null;
        }
        DEFINITION_DOWNLOADS.clear();
    }

    public static void setTestingListener(TestingListener newListener) {
        testingListener = newListener;
    }

    public static void clearTestingListener(TestingListener oldListener) {
        if (testingListener == oldListener)
            testingListener = null;
        TESTING_REQUESTS.clear();
    }

    public static int requestSearch(String query) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs search " + requestId + " " + encode(query), "Search request failed");
        return requestId;
    }

    public static int requestInspect(int npcId) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs inspect " + requestId + " " + npcId, "Inspect request failed");
        return requestId;
    }

    public static int requestPublishDefinition(BossLabsDraftDefinition definition, boolean save) {
        int requestId = nextRequestId();
        if (definition == null) {
            dispatchFailure(requestId, "No BossLabs draft is loaded.");
            return requestId;
        }
        String validation = definition.validate();
        if (validation != null) {
            dispatchFailure(requestId, validation);
            return requestId;
        }

        String payload;
        try {
            payload = definition.toPayload();
        } catch (RuntimeException e) {
            dispatchFailure(requestId, e.getMessage() == null ? "Unable to encode BossLabs draft." : e.getMessage());
            return requestId;
        }

        int chunkCount = (payload.length() + PUBLISH_CHUNK_LENGTH - 1) / PUBLISH_CHUNK_LENGTH;
        if (chunkCount <= 0 || chunkCount > MAX_PUBLISH_CHUNKS) {
            dispatchFailure(requestId, "BossLabs draft is too large for one publish transaction.");
            return requestId;
        }

        String[] commands = new String[chunkCount + 2];
        commands[0] = "bosslabs uploadbegin " + requestId + " " + (save ? 1 : 0) + " " + chunkCount;
        for (int index = 0; index < chunkCount; index++) {
            int start = index * PUBLISH_CHUNK_LENGTH;
            int end = Math.min(payload.length(), start + PUBLISH_CHUNK_LENGTH);
            commands[index + 1] = "bosslabs uploadchunk " + requestId + " " + index + " " + payload.substring(start, end);
        }
        commands[commands.length - 1] = "bosslabs uploadcommit " + requestId;

        String error = ClientConsoleBridge.queueConsoleCommands(commands);
        if (error != null)
            dispatchFailure(requestId, "Publish request failed: " + error);
        return requestId;
    }

    /**
     * Legacy identity-only requests retained for compatibility with the prior
     * bridge checkpoint. New editor publishing uses requestPublishDefinition().
     */
    public static int requestApplyLive(int npcId, String definitionId, String displayName) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs apply " + requestId + " " + npcId + " " + encode(definitionId) + " "
                + encode(displayName), "Apply Live request failed");
        return requestId;
    }

    public static int requestSaveAndApply(int npcId, String definitionId, String displayName) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs saveapply " + requestId + " " + npcId + " " + encode(definitionId) + " "
                + encode(displayName), "Save & Apply request failed");
        return requestId;
    }

    public static int requestUndo(int npcId) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs undo " + requestId + " " + npcId, "Undo request failed");
        return requestId;
    }

    public static int requestApplySaved(int npcId) {
        int requestId = nextRequestId();
        queue(requestId, "bosslabs applysaved " + requestId + " " + npcId, "Apply Saved request failed");
        return requestId;
    }

    public static int requestTestingSpawn(int npcId) {
        return requestTesting("spawn", npcId, null, null);
    }

    public static int requestTestingReset(int npcId) {
        return requestTesting("reset", npcId, null, null);
    }

    public static int requestTestingSetHealth(int npcId, int percent) {
        int requestId = nextRequestId();
        TESTING_REQUESTS.add(Integer.valueOf(requestId));
        queue(requestId, "bosslabs testing " + requestId + " sethp " + npcId + " " + percent,
                "Set Boss HP request failed");
        return requestId;
    }

    public static int requestTestingForcePhase(int npcId, String phaseId) {
        return requestTesting("forcephase", npcId, phaseId, null);
    }

    public static int requestTestingForceAttack(int npcId, String phaseId, String attackId) {
        return requestTesting("forceattack", npcId, phaseId, attackId);
    }

    public static int requestTestingClearHazards(int npcId) {
        return requestTesting("clearhazards", npcId, null, null);
    }

    public static int requestTestingClearMinions(int npcId) {
        return requestTesting("clearminions", npcId, null, null);
    }

    private static int requestTesting(String operation, int npcId, String firstText, String secondText) {
        int requestId = nextRequestId();
        TESTING_REQUESTS.add(Integer.valueOf(requestId));
        StringBuilder command = new StringBuilder("bosslabs testing ").append(requestId).append(' ')
                .append(operation).append(' ').append(npcId);
        if (firstText != null)
            command.append(' ').append(encode(firstText));
        if (secondText != null)
            command.append(' ').append(encode(secondText));
        queue(requestId, command.toString(), "BossLabs testing request failed");
        return requestId;
    }

    public static boolean handleServerCommand(String command) {
        if (command == null || !command.startsWith(RESPONSE_PREFIX)) {
            return false;
        }

        try {
            String[] parts = command.split("\\|", -1);
            if (parts.length < 2) {
                return true;
            }
            String type = parts[1];
            if ("search-begin".equals(type) && parts.length >= 3) {
                final int requestId = parseInt(parts[2]);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onSearchStarted(requestId);
                    }
                });
            } else if ("search-result".equals(type) && parts.length >= 8) {
                final int requestId = parseInt(parts[2]);
                final SearchResult result = new SearchResult(parseInt(parts[3]), decode(parts[4]), parseInt(parts[5]),
                        parts[6], parseBoolean(parts[7]));
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onSearchResult(requestId, result);
                    }
                });
            } else if ("search-end".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final int count = parseInt(parts[3]);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onSearchFinished(requestId, count);
                    }
                });
            } else if ("search-error".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final String message = decode(parts[3]);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onSearchError(requestId, message);
                    }
                });
            } else if ("inspect".equals(type) && parts.length >= 19) {
                final int requestId = parseInt(parts[2]);
                final Inspection inspection = new Inspection(parseInt(parts[3]), decode(parts[4]), parseInt(parts[5]),
                        parseInt(parts[6]), parseInt(parts[7]), parseInt(parts[8]), parseInt(parts[9]), parseInt(parts[10]),
                        parseInt(parts[11]), parseInt(parts[12]), parseInt(parts[13]), parseInt(parts[14]),
                        parseBoolean(parts[15]), parseInt(parts[16]), parseBoolean(parts[17]), parts[18]);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onInspection(requestId, inspection);
                    }
                });
                dispatchTestingSelection(inspection.getNpcId(), false);
            } else if ("ownership".equals(type) && parts.length >= 10) {
                final int requestId = parseInt(parts[2]);
                final Ownership ownership = new Ownership(parseInt(parts[3]), decode(parts[4]), parseBoolean(parts[5]),
                        decode(parts[6]), decode(parts[7]), parseBoolean(parts[8]), parseBoolean(parts[9]));
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onOwnership(requestId, ownership);
                    }
                });
                dispatchTestingSelection(ownership.getNpcId(), ownership.isBossLabsDefinition());
            } else if ("definition-empty".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final int npcId = parseInt(parts[3]);
                DEFINITION_DOWNLOADS.remove(Integer.valueOf(requestId));
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onDefinitionEmpty(requestId, npcId);
                    }
                });
                dispatchTestingSelection(npcId, false);
            } else if ("definition-begin".equals(type) && parts.length >= 5) {
                int requestId = parseInt(parts[2]);
                int npcId = parseInt(parts[3]);
                int chunkCount = parseInt(parts[4]);
                if (chunkCount <= 0 || chunkCount > 256)
                    throw new IllegalArgumentException("Invalid BossLabs definition response chunk count.");
                DEFINITION_DOWNLOADS.put(Integer.valueOf(requestId), new DefinitionDownload(npcId, chunkCount));
            } else if ("definition-chunk".equals(type) && parts.length >= 6) {
                int requestId = parseInt(parts[2]);
                int npcId = parseInt(parts[3]);
                int index = parseInt(parts[4]);
                DefinitionDownload download = DEFINITION_DOWNLOADS.get(Integer.valueOf(requestId));
                if (download != null && download.npcId == npcId)
                    download.setChunk(index, parts[5]);
            } else if ("definition-end".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final int npcId = parseInt(parts[3]);
                DefinitionDownload download = DEFINITION_DOWNLOADS.remove(Integer.valueOf(requestId));
                if (download == null || download.npcId != npcId)
                    throw new IllegalArgumentException("BossLabs definition response is incomplete.");
                final BossLabsDraftDefinition definition = BossLabsDraftDefinition.fromPayload(download.join());
                if (definition.getNpcId() != npcId)
                    throw new IllegalArgumentException("BossLabs definition response NPC id mismatch.");
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onDefinitionLoaded(requestId, definition);
                    }
                });
                dispatchTestingSelection(npcId, true);
            } else if ("inspect-missing".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final int npcId = parseInt(parts[3]);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onInspectionMissing(requestId, npcId);
                    }
                });
                dispatchTestingCleared();
            } else if ("action".equals(type) && parts.length >= 6) {
                final ActionResult result = new ActionResult(parseInt(parts[2]), parseBoolean(parts[3]),
                        parseInt(parts[4]), decode(parts[5]));
                if (TESTING_REQUESTS.remove(Integer.valueOf(result.getRequestId()))) {
                    dispatchTestingAction(result);
                } else {
                    dispatch(new ListenerCall() {
                        @Override
                        public void call(Listener target) {
                            target.onActionResult(result);
                        }
                    });
                }
            }
        } catch (RuntimeException ex) {
            System.err.println("BossLabs failed to decode server response: " + command);
            ex.printStackTrace();
        }
        return true;
    }

    private static void queue(final int requestId, String command, String failureLabel) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null)
            dispatchFailure(requestId, failureLabel + ": " + error);
    }

    private static void dispatchFailure(final int requestId, String message) {
        final ActionResult result = new ActionResult(requestId, false, -1, message);
        if (TESTING_REQUESTS.remove(Integer.valueOf(requestId))) {
            dispatchTestingAction(result);
            return;
        }
        dispatch(new ListenerCall() {
            @Override
            public void call(Listener target) {
                target.onActionResult(result);
            }
        });
    }

    private static int nextRequestId() {
        int value = REQUEST_SEQUENCE.getAndIncrement();
        if (value > 0) {
            return value;
        }
        REQUEST_SEQUENCE.set(2);
        return 1;
    }

    private static String encode(String value) {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value);
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static void dispatch(final ListenerCall call) {
        if (call == null || listener == null) {
            return;
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Listener target = listener;
                if (target != null) {
                    call.call(target);
                }
            }
        };
        dispatchOnEdt(runnable);
    }

    private static void dispatchTestingSelection(final int npcId, final boolean liveBossLabs) {
        dispatchTesting(new TestingListenerCall() {
            @Override
            public void call(TestingListener target) {
                target.onTestingSelection(npcId, liveBossLabs);
            }
        });
    }

    private static void dispatchTestingCleared() {
        dispatchTesting(new TestingListenerCall() {
            @Override
            public void call(TestingListener target) {
                target.onTestingSelectionCleared();
            }
        });
    }

    private static void dispatchTestingAction(final ActionResult result) {
        dispatchTesting(new TestingListenerCall() {
            @Override
            public void call(TestingListener target) {
                target.onTestingActionResult(result);
            }
        });
    }

    private static void dispatchTesting(final TestingListenerCall call) {
        if (call == null || testingListener == null)
            return;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                TestingListener target = testingListener;
                if (target != null)
                    call.call(target);
            }
        };
        dispatchOnEdt(runnable);
    }

    private static void dispatchOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private interface ListenerCall {
        void call(Listener target);
    }

    private interface TestingListenerCall {
        void call(TestingListener target);
    }

    public interface Listener {
        void onSearchStarted(int requestId);
        void onSearchResult(int requestId, SearchResult result);
        void onSearchFinished(int requestId, int count);
        void onSearchError(int requestId, String message);
        void onInspection(int requestId, Inspection inspection);
        void onOwnership(int requestId, Ownership ownership);
        void onDefinitionLoaded(int requestId, BossLabsDraftDefinition definition);
        void onDefinitionEmpty(int requestId, int npcId);
        void onInspectionMissing(int requestId, int npcId);
        void onActionResult(ActionResult result);
    }

    public interface TestingListener {
        void onTestingSelection(int npcId, boolean liveBossLabsDefinition);
        void onTestingSelectionCleared();
        void onTestingActionResult(ActionResult result);
    }

    public static final class SearchResult {
        private final int npcId;
        private final String name;
        private final int combatLevel;
        private final String combatSource;
        private final boolean bossLabsDefinition;

        private SearchResult(int npcId, String name, int combatLevel, String combatSource, boolean bossLabsDefinition) {
            this.npcId = npcId;
            this.name = name;
            this.combatLevel = combatLevel;
            this.combatSource = combatSource;
            this.bossLabsDefinition = bossLabsDefinition;
        }

        public int getNpcId() { return npcId; }
        public String getName() { return name; }
        public int getCombatLevel() { return combatLevel; }
        public String getCombatSource() { return combatSource; }
        public boolean isBossLabsDefinition() { return bossLabsDefinition; }

        @Override
        public String toString() {
            return name + "  [" + npcId + "]  lvl " + combatLevel + (bossLabsDefinition ? "  BossLabs" : "");
        }
    }

    public static final class Inspection {
        private final int npcId;
        private final String name;
        private final int combatLevel;
        private final int size;
        private final int hitpoints;
        private final int attackSpeed;
        private final int attackAnimation;
        private final int defenceAnimation;
        private final int deathAnimation;
        private final int respawnDelay;
        private final int attackGraphic;
        private final int attackProjectile;
        private final boolean aggressive;
        private final int aggressionRange;
        private final boolean poisonImmune;
        private final String combatSource;

        private Inspection(int npcId, String name, int combatLevel, int size, int hitpoints, int attackSpeed,
                int attackAnimation, int defenceAnimation, int deathAnimation, int respawnDelay, int attackGraphic,
                int attackProjectile, boolean aggressive, int aggressionRange, boolean poisonImmune, String combatSource) {
            this.npcId = npcId;
            this.name = name;
            this.combatLevel = combatLevel;
            this.size = size;
            this.hitpoints = hitpoints;
            this.attackSpeed = attackSpeed;
            this.attackAnimation = attackAnimation;
            this.defenceAnimation = defenceAnimation;
            this.deathAnimation = deathAnimation;
            this.respawnDelay = respawnDelay;
            this.attackGraphic = attackGraphic;
            this.attackProjectile = attackProjectile;
            this.aggressive = aggressive;
            this.aggressionRange = aggressionRange;
            this.poisonImmune = poisonImmune;
            this.combatSource = combatSource;
        }

        public int getNpcId() { return npcId; }
        public String getName() { return name; }
        public int getCombatLevel() { return combatLevel; }
        public int getSize() { return size; }
        public int getHitpoints() { return hitpoints; }
        public int getAttackSpeed() { return attackSpeed; }
        public int getAttackAnimation() { return attackAnimation; }
        public int getDefenceAnimation() { return defenceAnimation; }
        public int getDeathAnimation() { return deathAnimation; }
        public int getRespawnDelay() { return respawnDelay; }
        public int getAttackGraphic() { return attackGraphic; }
        public int getAttackProjectile() { return attackProjectile; }
        public boolean isAggressive() { return aggressive; }
        public int getAggressionRange() { return aggressionRange; }
        public boolean isPoisonImmune() { return poisonImmune; }
        public String getCombatSource() { return combatSource; }
    }

    public static final class Ownership {
        private final int npcId;
        private final String scriptName;
        private final boolean bossLabsDefinition;
        private final String definitionId;
        private final String displayName;
        private final boolean saved;
        private final boolean rollbackAvailable;

        private Ownership(int npcId, String scriptName, boolean bossLabsDefinition, String definitionId,
                String displayName, boolean saved, boolean rollbackAvailable) {
            this.npcId = npcId;
            this.scriptName = scriptName;
            this.bossLabsDefinition = bossLabsDefinition;
            this.definitionId = definitionId;
            this.displayName = displayName;
            this.saved = saved;
            this.rollbackAvailable = rollbackAvailable;
        }

        public int getNpcId() { return npcId; }
        public String getScriptName() { return scriptName; }
        public boolean isBossLabsDefinition() { return bossLabsDefinition; }
        public String getDefinitionId() { return definitionId; }
        public String getDisplayName() { return displayName; }
        public boolean isSaved() { return saved; }
        public boolean isRollbackAvailable() { return rollbackAvailable; }
    }

    public static final class ActionResult {
        private final int requestId;
        private final boolean success;
        private final int npcId;
        private final String message;

        private ActionResult(int requestId, boolean success, int npcId, String message) {
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

    private static final class DefinitionDownload {
        private final int npcId;
        private final String[] chunks;

        private DefinitionDownload(int npcId, int chunkCount) {
            this.npcId = npcId;
            this.chunks = new String[chunkCount];
        }

        private void setChunk(int index, String value) {
            if (index < 0 || index >= chunks.length)
                throw new IllegalArgumentException("BossLabs definition response chunk index is invalid.");
            chunks[index] = value;
        }

        private String join() {
            StringBuilder builder = new StringBuilder();
            for (String chunk : chunks) {
                if (chunk == null)
                    throw new IllegalArgumentException("BossLabs definition response is missing a chunk.");
                builder.append(chunk);
            }
            return builder.toString();
        }
    }
}
