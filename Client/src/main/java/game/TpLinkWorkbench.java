package game;

/**
 * Live developer-owned tuning state for Twilight Princess Link.
 * Matrix3 remains the world/input/gameplay owner.
 */
public final class TpLinkWorkbench {

    public enum PreviewAnimation {
        AUTO,
        IDLE,
        WALK,
        SWORD
    }

    private static final float DEFAULT_WORLD_SCALE = 5.0F;
    private static final float DEFAULT_RS_WIDTH = 0.92F;
    private static final float DEFAULT_RS_HEIGHT = 1.0F;
    private static final float DEFAULT_RS_DEPTH = 0.95F;
    private static final float DEFAULT_TURN_SPEED = 720.0F;
    private static final float DEFAULT_CONTACT_FRAME = 5.0F;

    private static volatile float worldScale = positiveProperty(
            "matrix3.tp.modelScale", DEFAULT_WORLD_SCALE);
    private static volatile float modelWidth = positiveProperty(
            "matrix3.tp.modelWidth", DEFAULT_RS_WIDTH);
    private static volatile float modelHeight = positiveProperty(
            "matrix3.tp.modelHeight", DEFAULT_RS_HEIGHT);
    private static volatile float modelDepth = positiveProperty(
            "matrix3.tp.modelDepth", DEFAULT_RS_DEPTH);
    private static volatile float offsetX = finiteProperty("matrix3.tp.offsetX", 0.0F);
    private static volatile float offsetY = finiteProperty("matrix3.tp.offsetY", 0.0F);
    private static volatile float offsetZ = finiteProperty("matrix3.tp.offsetZ", 0.0F);
    private static volatile float yawOffsetDegrees = finiteProperty(
            "matrix3.tp.yawOffsetDegrees", 0.0F);

    private static volatile float animationSpeed = 1.0F;
    private static volatile float movementThreshold = 0.25F;
    private static volatile int movementHoldMillis = 180;
    private static volatile float controllerTurnSpeed = positiveProperty(
            "matrix3.tp.turnSpeed", DEFAULT_TURN_SPEED);
    private static volatile float combatContactFrame = positiveProperty(
            "matrix3.tp.combatContactFrame", DEFAULT_CONTACT_FRAME);
    private static volatile boolean textureColorSampling = true;
    private static volatile PreviewAnimation previewAnimation = PreviewAnimation.AUTO;

    private static volatile long configRevision = 1L;
    private static volatile long reloadRevision;

    private static volatile boolean assetLoaded;
    private static volatile String assetPath = "Not loaded";
    private static volatile int vertexCount;
    private static volatile int triangleCount;
    private static volatile int jointCount;
    private static volatile int idleFrames;
    private static volatile int walkFrames;
    private static volatile int swordFrames;
    private static volatile boolean texturePresent;
    private static volatile String activeClip = "none";
    private static volatile int activeFrame;
    private static volatile boolean movementDetected;
    private static volatile boolean replacementReady;
    private static volatile String lastFailure = "None";

    private TpLinkWorkbench() {
    }

    public static float getWorldScale() {
        return worldScale;
    }

    public static void setWorldScale(float value) {
        worldScale = clampFinite(value, 0.10F, 20.0F, DEFAULT_WORLD_SCALE);
        bumpConfig();
    }

    public static float getModelWidth() {
        return modelWidth;
    }

    public static void setModelWidth(float value) {
        modelWidth = clampFinite(value, 0.25F, 2.0F, DEFAULT_RS_WIDTH);
        bumpConfig();
    }

    public static float getModelHeight() {
        return modelHeight;
    }

    public static void setModelHeight(float value) {
        modelHeight = clampFinite(value, 0.25F, 2.0F, DEFAULT_RS_HEIGHT);
        bumpConfig();
    }

    public static float getModelDepth() {
        return modelDepth;
    }

    public static void setModelDepth(float value) {
        modelDepth = clampFinite(value, 0.25F, 2.0F, DEFAULT_RS_DEPTH);
        bumpConfig();
    }

    public static float getOffsetX() {
        return offsetX;
    }

    public static void setOffsetX(float value) {
        offsetX = clampFinite(value, -1000.0F, 1000.0F, 0.0F);
        bumpConfig();
    }

    public static float getOffsetY() {
        return offsetY;
    }

    public static void setOffsetY(float value) {
        offsetY = clampFinite(value, -1000.0F, 1000.0F, 0.0F);
        bumpConfig();
    }

    public static float getOffsetZ() {
        return offsetZ;
    }

    public static void setOffsetZ(float value) {
        offsetZ = clampFinite(value, -1000.0F, 1000.0F, 0.0F);
        bumpConfig();
    }

    public static float getYawOffsetDegrees() {
        return yawOffsetDegrees;
    }

    public static void setYawOffsetDegrees(float value) {
        yawOffsetDegrees = clampFinite(value, -360.0F, 360.0F, 0.0F);
        bumpConfig();
    }

    public static float getAnimationSpeed() {
        return animationSpeed;
    }

    public static void setAnimationSpeed(float value) {
        animationSpeed = clampFinite(value, 0.05F, 4.0F, 1.0F);
        bumpConfig();
    }

    public static float getMovementThreshold() {
        return movementThreshold;
    }

    public static void setMovementThreshold(float value) {
        movementThreshold = clampFinite(value, 0.001F, 5.0F, 0.25F);
        bumpConfig();
    }

    public static int getMovementHoldMillis() {
        return movementHoldMillis;
    }

    public static void setMovementHoldMillis(int value) {
        movementHoldMillis = Math.max(0, Math.min(2000, value));
        bumpConfig();
    }

    public static float getControllerTurnSpeed() {
        return controllerTurnSpeed;
    }

    public static void setControllerTurnSpeed(float value) {
        controllerTurnSpeed = clampFinite(value, 30.0F, 2160.0F, DEFAULT_TURN_SPEED);
    }

    public static float getCombatContactFrame() {
        return combatContactFrame;
    }

    public static void setCombatContactFrame(float value) {
        combatContactFrame = clampFinite(value, 0.0F, 120.0F, DEFAULT_CONTACT_FRAME);
    }

    public static boolean isTextureColorSamplingEnabled() {
        return textureColorSampling;
    }

    public static void setTextureColorSamplingEnabled(boolean enabled) {
        textureColorSampling = enabled;
        bumpConfig();
    }

    public static PreviewAnimation getPreviewAnimation() {
        return previewAnimation;
    }

    public static void setPreviewAnimation(PreviewAnimation value) {
        previewAnimation = value == null ? PreviewAnimation.AUTO : value;
        bumpConfig();
    }

    public static long getConfigRevision() {
        return configRevision;
    }

    public static long getReloadRevision() {
        return reloadRevision;
    }

    public static void requestAssetReload() {
        reloadRevision++;
    }

    public static void useNativeProportions() {
        modelWidth = 1.0F;
        modelHeight = 1.0F;
        modelDepth = 1.0F;
        bumpConfig();
    }

    public static void useRuneScapeFitProportions() {
        worldScale = DEFAULT_WORLD_SCALE;
        modelWidth = DEFAULT_RS_WIDTH;
        modelHeight = DEFAULT_RS_HEIGHT;
        modelDepth = DEFAULT_RS_DEPTH;
        offsetX = 0.0F;
        offsetY = 0.0F;
        offsetZ = 0.0F;
        bumpConfig();
    }

    public static void resetPresentation() {
        worldScale = DEFAULT_WORLD_SCALE;
        modelWidth = DEFAULT_RS_WIDTH;
        modelHeight = DEFAULT_RS_HEIGHT;
        modelDepth = DEFAULT_RS_DEPTH;
        offsetX = 0.0F;
        offsetY = 0.0F;
        offsetZ = 0.0F;
        yawOffsetDegrees = 0.0F;
        bumpConfig();
    }

    public static void resetAnimation() {
        animationSpeed = 1.0F;
        movementThreshold = 0.25F;
        movementHoldMillis = 180;
        previewAnimation = PreviewAnimation.AUTO;
        bumpConfig();
    }

    public static void resetFacingTuning() {
        controllerTurnSpeed = DEFAULT_TURN_SPEED;
    }

    static void reportAsset(String path, int vertices, int triangles, int joints,
            int idle, int walk, int sword, boolean hasTexture) {
        assetLoaded = true;
        assetPath = path == null ? "Unknown" : path;
        vertexCount = vertices;
        triangleCount = triangles;
        jointCount = joints;
        idleFrames = idle;
        walkFrames = walk;
        swordFrames = sword;
        texturePresent = hasTexture;
        lastFailure = "None";
    }

    static void reportFrame(String clip, int frame, boolean moving, boolean ready) {
        activeClip = clip == null ? "none" : clip;
        activeFrame = frame;
        movementDetected = moving;
        replacementReady = ready;
    }

    static void reportInactive() {
        replacementReady = false;
        activeClip = "none";
        activeFrame = 0;
        movementDetected = false;
    }

    static void reportFailure(String message) {
        lastFailure = message == null ? "Unknown" : message;
        replacementReady = false;
    }

    static void reportAssetUnloaded() {
        assetLoaded = false;
        assetPath = "Not loaded";
    }

    public static boolean isAssetLoaded() {
        return assetLoaded;
    }

    public static String getAssetPath() {
        return assetPath;
    }

    public static int getVertexCount() {
        return vertexCount;
    }

    public static int getTriangleCount() {
        return triangleCount;
    }

    public static int getJointCount() {
        return jointCount;
    }

    public static int getIdleFrames() {
        return idleFrames;
    }

    public static int getWalkFrames() {
        return walkFrames;
    }

    public static int getSwordFrames() {
        return swordFrames;
    }

    public static boolean isTexturePresent() {
        return texturePresent;
    }

    public static String getActiveClip() {
        return activeClip;
    }

    public static int getActiveFrame() {
        return activeFrame;
    }

    public static boolean isMovementDetected() {
        return movementDetected;
    }

    public static boolean isReplacementReady() {
        return replacementReady;
    }

    public static String getLastFailure() {
        return lastFailure;
    }

    private static void bumpConfig() {
        configRevision++;
    }

    private static float positiveProperty(String key, float fallback) {
        float value = finiteProperty(key, fallback);
        return value > 0.0F ? value : fallback;
    }

    private static float finiteProperty(String key, float fallback) {
        String configured = System.getProperty(key);
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(configured.trim());
            if (!Float.isNaN(value) && !Float.isInfinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static float clampFinite(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            value = fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
