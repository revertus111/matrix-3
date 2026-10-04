package game;

/**
 * Stable semantic metadata exported by the patched libsm64 geometry adapter.
 *
 * Part ids are protocol data, not renderer ownership. This helper centralizes the
 * ids and the one head-local face reference consumed by helmet fitting/coverage so
 * the adapter and auto-fit cannot silently measure different animation poses.
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
            cachedTriangleCount = frame.triangleCount;
            cachedPartHash = partHash;
            cachedReference = null;
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
         */
        float modelScale = resolveModelScale();
        float width = (maxZ - minZ) * modelScale;
        float height = (maxX - minX) * modelScale;
        float depth = (maxY - minY) * modelScale;
        if (width <= 0.0F || height <= 0.0F || depth <= 0.0F
                || !finite(width) || !finite(height) || !finite(depth)) {
            cachedReference = null;
        } else {
            cachedReference = new Reference(
                    width, height, depth,
                    (minX + maxX) * 0.5F,
                    (minY + maxY) * 0.5F,
                    (minZ + maxZ) * 0.5F,
                    faceTriangles);
        }
        cachedTriangleCount = frame.triangleCount;
        cachedPartHash = partHash;
        return cachedReference;
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
        final int faceTriangles;

        Reference(float width, float height, float depth,
                float localCenterX, float localCenterY, float localCenterZ,
                int faceTriangles) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.localCenterX = localCenterX;
            this.localCenterY = localCenterY;
            this.localCenterZ = localCenterZ;
            this.faceTriangles = faceTriangles;
        }
    }
}
