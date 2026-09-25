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
    private static final int HUNGER_BASE_RGB = 0xFF8C00;
    private static final int THIRST_BASE_RGB = 0x00BFFF;
    private static final int ENERGY_BASE_RGB = 0xFFD700;
    private static final int CRITICAL_NEED_RGB = 0xFF2020;
    private static final float NEED_ARC_SLOT_DEGREES = 100.0F;
    private static final float HUNGER_ARC_CENTER_DEGREES = 30.0F;
    private static final float THIRST_ARC_CENTER_DEGREES = 150.0F;
    private static final float ENERGY_ARC_CENTER_DEGREES = 270.0F;
    private static final float NEED_ARC_RING_INNER_FRACTION = 0.55F;
    private static final float NEED_ARC_RING_OUTER_FRACTION = 1.04F;
    private static final float RETICULE_RING_FALLBACK_FRACTION = 0.80F;
    private static final float MIN_RADIUS_TILES = 0.0F;
    private static final float MAX_RADIUS_TILES = 64.0F;
    private static final int MODEL_FLAGS = 2048 | 0x80000 | 0x8000 | 0x100 | 0x5;
    private static final long HOVER_STALE_MS = 1250L;
    private static final int MIN_SELECTION_DRAG_PIXELS = 6;
    private static final int CLEAR_SELECTION_MENU_ACTION = 1530;
    private static final int MATRIX3_FIRST_OBJECT_ACTION = 3;
    private static final int MATRIX3_FIRST_NPC_ACTION = 9;
    private static final int STARTER_TREE_OBJECT_ID = 1276;
    private static final int STARTER_STONE_OBJECT_ID = 11933;
    private static final int STARTER_ORE_OBJECT_ID = 11936;
    private static final int STARTER_FOOD_NPC_ID = 327;

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
    private static volatile int[] liveDetectedWorkerNpcIndexes = new int[0];
    private static volatile int[] committedWorkerNpcIndexes = new int[0];
    private static volatile boolean livePlayerSelected;
    private static volatile boolean committedPlayerSelected;
    private static volatile int dragPressMouseX;
    private static volatile int dragPressMouseY;
    private static volatile boolean dragThresholdPassed;
    // A completed selection drag must not fall through as Matrix3 Walk Here.
    // The next real mouse press clears this latch, so it cannot eat a later click.
    private static volatile boolean selectionDragJustCommitted;
    private static volatile String lastEventState = "RWS-5 Worker Control disabled.";
    private static volatile String lastRenderState = "not rendered";

    /*
     * RWS-4 worker visual style. -1 RGB means preserve the native 4171 colour.
     * Two independent clones let the same proven ring asset act as outer +
     * inner layers without depending on a second GFX definition.
     */
    private static volatile int workerOuterRingScalePercent = 100;
    private static volatile int workerInnerRingScalePercent = 70;
    private static volatile int dragRingRgb = -1;
    private static volatile int workerOuterRingRgb = -1;
    private static volatile int workerInnerRingRgb = -1;

    /*
     * Worker-needs HUD visual prototype.
     * These are deliberately demo values until a clean server -> client needs
     * metadata seam is approved. Rendering ownership is production-shaped so
     * the eventual live values can replace only these numbers.
     */
    private static volatile boolean workerNeedsPreviewEnabled;
    private static volatile int demoHunger = 65;
    private static volatile int demoThirst = 35;
    private static volatile int demoEnergy = 70;
    private static volatile int needsArcScalePercent = 70;
    private static volatile String needsArcMaskState = "not rendered";

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

    public static int getCommittedWorkerCount() {
        return committedWorkerNpcIndexes.length;
    }

    public static String getCommittedWorkerNpcIndexesCsv() {
        return formatNpcIndexes(committedWorkerNpcIndexes);
    }

    public static boolean hasCommittedWorkerSelection() {
        return committed && committedWorkerNpcIndexes.length > 0;
    }

    public static boolean hasCommittedSelection() {
        return committed && (committedWorkerNpcIndexes.length > 0 || committedPlayerSelected);
    }

    public static boolean isLocalPlayerSelected() {
        return committed && committedPlayerSelected;
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
        lastEventState = "RWS-5 drag button set to " + button + ".";
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

    public static Color getDragRingColor() {
        return dragRingRgb < 0 ? null : new Color(dragRingRgb);
    }

    public static void setDragRingColor(Color color) {
        dragRingRgb = color == null ? -1 : color.getRGB() & 0xffffff;
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

    public static void resetRingColors() {
        dragRingRgb = -1;
        workerOuterRingRgb = -1;
        workerInnerRingRgb = -1;
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static String getWorkerRingStyleStatus() {
        return "RWS-4 GFX " + RETICULE_GFX_ID
                + " drag=" + formatRgb(dragRingRgb)
                + " | outer=" + workerOuterRingScalePercent + "%/" + formatRgb(workerOuterRingRgb)
                + " | inner=" + workerInnerRingScalePercent + "%/" + formatRgb(workerInnerRingRgb)
                + " | color-isolation=0x80000 texture-isolation=0x8000"
                + " | renderer-aware texture detach"
                + " | AbstractModel/Class89_Sub2/OpenGLModel";
    }

    public static boolean isWorkerNeedsPreviewEnabled() {
        return workerNeedsPreviewEnabled;
    }

    public static void setWorkerNeedsPreviewEnabled(boolean enabled) {
        workerNeedsPreviewEnabled = enabled;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = enabled
                ? "Worker Needs HUD preview ON (demo values)."
                : "Worker Needs HUD preview OFF.";
    }

    public static void setWorkerNeedsPreviewValues(int hunger, int thirst, int energy) {
        demoHunger = clampNeedValue(hunger);
        demoThirst = clampNeedValue(thirst);
        demoEnergy = clampNeedValue(energy);
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static void setWorkerNeedsArcScalePercent(int percent) {
        needsArcScalePercent = clampWorkerRingScale(percent);
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static int getWorkerNeedsArcScalePercent() {
        return needsArcScalePercent;
    }

    public static String getWorkerNeedsPreviewStatus() {
        return "Needs HUD " + (workerNeedsPreviewEnabled ? "ON" : "OFF")
                + " | DEMO ONLY | ARCS"
                + " | Hunger=" + demoHunger + "/100"
                + " | Thirst=" + demoThirst + "/100"
                + " | Energy=" + demoEnergy + "/100"
                + " | sharedScale=" + needsArcScalePercent + "%"
                + " | mask=" + needsArcMaskState;
    }

    public static void setWorkerControlEnabled(boolean enabled) {
        if (enabled) {
            ensureInputListener();
            workerControlEnabled = true;
            lastEventState = "RWS-5 Worker Control ON. Hold " + dragButton
                    + " on valid ground and drag.";
        } else {
            if (dragging) {
                cancelActiveDrag();
            }
            workerControlEnabled = false;
            lastEventState = "RWS-5 Worker Control OFF.";
        }
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static void clearCommittedRadius() {
        clearCommittedSelectionLocal();
        lastEventState = "RWS-5 committed selection cleared.";
        syncCommittedSelectionToServer();
    }

    private static void clearCommittedSelectionLocal() {
        committed = false;
        committedStartWorldX = -1;
        committedStartWorldY = -1;
        committedCenterWorldX = -1.0F;
        committedCenterWorldY = -1.0F;
        committedPlane = -1;
        committedRadiusTiles = MIN_RADIUS_TILES;
        committedWorkerNpcIndexes = new int[0];
        liveDetectedWorkerNpcIndexes = new int[0];
        livePlayerSelected = false;
        committedPlayerSelected = false;
        dragThresholdPassed = false;
        selectionDragJustCommitted = false;
        liveDetectedWorkerCount = 0;
        lastDetectedWorkers = "none";
        lastRenderedCycle = Integer.MIN_VALUE;
        lastRenderState = "not rendered";
    }

    public static String getStatus() {
        StringBuilder status = new StringBuilder(192);
        status.append(workerControlEnabled ? "RWS-5 ON" : "RWS-5 OFF");
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
        status.append(" | self=").append((dragging ? livePlayerSelected : committedPlayerSelected) ? "YES" : "NO");
        status.append(" | ").append(lastEventState);
        status.append(" | render=").append(lastRenderState);
        return status.toString();
    }

    /**
     * Adds one RuneScape-native world context option while an RTS selection is
     * committed. Ground/object/NPC right-click all expose the same clear action
     * without replacing Matrix3's normal interactions.
     */
    static void mirrorWorldSelectionEntry(int sourceAction, int localX, int localY) {
        if (!workerControlEnabled || !hasCommittedSelection()
                || Class25.aBool165 || 357782167 * Class25.anInt172 >= 504) {
            return;
        }
        int normalizedAction = sourceAction >= 2000 ? sourceAction - 2000 : sourceAction;
        if (!isWorldMenuSourceAction(normalizedAction) || hasMenuAction(CLEAR_SELECTION_MENU_ACTION)) {
            return;
        }
        Class572_Sub12_Sub10 entry = new Class572_Sub12_Sub10(
                "Clear Selection", "", -646491435 * client.anInt8751,
                CLEAR_SELECTION_MENU_ACTION, -1, 0L, localX, localY,
                true, false, 0L, true);
        Class412.method5075(entry, 722976984);
    }

    private static boolean isWorldMenuSourceAction(int action) {
        return action == MATRIX3_TILE_ACTION
                || action >= 3 && action <= 6
                || action == 1001 || action == 1002
                || action >= 9 && action <= 13
                || action == 1003;
    }

    private static boolean hasMenuAction(int targetAction) {
        for (Class572_Sub12_Sub10 entry =
                (Class572_Sub12_Sub10) Class25.aClass675_174.method7932((byte) 50);
                entry != null;
                entry = (Class572_Sub12_Sub10) Class25.aClass675_174.method7926(1709126908)) {
            int action = entry.anInt11402 * -44467871;
            if (action >= 2000) {
                action -= 2000;
            }
            if (action == targetAction) {
                return true;
            }
        }
        return false;
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
        if ((!workerControlEnabled && !workerNeedsPreviewEnabled)
                || scene == null || renderer == null || client.aClass613_8605 == null) {
            return;
        }

        boolean renderDragSelection = workerControlEnabled && dragging;
        boolean renderCommittedSelection = workerControlEnabled
                && !dragging && (committedWorkerNpcIndexes.length > 0 || committedPlayerSelected);
        boolean renderNeedsPreview = workerNeedsPreviewEnabled;
        if (!renderDragSelection && !renderCommittedSelection && !renderNeedsPreview) {
            return;
        }

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

        int renderedNeedsWorkers = renderNeedsPreview
                ? renderWorkerNeedsPreview(scene, renderer) : 0;

        if (!renderDragSelection && !renderCommittedSelection) {
            lastRenderState = renderedNeedsWorkers >= 0
                    ? "NEEDS preview workers=" + renderedNeedsWorkers
                    : "WAIT needs preview";
            return;
        }

        if (renderCommittedSelection) {
            int sceneBaseWorldX = sceneBase.localX * -2109597897;
            int sceneBaseWorldY = sceneBase.localY * 417324155;
            int sceneWidth = scene.anInt5833 * -1396185127;
            int sceneHeight = scene.anInt5834 * -1519623925;
            if (committedCenterWorldX < sceneBaseWorldX
                    || committedCenterWorldY < sceneBaseWorldY
                    || committedCenterWorldX >= sceneBaseWorldX + sceneWidth
                    || committedCenterWorldY >= sceneBaseWorldY + sceneHeight) {
                clearCommittedSelectionLocal();
                lastEventState = "RWS-5 committed selection cleared after leaving its scene.";
                return;
            }

            int renderedWorkers = renderCommittedWorkerSelection(scene, renderer);
            boolean renderedSelf = renderCommittedPlayerSelection(scene, renderer);
            if (renderedWorkers >= 0) {
                lastRenderState = "COMMITTED worker rings=" + renderedWorkers
                        + " [" + formatNpcIndexes(committedWorkerNpcIndexes) + "]"
                        + " self=" + (renderedSelf ? "YES" : (committedPlayerSelected ? "WAIT" : "NO"))
                        + (renderNeedsPreview ? " needsHUD=" + renderedNeedsWorkers : "");
            } else {
                lastRenderState = "WAIT committed selection render";
            }
            return;
        }

        float drawWorldX = liveCenterWorldX;
        float drawWorldY = liveCenterWorldY;
        int drawPlane = originPlane;
        float drawRadius = liveRadiusTiles;

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
        applyRingTint(model, dragRingRgb);

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
            liveDetectedWorkerNpcIndexes = new int[0];
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
        int[] detectedNpcIndexes = new int[activeCount];

        GraphicsDefinition markerDefinition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (markerDefinition == null) {
            liveDetectedWorkerNpcIndexes = new int[0];
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

            detectedNpcIndexes[detected++] = npcIndex;

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
            if (!workerNeedsPreviewEnabled) {
                renderWorkerRingLayer(
                        markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                        workerInnerRingScalePercent, workerInnerRingRgb);
            }
        }

        liveDetectedWorkerNpcIndexes = detected == detectedNpcIndexes.length
                ? detectedNpcIndexes
                : java.util.Arrays.copyOf(detectedNpcIndexes, detected);
        lastDetectedWorkers = formatNpcIndexes(liveDetectedWorkerNpcIndexes);

        livePlayerSelected = false;
        Player localPlayer = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (localPlayer != null && (localPlayer.aByte9009 & 0xff) == plane) {
            float playerWorldX = sceneBaseWorldX + localPlayer.screenX[0];
            float playerWorldY = sceneBaseWorldY + localPlayer.screenY[0];
            float playerDx = playerWorldX - centerWorldX;
            float playerDy = playerWorldY - centerWorldY;
            if (playerDx * playerDx + playerDy * playerDy <= radiusSquared) {
                livePlayerSelected = true;
                Class240 playerPosition = localPlayer.method5394().aClass240_2647;
                if (playerPosition != null) {
                    int markerSceneX = Math.round(playerPosition.aFloat2653);
                    int markerSceneZ = Math.round(playerPosition.aFloat2657);
                    int markerSceneY = ground.method2718(markerSceneX, markerSceneZ, 0);
                    renderWorkerRingLayer(
                            markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                            workerOuterRingScalePercent, workerOuterRingRgb);
                    if (!workerNeedsPreviewEnabled) {
                        renderWorkerRingLayer(
                                markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                                workerInnerRingScalePercent, workerInnerRingRgb);
                    }
                }
            }
        }
        return detected;
    }

    /**
     * Worker-needs HUD visual prototype.
     *
     * Server ownership is intentionally untouched here. The current persistent
     * needs live in SettlementWorkerState, but no clean per-worker client
     * metadata channel exists yet. This prototype validates ring density,
     * ordering, scale and severity colours on every active Settler using demo
     * values controlled from Con Revamp.
     */
    private static int renderWorkerNeedsPreview(Class523 scene, Class106 renderer) {
        if (!workerNeedsPreviewEnabled || scene == null || renderer == null
                || client.aClass676_8622 == null || client.anIntArray8626 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return -1;
        }

        int plane = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff;
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            return -1;
        }
        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            return -1;
        }

        GraphicsDefinition definition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (definition == null) {
            return -1;
        }

        int activeCount = client.anInt8625 * 765313669;
        if (activeCount < 0) {
            activeCount = 0;
        } else if (activeCount > client.anIntArray8626.length) {
            activeCount = client.anIntArray8626.length;
        }

        int hungerRgb = needSeverityColor(
                HUNGER_BASE_RGB, demoHunger / 80.0F);
        int thirstRgb = needSeverityColor(
                THIRST_BASE_RGB, demoThirst / 80.0F);
        int energyRgb = needSeverityColor(
                ENERGY_BASE_RGB, (100 - demoEnergy) / 80.0F);

        float hungerWellbeing = (100 - demoHunger) / 100.0F;
        float thirstWellbeing = (100 - demoThirst) / 100.0F;
        float energyWellbeing = demoEnergy / 100.0F;

        int rendered = 0;
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

            Class240 position = npc.method5394().aClass240_2647;
            if (position == null) {
                continue;
            }

            int sceneX = Math.round(position.aFloat2653);
            int sceneZ = Math.round(position.aFloat2657);
            int sceneY = ground.method2718(sceneX, sceneZ, 0);

            boolean hungerArc = renderWorkerArcLayer(
                    definition, renderer, sceneX, sceneY, sceneZ,
                    needsArcScalePercent, hungerRgb,
                    HUNGER_ARC_CENTER_DEGREES,
                    NEED_ARC_SLOT_DEGREES * hungerWellbeing);
            boolean thirstArc = renderWorkerArcLayer(
                    definition, renderer, sceneX, sceneY, sceneZ,
                    needsArcScalePercent, thirstRgb,
                    THIRST_ARC_CENTER_DEGREES,
                    NEED_ARC_SLOT_DEGREES * thirstWellbeing);
            boolean energyArc = renderWorkerArcLayer(
                    definition, renderer, sceneX, sceneY, sceneZ,
                    needsArcScalePercent, energyRgb,
                    ENERGY_ARC_CENTER_DEGREES,
                    NEED_ARC_SLOT_DEGREES * energyWellbeing);

            if (hungerArc || thirstArc || energyArc) {
                rendered++;
            }
        }
        return rendered;
    }

    /**
     * RWS-5 released-selection visualization.
     *
     * The large drag circle is temporary. Once selection is committed, the
     * small layered rings remain attached to the exact runtime NPC indexes
     * that were committed on release, so the player can always see the active
     * command group. Persistent command authority remains server-owned.
     *
     * @return rendered selected-worker count; 0 when the old runtime selection
     *         no longer exists; -1 when rendering prerequisites are unavailable.
     */
    private static int renderCommittedWorkerSelection(Class523 scene, Class106 renderer) {
        if (scene == null || renderer == null || client.aClass676_8622 == null
                || committedWorkerNpcIndexes.length == 0) {
            return -1;
        }
        if (committedPlane < 0 || committedPlane >= scene.aClass174Array5838.length) {
            return 0;
        }

        Class174 ground = scene.aClass174Array5838[committedPlane];
        if (ground == null) {
            return -1;
        }

        GraphicsDefinition markerDefinition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (markerDefinition == null) {
            return -1;
        }

        int rendered = 0;
        for (int npcIndex : committedWorkerNpcIndexes) {
            LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) npcIndex);
            if (link == null || !(link.anObject9081 instanceof NPC)) {
                continue;
            }

            NPC npc = (NPC) link.anObject9081;
            if (!isSettlementWorkerPreviewNpc(npc, committedPlane)) {
                continue;
            }

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
            if (!workerNeedsPreviewEnabled) {
                renderWorkerRingLayer(
                        markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                        workerInnerRingScalePercent, workerInnerRingRgb);
            }
            rendered++;
        }
        return rendered;
    }

    private static boolean renderCommittedPlayerSelection(Class523 scene, Class106 renderer) {
        if (!committedPlayerSelected || scene == null || renderer == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null
                || committedPlane < 0 || committedPlane >= scene.aClass174Array5838.length) {
            return false;
        }

        Player localPlayer = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if ((localPlayer.aByte9009 & 0xff) != committedPlane) {
            return false;
        }
        Class174 ground = scene.aClass174Array5838[committedPlane];
        if (ground == null) {
            return false;
        }
        GraphicsDefinition markerDefinition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (markerDefinition == null) {
            return false;
        }
        Class240 position = localPlayer.method5394().aClass240_2647;
        if (position == null) {
            return false;
        }

        int markerSceneX = Math.round(position.aFloat2653);
        int markerSceneZ = Math.round(position.aFloat2657);
        int markerSceneY = ground.method2718(markerSceneX, markerSceneZ, 0);
        renderWorkerRingLayer(
                markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                workerOuterRingScalePercent, workerOuterRingRgb);
        if (!workerNeedsPreviewEnabled) {
            renderWorkerRingLayer(
                    markerDefinition, renderer, markerSceneX, markerSceneY, markerSceneZ,
                    workerInnerRingScalePercent, workerInnerRingRgb);
        }
        return true;
    }

    private static boolean renderWorkerArcLayer(GraphicsDefinition definition, Class106 renderer,
            int sceneX, int sceneY, int sceneZ, int scalePercent, int rgb,
            float centerDegrees, float visibleSpanDegrees) {
        if (visibleSpanDegrees <= 0.5F) {
            return false;
        }

        Model marker = definition.method7764(
                renderer, MODEL_FLAGS, 0, 0, 0, 0, null, (byte) 2, 1913622280);
        if (marker == null) {
            needsArcMaskState = "model-null";
            return false;
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
        applyRingTint(marker, rgb);

        if (!maskOpenGlModelToArc(marker, centerDegrees, visibleSpanDegrees)) {
            needsArcMaskState = marker.getClass().getSimpleName() + " unsupported";
            return false;
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        marker.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        return true;
    }

    /**
     * Cuts an isolated GFX 4171 OpenGL clone into one angular arch.
     *
     * OpenGL faces reference duplicated render vertices. anIntArray10329 /
     * aShortArray10330 provide the original-vertex -> render-vertex mapping, so
     * invert it once per tiny clone and classify each face by the X/Z centroid
     * of its original model vertices. Faces outside the requested angular span
     * are made fully transparent through Model.method1473(...).
     *
     * MODEL_FLAGS includes 0x100 so face alpha is isolated on the clone.
     */
    private static boolean maskOpenGlModelToArc(
            Model model, float centerDegrees, float visibleSpanDegrees) {
        if (!(model instanceof OpenGLModel)) {
            return false;
        }

        OpenGLModel gl = (OpenGLModel) model;
        if (gl.anInt10299 <= 0 || gl.anInt10291 <= 0 || gl.anInt10285 <= 0
                || gl.aShortArray10303 == null || gl.aShortArray10327 == null
                || gl.aShortArray10305 == null || gl.anIntArray10329 == null
                || gl.aShortArray10330 == null || gl.anIntArray10336 == null
                || gl.anIntArray10331 == null) {
            return false;
        }

        int[] originalByRenderVertex = new int[gl.anInt10291];
        java.util.Arrays.fill(originalByRenderVertex, -1);

        int originalVertexCount = Math.min(gl.anInt10285, gl.anIntArray10329.length - 1);
        for (int originalVertex = 0; originalVertex < originalVertexCount; originalVertex++) {
            int start = gl.anIntArray10329[originalVertex];
            int end = gl.anIntArray10329[originalVertex + 1];
            start = Math.max(0, Math.min(start, gl.aShortArray10330.length));
            end = Math.max(start, Math.min(end, gl.aShortArray10330.length));

            for (int index = start; index < end; index++) {
                int encoded = gl.aShortArray10330[index] & 0xffff;
                if (encoded == 0) {
                    break;
                }
                int renderVertex = encoded - 1;
                if (renderVertex >= 0 && renderVertex < originalByRenderVertex.length) {
                    originalByRenderVertex[renderVertex] = originalVertex;
                }
            }
        }

        int faceCount = Math.min(gl.anInt10299,
                Math.min(gl.aShortArray10303.length,
                        Math.min(gl.aShortArray10327.length, gl.aShortArray10305.length)));
        byte[] sourceAlpha = model.method1392();
        byte[] arcAlpha = new byte[gl.anInt10299];
        if (sourceAlpha != null) {
            System.arraycopy(sourceAlpha, 0, arcAlpha, 0,
                    Math.min(sourceAlpha.length, arcAlpha.length));
        }

        float minX = gl.method1380();
        float maxX = gl.method1381();
        float minZ = gl.method1384();
        float maxZ = gl.method1508();
        float fallbackRadius = Math.max((maxX - minX) * 0.5F, (maxZ - minZ) * 0.5F);
        float ringBodyRadius = resolveReticuleRingRadiusUnits(gl, fallbackRadius);
        if (ringBodyRadius <= 0.0F) {
            ringBodyRadius = fallbackRadius * RETICULE_RING_FALLBACK_FRACTION;
        }
        float minimumRingRadius = ringBodyRadius * NEED_ARC_RING_INNER_FRACTION;
        float maximumRingRadius = ringBodyRadius * NEED_ARC_RING_OUTER_FRACTION;

        float halfSpan = Math.max(0.0F, Math.min(180.0F, visibleSpanDegrees * 0.5F));
        int visibleFaces = 0;
        int hiddenOutsideArc = 0;
        int hiddenOutsideRingBody = 0;
        for (int face = 0; face < faceCount; face++) {
            int renderA = gl.aShortArray10303[face] & 0xffff;
            int renderB = gl.aShortArray10327[face] & 0xffff;
            int renderC = gl.aShortArray10305[face] & 0xffff;
            if (renderA >= originalByRenderVertex.length
                    || renderB >= originalByRenderVertex.length
                    || renderC >= originalByRenderVertex.length) {
                arcAlpha[face] = (byte) 255;
                continue;
            }

            int originalA = originalByRenderVertex[renderA];
            int originalB = originalByRenderVertex[renderB];
            int originalC = originalByRenderVertex[renderC];
            if (originalA < 0 || originalB < 0 || originalC < 0
                    || originalA >= gl.anIntArray10336.length
                    || originalB >= gl.anIntArray10336.length
                    || originalC >= gl.anIntArray10336.length
                    || originalA >= gl.anIntArray10331.length
                    || originalB >= gl.anIntArray10331.length
                    || originalC >= gl.anIntArray10331.length) {
                arcAlpha[face] = (byte) 255;
                continue;
            }

            float ax = gl.anIntArray10336[originalA];
            float az = gl.anIntArray10331[originalA];
            float bx = gl.anIntArray10336[originalB];
            float bz = gl.anIntArray10331[originalB];
            float cx = gl.anIntArray10336[originalC];
            float cz = gl.anIntArray10331[originalC];

            float radiusA = (float) Math.sqrt(ax * ax + az * az);
            float radiusB = (float) Math.sqrt(bx * bx + bz * bz);
            float radiusC = (float) Math.sqrt(cx * cx + cz * cz);

            /*
             * A centroid-only radius test is too permissive for 4171. Its
             * decorative diamonds/spikes can straddle the circular band while
             * still placing their triangle centroid inside it. Require every
             * vertex of the face to live in the calibrated ring-body annulus.
             * This preserves actual ring strip triangles and rejects decorative
             * geometry that crosses inward/outward from the strip.
             */
            float faceMinRadius = Math.min(radiusA, Math.min(radiusB, radiusC));
            float faceMaxRadius = Math.max(radiusA, Math.max(radiusB, radiusC));
            if (faceMinRadius < minimumRingRadius || faceMaxRadius > maximumRingRadius) {
                arcAlpha[face] = (byte) 255;
                hiddenOutsideRingBody++;
                continue;
            }

            float x = (ax + bx + cx) / 3.0F;
            float z = (az + bz + cz) / 3.0F;
            float angle = (float) Math.toDegrees(Math.atan2(z, x));
            if (angle < 0.0F) {
                angle += 360.0F;
            }

            float delta = Math.abs(angle - normalizeDegrees(centerDegrees));
            if (delta > 180.0F) {
                delta = 360.0F - delta;
            }
            if (delta > halfSpan) {
                arcAlpha[face] = (byte) 255;
                hiddenOutsideArc++;
            } else {
                visibleFaces++;
            }
        }

        model.method1473((byte) 0, arcAlpha);
        needsArcMaskState = "OpenGL strict ring-body arc"
                + " visible=" + visibleFaces
                + " bodyHidden=" + hiddenOutsideRingBody
                + " arcHidden=" + hiddenOutsideArc
                + " band=" + formatRadius(minimumRingRadius)
                + ".." + formatRadius(maximumRingRadius)
                + " ringR=" + formatRadius(ringBodyRadius);
        return true;
    }

    private static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
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

        applyRingTint(marker, rgb);

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        marker.method1375(TRANSFORM, RENDER_BOUNDS, 0);
    }

    private static void applyRingTint(Model model, int rgb) {
        if (model == null || rgb < 0) {
            return;
        }

        /*
         * GFX 4171 is visibly texture-driven. Face-HSL tint alone therefore
         * leaves the red artwork intact. MODEL_FLAGS includes 0x8000 so this
         * per-call clone owns a private face-texture array; custom-color mode
         * can safely detach those textures from the clone only, then tint the
         * exposed face colours. Native/original mode never enters this path.
         */
        java.util.Set<Short> textureIds = new java.util.HashSet<Short>();

        if (model instanceof AbstractModel) {
            AbstractModel abstractModel = (AbstractModel) model;
            short[] textures = abstractModel.aShortArray10821;
            if (textures != null) {
                int faceCount = Math.min(abstractModel.anInt10833, textures.length);
                for (int i = 0; i < faceCount; i++) {
                    short textureId = textures[i];
                    if (textureId != (short) -1) {
                        textureIds.add(Short.valueOf(textureId));
                    }
                }
            }
        } else if (model instanceof Class89_Sub2) {
            Class89_Sub2 softwareModel = (Class89_Sub2) model;
            short[] textures = softwareModel.aShortArray10591;
            if (textures != null) {
                int faceCount = Math.min(softwareModel.anInt10573, textures.length);
                for (int i = 0; i < faceCount; i++) {
                    short textureId = textures[i];
                    if (textureId != (short) -1) {
                        textureIds.add(Short.valueOf(textureId));
                    }
                }
            }
        } else if (model instanceof OpenGLModel) {
            OpenGLModel openGLModel = (OpenGLModel) model;
            short[] textures = openGLModel.aShortArray10306;
            if (textures != null) {
                int faceCount = Math.min(openGLModel.anInt10299, textures.length);
                for (int i = 0; i < faceCount; i++) {
                    short textureId = textures[i];
                    if (textureId != (short) -1) {
                        textureIds.add(Short.valueOf(textureId));
                    }
                }
            }
        }

        for (Short textureId : textureIds) {
            model.method1475(textureId.shortValue(), (short) -1);
        }

        int[] hsl = rgbToModelHsl(rgb);
        model.method1396(hsl[0], hsl[1], hsl[2], 128);
    }

    private static int clampNeedValue(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int needSeverityColor(int baseRgb, float criticalProgress) {
        float severity = Math.max(0.0F, Math.min(1.0F, criticalProgress));
        if (severity <= 0.5F) {
            return baseRgb;
        }
        float blend = (severity - 0.5F) / 0.5F;
        return blendRgb(baseRgb, CRITICAL_NEED_RGB, blend);
    }

    private static int blendRgb(int fromRgb, int toRgb, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int fromR = fromRgb >> 16 & 0xff;
        int fromG = fromRgb >> 8 & 0xff;
        int fromB = fromRgb & 0xff;
        int toR = toRgb >> 16 & 0xff;
        int toG = toRgb >> 8 & 0xff;
        int toB = toRgb & 0xff;

        int r = Math.round(fromR + (toR - fromR) * t);
        int g = Math.round(fromG + (toG - fromG) * t);
        int b = Math.round(fromB + (toB - fromB) * t);
        return r << 16 | g << 8 | b;
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
     * RWS-5 selection snapshot is cached by the render-thread worker preview.
     * The AWT release handler only copies that already-proven set.
     */
    private static String formatNpcIndexes(int[] npcIndexes) {
        if (npcIndexes == null || npcIndexes.length == 0) {
            return "none";
        }
        StringBuilder csv = new StringBuilder();
        for (int npcIndex : npcIndexes) {
            if (csv.length() > 0) {
                csv.append(',');
            }
            csv.append(npcIndex);
        }
        return csv.toString();
    }


    private static void syncCommittedSelectionToServer() {
        String command;
        if (committedWorkerNpcIndexes.length == 0 && !committedPlayerSelected) {
            command = "itembrowser settlement workerselectionclear";
        } else {
            command = "itembrowser settlement workerselectionset "
                    + (committedWorkerNpcIndexes.length == 0
                            ? "none" : formatNpcIndexes(committedWorkerNpcIndexes))
                    + " " + (committedPlayerSelected ? "self" : "noself");
        }
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null) {
            lastEventState += " | server selection sync failed: " + error;
        }
    }

    /**
     * Returns the radius of the circular ring body, excluding GFX 4171's four
     * decorative outer diamonds when the active renderer exposes AbstractModel
     * vertex data.
     */
    private static float resolveReticuleRingRadiusUnits(Model model, float fallbackBoundsRadius) {
        float[] radii = null;

        if (model instanceof AbstractModel) {
            AbstractModel abstractModel = (AbstractModel) model;
            int vertexCount = abstractModel.maxVertexUsed;
            if (vertexCount >= 8 && abstractModel.vertexX != null && abstractModel.vertexZ != null) {
                radii = new float[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    float x = abstractModel.vertexX[i];
                    float z = abstractModel.vertexZ[i];
                    radii[i] = (float) Math.sqrt(x * x + z * z);
                }
            }
        } else if (model instanceof OpenGLModel) {
            OpenGLModel openGLModel = (OpenGLModel) model;
            int vertexCount = openGLModel.anInt10285;
            if (vertexCount >= 8 && openGLModel.anIntArray10336 != null
                    && openGLModel.anIntArray10331 != null) {
                vertexCount = Math.min(vertexCount,
                        Math.min(openGLModel.anIntArray10336.length,
                                openGLModel.anIntArray10331.length));
                radii = new float[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    float x = openGLModel.anIntArray10336[i];
                    float z = openGLModel.anIntArray10331[i];
                    radii[i] = (float) Math.sqrt(x * x + z * z);
                }
            }
        }

        if (radii == null || radii.length < 8) {
            return fallbackBoundsRadius * RETICULE_RING_FALLBACK_FRACTION;
        }

        java.util.Arrays.sort(radii);

        int start = Math.max(1, Math.round(radii.length * 0.55F));
        int end = Math.min(radii.length - 2, Math.round(radii.length * 0.98F));
        float largestGap = 0.0F;
        int largestGapIndex = -1;

        for (int i = start; i <= end; i++) {
            float gap = radii[i + 1] - radii[i];
            if (gap > largestGap) {
                largestGap = gap;
                largestGapIndex = i;
            }
        }

        float fullVertexRadius = radii[radii.length - 1];
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
            beginDrag(mouse.getX(), mouse.getY());
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_DRAGGED && dragging) {
            int dx = mouse.getX() - dragPressMouseX;
            int dy = mouse.getY() - dragPressMouseY;
            if (!dragThresholdPassed
                    && dx * dx + dy * dy >= MIN_SELECTION_DRAG_PIXELS * MIN_SELECTION_DRAG_PIXELS) {
                dragThresholdPassed = true;
            }
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
            if (!dragThresholdPassed) {
                cancelActiveDrag();
                lastEventState = committed
                        ? "RWS-5 click preserved the previous committed selection."
                        : "RWS-5 click ignored; drag to select.";
                return;
            }
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
            lastEventState = "RWS-5 WAIT: move over valid ground before pressing " + dragButton + ".";
            return false;
        }

        originWorldX = hoveredWorldX;
        originWorldY = hoveredWorldY;
        originPlane = hoveredPlane;
        dragPressMouseX = mouseX;
        dragPressMouseY = mouseY;
        dragThresholdPassed = false;
        selectionDragJustCommitted = false;
        liveCenterWorldX = originWorldX;
        liveCenterWorldY = originWorldY;
        liveRadiusTiles = MIN_RADIUS_TILES;
        liveDirectionWorldX = 0.0F;
        liveDirectionWorldY = 0.0F;
        liveDetectedWorkerNpcIndexes = new int[0];
        livePlayerSelected = false;
        liveDetectedWorkerCount = 0;
        lastDetectedWorkers = "none";
        dragging = true;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = "RWS-5 drag started at " + originWorldX + "," + originWorldY + "," + originPlane + ".";
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
        lastEventState = "RWS-5 dragging edgeB="
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

        /*
         * RWS-5 authority handoff:
         * Commit exactly the worker set produced by the proven render-thread
         * detection/preview pass. Do not traverse Matrix3's live NPC
         * collections again from this AWT release handler.
         */
        committedWorkerNpcIndexes = java.util.Arrays.copyOf(
                liveDetectedWorkerNpcIndexes, liveDetectedWorkerNpcIndexes.length);
        committedPlayerSelected = livePlayerSelected;
        liveDetectedWorkerCount = committedWorkerNpcIndexes.length;
        lastDetectedWorkers = formatNpcIndexes(committedWorkerNpcIndexes);
        committed = true;
        dragging = false;
        selectionDragJustCommitted = true;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastRenderState = "committed worker rings pending";
        lastEventState = "RWS-5 selection committed: "
                + committedWorkerNpcIndexes.length + " worker(s) ["
                + lastDetectedWorkers + "] in radius "
                + formatRadius(committedRadiusTiles)
                + " tiles; self=" + (committedPlayerSelected ? "YES" : "NO")
                + "; drag circle hidden, selected-unit rings remain visible.";
        syncCommittedSelectionToServer();
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
        liveDetectedWorkerNpcIndexes = new int[0];
        livePlayerSelected = false;
        liveDetectedWorkerCount = 0;
        lastDetectedWorkers = "none";
        lastRenderedCycle = Integer.MIN_VALUE;
        lastEventState = committed
                ? "RWS-5 drag cancelled; previous committed selection preserved."
                : "RWS-5 drag cancelled.";
    }

    /**
     * RWS-6 command bridge over Matrix3's existing context actions.
     *
     * Action 23 remains Matrix3 Walk Here. Selected workers receive the same
     * destination as a transient server order. If self is selected, vanilla
     * Walk Here is allowed to continue for the local player; otherwise it is
     * consumed so only the selected workers move.
     *
     * Object action 3 is Matrix3's first object option. For the starter tree
     * (1276), the same Chop action is mirrored to selected workers. If self is
     * selected, vanilla Chop continues for the player too.
     */
    static boolean handleMenuAction(int action, int localX, int localY, long targetUid) {
        int normalizedAction = action >= 2000 ? action - 2000 : action;
        if (normalizedAction == CLEAR_SELECTION_MENU_ACTION
                && workerControlEnabled && hasCommittedSelection()) {
            clearCommittedRadius();
            return true;
        }
        if (!workerControlEnabled || !hasCommittedSelection()) {
            return false;
        }

        /*
         * Mouse release after a real radial drag can still produce Matrix3's
         * action-23 Walk Here. That release belongs to selection, not an RTS
         * move order. Consume it once; a later click starts with a fresh latch.
         */
        if (normalizedAction == MATRIX3_TILE_ACTION && selectionDragJustCommitted) {
            selectionDragJustCommitted = false;
            lastEventState = "RWS-5 selection drag release consumed; no Walk Here order issued.";
            return true;
        }

        WorldPoint point = resolveWorldPoint(localX, localY);
        if (point == null) {
            return false;
        }

        if (normalizedAction == MATRIX3_TILE_ACTION) {
            if (committedWorkerNpcIndexes.length > 0) {
                queueSelectionOrder("workerselectionmove "
                        + point.worldX + " " + point.worldY + " " + point.plane);
            }
            return !committedPlayerSelected;
        }

        if (normalizedAction == MATRIX3_FIRST_OBJECT_ACTION) {
            int objectId = (int) (targetUid >>> 32) & 0x7fffffff;
            if (isStarterResourceObjectId(objectId) && committedWorkerNpcIndexes.length > 0) {
                queueSelectionOrder("workerselectiongather object " + objectId + " "
                        + point.worldX + " " + point.worldY + " " + point.plane);
                return !committedPlayerSelected;
            }
        }
        if (normalizedAction == MATRIX3_FIRST_NPC_ACTION && committedWorkerNpcIndexes.length > 0) {
            ResourceNpcTarget npcTarget = resolveResourceNpcTarget(targetUid);
            if (npcTarget != null && npcTarget.npcId == STARTER_FOOD_NPC_ID) {
                queueSelectionOrder("workerselectiongather npc " + npcTarget.npcId + " "
                        + npcTarget.worldX + " " + npcTarget.worldY + " " + npcTarget.plane);
                return !committedPlayerSelected;
            }
        }
        return false;
    }

    private static boolean isStarterResourceObjectId(int objectId) {
        return objectId == STARTER_TREE_OBJECT_ID
                || objectId == STARTER_STONE_OBJECT_ID
                || objectId == STARTER_ORE_OBJECT_ID;
    }

    private static ResourceNpcTarget resolveResourceNpcTarget(long targetUid) {
        if (client.aClass676_8622 == null || client.aClass613_8605 == null) {
            return null;
        }
        int npcIndex = (int) targetUid;
        LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) npcIndex);
        if (link == null || !(link.anObject9081 instanceof NPC)) {
            return null;
        }
        NPC npc = (NPC) link.anObject9081;
        if (npc.aClass410_11803 == null || npc.screenX == null || npc.screenY == null
                || npc.screenX.length == 0 || npc.screenY.length == 0) {
            return null;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return null;
        }
        int npcId = npc.aClass410_11803.anInt4819 * 1355909985;
        return new ResourceNpcTarget(npcId,
                sceneBase.localX * -2109597897 + npc.screenX[0],
                sceneBase.localY * 417324155 + npc.screenY[0],
                npc.aByte9009 & 0xff);
    }

    private static void queueSelectionOrder(String suffix) {
        String error = ClientConsoleBridge.queueConsoleCommand("itembrowser settlement " + suffix);
        if (error != null) {
            lastEventState = "RWS command failed: " + error;
        }
    }

    private static WorldPoint resolveWorldPoint(int localX, int localY) {
        if (client.aClass613_8605 == null || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return null;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        if (sceneBase == null) {
            return null;
        }
        return new WorldPoint(
                sceneBase.localX * -2109597897 + localX,
                sceneBase.localY * 417324155 + localY,
                Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.aByte9009 & 0xff);
    }

    private static final class WorldPoint {
        private final int worldX;
        private final int worldY;
        private final int plane;

        private WorldPoint(int worldX, int worldY, int plane) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }
    }

    private static final class ResourceNpcTarget {
        private final int npcId;
        private final int worldX;
        private final int worldY;
        private final int plane;

        private ResourceNpcTarget(int npcId, int worldX, int worldY, int plane) {
            this.npcId = npcId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }
    }

    private static String formatRadius(float radius) {
        return String.format(java.util.Locale.US, "%.2f", radius);
    }

    private static String formatWorld(float coordinate) {
        return String.format(java.util.Locale.US, "%.2f", coordinate);
    }
}
