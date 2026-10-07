package game;

/**
 * Matrix-owned action/presentation adapter for the current Java TP Link integration.
 *
 * AlternateCharacterController owns input, camera-relative movement, movement
 * timing, horizontal state and optional RuneScape clipping. TP Link contributes
 * authentic presentation clips, facing presentation and sword-contact timing.
 */
public final class TpLinkController {

    private static final float MOVE_EPSILON_SQ = 0.0001F;
    private static final float FACING_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FACING_FRAME_SECONDS = 0.050F;
    private static final Class230 FACING_ROTATION = new Class230(0.0F, 0.0F, 0.0F, 1.0F);

    private static boolean modeWasTpLink;
    private static boolean attackReleaseRequired;
    private static boolean targetReleaseRequired;
    private static boolean attackWasDown;

    private static long lastFacingTickNanos = Long.MIN_VALUE;
    private static boolean haveFacing;
    private static float facingYawDegrees;
    private static float targetYawDegrees;

    private static boolean attackActive;
    private static boolean attackSendsDamage;
    private static boolean swordFrameSeen;
    private static boolean contactConsumed;
    private static int lastSwordFrame = -1;
    private static String attackStatus = "Ready";

    private static boolean moving;
    private static boolean targeting;

    private TpLinkController() {
    }

    static void tickTpLinkDriver() {
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        boolean tpMode = PlayerControllerMode.isTpLinkMode();
        if (tpMode != modeWasTpLink) {
            if (tpMode) {
                enterTpLinkMode(player);
            } else {
                exitTpLinkMode(player);
            }
            modeWasTpLink = tpMode;
        }

        if (!tpMode) {
            return;
        }
        if (player == null) {
            fallbackToRuneScape("local player unavailable");
            return;
        }

        float facingFrameSeconds = facingFrameSeconds(System.nanoTime());
        AlternateCharacterController.ControlState controls =
                AlternateCharacterController.sampleControls();

        if (attackReleaseRequired && !controls.primaryAction) {
            attackReleaseRequired = false;
        }
        if (targetReleaseRequired && !controls.modifierAction) {
            targetReleaseRequired = false;
        }

        boolean attackDown = !attackReleaseRequired && controls.primaryAction;
        boolean targetDown = !targetReleaseRequired && controls.modifierAction;
        boolean attackEdge = attackDown && !attackWasDown;
        attackWasDown = attackDown;

        AlternateCharacterController.PlanarDirection facingBasis =
                AlternateCharacterCombatBridge.updateTargeting(
                        player, targetDown, controls.cameraForward);
        int lockedTarget = AlternateCharacterCombatBridge.getLockedTargetIndex();
        targeting = targetDown && lockedTarget >= 0;

        AlternateCharacterController.PlanarDirection movementBasis = targeting
                ? facingBasis : controls.cameraForward;
        AlternateCharacterController.MovementInput input =
                AlternateCharacterController.applyInputDrivenHorizontalMovement(
                        AlternateCharacterController.CharacterId.TP_LINK,
                        player,
                        controls,
                        movementBasis);

        moving = input.worldMoveX * input.worldMoveX
                + input.worldMoveZ * input.worldMoveZ > MOVE_EPSILON_SQ;

        if (targeting) {
            updateFacing(player, facingBasis, facingFrameSeconds);
        } else if (moving) {
            updateFacing(player,
                    new AlternateCharacterController.PlanarDirection(
                            input.worldMoveX, input.worldMoveZ),
                    facingFrameSeconds);
        }

        if (attackEdge && !attackActive) {
            startSwordAction(true);
        }
        updateSwordAction();
    }

    private static void enterTpLinkMode(Player player) {
        lastFacingTickNanos = Long.MIN_VALUE;
        moving = false;
        targeting = false;
        haveFacing = false;
        attackActive = false;
        swordFrameSeen = false;
        contactConsumed = false;
        lastSwordFrame = -1;
        attackStatus = "Ready";
        TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.AUTO);

        AlternateCharacterCombatBridge.reset();
        AlternateCharacterController.beginHorizontalMovement(
                AlternateCharacterController.CharacterId.TP_LINK, player);
        AlternateCharacterInputKeyboard.install();
        attackReleaseRequired = AlternateCharacterController.rawPrimaryDown();
        targetReleaseRequired = AlternateCharacterController.rawModifierDown();
        attackWasDown = attackReleaseRequired;

        System.out.println(
                "[TP] Controls: shared camera-relative WASD, F authentic sword, Shift target lock");
        System.out.println(
                "[TP] AlternateCharacterController owns movement/clipping; Matrix owns server-authoritative melee.");
    }

    private static void exitTpLinkMode(Player player) {
        AlternateCharacterController.restoreHorizontalMovement(
                AlternateCharacterController.CharacterId.TP_LINK, player);
        AlternateCharacterCombatBridge.reset();
        if (!PlayerControllerMode.isMarioMode() && !PlayerControllerMode.isLinkMode()) {
            AlternateCharacterInputKeyboard.uninstall();
        }
        if (attackActive) {
            finishSwordAction();
        } else {
            TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.AUTO);
        }
        moving = false;
        targeting = false;
        haveFacing = false;
        lastFacingTickNanos = Long.MIN_VALUE;
        attackReleaseRequired = false;
        targetReleaseRequired = false;
        attackWasDown = false;
    }

    private static void fallbackToRuneScape(String reason) {
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        AlternateCharacterController.restoreHorizontalMovement(
                AlternateCharacterController.CharacterId.TP_LINK, player);
        AlternateCharacterCombatBridge.reset();
        AlternateCharacterInputKeyboard.uninstall();
        if (attackActive) {
            finishSwordAction();
        }
        modeWasTpLink = false;
        System.out.println("[TP] Falling back to RuneScape control: " + reason);
        PlayerControllerMode.setMode(PlayerControllerMode.Mode.RUNESCAPE);
    }

    private static void updateFacing(Player player,
            AlternateCharacterController.PlanarDirection direction,
            float frameSeconds) {
        if (player == null || direction == null) {
            return;
        }
        float lengthSq = direction.x * direction.x + direction.z * direction.z;
        if (!(lengthSq > MOVE_EPSILON_SQ)) {
            return;
        }

        targetYawDegrees = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        if (!haveFacing) {
            facingYawDegrees = targetYawDegrees;
            haveFacing = true;
        } else {
            float delta = shortestDegrees(targetYawDegrees - facingYawDegrees);
            float maxStep = TpLinkWorkbench.getControllerTurnSpeed() * frameSeconds;
            if (delta > maxStep) {
                delta = maxStep;
            } else if (delta < -maxStep) {
                delta = -maxStep;
            }
            facingYawDegrees = shortestDegrees(facingYawDegrees + delta);
        }

        float radians = (float) Math.toRadians(facingYawDegrees);
        FACING_ROTATION.method3172(0.0F, 1.0F, 0.0F, radians);
        player.method5404(FACING_ROTATION);
    }

    private static void startSwordAction(boolean sendsDamage) {
        if (TpLinkWorkbench.getSwordFrames() <= 0) {
            attackStatus = "Sword clip unavailable";
            return;
        }
        attackActive = true;
        attackSendsDamage = sendsDamage;
        swordFrameSeen = false;
        contactConsumed = false;
        lastSwordFrame = -1;
        attackStatus = sendsDamage ? "Sword armed" : "Sword preview";
        TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.SWORD);
    }

    private static void updateSwordAction() {
        if (!attackActive) {
            return;
        }
        if (!"sword".equalsIgnoreCase(TpLinkWorkbench.getActiveClip())) {
            return;
        }

        int frame = TpLinkWorkbench.getActiveFrame();
        int frameCount = TpLinkWorkbench.getSwordFrames();
        if (!swordFrameSeen) {
            swordFrameSeen = true;
            lastSwordFrame = frame;
        }

        if (!contactConsumed && frame >= TpLinkWorkbench.getCombatContactFrame()) {
            boolean connected = !attackSendsDamage
                    || AlternateCharacterCombatBridge.requestPrimaryMeleeAttack();
            contactConsumed = true;
            attackStatus = attackSendsDamage
                    ? (connected ? "Contact sent" : "Swing missed")
                    : "Preview contact";
        }

        boolean wrapped = lastSwordFrame >= 0 && frame < lastSwordFrame;
        boolean reachedEnd = frameCount > 0 && frame >= frameCount - 1;
        lastSwordFrame = frame;
        if (wrapped || reachedEnd) {
            finishSwordAction();
        }
    }

    private static void finishSwordAction() {
        attackActive = false;
        attackSendsDamage = false;
        swordFrameSeen = false;
        contactConsumed = false;
        lastSwordFrame = -1;
        TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.AUTO);
        if (attackStatus.startsWith("Sword") || attackStatus.startsWith("Preview")) {
            attackStatus = "Ready";
        }
    }

    public static void playSwordPreview() {
        if (!PlayerControllerMode.isTpLinkMode()) {
            attackStatus = "Enter TP_LINK first";
            return;
        }
        if (!attackActive) {
            startSwordAction(false);
        }
    }

    public static void cancelSwordAction() {
        if (attackActive) {
            finishSwordAction();
        } else {
            TpLinkWorkbench.setPreviewAnimation(TpLinkWorkbench.PreviewAnimation.AUTO);
        }
        attackStatus = "Ready";
    }

    public static boolean isActive() {
        return PlayerControllerMode.isTpLinkMode();
    }

    public static boolean isMoving() {
        return moving;
    }

    public static boolean isTargeting() {
        return targeting;
    }

    public static int getLockedTargetIndex() {
        return AlternateCharacterCombatBridge.getLockedTargetIndex();
    }

    public static boolean isAttackActive() {
        return attackActive;
    }

    public static String getAttackStatus() {
        if (attackActive && "sword".equalsIgnoreCase(TpLinkWorkbench.getActiveClip())) {
            return attackStatus + " | frame " + TpLinkWorkbench.getActiveFrame()
                    + "/" + Math.max(0, TpLinkWorkbench.getSwordFrames() - 1);
        }
        return attackStatus;
    }

    public static float getFacingYawDegrees() {
        return facingYawDegrees;
    }

    private static float facingFrameSeconds(long now) {
        float seconds = FACING_FRAME_SECONDS;
        if (lastFacingTickNanos != Long.MIN_VALUE) {
            long delta = now - lastFacingTickNanos;
            if (delta > 0L) {
                seconds = delta / 1000000000.0F;
                if (seconds > MAX_FACING_FRAME_SECONDS) {
                    seconds = MAX_FACING_FRAME_SECONDS;
                }
            }
        }
        lastFacingTickNanos = now;
        return seconds;
    }

    private static float shortestDegrees(float value) {
        while (value > 180.0F) {
            value -= 360.0F;
        }
        while (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }
}
