package game;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Presentation-only Twilight Princess Link renderer.
 *
 * Matrix3 remains the player/world/input authority. This adapter loads the local
 * Bundle 1.2 DMK proof generated from the user's own GZ2E01 donor, evaluates the
 * stored TP idle/locomotion skin matrices, converts the result to a normal Matrix
 * Model and draws it at the local RuneScape player's transform.
 *
 * No Nintendo asset is stored in the Matrix3 repository and no Python/native
 * process is required while the client is running.
 */
final class TpLinkVisualRenderer {

    private static final int DMK_MAGIC = 0x314b4d44; // "DMK1" as little-endian u32.
    private static final int DMK_VERSION = 1;
    private static final int CHUNK_MESH = 0x4853454d;
    private static final int CHUNK_TEXT = 0x54584554;
    private static final int CHUNK_SKEL = 0x4c454b53;
    private static final int CHUNK_SKIN = 0x4e494b53;
    private static final int CHUNK_ANIM = 0x4d494e41;

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS =
            BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int MAX_MATRIX_VERTICES = 65535;
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;

    private static final long FRESH_RENDER_NS = 500000000L;
    private static final long MOVEMENT_HOLD_NS = 180000000L;
    private static final long LOAD_RETRY_NS = 2000000000L;
    private static final float MOVEMENT_EPSILON_SQ = 0.0625F;

    /*
     * Runtime evidence from the first Matrix render showed the donor-space model
     * at 1.0 was action-figure sized against normal revision-830 humanoids.
     * 5.0 is the first evidence-based envelope calibration; the system property
     * remains available for narrow follow-up tuning without changing ownership.
     */
    private static final float MODEL_SCALE = resolvePositiveFloat("matrix3.tp.modelScale", 5.0F);
    private static final float YAW_OFFSET_DEGREES = resolveFiniteFloat(
            "matrix3.tp.yawOffsetDegrees", 0.0F);

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static DmkAsset asset;
    private static Path assetPath;
    private static long lastAssetLoadAttemptNanos = Long.MIN_VALUE;
    private static String lastAssetFailure;

    private static Class106 cachedRenderer;
    private static Animation cachedAnimation;
    private static int cachedFrame = -1;
    private static Model cachedModel;

    private static Animation activeAnimation;
    private static long activeAnimationStartNanos;
    private static boolean sessionActive;
    private static boolean replacementReady;
    private static long lastFreshRenderSuccessNanos = Long.MIN_VALUE;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static boolean activeLogged;

    private static boolean haveLastPosition;
    private static float lastPlayerX;
    private static float lastPlayerZ;
    private static long movingUntilNanos = Long.MIN_VALUE;

    private TpLinkVisualRenderer() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!PlayerControllerMode.isTpLinkMode()) {
            if (sessionActive) {
                resetPresentationSession();
            }
            return;
        }
        sessionActive = true;

        if (scene == null || renderer == null) {
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

        DmkAsset currentAsset = ensureAsset();
        if (currentAsset == null) {
            clearReplacementReadiness();
            return;
        }

        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null) {
            clearReplacementReadiness();
            return;
        }

        Class240 position = playerTransform.aClass240_2647;
        long now = System.nanoTime();
        boolean moving = updateMovementState(position, now);
        Animation wanted = moving ? currentAsset.walk : currentAsset.idle;
        if (wanted == null) {
            clearReplacementReadiness();
            return;
        }

        if (activeAnimation != wanted) {
            activeAnimation = wanted;
            activeAnimationStartNanos = now;
            cachedAnimation = null;
            cachedFrame = -1;
        }
        int frame = animationFrame(wanted, now - activeAnimationStartNanos);

        if (cachedRenderer != renderer || cachedAnimation != wanted
                || cachedFrame != frame || cachedModel == null) {
            Model rebuilt = buildModel(renderer, currentAsset, wanted, frame);
            if (rebuilt == null) {
                clearReplacementReadiness();
                return;
            }
            cachedRenderer = renderer;
            cachedAnimation = wanted;
            cachedFrame = frame;
            cachedModel = rebuilt;
        }

        float[] rotation = playerRotation(playerTransform.aClass230_2648, YAW_OFFSET_DEGREES);
        if (rotation == null) {
            clearReplacementReadiness();
            return;
        }

        TRANSFORM.method3572(
                rotation[0], rotation[1], rotation[2],
                rotation[3], rotation[4], rotation[5],
                rotation[6], rotation[7], rotation[8]);
        TRANSFORM.method3578(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        TRANSFORM.method3580(position.aFloat2653, position.aFloat2656, position.aFloat2657);

        try {
            cachedModel.method1375(TRANSFORM, RENDER_BOUNDS, 0);
            replacementReady = true;
            lastFreshRenderSuccessNanos = now;
            if (!activeLogged) {
                activeLogged = true;
                System.out.println("[TP Visual] GZ2E01 Link -> Matrix Model ACTIVE"
                        + " vertices=" + currentAsset.vertexCount
                        + " triangles=" + (currentAsset.vertexCount / 3)
                        + " joints=" + currentAsset.jointCount
                        + " idle=" + currentAsset.idle.name
                        + " walk=" + currentAsset.walk.name
                        + " scale=" + MODEL_SCALE
                        + " yawOffset=" + YAW_OFFSET_DEGREES
                        + " source=" + currentAsset.source);
            }
        } catch (RuntimeException ex) {
            clearReplacementReadiness();
            logFailure("render failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /** Fail-open local-player replacement gate shared by Player.method10696(...). */
    static boolean shouldSuppressLocalPlayer(Player player) {
        if (player == null || player != Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976
                || !PlayerControllerMode.isTpLinkMode() || !replacementReady) {
            return false;
        }
        long last = lastFreshRenderSuccessNanos;
        if (last == Long.MIN_VALUE) {
            return false;
        }
        long age = System.nanoTime() - last;
        return age >= 0L && age <= FRESH_RENDER_NS;
    }

    private static DmkAsset ensureAsset() {
        if (asset != null) {
            return asset;
        }
        long now = System.nanoTime();
        if (lastAssetLoadAttemptNanos != Long.MIN_VALUE
                && now - lastAssetLoadAttemptNanos >= 0L
                && now - lastAssetLoadAttemptNanos < LOAD_RETRY_NS) {
            return null;
        }
        lastAssetLoadAttemptNanos = now;
        Path path = resolveAssetPath();
        assetPath = path;
        try {
            if (!Files.isRegularFile(path)) {
                logAssetFailure("local DMK not found: " + path);
                return null;
            }
            long size = Files.size(path);
            if (size <= 0L || size > MAX_FILE_BYTES) {
                logAssetFailure("local DMK has invalid size " + size + ": " + path);
                return null;
            }
            asset = DmkAsset.read(path);
            lastAssetFailure = null;
            System.out.println("[TP Visual] Loaded local TP Link DMK: " + path
                    + " vertices=" + asset.vertexCount
                    + " joints=" + asset.jointCount
                    + " idleFrames=" + asset.idle.frameCount
                    + " walkFrames=" + asset.walk.frameCount);
            return asset;
        } catch (IOException | RuntimeException ex) {
            logAssetFailure(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    private static Path resolveAssetPath() {
        String configured = System.getProperty("matrix3.tp.dmk");
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.trim().isEmpty()) {
            String home = System.getProperty("user.home", ".");
            localAppData = Paths.get(home, "AppData", "Local").toString();
        }
        return Paths.get(localAppData, "Matrix3", "TPLinkProof", "visual", "tp-link-proof.dmk")
                .toAbsolutePath().normalize();
    }

    private static boolean updateMovementState(Class240 position, long now) {
        float x = position.aFloat2653;
        float z = position.aFloat2657;
        if (haveLastPosition) {
            float dx = x - lastPlayerX;
            float dz = z - lastPlayerZ;
            if (dx * dx + dz * dz > MOVEMENT_EPSILON_SQ) {
                movingUntilNanos = now + MOVEMENT_HOLD_NS;
            }
        }
        lastPlayerX = x;
        lastPlayerZ = z;
        haveLastPosition = true;
        return movingUntilNanos != Long.MIN_VALUE && now <= movingUntilNanos;
    }

    private static int animationFrame(Animation animation, long elapsedNanos) {
        if (animation.frameCount <= 1 || animation.fps <= 0.0F) {
            return 0;
        }
        double seconds = Math.max(0L, elapsedNanos) / 1000000000.0;
        long frame = (long) Math.floor(seconds * animation.fps);
        return (int) (frame % animation.frameCount);
    }

    private static Model buildModel(Class106 renderer, DmkAsset data,
            Animation animation, int frame) {
        if (renderer == null || data == null || animation == null
                || data.vertexCount <= 0 || data.vertexCount > MAX_MATRIX_VERTICES) {
            return null;
        }

        float[] skinMatrices = buildSkinMatrices(data, animation, frame);
        if (skinMatrices == null) {
            return null;
        }

        int vertices = data.vertexCount;
        int triangles = vertices / 3;
        Class159 raw = new Class159(vertices, triangles, 0);
        raw.anInt1791 = vertices;
        raw.anInt1775 = vertices;
        raw.anInt1778 = triangles;
        Arrays.fill(raw.anIntArray1813, -1);
        Arrays.fill(raw.anIntArray1780, -1);
        Arrays.fill(raw.faceTextures, (short) -1);
        Arrays.fill(raw.faceTextureIndexes, (short) -1);

        int[] vertexRgb = new int[vertices];
        for (int vertex = 0; vertex < vertices; vertex++) {
            int influence = vertex * 4;
            int joint0 = data.influences[influence] & 0xff;
            int joint1 = data.influences[influence + 1] & 0xff;
            int weight0 = data.influences[influence + 2] & 0xff;
            int weight1 = data.influences[influence + 3] & 0xff;
            if (joint0 >= data.jointCount || joint1 >= data.jointCount) {
                logFailure("skin joint index out of range at vertex " + vertex);
                return null;
            }

            int p = vertex * 3;
            float x = data.positions[p];
            float y = data.positions[p + 1];
            float z = data.positions[p + 2];
            int m0 = joint0 * 12;
            int m1 = joint1 * 12;

            float x0 = skinMatrices[m0] * x + skinMatrices[m0 + 1] * y
                    + skinMatrices[m0 + 2] * z + skinMatrices[m0 + 3];
            float y0 = skinMatrices[m0 + 4] * x + skinMatrices[m0 + 5] * y
                    + skinMatrices[m0 + 6] * z + skinMatrices[m0 + 7];
            float z0 = skinMatrices[m0 + 8] * x + skinMatrices[m0 + 9] * y
                    + skinMatrices[m0 + 10] * z + skinMatrices[m0 + 11];

            float skinnedX;
            float skinnedY;
            float skinnedZ;
            if (weight1 == 0 || joint0 == joint1) {
                skinnedX = x0;
                skinnedY = y0;
                skinnedZ = z0;
            } else {
                float x1 = skinMatrices[m1] * x + skinMatrices[m1 + 1] * y
                        + skinMatrices[m1 + 2] * z + skinMatrices[m1 + 3];
                float y1 = skinMatrices[m1 + 4] * x + skinMatrices[m1 + 5] * y
                        + skinMatrices[m1 + 6] * z + skinMatrices[m1 + 7];
                float z1 = skinMatrices[m1 + 8] * x + skinMatrices[m1 + 9] * y
                        + skinMatrices[m1 + 10] * z + skinMatrices[m1 + 11];
                int total = weight0 + weight1;
                if (total <= 0) {
                    total = 255;
                    weight0 = 255;
                    weight1 = 0;
                }
                float inv = 1.0F / total;
                skinnedX = (x0 * weight0 + x1 * weight1) * inv;
                skinnedY = (y0 * weight0 + y1 * weight1) * inv;
                skinnedZ = (z0 * weight0 + z1 * weight1) * inv;
            }

            raw.anIntArray1782[vertex] = Math.round(skinnedX);
            // TP/J3D is +Y up; Matrix model-space altitude is -Y.
            raw.anIntArray1777[vertex] = Math.round(-skinnedY);
            raw.anIntArray1797[vertex] = Math.round(skinnedZ);
            vertexRgb[vertex] = sampleVertexColor(data, vertex);
        }

        for (int triangle = 0; triangle < triangles; triangle++) {
            int base = triangle * 3;
            // Negating Y mirrors one axis; swap B/C to preserve winding.
            raw.aShortArray1786[triangle] = (short) base;
            raw.aShortArray1787[triangle] = (short) (base + 2);
            raw.aShortArray1789[triangle] = (short) (base + 1);
            int rgb0 = vertexRgb[base];
            int rgb1 = vertexRgb[base + 1];
            int rgb2 = vertexRgb[base + 2];
            int r = (((rgb0 >>> 16) & 0xff) + ((rgb1 >>> 16) & 0xff)
                    + ((rgb2 >>> 16) & 0xff)) / 3;
            int g = (((rgb0 >>> 8) & 0xff) + ((rgb1 >>> 8) & 0xff)
                    + ((rgb2 >>> 8) & 0xff)) / 3;
            int b = ((rgb0 & 0xff) + (rgb1 & 0xff) + (rgb2 & 0xff)) / 3;
            raw.faceColours[triangle] = rgbToRsHsl(r, g, b);
        }

        try {
            Model model = renderer.method1755(raw, RAW_BUILD_FLAGS, 0, 64, 850);
            if (model != null) {
                model.method1450(FINAL_MODEL_FLAGS);
            }
            return model;
        } catch (RuntimeException ex) {
            logFailure("Matrix model conversion failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    private static float[] buildSkinMatrices(DmkAsset data, Animation animation, int frame) {
        if (frame < 0 || frame >= animation.frameCount
                || animation.jointCount != data.jointCount) {
            return null;
        }
        float[] result = new float[data.jointCount * 12];
        int frameBase = frame * data.jointCount * 12;
        for (int joint = 0; joint < data.jointCount; joint++) {
            int pose = frameBase + joint * 12;
            int inv = joint * 16;
            int out = joint * 12;
            for (int row = 0; row < 3; row++) {
                int pr = pose + row * 4;
                int or = out + row * 4;
                for (int col = 0; col < 4; col++) {
                    result[or + col] = animation.frames[pr] * data.inverseBinds[inv + col]
                            + animation.frames[pr + 1] * data.inverseBinds[inv + 4 + col]
                            + animation.frames[pr + 2] * data.inverseBinds[inv + 8 + col]
                            + animation.frames[pr + 3] * data.inverseBinds[inv + 12 + col];
                }
            }
        }
        return result;
    }

    private static int sampleVertexColor(DmkAsset data, int vertex) {
        int c = vertex * 4;
        int baseR = data.rgba[c] & 0xff;
        int baseG = data.rgba[c + 1] & 0xff;
        int baseB = data.rgba[c + 2] & 0xff;
        if (data.texture == null) {
            return clamp(baseR * 2, 0, 255) << 16
                    | clamp(baseG * 2, 0, 255) << 8
                    | clamp(baseB * 2, 0, 255);
        }

        int uv = vertex * 2;
        float u = data.uvs[uv];
        float v = data.uvs[uv + 1];
        if (Float.isNaN(u) || Float.isNaN(v)) {
            return 0;
        }
        int x = clamp((int) Math.floor(u * data.texture.width), 0, data.texture.width - 1);
        int y = clamp((int) Math.floor(v * data.texture.height), 0, data.texture.height - 1);
        int pixel = y * data.texture.width + x;
        if (pixel < 0 || pixel >= data.texture.indices.length) {
            return 0;
        }
        int paletteIndex = data.texture.indices[pixel] & 0xff;
        if (paletteIndex >= data.texture.paletteRgb.length) {
            return 0;
        }
        int tex = data.texture.paletteRgb[paletteIndex];
        int r = clamp((((tex >>> 16) & 0xff) * baseR + 64) / 128, 0, 255);
        int g = clamp((((tex >>> 8) & 0xff) * baseG + 64) / 128, 0, 255);
        int b = clamp(((tex & 0xff) * baseB + 64) / 128, 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static float[] playerRotation(Class230 quaternion, float yawOffsetDegrees) {
        float x = quaternion == null ? 0.0F : quaternion.aFloat2624;
        float y = quaternion == null ? 0.0F : quaternion.aFloat2623;
        float z = quaternion == null ? 0.0F : quaternion.aFloat2626;
        float w = quaternion == null ? 1.0F : quaternion.aFloat2621;
        float lengthSq = x * x + y * y + z * z + w * w;
        if (!(lengthSq > 0.0F) || Float.isNaN(lengthSq) || Float.isInfinite(lengthSq)) {
            return null;
        }
        float invLength = 1.0F / (float) Math.sqrt(lengthSq);
        x *= invLength;
        y *= invLength;
        z *= invLength;
        w *= invLength;

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;
        float[] player = new float[] {
                1.0F - 2.0F * (yy + zz), 2.0F * (xy - wz), 2.0F * (xz + wy),
                2.0F * (xy + wz), 1.0F - 2.0F * (xx + zz), 2.0F * (yz - wx),
                2.0F * (xz - wy), 2.0F * (yz + wx), 1.0F - 2.0F * (xx + yy)
        };

        double radians = Math.toRadians(yawOffsetDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float[] correction = new float[] {
                cos, 0.0F, sin,
                0.0F, 1.0F, 0.0F,
                -sin, 0.0F, cos
        };
        return multiply3x3(player, correction);
    }

    private static float[] multiply3x3(float[] a, float[] b) {
        float[] out = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                out[row * 3 + col] = a[row * 3] * b[col]
                        + a[row * 3 + 1] * b[3 + col]
                        + a[row * 3 + 2] * b[6 + col];
            }
        }
        return out;
    }

    private static void resetPresentationSession() {
        sessionActive = false;
        cachedRenderer = null;
        cachedAnimation = null;
        cachedFrame = -1;
        cachedModel = null;
        activeAnimation = null;
        activeAnimationStartNanos = 0L;
        lastRenderedCycle = Integer.MIN_VALUE;
        activeLogged = false;
        haveLastPosition = false;
        movingUntilNanos = Long.MIN_VALUE;
        clearReplacementReadiness();
    }

    private static void clearReplacementReadiness() {
        replacementReady = false;
        lastFreshRenderSuccessNanos = Long.MIN_VALUE;
    }

    private static void logAssetFailure(String message) {
        if (!message.equals(lastAssetFailure)) {
            lastAssetFailure = message;
            System.err.println("[TP Visual] " + message
                    + "; RuneScape local player remains visible");
        }
    }

    private static void logFailure(String message) {
        System.err.println("[TP Visual] " + message);
    }

    private static float resolvePositiveFloat(String key, float fallback) {
        float value = resolveFiniteFloat(key, fallback);
        if (value > 0.0F) {
            return value;
        }
        System.out.println("[TP Visual] Invalid " + key + "=" + value + "; using " + fallback);
        return fallback;
    }

    private static float resolveFiniteFloat(String key, float fallback) {
        String configured = System.getProperty(key);
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(configured.trim());
            if (!Float.isNaN(value) && !Float.isInfinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        System.out.println("[TP Visual] Invalid " + key + "='" + configured
                + "'; using " + fallback);
        return fallback;
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
            sat = light < 0.5 ? delta / (max + min) : delta / (2.0 - max - min);
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

    private static int align16(int value) {
        return (value + 15) & ~15;
    }

    private static final class DmkAsset {
        final Path source;
        int vertexCount;
        float[] positions;
        float[] uvs;
        byte[] rgba;
        Texture texture;
        int jointCount;
        float[] inverseBinds;
        byte[] influences;
        Animation idle;
        Animation walk;

        DmkAsset(Path source) {
            this.source = source;
        }

        static DmkAsset read(Path path) throws IOException {
            byte[] bytes = Files.readAllBytes(path);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (buffer.remaining() < 8 || buffer.getInt() != DMK_MAGIC) {
                throw new IllegalArgumentException("not a DMK1 file: " + path);
            }
            int version = buffer.getShort() & 0xffff;
            int chunkCount = buffer.getShort() & 0xffff;
            if (version != DMK_VERSION || chunkCount <= 0 || chunkCount > 64) {
                throw new IllegalArgumentException("unsupported DMK header version="
                        + version + " chunks=" + chunkCount);
            }

            DmkAsset result = new DmkAsset(path);
            for (int chunk = 0; chunk < chunkCount; chunk++) {
                if (buffer.remaining() < 8) {
                    throw new IllegalArgumentException("truncated DMK chunk header " + chunk);
                }
                int type = buffer.getInt();
                int size = buffer.getInt();
                int payload = buffer.position();
                if (size < 0 || payload > bytes.length - size) {
                    throw new IllegalArgumentException("invalid DMK chunk size " + size);
                }
                if (type == CHUNK_MESH) {
                    result.readMesh(buffer, payload, size);
                } else if (type == CHUNK_TEXT) {
                    result.readTexture(buffer, payload, size);
                } else if (type == CHUNK_SKEL) {
                    result.readSkeleton(buffer, payload, size);
                } else if (type == CHUNK_SKIN) {
                    result.readSkin(buffer, payload, size);
                } else if (type == CHUNK_ANIM) {
                    result.readAnimation(buffer, payload, size);
                }
                int next = payload + align16(size);
                if (next < payload || next > bytes.length) {
                    throw new IllegalArgumentException("DMK chunk padding exceeds file");
                }
                buffer.position(next);
            }
            result.validate();
            return result;
        }

        private void readMesh(ByteBuffer buffer, int payload, int size) {
            if (size < 32) {
                throw new IllegalArgumentException("MESH chunk too small");
            }
            buffer.position(payload);
            int count = buffer.getInt();
            int flags = buffer.getInt();
            if (count <= 0 || count > MAX_MATRIX_VERTICES || count % 3 != 0
                    || (flags & 0x2) == 0) {
                throw new IllegalArgumentException("unsupported MESH vertices=" + count
                        + " flags=" + flags);
            }
            int required = 32 + count * 24;
            if (required > size) {
                throw new IllegalArgumentException("truncated MESH payload");
            }
            for (int i = 0; i < 6; i++) {
                buffer.getFloat(); // bbox min/max
            }
            vertexCount = count;
            positions = new float[count * 3];
            uvs = new float[count * 2];
            rgba = new byte[count * 4];
            for (int vertex = 0; vertex < count; vertex++) {
                int p = vertex * 3;
                positions[p] = buffer.getFloat();
                positions[p + 1] = buffer.getFloat();
                positions[p + 2] = buffer.getFloat();
                int uv = vertex * 2;
                uvs[uv] = buffer.getFloat();
                uvs[uv + 1] = buffer.getFloat();
                buffer.get(rgba, vertex * 4, 4);
            }
        }

        private void readTexture(ByteBuffer buffer, int payload, int size) {
            if (size < 8) {
                throw new IllegalArgumentException("TEXT chunk too small");
            }
            buffer.position(payload);
            int width = buffer.getShort() & 0xffff;
            int height = buffer.getShort() & 0xffff;
            int bpp = buffer.getShort() & 0xffff;
            int colors = buffer.getShort() & 0xffff;
            if (width <= 0 || height <= 0 || width > 256 || height > 256
                    || bpp != 8 || colors <= 0 || colors > 256) {
                throw new IllegalArgumentException("unsupported TEXT " + width + "x" + height
                        + " bpp=" + bpp + " colors=" + colors);
            }
            int required = 8 + colors * 4 + width * height;
            if (required > size) {
                throw new IllegalArgumentException("truncated TEXT payload");
            }
            int[] palette = new int[colors];
            for (int i = 0; i < colors; i++) {
                int r = buffer.get() & 0xff;
                int g = buffer.get() & 0xff;
                int b = buffer.get() & 0xff;
                buffer.get(); // GS alpha; colour bake needs RGB only.
                palette[i] = r << 16 | g << 8 | b;
            }
            byte[] indices = new byte[width * height];
            buffer.get(indices);
            texture = new Texture(width, height, palette, indices);
        }

        private void readSkeleton(ByteBuffer buffer, int payload, int size) {
            if (size < 16) {
                throw new IllegalArgumentException("SKEL chunk too small");
            }
            buffer.position(payload);
            int count = buffer.getInt();
            buffer.position(payload + 16);
            int parentsBytes = align16(count * 4);
            int inverseOffset = payload + 16 + parentsBytes;
            int required = 16 + parentsBytes + count * 16 * 4;
            if (count <= 0 || count > 256 || required > size) {
                throw new IllegalArgumentException("unsupported SKEL joints=" + count);
            }
            jointCount = count;
            inverseBinds = new float[count * 16];
            buffer.position(inverseOffset);
            for (int i = 0; i < inverseBinds.length; i++) {
                inverseBinds[i] = buffer.getFloat();
            }
        }

        private void readSkin(ByteBuffer buffer, int payload, int size) {
            if (size < 16) {
                throw new IllegalArgumentException("SKIN chunk too small");
            }
            buffer.position(payload);
            int count = buffer.getInt();
            int required = 16 + count * 4;
            if (count <= 0 || required > size) {
                throw new IllegalArgumentException("unsupported SKIN vertices=" + count);
            }
            influences = new byte[count * 4];
            buffer.position(payload + 16);
            buffer.get(influences);
        }

        private void readAnimation(ByteBuffer buffer, int payload, int size) {
            if (size < 32) {
                throw new IllegalArgumentException("ANIM chunk too small");
            }
            buffer.position(payload);
            byte[] nameBytes = new byte[16];
            buffer.get(nameBytes);
            int nameLength = 0;
            while (nameLength < nameBytes.length && nameBytes[nameLength] != 0) {
                nameLength++;
            }
            String name = new String(nameBytes, 0, nameLength);
            float fps = buffer.getFloat();
            int frames = buffer.getInt();
            int joints = buffer.getInt();
            buffer.getInt(); // pad
            long floatCount = (long) frames * joints * 12L;
            long required = 32L + floatCount * 4L;
            if (fps <= 0.0F || Float.isNaN(fps) || Float.isInfinite(fps)
                    || frames <= 0 || joints <= 0 || floatCount > Integer.MAX_VALUE
                    || required > size) {
                throw new IllegalArgumentException("unsupported ANIM " + name
                        + " frames=" + frames + " joints=" + joints + " fps=" + fps);
            }
            float[] samples = new float[(int) floatCount];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = buffer.getFloat();
            }
            Animation animation = new Animation(name, fps, frames, joints, samples);
            if ("idle".equalsIgnoreCase(name)) {
                idle = animation;
            } else if ("walk".equalsIgnoreCase(name)) {
                walk = animation;
            }
        }

        private void validate() {
            if (vertexCount <= 0 || positions == null || uvs == null || rgba == null) {
                throw new IllegalArgumentException("DMK is missing MESH data");
            }
            if (jointCount <= 0 || inverseBinds == null) {
                throw new IllegalArgumentException("DMK is missing SKEL data");
            }
            if (influences == null || influences.length != vertexCount * 4) {
                throw new IllegalArgumentException("DMK SKIN/MESH vertex counts do not match");
            }
            if (idle == null || walk == null) {
                throw new IllegalArgumentException("DMK must contain named idle and walk ANIM chunks");
            }
            if (idle.jointCount != jointCount || walk.jointCount != jointCount) {
                throw new IllegalArgumentException("DMK animation/skeleton joint counts do not match");
            }
        }
    }

    private static final class Texture {
        final int width;
        final int height;
        final int[] paletteRgb;
        final byte[] indices;

        Texture(int width, int height, int[] paletteRgb, byte[] indices) {
            this.width = width;
            this.height = height;
            this.paletteRgb = paletteRgb;
            this.indices = indices;
        }
    }

    private static final class Animation {
        final String name;
        final float fps;
        final int frameCount;
        final int jointCount;
        final float[] frames;

        Animation(String name, float fps, int frameCount, int jointCount, float[] frames) {
            this.name = name;
            this.fps = fps;
            this.frameCount = frameCount;
            this.jointCount = jointCount;
            this.frames = frames;
        }
    }
}
