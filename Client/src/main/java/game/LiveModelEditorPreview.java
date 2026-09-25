package game;

import java.util.List;

/**
 * Live Model Editor runtime clone + mesh-part authoring preview.
 *
 * Bundle 1 proved private in-world whole-model transforms. Bundle 2 decodes the
 * selected definition's source Class159 geometry, finds connected components,
 * applies developer part edits to fresh decoded copies, then builds renderer
 * Models through Matrix3's existing renderer. Shared cache geometry is never
 * mutated and this path performs no cache/server-world writes.
 */
public final class LiveModelEditorPreview {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int EDIT_TRANSFORM_FLAGS = 0x0f;
    private static final int EDIT_MODEL_FLAGS = BASE_MODEL_FLAGS | EDIT_TRANSFORM_FLAGS;
    private static final int RAW_BUILD_FLAGS = EDIT_MODEL_FLAGS | 0x1f01f | 0x80000;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();
    private static final LiveModelEditorParts PARTS = new LiveModelEditorParts();

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
    private static volatile int modelRevision;

    private static Class106 cachedRenderer;
    private static int cachedRevision = Integer.MIN_VALUE;
    private static Model cachedMainModel;
    private static Model[] cachedDuplicateModels = new Model[0];

    private LiveModelEditorPreview() {
    }

    public static void show(String name, int id, int type, int rotation,
            int worldX, int worldY, int worldPlane,
            int tileOffsetX, int tileOffsetY,
            int sxPercent, int syPercent, int szPercent,
            int moveX, int moveY, int moveZ, int yaw) {
        int previousId = objectId;
        int previousType = objectType;
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
        if (previousId != objectId || previousType != objectType) PARTS.clear();
        invalidateModels();
        active = id >= 0;
        status = active ? describe("READY") : "INVALID object id";
    }

    public static void hide() {
        active = false;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
    }

    public static boolean isActive() { return active; }
    public static int getObjectId() { return objectId; }
    public static String getStatus() { return status; }

    public static int initializeParts() {
        ObjectDefinitions definition = currentDefinition();
        if (definition == null) {
            status = "PARTS WAIT object definitions";
            return 0;
        }
        int count = PARTS.ensureSource(definition, objectId, objectType);
        invalidateModels();
        status = count > 0 ? "PARTS READY " + count + " connected components"
                : "PARTS unavailable for object type " + objectType;
        return count;
    }

    public static String[] getPartLabels() { return PARTS.getLabels(); }
    public static int getSelectedPart() { return PARTS.getSelected(); }
    public static int[] getSelectedPartTransform() { return PARTS.getSelectedTransform(); }

    public static boolean selectPart(int index) {
        boolean changed = PARTS.select(index);
        invalidateModels();
        return changed;
    }

    public static boolean setSelectedPartTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        boolean changed = PARTS.setSelectedTransform(sx, sy, sz, mx, my, mz, yaw);
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean toggleSelectedPartHidden() {
        boolean changed = PARTS.toggleSelectedHidden();
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean deleteSelectedPart() {
        boolean changed = PARTS.deleteSelected();
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean duplicateSelectedPart() {
        boolean changed = PARTS.duplicateSelected();
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean showAllParts() {
        boolean changed = PARTS.showAll();
        if (changed) invalidateModels();
        return changed;
    }

    public static void toggleIsolatePart() {
        PARTS.toggleIsolate();
        invalidateModels();
    }

    public static boolean isPartIsolated() { return PARTS.isIsolate(); }

    public static boolean undoPartEdit() {
        boolean changed = PARTS.undo();
        if (changed) invalidateModels();
        return changed;
    }

    public static String getPartProjectJsonFields() { return PARTS.projectJsonFields(); }

    public static void loadPartProjectJson(String json) {
        PARTS.loadProjectJson(json);
        invalidateModels();
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!active || objectId < 0 || scene == null || renderer == null) return;
        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) return;
        lastRenderedCycle = cycle;

        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) return;

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

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(objectId, -1356282071);
        if (definition == null) {
            status = "UNKNOWN object id " + objectId;
            return;
        }

        int renderRotation = objectRotation & 0x3;
        int sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
        int sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
        if ((renderRotation & 0x1) != 0) {
            int swap = sizeX; sizeX = sizeY; sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            status = "SKIP runtime clone outside active scene";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = renderPlane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[renderPlane + 1] : null;

        if (PARTS.isReadyFor(objectId, objectType)) {
            renderPartModels(renderer, definition, ground, upperGround,
                    sceneX, sceneY, sceneZ, renderRotation);
            status = describe("DRAW PARTS") + " count=" + PARTS.getPartCount()
                    + " at=" + worldX + "," + worldY + "," + renderPlane;
            return;
        }

        Class647 built = definition.method6057(renderer, EDIT_MODEL_FLAGS,
                objectType, renderRotation, ground, upperGround,
                sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            status = "MODEL_NULL id=" + objectId + " type=" + objectType + " rot=" + renderRotation;
            return;
        }
        Model model = ((Model) built.anObject8324).method1351((byte) 0, EDIT_MODEL_FLAGS, true);
        if (model == null) {
            status = "CLONE_NULL id=" + objectId;
            return;
        }
        applyWholeTransforms(model);
        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        status = describe("DRAW") + " at=" + worldX + "," + worldY + "," + renderPlane;
    }

    private static void renderPartModels(Class106 renderer, ObjectDefinitions definition,
            Class174 ground, Class174 upperGround, int sceneX, int sceneY, int sceneZ,
            int rotation) {
        int revision = modelRevision * 31 + PARTS.getRevision();
        if (cachedRenderer != renderer || cachedRevision != revision || cachedMainModel == null) {
            Class159 mainRaw = PARTS.buildMainRaw();
            cachedMainModel = mainRaw == null ? null
                    : buildDefinitionModel(renderer, definition, mainRaw, rotation,
                            ground, upperGround, sceneX, sceneY, sceneZ);
            List<Class159> duplicateRaws = PARTS.buildDuplicateRaws();
            cachedDuplicateModels = new Model[duplicateRaws.size()];
            for (int i = 0; i < duplicateRaws.size(); i++) {
                cachedDuplicateModels[i] = buildDefinitionModel(renderer, definition,
                        duplicateRaws.get(i), rotation,
                        ground, upperGround, sceneX, sceneY, sceneZ);
            }
            cachedRenderer = renderer;
            cachedRevision = revision;
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        if (cachedMainModel != null) cachedMainModel.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        for (Model model : cachedDuplicateModels)
            if (model != null) model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
    }

    private static Model buildDefinitionModel(Class106 renderer, ObjectDefinitions definition,
            Class159 raw, int rotation, Class174 ground, Class174 upperGround,
            int sceneX, int sceneY, int sceneZ) {
        int ambient = definition.anInt5638 * 1878786655 + 64;
        int contrast = -69277109 * definition.anInt5639 + 850;
        Model model = renderer.method1755(raw, RAW_BUILD_FLAGS,
                definition.aClass518_5608.anInt5751 * 1583875953, ambient, contrast);
        if (model == null) return null;

        if (definition.aBool5647) model.method1359();
        int rot = rotation & 0x3;
        if (rot == 1) model.method1412(4096);
        else if (rot == 2) model.method1412(8192);
        else if (rot == 3) model.method1412(12288);

        if (definition.aShortArray5613 != null) {
            for (int i = 0; i < definition.aShortArray5613.length; i++) {
                short replacement = definition.aShortArray5621[i];
                if (definition.aByteArray5615 != null && i < definition.aByteArray5615.length)
                    replacement = ObjectDefinitions.aShortArray5606[definition.aByteArray5615[i] & 0xff];
                model.method1393(definition.aShortArray5613[i], replacement);
            }
        }
        if (definition.aShortArray5618 != null) {
            for (int i = 0; i < definition.aShortArray5618.length; i++)
                model.method1494(definition.aShortArray5618[i], definition.aShortArray5617[i]);
        }
        if (definition.aByte5666 != 0)
            model.method1396(definition.aByte5616, definition.aByte5681,
                    definition.aByte5622, definition.aByte5666 & 0xff);

        int dsx = definition.anInt5646 * 898312795;
        int dsy = definition.anInt5634 * 1899990883;
        int dsz = definition.anInt5641 * 1427207859;
        if (dsx != 128 || dsy != 128 || dsz != 128) model.method1464(dsx, dsy, dsz);

        int dmx = definition.anInt5652 * -865773249;
        int dmy = definition.anInt5653 * -955267449;
        int dmz = definition.anInt5654 * -504975083;
        if (dmx != 0 || dmy != 0 || dmz != 0) model.method1358(dmx, dmy, dmz);

        if (definition.aByte5628 != 0)
            model.method1463(definition.aByte5628, definition.anInt5629 * -1793366483,
                    ground, upperGround, sceneX, sceneY, sceneZ);

        int extraX = definition.anInt5655 * 1281867755;
        int extraY = definition.anInt5673 * -1496350233;
        int extraZ = definition.anInt5657 * -2114564345;
        if (extraX != 0 || extraY != 0 || extraZ != 0) model.method1358(extraX, extraY, extraZ);

        applyWholeTransforms(model);
        model.method1450(EDIT_MODEL_FLAGS);
        return model;
    }

    private static void applyWholeTransforms(Model model) {
        int sx = percentToModelScale(scaleXPercent);
        int sy = percentToModelScale(scaleYPercent);
        int sz = percentToModelScale(scaleZPercent);
        if (sx != 128 || sy != 128 || sz != 128) model.method1464(sx, sy, sz);
        int yawUnits = yawDegrees * 16384 / 360 & 0x3fff;
        if (yawUnits != 0) model.method1412(yawUnits);
        if (translateX != 0 || translateY != 0 || translateZ != 0)
            model.method1358(translateX, translateY, translateZ);
    }

    private static ObjectDefinitions currentDefinition() {
        Class613 region = client.aClass613_8605;
        if (region == null || objectId < 0) return null;
        Class639_Sub16 definitions = region.method7288(0);
        if (definitions == null) return null;
        try {
            return (ObjectDefinitions) definitions.getDefinition(objectId, -1356282071);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void invalidateModels() {
        modelRevision++;
        cachedRevision = Integer.MIN_VALUE;
        cachedMainModel = null;
        cachedDuplicateModels = new Model[0];
        lastRenderedCycle = Integer.MIN_VALUE;
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
