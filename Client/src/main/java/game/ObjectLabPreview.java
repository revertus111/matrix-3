package game;

/**
 * Client-only direct-render preview for Object Lab.
 *
 * The selected object is built through Matrix3's normal ObjectDefinitions model
 * factory and drawn during the active scene pass without registering it in
 * Class523. It therefore owns no collision, clipping, persistence, or world
 * object lifecycle.
 */
public final class ObjectLabPreview {

    private static final int MODEL_FLAGS = 2048;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean active;
    private static volatile int objectId = -1;
    private static volatile int objectType = 10;
    private static volatile int rotation;
    private static volatile int sourceX;
    private static volatile int sourceY;
    private static volatile int plane;
    private static volatile int offsetX = 3;
    private static volatile int offsetY;
    private static volatile String objectName = "Object";
    private static volatile String status = "IDLE";
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;

    private ObjectLabPreview() {
    }

    public static void showPreview(String name, int id, int type, int rot,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        objectName = name == null || name.trim().length() == 0 ? "Object" : name;
        objectId = id;
        objectType = clamp(type, 0, 22);
        rotation = rot & 0x3;
        sourceX = worldX;
        sourceY = worldY;
        plane = clamp(worldPlane, 0, 3);
        offsetX = clamp(xOffset, -12, 12);
        offsetY = clamp(yOffset, -12, 12);
        lastRenderedCycle = Integer.MIN_VALUE;
        active = id >= 0;
        status = active
                ? "READY " + objectName + " id=" + objectId + " type=" + objectType
                        + " rot=" + rotation + " preview="
                        + getPreviewX() + "," + getPreviewY() + "," + plane
                : "INVALID object id";
    }

    public static void hide() {
        active = false;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
    }

    public static boolean isActive() {
        return active;
    }

    public static String getStatus() {
        return status;
    }

    public static int getObjectId() {
        return objectId;
    }

    public static void updateTypeRotation(int type, int rot) {
        objectType = clamp(type, 0, 22);
        rotation = rot & 0x3;
        lastRenderedCycle = Integer.MIN_VALUE;
        if (active) {
            status = "READY id=" + objectId + " type=" + objectType + " rot=" + rotation
                    + " preview=" + getPreviewX() + "," + getPreviewY() + "," + plane;
        }
    }

    public static void updateOffset(int xOffset, int yOffset) {
        offsetX = clamp(xOffset, -12, 12);
        offsetY = clamp(yOffset, -12, 12);
        lastRenderedCycle = Integer.MIN_VALUE;
        if (active) {
            status = "READY id=" + objectId + " type=" + objectType + " rot=" + rotation
                    + " preview=" + getPreviewX() + "," + getPreviewY() + "," + plane;
        }
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!active || objectId < 0 || scene == null || renderer == null) {
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

        int worldX = sourceX + offsetX;
        int worldY = sourceY + offsetY;
        int localX = worldX - sceneBase.localX * -2109597897;
        int localY = worldY - sceneBase.localY * 417324155;
        int renderPlane = plane;
        if (renderPlane < 0 || renderPlane >= scene.aClass174Array5838.length) {
            status = "SKIP invalid plane " + renderPlane;
            return;
        }

        Class174 ground = scene.aClass174Array5838[renderPlane];
        if (ground == null) {
            status = "WAIT terrain plane " + renderPlane;
            return;
        }

        ObjectDefinitions definition =
                (ObjectDefinitions) definitions.getDefinition(objectId, -1356282071);
        if (definition == null) {
            status = "UNKNOWN object id " + objectId;
            return;
        }

        int renderRotation = rotation & 0x3;
        int sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
        int sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
        if ((renderRotation & 0x1) != 0) {
            int swap = sizeX;
            sizeX = sizeY;
            sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0
                || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            status = "SKIP preview tile outside active scene";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = renderPlane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[renderPlane + 1]
                : null;

        Class647 built = definition.method6057(renderer, MODEL_FLAGS, objectType, renderRotation,
                ground, upperGround, sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            status = "MODEL_NULL id=" + objectId + " type=" + objectType + " rot=" + renderRotation;
            return;
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
            renderer.method1790(scene.aClass174Array5838[0].method2726(sceneX, sceneZ, 358769667),
                    environment);
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
        status = "DRAW id=" + objectId + " type=" + objectType + " rot=" + renderRotation
                + " preview=" + worldX + "," + worldY + "," + renderPlane;
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
}
