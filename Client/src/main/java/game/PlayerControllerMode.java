package game;

/**
 * Matrix3-native activation boundary for alternate local-player controllers.
 *
 * Normal RuneScape control remains the default. Mario and OoT Link are opt-in
 * imported controllers; TP Link currently has a presentation-only mode so normal
 * RuneScape input/world authority stays untouched while its local DMK is rendered.
 */
public final class PlayerControllerMode {

    public enum Mode {
        RUNESCAPE,
        MARIO,
        LINK,
        TP_LINK
    }

    // Class549_Sub1 normalized-key mappings: Ctrl=82, Shift=81, M=70, L=56.
    private static final int INTERNAL_CTRL_KEY = 82;
    private static final int INTERNAL_SHIFT_KEY = 81;
    private static final int INTERNAL_M_KEY = 70;
    private static final int INTERNAL_L_KEY = 56;

    private static Mode mode = Mode.RUNESCAPE;
    private static boolean marioToggleWasDown;
    private static boolean linkChordWasDown;
    private static int lastTickCycle = Integer.MIN_VALUE;

    private PlayerControllerMode() {
    }

    /**
     * Polls the existing Matrix3 held-key owner once per client cycle. Rising-edge
     * detection keeps held chords from oscillating between modes.
     *
     * Ctrl+L keeps the established OoT Link controller. Ctrl+Shift+L activates
     * TP Link presentation only. The base Ctrl+L chord is latched until released,
     * so releasing Shift first cannot accidentally retrigger OoT Link.
     */
    public static void tick() {
        if (lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        boolean ctrl = keyDown(INTERNAL_CTRL_KEY);
        boolean shift = keyDown(INTERNAL_SHIFT_KEY);
        boolean marioToggleDown = ctrl && keyDown(INTERNAL_M_KEY);
        boolean linkChordDown = ctrl && keyDown(INTERNAL_L_KEY);
        if (marioToggleDown && !marioToggleWasDown) {
            setMode(mode == Mode.MARIO ? Mode.RUNESCAPE : Mode.MARIO);
        } else if (linkChordDown && !linkChordWasDown) {
            if (shift) {
                setMode(mode == Mode.TP_LINK ? Mode.RUNESCAPE : Mode.TP_LINK);
            } else {
                setMode(mode == Mode.LINK ? Mode.RUNESCAPE : Mode.LINK);
            }
        }
        marioToggleWasDown = marioToggleDown;
        linkChordWasDown = linkChordDown;
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
        boolean ctrl = keyDown(INTERNAL_CTRL_KEY);
        marioToggleWasDown = ctrl && keyDown(INTERNAL_M_KEY);
        linkChordWasDown = ctrl && keyDown(INTERNAL_L_KEY);
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

    public static boolean isTpLinkMode() {
        return mode == Mode.TP_LINK;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }
}
