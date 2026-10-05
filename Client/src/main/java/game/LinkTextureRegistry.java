package game;

import java.util.Arrays;

/**
 * Runtime-only material adapter for liboot Link textures.
 *
 * OoT RGBA pixels remain local and are uploaded directly into the active Matrix
 * renderer. Nothing is written to the revision-830 cache or repository. A
 * synthetic material block is appended to the live MapSize material table and
 * backed by Class360's normal GPU texture cache.
 */
final class LinkTextureRegistry {

    private static final int MAX_OOT_TEXTURES = 1024;
    private static final int MAX_POSITIVE_MATERIAL_ID = 32766;
    private static final int MATERIAL_TEXTURE_ENCODE_MULTIPLIER = -1658409611;

    private static Class106_Sub3 activeRenderer;
    private static int materialBase = -1;
    private static final int[] uploadedRevision = new int[MAX_OOT_TEXTURES];
    private static final int[] uploadedSize = new int[MAX_OOT_TEXTURES];
    private static final boolean[] available = new boolean[MAX_OOT_TEXTURES];
    private static boolean loggedReady;
    private static boolean loggedUnavailable;

    static {
        Arrays.fill(uploadedRevision, Integer.MIN_VALUE);
    }

    private LinkTextureRegistry() {
    }

    static boolean prepare(Class106 renderer, OotBridgeSession.LinkFrame frame) {
        if (!(renderer instanceof Class106_Sub3) || frame == null) {
            logUnavailable("active renderer does not expose the Matrix texture cache");
            return false;
        }
        Class106_Sub3 nativeRenderer = (Class106_Sub3) renderer;
        if (activeRenderer != nativeRenderer) {
            activeRenderer = nativeRenderer;
            materialBase = -1;
            Arrays.fill(uploadedRevision, Integer.MIN_VALUE);
            Arrays.fill(uploadedSize, 0);
            Arrays.fill(available, false);
            loggedReady = false;
            loggedUnavailable = false;
        }
        if (!ensureMaterialBlock(nativeRenderer)) {
            return false;
        }

        OotBridgeSession.TextureUpdate[] textures = frame.textureUpdates;
        for (int i = 0; i < textures.length; i++) {
            OotBridgeSession.TextureUpdate texture = textures[i];
            if (texture == null || texture.index < 0 || texture.index >= MAX_OOT_TEXTURES) {
                continue;
            }
            if (!available[texture.index]
                    || uploadedRevision[texture.index] != texture.revision) {
                install(nativeRenderer, texture);
            }
        }

        if (!loggedReady) {
            int ready = 0;
            for (int i = 0; i < available.length; i++) {
                if (available[i]) ready++;
            }
            if (ready > 0) {
                loggedReady = true;
                System.out.println("[OoT Material] Runtime texture bridge ACTIVE"
                        + " materials=" + ready
                        + " materialBase=" + materialBase
                        + " source=local-ROM-RGBA");
            }
        }
        return true;
    }

    static int materialIdFor(int ootTextureIndex) {
        if (materialBase < 0
                || ootTextureIndex < 0 || ootTextureIndex >= MAX_OOT_TEXTURES
                || !available[ootTextureIndex]) {
            return -1;
        }
        return materialBase + ootTextureIndex;
    }

    private static boolean ensureMaterialBlock(Class106_Sub3 renderer) {
        if (materialBase >= 0) {
            return true;
        }
        MapSize mapSize = renderer.aClass94_1396;
        if (mapSize == null || mapSize.textures == null || renderer.aClass360_10487 == null) {
            logUnavailable("Matrix material/texture provider is unavailable");
            return false;
        }
        int existing = mapSize.textures.length;
        if (existing + MAX_OOT_TEXTURES - 1 > MAX_POSITIVE_MATERIAL_ID) {
            logUnavailable("not enough positive short material IDs for OoT runtime textures"
                    + " existing=" + existing);
            return false;
        }
        materialBase = existing;
        mapSize.textures = Arrays.copyOf(mapSize.textures, existing + MAX_OOT_TEXTURES);
        return true;
    }

    private static void install(Class106_Sub3 renderer,
            OotBridgeSession.TextureUpdate source) {
        try {
            int textureSize = chooseTextureSize(source.width, source.height);
            int[] argb = resampleArgb(source, textureSize);
            Interface43 gpuTexture = renderer.method9711(
                    textureSize, textureSize, true, argb);
            if (gpuTexture == null) {
                available[source.index] = false;
                return;
            }

            /*
             * Matrix exposes repeat as a boolean. OoT mirror wrapping has no
             * equivalent at this seam yet, so mirror intentionally degrades to
             * repeat; clamp remains clamp.
             */
            gpuTexture.method253(source.wrapS != 2, source.wrapT != 2);

            int materialId = materialBase + source.index;
            MaterialInformation material = new MaterialInformation();
            material.textureId = materialId * MATERIAL_TEXTURE_ENCODE_MULTIPLIER;
            material.anInt1338 = encodedMaterialSize(textureSize);
            material.aByte1344 = (byte) (source.wrapS == 2 ? 0 : 1);
            material.aByte1345 = (byte) (source.wrapT == 2 ? 0 : 1);
            material.aClass511_1342 = hasTransparency(source.rgba)
                    ? Class511.aClass511_5697
                    : Class511.aClass511_5695;
            material.aBool1341 = false;
            material.aBool1350 = false;
            material.aBool1363 = false;

            Class360 textureManager = renderer.aClass360_10487;
            if (uploadedSize[source.index] > 0) {
                textureManager.aClass127_4430.method2227(
                        cacheKey(uploadedSize[source.index], materialId));
            }
            renderer.aClass94_1396.textures[materialId] = material;
            textureManager.aClass127_4430.method2230(
                    gpuTexture,
                    cacheKey(textureSize, materialId),
                    textureSize * textureSize,
                    528495706);

            uploadedRevision[source.index] = source.revision;
            uploadedSize[source.index] = textureSize;
            available[source.index] = true;
        } catch (RuntimeException ex) {
            available[source.index] = false;
            System.err.println("[OoT Material] texture upload failed index="
                    + source.index + ": "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static long cacheKey(int size, int materialId) {
        return (long) (size << 16 | materialId);
    }

    private static int chooseTextureSize(int width, int height) {
        int wanted = Math.max(64, Math.max(width, height));
        int size = 64;
        while (size < wanted && size < 1024) {
            size <<= 1;
        }
        return size > 1024 ? 1024 : size;
    }

    private static int encodedMaterialSize(int size) {
        switch (size) {
            case 64: return 996222400;
            case 128: return 1992444800;
            case 256: return -310077696;
            case 512: return -620155392;
            case 1024: return -1240310784;
            default: throw new IllegalArgumentException("unsupported Matrix texture size: " + size);
        }
    }

    private static int[] resampleArgb(OotBridgeSession.TextureUpdate source,
            int targetSize) {
        int[] pixels = new int[targetSize * targetSize];
        for (int y = 0; y < targetSize; y++) {
            int sourceY = y * source.height / targetSize;
            for (int x = 0; x < targetSize; x++) {
                int sourceX = x * source.width / targetSize;
                int offset = (sourceY * source.width + sourceX) * 4;
                int r = source.rgba[offset] & 0xff;
                int g = source.rgba[offset + 1] & 0xff;
                int b = source.rgba[offset + 2] & 0xff;
                int a = source.rgba[offset + 3] & 0xff;
                pixels[y * targetSize + x] =
                        a << 24 | r << 16 | g << 8 | b;
            }
        }
        return pixels;
    }

    private static boolean hasTransparency(byte[] rgba) {
        for (int i = 3; i < rgba.length; i += 4) {
            if ((rgba[i] & 0xff) != 255) {
                return true;
            }
        }
        return false;
    }

    private static void logUnavailable(String reason) {
        if (!loggedUnavailable) {
            loggedUnavailable = true;
            System.out.println("[OoT Material] texture bridge unavailable: " + reason
                    + "; falling back to vertex colour");
        }
    }
}
