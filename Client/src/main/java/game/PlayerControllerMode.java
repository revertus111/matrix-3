package game;

/**
 * Matrix3-native activation boundary for alternate local-player controllers.
 *
 * Normal RuneScape control remains the default. Mario and Link are opt-in
 * developer modes. This class owns only controller-mode selection; Matrix3 keeps
 * movement/clipping/server authority, camera ownership, and final rendering.
 */
public final class PlayerControllerMode {

    public enum Mode {
        RUNESCAPE,
        MARIO,
        LINK
    }

    // Class549_Sub1 normalized-key mappings: Ctrl=82, M=70, L=56.
    private static final int INTERNAL_CTRL_KEY = 82;
    private static final int INTERNAL_M_KEY = 70;
    private static final int INTERNAL_L_KEY = 56;

    private static Mode mode = Mode.RUNESCAPE;
    private static boolean marioToggleWasDown;
    private static boolean linkToggleWasDown;
    private static int lastTickCycle = Integer.MIN_VALUE;

    private PlayerControllerMode() {
    }

    /**
     * Polls the existing Matrix3 held-key owner once per client cycle. Rising-edge
     * detection keeps held chords from oscillating between modes.
     */
    public static void tick() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        boolean marioToggleDown = keyDown(INTERNAL_CTRL_KEY) && keyDown(INTERNAL_M_KEY);
        boolean linkToggleDown = keyDown(INTERNAL_CTRL_KEY) && keyDown(INTERNAL_L_KEY);
        if (marioToggleDown && !marioToggleWasDown) {
            setMode(mode == Mode.MARIO ? Mode.RUNESCAPE : Mode.MARIO);
        } else if (linkToggleDown && !linkToggleWasDown) {
            setMode(mode == Mode.LINK ? Mode.RUNESCAPE : Mode.LINK);
        }
        marioToggleWasDown = marioToggleDown;
        linkToggleWasDown = linkToggleDown;
    }

    public static void setMode(Mode nextMode) {
        if (nextMode == null || nextMode == mode) {
            return;
        }
        mode = nextMode;
        System.out.println("[Alternate Character] Controller mode: " + mode.name());
    }

    /**
     * Login/player-object transitions return to vanilla RuneScape control so an
     * old local-session controller cannot leak into a newly created player.
     */
    static void resetForPlayerLifecycle() {
        mode = Mode.RUNESCAPE;
        marioToggleWasDown = keyDown(INTERNAL_CTRL_KEY) && keyDown(INTERNAL_M_KEY);
        linkToggleWasDown = keyDown(INTERNAL_CTRL_KEY) && keyDown(INTERNAL_L_KEY);
        lastTickCycle = Integer.MIN_VALUE;
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean isMarioMode() {
        return mode == Mode.MARIO;
    }

    public static boolean isLinkMode() {
        return mode == Mode.LINK;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }
}
