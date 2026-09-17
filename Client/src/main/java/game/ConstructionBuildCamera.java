package game;

/**
 * Construction build-camera lifecycle placeholder.
 *
 * Runtime verification proved Matrix3's legacy Orb-of-Oculus interface path is
 * incompatible with the current client/cache: entering it installs interface
 * component 57 under root 475 and crashes the client with an
 * ArrayIndexOutOfBoundsException while decoding SET_INTERFACE.
 *
 * Keep the Construction camera lifecycle seam client-owned, but do not request
 * the obsolete server/interface path. A modern Free Build camera must be wired
 * only after the current client camera/input owner is verified-static.
 */
public final class ConstructionBuildCamera {

    private ConstructionBuildCamera() {
    }

    public static boolean isRequested() {
        return false;
    }

    public static String enter() {
        return "Construction build camera disabled pending current-client camera seam.";
    }

    public static String exit() {
        return "Construction build camera is not active.";
    }
}
