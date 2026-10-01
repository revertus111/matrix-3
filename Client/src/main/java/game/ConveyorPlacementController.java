package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

/**
 * Player-facing two-click ConveyorRun placement tool.
 *
 * Point A/B are selected from Matrix3's already-resolved Construction hovered
 * tile. The client owns only preview/input; the server remains authoritative for
 * endpoint snapping, plot validation and persistent ConveyorRun creation.
 */
public final class ConveyorPlacementController {

    private static volatile boolean enabled;
    private static volatile ConstructionPlacementController.HoverTile start;
    private static volatile int routeAxis = ConveyorRunPreview.ROUTE_AUTO;
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
        routeAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        if (enabled) {
            ensureInputListener();
            status = "Conveyor armed: click Point A, then Point B. Right-click cancels A.";
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
        if (routeAxis == ConveyorRunPreview.ROUTE_AUTO) {
            int dx = hover.getWorldX() - start.getWorldX();
            int dy = hover.getWorldY() - start.getWorldY();
            routeAxis = Math.abs(dx) >= Math.abs(dy)
                    ? ConveyorRunPreview.ROUTE_X_FIRST
                    : ConveyorRunPreview.ROUTE_Y_FIRST;
        }
        ConveyorRunPreview.setPlacementPreview(
                start.getWorldX(), start.getWorldY(),
                hover.getWorldX(), hover.getWorldY(),
                start.getPlane(), routeAxis);
        status = "Conveyor preview A=" + start.getWorldX() + "," + start.getWorldY()
                + " -> B=" + hover.getWorldX() + "," + hover.getWorldY()
                + " route=" + routeLabel(routeAxis)
                + " (" + String.format("%.2f", distanceTiles(start, hover)) + " tiles).";
    }

    /**
     * Escape uses this before the palette itself closes.
     */
    public static synchronized boolean cancelPendingEndpoint() {
        if (!enabled || start == null) {
            return false;
        }
        start = null;
        routeAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        status = "Conveyor Point A cancelled. Click a new Point A.";
        return true;
    }

    public static synchronized void cancel() {
        start = null;
        routeAxis = ConveyorRunPreview.ROUTE_AUTO;
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
            start = hover;
            routeAxis = ConveyorRunPreview.ROUTE_AUTO;
            ConveyorRunPreview.clearPlacementPreview();
            status = "Conveyor Point A set at "
                    + start.getWorldX() + "," + start.getWorldY()
                    + ". Move to Point B and click.";
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

        int committedRouteAxis = routeAxis;
        if (committedRouteAxis == ConveyorRunPreview.ROUTE_AUTO) {
            int dx = hover.getWorldX() - start.getWorldX();
            int dy = hover.getWorldY() - start.getWorldY();
            committedRouteAxis = Math.abs(dx) >= Math.abs(dy)
                    ? ConveyorRunPreview.ROUTE_X_FIRST
                    : ConveyorRunPreview.ROUTE_Y_FIRST;
        }
        String command = "settlementconveyorcreate "
                + start.getWorldX() + " " + start.getWorldY() + " "
                + hover.getWorldX() + " " + hover.getWorldY() + " "
                + start.getPlane() + " " + committedRouteAxis;
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null) {
            status = "Conveyor placement failed to queue: " + error;
            return;
        }

        double length = distanceTiles(start, hover);
        String committedRoute = routeLabel(committedRouteAxis);
        start = null;
        routeAxis = ConveyorRunPreview.ROUTE_AUTO;
        ConveyorRunPreview.clearPlacementPreview();
        status = "ConveyorRun create queued (" + String.format("%.2f", length)
                + " tiles, " + committedRoute
                + "). Click a new Point A to keep building.";
    }

    private static double distanceTiles(
            ConstructionPlacementController.HoverTile a,
            ConstructionPlacementController.HoverTile b) {
        return Math.abs(b.getWorldX() - a.getWorldX())
                + Math.abs(b.getWorldY() - a.getWorldY());
    }

    private static String routeLabel(int axis) {
        return axis == ConveyorRunPreview.ROUTE_Y_FIRST
                ? "Y-first" : "X-first";
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
}
