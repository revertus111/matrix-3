package game;

import java.util.Arrays;

/**
 * Matrix-native presentation adapter for liboot's animated adult-Link geometry.
 *
 * Phase 1 V1 intentionally uses liboot vertex colour only. Exact OoT texture
 * material translation is deferred until the basic native->Matrix geometry seam
 * is runtime-proven against the user's NTSC-U 1.2 ROM.
 */
public final class LinkVisualRenderer {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS =
            BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int MAX_MATRIX_VERTICES = 65535;
    private static final long MAX_FRAME_AGE_NANOS = 500000000L;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static long cachedSequence = -1L;
    private static long cachedFitRevision = -1L;
    private static Model cachedModel;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static long lastLoggedSequence = -1L;
    private static long lastFailedSequence = -1L;

    private LinkVisualRenderer() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!PlayerControllerMode.isLinkMode()) {
            resetPresentationCache();
            return;
        }
        if (scene == null || renderer == null || !OotBridgeSession.isReady()) {
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        OotBridgeSession.LinkFrame frame = OotBridgeSession.getLatestFrame();
        if (player == null || !isUsable(frame)) {
            return;
        }

        LinkCharacterFit.Profile fit = LinkCharacterFit.resolve(player, frame);
        if (fit == null) {
            return;
        }

        if (cachedRenderer != renderer
                || cachedSequence != frame.sequence
                || cachedFitRevision != fit.revision
                || cachedModel == null) {
            Model rebuilt = buildModel(renderer, frame, fit);
            if (rebuilt == null) {
                if (lastFailedSequence != frame.sequence) {
                    lastFailedSequence = frame.sequence;
                    System.err.println("[OoT Visual] Matrix model build failed for native frame "
                            + frame.sequence + " triangles=" + frame.triangleCount);
                }
                return;
            }
            cachedRenderer = renderer;
            cachedSequence = frame.sequence;
            cachedFitRevision = fit.revision;
            cachedModel = rebuilt;
        }

        try {
            Class238 playerTransform = player.method5394();
            if (playerTransform == null || playerTransform.aClass240_2647 == null) {
                return;
            }
            Class240 position = playerTransform.aClass240_2647;
            TRANSFORM.method3588(
                    Math.round(position.aFloat2653),
                    Math.round(position.aFloat2656),
                    Math.round(position.aFloat2657));
            cachedModel.method1375(TRANSFORM, RENDER_BOUNDS, 0);
            if (lastLoggedSequence < 0L) {
                lastLoggedSequence = frame.sequence;
                System.out.println("[OoT Visual] Native ADULT Link -> Matrix Model ACTIVE"
                        + " triangles=" + frame.triangleCount
                        + " anim=" + frame.animId
                        + " action=" + frame.action
                        + " scale=" + fit.scale
                        + " fit=" + (fit.forcedScale ? "forced" : fit.autoFit ? "830-auto" : "fallback")
                        + " colour=liboot-vertex-v1");
            }
        } catch (RuntimeException ex) {
            if (lastFailedSequence != frame.sequence) {
                lastFailedSequence = frame.sequence;
                System.err.println("[OoT Visual] Render failed: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    private static void resetPresentationCache() {
        cachedRenderer = null;
        cachedSequence = -1L;
        cachedFitRevision = -1L;
        cachedModel = null;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastLoggedSequence = -1L;
        lastFailedSequence = -1L;
        LinkCharacterFit.resetSession();
    }

    private static boolean isUsable(OotBridgeSession.LinkFrame frame) {
        if (frame == null || !frame.skeletonAvailable || frame.triangleCount <= 0
                || frame.positions == null || frame.colors == null
                || frame.positions.length < frame.triangleCount * 9
                || frame.colors.length < frame.triangleCount * 9) {
            return false;
        }
        long age = System.nanoTime() - frame.receivedNanos;
        return age >= 0L && age <= MAX_FRAME_AGE_NANOS;
    }

    private static Model buildModel(Class106 renderer, OotBridgeSession.LinkFrame frame,
            LinkCharacterFit.Profile fit) {
        int triangles = frame.triangleCount;
        int vertices = triangles * 3;
        if (triangles <= 0 || vertices > MAX_MATRIX_VERTICES) {
            return null;
        }

        Class159 raw = new Class159(vertices, triangles, 0);
        raw.anInt1791 = vertices;
        raw.anInt1775 = vertices;
        raw.anInt1778 = triangles;

        Arrays.fill(raw.anIntArray1813, -1);
        Arrays.fill(raw.anIntArray1780, -1);
        Arrays.fill(raw.faceTextures, (short) -1);
        Arrays.fill(raw.faceTextureIndexes, (short) -1);

        for (int triangle = 0; triangle < triangles; triangle++) {
            int vertexBase = triangle * 3;
            int positionBase = triangle * 9;
            int colorBase = triangle * 9;
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;

            for (int vertex = 0; vertex < 3; vertex++) {
                int rawVertex = vertexBase + vertex;
                int p = positionBase + vertex * 3;
                int c = colorBase + vertex * 3;

                /*
                 * Matrix owns host X/Z while liboot owns Link pose/root-height.
                 * Uniform auto-fit preserves adult Link's OoT proportions. The
                 * calibrated native standing floor maps to Matrix local Y=0,
                 * while native root-height deltas remain visible for jumps/actions.
                 */
                raw.anIntArray1782[rawVertex] = Math.round(
                        (frame.positions[p] - frame.x) * fit.scale);
                raw.anIntArray1777[rawVertex] = Math.round(
                        fit.toMatrixY(frame.positions[p + 1], frame.y));
                raw.anIntArray1797[rawVertex] = Math.round(
                        (frame.positions[p + 2] - frame.z) * fit.scale);

                sumR += unitColor(frame.colors[c]);
                sumG += unitColor(frame.colors[c + 1]);
                sumB += unitColor(frame.colors[c + 2]);
            }

            // Negating model Y mirrors one axis; swap B/C to preserve winding.
            raw.aShortArray1786[triangle] = (short) vertexBase;
            raw.aShortArray1787[triangle] = (short) (vertexBase + 2);
            raw.aShortArray1789[triangle] = (short) (vertexBase + 1);
            raw.faceColours[triangle] = rgbToRsHsl(
                    sumR / 3, sumG / 3, sumB / 3);
        }

        try {
            Model model = renderer.method1755(raw, RAW_BUILD_FLAGS, 0, 64, 850);
            if (model != null) {
                model.method1450(FINAL_MODEL_FLAGS);
            }
            return model;
        } catch (RuntimeException ex) {
            System.err.println("[OoT Visual] Raw model conversion failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    private static int unitColor(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }
        int color = Math.round(value * 255.0F);
        return clamp(color, 0, 255);
    }

    /** Standard RuneScape packed-HSL face-colour conversion. */
    private static short rgbToRsHsl(int r, int g, int b) {
        double rd = clamp(r, 0, 255) / 256.0;
        double gd = clamp(g, 0, 255) / 256.0;
        double bd = clamp(b, 0, 255) / 256.0;
        double min = Math.min(rd, Math.min(gd, bd));
        double max = Math.max(rd, Math.max(gd, bd));
        double light = (min + max) * 0.5;
        double hue = 0.0;
        double sat = 0.0;

        if (max != min) {
            double delta = max - min;
            sat = light < 0.5
                    ? delta / (max + min)
                    : delta / (2.0 - max - min);
            if (rd == max) {
                hue = (gd - bd) / delta;
            } else if (gd == max) {
                hue = 2.0 + (bd - rd) / delta;
            } else {
                hue = 4.0 + (rd - gd) / delta;
            }
            hue /= 6.0;
            if (hue < 0.0) {
                hue += 1.0;
            }
        }

        int h = clamp((int) (hue * 256.0), 0, 255);
        int s = clamp((int) (sat * 256.0), 0, 255);
        int l = clamp((int) (light * 256.0), 0, 255);
        if (l > 243) s >>= 4;
        else if (l > 217) s >>= 3;
        else if (l > 192) s >>= 2;
        else if (l > 179) s >>= 1;
        return (short) (((h >> 2) << 10) | ((s >> 5) << 7) | (l >> 1));
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
