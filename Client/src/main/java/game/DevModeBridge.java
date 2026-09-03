package game;

import javax.swing.SwingUtilities;

import game.console.DevSpawnBrowserWindow;

/**
 * Client-side Dev Mode state and the narrow bridge between Matrix3's existing
 * scene menu entries and the external developer UI.
 *
 * Matrix3 remains authoritative for scene interaction and world state. Dev Mode
 * mirrors the existing tile movement entry only to recover the already-resolved
 * local tile coordinates; it does not replace scene picking or movement.
 */
public final class DevModeBridge {

    public static final int TILE_SPAWN_MENU_ACTION = 1500;
    private static final int MATRIX3_TILE_ACTION = 23;

    private static volatile boolean enabled;

    private DevModeBridge() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * VERIFIED: action 23 is Matrix3's normal scene-tile movement action. Its
     * menu entry carries the local X/Y later converted to world coordinates by
     * Class319.method4094. Action 60 is the staff/admin teleport-debug path and
     * must not be used as the ordinary tile-menu source.
     */
    static void mirrorTileSpawnEntry(int sourceAction, int localX, int localY) {
        int normalizedAction = sourceAction >= 2000 ? sourceAction - 2000 : sourceAction;
        if (!enabled || normalizedAction != MATRIX3_TILE_ACTION || !isOwnerSession()) {
            return;
        }
        if (Class25.aBool165 || 357782167 * Class25.anInt172 >= 504 || hasTileSpawnEntry()) {
            return;
        }

        Class572_Sub12_Sub10 entry = new Class572_Sub12_Sub10(
                "Dev > Spawn...",
                "",
                -646491435 * client.anInt8751,
                TILE_SPAWN_MENU_ACTION,
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

    /**
     * Handles only the custom Dev Mode action and leaves every normal Matrix3
     * menu action untouched.
     */
    static boolean handleMenuAction(int action, int localX, int localY) {
        if (action != TILE_SPAWN_MENU_ACTION) {
            return false;
        }
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

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                DevSpawnBrowserWindow.open(worldX, worldY, plane);
            }
        });
        return true;
    }

    private static boolean isOwnerSession() {
        return ClientConsoleBridge.hasLocalPlayer() && ClientConsoleBridge.getRights() >= 2;
    }

    private static boolean hasTileSpawnEntry() {
        for (Class572_Sub12_Sub10 entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7932((byte) 50);
                entry != null;
                entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7926(1709126908)) {
            int action = entry.anInt11402 * -44467871;
            if (action >= 2000) {
                action -= 2000;
            }
            if (action == TILE_SPAWN_MENU_ACTION) {
                return true;
            }
        }
        return false;
    }
}
