package game;

/**
 * Mario-mode view over Matrix3's existing keyboard owner.
 *
 * The original keyboard implementation remains attached to the AWT component and
 * continues tracking physical key state. While Mario mode is active this wrapper
 * hides WASD only from normal Matrix held-key consumers (notably the detached
 * Construction camera), while Mario reads the original held state through
 * {@link #rawKeyDown(int)}. No second keyboard listener is installed.
 */
final class MarioInputKeyboard extends Class549 {

    private static final int INTERNAL_W_KEY = 33;
    private static final int INTERNAL_A_KEY = 48;
    private static final int INTERNAL_S_KEY = 49;
    private static final int INTERNAL_D_KEY = 50;

    private final Class549 delegate;

    private MarioInputKeyboard(Class549 delegate) {
        this.delegate = delegate;
    }

    static void install() {
        Class549 current = Class108.aClass549_1426;
        if (current == null || current instanceof MarioInputKeyboard) {
            return;
        }
        Class108.aClass549_1426 = new MarioInputKeyboard(current);
    }

    static void uninstall() {
        Class549 current = Class108.aClass549_1426;
        if (current instanceof MarioInputKeyboard) {
            Class108.aClass549_1426 = ((MarioInputKeyboard) current).delegate;
        }
    }

    static boolean rawKeyDown(int internalKey) {
        Class549 current = Class108.aClass549_1426;
        if (current instanceof MarioInputKeyboard) {
            Class549 raw = ((MarioInputKeyboard) current).delegate;
            return raw != null && raw.method6518(internalKey);
        }
        return current != null && current.method6518(internalKey);
    }

    private static boolean isMarioWasd(int internalKey) {
        return internalKey == INTERNAL_W_KEY
                || internalKey == INTERNAL_A_KEY
                || internalKey == INTERNAL_S_KEY
                || internalKey == INTERNAL_D_KEY;
    }

    @Override
    public void method6512() {
        delegate.method6512();
    }

    @Override
    public void method6513(int i) {
        delegate.method6513(i);
    }

    @Override
    public boolean method6514(int i, byte i_0_) {
        if (isMarioWasd(i)) {
            return false;
        }
        return delegate.method6514(i, i_0_);
    }

    @Override
    public Interface64 method6515(byte i) {
        return delegate.method6515(i);
    }

    @Override
    public Interface64 method6516() {
        return delegate.method6516();
    }

    @Override
    public Interface64 method6517() {
        return delegate.method6517();
    }

    @Override
    public boolean method6518(int i) {
        return delegate.method6518(i);
    }

    @Override
    public void method6519() {
        delegate.method6519();
    }

    @Override
    public boolean method6520(int i) {
        return delegate.method6520(i);
    }

    @Override
    public boolean method6521(int i) {
        return delegate.method6521(i);
    }

    @Override
    public void method6522() {
        delegate.method6522();
    }

    @Override
    public void method6523(int i) {
        delegate.method6523(i);
    }

    @Override
    public void method6524() {
        delegate.method6524();
    }

    @Override
    public void method6525() {
        delegate.method6525();
    }

    @Override
    public boolean method6526(int i) {
        return delegate.method6526(i);
    }
}
