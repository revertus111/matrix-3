package game;

/**
 * Client-only Construction placement preview.
 *
 * The preview is rendered directly through Matrix3's normal object-model path
 * and is never registered with Class523. It therefore owns no collision,
 * persistence, server state, or scene-object lifecycle.
 */
public final class ConstructionGhostPreview {

    private static final int MODEL_FLAGS = 2048;
    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private ConstructionGhostPreview() {
    }

    /**
     * Draws the currently selected Construction piece at the hovered world tile.
     *
     * verified-static: ObjectDefinitions.method6057(...) is the same model factory
     * used by Matrix3 scene objects, and Model.method1375(...) is their live draw
     * path. This method intentionally never calls Class523 attach/remove methods.
     */
    static void render(Class523 scene, Class106 renderer) {
        if (scene == null || renderer == null || !ConstructionPlacementController.isHoverTracking()
                || !ConstructionPlacementController.isArmed()) {
            return;
        }

        ConstructionPlacementController.BuildPiece piece = ConstructionPlacementController.getSelectedPiece();
        ConstructionPlacementController.HoverTile tile = ConstructionPlacementController.getHoveredTile();
        Class613 region = client.aClass613_8605;
        if (piece == null || tile == null || region == null || region.method7285(0) != scene) {
            return;
        }

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            return;
        }

        int localX = tile.getWorldX() - sceneBase.localX * -2109597897;
        int localY = tile.getWorldY() - sceneBase.localY * 417324155;
        int plane = tile.getPlane();
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            return;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            return;
        }

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(piece.getObjectId(), -1356282071);
        if (definition == null) {
            return;
        }

        int rotation = ConstructionPlacementController.getRotation() & 0x3;
        int sizeX = definition.sizeX * 1755098015;
        int sizeY = definition.sizeY * -1692133213;
        if ((rotation & 0x1) != 0) {
            int swap = sizeX;
            sizeX = sizeY;
            sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = plane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[plane + 1]
                : null;

        Class647 built = definition.method6057(renderer, MODEL_FLAGS, piece.getObjectType(), rotation,
                ground, upperGround, sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            return;
        }

        if (scene.aClass174Array5840 == scene.aClass174Array5875 && scene.aClass174Array5838[0] != null) {
            Class86 environment = new Class86();
            environment.anInt1193 = scene.method6231(localX, localY, 1258315415) * 1368828903;
            environment.anInt1190 = scene.method6230(localX, localY, -981999643) * 1765263439;
            environment.anInt1191 = scene.method6283(localX, localY, 775342000) * 628738217;
            environment.anInt1189 = scene.method6233(localX, localY, -1042067865) * -233369847;
            environment.anInt1194 = scene.method6234(localX, localY, (byte) 16) * -223776263;
            environment.anInt1195 = scene.method6235(localX, localY, (byte) 95) * -963547665;
            renderer.method1790(scene.aClass174Array5838[0].method2726(sceneX, sceneZ, 358769667), environment);
        }

        Model model = (Model) built.anObject8324;
        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        Class326 bounds = definition.aClass326_5684;
        if (bounds != null) {
            model.method1375(TRANSFORM, null, 0);
            renderer.method1738(TRANSFORM, RENDER_BOUNDS, bounds);
        } else {
            model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        }
    }
}
