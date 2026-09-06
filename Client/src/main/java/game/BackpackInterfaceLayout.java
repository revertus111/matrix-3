package game;

/**
 * Narrow layout repair for interface 671.
 *
 * The cache decodes the root at 485x299, but this client revision later
 * collapses it to 290x299. That causes the two 225px child panes to overlap
 * and leaves the real BoB item component exposed as a thin right-side strip.
 * Restore only that verified bad runtime state to the cache-defined width.
 */
final class BackpackInterfaceLayout {

    private static final int INTERFACE_ID = 671;
    private static final int ROOT_COMPONENT = 9;

    private static final int CACHE_WIDTH = 485;
    private static final int COLLAPSED_WIDTH = 290;
    private static final int CACHE_HEIGHT = 299;

    private static final int WIDTH_ENCODE_MULTIPLIER = 1256520373;
    private static final int WIDTH_DECODE_MULTIPLIER = 1473094557;
    private static final int HEIGHT_DECODE_MULTIPLIER = 1647331279;

    private BackpackInterfaceLayout() {
    }

    static void apply() {
        InterfaceDefinitions root;
        try {
            root = Class512.method6083((INTERFACE_ID << 16) | ROOT_COMPONENT, (short) 3691);
        } catch (RuntimeException e) {
            return;
        }
        if (root == null)
            return;

        int width = root.anInt760 * WIDTH_DECODE_MULTIPLIER;
        int height = root.anInt761 * HEIGHT_DECODE_MULTIPLIER;
        if (width != COLLAPSED_WIDTH || height != CACHE_HEIGHT)
            return;

        root.anInt760 = CACHE_WIDTH * WIDTH_ENCODE_MULTIPLIER;
        Class555.method6575(root, (short) 0);
    }
}
