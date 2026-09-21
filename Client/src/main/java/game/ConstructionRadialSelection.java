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

    /**
     * Developer-only visual probe for the double-ring GFX 4187 candidate.
     *
     * COLOR_A / COLOR_B recolor only one sampled packed face colour on the
     * per-call model clone. They are diagnostic modes, not final worker colours.
     */
    public enum Reticule4187ProbeMode {
        OFF("Off"),
        ORIGINAL("Original"),
        COLOR_A("Highlight Color A"),
        COLOR_B("Highlight Color B");

        private final String displayName;

        Reticule4187ProbeMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int RETICULE_GFX_ID = 4171;
    private static final int WORKER_STATUS_GFX_ID = 4187;
    private static final int BASE_MODEL_SCALE = 128;
    private static final int PROBE_MAX_REPORTED_COLORS = 8;
    private static final short PROBE_COLOR_A_REPLACEMENT = packHsl(32, 7, 88);
    private static final short PROBE_COLOR_B_REPLACEMENT = packHsl(52, 7, 88);
    private static final float MIN_RADIUS_TILES = 0.0F;
    private static final float MAX_RADIUS_TILES = 64.0F;
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

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile int lastRenderedScalePercent;
    private static volatile String lastEventState = "RWS-2 Worker Control disabled.";
    private static volatile String lastRenderState = "not rendered";

    private static volatile Reticule4187ProbeMode reticule4187ProbeMode = Reticule4187ProbeMode.OFF;
    private static volatile boolean reticule4187ColorsSampled;
    private static volatile int reticule4187ColorA = Integer.MIN_VALUE;
    private static volatile int reticule4187ColorB = Integer.MIN_VALUE;
    private static volatile String reticule4187ColorSummary = "not sampled";
    private static volatile String reticule4187RenderState = "hidden";

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

    public static Reticule4187ProbeMode getReticule4187ProbeMode() {
        return reticule4187ProbeMode;
    }

    public static void setReticule4187ProbeMode(Reticule4187ProbeMode mode) {
        if (mode == null) {
            mode = Reticule4187ProbeMode.OFF;
        }

        if (mode != Reticule4187ProbeMode.OFF) {
            if (dragging) {
                cancelActiveDrag();
            }
            workerControlEnabled = false;
            reticule4187RenderState = "waiting for valid world hover";
        } else {
            reticule4187RenderState = "hidden";
        }

        reticule4187ProbeMode = mode;
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static String getReticule4187ProbeStatus() {
        return "GFX 4187 probe=" + reticule4187ProbeMode
                + " | candidates=" + reticule4187ColorSummary
                + " | render=" + reticule4187RenderState
                + " | clone-color-isolation=0x80000";
    }

    public static void setWorkerControlEnabled(boolean enabled) {
        if (enabled) {
            ensureInputListener();
            reticule4187ProbeMode = Reticule4187ProbeMode.OFF;
            reticule4187RenderState = "hidden";
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
        status.append(" | ").append(lastEventState);
        status.append(" | render=").append(lastRenderState);
        return status.toString();
    }

    /**
     * Mirrors Matrix3's already-resolved scene-tile menu target. This is not a
     * second picker and does not alter the menu entry.
     */
    static void observeSceneMenuTile(int sourceAction, int localX, int localY) {
        if (!workerControlEnabled && reticule4187ProbeMode == Reticule4187ProbeMode.OFF) {
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
        if (reticule4187ProbeMode != Reticule4187ProbeMode.OFF) {
            renderReticule4187Probe(scene, renderer);
            return;
        }

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
        float baseRadiusUnits = Math.max(halfWidthX, halfWidthZ);
        if (baseRadiusUnits <= 0.0F) {
            lastRenderState = "FAIL GFX " + RETICULE_GFX_ID + " zero horizontal bounds";
            return;
        }

        float desiredRadiusUnits = drawRadius * tileSize;
        int runtimeScale = Math.max(1,
                Math.round(BASE_MODEL_SCALE * desiredRadiusUnits / baseRadiusUnits));
        model.method1464(runtimeScale, BASE_MODEL_SCALE, runtimeScale);
        lastRenderedScalePercent = Math.max(1,
                Math.round(runtimeScale * 100.0F / BASE_MODEL_SCALE));

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        lastRenderState = "DRAW gfx=" + RETICULE_GFX_ID
                + " radius=" + formatRadius(drawRadius)
                + " scale=" + lastRenderedScalePercent + "%"
                + " baseRadiusUnits=" + formatRadius(baseRadiusUnits)
                + " modelOffset=" + modelCenterX + "," + modelCenterZ
                + " center=" + formatWorld(drawWorldX) + "," + formatWorld(drawWorldY)
                + "," + drawPlane;
    }

    /**
     * RWS-4 preflight probe. GFX 4187 is rendered independently of combat and
     * only mutates the per-call clone returned by GraphicsDefinition.
     *
     * verified-static:
     * - MODEL_FLAGS includes 0x80000.
     * - AbstractModel.method10013(...) deep-copies aShortArray10793 when
     *   Class368.method4501(...) accepts that flag.
     * - Model.method1393(...) replaces only faces whose packed colour exactly
     *   matches the requested source colour.
     *
     * Runtime must still prove whether sampled colour A/B map cleanly to the
     * outer and inner rings of GFX 4187.
     */
    private static void renderReticule4187Probe(Class523 scene, Class106 renderer) {
        if (scene == null || renderer == null || client.aClass613_8605 == null) {
            reticule4187RenderState = "WAIT renderer/scene";
            return;
        }

        long age = System.currentTimeMillis() - hoveredAtMillis;
        if (hoveredWorldX < 0 || hoveredWorldY < 0 || hoveredPlane < 0
                || hoveredAtMillis == 0L || age > HOVER_STALE_MS) {
            reticule4187RenderState = "WAIT move mouse over valid ground";
            return;
        }

        Class613 region = client.aClass613_8605;
        if (region.method7285(0) != scene) {
            reticule4187RenderState = "WAIT active scene";
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class497 sceneBase = region.method7280((byte) -102);
        if (sceneBase == null) {
            reticule4187RenderState = "WAIT scene base";
            return;
        }

        int sceneBaseWorldX = sceneBase.localX * -2109597897;
        int sceneBaseWorldY = sceneBase.localY * 417324155;
        int localX = hoveredWorldX - sceneBaseWorldX;
        int localY = hoveredWorldY - sceneBaseWorldY;
        int plane = hoveredPlane;

        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            reticule4187RenderState = "SKIP invalid plane " + plane;
            return;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            reticule4187RenderState = "WAIT terrain";
            return;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX >= sceneWidth || localY >= sceneHeight) {
            reticule4187RenderState = "SKIP hover outside scene";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + tileSize / 2;
        int sceneZ = localY * tileSize + tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);

        GraphicsDefinition definition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(WORKER_STATUS_GFX_ID, 235749166);
        if (definition == null) {
            reticule4187RenderState = "FAIL GFX " + WORKER_STATUS_GFX_ID + " definition";
            return;
        }

        Model model = definition.method7764(renderer, MODEL_FLAGS, 0, 0, 0, 0, null, (byte) 2, 1913622280);
        if (model == null) {
            reticule4187RenderState = "FAIL GFX " + WORKER_STATUS_GFX_ID + " model";
            return;
        }

        sampleReticule4187Colors(model);

        Reticule4187ProbeMode mode = reticule4187ProbeMode;
        if (mode == Reticule4187ProbeMode.COLOR_A) {
            if (reticule4187ColorA == Integer.MIN_VALUE) {
                reticule4187RenderState = "FAIL no Color A candidate";
                return;
            }
            model.method1393((short) reticule4187ColorA, PROBE_COLOR_A_REPLACEMENT);
        } else if (mode == Reticule4187ProbeMode.COLOR_B) {
            if (reticule4187ColorB == Integer.MIN_VALUE) {
                reticule4187RenderState = "FAIL no Color B candidate";
                return;
            }
            model.method1393((short) reticule4187ColorB, PROBE_COLOR_B_REPLACEMENT);
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        String recolor = "none";
        if (mode == Reticule4187ProbeMode.COLOR_A) {
            recolor = formatPackedColor(reticule4187ColorA) + "->"
                    + formatPackedColor(PROBE_COLOR_A_REPLACEMENT & 0xffff);
        } else if (mode == Reticule4187ProbeMode.COLOR_B) {
            recolor = formatPackedColor(reticule4187ColorB) + "->"
                    + formatPackedColor(PROBE_COLOR_B_REPLACEMENT & 0xffff);
        }

        reticule4187RenderState = "DRAW gfx=" + WORKER_STATUS_GFX_ID
                + " mode=" + mode
                + " recolor=" + recolor
                + " world=" + hoveredWorldX + "," + hoveredWorldY + "," + plane;
    }

    private static synchronized void sampleReticule4187Colors(Model model) {
        if (reticule4187ColorsSampled) {
            return;
        }

        reticule4187ColorsSampled = true;
        if (!(model instanceof AbstractModel)) {
            reticule4187ColorSummary = "face colours unavailable on " + model.getClass().getSimpleName();
            return;
        }

        AbstractModel abstractModel = (AbstractModel) model;
        short[] faceColors = abstractModel.aShortArray10793;
        if (faceColors == null || abstractModel.anInt10833 <= 0) {
            reticule4187ColorSummary = "no face-colour array";
            return;
        }

        int faceCount = Math.min(abstractModel.anInt10833, faceColors.length);
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<Integer, Integer>();
        for (int index = 0; index < faceCount; index++) {
            int packed = faceColors[index] & 0xffff;
            if (packed == 0xffff) {
                continue;
            }
            Integer count = counts.get(Integer.valueOf(packed));
            counts.put(Integer.valueOf(packed), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }

        java.util.List<java.util.Map.Entry<Integer, Integer>> entries =
                new java.util.ArrayList<java.util.Map.Entry<Integer, Integer>>(counts.entrySet());
        java.util.Collections.sort(entries, new java.util.Comparator<java.util.Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(java.util.Map.Entry<Integer, Integer> left,
                    java.util.Map.Entry<Integer, Integer> right) {
                int byCount = right.getValue().intValue() - left.getValue().intValue();
                if (byCount != 0) {
                    return byCount;
                }
                return left.getKey().intValue() - right.getKey().intValue();
            }
        });

        if (!entries.isEmpty()) {
            reticule4187ColorA = entries.get(0).getKey().intValue();
        }
        if (entries.size() > 1) {
            reticule4187ColorB = entries.get(1).getKey().intValue();
        }

        StringBuilder summary = new StringBuilder(160);
        int reportCount = Math.min(PROBE_MAX_REPORTED_COLORS, entries.size());
        for (int index = 0; index < reportCount; index++) {
            if (index > 0) {
                summary.append(", ");
            }
            java.util.Map.Entry<Integer, Integer> entry = entries.get(index);
            if (index == 0) {
                summary.append("A=");
            } else if (index == 1) {
                summary.append("B=");
            } else {
                summary.append("#").append(index + 1).append("=");
            }
            summary.append(formatPackedColor(entry.getKey().intValue()))
                    .append("(").append(entry.getValue().intValue()).append(" faces)");
        }
        if (entries.isEmpty()) {
            summary.append("no non-sentinel packed colours");
        }
        reticule4187ColorSummary = summary.toString();
    }

    private static short packHsl(int hue, int saturation, int lightness) {
        hue = Math.max(0, Math.min(63, hue));
        saturation = Math.max(0, Math.min(7, saturation));
        lightness = Math.max(0, Math.min(127, lightness));
        return (short) (hue << 10 | saturation << 7 | lightness);
    }

    private static String formatPackedColor(int packed) {
        return String.format(java.util.Locale.US, "0x%04X", packed & 0xffff);
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
