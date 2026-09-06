package game;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

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

    public static final int NPC_INSPECT_MENU_ACTION = 1510;
    public static final int NPC_EDIT_MENU_ACTION = 1511;
    public static final int NPC_COPY_ID_MENU_ACTION = 1512;
    public static final int NPC_COPY_TILE_MENU_ACTION = 1513;

    public static final int OBJECT_INSPECT_MENU_ACTION = 1520;
    public static final int OBJECT_EDIT_MENU_ACTION = 1521;
    public static final int OBJECT_COPY_ID_MENU_ACTION = 1522;
    public static final int OBJECT_COPY_TILE_MENU_ACTION = 1523;

    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int NPC_DEFINITION_ID_MULTIPLIER = 1355909985;

    private static volatile boolean enabled;
    private static volatile DevTarget currentTarget;

    private DevModeBridge() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            currentTarget = null;
        }
    }

    public static DevTarget getCurrentTarget() {
        return currentTarget;
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
        if (!enabled || !isOwnerSession() || Class25.aBool165 || 357782167 * Class25.anInt172 > 500) {
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
        }
    }

    /**
     * Handles only custom Dev Mode actions and leaves every normal Matrix3 menu
     * action untouched.
     */
    static boolean handleMenuAction(int action, int payloadA, int payloadB) {
        AtlasRuntimeBridge.observeMenuAction(action, payloadA, payloadB);

        if (action == TILE_SPAWN_MENU_ACTION || action == TILE_EDIT_MENU_ACTION) {
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

    private static boolean handleTileAction(int action, int localX, int localY) {
        if (!enabled || !isOwnerSession() || client.aClass613_8605 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return true;
        }

        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return true;
        }

        final int worldX = sceneBase.localX * -2109597897 + localX;
        final int worldY = sceneBase.localY * 417324155 + localY;
        final int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;
        final boolean editTile = action == TILE_EDIT_MENU_ACTION;

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
        return action >= NPC_INSPECT_MENU_ACTION && action <= NPC_COPY_TILE_MENU_ACTION;
    }

    private static boolean isEntityDevAction(int action) {
        return isNpcDevAction(action)
                || action >= OBJECT_INSPECT_MENU_ACTION && action <= OBJECT_COPY_TILE_MENU_ACTION;
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
     * Immutable shared target context used by the first contextual Inspector.
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
}
