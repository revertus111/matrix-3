package game;

/**
 * Construction lifecycle/controller for Matrix3's detached Class24/Class411
 * developer camera.
 *
 * Construction owns only the build-mode controls. The camera object/render path
 * remains Matrix3's existing Class411 free-camera path.
 */
public final class ConstructionBuildCamera {

    // Speeds preserve the accepted v1 feel while making motion time-based.
    private static final float NORMAL_SPEED = 1250.0F;
    private static final float FAST_SPEED = 3000.0F;
    private static final float PRECISION_SPEED = 400.0F;

    // Exponential response rates: higher = more immediate.
    private static final float ACCEL_RESPONSE = 10.0F;
    private static final float DECEL_RESPONSE = 7.0F;

    private static final float DEFAULT_DT = 0.020F;
    private static final float MIN_DT = 0.005F;
    private static final float MAX_DT = 0.050F;
    private static final float VELOCITY_EPSILON = 0.5F;

    private static volatile boolean active;
    private static volatile boolean ownsFreeCamera;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static int lastMouseX;
    private static int lastMouseY;
    private static long lastTickNanos;

    // World-space velocity in Matrix3 camera units/second.
    private static float velocityX;
    private static float velocityY;
    private static float velocityZ;

    // A placement click should stop motion even if a key is still physically held.
    // Movement can resume only after all movement keys are released once.
    private static boolean clickStopLatched;

    private static boolean tickReported;
    private static boolean inputReported;
    private static boolean stopReported;
    private static boolean failureReported;

    private ConstructionBuildCamera() {
    }

    public static boolean isRequested() {
        return active;
    }

    public static String enter() {
        if (active) {
            return "Construction Free Build camera is already active.";
        }
        if (Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return "Construction Free Build camera is waiting for the local player.";
        }

        boolean existingFreeCamera = IncomingPacket.method4113((byte) 0);
        ownsFreeCamera = !existingFreeCamera;

        if (ownsFreeCamera) {
            Class102_Sub5.method9948(
                    Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976.method273((byte) -63),
                    0);
        }

        active = true;
        lastTickCycle = Integer.MIN_VALUE;
        lastMouseX = Class26.aClass564_216.method6657((short) -1);
        lastMouseY = Class26.aClass564_216.method6658((byte) -1);
        lastTickNanos = System.nanoTime();
        clearVelocity();
        clickStopLatched = false;
        tickReported = false;
        inputReported = false;
        stopReported = false;
        failureReported = false;

        reportToServer("ENTER active=" + IncomingPacket.method4113((byte) 0)
                + " owned=" + ownsFreeCamera);
        return "Construction Free Build camera active.";
    }

    public static String exit() {
        if (!active) {
            return "Construction build camera is not active.";
        }

        boolean freeCameraWasActive = IncomingPacket.method4113((byte) 0);
        if (ownsFreeCamera && freeCameraWasActive) {
            RSSocket.method7604(0);
        }

        active = false;
        ownsFreeCamera = false;
        lastTickCycle = Integer.MIN_VALUE;
        lastTickNanos = 0L;
        clearVelocity();
        clickStopLatched = false;

        reportToServer("EXIT active=" + IncomingPacket.method4113((byte) 0));
        return "Construction Free Build camera closed.";
    }

    /**
     * Called from the live viewport before the active camera transform is
     * submitted. Guarded to one update per client cycle.
     */
    public static void tick() {
        if (!active || lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        if (!IncomingPacket.method4113((byte) 0) || Class24.aClass411_Sub1_158 == null) {
            if (!failureReported) {
                failureReported = true;
                reportToServer("FAIL detached-camera-not-active");
            }
            return;
        }

        try {
            Class423_Sub2 positionController = (Class423_Sub2) Class24.aClass411_Sub1_158.method4990((byte) -37);
            Class658_Sub2 lookController = (Class658_Sub2) Class24.aClass411_Sub1_158.method4991(-589573040);
            Class240 position = positionController.method5159((byte) -54);
            Class230 orientation = lookController.method8929((short) 11906);

            if (!tickReported) {
                tickReported = true;
                reportToServer("TICK live");
            }

            float dt = consumeDeltaSeconds();
            updateMouseLook(lookController, orientation);
            orientation.method3175();

            boolean forward = keyDown(98) || keyDown(33); // Up or W
            boolean backward = keyDown(99) || keyDown(49); // Down or S
            boolean left = keyDown(96) || keyDown(48); // Left or A
            boolean right = keyDown(97) || keyDown(50); // Right or D
            boolean up = keyDown(34); // E
            boolean down = keyDown(32); // Q

            boolean anyMovementKey = forward || backward || left || right || up || down;

            if (!inputReported && anyMovementKey) {
                inputReported = true;
                reportToServer("INPUT W=" + keyDown(33)
                        + " A=" + keyDown(48)
                        + " S=" + keyDown(49)
                        + " D=" + keyDown(50)
                        + " Q=" + keyDown(32)
                        + " E=" + keyDown(34)
                        + " shift=" + keyDown(81)
                        + " ctrl=" + keyDown(82));
            }

            if (clickStopLatched) {
                if (anyMovementKey) {
                    forward = false;
                    backward = false;
                    left = false;
                    right = false;
                    up = false;
                    down = false;
                    anyMovementKey = false;
                } else {
                    clickStopLatched = false;
                }
            }

            float localX = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
            float localZ = (forward ? 1.0F : 0.0F) - (backward ? 1.0F : 0.0F);
            float vertical = (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F);

            float magnitude = (float) Math.sqrt(localX * localX + localZ * localZ + vertical * vertical);
            if (magnitude > 1.0F) {
                localX /= magnitude;
                localZ /= magnitude;
                vertical /= magnitude;
            }

            float targetX = 0.0F;
            float targetY = 0.0F;
            float targetZ = 0.0F;

            if (anyMovementKey) {
                float speed = movementSpeed();

                Class240 direction = Class240.method3316(localX, 0.0F, localZ);
                direction.method3288(orientation);
                direction.aFloat2656 *= -1.0F;

                targetX = direction.aFloat2653 * speed;
                targetY = direction.aFloat2656 * speed + vertical * speed;
                targetZ = direction.aFloat2657 * speed;
            }

            float response = anyMovementKey ? ACCEL_RESPONSE : DECEL_RESPONSE;
            float blend = 1.0F - (float) Math.exp(-response * dt);

            velocityX += (targetX - velocityX) * blend;
            velocityY += (targetY - velocityY) * blend;
            velocityZ += (targetZ - velocityZ) * blend;

            if (!anyMovementKey) {
                velocityX = settle(velocityX);
                velocityY = settle(velocityY);
                velocityZ = settle(velocityZ);
            }

            position.aFloat2653 += velocityX * dt;
            position.aFloat2656 += velocityY * dt;
            position.aFloat2657 += velocityZ * dt;

            Class572_Sub17 target = new Class572_Sub17(
                    0,
                    (int) position.aFloat2653,
                    (int) position.aFloat2656,
                    (int) position.aFloat2657);
            positionController.method9278(target, (byte) 3);

            Class497 sceneBase = client.aClass613_8605.method7280((byte) -115);
            int baseX = sceneBase.localX * -2109597897 << 9;
            int baseY = sceneBase.localY * 417324155 << 9;
            Class24.aClass411_Sub1_158.method5012(
                    0.02F,
                    client.aClass613_8605.method7293(1134705460).anIntArrayArrayArray3141,
                    client.aClass613_8605.method7287((byte) -67),
                    baseX,
                    baseY,
                    (byte) -99);
        } catch (RuntimeException ex) {
            if (!failureReported) {
                failureReported = true;
                reportToServer("FAIL tick-exception-" + ex.getClass().getSimpleName());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Ground/build confirmation stops the camera immediately. If a movement key
     * is still held, movement remains latched off until all movement keys have
     * been released once, preventing the camera from restarting on the next tick.
     */
    public static void stopMovement() {
        if (!active) {
            return;
        }
        clearVelocity();
        clickStopLatched = true;
        if (!stopReported) {
            stopReported = true;
            reportToServer("STOP click");
        }
    }

    private static void updateMouseLook(Class658_Sub2 lookController, Class230 orientation) {
        int mouseX = Class26.aClass564_216.method6657((short) -1);
        int mouseY = Class26.aClass564_216.method6658((byte) -1);

        if (Class26.aClass564_216.method6654((byte) -102)) {
            Class230 pitch = Class230.method3210();
            pitch.method3172(
                    1.0F,
                    0.0F,
                    0.0F,
                    (float) (mouseY - lastMouseY) / 200.0F);
            orientation.method3189(pitch);

            Class240 upAxis = Class240.method3316(0.0F, 1.0F, 0.0F);
            upAxis.method3288(orientation);

            Class230 yaw = Class230.method3210();
            yaw.method3209(
                    upAxis,
                    (float) (lastMouseX - mouseX) / 200.0F);
            orientation.method3189(yaw);

            lookController.method8940(orientation, (byte) 4);
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    private static float consumeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastTickNanos == 0L) {
            lastTickNanos = now;
            return DEFAULT_DT;
        }

        float dt = (now - lastTickNanos) / 1000000000.0F;
        lastTickNanos = now;

        if (dt < MIN_DT) {
            return MIN_DT;
        }
        if (dt > MAX_DT) {
            return MAX_DT;
        }
        return dt;
    }

    private static float movementSpeed() {
        if (keyDown(82)) {
            return PRECISION_SPEED;
        }
        if (keyDown(81)) {
            return FAST_SPEED;
        }
        return NORMAL_SPEED;
    }

    private static float settle(float value) {
        return Math.abs(value) < VELOCITY_EPSILON ? 0.0F : value;
    }

    private static void clearVelocity() {
        velocityX = 0.0F;
        velocityY = 0.0F;
        velocityZ = 0.0F;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }

    private static void reportToServer(String message) {
        String safe = message == null ? "unknown" : message.replace(' ', '_');
        String error = ClientConsoleBridge.queueConsoleCommand(
                "itembrowser constructioncamera debug " + safe);
        if (error != null) {
            System.out.println("[ConstructionBuildCamera] " + message + " (server debug unavailable: " + error + ")");
        }
    }
}
