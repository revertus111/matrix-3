package game;

/**
 * Matrix3 client-thread presentation/input adapter for Mario-mode native SM64 state.
 *
 * The legacy class name is retained because the established viewport hook calls
 * this owner. Generic alternate-character ownership now lives in
 * {@link AlternateCharacterController}; this class is the Mario/libsm64 driver.
 */
public final class MarioJumpController {

    private static final AlternateCharacterFreeMovement freeMovement =
            new AlternateCharacterFreeMovement();
    private static boolean clippingWasEnabled;

    // Presentation calibration only. libsm64 remains the action/physics owner.
    private static final float DEFAULT_SM64_TO_MATRIX_Y_SCALE = 3.0F;
    private static final float DEFAULT_SM64_TO_MATRIX_XZ_SCALE = 3.0F;
    private static final float SM64_TO_MATRIX_Y_SCALE = resolveVerticalScale();
    private static final float SM64_TO_MATRIX_XZ_SCALE = resolveHorizontalScale();
    private static final float EXTERNAL_POSITION_EPSILON = 0.5F;
    private static final int WALK_RETRY_CYCLES = 10;
    private static final float MOVE_DIRECTION_DEADZONE = 0.20F;

    /*
     * Temporary hybrid collision mode. RuneScape decides whether the next tile is
     * legal, but native Mario remains continuous inside the accepted corridor.
     * Exactly half a tile is the legal edge: without an accepted adjacent tile,
     * Mario may reach that boundary but never cross it visually.
     */
    private static final float COLLISION_PRESENTATION_LEAD = 256.0F;
    private static final float WALK_REQUEST_LEAD = 128.0F;

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
    private static boolean nativePresentationValid;
    private static boolean pendingAuthorityTileValid;

    private static float groundX;
    private static float groundY;
    private static float groundZ;
    private static float nativeGroundY;
    private static float lastAppliedX;
    private static float lastAppliedY;
    private static float lastAppliedZ;
    private static float lastNativePresentationX;
    private static float lastNativePresentationZ;
    private static float requestedWorldMoveX;
    private static float requestedWorldMoveZ;
    private static int collisionTileX = Integer.MIN_VALUE;
    private static int collisionTileY = Integer.MIN_VALUE;
    private static int pendingAuthorityTileX = Integer.MIN_VALUE;
    private static int pendingAuthorityTileY = Integer.MIN_VALUE;
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

        boolean clipping = AlternateCharacterController.isRuneScapeClippingEnabled();
        if (baselineValid && clipping != clippingWasEnabled) {
            restoreGroundBaseline(player);
            resetPresentation();
        }
        clippingWasEnabled = clipping;

        Sm64BridgeSession.NativePosition latestNative = Sm64BridgeSession.getLatestPosition();
        if (!baselineValid) {
            // Do not feed movement/actions until Matrix and native baselines exist.
            publishIdleInput();
            if (latestNative == null) {
                Mario64Diagnostics.observeRuntime(player);
                return;
            }

            Class240 position = player.method5394().aClass240_2647;
            collisionTileX = player.screenX[0];
            collisionTileY = player.screenY[0];
            groundX = clipping ? tileCenter(player, collisionTileX) : position.aFloat2653;
            groundY = position.aFloat2656;
            groundZ = clipping ? tileCenter(player, collisionTileY) : position.aFloat2657;
            nativeGroundY = latestNative.y;
            baselineValid = true;
            appliedPositionValid = false;
            nativePresentationValid = false;
            pendingAuthorityTileValid = false;
            System.out.println("[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale "
                    + SM64_TO_MATRIX_Y_SCALE + ", XZ scale " + SM64_TO_MATRIX_XZ_SCALE
                    + (clipping ? ", vanilla RS3 tile clipping)" : ", continuous free movement / clipping OFF)"));
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
        if (appliedPositionValid && Math.abs(currentY - lastAppliedY) > EXTERNAL_POSITION_EPSILON) {
            /*
             * Terrain/plane corrections remain Matrix-owned. Horizontal stock
             * movement is intentionally NOT copied directly into groundX/Z here:
             * doing that every accepted tile was the source of the visible hitch.
             */
            groundY = currentY;
        }

        if (!clipping) {
            freeMovement.apply(player, interpolatedNative.x, interpolatedNative.z,
                    SM64_TO_MATRIX_XZ_SCALE);
            Class240 freePosition = player.method5394().aClass240_2647;
            float height = Math.max(0.0F, interpolatedNative.y - nativeGroundY);
            lastAppliedX = freePosition.aFloat2653;
            lastAppliedY = groundY - height * SM64_TO_MATRIX_Y_SCALE;
            lastAppliedZ = freePosition.aFloat2657;
            player.method5395(lastAppliedX, lastAppliedY, lastAppliedZ);
            appliedPositionValid = true;
            Mario64Diagnostics.observeRuntime(player);
            return;
        }

        float nativeDeltaX = 0.0F;
        float nativeDeltaZ = 0.0F;
        if (nativePresentationValid) {
            nativeDeltaX = (interpolatedNative.x - lastNativePresentationX) * SM64_TO_MATRIX_XZ_SCALE;
            nativeDeltaZ = (interpolatedNative.z - lastNativePresentationZ) * SM64_TO_MATRIX_XZ_SCALE;
        }
        lastNativePresentationX = interpolatedNative.x;
        lastNativePresentationZ = interpolatedNative.z;
        nativePresentationValid = true;

        float presentationBaseX = appliedPositionValid ? lastAppliedX : currentX;
        float presentationBaseZ = appliedPositionValid ? lastAppliedZ : currentZ;
        float desiredX = presentationBaseX + nativeDeltaX;
        float desiredZ = presentationBaseZ + nativeDeltaZ;

        /*
         * Stock RuneScape may approve the requested adjacent tile before Mario's
         * continuous presentation reaches the shared boundary. Keep that approval
         * pending. Only switch the collision anchor when the visible native motion
         * reaches the boundary, where old-tile +256 and new-tile -256 are the exact
         * same world coordinate. This makes the handoff mathematically continuous.
         */
        syncVanillaAuthority(player, desiredX, desiredZ);
        requestVanillaRuneScapeStep(player, desiredX, desiredZ);

        float targetX = clamp(desiredX,
                groundX - COLLISION_PRESENTATION_LEAD,
                groundX + COLLISION_PRESENTATION_LEAD);
        float targetZ = clamp(desiredZ,
                groundZ - COLLISION_PRESENTATION_LEAD,
                groundZ + COLLISION_PRESENTATION_LEAD);

        float nativeHeight = interpolatedNative.y - nativeGroundY;
        if (nativeHeight < 0.0F) {
            nativeHeight = 0.0F;
        }

        // Matrix altitude increases as scene-Y decreases.
        float targetY = groundY - nativeHeight * SM64_TO_MATRIX_Y_SCALE;
        player.method5395(targetX, targetY, targetZ);
        lastAppliedX = targetX;
        lastAppliedY = targetY;
        lastAppliedZ = targetZ;
        appliedPositionValid = true;

        Mario64Diagnostics.observeRuntime(player);
    }

    /**
     * Observe the stock player tile as collision approval, but defer adopting an
     * adjacent approved tile until continuous Mario motion reaches the shared tile
     * boundary. Larger stock corrections are treated as teleports/rebases and are
     * adopted immediately rather than hidden behind presentation smoothing.
     */
    private static void syncVanillaAuthority(Player player, float desiredX, float desiredZ) {
        int authorityTileX = player.screenX[0];
        int authorityTileY = player.screenY[0];

        if (collisionTileX == Integer.MIN_VALUE || collisionTileY == Integer.MIN_VALUE) {
            collisionTileX = authorityTileX;
            collisionTileY = authorityTileY;
            groundX = tileCenter(player, collisionTileX);
            groundZ = tileCenter(player, collisionTileY);
            pendingAuthorityTileValid = false;
            return;
        }

        if (authorityTileX == collisionTileX && authorityTileY == collisionTileY) {
            pendingAuthorityTileValid = false;
            return;
        }

        int tileDeltaX = authorityTileX - collisionTileX;
        int tileDeltaY = authorityTileY - collisionTileY;
        if (Math.abs(tileDeltaX) > 1 || Math.abs(tileDeltaY) > 1) {
            hardRebaseHorizontalAuthority(player, authorityTileX, authorityTileY);
            return;
        }

        pendingAuthorityTileX = authorityTileX;
        pendingAuthorityTileY = authorityTileY;
        pendingAuthorityTileValid = true;

        float boundaryX = groundX + tileDeltaX * COLLISION_PRESENTATION_LEAD;
        float boundaryZ = groundZ + tileDeltaY * COLLISION_PRESENTATION_LEAD;
        boolean crossedX = tileDeltaX == 0
                || (tileDeltaX > 0 ? desiredX >= boundaryX : desiredX <= boundaryX);
        boolean crossedZ = tileDeltaY == 0
                || (tileDeltaY > 0 ? desiredZ >= boundaryZ : desiredZ <= boundaryZ);

        if (!crossedX || !crossedZ) {
            return;
        }

        collisionTileX = pendingAuthorityTileX;
        collisionTileY = pendingAuthorityTileY;
        groundX = tileCenter(player, collisionTileX);
        groundZ = tileCenter(player, collisionTileY);
        pendingAuthorityTileValid = false;
        lastWalkTargetX = Integer.MIN_VALUE;
        lastWalkTargetY = Integer.MIN_VALUE;
        lastWalkRequestCycle = Integer.MIN_VALUE;
    }

    private static void hardRebaseHorizontalAuthority(Player player, int tileX, int tileY) {
        collisionTileX = tileX;
        collisionTileY = tileY;
        groundX = tileCenter(player, collisionTileX);
        groundZ = tileCenter(player, collisionTileY);
        pendingAuthorityTileValid = false;
        lastWalkTargetX = Integer.MIN_VALUE;
        lastWalkTargetY = Integer.MIN_VALUE;
        lastWalkRequestCycle = Integer.MIN_VALUE;
        lastAppliedX = groundX;
        lastAppliedZ = groundZ;
        nativePresentationValid = false;
    }

    private static float tileCenter(Player player, int tile) {
        return tile * 512.0F + player.method10556((short) -23679) * 256.0F;
    }

    /**
     * Temporary collision handoff. Native Mario owns the continuous presentation,
     * but crossing the local tile boundary requires a normal Matrix3 walk request.
     * Once the server/client stock path approves that adjacent tile, the approval
     * remains pending until Mario visibly reaches the boundary.
     */
    private static void requestVanillaRuneScapeStep(Player player, float desiredX, float desiredZ) {
        if (player == null || client.aClass195_8589 == null || pendingAuthorityTileValid) {
            return;
        }

        float leadX = desiredX - groundX;
        float leadZ = desiredZ - groundZ;
        int dx = 0;
        int dz = 0;

        if (requestedWorldMoveX > MOVE_DIRECTION_DEADZONE && leadX >= WALK_REQUEST_LEAD) {
            dx = 1;
        } else if (requestedWorldMoveX < -MOVE_DIRECTION_DEADZONE && leadX <= -WALK_REQUEST_LEAD) {
            dx = -1;
        }
        if (requestedWorldMoveZ > MOVE_DIRECTION_DEADZONE && leadZ >= WALK_REQUEST_LEAD) {
            dz = 1;
        } else if (requestedWorldMoveZ < -MOVE_DIRECTION_DEADZONE && leadZ <= -WALK_REQUEST_LEAD) {
            dz = -1;
        }

        if (dx == 0 && dz == 0) {
            return;
        }

        int targetTileX = collisionTileX + dx;
        int targetTileY = collisionTileY + dz;

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

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
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
         * Shared controller already resolved screen input into Matrix world X/Z.
         * The existing neutral-camera libsm64 adapter encodes (-X, -Z).
         * Keep source-game physics native; do not rotate by camera or actor again.
         */
        float worldMoveX = controls.worldMoveX;
        float worldMoveZ = controls.worldMoveZ;
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
        if (!clippingWasEnabled) {
            freeMovement.restore(player);
            Class240 position = player.method5394().aClass240_2647;
            player.method5395(position.aFloat2653, groundY, position.aFloat2657);
        } else {
            player.method5395(groundX, groundY, groundZ);
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
        freeMovement.reset();
        MarioWeaponCombat.reset();
        baselineValid = false;
        appliedPositionValid = false;
        nativePresentationValid = false;
        pendingAuthorityTileValid = false;
        groundX = 0.0F;
        groundY = 0.0F;
        groundZ = 0.0F;
        nativeGroundY = 0.0F;
        lastAppliedX = 0.0F;
        lastAppliedY = 0.0F;
        lastAppliedZ = 0.0F;
        lastNativePresentationX = 0.0F;
        lastNativePresentationZ = 0.0F;
        requestedWorldMoveX = 0.0F;
        requestedWorldMoveZ = 0.0F;
        collisionTileX = Integer.MIN_VALUE;
        collisionTileY = Integer.MIN_VALUE;
        pendingAuthorityTileX = Integer.MIN_VALUE;
        pendingAuthorityTileY = Integer.MIN_VALUE;
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
