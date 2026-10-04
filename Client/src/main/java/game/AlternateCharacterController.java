package game;

/**
 * Shared client-side controller foundation for non-RuneScape character drivers.
 *
 * Matrix remains the host camera/input/world architecture. Character-specific
 * drivers consume the same camera-relative input and combat bridge instead of
 * each game implementing its own keyboard/camera rules.
 */
public final class AlternateCharacterController {

    public enum CharacterId {
        MARIO
    }

    public enum CombatStyle {
        MELEE,
        RANGED,
        MAGIC
    }

    interface CharacterDriver {
        CharacterId getId();
        void tick();
        boolean supportsCombatStyle(CombatStyle style);
    }

    static final class PlanarDirection {
        final float x;
        final float z;

        PlanarDirection(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final PlanarDirection DEFAULT_FORWARD = new PlanarDirection(0.0F, -1.0F);

    private static final CharacterDriver MARIO_DRIVER = new CharacterDriver() {
        @Override
        public CharacterId getId() {
            return CharacterId.MARIO;
        }

        @Override
        public void tick() {
            MarioJumpController.tickMarioDriver();
        }

        @Override
        public boolean supportsCombatStyle(CombatStyle style) {
            return style == CombatStyle.MELEE;
        }
    };

    private AlternateCharacterController() {
    }

    /**
     * Compatibility entry used by the established viewport hook. Future imported
     * characters register/dispatch here rather than adding another viewport tick.
     */
    public static void tick() {
        MARIO_DRIVER.tick();
    }

    static CharacterId getActiveCharacter() {
        return PlayerControllerMode.isMarioMode() ? CharacterId.MARIO : null;
    }

    static boolean supportsCombatStyle(CombatStyle style) {
        CharacterId active = getActiveCharacter();
        return active == CharacterId.MARIO && MARIO_DRIVER.supportsCombatStyle(style);
    }

    /**
     * Returns the actual rendered Matrix camera forward direction on the X/Z
     * ground plane. Detached/free cameras use their real Class411 look vector;
     * vanilla camera modes mirror the yaw domain consumed by Class246.method3359.
     */
    static PlanarDirection getCameraForward() {
        Class411_Sub1 detached = null;
        try {
            if (IncomingPacket.method4113((byte) 0) && Class24.aClass411_Sub1_158 != null) {
                detached = Class24.aClass411_Sub1_158;
            } else if (Class18.anInt143 * 625220759 == 1
                    && Class133_Sub1.aClass411_Sub1_9827 != null) {
                detached = Class133_Sub1.aClass411_Sub1_9827;
            }
        } catch (RuntimeException ignored) {
            // Fall through to vanilla yaw/fixed-safe fallback.
        }

        PlanarDirection direction = detached == null ? null : getDetachedCameraForward(detached);
        if (direction != null) {
            return direction;
        }

        try {
            int yawUnits = (int) client.aFloat8678;
            if (Class18.anInt143 * 625220759 == 4) {
                // Match the yaw submitted to Class246.method3359 in the live viewport.
                yawUnits += client.anInt8665 * -706438965;
            }
            yawUnits &= 0x3fff;
            double radians = yawUnits * (Math.PI * 2.0 / 16384.0);

            // Class246.method3359 positions the camera behind this forward vector.
            float x = -(float) Math.sin(radians);
            float z = (float) Math.cos(radians);
            PlanarDirection vanilla = normalize(x, z);
            if (vanilla != null) {
                return vanilla;
            }
        } catch (RuntimeException ignored) {
            // Preserve the previous known-working north reference on uncertainty.
        }
        return DEFAULT_FORWARD;
    }

    private static PlanarDirection getDetachedCameraForward(Class411_Sub1 camera) {
        try {
            Class423_Sub2 positionController =
                    (Class423_Sub2) camera.method4990((byte) -37);
            Class658_Sub2 lookController =
                    (Class658_Sub2) camera.method4991(-589573040);
            Class240 position = positionController.method5159((byte) -54);
            Class240 forwardPoint = lookController.method7736(0);
            return normalize(
                    forwardPoint.aFloat2653 - position.aFloat2653,
                    forwardPoint.aFloat2657 - position.aFloat2657);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static PlanarDirection normalize(float x, float z) {
        float length = (float) Math.sqrt(x * x + z * z);
        if (Float.isNaN(length) || Float.isInfinite(length) || length < 0.001F) {
            return null;
        }
        return new PlanarDirection(x / length, z / length);
    }
}
