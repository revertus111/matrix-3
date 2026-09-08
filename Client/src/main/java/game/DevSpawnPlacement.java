package game;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side Dev Spawn placement state.
 *
 * This class owns only the developer-tool placement session. Matrix3/server
 * world authority remains unchanged: every actual placement is sent through the
 * existing owner-only `itembrowser devspawn` server bridge and validated there.
 */
public final class DevSpawnPlacement {

    public enum SpawnMode {
        ONCE("Once"),
        CONTINUOUS("Continuous"),
        PAINT("Paint");

        private final String displayName;

        SpawnMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RotationMode {
        FIXED("Fixed"),
        CYCLE("Cycle"),
        RANDOM("Random");

        private final String displayName;

        RotationMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private enum Kind {
        NPC("NPC"),
        OBJECT("Object"),
        ITEM("Item");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }
    }

    private static volatile Request activeRequest;
    private static volatile SpawnMode activeMode;
    private static volatile Request lastRequest;

    private DevSpawnPlacement() {
    }

    public static Request npc(int id) {
        return new Request(Kind.NPC, id, 0, 0, RotationMode.FIXED, 1);
    }

    public static Request object(int id, int type, int rotation, RotationMode rotationMode) {
        return new Request(Kind.OBJECT, id, type, rotation, rotationMode, 1);
    }

    public static Request item(int id, int amount) {
        return new Request(Kind.ITEM, id, 0, 0, RotationMode.FIXED, amount);
    }

    public static String placeOnce(Request request, int x, int y, int plane) {
        if (request == null) {
            return "Choose a valid spawn target first.";
        }
        lastRequest = request.copy();
        String error = queue(request, x, y, plane);
        if (error != null) {
            return error;
        }
        return "Spawn queued for " + x + ", " + y + ", " + plane + ".";
    }

    public static String arm(Request request, SpawnMode mode) {
        if (request == null || mode == null || mode == SpawnMode.ONCE) {
            return "Continuous/Paint placement requires a valid spawn target.";
        }
        lastRequest = request.copy();
        activeRequest = request.copy();
        activeMode = mode;
        if (mode == SpawnMode.PAINT) {
            return "Paint armed for " + activeRequest.describe()
                    + ". Left-click normal world tiles to place while Walk Here remains active. Press Escape to cancel.";
        }
        return "Continuous placement armed for " + activeRequest.describe()
                + ". Right-click tiles and choose the Dev placement entry. Press Escape to cancel.";
    }

    public static String placeActive(int x, int y, int plane) {
        Request request = activeRequest;
        SpawnMode mode = activeMode;
        if (request == null || mode == null) {
            return "No continuous/paint spawn placement is armed.";
        }
        String error = queue(request, x, y, plane);
        if (error != null) {
            return error;
        }
        return mode.getDisplayName() + " placed " + request.describe() + " at " + x + ", " + y + ", " + plane
                + ". Placement remains armed.";
    }

    public static String placeLast(int x, int y, int plane) {
        Request request = lastRequest;
        if (request == null) {
            return "No previous Dev Spawn target is available yet.";
        }
        String error = queue(request, x, y, plane);
        if (error != null) {
            return error;
        }
        return "Placed last target " + request.describe() + " at " + x + ", " + y + ", " + plane + ".";
    }

    public static boolean cancel() {
        boolean hadActive = activeRequest != null || activeMode != null;
        activeRequest = null;
        activeMode = null;
        return hadActive;
    }

    public static boolean hasActive() {
        return activeRequest != null && activeMode != null;
    }

    public static boolean isPaintActive() {
        return hasActive() && activeMode == SpawnMode.PAINT;
    }

    public static boolean hasLast() {
        return lastRequest != null;
    }

    public static String getActiveMenuText() {
        Request request = activeRequest;
        SpawnMode mode = activeMode;
        if (request == null || mode == null) {
            return null;
        }
        return mode == SpawnMode.PAINT
                ? "Dev > Paint " + request.menuLabel() + " Here"
                : "Dev > Place " + request.menuLabel() + " Here";
    }

    public static String getLastMenuText() {
        Request request = lastRequest;
        return request == null ? null : "Dev > Place Last " + request.menuLabel();
    }

    private static String queue(Request request, int x, int y, int plane) {
        if (!validTile(x, y, plane)) {
            return "Placement tile is outside the supported Matrix3 world range.";
        }

        int rotation = request.rotationForNextPlacement();
        StringBuilder command = new StringBuilder(96);
        command.append("itembrowser devspawn ");
        if (request.kind == Kind.NPC) {
            command.append("npc ").append(request.id).append(' ').append(x).append(' ').append(y).append(' ').append(plane);
        } else if (request.kind == Kind.OBJECT) {
            command.append("object ").append(request.id).append(' ').append(x).append(' ').append(y).append(' ')
                    .append(plane).append(' ').append(request.objectType).append(' ').append(rotation);
        } else {
            command.append("item ").append(request.id).append(' ').append(x).append(' ').append(y).append(' ')
                    .append(plane).append(' ').append(request.amount);
        }

        String error = ClientConsoleBridge.queueConsoleCommand(command.toString());
        if (error == null) {
            request.markPlacementComplete();
        }
        return error;
    }

    private static boolean validTile(int x, int y, int plane) {
        return x >= 0 && x <= 16383 && y >= 0 && y <= 16383 && plane >= 0 && plane <= 3;
    }

    public static final class Request {
        private final Kind kind;
        private final int id;
        private final int objectType;
        private final int baseRotation;
        private final RotationMode rotationMode;
        private final int amount;
        private int placementCount;

        private Request(Kind kind, int id, int objectType, int baseRotation, RotationMode rotationMode,
                int amount) {
            this.kind = kind;
            this.id = id;
            this.objectType = objectType;
            this.baseRotation = baseRotation & 0x3;
            this.rotationMode = rotationMode == null ? RotationMode.FIXED : rotationMode;
            this.amount = amount;
        }

        private Request copy() {
            return new Request(kind, id, objectType, baseRotation, rotationMode, amount);
        }

        private int rotationForNextPlacement() {
            if (kind != Kind.OBJECT) {
                return 0;
            }
            if (rotationMode == RotationMode.RANDOM) {
                return ThreadLocalRandom.current().nextInt(4);
            }
            if (rotationMode == RotationMode.CYCLE) {
                return (baseRotation + placementCount) & 0x3;
            }
            return baseRotation;
        }

        private void markPlacementComplete() {
            placementCount++;
        }

        private String menuLabel() {
            return kind.displayName + " " + id;
        }

        private String describe() {
            if (kind == Kind.OBJECT) {
                return "Object " + id + " (type " + objectType + ", " + rotationMode.getDisplayName().toLowerCase()
                        + " rotation)";
            }
            if (kind == Kind.ITEM) {
                return amount + " x Item " + id;
            }
            return "NPC " + id;
        }
    }
}
