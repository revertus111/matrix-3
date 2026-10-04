package game;

import java.util.Arrays;

/**
 * Matrix-native equipment attachment adapter for the foreign Mario presentation.
 *
 * V1 deliberately proves one slot only: the visible local player's revision-830
 * helmet is loaded through the normal ItemDefinitions worn-model path, measured,
 * cached, fitted to an animated Mario head anchor, and rendered through Matrix's
 * existing Model path. The libsm64 geometry remains the Mario pose/animation
 * authority and RuneScape equipment/definitions remain the item-model authority.
 */
public final class MarioEquipmentAdapter {

    private static final int EQUIPMENT_SLOT_HAT = 0;
    /* Inverse of ItemDefinitions' decompiler multiplier -1127488921. */
    private static final int EQUIP_SLOT_DECODE_MULTIPLIER = -917104297;

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;

    private static final float DEFAULT_MARIO_MODEL_SCALE = 2.0F;
    private static final float DEFAULT_HELMET_FIT_PADDING = 1.12F;
    private static final float HEAD_START_FRACTION = 0.62F;
    private static final float HEAD_RADIAL_FRACTION = 0.40F;
    private static final int MIN_HEAD_VERTICES = 12;

    private static final float MARIO_MODEL_SCALE = resolvePositiveFloat(
            "matrix3.sm64.modelScale", DEFAULT_MARIO_MODEL_SCALE);
    private static final float HELMET_FIT_PADDING = resolvePositiveFloat(
            "matrix3.sm64.helmetFitPadding", DEFAULT_HELMET_FIT_PADDING);
    private static final float HELMET_VERTICAL_OFFSET = resolveFiniteFloat(
            "matrix3.sm64.helmetVerticalOffset", 0.0F);
    private static final float HELMET_YAW_OFFSET_RADIANS = (float) Math.toRadians(resolveFiniteFloat(
            "matrix3.sm64.helmetYawOffsetDegrees", 0.0F));
    private static final boolean HELMET_YAW_FLIP = Boolean.parseBoolean(
            System.getProperty("matrix3.sm64.helmetYawFlip", "false"));

    private static final Class261 HELMET_TRANSFORM = new Class261();
    private static final Class90 HELMET_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static int cachedItemId = -1;
    private static boolean cachedFemale;
    private static Class634 cachedCustomization;
    private static Model cachedHelmetModel;
    private static float cachedHelmetHorizontalSpan;
    private static String cachedHelmetName;
    private static boolean cachedHelmetLogged;
    private static int lastFailedItemId = -1;

    private static int headTopologyTriangleCount = -1;
    private static int[] headVertexIndices;
    private static long lastFrameSequence = -1L;

    private MarioEquipmentAdapter() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (scene == null || renderer == null || !PlayerControllerMode.isMarioMode()
                || !Sm64BridgeSession.isReady()) {
            resetFrameTracking();
            return;
        }

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null || !MarioVisualRenderer.shouldSuppressLocalPlayer(player)) {
            return;
        }

        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        if (!isUsable(frame)) {
            return;
        }
        if (lastFrameSequence >= 0L && frame.sequence < lastFrameSequence) {
            resetHeadSelection();
        }
        lastFrameSequence = frame.sequence;

        HelmetAppearance helmet = findVisibleHelmet(player);
        if (helmet == null) {
            return;
        }
        if (!ensureHelmetModel(renderer, helmet)) {
            return;
        }

        HeadAnchor anchor = calculateHeadAnchor(frame);
        if (anchor == null || anchor.horizontalSpan <= 0.0F || cachedHelmetHorizontalSpan <= 0.0F) {
            return;
        }

        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null) {
            return;
        }

        float fitScale = anchor.horizontalSpan * HELMET_FIT_PADDING / cachedHelmetHorizontalSpan;
        if (!isFinite(fitScale)) {
            return;
        }
        fitScale = clamp(fitScale, 0.05F, 20.0F);

        float yaw = frame.state.faceAngle;
        if (!isFinite(yaw)) {
            yaw = 0.0F;
        }
        if (HELMET_YAW_FLIP) {
            yaw = -yaw;
        }
        yaw += HELMET_YAW_OFFSET_RADIANS;

        Class240 position = playerTransform.aClass240_2647;
        HELMET_TRANSFORM.method3577(fitScale, fitScale, fitScale);
        HELMET_TRANSFORM.method3576(0.0F, 1.0F, 0.0F, yaw);
        HELMET_TRANSFORM.method3580(
                position.aFloat2653 + anchor.x,
                position.aFloat2656 + anchor.y + HELMET_VERTICAL_OFFSET,
                position.aFloat2657 + anchor.z);

        try {
            cachedHelmetModel.method1375(HELMET_TRANSFORM, HELMET_BOUNDS, 0);
            if (!cachedHelmetLogged) {
                cachedHelmetLogged = true;
                System.out.println("[SM64 Equipment] Helmet ACTIVE item=" + cachedItemId
                        + " name=" + cachedHelmetName
                        + " fit=" + fitScale
                        + " headSpan=" + anchor.horizontalSpan
                        + " helmetSpan=" + cachedHelmetHorizontalSpan
                        + " yawFlip=" + HELMET_YAW_FLIP
                        + " yawOffsetDeg=" + Math.toDegrees(HELMET_YAW_OFFSET_RADIANS));
            }
        } catch (RuntimeException ex) {
            logHelmetFailure(helmet.itemId,
                    "render failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static HelmetAppearance findVisibleHelmet(Player player) {
        Class474 appearance = player.aClass474_11831;
        if (appearance == null || appearance.anIntArray5317 == null) {
            return null;
        }

        int[] parts = appearance.anIntArray5317;
        for (int index = 0; index < parts.length; index++) {
            int encoded = parts[index];
            if ((encoded & 0x40000000) == 0) {
                continue;
            }

            int itemId = encoded & 0x3fffffff;
            ItemDefinitions definition = (ItemDefinitions) Class672.aClass639_Sub5_8533
                    .getDefinition(itemId, -1597715602);
            if (definition == null
                    || definition.equipSlot * EQUIP_SLOT_DECODE_MULTIPLIER != EQUIPMENT_SLOT_HAT) {
                continue;
            }

            Class634 customization = null;
            if (appearance.aClass634Array5318 != null
                    && index < appearance.aClass634Array5318.length) {
                customization = appearance.aClass634Array5318[index];
            }
            return new HelmetAppearance(itemId, definition, customization, appearance.aBool5314);
        }
        return null;
    }

    private static boolean ensureHelmetModel(Class106 renderer, HelmetAppearance helmet) {
        if (cachedHelmetModel != null
                && cachedRenderer == renderer
                && cachedItemId == helmet.itemId
                && cachedFemale == helmet.female
                && cachedCustomization == helmet.customization) {
            return true;
        }

        cachedRenderer = renderer;
        cachedItemId = helmet.itemId;
        cachedFemale = helmet.female;
        cachedCustomization = helmet.customization;
        cachedHelmetModel = null;
        cachedHelmetHorizontalSpan = 0.0F;
        cachedHelmetName = helmet.definition.aString8180;
        cachedHelmetLogged = false;

        try {
            Class159 raw = helmet.definition.method7531(
                    helmet.female, helmet.customization, (byte) 114);
            if (raw == null || raw.anInt1791 <= 0
                    || raw.anIntArray1782 == null
                    || raw.anIntArray1777 == null
                    || raw.anIntArray1797 == null) {
                logHelmetFailure(helmet.itemId, "worn raw model unavailable");
                return false;
            }

            RawBounds bounds = measureRawBounds(raw);
            if (bounds == null || bounds.horizontalSpan <= 0.0F) {
                logHelmetFailure(helmet.itemId, "invalid worn-model bounds");
                return false;
            }

            raw.method2564(-bounds.centerX, -bounds.centerY, -bounds.centerZ);
            Model model = renderer.method1755(raw, RAW_BUILD_FLAGS, 0, 64, 850);
            if (model == null) {
                logHelmetFailure(helmet.itemId, "Matrix model build returned null");
                return false;
            }
            model.method1450(FINAL_MODEL_FLAGS);

            cachedHelmetModel = model;
            cachedHelmetHorizontalSpan = bounds.horizontalSpan;
            lastFailedItemId = -1;
            return true;
        } catch (RuntimeException ex) {
            logHelmetFailure(helmet.itemId,
                    "build failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    private static RawBounds measureRawBounds(Class159 raw) {
        int count = raw.anInt1791;
        if (count <= 0) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int i = 0; i < count; i++) {
            int x = raw.anIntArray1782[i];
            int y = raw.anIntArray1777[i];
            int z = raw.anIntArray1797[i];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float horizontalSpan = Math.max(maxX - minX, maxZ - minZ);
        return new RawBounds(
                Math.round((minX + maxX) * 0.5F),
                Math.round((minY + maxY) * 0.5F),
                Math.round((minZ + maxZ) * 0.5F),
                horizontalSpan);
    }

    private static HeadAnchor calculateHeadAnchor(Sm64BridgeSession.GeometryFrame frame) {
        ensureHeadSelection(frame);
        if (headVertexIndices == null || headVertexIndices.length < MIN_HEAD_VERTICES) {
            return null;
        }

        int totalVertices = frame.triangleCount * 3;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int vertexIndex : headVertexIndices) {
            if (vertexIndex < 0 || vertexIndex >= totalVertices) {
                resetHeadSelection();
                return null;
            }
            int base = vertexIndex * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float centerX = (minX + maxX) * 0.5F;
        float centerY = (minY + maxY) * 0.5F;
        float centerZ = (minZ + maxZ) * 0.5F;
        float horizontalSpan = Math.max(maxX - minX, maxZ - minZ) * MARIO_MODEL_SCALE;
        if (!isFinite(centerX) || !isFinite(centerY) || !isFinite(centerZ)
                || !isFinite(horizontalSpan) || horizontalSpan <= 0.0F) {
            return null;
        }

        return new HeadAnchor(
                centerX * MARIO_MODEL_SCALE,
                -centerY * MARIO_MODEL_SCALE,
                centerZ * MARIO_MODEL_SCALE,
                horizontalSpan);
    }

    /**
     * The binary bridge publishes final animated triangles rather than a bone
     * skeleton. V1 therefore captures the stable vertex-stream indices belonging
     * to Mario's head from the first upright frame and follows those same indices
     * on later animation frames, including jumps/backflips where the head is no
     * longer simply the highest part of the mesh.
     */
    private static void ensureHeadSelection(Sm64BridgeSession.GeometryFrame frame) {
        if (headVertexIndices != null && headTopologyTriangleCount == frame.triangleCount) {
            return;
        }

        int totalVertices = frame.triangleCount * 3;
        if (totalVertices <= 0) {
            resetHeadSelection();
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int vertex = 0; vertex < totalVertices; vertex++) {
            int base = vertex * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float height = maxY - minY;
        float bodyWidth = maxX - minX;
        float bodyDepth = maxZ - minZ;
        if (!isFinite(height) || height <= 0.0F) {
            resetHeadSelection();
            return;
        }

        float centerX = (minX + maxX) * 0.5F;
        float centerZ = (minZ + maxZ) * 0.5F;
        float headStartY = minY + height * HEAD_START_FRACTION;
        float radialLimit = Math.max(bodyWidth, bodyDepth) * HEAD_RADIAL_FRACTION;
        float radialLimitSquared = radialLimit * radialLimit;

        int[] selected = new int[totalVertices];
        int selectedCount = 0;
        for (int vertex = 0; vertex < totalVertices; vertex++) {
            int base = vertex * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            float dx = x - centerX;
            float dz = z - centerZ;
            if (y >= headStartY && dx * dx + dz * dz <= radialLimitSquared) {
                selected[selectedCount++] = vertex;
            }
        }

        if (selectedCount < MIN_HEAD_VERTICES) {
            selectedCount = 0;
            for (int vertex = 0; vertex < totalVertices; vertex++) {
                int base = vertex * 3;
                float y = frame.positions[base + 1] - frame.state.y;
                if (y >= headStartY) {
                    selected[selectedCount++] = vertex;
                }
            }
        }

        if (selectedCount < MIN_HEAD_VERTICES) {
            resetHeadSelection();
            return;
        }

        headVertexIndices = Arrays.copyOf(selected, selectedCount);
        headTopologyTriangleCount = frame.triangleCount;
        System.out.println("[SM64 Equipment] Captured Mario HEAD anchor vertices="
                + selectedCount + " triangles=" + frame.triangleCount);
    }

    private static boolean isUsable(Sm64BridgeSession.GeometryFrame frame) {
        return frame != null && frame.state != null && frame.triangleCount > 0
                && frame.positions != null
                && frame.positions.length >= frame.triangleCount * 9;
    }

    private static void resetFrameTracking() {
        lastFrameSequence = -1L;
        resetHeadSelection();
    }

    private static void resetHeadSelection() {
        headTopologyTriangleCount = -1;
        headVertexIndices = null;
    }

    private static void logHelmetFailure(int itemId, String reason) {
        if (lastFailedItemId == itemId) {
            return;
        }
        lastFailedItemId = itemId;
        System.err.println("[SM64 Equipment] Helmet item=" + itemId + " " + reason);
    }

    private static float resolvePositiveFloat(String property, float fallback) {
        float value = resolveFiniteFloat(property, fallback);
        return value > 0.0F ? value : fallback;
    }

    private static float resolveFiniteFloat(String property, float fallback) {
        String raw = System.getProperty(property);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            return isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    private static final class HelmetAppearance {
        final int itemId;
        final ItemDefinitions definition;
        final Class634 customization;
        final boolean female;

        HelmetAppearance(int itemId, ItemDefinitions definition,
                Class634 customization, boolean female) {
            this.itemId = itemId;
            this.definition = definition;
            this.customization = customization;
            this.female = female;
        }
    }

    private static final class RawBounds {
        final int centerX;
        final int centerY;
        final int centerZ;
        final float horizontalSpan;

        RawBounds(int centerX, int centerY, int centerZ, float horizontalSpan) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.horizontalSpan = horizontalSpan;
        }
    }

    private static final class HeadAnchor {
        final float x;
        final float y;
        final float z;
        final float horizontalSpan;

        HeadAnchor(float x, float y, float z, float horizontalSpan) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.horizontalSpan = horizontalSpan;
        }
    }
}
