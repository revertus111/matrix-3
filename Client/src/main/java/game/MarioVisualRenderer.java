package game;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Matrix-native presentation adapter for libsm64's already-animated Mario mesh.
 *
 * libsm64 remains the animation/pose owner. This class consumes immutable native
 * geometry frames on Matrix's render thread, converts them to Class159 raw
 * geometry, builds a normal renderer Model, and draws it through the same direct
 * scene-render seam used by other Matrix developer previews.
 *
 * Matrix cannot yet bind libsm64's ROM atlas as a renderer-native runtime texture.
 * Instead, textured SM64 triangles are split into small Matrix faces and the atlas
 * is sampled per micro-face. Boundary vertices are shared across compatible source
 * faces so Matrix can produce smooth vertex-normal shading without welding genuine
 * hard edges together.
 */
public final class MarioVisualRenderer {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int MAX_MATRIX_VERTICES = 65535;
    private static final int MAX_MATRIX_TRIANGLES = MAX_MATRIX_VERTICES / 3;
    private static final int DEFAULT_TEXTURE_SUBDIVISIONS = 4;
    private static final int MAX_TEXTURE_SUBDIVISIONS = 4;
    private static final float DEFAULT_SMOOTH_ANGLE_DEGREES = 70.0F;
    private static final long MAX_FRAME_AGE_NANOS = 500000000L;
    private static final long REPLACEMENT_GRACE_NANOS = 750000000L;
    private static final float DEFAULT_MODEL_SCALE = 2.0F;
    private static final float MODEL_SCALE = resolveModelScale();
    private static final int TEXTURE_SUBDIVISIONS = resolveTextureSubdivisions();
    private static final float SMOOTH_ANGLE_DEGREES = resolveSmoothAngleDegrees();
    private static final float SMOOTH_DOT_THRESHOLD =
            (float) Math.cos(Math.toRadians(SMOOTH_ANGLE_DEGREES));
    private static final boolean DEBUG_ATLAS_FACE_BAKE = resolveAtlasFaceBake();

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static long cachedSequence = -1L;
    private static long cachedMaskRevision = Long.MIN_VALUE;
    private static Model cachedModel;
    private static volatile boolean replacementReady;
    private static volatile long lastFreshRenderSuccessNanos = Long.MIN_VALUE;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static long lastLoggedSequence = -1L;
    private static long lastFailedSequence = -1L;
    private static int lastBuiltOutputTriangles;
    private static int lastBuiltOutputVertices;
    private static int lastBuiltTextureSubdivisions;

    private MarioVisualRenderer() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!PlayerControllerMode.isMarioMode() || scene == null || renderer == null
                || !Sm64BridgeSession.isReady()) {
            clearReplacementReadiness();
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null) {
            clearReplacementReadiness();
            return;
        }

        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        if (!isUsable(frame)) {
            if (!renderCachedGrace(player, renderer)) {
                replacementReady = false;
            }
            return;
        }

        long maskRevision = MarioEquipmentWorkbench.getMaskRevision();
        if (cachedRenderer != renderer
                || cachedSequence != frame.sequence
                || cachedMaskRevision != maskRevision
                || cachedModel == null) {
            Model rebuilt = buildModel(renderer, frame, Sm64BridgeSession.getTextureAtlas());
            if (rebuilt == null) {
                if (!renderCachedGrace(player, renderer)) {
                    replacementReady = false;
                }
                if (lastFailedSequence != frame.sequence) {
                    lastFailedSequence = frame.sequence;
                    System.err.println("[SM64 Visual] Matrix model build failed for native frame "
                            + frame.sequence + " triangles=" + frame.triangleCount);
                }
                return;
            }
            cachedRenderer = renderer;
            cachedSequence = frame.sequence;
            cachedMaskRevision = maskRevision;
            cachedModel = rebuilt;
        }

        try {
            if (!renderModelAtPlayer(player, cachedModel)) {
                replacementReady = false;
                return;
            }
            replacementReady = true;
            lastFreshRenderSuccessNanos = System.nanoTime();
            if (lastLoggedSequence < 0L) {
                lastLoggedSequence = frame.sequence;
                String colourMode = lastBuiltTextureSubdivisions <= 0
                        ? "libsm64-base-v2"
                        : lastBuiltTextureSubdivisions == 1
                                ? "atlas-face-bake-debug"
                                : "atlas-micro-v3";
                System.out.println("[SM64 Visual] Native Mario -> Matrix Model ACTIVE"
                        + " triangles=" + frame.triangleCount
                        + " matrixTriangles=" + lastBuiltOutputTriangles
                        + " matrixVertices=" + lastBuiltOutputVertices
                        + " anim=" + frame.state.animId
                        + " frame=" + frame.state.animFrame
                        + " scale=" + MODEL_SCALE
                        + " colour=" + colourMode
                        + " textureSubdivisions=" + lastBuiltTextureSubdivisions
                        + " smoothAngle=" + SMOOTH_ANGLE_DEGREES);
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
     * Local-player suppression is presentation-only and fail-open. A brief native
     * geometry/build gap may keep the last successfully rendered Mario model for a
     * bounded grace period; real mode/bridge loss still restores RuneScape at once.
     */
    static boolean shouldSuppressLocalPlayer(Player player) {
        if (player == null || player != Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976
                || !PlayerControllerMode.isMarioMode()
                || !Sm64BridgeSession.isReady()
                || !replacementReady) {
            return false;
        }
        return isUsable(Sm64BridgeSession.getLatestGeometryFrame())
                || isWithinReplacementGrace();
    }

    private static void clearReplacementReadiness() {
        replacementReady = false;
        lastFreshRenderSuccessNanos = Long.MIN_VALUE;
    }

    /**
     * Keeps presentation continuous through one/few missing native frames without
     * turning a stale model into permanent authority. The grace deadline is based
     * only on the last genuinely fresh successful render and is never extended by
     * cached fallback renders.
     */
    private static boolean renderCachedGrace(Player player, Class106 renderer) {
        if (cachedModel == null || cachedRenderer != renderer || !isWithinReplacementGrace()) {
            return false;
        }
        try {
            if (!renderModelAtPlayer(player, cachedModel)) {
                return false;
            }
            replacementReady = true;
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean renderModelAtPlayer(Player player, Model model) {
        if (player == null || model == null) {
            return false;
        }
        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null) {
            return false;
        }
        Class240 position = playerTransform.aClass240_2647;
        TRANSFORM.method3588(
                Math.round(position.aFloat2653),
                Math.round(position.aFloat2656),
                Math.round(position.aFloat2657));
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        return true;
    }

    private static boolean isWithinReplacementGrace() {
        long last = lastFreshRenderSuccessNanos;
        if (last == Long.MIN_VALUE) {
            return false;
        }
        long age = System.nanoTime() - last;
        return age >= 0L && age <= REPLACEMENT_GRACE_NANOS;
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
        int sourceTriangles = frame.triangleCount;
        if (sourceTriangles <= 0) {
            return null;
        }

        MarioEquipmentWorkbench.prepareMaskFrame(frame);
        int textureSubdivisions = chooseTextureSubdivisions(frame, atlas);
        int outputTriangles = countOutputTriangles(frame, atlas, textureSubdivisions);
        int vertexCapacity = outputTriangles * 3;
        if (outputTriangles <= 0 || outputTriangles > MAX_MATRIX_TRIANGLES
                || vertexCapacity > MAX_MATRIX_VERTICES) {
            return null;
        }

        Class159 raw = new Class159(vertexCapacity, outputTriangles, 0);
        raw.anInt1791 = 0;
        raw.anInt1775 = 0;
        raw.anInt1778 = outputTriangles;

        Arrays.fill(raw.anIntArray1813, -1);
        Arrays.fill(raw.anIntArray1780, -1);
        Arrays.fill(raw.faceTextures, (short) -1);
        Arrays.fill(raw.faceTextureIndexes, (short) -1);

        float stateX = frame.state.x;
        float stateY = frame.state.y;
        float stateZ = frame.state.z;
        SmoothVertexPool vertexPool =
                new SmoothVertexPool(raw, vertexCapacity, SMOOTH_DOT_THRESHOLD);
        float[] sourceNormal = new float[3];
        int outputTriangle = 0;
        int maskedSourceTriangles = 0;

        for (int sourceTriangle = 0; sourceTriangle < sourceTriangles; sourceTriangle++) {
            if (MarioEquipmentWorkbench.shouldMaskTriangle(frame, sourceTriangle)) {
                maskedSourceTriangles++;
                continue;
            }

            int positionBase = sourceTriangle * 9;
            calculateSourceNormal(frame, positionBase, sourceNormal);

            boolean textured = textureSubdivisions > 0
                    && isTexturedTriangle(frame, atlas, sourceTriangle);
            if (!textured) {
                outputTriangle = emitBakedTriangle(
                        raw, vertexPool, outputTriangle, frame, atlas, sourceTriangle,
                        0.0F, 0.0F,
                        1.0F, 0.0F,
                        0.0F, 1.0F,
                        false, sourceNormal, stateX, stateY, stateZ);
                continue;
            }

            float step = 1.0F / textureSubdivisions;
            for (int b = 0; b < textureSubdivisions; b++) {
                for (int c = 0; c < textureSubdivisions - b; c++) {
                    float b0 = b * step;
                    float c0 = c * step;
                    float b1 = (b + 1) * step;
                    float c1 = c * step;
                    float b2 = b * step;
                    float c2 = (c + 1) * step;
                    outputTriangle = emitBakedTriangle(
                            raw, vertexPool, outputTriangle, frame, atlas, sourceTriangle,
                            b0, c0, b1, c1, b2, c2,
                            true, sourceNormal, stateX, stateY, stateZ);

                    if (b + c + 1 < textureSubdivisions) {
                        float b3 = (b + 1) * step;
                        float c3 = (c + 1) * step;
                        outputTriangle = emitBakedTriangle(
                                raw, vertexPool, outputTriangle, frame, atlas, sourceTriangle,
                                b1, c1, b3, c3, b2, c2,
                                true, sourceNormal, stateX, stateY, stateZ);
                    }
                }
            }
        }

        MarioEquipmentWorkbench.recordMaskedTriangleCount(maskedSourceTriangles);
        if (outputTriangle != outputTriangles) {
            System.err.println("[SM64 Visual] Texture tessellation count mismatch expected="
                    + outputTriangles + " actual=" + outputTriangle);
            return null;
        }

        lastBuiltOutputTriangles = outputTriangles;
        lastBuiltOutputVertices = raw.anInt1791;
        lastBuiltTextureSubdivisions = textureSubdivisions;

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

    private static int emitBakedTriangle(
            Class159 raw,
            SmoothVertexPool vertexPool,
            int outputTriangle,
            Sm64BridgeSession.GeometryFrame frame,
            Sm64BridgeSession.TextureAtlas atlas,
            int sourceTriangle,
            float b0, float c0,
            float b1, float c1,
            float b2, float c2,
            boolean sampleTexture,
            float[] sourceNormal,
            float stateX, float stateY, float stateZ) {
        int positionBase = sourceTriangle * 9;
        int colorBase = sourceTriangle * 9;
        int uvBase = sourceTriangle * 6;

        int vertexA = writeVertex(vertexPool, frame, positionBase, b0, c0,
                sourceNormal, stateX, stateY, stateZ);
        int vertexB = writeVertex(vertexPool, frame, positionBase, b1, c1,
                sourceNormal, stateX, stateY, stateZ);
        int vertexC = writeVertex(vertexPool, frame, positionBase, b2, c2,
                sourceNormal, stateX, stateY, stateZ);

        float centerB = (b0 + b1 + b2) / 3.0F;
        float centerC = (c0 + c1 + c2) / 3.0F;
        int baseR = unitColor(interpolate(frame.colors, colorBase, 3, 0, centerB, centerC));
        int baseG = unitColor(interpolate(frame.colors, colorBase, 3, 1, centerB, centerC));
        int baseB = unitColor(interpolate(frame.colors, colorBase, 3, 2, centerB, centerC));
        int rgb;
        if (sampleTexture) {
            float u = interpolate(frame.uvs, uvBase, 2, 0, centerB, centerC);
            float v = interpolate(frame.uvs, uvBase, 2, 1, centerB, centerC);
            rgb = sampleMarioColor(atlas, u, v, baseR, baseG, baseB);
        } else {
            rgb = baseR << 16 | baseG << 8 | baseB;
        }

        /*
         * Negating model Y mirrors one axis, so B/C are swapped to preserve
         * the native triangle winding for Matrix back-face culling.
         */
        raw.aShortArray1786[outputTriangle] = (short) vertexA;
        raw.aShortArray1787[outputTriangle] = (short) vertexC;
        raw.aShortArray1789[outputTriangle] = (short) vertexB;
        raw.faceColours[outputTriangle] = rgbToRsHsl(
                (rgb >>> 16) & 0xff,
                (rgb >>> 8) & 0xff,
                rgb & 0xff);
        return outputTriangle + 1;
    }

    private static int writeVertex(
            SmoothVertexPool vertexPool,
            Sm64BridgeSession.GeometryFrame frame,
            int positionBase,
            float b,
            float c,
            float[] sourceNormal,
            float stateX,
            float stateY,
            float stateZ) {
        float x = interpolate(frame.positions, positionBase, 3, 0, b, c);
        float y = interpolate(frame.positions, positionBase, 3, 1, b, c);
        float z = interpolate(frame.positions, positionBase, 3, 2, b, c);
        int modelX = Math.round((x - stateX) * MODEL_SCALE);
        int modelY = Math.round(-(y - stateY) * MODEL_SCALE);
        int modelZ = Math.round((z - stateZ) * MODEL_SCALE);

        if (isTriangleBoundaryPoint(b, c)) {
            return vertexPool.getOrCreateSmooth(
                    modelX, modelY, modelZ,
                    sourceNormal[0], sourceNormal[1], sourceNormal[2]);
        }
        return vertexPool.addUnique(modelX, modelY, modelZ);
    }

    private static boolean isTriangleBoundaryPoint(float b, float c) {
        float a = 1.0F - b - c;
        final float epsilon = 0.0001F;
        return Math.abs(a) <= epsilon || Math.abs(b) <= epsilon || Math.abs(c) <= epsilon;
    }

    /**
     * Geometric normal for the source SM64 triangle after Matrix's Y-axis mirror
     * and B/C winding correction. It is used only to decide which coincident
     * boundary vertices may share Matrix normal smoothing.
     */
    private static void calculateSourceNormal(
            Sm64BridgeSession.GeometryFrame frame,
            int positionBase,
            float[] out) {
        float ax = frame.positions[positionBase];
        float ay = -frame.positions[positionBase + 1];
        float az = frame.positions[positionBase + 2];

        // Matrix face order is A,C,B after the Y mirror.
        float bx = frame.positions[positionBase + 6] - ax;
        float by = -frame.positions[positionBase + 7] - ay;
        float bz = frame.positions[positionBase + 8] - az;
        float cx = frame.positions[positionBase + 3] - ax;
        float cy = -frame.positions[positionBase + 4] - ay;
        float cz = frame.positions[positionBase + 5] - az;

        float nx = by * cz - bz * cy;
        float ny = bz * cx - bx * cz;
        float nz = bx * cy - by * cx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0.000001F || Float.isNaN(length) || Float.isInfinite(length)) {
            out[0] = 0.0F;
            out[1] = 0.0F;
            out[2] = 0.0F;
            return;
        }
        out[0] = nx / length;
        out[1] = ny / length;
        out[2] = nz / length;
    }

    private static float interpolate(float[] values, int base, int stride, int component,
            float b, float c) {
        float a = 1.0F - b - c;
        return values[base + component] * a
                + values[base + stride + component] * b
                + values[base + stride * 2 + component] * c;
    }

    private static int chooseTextureSubdivisions(
            Sm64BridgeSession.GeometryFrame frame,
            Sm64BridgeSession.TextureAtlas atlas) {
        if (!atlasReady(atlas) || TEXTURE_SUBDIVISIONS <= 0) {
            return 0;
        }
        int subdivisions = DEBUG_ATLAS_FACE_BAKE ? 1 : TEXTURE_SUBDIVISIONS;
        while (subdivisions > 1
                && countOutputTriangles(frame, atlas, subdivisions) > MAX_MATRIX_TRIANGLES) {
            subdivisions--;
        }
        if (countOutputTriangles(frame, atlas, subdivisions) > MAX_MATRIX_TRIANGLES) {
            return 0;
        }
        return subdivisions;
    }

    private static int countOutputTriangles(
            Sm64BridgeSession.GeometryFrame frame,
            Sm64BridgeSession.TextureAtlas atlas,
            int textureSubdivisions) {
        boolean canTexture = textureSubdivisions > 0 && atlasReady(atlas);
        int texturedMultiplier = canTexture ? textureSubdivisions * textureSubdivisions : 1;
        int total = 0;
        for (int triangle = 0; triangle < frame.triangleCount; triangle++) {
            if (MarioEquipmentWorkbench.shouldMaskTriangle(frame, triangle)) {
                continue;
            }
            total += canTexture && isTexturedTriangle(frame, atlas, triangle)
                    ? texturedMultiplier : 1;
        }
        return total;
    }

    /**
     * libsm64 writes (1,1) for all three UVs when texturing is disabled for a
     * source triangle. Any other finite UV set represents an atlas-textured face.
     */
    private static boolean isTexturedTriangle(
            Sm64BridgeSession.GeometryFrame frame,
            Sm64BridgeSession.TextureAtlas atlas,
            int triangle) {
        if (!atlasReady(atlas)) {
            return false;
        }
        int uvBase = triangle * 6;
        boolean differsFromUntexturedSentinel = false;
        for (int i = 0; i < 6; i++) {
            float value = frame.uvs[uvBase + i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return false;
            }
            if (Math.abs(value - 1.0F) > 0.0001F) {
                differsFromUntexturedSentinel = true;
            }
        }
        return differsFromUntexturedSentinel;
    }

    private static boolean atlasReady(Sm64BridgeSession.TextureAtlas atlas) {
        return atlas != null && atlas.rgba != null
                && atlas.width > 0 && atlas.height > 0
                && atlas.rgba.length >= atlas.width * atlas.height * 4;
    }

    /**
     * Bakes the same albedo decision as libsm64's GL3 shader:
     * mix(baseColor, texture.rgb, texture.a). Sampling follows the reference
     * texture state: GL_CLAMP_TO_EDGE + GL_LINEAR.
     */
    private static int sampleMarioColor(Sm64BridgeSession.TextureAtlas atlas,
            float u, float v, int baseR, int baseG, int baseB) {
        if (!atlasReady(atlas) || Float.isNaN(u) || Float.isNaN(v)
                || Float.isInfinite(u) || Float.isInfinite(v)) {
            return baseR << 16 | baseG << 8 | baseB;
        }

        float clampedU = clamp(u, 0.0F, 1.0F);
        float clampedV = clamp(v, 0.0F, 1.0F);
        float x = clampedU * atlas.width - 0.5F;
        float y = clampedV * atlas.height - 0.5F;
        int rawX0 = (int) Math.floor(x);
        int rawY0 = (int) Math.floor(y);
        int rawX1 = rawX0 + 1;
        int rawY1 = rawY0 + 1;
        float tx = x - rawX0;
        float ty = y - rawY0;
        int x0 = clamp(rawX0, 0, atlas.width - 1);
        int y0 = clamp(rawY0, 0, atlas.height - 1);
        int x1 = clamp(rawX1, 0, atlas.width - 1);
        int y1 = clamp(rawY1, 0, atlas.height - 1);

        int texR = Math.round(bilinearChannel(atlas, x0, y0, x1, y1, tx, ty, 0));
        int texG = Math.round(bilinearChannel(atlas, x0, y0, x1, y1, tx, ty, 1));
        int texB = Math.round(bilinearChannel(atlas, x0, y0, x1, y1, tx, ty, 2));
        int alpha = Math.round(bilinearChannel(atlas, x0, y0, x1, y1, tx, ty, 3));
        int inv = 255 - alpha;
        int r = (baseR * inv + texR * alpha + 127) / 255;
        int g = (baseG * inv + texG * alpha + 127) / 255;
        int b = (baseB * inv + texB * alpha + 127) / 255;
        return r << 16 | g << 8 | b;
    }

    private static float bilinearChannel(
            Sm64BridgeSession.TextureAtlas atlas,
            int x0, int y0, int x1, int y1,
            float tx, float ty,
            int channel) {
        float top = lerp(atlasChannel(atlas, x0, y0, channel),
                atlasChannel(atlas, x1, y0, channel), tx);
        float bottom = lerp(atlasChannel(atlas, x0, y1, channel),
                atlasChannel(atlas, x1, y1, channel), tx);
        return lerp(top, bottom, ty);
    }

    private static int atlasChannel(Sm64BridgeSession.TextureAtlas atlas,
            int x, int y, int channel) {
        int index = (y * atlas.width + x) * 4 + channel;
        return atlas.rgba[index] & 0xff;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
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

    private static int resolveTextureSubdivisions() {
        String configured = System.getProperty("matrix3.sm64.textureSubdivisions");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_TEXTURE_SUBDIVISIONS;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed >= 0 && parsed <= MAX_TEXTURE_SUBDIVISIONS) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }
        System.out.println("[SM64 Visual] Invalid matrix3.sm64.textureSubdivisions='"
                + configured + "'; using " + DEFAULT_TEXTURE_SUBDIVISIONS);
        return DEFAULT_TEXTURE_SUBDIVISIONS;
    }

    private static float resolveSmoothAngleDegrees() {
        String configured = System.getProperty("matrix3.sm64.smoothAngleDegrees");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_SMOOTH_ANGLE_DEGREES;
        }
        try {
            float parsed = Float.parseFloat(configured.trim());
            if (parsed >= 0.0F && parsed <= 180.0F
                    && !Float.isNaN(parsed) && !Float.isInfinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }
        System.out.println("[SM64 Visual] Invalid matrix3.sm64.smoothAngleDegrees='"
                + configured + "'; using " + DEFAULT_SMOOTH_ANGLE_DEGREES);
        return DEFAULT_SMOOTH_ANGLE_DEGREES;
    }

    private static boolean resolveAtlasFaceBake() {
        String configured = System.getProperty("matrix3.sm64.debugAtlasFaceBake");
        return configured != null && Boolean.parseBoolean(configured.trim());
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    /**
     * Reuses only coincident boundary vertices whose source-face normals fall
     * within the configured smoothing angle. This keeps low-poly hard edges while
     * allowing Matrix's generated model path to average compatible shared normals.
     */
    private static final class SmoothVertexPool {
        private final Class159 raw;
        private final int capacity;
        private final float dotThreshold;
        private final HashMap<PositionKey, SmoothVertex> buckets =
                new HashMap<PositionKey, SmoothVertex>();

        SmoothVertexPool(Class159 raw, int capacity, float dotThreshold) {
            this.raw = raw;
            this.capacity = capacity;
            this.dotThreshold = dotThreshold;
        }

        int addUnique(int x, int y, int z) {
            return appendVertex(x, y, z);
        }

        int getOrCreateSmooth(int x, int y, int z, float nx, float ny, float nz) {
            PositionKey key = new PositionKey(x, y, z);
            SmoothVertex candidate = buckets.get(key);
            for (SmoothVertex current = candidate; current != null; current = current.next) {
                if (compatibleNormal(current, nx, ny, nz)) {
                    current.addNormal(nx, ny, nz);
                    return current.index;
                }
            }

            int index = appendVertex(x, y, z);
            buckets.put(key, new SmoothVertex(index, nx, ny, nz, candidate));
            return index;
        }

        private int appendVertex(int x, int y, int z) {
            int index = raw.anInt1791;
            if (index >= capacity) {
                throw new IllegalStateException("Mario generated vertex capacity exceeded");
            }
            raw.anIntArray1782[index] = x;
            raw.anIntArray1777[index] = y;
            raw.anIntArray1797[index] = z;
            raw.anInt1791 = index + 1;
            raw.anInt1775 = raw.anInt1791;
            return index;
        }

        private boolean compatibleNormal(SmoothVertex vertex, float nx, float ny, float nz) {
            if (isZeroNormal(vertex.nx, vertex.ny, vertex.nz)
                    || isZeroNormal(nx, ny, nz)) {
                return false;
            }
            float dot = vertex.nx * nx + vertex.ny * ny + vertex.nz * nz;
            return dot >= dotThreshold;
        }
    }

    private static final class SmoothVertex {
        final int index;
        final SmoothVertex next;
        float nx;
        float ny;
        float nz;

        SmoothVertex(int index, float nx, float ny, float nz, SmoothVertex next) {
            this.index = index;
            this.next = next;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        void addNormal(float addX, float addY, float addZ) {
            float x = nx + addX;
            float y = ny + addY;
            float z = nz + addZ;
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length > 0.000001F && !Float.isNaN(length) && !Float.isInfinite(length)) {
                nx = x / length;
                ny = y / length;
                nz = z / length;
            }
        }
    }

    private static final class PositionKey {
        final int x;
        final int y;
        final int z;

        PositionKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof PositionKey)) {
                return false;
            }
            PositionKey other = (PositionKey) object;
            return x == other.x && y == other.y && z == other.z;
        }
    }

    private static boolean isZeroNormal(float x, float y, float z) {
        return Math.abs(x) <= 0.000001F
                && Math.abs(y) <= 0.000001F
                && Math.abs(z) <= 0.000001F;
    }
}
