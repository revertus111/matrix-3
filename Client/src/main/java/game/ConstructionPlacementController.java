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
        FURNITURE("Furniture"),
        RAILS("Rails");

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
                    "Verified Matrix3 bed object; each placed bed adds one settlement population capacity."),
            new BuildPiece("wooden-workbench", "Wooden workbench", Category.FURNITURE, 13704, 10,
                    "Phase 3 workstation; Carpenter workers process 2 Wood into 1 Plank here."),
            new BuildPiece("basic-rail", "Rail route", Category.RAILS, 46353, 22,
                    "Factorio-style rail network: drag track, then start from existing track to extend or branch."),
            new BuildPiece("rail-loader", "Rail Loader", Category.RAILS, 13450, 0,
                    "V1 logistics endpoint; place cardinally adjacent to a rail."),
            new BuildPiece("rail-unloader", "Rail Unloader", Category.RAILS, 13344, 0,
                    "V1 logistics endpoint; place cardinally adjacent to the destination rail.")
    };

    private static volatile BuildPiece selectedPiece;
    private static volatile int rotation;
    private static volatile PlacementMode placementMode = PlacementMode.PAINT;
    private static volatile String status = "Choose a build piece.";
    private static volatile boolean eraserMode;

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

    public static boolean isEraserMode() {
        return eraserMode;
    }

    public static String setEraserMode(boolean enabled) {
        eraserMode = enabled;
        if (enabled) {
            RailRoutePreview.setEnabled(false);
            DevModeBridge.cancelPlacement();
            status = "Eraser armed. Click a settlement build tile to remove it.";
        } else {
            status = selectedPiece == null ? "Choose a build piece." : armSelected();
        }
        return status;
    }

    public static boolean eraseAtLocalTile(int localX, int localY) {
        if (!eraserMode || client.aClass613_8605 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return false;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            status = "Eraser target could not resolve the current scene.";
            return true;
        }
        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;

        /*
         * Rails are derived from Rail Network V1 logical topology. Let the rail
         * owner remove the logical node and submit the physical delta; a generic
         * server-only tile delete would leave stale client topology that can
         * resurrect on the next rail edit.
         */
        String railErase = RailRoutePreview.eraseAtWorldTile(worldX, worldY, plane);
        if (railErase != null) {
            status = railErase;
            return true;
        }

        String error = ClientConsoleBridge.queueConsoleCommand(
                "itembrowser settlement erasetile " + worldX + " " + worldY + " " + plane);
        status = error == null
                ? "Erase queued for " + worldX + ", " + worldY + ", " + plane + "."
                : error;
        return true;
    }

    public static boolean isArmed() {
        return DevSpawnPlacement.hasActive() || isRailRouteSelected();
    }

    public static boolean isRailRouteSelected() {
        BuildPiece piece = selectedPiece;
        return piece != null && "basic-rail".equals(piece.getKey()) && RailRoutePreview.isEnabled();
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
        // The palette no longer owns camera lifetime while the settlement lifecycle
        // owns the always-on RTS session. Closing build UI must not restore vanilla.
        if (!ConstructionBuildCamera.isSettlementAutoMode()) {
            ConstructionBuildCamera.exit();
        }
        RailRoutePreview.setEnabled(false);
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
        eraserMode = false;
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
        eraserMode = false;
        RailRoutePreview.setEnabled(false);
        status = DevModeBridge.cancelPlacement();
        return status;
    }

    public static void onRailRouteCommitted(java.util.List<RailRoutePreview.RoutePiece> route) {
        if (!isRailRouteSelected() || route == null || route.isEmpty()) {
            return;
        }

        /*
         * The first Rail Network commit must be one server-owned transaction.
         * The old prototype queued one settlementbuild command per physical
         * piece. A long sampled route can outrun/partially apply that command
         * stream, which is why the client preview vanished on release while
         * only some curve components remained in the world.
         */
        java.util.List<String> commands = new java.util.ArrayList<String>();
        commands.add("settlementrailreplacebegin");
        appendRailNewChunks(commands, route);
        commands.add("settlementrailreplacecommit");

        String error = ClientConsoleBridge.queueConsoleCommands(
                commands.toArray(new String[commands.size()]));
        status = error == null
                ? "Rail network commit queued: +" + route.size() + " piece(s)."
                : "Rail network commit failed to queue: " + error;
    }

    public static void onRailNetworkDelta(
            java.util.List<RailRoutePreview.RoutePiece> previousRoute,
            java.util.List<RailRoutePreview.RoutePiece> replacementRoute) {
        if (!isRailRouteSelected() || previousRoute == null || replacementRoute == null
                || replacementRoute.isEmpty()) {
            status = "Rail network delta requires a replacement network.";
            return;
        }

        java.util.Map<String, RailRoutePreview.RoutePiece> oldBySlot =
                new java.util.LinkedHashMap<String, RailRoutePreview.RoutePiece>();
        for (RailRoutePreview.RoutePiece piece : previousRoute) {
            oldBySlot.put(railPhysicalSlot(piece), piece);
        }
        java.util.Map<String, RailRoutePreview.RoutePiece> newBySlot =
                new java.util.LinkedHashMap<String, RailRoutePreview.RoutePiece>();
        for (RailRoutePreview.RoutePiece piece : replacementRoute) {
            newBySlot.put(railPhysicalSlot(piece), piece);
        }

        java.util.List<RailRoutePreview.RoutePiece> remove =
                new java.util.ArrayList<RailRoutePreview.RoutePiece>();
        java.util.List<RailRoutePreview.RoutePiece> add =
                new java.util.ArrayList<RailRoutePreview.RoutePiece>();

        for (java.util.Map.Entry<String, RailRoutePreview.RoutePiece> entry : oldBySlot.entrySet()) {
            RailRoutePreview.RoutePiece replacement = newBySlot.get(entry.getKey());
            if (replacement == null || !sameRailPhysicalPiece(entry.getValue(), replacement)) {
                remove.add(entry.getValue());
            }
        }
        for (java.util.Map.Entry<String, RailRoutePreview.RoutePiece> entry : newBySlot.entrySet()) {
            RailRoutePreview.RoutePiece previous = oldBySlot.get(entry.getKey());
            if (previous == null || !sameRailPhysicalPiece(previous, entry.getValue())) {
                add.add(entry.getValue());
            }
        }

        if (remove.isEmpty() && add.isEmpty()) {
            status = "Rail network unchanged.";
            return;
        }

        java.util.List<String> commands = new java.util.ArrayList<String>();
        commands.add("settlementrailreplacebegin");
        appendRailOldChunks(commands, remove);
        appendRailNewChunks(commands, add);
        commands.add("settlementrailreplacecommit");

        String error = ClientConsoleBridge.queueConsoleCommands(
                commands.toArray(new String[commands.size()]));
        status = error == null
                ? "Rail network delta queued: -" + remove.size() + " +" + add.size() + " piece(s)."
                : "Rail network delta failed to queue: " + error;
    }

    private static void appendRailOldChunks(java.util.List<String> commands,
            java.util.List<RailRoutePreview.RoutePiece> pieces) {
        StringBuilder chunk = new StringBuilder("settlementrailreplaceold");
        int count = 0;
        for (RailRoutePreview.RoutePiece piece : pieces) {
            String entry = " " + piece.getObjectId() + "," + piece.getWorldX() + ","
                    + piece.getWorldY() + "," + piece.getPlane();
            if (count >= 6 || chunk.length() + entry.length() > 220) {
                commands.add(chunk.toString());
                chunk = new StringBuilder("settlementrailreplaceold");
                count = 0;
            }
            chunk.append(entry);
            count++;
        }
        if (count > 0) commands.add(chunk.toString());
    }

    private static void appendRailNewChunks(java.util.List<String> commands,
            java.util.List<RailRoutePreview.RoutePiece> pieces) {
        StringBuilder chunk = new StringBuilder("settlementrailreplacenew");
        int count = 0;
        for (RailRoutePreview.RoutePiece piece : pieces) {
            String key = railBuildKey(piece.getObjectId());
            if (key == null) continue;
            String entry = " " + key + "," + piece.getWorldX() + "," + piece.getWorldY()
                    + "," + piece.getPlane() + "," + piece.getRotation();
            if (count >= 5 || chunk.length() + entry.length() > 220) {
                commands.add(chunk.toString());
                chunk = new StringBuilder("settlementrailreplacenew");
                count = 0;
            }
            chunk.append(entry);
            count++;
        }
        if (count > 0) commands.add(chunk.toString());
    }

    private static String railPhysicalSlot(RailRoutePreview.RoutePiece piece) {
        return piece.getWorldX() + ":" + piece.getWorldY() + ":" + piece.getPlane()
                + ":" + piece.getObjectType();
    }

    private static boolean sameRailPhysicalPiece(RailRoutePreview.RoutePiece a,
            RailRoutePreview.RoutePiece b) {
        return a.getObjectId() == b.getObjectId()
                && a.getObjectType() == b.getObjectType()
                && a.getRotation() == b.getRotation();
    }

    public static void onRailRouteEdited(
            java.util.List<RailRoutePreview.RoutePiece> previousRoute,
            java.util.List<RailRoutePreview.RoutePiece> replacementRoute) {
        if (!isRailRouteSelected() || previousRoute == null || previousRoute.isEmpty()
                || replacementRoute == null || replacementRoute.isEmpty()) {
            status = "Rail endpoint edit requires both the old and replacement route.";
            return;
        }

        java.util.List<String> commands = new java.util.ArrayList<String>();
        commands.add("settlementrailreplacebegin");

        StringBuilder chunk = new StringBuilder("settlementrailreplaceold");
        int chunkCount = 0;
        for (RailRoutePreview.RoutePiece piece : previousRoute) {
            String entry = " " + piece.getObjectId() + "," + piece.getWorldX() + ","
                    + piece.getWorldY() + "," + piece.getPlane();
            if (chunkCount >= 6 || chunk.length() + entry.length() > 220) {
                commands.add(chunk.toString());
                chunk = new StringBuilder("settlementrailreplaceold");
                chunkCount = 0;
            }
            chunk.append(entry);
            chunkCount++;
        }
        if (chunkCount > 0) {
            commands.add(chunk.toString());
        }

        chunk = new StringBuilder("settlementrailreplacenew");
        chunkCount = 0;
        for (RailRoutePreview.RoutePiece piece : replacementRoute) {
            String key = railBuildKey(piece.getObjectId());
            if (key == null) {
                status = "Unsupported rail object " + piece.getObjectId() + " in edited route.";
                return;
            }
            String entry = " " + key + "," + piece.getWorldX() + "," + piece.getWorldY()
                    + "," + piece.getPlane() + "," + piece.getRotation();
            if (chunkCount >= 5 || chunk.length() + entry.length() > 220) {
                commands.add(chunk.toString());
                chunk = new StringBuilder("settlementrailreplacenew");
                chunkCount = 0;
            }
            chunk.append(entry);
            chunkCount++;
        }
        if (chunkCount > 0) {
            commands.add(chunk.toString());
        }
        commands.add("settlementrailreplacecommit");

        String error = ClientConsoleBridge.queueConsoleCommands(
                commands.toArray(new String[commands.size()]));
        status = error == null
                ? "Packet-safe atomic rail edit queued: " + previousRoute.size()
                        + " old -> " + replacementRoute.size() + " new piece(s)."
                : "Rail endpoint edit failed to queue: " + error;
    }

    private static String railBuildKey(int objectId) {
        switch (objectId) {
        case 46353:
            return "basic-rail";
        case 46377:
            return "rail-curve-a";
        case 46379:
            return "rail-curve-elbow";
        case 46381:
            return "rail-curve-b";
        default:
            return null;
        }
    }

    private static String armSelected() {
        BuildPiece piece = selectedPiece;
        if (piece == null) {
            status = "Choose a build piece.";
            return status;
        }

        DevModeBridge.setEnabled(true);
        if ("basic-rail".equals(piece.getKey())) {
            DevModeBridge.cancelPlacement();
            RailRoutePreview.configure("Settlement rail", 46353, 22, 3,
                    RailRoutePreview.RouteOrder.X_THEN_Y);
            RailRoutePreview.reloadCurveComposite();
            RailRoutePreview.setEnabled(true);
            status = "Rail Network armed. Drag track; start another drag on existing authored track to extend or branch.";
            return status;
        }
        RailRoutePreview.setEnabled(false);
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
