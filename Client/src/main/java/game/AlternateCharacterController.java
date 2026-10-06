package game;

/**
 * One client-side horizontal movement controller for native character drivers.
 *
 * Matrix remains the host camera/input/world architecture. Character-specific
 * This owner samples one control frame, resolves world/native input and applies
 * one free/clipped horizontal movement state. Drivers exchange native state and
 * retain their action, animation, vertical physics and combat integration only.
 */
public final class AlternateCharacterController {

    private static final AlternateCharacterFreeMovement HORIZONTAL_MOVEMENT =
            new AlternateCharacterFreeMovement();
    private static CharacterId horizontalOwner;
    private static int lastTickCycle = Integer.MIN_VALUE;
    private static int lastControlCycle = Integer.MIN_VALUE;
    private static CharacterId sampledCharacter;
    private static boolean sampledCalibration;
    private static ControlState sampledControls;

    // UI requests only; the shared movement state applies transitions on the client thread.
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

        ControlState withMovementForward(PlanarDirection forward) {
            return forward == null || forward == cameraForward ? this : new ControlState(
                    moveX, moveY, jump, primaryAction, modifierAction, forward);
        }
    }

    /** One world intent plus its native transport encoding; no actor-facing input. */
    static final class MovementInput {
        final float worldMoveX, worldMoveZ;
        final float cameraX, cameraZ, stickX, stickY;

        MovementInput(float worldX, float worldZ, float cameraX, float cameraZ,
                float stickX, float stickY) {
            this.worldMoveX = worldX;
            this.worldMoveZ = worldZ;
            this.cameraX = cameraX;
            this.cameraZ = cameraZ;
            this.stickX = stickX;
            this.stickY = stickY;
        }
    }

    static final MovementInput IDLE_MOVEMENT =
            new MovementInput(0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);

    static MovementInput movementInput(CharacterId character, ControlState controls,
            PlanarDirection movementForward) {
        ControlState resolved = controls.withMovementForward(movementForward);
        /* verified-static at libsm64 fd118132, liboot 25208734 / OoT 269d0301.
         * The controller alone owns native axis conversion. Both profiles decode
         * to resolved.worldMoveX/Z; an explicit Z-target basis uses the SAME
         * world intent for native input and optional Matrix clipping. */
        if (character == CharacterId.MARIO) {
            return new MovementInput(resolved.worldMoveX, resolved.worldMoveZ,
                    0.0F, 1.0F, -resolved.worldMoveX, -resolved.worldMoveZ);
        }
        if (character == CharacterId.LINK) {
            return new MovementInput(resolved.worldMoveX, resolved.worldMoveZ,
                    resolved.cameraForward.x, resolved.cameraForward.z,
                    -resolved.moveX, resolved.moveY);
        }
        throw new IllegalArgumentException("Missing native movement profile: " + character);
    }

    static void beginHorizontalMovement(CharacterId character, Player player) {
        if (horizontalOwner != character) {
            HORIZONTAL_MOVEMENT.restore(player);
            HORIZONTAL_MOVEMENT.reset();
            horizontalOwner = character;
        }
    }

    static void applyHorizontalMovement(CharacterId character, Player player,
            float nativeX, float nativeZ, float scale, MovementInput input) {
        beginHorizontalMovement(character, player);
        HORIZONTAL_MOVEMENT.apply(player, nativeX, nativeZ, scale,
                input.worldMoveX, input.worldMoveZ, isRuneScapeClippingEnabled());
    }

    static void restoreHorizontalMovement(CharacterId character, Player player) {
        if (horizontalOwner == character) {
            HORIZONTAL_MOVEMENT.restore(player);
            horizontalOwner = null;
        }
    }

    static void resetHorizontalMovement(CharacterId character) {
        if (horizontalOwner == character) {
            HORIZONTAL_MOVEMENT.reset();
            horizontalOwner = null;
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
            return style == CombatStyle.MELEE;
        }
    };

    private AlternateCharacterController() {
    }

    /**
     * Compatibility entry used by the established viewport hook. Imported
     * characters dispatch here instead of adding per-game viewport ticks.
     */
    public static void tick() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;
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
        CharacterId character = getActiveCharacter();
        boolean calibration = character == CharacterId.MARIO
                && MarioHelmetCalibrationController.isActive();
        if (sampledControls != null && lastControlCycle == client.cycles
                && sampledCharacter == character && sampledCalibration == calibration) {
            return sampledControls;
        }
        lastControlCycle = client.cycles;
        sampledCharacter = character;
        sampledCalibration = calibration;
        if (calibration) {
            sampledControls = new ControlState(
                    0.0F, 0.0F,
                    false, false, false,
                    getCameraForward());
            return sampledControls;
        }

        float moveX = (rawKeyDown(INTERNAL_D_KEY) ? 1.0F : 0.0F)
                - (rawKeyDown(INTERNAL_A_KEY) ? 1.0F : 0.0F);
        float moveY = (rawKeyDown(INTERNAL_W_KEY) ? 1.0F : 0.0F)
                - (rawKeyDown(INTERNAL_S_KEY) ? 1.0F : 0.0F);
        if (moveX != 0.0F && moveY != 0.0F) {
            moveX *= DIAGONAL_STICK_SCALE;
            moveY *= DIAGONAL_STICK_SCALE;
        }
        sampledControls = new ControlState(
                moveX,
                moveY,
                rawKeyDown(INTERNAL_JUMP_KEY),
                rawPrimaryDown(),
                rawModifierDown(),
                getCameraForward());
        return sampledControls;
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
