package game;

import java.util.Arrays;

/**
 * Derives a stable relative Mario head orientation from libsm64's final animated
 * vertex stream.
 *
 * libsm64 does not currently expose a bone matrix through the Matrix bridge, so
 * equipment cannot attach to a named head joint directly. This tracker captures
 * stable top/bottom and side landmark vertex groups from the upper skull inside
 * the already-selected Mario head region, then compares those same physical
 * vertex groups on later frames. The result is a relative 3x3 rotation matrix
 * that follows head yaw, pitch and roll without recreating animation in Java.
 */
final class MarioHeadOrientationTracker {

    private static final float ORIENTATION_HEAD_START_FRACTION = 0.58F;
    private static final float LANDMARK_FRACTION = 0.20F;
    private static final int MIN_LANDMARK_VERTICES = 6;
    private static final float MIN_VECTOR_LENGTH = 0.001F;

    private static int topologyTriangleCount = -1;
    private static int[] orientationVertices;
    private static int[] topVertices;
    private static int[] bottomVertices;
    private static int[] sideLowVertices;
    private static int[] sideHighVertices;
    private static float[] referenceBasis;
    private static float referenceFaceAngle;

    private MarioHeadOrientationTracker() {
    }

    static boolean captureReference(
            Sm64BridgeSession.GeometryFrame frame,
            int[] headVertices) {
        reset();
        if (!isUsable(frame) || headVertices == null
                || headVertices.length < MIN_LANDMARK_VERTICES * 2) {
            return false;
        }

        int totalVertices = frame.triangleCount * 3;
        int broadCount = 0;
        int[] broad = new int[headVertices.length];
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int vertex : headVertices) {
            if (vertex < 0 || vertex >= totalVertices) {
                continue;
            }
            broad[broadCount++] = vertex;
            float y = frame.positions[vertex * 3 + 1] - frame.state.y;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        if (broadCount < MIN_LANDMARK_VERTICES * 2
                || !finite(minY) || !finite(maxY) || maxY <= minY) {
            return false;
        }

        /*
         * The V2 fit region is intentionally broad. Orientation must not use that
         * entire region or shoulders/upper torso can steer the helmet. Capture the
         * stable upper-skull vertices once from the initial upright/reference pose.
         */
        float orientationStartY = minY
                + (maxY - minY) * ORIENTATION_HEAD_START_FRACTION;
        int[] skull = new int[broadCount];
        int skullCount = 0;
        for (int i = 0; i < broadCount; i++) {
            int vertex = broad[i];
            float y = frame.positions[vertex * 3 + 1] - frame.state.y;
            if (y >= orientationStartY) {
                skull[skullCount++] = vertex;
            }
        }
        if (skullCount < MIN_LANDMARK_VERTICES * 2) {
            return false;
        }
        orientationVertices = Arrays.copyOf(skull, skullCount);

        float[] ys = new float[skullCount];
        float[] xs = new float[skullCount];
        float[] zs = new float[skullCount];
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < skullCount; i++) {
            int base = orientationVertices[i] * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            xs[i] = x;
            ys[i] = y;
            zs[i] = z;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float[] sortedY = Arrays.copyOf(ys, skullCount);
        Arrays.sort(sortedY);
        boolean sideUsesX = maxX - minX >= maxZ - minZ;
        float[] sortedSide = Arrays.copyOf(sideUsesX ? xs : zs, skullCount);
        Arrays.sort(sortedSide);

        int lowIndex = clampIndex(
                Math.round((skullCount - 1) * LANDMARK_FRACTION), skullCount);
        int highIndex = clampIndex(
                Math.round((skullCount - 1) * (1.0F - LANDMARK_FRACTION)), skullCount);
        float bottomThreshold = sortedY[lowIndex];
        float topThreshold = sortedY[highIndex];
        float sideLowThreshold = sortedSide[lowIndex];
        float sideHighThreshold = sortedSide[highIndex];

        int[] top = new int[skullCount];
        int[] bottom = new int[skullCount];
        int[] sideLow = new int[skullCount];
        int[] sideHigh = new int[skullCount];
        int topCount = 0;
        int bottomCount = 0;
        int sideLowCount = 0;
        int sideHighCount = 0;

        for (int i = 0; i < skullCount; i++) {
            int vertex = orientationVertices[i];
            if (ys[i] >= topThreshold) {
                top[topCount++] = vertex;
            }
            if (ys[i] <= bottomThreshold) {
                bottom[bottomCount++] = vertex;
            }
            float side = sideUsesX ? xs[i] : zs[i];
            if (side <= sideLowThreshold) {
                sideLow[sideLowCount++] = vertex;
            }
            if (side >= sideHighThreshold) {
                sideHigh[sideHighCount++] = vertex;
            }
        }

        if (topCount < MIN_LANDMARK_VERTICES
                || bottomCount < MIN_LANDMARK_VERTICES
                || sideLowCount < MIN_LANDMARK_VERTICES
                || sideHighCount < MIN_LANDMARK_VERTICES) {
            reset();
            return false;
        }

        topVertices = Arrays.copyOf(top, topCount);
        bottomVertices = Arrays.copyOf(bottom, bottomCount);
        sideLowVertices = Arrays.copyOf(sideLow, sideLowCount);
        sideHighVertices = Arrays.copyOf(sideHigh, sideHighCount);
        topologyTriangleCount = frame.triangleCount;
        referenceFaceAngle = finite(frame.state.faceAngle) ? frame.state.faceAngle : 0.0F;
        referenceBasis = calculateBasis(frame);
        if (referenceBasis == null) {
            reset();
            return false;
        }

        System.out.println("[SM64 Equipment] HEAD orientation landmarks"
                + " skull=" + orientationVertices.length
                + " top=" + topVertices.length
                + " bottom=" + bottomVertices.length
                + " sideLow=" + sideLowVertices.length
                + " sideHigh=" + sideHighVertices.length
                + " sideAxis=" + (sideUsesX ? "X" : "Z")
                + " skullStart=" + ORIENTATION_HEAD_START_FRACTION);
        return true;
    }

    static float[] calculateRotationDelta(Sm64BridgeSession.GeometryFrame frame) {
        if (!isUsable(frame)
                || referenceBasis == null
                || frame.triangleCount != topologyTriangleCount) {
            return null;
        }
        float[] currentBasis = calculateBasis(frame);
        if (currentBasis == null) {
            return null;
        }

        /*
         * Bases are stored row-major with semantic axes as columns:
         * [ side.x up.x forward.x ]
         * [ side.y up.y forward.y ]
         * [ side.z up.z forward.z ]
         *
         * Rdelta = Bcurrent * transpose(Breference). At the capture frame this is
         * identity; later it is the rigid rotation carrying the reference head
         * orientation into the current animated head orientation.
         */
        float[] delta = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float value = 0.0F;
                for (int axis = 0; axis < 3; axis++) {
                    value += currentBasis[row * 3 + axis]
                            * referenceBasis[col * 3 + axis];
                }
                delta[row * 3 + col] = value;
            }
        }
        return isRotationUsable(delta) ? delta : null;
    }

    /** Returns the tracked upper-skull centroid in Matrix-local X/Y/Z units. */
    static float[] calculateHeadCenter(Sm64BridgeSession.GeometryFrame frame) {
        Vec3 center = centroid(frame, orientationVertices);
        return center == null ? null : new float[] { center.x, center.y, center.z };
    }

    static float getReferenceFaceAngle() {
        return referenceFaceAngle;
    }

    static boolean hasReference() {
        return referenceBasis != null;
    }

    static void reset() {
        topologyTriangleCount = -1;
        orientationVertices = null;
        topVertices = null;
        bottomVertices = null;
        sideLowVertices = null;
        sideHighVertices = null;
        referenceBasis = null;
        referenceFaceAngle = 0.0F;
    }

    private static float[] calculateBasis(Sm64BridgeSession.GeometryFrame frame) {
        Vec3 top = centroid(frame, topVertices);
        Vec3 bottom = centroid(frame, bottomVertices);
        Vec3 sideLow = centroid(frame, sideLowVertices);
        Vec3 sideHigh = centroid(frame, sideHighVertices);
        if (top == null || bottom == null || sideLow == null || sideHigh == null) {
            return null;
        }

        Vec3 up = normalize(subtract(top, bottom));
        Vec3 sideRaw = subtract(sideHigh, sideLow);
        if (up == null || sideRaw == null) {
            return null;
        }

        // Gram-Schmidt keeps side perpendicular to the live head-up vector.
        float sideUpDot = dot(sideRaw, up);
        Vec3 side = normalize(new Vec3(
                sideRaw.x - up.x * sideUpDot,
                sideRaw.y - up.y * sideUpDot,
                sideRaw.z - up.z * sideUpDot));
        if (side == null) {
            return null;
        }

        Vec3 forward = normalize(cross(side, up));
        if (forward == null) {
            return null;
        }
        side = normalize(cross(up, forward));
        if (side == null) {
            return null;
        }

        return new float[] {
                side.x, up.x, forward.x,
                side.y, up.y, forward.y,
                side.z, up.z, forward.z
        };
    }

    private static Vec3 centroid(
            Sm64BridgeSession.GeometryFrame frame,
            int[] vertices) {
        if (vertices == null || vertices.length == 0) {
            return null;
        }
        int totalVertices = frame.triangleCount * 3;
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        int count = 0;
        for (int vertex : vertices) {
            if (vertex < 0 || vertex >= totalVertices) {
                return null;
            }
            int base = vertex * 3;
            x += frame.positions[base] - frame.state.x;
            // Matrix's presentation coordinate has scene-Y inverted from libsm64.
            y -= frame.positions[base + 1] - frame.state.y;
            z += frame.positions[base + 2] - frame.state.z;
            count++;
        }
        if (count == 0) {
            return null;
        }
        float inverse = 1.0F / count;
        return new Vec3(x * inverse, y * inverse, z * inverse);
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return a == null || b == null
                ? null
                : new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    private static Vec3 normalize(Vec3 value) {
        if (value == null) {
            return null;
        }
        float length = (float) Math.sqrt(
                value.x * value.x + value.y * value.y + value.z * value.z);
        if (!finite(length) || length < MIN_VECTOR_LENGTH) {
            return null;
        }
        float inverse = 1.0F / length;
        return new Vec3(value.x * inverse, value.y * inverse, value.z * inverse);
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }

    private static float dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static boolean isRotationUsable(float[] matrix) {
        if (matrix == null || matrix.length != 9) {
            return false;
        }
        for (float value : matrix) {
            if (!finite(value)) {
                return false;
            }
        }
        float determinant =
                matrix[0] * (matrix[4] * matrix[8] - matrix[5] * matrix[7])
                - matrix[1] * (matrix[3] * matrix[8] - matrix[5] * matrix[6])
                + matrix[2] * (matrix[3] * matrix[7] - matrix[4] * matrix[6]);
        return finite(determinant) && determinant > 0.5F && determinant < 1.5F;
    }

    private static boolean isUsable(Sm64BridgeSession.GeometryFrame frame) {
        return frame != null && frame.state != null && frame.triangleCount > 0
                && frame.positions != null
                && frame.positions.length >= frame.triangleCount * 9;
    }

    private static int clampIndex(int index, int count) {
        if (index < 0) {
            return 0;
        }
        if (index >= count) {
            return count - 1;
        }
        return index;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static final class Vec3 {
        final float x;
        final float y;
        final float z;

        Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
