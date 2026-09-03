package game;

/**
 * Read-only bootstrap for the Owner RoTS Deep Scan GFX definition loader.
 * Uses the already-live Matrix3 config archive and does not render or mutate GFX.
 */
public final class ClientConsoleRotsGfxBootstrap {

    private ClientConsoleRotsGfxBootstrap() {
    }

    public static boolean ensureReady() {
        if (ClientConsoleRotsBridge.isDeepReady()) {
            return true;
        }

        Class639_Sub11 renderLoader = Class197.aClass639_Sub11_2359;
        if (renderLoader == null || renderLoader.aClass248_8285 == null) {
            return false;
        }

        // JS5 config group 13 is the spot-animation/GFX definition group for this cache format.
        Class248 configArchive = renderLoader.aClass248_8285;
        Class248 modelArchive = GraphicsDefinition.aClass248_8442;
        if (modelArchive == null) {
            // Deep Scan only decodes definition fields; it never asks this factory to render a model.
            modelArchive = configArchive;
        }

        Interface18 definitions = new Class639(null, null, configArchive,
                JS5ConfigGroup.aClass220_2537, 64, new Class452_Sub1(modelArchive));
        ClientConsoleRotsBridge.registerGraphicsDefinitions(definitions);
        return ClientConsoleRotsBridge.isDeepReady();
    }
}
