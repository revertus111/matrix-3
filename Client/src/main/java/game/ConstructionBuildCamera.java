package game;

/**
 * Construction lifecycle owner for Matrix3's proven detached developer camera.
 *
 * Runtime evidence established that the working free camera is Class24's
 * separate Class411_Sub1 path. Construction activates that same camera instead
 * of writing renderer globals or opening the incompatible Orb interface.
 */
public final class ConstructionBuildCamera {

    private static volatile boolean active;
    private static volatile boolean ownsFreeCamera;

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
        System.out.println("[ConstructionBuildCamera] ENTER source=Class24/Class411"
                + " existing=" + existingFreeCamera
                + " activeNow=" + IncomingPacket.method4113((byte) 0)
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

        System.out.println("[ConstructionBuildCamera] EXIT"
                + " freeCameraWasActive=" + freeCameraWasActive
                + " activeNow=" + IncomingPacket.method4113((byte) 0));
        return "Construction Free Build camera closed.";
    }

    /**
     * The proven Class24 free camera has no movement inertia yet, so there is no
     * velocity accumulator to clear. Kept as the Construction interaction seam
     * for later smooth-movement polish.
     */
    public static void stopMovement() {
        // No-op until the proven Class24 path receives Construction smoothing.
    }

    static float movementStep() {
        if (!active) {
            return 25.0F;
        }
        if (Class108.aClass549_1426.method6514(82, (byte) 1)) {
            return 8.0F; // Ctrl precision
        }
        if (Class108.aClass549_1426.method6514(81, (byte) 1)) {
            return 60.0F; // Shift fast
        }
        return 25.0F;
    }
}
