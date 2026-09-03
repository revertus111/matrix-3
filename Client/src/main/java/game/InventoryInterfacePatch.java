package game;

/**
 * Narrow runtime layout override for Matrix3's expanded 36-slot inventory.
 *
 * Runtime evidence confirms the server/client container accepts slots 29-36,
 * while the stock NIS inventory components still expose only the original
 * 28 visual slots. Keep the cache definitions untouched and extend only the
 * two known inventory components after their interface groups are loaded.
 */
public final class InventoryInterfacePatch {

    private static final int INVENTORY_INTERFACE = 1473;
    private static final int INVENTORY_COMPONENT = 34;
    private static final int INVENTORY_MENU_INTERFACE = 1474;
    private static final int INVENTORY_MENU_COMPONENT = 15;

    private static final int EXTRA_GRID_ROWS = 2;
    private static final int EXTRA_PIXEL_HEIGHT = 72;

    /* InterfaceDefinitions.method1100 raw-height encoder and its modular inverse. */
    private static final int RAW_HEIGHT_ENCODER = 200498991;
    private static final int RAW_HEIGHT_DECODER = 1647331279;

    private static InterfaceDefinitions patchedInventoryComponent;
    private static InterfaceDefinitions patchedMenuInventoryComponent;

    private InventoryInterfacePatch() {
    }

    public static void apply() {
        patchedInventoryComponent = apply(
                INVENTORY_INTERFACE,
                INVENTORY_COMPONENT,
                patchedInventoryComponent);
        patchedMenuInventoryComponent = apply(
                INVENTORY_MENU_INTERFACE,
                INVENTORY_MENU_COMPONENT,
                patchedMenuInventoryComponent);
    }

    private static InterfaceDefinitions apply(
            int interfaceId,
            int componentId,
            InterfaceDefinitions alreadyPatched) {
        if (interfaceId < 0
                || Class572_Sub12_Sub2.aBoolArray11253 == null
                || interfaceId >= Class572_Sub12_Sub2.aBoolArray11253.length
                || !Class572_Sub12_Sub2.aBoolArray11253[interfaceId]
                || Class534.aClass83Array5975 == null
                || interfaceId >= Class534.aClass83Array5975.length) {
            return alreadyPatched;
        }

        Class83 group = Class534.aClass83Array5975[interfaceId];
        if (group == null) {
            return alreadyPatched;
        }

        InterfaceDefinitions[] components = group.method1257(0);
        if (components == null || componentId < 0 || componentId >= components.length) {
            return alreadyPatched;
        }

        InterfaceDefinitions component = components[componentId];
        if (component == null || component == alreadyPatched) {
            return alreadyPatched;
        }

        int rawHeight = component.anInt761 * RAW_HEIGHT_DECODER;
        if (rawHeight <= 0) {
            return alreadyPatched;
        }

        /*
         * Type-2 style inventory grids use the generic raw-height field as a row
         * count (normally 7). Pixel-sized NIS containers use the same field as
         * geometry. Support both representations without touching unrelated
         * component fields.
         */
        int expandedHeight = rawHeight <= 16
                ? rawHeight + EXTRA_GRID_ROWS
                : rawHeight + EXTRA_PIXEL_HEIGHT;
        component.anInt761 = expandedHeight * RAW_HEIGHT_ENCODER;
        return component;
    }
}
