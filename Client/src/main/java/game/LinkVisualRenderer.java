package game;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Matrix-native presentation adapter for liboot's animated adult-Link geometry.
 *
 * Protocol V2 carries OoT positions, normals, colours, UVs, per-triangle texture
 * indices, and local-ROM RGBA texture updates. Matrix's synthetic runtime-material
 * path was not reliable for these local textures, so Link uses a CPU micro-bake:
 * the real OoT RGBA pixels are sampled over subdivided source triangles and emitted
 * as normal Matrix face colours. Compatible source boundaries are shared through
 * an angle-aware smoothing pool so Matrix does not relight every OoT polygon as an
 * isolated crystal face.
 */
public final class LinkVisualRenderer {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS =
            BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int MAX_MATRIX_VERTICES = 65535;
    private static final int MAX_MATRIX_TRIANGLES = MAX_MATRIX_VERTICES / 3;
    private static final int DEFAULT_TEXTURE_SUBDIVISIONS = 6;
    private static final int MAX_TEXTURE_SUBDIVISIONS = 8;
    private static final float DEFAULT_SMOOTH_ANGLE_DEGREES = 70.0F;
    private static final long MAX_FRAME_AGE_NANOS = 500000000L;

    private static final int TEXTURE_SUBDIVISIONS = resolveTextureSubdivisions();
    private static final float SMOOTH_ANGLE_DEGREES = resolveSmoothAngleDegrees();
    private static final float SMOOTH_DOT_THRESHOLD =
            (float) Math.cos(Math.toRadians(SMOOTH_ANGLE_DEGREES));

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static long cachedSequence = -1L;
    private static long cachedFitRevision = -1L;
    private static Model cachedModel;
    private static volatile boolean replacementReady;
    private static volatile long lastFreshRenderSuccessNanos = Long.MIN_VALUE;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static long lastLoggedSequence = -1L;
    private static long lastFailedSequence = -1L;
    private static int cachedUniqueVertices;
    private static int cachedOutputTriangles;
    private static int cachedTexturedSourceFaces;
    private static int cachedTextureCatalogSize;
    private static int cachedTextureSubdivisions;

    private LinkVisualRenderer() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!PlayerControllerMode.isLinkMode()) {
            resetPresentationCache();
            return;
        }
        if (scene == null || renderer == null || !OotBridgeSession.isReady()) {
            clearReplacementReadiness();
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
            clearReplacementReadiness();
            return;
        }

        LinkCharacterFit.Profile fit = LinkCharacterFit.resolve(player, frame);
        if (fit == null) {
            clearReplacementReadiness();
            return;
        }

        if (cachedRenderer != renderer
                || cachedSequence != frame.sequence
                || cachedFitRevision != fit.revision
                || cachedModel == null) {
            Model rebuilt = buildModel(renderer, frame, fit);
            if (rebuilt == null) {
                clearReplacementReadiness();
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
                clearReplacementReadiness();
                return;
            }
            Class240 position = playerTransform.aClass240_2647;
            TRANSFORM.method3588(
                    Math.round(position.aFloat2653),
                    Math.round(position.aFloat2656),
                    Math.round(position.aFloat2657));
            cachedModel.method1375(TRANSFORM, RENDER_BOUNDS, 0);
            replacementReady = true;
            lastFreshRenderSuccessNanos = System.nanoTime();

            if (lastLoggedSequence < 0L) {
                lastLoggedSequence = frame.sequence;
                System.out.println("[OoT Visual] Native ADULT Link -> Matrix Model ACTIVE"
                        + " triangles=" + frame.triangleCount
                        + " matrixTriangles=" + cachedOutputTriangles
                        + " uniqueVerts=" + cachedUniqueVertices
                        + " texturedSource=" + cachedTexturedSourceFaces
                        + " textureCatalog=" + cachedTextureCatalogSize
                        + " textureSubdivisions=" + cachedTextureSubdivisions
                        + " smoothAngle=" + SMOOTH_ANGLE_DEGREES
                        + " textureFilter=4tap"
                        + " anim=" + frame.animId
                        + " action=" + frame.action
                        + " scale=" + fit.scale
                        + " fit=" + (fit.forcedScale ? "forced"
                                : fit.autoFit ? "830-auto" : "fallback")
                        + " material=oot-rgba-micro-v4");
                if (cachedTextureCatalogSize <= 0 || cachedTexturedSourceFaces <= 0) {
                    System.err.println("[OoT Visual] OoT RGBA bake has no usable textured faces yet"
                            + " textureCatalog=" + cachedTextureCatalogSize
                            + " texturedSource=" + cachedTexturedSourceFaces
                            + "; vertex-colour fallback is active");
                }
            }
        } catch (RuntimeException ex) {
            clearReplacementReadiness();
            if (lastFailedSequence != frame.sequence) {
                lastFailedSequence = frame.sequence;
                System.err.println("[OoT Visual] Render failed: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    static boolean shouldSuppressLocalPlayer(Player player) {
        if (player == null || player != Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976
                || !PlayerControllerMode.isLinkMode()
                || !OotBridgeSession.isReady()
                || !replacementReady
                || !isUsable(OotBridgeSession.getLatestFrame())) {
            return false;
        }
        long last = lastFreshRenderSuccessNanos;
        if (last == Long.MIN_VALUE) {
            return false;
        }
        long age = System.nanoTime() - last;
        return age >= 0L && age <= MAX_FRAME_AGE_NANOS;
    }

    private static void clearReplacementReadiness() {
        replacementReady = false;
        lastFreshRenderSuccessNanos = Long.MIN_VALUE;
    }

    private static void resetPresentationCache() {
        cachedRenderer = null;
        cachedSequence = -1L;
        cachedFitRevision = -1L;
        cachedModel = null;
        cachedUniqueVertices = 0;
        cachedOutputTriangles = 0;
        cachedTexturedSourceFaces = 0;
        cachedTextureCatalogSize = 0;
        cachedTextureSubdivisions = 0;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastLoggedSequence = -1L;
        lastFailedSequence = -1L;
        clearReplacementReadiness();
        LinkCharacterFit.resetSession();
        LinkTextureRegistry.resetSession();
    }

    private static boolean isUsable(OotBridgeSession.LinkFrame frame) {
        if (frame == null || !frame.skeletonAvailable || frame.triangleCount <= 0
                || frame.positions == null || frame.normals == null
                || frame.colors == null || frame.uvs == null
                || frame.triangleTextures == null
                || frame.positions.length < frame.triangleCount * 9
                || frame.normals.length < frame.triangleCount * 9
                || frame.colors.length < frame.triangleCount * 9
                || frame.uvs.length < frame.triangleCount * 6
                || frame.triangleTextures.length < frame.triangleCount) {
            return false;
        }
        long age = System.nanoTime() - frame.receivedNanos;
        return age >= 0L && age <= MAX_FRAME_AGE_NANOS;
    }

    private static Model buildModel(Class106 renderer, OotBridgeSession.LinkFrame frame,
            LinkCharacterFit.Profile fit) {
        TextureCatalog textures = new TextureCatalog(frame.textureUpdates);
        int subdivisions = chooseTextureSubdivisions(frame, textures);
        int outputTriangles = countOutputTriangles(frame, textures, subdivisions);
        int vertexCapacity = outputTriangles * 3;
        if (outputTriangles <= 0 || outputTriangles > MAX_MATRIX_TRIANGLES
                || vertexCapacity <= 0 || vertexCapacity > MAX_MATRIX_VERTICES) {
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

        SmoothVertexPool vertices =
                new SmoothVertexPool(raw, vertexCapacity, SMOOTH_DOT_THRESHOLD);
        float[] sourceNormal = new float[3];
        int outputTriangle = 0;
        int texturedSourceFaces = 0;

        for (int sourceTriangle = 0; sourceTriangle < frame.triangleCount; sourceTriangle++) {
            calculateSourceNormal(frame, sourceTriangle, sourceNormal);

            OotBridgeSession.TextureUpdate texture =
                    textureForTriangle(frame, textures, sourceTriangle);
            boolean textured = subdivisions > 0 && texture != null
                    && hasFiniteUvs(frame, sourceTriangle);

            if (!textured) {
                outputTriangle = emitMicroTriangle(
                        raw, vertices, outputTriangle,
                        frame, fit, sourceTriangle, null, sourceNormal,
                        0.0F, 0.0F,
                        1.0F, 0.0F,
                        0.0F, 1.0F);
                continue;
            }

            texturedSourceFaces++;
            float step = 1.0F / subdivisions;
            for (int b = 0; b < subdivisions; b++) {
                for (int c = 0; c < subdivisions - b; c++) {
                    float b0 = b * step;
                    float c0 = c * step;
                    float b1 = (b + 1) * step;
                    float c1 = c * step;
                    float b2 = b * step;
                    float c2 = (c + 1) * step;

                    outputTriangle = emitMicroTriangle(
                            raw, vertices, outputTriangle,
                            frame, fit, sourceTriangle, texture, sourceNormal,
                            b0, c0, b1, c1, b2, c2);

                    if (b + c + 1 < subdivisions) {
                        float b3 = (b + 1) * step;
                        float c3 = (c + 1) * step;
                        outputTriangle = emitMicroTriangle(
                                raw, vertices, outputTriangle,
                                frame, fit, sourceTriangle, texture, sourceNormal,
                                b1, c1, b3, c3, b2, c2);
                    }
                }
            }
        }

        if (outputTriangle != outputTriangles) {
            System.err.println("[OoT Visual] Texture tessellation count mismatch expected="
                    + outputTriangles + " actual=" + outputTriangle);
            return null;
        }

        cachedUniqueVertices = vertices.size();
        cachedOutputTriangles = outputTriangles;
        cachedTexturedSourceFaces = texturedSourceFaces;
        cachedTextureCatalogSize = textures.size();
        cachedTextureSubdivisions = texturedSourceFaces > 0 ? subdivisions : 0;

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

    private static int emitMicroTriangle(
            Class159 raw,
            SmoothVertexPool vertices,
            int outputTriangle,
            OotBridgeSession.LinkFrame frame,
            LinkCharacterFit.Profile fit,
            int sourceTriangle,
            OotBridgeSession.TextureUpdate texture,
            float[] sourceNormal,
            float b0, float c0,
            float b1, float c1,
            float b2, float c2) {
        int vertexA = writeVertex(
                vertices, frame, fit, sourceTriangle, b0, c0, sourceNormal);
        int vertexB = writeVertex(
                vertices, frame, fit, sourceTriangle, b1, c1, sourceNormal);
        int vertexC = writeVertex(
                vertices, frame, fit, sourceTriangle, b2, c2, sourceNormal);

        raw.aShortArray1786[outputTriangle] = (short) vertexA;
        raw.aShortArray1787[outputTriangle] = (short) vertexC;
        raw.aShortArray1789[outputTriangle] = (short) vertexB;

        int rgb = sampleMicroTriangleColor(
                frame, sourceTriangle, texture,
                b0, c0, b1, c1, b2, c2);
        raw.faceColours[outputTriangle] = rgbToRsHsl(
                rgb >>> 16 & 0xff,
                rgb >>> 8 & 0xff,
                rgb & 0xff);
        return outputTriangle + 1;
    }

    private static int sampleMicroTriangleColor(
            OotBridgeSession.LinkFrame frame,
            int sourceTriangle,
            OotBridgeSession.TextureUpdate texture,
            float b0, float c0,
            float b1, float c1,
            float b2, float c2) {
        float centerB = (b0 + b1 + b2) / 3.0F;
        float centerC = (c0 + c1 + c2) / 3.0F;

        int center = sampleBakedColor(frame, sourceTriangle, texture, centerB, centerC);
        if (texture == null) {
            return center;
        }

        int s0 = sampleBakedColor(frame, sourceTriangle, texture,
                (centerB + b0) * 0.5F, (centerC + c0) * 0.5F);
        int s1 = sampleBakedColor(frame, sourceTriangle, texture,
                (centerB + b1) * 0.5F, (centerC + c1) * 0.5F);
        int s2 = sampleBakedColor(frame, sourceTriangle, texture,
                (centerB + b2) * 0.5F, (centerC + c2) * 0.5F);

        int r = ((center >>> 16 & 0xff)
                + (s0 >>> 16 & 0xff)
                + (s1 >>> 16 & 0xff)
                + (s2 >>> 16 & 0xff) + 2) / 4;
        int g = ((center >>> 8 & 0xff)
                + (s0 >>> 8 & 0xff)
                + (s1 >>> 8 & 0xff)
                + (s2 >>> 8 & 0xff) + 2) / 4;
        int b = ((center & 0xff)
                + (s0 & 0xff)
                + (s1 & 0xff)
                + (s2 & 0xff) + 2) / 4;
        return r << 16 | g << 8 | b;
    }

    private static int sampleBakedColor(
            OotBridgeSession.LinkFrame frame,
            int sourceTriangle,
            OotBridgeSession.TextureUpdate texture,
            float b, float c) {
        int colorBase = sourceTriangle * 9;
        int baseR = unitColor(interpolate(frame.colors, colorBase, 3, 0, b, c));
        int baseG = unitColor(interpolate(frame.colors, colorBase, 3, 1, b, c));
        int baseB = unitColor(interpolate(frame.colors, colorBase, 3, 2, b, c));

        if (texture == null) {
            return baseR << 16 | baseG << 8 | baseB;
        }

        int uvBase = sourceTriangle * 6;
        float u = interpolate(frame.uvs, uvBase, 2, 0, b, c);
        float v = interpolate(frame.uvs, uvBase, 2, 1, b, c);
        int rgba = sampleTexture(texture, u, v);
        int alpha = rgba >>> 24 & 0xff;

        if (alpha <= 0) {
            return baseR << 16 | baseG << 8 | baseB;
        }

        int texR = rgba >>> 16 & 0xff;
        int texG = rgba >>> 8 & 0xff;
        int texB = rgba & 0xff;
        int litR = (texR * baseR + 127) / 255;
        int litG = (texG * baseG + 127) / 255;
        int litB = (texB * baseB + 127) / 255;

        if (alpha >= 255) {
            return litR << 16 | litG << 8 | litB;
        }

        int inverse = 255 - alpha;
        int r = (baseR * inverse + litR * alpha + 127) / 255;
        int g = (baseG * inverse + litG * alpha + 127) / 255;
        int blue = (baseB * inverse + litB * alpha + 127) / 255;
        return r << 16 | g << 8 | blue;
    }

    private static int writeVertex(
            SmoothVertexPool vertices,
            OotBridgeSession.LinkFrame frame,
            LinkCharacterFit.Profile fit,
            int sourceTriangle,
            float b,
            float c,
            float[] sourceNormal) {
        int positionBase = sourceTriangle * 9;
        float x = interpolate(frame.positions, positionBase, 3, 0, b, c);
        float y = interpolate(frame.positions, positionBase, 3, 1, b, c);
        float z = interpolate(frame.positions, positionBase, 3, 2, b, c);
        int modelX = Math.round((x - frame.x) * fit.scale);
        int modelY = Math.round(fit.toMatrixY(y, frame.y));
        int modelZ = Math.round((z - frame.z) * fit.scale);

        if (isTriangleBoundaryPoint(b, c)) {
            return vertices.getOrCreateSmooth(
                    modelX, modelY, modelZ,
                    sourceNormal[0], sourceNormal[1], sourceNormal[2]);
        }
        return vertices.addUnique(modelX, modelY, modelZ);
    }

    private static boolean isTriangleBoundaryPoint(float b, float c) {
        float a = 1.0F - b - c;
        final float epsilon = 0.0001F;
        return Math.abs(a) <= epsilon
                || Math.abs(b) <= epsilon
                || Math.abs(c) <= epsilon;
    }

    private static void calculateSourceNormal(
            OotBridgeSession.LinkFrame frame,
            int sourceTriangle,
            float[] out) {
        int base = sourceTriangle * 9;

        float ax = frame.positions[base];
        float ay = -frame.positions[base + 1];
        float az = frame.positions[base + 2];

        float bx = frame.positions[base + 6] - ax;
        float by = -frame.positions[base + 7] - ay;
        float bz = frame.positions[base + 8] - az;
        float cx = frame.positions[base + 3] - ax;
        float cy = -frame.positions[base + 4] - ay;
        float cz = frame.positions[base + 5] - az;

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

    private static OotBridgeSession.TextureUpdate textureForTriangle(
            OotBridgeSession.LinkFrame frame, TextureCatalog textures, int triangle) {
        if (frame == null || triangle < 0 || triangle >= frame.triangleCount) {
            return null;
        }
        int textureIndex = frame.triangleTextures[triangle];
        if (textureIndex == 0xffff) {
            return null;
        }
        return textures.get(textureIndex);
    }

    private static boolean hasFiniteUvs(OotBridgeSession.LinkFrame frame, int triangle) {
        int base = triangle * 6;
        for (int i = 0; i < 6; i++) {
            float value = frame.uvs[base + i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static int chooseTextureSubdivisions(
            OotBridgeSession.LinkFrame frame, TextureCatalog textures) {
        if (TEXTURE_SUBDIVISIONS <= 0 || textures.size() <= 0) {
            return 0;
        }
        int subdivisions = TEXTURE_SUBDIVISIONS;
        while (subdivisions > 1
                && countOutputTriangles(frame, textures, subdivisions) > MAX_MATRIX_TRIANGLES) {
            subdivisions--;
        }
        if (countOutputTriangles(frame, textures, subdivisions) > MAX_MATRIX_TRIANGLES) {
            return 0;
        }
        return subdivisions;
    }

    private static int countOutputTriangles(OotBridgeSession.LinkFrame frame,
            TextureCatalog textures, int subdivisions) {
        int total = 0;
        int texturedMultiplier = subdivisions > 0 ? subdivisions * subdivisions : 1;
        for (int triangle = 0; triangle < frame.triangleCount; triangle++) {
            boolean textured = subdivisions > 0
                    && textureForTriangle(frame, textures, triangle) != null
                    && hasFiniteUvs(frame, triangle);
            total += textured ? texturedMultiplier : 1;
        }
        return total;
    }

    private static int sampleTexture(OotBridgeSession.TextureUpdate texture, float u, float v) {
        if (texture == null || texture.rgba == null
                || texture.width <= 0 || texture.height <= 0
                || texture.rgba.length < texture.width * texture.height * 4
                || Float.isNaN(u) || Float.isInfinite(u)
                || Float.isNaN(v) || Float.isInfinite(v)) {
            return 0xffffffff;
        }

        float x = u * texture.width - 0.5F;
        float y = v * texture.height - 0.5F;
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        float tx = x - x0;
        float ty = y - y0;

        int sx0 = wrapTexel(x0, texture.width, texture.wrapS);
        int sx1 = wrapTexel(x1, texture.width, texture.wrapS);
        int sy0 = wrapTexel(y0, texture.height, texture.wrapT);
        int sy1 = wrapTexel(y1, texture.height, texture.wrapT);

        int r = clamp(Math.round(
                bilinearChannel(texture, sx0, sy0, sx1, sy1, tx, ty, 0)), 0, 255);
        int g = clamp(Math.round(
                bilinearChannel(texture, sx0, sy0, sx1, sy1, tx, ty, 1)), 0, 255);
        int b = clamp(Math.round(
                bilinearChannel(texture, sx0, sy0, sx1, sy1, tx, ty, 2)), 0, 255);
        int a = clamp(Math.round(
                bilinearChannel(texture, sx0, sy0, sx1, sy1, tx, ty, 3)), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float bilinearChannel(OotBridgeSession.TextureUpdate texture,
            int x0, int y0, int x1, int y1,
            float tx, float ty, int channel) {
        float top = lerp(
                textureChannel(texture, x0, y0, channel),
                textureChannel(texture, x1, y0, channel), tx);
        float bottom = lerp(
                textureChannel(texture, x0, y1, channel),
                textureChannel(texture, x1, y1, channel), tx);
        return lerp(top, bottom, ty);
    }

    private static int textureChannel(OotBridgeSession.TextureUpdate texture,
            int x, int y, int channel) {
        int offset = (y * texture.width + x) * 4 + channel;
        return texture.rgba[offset] & 0xff;
    }

    private static int wrapTexel(int coordinate, int size, int mode) {
        if (size <= 1) {
            return 0;
        }
        if (mode == 2) {
            return clamp(coordinate, 0, size - 1);
        }
        if (mode == 1) {
            int period = size * 2;
            int value = coordinate % period;
            if (value < 0) {
                value += period;
            }
            return value < size ? value : period - 1 - value;
        }
        int value = coordinate % size;
        return value < 0 ? value + size : value;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int unitColor(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }
        return clamp(Math.round(value * 255.0F), 0, 255);
    }

    private static int resolveTextureSubdivisions() {
        String configured = System.getProperty("matrix3.oot.textureSubdivisions");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_TEXTURE_SUBDIVISIONS;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed >= 0 && parsed <= MAX_TEXTURE_SUBDIVISIONS) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        System.out.println("[OoT Visual] Invalid matrix3.oot.textureSubdivisions='"
                + configured + "'; using " + DEFAULT_TEXTURE_SUBDIVISIONS);
        return DEFAULT_TEXTURE_SUBDIVISIONS;
    }

    private static float resolveSmoothAngleDegrees() {
        String configured = System.getProperty("matrix3.oot.smoothAngleDegrees");
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
        }
        System.out.println("[OoT Visual] Invalid matrix3.oot.smoothAngleDegrees='"
                + configured + "'; using " + DEFAULT_SMOOTH_ANGLE_DEGREES);
        return DEFAULT_SMOOTH_ANGLE_DEGREES;
    }

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
        if (l > 243) {
            s >>= 4;
        } else if (l > 217) {
            s >>= 3;
        } else if (l > 192) {
            s >>= 2;
        } else if (l > 179) {
            s >>= 1;
        }
        return (short) (((h >> 2) << 10) | ((s >> 5) << 7) | (l >> 1));
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static final class TextureCatalog {
        private final Map<Integer, OotBridgeSession.TextureUpdate> byIndex =
                new HashMap<Integer, OotBridgeSession.TextureUpdate>();

        TextureCatalog(OotBridgeSession.TextureUpdate[] updates) {
            if (updates == null) {
                return;
            }
            for (int i = 0; i < updates.length; i++) {
                OotBridgeSession.TextureUpdate texture = updates[i];
                if (texture == null || texture.index < 0
                        || texture.rgba == null
                        || texture.width <= 0 || texture.height <= 0) {
                    continue;
                }
                long required = (long) texture.width * (long) texture.height * 4L;
                if (required <= 0L || required > Integer.MAX_VALUE
                        || texture.rgba.length < (int) required) {
                    continue;
                }
                byIndex.put(Integer.valueOf(texture.index), texture);
            }
        }

        OotBridgeSession.TextureUpdate get(int index) {
            return byIndex.get(Integer.valueOf(index));
        }

        int size() {
            return byIndex.size();
        }
    }

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

        int size() {
            return raw.anInt1791;
        }

        private int appendVertex(int x, int y, int z) {
            int index = raw.anInt1791;
            if (index >= capacity || index >= MAX_MATRIX_VERTICES) {
                throw new IllegalStateException("Link generated vertex capacity exceeded");
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
            if (length > 0.000001F
                    && !Float.isNaN(length) && !Float.isInfinite(length)) {
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
