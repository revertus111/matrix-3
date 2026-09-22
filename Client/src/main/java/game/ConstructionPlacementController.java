package game;

/**
 * Construction build-selection and placement-session state shared by the custom
 * in-game palette and Matrix3's proven placement input bridge.
 *
 * This class never owns world objects. Confirmed Construction placement reuses
 * DevModeBridge/DevSpawnPlacement only for input/menu plumbing, then sends the
 * stable build-piece key through the server-authoritative normal-player
 * settlement build command rather than the owner-only devspawn command.
 */
public final class ConstructionPlacementController {

    public enum Category {
        WALLS("Walls"),
        FLOORS("Floors"),
        DOORS("Doors"),
        FURNITURE("Furniture");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PlacementMode {
        PAINT("Paint", DevSpawnPlacement.SpawnMode.PAINT),
        CONTINUOUS("Continuous", DevSpawnPlacement.SpawnMode.CONTINUOUS);

        private final String displayName;
        private final DevSpawnPlacement.SpawnMode spawnMode;

        PlacementMode(String displayName, DevSpawnPlacement.SpawnMode spawnMode) {
            this.displayName = displayName;
            this.spawnMode = spawnMode;
        }

        public String getDisplayName() {
            return displayName;
        }

        private DevSpawnPlacement.SpawnMode getSpawnMode() {
            return spawnMode;
        }
    }

    /**
     * Runtime-observed starter catalog. These are placement/tooling candidates,
     * not final Construction art definitions.
     */
    private static final BuildPiece[] PIECES = {
            new BuildPiece("wood-fence-test", "Wooden fence", Category.WALLS, 13450, 0,
                    "Temporary wall-placement test; not the final wooden wall."),
            new BuildPiece("floor-decoration", "Floor decoration", Category.FLOORS, 13684, 22,
                    "Current floor candidate; final Construction art acceptance is pending."),
            new BuildPiece("basic-door", "Door", Category.DOORS, 13344, 0,
                    "Current doorway candidate; final Construction art acceptance is pending."),
            new BuildPiece("basic-bed", "Bed", Category.FURNITURE, 14872, 10,
                    "Verified Matrix3 bed object; each placed bed adds one settlement population capacity.")
    };

    private static volatile BuildPiece selectedPiece;
    private static volatile int rotation;
    private static volatile PlacementMode placementMode = PlacementMode.PAINT;
    private static volatile String status = "Choose a build piece.";

    private static volatile boolean hoverTracking;
    private static volatile int hoveredWorldX = -1;
    private static volatile int hoveredWorldY = -1;
    private static volatile int hoveredPlane = -1;
    private static volatile long hoveredAtMillis;

    private static final long HOVER_STALE_MS = 1250L;
    private static final int MATRIX3_TILE_ACTION = 23;

    private ConstructionPlacementController() {
    }

    public static BuildPiece[] getPieces() {
        return PIECES.clone();
    }

    public static BuildPiece getSelectedPiece() {
        return selectedPiece;
    }

    public static int getRotation() {
        return rotation;
    }

    public static PlacementMode getPlacementMode() {
        return placementMode;
    }

    public static String getStatus() {
        return status;
    }

    public static boolean isArmed() {
        return DevSpawnPlacement.hasActive();
    }

    public static void beginPaletteSession() {
        hoverTracking = true;
        clearHoveredTile();
        ConstructionGhostPreview.beginDebugSession();
        // Construction always opens in RTS management view. Free Build remains
        // available as an explicit palette switch for close-up placement work.
        ConstructionBuildCamera.setMode(ConstructionBuildCamera.CameraMode.RTS);
        ConstructionBuildCamera.enter();
        status = DevModeBridge.cancelPlacement();
        if ("No placement is armed.".equals(status)) {
            status = "Choose a build piece.";
        }
    }

    public static void endPaletteSession(boolean cancelPlacement) {
        hoverTracking = false;
        clearHoveredTile();
        ConstructionGhostPreview.endDebugSession();
        ConstructionBuildCamera.exit();
        if (cancelPlacement) {
            status = DevModeBridge.cancelPlacement();
        }
    }

    public static boolean isHoverTracking() {
        return hoverTracking;
    }

    /**
     * Free Build paint confirmation consumes the already-resolved hovered tile.
     * It does not perform a second scene pick and still queues the normal
     * server-authoritative settlement build command.
     *
     * @return true when the current click was a valid armed Paint placement and
     *         should therefore be consumed instead of becoming Walk Here.
     */
    public static boolean placeHoveredFromBuildCamera() {
        if (!ConstructionBuildCamera.isRequested() || placementMode != PlacementMode.PAINT
                || !DevSpawnPlacement.isPaintActive()) {
            return false;
        }
        HoverTile tile = getHoveredTile();
        if (tile == null) {
            return false;
        }
        status = DevSpawnPlacement.placeActive(tile.worldX, tile.worldY, tile.plane);
        return true;
    }

    /**
     * verified-static: action 23 is Matrix3's normal scene-tile menu entry and
     * carries local X/Y. This mirrors only its resolved tile for preview state;
     * it does not pick a second tile or alter the menu entry.
     */
    static void observeSceneMenuTile(int sourceAction, int localX, int localY) {
        int normalizedAction = sourceAction >= 2000 ? sourceAction - 2000 : sourceAction;
        if (!hoverTracking || normalizedAction != MATRIX3_TILE_ACTION
                || client.aClass613_8605 == null || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return;
        }
        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;
        observeHoveredWorldTile(worldX, worldY, plane);
    }

    private static void observeHoveredWorldTile(int worldX, int worldY, int plane) {
        hoveredWorldX = worldX;
        hoveredWorldY = worldY;
        hoveredPlane = plane;
        hoveredAtMillis = System.currentTimeMillis();
    }

    public static HoverTile getHoveredTile() {
        long age = System.currentTimeMillis() - hoveredAtMillis;
        if (!hoverTracking || hoveredWorldX < 0 || hoveredWorldY < 0 || hoveredPlane < 0
                || hoveredAtMillis == 0L || age > HOVER_STALE_MS) {
            return null;
        }
        return new HoverTile(hoveredWorldX, hoveredWorldY, hoveredPlane);
    }

    public static String select(BuildPiece piece) {
        if (piece == null) {
            status = "Choose a valid Construction piece.";
            return status;
        }
        selectedPiece = piece;
        rotation = 0;
        return armSelected();
    }

    public static String setPlacementMode(PlacementMode mode) {
        if (mode == null) {
            return status;
        }
        placementMode = mode;
        if (selectedPiece != null && DevSpawnPlacement.hasActive()) {
            return armSelected();
        }
        status = "Placement mode: " + mode.getDisplayName() + ".";
        return status;
    }

    public static String rotate(int delta) {
        if (delta != -1 && delta != 1) {
            return status;
        }
        rotation = (rotation + delta) & 0x3;
        if (selectedPiece != null && DevSpawnPlacement.hasActive()) {
            return armSelected();
        }
        status = "Rotation: " + rotation + " (" + (rotation * 90) + " deg).";
        return status;
    }

    public static String cancel() {
        status = DevModeBridge.cancelPlacement();
        return status;
    }

    private static String armSelected() {
        BuildPiece piece = selectedPiece;
        if (piece == null) {
            status = "Choose a build piece.";
            return status;
        }

        DevModeBridge.setEnabled(true);
        DevSpawnPlacement.Request request = DevSpawnPlacement.constructionObject(
                piece.getKey(),
                piece.getObjectId(),
                piece.getObjectType(),
                rotation,
                DevSpawnPlacement.RotationMode.FIXED);
        status = DevModeBridge.armSpawn(request, placementMode.getSpawnMode());
        return status;
    }

    private static void clearHoveredTile() {
        hoveredWorldX = -1;
        hoveredWorldY = -1;
        hoveredPlane = -1;
        hoveredAtMillis = 0L;
    }

    public static final class BuildPiece {
        private final String key;
        private final String displayName;
        private final Category category;
        private final int objectId;
        private final int objectType;
        private final String note;

        private BuildPiece(String key, String displayName, Category category, int objectId, int objectType,
                String note) {
            this.key = key;
            this.displayName = displayName;
            this.category = category;
            this.objectId = objectId;
            this.objectType = objectType;
            this.note = note;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Category getCategory() {
            return category;
        }

        public int getObjectId() {
            return objectId;
        }

        public int getObjectType() {
            return objectType;
        }

        public String getNote() {
            return note;
        }

        public boolean matches(String filter) {
            if (filter == null || filter.trim().length() == 0) {
                return true;
            }
            String needle = filter.trim().toLowerCase();
            return displayName.toLowerCase().contains(needle)
                    || category.getDisplayName().toLowerCase().contains(needle)
                    || Integer.toString(objectId).contains(needle)
                    || note.toLowerCase().contains(needle);
        }
    }

    public static final class HoverTile {
        private final int worldX;
        private final int worldY;
        private final int plane;

        private HoverTile(int worldX, int worldY, int plane) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }

        public int getWorldX() {
            return worldX;
        }

        public int getWorldY() {
            return worldY;
        }

        public int getPlane() {
            return plane;
        }
    }
}
