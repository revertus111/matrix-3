package game;

import java.util.Arrays;

/**
 * Matrix-native presentation adapter for libsm64's already-animated Mario mesh.
 *
 * libsm64 remains the animation/pose owner. This class consumes immutable native
 * geometry frames on Matrix's render thread, converts them to Class159 raw
 * geometry, builds a normal renderer Model, and draws it through the same direct
 * scene-render seam used by other Matrix developer previews.
 *
 * V1 keeps Matrix texture ownership intact: the ROM-derived libsm64 RGBA atlas is
 * sampled into per-triangle Matrix face albedo. Exact runtime UV texture injection
 * is deliberately deferred until a renderer-backend-neutral texture seam is proven.
 */
public final class MarioVisualRenderer {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final long MAX_FRAME_AGE_NANOS = 500000000L;
    private static final float DEFAULT_MODEL_SCALE = 2.0F;
    private static final float MODEL_SCALE = resolveModelScale();

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static long cachedSequence = -1L;
    private static Model cachedModel;
    private static volatile boolean replacementReady;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static long lastLoggedSequence = -1L;
    private static long lastFailedSequence = -1L;

    private MarioVisualRenderer() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!PlayerControllerMode.isMarioMode() || scene == null || renderer == null
                || !Sm64BridgeSession.isReady()) {
            replacementReady = false;
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        if (player == null || !isUsable(frame)) {
            replacementReady = false;
            return;
        }

        if (cachedRenderer != renderer || cachedSequence != frame.sequence || cachedModel == null) {
            Model rebuilt = buildModel(renderer, frame, Sm64BridgeSession.getTextureAtlas());
            if (rebuilt == null) {
                replacementReady = false;
                if (lastFailedSequence != frame.sequence) {
                    lastFailedSequence = frame.sequence;
                    System.err.println("[SM64 Visual] Matrix model build failed for native frame "
                            + frame.sequence + " triangles=" + frame.triangleCount);
                }
                return;
            }
            cachedRenderer = renderer;
            cachedSequence = frame.sequence;
            cachedModel = rebuilt;
        }

        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null) {
            replacementReady = false;
            return;
        }

        Class240 position = playerTransform.aClass240_2647;
        TRANSFORM.method3588(
                Math.round(position.aFloat2653),
                Math.round(position.aFloat2656),
                Math.round(position.aFloat2657));
        try {
            cachedModel.method1375(TRANSFORM, RENDER_BOUNDS, 0);
            replacementReady = true;
            if (lastLoggedSequence < 0L) {
                lastLoggedSequence = frame.sequence;
                System.out.println("[SM64 Visual] Native Mario -> Matrix Model ACTIVE"
                        + " triangles=" + frame.triangleCount
                        + " anim=" + frame.state.animId
                        + " frame=" + frame.state.animFrame
                        + " scale=" + MODEL_SCALE
                        + " texture=ROM-albedo-v1");
            }
        } catch (RuntimeException ex) {
            replacementReady = false;
            if (lastFailedSequence != frame.sequence) {
                lastFailedSequence = frame.sequence;
                System.err.println("[SM64 Visual] Render failed: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Local-player suppression is presentation-only and fail-open. The normal
     * RuneScape player disappears only after a fresh native frame has actually
     * built and rendered successfully through Matrix's renderer.
     */
    static boolean shouldSuppressLocalPlayer(Player player) {
        if (player == null || player != Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976
                || !PlayerControllerMode.isMarioMode()
                || !Sm64BridgeSession.isReady()
                || !replacementReady) {
            return false;
        }
        return isUsable(Sm64BridgeSession.getLatestGeometryFrame());
    }

    private static boolean isUsable(Sm64BridgeSession.GeometryFrame frame) {
        if (frame == null || frame.state == null || frame.triangleCount <= 0
                || frame.positions == null || frame.colors == null || frame.uvs == null
                || frame.positions.length < frame.triangleCount * 9
                || frame.colors.length < frame.triangleCount * 9
                || frame.uvs.length < frame.triangleCount * 6) {
            return false;
        }
        long age = System.nanoTime() - frame.state.receivedNanos;
        return age >= 0L && age <= MAX_FRAME_AGE_NANOS;
    }

    private static Model buildModel(Class106 renderer,
            Sm64BridgeSession.GeometryFrame frame,
            Sm64BridgeSession.TextureAtlas atlas) {
        int triangles = frame.triangleCount;
        int vertices = triangles * 3;
        if (triangles <= 0 || vertices > 65535) {
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

        float stateX = frame.state.x;
        float stateY = frame.state.y;
        float stateZ = frame.state.z;

        for (int triangle = 0; triangle < triangles; triangle++) {
            int vertexBase = triangle * 3;
            int positionBase = triangle * 9;
            int colorBase = triangle * 9;
            int uvBase = triangle * 6;

            int sumR = 0;
            int sumG = 0;
            int sumB = 0;

            for (int vertex = 0; vertex < 3; vertex++) {
                int rawVertex = vertexBase + vertex;
                int p = positionBase + vertex * 3;
                int c = colorBase + vertex * 3;
                int uv = uvBase + vertex * 2;

                raw.anIntArray1782[rawVertex] = Math.round(
                        (frame.positions[p] - stateX) * MODEL_SCALE);
                // SM64 altitude is +Y; Matrix model-space altitude is -Y.
                raw.anIntArray1777[rawVertex] = Math.round(
                        -(frame.positions[p + 1] - stateY) * MODEL_SCALE);
                raw.anIntArray1797[rawVertex] = Math.round(
                        (frame.positions[p + 2] - stateZ) * MODEL_SCALE);

                int baseR = unitColor(frame.colors[c]);
                int baseG = unitColor(frame.colors[c + 1]);
                int baseB = unitColor(frame.colors[c + 2]);
                int rgb = sampleMarioColor(
                        atlas, frame.uvs[uv], frame.uvs[uv + 1],
                        baseR, baseG, baseB);
                sumR += (rgb >>> 16) & 0xff;
                sumG += (rgb >>> 8) & 0xff;
                sumB += rgb & 0xff;
            }

            /*
             * Negating model Y mirrors one axis, so B/C are swapped to preserve
             * the native triangle winding for Matrix back-face culling.
             */
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
            System.err.println("[SM64 Visual] Raw model conversion failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Mirrors libsm64's test shader: texture RGB replaces vertex color according
     * to the atlas texel alpha. V1 bakes the result into Matrix face albedo.
     */
    private static int sampleMarioColor(Sm64BridgeSession.TextureAtlas atlas,
            float u, float v, int baseR, int baseG, int baseB) {
        if (atlas == null || atlas.rgba == null || atlas.width <= 0 || atlas.height <= 0
                || Float.isNaN(u) || Float.isNaN(v) || u < 0.0F) {
            return baseR << 16 | baseG << 8 | baseB;
        }

        float wrappedU = u - (float) Math.floor(u);
        float wrappedV = v - (float) Math.floor(v);
        int x = clamp((int) (wrappedU * atlas.width), 0, atlas.width - 1);
        int y = clamp((int) (wrappedV * atlas.height), 0, atlas.height - 1);
        int index = (y * atlas.width + x) * 4;
        if (index < 0 || index + 3 >= atlas.rgba.length) {
            return baseR << 16 | baseG << 8 | baseB;
        }

        int texR = atlas.rgba[index] & 0xff;
        int texG = atlas.rgba[index + 1] & 0xff;
        int texB = atlas.rgba[index + 2] & 0xff;
        int alpha = atlas.rgba[index + 3] & 0xff;
        int inv = 255 - alpha;
        int r = (baseR * inv + texR * alpha + 127) / 255;
        int g = (baseG * inv + texG * alpha + 127) / 255;
        int b = (baseB * inv + texB * alpha + 127) / 255;
        return r << 16 | g << 8 | b;
    }

    private static int unitColor(float value) {
        if (Float.isNaN(value)) {
            return 0;
        }
        return clamp(Math.round(value * 255.0F), 0, 255);
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

    private static float resolveModelScale() {
        String configured = System.getProperty("matrix3.sm64.modelScale");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_MODEL_SCALE;
        }
        try {
            float parsed = Float.parseFloat(configured.trim());
            if (parsed > 0.0F && !Float.isNaN(parsed) && !Float.isInfinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }
        System.out.println("[SM64 Visual] Invalid matrix3.sm64.modelScale='" + configured
                + "'; using " + DEFAULT_MODEL_SCALE);
        return DEFAULT_MODEL_SCALE;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
