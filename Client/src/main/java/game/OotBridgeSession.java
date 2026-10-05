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

    private static final int BINARY_PROTOCOL_VERSION = 3;
    private static final int BINARY_CMD_STEP = 1;
    private static final int MAX_TRIANGLES = 4096;
    private static final int MAX_TEXTURES = 1024;
    private static final int MAX_SKELETON_JOINTS = 21;
    private static final int MAX_TEXTURE_DIMENSION = 1024;
    private static final int MAX_TEXTURE_BYTES = 16 * 1024 * 1024;
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

    /** One native tick delayed; position remains continuous between 20 Hz frames. */
    static NativePosition getInterpolatedPosition() {
        Worker worker = current;
        PositionFrames frames = worker == null ? null : worker.positionFrames;
        return frames == null ? null : frames.sample(System.nanoTime());
    }

    static final class NativePosition {
        final float x, z;
        NativePosition(float x, float z) { this.x = x; this.z = z; }
    }

    private static final class PositionFrames {
        final float oldX, oldZ, x, z;
        final long receivedNanos;
        PositionFrames(LinkFrame previous, LinkFrame latest) {
            oldX = previous == null ? latest.x : previous.x;
            oldZ = previous == null ? latest.z : previous.z;
            x = latest.x;
            z = latest.z;
            receivedNanos = latest.receivedNanos;
        }
        NativePosition sample(long now) {
            float alpha = Math.max(0.0F, Math.min(1.0F,
                    (float) (now - receivedNanos) / STEP_NANOS));
            return new NativePosition(oldX + (x - oldX) * alpha,
                    oldZ + (z - oldZ) * alpha);
        }
    }

    private static final class Worker implements Runnable {
        private final File bridge;
        private final File rom;
        private final TextureUpdate[] textureCatalog = new TextureUpdate[MAX_TEXTURES];

        private volatile boolean running = true;
        private volatile boolean ready;
        private volatile boolean failed;
        private volatile String failureReason;
        private volatile InputState inputState = InputState.IDLE;
        private volatile LinkFrame latestFrame;
        private volatile PositionFrames positionFrames;
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

        private void publishFrame(LinkFrame frame) {
            positionFrames = new PositionFrames(latestFrame, frame);
            latestFrame = frame;
        }

        void setInput(InputState input) {
            inputState = input;
        }

        private LinkFrame retainTextureCatalog(LinkFrame frame) {
            for (int i = 0; i < frame.textureUpdates.length; i++) {
                TextureUpdate update = frame.textureUpdates[i];
                textureCatalog[update.index] = update;
            }
            int count = 0;
            for (int i = 0; i < textureCatalog.length; i++) {
                if (textureCatalog[i] != null) {
                    count++;
                }
            }
            TextureUpdate[] catalog = new TextureUpdate[count];
            int out = 0;
            for (int i = 0; i < textureCatalog.length; i++) {
                if (textureCatalog[i] != null) {
                    catalog[out++] = textureCatalog[i];
                }
            }
            return frame.withTextures(catalog);
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
                input = new BufferedInputStream(localProcess.getInputStream(), 512 * 1024);
                output = new BufferedOutputStream(localProcess.getOutputStream(), 16 * 1024);

                readHandshake(input);
                publishFrame(retainTextureCatalog(step(output, input, InputState.IDLE)));
                publishFrame(retainTextureCatalog(step(output, input, InputState.IDLE)));
                ready = true;
                System.out.println(
                        "[OoT Bridge] Persistent session READY (20 Hz + Link materials + skeleton, protocol v3)");

                long nextStep = System.nanoTime();
                while (running) {
                    publishFrame(retainTextureCatalog(step(output, input, inputState)));
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
                    "unsupported OoT bridge protocol version: " + version
                    + " (rebuild native/oot-bridge for protocol v3)");
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

        int skeletonJointCount = readIntLE(input);
        if (skeletonJointCount < 0 || skeletonJointCount > MAX_SKELETON_JOINTS) {
            throw new IllegalStateException(
                    "invalid OoT skeleton joint count: " + skeletonJointCount);
        }
        if (skeletonAvailable && skeletonJointCount <= 0) {
            throw new IllegalStateException("OoT skeleton marked available with no joints");
        }
        int[] skeletonParents = new int[skeletonJointCount];
        for (int i = 0; i < skeletonJointCount; i++) {
            int parent = readIntLE(input);
            if (parent != 0xff && (parent < 0 || parent >= skeletonJointCount)) {
                throw new IllegalStateException(
                        "invalid OoT skeleton parent at joint " + i + ": " + parent);
            }
            skeletonParents[i] = parent;
        }
        float[] skeletonJointPositions = readFloatArray(input, skeletonJointCount * 3);

        int triangleCount = readIntLE(input);
        if (triangleCount <= 0 || triangleCount > MAX_TRIANGLES) {
            throw new IllegalStateException(
                    "invalid OoT triangle count: " + triangleCount);
        }
        if (geometryTruncated) {
            throw new IllegalStateException("OoT Link geometry truncated");
        }

        int xyzFloatCount = triangleCount * 9;
        int uvFloatCount = triangleCount * 6;
        float[] positions = readFloatArray(input, xyzFloatCount);
        float[] normals = readFloatArray(input, xyzFloatCount);
        float[] colors = readFloatArray(input, xyzFloatCount);
        float[] uvs = readFloatArray(input, uvFloatCount);
        int[] triangleTextures = new int[triangleCount];
        for (int i = 0; i < triangleCount; i++) {
            int texture = readIntLE(input);
            if (texture != 0xffff && (texture < 0 || texture >= MAX_TEXTURES)) {
                throw new IllegalStateException("invalid OoT texture index: " + texture);
            }
            triangleTextures[i] = texture;
        }

        int updateCount = readIntLE(input);
        if (updateCount < 0 || updateCount > MAX_TEXTURES) {
            throw new IllegalStateException("invalid OoT texture update count: " + updateCount);
        }
        TextureUpdate[] updates = new TextureUpdate[updateCount];
        for (int i = 0; i < updateCount; i++) {
            int index = readIntLE(input);
            int width = readIntLE(input);
            int height = readIntLE(input);
            int wrapS = readIntLE(input);
            int wrapT = readIntLE(input);
            int revision = readIntLE(input);
            int rgbaSize = readIntLE(input);
            if (index < 0 || index >= MAX_TEXTURES) {
                throw new IllegalStateException("invalid OoT texture update index: " + index);
            }
            if (width <= 0 || height <= 0
                    || width > MAX_TEXTURE_DIMENSION || height > MAX_TEXTURE_DIMENSION) {
                throw new IllegalStateException(
                        "invalid OoT texture dimensions: " + width + "x" + height);
            }
            long expected = (long) width * (long) height * 4L;
            if (rgbaSize <= 0 || rgbaSize > MAX_TEXTURE_BYTES || expected != rgbaSize) {
                throw new IllegalStateException(
                        "invalid OoT texture payload size: " + rgbaSize
                        + " expected=" + expected);
            }
            if (wrapS < 0 || wrapS > 2 || wrapT < 0 || wrapT > 2) {
                throw new IllegalStateException(
                        "invalid OoT texture wrap mode: " + wrapS + "/" + wrapT);
            }
            updates[i] = new TextureUpdate(
                    index, width, height, wrapS, wrapT, revision,
                    readByteArray(input, rgbaSize));
        }

        return new LinkFrame(
                sequence, simulationTick,
                x, y, z,
                faceAngle, action, animId, animFrame,
                skeletonAvailable,
                skeletonJointCount, skeletonParents, skeletonJointPositions,
                triangleCount,
                positions, normals, colors, uvs, triangleTextures,
                updates, System.nanoTime());
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

    private static byte[] readByteArray(InputStream input, int length) throws IOException {
        byte[] values = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(values, offset, length - offset);
            if (read < 0) {
                throw new IOException("unexpected EOF from OoT texture payload");
            }
            offset += read;
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

    static final class TextureUpdate {
        final int index;
        final int width;
        final int height;
        final int wrapS;
        final int wrapT;
        final int revision;
        final byte[] rgba;

        TextureUpdate(int index, int width, int height,
                int wrapS, int wrapT, int revision, byte[] rgba) {
            this.index = index;
            this.width = width;
            this.height = height;
            this.wrapS = wrapS;
            this.wrapT = wrapT;
            this.revision = revision;
            this.rgba = rgba;
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
        final int skeletonJointCount;
        final int[] skeletonParents;
        final float[] skeletonJointPositions;
        final int triangleCount;
        final float[] positions;
        final float[] normals;
        final float[] colors;
        final float[] uvs;
        final int[] triangleTextures;
        final TextureUpdate[] textureUpdates;
        final long receivedNanos;

        LinkFrame(long sequence, long simulationTick,
                float x, float y, float z,
                int faceAngle, int action, int animId, float animFrame,
                boolean skeletonAvailable,
                int skeletonJointCount, int[] skeletonParents,
                float[] skeletonJointPositions,
                int triangleCount,
                float[] positions, float[] normals, float[] colors,
                float[] uvs, int[] triangleTextures,
                TextureUpdate[] textureUpdates, long receivedNanos) {
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
            this.skeletonJointCount = skeletonJointCount;
            this.skeletonParents = skeletonParents;
            this.skeletonJointPositions = skeletonJointPositions;
            this.triangleCount = triangleCount;
            this.positions = positions;
            this.normals = normals;
            this.colors = colors;
            this.uvs = uvs;
            this.triangleTextures = triangleTextures;
            this.textureUpdates = textureUpdates;
            this.receivedNanos = receivedNanos;
        }

        boolean hasJoint(int joint) {
            return skeletonAvailable
                    && joint >= 0 && joint < skeletonJointCount
                    && skeletonJointPositions != null
                    && skeletonJointPositions.length >= skeletonJointCount * 3;
        }

        float jointX(int joint) {
            return skeletonJointPositions[joint * 3];
        }

        float jointY(int joint) {
            return skeletonJointPositions[joint * 3 + 1];
        }

        float jointZ(int joint) {
            return skeletonJointPositions[joint * 3 + 2];
        }

        LinkFrame withTextures(TextureUpdate[] textures) {
            return new LinkFrame(
                    sequence, simulationTick,
                    x, y, z,
                    faceAngle, action, animId, animFrame,
                    skeletonAvailable,
                    skeletonJointCount, skeletonParents, skeletonJointPositions,
                    triangleCount,
                    positions, normals, colors, uvs, triangleTextures,
                    textures, receivedNanos);
        }
    }
}
