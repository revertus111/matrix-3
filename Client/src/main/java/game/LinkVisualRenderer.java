package game;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Matrix-native presentation adapter for liboot's animated adult-Link geometry.
 *
 * Protocol V2 carries OoT positions, normals, colours, UVs, per-triangle texture
 * indices, and local-ROM texture updates. Textures are registered at runtime in
 * Matrix's existing material path; no OoT texture bytes are written to the cache.
 */
public final class LinkVisualRenderer {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS =
            BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int MAX_MATRIX_VERTICES = 65535;
    private static final int DIRECT_UV_TEXTURE_INDEX = 32766;
    private static final long MAX_FRAME_AGE_NANOS = 500000000L;
    private static final float NORMAL_QUANTIZE = 1024.0F;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static long cachedSequence = -1L;
    private static long cachedFitRevision = -1L;
    private static Model cachedModel;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static long lastLoggedSequence = -1L;
    private static long lastFailedSequence = -1L;
    private static int cachedUniqueVertices;
    private static int cachedTexturedFaces;

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

        LinkTextureRegistry.prepare(renderer, frame);

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
                        + " uniqueVerts=" + cachedUniqueVertices
                        + " texturedFaces=" + cachedTexturedFaces
                        + " anim=" + frame.animId
                        + " action=" + frame.action
                        + " scale=" + fit.scale
                        + " fit=" + (fit.forcedScale ? "forced" : fit.autoFit ? "830-auto" : "fallback")
                        + " material=oot-uv-texture-v2");
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
        cachedUniqueVertices = 0;
        cachedTexturedFaces = 0;
        lastRenderedCycle = Integer.MIN_VALUE;
        lastLoggedSequence = -1L;
        lastFailedSequence = -1L;
        LinkCharacterFit.resetSession();
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
        int triangles = frame.triangleCount;
        int maxVertices = triangles * 3;
        if (triangles <= 0 || maxVertices > MAX_MATRIX_VERTICES) {
            return null;
        }

        Class159 raw = new Class159(maxVertices, triangles, 0);
        raw.anInt1778 = triangles;
        raw.anIntArray1774 = new int[maxVertices];
        raw.aFloatArray1771 = new float[maxVertices];
        raw.aFloatArray1784 = new float[maxVertices];
        raw.uvCoordVertexA = new byte[triangles];
        raw.uvCoordVertexB = new byte[triangles];
        raw.uvCoordVertexC = new byte[triangles];

        Arrays.fill(raw.anIntArray1813, -1);
        Arrays.fill(raw.anIntArray1780, -1);
        Arrays.fill(raw.faceTextures, (short) -1);
        Arrays.fill(raw.faceTextureIndexes, (short) -1);

        Map<VertexKey, Integer> sharedVertices = new HashMap<VertexKey, Integer>(maxVertices * 2);
        int uniqueVertices = 0;
        int texturedFaces = 0;

        for (int triangle = 0; triangle < triangles; triangle++) {
            int positionBase = triangle * 9;
            int colorBase = triangle * 9;
            int uvBase = triangle * 6;
            int ootTexture = frame.triangleTextures[triangle];
            int materialId = ootTexture == 0xffff
                    ? -1
                    : LinkTextureRegistry.materialIdFor(ootTexture);
            int[] faceVertex = new int[3];
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;

            for (int vertex = 0; vertex < 3; vertex++) {
                int p = positionBase + vertex * 3;
                int c = colorBase + vertex * 3;
                int uv = uvBase + vertex * 2;

                int x = Math.round((frame.positions[p] - frame.x) * fit.scale);
                int y = Math.round(fit.toMatrixY(frame.positions[p + 1], frame.y));
                int z = Math.round((frame.positions[p + 2] - frame.z) * fit.scale);
                int nx = quantizeNormal(frame.normals[p]);
                int ny = quantizeNormal(-frame.normals[p + 1]);
                int nz = quantizeNormal(frame.normals[p + 2]);
                float u = finiteOrZero(frame.uvs[uv]);
                float v = finiteOrZero(frame.uvs[uv + 1]);

                VertexKey key = new VertexKey(
                        x, y, z, nx, ny, nz,
                        Float.floatToIntBits(u), Float.floatToIntBits(v),
                        materialId);
                Integer existing = sharedVertices.get(key);
                int rawVertex;
                if (existing != null) {
                    rawVertex = existing.intValue();
                } else {
                    rawVertex = uniqueVertices++;
                    if (rawVertex >= MAX_MATRIX_VERTICES) {
                        return null;
                    }
                    sharedVertices.put(key, Integer.valueOf(rawVertex));
                    raw.anIntArray1782[rawVertex] = x;
                    raw.anIntArray1777[rawVertex] = y;
                    raw.anIntArray1797[rawVertex] = z;
                    raw.anIntArray1774[rawVertex] = rawVertex;
                    raw.aFloatArray1771[rawVertex] = u;
                    raw.aFloatArray1784[rawVertex] = v;
                }
                faceVertex[vertex] = rawVertex;

                sumR += unitColor(frame.colors[c]);
                sumG += unitColor(frame.colors[c + 1]);
                sumB += unitColor(frame.colors[c + 2]);
            }

            // Negating model Y mirrors one axis; swap B/C to preserve winding.
            raw.aShortArray1786[triangle] = (short) faceVertex[0];
            raw.aShortArray1787[triangle] = (short) faceVertex[2];
            raw.aShortArray1789[triangle] = (short) faceVertex[1];
            raw.faceColours[triangle] = rgbToRsHsl(
                    sumR / 3, sumG / 3, sumB / 3);

            if (materialId >= 0) {
                raw.faceTextures[triangle] = (short) materialId;
                raw.faceTextureIndexes[triangle] = (short) DIRECT_UV_TEXTURE_INDEX;
                texturedFaces++;
            }
        }

        raw.anInt1791 = uniqueVertices;
        raw.anInt1775 = uniqueVertices;
        cachedUniqueVertices = uniqueVertices;
        cachedTexturedFaces = texturedFaces;

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

    private static int quantizeNormal(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * NORMAL_QUANTIZE);
    }

    private static float finiteOrZero(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0.0F : value;
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

    private static final class VertexKey {
        final int x;
        final int y;
        final int z;
        final int nx;
        final int ny;
        final int nz;
        final int uBits;
        final int vBits;
        final int materialId;

        VertexKey(int x, int y, int z,
                int nx, int ny, int nz,
                int uBits, int vBits, int materialId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.uBits = uBits;
            this.vBits = vBits;
            this.materialId = materialId;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + nx;
            result = 31 * result + ny;
            result = 31 * result + nz;
            result = 31 * result + uBits;
            result = 31 * result + vBits;
            result = 31 * result + materialId;
            return result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof VertexKey)) return false;
            VertexKey other = (VertexKey) object;
            return x == other.x && y == other.y && z == other.z
                    && nx == other.nx && ny == other.ny && nz == other.nz
                    && uBits == other.uBits && vBits == other.vBits
                    && materialId == other.materialId;
        }
    }
}
