package game;

/**
 * Construction lifecycle/controller for Matrix3's detached Class24/Class411
 * developer camera.
 *
 * Construction owns only the build-mode controls. The camera object/render path
 * remains Matrix3's existing Class411 free-camera path.
 */
public final class ConstructionBuildCamera {

    private static volatile boolean active;
    private static volatile boolean ownsFreeCamera;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static int lastMouseX;
    private static int lastMouseY;
    private static boolean tickReported;
    private static boolean inputReported;
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
        tickReported = false;
        inputReported = false;
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

            updateMouseLook(lookController, orientation);

            // Mirror Matrix3's existing detached-camera movement math, but read
            // Construction keys from the live keyboard state at the render seam.
            orientation.method3175();
            float step = movementStep();

            boolean forward = keyDown(98) || keyDown(33); // Up or W
            boolean backward = keyDown(99) || keyDown(49); // Down or S
            boolean left = keyDown(96) || keyDown(48); // Left or A
            boolean right = keyDown(97) || keyDown(50); // Right or D
            boolean up = keyDown(34); // E
            boolean down = keyDown(32); // Q

            if (!inputReported && (forward || backward || left || right || up || down)) {
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

            if (forward) {
                addRelative(position, orientation, 0.0F, 0.0F, step);
            }
            if (backward) {
                addRelative(position, orientation, 0.0F, 0.0F, -step);
            }
            if (left) {
                addRelative(position, orientation, -step, 0.0F, 0.0F);
            }
            if (right) {
                addRelative(position, orientation, step, 0.0F, 0.0F);
            }

            // Matrix3 world Y is the camera's vertical axis in this controller.
            if (up) {
                position.aFloat2656 += step;
            }
            if (down) {
                position.aFloat2656 -= step;
            }

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
     * No inertia exists in this acceptance build yet, so there is no velocity
     * accumulator to clear. This remains the future click-to-stop seam.
     */
    public static void stopMovement() {
        // No-op until smooth velocity is added on the verified live tick.
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

    private static void addRelative(Class240 position, Class230 orientation,
            float x, float y, float z) {
        Class240 delta = Class240.method3316(x, y, z);
        delta.method3288(orientation);
        delta.aFloat2656 *= -1.0F;
        position.method3305(delta);
    }

    private static float movementStep() {
        if (keyDown(82)) {
            return 8.0F; // Ctrl precision
        }
        if (keyDown(81)) {
            return 60.0F; // Shift fast
        }
        return 25.0F;
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
