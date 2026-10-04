package game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Persistent Java 8 <-> libsm64 sidecar session for Mario-mode simulation.
 *
 * The worker owns only native process I/O and fixed-step SM64 simulation. Matrix
 * scene transforms remain client-thread-owned: callers read the published native
 * state and apply presentation from the established viewport tick.
 */
public final class Sm64BridgeSession {

    private static final int PROTOCOL_VERSION = 1;
    private static final long STEP_NANOS = 1000000000L / 30L;
    private static final long MAX_SCHEDULE_DRIFT_NANOS = STEP_NANOS * 4L;

    private static volatile Worker current;

    private Sm64BridgeSession() {
    }

    public static synchronized void start() {
        Worker existing = current;
        if (existing != null && existing.isRunning()) {
            return;
        }

        Worker worker = new Worker(resolveBridgePath(), resolveRomPath());
        current = worker;
        worker.start();
    }

    public static synchronized void stop() {
        Worker worker = current;
        current = null;
        if (worker != null) {
            worker.stop();
        }
    }

    public static void setButtonA(boolean down) {
        Worker worker = current;
        if (worker != null) {
            worker.setButtonA(down);
        }
    }

    public static boolean isReady() {
        Worker worker = current;
        return worker != null && worker.isReady();
    }

    public static boolean hasFailed() {
        Worker worker = current;
        return worker != null && worker.hasFailed();
    }

    public static String getFailureReason() {
        Worker worker = current;
        return worker == null ? null : worker.getFailureReason();
    }

    public static Float getLatestY() {
        Worker worker = current;
        NativeState state = worker == null ? null : worker.getLatestState();
        return state == null ? null : Float.valueOf(state.y);
    }

    /**
     * One-native-tick-delayed interpolation keeps 30 Hz simulation smooth at
     * higher Matrix render/update rates without making physics frame dependent.
     */
    public static Float getInterpolatedY() {
        Worker worker = current;
        if (worker == null) {
            return null;
        }
        NativeState latest = worker.getLatestState();
        if (latest == null) {
            return null;
        }
        NativeState previous = worker.getPreviousState();
        if (previous == null) {
            return Float.valueOf(latest.y);
        }

        float alpha = (float) (System.nanoTime() - latest.receivedNanos) / (float) STEP_NANOS;
        if (alpha < 0.0F) {
            alpha = 0.0F;
        } else if (alpha > 1.0F) {
            alpha = 1.0F;
        }
        return Float.valueOf(previous.y + (latest.y - previous.y) * alpha);
    }

    private static final class Worker implements Runnable {
        private final File bridge;
        private final File rom;

        private volatile boolean running = true;
        private volatile boolean ready;
        private volatile boolean failed;
        private volatile String failureReason;
        private volatile boolean buttonA;
        private volatile NativeState previousState;
        private volatile NativeState latestState;

        private volatile Process process;
        private Thread thread;

        Worker(File bridge, File rom) {
            this.bridge = bridge;
            this.rom = rom;
        }

        void start() {
            thread = new Thread(this, "sm64-bridge-session");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            buttonA = false;
            ready = false;

            Process localProcess = process;
            if (localProcess != null) {
                localProcess.destroy();
            }
            Thread localThread = thread;
            if (localThread != null) {
                localThread.interrupt();
            }
        }

        boolean isRunning() {
            return running && !failed;
        }

        boolean isReady() {
            return ready && !failed;
        }

        boolean hasFailed() {
            return failed;
        }

        String getFailureReason() {
            return failureReason;
        }

        void setButtonA(boolean down) {
            buttonA = down;
        }

        NativeState getPreviousState() {
            return previousState;
        }

        NativeState getLatestState() {
            return latestState;
        }

        @Override
        public void run() {
            Process localProcess = null;
            BufferedReader reader = null;
            BufferedWriter writer = null;
            try {
                if (!bridge.isFile()) {
                    throw new IllegalStateException("sidecar not found: " + bridge.getAbsolutePath());
                }
                if (!rom.isFile()) {
                    throw new IllegalStateException("ROM not found: " + rom.getAbsolutePath());
                }

                ProcessBuilder builder = new ProcessBuilder(bridge.getAbsolutePath(), rom.getAbsolutePath());
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
                localProcess = builder.start();
                process = localProcess;

                reader = new BufferedReader(new InputStreamReader(localProcess.getInputStream(), "UTF-8"));
                writer = new BufferedWriter(new OutputStreamWriter(localProcess.getOutputStream(), "UTF-8"));

                String readyLine = reader.readLine();
                if (!("READY " + PROTOCOL_VERSION).equals(readyLine)) {
                    throw new IllegalStateException("unexpected READY: " + readyLine);
                }

                send(writer, "PING");
                String pong = reader.readLine();
                if (!("PONG " + PROTOCOL_VERSION).equals(pong)) {
                    throw new IllegalStateException("unexpected PONG: " + pong);
                }

                // Stabilize the freshly reset native Mario before accepting input.
                publish(step(writer, reader, false));
                publish(step(writer, reader, false));
                ready = true;
                System.out.println("[SM64 Bridge] Persistent session READY (30 Hz)");

                long nextStep = System.nanoTime();
                while (running) {
                    publish(step(writer, reader, buttonA));

                    nextStep += STEP_NANOS;
                    long now = System.nanoTime();
                    long waitNanos = nextStep - now;
                    if (waitNanos > 0L) {
                        sleepNanos(waitNanos);
                    } else if (-waitNanos > MAX_SCHEDULE_DRIFT_NANOS) {
                        nextStep = now;
                    }
                }
            } catch (Throwable throwable) {
                if (running) {
                    failed = true;
                    failureReason = throwable.getMessage() == null
                            ? throwable.getClass().getSimpleName()
                            : throwable.getMessage();
                    System.out.println("[SM64 Bridge] Persistent session failed: " + failureReason);
                }
            } finally {
                ready = false;
                running = false;
                buttonA = false;
                process = null;
                closeQuietly(writer);
                closeQuietly(reader);
                if (localProcess != null) {
                    localProcess.destroy();
                    try {
                        localProcess.waitFor(250L, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private void publish(NativeState state) {
            previousState = latestState;
            latestState = state;
        }
    }

    private static NativeState step(BufferedWriter writer, BufferedReader reader, boolean buttonA) throws Exception {
        send(writer, String.format(Locale.ROOT,
                "STEP 0.0 -1.0 0.0 0.0 %d 0 0",
                buttonA ? 1 : 0));
        return NativeState.parse(reader.readLine(), System.nanoTime());
    }

    private static void send(BufferedWriter writer, String command) throws Exception {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private static void sleepNanos(long nanos) throws InterruptedException {
        long millis = nanos / 1000000L;
        int remainderNanos = (int) (nanos % 1000000L);
        Thread.sleep(millis, remainderNanos);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Shutdown path only.
        }
    }

    private static File resolveBridgePath() {
        return resolvePath("matrix3.sm64.bridge", "SM64_BRIDGE_EXE", defaultBridgePaths());
    }

    private static File resolveRomPath() {
        return resolvePath("matrix3.sm64.rom", "SM64_ROM", defaultRomPaths());
    }

    private static File resolvePath(String propertyName, String environmentName, String... fallbacks) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(environmentName);
        }
        if (value != null && !value.trim().isEmpty()) {
            return new File(value);
        }

        for (String fallback : fallbacks) {
            File candidate = new File(fallback);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return new File(fallbacks[0]);
    }

    private static String[] defaultBridgePaths() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String executable = osName.contains("win") ? "sm64_bridge.exe" : "sm64_bridge";
        return new String[] {
                "native/sm64-bridge/dist/" + executable,
                "../native/sm64-bridge/dist/" + executable
        };
    }

    private static String[] defaultRomPaths() {
        return new String[] {
                "native/sm64-bridge/baserom.us.z64",
                "../native/sm64-bridge/baserom.us.z64"
        };
    }

    private static final class NativeState {
        final float x;
        final float y;
        final float z;
        final float vx;
        final float vy;
        final float vz;
        final float faceAngle;
        final float forwardVelocity;
        final long action;
        final int animId;
        final int animFrame;
        final long flags;
        final long receivedNanos;

        NativeState(
                float x, float y, float z,
                float vx, float vy, float vz,
                float faceAngle, float forwardVelocity,
                long action, int animId, int animFrame, long flags,
                long receivedNanos) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.faceAngle = faceAngle;
            this.forwardVelocity = forwardVelocity;
            this.action = action;
            this.animId = animId;
            this.animFrame = animFrame;
            this.flags = flags;
            this.receivedNanos = receivedNanos;
        }

        static NativeState parse(String line, long receivedNanos) {
            if (line == null) {
                throw new IllegalStateException("sidecar closed stdout");
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length != 13 || !"STATE".equals(tokens[0])) {
                throw new IllegalStateException("unexpected state packet: " + line);
            }
            return new NativeState(
                    Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3]),
                    Float.parseFloat(tokens[4]), Float.parseFloat(tokens[5]), Float.parseFloat(tokens[6]),
                    Float.parseFloat(tokens[7]), Float.parseFloat(tokens[8]),
                    Long.parseLong(tokens[9]), Integer.parseInt(tokens[10]), Integer.parseInt(tokens[11]),
                    Long.parseLong(tokens[12]), receivedNanos);
        }
    }
}
