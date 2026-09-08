package game;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;

import javax.swing.SwingUtilities;

import game.console.DevInspectorWindow;
import game.console.DevSpawnBrowserWindow;
import game.console.DevTileEditorWindow;

/**
 * Client-side Dev Mode state and the narrow bridge between Matrix3's existing
 * scene menu entries and the external developer UI.
 *
 * Matrix3 remains authoritative for scene interaction and world state. Dev Mode
 * mirrors existing menu targets only after Matrix3 has already resolved them; it
 * does not replace scene picking, movement, entity ownership, or definitions.
 */
public final class DevModeBridge {

    public static final int TILE_SPAWN_MENU_ACTION = 1500;
    public static final int TILE_EDIT_MENU_ACTION = 1501;
    public static final int TILE_MOVE_HERE_MENU_ACTION = 1502;
    public static final int TILE_DUPLICATE_HERE_MENU_ACTION = 1503;
    public static final int TILE_PLACE_ACTIVE_MENU_ACTION = 1504;
    public static final int TILE_PLACE_LAST_MENU_ACTION = 1505;
    public static final int TILE_CANCEL_PLACEMENT_MENU_ACTION = 1506;

    public static final int NPC_INSPECT_MENU_ACTION = 1510;
    public static final int NPC_EDIT_MENU_ACTION = 1511;
    public static final int NPC_COPY_ID_MENU_ACTION = 1512;
    public static final int NPC_COPY_TILE_MENU_ACTION = 1513;
    public static final int NPC_MOVE_MENU_ACTION = 1514;
    public static final int NPC_DUPLICATE_MENU_ACTION = 1515;
    public static final int NPC_DELETE_DEV_MENU_ACTION = 1516;

    public static final int OBJECT_INSPECT_MENU_ACTION = 1520;
    public static final int OBJECT_EDIT_MENU_ACTION = 1521;
    public static final int OBJECT_COPY_ID_MENU_ACTION = 1522;
    public static final int OBJECT_COPY_TILE_MENU_ACTION = 1523;
    public static final int OBJECT_MOVE_MENU_ACTION = 1524;
    public static final int OBJECT_ROTATE_LEFT_MENU_ACTION = 1525;
    public static final int OBJECT_ROTATE_RIGHT_MENU_ACTION = 1526;
    public static final int OBJECT_DUPLICATE_MENU_ACTION = 1527;
    public static final int OBJECT_DELETE_DEV_MENU_ACTION = 1528;

    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int NPC_DEFINITION_ID_MULTIPLIER = 1355909985;

    private static volatile boolean enabled;
    private static volatile DevTarget currentTarget;
    private static volatile PlacementMode placementMode = PlacementMode.NONE;
    private static volatile DevTarget placementTarget;
    private static volatile boolean escapeListenerInstalled;

    private DevModeBridge() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            ensureEscapeListener();
        } else {
            currentTarget = null;
            clearManipulationPlacement();
            DevSpawnPlacement.cancel();
        }
    }

    public static DevTarget getCurrentTarget() {
        return currentTarget;
    }

    public static String armMove(DevTarget target) {
        return armPlacement(PlacementMode.MOVE, target);
    }

    public static String armDuplicate(DevTarget target) {
        return armPlacement(PlacementMode.DUPLICATE, target);
    }

    public static String spawnOnce(DevSpawnPlacement.Request request, int x, int y, int plane) {
        if (!enabled || !isOwnerSession()) {
            return "Dev Spawn requires an Admin+ live session with Dev Mode enabled.";
        }
        clearManipulationPlacement();
        DevSpawnPlacement.cancel();
        return DevSpawnPlacement.placeOnce(request, x, y, plane);
    }

    public static String armSpawn(DevSpawnPlacement.Request request, DevSpawnPlacement.SpawnMode mode) {
        if (!enabled || !isOwnerSession()) {
            return "Dev Spawn requires an Admin+ live session with Dev Mode enabled.";
        }
        clearManipulationPlacement();
        return DevSpawnPlacement.arm(request, mode);
    }

    public static String cancelPlacement() {
        boolean hadManipulation = placementTarget != null || placementMode != PlacementMode.NONE;
        clearManipulationPlacement();
        boolean hadSpawn = DevSpawnPlacement.cancel();
        return hadManipulation || hadSpawn ? "Placement cancelled." : "No placement is armed.";
    }

    public static String rotateTarget(DevTarget target, int delta) {
        if (!enabled || target == null || target.getType() != TargetType.OBJECT) {
            return "Select an object before rotating it.";
        }
        if (delta != -1 && delta != 1) {
            return "Rotation direction must be left or right.";
        }
        String error = queueEditCommand("rotate", target, " " + delta);
        if (error != null) {
            return error;
        }
        return "Rotate queued. Dev-owned runtime objects rotate immediately; permanent map objects are protected.";
    }

    public static String deleteTarget(DevTarget target) {
        if (!enabled || target == null) {
            return "Select an NPC or object before deleting it.";
        }
        String error = queueEditCommand("delete", target, "");
        if (error != null) {
            return error;
        }
        return "Delete queued. The server will remove only a proven Dev-owned runtime placement.";
    }

    /**
     * VERIFIED: action 23 is Matrix3's normal scene-tile movement action. Its
     * menu entry carries the local X/Y later converted to world coordinates by
     * Class319.method4094. Action 60 is the staff/admin teleport-debug path and
     * must not be used as the ordinary tile-menu source.
     */
    static void mirrorTileSpawnEntry(int sourceAction, int localX, int localY) {
        int normalizedAction = normalizeAction(sourceAction);
        if (!enabled || normalizedAction != MATRIX3_TILE_ACTION || !isOwnerSession()) {
            return;
        }
        if (Class25.aBool165 || 357782167 * Class25.anInt172 >= 504) {
            return;
        }

        addTileEntry("Dev > Edit Tile", TILE_EDIT_MENU_ACTION, localX, localY);
        addTileEntry("Dev > Spawn...", TILE_SPAWN_MENU_ACTION, localX, localY);

        if (placementTarget != null && placementMode == PlacementMode.MOVE) {
            addTileEntry("Dev > Move Here", TILE_MOVE_HERE_MENU_ACTION, localX, localY);
        } else if (placementTarget != null && placementMode == PlacementMode.DUPLICATE) {
            addTileEntry("Dev > Duplicate Here", TILE_DUPLICATE_HERE_MENU_ACTION, localX, localY);
        } else {
            String activeText = DevSpawnPlacement.getActiveMenuText();
            if (activeText != null) {
                addTileEntry(activeText, TILE_PLACE_ACTIVE_MENU_ACTION, localX, localY);
            }
            String lastText = DevSpawnPlacement.getLastMenuText();
            if (lastText != null) {
                addTileEntry(lastText, TILE_PLACE_LAST_MENU_ACTION, localX, localY);
            }
        }

        if (hasAnyPlacementArmed()) {
            addTileEntry("Dev > Cancel Placement", TILE_CANCEL_PLACEMENT_MENU_ACTION, localX, localY);
        }
    }

    /**
     * verified-static: NPC actions 9-13/1003 and object actions 3-6/1001/1002
     * dispatch through Class319 using the target UID already resolved by the
     * normal Matrix3 menu builder. Dev Mode mirrors that target instead of doing
     * a second scene pick.
     */
    static void mirrorEntityEntries(String targetText, int cursor, int sourceAction, int sourceParam,
            long targetUid, int localX, int localY, boolean bool, boolean bool5, long groupUid,
            boolean bool7) {
        if (!enabled || !isOwnerSession() || Class25.aBool165 || 357782167 * Class25.anInt172 >= 504) {
            return;
        }

        int normalizedAction = normalizeAction(sourceAction);
        if (isNpcSourceAction(normalizedAction)) {
            int npcIndex = (int) targetUid;
            addEntityEntry("Dev > Inspect NPC", targetText, cursor, NPC_INSPECT_MENU_ACTION, sourceParam,
                    targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Edit NPC", targetText, cursor, NPC_EDIT_MENU_ACTION, sourceParam,
                    targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Copy NPC ID", targetText, cursor, NPC_COPY_ID_MENU_ACTION, sourceParam,
                    targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Copy NPC Tile", targetText, cursor, NPC_COPY_TILE_MENU_ACTION, sourceParam,
                    targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Move NPC", targetText, cursor, NPC_MOVE_MENU_ACTION, sourceParam,
                    targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Duplicate NPC", targetText, cursor, NPC_DUPLICATE_MENU_ACTION, sourceParam,
                    targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Delete Development Spawn", targetText, cursor, NPC_DELETE_DEV_MENU_ACTION,
                    sourceParam, targetUid, npcIndex, 0, bool, bool5, groupUid, bool7);
        } else if (isObjectSourceAction(normalizedAction)) {
            int objectId = (int) (targetUid >>> 32) & 0x7fffffff;
            int packedTile = packLocalCoordinates(localX, localY);
            addEntityEntry("Dev > Inspect Object", targetText, cursor, OBJECT_INSPECT_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Edit Object", targetText, cursor, OBJECT_EDIT_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Copy Object ID", targetText, cursor, OBJECT_COPY_ID_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Copy Object Tile", targetText, cursor, OBJECT_COPY_TILE_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Move Object", targetText, cursor, OBJECT_MOVE_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Rotate Left", targetText, cursor, OBJECT_ROTATE_LEFT_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Rotate Right", targetText, cursor, OBJECT_ROTATE_RIGHT_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Duplicate Object", targetText, cursor, OBJECT_DUPLICATE_MENU_ACTION, sourceParam,
                    targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
            addEntityEntry("Dev > Delete Development Spawn", targetText, cursor, OBJECT_DELETE_DEV_MENU_ACTION,
                    sourceParam, targetUid, objectId, packedTile, bool, bool5, groupUid, bool7);
        }
    }

    /**
     * Handles only custom Dev Mode actions and leaves every normal Matrix3 menu
     * action untouched. Paint placement observes normal action 23, queues a Dev
     * spawn, then returns false so Matrix3 still performs its ordinary Walk Here.
     */
    static boolean handleMenuAction(int action, int payloadA, int payloadB) {
        AtlasRuntimeBridge.observeMenuAction(action, payloadA, payloadB);

        int normalizedAction = normalizeAction(action);
        if (normalizedAction == MATRIX3_TILE_ACTION && enabled && isOwnerSession()
                && DevSpawnPlacement.isPaintActive()) {
            notifyPlacementStatus(placeActiveSpawnAtLocal(payloadA, payloadB));
            return false;
        }

        if (isTileDevAction(action)) {
            return handleTileAction(action, payloadA, payloadB);
        }
        if (!isEntityDevAction(action)) {
            return false;
        }
        if (!enabled || !isOwnerSession()) {
            return true;
        }

        final DevTarget target = isNpcDevAction(action)
                ? resolveNpcTarget(payloadA)
                : resolveObjectTarget(payloadA, payloadB);
        if (target == null) {
            notifyInspector("The live target could not be resolved. Right-click it again.");
            return true;
        }
        currentTarget = target;

        if (action == NPC_INSPECT_MENU_ACTION || action == OBJECT_INSPECT_MENU_ACTION
                || action == NPC_EDIT_MENU_ACTION || action == OBJECT_EDIT_MENU_ACTION) {
            final boolean editIntent = action == NPC_EDIT_MENU_ACTION || action == OBJECT_EDIT_MENU_ACTION;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    DevInspectorWindow.open(target, editIntent);
                }
            });
        } else if (action == NPC_COPY_ID_MENU_ACTION || action == OBJECT_COPY_ID_MENU_ACTION) {
            copyTargetId(target);
        } else if (action == NPC_COPY_TILE_MENU_ACTION || action == OBJECT_COPY_TILE_MENU_ACTION) {
            copyTargetTile(target);
        } else if (action == NPC_MOVE_MENU_ACTION || action == OBJECT_MOVE_MENU_ACTION) {
            notifyInspector(armMove(target));
        } else if (action == NPC_DUPLICATE_MENU_ACTION || action == OBJECT_DUPLICATE_MENU_ACTION) {
            notifyInspector(armDuplicate(target));
        } else if (action == OBJECT_ROTATE_LEFT_MENU_ACTION) {
            notifyInspector(rotateTarget(target, -1));
        } else if (action == OBJECT_ROTATE_RIGHT_MENU_ACTION) {
            notifyInspector(rotateTarget(target, 1));
        } else if (action == NPC_DELETE_DEV_MENU_ACTION || action == OBJECT_DELETE_DEV_MENU_ACTION) {
            notifyInspector(deleteTarget(target));
        }
        return true;
    }

    public static boolean copyTargetId(DevTarget target) {
        if (target == null || target.getId() < 0) {
            return false;
        }
        copyText(Integer.toString(target.getId()));
        return true;
    }

    public static boolean copyTargetTile(DevTarget target) {
        if (target == null) {
            return false;
        }
        copyText(target.getWorldX() + ", " + target.getWorldY() + ", " + target.getPlane());
        return true;
    }

    private static String armPlacement(PlacementMode mode, DevTarget target) {
        if (!enabled || target == null || target.getId() < 0) {
            return "Select a valid NPC or object first.";
        }
        DevSpawnPlacement.cancel();
        placementMode = mode;
        placementTarget = target;
        currentTarget = target;
        return (mode == PlacementMode.MOVE ? "Move" : "Duplicate") + " armed for " + target.getName()
                + ". Right-click the destination tile and choose Dev > "
                + (mode == PlacementMode.MOVE ? "Move Here" : "Duplicate Here") + ".";
    }

    private static boolean handleTileAction(int action, int localX, int localY) {
        if (!enabled || !isOwnerSession()) {
            return true;
        }

        if (action == TILE_CANCEL_PLACEMENT_MENU_ACTION) {
            notifyPlacementStatus(cancelPlacement());
            return true;
        }

        WorldTileTarget tile = resolveWorldTile(localX, localY);
        if (tile == null) {
            return true;
        }

        if (action == TILE_MOVE_HERE_MENU_ACTION || action == TILE_DUPLICATE_HERE_MENU_ACTION) {
            notifyInspector(executePlacement(tile.worldX, tile.worldY, tile.plane));
            return true;
        }
        if (action == TILE_PLACE_ACTIVE_MENU_ACTION) {
            notifyPlacementStatus(DevSpawnPlacement.placeActive(tile.worldX, tile.worldY, tile.plane));
            return true;
        }
        if (action == TILE_PLACE_LAST_MENU_ACTION) {
            notifyPlacementStatus(DevSpawnPlacement.placeLast(tile.worldX, tile.worldY, tile.plane));
            return true;
        }

        final boolean editTile = action == TILE_EDIT_MENU_ACTION;
        final int worldX = tile.worldX;
        final int worldY = tile.worldY;
        final int plane = tile.plane;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (editTile) {
                    DevTileEditorWindow.open(worldX, worldY, plane);
                } else {
                    DevSpawnBrowserWindow.open(worldX, worldY, plane);
                }
            }
        });
        return true;
    }

    private static String placeActiveSpawnAtLocal(int localX, int localY) {
        WorldTileTarget tile = resolveWorldTile(localX, localY);
        if (tile == null) {
            return "Paint placement could not resolve that live world tile.";
        }
        return DevSpawnPlacement.placeActive(tile.worldX, tile.worldY, tile.plane);
    }

    private static WorldTileTarget resolveWorldTile(int localX, int localY) {
        if (client.aClass613_8605 == null || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return null;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return null;
        }
        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;
        return new WorldTileTarget(worldX, worldY, plane);
    }

    private static String executePlacement(int worldX, int worldY, int plane) {
        DevTarget target = placementTarget;
        PlacementMode mode = placementMode;
        if (target == null || mode == PlacementMode.NONE) {
            return "No Move/Duplicate placement is armed.";
        }

        String operation = mode == PlacementMode.MOVE ? "move" : "duplicate";
        String error = queueEditCommand(operation, target, " " + worldX + " " + worldY + " " + plane);
        if (error != null) {
            return error;
        }

        clearManipulationPlacement();
        if (mode == PlacementMode.MOVE) {
            DevTarget moved = target.withTile(worldX, worldY, plane);
            currentTarget = moved;
            refreshInspectorTarget(moved);
        }
        return (mode == PlacementMode.MOVE ? "Move" : "Duplicate") + " queued for " + worldX + ", "
                + worldY + ", " + plane + ".";
    }

    private static String queueEditCommand(String operation, DevTarget target, String extra) {
        if (!isOwnerSession()) {
            return "Dev Mode manipulation requires an Admin+ live session.";
        }
        String kind = target.getType() == TargetType.NPC ? "npc" : "object";
        String command = "itembrowser devedit " + operation + " " + kind + " " + target.getId() + " "
                + target.getWorldX() + " " + target.getWorldY() + " " + target.getPlane() + " "
                + target.getRuntimeIndex() + extra;
        return ClientConsoleBridge.queueConsoleCommand(command);
    }

    private static DevTarget resolveNpcTarget(int npcIndex) {
        if (client.aClass676_8622 == null || client.aClass613_8605 == null) {
            return null;
        }
        LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) npcIndex);
        if (link == null || !(link.anObject9081 instanceof NPC)) {
            return null;
        }

        NPC npc = (NPC) link.anObject9081;
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null || npc.screenX == null || npc.screenY == null
                || npc.screenX.length == 0 || npc.screenY.length == 0) {
            return null;
        }

        NPCDefintion definition = npc.aClass410_11803;
        int npcId = definition == null ? -1 : definition.anInt4819 * NPC_DEFINITION_ID_MULTIPLIER;
        String name = definition == null ? "NPC" : cleanName(definition.aString4791, "NPC");
        int worldX = sceneBase.localX * -2109597897 + npc.screenX[0];
        int worldY = sceneBase.localY * 417324155 + npc.screenY[0];
        int plane = npc.aByte9009 & 0xff;
        return new DevTarget(TargetType.NPC, npcId, name, worldX, worldY, plane, npcIndex);
    }

    private static DevTarget resolveObjectTarget(int objectId, int packedTile) {
        if (client.aClass613_8605 == null || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return null;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return null;
        }

        int localX = packedTile >>> 16 & 0xffff;
        int localY = packedTile & 0xffff;
        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;
        DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfo(objectId);
        String name = info == null ? "Object" : info.getName();
        return new DevTarget(TargetType.OBJECT, objectId, name, worldX, worldY, plane, -1);
    }

    private static void addTileEntry(String text, int action, int localX, int localY) {
        if (hasDevAction(action) || Class25.aBool165 || 357782167 * Class25.anInt172 >= 504) {
            return;
        }
        Class572_Sub12_Sub10 entry = new Class572_Sub12_Sub10(
                text,
                "",
                -646491435 * client.anInt8751,
                action,
                -1,
                0L,
                localX,
                localY,
                true,
                false,
                0L,
                true);
        Class412.method5075(entry, 722976984);
    }

    private static void addEntityEntry(String text, String targetText, int cursor, int action, int sourceParam,
            long targetUid, int payloadA, int payloadB, boolean bool, boolean bool5, long groupUid,
            boolean bool7) {
        if (hasDevActionForTarget(action, targetUid) || Class25.aBool165 || 357782167 * Class25.anInt172 >= 504) {
            return;
        }
        Class572_Sub12_Sub10 entry = new Class572_Sub12_Sub10(
                text,
                targetText,
                cursor,
                action,
                sourceParam,
                targetUid,
                payloadA,
                payloadB,
                bool,
                bool5,
                groupUid,
                bool7);
        Class412.method5075(entry, 722976984);
    }

    private static boolean isOwnerSession() {
        return ClientConsoleBridge.hasLocalPlayer() && ClientConsoleBridge.getRights() >= 2;
    }

    private static boolean isNpcSourceAction(int action) {
        return action >= 9 && action <= 13 || action == 1003;
    }

    private static boolean isObjectSourceAction(int action) {
        return action >= 3 && action <= 6 || action == 1001 || action == 1002;
    }

    private static boolean isNpcDevAction(int action) {
        return action >= NPC_INSPECT_MENU_ACTION && action <= NPC_DELETE_DEV_MENU_ACTION;
    }

    private static boolean isEntityDevAction(int action) {
        return isNpcDevAction(action)
                || action >= OBJECT_INSPECT_MENU_ACTION && action <= OBJECT_DELETE_DEV_MENU_ACTION;
    }

    private static boolean isTileDevAction(int action) {
        return action == TILE_SPAWN_MENU_ACTION || action == TILE_EDIT_MENU_ACTION
                || action == TILE_MOVE_HERE_MENU_ACTION || action == TILE_DUPLICATE_HERE_MENU_ACTION
                || action == TILE_PLACE_ACTIVE_MENU_ACTION || action == TILE_PLACE_LAST_MENU_ACTION
                || action == TILE_CANCEL_PLACEMENT_MENU_ACTION;
    }

    private static int normalizeAction(int action) {
        return action >= 2000 ? action - 2000 : action;
    }

    private static int packLocalCoordinates(int localX, int localY) {
        return (localX & 0xffff) << 16 | localY & 0xffff;
    }

    private static String cleanName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String name = value.trim();
        return name.length() == 0 || "null".equalsIgnoreCase(name) ? fallback : name;
    }

    private static void copyText(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                } catch (RuntimeException ex) {
                    // Clipboard failure must never interfere with the live client.
                }
            }
        });
    }

    private static void notifyInspector(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                DevInspectorWindow.showStatus(message);
            }
        });
    }

    private static void notifyPlacementStatus(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                DevInspectorWindow.showStatus(message);
                DevSpawnBrowserWindow.showStatus(message);
            }
        });
    }

    private static void refreshInspectorTarget(final DevTarget target) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                DevInspectorWindow.refreshTarget(target);
            }
        });
    }

    private static void clearManipulationPlacement() {
        placementMode = PlacementMode.NONE;
        placementTarget = null;
    }

    private static boolean hasAnyPlacementArmed() {
        return placementTarget != null || placementMode != PlacementMode.NONE || DevSpawnPlacement.hasActive();
    }

    private static synchronized void ensureEscapeListener() {
        if (escapeListenerInstalled) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override
                public void eventDispatched(AWTEvent event) {
                    if (!(event instanceof KeyEvent)) {
                        return;
                    }
                    KeyEvent keyEvent = (KeyEvent) event;
                    if (keyEvent.getID() == KeyEvent.KEY_PRESSED && keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE
                            && enabled && hasAnyPlacementArmed()) {
                        notifyPlacementStatus(cancelPlacement());
                    }
                }
            }, AWTEvent.KEY_EVENT_MASK);
            escapeListenerInstalled = true;
        } catch (RuntimeException ex) {
            // Escape is a convenience cancellation path. Explicit Cancel remains available.
        }
    }

    private static boolean hasDevAction(int targetAction) {
        for (Class572_Sub12_Sub10 entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7932((byte) 50);
                entry != null;
                entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7926(1709126908)) {
            if (normalizeAction(entry.anInt11402 * -44467871) == targetAction) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDevActionForTarget(int targetAction, long targetUid) {
        for (Class572_Sub12_Sub10 entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7932((byte) 50);
                entry != null;
                entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7926(1709126908)) {
            if (normalizeAction(entry.anInt11402 * -44467871) == targetAction
                    && entry.method10329() == targetUid) {
                return true;
            }
        }
        return false;
    }

    private enum PlacementMode {
        NONE,
        MOVE,
        DUPLICATE
    }

    public enum TargetType {
        NPC("NPC"),
        OBJECT("Object");

        private final String displayName;

        TargetType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Immutable shared target context used by the contextual Inspector and Dev
     * manipulation placement modes.
     */
    public static final class DevTarget {
        private final TargetType type;
        private final int id;
        private final String name;
        private final int worldX;
        private final int worldY;
        private final int plane;
        private final int runtimeIndex;

        private DevTarget(TargetType type, int id, String name, int worldX, int worldY, int plane,
                int runtimeIndex) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.runtimeIndex = runtimeIndex;
        }

        private DevTarget withTile(int x, int y, int newPlane) {
            return new DevTarget(type, id, name, x, y, newPlane, runtimeIndex);
        }

        public TargetType getType() {
            return type;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
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

        public int getRuntimeIndex() {
            return runtimeIndex;
        }
    }

    private static final class WorldTileTarget {
        private final int worldX;
        private final int worldY;
        private final int plane;

        private WorldTileTarget(int worldX, int worldY, int plane) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }
    }
}
