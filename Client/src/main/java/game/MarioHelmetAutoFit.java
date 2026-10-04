package game;

import java.util.Arrays;

/**
 * One-shot mathematical default for Mario helmet scale.
 *
 * Protocol-v2 semantic geometry supplies a stable display-list-local FACE
 * reference shared with the equipment adapter. Legacy protocol-v1 frames keep the
 * older current-pose measurement only as a fail-open fallback. Manual workbench
 * calibration remains the final visual authority after this initial estimate.
 */
final class MarioHelmetAutoFit {

    private static final int EQUIPMENT_SLOT_HAT = 0;
    private static final int EQUIP_SLOT_DECODE_MULTIPLIER = -917104297;

    private static final float HEAD_START_FRACTION = 0.50F;
    private static final float HEAD_RADIAL_FRACTION = 0.40F;
    private static final float HEAD_TRIM_FRACTION = 0.06F;
    private static final int MIN_HEAD_VERTICES = 12;

    private static final float DEFAULT_MODEL_SCALE = 2.0F;
    private static final float DEFAULT_CLEARANCE_FRACTION = 0.05F;
    private static final float DEFAULT_MIN_CLEARANCE = 2.0F;
    private static final float DEFAULT_CAVITY_HORIZONTAL_FRACTION = 0.72F;
    private static final float DEFAULT_CAVITY_VERTICAL_FRACTION = 0.82F;
    private static final float DEFAULT_VERTICAL_ASSIST_LIMIT = 1.10F;

    private static int lastLoggedItemId = -1;

    private MarioHelmetAutoFit() {
    }

    static float recommendScaleMultiplier(int itemId) {
        if (itemId < 0) {
            return Float.NaN;
        }

        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        if (!usable(frame)) {
            return Float.NaN;
        }

        HelmetSource helmet = findHelmet(itemId);
        if (helmet == null) {
            return Float.NaN;
        }

        MarioSemanticGeometry.Reference semantic = MarioSemanticGeometry.getReference(frame);
        boolean semanticReference = semantic != null;
        Dimensions head = semanticReference
                ? dimensions(semantic.width, semantic.height, semantic.depth)
                : measureHead(frame);
        Dimensions outer = measureHelmet(helmet);
        if (head == null || outer == null) {
            return Float.NaN;
        }

        float clearanceFraction = propertyNonNegative(
                "matrix3.sm64.helmetClearanceFraction", DEFAULT_CLEARANCE_FRACTION);
        float minimumClearance = propertyNonNegative(
                "matrix3.sm64.helmetMinClearance", DEFAULT_MIN_CLEARANCE);
        float cavityHorizontal = propertyPositive(
                "matrix3.sm64.helmetCavityHorizontalFraction",
                DEFAULT_CAVITY_HORIZONTAL_FRACTION);
        float cavityVertical = propertyPositive(
                "matrix3.sm64.helmetCavityVerticalFraction",
                DEFAULT_CAVITY_VERTICAL_FRACTION);
        float verticalAssistLimit = propertyPositive(
                "matrix3.sm64.helmetVerticalAssistLimit",
                DEFAULT_VERTICAL_ASSIST_LIMIT);

        /*
         * Match MarioEquipmentAdapter's base scale so this helper returns only the
         * calibration multiplier layered on top. With protocol v2 both sides use
         * the same semantic FACE width. Legacy v1 keeps the old broad head span.
         */
        float headSpan = semanticReference ? head.width : Math.max(head.width, head.depth);
        float helmetSpan = Math.max(outer.width, outer.depth);
        float oldClearance = Math.max(minimumClearance, headSpan * clearanceFraction);
        float oldTargetSpan = headSpan + oldClearance * 2.0F;
        float oldAutoFit = oldTargetSpan / helmetSpan;
        if (!finite(oldAutoFit) || oldAutoFit <= 0.0F) {
            return Float.NaN;
        }

        float widthClearance = Math.max(minimumClearance, head.width * clearanceFraction);
        float heightClearance = Math.max(minimumClearance, head.height * clearanceFraction);
        float targetWidth = head.width + widthClearance * 2.0F;
        float targetHeight = head.height + heightClearance * 2.0F;

        float cavityWidth = outer.width * cavityHorizontal;
        float cavityHeight = outer.height * cavityVertical;
        if (cavityWidth <= 0.0F || cavityHeight <= 0.0F) {
            return Float.NaN;
        }

        float widthFit = targetWidth / cavityWidth;
        float heightFit = targetHeight / cavityHeight;
        float desiredAutoFit;
        float depthFit = Float.NaN;

        if (semanticReference) {
            /*
             * FACE local +Y contains Mario's protected nose projection. A full
             * helmet must not scale up just to contain that depth; the nose/face is
             * intentionally allowed to project through the helmet opening.
             */
            float verticalFit = Math.min(heightFit, widthFit * verticalAssistLimit);
            desiredAutoFit = Math.max(widthFit, verticalFit);
        } else {
            float depthClearance = Math.max(minimumClearance, head.depth * clearanceFraction);
            float targetDepth = head.depth + depthClearance * 2.0F;
            float cavityDepth = outer.depth * cavityHorizontal;
            if (cavityDepth <= 0.0F) {
                return Float.NaN;
            }
            depthFit = targetDepth / cavityDepth;
            float horizontalFit = Math.max(widthFit, depthFit);
            float verticalFit = Math.min(heightFit, horizontalFit * verticalAssistLimit);
            desiredAutoFit = Math.max(horizontalFit, verticalFit);
        }

        float multiplier = desiredAutoFit / oldAutoFit;
        if (!finite(multiplier) || multiplier <= 0.0F) {
            return Float.NaN;
        }
        multiplier = clamp(multiplier, 0.50F, 2.50F);

        if (lastLoggedItemId != itemId) {
            lastLoggedItemId = itemId;
            System.out.println("[SM64 Equipment AutoFit] item=" + itemId
                    + " name=" + helmet.name
                    + " reference=" + (semanticReference ? "semantic-face" : "legacy-current-pose")
                    + " headW/H/D=" + head.width + "/" + head.height + "/" + head.depth
                    + " helmetOuterW/H/D=" + outer.width + "/" + outer.height + "/" + outer.depth
                    + " cavityH=" + cavityHorizontal
                    + " cavityV=" + cavityVertical
                    + " widthFit=" + widthFit
                    + " heightFit=" + heightFit
                    + " depthFit=" + depthFit
                    + " baseAutoFit=" + oldAutoFit
                    + " desiredAutoFit=" + desiredAutoFit
                    + " manualScale=" + multiplier);
        }
        return multiplier;
    }

    private static Dimensions measureHead(Sm64BridgeSession.GeometryFrame frame) {
        int totalVertices = frame.triangleCount * 3;
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

        float bodyHeight = maxY - minY;
        float bodyWidth = maxX - minX;
        float bodyDepth = maxZ - minZ;
        if (!finite(bodyHeight) || bodyHeight <= 0.0F) {
            return null;
        }

        float centerX = (minX + maxX) * 0.5F;
        float centerZ = (minZ + maxZ) * 0.5F;
        float headStartY = minY + bodyHeight * HEAD_START_FRACTION;
        float radialLimit = Math.max(bodyWidth, bodyDepth) * HEAD_RADIAL_FRACTION;
        float radialLimitSquared = radialLimit * radialLimit;

        int[] candidates = new int[totalVertices];
        int count = 0;
        for (int vertex = 0; vertex < totalVertices; vertex++) {
            int base = vertex * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            float dx = x - centerX;
            float dz = z - centerZ;
            if (y >= headStartY && dx * dx + dz * dz <= radialLimitSquared) {
                candidates[count++] = vertex;
            }
        }
        if (count < MIN_HEAD_VERTICES) {
            return null;
        }

        float[] xs = new float[count];
        float[] zs = new float[count];
        for (int i = 0; i < count; i++) {
            int base = candidates[i] * 3;
            xs[i] = frame.positions[base] - frame.state.x;
            zs[i] = frame.positions[base + 2] - frame.state.z;
        }
        Arrays.sort(xs);
        Arrays.sort(zs);

        int trim = (int) Math.floor(count * HEAD_TRIM_FRACTION);
        if (trim * 2 >= count - MIN_HEAD_VERTICES) {
            trim = 0;
        }
        float coreMinX = xs[trim];
        float coreMaxX = xs[count - 1 - trim];
        float coreMinZ = zs[trim];
        float coreMaxZ = zs[count - 1 - trim];
        float coreMinY = Float.POSITIVE_INFINITY;
        float coreMaxY = Float.NEGATIVE_INFINITY;
        int coreCount = 0;

        for (int i = 0; i < count; i++) {
            int base = candidates[i] * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            if (x >= coreMinX && x <= coreMaxX && z >= coreMinZ && z <= coreMaxZ) {
                if (y < coreMinY) coreMinY = y;
                if (y > coreMaxY) coreMaxY = y;
                coreCount++;
            }
        }
        if (coreCount < MIN_HEAD_VERTICES) {
            return null;
        }

        float modelScale = propertyPositive("matrix3.sm64.modelScale", DEFAULT_MODEL_SCALE);
        return dimensions(
                (coreMaxX - coreMinX) * modelScale,
                (coreMaxY - coreMinY) * modelScale,
                (coreMaxZ - coreMinZ) * modelScale);
    }

    private static Dimensions measureHelmet(HelmetSource source) {
        try {
            Class159 raw = source.definition.method7531(
                    source.female, source.customization, (byte) 114);
            if (raw == null || raw.anInt1791 <= 0
                    || raw.anIntArray1782 == null
                    || raw.anIntArray1777 == null
                    || raw.anIntArray1797 == null) {
                return null;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < raw.anInt1791; i++) {
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
            return dimensions(maxX - minX, maxY - minY, maxZ - minZ);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static HelmetSource findHelmet(int itemId) {
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null || player.aClass474_11831 == null
                || player.aClass474_11831.anIntArray5317 == null) {
            return null;
        }
        Class474 appearance = player.aClass474_11831;
        for (int index = 0; index < appearance.anIntArray5317.length; index++) {
            int encoded = appearance.anIntArray5317[index];
            if ((encoded & 0x40000000) == 0 || (encoded & 0x3fffffff) != itemId) {
                continue;
            }
            ItemDefinitions definition = (ItemDefinitions) Class672.aClass639_Sub5_8533
                    .getDefinition(itemId, -1597715602);
            if (definition == null
                    || definition.equipSlot * EQUIP_SLOT_DECODE_MULTIPLIER != EQUIPMENT_SLOT_HAT) {
                return null;
            }
            Class634 customization = null;
            if (appearance.aClass634Array5318 != null
                    && index < appearance.aClass634Array5318.length) {
                customization = appearance.aClass634Array5318[index];
            }
            String name = definition.aString8180;
            return new HelmetSource(definition, customization, appearance.aBool5314,
                    name == null ? "unknown" : name);
        }
        return null;
    }

    private static Dimensions dimensions(float width, float height, float depth) {
        if (!finite(width) || !finite(height) || !finite(depth)
                || width <= 0.0F || height <= 0.0F || depth <= 0.0F) {
            return null;
        }
        return new Dimensions(width, height, depth);
    }

    private static boolean usable(Sm64BridgeSession.GeometryFrame frame) {
        return frame != null && frame.state != null && frame.triangleCount > 0
                && frame.positions != null
                && frame.positions.length >= frame.triangleCount * 9;
    }

    private static float propertyPositive(String key, float fallback) {
        float value = propertyFloat(key, fallback);
        return value > 0.0F ? value : fallback;
    }

    private static float propertyNonNegative(String key, float fallback) {
        float value = propertyFloat(key, fallback);
        return value >= 0.0F ? value : fallback;
    }

    private static float propertyFloat(String key, float fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            return finite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static final class Dimensions {
        final float width;
        final float height;
        final float depth;

        Dimensions(float width, float height, float depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    private static final class HelmetSource {
        final ItemDefinitions definition;
        final Class634 customization;
        final boolean female;
        final String name;

        HelmetSource(ItemDefinitions definition, Class634 customization,
                boolean female, String name) {
            this.definition = definition;
            this.customization = customization;
            this.female = female;
            this.name = name;
        }
    }
}
