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
 * Persistent Java 8 <-> libsm64 sidecar session for Mario-mode simulation.
 *
 * The worker owns native process I/O, fixed-step SM64 simulation and immutable
 * native frame publication. Matrix scene/model mutation stays on the client or
 * renderer thread; callers only consume the published snapshots.
 */
public final class Sm64BridgeSession {

    private static final int MIN_BINARY_PROTOCOL_VERSION = 1;
    private static final int MAX_BINARY_PROTOCOL_VERSION = 3;
    private static final int BINARY_CMD_STEP = 1;
    private static final long STEP_NANOS = 1000000000L / 30L;
    private static final long MAX_SCHEDULE_DRIFT_NANOS = STEP_NANOS * 4L;
    private static final int MAX_TEXTURE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TRIANGLES = 1024;

    private static volatile Worker current;
    private static volatile GeometryFrame presentationFrozenFrame;

    private Sm64BridgeSession() {
    }

    public static synchronized void start() {
        Worker existing = current;
        if (existing != null && existing.isRunning()) {
            return;
        }

        presentationFrozenFrame = null;
        Worker worker = new Worker(resolveBridgePath(), resolveRomPath());
        current = worker;
        worker.start();
    }

    public static synchronized void stop() {
        Worker worker = current;
        current = null;
        presentationFrozenFrame = null;
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

    /**
     * Compatibility overload preserving the original fixed camera direction.
     * New alternate-character input should use the camera-aware overload below.
     */
    public static void setInput(float stickX, float stickY,
            boolean buttonA, boolean buttonB, boolean buttonZ) {
        setInput(0.0F, -1.0F, stickX, stickY, buttonA, buttonB, buttonZ);
    }

    /**
     * Publishes one normalized Matrix input snapshot for the fixed-rate native
     * simulation worker. Camera look and stick values are captured together so a
     * native step cannot combine input from different client frames.
     */
    public static void setInput(float cameraLookX, float cameraLookZ,
            float stickX, float stickY,
            boolean buttonA, boolean buttonB, boolean buttonZ) {
        Worker worker = current;
        if (worker != null) {
            worker.setInput(
                    cameraLookX, cameraLookZ,
                    stickX, stickY,
                    buttonA, buttonB, buttonZ);
        }
    }

    static void setCombatInput(float cameraLookX, float cameraLookZ,
            float stickX, float stickY, boolean a, boolean b, boolean z,
            int combatMode, int combatRequest) {
        Worker worker = current;
        if (worker != null) {
            float[] camera = normalizeCamera(cameraLookX, cameraLookZ);
            worker.inputState = new InputState(camera[0], camera[1],
                    clampStick(stickX), clampStick(stickY), a, b, z,
                    combatMode, combatRequest);
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

    static int getBinaryProtocolVersion() {
        Worker worker = current;
        return worker == null ? 0 : worker.getProtocolVersion();
    }

    /**
     * Freezes/unfreezes Matrix's visible SM64 snapshot without pausing the native
     * worker. Helmet calibration uses this so Mario's body/head stop animating
     * while the sidecar remains healthy and keeps its fixed 30 Hz ownership.
     */
    static void setPresentationFrozen(boolean frozen) {
        if (!frozen) {
            presentationFrozenFrame = null;
            return;
        }
        Worker worker = current;
        GeometryFrame frame = worker == null ? null : worker.getLatestFrame();
        if (frame != null) {
            presentationFrozenFrame = frame;
        }
    }

    static boolean isPresentationFrozen() {
        return presentationFrozenFrame != null;
    }

    static NativePosition getLatestPosition() {
        GeometryFrame frozen = presentationFrozenFrame;
        if (frozen != null && frozen.state != null) {
            return new NativePosition(frozen.state.x, frozen.state.y, frozen.state.z);
        }
        Worker worker = current;
        NativeState state = worker == null ? null : worker.getLatestState();
        return state == null ? null : new NativePosition(state.x, state.y, state.z);
    }

    public static Float getLatestY() {
        NativePosition position = getLatestPosition();
        return position == null ? null : Float.valueOf(position.y);
    }

    static GeometryFrame getLatestGeometryFrame() {
        GeometryFrame frozen = presentationFrozenFrame;
        if (frozen != null) {
            return refreshPresentationTimestamp(frozen);
        }
        Worker worker = current;
        return worker == null ? null : worker.getLatestFrame();
    }

    static TextureAtlas getTextureAtlas() {
        Worker worker = current;
        return worker == null ? null : worker.getTextureAtlas();
    }

    /**
     * One-native-tick-delayed interpolation keeps the fixed 30 Hz simulation
     * smooth at higher Matrix update rates without making physics frame dependent.
     * XYZ is sampled with one shared alpha so horizontal and vertical presentation
     * cannot drift onto different native interpolation phases.
     */
    static NativePosition getInterpolatedPosition() {
        GeometryFrame frozen = presentationFrozenFrame;
        if (frozen != null && frozen.state != null) {
            return new NativePosition(frozen.state.x, frozen.state.y, frozen.state.z);
        }

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
            return new NativePosition(latest.x, latest.y, latest.z);
        }

        float alpha = interpolationAlpha(latest);
        return new NativePosition(
                previous.x + (latest.x - previous.x) * alpha,
                previous.y + (latest.y - previous.y) * alpha,
                previous.z + (latest.z - previous.z) * alpha);
    }

    public static Float getInterpolatedY() {
        NativePosition position = getInterpolatedPosition();
        return position == null ? null : Float.valueOf(position.y);
    }

    private static float interpolationAlpha(NativeState latest) {
        float alpha = (float) (System.nanoTime() - latest.receivedNanos) / (float) STEP_NANOS;
        if (alpha < 0.0F) {
            return 0.0F;
        }
        if (alpha > 1.0F) {
            return 1.0F;
        }
        return alpha;
    }

    /**
     * MarioVisualRenderer rejects stale frames after a bounded age. A calibration
     * freeze intentionally reuses one geometry snapshot, so only its immutable
     * NativeState timestamp is refreshed for presentation freshness; geometry,
     * semantic metadata, sequence and native worker publication remain untouched.
     */
    private static GeometryFrame refreshPresentationTimestamp(GeometryFrame frame) {
        NativeState state = frame.state;
        if (state == null) {
            return frame;
        }
        NativeState freshState = new NativeState(
                state.x, state.y, state.z,
                state.vx, state.vy, state.vz,
                state.faceAngle, state.forwardVelocity,
                state.action, state.animId, state.animFrame,
                state.flags, state.particleFlags,
                System.nanoTime());
        return new GeometryFrame(
                frame.protocolVersion,
                frame.sequence,
                freshState,
                frame.triangleCount,
                frame.positions,
                frame.colors,
                frame.uvs,
                frame.localPositions,
                frame.partIds, frame.rightHand, frame.combatAnimation,
                frame.combatTime, frame.combatWeight);
    }

    private static final class Worker implements Runnable {
        private final File bridge;
        private final File rom;

        private volatile boolean running = true;
        private volatile boolean ready;
        private volatile boolean failed;
        private volatile String failureReason;
        private volatile InputState inputState = InputState.IDLE;
        private volatile GeometryFrame previousFrame;
        private volatile GeometryFrame latestFrame;
        private volatile TextureAtlas textureAtlas;
        private volatile int protocolVersion;

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

        int getProtocolVersion() {
            return protocolVersion;
        }

        void setButtonA(boolean down) {
            InputState input = inputState;
            inputState = new InputState(
                    input.cameraLookX, input.cameraLookZ,
                    input.stickX, input.stickY,
                    down, input.buttonB, input.buttonZ, input.combatMode, input.combatRequest);
        }

        void setInput(float cameraLookX, float cameraLookZ,
                float stickX, float stickY,
                boolean buttonA, boolean buttonB, boolean buttonZ) {
            float[] camera = normalizeCamera(cameraLookX, cameraLookZ);
            inputState = new InputState(
                    camera[0], camera[1],
                    clampStick(stickX), clampStick(stickY),
                    buttonA, buttonB, buttonZ);
        }

        NativeState getPreviousState() {
            GeometryFrame frame = previousFrame;
            return frame == null ? null : frame.state;
        }

        NativeState getLatestState() {
            GeometryFrame frame = latestFrame;
            return frame == null ? null : frame.state;
        }

        GeometryFrame getLatestFrame() {
            return latestFrame;
        }

        TextureAtlas getTextureAtlas() {
            return textureAtlas;
        }

        @Override
        public void run() {
            Process localProcess = null;
            BufferedInputStream input = null;
            BufferedOutputStream output = null;
            try {
                if (!bridge.isFile()) {
                    throw new IllegalStateException("sidecar not found: " + bridge.getAbsolutePath());
                }
                if (!rom.isFile()) {
                    throw new IllegalStateException("ROM not found: " + rom.getAbsolutePath());
                }

                ProcessBuilder builder = new ProcessBuilder(
                        bridge.getAbsolutePath(), "--binary", rom.getAbsolutePath());
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
                localProcess = builder.start();
                process = localProcess;

                input = new BufferedInputStream(localProcess.getInputStream(), 256 * 1024);
                output = new BufferedOutputStream(localProcess.getOutputStream(), 16 * 1024);

                Handshake handshake = readHandshake(input);
                protocolVersion = handshake.protocolVersion;
                textureAtlas = handshake.textureAtlas;

                // Stabilize the freshly reset native Mario before accepting input.
                publish(step(output, input, InputState.IDLE, protocolVersion));
                publish(step(output, input, InputState.IDLE, protocolVersion));
                ready = true;
                if (protocolVersion >= 3) {
                    System.out.println("[SM64 Bridge] Persistent session READY (30 Hz + combat socket v3)");
                } else if (protocolVersion >= 2) {
                    System.out.println("[SM64 Bridge] Persistent session READY (30 Hz + semantic geometry v2)");
                } else {
                    System.out.println("[SM64 Bridge] Persistent session READY (30 Hz + geometry, protocol v1)");
                    System.out.println("[SM64 Bridge] Semantic equipment metadata unavailable; rebuild native/sm64-bridge with make bootstrap.");
                }

                long nextStep = System.nanoTime();
                while (running) {
                    InputState inputSnapshot = inputState;
                    publish(step(output, input, inputSnapshot, protocolVersion));

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

        private void publish(GeometryFrame frame) {
            previousFrame = latestFrame;
            latestFrame = frame;
        }
    }

    private static Handshake readHandshake(InputStream input) throws IOException {
        expectMagic(input, 'M', '6', '4', 'B');
        int version = readIntLE(input);
        if (version < MIN_BINARY_PROTOCOL_VERSION || version > MAX_BINARY_PROTOCOL_VERSION) {
            throw new IllegalStateException("unsupported native binary protocol version: " + version
                    + " (supported " + MIN_BINARY_PROTOCOL_VERSION + "-" + MAX_BINARY_PROTOCOL_VERSION + ")");
        }

        int width = readIntLE(input);
        int height = readIntLE(input);
        int length = readIntLE(input);
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                || length <= 0 || length > MAX_TEXTURE_BYTES
                || length != width * height * 4) {
            throw new IllegalStateException(
                    "invalid native texture header: " + width + "x" + height + " bytes=" + length);
        }

        byte[] rgba = new byte[length];
        readFully(input, rgba, 0, rgba.length);
        return new Handshake(version, new TextureAtlas(width, height, rgba));
    }

    private static GeometryFrame step(
            OutputStream output, InputStream input, InputState controls,
            int protocolVersion) throws IOException {
        output.write(BINARY_CMD_STEP);
        writeFloatLE(output, controls.cameraLookX);
        writeFloatLE(output, controls.cameraLookZ);
        writeFloatLE(output, controls.stickX);
        writeFloatLE(output, controls.stickY);
        output.write(controls.buttonA ? 1 : 0);
        output.write(controls.buttonB ? 1 : 0);
        output.write(controls.buttonZ ? 1 : 0);
        if (protocolVersion >= 3) {
            output.write(controls.combatMode);
            writeIntLE(output, controls.combatRequest);
        }
        output.flush();

        expectMagic(input, 'M', '6', '4', 'F');
        long sequence = readIntLE(input) & 0xffffffffL;
        long receivedNanos = System.nanoTime();
        NativeState state = new NativeState(
                readFloatLE(input), readFloatLE(input), readFloatLE(input),
                readFloatLE(input), readFloatLE(input), readFloatLE(input),
                readFloatLE(input), readFloatLE(input),
                readIntLE(input) & 0xffffffffL,
                readIntLE(input),
                readShortLE(input),
                readIntLE(input) & 0xffffffffL,
                readIntLE(input) & 0xffffffffL,
                receivedNanos);

        int triangleCount = readUnsignedShortLE(input);
        if (triangleCount < 0 || triangleCount > MAX_TRIANGLES) {
            throw new IllegalStateException("invalid native triangle count: " + triangleCount);
        }

        float[] positions = readFloatArray(input, triangleCount * 9);
        float[] colors = readFloatArray(input, triangleCount * 9);
        float[] uvs = readFloatArray(input, triangleCount * 6);
        float[] localPositions = null;
        byte[] partIds = null;
        if (protocolVersion >= 2) {
            localPositions = readFloatArray(input, triangleCount * 9);
            partIds = readByteArray(input, triangleCount);
        }
        float[] rightHand = null;
        int combatAnimation = 0;
        float combatTime = 0, combatWeight = 0;
        if (protocolVersion >= 3) {
            int available = readIntLE(input);
            float[] matrix = readFloatArray(input, 16);
            combatAnimation = readIntLE(input);
            combatTime = readFloatLE(input);
            combatWeight = readFloatLE(input);
            if (available < 0 || available > 1 || combatAnimation < 0 || combatAnimation > 1
                    || !unitFloat(combatTime) || !unitFloat(combatWeight))
                throw new IOException("invalid combat socket frame");
            for (float value : matrix) {
                if (Float.isNaN(value) || Float.isInfinite(value))
                    throw new IOException("non-finite hand socket");
            }
            if (available == 1) rightHand = matrix;
        }
        return new GeometryFrame(
                protocolVersion, sequence, state, triangleCount,
                positions, colors, uvs, localPositions, partIds,
                rightHand, combatAnimation, combatTime, combatWeight);
    }

    private static boolean unitFloat(float value) {
        return value >= 0.0F && value <= 1.0F;
    }

    private static float[] normalizeCamera(float x, float z) {
        if (Float.isNaN(x) || Float.isNaN(z)
                || Float.isInfinite(x) || Float.isInfinite(z)) {
            return new float[] { 0.0F, -1.0F };
        }
        float length = (float) Math.sqrt(x * x + z * z);
        if (length < 0.001F || Float.isNaN(length) || Float.isInfinite(length)) {
            return new float[] { 0.0F, -1.0F };
        }
        return new float[] { x / length, z / length };
    }

    private static float clampStick(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0F;
        }
        if (value < -1.0F) {
            return -1.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static float[] readFloatArray(InputStream input, int count) throws IOException {
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = readFloatLE(input);
        }
        return values;
    }

    private static byte[] readByteArray(InputStream input, int count) throws IOException {
        byte[] values = new byte[count];
        readFully(input, values, 0, count);
        return values;
    }

    private static void expectMagic(InputStream input, char a, char b, char c, char d)
            throws IOException {
        int av = input.read();
        int bv = input.read();
        int cv = input.read();
        int dv = input.read();
        if (av != a || bv != b || cv != c || dv != d) {
            throw new IllegalStateException("unexpected native binary packet magic");
        }
    }

    private static void writeFloatLE(OutputStream output, float value) throws IOException {
        writeIntLE(output, Float.floatToRawIntBits(value));
    }

    private static void writeIntLE(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static float readFloatLE(InputStream input) throws IOException {
        return Float.intBitsToFloat(readIntLE(input));
    }

    private static int readIntLE(InputStream input) throws IOException {
        int b0 = readRequired(input);
        int b1 = readRequired(input);
        int b2 = readRequired(input);
        int b3 = readRequired(input);
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readUnsignedShortLE(InputStream input) throws IOException {
        int b0 = readRequired(input);
        int b1 = readRequired(input);
        return b0 | (b1 << 8);
    }

    private static short readShortLE(InputStream input) throws IOException {
        return (short) readUnsignedShortLE(input);
    }

    private static int readRequired(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("native sidecar closed binary stream");
        }
        return value;
    }

    private static void readFully(InputStream input, byte[] data, int offset, int length)
            throws IOException {
        int remaining = length;
        int cursor = offset;
        while (remaining > 0) {
            int read = input.read(data, cursor, remaining);
            if (read < 0) {
                throw new IOException("native sidecar closed binary stream");
            }
            cursor += read;
            remaining -= read;
        }
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

    private static final class Handshake {
        final int protocolVersion;
        final TextureAtlas textureAtlas;

        Handshake(int protocolVersion, TextureAtlas textureAtlas) {
            this.protocolVersion = protocolVersion;
            this.textureAtlas = textureAtlas;
        }
    }

    private static final class InputState {
        static final InputState IDLE = new InputState(
                0.0F, -1.0F,
                0.0F, 0.0F,
                false, false, false);

        final float cameraLookX;
        final float cameraLookZ;
        final float stickX;
        final float stickY;
        final boolean buttonA;
        final boolean buttonB;
        final boolean buttonZ;
        final int combatMode;
        final int combatRequest;

        InputState(float cameraLookX, float cameraLookZ,
                float stickX, float stickY,
                boolean buttonA, boolean buttonB, boolean buttonZ) {
            this(cameraLookX, cameraLookZ, stickX, stickY, buttonA, buttonB, buttonZ, 0, 0);
        }

        InputState(float cameraLookX, float cameraLookZ, float stickX, float stickY,
                boolean buttonA, boolean buttonB, boolean buttonZ, int combatMode, int combatRequest) {
            this.combatMode = combatMode;
            this.combatRequest = combatRequest;
            this.cameraLookX = cameraLookX;
            this.cameraLookZ = cameraLookZ;
            this.stickX = stickX;
            this.stickY = stickY;
            this.buttonA = buttonA;
            this.buttonB = buttonB;
            this.buttonZ = buttonZ;
        }
    }

    static final class NativePosition {
        final float x;
        final float y;
        final float z;

        NativePosition(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final class TextureAtlas {
        final int width;
        final int height;
        final byte[] rgba;

        TextureAtlas(int width, int height, byte[] rgba) {
            this.width = width;
            this.height = height;
            this.rgba = rgba;
        }
    }

    static final class GeometryFrame {
        final int protocolVersion;
        final long sequence;
        final NativeState state;
        final int triangleCount;
        final float[] positions;
        final float[] colors;
        final float[] uvs;
        final float[] localPositions;
        final byte[] partIds;
        final float[] rightHand;
        final int combatAnimation;
        final float combatTime;
        final float combatWeight;

        GeometryFrame(int protocolVersion, long sequence, NativeState state, int triangleCount,
                float[] positions, float[] colors, float[] uvs,
                float[] localPositions, byte[] partIds) {
            this(protocolVersion, sequence, state, triangleCount, positions, colors, uvs,
                    localPositions, partIds, null, 0, 0, 0);
        }

        GeometryFrame(int protocolVersion, long sequence, NativeState state, int triangleCount,
                float[] positions, float[] colors, float[] uvs, float[] localPositions, byte[] partIds,
                float[] rightHand, int combatAnimation, float combatTime, float combatWeight) {
            this.rightHand = rightHand;
            this.combatAnimation = combatAnimation;
            this.combatTime = combatTime;
            this.combatWeight = combatWeight;
            this.protocolVersion = protocolVersion;
            this.sequence = sequence;
            this.state = state;
            this.triangleCount = triangleCount;
            this.positions = positions;
            this.colors = colors;
            this.uvs = uvs;
            this.localPositions = localPositions;
            this.partIds = partIds;
        }

        boolean hasSemanticGeometry() {
            return protocolVersion >= 2
                    && localPositions != null
                    && localPositions.length >= triangleCount * 9
                    && partIds != null
                    && partIds.length >= triangleCount;
        }
    }

    static final class NativeState {
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
        final long particleFlags;
        final long receivedNanos;

        NativeState(
                float x, float y, float z,
                float vx, float vy, float vz,
                float faceAngle, float forwardVelocity,
                long action, int animId, int animFrame,
                long flags, long particleFlags, long receivedNanos) {
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
            this.particleFlags = particleFlags;
            this.receivedNanos = receivedNanos;
        }
    }
}

