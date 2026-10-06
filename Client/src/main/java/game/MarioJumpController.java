package game;

/**
 * Matrix3 client-thread presentation/input adapter for Mario-mode native SM64 state.
 *
 * The legacy class name is retained because the established viewport hook calls
 * this owner. Generic alternate-character ownership now lives in
 * {@link AlternateCharacterController}; this class is the Mario/libsm64 driver.
 */
public final class MarioJumpController {

    // Native action/vertical presentation calibration, not horizontal policy.
    private static final float DEFAULT_SM64_TO_MATRIX_Y_SCALE = 3.0F;
    private static final float DEFAULT_SM64_TO_MATRIX_XZ_SCALE = 3.0F;
    private static final float SM64_TO_MATRIX_Y_SCALE = resolveVerticalScale();
    private static final float SM64_TO_MATRIX_XZ_SCALE = resolveHorizontalScale();
    private static final float EXTERNAL_POSITION_EPSILON = 0.5F;

    /*
     * V4 native slash keyframes are 0.00, 0.20, 0.45, 0.70, 1.00. The 0.45
     * pose is the cross-body strike point, so the shared combat owner uses that
     * native progress as Mario's contact threshold. Visual acceptance remains a
     * runtime check; this value is sourced from combat_overlay.h, not invented
     * RuneScape timing.
     */
    private static final float MARIO_MELEE_CONTACT_TIME = 0.45F;
    private static final long MAX_ARMED_NATIVE_TICKS = 24L;

    private static Player lastPlayer;
    private static boolean modeWasMario;
    private static boolean spaceReleaseRequired, attackReleaseRequired, crouchReleaseRequired;
    private static boolean combatAttackWasDown, combatAttackQueued, baselineValid, appliedPositionValid;
    private static float groundY, nativeGroundY, lastAppliedY;

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
            combatAttackQueued = false;
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
            publishIdleInput();
            if (latestNative == null) {
                Mario64Diagnostics.observeRuntime(player);
                return;
            }
            groundY = player.method5394().aClass240_2647.aFloat2656;
            nativeGroundY = latestNative.y;
            baselineValid = true;
            appliedPositionValid = false;
            System.out.println("[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale "
                    + SM64_TO_MATRIX_Y_SCALE + ", XZ scale " + SM64_TO_MATRIX_XZ_SCALE
                    + "; shared horizontal controller)");
            System.out.println("[Mario] Controls: camera-relative WASD move, Space jump, F attack, Shift crouch/ground-pound");
        }

        AlternateCharacterController.MovementInput input = publishControls(player);
        Sm64BridgeSession.NativePosition position = Sm64BridgeSession.getInterpolatedPosition();
        if (position == null) {
            Mario64Diagnostics.observeRuntime(player);
            return;
        }
        float currentY = player.method5394().aClass240_2647.aFloat2656;
        if (appliedPositionValid && Math.abs(currentY - lastAppliedY) > EXTERNAL_POSITION_EPSILON) {
            groundY = currentY; // Terrain/plane corrections remain Matrix-owned.
        }
        AlternateCharacterController.applyHorizontalMovement(
                AlternateCharacterController.CharacterId.MARIO, player,
                position.x, position.z, SM64_TO_MATRIX_XZ_SCALE, input);

        // Native Mario retains vertical physics/actions, not horizontal policy.
        Class240 horizontal = player.method5394().aClass240_2647;
        float height = Math.max(0.0F, position.y - nativeGroundY);
        lastAppliedY = groundY - height * SM64_TO_MATRIX_Y_SCALE;
        player.method5395(horizontal.aFloat2653, lastAppliedY, horizontal.aFloat2657);
        appliedPositionValid = true;
        Mario64Diagnostics.observeRuntime(player);
    }

    private static void publishIdleInput() {
        AlternateCharacterController.ControlState controls = AlternateCharacterController.sampleControls();
        AlternateCharacterController.MovementInput input = AlternateCharacterController.IDLE_MOVEMENT;
        Sm64BridgeSession.setInput(input.cameraX, input.cameraZ, input.stickX, input.stickY,
                false, false, false);
        Mario64Diagnostics.observeControls(controls, false, false, false);
    }

    private static AlternateCharacterController.MovementInput publishControls(Player player) {
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

        AlternateCharacterController.MovementInput input =
                AlternateCharacterController.movementInput(
                        AlternateCharacterController.CharacterId.MARIO, controls, null);

        /*
         * Capture the current native frame BEFORE publishing a new slash request.
         * A physical F press is queued one-deep while the previous native slash is
         * still active. The queued press is converted into a new native request
         * only after combatAnimation returns idle, and the shared combat owner is
         * armed from that real native start rather than from the earlier key edge.
         * This preserves one press -> one attack attempt without allowing held-F
         * repeat or letting recovery-time presses poison the next contact cycle.
         */
        Sm64BridgeSession.GeometryFrame contactFrame =
                Sm64BridgeSession.getLatestGeometryFrame();
        boolean attackEdge = buttonB && !combatAttackWasDown;
        if (attackEdge) {
            combatAttackQueued = true;
        }
        boolean nativeSlashBusy = contactFrame != null && contactFrame.combatAnimation == 1;
        boolean startQueuedAttack = combatAttackQueued && !nativeSlashBusy;
        boolean weaponCombat = MarioWeaponCombat.updateInput(startQueuedAttack);
        boolean nativeAttackStarted = startQueuedAttack && weaponCombat;
        if (startQueuedAttack) {
            combatAttackQueued = false;
        }

        AlternateCharacterCombatBridge.updateNativeMeleeContact(
                player,
                AlternateCharacterController.CharacterId.MARIO,
                nativeAttackStarted,
                controls.cameraForward,
                contactFrame == null ? -1L : contactFrame.sequence,
                contactFrame == null ? 0 : contactFrame.combatAnimation,
                0,
                contactFrame == null ? 0.0F : contactFrame.combatTime,
                MARIO_MELEE_CONTACT_TIME,
                MAX_ARMED_NATIVE_TICKS);

        Sm64BridgeSession.setCombatInput(
                input.cameraX,
                input.cameraZ,
                input.stickX,
                input.stickY,
                buttonA,
                weaponCombat ? false : buttonB,
                buttonZ,
                weaponCombat ? 1 : 0,
                MarioWeaponCombat.getRequest());
        Mario64Diagnostics.observeControls(controls, buttonA, weaponCombat ? false : buttonB, buttonZ);

        combatAttackWasDown = buttonB;
        return input;
    }

    private static void enterMarioMode() {
        resetPresentation();
        AlternateCharacterController.beginHorizontalMovement(
                AlternateCharacterController.CharacterId.MARIO,
                Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976);
        AlternateCharacterCombatBridge.reset();
        combatAttackWasDown = false;
        combatAttackQueued = false;
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
        combatAttackQueued = false;
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
        combatAttackQueued = false;
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
        AlternateCharacterController.restoreHorizontalMovement(
                AlternateCharacterController.CharacterId.MARIO, player);
        if (player != null && baselineValid) {
            Class240 position = player.method5394().aClass240_2647;
            player.method5395(position.aFloat2653, groundY, position.aFloat2657);
        }
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
        AlternateCharacterController.resetHorizontalMovement(
                AlternateCharacterController.CharacterId.MARIO);
        MarioWeaponCombat.reset();
        baselineValid = false;
        appliedPositionValid = false;
        groundY = 0.0F;
        nativeGroundY = 0.0F;
        lastAppliedY = 0.0F;
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
