package game;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only generic object/composite preview used by Test -> Object Explorer.
 *
 * Any number of stock object models can be direct-rendered around one layout
 * origin, including multiple models on the exact same tile, without registering
 * them in Class523. This is visual authoring only; normal scene-slot ownership
 * remains unchanged.
 */
public final class ObjectCompositePreview {

    private static final int MODEL_FLAGS = 2048;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean active;
    private static volatile PreviewSpec[] specs = new PreviewSpec[0];
    private static volatile String previewName = "Preview";
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
        List<PreviewSpec> entries = new ArrayList<PreviewSpec>();
        entries.add(new PreviewSpec(name, id, type, rotation));
        show("Single", entries, worldX, worldY, worldPlane, xOffset, yOffset);
    }

    public static void showOverlay(
            String nameA, int idA, int typeA, int rotationA,
            String nameB, int idB, int typeB, int rotationB,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        List<PreviewSpec> entries = new ArrayList<PreviewSpec>();
        entries.add(new PreviewSpec(nameA, idA, typeA, rotationA));
        entries.add(new PreviewSpec(nameB, idB, typeB, rotationB));
        show("Overlay", entries, worldX, worldY, worldPlane, xOffset, yOffset);
    }

    public static void showComposite(String name,
            List<RailCompositeLibrary.Component> components,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        List<PreviewSpec> entries = new ArrayList<PreviewSpec>();
        if (components != null) {
            for (RailCompositeLibrary.Component component : components) {
                if (component != null) {
                    entries.add(new PreviewSpec("component", component.getId(),
                            component.getType(), component.getRotation(),
                            component.getOffsetX(), component.getOffsetY()));
                }
            }
        }
        show(name == null ? "Composite" : name,
                entries, worldX, worldY, worldPlane, xOffset, yOffset);
    }

    private static void show(String name, List<PreviewSpec> entries,
            int worldX, int worldY, int worldPlane, int xOffset, int yOffset) {
        specs = entries == null
                ? new PreviewSpec[0]
                : entries.toArray(new PreviewSpec[entries.size()]);
        previewName = name == null || name.trim().isEmpty() ? "Preview" : name;
        sourceX = worldX;
        sourceY = worldY;
        plane = clamp(worldPlane, 0, 3);
        offsetX = clamp(xOffset, -12, 12);
        offsetY = clamp(yOffset, -12, 12);
        active = specs.length > 0;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = active
                ? "READY " + previewName + " components=" + specs.length
                        + " sameTile=" + getPreviewX() + "," + getPreviewY() + "," + plane
                : "INVALID preview";
    }

    public static void hide() {
        active = false;
        specs = new PreviewSpec[0];
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
    }

    public static String getStatus() {
        return status;
    }

    static void render(Class523 scene, Class106 renderer) {
        PreviewSpec[] current = specs;
        if (!active || current.length == 0 || scene == null || renderer == null) {
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
        int failed = 0;

        for (PreviewSpec spec : current) {
            if (renderOne(spec, scene, renderer, sceneBase, definitions,
                    worldX + spec.offsetX, worldY + spec.offsetY, plane)) {
                rendered++;
            } else {
                failed++;
            }
        }

        status = "DRAW " + rendered + "/" + current.length
                + " sameTile=" + worldX + "," + worldY + "," + plane
                + (failed == 0 ? "" : " failed=" + failed);
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
        private final int id;
        private final int type;
        private final int rotation;
        private final int offsetX;
        private final int offsetY;

        private PreviewSpec(String name, int id, int type, int rotation) {
            this(name, id, type, rotation, 0, 0);
        }

        private PreviewSpec(String name, int id, int type, int rotation,
                int offsetX, int offsetY) {
            this.id = id;
            this.type = clamp(type, 0, 22);
            this.rotation = rotation & 0x3;
            this.offsetX = clamp(offsetX, -12, 12);
            this.offsetY = clamp(offsetY, -12, 12);
        }
    }
}
