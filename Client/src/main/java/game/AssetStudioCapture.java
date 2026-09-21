package game;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only client-scene capture engine for Matrix3 Asset Studio.
 *
 * Capture reads the same live Class523 cells Matrix3 renders. It never attaches,
 * removes, rotates, replaces or otherwise mutates scene objects.
 */
public final class AssetStudioCapture {

    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private AssetStudioCapture() {
    }

    public static CaptureBatch capturePlayerArea(int radius) {
        if (client.aClass613_8605 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return CaptureBatch.failure("Live player/scene is unavailable.");
        }
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player.screenX == null || player.screenY == null
                || player.screenX.length == 0 || player.screenY.length == 0) {
            return CaptureBatch.failure("Live player tile is unavailable.");
        }
        int plane = player.aByte9009 & 0xff;
        return captureLocalArea(player.screenX[0], player.screenY[0], plane, radius);
    }

    public static CaptureBatch capturePlayerOffset(int dx, int dy, int radius) {
        if (client.aClass613_8605 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return CaptureBatch.failure("Live player/scene is unavailable.");
        }
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player.screenX == null || player.screenY == null
                || player.screenX.length == 0 || player.screenY.length == 0) {
            return CaptureBatch.failure("Live player tile is unavailable.");
        }
        int plane = player.aByte9009 & 0xff;
        return captureLocalArea(player.screenX[0] + dx, player.screenY[0] + dy, plane, radius);
    }

    public static CaptureBatch captureWorldArea(int worldX, int worldY, int plane, int radius) {
        Class613 region = client.aClass613_8605;
        if (region == null) {
            return CaptureBatch.failure("Live scene is unavailable.");
        }
        Class497 sceneBase = region.method7280((byte) -102);
        if (sceneBase == null) {
            return CaptureBatch.failure("Scene base is unavailable.");
        }
        int localX = worldX - sceneBase.localX * -2109597897;
        int localY = worldY - sceneBase.localY * 417324155;
        return captureLocalArea(localX, localY, plane, radius);
    }

    private static CaptureBatch captureLocalArea(int centerLocalX, int centerLocalY,
            int plane, int requestedRadius) {
        Class613 region = client.aClass613_8605;
        if (region == null) {
            return CaptureBatch.failure("Live scene is unavailable.");
        }
        Class523 scene = region.method7285(0);
        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (scene == null || sceneBase == null) {
            return CaptureBatch.failure("Live scene data is unavailable.");
        }
        if (plane < 0 || plane >= scene.aClass536ArrayArrayArray5882.length) {
            return CaptureBatch.failure("Capture plane is outside the active scene.");
        }

        int radius = Math.max(0, Math.min(requestedRadius, 12));
        Class536[][] planeCells = scene.aClass536ArrayArrayArray5882[plane];
        int width = planeCells.length;
        int height = width == 0 ? 0 : planeCells[0].length;
        if (height == 0) {
            return CaptureBatch.failure("Active scene grid is unavailable.");
        }

        int worldCenterX = sceneBase.localX * -2109597897 + centerLocalX;
        int worldCenterY = sceneBase.localY * 417324155 + centerLocalY;
        List<CaptureEntry> entries = new ArrayList<CaptureEntry>();
        Map<Object, Boolean> seen = new IdentityHashMap<Object, Boolean>();

        int minX = Math.max(0, centerLocalX - radius);
        int maxX = Math.min(width - 1, centerLocalX + radius);
        int minY = Math.max(0, centerLocalY - radius);
        int maxY = Math.min(height - 1, centerLocalY + radius);

        for (int localX = minX; localX <= maxX; localX++) {
            for (int localY = minY; localY <= maxY; localY++) {
                Class536 cell = planeCells[localX][localY];
                if (cell == null) {
                    continue;
                }

                add(entries, seen, cell.aClass456_Sub1_Sub4_5988,
                        "wall-a", localX, localY, plane, sceneBase, definitions);
                add(entries, seen, cell.aClass456_Sub1_Sub4_5989,
                        "wall-b", localX, localY, plane, sceneBase, definitions);
                add(entries, seen, cell.aClass456_Sub1_Sub3_5990,
                        "wall-decoration-a", localX, localY, plane, sceneBase, definitions);
                add(entries, seen, cell.aClass456_Sub1_Sub3_5998,
                        "wall-decoration-b", localX, localY, plane, sceneBase, definitions);
                add(entries, seen, cell.aClass456_Sub1_Sub1_5992,
                        "floor-decoration", localX, localY, plane, sceneBase, definitions);

                for (Class543 link = cell.aClass543_5994;
                        link != null; link = link.aClass543_6100) {
                    Class456_Sub1_Sub2 floorObject = link.aClass456_Sub1_Sub2_6099;
                    if (floorObject == null) {
                        continue;
                    }
                    add(entries, seen, floorObject, "floor",
                            floorObject.aShort11503, floorObject.aShort11500,
                            plane, sceneBase, definitions);
                }
            }
        }

        return CaptureBatch.success(worldCenterX, worldCenterY, plane, radius, entries);
    }

    private static void add(List<CaptureEntry> entries, Map<Object, Boolean> seen,
            Object sceneObject, String slot, int localX, int localY, int plane,
            Class497 sceneBase, Class639_Sub16 definitions) {
        if (!(sceneObject instanceof Interface65) || seen.containsKey(sceneObject)) {
            return;
        }
        seen.put(sceneObject, Boolean.TRUE);

        Interface65 object = (Interface65) sceneObject;
        final int id;
        final int type;
        final int rotation;
        try {
            id = object.method136(0);
            type = object.method55();
            rotation = object.method383(0) & 0x3;
        } catch (RuntimeException ex) {
            return;
        }
        if (id < 0) {
            return;
        }

        String name = "id-" + id;
        DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfo(id);
        if (info != null && info.getName() != null) {
            name = info.getName();
        }

        int sizeX = -1;
        int sizeY = -1;
        if (definitions != null) {
            try {
                ObjectDefinitions definition =
                        (ObjectDefinitions) definitions.getDefinition(id, -1356282071);
                if (definition != null) {
                    sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
                    sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
                }
            } catch (RuntimeException ex) {
                // Size is optional metadata; identity/type/rotation remain valid.
            }
        }

        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        entries.add(new CaptureEntry(id, name, type, rotation, slot,
                worldX, worldY, plane, sizeX, sizeY));
    }

    public static final class CaptureBatch {
        private final boolean success;
        private final String error;
        private final int centerX;
        private final int centerY;
        private final int plane;
        private final int radius;
        private final List<CaptureEntry> entries;

        private CaptureBatch(boolean success, String error, int centerX, int centerY,
                int plane, int radius, List<CaptureEntry> entries) {
            this.success = success;
            this.error = error;
            this.centerX = centerX;
            this.centerY = centerY;
            this.plane = plane;
            this.radius = radius;
            this.entries = entries;
        }

        private static CaptureBatch failure(String error) {
            return new CaptureBatch(false, error, 0, 0, 0, 0,
                    new ArrayList<CaptureEntry>());
        }

        private static CaptureBatch success(int centerX, int centerY, int plane,
                int radius, List<CaptureEntry> entries) {
            return new CaptureBatch(true, null, centerX, centerY, plane, radius, entries);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public int getCenterX() {
            return centerX;
        }

        public int getCenterY() {
            return centerY;
        }

        public int getPlane() {
            return plane;
        }

        public int getRadius() {
            return radius;
        }

        public List<CaptureEntry> getEntries() {
            return new ArrayList<CaptureEntry>(entries);
        }
    }

    public static final class CaptureEntry {
        private final int id;
        private final String name;
        private final int type;
        private final int rotation;
        private final String slot;
        private final int worldX;
        private final int worldY;
        private final int plane;
        private final int sizeX;
        private final int sizeY;

        private CaptureEntry(int id, String name, int type, int rotation, String slot,
                int worldX, int worldY, int plane, int sizeX, int sizeY) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.rotation = rotation;
            this.slot = slot;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public int getType() { return type; }
        public int getRotation() { return rotation; }
        public String getSlot() { return slot; }
        public int getWorldX() { return worldX; }
        public int getWorldY() { return worldY; }
        public int getPlane() { return plane; }
        public int getSizeX() { return sizeX; }
        public int getSizeY() { return sizeY; }

        public String getTileText() {
            return worldX + "," + worldY + "," + plane;
        }

        public String getSizeText() {
            return sizeX < 0 || sizeY < 0 ? "?" : sizeX + "x" + sizeY;
        }

        public String getKey() {
            return id + ":" + type + ":" + rotation + ":" + slot + ":"
                    + worldX + ":" + worldY + ":" + plane;
        }

        public boolean isRailCandidate() {
            String lower = name == null ? "" : name.toLowerCase();
            return (type == 22 && slot != null && slot.startsWith("floor-decoration"))
                    || lower.contains("rail") || lower.contains("track");
        }

        public String getSuggestedTag() {
            String lower = name == null ? "" : name.toLowerCase();
            if (lower.contains("buffer"))
                return "BUFFER";
            if (lower.contains("cart") || lower.contains("truck"))
                return "CART";
            if (lower.contains("signal"))
                return "SIGNAL";
            if (lower.contains("load"))
                return "LOADING_POINT";
            if (isRailCandidate())
                return "RAIL_CANDIDATE";
            return "UNKNOWN";
        }
    }
}
