package game;

/**
 * Matrix3 client-thread presentation/input adapter for Mario-mode native SM64 state.
 *
 * The legacy class name is retained because the established viewport hook calls
 * this owner. Java no longer simulates Mario gravity here: libsm64 owns Mario's
 * movement/action simulation while this class forwards Matrix input and maps the
 * published native vertical state onto Matrix3's verified local-player transform.
 */
public final class MarioJumpController {

    // Class549_Sub1 normalized-key mappings, verified from anIntArray8901.
    private static final int INTERNAL_W_KEY = 33;
    private static final int INTERNAL_A_KEY = 48;
    private static final int INTERNAL_S_KEY = 49;
    private static final int INTERNAL_D_KEY = 50;
    private static final int INTERNAL_ATTACK_KEY = 51; // F -> SM64 B
    private static final int INTERNAL_CROUCH_KEY = 81; // Shift -> SM64 Z
    private static final int INTERNAL_SPACE_KEY = 83;  // Space -> SM64 A
    private static final float DIAGONAL_STICK_SCALE = 0.70710677F;

    // Initial presentation calibration only. Native SM64 remains physics owner.
    // Tune from runtime evidence before collision adapter work treats this as final.
    private static final float DEFAULT_SM64_TO_MATRIX_Y_SCALE = 3.0F;
    private static final float SM64_TO_MATRIX_Y_SCALE = resolveVerticalScale();
    private static final float EXTERNAL_Y_EPSILON = 0.5F;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static Player lastPlayer;
    private static boolean modeWasMario;
    private static boolean spaceReleaseRequired;
    private static boolean attackReleaseRequired;
    private static boolean crouchReleaseRequired;
    private static boolean baselineValid;
    private static boolean appliedYValid;

    private static float groundY;
    private static float nativeGroundY;
    private static float lastAppliedY;

    private MarioJumpController() {
    }

    /**
     * Runs from Matrix3's established live viewport tick. Native simulation runs
     * independently at fixed 30 Hz; scene mutation remains on this client thread.
     */
    public static void tick() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player != lastPlayer) {
            Sm64BridgeSession.stop();
            resetPresentation();
            modeWasMario = false;
            spaceReleaseRequired = false;
            attackReleaseRequired = false;
            crouchReleaseRequired = false;
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
            return;
        }

        if (player == null) {
            fallbackToRuneScape(null, "local player unavailable");
            return;
        }

        if (Sm64BridgeSession.hasFailed()) {
            String reason = Sm64BridgeSession.getFailureReason();
            fallbackToRuneScape(player, reason == null ? "native session failed" : reason);
            return;
        }

        Float latestNativeY = Sm64BridgeSession.getLatestY();
        if (!baselineValid) {
            // Do not feed movement/actions until Matrix and native baselines exist.
            Sm64BridgeSession.setInput(0.0F, 0.0F, false, false, false);
            if (latestNativeY == null) {
                return;
            }

            Class240 position = player.method5394().aClass240_2647;
            groundY = position.aFloat2656;
            nativeGroundY = latestNativeY.floatValue();
            baselineValid = true;
            appliedYValid = false;
            System.out.println("[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale "
                    + SM64_TO_MATRIX_Y_SCALE + ")");
            System.out.println("[Mario] Controls: WASD move, Space jump, F attack, Shift crouch/ground-pound");
        }

        publishControls();

        Float interpolatedNativeY = Sm64BridgeSession.getInterpolatedY();
        if (interpolatedNativeY == null) {
            return;
        }

        Class240 position = player.method5394().aClass240_2647;
        float currentY = position.aFloat2656;
        if (appliedYValid && Math.abs(currentY - lastAppliedY) > EXTERNAL_Y_EPSILON) {
            /*
             * Until real RuneScape terrain is streamed into libsm64, preserve
             * Matrix's terrain/movement Y as the presentation baseline instead
             * of fighting a normal client-owned height update.
             */
            groundY = currentY;
        }

        float nativeHeight = interpolatedNativeY.floatValue() - nativeGroundY;
        if (nativeHeight < 0.0F) {
            nativeHeight = 0.0F;
        }

        // Matrix altitude increases as scene-Y decreases.
        float targetY = groundY - nativeHeight * SM64_TO_MATRIX_Y_SCALE;
        player.method5395(position.aFloat2653, targetY, position.aFloat2657);
        lastAppliedY = targetY;
        appliedYValid = true;
    }

    private static void publishControls() {
        float stickX = (keyDown(INTERNAL_D_KEY) ? 1.0F : 0.0F)
                - (keyDown(INTERNAL_A_KEY) ? 1.0F : 0.0F);
        float stickY = (keyDown(INTERNAL_W_KEY) ? 1.0F : 0.0F)
                - (keyDown(INTERNAL_S_KEY) ? 1.0F : 0.0F);
        if (stickX != 0.0F && stickY != 0.0F) {
            stickX *= DIAGONAL_STICK_SCALE;
            stickY *= DIAGONAL_STICK_SCALE;
        }

        boolean spaceDown = keyDown(INTERNAL_SPACE_KEY);
        if (spaceReleaseRequired && !spaceDown) {
            spaceReleaseRequired = false;
        }
        boolean buttonA = !spaceReleaseRequired && spaceDown;

        boolean attackDown = keyDown(INTERNAL_ATTACK_KEY);
        if (attackReleaseRequired && !attackDown) {
            attackReleaseRequired = false;
        }
        boolean buttonB = !attackReleaseRequired && attackDown;

        boolean crouchDown = keyDown(INTERNAL_CROUCH_KEY);
        if (crouchReleaseRequired && !crouchDown) {
            crouchReleaseRequired = false;
        }
        boolean buttonZ = !crouchReleaseRequired && crouchDown;

        Sm64BridgeSession.setInput(stickX, stickY, buttonA, buttonB, buttonZ);
    }

    private static void enterMarioMode() {
        resetPresentation();
        captureHeldActionGuards();
        Sm64BridgeSession.start();
    }

    private static void exitMarioMode(Player player) {
        restoreGroundBaseline(player);
        Sm64BridgeSession.stop();
        resetPresentation();
        captureHeldActionGuards();
    }

    private static void fallbackToRuneScape(Player player, String reason) {
        System.out.println("[SM64 Bridge] Falling back to RuneScape control: " + reason);
        restoreGroundBaseline(player);
        Sm64BridgeSession.stop();
        resetPresentation();
        captureHeldActionGuards();
        modeWasMario = false;
        PlayerControllerMode.setMode(PlayerControllerMode.Mode.RUNESCAPE);
    }

    private static void captureHeldActionGuards() {
        spaceReleaseRequired = keyDown(INTERNAL_SPACE_KEY);
        attackReleaseRequired = keyDown(INTERNAL_ATTACK_KEY);
        crouchReleaseRequired = keyDown(INTERNAL_CROUCH_KEY);
    }

    private static void restoreGroundBaseline(Player player) {
        if (player == null || !baselineValid) {
            return;
        }
        Class240 position = player.method5394().aClass240_2647;
        player.method5395(position.aFloat2653, groundY, position.aFloat2657);
    }

    private static float resolveVerticalScale() {
        String configured = System.getProperty("matrix3.sm64.verticalScale");
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_SM64_TO_MATRIX_Y_SCALE;
        }
        try {
            float parsed = Float.parseFloat(configured.trim());
            if (parsed > 0.0F && !Float.isNaN(parsed) && !Float.isInfinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the safe default.
        }
        System.out.println("[SM64 Bridge] Invalid matrix3.sm64.verticalScale='" + configured
                + "'; using " + DEFAULT_SM64_TO_MATRIX_Y_SCALE);
        return DEFAULT_SM64_TO_MATRIX_Y_SCALE;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }

    private static void resetPresentation() {
        baselineValid = false;
        appliedYValid = false;
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
