package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

/**
 * Player-facing continuous-chain ConveyorRun placement tool.
 *
 * Point A/B are selected from Matrix3's already-resolved Construction hovered
 * tile. After a successful A -> B submission, B becomes the next Point A so the
 * player can author A -> B -> C -> D without reselecting every seam. The client
 * owns only preview/input; the server remains authoritative for endpoint
 * snapping, plot validation, persistent ConveyorRun creation and belt links.
 */
public final class ConveyorPlacementController {

    private static volatile boolean enabled;
    private static volatile Anchor start;
    private static volatile int segmentAxis = ConveyorRunPreview.ROUTE_AUTO;
    private static volatile String status = "Conveyor: choose Point A.";
    private static boolean inputListenerInstalled;

    private ConveyorPlacementController() {
    }

    public static synchronized String setEnabled(boolean value) {
        if (enabled == value) {
            return status;
        }
        enabled = value;
        start = null;
        segmentAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        if (enabled) {
            ensureInputListener();
            status = "Conveyor armed: click Point A, then endpoints to continue the chain. Right-click ends the chain.";
        } else {
            status = "Conveyor placement disabled.";
        }
        return status;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getStatus() {
        return status;
    }

    /**
     * Called whenever Construction's verified tile hover owner changes.
     */
    static void onHoveredTileChanged() {
        if (!enabled || start == null) {
            return;
        }
        ConstructionPlacementController.HoverTile hover =
                ConstructionPlacementController.getHoveredTile();
        if (hover == null || hover.getPlane() != start.getPlane()
                || hover.getWorldX() == start.getWorldX()
                    && hover.getWorldY() == start.getWorldY()) {
            ConveyorRunPreview.clearPlacementPreview();
            return;
        }

        if (segmentAxis == ConveyorRunPreview.ROUTE_AUTO) {
            segmentAxis = chooseSegmentAxis(start, hover);
        }
        int[] projected = projectStraightEnd(start, hover, segmentAxis);
        if (projected[0] == start.getWorldX()
                && projected[1] == start.getWorldY()) {
            ConveyorRunPreview.clearPlacementPreview();
            status = "Conveyor segment locked " + axisLabel(segmentAxis)
                    + ": move along that axis or right-click/Escape to restart.";
            return;
        }

        ConveyorRunPreview.setPlacementPreview(
                start.getWorldX(), start.getWorldY(),
                projected[0], projected[1],
                start.getPlane(), segmentAxis);
        status = "Conveyor preview A=" + start.getWorldX() + "," + start.getWorldY()
                + " -> B=" + projected[0] + "," + projected[1]
                + " straight=" + axisLabel(segmentAxis) + " LOCKED"
                + " (" + String.format("%.2f",
                        distanceTiles(start.getWorldX(), start.getWorldY(),
                                projected[0], projected[1])) + " tiles).";
    }

    /**
     * Escape uses this before the palette itself closes. For Conveyor, the first
     * Escape/right-click ends the active chain but keeps the Conveyor tool armed.
     */
    public static synchronized boolean cancelPendingEndpoint() {
        if (!enabled || start == null) {
            return false;
        }
        start = null;
        segmentAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        status = "Conveyor chain ended. Click a new Point A.";
        return true;
    }

    public static synchronized void cancel() {
        start = null;
        segmentAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        enabled = false;
        status = "Conveyor placement cancelled.";
    }

    private static synchronized void handleLeftClick() {
        if (!enabled) {
            return;
        }
        ConstructionPlacementController.HoverTile hover =
                ConstructionPlacementController.getHoveredTile();
        if (hover == null) {
            status = "Conveyor: hover a valid settlement tile first.";
            return;
        }

        if (start == null) {
            start = new Anchor(
                    hover.getWorldX(), hover.getWorldY(), hover.getPlane());
            segmentAxis = ConveyorRunPreview.ROUTE_AUTO;
            ConveyorRunPreview.clearPlacementPreview();
            status = "Conveyor Point A set at "
                    + start.getWorldX() + "," + start.getWorldY()
                    + ". Move toward Point B; the first movement locks this segment E/W or N/S.";
            return;
        }

        if (hover.getPlane() != start.getPlane()) {
            status = "Conveyor Point B must be on the same plane as Point A.";
            return;
        }
        if (hover.getWorldX() == start.getWorldX()
                && hover.getWorldY() == start.getWorldY()) {
            status = "Conveyor Point B must be different from Point A.";
            return;
        }

        if (segmentAxis == ConveyorRunPreview.ROUTE_AUTO) {
            segmentAxis = chooseSegmentAxis(start, hover);
        }
        int[] projected = projectStraightEnd(start, hover, segmentAxis);
        if (projected[0] == start.getWorldX()
                && projected[1] == start.getWorldY()) {
            status = "Conveyor segment is locked " + axisLabel(segmentAxis)
                    + "; move along that axis before placing Point B.";
            return;
        }

        String command = "settlementconveyorcreate "
                + start.getWorldX() + " " + start.getWorldY() + " "
                + projected[0] + " " + projected[1] + " "
                + start.getPlane();
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null) {
            status = "Conveyor placement failed to queue: " + error;
            return;
        }

        double length = distanceTiles(
                start.getWorldX(), start.getWorldY(), projected[0], projected[1]);
        String committedAxis = axisLabel(segmentAxis);
        int plane = start.getPlane();

        /*
         * Continuous-chain ownership: the committed Point B becomes the next
         * Point A immediately. The next segment receives a fresh axis lock so a
         * player can turn 90 degrees without the current segment flipping while
         * the mouse moves around.
         */
        start = new Anchor(projected[0], projected[1], plane);
        segmentAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        status = "Straight ConveyorRun create queued ("
                + String.format("%.2f", length) + " tiles, "
                + committedAxis + "). Continue from B at "
                + start.getWorldX() + "," + start.getWorldY()
                + "; move to lock the next segment axis. Right-click/Escape ends the chain.";
    }

    private static int chooseSegmentAxis(
            Anchor a,
            ConstructionPlacementController.HoverTile b) {
        int dx = b.getWorldX() - a.getWorldX();
        int dy = b.getWorldY() - a.getWorldY();
        return Math.abs(dx) >= Math.abs(dy)
                ? ConveyorRunPreview.ROUTE_X_FIRST
                : ConveyorRunPreview.ROUTE_Y_FIRST;
    }

    private static int[] projectStraightEnd(
            Anchor a,
            ConstructionPlacementController.HoverTile b,
            int axis) {
        if (axis == ConveyorRunPreview.ROUTE_Y_FIRST) {
            return new int[] {
                    a.getWorldX(), b.getWorldY(),
                    ConveyorRunPreview.ROUTE_Y_FIRST
            };
        }
        return new int[] {
                b.getWorldX(), a.getWorldY(),
                ConveyorRunPreview.ROUTE_X_FIRST
        };
    }

    private static double distanceTiles(
            int startX, int startY, int endX, int endY) {
        return Math.abs(endX - startX) + Math.abs(endY - startY);
    }

    private static String axisLabel(int axis) {
        return axis == ConveyorRunPreview.ROUTE_Y_FIRST
                ? "N/S" : "E/W";
    }

    private static synchronized void ensureInputListener() {
        if (inputListenerInstalled) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (!(event instanceof MouseEvent) || !enabled) {
                    return;
                }
                MouseEvent mouse = (MouseEvent) event;
                Canvas canvas = Class584.aCanvas7745;
                if (canvas == null || mouse.getSource() != canvas
                        || mouse.getID() != MouseEvent.MOUSE_PRESSED) {
                    return;
                }

                if (mouse.getButton() == MouseEvent.BUTTON1) {
                    handleLeftClick();
                    mouse.consume();
                } else if (mouse.getButton() == MouseEvent.BUTTON3) {
                    if (cancelPendingEndpoint()) {
                        mouse.consume();
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
        inputListenerInstalled = true;
    }

    /**
     * Local immutable chain anchor. Construction's HoverTile constructor is
     * intentionally private to its picker owner, so Conveyor stores only the
     * resolved coordinates it needs between committed segments.
     */
    private static final class Anchor {
        private final int worldX;
        private final int worldY;
        private final int plane;

        Anchor(int worldX, int worldY, int plane) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }

        int getWorldX() {
            return worldX;
        }

        int getWorldY() {
            return worldY;
        }

        int getPlane() {
            return plane;
        }
    }
}
