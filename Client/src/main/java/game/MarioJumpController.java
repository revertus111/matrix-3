package game;

/**
 * Matrix3-native vertical movement proof for the Mario controller workstream.
 *
 * This remains presentation-only: it moves the local client's player transform
 * above the existing RuneScape terrain baseline. It does not change plane,
 * pathfinding, clipping, server position authority, animation, or combat.
 *
 * Mario behavior is gated by PlayerControllerMode; normal RuneScape control is
 * the default and Space does nothing here until Mario mode is explicitly active.
 */
public final class MarioJumpController {

    // Class549_Sub1 maps java.awt.event.KeyEvent.VK_SPACE (32) to Matrix3 key 83.
    private static final int INTERNAL_SPACE_KEY = 83;

    // Initial proof constants. These are Matrix3 scene units/second, not SM64
    // units; later Mario movement work will translate/tune against the decomp.
    private static final float JUMP_VELOCITY = 1600.0F;
    private static final float GRAVITY = 4200.0F;
    private static final float DEFAULT_DT = 0.020F;
    private static final float MAX_DT = 0.050F;
    private static final float EXTERNAL_Y_EPSILON = 0.5F;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static long lastTickNanos;

    private static Player lastPlayer;
    private static boolean modeWasMario;
    private static boolean spaceWasDown;
    private static boolean airborne;
    private static boolean baselineValid;
    private static boolean appliedYValid;

    private static float groundY;
    private static float heightOffset;
    private static float verticalVelocity;
    private static float lastAppliedY;

    private MarioJumpController() {
    }

    /**
     * Runs from Matrix3's established live viewport tick.
     *
     * Higher altitude uses a smaller scene-Y value in this client coordinate
     * system, matching the stock camera's terrainHeight - cameraHeight path.
     */
    public static void tick() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player != lastPlayer) {
            /*
             * A newly created local-player object is a controller lifecycle
             * boundary. Never carry Mario/airborne state across login/relog.
             */
            resetPhysics();
            spaceWasDown = keyDown(INTERNAL_SPACE_KEY);
            modeWasMario = false;
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

        boolean spaceDown = keyDown(INTERNAL_SPACE_KEY);
        if (player == null) {
            resetPhysics();
            spaceWasDown = spaceDown;
            return;
        }

        if (spaceDown && !spaceWasDown && !airborne) {
            beginJump(player);
        }
        spaceWasDown = spaceDown;

        if (!airborne) {
            return;
        }

        Class240 position = player.method5394().aClass240_2647;
        float currentY = position.aFloat2656;

        if (!baselineValid) {
            groundY = currentY;
            baselineValid = true;
        } else if (appliedYValid && Math.abs(currentY - lastAppliedY) > EXTERNAL_Y_EPSILON) {
            /*
             * A normal Matrix3 movement/terrain owner changed Y after our last
             * viewport tick. Treat that value as the new terrain baseline rather
             * than fighting it. X/Z and all normal movement ownership stay intact.
             */
            groundY = currentY;
        }

        float dt = consumeDeltaSeconds();
        heightOffset += verticalVelocity * dt;
        verticalVelocity -= GRAVITY * dt;

        if (heightOffset <= 0.0F && verticalVelocity <= 0.0F) {
            heightOffset = 0.0F;
            verticalVelocity = 0.0F;
            airborne = false;
        }

        float targetY = groundY - heightOffset;
        player.method5395(position.aFloat2653, targetY, position.aFloat2657);
        lastAppliedY = targetY;
        appliedYValid = true;

        if (!airborne) {
            lastTickNanos = 0L;
            baselineValid = false;
            appliedYValid = false;
        }
    }

    private static void enterMarioMode() {
        resetPhysics();
        Sm64BridgeProbe.startOnce();
        // Enabling while Space is already held must not manufacture a jump.
        spaceWasDown = keyDown(INTERNAL_SPACE_KEY);
    }

    private static void exitMarioMode(Player player) {
        if (player != null && airborne && baselineValid) {
            Class240 position = player.method5394().aClass240_2647;
            player.method5395(position.aFloat2653, groundY, position.aFloat2657);
        }
        resetPhysics();
        spaceWasDown = keyDown(INTERNAL_SPACE_KEY);
    }

    private static void beginJump(Player player) {
        Class240 position = player.method5394().aClass240_2647;
        groundY = position.aFloat2656;
        baselineValid = true;
        appliedYValid = false;
        heightOffset = 0.0F;
        verticalVelocity = JUMP_VELOCITY;
        airborne = true;
        lastTickNanos = System.nanoTime();
    }

    private static float consumeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastTickNanos == 0L) {
            lastTickNanos = now;
            return DEFAULT_DT;
        }

        float dt = (now - lastTickNanos) / 1000000000.0F;
        lastTickNanos = now;
        if (dt < 0.0F) {
            return 0.0F;
        }
        if (dt > MAX_DT) {
            return MAX_DT;
        }
        return dt;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }

    private static void resetPhysics() {
        airborne = false;
        baselineValid = false;
        appliedYValid = false;
        groundY = 0.0F;
        heightOffset = 0.0F;
        verticalVelocity = 0.0F;
        lastAppliedY = 0.0F;
        lastTickNanos = 0L;
    }

    public static boolean isAirborne() {
        return airborne;
    }

    public static float getHeightOffset() {
        return heightOffset;
    }
}
