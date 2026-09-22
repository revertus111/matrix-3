package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Color;
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
 * press. Edge B is the current Matrix3-resolved action-23 ground tile while
 * dragging. Selection geometry is therefore pure world-space geometry: the
 * selector renders at midpoint(A, B), with radius distance(A, B) / 2.
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
    private static final int SETTLEMENT_WORKER_NPC_ID = 1;
    private static final String SETTLEMENT_WORKER_NAME = "Settler";
    private static final int BASE_MODEL_SCALE = 128;
    private static final int MIN_WORKER_RING_SCALE_PERCENT = 25;
    private static final int MAX_WORKER_RING_SCALE_PERCENT = 300;
    private static final float RETICULE_RING_FALLBACK_FRACTION = 0.80F;
    private static final float MIN_RADIUS_TILES = 0.0F;
    private static final float MAX_RADIUS_TILES = 64.0F;
    private static final int MODEL_FLAGS = 2048 | 0x80000 | 0x5;

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

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile int lastRenderedScalePercent;
    private static volatile int liveDetectedWorkerCount;
    private static volatile String lastDetectedWorkers = "none";
    private static volatile String lastEventState = "RWS-2 Worker Control disabled.";
    private static volatile String lastRenderState = "not rendered";

    /*
     * RWS-4 worker visual style. -1 RGB means preserve the native 4171 colour.
     * Two independent clones let the same proven ring asset act as outer +
     * inner layers without depending on a second GFX definition.
     */
    private static volatile int workerOuterRingScalePercent = 100;
    private static volatile int workerInnerRingScalePercent = 70;
    private static volatile int workerOuterRingRgb = -1;
    private static volatile int workerInnerRingRgb = -1;

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

    public static int getWorkerOuterRingScalePercent() {
        return workerOuterRingScalePercent;
    }

    public static int getWorkerInnerRingScalePercent() {
        return workerInnerRingScalePercent;
    }

    public static void setWorkerOuterRingScalePercent(int percent) {
        workerOuterRingScalePercent = clampWorkerRingScale(percent);
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static void setWorkerInnerRingScalePercent(int percent) {
        workerInnerRingScalePercent = clampWorkerRingScale(percent);
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static Color getWorkerOuterRingColor() {
        return workerOuterRingRgb < 0 ? null : new Color(workerOuterRingRgb);
    }

    public static Color getWorkerInnerRingColor() {
        return workerInnerRingRgb < 0 ? null : new Color(workerInnerRingRgb);
    }

    public static void setWorkerOuterRingColor(Color color) {
        workerOuterRingRgb = color == null ? -1 : color.getRGB() & 0xffffff;
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static void setWorkerInnerRingColor(Color color) {
        workerInnerRingRgb = color == null ? -1 : color.getRGB() & 0xffffff;
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static void resetWorkerRingColors() {
        workerOuterRingRgb = -1;
        workerInnerRingRgb = -1;
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static String getWorkerRingStyleStatus() {
        return "RWS-4 GFX " + RETICULE_GFX_ID
                + " outer=" + workerOuterRingScalePercent + "%/" + formatRgb(workerOuterRingRgb)
                + " inner=" + workerInnerRingScalePercent + "%/" + formatRgb(workerInnerRingRgb)
                + " | tint isolation=0x80000";
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
                    .append(" scale=").append(lastRenderedScalePercent).append('%');
        } else if (committed) {
            status.append(" | COMMITTED edgeA=")
                    .append(committedStartWorldX).append(',').append(committedStartWorldY)
                    .append(',').append(committedPlane)
                    .append(" center=").append(formatWorld(committedCenterWorldX)).append(',')
                    .append(formatWorld(committedCenterWorldY))
                    .append(" radius=").append(formatRadius(committedRadiusTiles)).append(" tiles")
                    .append(" scale=").append(lastRenderedScalePercent).append('%');
        } else {
            status.append(" | no committed radius");
        }
        status.append(" | workersInCircle=").append(liveDetectedWorkerCount);
        status.append(" [").append(lastDetectedWorkers).append(']');
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
            updateLiveGeometryFromWorld(worldX, worldY);
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

        /*
         * GFX 4171 is not guaranteed to be centered on its model-space origin.
         * Scaling an off-center clone around (0, 0, 0) makes one visual edge
         * drift even when the world-space midpoint/radius math is exact.
         *
         * Recenter the per-call clone on its real X/Z bounds first, then scale
         * from the centered half-extents. This keeps the model's visual center
         * aligned with midpoint(A, B), so the circumference at edge A remains
         * fixed while edge B moves.
         */
        int minX = model.method1380();
        int maxX = model.method1381();
        int minZ = model.method1384();
        int maxZ = model.method1508();

        int modelCenterX = (minX + maxX) / 2;
        int modelCenterZ = (minZ + maxZ) / 2;
        if (modelCenterX != 0 || modelCenterZ != 0) {
            model.method1358(-modelCenterX, 0, -modelCenterZ);
        }

        float halfWidthX = (maxX - minX) * 0.5F;
        float halfWidthZ = (maxZ - minZ) * 0.5F;
        float fullModelRadiusUnits = Math.max(halfWidthX, halfWidthZ);
        if (fullModelRadiusUnits <= 0.0F) {
            lastRenderState = "FAIL GFX " + RETICULE_GFX_ID + " zero horizontal bounds";
            return;
        }

        /*
         * GFX 4171's four decorative diamonds extend farther than the actual
         * circular ring. The previous exact-bounds patch therefore pinned the
         * DIAMONDS to A/B while the visible ring still slid inward.
         *
         * On AbstractModel we can inspect the cloned vertex radii directly.
         * The ring is the dense inner radial cluster; the marker diamonds are
         * separated by a large outer radial gap. Use the last radius before
         * that gap as the visual ring radius. Other renderer model types use a
         * bounded GFX-4171 fallback ratio until runtime proves a better generic
         * seam.
         */
        float ringBodyRadiusUnits = resolveReticuleRingRadiusUnits(model, fullModelRadiusUnits);
        if (ringBodyRadiusUnits <= 0.0F) {
            ringBodyRadiusUnits = fullModelRadiusUnits * RETICULE_RING_FALLBACK_FRACTION;
        }

        float desiredRadiusUnits = drawRadius * tileSize;
        int runtimeScale = Math.max(1,
                Math.round(BASE_MODEL_SCALE * desiredRadiusUnits / ringBodyRadiusUnits));
        model.method1464(runtimeScale, BASE_MODEL_SCALE, runtimeScale);
        lastRenderedScalePercent = Math.max(1,
                Math.round(runtimeScale * 100.0F / BASE_MODEL_SCALE));

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        int detectedWorkers = renderLiveWorkerPreview(
                scene, renderer, sceneBase, ground, drawPlane, drawWorldX, drawWorldY, drawRadius);
        liveDetectedWorkerCount = detectedWorkers;

        lastRenderState = "DRAW gfx=" + RETICULE_GFX_ID
                + " radius=" + formatRadius(drawRadius)
                + " scale=" + lastRenderedScalePercent + "%"
                + " ringRadiusUnits=" + formatRadius(ringBodyRadiusUnits)
                + " fullRadiusUnits=" + formatRadius(fullModelRadiusUnits)
                + " modelOffset=" + modelCenterX + "," + modelCenterZ
                + " center=" + formatWorld(drawWorldX) + "," + formatWorld(drawWorldY)
                + "," + drawPlane;
    }

    /**
     * RWS-3 client-only live worker detection/preview.
     *
     * Scope is intentionally narrow until RWS-5:
     * - iterate Matrix3's bounded active-local NPC index list only
     * - require the settlement worker NPC definition + server-overridden name
     * - require same plane
     * - test NPC tile-center distance against the live world-space circle
     * - render a temporary small 4171 marker while the drag is active
     *
     * Persistent worker IDs and final authority remain server-owned in RWS-5.
     */
    private static int renderLiveWorkerPreview(Class523 scene, Class106 renderer,
            Class497 sceneBase, Class174 ground, int plane,
            float centerWorldX, float centerWorldY, float radiusTiles) {
        if (client.aClass676_8622 == null || client.anIntArray8626 == null
                || client.aClass572_Sub9Array8623 == null || sceneBase == null || ground == null) {
            lastDetectedWorkers = "none";
            return 0;
        }

        int activeCount = client.anInt8625 * 765313669;
        if (activeCount < 0) {
            activeCount = 0;
        } else if (activeCount > client.anIntArray8626.length) {
            activeCount = client.anIntArray8626.length;
        }

        int sceneBaseWorldX = sceneBase.localX * -2109597897;
        int sceneBaseWorldY = sceneBase.localY * 417324155;
        float radiusSquared = radiusTiles * radiusTiles;
        int detected = 0;
        StringBuilder ids = new StringBuilder();

        GraphicsDefinition markerDefinition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (markerDefinition == null) {
            lastDetectedWorkers = "marker-def-missing";
            return 0;
        }

        for (int i = 0; i < activeCount; i++) {
            int npcIndex = client.anIntArray8626[i];
            LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) npcIndex);
            if (link == null || !(link.anObject9081 instanceof NPC)) {
                continue;
            }

            NPC npc = (NPC) link.anObject9081;
            if (!isSettlementWorkerPreviewNpc(npc, plane)) {
                continue;
            }

            float workerWorldX = sceneBaseWorldX + npc.screenX[0];
            float workerWorldY = sceneBaseWorldY + npc.screenY[0];
            float dx = workerWorldX - centerWorldX;
            float dy = workerWorldY - centerWorldY;
            if (dx * dx + dy * dy > radiusSquared) {
                continue;
            }

            detected++;
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(npcIndex);

            Class240 position = npc.method5394().aClass240_2647;
            if (position == null) {
                continue;
            }

            int markerSceneX = Math.round(position.aFloat2653);
            int markerSceneZ = Math.round(position.aFloat2657);
            int markerSceneY = ground.method2718(markerSceneX, markerSceneZ, 0);

            renderWorkerRingLayer(
                    markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                    workerOuterRingScalePercent, workerOuterRingRgb);
            renderWorkerRingLayer(
                    markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                    workerInnerRingScalePercent, workerInnerRingRgb);
        }

        lastDetectedWorkers = ids.length() == 0 ? "none" : ids.toString();
        return detected;
    }

    private static void renderWorkerRingLayer(GraphicsDefinition definition, Class106 renderer,
            int sceneX, int sceneY, int sceneZ, int scalePercent, int rgb) {
        Model marker = definition.method7764(
                renderer, MODEL_FLAGS, 0, 0, 0, 0, null, (byte) 2, 1913622280);
        if (marker == null) {
            return;
        }

        int minX = marker.method1380();
        int maxX = marker.method1381();
        int minZ = marker.method1384();
        int maxZ = marker.method1508();
        int markerCenterX = (minX + maxX) / 2;
        int markerCenterZ = (minZ + maxZ) / 2;
        if (markerCenterX != 0 || markerCenterZ != 0) {
            marker.method1358(-markerCenterX, 0, -markerCenterZ);
        }

        int runtimeScale = Math.max(1,
                Math.round(BASE_MODEL_SCALE * (clampWorkerRingScale(scalePercent) / 100.0F)));
        marker.method1464(runtimeScale, BASE_MODEL_SCALE, runtimeScale);

        if (rgb >= 0) {
            int[] hsl = rgbToModelHsl(rgb);
            marker.method1396(hsl[0], hsl[1], hsl[2], 128);
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        marker.method1375(TRANSFORM, RENDER_BOUNDS, 0);
    }

    private static int clampWorkerRingScale(int percent) {
        return Math.max(MIN_WORKER_RING_SCALE_PERCENT,
                Math.min(MAX_WORKER_RING_SCALE_PERCENT, percent));
    }

    private static int[] rgbToModelHsl(int rgb) {
        float r = ((rgb >> 16) & 0xff) / 255.0F;
        float g = ((rgb >> 8) & 0xff) / 255.0F;
        float b = (rgb & 0xff) / 255.0F;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float hue = 0.0F;
        float saturation = 0.0F;
        float lightness = (max + min) * 0.5F;

        if (max != min) {
            float delta = max - min;
            saturation = lightness > 0.5F
                    ? delta / (2.0F - max - min)
                    : delta / (max + min);

            if (max == r) {
                hue = (g - b) / delta + (g < b ? 6.0F : 0.0F);
            } else if (max == g) {
                hue = (b - r) / delta + 2.0F;
            } else {
                hue = (r - g) / delta + 4.0F;
            }
            hue /= 6.0F;
        }

        return new int[] {
                Math.max(0, Math.min(63, Math.round(hue * 63.0F))),
                Math.max(0, Math.min(7, Math.round(saturation * 7.0F))),
                Math.max(0, Math.min(127, Math.round(lightness * 127.0F)))
        };
    }

    private static String formatRgb(int rgb) {
        return rgb < 0
                ? "original"
                : String.format(java.util.Locale.US, "#%06X", rgb & 0xffffff);
    }

    private static boolean isSettlementWorkerPreviewNpc(NPC npc, int plane) {
        if (npc == null || npc.aClass410_11803 == null
                || (npc.aByte9009 & 0xff) != plane) {
            return false;
        }

        int definitionId = npc.aClass410_11803.anInt4819 * 1355909985;
        if (definitionId != SETTLEMENT_WORKER_NPC_ID) {
            return false;
        }

        return npc.aString11807 != null
                && SETTLEMENT_WORKER_NAME.equalsIgnoreCase(npc.aString11807.trim());
    }

    /**
     * Returns the radius of the circular ring body, excluding GFX 4171's four
     * decorative outer diamonds when the active renderer exposes AbstractModel
     * vertex data.
     */
    private static float resolveReticuleRingRadiusUnits(Model model, float fallbackBoundsRadius) {
        if (!(model instanceof AbstractModel)) {
            return fallbackBoundsRadius * RETICULE_RING_FALLBACK_FRACTION;
        }

        AbstractModel abstractModel = (AbstractModel) model;
        int vertexCount = abstractModel.maxVertexUsed;
        if (vertexCount < 8 || abstractModel.vertexX == null || abstractModel.vertexZ == null) {
            return fallbackBoundsRadius * RETICULE_RING_FALLBACK_FRACTION;
        }

        float[] radii = new float[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            float x = abstractModel.vertexX[i];
            float z = abstractModel.vertexZ[i];
            radii[i] = (float) Math.sqrt(x * x + z * z);
        }
        java.util.Arrays.sort(radii);

        int start = Math.max(1, Math.round(vertexCount * 0.55F));
        int end = Math.min(vertexCount - 2, Math.round(vertexCount * 0.98F));
        float largestGap = 0.0F;
        int largestGapIndex = -1;

        for (int i = start; i <= end; i++) {
            float gap = radii[i + 1] - radii[i];
            if (gap > largestGap) {
                largestGap = gap;
                largestGapIndex = i;
            }
        }

        float fullVertexRadius = radii[vertexCount - 1];
        float meaningfulGap = Math.max(4.0F, fullVertexRadius * 0.05F);
        if (largestGapIndex >= start && largestGap >= meaningfulGap) {
            return radii[largestGapIndex];
        }

        return fallbackBoundsRadius * RETICULE_RING_FALLBACK_FRACTION;
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
            /*
             * Do not consume drag motion here. Matrix3's existing mouse/menu
             * path must see the cursor movement and resolve the live action-23
             * ground tile. observeSceneMenuTile(...) then updates edge B and
             * all selection geometry directly in world space.
             */
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_RELEASED && dragging
                && mouse.getButton() == dragButton.getAwtButton()) {
            if (hoveredPlane == originPlane && hoveredWorldX >= 0 && hoveredWorldY >= 0) {
                updateLiveGeometryFromWorld(hoveredWorldX, hoveredWorldY);
            }
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
        liveCenterWorldX = originWorldX;
        liveCenterWorldY = originWorldY;
        liveRadiusTiles = MIN_RADIUS_TILES;
        liveDirectionWorldX = 0.0F;
        liveDirectionWorldY = 0.0F;
        liveDetectedWorkerCount = 0;
        lastDetectedWorkers = "none";
        dragging = true;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = "RWS-2 drag started at " + originWorldX + "," + originWorldY + "," + originPlane + ".";
        return true;
    }

    private static void updateLiveGeometryFromWorld(int edgeBWorldX, int edgeBWorldY) {
        float worldDx = edgeBWorldX - originWorldX;
        float worldDy = edgeBWorldY - originWorldY;
        float spanTiles = (float) Math.sqrt(worldDx * worldDx + worldDy * worldDy);
        float radius = spanTiles * 0.5F;

        if (radius > MAX_RADIUS_TILES) {
            radius = MAX_RADIUS_TILES;
        }

        liveRadiusTiles = radius;
        if (spanTiles > 0.0F) {
            float directionX = worldDx / spanTiles;
            float directionY = worldDy / spanTiles;
            liveDirectionWorldX = directionX;
            liveDirectionWorldY = directionY;
            liveCenterWorldX = originWorldX + directionX * radius;
            liveCenterWorldY = originWorldY + directionY * radius;
        } else {
            liveDirectionWorldX = 0.0F;
            liveDirectionWorldY = 0.0F;
            liveCenterWorldX = originWorldX;
            liveCenterWorldY = originWorldY;
        }

        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = "RWS-2 dragging edgeB="
                + edgeBWorldX + "," + edgeBWorldY
                + " center=" + formatWorld(liveCenterWorldX) + ","
                + formatWorld(liveCenterWorldY)
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
        liveDetectedWorkerCount = 0;
        lastDetectedWorkers = "none";
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = committed
                ? "RWS-2 drag cancelled; previous committed radius preserved."
                : "RWS-2 drag cancelled.";
    }

    private static String formatRadius(float radius) {
        return String.format(java.util.Locale.US, "%.2f", radius);
    }

    private static String formatWorld(float coordinate) {
        return String.format(java.util.Locale.US, "%.2f", coordinate);
    }
}
