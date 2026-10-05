package game;

import java.util.Arrays;

/**
 * Stable semantic metadata exported by the patched libsm64 geometry adapter.
 *
 * Part ids are protocol data, not renderer ownership. This helper centralizes the
 * ids and the one semantic FACE reference consumed by helmet fitting/coverage so
 * the adapter and auto-fit cannot silently measure different animation poses.
 *
 * Important unit rule: libsm64 localPosition values are source display-list units,
 * not final posed Mario units. The native head graph applies a transform before
 * publishing frame.positions. We therefore recover that uniform local->posed scale
 * from corresponding FACE triangle edge lengths before converting to Matrix units.
 */
final class MarioSemanticGeometry {

    static final int PART_UNKNOWN = 0;
    static final int PART_FACE = 1;
    static final int PART_EYES = 2;
    static final int PART_MUSTACHE = 3;
    static final int PART_CAP = 4;
    static final int PART_HAIR_SIDEBURN = 5;
    static final int PART_HAIR_BACK = 6;

    private static final float DEFAULT_MODEL_SCALE = 2.0F;
    private static final int MIN_FACE_TRIANGLES = 8;
    private static final float MIN_EDGE_LENGTH = 0.001F;
    private static final float MIN_POSED_SCALE = 0.001F;
    private static final float MAX_POSED_SCALE = 10.0F;

    private static int cachedTriangleCount = -1;
    private static int cachedPartHash;
    private static Reference cachedReference;

    private MarioSemanticGeometry() {
    }

    static boolean isAvailable(Sm64BridgeSession.GeometryFrame frame) {
        return frame != null && frame.hasSemanticGeometry();
    }

    static Reference getReference(Sm64BridgeSession.GeometryFrame frame) {
        if (!isAvailable(frame)) {
            return null;
        }
        int partHash = partHash(frame.partIds, frame.triangleCount);
        if (cachedReference != null
                && cachedTriangleCount == frame.triangleCount
                && cachedPartHash == partHash) {
            return cachedReference;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int faceTriangles = 0;

        for (int triangle = 0; triangle < frame.triangleCount; triangle++) {
            if ((frame.partIds[triangle] & 0xff) != PART_FACE) {
                continue;
            }
            faceTriangles++;
            int base = triangle * 9;
            for (int vertex = 0; vertex < 3; vertex++) {
                int offset = base + vertex * 3;
                float x = frame.localPositions[offset];
                float y = frame.localPositions[offset + 1];
                float z = frame.localPositions[offset + 2];
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }
        }

        if (faceTriangles < MIN_FACE_TRIANGLES
                || !finite(minX) || !finite(maxX)
                || !finite(minY) || !finite(maxY)
                || !finite(minZ) || !finite(maxZ)) {
            cacheFailure(frame.triangleCount, partHash);
            return null;
        }

        float posedScale = deriveLocalToPosedScale(frame, faceTriangles);
        if (!finite(posedScale)
                || posedScale < MIN_POSED_SCALE
                || posedScale > MAX_POSED_SCALE) {
            cacheFailure(frame.triangleCount, partHash);
            return null;
        }

        /*
         * verified-static from the pinned Mario source mesh:
         *   local Z = left/right across the face,
         *   local +Y = face-out/front (eyes/moustache normals and nose projection),
         *   local X = the remaining head-local vertical axis.
         *
         * The nose remains inside FACE and is intentionally protected. Helmet
         * baseline width therefore uses left/right Z; nose depth never inflates it.
         *
         * localPosition is pre-head-transform geometry. posedScale converts those
         * source units to libsm64 final geometry units; modelScale then converts the
         * same geometry to the Matrix Mario model scale used by the renderer.
         */
        float matrixScale = posedScale * resolveModelScale();
        float width = (maxZ - minZ) * matrixScale;
        float height = (maxX - minX) * matrixScale;
        float depth = (maxY - minY) * matrixScale;
        if (width <= 0.0F || height <= 0.0F || depth <= 0.0F
                || !finite(width) || !finite(height) || !finite(depth)) {
            cachedReference = null;
        } else {
            cachedReference = new Reference(
                    width, height, depth,
                    (minX + maxX) * 0.5F,
                    (minY + maxY) * 0.5F,
                    (minZ + maxZ) * 0.5F,
                    posedScale,
                    faceTriangles);
        }
        cachedTriangleCount = frame.triangleCount;
        cachedPartHash = partHash;
        return cachedReference;
    }

    /**
     * Recovers the native head-node scale without depending on pose orientation.
     * Translation cancels in edge lengths and rotation preserves length, leaving
     * only the uniform scale between display-list-local and final posed geometry.
     * Median edge ratio rejects degenerate or unusual source triangles.
     */
    private static float deriveLocalToPosedScale(
            Sm64BridgeSession.GeometryFrame frame,
            int faceTriangles) {
        float[] ratios = new float[Math.max(1, faceTriangles * 3)];
        int count = 0;

        for (int triangle = 0; triangle < frame.triangleCount; triangle++) {
            if ((frame.partIds[triangle] & 0xff) != PART_FACE) {
                continue;
            }
            int base = triangle * 9;
            count = addEdgeRatio(frame, base, 0, 1, ratios, count);
            count = addEdgeRatio(frame, base, 1, 2, ratios, count);
            count = addEdgeRatio(frame, base, 2, 0, ratios, count);
        }

        if (count < MIN_FACE_TRIANGLES) {
            return Float.NaN;
        }
        Arrays.sort(ratios, 0, count);
        int middle = count / 2;
        return (count & 1) == 0
                ? (ratios[middle - 1] + ratios[middle]) * 0.5F
                : ratios[middle];
    }

    private static int addEdgeRatio(
            Sm64BridgeSession.GeometryFrame frame,
            int base,
            int vertexA,
            int vertexB,
            float[] ratios,
            int count) {
        int localA = base + vertexA * 3;
        int localB = base + vertexB * 3;
        float localLength = distance(
                frame.localPositions[localA],
                frame.localPositions[localA + 1],
                frame.localPositions[localA + 2],
                frame.localPositions[localB],
                frame.localPositions[localB + 1],
                frame.localPositions[localB + 2]);
        if (!finite(localLength) || localLength <= MIN_EDGE_LENGTH) {
            return count;
        }

        int posedA = base + vertexA * 3;
        int posedB = base + vertexB * 3;
        float posedLength = distance(
                frame.positions[posedA],
                frame.positions[posedA + 1],
                frame.positions[posedA + 2],
                frame.positions[posedB],
                frame.positions[posedB + 1],
                frame.positions[posedB + 2]);
        if (!finite(posedLength) || posedLength <= MIN_EDGE_LENGTH) {
            return count;
        }

        float ratio = posedLength / localLength;
        if (!finite(ratio) || ratio < MIN_POSED_SCALE || ratio > MAX_POSED_SCALE
                || count >= ratios.length) {
            return count;
        }
        ratios[count] = ratio;
        return count + 1;
    }

    private static float distance(
            float ax, float ay, float az,
            float bx, float by, float bz) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void cacheFailure(int triangleCount, int partHash) {
        cachedTriangleCount = triangleCount;
        cachedPartHash = partHash;
        cachedReference = null;
    }

    static boolean isProtectedFacePart(int partId) {
        return partId == PART_FACE || partId == PART_EYES || partId == PART_MUSTACHE;
    }

    static boolean isFullHelmSafeHiddenPart(int partId) {
        return partId == PART_CAP
                || partId == PART_HAIR_SIDEBURN
                || partId == PART_HAIR_BACK;
    }

    static String partName(int partId) {
        switch (partId) {
            case PART_FACE:
                return "FACE (protected)";
            case PART_EYES:
                return "EYES (protected)";
            case PART_MUSTACHE:
                return "MOUSTACHE (protected)";
            case PART_CAP:
                return "CAP";
            case PART_HAIR_SIDEBURN:
                return "HAIR_SIDEBURN";
            case PART_HAIR_BACK:
                return "HAIR_BACK";
            default:
                return "UNKNOWN";
        }
    }

    static void resetReference() {
        cachedTriangleCount = -1;
        cachedPartHash = 0;
        cachedReference = null;
    }

    private static int partHash(byte[] partIds, int count) {
        int hash = 1;
        int limit = Math.min(count, partIds == null ? 0 : partIds.length);
        for (int i = 0; i < limit; i++) {
            hash = 31 * hash + (partIds[i] & 0xff);
        }
        return hash;
    }

    private static float resolveModelScale() {
        String raw = System.getProperty("matrix3.sm64.modelScale");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                float value = Float.parseFloat(raw.trim());
                if (value > 0.0F && finite(value)) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to established Mario model scale.
            }
        }
        return DEFAULT_MODEL_SCALE;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static final class Reference {
        final float width;
        final float height;
        final float depth;
        final float localCenterX;
        final float localCenterY;
        final float localCenterZ;
        final float localToPosedScale;
        final int faceTriangles;

        Reference(float width, float height, float depth,
                float localCenterX, float localCenterY, float localCenterZ,
                float localToPosedScale,
                int faceTriangles) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.localCenterX = localCenterX;
            this.localCenterY = localCenterY;
            this.localCenterZ = localCenterZ;
            this.localToPosedScale = localToPosedScale;
            this.faceTriangles = faceTriangles;
        }
    }
}
