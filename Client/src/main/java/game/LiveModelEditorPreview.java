package game;

/**
 * Live Model Editor Bundle 1 runtime clone.
 *
 * verified-static: this uses the same ObjectDefinitions model factory and the
 * existing Class578 developer direct-render seam as Object Lab. The returned
 * model is cloned again before editor transforms are applied, so cached/shared
 * definition models are not mutated.
 *
 * Bundle 1 is intentionally visual-only: it does not register a scene object,
 * change clipping, change server world state, or write cache bytes.
 */
public final class LiveModelEditorPreview {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int EDIT_TRANSFORM_FLAGS = 0x0f;
    private static final int EDIT_MODEL_FLAGS = BASE_MODEL_FLAGS | EDIT_TRANSFORM_FLAGS;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean active;
    private static volatile int objectId = -1;
    private static volatile String objectName = "Object";
    private static volatile int objectType = 10;
    private static volatile int objectRotation;
    private static volatile int sourceX;
    private static volatile int sourceY;
    private static volatile int plane;
    private static volatile int previewOffsetX = 2;
    private static volatile int previewOffsetY;

    private static volatile int scaleXPercent = 100;
    private static volatile int scaleYPercent = 100;
    private static volatile int scaleZPercent = 100;
    private static volatile int translateX;
    private static volatile int translateY;
    private static volatile int translateZ;
    private static volatile int yawDegrees;

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "IDLE";

    private LiveModelEditorPreview() {
    }

    public static void show(String name, int id, int type, int rotation,
            int worldX, int worldY, int worldPlane,
            int tileOffsetX, int tileOffsetY,
            int sxPercent, int syPercent, int szPercent,
            int moveX, int moveY, int moveZ, int yaw) {
        objectName = name == null || name.trim().length() == 0 ? "Object" : name;
        objectId = id;
        objectType = clamp(type, 0, 22);
        objectRotation = rotation & 0x3;
        sourceX = worldX;
        sourceY = worldY;
        plane = clamp(worldPlane, 0, 3);
        previewOffsetX = clamp(tileOffsetX, -12, 12);
        previewOffsetY = clamp(tileOffsetY, -12, 12);
        scaleXPercent = clamp(sxPercent, 10, 400);
        scaleYPercent = clamp(syPercent, 10, 400);
        scaleZPercent = clamp(szPercent, 10, 400);
        translateX = clamp(moveX, -4096, 4096);
        translateY = clamp(moveY, -4096, 4096);
        translateZ = clamp(moveZ, -4096, 4096);
        yawDegrees = normalizeDegrees(yaw);
        lastRenderedCycle = Integer.MIN_VALUE;
        active = id >= 0;
        status = active ? describe("READY") : "INVALID object id";
    }

    public static void hide() {
        active = false;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
    }

    public static boolean isActive() {
        return active;
    }

    public static int getObjectId() {
        return objectId;
    }

    public static String getStatus() {
        return status;
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

        int worldX = sourceX + previewOffsetX;
        int worldY = sourceY + previewOffsetY;
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

        int renderRotation = objectRotation & 0x3;
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
            status = "SKIP runtime clone outside active scene";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = renderPlane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[renderPlane + 1]
                : null;

        Class647 built = definition.method6057(renderer, EDIT_MODEL_FLAGS,
                objectType, renderRotation, ground, upperGround,
                sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            status = "MODEL_NULL id=" + objectId + " type=" + objectType
                    + " rot=" + renderRotation;
            return;
        }

        /*
         * Private editor clone. Never apply transforms to the factory return
         * directly because its internals may originate from definition caches.
         */
        Model model = ((Model) built.anObject8324)
                .method1351((byte) 0, EDIT_MODEL_FLAGS, true);
        if (model == null) {
            status = "CLONE_NULL id=" + objectId;
            return;
        }

        int sx = percentToModelScale(scaleXPercent);
        int sy = percentToModelScale(scaleYPercent);
        int sz = percentToModelScale(scaleZPercent);
        if (sx != 128 || sy != 128 || sz != 128) {
            model.method1464(sx, sy, sz);
        }

        int yawUnits = yawDegrees * 16384 / 360 & 0x3fff;
        if (yawUnits != 0) {
            model.method1412(yawUnits);
        }

        if (translateX != 0 || translateY != 0 || translateZ != 0) {
            model.method1358(translateX, translateY, translateZ);
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        status = describe("DRAW") + " at=" + worldX + "," + worldY + "," + renderPlane;
    }

    private static String describe(String state) {
        return state + " " + objectName + " id=" + objectId
                + " type=" + objectType + " rot=" + objectRotation
                + " scale=" + scaleXPercent + "/" + scaleYPercent + "/" + scaleZPercent
                + " move=" + translateX + "/" + translateY + "/" + translateZ
                + " yaw=" + yawDegrees;
    }

    private static int percentToModelScale(int percent) {
        return Math.max(1, percent * 128 / 100);
    }

    private static int normalizeDegrees(int value) {
        int normalized = value % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
