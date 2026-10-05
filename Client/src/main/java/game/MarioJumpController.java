package game;

/**
 * Matrix3 client-thread presentation/input adapter for Mario-mode native SM64 state.
 *
 * The legacy class name is retained because the established viewport hook calls
 * this owner. Generic alternate-character ownership now lives in
 * {@link AlternateCharacterController}; this class is the Mario/libsm64 driver.
 */
public final class MarioJumpController {

    // Presentation calibration only. libsm64 remains the action/physics owner.
    private static final float DEFAULT_SM64_TO_MATRIX_Y_SCALE = 3.0F;
    private static final float SM64_TO_MATRIX_Y_SCALE = resolveVerticalScale();
    private static final float EXTERNAL_POSITION_EPSILON = 0.5F;
    private static final int WALK_RETRY_CYCLES = 10;
    private static final float MOVE_DIRECTION_DEADZONE = 0.20F;

    /*
     * Keep libsm64's camera basis fixed and encode Matrix's already-resolved
     * screen-relative movement as a world-space stick vector. This avoids a
     * second camera-handedness conversion at the native boundary.
     */
    private static final float LIBSM64_NEUTRAL_CAMERA_X = 0.0F;
    private static final float LIBSM64_NEUTRAL_CAMERA_Z = 1.0F;

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
    private static float nativeGroundY;
    private static float lastAppliedX;
    private static float lastAppliedY;
    private static float lastAppliedZ;
    private static float requestedWorldMoveX;
    private static float requestedWorldMoveZ;
    private static int lastWalkTargetX = Integer.MIN_VALUE;
    private static int lastWalkTargetY = Integer.MIN_VALUE;
    private static int lastWalkRequestCycle = Integer.MIN_VALUE;

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
            nativeGroundY = latestNative.y;
            baselineValid = true;
            appliedPositionValid = false;
            System.out.println("[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale "
                    + SM64_TO_MATRIX_Y_SCALE + ", XZ owner vanilla RS3 walking/collision)");
            System.out.println("[Mario] Controls: camera-relative WASD move, Space jump, F attack, Shift crouch/ground-pound");
        }

        publishControls();
        requestVanillaRuneScapeStep(player);

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
             * Matrix/server walking owns X/Z and terrain corrections. Mario only
             * overlays native vertical displacement. Rebase any external change
             * instead of fighting RuneScape movement/collision ownership.
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

        /*
         * Always accept the live Matrix X/Z as ground truth. This is the temporary
         * vanilla-RS3 collision mode: normal walk requests go to the server and the
         * ordinary player movement/update path decides whether/how X/Z advances.
         * libsm64 still receives the same movement input for authentic Mario action
         * and animation state, but native X/Z is deliberately not applied locally.
         */
        groundX = currentX;
        groundZ = currentZ;

        float nativeHeight = interpolatedNative.y - nativeGroundY;
        if (nativeHeight < 0.0F) {
            nativeHeight = 0.0F;
        }

        // Matrix altitude increases as scene-Y decreases.
        float targetY = groundY - nativeHeight * SM64_TO_MATRIX_Y_SCALE;
        player.method5395(currentX, targetY, currentZ);
        lastAppliedX = currentX;
        lastAppliedY = targetY;
        lastAppliedZ = currentZ;
        appliedPositionValid = true;

        Mario64Diagnostics.observeRuntime(player);
    }

    /**
     * Temporary collision handoff: translate the camera-relative Mario movement
     * intent into Matrix3's existing scene-walk packet. The normal server
     * route/collision owner then validates walls, objects, blocked floor and
     * diagonal legality and returns movement through the stock player update path.
     * No custom clip masks or parallel Mario collision map are introduced here.
     */
    private static void requestVanillaRuneScapeStep(Player player) {
        if (player == null || client.aClass195_8589 == null) {
            return;
        }

        int dx = directionStep(requestedWorldMoveX);
        int dz = directionStep(requestedWorldMoveZ);
        if (dx == 0 && dz == 0) {
            lastWalkTargetX = Integer.MIN_VALUE;
            lastWalkTargetY = Integer.MIN_VALUE;
            return;
        }

        int currentTileX = player.screenX[0];
        int currentTileY = player.screenY[0];
        int targetTileX = currentTileX + dx;
        int targetTileY = currentTileY + dz;

        if (targetTileX < 0 || targetTileY < 0
                || targetTileX >= client.aClass613_8605.method7347(-520836217)
                || targetTileY >= client.aClass613_8605.method7278(277214477)) {
            return;
        }

        boolean targetChanged = targetTileX != lastWalkTargetX || targetTileY != lastWalkTargetY;
        boolean retryDue = lastWalkRequestCycle == Integer.MIN_VALUE
                || client.cycles - lastWalkRequestCycle >= WALK_RETRY_CYCLES;
        if (!targetChanged && !retryDue) {
            return;
        }

        Class572_Sub25 packet = IncomingPacket.method4108(targetTileX, targetTileY, 0, 0);
        if (packet == null) {
            return;
        }
        client.aClass195_8589.method2929(packet, (byte) -1);
        lastWalkTargetX = targetTileX;
        lastWalkTargetY = targetTileY;
        lastWalkRequestCycle = client.cycles;
    }

    private static int directionStep(float value) {
        if (value > MOVE_DIRECTION_DEADZONE) {
            return 1;
        }
        if (value < -MOVE_DIRECTION_DEADZONE) {
            return -1;
        }
        return 0;
    }

    private static void publishIdleInput() {
        AlternateCharacterController.ControlState controls =
                AlternateCharacterController.sampleControls();
        requestedWorldMoveX = 0.0F;
        requestedWorldMoveZ = 0.0F;
        Sm64BridgeSession.setInput(
                LIBSM64_NEUTRAL_CAMERA_X,
                LIBSM64_NEUTRAL_CAMERA_Z,
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
         * Construction's accepted RTS movement maps local screen input with:
         *   right = (forwardZ, -forwardX)
         *   world = localX * right + localY * forward
         *
         * verified-static against libsm64:
         *   cameraYaw = atan2s(camLookZ, camLookX)
         *   controller.stickX = -64 * input.stickX
         *   controller.stickY =  64 * input.stickY
         *   intendedYaw = atan2s(-stickY, stickX) + cameraYaw
         *
         * With neutral camLook=(0,+1), publishing stick=(-worldX,-worldZ)
         * makes libsm64's intendedYaw point at the exact Matrix world vector.
         * This removes all camera sign/handedness interpretation from libsm64.
         */
        float forwardX = controls.cameraForward.x;
        float forwardZ = controls.cameraForward.z;
        float rightX = forwardZ;
        float rightZ = -forwardX;
        float worldMoveX = controls.moveX * rightX + controls.moveY * forwardX;
        float worldMoveZ = controls.moveX * rightZ + controls.moveY * forwardZ;
        requestedWorldMoveX = worldMoveX;
        requestedWorldMoveZ = worldMoveZ;

        boolean weaponCombat = MarioWeaponCombat.updateInput(buttonB && !combatAttackWasDown);
        Sm64BridgeSession.setCombatInput(
                LIBSM64_NEUTRAL_CAMERA_X,
                LIBSM64_NEUTRAL_CAMERA_Z,
                -worldMoveX,
                -worldMoveZ,
                buttonA,
                weaponCombat ? false : buttonB,
                buttonZ,
                weaponCombat ? 1 : 0,
                MarioWeaponCombat.getRequest());
        Mario64Diagnostics.observeControls(controls, buttonA, weaponCombat ? false : buttonB, buttonZ);

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
        MarioWeaponCombat.reset();
        baselineValid = false;
        appliedPositionValid = false;
        groundX = 0.0F;
        groundY = 0.0F;
        groundZ = 0.0F;
        nativeGroundY = 0.0F;
        lastAppliedX = 0.0F;
        lastAppliedY = 0.0F;
        lastAppliedZ = 0.0F;
        requestedWorldMoveX = 0.0F;
        requestedWorldMoveZ = 0.0F;
        lastWalkTargetX = Integer.MIN_VALUE;
        lastWalkTargetY = Integer.MIN_VALUE;
        lastWalkRequestCycle = Integer.MIN_VALUE;
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
