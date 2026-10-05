package game;

/**
 * Matrix3 client-thread adapter for Link-mode liboot input/state.
 *
 * Default X/Z is local continuous native presentation. The optional RuneScape
 * clipping mode retains stock tile walking. liboot owns animation/action state;
 * free movement does not change the authoritative server position.
 */
public final class LinkController {

    private static final AlternateCharacterFreeMovement freeMovement =
            new AlternateCharacterFreeMovement();
    private static boolean clippingWasEnabled;

    private static final int WALK_RETRY_CYCLES = 10;
    private static final float MOVE_DIRECTION_DEADZONE = 0.20F;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static boolean modeWasLink;
    private static boolean spaceReleaseRequired;
    private static boolean attackReleaseRequired;
    private static boolean targetReleaseRequired;
    private static int lastWalkTargetX = Integer.MIN_VALUE;
    private static int lastWalkTargetY = Integer.MIN_VALUE;
    private static int lastWalkRequestCycle = Integer.MIN_VALUE;

    private LinkController() {
    }

    static void tickLinkDriver() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        boolean linkMode = PlayerControllerMode.isLinkMode();
        if (linkMode != modeWasLink) {
            if (linkMode) {
                enterLinkMode();
            } else {
                exitLinkMode();
            }
            modeWasLink = linkMode;
        }

        if (!linkMode) {
            return;
        }
        if (player == null) {
            fallbackToRuneScape("local player unavailable");
            return;
        }
        if (OotBridgeSession.hasFailed()) {
            String reason = OotBridgeSession.getFailureReason();
            fallbackToRuneScape(reason == null ? "native session failed" : reason);
            return;
        }

        AlternateCharacterController.ControlState controls =
                AlternateCharacterController.sampleControls();
        boolean clipping = AlternateCharacterController.isRuneScapeClippingEnabled();
        if (clipping != clippingWasEnabled) {
            freeMovement.restore(player);
            resetMovement();
            clippingWasEnabled = clipping;
        }
        publishControls(controls);
        if (clipping) {
            requestVanillaRuneScapeStep(player, controls);
        } else {
            OotBridgeSession.LinkFrame frame = OotBridgeSession.getLatestFrame();
            OotBridgeSession.NativePosition position = OotBridgeSession.getInterpolatedPosition();
            LinkCharacterFit.Profile fit = frame == null ? null : LinkCharacterFit.resolve(player, frame);
            if (position != null && fit != null) {
                freeMovement.apply(player, position.x, position.z, fit.scale);
            }
        }
    }

    private static void publishControls(AlternateCharacterController.ControlState controls) {
        if (spaceReleaseRequired && !controls.jump) {
            spaceReleaseRequired = false;
        }
        if (attackReleaseRequired && !controls.primaryAction) {
            attackReleaseRequired = false;
        }
        if (targetReleaseRequired && !controls.modifierAction) {
            targetReleaseRequired = false;
        }

        boolean buttonA = !spaceReleaseRequired && controls.jump;
        boolean buttonB = !attackReleaseRequired && controls.primaryAction;
        boolean buttonZ = !targetReleaseRequired && controls.modifierAction;

        /*
         * verified-static: liboot 25208734 uses host yaw atan2(camX, camZ)
         * plus Lib_GetControlStickData -> Math_Atan2S(relY, -relX).
         * Neutral camera and stick (-worldX, +worldZ) therefore target the
         * shared Matrix world vector without a second camera transform.
         */
        OotBridgeSession.setInput(
                0.0F,
                1.0F,
                -controls.worldMoveX,
                controls.worldMoveZ,
                buttonA,
                buttonB,
                buttonZ);
    }

    /**
     * Temporary Phase-1 movement handoff. Matrix's stock walk packet remains the
     * X/Z authority, so Link cannot bypass normal scene/server collision while the
     * later Matrix->OoT collision adapter is still unimplemented.
     */
    private static void requestVanillaRuneScapeStep(Player player,
            AlternateCharacterController.ControlState controls) {
        if (player == null || client.aClass195_8589 == null) {
            return;
        }

        int dx = directionStep(controls.worldMoveX);
        int dz = directionStep(controls.worldMoveZ);

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

        boolean targetChanged = targetTileX != lastWalkTargetX
                || targetTileY != lastWalkTargetY;
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

    private static void enterLinkMode() {
        resetMovement();
        clippingWasEnabled = AlternateCharacterController.isRuneScapeClippingEnabled();
        AlternateCharacterInputKeyboard.install();
        captureHeldActionGuards();
        OotBridgeSession.start();
        System.out.println(
                "[OoT] Controls: camera-relative WASD move, Space A/action, F B/sword, Shift Z-target");
        System.out.println(
                "[OoT] Movement: " + (clippingWasEnabled ? "RuneScape tile clipping" : "continuous free / clipping OFF")
                + "; Link B does not send an NPC attack packet yet.");
    }

    private static void exitLinkMode() {
        freeMovement.restore(Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976);
        OotBridgeSession.stop();
        /*
         * Link->Mario can transition in the same client tick. Mario installs the
         * shared keyboard wrapper before this driver observes its exit, so leave
         * that wrapper in place when Mario is the new owner.
         */
        if (!PlayerControllerMode.isMarioMode()) {
            AlternateCharacterInputKeyboard.uninstall();
        }
        resetMovement();
        captureHeldActionGuards();
    }

    private static void fallbackToRuneScape(String reason) {
        freeMovement.restore(Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976);
        System.out.println("[OoT Bridge] Falling back to RuneScape control: " + reason);
        OotBridgeSession.stop();
        AlternateCharacterInputKeyboard.uninstall();
        resetMovement();
        captureHeldActionGuards();
        modeWasLink = false;
        PlayerControllerMode.setMode(PlayerControllerMode.Mode.RUNESCAPE);
    }

    private static void captureHeldActionGuards() {
        spaceReleaseRequired = AlternateCharacterController.rawJumpDown();
        attackReleaseRequired = AlternateCharacterController.rawPrimaryDown();
        targetReleaseRequired = AlternateCharacterController.rawModifierDown();
    }

    private static void resetMovement() {
        freeMovement.reset();
        lastWalkTargetX = Integer.MIN_VALUE;
        lastWalkTargetY = Integer.MIN_VALUE;
        lastWalkRequestCycle = Integer.MIN_VALUE;
    }
}
