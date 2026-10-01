package game;

/**
 * Client-only Dev Mode object placement preview.
 *
 * The preview uses Matrix3's normal object-definition model factory and direct
 * render seam but never registers an object with the scene. A real server-owned
 * Dev object is created only after the user confirms placement with the normal
 * scene-tile click.
 */
public final class DevObjectPlacementPreview {

    private static final int MODEL_FLAGS = 2048 | 0x80000 | 0x100;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean active;
    private static volatile int objectId = -1;
    private static volatile int objectType = 10;
    private static volatile int rotation;
    private static volatile String objectName = "Object";

    private static volatile int hoveredWorldX = -1;
    private static volatile int hoveredWorldY = -1;
    private static volatile int hoveredPlane = -1;
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;

    private DevObjectPlacementPreview() {
    }

    public static String arm(int id, int newRotation) {
        if (id < 0 || newRotation < 0 || newRotation > 3) {
            return "Live object placement requires a valid object ID and rotation 0-3.";
        }
        objectId = id;
        objectType = DevDefinitionBridge.getPreferredObjectType(id);
        rotation = newRotation & 0x3;
        objectName = resolveName(id);
        active = true;
        clearHoveredTile();
        lastRenderedCycle = Integer.MIN_VALUE;
        return "Live placement: " + objectName + " (" + objectId + "), type " + objectType
                + ". 1/2 change object ID, left-click places, Escape cancels.";
    }

    public static String arm(int id, int ignoredType, int newRotation) {
        return arm(id, newRotation);
    }

    public static boolean isActive() {
        return active;
    }

    public static String updateObject(int id) {
        if (!active) {
            return "Live object placement is not active.";
        }
        if (id < 0) {
            return "Choose a valid object.";
        }
        objectId = id;
        objectType = DevDefinitionBridge.getPreferredObjectType(id);
        objectName = resolveName(id);
        lastRenderedCycle = Integer.MIN_VALUE;
        return describeCurrent();
    }

    public static String cycleObjectId(int delta) {
        if (!active) {
            return "Live object placement is not active.";
        }
        if (delta != -1 && delta != 1) {
            return describeCurrent();
        }

        int count = DevDefinitionBridge.getObjectCount();
        int next = objectId + delta;
        if (count > 0) {
            if (next < 0) {
                next = count - 1;
            } else if (next >= count) {
                next = 0;
            }
        } else if (next < 0) {
            next = 0;
        }

        objectId = next;
        objectType = DevDefinitionBridge.getPreferredObjectType(next);
        objectName = resolveName(next);
        lastRenderedCycle = Integer.MIN_VALUE;
        return describeCurrent();
    }

    static void observeWorldTile(int worldX, int worldY, int plane) {
        if (!active) {
            return;
        }
        hoveredWorldX = worldX;
        hoveredWorldY = worldY;
        hoveredPlane = plane;
    }

    public static String placeAt(int worldX, int worldY, int plane) {
        if (!active) {
            return "Live object placement is not active.";
        }
        DevSpawnPlacement.Request request = DevSpawnPlacement.object(
                objectId, objectType, rotation, DevSpawnPlacement.RotationMode.FIXED);
        String result = DevSpawnPlacement.placeOnce(request, worldX, worldY, plane);
        if (result != null && result.startsWith("Spawn queued")) {
            return "Placed " + objectName + " (" + objectId + ") at "
                    + worldX + ", " + worldY + ", " + plane + ". Live placement remains active.";
        }
        return result;
    }

    public static boolean cancel() {
        boolean wasActive = active;
        active = false;
        objectId = -1;
        objectName = "Object";
        clearHoveredTile();
        lastRenderedCycle = Integer.MIN_VALUE;
        return wasActive;
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!active || scene == null || renderer == null
                || hoveredWorldX < 0 || hoveredWorldY < 0 || hoveredPlane < 0) {
            return;
        }
        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) {
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
            return;
        }

        int localX = hoveredWorldX - sceneBase.localX * -2109597897;
        int localY = hoveredWorldY - sceneBase.localY * 417324155;
        int plane = hoveredPlane;
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            return;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            return;
        }

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(objectId, -1356282071);
        if (definition == null) {
            return;
        }

        int previewRotation = rotation & 0x3;
        int sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
        int sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
        if ((previewRotation & 0x1) != 0) {
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

        Class647 built = definition.method6057(renderer, MODEL_FLAGS, objectType, previewRotation,
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

    private static String describeCurrent() {
        return "Live placement: " + objectName + " (" + objectId + "), type " + objectType
                + ", rotation " + rotation + ".";
    }

    private static String resolveName(int id) {
        DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfo(id);
        if (info == null || info.getName() == null || info.getName().trim().length() == 0) {
            return "Object";
        }
        return info.getName().trim();
    }

    private static void clearHoveredTile() {
        hoveredWorldX = -1;
        hoveredWorldY = -1;
        hoveredPlane = -1;
    }
}
