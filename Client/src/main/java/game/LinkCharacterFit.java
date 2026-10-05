package game;

import java.util.Arrays;

/**
 * Adult-Link <-> Matrix local-player presentation fit.
 *
 * V1 intentionally uses one uniform scale so OoT proportions are preserved. The
 * live 830 player model remains the height reference while Link's native geometry
 * supplies its own standing floor/body dimensions. Width/depth are recorded now
 * as groundwork for later equipment/socket fitting, but V1 does not deform Link
 * independently on X/Y/Z.
 */
final class LinkCharacterFit {

    private static final float FALLBACK_SCALE = 3.0F;
    private static final float TRIM_FRACTION = 0.01F;
    private static final float MIN_NATIVE_HEIGHT = 1.0F;
    private static final float MIN_MATRIX_HEIGHT = 64.0F;
    private static final float MAX_MATRIX_HEIGHT = 2048.0F;
    private static final float MIN_SCALE = 0.25F;
    private static final float MAX_SCALE = 32.0F;

    private static Profile current;
    private static long nextRevision = 1L;
    private static boolean fallbackLogged;

    private LinkCharacterFit() {
    }

    static synchronized Profile resolve(Player player, OotBridgeSession.LinkFrame frame) {
        if (frame == null || frame.positions == null || frame.triangleCount <= 0) {
            return null;
        }

        float matrixHeight = resolveMatrixHeight(player);
        boolean matrixReady = finite(matrixHeight)
                && matrixHeight >= MIN_MATRIX_HEIGHT
                && matrixHeight <= MAX_MATRIX_HEIGHT;

        if (current != null) {
            if (current.autoFit || current.forcedScale) {
                return current;
            }
            if (!matrixReady) {
                return current;
            }
            Profile upgraded = createProfile(
                    current.nativeFloorY,
                    current.nativeTopY,
                    current.nativeWidth,
                    current.nativeDepth,
                    current.referenceRootY,
                    matrixHeight,
                    true);
            if (upgraded != null) {
                current = upgraded;
                logProfile(current);
            }
            return current;
        }

        NativeBounds nativeBounds = measureNative(frame);
        if (nativeBounds == null) {
            return null;
        }

        current = createProfile(
                nativeBounds.floorY,
                nativeBounds.topY,
                nativeBounds.width,
                nativeBounds.depth,
                frame.y,
                matrixHeight,
                matrixReady);
        if (current != null) {
            if (current.autoFit || current.forcedScale) {
                logProfile(current);
            } else if (!fallbackLogged) {
                fallbackLogged = true;
                System.out.println("[OoT Fit] Adult Link waiting for live 830 height;"
                        + " temporary fallback scale=" + current.scale);
            }
        }
        return current;
    }

    static synchronized void resetSession() {
        current = null;
        fallbackLogged = false;
    }

    private static Profile createProfile(
            float nativeFloorY, float nativeTopY,
            float nativeWidth, float nativeDepth,
            float referenceRootY, float matrixHeight,
            boolean matrixReady) {
        float nativeHeight = nativeTopY - nativeFloorY;
        if (!finite(nativeHeight) || nativeHeight < MIN_NATIVE_HEIGHT) {
            return null;
        }

        float forcedScale = propertyPositiveOrNaN("matrix3.oot.modelScale");
        float fitMultiplier = propertyPositive("matrix3.oot.fitMultiplier", 1.0F);
        boolean forced = finite(forcedScale);
        boolean autoFit = !forced && matrixReady;
        float scale;
        if (forced) {
            scale = forcedScale;
        } else if (autoFit) {
            scale = matrixHeight / nativeHeight * fitMultiplier;
        } else {
            scale = FALLBACK_SCALE * fitMultiplier;
        }
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);

        return new Profile(
                nextRevision++,
                scale,
                nativeFloorY,
                nativeTopY,
                nativeHeight,
                nativeWidth,
                nativeDepth,
                referenceRootY,
                matrixReady ? matrixHeight : Float.NaN,
                autoFit,
                forced);
    }

    private static float resolveMatrixHeight(Player player) {
        if (player == null) {
            return Float.NaN;
        }
        try {
            /*
             * verified-static: Player.method10696 stores Model.method1382() in
             * anInt11610. AbstractModel.method1382() is the minimum vertex Y.
             * Normal Matrix player geometry uses negative Y above the local ground
             * origin, so its magnitude is the rendered head-to-ground reference.
             * method8310's decompiler byte parameter is unused by the accessor.
             */
            int minimumY = player.method8310((byte) 0);
            if (minimumY == 0) {
                return Float.NaN;
            }
            return Math.abs((float) minimumY);
        } catch (RuntimeException ex) {
            return Float.NaN;
        }
    }

    private static NativeBounds measureNative(OotBridgeSession.LinkFrame frame) {
        int vertexCount = frame.triangleCount * 3;
        if (vertexCount <= 0 || frame.positions.length < vertexCount * 3) {
            return null;
        }

        float[] localYs = new float[vertexCount];
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = vertex * 3;
            float x = frame.positions[base] - frame.x;
            float y = frame.positions[base + 1] - frame.y;
            float z = frame.positions[base + 2] - frame.z;
            if (!finite(x) || !finite(y) || !finite(z)) {
                return null;
            }
            localYs[vertex] = y;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        Arrays.sort(localYs);
        int trim = (int) Math.floor(vertexCount * TRIM_FRACTION);
        if (trim * 2 >= vertexCount - 6) {
            trim = 0;
        }
        float floorY = localYs[trim];
        float topY = localYs[vertexCount - 1 - trim];
        float height = topY - floorY;
        float width = maxX - minX;
        float depth = maxZ - minZ;
        if (!finite(height) || height < MIN_NATIVE_HEIGHT
                || !finite(width) || width <= 0.0F
                || !finite(depth) || depth <= 0.0F) {
            return null;
        }
        return new NativeBounds(floorY, topY, width, depth);
    }

    private static void logProfile(Profile profile) {
        System.out.println("[OoT Fit] ADULT Link profile"
                + " source=" + (profile.forcedScale ? "forced" : profile.autoFit ? "830-auto" : "fallback")
                + " matrixHeight=" + profile.matrixHeight
                + " nativeH/W/D=" + profile.nativeHeight
                + "/" + profile.nativeWidth
                + "/" + profile.nativeDepth
                + " floorLocalY=" + profile.nativeFloorY
                + " scale=" + profile.scale
                + " fitMultiplier=" + propertyPositive("matrix3.oot.fitMultiplier", 1.0F));
    }

    private static float propertyPositiveOrNaN(String key) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return Float.NaN;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            return finite(value) && value > 0.0F ? value : Float.NaN;
        } catch (NumberFormatException ex) {
            return Float.NaN;
        }
    }

    private static float propertyPositive(String key, float fallback) {
        float value = propertyPositiveOrNaN(key);
        return finite(value) ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static final class Profile {
        final long revision;
        final float scale;
        final float nativeFloorY;
        final float nativeTopY;
        final float nativeHeight;
        final float nativeWidth;
        final float nativeDepth;
        final float referenceRootY;
        final float matrixHeight;
        final boolean autoFit;
        final boolean forcedScale;

        Profile(long revision, float scale,
                float nativeFloorY, float nativeTopY, float nativeHeight,
                float nativeWidth, float nativeDepth,
                float referenceRootY, float matrixHeight,
                boolean autoFit, boolean forcedScale) {
            this.revision = revision;
            this.scale = scale;
            this.nativeFloorY = nativeFloorY;
            this.nativeTopY = nativeTopY;
            this.nativeHeight = nativeHeight;
            this.nativeWidth = nativeWidth;
            this.nativeDepth = nativeDepth;
            this.referenceRootY = referenceRootY;
            this.matrixHeight = matrixHeight;
            this.autoFit = autoFit;
            this.forcedScale = forcedScale;
        }

        float toMatrixY(float nativeWorldY, float currentRootY) {
            float localBodyY = (nativeWorldY - currentRootY) - nativeFloorY;
            float rootDeltaY = currentRootY - referenceRootY;
            return -(localBodyY + rootDeltaY) * scale;
        }
    }

    private static final class NativeBounds {
        final float floorY;
        final float topY;
        final float width;
        final float depth;

        NativeBounds(float floorY, float topY, float width, float depth) {
            this.floorY = floorY;
            this.topY = topY;
            this.width = width;
            this.depth = depth;
        }
    }
}
