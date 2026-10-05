package game;

/**
 * Adult OoT Link equipment sockets derived from liboot's real world-space
 * skeleton pose. This is intentionally independent from the Mario equipment
 * workbench/geometry-envelope path.
 *
 * liboot documents skeleton entries as PLAYER_LIMB_* - 1. The current bridge
 * streams the exact parent table and world-space joint positions every native
 * frame; this class converts those joints into Matrix local-player space using
 * the same LinkCharacterFit transform as the body renderer.
 */
final class LinkSkeletonSockets {

    /* PLAYER_LIMB_* - 1 from OoT player.h. */
    static final int JOINT_ROOT = 0;
    static final int JOINT_WAIST = 1;
    static final int JOINT_LOWER = 2;
    static final int JOINT_R_THIGH = 3;
    static final int JOINT_R_SHIN = 4;
    static final int JOINT_R_FOOT = 5;
    static final int JOINT_L_THIGH = 6;
    static final int JOINT_L_SHIN = 7;
    static final int JOINT_L_FOOT = 8;
    static final int JOINT_UPPER = 9;
    static final int JOINT_HEAD = 10;
    static final int JOINT_HAT = 11;
    static final int JOINT_COLLAR = 12;
    static final int JOINT_L_SHOULDER = 13;
    static final int JOINT_L_FOREARM = 14;
    static final int JOINT_L_HAND = 15;
    static final int JOINT_R_SHOULDER = 16;
    static final int JOINT_R_FOREARM = 17;
    static final int JOINT_R_HAND = 18;
    static final int JOINT_SHEATH = 19;
    static final int JOINT_TORSO = 20;

    private static final float EPSILON = 0.0001F;
    private static final float DEFAULT_HEAD_HAT_BLEND = 0.35F;
    private static final float HEAD_HAT_BLEND = resolveHeadHatBlend();

    private LinkSkeletonSockets() {
    }

    static SocketPose resolveHead(OotBridgeSession.LinkFrame frame,
            LinkCharacterFit.Profile fit) {
        if (!hasRequiredHeadJoints(frame) || fit == null) {
            return null;
        }

        Vec3 head = joint(frame, fit, JOINT_HEAD);
        Vec3 hat = joint(frame, fit, JOINT_HAT);
        Vec3 collar = joint(frame, fit, JOINT_COLLAR);
        Vec3 leftShoulder = joint(frame, fit, JOINT_L_SHOULDER);
        Vec3 rightShoulder = joint(frame, fit, JOINT_R_SHOULDER);
        if (head == null || hat == null || collar == null
                || leftShoulder == null || rightShoulder == null) {
            return null;
        }

        /*
         * Anchor follows the real animated head/hat chain, not a geometry
         * envelope. A small blend toward the hat joint puts the centered 830
         * worn model around Link's skull rather than directly on the neck joint.
         */
        Vec3 anchor = add(head, scale(subtract(hat, head), HEAD_HAT_BLEND));

        Vec3 up = normalize(subtract(head, collar));
        if (up == null) {
            up = normalize(subtract(head, joint(frame, fit, JOINT_UPPER)));
        }
        if (up == null) {
            return null;
        }

        /*
         * The head->hat child direction carries real head-pose motion. Remove
         * its vertical component to obtain the local head depth direction. If
         * the current animation collapses that projection, fall back to the
         * shoulder plane for a stable facing basis.
         */
        Vec3 headToHat = subtract(hat, head);
        Vec3 forward = reject(headToHat, up);
        forward = normalize(forward);
        if (forward == null) {
            Vec3 shoulderRight = normalize(subtract(rightShoulder, leftShoulder));
            if (shoulderRight == null) {
                return null;
            }
            forward = normalize(cross(shoulderRight, up));
        }
        if (forward == null) {
            return null;
        }

        /* Resolve the head-axis sign against OoT's actor yaw (0 = +Z). */
        float yaw = (float) (frame.faceAngle * Math.PI / 32768.0);
        Vec3 expectedForward = new Vec3(
                (float) Math.sin(yaw), 0.0F, (float) Math.cos(yaw));
        if (dot(forward, expectedForward) < 0.0F) {
            forward = scale(forward, -1.0F);
        }

        /* OoT semantic right at yaw 0 points -X. */
        Vec3 right = normalize(cross(up, forward));
        if (right == null) {
            return null;
        }
        forward = normalize(cross(right, up));
        if (forward == null) {
            return null;
        }

        /*
         * Matrix worn models use +Y downward in local model space. Columns are
         * therefore local X=right, local Y=down, local Z=forward.
         */
        Vec3 down = scale(up, -1.0F);
        float[] rotation = new float[] {
                right.x, down.x, forward.x,
                right.y, down.y, forward.y,
                right.z, down.z, forward.z
        };
        if (!finite(rotation)) {
            return null;
        }
        return new SocketPose(anchor.x, anchor.y, anchor.z, rotation,
                JOINT_HEAD, frame.sequence);
    }

    private static boolean hasRequiredHeadJoints(OotBridgeSession.LinkFrame frame) {
        return frame != null
                && frame.hasJoint(JOINT_HEAD)
                && frame.hasJoint(JOINT_HAT)
                && frame.hasJoint(JOINT_COLLAR)
                && frame.hasJoint(JOINT_L_SHOULDER)
                && frame.hasJoint(JOINT_R_SHOULDER);
    }

    private static Vec3 joint(OotBridgeSession.LinkFrame frame,
            LinkCharacterFit.Profile fit, int joint) {
        if (frame == null || fit == null || !frame.hasJoint(joint)) {
            return null;
        }
        float x = (frame.jointX(joint) - frame.x) * fit.scale;
        float y = fit.toMatrixY(frame.jointY(joint), frame.y);
        float z = (frame.jointZ(joint) - frame.z) * fit.scale;
        if (!finite(x) || !finite(y) || !finite(z)) {
            return null;
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return a == null || b == null ? null
                : new Vec3(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return a == null || b == null ? null
                : new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    private static Vec3 scale(Vec3 value, float amount) {
        return value == null ? null
                : new Vec3(value.x * amount, value.y * amount, value.z * amount);
    }

    private static float dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return null;
        }
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }

    private static Vec3 reject(Vec3 value, Vec3 axis) {
        if (value == null || axis == null) {
            return null;
        }
        return subtract(value, scale(axis, dot(value, axis)));
    }

    private static Vec3 normalize(Vec3 value) {
        if (value == null) {
            return null;
        }
        float length = (float) Math.sqrt(
                value.x * value.x + value.y * value.y + value.z * value.z);
        if (!finite(length) || length <= EPSILON) {
            return null;
        }
        return new Vec3(value.x / length, value.y / length, value.z / length);
    }

    private static float resolveHeadHatBlend() {
        String raw = System.getProperty("matrix3.oot.headSocketHatBlend");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_HEAD_HAT_BLEND;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            if (finite(value) && value >= -1.0F && value <= 2.0F) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        System.out.println("[OoT Socket] Invalid matrix3.oot.headSocketHatBlend='"
                + raw + "'; using " + DEFAULT_HEAD_HAT_BLEND);
        return DEFAULT_HEAD_HAT_BLEND;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean finite(float[] values) {
        if (values == null) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            if (!finite(values[i])) {
                return false;
            }
        }
        return true;
    }

    static final class SocketPose {
        final float x;
        final float y;
        final float z;
        final float[] rotation;
        final int joint;
        final long frameSequence;

        SocketPose(float x, float y, float z, float[] rotation,
                int joint, long frameSequence) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
            this.joint = joint;
            this.frameSequence = frameSequence;
        }
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
