package game.console.bosslabs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import game.ClientConsoleBridge;

/**
 * Client-side BossLabs transport adapter using Matrix3's existing console
 * command packet for requests and CLIENT_COMMAND packet for replies.
 */
public final class BossLabsClientBridge {

    private static final String RESPONSE_PREFIX = "bosslabs|";
    private static final AtomicInteger REQUEST_SEQUENCE = new AtomicInteger(1);

    private static volatile Listener listener;

    private BossLabsClientBridge() {
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    public static void clearListener(Listener oldListener) {
        if (listener == oldListener) {
            listener = null;
        }
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

    /**
     * Consumes only BossLabs-prefixed server CLIENT_COMMAND payloads. Returning
     * false preserves the original Matrix3 client-command parser unchanged for
     * every other command.
     */
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
            } else if ("inspect-missing".equals(type) && parts.length >= 4) {
                final int requestId = parseInt(parts[2]);
                final int npcId = parseInt(parts[3]);
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onInspectionMissing(requestId, npcId);
                    }
                });
            } else if ("action".equals(type) && parts.length >= 6) {
                final ActionResult result = new ActionResult(parseInt(parts[2]), parseBoolean(parts[3]),
                        parseInt(parts[4]), decode(parts[5]));
                dispatch(new ListenerCall() {
                    @Override
                    public void call(Listener target) {
                        target.onActionResult(result);
                    }
                });
            }
        } catch (RuntimeException ex) {
            System.err.println("BossLabs failed to decode server response: " + command);
            ex.printStackTrace();
        }
        return true;
    }

    private static void queue(final int requestId, String command, String failureLabel) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error == null) {
            return;
        }
        final ActionResult result = new ActionResult(requestId, false, -1, failureLabel + ": " + error);
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
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private interface ListenerCall {
        void call(Listener target);
    }

    public interface Listener {
        void onSearchStarted(int requestId);
        void onSearchResult(int requestId, SearchResult result);
        void onSearchFinished(int requestId, int count);
        void onSearchError(int requestId, String message);
        void onInspection(int requestId, Inspection inspection);
        void onOwnership(int requestId, Ownership ownership);
        void onInspectionMissing(int requestId, int npcId);
        void onActionResult(ActionResult result);
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
}
