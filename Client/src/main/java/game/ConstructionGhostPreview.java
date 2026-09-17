package game;

/**
 * Client-only Construction placement preview.
 *
 * The preview is rendered directly through Matrix3's normal object-model path
 * and is never registered with Class523. It therefore owns no collision,
 * persistence, server state, or scene-object lifecycle.
 */
public final class ConstructionGhostPreview {

    private static final int MODEL_FLAGS = 2048 | 0x80000;
    private static final int GHOST_TINT_HUE = 0;
    private static final int GHOST_TINT_SATURATION = 0;
    private static final int GHOST_TINT_LIGHTNESS = 127;
    private static final int GHOST_TINT_WEIGHT = 160;
    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();
    private static volatile String lastDiagnosticKey = "";
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;

    private ConstructionGhostPreview() {
    }

    /**
     * Draws the currently selected Construction piece at the hovered world tile.
     *
     * verified-static: ObjectDefinitions.method6057(...) is the same model factory
     * used by Matrix3 scene objects, and Model.method1375(...) is their live draw
     * path. Model.method1396(...) is Matrix3's existing whole-model tint override
     * path. This method intentionally never calls Class523 attach/remove methods.
     */
    static void render(Class523 scene, Class106 renderer) {
        if (scene == null || renderer == null) {
            diagnostic("WAIT_SCENE", "WAIT scene/renderer unavailable");
            return;
        }
        if (!ConstructionPlacementController.isHoverTracking()) {
            diagnostic("WAIT_TRACKING", "WAIT hover tracking disabled");
            return;
        }
        if (!ConstructionPlacementController.isArmed()) {
            diagnostic("WAIT_ARMED", "WAIT placement not armed");
            return;
        }

        ConstructionPlacementController.BuildPiece piece = ConstructionPlacementController.getSelectedPiece();
        ConstructionPlacementController.HoverTile tile = ConstructionPlacementController.getHoveredTile();
        Class613 region = client.aClass613_8605;
        if (piece == null) {
            diagnostic("WAIT_PIECE", "WAIT no selected Construction piece");
            return;
        }
        if (tile == null) {
            diagnostic("WAIT_HOVER", "WAIT no current hovered world tile");
            return;
        }
        if (region == null) {
            diagnostic("WAIT_REGION", "WAIT active region unavailable");
            return;
        }
        if (region.method7285(0) != scene) {
            diagnostic("WAIT_SCENE_MISMATCH", "WAIT active scene mismatch");
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            diagnostic("WAIT_REGION_DATA", "WAIT scene base/object definitions unavailable");
            return;
        }

        int localX = tile.getWorldX() - sceneBase.localX * -2109597897;
        int localY = tile.getWorldY() - sceneBase.localY * 417324155;
        int plane = tile.getPlane();
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            diagnostic("SKIP_PLANE", "SKIP invalid hovered plane " + plane);
            return;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            diagnostic("WAIT_GROUND", "WAIT terrain unavailable for plane " + plane);
            return;
        }

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(piece.getObjectId(), -1356282071);
        if (definition == null) {
            diagnostic("MODEL_DEFINITION_NULL:" + piece.getObjectId(),
                    "MODEL_DEFINITION_NULL object=" + piece.getObjectId());
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
            diagnostic("SKIP_BOUNDS", "SKIP hovered tile outside active scene local=" + localX + "," + localY
                    + " size=" + sizeX + "x" + sizeY + " scene=" + sceneWidth + "x" + sceneHeight);
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
        if (built == null) {
            diagnostic("MODEL_NULL:" + piece.getObjectId() + ":" + piece.getObjectType() + ":" + rotation,
                    "MODEL_NULL object=" + piece.getObjectId() + " type=" + piece.getObjectType()
                            + " rot=" + rotation + " world=" + tile.getWorldX() + "," + tile.getWorldY() + ","
                            + plane + " local=" + localX + "," + localY);
            return;
        }
        if (!(built.anObject8324 instanceof Model)) {
            String actual = built.anObject8324 == null ? "null" : built.anObject8324.getClass().getName();
            diagnostic("MODEL_NOT_MODEL:" + piece.getObjectId() + ":" + actual,
                    "MODEL_NOT_MODEL object=" + piece.getObjectId() + " result=" + actual);
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
        model.method1396(GHOST_TINT_HUE, GHOST_TINT_SATURATION, GHOST_TINT_LIGHTNESS, GHOST_TINT_WEIGHT);
        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        Class326 bounds = definition.aClass326_5684;
        if (bounds != null) {
            model.method1375(TRANSFORM, null, 0);
            renderer.method1738(TRANSFORM, RENDER_BOUNDS, bounds);
        } else {
            model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        }

        diagnostic("DRAW_SUBMITTED:" + piece.getObjectId() + ":" + piece.getObjectType() + ":" + rotation
                        + ":" + (bounds != null),
                "DRAW_SUBMITTED object=" + piece.getObjectId() + " type=" + piece.getObjectType()
                        + " rot=" + rotation + " specialBounds=" + (bounds != null)
                        + " ghostTint=white/" + GHOST_TINT_WEIGHT
                        + " world=" + tile.getWorldX() + "," + tile.getWorldY() + "," + plane
                        + " local=" + localX + "," + localY
                        + " scene=" + sceneX + "," + sceneY + "," + sceneZ);
    }

    private static void diagnostic(String key, String message) {
        if (key.equals(lastDiagnosticKey)) {
            return;
        }
        lastDiagnosticKey = key;
        System.out.println("[ConstructionGhostPreview] " + message);
    }
}
