package game;

/**
 * Matrix3-native activation boundary for alternate local-player controllers.
 *
 * Normal RuneScape control remains the default. Mario behavior is opt-in and
 * currently toggled with Ctrl+M for developer testing. This class owns only the
 * local controller-mode selection; it does not replace Matrix3 movement,
 * clipping, server authority, camera ownership, or rendering.
 */
public final class PlayerControllerMode {

    public enum Mode {
        RUNESCAPE,
        MARIO
    }

    // Class549_Sub1 normalized-key mappings: Ctrl=82, M=70.
    private static final int INTERNAL_CTRL_KEY = 82;
    private static final int INTERNAL_M_KEY = 70;

    private static Mode mode = Mode.RUNESCAPE;
    private static boolean toggleWasDown;
    private static int lastTickCycle = Integer.MIN_VALUE;

    private PlayerControllerMode() {
    }

    /**
     * Polls the existing Matrix3 held-key owner once per client cycle.
     * Ctrl+M uses rising-edge detection so holding the chord cannot oscillate
     * repeatedly between modes.
     */
    public static void tick() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        boolean toggleDown = keyDown(INTERNAL_CTRL_KEY) && keyDown(INTERNAL_M_KEY);
        if (toggleDown && !toggleWasDown) {
            setMode(mode == Mode.MARIO ? Mode.RUNESCAPE : Mode.MARIO);
        }
        toggleWasDown = toggleDown;
    }

    public static void setMode(Mode nextMode) {
        if (nextMode == null || nextMode == mode) {
            return;
        }
        mode = nextMode;
        System.out.println("[Mario] Controller mode: " + mode.name());
    }

    /**
     * Login/player-object transitions return to vanilla RuneScape control so an
     * old local-session controller cannot leak into a newly created player.
     */
    static void resetForPlayerLifecycle() {
        mode = Mode.RUNESCAPE;
        toggleWasDown = keyDown(INTERNAL_CTRL_KEY) && keyDown(INTERNAL_M_KEY);
        lastTickCycle = Integer.MIN_VALUE;
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean isMarioMode() {
        return mode == Mode.MARIO;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }
}
