package game;

/**
 * Owner-facing visual simulation clock for Dev Mode.
 *
 * V1 deliberately controls only transient client visual simulation
 * (spot animations / projectiles). Networking, input, rendering, the real
 * client cycle counter, and server simulation remain authoritative and
 * continue at normal speed.
 */
public final class DevTimeController {

    private static final int[] SPEED_DIVISORS = { 1, 2, 4, 10 };
    private static final String[] SPEED_LABELS = { "1.00x", "0.50x", "0.25x", "0.10x" };

    private static volatile boolean paused;
    private static volatile boolean stepPending;
    private static volatile boolean advanceVisuals = true;
    private static volatile int speedIndex;
    private static volatile int currentClientCycle = -1;

    private DevTimeController() {
    }

    /**
     * Called once from the real Matrix3 client logic tick. This never blocks or
     * skips the owning client tick; it only decides whether visual-only owners
     * may advance during this cycle.
     */
    public static synchronized void beginClientCycle(int clientCycle) {
        currentClientCycle = clientCycle;
        if (paused) {
            if (stepPending) {
                stepPending = false;
                advanceVisuals = true;
            } else {
                advanceVisuals = false;
            }
            return;
        }

        int divisor = SPEED_DIVISORS[speedIndex];
        advanceVisuals = divisor <= 1 || Math.floorMod(clientCycle, divisor) == 0;
    }

    public static boolean shouldAdvanceVisuals() {
        return advanceVisuals;
    }

    public static synchronized boolean togglePaused() {
        paused = !paused;
        stepPending = false;
        if (!paused) {
            advanceVisuals = true;
        }
        return paused;
    }

    public static synchronized void stepOnce() {
        paused = true;
        stepPending = true;
    }

    public static synchronized String cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEED_DIVISORS.length;
        return getSpeedLabel();
    }

    public static synchronized void reset() {
        paused = false;
        stepPending = false;
        advanceVisuals = true;
        speedIndex = 0;
        currentClientCycle = -1;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static boolean isStepPending() {
        return stepPending;
    }

    public static String getSpeedLabel() {
        return SPEED_LABELS[speedIndex];
    }

    public static int getCurrentClientCycle() {
        return currentClientCycle;
    }

    public static String getStatusText() {
        if (paused) {
            return stepPending
                    ? "PAUSED / STEP ARMED (" + getSpeedLabel() + " resume)"
                    : "PAUSED (" + getSpeedLabel() + " resume)";
        }
        return "RUN " + getSpeedLabel();
    }
}
