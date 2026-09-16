package game;

/**
 * Read-only readiness helper for the Owner RoTS Deep Scan GFX definition loader.
 * The authentic Matrix3 GFX loader registers itself from Class639 construction.
 */
public final class ClientConsoleRotsGfxBootstrap {

    private ClientConsoleRotsGfxBootstrap() {
    }

    public static boolean ensureReady() {
        return ClientConsoleRotsBridge.isDeepReady();
    }
}
