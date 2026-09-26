package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Client-only Point-A -> Point-B rail route visual proof.
 *
 * V2 proves drag UX, Manhattan routing, straight-axis orientation and an
 * authored multi-tile CURVE pattern at the bend. The accepted three-piece curve
 * uses ordinary neighboring type-22 object origins, so same-tile overlay is not
 * required for the current route. A single-object curve remains fallback
 * authoring support. It does not place real world objects, own
 * collision, persist gameplay state, consume settlement resources, or attempt
 * switch/junction auto-tiling.
 */
public final class RailRoutePreview {

    public enum RouteOrder {
        X_THEN_Y("X then Y"),
        Y_THEN_X("Y then X");

        private final String displayName;

        RouteOrder(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int MODEL_FLAGS = 2048;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;
    private static final int MAX_GESTURE_TILES = 256;
    private static final int MAX_NETWORK_PIECES = 4096;
    private static final long HOVER_STALE_MS = 1250L;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean enabled;
    private static volatile boolean dragging;
    private static volatile boolean committed;

    private static volatile int objectId = -1;
    private static volatile int objectType = 22;
    private static volatile int horizontalRotation;
    private static volatile String objectName = "Rail";

    private static volatile int curveObjectId = -1;
    private static volatile int curveObjectType = 22;
    private static volatile int curveBaseRotation;
    private static volatile int curveRotationOffset;
    private static volatile String curveObjectName = "Curve";
    private static volatile RailCompositeLibrary.CompositeDefinition curveComposite;

    private static volatile RouteOrder routeOrder = RouteOrder.X_THEN_Y;

    private static volatile int hoveredWorldX = -1;
    private static volatile int hoveredWorldY = -1;
    private static volatile int hoveredPlane = -1;
    private static volatile long hoveredAtMillis;

    private static volatile int liveStartX = -1;
    private static volatile int liveStartY = -1;
    private static volatile int liveEndX = -1;
    private static volatile int liveEndY = -1;
    private static volatile int livePlane = -1;

    private static volatile int committedStartX = -1;
    private static volatile int committedStartY = -1;
    private static volatile int committedEndX = -1;
    private static volatile int committedEndY = -1;
    private static volatile int committedPlane = -1;
    private static volatile int continuationHorizontalDirection;
    private static volatile int continuationVerticalDirection;
    private static volatile boolean editingEndpointB;
    /*
     * Until dedicated crossing/switch art exists, a gesture may branch FROM an
     * existing rail or connect INTO one, but it must not pass through existing
     * topology. First contact becomes the authored endpoint. This prevents one
     * drag from silently creating multiple degree-3/4 junctions and rewriting
     * nearby curve footprints.
     */
    private static volatile boolean liveDragLockedAtExistingRail;
    private static volatile int liveExistingContactX = -1;
    private static volatile int liveExistingContactY = -1;
    private static volatile boolean liveExistingContactRequiresSpecialTool;
    private static volatile boolean liveDragLockedBySharpTurn;
    private static volatile int liveSharpTurnX = -1;
    private static volatile int liveSharpTurnY = -1;
    private static final java.util.List<RoutePiece> authoredPath =
            new java.util.ArrayList<RoutePiece>();
    /*
     * Ordered cardinal tiles sampled while Left is held. This is the authored
     * route for the active gesture; Point A/Point B Manhattan routing is legacy
     * preview behavior and must not decide Rail Network V1 topology.
     */
    private static final java.util.List<int[]> liveDragPath =
            new java.util.ArrayList<int[]>();
    /*
     * Rail Network V1 authority: logical occupied rail tiles. Physical RS3
     * objects are derived from this topology; they are not the authored route.
     */
    private static final java.util.LinkedHashSet<String> logicalNetwork =
            new java.util.LinkedHashSet<String>();
    /*
     * Explicit authored cardinal edges. Tile adjacency alone is NOT connectivity:
     * parallel/nearby rails may occupy neighboring tiles without being joined.
     * This preserves the user's gesture topology and prevents later routes from
     * retroactively turning old corners into junctions/straights.
     */
    private static final java.util.LinkedHashSet<String> logicalConnections =
            new java.util.LinkedHashSet<String>();
    private static volatile int pathEndX = -1;
    private static volatile int pathEndY = -1;
    private static volatile int pathPlane = -1;
    private static volatile int pathIncomingX;
    private static volatile int pathIncomingY;

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String eventState = "A->B rail preview disabled.";
    private static volatile String renderState = "hidden";
    private static volatile boolean debugEnabled;
    private static volatile long debugOperationId;
    private static volatile String debugReport = "Rail debug: no committed route yet.";
    private static boolean inputListenerInstalled;

    private RailRoutePreview() {
    }

    public static void configure(String name, int id, int type, int baseHorizontalRotation,
            RouteOrder order) {
        objectName = name == null || name.trim().isEmpty() ? "Rail" : name;
        objectId = id;
        objectType = clamp(type, 0, 22);
        horizontalRotation = baseHorizontalRotation & 0x3;
        if (order != null) {
            routeOrder = order;
        }
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = "Route rail configured: " + objectName + " id=" + objectId
                + " type=" + objectType + " northSouthRot=" + horizontalRotation
                + " eastWestRot=" + eastWestRotation() + ".";
    }

    public static void configureCurve(String name, int id, int type, int baseRotation) {
        curveObjectName = name == null || name.trim().isEmpty() ? "Curve" : name;
        curveObjectId = id;
        curveObjectType = clamp(type, 0, 22);
        curveBaseRotation = baseRotation & 0x3;
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = "Curve rail configured: " + curveObjectName + " id=" + curveObjectId
                + " type=" + curveObjectType + " baseRot=" + curveBaseRotation
                + " mapOffset=" + curveRotationOffset + ".";
    }

    public static int getConfiguredCurveObjectId() {
        return curveObjectId;
    }
    public static boolean reloadCurveComposite() {
        curveComposite = RailCompositeLibrary.findAcceptedCurveForRoute();
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = curveComposite == null
                ? "No authored curve pattern found; single-object curve fallback remains available."
                : "Loaded curve pattern: " + curveComposite.describe()
                        + " (accepted elbow-anchored layout).";
        return curveComposite != null;
    }

    public static String getConfiguredCurveCompositeName() {
        RailCompositeLibrary.CompositeDefinition composite = curveComposite;
        return composite == null ? "none" : composite.getName();
    }


    public static void setCurveRotationOffset(int offset) {
        curveRotationOffset = offset & 0x3;
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = "Curve rotation map offset set to R" + curveRotationOffset + ".";
    }

    public static int getCurveRotationOffset() {
        return curveRotationOffset;
    }

    public static void setRouteOrder(RouteOrder order) {
        if (order == null) {
            return;
        }
        routeOrder = order;
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = "Route order: " + routeOrder + ".";
    }

    public static RouteOrder getRouteOrder() {
        return routeOrder;
    }

    public static int getConfiguredObjectId() {
        return objectId;
    }

    public static void setEnabled(boolean value) {
        if (value) {
            if (objectId < 0) {
                eventState = "Choose/configure a straight rail before enabling A->B preview.";
                return;
            }
            ensureInputListener();

            // Prevent two developer drag systems from owning the same mouse gesture.
            ConstructionRadialSelection.setWorkerControlEnabled(false);
            ObjectLabPreview.hide();
            ObjectCompositePreview.hide();
            reloadCurveComposite();

            enabled = true;
            eventState = "Rail Network V1 ON. Drag new track or start from any authored rail tile to extend/branch.";
        } else {
            if (dragging) {
                cancelActiveDrag();
            }
            enabled = false;
            lastRenderedCycle = Integer.MIN_VALUE;
            renderState = "hidden";
            eventState = "A->B rail preview OFF.";
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }


    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static String setDebugEnabled(boolean value) {
        if (!value) {
            debugEnabled = false;
            eventState = "Rail debug OFF.";
            return eventState;
        }
        java.io.File root = getDebugRootDirectory();
        java.io.File shots = new java.io.File(root, "rail_debug");
        if ((!root.exists() && !root.mkdirs()) || (!shots.exists() && !shots.mkdirs())) {
            debugEnabled = false;
            eventState = "Rail debug failed: cannot create " + root.getAbsolutePath();
            return eventState;
        }
        debugEnabled = true;
        eventState = "Rail debug ON -> " + root.getAbsolutePath();
        return eventState;
    }

    public static String copyDebugReportToClipboard() {
        String report = debugReport;
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(report), null);
            eventState = "Rail debug copied to clipboard.";
            return eventState;
        } catch (Throwable t) {
            eventState = "Rail debug copy failed: " + t.getClass().getSimpleName();
            return eventState;
        }
    }

    private static java.io.File getDebugRootDirectory() {
        java.io.File cwd = new java.io.File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        if ("Client".equalsIgnoreCase(cwd.getName())) {
            return new java.io.File(cwd, "data/construction");
        }
        java.io.File clientDir = new java.io.File(cwd, "Client");
        if (clientDir.isDirectory()) {
            return new java.io.File(clientDir, "data/construction");
        }
        return new java.io.File(cwd, "data/construction");
    }

    private static void appendDebugReportToFile(long operationId, String report,
            java.util.List<RoutePiece> oldPieces, java.util.List<RoutePiece> newPieces,
            java.util.List<int[]> gesture) {
        java.io.FileWriter writer = null;
        try {
            java.io.File dir = getDebugRootDirectory();
            if (!dir.exists() && !dir.mkdirs()) {
                eventState = "Rail debug directory could not be created: " + dir.getPath();
                return;
            }
            java.io.File file = new java.io.File(dir, "rail_runtime_debug.txt");
            writer = new java.io.FileWriter(file, true);
            writer.write("\r\n============================================================\r\n");
            writer.write("Rail Network V1 runtime commit " + operationId
                    + " @ " + new java.util.Date() + "\r\n");
            writer.write("debugBuild=SNAPSHOT_PREVIEW_V2 gestureLimit=" + MAX_GESTURE_TILES
                    + " networkLimit=" + MAX_NETWORK_PIECES + "\r\n");
            writer.write("event=" + eventState + "\r\n");
            writer.write("render=" + renderState + "\r\n");
            writer.write("logicalTiles=" + logicalNetwork.size()
                    + " oldPhysical=" + (oldPieces == null ? 0 : oldPieces.size())
                    + " newPhysical=" + (newPieces == null ? 0 : newPieces.size()) + "\r\n");
            writer.write("liveGesture=");
            for (int index = 0; index < gesture.size(); index++) {
                int[] tile = gesture.get(index);
                if (index > 0) writer.write(" -> ");
                writer.write(tile[0] + "," + tile[1] + "," + tile[2]);
            }
            writer.write("\r\n");
            writer.write(report == null ? "(no debug report)" : report);
            if (report == null || !report.endsWith("\n")) writer.write("\r\n");
            writer.flush();
        } catch (Throwable t) {
            eventState = "Rail debug file write failed: " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage());
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private static void scheduleDebugWorldScreenshot(
            final long operationId, final String report,
            final java.util.List<RoutePiece> newPieces, final int logicalCount,
            final int startX, final int startY, final int endX, final int endY,
            final int plane, final String event) {
        final java.util.List<RoutePiece> pieceSnapshot =
                new java.util.ArrayList<RoutePiece>(newPieces);
        Thread captureThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(650L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                captureDebugScreenshot(operationId, report, pieceSnapshot, logicalCount,
                        startX, startY, endX, endY, plane, event);
            }
        }, "RailDebugCapture-" + operationId);
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private static void captureDebugScreenshot(
            long operationId, String report,
            java.util.List<RoutePiece> newPieces, int logicalCount,
            int startX, int startY, int endX, int endY, int plane, String event) {
        try {
            java.io.File dir = new java.io.File(getDebugRootDirectory(), "rail_debug");
            if (!dir.exists() && !dir.mkdirs()) {
                eventState = "Rail debug screenshot directory could not be created: " + dir.getPath();
                return;
            }

            java.awt.Rectangle bounds = new java.awt.Rectangle(
                    java.awt.Toolkit.getDefaultToolkit().getScreenSize());
            java.awt.image.BufferedImage image = new java.awt.Robot().createScreenCapture(bounds);

            java.awt.Graphics2D g = image.createGraphics();
            try {
                g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 14));
                java.util.List<String> lines = buildScreenshotDebugLines(
                        operationId, report, newPieces, logicalCount,
                        startX, startY, endX, endY, plane, event);
                int lineHeight = 18;
                int panelWidth = 590;
                int panelHeight = Math.min(image.getHeight() - 20,
                        20 + (lines.size() * lineHeight));
                g.setColor(new java.awt.Color(0, 0, 0, 190));
                g.fillRect(10, 10, panelWidth, panelHeight);
                g.setColor(java.awt.Color.WHITE);
                int y = 30;
                for (String line : lines) {
                    if (y > panelHeight) break;
                    g.drawString(line, 20, y);
                    y += lineHeight;
                }
            } finally {
                g.dispose();
            }

            String name = String.format(java.util.Locale.ROOT,
                    "rail_%04d.png", Long.valueOf(operationId));
            javax.imageio.ImageIO.write(image, "png", new java.io.File(dir, name));
        } catch (Throwable t) {
            eventState = "Rail debug screenshot failed: " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage());
        }
    }

    private static java.util.List<String> buildScreenshotDebugLines(
            long operationId, String report,
            java.util.List<RoutePiece> newPieces, int logicalCount,
            int startX, int startY, int endX, int endY, int plane, String event) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add("RAIL NETWORK V1 DEBUG  OP " + operationId + "  SNAPSHOT_PREVIEW_V2");
        lines.add("logical=" + logicalCount + " physical="
                + (newPieces == null ? 0 : newPieces.size())
                + " limits=" + MAX_GESTURE_TILES + "/" + MAX_NETWORK_PIECES);
        lines.add("A=" + startX + "," + startY
                + "  B=" + endX + "," + endY
                + "  plane=" + plane);
        lines.add("event=" + event);
        lines.add("capture=POST_QUEUE +650ms (world-state evidence)");
        if (newPieces != null) {
            int index = 0;
            for (RoutePiece piece : newPieces) {
                lines.add(String.format(java.util.Locale.ROOT,
                        "P%02d id=%d type=%d rot=%d @ %d,%d,%d",
                        Integer.valueOf(index++), Integer.valueOf(piece.getObjectId()),
                        Integer.valueOf(piece.getObjectType()), Integer.valueOf(piece.getRotation()),
                        Integer.valueOf(piece.getWorldX()), Integer.valueOf(piece.getWorldY()),
                        Integer.valueOf(piece.getPlane())));
                if (lines.size() >= 30) {
                    lines.add("... full piece list in rail_runtime_debug.txt");
                    break;
                }
            }
        }
        return lines;
    }

    public static String getDebugScreenshotDirectory() {
        return new java.io.File(getDebugRootDirectory(), "rail_debug").getAbsolutePath();
    }

    public static String getDebugOutputDirectory() {
        return getDebugRootDirectory().getAbsolutePath();
    }

    public static String getDebugFilePath() {
        return new java.io.File(getDebugRootDirectory(), "rail_runtime_debug.txt").getAbsolutePath();
    }

    public static String getDebugReport() {
        return debugReport;
    }


    public static java.util.List<String> getDebugOverlayLines() {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        if (!debugEnabled) {
            return lines;
        }
        String[] reportLines = debugReport.split("\\n");
        for (String line : reportLines) {
            if (line == null || line.length() == 0) {
                continue;
            }
            String compact = line.replace('\t', ' ');
            if (compact.startsWith("RAIL_DEBUG")
                    || compact.startsWith("MODE")
                    || compact.startsWith("CURRENT")
                    || compact.startsWith("PREVIOUS")
                    || compact.startsWith("OLD_PIECES")
                    || compact.startsWith("OLD_PIECE")
                    || compact.startsWith("SEAM")
                    || compact.startsWith("SEAM_CURVE")
                    || compact.startsWith("FOOTPRINT")
                    || compact.startsWith("PIECES")
                    || compact.startsWith("PIECE")) {
                lines.add(compact);
            }
            if (lines.size() >= 22) {
                break;
            }
        }
        return lines;
    }

    public static void clearRoute() {
        dragging = false;
        editingEndpointB = false;
        committed = false;
        authoredPath.clear();
        logicalNetwork.clear();
        logicalConnections.clear();
        pathEndX = -1;
        pathEndY = -1;
        pathPlane = -1;
        pathIncomingX = 0;
        pathIncomingY = 0;
        liveStartX = -1;
        liveStartY = -1;
        liveEndX = -1;
        liveEndY = -1;
        livePlane = -1;
        committedStartX = -1;
        committedStartY = -1;
        committedEndX = -1;
        committedEndY = -1;
        committedPlane = -1;
        liveDragLockedAtExistingRail = false;
        liveExistingContactX = -1;
        liveExistingContactY = -1;
        liveExistingContactRequiresSpecialTool = false;
        liveDragLockedBySharpTurn = false;
        liveSharpTurnX = -1;
        liveSharpTurnY = -1;
        lastRenderedCycle = Integer.MIN_VALUE;
        renderState = "cleared";
        eventState = "Rail network draft cleared.";
    }

    public static String getStatus() {
        StringBuilder status = new StringBuilder(192);
        status.append(enabled ? "RAIL NETWORK V1 ON" : "RAIL NETWORK V1 OFF");
        status.append(" | straight=").append(objectId < 0 ? "none" : objectId + "/" + objectType)
                .append(" nsRot=").append(horizontalRotation)
                .append(" ewRot=").append(eastWestRotation())
                .append(" | curve=").append(curveObjectId < 0
                        ? "none" : curveObjectId + "/" + curveObjectType)
                .append(" baseRot=").append(curveBaseRotation)
                .append(" | composite=").append(getConfiguredCurveCompositeName())
                .append(" mapOffset=R").append(curveRotationOffset)
                .append(" | order=").append(routeOrder);

        if (dragging) {
            status.append(" | DRAG A=")
                    .append(liveStartX).append(',').append(liveStartY)
                    .append(" B=").append(liveEndX).append(',').append(liveEndY)
                    .append(" tiles=").append(routeTileCount(
                            liveStartX, liveStartY, liveEndX, liveEndY));
        } else if (committed) {
            status.append(" | COMMITTED A=")
                    .append(committedStartX).append(',').append(committedStartY)
                    .append(" B=").append(committedEndX).append(',').append(committedEndY)
                    .append(" tiles=").append(routeTileCount(
                            committedStartX, committedStartY, committedEndX, committedEndY));
        } else {
            status.append(" | no route");
        }

        status.append(" | ").append(eventState);
        status.append(" | render=").append(renderState);
        return status.toString();
    }

    /**
     * Mirrors Matrix3's already-resolved action-23 world tile.
     * No second picker is introduced.
     */
    static void observeSceneMenuTile(int sourceAction, int localX, int localY) {
        if (!enabled) {
            return;
        }

        int normalizedAction = sourceAction >= 2000 ? sourceAction - 2000 : sourceAction;
        if (normalizedAction != MATRIX3_TILE_ACTION || client.aClass613_8605 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return;
        }

        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return;
        }

        hoveredWorldX = sceneBase.localX * -2109597897 + localX;
        hoveredWorldY = sceneBase.localY * 417324155 + localY;
        hoveredPlane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;
        hoveredAtMillis = System.currentTimeMillis();

        if (dragging && hoveredPlane == livePlane) {
            appendLiveDragToward(hoveredWorldX, hoveredWorldY);
            if (liveDragLockedBySharpTurn) {
                eventState = "Rail turn blocked at " + liveSharpTurnX + "," + liveSharpTurnY
                        + ": curve footprints would overlap; release and widen the bend.";
            } else if (liveDragLockedAtExistingRail) {
                liveEndX = liveExistingContactX;
                liveEndY = liveExistingContactY;
                eventState = liveExistingContactRequiresSpecialTool
                        ? "Normal Rail stopped at " + liveEndX + "," + liveEndY
                                + ": Junction/Crossing/Splitter required."
                        : "Rail path connected at existing track "
                                + liveEndX + "," + liveEndY
                                + "; release to commit.";
            } else {
                liveEndX = hoveredWorldX;
                liveEndY = hoveredWorldY;
                eventState = "Drawing rail path to " + liveEndX + "," + liveEndY
                        + " (" + liveDragPath.size() + " sampled tile(s)).";
            }
            lastRenderedCycle = Integer.MIN_VALUE;
        }
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!enabled || objectId < 0 || scene == null || renderer == null) {
            return;
        }

        /*
         * Rail Network V1 only renders a client ghost while the mouse gesture
         * is active. Once released, the persistent server-owned world objects
         * are the sole visual authority; retaining the old A->B committed
         * preview creates detached/duplicate rails after a commit.
         */
        if (!dragging) {
            renderState = "idle - server rail visuals";
            return;
        }

        int startX = liveStartX;
        int startY = liveStartY;
        int endX = liveEndX;
        int endY = liveEndY;
        int plane = livePlane;

        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) {
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            renderState = "WAIT scene/object definitions";
            return;
        }

        java.util.LinkedHashSet<String> previewNetwork =
                new java.util.LinkedHashSet<String>(logicalNetwork);
        java.util.LinkedHashSet<String> previewConnections =
                new java.util.LinkedHashSet<String>(logicalConnections);
        addDragPathToTopology(previewNetwork, previewConnections, liveDragPath);
        int rendered = renderResolvedNetwork(
                scene, renderer, sceneBase, definitions, previewNetwork, previewConnections);

        int requested = liveDragPath.size();
        renderState = "DRAW " + rendered + " resolved piece(s) from "
                + requested + " sampled gesture tile(s)"
                + (requested >= MAX_GESTURE_TILES ? " [gesture cap " + MAX_GESTURE_TILES + "]" : "")
                + " snapshot-preview";
    }

    private static int renderResolvedNetwork(Class523 scene, Class106 renderer,
            Class497 sceneBase, Class639_Sub16 definitions) {
        return renderResolvedNetwork(scene, renderer, sceneBase, definitions,
                logicalNetwork, logicalConnections);
    }

    private static int renderResolvedNetwork(Class523 scene, Class106 renderer,
            Class497 sceneBase, Class639_Sub16 definitions,
            java.util.Set<String> network, java.util.Set<String> connections) {
        int rendered = 0;
        for (RoutePiece piece : resolveLogicalNetworkPieces(network, connections)) {
            ObjectDefinitions def = (ObjectDefinitions) definitions.getDefinition(
                    piece.getObjectId(), -1356282071);
            if (def != null && renderPiece(scene, renderer, sceneBase, def,
                    piece.getObjectType(), piece.getWorldX(), piece.getWorldY(),
                    piece.getPlane(), piece.getRotation())) {
                rendered++;
            }
        }
        return rendered;
    }

    private static int renderXThenY(Class523 scene, Class106 renderer, Class497 sceneBase,
            Class639_Sub16 definitions,
            ObjectDefinitions straightDefinition, ObjectDefinitions curveDefinition,
            int startX, int startY, int endX, int endY, int plane) {
        if (startY == endY) {
            return renderHorizontalLine(scene, renderer, sceneBase, straightDefinition,
                    startX, endX, startY, plane);
        }
        if (startX == endX) {
            return renderVerticalLine(scene, renderer, sceneBase, straightDefinition,
                    startY, endY, startX, plane);
        }

        int rendered = 0;
        int attempted = 0;
        int xStep = Integer.compare(endX, startX);
        int yStep = Integer.compare(endY, startY);
        int horizontalNeighborDirection = -xStep;
        int verticalNeighborDirection = yStep;
        CurvePlacement curvePlacement = createCurvePlacement(
                endX, startY, horizontalNeighborDirection, verticalNeighborDirection);

        int x = startX;
        while (x != endX && attempted < MAX_GESTURE_TILES) {
            attempted++;
            if (!curveOccupies(curvePlacement, x, startY)
                    && renderPiece(scene, renderer, sceneBase, straightDefinition,
                            objectType, x, startY, plane, eastWestRotation())) {
                rendered++;
            }
            x += xStep;
        }

        if (attempted >= MAX_GESTURE_TILES) {
            return rendered;
        }

        attempted++;
        if (curvePlacement != null) {
            rendered += renderCurveComposite(
                    scene, renderer, sceneBase, definitions, curvePlacement, plane);
        } else if (curveDefinition != null) {
            if (renderPiece(scene, renderer, sceneBase, curveDefinition, curveObjectType,
                    endX, startY, plane,
                    curveRotationFor(horizontalNeighborDirection, verticalNeighborDirection))) {
                rendered++;
            }
        } else if (renderPiece(scene, renderer, sceneBase, straightDefinition, objectType,
                endX, startY, plane, verticalRotation())) {
            rendered++;
        }

        int y = startY + yStep;
        while (attempted < MAX_GESTURE_TILES) {
            attempted++;
            if (!curveOccupies(curvePlacement, endX, y)
                    && renderPiece(scene, renderer, sceneBase, straightDefinition,
                            objectType, endX, y, plane, verticalRotation())) {
                rendered++;
            }
            if (y == endY) {
                break;
            }
            y += yStep;
        }
        return rendered;
    }

    private static int renderYThenX(Class523 scene, Class106 renderer, Class497 sceneBase,
            Class639_Sub16 definitions,
            ObjectDefinitions straightDefinition, ObjectDefinitions curveDefinition,
            int startX, int startY, int endX, int endY, int plane) {
        if (startY == endY) {
            return renderHorizontalLine(scene, renderer, sceneBase, straightDefinition,
                    startX, endX, startY, plane);
        }
        if (startX == endX) {
            return renderVerticalLine(scene, renderer, sceneBase, straightDefinition,
                    startY, endY, startX, plane);
        }

        int rendered = 0;
        int attempted = 0;
        int xStep = Integer.compare(endX, startX);
        int yStep = Integer.compare(endY, startY);
        int horizontalNeighborDirection = xStep;
        int verticalNeighborDirection = -yStep;
        CurvePlacement curvePlacement = createCurvePlacement(
                startX, endY, horizontalNeighborDirection, verticalNeighborDirection);

        int y = startY;
        while (y != endY && attempted < MAX_GESTURE_TILES) {
            attempted++;
            if (!curveOccupies(curvePlacement, startX, y)
                    && renderPiece(scene, renderer, sceneBase, straightDefinition,
                            objectType, startX, y, plane, verticalRotation())) {
                rendered++;
            }
            y += yStep;
        }

        if (attempted >= MAX_GESTURE_TILES) {
            return rendered;
        }

        attempted++;
        if (curvePlacement != null) {
            rendered += renderCurveComposite(
                    scene, renderer, sceneBase, definitions, curvePlacement, plane);
        } else if (curveDefinition != null) {
            if (renderPiece(scene, renderer, sceneBase, curveDefinition, curveObjectType,
                    startX, endY, plane,
                    curveRotationFor(horizontalNeighborDirection, verticalNeighborDirection))) {
                rendered++;
            }
        } else if (renderPiece(scene, renderer, sceneBase, straightDefinition, objectType,
                startX, endY, plane, eastWestRotation())) {
            rendered++;
        }

        int x = startX + xStep;
        while (attempted < MAX_GESTURE_TILES) {
            attempted++;
            if (!curveOccupies(curvePlacement, x, endY)
                    && renderPiece(scene, renderer, sceneBase, straightDefinition,
                            objectType, x, endY, plane, eastWestRotation())) {
                rendered++;
            }
            if (x == endX) {
                break;
            }
            x += xStep;
        }
        return rendered;
    }

    private static int renderHorizontalLine(Class523 scene, Class106 renderer, Class497 sceneBase,
            ObjectDefinitions definition, int startX, int endX, int y, int plane) {
        int rendered = 0;
        int attempted = 0;
        int step = Integer.compare(endX, startX);
        int x = startX;
        while (attempted < MAX_GESTURE_TILES) {
            attempted++;
            if (renderPiece(scene, renderer, sceneBase, definition,
                    objectType, x, y, plane, eastWestRotation())) {
                rendered++;
            }
            if (x == endX) {
                break;
            }
            x += step;
        }
        return rendered;
    }

    private static int renderVerticalLine(Class523 scene, Class106 renderer, Class497 sceneBase,
            ObjectDefinitions definition, int startY, int endY, int x, int plane) {
        int rendered = 0;
        int attempted = 0;
        int step = Integer.compare(endY, startY);
        int y = startY;
        while (attempted < MAX_GESTURE_TILES) {
            attempted++;
            if (renderPiece(scene, renderer, sceneBase, definition,
                    objectType, x, y, plane, verticalRotation())) {
                rendered++;
            }
            if (y == endY) {
                break;
            }
            y += step;
        }
        return rendered;
    }

    private static boolean renderPiece(Class523 scene, Class106 renderer, Class497 sceneBase,
            ObjectDefinitions definition, int pieceType,
            int worldX, int worldY, int plane, int rotation) {
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            return false;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            return false;
        }

        int localX = worldX - sceneBase.localX * -2109597897;
        int localY = worldY - sceneBase.localY * 417324155;
        int renderRotation = rotation & 0x3;

        int sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
        int sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
        if ((renderRotation & 0x1) != 0) {
            int swap = sizeX;
            sizeX = sizeY;
            sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0
                || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            return false;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = plane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[plane + 1]
                : null;

        Class647 built = definition.method6057(renderer, MODEL_FLAGS, pieceType, renderRotation,
                ground, upperGround, sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            return false;
        }

        if (scene.aClass174Array5840 == scene.aClass174Array5875
                && scene.aClass174Array5838[0] != null) {
            Class86 environment = new Class86();
            environment.anInt1193 = scene.method6231(localX, localY, 1258315415) * 1368828903;
            environment.anInt1190 = scene.method6230(localX, localY, -981999643) * 1765263439;
            environment.anInt1191 = scene.method6283(localX, localY, 775342000) * 628738217;
            environment.anInt1189 = scene.method6233(localX, localY, -1042067865) * -233369847;
            environment.anInt1194 = scene.method6234(localX, localY, (byte) 16) * -223776263;
            environment.anInt1195 = scene.method6235(localX, localY, (byte) 95) * -963547665;
            renderer.method1790(scene.aClass174Array5838[0]
                    .method2726(sceneX, sceneZ, 358769667), environment);
        }

        Model model = (Model) built.anObject8324;
        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        Class326 bounds = definition.aClass326_5684;
        if (bounds != null) {
            model.method1375(TRANSFORM, null, 0);
            renderer.method1738(TRANSFORM, RENDER_BOUNDS, bounds);
        } else {
            model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        }
        return true;
    }

    private static synchronized void ensureInputListener() {
        if (inputListenerInstalled) {
            return;
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent) {
                    handleMouseEvent((MouseEvent) event);
                } else if (event instanceof KeyEvent) {
                    handleKeyEvent((KeyEvent) event);
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

        inputListenerInstalled = true;
    }

    private static void handleMouseEvent(MouseEvent mouse) {
        if (!enabled) {
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || mouse.getSource() != canvas) {
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_PRESSED && mouse.getButton() == MouseEvent.BUTTON1) {
            if (beginDrag()) {
                mouse.consume();
            }
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_DRAGGED && dragging) {
            // Keep Matrix3's normal mouse/menu path live so action-23 hover updates B.
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_RELEASED && dragging
                && mouse.getButton() == MouseEvent.BUTTON1) {
            if (!liveDragLockedAtExistingRail && !liveDragLockedBySharpTurn
                    && hoveredPlane == livePlane && hoveredWorldX >= 0 && hoveredWorldY >= 0) {
                liveEndX = hoveredWorldX;
                liveEndY = hoveredWorldY;
            }
            commitActiveDrag();
            mouse.consume();
        }
    }

    private static void handleKeyEvent(KeyEvent key) {
        if (!enabled || !dragging || key.getID() != KeyEvent.KEY_PRESSED
                || key.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return;
        }
        cancelActiveDrag();
        key.consume();
    }

    private static boolean beginDrag() {
        long age = System.currentTimeMillis() - hoveredAtMillis;
        if (objectId < 0) {
            eventState = "No route rail configured.";
            return false;
        }
        if (hoveredWorldX < 0 || hoveredWorldY < 0 || hoveredPlane < 0
                || hoveredAtMillis == 0L || age > HOVER_STALE_MS) {
            eventState = "WAIT: move over valid world ground before pressing Left mouse.";
            return false;
        }

        editingEndpointB = logicalNetwork.contains(
                logicalKey(hoveredWorldX, hoveredWorldY, hoveredPlane));
        if (editingEndpointB) {
            liveStartX = hoveredWorldX;
            liveStartY = hoveredWorldY;
            liveEndX = hoveredWorldX;
            liveEndY = hoveredWorldY;
            livePlane = hoveredPlane;
            eventState = "Extending/branching rail network from "
                    + liveStartX + "," + liveStartY + ".";
        } else {
            liveStartX = hoveredWorldX;
            liveStartY = hoveredWorldY;
            liveEndX = hoveredWorldX;
            liveEndY = hoveredWorldY;
            livePlane = hoveredPlane;
            eventState = "New route drag started at A=" + liveStartX + "," + liveStartY + ".";
        }
        liveDragPath.clear();
        liveDragPath.add(new int[] { liveStartX, liveStartY, livePlane });
        liveDragLockedAtExistingRail = false;
        liveExistingContactX = -1;
        liveExistingContactY = -1;
        liveExistingContactRequiresSpecialTool = false;
        liveDragLockedBySharpTurn = false;
        liveSharpTurnX = -1;
        liveSharpTurnY = -1;
        dragging = true;
        lastRenderedCycle = Integer.MIN_VALUE;
        return true;
    }

    private static void appendLiveDragToward(int targetX, int targetY) {
        if (liveDragLockedAtExistingRail || liveDragLockedBySharpTurn) {
            return;
        }
        if (liveDragPath.isEmpty()) {
            liveDragPath.add(new int[] { liveStartX, liveStartY, livePlane });
        }
        int[] last = liveDragPath.get(liveDragPath.size() - 1);
        int x = last[0], y = last[1];
        if (x == targetX && y == targetY) {
            return;
        }

        /*
         * Grid-snapped rail planner. Do not record every hover wobble. The
         * current run stays locked to its axis until the cursor has moved at
         * least two tiles perpendicular to that run; only then is a deliberate
         * 90-degree turn authored. This gives the gesture hysteresis while still
         * allowing tight intentional corners.
         */
        int axis = liveDragAxis();
        int dx = targetX - x;
        int dy = targetY - y;
        if (axis == 0) {
            axis = Math.abs(dx) >= Math.abs(dy) ? 1 : 2;
        } else if (axis == 1 && Math.abs(dy) >= 2 && Math.abs(dy) > Math.abs(dx)) {
            axis = 2;
        } else if (axis == 2 && Math.abs(dx) >= 2 && Math.abs(dx) > Math.abs(dy)) {
            axis = 1;
        }

        int destination = axis == 1 ? targetX : targetY;
        int cursor = axis == 1 ? x : y;
        while (cursor != destination && liveDragPath.size() < MAX_GESTURE_TILES) {
            int previousX = x;
            int previousY = y;
            cursor += Integer.compare(destination, cursor);
            if (axis == 1) x = cursor; else y = cursor;

            /*
             * Ordinary Rail may extend an endpoint, but it must not manufacture
             * a degree-3/4 node. Junction/Crossing/Splitter own those semantics.
             *
             * This guard covers both directions:
             *  - dragging FROM an existing degree-2 rail into empty ground;
             *  - dragging INTO an existing degree-2 rail from empty ground.
             *
             * In either case the normal Rail gesture stops before the new edge
             * is authored, preserving the existing straight/curve resolver.
             */
            if (isExistingRailTile(previousX, previousY)
                    && wouldRequireSpecialNode(previousX, previousY, x, y)) {
                lockAtSpecialRailContact(previousX, previousY, previousX, previousY);
                return;
            }

            if (wouldCreateOverlappingCurveFootprints(previousX, previousY, x, y)) {
                lockAtSharpTurn(previousX, previousY);
                return;
            }

            if (isPreExistingRailContact(x, y)) {
                if (wouldRequireSpecialNode(x, y, previousX, previousY)) {
                    lockAtSpecialRailContact(x, y, previousX, previousY);
                    return;
                }

                appendLiveDragTile(x, y);
                liveDragLockedAtExistingRail = true;
                liveExistingContactX = x;
                liveExistingContactY = y;
                liveExistingContactRequiresSpecialTool = false;
                liveEndX = x;
                liveEndY = y;
                return;
            }

            appendLiveDragTile(x, y);
        }
    }

    private static boolean isPreExistingRailContact(int x, int y) {
        if (x == liveStartX && y == liveStartY) {
            return false;
        }
        return isExistingRailTile(x, y);
    }

    private static boolean isExistingRailTile(int x, int y) {
        return logicalNetwork.contains(logicalKey(x, y, livePlane));
    }

    private static boolean wouldRequireSpecialNode(
            int existingX, int existingY, int neighborX, int neighborY) {
        if (!isExistingRailTile(existingX, existingY)) {
            return false;
        }
        if (hasLogicalConnection(existingX, existingY, neighborX, neighborY, livePlane)) {
            return false;
        }
        return Integer.bitCount(
                logicalNeighborMask(existingX, existingY, livePlane)) >= 2;
    }

    private static void lockAtSpecialRailContact(
            int contactX, int contactY, int stopX, int stopY) {
        liveDragLockedAtExistingRail = true;
        liveExistingContactX = contactX;
        liveExistingContactY = contactY;
        liveExistingContactRequiresSpecialTool = true;
        liveEndX = stopX;
        liveEndY = stopY;
        eventState = "Normal Rail stopped at " + contactX + "," + contactY
                + ": Junction/Crossing/Splitter required for a degree-3/4 node.";
    }

    private static void lockAtSharpTurn(int stopX, int stopY) {
        liveDragLockedBySharpTurn = true;
        liveSharpTurnX = stopX;
        liveSharpTurnY = stopY;
        liveEndX = stopX;
        liveEndY = stopY;
        eventState = "Rail turn blocked at " + stopX + "," + stopY
                + ": accepted curve composites would overlap. Widen the bend.";
    }

    /**
     * The accepted three-object curve owns the corner plus the first tile on
     * each leg. Two corners packed too tightly can therefore claim the same
     * physical tile even though their logical path is valid. Detect that at
     * authoring time instead of letting the physical resolver emit interleaved
     * curve objects like the sharp S/hairpin failure.
     */
    private static boolean wouldCreateOverlappingCurveFootprints(
            int previousX, int previousY, int candidateX, int candidateY) {
        java.util.LinkedHashSet<String> network =
                new java.util.LinkedHashSet<String>(logicalNetwork);
        java.util.LinkedHashSet<String> connections =
                new java.util.LinkedHashSet<String>(logicalConnections);
        addDragPathToTopology(network, connections, liveDragPath);

        network.add(logicalKey(candidateX, candidateY, livePlane));
        addLogicalConnection(connections,
                previousX, previousY, candidateX, candidateY, livePlane);

        return curveAtOverlapsAnother(
                previousX, previousY, livePlane, network, connections)
                || curveAtOverlapsAnother(
                        candidateX, candidateY, livePlane, network, connections);
    }

    private static boolean curveAtOverlapsAnother(
            int x, int y, int plane,
            java.util.Set<String> network, java.util.Set<String> connections) {
        CurvePlacement placement = curvePlacementForLogicalNode(
                x, y, plane, network, connections);
        if (placement == null) {
            return false;
        }

        java.util.HashSet<String> footprint = curveFootprintKeys(placement, plane);
        for (String key : network) {
            int[] tile = parseLogicalKey(key);
            if (tile[2] != plane || (tile[0] == x && tile[1] == y)) {
                continue;
            }
            CurvePlacement other = curvePlacementForLogicalNode(
                    tile[0], tile[1], tile[2], network, connections);
            if (other == null) {
                continue;
            }
            for (String occupied : curveFootprintKeys(other, plane)) {
                if (footprint.contains(occupied)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CurvePlacement curvePlacementForLogicalNode(
            int x, int y, int plane,
            java.util.Set<String> network, java.util.Set<String> connections) {
        if (!network.contains(logicalKey(x, y, plane))) {
            return null;
        }
        int mask = logicalNeighborMask(x, y, plane, connections);
        if (Integer.bitCount(mask) != 2 || isOppositePair(mask)
                || touchesLogicalJunction(x, y, plane, mask, connections)) {
            return null;
        }
        int horizontalDirection = (mask & 2) != 0 ? 1 : -1;
        int verticalDirection = (mask & 1) != 0 ? 1 : -1;
        return createCurvePlacement(x, y, horizontalDirection, verticalDirection);
    }

    private static java.util.HashSet<String> curveFootprintKeys(
            CurvePlacement placement, int plane) {
        java.util.HashSet<String> footprint = new java.util.HashSet<String>();
        if (placement == null) {
            return footprint;
        }
        for (RailCompositeLibrary.Component component : placement.composite.getComponents()) {
            int[] rotatedOffset = rotateLayoutOffset(
                    component.getOffsetX() - placement.anchorX,
                    component.getOffsetY() - placement.anchorY,
                    placement.layoutTurns);
            footprint.add(logicalKey(
                    placement.cornerX + rotatedOffset[0],
                    placement.cornerY + rotatedOffset[1],
                    plane));
        }
        return footprint;
    }

    private static int liveDragAxis() {
        if (liveDragPath.size() < 2) return 0;
        int[] a = liveDragPath.get(liveDragPath.size() - 2);
        int[] b = liveDragPath.get(liveDragPath.size() - 1);
        if (a[0] != b[0]) return 1;
        if (a[1] != b[1]) return 2;
        return 0;
    }

    private static void appendLiveDragTile(int x, int y) {
        if (!liveDragPath.isEmpty()) {
            int[] last = liveDragPath.get(liveDragPath.size() - 1);
            if (last[0] == x && last[1] == y && last[2] == livePlane) return;
            /*
             * Backtracking one tile removes the last sample instead of creating
             * a tiny U-turn/loop from normal mouse correction.
             */
            if (liveDragPath.size() >= 2) {
                int[] previous = liveDragPath.get(liveDragPath.size() - 2);
                if (previous[0] == x && previous[1] == y && previous[2] == livePlane) {
                    liveDragPath.remove(liveDragPath.size() - 1);
                    return;
                }
            }
        }
        liveDragPath.add(new int[] { x, y, livePlane });
    }

    private static void addLiveDragToLogicalNetwork() {
        addDragPathToTopology(logicalNetwork, logicalConnections, liveDragPath);
    }

    private static void addDragPathToTopology(
            java.util.Set<String> network, java.util.Set<String> connections,
            java.util.List<int[]> path) {
        int[] previous = null;
        for (int[] tile : path) {
            network.add(logicalKey(tile[0], tile[1], tile[2]));
            if (previous != null) {
                addLogicalConnection(connections,
                        previous[0], previous[1], tile[0], tile[1], tile[2]);
            }
            previous = tile;
        }
    }

    private static java.util.List<int[]> copyLiveDragPath() {
        java.util.List<int[]> copy = new java.util.ArrayList<int[]>(liveDragPath.size());
        for (int[] tile : liveDragPath) {
            copy.add(new int[] { tile[0], tile[1], tile[2] });
        }
        return copy;
    }

    private static void commitActiveDrag() {
        java.util.List<RoutePiece> oldPhysical = resolveLogicalNetworkPieces();
        boolean joinedExisting = logicalNetwork.contains(
                logicalKey(liveStartX, liveStartY, livePlane));

        appendLiveDragToward(liveEndX, liveEndY);
        java.util.List<int[]> committedGesture = copyLiveDragPath();
        addLiveDragToLogicalNetwork();
        java.util.List<RoutePiece> newPhysical = resolveLogicalNetworkPieces();

        committedStartX = liveStartX;
        committedStartY = liveStartY;
        committedEndX = liveEndX;
        committedEndY = liveEndY;
        committedPlane = livePlane;
        committed = true;
        dragging = false;
        editingEndpointB = false;
        continuationHorizontalDirection = 0;
        continuationVerticalDirection = 0;
        lastRenderedCycle = Integer.MIN_VALUE;

        pathEndX = liveEndX;
        pathEndY = liveEndY;
        pathPlane = livePlane;

        eventState = liveDragLockedBySharpTurn
                ? "Rail turn stopped at " + liveSharpTurnX + "," + liveSharpTurnY
                        + " because adjacent curve composites would overlap."
                : (liveDragLockedAtExistingRail
                        ? (liveExistingContactRequiresSpecialTool
                                ? "Normal Rail stopped before unsupported special node at "
                                        + liveExistingContactX + "," + liveExistingContactY
                                        + "; use Junction/Crossing/Splitter."
                                : "Rail network connected to existing track at "
                                        + liveExistingContactX + "," + liveExistingContactY
                                        + "; cross-through intentionally stopped.")
                        : (joinedExisting
                                ? "Rail network extended from an existing endpoint."
                                : "Rail network segment added."));

        long debugOp = -1L;
        String debugEvent = eventState;
        int debugLogicalCount = logicalNetwork.size();
        if (debugEnabled) {
            debugOperationId++;
            debugOp = debugOperationId;
            debugReport = buildDebugReport(debugOperationId, !oldPhysical.isEmpty(), joinedExisting,
                    liveStartX, liveStartY, liveStartX, liveStartY, livePlane,
                    oldPhysical, newPhysical, committedGesture);
            appendDebugReportToFile(
                    debugOperationId, debugReport, oldPhysical, newPhysical, committedGesture);
        }

        /*
         * Queue the real server-owned mutation before the screenshot. The
         * screenshot is delayed slightly so it records the applied world state,
         * not merely the client preview that existed at mouse release.
         */
        if (oldPhysical.isEmpty()) {
            ConstructionPlacementController.onRailRouteCommitted(newPhysical);
        } else {
            ConstructionPlacementController.onRailNetworkDelta(oldPhysical, newPhysical);
        }

        if (debugEnabled && debugOp >= 0L) {
            scheduleDebugWorldScreenshot(
                    debugOp, debugReport, newPhysical, debugLogicalCount,
                    committedStartX, committedStartY, committedEndX, committedEndY,
                    committedPlane, debugEvent);
        }

        liveDragLockedAtExistingRail = false;
        liveExistingContactX = -1;
        liveExistingContactY = -1;
        liveExistingContactRequiresSpecialTool = false;
        liveDragLockedBySharpTurn = false;
        liveSharpTurnX = -1;
        liveSharpTurnY = -1;
        liveDragPath.clear();
    }

    private static void addLogicalManhattanSegment(
            int startX, int startY, int endX, int endY, int plane) {
        int x = startX;
        int y = startY;
        logicalNetwork.add(logicalKey(x, y, plane));
        int xStep = Integer.compare(endX, startX);
        int yStep = Integer.compare(endY, startY);

        if (routeOrder == RouteOrder.Y_THEN_X) {
            while (y != endY && logicalNetwork.size() < MAX_NETWORK_PIECES) {
                int oldY = y;
                y += yStep;
                logicalNetwork.add(logicalKey(x, y, plane));
                addLogicalConnection(x, oldY, x, y, plane);
            }
            while (x != endX && logicalNetwork.size() < MAX_NETWORK_PIECES) {
                int oldX = x;
                x += xStep;
                logicalNetwork.add(logicalKey(x, y, plane));
                addLogicalConnection(oldX, y, x, y, plane);
            }
        } else {
            while (x != endX && logicalNetwork.size() < MAX_NETWORK_PIECES) {
                int oldX = x;
                x += xStep;
                logicalNetwork.add(logicalKey(x, y, plane));
                addLogicalConnection(oldX, y, x, y, plane);
            }
            while (y != endY && logicalNetwork.size() < MAX_NETWORK_PIECES) {
                int oldY = y;
                y += yStep;
                logicalNetwork.add(logicalKey(x, y, plane));
                addLogicalConnection(x, oldY, x, y, plane);
            }
        }
    }

    private static java.util.List<RoutePiece> resolveLogicalNetworkPieces() {
        return resolveLogicalNetworkPieces(logicalNetwork, logicalConnections);
    }

    private static java.util.List<RoutePiece> resolveLogicalNetworkPieces(
            java.util.Set<String> network, java.util.Set<String> connections) {
        java.util.List<RoutePiece> pieces = new java.util.ArrayList<RoutePiece>();
        java.util.HashSet<String> physicalOccupied = new java.util.HashSet<String>();

        // Resolve 90-degree logical corners first because the accepted RS3 curve
        // owns a three-object physical footprint around one logical node.
        for (String key : network) {
            int[] tile = parseLogicalKey(key);
            int mask = logicalNeighborMask(tile[0], tile[1], tile[2], connections);
            if (Integer.bitCount(mask) != 2 || isOppositePair(mask)
                    || touchesLogicalJunction(
                            tile[0], tile[1], tile[2], mask, connections)) {
                continue;
            }
            int horizontalDirection = (mask & 2) != 0 ? 1 : -1;
            int verticalDirection = (mask & 1) != 0 ? 1 : -1;
            CurvePlacement curve = createCurvePlacement(
                    tile[0], tile[1], horizontalDirection, verticalDirection);
            if (curve != null) {
                appendUniqueCurvePieces(pieces, physicalOccupied, curve, tile[2]);
                reserveCurveLogicalLegs(physicalOccupied, tile[0], tile[1], tile[2], mask);
            }
        }

        // All remaining logical nodes receive a straight placeholder. Degree
        // 3/4 nodes are intentionally logical junctions in V1; final switch art
        // can replace this resolver choice later without changing saved topology.
        for (String key : network) {
            int[] tile = parseLogicalKey(key);
            String physicalKey = logicalKey(tile[0], tile[1], tile[2]);
            if (physicalOccupied.contains(physicalKey)) {
                continue;
            }
            int mask = logicalNeighborMask(tile[0], tile[1], tile[2], connections);
            int rotation = chooseStraightRotation(mask);
            RoutePiece piece = new RoutePiece(objectId, objectType, rotation,
                    tile[0], tile[1], tile[2]);
            pieces.add(piece);
            physicalOccupied.add(physicalKey);
            if (pieces.size() >= MAX_NETWORK_PIECES) {
                break;
            }
        }
        return pieces;
    }

    private static void appendUniqueCurvePieces(java.util.List<RoutePiece> pieces,
            java.util.Set<String> occupied, CurvePlacement placement, int plane) {
        for (RailCompositeLibrary.Component component : placement.composite.getComponents()) {
            if (pieces.size() >= MAX_NETWORK_PIECES) {
                return;
            }
            int[] offset = rotateLayoutOffset(
                    component.getOffsetX() - placement.anchorX,
                    component.getOffsetY() - placement.anchorY,
                    placement.layoutTurns);
            int x = placement.cornerX + offset[0];
            int y = placement.cornerY + offset[1];
            String key = logicalKey(x, y, plane);
            if (!occupied.add(key)) {
                continue;
            }
            pieces.add(new RoutePiece(component.getId(), component.getType(),
                    (component.getRotation() + placement.layoutTurns) & 0x3,
                    x, y, plane));
        }
    }

    private static void reserveCurveLogicalLegs(java.util.Set<String> occupied,
            int cornerX, int cornerY, int plane, int mask) {
        /*
         * The accepted three-object RS3 curve models visually occupy the corner
         * plus the first tile of each connected leg. Those logical tiles must
         * not also emit straight object 46353 or the straight renders through
         * the curve even though the topology itself is correct.
         */
        occupied.add(logicalKey(cornerX, cornerY, plane));
        if ((mask & 1) != 0) occupied.add(logicalKey(cornerX, cornerY + 1, plane));
        if ((mask & 2) != 0) occupied.add(logicalKey(cornerX + 1, cornerY, plane));
        if ((mask & 4) != 0) occupied.add(logicalKey(cornerX, cornerY - 1, plane));
        if ((mask & 8) != 0) occupied.add(logicalKey(cornerX - 1, cornerY, plane));
    }

    private static boolean touchesLogicalJunction(int x, int y, int plane, int mask) {
        return touchesLogicalJunction(x, y, plane, mask, logicalConnections);
    }

    private static boolean touchesLogicalJunction(
            int x, int y, int plane, int mask, java.util.Set<String> connections) {
        /*
         * CURVE_RAIL_LAYOUT_01 owns the first tile of both legs. It must never
         * consume a degree-3/4 node or the junction appears as a misplaced curve.
         * Until dedicated switch/crossing art is classified, keep the junction
         * tile as the straight-through placeholder and terminate the branch
         * cleanly into it.
         */
        if ((mask & 1) != 0 && isLogicalJunction(x, y + 1, plane, connections)) return true;
        if ((mask & 2) != 0 && isLogicalJunction(x + 1, y, plane, connections)) return true;
        if ((mask & 4) != 0 && isLogicalJunction(x, y - 1, plane, connections)) return true;
        if ((mask & 8) != 0 && isLogicalJunction(x - 1, y, plane, connections)) return true;
        return false;
    }

    private static boolean isLogicalJunction(int x, int y, int plane) {
        return isLogicalJunction(x, y, plane, logicalConnections);
    }

    private static boolean isLogicalJunction(
            int x, int y, int plane, java.util.Set<String> connections) {
        return Integer.bitCount(logicalNeighborMask(x, y, plane, connections)) >= 3;
    }

    /**
     * Direction-neutral rail topology mask for cart routing.
     * N=1, E=2, S=4, W=8. A cart's incoming edge selects which of these
     * connected outgoing edges are valid; physical object rotation is visual
     * only and is never the travel-direction authority.
     */
    /**
     * Eraser bridge for Rail Network V1.
     *
     * Persistent rail objects are derived from logical topology. Deleting only
     * the visible server object leaves the client graph believing the rail still
     * exists, so the next edit can resurrect/re-route it. Erase the authored
     * logical node first, then submit the resulting physical delta through the
     * same atomic rail replacement owner used by normal rail edits.
     *
     * @return non-null status when the tile belonged to the current logical rail
     *         graph; null when the generic settlement eraser should handle it.
     */
    public static synchronized String eraseAtWorldTile(int worldX, int worldY, int plane) {
        if (logicalNetwork.isEmpty()) {
            return null;
        }

        String targetKey = findLogicalEraseTarget(worldX, worldY, plane);
        if (targetKey == null) {
            return null;
        }

        java.util.List<RoutePiece> oldPhysical = resolveLogicalNetworkPieces();
        int[] target = parseLogicalKey(targetKey);

        logicalNetwork.remove(targetKey);
        removeLogicalConnectionsAt(target[0], target[1], target[2]);

        java.util.List<RoutePiece> newPhysical = resolveLogicalNetworkPieces();
        ConstructionPlacementController.onRailNetworkDelta(oldPhysical, newPhysical);

        committed = !logicalNetwork.isEmpty();
        dragging = false;
        editingEndpointB = false;
        liveDragPath.clear();
        liveDragLockedAtExistingRail = false;
        liveExistingContactX = -1;
        liveExistingContactY = -1;
        lastRenderedCycle = Integer.MIN_VALUE;

        eventState = "Rail eraser removed logical tile "
                + target[0] + "," + target[1] + "," + target[2]
                + " and queued " + oldPhysical.size() + " -> "
                + newPhysical.size() + " physical reconciliation.";
        return eventState;
    }

    private static String findLogicalEraseTarget(int worldX, int worldY, int plane) {
        String exact = logicalKey(worldX, worldY, plane);
        if (logicalNetwork.contains(exact)) {
            return exact;
        }

        /*
         * A rendered curve is a three-object composite. A clicked component can
         * sit on an offset tile rather than the authored corner, so map that
         * physical footprint back to its logical corner before erasing.
         */
        for (String key : logicalNetwork) {
            int[] tile = parseLogicalKey(key);
            if (tile[2] != plane) {
                continue;
            }
            int mask = logicalNeighborMask(tile[0], tile[1], tile[2]);
            if (Integer.bitCount(mask) != 2 || isOppositePair(mask)
                    || touchesLogicalJunction(tile[0], tile[1], tile[2], mask)) {
                continue;
            }
            int horizontalDirection = (mask & 2) != 0 ? 1 : -1;
            int verticalDirection = (mask & 1) != 0 ? 1 : -1;
            CurvePlacement curve = createCurvePlacement(
                    tile[0], tile[1], horizontalDirection, verticalDirection);
            if (curveOccupies(curve, worldX, worldY)) {
                return key;
            }
        }
        return null;
    }

    private static void removeLogicalConnectionsAt(int x, int y, int plane) {
        logicalConnections.remove(logicalConnectionKey(x, y, x, y + 1, plane));
        logicalConnections.remove(logicalConnectionKey(x, y, x + 1, y, plane));
        logicalConnections.remove(logicalConnectionKey(x, y, x, y - 1, plane));
        logicalConnections.remove(logicalConnectionKey(x, y, x - 1, y, plane));
    }

    public static synchronized int getCartConnectionMask(int worldX, int worldY, int plane) {
        if (!logicalNetwork.contains(logicalKey(worldX, worldY, plane))) return 0;
        return logicalNeighborMask(worldX, worldY, plane);
    }

    private static int logicalNeighborMask(int x, int y, int plane) {
        return logicalNeighborMask(x, y, plane, logicalConnections);
    }

    private static int logicalNeighborMask(
            int x, int y, int plane, java.util.Set<String> connections) {
        int mask = 0;
        if (hasLogicalConnection(connections, x, y, x, y + 1, plane)) mask |= 1; // N
        if (hasLogicalConnection(connections, x, y, x + 1, y, plane)) mask |= 2; // E
        if (hasLogicalConnection(connections, x, y, x, y - 1, plane)) mask |= 4; // S
        if (hasLogicalConnection(connections, x, y, x - 1, y, plane)) mask |= 8; // W
        return mask;
    }

    private static void addLogicalConnection(int ax, int ay, int bx, int by, int plane) {
        addLogicalConnection(logicalConnections, ax, ay, bx, by, plane);
    }

    private static void addLogicalConnection(
            java.util.Set<String> connections,
            int ax, int ay, int bx, int by, int plane) {
        if (Math.abs(ax - bx) + Math.abs(ay - by) != 1) return;
        connections.add(logicalConnectionKey(ax, ay, bx, by, plane));
    }

    private static boolean hasLogicalConnection(int ax, int ay, int bx, int by, int plane) {
        return hasLogicalConnection(logicalConnections, ax, ay, bx, by, plane);
    }

    private static boolean hasLogicalConnection(
            java.util.Set<String> connections,
            int ax, int ay, int bx, int by, int plane) {
        return connections.contains(logicalConnectionKey(ax, ay, bx, by, plane));
    }

    private static String logicalConnectionKey(int ax, int ay, int bx, int by, int plane) {
        if (ax > bx || (ax == bx && ay > by)) {
            int tx = ax; ax = bx; bx = tx;
            int ty = ay; ay = by; by = ty;
        }
        return ax + ":" + ay + ":" + plane + ">" + bx + ":" + by + ":" + plane;
    }

    private static boolean isOppositePair(int mask) {
        return mask == (1 | 4) || mask == (2 | 8);
    }

    private static int chooseStraightRotation(int mask) {
        boolean eastWest = (mask & (2 | 8)) != 0;
        boolean northSouth = (mask & (1 | 4)) != 0;
        if (eastWest && !northSouth) return eastWestRotation();
        if (northSouth && !eastWest) return verticalRotation();
        // Junction placeholder: preserve the through axis when one exists;
        // otherwise prefer E/W. Final junction art is a later resolver concern.
        if ((mask & (2 | 8)) == (2 | 8)) return eastWestRotation();
        return verticalRotation();
    }

    private static String logicalKey(int x, int y, int plane) {
        return x + ":" + y + ":" + plane;
    }

    private static int[] parseLogicalKey(String key) {
        String[] parts = key.split(":");
        return new int[] {
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }

    private static String buildDebugReport(long operationId, boolean hadPrevious, boolean endpointEdit,
            int previousStartX, int previousStartY, int previousEndX, int previousEndY,
            int previousPlane, java.util.List<RoutePiece> oldPieces,
            java.util.List<RoutePiece> pieces, java.util.List<int[]> gesture) {
        StringBuilder out = new StringBuilder(2048);
        out.append("RAIL_DEBUG\top=").append(operationId)
                .append("\tbuild=SNAPSHOT_PREVIEW_V2")
                .append("\torder=").append(routeOrder)
                .append("\tcomposite=").append(getConfiguredCurveCompositeName())
                .append("\tgestureTiles=").append(gesture == null ? 0 : gesture.size())
                .append("\tlogicalTiles=").append(logicalNetwork.size())
                .append("\tcontactStop=").append(liveDragLockedAtExistingRail
                        ? (liveExistingContactX + "," + liveExistingContactY) : "none")
                .append("\tcontactMode=").append(liveDragLockedAtExistingRail
                        ? (liveExistingContactRequiresSpecialTool ? "SPECIAL_NODE_BLOCKED" : "CONNECTED")
                        : "none")
                .append("\tbendStop=").append(liveDragLockedBySharpTurn
                        ? (liveSharpTurnX + "," + liveSharpTurnY) : "none")
                .append("\tlimits=").append(MAX_GESTURE_TILES).append('/').append(MAX_NETWORK_PIECES)
                .append("\n");
        out.append("MODE\tendpointEdit=").append(endpointEdit)
                .append("\tfixedA=").append(endpointEdit)
                .append("\n");
        out.append("CURRENT\tA=").append(committedStartX).append(',').append(committedStartY)
                .append("\tB=").append(committedEndX).append(',').append(committedEndY)
                .append("\tplane=").append(committedPlane).append("\n");
        if (hadPrevious) {
            out.append("PREVIOUS\tA=").append(previousStartX).append(',').append(previousStartY)
                    .append("\tB=").append(previousEndX).append(',').append(previousEndY)
                    .append("\tplane=").append(previousPlane).append("\n");
        } else {
            out.append("PREVIOUS\tnone\n");
        }

        boolean continuation = hadPrevious && previousPlane == committedPlane
                && previousEndX == committedStartX && previousEndY == committedStartY;
        int[] incoming = continuation
                ? finalTravelDirection(previousStartX, previousStartY, previousEndX, previousEndY)
                : new int[] { 0, 0 };
        int[] outgoing = firstTravelDirection(
                committedStartX, committedStartY, committedEndX, committedEndY);
        out.append("SEAM\tcontinuation=").append(continuation)
                .append("\tB=").append(committedStartX).append(',').append(committedStartY)
                .append("\tin=").append(directionName(incoming[0], incoming[1]))
                .append("\tout=").append(directionName(outgoing[0], outgoing[1]))
                .append("\tcontinuationH=").append(continuationHorizontalDirection)
                .append("\tcontinuationV=").append(continuationVerticalDirection)
                .append("\n");

        if (continuationHorizontalDirection != 0 && continuationVerticalDirection != 0) {
            CurvePlacement placement = createCurvePlacement(
                    committedStartX, committedStartY,
                    continuationHorizontalDirection, continuationVerticalDirection);
            appendCurveDebug(out, "SEAM_CURVE", placement);
        }

        out.append("OLD_PIECES\tcount=").append(oldPieces == null ? 0 : oldPieces.size()).append("\n");
        if (oldPieces != null) {
            int oldIndex = 0;
            for (RoutePiece piece : oldPieces) {
                out.append("OLD_PIECE\t").append(oldIndex++)
                        .append("\tx=").append(piece.getWorldX())
                        .append("\ty=").append(piece.getWorldY())
                        .append("\tplane=").append(piece.getPlane())
                        .append("\tid=").append(piece.getObjectId())
                        .append("\ttype=").append(piece.getObjectType())
                        .append("\trot=").append(piece.getRotation())
                        .append("\trole=").append(debugRole(piece.getObjectId()))
                        .append("\n");
            }
        }
        out.append("PIECES\tcount=").append(pieces == null ? 0 : pieces.size()).append("\n");
        if (pieces != null) {
            int index = 0;
            for (RoutePiece piece : pieces) {
                out.append("PIECE\t").append(index++)
                        .append("\tx=").append(piece.getWorldX())
                        .append("\ty=").append(piece.getWorldY())
                        .append("\tplane=").append(piece.getPlane())
                        .append("\tid=").append(piece.getObjectId())
                        .append("\ttype=").append(piece.getObjectType())
                        .append("\trot=").append(piece.getRotation())
                        .append("\trole=").append(debugRole(piece.getObjectId()))
                        .append("\n");
            }
        }
        return out.toString();
    }

    private static void appendCurveDebug(StringBuilder out, String label,
            CurvePlacement placement) {
        if (placement == null) {
            out.append(label).append("\tnone\n");
            return;
        }
        out.append(label)
                .append("\tcorner=").append(placement.cornerX).append(',').append(placement.cornerY)
                .append("\tanchorOffset=").append(placement.anchorX).append(',').append(placement.anchorY)
                .append("\tlayoutTurns=").append(placement.layoutTurns)
                .append("\n");
        for (RailCompositeLibrary.Component component : placement.composite.getComponents()) {
            int[] offset = rotateLayoutOffset(
                    component.getOffsetX() - placement.anchorX,
                    component.getOffsetY() - placement.anchorY,
                    placement.layoutTurns);
            out.append("FOOTPRINT")
                    .append("\tx=").append(placement.cornerX + offset[0])
                    .append("\ty=").append(placement.cornerY + offset[1])
                    .append("\tid=").append(component.getId())
                    .append("\ttype=").append(component.getType())
                    .append("\trot=").append((component.getRotation() + placement.layoutTurns) & 0x3)
                    .append("\tdx=").append(offset[0])
                    .append("\tdy=").append(offset[1])
                    .append("\n");
        }
    }

    private static String debugRole(int id) {
        if (id == 46353) return "STRAIGHT";
        if (id == 46377) return "CURVE_APPROACH";
        if (id == 46379) return "CURVE_ELBOW";
        if (id == 46381) return "CURVE_EXIT";
        return "RAIL_OTHER";
    }

    private static String directionName(int dx, int dy) {
        if (dx > 0) return "E";
        if (dx < 0) return "W";
        if (dy > 0) return "N";
        if (dy < 0) return "S";
        return "NONE";
    }

    public static java.util.List<RoutePiece> snapshotCommittedRoutePieces() {
        java.util.List<RoutePiece> pieces = new java.util.ArrayList<RoutePiece>();
        if (!committed || committedPlane < 0) {
            return pieces;
        }
        if (!logicalNetwork.isEmpty()) {
            pieces.addAll(resolveLogicalNetworkPieces());
        } else {
            appendRoutePieces(pieces, committedStartX, committedStartY,
                    committedEndX, committedEndY, committedPlane);
        }
        return pieces;
    }

    private static int[] finalTravelDirection(
            int startX, int startY, int endX, int endY) {
        int dx = Integer.compare(endX, startX);
        int dy = Integer.compare(endY, startY);
        if (routeOrder == RouteOrder.X_THEN_Y && dy != 0) {
            return new int[] { 0, dy };
        }
        if (routeOrder == RouteOrder.Y_THEN_X && dx != 0) {
            return new int[] { dx, 0 };
        }
        return dx != 0 ? new int[] { dx, 0 } : new int[] { 0, dy };
    }

    private static int[] firstTravelDirection(
            int startX, int startY, int endX, int endY) {
        int dx = Integer.compare(endX, startX);
        int dy = Integer.compare(endY, startY);
        if (routeOrder == RouteOrder.X_THEN_Y && dx != 0) {
            return new int[] { dx, 0 };
        }
        if (routeOrder == RouteOrder.Y_THEN_X && dy != 0) {
            return new int[] { 0, dy };
        }
        return dx != 0 ? new int[] { dx, 0 } : new int[] { 0, dy };
    }

    private static void removePiecesOccupiedByCurve(
            java.util.List<RoutePiece> pieces, CurvePlacement curve) {
        java.util.Iterator<RoutePiece> iterator = pieces.iterator();
        while (iterator.hasNext()) {
            RoutePiece piece = iterator.next();
            if (curveOccupies(curve, piece.getWorldX(), piece.getWorldY())) {
                iterator.remove();
            }
        }
    }

    private static void appendRoutePieces(java.util.List<RoutePiece> pieces,
            int startX, int startY, int endX, int endY, int plane) {
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0 || objectId < 0) {
            return;
        }
        if (startY == endY) {
            appendHorizontalPieces(pieces, startX, endX, startY, plane, null);
            return;
        }
        if (startX == endX) {
            appendVerticalPieces(pieces, startY, endY, startX, plane, null);
            return;
        }

        int xStep = Integer.compare(endX, startX);
        int yStep = Integer.compare(endY, startY);
        CurvePlacement curve;
        if (routeOrder == RouteOrder.Y_THEN_X) {
            curve = createCurvePlacement(startX, endY, xStep, -yStep);
            int y = startY;
            int attempted = 0;
            while (y != endY && attempted++ < MAX_GESTURE_TILES) {
                if (!curveOccupies(curve, startX, y)) {
                    pieces.add(new RoutePiece(objectId, objectType, verticalRotation(),
                            startX, y, plane));
                }
                y += yStep;
            }
            appendCurvePieces(pieces, curve, startX, endY, plane);
            int x = startX + xStep;
            while (pieces.size() < MAX_NETWORK_PIECES) {
                if (!curveOccupies(curve, x, endY)) {
                    pieces.add(new RoutePiece(objectId, objectType, eastWestRotation(),
                            x, endY, plane));
                }
                if (x == endX) {
                    break;
                }
                x += xStep;
            }
        } else {
            curve = createCurvePlacement(endX, startY, -xStep, yStep);
            int x = startX;
            int attempted = 0;
            while (x != endX && attempted++ < MAX_GESTURE_TILES) {
                if (!curveOccupies(curve, x, startY)) {
                    pieces.add(new RoutePiece(objectId, objectType, eastWestRotation(),
                            x, startY, plane));
                }
                x += xStep;
            }
            appendCurvePieces(pieces, curve, endX, startY, plane);
            int y = startY + yStep;
            while (pieces.size() < MAX_NETWORK_PIECES) {
                if (!curveOccupies(curve, endX, y)) {
                    pieces.add(new RoutePiece(objectId, objectType, verticalRotation(),
                            endX, y, plane));
                }
                if (y == endY) {
                    break;
                }
                y += yStep;
            }
        }
    }

    private static void appendHorizontalPieces(java.util.List<RoutePiece> pieces,
            int startX, int endX, int y, int plane, CurvePlacement curve) {
        int step = Integer.compare(endX, startX);
        int x = startX;
        while (pieces.size() < MAX_NETWORK_PIECES) {
            if (!curveOccupies(curve, x, y)) {
                pieces.add(new RoutePiece(objectId, objectType, eastWestRotation(), x, y, plane));
            }
            if (x == endX) {
                break;
            }
            x += step;
        }
    }

    private static void appendVerticalPieces(java.util.List<RoutePiece> pieces,
            int startY, int endY, int x, int plane, CurvePlacement curve) {
        int step = Integer.compare(endY, startY);
        int y = startY;
        while (pieces.size() < MAX_NETWORK_PIECES) {
            if (!curveOccupies(curve, x, y)) {
                pieces.add(new RoutePiece(objectId, objectType, verticalRotation(), x, y, plane));
            }
            if (y == endY) {
                break;
            }
            y += step;
        }
    }

    private static void appendCurvePieces(java.util.List<RoutePiece> pieces,
            CurvePlacement placement, int cornerX, int cornerY, int plane) {
        if (placement != null) {
            for (RailCompositeLibrary.Component component : placement.composite.getComponents()) {
                if (pieces.size() >= MAX_NETWORK_PIECES) {
                    return;
                }
                int[] offset = rotateLayoutOffset(
                        component.getOffsetX() - placement.anchorX,
                        component.getOffsetY() - placement.anchorY,
                        placement.layoutTurns);
                pieces.add(new RoutePiece(component.getId(), component.getType(),
                        (component.getRotation() + placement.layoutTurns) & 0x3,
                        placement.cornerX + offset[0], placement.cornerY + offset[1], plane));
            }
        } else if (curveObjectId >= 0) {
            pieces.add(new RoutePiece(curveObjectId, curveObjectType, curveBaseRotation,
                    cornerX, cornerY, plane));
        } else {
            pieces.add(new RoutePiece(objectId, objectType, verticalRotation(),
                    cornerX, cornerY, plane));
        }
    }

    public static final class RoutePiece {
        private final int objectId;
        private final int objectType;
        private final int rotation;
        private final int worldX;
        private final int worldY;
        private final int plane;

        private RoutePiece(int objectId, int objectType, int rotation,
                int worldX, int worldY, int plane) {
            this.objectId = objectId;
            this.objectType = objectType;
            this.rotation = rotation & 0x3;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }

        public int getObjectId() { return objectId; }
        public int getObjectType() { return objectType; }
        public int getRotation() { return rotation; }
        public int getWorldX() { return worldX; }
        public int getWorldY() { return worldY; }
        public int getPlane() { return plane; }
    }

    private static void cancelActiveDrag() {
        dragging = false;
        liveDragPath.clear();
        liveStartX = -1;
        liveStartY = -1;
        liveEndX = -1;
        liveEndY = -1;
        livePlane = -1;
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = committed
                ? "Active route drag cancelled; previous committed preview preserved."
                : "Active route drag cancelled.";
    }

    private static int verticalRotation() {
        /*
         * Runtime V0 proof showed the classifier's selected preview rotation
         * represented the rail model's north/south axis, not east/west.
         * Keep the persisted/UI value as the canonical N/S orientation and
         * derive E/W by a quarter-turn.
         */
        return horizontalRotation;
    }

    private static int eastWestRotation() {
        return (horizontalRotation + 1) & 0x3;
    }

    private static CurvePlacement createCurvePlacement(
            int cornerX, int cornerY, int horizontalDirection, int verticalDirection) {
        RailCompositeLibrary.CompositeDefinition composite = curveComposite;
        if (composite == null) {
            return null;
        }

        java.util.List<RailCompositeLibrary.Component> components = composite.getComponents();
        if (components.isEmpty()) {
            return null;
        }

        CurveAnchor anchor = findCurveAnchor(components);
        int targetQuarterTurns = curveQuarterTurnsFor(horizontalDirection, verticalDirection);
        int layoutTurns = (targetQuarterTurns - anchor.baseQuarterTurns
                + curveRotationOffset) & 0x3;
        return new CurvePlacement(
                composite, cornerX, cornerY, anchor.offsetX, anchor.offsetY, layoutTurns);
    }

    private static CurveAnchor findCurveAnchor(
            java.util.List<RailCompositeLibrary.Component> components) {
        RailCompositeLibrary.Component first = components.get(0);
        for (RailCompositeLibrary.Component candidate : components) {
            int horizontalDirection = 0;
            int verticalDirection = 0;
            for (RailCompositeLibrary.Component other : components) {
                if (other == candidate) {
                    continue;
                }
                int dx = other.getOffsetX() - candidate.getOffsetX();
                int dy = other.getOffsetY() - candidate.getOffsetY();
                if (dy == 0 && Math.abs(dx) == 1) {
                    horizontalDirection = dx;
                } else if (dx == 0 && Math.abs(dy) == 1) {
                    verticalDirection = dy;
                }
            }
            if (horizontalDirection != 0 && verticalDirection != 0) {
                return new CurveAnchor(
                        candidate.getOffsetX(), candidate.getOffsetY(),
                        curveQuarterTurnsFor(horizontalDirection, verticalDirection));
            }
        }
        return new CurveAnchor(first.getOffsetX(), first.getOffsetY(), 0);
    }

    private static boolean curveOccupies(CurvePlacement placement, int worldX, int worldY) {
        if (placement == null) {
            return false;
        }
        for (RailCompositeLibrary.Component component : placement.composite.getComponents()) {
            int[] rotatedOffset = rotateLayoutOffset(
                    component.getOffsetX() - placement.anchorX,
                    component.getOffsetY() - placement.anchorY,
                    placement.layoutTurns);
            if (placement.cornerX + rotatedOffset[0] == worldX
                    && placement.cornerY + rotatedOffset[1] == worldY) {
                return true;
            }
        }
        return false;
    }

    private static int renderCurveComposite(Class523 scene, Class106 renderer,
            Class497 sceneBase, Class639_Sub16 definitions,
            CurvePlacement placement, int plane) {
        if (placement == null) {
            return 0;
        }

        int rendered = 0;
        for (RailCompositeLibrary.Component component : placement.composite.getComponents()) {
            ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(
                    component.getId(), -1356282071);
            if (definition == null) {
                continue;
            }
            int rotation = (component.getRotation() + placement.layoutTurns) & 0x3;
            int[] rotatedOffset = rotateLayoutOffset(
                    component.getOffsetX() - placement.anchorX,
                    component.getOffsetY() - placement.anchorY,
                    placement.layoutTurns);
            if (renderPiece(scene, renderer, sceneBase, definition, component.getType(),
                    placement.cornerX + rotatedOffset[0],
                    placement.cornerY + rotatedOffset[1],
                    plane, rotation)) {
                rendered++;
            }
        }
        return rendered;
    }

    private static int[] rotateLayoutOffset(int offsetX, int offsetY, int quarterTurns) {
        switch (quarterTurns & 0x3) {
        case 1:
            return new int[] { offsetY, -offsetX };
        case 2:
            return new int[] { -offsetX, -offsetY };
        case 3:
            return new int[] { -offsetY, offsetX };
        default:
            return new int[] { offsetX, offsetY };
        }
    }

    private static final class CurveAnchor {
        private final int offsetX;
        private final int offsetY;
        private final int baseQuarterTurns;

        private CurveAnchor(int offsetX, int offsetY, int baseQuarterTurns) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.baseQuarterTurns = baseQuarterTurns & 0x3;
        }
    }

    private static final class CurvePlacement {
        private final RailCompositeLibrary.CompositeDefinition composite;
        private final int cornerX;
        private final int cornerY;
        private final int anchorX;
        private final int anchorY;
        private final int layoutTurns;

        private CurvePlacement(RailCompositeLibrary.CompositeDefinition composite,
                int cornerX, int cornerY, int anchorX, int anchorY, int layoutTurns) {
            this.composite = composite;
            this.cornerX = cornerX;
            this.cornerY = cornerY;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.layoutTurns = layoutTurns & 0x3;
        }
    }

    private static int curveQuarterTurnsFor(int horizontalDirection, int verticalDirection) {
        if (horizontalDirection > 0 && verticalDirection > 0) {
            return 0;
        }
        if (horizontalDirection > 0 && verticalDirection < 0) {
            return 1;
        }
        if (horizontalDirection < 0 && verticalDirection < 0) {
            return 2;
        }
        return 3;
    }

    private static int curveRotationFor(int horizontalDirection, int verticalDirection) {
        /*
         * V1 canonical mapping:
         *   base rotation = curve connecting EAST + NORTH
         *   +1 = EAST + SOUTH
         *   +2 = WEST + SOUTH
         *   +3 = WEST + NORTH
         *
         * The UI exposes a global R0-R3 mapping offset because the classified
         * cache model's saved preview rotation is visual evidence, not yet a
         * runtime-verified canonical EN orientation.
         */
        int quarterTurns = curveQuarterTurnsFor(horizontalDirection, verticalDirection);
        return (curveBaseRotation + curveRotationOffset + quarterTurns) & 0x3;
    }

    private static int routeTileCount(int startX, int startY, int endX, int endY) {
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            return 0;
        }
        return Math.abs(endX - startX) + Math.abs(endY - startY) + 1;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
