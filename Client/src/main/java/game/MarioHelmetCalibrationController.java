package game;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-only live calibration for Mario helmet presentation.
 *
 * This intentionally does not persist item overrides yet. The user can tune a
 * representative set of helmets at runtime, print the exact values, and then the
 * accepted common values can become the Mario helmet profile while true outliers
 * receive explicit per-item overrides later.
 */
final class MarioHelmetCalibrationController {

    // Class549_Sub1 normalized-key mappings, verified from anIntArray8901.
    private static final int INTERNAL_F6_KEY = 6;
    private static final int INTERNAL_R_KEY = 35;
    private static final int INTERNAL_P_KEY = 41;
    private static final int INTERNAL_OPEN_BRACKET_KEY = 42;
    private static final int INTERNAL_CLOSE_BRACKET_KEY = 43;
    private static final int INTERNAL_SHIFT_KEY = 81;
    private static final int INTERNAL_LEFT_KEY = 96;
    private static final int INTERNAL_RIGHT_KEY = 97;
    private static final int INTERNAL_UP_KEY = 98;
    private static final int INTERNAL_DOWN_KEY = 99;
    private static final int INTERNAL_HOME_KEY = 102;
    private static final int INTERNAL_END_KEY = 103;
    private static final int INTERNAL_PAGE_UP_KEY = 104;
    private static final int INTERNAL_PAGE_DOWN_KEY = 105;

    private static final float POSITION_STEP = 2.0F;
    private static final float SCALE_STEP = 0.02F;
    private static final float YAW_STEP_DEGREES = 5.0F;
    private static final float COARSE_MULTIPLIER = 5.0F;

    private static final boolean[] KEY_WAS_DOWN = new boolean[112];
    private static final Map<Integer, Calibration> SESSION_CALIBRATIONS =
            new HashMap<Integer, Calibration>();

    /* Swing workbench reads this outside the client thread. */
    private static volatile boolean active;
    private static int activeItemId = -1;
    private static String activeItemName;

    private MarioHelmetCalibrationController() {
    }

    static synchronized void tick(int itemId, String itemName) {
        boolean togglePressed = pressedEdge(INTERNAL_F6_KEY);
        if (togglePressed) {
            if (active) {
                printCurrent("CALIBRATION OFF");
                active = false;
                activeItemId = -1;
                activeItemName = null;
                Sm64BridgeSession.setPresentationFrozen(false);
                clearAdjustmentEdges();
            } else if (itemId >= 0) {
                active = true;
                activeItemId = itemId;
                activeItemName = safeName(itemName);
                Sm64BridgeSession.setPresentationFrozen(true);
                clearAdjustmentEdges();
                System.out.println("[SM64 Equipment Calibration] ON item=" + itemId
                        + " name=" + activeItemName + " presentation=FROZEN");
                System.out.println("[SM64 Equipment Calibration] Controls:"
                        + " Left/Right=local X, Up/Down=local Y, PgUp/PgDn=local Z,"
                        + " Home/End=scale -/+, [/]=yaw -/+5deg,"
                        + " Shift=5x step, R=reset item, P=print, F6=done");
                printCurrent("CURRENT");
            } else {
                System.out.println("[SM64 Equipment Calibration] Equip a helmet before pressing F6.");
            }
        }

        if (!active) {
            return;
        }
        if (itemId < 0) {
            System.out.println("[SM64 Equipment Calibration] OFF - helmet unequipped.");
            active = false;
            activeItemId = -1;
            activeItemName = null;
            Sm64BridgeSession.setPresentationFrozen(false);
            clearAdjustmentEdges();
            return;
        }
        if (activeItemId != itemId) {
            activeItemId = itemId;
            activeItemName = safeName(itemName);
            clearAdjustmentEdges();
            System.out.println("[SM64 Equipment Calibration] Switched item=" + itemId
                    + " name=" + activeItemName);
            printCurrent("CURRENT");
        }

        Calibration calibration = getOrCreate(itemId);
        float coarse = AlternateCharacterInputKeyboard.rawKeyDown(INTERNAL_SHIFT_KEY)
                ? COARSE_MULTIPLIER : 1.0F;
        boolean changed = false;
        boolean manualOverride = false;

        if (pressedEdge(INTERNAL_LEFT_KEY)) {
            calibration.offsetX -= POSITION_STEP * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_RIGHT_KEY)) {
            calibration.offsetX += POSITION_STEP * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_UP_KEY)) {
            calibration.offsetY -= POSITION_STEP * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_DOWN_KEY)) {
            calibration.offsetY += POSITION_STEP * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_PAGE_UP_KEY)) {
            calibration.offsetZ += POSITION_STEP * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_PAGE_DOWN_KEY)) {
            calibration.offsetZ -= POSITION_STEP * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_HOME_KEY)) {
            calibration.scaleMultiplier = Math.max(
                    0.10F, calibration.scaleMultiplier - SCALE_STEP * coarse);
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_END_KEY)) {
            calibration.scaleMultiplier = Math.min(
                    5.00F, calibration.scaleMultiplier + SCALE_STEP * coarse);
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_OPEN_BRACKET_KEY)) {
            calibration.yawDegrees -= YAW_STEP_DEGREES * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_CLOSE_BRACKET_KEY)) {
            calibration.yawDegrees += YAW_STEP_DEGREES * coarse;
            changed = true;
            manualOverride = true;
        }
        if (pressedEdge(INTERNAL_R_KEY)) {
            calibration.reset();
            changed = true;
        }
        if (pressedEdge(INTERNAL_P_KEY)) {
            printCurrent("CURRENT");
        }

        if (changed) {
            if (manualOverride) {
                calibration.autoFitResolved = true;
            }
            printCurrent("ADJUST");
        }
    }

    static synchronized void onMarioModeInactive() {
        if (active) {
            printCurrent("CALIBRATION OFF");
        }
        active = false;
        activeItemId = -1;
        activeItemName = null;
        Sm64BridgeSession.setPresentationFrozen(false);
        for (int i = 0; i < KEY_WAS_DOWN.length; i++) {
            KEY_WAS_DOWN[i] = false;
        }
    }

    static boolean isActive() {
        return active;
    }

    /**
     * Prevents held-key Matrix camera/tool consumers from reacting to calibration
     * input while the raw delegate remains available to this controller.
     */
    static boolean shouldSuppressMatrixKey(int internalKey) {
        if (!PlayerControllerMode.isMarioMode()) {
            return false;
        }
        if (internalKey == INTERNAL_F6_KEY) {
            return true;
        }
        if (!active) {
            return false;
        }
        return internalKey == INTERNAL_LEFT_KEY
                || internalKey == INTERNAL_RIGHT_KEY
                || internalKey == INTERNAL_UP_KEY
                || internalKey == INTERNAL_DOWN_KEY
                || internalKey == INTERNAL_HOME_KEY
                || internalKey == INTERNAL_END_KEY
                || internalKey == INTERNAL_PAGE_UP_KEY
                || internalKey == INTERNAL_PAGE_DOWN_KEY
                || internalKey == INTERNAL_OPEN_BRACKET_KEY
                || internalKey == INTERNAL_CLOSE_BRACKET_KEY
                || internalKey == INTERNAL_R_KEY
                || internalKey == INTERNAL_P_KEY;
    }

    static synchronized Snapshot getSnapshot(int itemId) {
        if (itemId < 0) {
            return Snapshot.DEFAULT;
        }
        Calibration calibration = getOrCreate(itemId);
        return new Snapshot(
                calibration.scaleMultiplier,
                calibration.offsetX,
                calibration.offsetY,
                calibration.offsetZ,
                calibration.yawDegrees);
    }

    /** N64 developer workbench seam. Values remain session-only. */
    static synchronized void setSnapshot(int itemId, Snapshot snapshot) {
        if (itemId < 0 || snapshot == null) {
            return;
        }
        Calibration calibration = getOrCreate(itemId);
        calibration.scaleMultiplier = clamp(snapshot.scaleMultiplier, 0.10F, 5.00F);
        calibration.offsetX = finiteOrZero(snapshot.offsetX);
        calibration.offsetY = finiteOrZero(snapshot.offsetY);
        calibration.offsetZ = finiteOrZero(snapshot.offsetZ);
        calibration.yawDegrees = finiteOrZero(snapshot.yawDegrees);
        calibration.autoFitResolved = true;
    }

    static synchronized void resetItem(int itemId) {
        if (itemId < 0) {
            return;
        }
        getOrCreate(itemId).reset();
    }

    private static Calibration getOrCreate(int itemId) {
        Integer key = Integer.valueOf(itemId);
        Calibration calibration = SESSION_CALIBRATIONS.get(key);
        if (calibration == null) {
            calibration = new Calibration();
            SESSION_CALIBRATIONS.put(key, calibration);
        }
        if (!calibration.autoFitResolved) {
            float recommended = MarioHelmetAutoFit.recommendScaleMultiplier(itemId);
            if (!Float.isNaN(recommended) && !Float.isInfinite(recommended)
                    && recommended > 0.0F) {
                calibration.scaleMultiplier = clamp(recommended, 0.10F, 5.00F);
                calibration.autoFitResolved = true;
            }
        }
        return calibration;
    }

    private static boolean pressedEdge(int internalKey) {
        if (internalKey < 0 || internalKey >= KEY_WAS_DOWN.length) {
            return false;
        }
        boolean down = AlternateCharacterInputKeyboard.rawKeyDown(internalKey);
        boolean pressed = down && !KEY_WAS_DOWN[internalKey];
        KEY_WAS_DOWN[internalKey] = down;
        return pressed;
    }

    private static void clearAdjustmentEdges() {
        for (int i = 0; i < KEY_WAS_DOWN.length; i++) {
            if (i != INTERNAL_F6_KEY) {
                KEY_WAS_DOWN[i] = false;
            }
        }
    }

    private static void printCurrent(String label) {
        if (activeItemId < 0) {
            return;
        }
        Calibration calibration = getOrCreate(activeItemId);
        System.out.println("[SM64 Equipment Calibration] " + label
                + " item=" + activeItemId
                + " name=" + safeName(activeItemName)
                + " scale=" + calibration.scaleMultiplier
                + " x=" + calibration.offsetX
                + " y=" + calibration.offsetY
                + " z=" + calibration.offsetZ
                + " yawDeltaDeg=" + calibration.yawDegrees);
    }

    private static String safeName(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private static float finiteOrZero(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0.0F : value;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        return value < min ? min : value > max ? max : value;
    }

    static final class Snapshot {
        static final Snapshot DEFAULT = new Snapshot(1.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        final float scaleMultiplier;
        final float offsetX;
        final float offsetY;
        final float offsetZ;
        final float yawDegrees;

        Snapshot(float scaleMultiplier, float offsetX, float offsetY,
                float offsetZ, float yawDegrees) {
            this.scaleMultiplier = scaleMultiplier;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.yawDegrees = yawDegrees;
        }
    }

    private static final class Calibration {
        float scaleMultiplier = 1.0F;
        float offsetX;
        float offsetY;
        float offsetZ;
        float yawDegrees;
        boolean autoFitResolved;

        void reset() {
            scaleMultiplier = 1.0F;
            offsetX = 0.0F;
            offsetY = 0.0F;
            offsetZ = 0.0F;
            yawDegrees = 0.0F;
            autoFitResolved = false;
        }
    }
}
