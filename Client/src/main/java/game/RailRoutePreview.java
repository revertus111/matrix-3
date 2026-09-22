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
 * V2 proves drag UX, Manhattan routing, straight-axis orientation and a saved
 * same-tile CURVE composite at the bend. A single-object curve remains only as
 * fallback authoring support. It does not place real world objects, own
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
    private static final int MAX_ROUTE_TILES = 64;
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

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String eventState = "A->B rail preview disabled.";
    private static volatile String renderState = "hidden";
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
        curveComposite = RailCompositeLibrary.findFirst(RailCompositeLibrary.Role.CURVE);
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = curveComposite == null
                ? "No saved CURVE composite found; single-object curve fallback remains available."
                : "Loaded curve composite: " + curveComposite.describe() + ".";
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
            eventState = "A->B rail preview ON. Move over ground, then hold Left mouse and drag.";
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

    public static void clearRoute() {
        dragging = false;
        committed = false;
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
        lastRenderedCycle = Integer.MIN_VALUE;
        renderState = "cleared";
        eventState = "A->B route cleared.";
    }

    public static String getStatus() {
        StringBuilder status = new StringBuilder(192);
        status.append(enabled ? "RAIL V2 ON" : "RAIL V2 OFF");
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
            liveEndX = hoveredWorldX;
            liveEndY = hoveredWorldY;
            lastRenderedCycle = Integer.MIN_VALUE;
            eventState = "Dragging route to " + liveEndX + "," + liveEndY + ".";
        }
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!enabled || objectId < 0 || scene == null || renderer == null) {
            return;
        }

        int startX;
        int startY;
        int endX;
        int endY;
        int plane;

        if (dragging) {
            startX = liveStartX;
            startY = liveStartY;
            endX = liveEndX;
            endY = liveEndY;
            plane = livePlane;
        } else if (committed) {
            startX = committedStartX;
            startY = committedStartY;
            endX = committedEndX;
            endY = committedEndY;
            plane = committedPlane;
        } else {
            return;
        }

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

        ObjectDefinitions definition =
                (ObjectDefinitions) definitions.getDefinition(objectId, -1356282071);
        if (definition == null) {
            renderState = "UNKNOWN straight object id " + objectId;
            return;
        }

        ObjectDefinitions curveDefinition = curveObjectId < 0 ? null
                : (ObjectDefinitions) definitions.getDefinition(curveObjectId, -1356282071);

        int rendered;
        if (routeOrder == RouteOrder.Y_THEN_X) {
            rendered = renderYThenX(scene, renderer, sceneBase, definitions,
                    definition, curveDefinition, startX, startY, endX, endY, plane);
        } else {
            rendered = renderXThenY(scene, renderer, sceneBase, definitions,
                    definition, curveDefinition, startX, startY, endX, endY, plane);
        }

        int requested = routeTileCount(startX, startY, endX, endY);
        boolean hasCorner = startX != endX && startY != endY;
        renderState = "DRAW " + rendered + "/" + requested + " tile(s)"
                + (requested > MAX_ROUTE_TILES ? " [capped " + MAX_ROUTE_TILES + "]" : "")
                + (hasCorner
                        ? (curveComposite != null
                                ? " V2 composite curve=" + curveComposite.getName()
                                : (curveDefinition != null
                                        ? " V2 single-curve fallback"
                                        : " V2 curve missing -> straight fallback"))
                        : " straight route");
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

        int x = startX;
        while (x != endX && attempted < MAX_ROUTE_TILES) {
            attempted++;
            if (renderPiece(scene, renderer, sceneBase, straightDefinition,
                    objectType, x, startY, plane, eastWestRotation())) {
                rendered++;
            }
            x += xStep;
        }

        if (attempted >= MAX_ROUTE_TILES) {
            return rendered;
        }

        attempted++;
        int horizontalNeighborDirection = -xStep;
        int verticalNeighborDirection = yStep;
        if (curveComposite != null) {
            if (renderCurveComposite(scene, renderer, sceneBase, definitions,
                    endX, startY, plane,
                    horizontalNeighborDirection, verticalNeighborDirection)) {
                rendered++;
            }
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
        while (attempted < MAX_ROUTE_TILES) {
            attempted++;
            if (renderPiece(scene, renderer, sceneBase, straightDefinition,
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

        int y = startY;
        while (y != endY && attempted < MAX_ROUTE_TILES) {
            attempted++;
            if (renderPiece(scene, renderer, sceneBase, straightDefinition,
                    objectType, startX, y, plane, verticalRotation())) {
                rendered++;
            }
            y += yStep;
        }

        if (attempted >= MAX_ROUTE_TILES) {
            return rendered;
        }

        attempted++;
        int horizontalNeighborDirection = xStep;
        int verticalNeighborDirection = -yStep;
        if (curveComposite != null) {
            if (renderCurveComposite(scene, renderer, sceneBase, definitions,
                    startX, endY, plane,
                    horizontalNeighborDirection, verticalNeighborDirection)) {
                rendered++;
            }
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
        while (attempted < MAX_ROUTE_TILES) {
            attempted++;
            if (renderPiece(scene, renderer, sceneBase, straightDefinition,
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
        while (attempted < MAX_ROUTE_TILES) {
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
        while (attempted < MAX_ROUTE_TILES) {
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
            if (hoveredPlane == livePlane && hoveredWorldX >= 0 && hoveredWorldY >= 0) {
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

        liveStartX = hoveredWorldX;
        liveStartY = hoveredWorldY;
        liveEndX = hoveredWorldX;
        liveEndY = hoveredWorldY;
        livePlane = hoveredPlane;
        dragging = true;
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = "Route drag started at " + liveStartX + "," + liveStartY + "," + livePlane + ".";
        return true;
    }

    private static void commitActiveDrag() {
        committedStartX = liveStartX;
        committedStartY = liveStartY;
        committedEndX = liveEndX;
        committedEndY = liveEndY;
        committedPlane = livePlane;
        committed = true;
        dragging = false;
        lastRenderedCycle = Integer.MIN_VALUE;
        eventState = "Route committed A=" + committedStartX + "," + committedStartY
                + " B=" + committedEndX + "," + committedEndY + ".";
    }

    private static void cancelActiveDrag() {
        dragging = false;
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

    private static boolean renderCurveComposite(Class523 scene, Class106 renderer,
            Class497 sceneBase, Class639_Sub16 definitions,
            int worldX, int worldY, int plane,
            int horizontalDirection, int verticalDirection) {
        RailCompositeLibrary.CompositeDefinition composite = curveComposite;
        if (composite == null) {
            return false;
        }

        int quarterTurns = curveQuarterTurnsFor(horizontalDirection, verticalDirection);
        int layoutTurns = (curveRotationOffset + quarterTurns) & 0x3;
        boolean any = false;
        for (RailCompositeLibrary.Component component : composite.getComponents()) {
            ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(
                    component.getId(), -1356282071);
            if (definition == null) {
                continue;
            }
            int rotation = (component.getRotation() + layoutTurns) & 0x3;
            int[] rotatedOffset = rotateLayoutOffset(
                    component.getOffsetX(), component.getOffsetY(), layoutTurns);
            if (renderPiece(scene, renderer, sceneBase, definition, component.getType(),
                    worldX + rotatedOffset[0], worldY + rotatedOffset[1], plane, rotation)) {
                any = true;
            }
        }
        return any;
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
