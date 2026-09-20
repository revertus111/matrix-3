package game;

/**
 * Client-only radial worker-selection visual proof.
 *
 * RWS-1 intentionally owns no worker selection, combat state, cache mutation,
 * scene registration, collision, persistence, or server authority. It reuses a
 * stock target-reticule GFX model, applies a per-instance X/Z scale to the cloned
 * model, and direct-renders it on the hovered world tile through the already
 * proven Construction scene-pass hook.
 */
public final class ConstructionRadialSelection {

    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int RETICULE_GFX_ID = 4171;
    private static final int BASE_MODEL_SCALE = 128;
    private static final int MIN_SCALE_PERCENT = 25;
    private static final int MAX_SCALE_PERCENT = 1200;
    private static final int MODEL_FLAGS = 2048 | 0x80000 | 0x5;
    private static final long HOVER_STALE_MS = 1250L;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean proofEnabled;
    private static volatile int scalePercent = 100;
    private static volatile int hoveredWorldX = -1;
    private static volatile int hoveredWorldY = -1;
    private static volatile int hoveredPlane = -1;
    private static volatile long hoveredAtMillis;
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "RWS-1 reticule proof disabled.";

    private ConstructionRadialSelection() {
    }

    public static boolean isProofEnabled() {
        return proofEnabled;
    }

    public static void setProofEnabled(boolean enabled) {
        proofEnabled = enabled;
        lastRenderedCycle = Integer.MIN_VALUE;
        if (!enabled) {
            clearHoveredTile();
            status = "RWS-1 reticule proof disabled.";
        } else {
            status = "RWS-1 reticule proof enabled. Move the mouse over world ground.";
        }
    }

    public static int getScalePercent() {
        return scalePercent;
    }

    public static void setScalePercent(int percent) {
        if (percent < MIN_SCALE_PERCENT) {
            percent = MIN_SCALE_PERCENT;
        } else if (percent > MAX_SCALE_PERCENT) {
            percent = MAX_SCALE_PERCENT;
        }
        scalePercent = percent;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "RWS-1 scale set to " + scalePercent + "%. Move the mouse over world ground.";
    }

    public static String getStatus() {
        return status;
    }

    /**
     * Mirrors Matrix3's already-resolved scene-tile menu target. This is not a
     * second picker and does not alter the menu entry.
     */
    static void observeSceneMenuTile(int sourceAction, int localX, int localY) {
        if (!proofEnabled) {
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
    }

    /**
     * verified-static:
     * - GraphicsDefinition.method7764(...) returns a per-call model clone.
     * - Model.method1464(...) is Matrix3's model scale transform.
     * - Applying the second scale to that clone does not mutate the cache
     *   definition or the cached base model.
     * - Model.method1375(...) is the live Matrix3 draw path already proven by
     *   ConstructionGhostPreview.
     */
    static void render(Class523 scene, Class106 renderer) {
        if (!proofEnabled || scene == null || renderer == null || client.aClass613_8605 == null) {
            return;
        }

        long age = System.currentTimeMillis() - hoveredAtMillis;
        if (hoveredWorldX < 0 || hoveredWorldY < 0 || hoveredPlane < 0
                || hoveredAtMillis == 0L || age > HOVER_STALE_MS) {
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
            status = "RWS-1 WAIT: scene base unavailable.";
            return;
        }

        int localX = hoveredWorldX - sceneBase.localX * -2109597897;
        int localY = hoveredWorldY - sceneBase.localY * 417324155;
        int plane = hoveredPlane;
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            status = "RWS-1 SKIP: invalid plane " + plane + ".";
            return;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            status = "RWS-1 WAIT: terrain unavailable.";
            return;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX >= sceneWidth || localY >= sceneHeight) {
            status = "RWS-1 SKIP: hovered tile outside active scene.";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + tileSize / 2;
        int sceneZ = localY * tileSize + tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);

        GraphicsDefinition definition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(RETICULE_GFX_ID, 235749166);
        if (definition == null) {
            status = "RWS-1 FAIL: target reticule GFX " + RETICULE_GFX_ID + " definition unavailable.";
            return;
        }

        Model model = definition.method7764(renderer, MODEL_FLAGS, 0, 0, 0, 0, null, (byte) 2, 1913622280);
        if (model == null) {
            status = "RWS-1 FAIL: target reticule GFX " + RETICULE_GFX_ID + " model unavailable.";
            return;
        }

        int runtimeScale = Math.max(1, Math.round(BASE_MODEL_SCALE * (scalePercent / 100.0F)));
        model.method1464(runtimeScale, BASE_MODEL_SCALE, runtimeScale);

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        status = "RWS-1 DRAW_SUBMITTED gfx=" + RETICULE_GFX_ID
                + " scale=" + scalePercent + "%"
                + " world=" + hoveredWorldX + "," + hoveredWorldY + "," + plane;
    }

    private static void clearHoveredTile() {
        hoveredWorldX = -1;
        hoveredWorldY = -1;
        hoveredPlane = -1;
        hoveredAtMillis = 0L;
        lastRenderedCycle = Integer.MIN_VALUE;
    }
}
