package game;

/**
 * Link-specific revision-830 equipment presentation.
 *
 * Unlike Mario's geometry-envelope fitter, this adapter consumes liboot's real
 * Adult Link skeleton socket pose. The first proof intentionally keeps the 830
 * worn helmet near native Matrix scale because Adult Link was already scaled into
 * the 830 human envelope for this purpose.
 */
final class LinkEquipmentAdapter {

    private static final int EQUIPMENT_SLOT_HAT = 0;
    /* Inverse of ItemDefinitions' decompiler multiplier -1127488921. */
    private static final int EQUIP_SLOT_DECODE_MULTIPLIER = -917104297;

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int RAW_BUILD_FLAGS =
            BASE_MODEL_FLAGS | TRANSFORM_FLAGS | 0x1f01f | 0x80000;
    private static final int FINAL_MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;

    private static final float HELMET_SCALE = resolvePositiveFloat(
            "matrix3.oot.helmetScale", 1.0F);
    private static final float HELMET_OFFSET_X = resolveFiniteFloat(
            "matrix3.oot.helmetOffsetX", 0.0F);
    private static final float HELMET_OFFSET_Y = resolveFiniteFloat(
            "matrix3.oot.helmetOffsetY", 0.0F);
    private static final float HELMET_OFFSET_Z = resolveFiniteFloat(
            "matrix3.oot.helmetOffsetZ", 0.0F);
    private static final float HELMET_YAW_DEGREES = resolveFiniteFloat(
            "matrix3.oot.helmetYawDegrees", 180.0F);
    private static final float HELMET_PITCH_DEGREES = resolveFiniteFloat(
            "matrix3.oot.helmetPitchDegrees", 0.0F);
    private static final float HELMET_ROLL_DEGREES = resolveFiniteFloat(
            "matrix3.oot.helmetRollDegrees", 0.0F);

    private static final Class261 HELMET_TRANSFORM = new Class261();
    private static final Class90 HELMET_BOUNDS = new Class90();

    private static Class106 cachedRenderer;
    private static int cachedItemId = -1;
    private static boolean cachedFemale;
    private static Class634 cachedCustomization;
    private static Model cachedHelmetModel;
    private static RawBounds cachedHelmetBounds;
    private static String cachedHelmetName;
    private static boolean activeLogged;
    private static boolean noHelmetLogged;
    private static boolean noSocketLogged;
    private static int lastFailedItemId = -1;

    private LinkEquipmentAdapter() {
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!PlayerControllerMode.isLinkMode()) {
            resetSession();
            return;
        }
        if (scene == null || renderer == null || !OotBridgeSession.isReady()) {
            return;
        }

        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null || !LinkVisualRenderer.shouldSuppressLocalPlayer(player)) {
            return;
        }

        OotBridgeSession.LinkFrame frame = OotBridgeSession.getLatestFrame();
        if (frame == null || !frame.skeletonAvailable) {
            return;
        }
        LinkCharacterFit.Profile fit = LinkCharacterFit.resolve(player, frame);
        if (fit == null) {
            return;
        }

        LinkSkeletonSockets.SocketPose head = LinkSkeletonSockets.resolveHead(frame, fit);
        if (head == null) {
            if (!noSocketLogged) {
                noSocketLogged = true;
                System.err.println("[OoT Equipment] Adult Link HEAD socket unavailable"
                        + " joints=" + frame.skeletonJointCount);
            }
            return;
        }
        noSocketLogged = false;

        HelmetAppearance helmet = findVisibleHelmet(player);
        if (helmet == null) {
            if (!noHelmetLogged) {
                noHelmetLogged = true;
                System.out.println("[OoT Equipment] No revision-830 hat-slot item equipped;"
                        + " equip a helmet to test the Adult Link HEAD socket");
            }
            return;
        }
        noHelmetLogged = false;

        if (!ensureHelmetModel(renderer, helmet)) {
            return;
        }

        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null) {
            return;
        }

        float[] correction = eulerCorrection(
                HELMET_PITCH_DEGREES,
                HELMET_YAW_DEGREES,
                HELMET_ROLL_DEGREES);
        float[] rotation = multiply3x3(head.rotation, correction);
        if (rotation == null) {
            return;
        }

        float worldOffsetX = rotation[0] * HELMET_OFFSET_X
                + rotation[1] * HELMET_OFFSET_Y
                + rotation[2] * HELMET_OFFSET_Z;
        float worldOffsetY = rotation[3] * HELMET_OFFSET_X
                + rotation[4] * HELMET_OFFSET_Y
                + rotation[5] * HELMET_OFFSET_Z;
        float worldOffsetZ = rotation[6] * HELMET_OFFSET_X
                + rotation[7] * HELMET_OFFSET_Y
                + rotation[8] * HELMET_OFFSET_Z;

        Class240 position = playerTransform.aClass240_2647;
        HELMET_TRANSFORM.method3572(
                rotation[0], rotation[1], rotation[2],
                rotation[3], rotation[4], rotation[5],
                rotation[6], rotation[7], rotation[8]);
        HELMET_TRANSFORM.method3578(HELMET_SCALE, HELMET_SCALE, HELMET_SCALE);
        HELMET_TRANSFORM.method3580(
                position.aFloat2653 + head.x + worldOffsetX,
                position.aFloat2656 + head.y + worldOffsetY,
                position.aFloat2657 + head.z + worldOffsetZ);

        try {
            cachedHelmetModel.method1375(HELMET_TRANSFORM, HELMET_BOUNDS, 0);
            if (!activeLogged) {
                activeLogged = true;
                System.out.println("[OoT Equipment] 830 HELMET -> ADULT Link HEAD ACTIVE"
                        + " item=" + cachedItemId
                        + " name=" + cachedHelmetName
                        + " joint=" + head.joint
                        + " skeletonJoints=" + frame.skeletonJointCount
                        + " scale=" + HELMET_SCALE
                        + " rawW/H/D=" + cachedHelmetBounds.width
                        + "/" + cachedHelmetBounds.height
                        + "/" + cachedHelmetBounds.depth
                        + " offset=" + HELMET_OFFSET_X
                        + "/" + HELMET_OFFSET_Y
                        + "/" + HELMET_OFFSET_Z
                        + " rotDeg=" + HELMET_PITCH_DEGREES
                        + "/" + HELMET_YAW_DEGREES
                        + "/" + HELMET_ROLL_DEGREES
                        + " source=liboot-skeleton-v1");
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
            return new HelmetAppearance(
                    itemId, definition, customization, appearance.aBool5314);
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
        cachedHelmetBounds = null;
        cachedHelmetName = helmet.definition.aString8180;
        activeLogged = false;

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
            if (bounds == null || bounds.width <= 0.0F
                    || bounds.height <= 0.0F || bounds.depth <= 0.0F) {
                logHelmetFailure(helmet.itemId, "invalid worn-model bounds");
                return false;
            }

            /*
             * Worn models are authored around the RuneScape avatar origin.
             * Recenter only the equipment mesh; its scale stays near-native and
             * the real OoT HEAD socket supplies the animated world placement.
             */
            raw.method2564(-bounds.centerX, -bounds.centerY, -bounds.centerZ);
            Model model = renderer.method1755(raw, RAW_BUILD_FLAGS, 0, 64, 850);
            if (model == null) {
                logHelmetFailure(helmet.itemId, "Matrix model build returned null");
                return false;
            }
            model.method1450(FINAL_MODEL_FLAGS);

            cachedHelmetModel = model;
            cachedHelmetBounds = bounds;
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

        return new RawBounds(
                Math.round((minX + maxX) * 0.5F),
                Math.round((minY + maxY) * 0.5F),
                Math.round((minZ + maxZ) * 0.5F),
                maxX - minX,
                maxY - minY,
                maxZ - minZ);
    }

    private static float[] eulerCorrection(float pitchDegrees,
            float yawDegrees, float rollDegrees) {
        float[] pitch = rotationX((float) Math.toRadians(pitchDegrees));
        float[] yaw = rotationY((float) Math.toRadians(yawDegrees));
        float[] roll = rotationZ((float) Math.toRadians(rollDegrees));
        return multiply3x3(multiply3x3(yaw, pitch), roll);
    }

    private static float[] rotationX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[] {
                1.0F, 0.0F, 0.0F,
                0.0F, c, -s,
                0.0F, s, c
        };
    }

    private static float[] rotationY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[] {
                c, 0.0F, -s,
                0.0F, 1.0F, 0.0F,
                s, 0.0F, c
        };
    }

    private static float[] rotationZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[] {
                c, -s, 0.0F,
                s, c, 0.0F,
                0.0F, 0.0F, 1.0F
        };
    }

    private static float[] multiply3x3(float[] left, float[] right) {
        if (left == null || right == null || left.length != 9 || right.length != 9) {
            return null;
        }
        float[] result = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float value = 0.0F;
                for (int k = 0; k < 3; k++) {
                    value += left[row * 3 + k] * right[k * 3 + col];
                }
                if (!finite(value)) {
                    return null;
                }
                result[row * 3 + col] = value;
            }
        }
        return result;
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
            return finite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static void logHelmetFailure(int itemId, String reason) {
        if (lastFailedItemId == itemId) {
            return;
        }
        lastFailedItemId = itemId;
        System.err.println("[OoT Equipment] Helmet item=" + itemId + " " + reason);
    }

    private static void resetSession() {
        cachedRenderer = null;
        cachedItemId = -1;
        cachedFemale = false;
        cachedCustomization = null;
        cachedHelmetModel = null;
        cachedHelmetBounds = null;
        cachedHelmetName = null;
        activeLogged = false;
        noHelmetLogged = false;
        noSocketLogged = false;
        lastFailedItemId = -1;
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
        final float width;
        final float height;
        final float depth;

        RawBounds(int centerX, int centerY, int centerZ,
                float width, float height, float depth) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }
}
