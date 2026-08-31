package game;

public final class ClientConsoleBridge {

    private static final int RIGHTS_MULTIPLIER = -1550439133;

    private ClientConsoleBridge() {
    }

    public static boolean hasLocalPlayer() {
        return Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 != null;
    }

    public static String getDisplayName() {
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null) {
            return "Waiting for player";
        }
        if (player.displayName != null && !player.displayName.trim().isEmpty()) {
            return player.displayName;
        }
        if (player.username != null && !player.username.trim().isEmpty()) {
            return player.username;
        }
        return "Player loaded";
    }

    public static int getRights() {
        return RIGHTS_MULTIPLIER * client.rights;
    }

    public static String getRightsLabel() {
        int rights = getRights();
        if (rights >= 2) {
            return "Owner / Admin (" + rights + ")";
        }
        if (rights == 1) {
            return "Moderator (1)";
        }
        return "Player (" + rights + ")";
    }

    public static String getPlayerStateLabel() {
        return hasLocalPlayer() ? "Player loaded" : "Waiting for login";
    }
}
