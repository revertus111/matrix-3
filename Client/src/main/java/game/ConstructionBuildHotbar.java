package game;

import java.awt.Canvas;
import java.awt.event.KeyEvent;

import game.ConstructionPlacementController.BuildPiece;

/**
 * Construction build-bar controller.
 *
 * Visual ownership belongs to Matrix3's native action bar (interface 1430).
 * This client class owns only Construction tool semantics and the local
 * key/click bridge. The player's real combat/action-bar shortcuts remain
 * server-owned and are never replaced in persistent ActionBar state.
 */
public final class ConstructionBuildHotbar {

    private static final int SLOT_COUNT = 9;

    private static volatile boolean visible;
    private static volatile String lastObjectKey;
    private static volatile String lastObjectName = "Object";
    private static volatile String status = "Construction build bar 1-9";

    private ConstructionBuildHotbar() {
    }

    public static void show() {
        if (visible) {
            return;
        }
        visible = true;
        String error = ClientConsoleBridge.queueConsoleCommand("settlementbuildbaropen");
        status = error == null
                ? "Native Construction build bar requested."
                : "Build bar open failed: " + error;
    }

    public static void hide() {
        if (!visible) {
            return;
        }
        visible = false;
        String error = ClientConsoleBridge.queueConsoleCommand("settlementbuildbarclose");
        if (error != null) {
            status = "Build bar close failed: " + error;
        }
    }

    /**
     * Retained for ConstructionPaletteOverlay's refresh lifecycle. Native
     * interface 1430 owns rendering; this only tracks the most recent object.
     */
    public static void refresh(Canvas canvas) {
        if (visible) {
            rememberSelectedObject();
        }
    }

    public static boolean isVisible() {
        return visible;
    }

    public static String getStatus() {
        return status;
    }

    public static boolean handleKey(KeyEvent event) {
        if (!visible || event == null || event.getID() != KeyEvent.KEY_PRESSED
                || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }

        int index = -1;
        switch (event.getKeyCode()) {
        case KeyEvent.VK_1: index = 0; break;
        case KeyEvent.VK_2: index = 1; break;
        case KeyEvent.VK_3: index = 2; break;
        case KeyEvent.VK_4: index = 3; break;
        case KeyEvent.VK_5: index = 4; break;
        case KeyEvent.VK_6: index = 5; break;
        case KeyEvent.VK_7: index = 6; break;
        case KeyEvent.VK_8: index = 7; break;
        case KeyEvent.VK_9: index = 8; break;
        default:
            return false;
        }

        activate(index);
        event.consume();
        return true;
    }

    /**
     * Server-native action-bar clicks return through packet 69
     * (CLIENT_COMMAND) as "constructionbar <1-9>".
     */
    public static boolean handleServerCommand(String rawCommand) {
        if (rawCommand == null) {
            return false;
        }
        String command = rawCommand.trim();
        if (!command.startsWith("constructionbar ")) {
            return false;
        }
        if (!visible) {
            return true;
        }
        try {
            int slot = Integer.parseInt(command.substring("constructionbar ".length()).trim());
            if (slot < 1 || slot > SLOT_COUNT) {
                status = "Invalid Construction build-bar slot " + slot + ".";
                return true;
            }
            activate(slot - 1);
        } catch (NumberFormatException ex) {
            status = "Invalid Construction build-bar command.";
        }
        return true;
    }

    private static void activate(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            return;
        }

        switch (index) {
        case 0:
            BuildPiece rail = findPiece("basic-rail");
            if (rail == null) {
                status = "Rail tool is unavailable.";
                return;
            }
            ConstructionPlacementController.select(rail);
            status = RailRoutePreview.setToolMode(RailRoutePreview.ToolMode.NORMAL);
            return;
        case 1:
            BuildPiece junctionRail = findPiece("basic-rail");
            if (junctionRail == null) {
                status = "Rail tool is unavailable.";
                return;
            }
            ConstructionPlacementController.select(junctionRail);
            status = RailRoutePreview.setToolMode(RailRoutePreview.ToolMode.JUNCTION);
            return;
        case 2:
            BuildPiece crossingRail = findPiece("basic-rail");
            if (crossingRail != null) {
                ConstructionPlacementController.select(crossingRail);
            }
            status = RailRoutePreview.setToolMode(RailRoutePreview.ToolMode.CROSSING);
            return;
        case 3:
            BuildPiece splitterRail = findPiece("basic-rail");
            if (splitterRail != null) {
                ConstructionPlacementController.select(splitterRail);
            }
            status = RailRoutePreview.setToolMode(RailRoutePreview.ToolMode.SPLITTER);
            return;
        case 4:
            rememberSelectedObject();
            BuildPiece object = findPiece(lastObjectKey);
            if (object == null) {
                status = "Select a non-rail build object in the palette first.";
                return;
            }
            ConstructionPlacementController.select(object);
            status = lastObjectName + " armed from Object slot.";
            return;
        case 5:
            ConstructionPlacementController.setEraserMode(true);
            status = "Eraser armed.";
            return;
        case 6:
            if (!ConstructionPlacementController.isArmed()
                    || ConstructionPlacementController.isEraserMode()) {
                status = "Rotate needs an armed build piece.";
                return;
            }
            ConstructionPlacementController.rotate(1);
            status = "Rotated selected build piece +90 degrees.";
            return;
        case 7:
            String error = ClientConsoleBridge.queueConsoleCommand("itembrowser settlement undo");
            status = error == null ? "Undo queued." : error;
            return;
        case 8:
            status = "Favorites reserved for assignable build slots.";
            return;
        default:
            return;
        }
    }

    private static void rememberSelectedObject() {
        BuildPiece selected = ConstructionPlacementController.getSelectedPiece();
        if (selected == null || "basic-rail".equals(selected.getKey())) {
            return;
        }
        lastObjectKey = selected.getKey();
        lastObjectName = selected.getDisplayName();
    }

    private static BuildPiece findPiece(String key) {
        if (key == null) {
            return null;
        }
        for (BuildPiece piece : ConstructionPlacementController.getPieces()) {
            if (key.equals(piece.getKey())) {
                return piece;
            }
        }
        return null;
    }
}
