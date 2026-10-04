package game;

/**
 * Matrix3 client-thread presentation/input adapter for Mario-mode native SM64 state.
 *
 * The legacy class name is retained because the established viewport hook calls
 * this owner. Generic alternate-character ownership now lives in
 * {@link AlternateCharacterController}; this class is the Mario/libsm64 driver.
 */
public final class MarioJumpController {

    // Presentation calibration only. libsm64 remains the movement/physics owner.
    private static final float DEFAULT_SM64_TO_MATRIX_Y_SCALE = 3.0F;
    private static final float DEFAULT_SM64_TO_MATRIX_XZ_SCALE = 3.0F;
    private static final float SM64_TO_MATRIX_Y_SCALE = resolveVerticalScale();
    private static final float SM64_TO_MATRIX_XZ_SCALE = resolveHorizontalScale();
    private static final float EXTERNAL_POSITION_EPSILON = 0.5F;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static Player lastPlayer;
    private static boolean modeWasMario;
    private static boolean spaceReleaseRequired;
    private static boolean attackReleaseRequired;
    private static boolean crouchReleaseRequired;
    private static boolean combatAttackWasDown;
    private static boolean baselineValid;
    private static boolean appliedPositionValid;

    private static float groundX;
    private static float groundY;
    private static float groundZ;
    private static float nativeGroundX;
    private static float nativeGroundY;
    private static float nativeGroundZ;
    private static float lastAppliedX;
    private static float lastAppliedY;
    private static float lastAppliedZ;

    private MarioJumpController() {
    }

    /**
     * Compatibility seam retained for Class343.method4302(...). New imported
     * characters dispatch through one master controller instead of adding new
     * viewport hooks/controllers per game.
     */
    public static void tick() {
        AlternateCharacterController.tick();
    }

    /** Mario/libsm64 driver invoked by AlternateCharacterController. */
    static void tickMarioDriver() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player != lastPlayer) {
            Sm64BridgeSession.stop();
            AlternateCharacterInputKeyboard.uninstall();
            AlternateCharacterCombatBridge.reset();
            resetPresentation();
            modeWasMario = false;
            spaceReleaseRequired = false;
            attackReleaseRequired = false;
            crouchReleaseRequired = false;
            combatAttackWasDown = false;
            PlayerControllerMode.resetForPlayerLifecycle();
            lastPlayer = player;
        }

        PlayerControllerMode.tick();
        boolean marioMode = PlayerControllerMode.isMarioMode();
        if (marioMode != modeWasMario) {
            if (marioMode) {
                enterMarioMode();
            } else {
                exitMarioMode(player);
            }
            modeWasMario = marioMode;
        }

        if (!marioMode) {
            Mario64Diagnostics.observeRuntime(player);
            return;
        }

        if (player == null) {
            fallbackToRuneScape(null, "local player unavailable");
            Mario64Diagnostics.observeRuntime(null);
            return;
        }

        if (Sm64BridgeSession.hasFailed()) {
            String reason = Sm64BridgeSession.getFailureReason();
            fallbackToRuneScape(player, reason == null ? "native session failed" : reason);
            Mario64Diagnostics.observeRuntime(player);
            return;
        }

        Sm64BridgeSession.NativePosition latestNative = Sm64BridgeSession.getLatestPosition();
        if (!baselineValid) {
            // Do not feed movement/actions until Matrix and native baselines exist.
            publishIdleInput();
            if (latestNative == null) {
                Mario64Diagnostics.observeRuntime(player);
                return;
            }

            Class240 position = player.method5394().aClass240_2647;
            groundX = position.aFloat2653;
            groundY = position.aFloat2656;
            groundZ = position.aFloat2657;
            nativeGroundX = latestNative.x;
            nativeGroundY = latestNative.y;
            nativeGroundZ = latestNative.z;
            baselineValid = true;
            appliedPositionValid = false;
            System.out.println("[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale "
                    + SM64_TO_MATRIX_Y_SCALE + ", XZ scale " + SM64_TO_MATRIX_XZ_SCALE + ")");
            System.out.println("[Mario] Controls: camera-relative WASD move, Space jump, F attack, Shift crouch/ground-pound");
        }

        publishControls();

        Sm64BridgeSession.NativePosition interpolatedNative =
                Sm64BridgeSession.getInterpolatedPosition();
        if (interpolatedNative == null) {
            Mario64Diagnostics.observeRuntime(player);
            return;
        }

        Class240 position = player.method5394().aClass240_2647;
        float currentX = position.aFloat2653;
        float currentY = position.aFloat2656;
        float currentZ = position.aFloat2657;
        if (appliedPositionValid) {
            /*
             * Matrix/server-owned corrections remain authoritative underneath the
             * temporary Mario presentation offset. Rebase only the corrected axis
             * instead of fighting normal RuneScape movement/terrain ownership.
             */
            if (Math.abs(currentX - lastAppliedX) > EXTERNAL_POSITION_EPSILON) {
                groundX = currentX;
            }
            if (Math.abs(currentY - lastAppliedY) > EXTERNAL_POSITION_EPSILON) {
                groundY = currentY;
            }
            if (Math.abs(currentZ - lastAppliedZ) > EXTERNAL_POSITION_EPSILON) {
                groundZ = currentZ;
            }
        }

        float nativeHeight = interpolatedNative.y - nativeGroundY;
        if (nativeHeight < 0.0F) {
            nativeHeight = 0.0F;
        }

        float targetX = groundX + (interpolatedNative.x - nativeGroundX) * SM64_TO_MATRIX_XZ_SCALE;
        // Matrix altitude increases as scene-Y decreases.
        float targetY = groundY - nativeHeight * SM64_TO_MATRIX_Y_SCALE;
        float targetZ = groundZ + (interpolatedNative.z - nativeGroundZ) * SM64_TO_MATRIX_XZ_SCALE;
        player.method5395(targetX, targetY, targetZ);
        lastAppliedX = targetX;
        lastAppliedY = targetY;
        lastAppliedZ = targetZ;
        appliedPositionValid = true;

        Mario64Diagnostics.observeRuntime(player);
    }

    private static void publishIdleInput() {
        AlternateCharacterController.ControlState controls =
                AlternateCharacterController.sampleControls();
        AlternateCharacterController.PlanarDirection camera = controls.cameraForward;
        Sm64BridgeSession.setInput(
                -camera.x, -camera.z,
                0.0F, 0.0F,
                false, false, false);
        Mario64Diagnostics.observeControls(controls, false, false, false);
    }

    private static void publishControls() {
        AlternateCharacterController.ControlState controls =
                AlternateCharacterController.sampleControls();

        if (spaceReleaseRequired && !controls.jump) {
            spaceReleaseRequired = false;
        }
        boolean buttonA = !spaceReleaseRequired && controls.jump;

        if (attackReleaseRequired && !controls.primaryAction) {
            attackReleaseRequired = false;
        }
        boolean buttonB = !attackReleaseRequired && controls.primaryAction;

        if (crouchReleaseRequired && !controls.modifierAction) {
            crouchReleaseRequired = false;
        }
        boolean buttonZ = !crouchReleaseRequired && controls.modifierAction;

        /*
         * Runtime evidence with the correctly owned Construction camera proved
         * the entire SM64 steering basis was 180 degrees reversed: W/S and A/D
         * were both inverted together. Keep the shared Matrix camera-forward
         * semantic unchanged for future character drivers and adapt only the
         * libsm64 camera-look convention here.
         */
        Sm64BridgeSession.setInput(
                -controls.cameraForward.x,
                -controls.cameraForward.z,
                controls.moveX,
                controls.moveY,
                buttonA,
                buttonB,
                buttonZ);
        Mario64Diagnostics.observeControls(controls, buttonA, buttonB, buttonZ);

        if (buttonB && !combatAttackWasDown) {
            AlternateCharacterCombatBridge.requestPrimaryMeleeAttack();
        }
        combatAttackWasDown = buttonB;
    }

    private static void enterMarioMode() {
        resetPresentation();
        AlternateCharacterCombatBridge.reset();
        combatAttackWasDown = false;
        AlternateCharacterInputKeyboard.install();
        captureHeldActionGuards();
        Sm64BridgeSession.start();
    }

    private static void exitMarioMode(Player player) {
        restoreGroundBaseline(player);
        Sm64BridgeSession.stop();
        AlternateCharacterInputKeyboard.uninstall();
        AlternateCharacterCombatBridge.reset();
        resetPresentation();
        combatAttackWasDown = false;
        captureHeldActionGuards();
    }

    private static void fallbackToRuneScape(Player player, String reason) {
        System.out.println("[SM64 Bridge] Falling back to RuneScape control: " + reason);
        Mario64Diagnostics.noteFallback(reason);
        restoreGroundBaseline(player);
        Sm64BridgeSession.stop();
        AlternateCharacterInputKeyboard.uninstall();
        AlternateCharacterCombatBridge.reset();
        resetPresentation();
        combatAttackWasDown = false;
        captureHeldActionGuards();
        modeWasMario = false;
        PlayerControllerMode.setMode(PlayerControllerMode.Mode.RUNESCAPE);
    }

    private static void captureHeldActionGuards() {
        spaceReleaseRequired = AlternateCharacterController.rawJumpDown();
        attackReleaseRequired = AlternateCharacterController.rawPrimaryDown();
        crouchReleaseRequired = AlternateCharacterController.rawModifierDown();
    }

    private static void restoreGroundBaseline(Player player) {
        if (player == null || !baselineValid) {
            return;
        }
        player.method5395(groundX, groundY, groundZ);
    }

    private static float resolveVerticalScale() {
        return resolvePositiveScale(
                "matrix3.sm64.verticalScale",
                DEFAULT_SM64_TO_MATRIX_Y_SCALE);
    }

    private static float resolveHorizontalScale() {
        return resolvePositiveScale(
                "matrix3.sm64.horizontalScale",
                DEFAULT_SM64_TO_MATRIX_XZ_SCALE);
    }

    private static float resolvePositiveScale(String propertyName, float defaultValue) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            float parsed = Float.parseFloat(configured.trim());
            if (parsed > 0.0F && !Float.isNaN(parsed) && !Float.isInfinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the safe default.
        }
        System.out.println("[SM64 Bridge] Invalid " + propertyName + "='" + configured
                + "'; using " + defaultValue);
        return defaultValue;
    }

    private static void resetPresentation() {
        baselineValid = false;
        appliedPositionValid = false;
        groundX = 0.0F;
        groundY = 0.0F;
        groundZ = 0.0F;
        nativeGroundX = 0.0F;
        nativeGroundY = 0.0F;
        nativeGroundZ = 0.0F;
        lastAppliedX = 0.0F;
        lastAppliedY = 0.0F;
        lastAppliedZ = 0.0F;
    }

    public static boolean isAirborne() {
        Float nativeY = Sm64BridgeSession.getLatestY();
        return baselineValid && nativeY != null && nativeY.floatValue() - nativeGroundY > 0.5F;
    }

    public static float getHeightOffset() {
        Float nativeY = Sm64BridgeSession.getInterpolatedY();
        if (!baselineValid || nativeY == null) {
            return 0.0F;
        }
        float nativeHeight = nativeY.floatValue() - nativeGroundY;
        return nativeHeight <= 0.0F ? 0.0F : nativeHeight * SM64_TO_MATRIX_Y_SCALE;
    }
}
