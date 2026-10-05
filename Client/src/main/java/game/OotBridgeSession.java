package game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Persistent Java 8 <-> liboot sidecar session for Link-mode simulation.
 *
 * The worker owns native process I/O and the fixed 20 Hz OoT step. Matrix scene
 * and renderer mutation remain on the client/render thread; callers consume only
 * immutable LinkFrame snapshots published by the worker.
 */
public final class OotBridgeSession {

    static final int BUTTON_A = 1 << 0;
    static final int BUTTON_B = 1 << 1;
    static final int BUTTON_Z = 1 << 2;
    static final int BUTTON_R = 1 << 3;

    private static final int BINARY_PROTOCOL_VERSION = 1;
    private static final int BINARY_CMD_STEP = 1;
    private static final int MAX_TRIANGLES = 4096;
    private static final long STEP_NANOS = 1000000000L / 20L;
    private static final long MAX_SCHEDULE_DRIFT_NANOS = STEP_NANOS * 4L;
    private static final String ROM_FILE_NAME =
            "Legend of Zelda, The - Ocarina of Time (U) (V1.2) [!].z64";

    private static volatile Worker current;

    private OotBridgeSession() {
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

    static void setInput(float cameraLookX, float cameraLookZ,
            float stickX, float stickY,
            boolean buttonA, boolean buttonB, boolean buttonZ) {
        Worker worker = current;
        if (worker == null) {
            return;
        }
        float[] camera = normalize(cameraLookX, cameraLookZ);
        int buttons = 0;
        if (buttonA) buttons |= BUTTON_A;
        if (buttonB) buttons |= BUTTON_B;
        if (buttonZ) buttons |= BUTTON_Z;
        worker.setInput(new InputState(
                camera[0], camera[1],
                clampStick(stickX), clampStick(stickY), buttons));
    }

    static boolean isReady() {
        Worker worker = current;
        return worker != null && worker.isReady();
    }

    static boolean hasFailed() {
        Worker worker = current;
        return worker != null && worker.hasFailed();
    }

    static String getFailureReason() {
        Worker worker = current;
        return worker == null ? null : worker.getFailureReason();
    }

    static LinkFrame getLatestFrame() {
        Worker worker = current;
        return worker == null ? null : worker.getLatestFrame();
    }

    private static final class Worker implements Runnable {
        private final File bridge;
        private final File rom;

        private volatile boolean running = true;
        private volatile boolean ready;
        private volatile boolean failed;
        private volatile String failureReason;
        private volatile InputState inputState = InputState.IDLE;
        private volatile LinkFrame latestFrame;
        private volatile Process process;
        private Thread thread;

        Worker(File bridge, File rom) {
            this.bridge = bridge;
            this.rom = rom;
        }

        void start() {
            thread = new Thread(this, "oot-bridge-session");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            inputState = InputState.IDLE;
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

        LinkFrame getLatestFrame() {
            return latestFrame;
        }

        void setInput(InputState input) {
            inputState = input;
        }

        @Override
        public void run() {
            Process localProcess = null;
            BufferedInputStream input = null;
            BufferedOutputStream output = null;
            try {
                if (!bridge.isFile()) {
                    throw new IllegalStateException(
                            "OoT sidecar not found: " + bridge.getAbsolutePath());
                }
                if (!rom.isFile()) {
                    throw new IllegalStateException(
                            "OoT NTSC-U 1.2 ROM not found: " + rom.getAbsolutePath());
                }

                ProcessBuilder builder = new ProcessBuilder(
                        bridge.getAbsolutePath(), "--binary", rom.getAbsolutePath());
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
                localProcess = builder.start();
                process = localProcess;
                input = new BufferedInputStream(localProcess.getInputStream(), 256 * 1024);
                output = new BufferedOutputStream(localProcess.getOutputStream(), 16 * 1024);

                readHandshake(input);
                latestFrame = step(output, input, InputState.IDLE);
                latestFrame = step(output, input, InputState.IDLE);
                ready = true;
                System.out.println(
                        "[OoT Bridge] Persistent session READY (20 Hz + Link geometry, protocol v1)");

                long nextStep = System.nanoTime();
                while (running) {
                    latestFrame = step(output, input, inputState);
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
                    System.out.println(
                            "[OoT Bridge] Persistent session failed: " + failureReason);
                }
            } finally {
                ready = false;
                running = false;
                inputState = InputState.IDLE;
                process = null;
                closeQuietly(output);
                closeQuietly(input);
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
    }

    private static void readHandshake(InputStream input) throws IOException {
        expectMagic(input, 'O', 'O', 'T', 'B');
        int version = readIntLE(input);
        if (version != BINARY_PROTOCOL_VERSION) {
            throw new IllegalStateException(
                    "unsupported OoT bridge protocol version: " + version);
        }
    }

    private static LinkFrame step(OutputStream output, InputStream input,
            InputState controls) throws IOException {
        output.write(BINARY_CMD_STEP);
        writeFloatLE(output, controls.cameraLookX);
        writeFloatLE(output, controls.cameraLookZ);
        writeFloatLE(output, controls.stickX);
        writeFloatLE(output, controls.stickY);
        writeIntLE(output, controls.buttons);
        output.flush();

        expectMagic(input, 'O', 'O', 'T', 'F');
        long sequence = readIntLE(input) & 0xffffffffL;
        long simulationTick = readLongLE(input);
        float x = readFloatLE(input);
        float y = readFloatLE(input);
        float z = readFloatLE(input);
        int faceAngle = readIntLE(input);
        int action = readIntLE(input);
        int animId = readIntLE(input);
        float animFrame = readFloatLE(input);
        boolean skeletonAvailable = readUnsignedByte(input) != 0;
        boolean geometryTruncated = readUnsignedByte(input) != 0;
        readUnsignedByte(input);
        readUnsignedByte(input);
        int triangleCount = readIntLE(input);
        if (triangleCount <= 0 || triangleCount > MAX_TRIANGLES) {
            throw new IllegalStateException(
                    "invalid OoT triangle count: " + triangleCount);
        }
        if (geometryTruncated) {
            throw new IllegalStateException("OoT Link geometry truncated");
        }
        int floatCount = triangleCount * 9;
        float[] positions = readFloatArray(input, floatCount);
        float[] colors = readFloatArray(input, floatCount);
        return new LinkFrame(
                sequence, simulationTick,
                x, y, z,
                faceAngle, action, animId, animFrame,
                skeletonAvailable, triangleCount,
                positions, colors, System.nanoTime());
    }

    private static File resolveBridgePath() {
        String binary = isWindows() ? "oot_bridge.exe" : "oot_bridge";
        return resolvePath(
                "matrix3.oot.bridge", "OOT_BRIDGE_EXE",
                new String[] {
                        "native/oot-bridge/build/dist/" + binary,
                        "../native/oot-bridge/build/dist/" + binary,
                        "build/dist/" + binary
                });
    }

    private static File resolveRomPath() {
        return resolvePath(
                "matrix3.oot.rom", "OOT_ROM",
                new String[] {
                        "native/oot-bridge/" + ROM_FILE_NAME,
                        "../native/oot-bridge/" + ROM_FILE_NAME,
                        ROM_FILE_NAME
                });
    }

    private static File resolvePath(String propertyName, String environmentName,
            String[] defaults) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv(environmentName);
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured.trim()).getAbsoluteFile();
        }
        for (int i = 0; i < defaults.length; i++) {
            File candidate = new File(defaults[i]).getAbsoluteFile();
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return new File(defaults[0]).getAbsoluteFile();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        return os.contains("win");
    }

    private static float clampStick(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0F;
        }
        return value < -1.0F ? -1.0F : value > 1.0F ? 1.0F : value;
    }

    private static float[] normalize(float x, float z) {
        float length = (float) Math.sqrt(x * x + z * z);
        if (Float.isNaN(length) || Float.isInfinite(length) || length < 0.001F) {
            return new float[] { 0.0F, 1.0F };
        }
        return new float[] { x / length, z / length };
    }

    private static void expectMagic(InputStream input,
            int a, int b, int c, int d) throws IOException {
        if (readUnsignedByte(input) != a
                || readUnsignedByte(input) != b
                || readUnsignedByte(input) != c
                || readUnsignedByte(input) != d) {
            throw new IllegalStateException("invalid OoT sidecar protocol magic");
        }
    }

    private static int readUnsignedByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("unexpected EOF from OoT sidecar");
        }
        return value;
    }

    private static int readIntLE(InputStream input) throws IOException {
        int b0 = readUnsignedByte(input);
        int b1 = readUnsignedByte(input);
        int b2 = readUnsignedByte(input);
        int b3 = readUnsignedByte(input);
        return b0 | b1 << 8 | b2 << 16 | b3 << 24;
    }

    private static long readLongLE(InputStream input) throws IOException {
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value |= (long) readUnsignedByte(input) << (i * 8);
        }
        return value;
    }

    private static float readFloatLE(InputStream input) throws IOException {
        return Float.intBitsToFloat(readIntLE(input));
    }

    private static float[] readFloatArray(InputStream input, int length) throws IOException {
        float[] values = new float[length];
        for (int i = 0; i < length; i++) {
            values[i] = readFloatLE(input);
        }
        return values;
    }

    private static void writeIntLE(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write(value >>> 8 & 0xff);
        output.write(value >>> 16 & 0xff);
        output.write(value >>> 24 & 0xff);
    }

    private static void writeFloatLE(OutputStream output, float value) throws IOException {
        writeIntLE(output, Float.floatToRawIntBits(value));
    }

    private static void sleepNanos(long nanos) throws InterruptedException {
        long millis = nanos / 1000000L;
        int extraNanos = (int) (nanos % 1000000L);
        Thread.sleep(millis, extraNanos);
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(OutputStream output) {
        if (output == null) return;
        try {
            output.close();
        } catch (IOException ignored) {
        }
    }

    private static final class InputState {
        static final InputState IDLE = new InputState(0.0F, 1.0F, 0.0F, 0.0F, 0);

        final float cameraLookX;
        final float cameraLookZ;
        final float stickX;
        final float stickY;
        final int buttons;

        InputState(float cameraLookX, float cameraLookZ,
                float stickX, float stickY, int buttons) {
            this.cameraLookX = cameraLookX;
            this.cameraLookZ = cameraLookZ;
            this.stickX = stickX;
            this.stickY = stickY;
            this.buttons = buttons;
        }
    }

    static final class LinkFrame {
        final long sequence;
        final long simulationTick;
        final float x;
        final float y;
        final float z;
        final int faceAngle;
        final int action;
        final int animId;
        final float animFrame;
        final boolean skeletonAvailable;
        final int triangleCount;
        final float[] positions;
        final float[] colors;
        final long receivedNanos;

        LinkFrame(long sequence, long simulationTick,
                float x, float y, float z,
                int faceAngle, int action, int animId, float animFrame,
                boolean skeletonAvailable, int triangleCount,
                float[] positions, float[] colors, long receivedNanos) {
            this.sequence = sequence;
            this.simulationTick = simulationTick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.faceAngle = faceAngle;
            this.action = action;
            this.animId = animId;
            this.animFrame = animFrame;
            this.skeletonAvailable = skeletonAvailable;
            this.triangleCount = triangleCount;
            this.positions = positions;
            this.colors = colors;
            this.receivedNanos = receivedNanos;
        }
    }
}
