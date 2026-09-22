package game;

/**
 * Client-only generic object preview used by Test -> Object Explorer.
 *
 * It can draw one or two object models on exactly the same world tile without
 * registering either object in Class523. This proves visual composability only;
 * it does not prove that two objects can coexist in the same normal scene slot.
 */
public final class ObjectCompositePreview {

    private static final int MODEL_FLAGS = 2048;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean active;
    private static volatile PreviewSpec a;
    private static volatile PreviewSpec b;
    private static volatile int sourceX;
    private static volatile int sourceY;
    private static volatile int plane;
    private static volatile int offsetX = 3;
    private static volatile int offsetY;
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "IDLE";

    private ObjectCompositePreview() {
    }

    public static void showSingle(String name, int id, int type, int rotation,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        show(new PreviewSpec(name, id, type, rotation), null,
                worldX, worldY, worldPlane, xOffset, yOffset);
    }

    public static void showOverlay(
            String nameA, int idA, int typeA, int rotationA,
            String nameB, int idB, int typeB, int rotationB,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        show(new PreviewSpec(nameA, idA, typeA, rotationA),
                new PreviewSpec(nameB, idB, typeB, rotationB),
                worldX, worldY, worldPlane, xOffset, yOffset);
    }

    private static void show(PreviewSpec first, PreviewSpec second,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        a = first;
        b = second;
        sourceX = worldX;
        sourceY = worldY;
        plane = clamp(worldPlane, 0, 3);
        offsetX = clamp(xOffset, -12, 12);
        offsetY = clamp(yOffset, -12, 12);
        active = first != null && first.id >= 0;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = active
                ? "READY " + describe(first)
                        + (second == null ? "" : " + " + describe(second))
                        + " sameTile=" + getPreviewX() + "," + getPreviewY() + "," + plane
                : "INVALID preview";
    }

    public static void hide() {
        active = false;
        a = null;
        b = null;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
    }

    public static String getStatus() {
        return status;
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!active || a == null || scene == null || renderer == null) {
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) {
            return;
        }

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            status = "WAIT scene/object definitions";
            return;
        }

        int worldX = getPreviewX();
        int worldY = getPreviewY();
        int rendered = 0;
        StringBuilder failures = new StringBuilder();

        if (renderOne(a, scene, renderer, sceneBase, definitions, worldX, worldY, plane)) {
            rendered++;
        } else {
            failures.append(" A_FAIL");
        }

        PreviewSpec second = b;
        if (second != null && second.id >= 0) {
            if (renderOne(second, scene, renderer, sceneBase, definitions,
                    worldX, worldY, plane)) {
                rendered++;
            } else {
                failures.append(" B_FAIL");
            }
        }

        int requested = second == null || second.id < 0 ? 1 : 2;
        status = "DRAW " + rendered + "/" + requested
                + " sameTile=" + worldX + "," + worldY + "," + plane
                + failures.toString();
    }

    private static boolean renderOne(PreviewSpec spec, Class523 scene, Class106 renderer,
            Class497 sceneBase, Class639_Sub16 definitions,
            int worldX, int worldY, int renderPlane) {
        if (spec == null || spec.id < 0
                || renderPlane < 0 || renderPlane >= scene.aClass174Array5838.length) {
            return false;
        }

        Class174 ground = scene.aClass174Array5838[renderPlane];
        if (ground == null) {
            return false;
        }

        ObjectDefinitions definition =
                (ObjectDefinitions) definitions.getDefinition(spec.id, -1356282071);
        if (definition == null) {
            return false;
        }

        int localX = worldX - sceneBase.localX * -2109597897;
        int localY = worldY - sceneBase.localY * 417324155;
        int rotation = spec.rotation & 0x3;

        int sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
        int sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
        if ((rotation & 0x1) != 0) {
            int swap = sizeX;
            sizeX = sizeY;
            sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0
                || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            return false;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = renderPlane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[renderPlane + 1]
                : null;

        Class647 built = definition.method6057(renderer, MODEL_FLAGS, spec.type, rotation,
                ground, upperGround, sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            return false;
        }

        if (scene.aClass174Array5840 == scene.aClass174Array5875
                && scene.aClass174Array5838[0] != null) {
            Class86 environment = new Class86();
            environment.anInt1193 = scene.method6231(localX, localY, 1258315415) * 1368828903;
            environment.anInt1190 = scene.method6230(localX, localY, -981999643) * 1765263439;
            environment.anInt1191 = scene.method6283(localX, localY, 775342000) * 628738217;
            environment.anInt1189 = scene.method6233(localX, localY, -1042067865) * -233369847;
            environment.anInt1194 = scene.method6234(localX, localY, (byte) 16) * -223776263;
            environment.anInt1195 = scene.method6235(localX, localY, (byte) 95) * -963547665;
            renderer.method1790(scene.aClass174Array5838[0]
                    .method2726(sceneX, sceneZ, 358769667), environment);
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
        return true;
    }

    private static String describe(PreviewSpec spec) {
        return spec.name + " id=" + spec.id + " type=" + spec.type + " rot=" + spec.rotation;
    }

    private static int getPreviewX() {
        return sourceX + offsetX;
    }

    private static int getPreviewY() {
        return sourceY + offsetY;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static final class PreviewSpec {
        private final String name;
        private final int id;
        private final int type;
        private final int rotation;

        private PreviewSpec(String name, int id, int type, int rotation) {
            this.name = name == null || name.trim().isEmpty() ? "Object" : name;
            this.id = id;
            this.type = clamp(type, 0, 22);
            this.rotation = rotation & 0x3;
        }
    }
}
