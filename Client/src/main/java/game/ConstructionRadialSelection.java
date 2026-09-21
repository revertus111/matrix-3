package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Client-owned world-space radial selection input/visual primitive.
 *
 * RWS-2 owns only input state, world-space drag geometry and the temporary
 * radial visualization. It does not own worker identity, AI, commands,
 * persistence, combat target state, cache definitions, collision or scene
 * registration.
 *
 * Edge A comes from Matrix3's already-resolved action-23 ground tile at mouse
 * press. During the drag, mouse-pixel movement is calibrated against subsequent
 * resolved world-tile changes. The selector renders at the midpoint between
 * edge A and the live edge B, while radius is half of the A-to-B span.
 */
public final class ConstructionRadialSelection {

    public enum DragButton {
        LEFT("Left mouse", MouseEvent.BUTTON1),
        RIGHT("Right mouse", MouseEvent.BUTTON3);

        private final String displayName;
        private final int awtButton;

        DragButton(String displayName, int awtButton) {
            this.displayName = displayName;
            this.awtButton = awtButton;
        }

        int getAwtButton() {
            return awtButton;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int RETICULE_GFX_ID = 4171;
    private static final int BASE_MODEL_SCALE = 128;
    private static final float SCALE_PERCENT_PER_TILE = 100.0F;
    private static final float MIN_RADIUS_TILES = 0.25F;
    private static final float MAX_RADIUS_TILES = 64.0F;
    private static final float DEFAULT_PIXELS_PER_TILE = 48.0F;
    private static final float MIN_PIXELS_PER_TILE = 8.0F;
    private static final float MAX_PIXELS_PER_TILE = 320.0F;
    private static final int MODEL_FLAGS = 2048 | 0x80000 | 0x5;
    private static final long HOVER_STALE_MS = 1250L;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean workerControlEnabled;
    private static volatile boolean dragging;
    private static volatile boolean committed;
    private static volatile DragButton dragButton = DragButton.LEFT;

    private static volatile int hoveredWorldX = -1;
    private static volatile int hoveredWorldY = -1;
    private static volatile int hoveredPlane = -1;
    private static volatile long hoveredAtMillis;

    private static volatile int originWorldX = -1;
    private static volatile int originWorldY = -1;
    private static volatile int originPlane = -1;
    private static volatile float liveCenterWorldX = -1.0F;
    private static volatile float liveCenterWorldY = -1.0F;
    private static volatile float liveRadiusTiles = MIN_RADIUS_TILES;
    private static volatile float liveDirectionWorldX;
    private static volatile float liveDirectionWorldY;

    private static volatile int committedStartWorldX = -1;
    private static volatile int committedStartWorldY = -1;
    private static volatile float committedCenterWorldX = -1.0F;
    private static volatile float committedCenterWorldY = -1.0F;
    private static volatile int committedPlane = -1;
    private static volatile float committedRadiusTiles = MIN_RADIUS_TILES;

    private static volatile int anchorMouseX;
    private static volatile int anchorMouseY;
    private static volatile int currentMouseX;
    private static volatile int currentMouseY;
    private static volatile float pixelsPerTileEstimate = DEFAULT_PIXELS_PER_TILE;

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String lastEventState = "RWS-2 Worker Control disabled.";
    private static volatile String lastRenderState = "not rendered";
    private static boolean inputListenerInstalled;

    private ConstructionRadialSelection() {
    }

    public static boolean isWorkerControlEnabled() {
        return workerControlEnabled;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean hasCommittedRadius() {
        return committed;
    }

    public static float getLiveRadiusTiles() {
        return liveRadiusTiles;
    }

    public static float getCommittedRadiusTiles() {
        return committedRadiusTiles;
    }

    public static DragButton getDragButton() {
        return dragButton;
    }

    public static void setDragButton(DragButton button) {
        if (button == null) {
            return;
        }
        if (dragging) {
            cancelActiveDrag();
        }
        dragButton = button;
        lastEventState = "RWS-2 drag button set to " + button + ".";
    }

    public static void setWorkerControlEnabled(boolean enabled) {
        if (enabled) {
            ensureInputListener();
            workerControlEnabled = true;
            lastEventState = "RWS-2 Worker Control ON. Hold " + dragButton
                    + " on valid ground and drag.";
        } else {
            if (dragging) {
                cancelActiveDrag();
            }
            workerControlEnabled = false;
            lastEventState = "RWS-2 Worker Control OFF.";
        }
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static void clearCommittedRadius() {
        committed = false;
        committedStartWorldX = -1;
        committedStartWorldY = -1;
        committedCenterWorldX = -1.0F;
        committedCenterWorldY = -1.0F;
        committedPlane = -1;
        committedRadiusTiles = MIN_RADIUS_TILES;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastRenderState = "not rendered";
        lastEventState = "RWS-2 committed radius cleared.";
    }

    public static String getStatus() {
        StringBuilder status = new StringBuilder(192);
        status.append(workerControlEnabled ? "RWS-2 ON" : "RWS-2 OFF");
        status.append(" | button=").append(dragButton);
        if (dragging) {
            status.append(" | DRAGGING edgeA=")
                    .append(originWorldX).append(',').append(originWorldY).append(',').append(originPlane)
                    .append(" edgeB=").append(hoveredWorldX).append(',').append(hoveredWorldY)
                    .append(" center=").append(formatWorld(liveCenterWorldX)).append(',')
                    .append(formatWorld(liveCenterWorldY))
                    .append(" radius=").append(formatRadius(liveRadiusTiles)).append(" tiles")
                    .append(" scale=").append(radiusToScalePercent(liveRadiusTiles)).append('%');
        } else if (committed) {
            status.append(" | COMMITTED edgeA=")
                    .append(committedStartWorldX).append(',').append(committedStartWorldY)
                    .append(',').append(committedPlane)
                    .append(" center=").append(formatWorld(committedCenterWorldX)).append(',')
                    .append(formatWorld(committedCenterWorldY))
                    .append(" radius=").append(formatRadius(committedRadiusTiles)).append(" tiles")
                    .append(" scale=").append(radiusToScalePercent(committedRadiusTiles)).append('%');
        } else {
            status.append(" | no committed radius");
        }
        status.append(" | ").append(lastEventState);
        status.append(" | render=").append(lastRenderState);
        return status.toString();
    }

    /**
     * Mirrors Matrix3's already-resolved scene-tile menu target. This is not a
     * second picker and does not alter the menu entry.
     */
    static void observeSceneMenuTile(int sourceAction, int localX, int localY) {
        if (!workerControlEnabled) {
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

        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;

        hoveredWorldX = worldX;
        hoveredWorldY = worldY;
        hoveredPlane = plane;
        hoveredAtMillis = System.currentTimeMillis();

        if (dragging && plane == originPlane) {
            updateLiveWorldDirection(worldX, worldY);
            calibratePixelsPerTile(worldX, worldY);
        }
    }

    /**
     * verified-static:
     * - GraphicsDefinition.method7764(...) returns a per-call model clone.
     * - Model.method1464(...) is Matrix3's model scale transform.
     * - The extra transform therefore affects only this radial instance.
     * - Model.method1375(...) is the live Matrix3 draw path runtime-verified by
     *   RWS-1 and ConstructionGhostPreview.
     */
    static void render(Class523 scene, Class106 renderer) {
        if (!workerControlEnabled || !dragging || scene == null || renderer == null
                || client.aClass613_8605 == null) {
            return;
        }

        float drawWorldX = liveCenterWorldX;
        float drawWorldY = liveCenterWorldY;
        int drawPlane = originPlane;
        float drawRadius = liveRadiusTiles;

        Class613 region = client.aClass613_8605;
        if (region.method7285(0) != scene) {
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class497 sceneBase = region.method7280((byte) -102);
        if (sceneBase == null) {
            lastRenderState = "WAIT scene base";
            return;
        }

        int sceneBaseWorldX = sceneBase.localX * -2109597897;
        int sceneBaseWorldY = sceneBase.localY * 417324155;
        float localX = drawWorldX - sceneBaseWorldX;
        float localY = drawWorldY - sceneBaseWorldY;
        if (drawPlane < 0 || drawPlane >= scene.aClass174Array5838.length) {
            lastRenderState = "SKIP invalid plane " + drawPlane;
            return;
        }

        Class174 ground = scene.aClass174Array5838[drawPlane];
        if (ground == null) {
            lastRenderState = "WAIT terrain";
            return;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0.0F || localY < 0.0F || localX >= sceneWidth || localY >= sceneHeight) {
            lastRenderState = "SKIP center outside scene";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = Math.round((localX + 0.5F) * tileSize);
        int sceneZ = Math.round((localY + 0.5F) * tileSize);
        int sceneY = ground.method2718(sceneX, sceneZ, 0);

        GraphicsDefinition definition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (definition == null) {
            lastRenderState = "FAIL GFX " + RETICULE_GFX_ID + " definition";
            return;
        }

        Model model = definition.method7764(renderer, MODEL_FLAGS, 0, 0, 0, 0, null, (byte) 2, 1913622280);
        if (model == null) {
            lastRenderState = "FAIL GFX " + RETICULE_GFX_ID + " model";
            return;
        }

        int runtimeScale = Math.max(1,
                Math.round(BASE_MODEL_SCALE * (radiusToScalePercent(drawRadius) / 100.0F)));
        model.method1464(runtimeScale, BASE_MODEL_SCALE, runtimeScale);

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        lastRenderState = "DRAW gfx=" + RETICULE_GFX_ID
                + " radius=" + formatRadius(drawRadius)
                + " scale=" + radiusToScalePercent(drawRadius) + "%"
                + " center=" + formatWorld(drawWorldX) + "," + formatWorld(drawWorldY)
                + "," + drawPlane;
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
        if (!workerControlEnabled) {
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || mouse.getSource() != canvas) {
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_PRESSED && mouse.getButton() == dragButton.getAwtButton()) {
            if (beginDrag(mouse.getX(), mouse.getY())) {
                mouse.consume();
            }
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_DRAGGED && dragging) {
            currentMouseX = mouse.getX();
            currentMouseY = mouse.getY();
            updateLiveRadiusFromMouse();

            /*
             * Do not consume drag motion here. Matrix3's existing mouse/menu
             * path must still see the live cursor position so action-23 ground
             * hover can keep edge B/world direction current. Press/release
             * remain consumed, so Worker Control still owns the configured
             * drag gesture without creating a normal ground click.
             */
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_RELEASED && dragging
                && mouse.getButton() == dragButton.getAwtButton()) {
            currentMouseX = mouse.getX();
            currentMouseY = mouse.getY();
            updateLiveRadiusFromMouse();
            commitActiveDrag();
            mouse.consume();
        }
    }

    private static void handleKeyEvent(KeyEvent key) {
        if (!workerControlEnabled || !dragging || key.getID() != KeyEvent.KEY_PRESSED
                || key.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return;
        }

        cancelActiveDrag();
        key.consume();
    }

    private static boolean beginDrag(int mouseX, int mouseY) {
        long age = System.currentTimeMillis() - hoveredAtMillis;
        if (hoveredWorldX < 0 || hoveredWorldY < 0 || hoveredPlane < 0
                || hoveredAtMillis == 0L || age > HOVER_STALE_MS) {
            lastEventState = "RWS-2 WAIT: move over valid ground before pressing " + dragButton + ".";
            return false;
        }

        originWorldX = hoveredWorldX;
        originWorldY = hoveredWorldY;
        originPlane = hoveredPlane;
        anchorMouseX = mouseX;
        anchorMouseY = mouseY;
        currentMouseX = mouseX;
        currentMouseY = mouseY;
        pixelsPerTileEstimate = DEFAULT_PIXELS_PER_TILE;
        liveCenterWorldX = originWorldX;
        liveCenterWorldY = originWorldY;
        liveRadiusTiles = MIN_RADIUS_TILES;
        liveDirectionWorldX = 0.0F;
        liveDirectionWorldY = 0.0F;
        dragging = true;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = "RWS-2 drag started at " + originWorldX + "," + originWorldY + "," + originPlane + ".";
        return true;
    }

    private static void updateLiveWorldDirection(int hoverX, int hoverY) {
        float worldDx = hoverX - originWorldX;
        float worldDy = hoverY - originWorldY;
        float worldDistance = (float) Math.sqrt(worldDx * worldDx + worldDy * worldDy);
        if (worldDistance < 0.25F) {
            return;
        }

        liveDirectionWorldX = worldDx / worldDistance;
        liveDirectionWorldY = worldDy / worldDistance;
    }

    private static void calibratePixelsPerTile(int hoverX, int hoverY) {
        float worldDx = hoverX - originWorldX;
        float worldDy = hoverY - originWorldY;
        float worldDistance = (float) Math.sqrt(worldDx * worldDx + worldDy * worldDy);
        if (worldDistance < 0.75F) {
            return;
        }

        int mouseDx = currentMouseX - anchorMouseX;
        int mouseDy = currentMouseY - anchorMouseY;
        float pixelDistance = (float) Math.sqrt((float) mouseDx * mouseDx + (float) mouseDy * mouseDy);
        if (pixelDistance < 4.0F) {
            return;
        }

        float estimate = pixelDistance / worldDistance;
        if (estimate < MIN_PIXELS_PER_TILE) {
            estimate = MIN_PIXELS_PER_TILE;
        } else if (estimate > MAX_PIXELS_PER_TILE) {
            estimate = MAX_PIXELS_PER_TILE;
        }

        pixelsPerTileEstimate = estimate;
        updateLiveRadiusFromMouse();
    }

    private static void updateLiveRadiusFromMouse() {
        int dx = currentMouseX - anchorMouseX;
        int dy = currentMouseY - anchorMouseY;
        float pixelDistance = (float) Math.sqrt((float) dx * dx + (float) dy * dy);
        float estimate = pixelsPerTileEstimate <= 0.0F ? DEFAULT_PIXELS_PER_TILE : pixelsPerTileEstimate;
        float spanTiles = pixelDistance / estimate;
        float radius = spanTiles * 0.5F;

        if (radius < MIN_RADIUS_TILES) {
            radius = MIN_RADIUS_TILES;
        } else if (radius > MAX_RADIUS_TILES) {
            radius = MAX_RADIUS_TILES;
        }

        liveRadiusTiles = radius;
        if (liveDirectionWorldX != 0.0F || liveDirectionWorldY != 0.0F) {
            liveCenterWorldX = originWorldX + liveDirectionWorldX * radius;
            liveCenterWorldY = originWorldY + liveDirectionWorldY * radius;
        } else {
            liveCenterWorldX = originWorldX;
            liveCenterWorldY = originWorldY;
        }
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = "RWS-2 dragging center="
                + formatWorld(liveCenterWorldX) + "," + formatWorld(liveCenterWorldY)
                + " radius=" + formatRadius(radius) + " tiles.";
    }

    private static void commitActiveDrag() {
        committedStartWorldX = originWorldX;
        committedStartWorldY = originWorldY;
        committedCenterWorldX = liveCenterWorldX;
        committedCenterWorldY = liveCenterWorldY;
        committedPlane = originPlane;
        committedRadiusTiles = liveRadiusTiles;
        committed = true;
        dragging = false;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastRenderState = "hidden after release";
        lastEventState = "RWS-2 radius committed at " + formatRadius(committedRadiusTiles)
                + " tiles; area reticule hidden after release.";
    }

    private static void cancelActiveDrag() {
        dragging = false;
        originWorldX = -1;
        originWorldY = -1;
        originPlane = -1;
        liveCenterWorldX = -1.0F;
        liveCenterWorldY = -1.0F;
        liveRadiusTiles = MIN_RADIUS_TILES;
        liveDirectionWorldX = 0.0F;
        liveDirectionWorldY = 0.0F;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = committed
                ? "RWS-2 drag cancelled; previous committed radius preserved."
                : "RWS-2 drag cancelled.";
    }

    private static int radiusToScalePercent(float radiusTiles) {
        return Math.max(25, Math.round(radiusTiles * SCALE_PERCENT_PER_TILE));
    }

    private static String formatRadius(float radius) {
        return String.format(java.util.Locale.US, "%.2f", radius);
    }

    private static String formatWorld(float coordinate) {
        return String.format(java.util.Locale.US, "%.2f", coordinate);
    }
}
