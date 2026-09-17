package game;

/**
 * Construction-only lifecycle bridge into Matrix3's existing Orb of Oculus
 * free-camera mode.
 *
 * This class does not own camera physics, player movement, scene picking, or
 * object placement. It only requests enter/exit through the same owner-only
 * client-to-server console bridge already used by Construction Dev placement.
 */
public final class ConstructionBuildCamera {

    private static volatile boolean requested;

    private ConstructionBuildCamera() {
    }

    public static boolean isRequested() {
        return requested;
    }

    public static String enter() {
        if (requested) {
            return "Construction build camera is already requested.";
        }
        String error = ClientConsoleBridge.queueConsoleCommand("itembrowser constructioncamera enter");
        if (error != null) {
            return error;
        }
        requested = true;
        return "Construction build camera requested.";
    }

    public static String exit() {
        if (!requested) {
            return "Construction build camera is not active.";
        }
        String error = ClientConsoleBridge.queueConsoleCommand("itembrowser constructioncamera exit");
        if (error != null) {
            return error;
        }
        requested = false;
        return "Construction build camera exit requested.";
    }
}
