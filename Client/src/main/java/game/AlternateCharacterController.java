package game;

/**
 * Shared client-side controller foundation for non-RuneScape character drivers.
 *
 * Matrix remains the host camera/input/world architecture. Character-specific
 * drivers consume the same camera-relative input and combat bridge instead of
 * each game implementing its own keyboard/camera rules.
 */
public final class AlternateCharacterController {

    // UI requests only; each driver applies transitions on the client thread.
    // Free movement is the default for this local development controller.
    private static volatile boolean runeScapeClippingEnabled;

    public static boolean isRuneScapeClippingEnabled() {
        return runeScapeClippingEnabled;
    }

    public static void setRuneScapeClippingEnabled(boolean enabled) {
        runeScapeClippingEnabled = enabled;
    }

    public enum CharacterId {
        MARIO,
        LINK
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

    /**
     * One generic control vocabulary shared by all alternate-character drivers.
     * A driver decides what Jump/Primary/Modifier mean for that character.
     */
    static final class ControlState {
        final float moveX;
        final float moveY;
        final float worldMoveX;
        final float worldMoveZ;
        final boolean jump;
        final boolean primaryAction;
        final boolean modifierAction;
        final PlanarDirection cameraForward;

        ControlState(float moveX, float moveY,
                boolean jump, boolean primaryAction, boolean modifierAction,
                PlanarDirection cameraForward) {
            this.moveX = moveX;
            this.moveY = moveY;
            this.jump = jump;
            this.primaryAction = primaryAction;
            this.modifierAction = modifierAction;
            this.cameraForward = cameraForward;
            // Single screen-to-world owner for every imported character.
            // W is forward/up-screen; D is right, independent of actor facing.
            this.worldMoveX = moveX * cameraForward.z + moveY * cameraForward.x;
            this.worldMoveZ = -moveX * cameraForward.x + moveY * cameraForward.z;
        }
    }

    // Class549_Sub1 normalized-key mappings, verified from anIntArray8901.
    private static final int INTERNAL_W_KEY = 33;
    private static final int INTERNAL_A_KEY = 48;
    private static final int INTERNAL_S_KEY = 49;
    private static final int INTERNAL_D_KEY = 50;
    private static final int INTERNAL_PRIMARY_KEY = 51; // F
    private static final int INTERNAL_MODIFIER_KEY = 81; // Shift
    private static final int INTERNAL_JUMP_KEY = 83; // Space
    private static final float DIAGONAL_STICK_SCALE = 0.70710677F;

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

    private static final CharacterDriver LINK_DRIVER = new CharacterDriver() {
        @Override
        public CharacterId getId() {
            return CharacterId.LINK;
        }

        @Override
        public void tick() {
            LinkController.tickLinkDriver();
        }

        @Override
        public boolean supportsCombatStyle(CombatStyle style) {
            // Phase 1 deliberately does not expose Matrix combat authority yet.
            return false;
        }
    };

    private AlternateCharacterController() {
    }

    /**
     * Compatibility entry used by the established viewport hook. Imported
     * characters dispatch here instead of adding per-game viewport ticks.
     */
    public static void tick() {
        MARIO_DRIVER.tick();
        LINK_DRIVER.tick();
    }

    static CharacterId getActiveCharacter() {
        if (PlayerControllerMode.isMarioMode()) {
            return CharacterId.MARIO;
        }
        if (PlayerControllerMode.isLinkMode()) {
            return CharacterId.LINK;
        }
        return null;
    }

    static boolean supportsCombatStyle(CombatStyle style) {
        CharacterId active = getActiveCharacter();
        if (active == CharacterId.MARIO) {
            return MARIO_DRIVER.supportsCombatStyle(style);
        }
        if (active == CharacterId.LINK) {
            return LINK_DRIVER.supportsCombatStyle(style);
        }
        return false;
    }

    static ControlState sampleControls() {
        if (PlayerControllerMode.isMarioMode() && MarioHelmetCalibrationController.isActive()) {
            return new ControlState(
                    0.0F, 0.0F,
                    false, false, false,
                    getCameraForward());
        }

        float moveX = (rawKeyDown(INTERNAL_D_KEY) ? 1.0F : 0.0F)
                - (rawKeyDown(INTERNAL_A_KEY) ? 1.0F : 0.0F);
        float moveY = (rawKeyDown(INTERNAL_W_KEY) ? 1.0F : 0.0F)
                - (rawKeyDown(INTERNAL_S_KEY) ? 1.0F : 0.0F);
        if (moveX != 0.0F && moveY != 0.0F) {
            moveX *= DIAGONAL_STICK_SCALE;
            moveY *= DIAGONAL_STICK_SCALE;
        }
        return new ControlState(
                moveX,
                moveY,
                rawKeyDown(INTERNAL_JUMP_KEY),
                rawKeyDown(INTERNAL_PRIMARY_KEY),
                rawKeyDown(INTERNAL_MODIFIER_KEY),
                getCameraForward());
    }

    static boolean rawJumpDown() {
        return rawKeyDown(INTERNAL_JUMP_KEY);
    }

    static boolean rawPrimaryDown() {
        return rawKeyDown(INTERNAL_PRIMARY_KEY);
    }

    static boolean rawModifierDown() {
        return rawKeyDown(INTERNAL_MODIFIER_KEY);
    }

    private static boolean rawKeyDown(int internalKey) {
        return AlternateCharacterInputKeyboard.rawKeyDown(internalKey);
    }

    /**
     * Resolve the ground-plane basis from the camera that owns the view.
     * Prefer the live rendered detached position/look transform used by RTS orbit;
     * Construction's helper and vanilla camera geometry remain fallbacks.
     */
    static PlanarDirection getCameraForward() {
        /*
         * The detached Class411 transform is the rendered camera. Read it first
         * every tick so mouse/Q/E orbit changes reach WASD immediately. The
         * Construction helper remains a fallback for frames where the camera
         * owner is being created or torn down.
         */
        Class411_Sub1 detached = null;
        try {
            if (ConstructionBuildCamera.isRequested()
                    && Class24.aClass411_Sub1_158 != null) {
                detached = Class24.aClass411_Sub1_158;
            } else if (IncomingPacket.method4113((byte) 0)
                    && Class24.aClass411_Sub1_158 != null) {
                detached = Class24.aClass411_Sub1_158;
            } else if (Class18.anInt143 * 625220759 == 1
                    && Class133_Sub1.aClass411_Sub1_9827 != null) {
                detached = Class133_Sub1.aClass411_Sub1_9827;
            }
        } catch (RuntimeException ignored) {
            // Fall through to vanilla resolved-camera/fixed-safe fallback.
        }

        PlanarDirection direction = detached == null ? null : getDetachedCameraForward(detached);
        if (direction != null) {
            return direction;
        }

        try {
            float[] constructionForward = ConstructionBuildCamera.getMovementForward();
            if (constructionForward != null) {
                PlanarDirection construction = normalize(
                        constructionForward[0], constructionForward[1]);
                if (construction != null) {
                    return construction;
                }
            }
        } catch (RuntimeException ignored) {
            // Continue to the vanilla resolved-camera fallback.
        }

        try {
            int cameraMode = Class18.anInt143 * 625220759;
            if (cameraMode == 4 || cameraMode == 6) {
                float focusX = Entity.anInt11674 * 1007135537;
                float focusZ = Class165.anInt2050 * -1126693191;
                float cameraX = Class36.anInt387 * 386814715;
                float cameraZ = Class49.anInt490 * -999214779;
                PlanarDirection vanilla = normalize(
                        focusX - cameraX,
                        focusZ - cameraZ);
                if (vanilla != null) {
                    return vanilla;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the established yaw fallback below.
        }

        try {
            int yawUnits = (int) client.aFloat8678;
            if (Class18.anInt143 * 625220759 == 4) {
                yawUnits += client.anInt8665 * -706438965;
            }
            yawUnits &= 0x3fff;
            double radians = yawUnits * (Math.PI * 2.0 / 16384.0);
            PlanarDirection fallback = normalize(
                    -(float) Math.sin(radians),
                    (float) Math.cos(radians));
            if (fallback != null) {
                return fallback;
            }
        } catch (RuntimeException ignored) {
            // Preserve a deterministic fixed-safe fallback on uncertainty.
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
