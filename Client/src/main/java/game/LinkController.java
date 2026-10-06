package game;

/**
 * Matrix3 client-thread adapter for Link-mode liboot input/state.
 *
 * AlternateCharacterController owns horizontal input, free/clipped movement
 * and movement resets. liboot owns animation/action state; this adapter only
 * exchanges native input/position/scale and preserves Link combat.
 */
public final class LinkController {

    private static final long MAX_ARMED_NATIVE_TICKS = 12L;
    private static final float CONTACT_FRAME = positiveFloatProperty(
            "matrix3.oot.combatContactFrame", 2.0F);

    private static boolean modeWasLink;
    private static boolean spaceReleaseRequired;
    private static boolean attackReleaseRequired;
    private static boolean targetReleaseRequired;

    private LinkController() {
    }

    static void tickLinkDriver() {
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
        AlternateCharacterController.MovementInput input = publishControls(player, controls);
        OotBridgeSession.LinkFrame frame = OotBridgeSession.getLatestFrame();
        OotBridgeSession.NativePosition position = OotBridgeSession.getInterpolatedPosition();
        LinkCharacterFit.Profile fit = frame == null ? null : LinkCharacterFit.resolve(player, frame);
        if (position != null && fit != null) {
            AlternateCharacterController.applyHorizontalMovement(
                    AlternateCharacterController.CharacterId.LINK, player,
                    position.x, position.z, fit.scale, input);
        }
    }

    private static AlternateCharacterController.MovementInput publishControls(Player player,
            AlternateCharacterController.ControlState controls) {
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
         * Matrix target acquisition is shared for every imported character.
         * Link only consumes the resulting direction as liboot's live camera/look
         * basis while native Z remains pressed.
         */
        AlternateCharacterController.PlanarDirection inputForward =
                AlternateCharacterCombatBridge.updateTargeting(
                        player, buttonZ, controls.cameraForward);
        if (inputForward == null) {
            inputForward = controls.cameraForward;
        }

        /*
         * Link contributes only native action/animation evidence. Shared combat
         * owns attack-edge arming, one-shot protection, target selection and the
         * common server-authoritative manual-melee request.
         */
        OotBridgeSession.LinkFrame frame = OotBridgeSession.getLatestFrame();
        AlternateCharacterCombatBridge.updateNativeMeleeContact(
                player,
                AlternateCharacterController.CharacterId.LINK,
                buttonB,
                controls.cameraForward,
                frame == null ? -1L : frame.sequence,
                frame == null ? 0 : frame.action,
                frame == null ? 0 : frame.animId,
                frame == null ? 0.0F : frame.animFrame,
                CONTACT_FRAME,
                MAX_ARMED_NATIVE_TICKS);

        AlternateCharacterController.MovementInput input =
                AlternateCharacterController.movementInput(
                        AlternateCharacterController.CharacterId.LINK, controls, inputForward);
        OotBridgeSession.setInput(
                input.cameraX,
                input.cameraZ,
                input.stickX,
                input.stickY,
                buttonA,
                buttonB,
                buttonZ);
        return input;
    }

    private static void enterLinkMode() {
        resetMovement();
        AlternateCharacterCombatBridge.reset();
        AlternateCharacterController.beginHorizontalMovement(
                AlternateCharacterController.CharacterId.LINK,
                Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976);
        AlternateCharacterInputKeyboard.install();
        captureHeldActionGuards();
        OotBridgeSession.start();
        System.out.println(
                "[OoT] Controls: camera-relative WASD move, Space A/action, F B/sword, Shift hold Z-target");
        System.out.println(
                "[OoT] Movement: shared horizontal controller; sword damage is server-authoritative at native contact.");
    }

    private static void exitLinkMode() {
        AlternateCharacterController.restoreHorizontalMovement(
                AlternateCharacterController.CharacterId.LINK,
                Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976);
        AlternateCharacterCombatBridge.reset();
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
        AlternateCharacterController.restoreHorizontalMovement(
                AlternateCharacterController.CharacterId.LINK,
                Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976);
        System.out.println("[OoT Bridge] Falling back to RuneScape control: " + reason);
        AlternateCharacterCombatBridge.reset();
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
        AlternateCharacterController.resetHorizontalMovement(
                AlternateCharacterController.CharacterId.LINK);
    }

    private static float positiveFloatProperty(String name, float defaultValue) {
        try {
            String value = System.getProperty(name);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            float parsed = Float.parseFloat(value.trim());
            return Float.isNaN(parsed) || Float.isInfinite(parsed) || parsed < 0.0F
                    ? defaultValue : parsed;
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }
}
