package game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot Java 8 <-> native libsm64 sidecar proof.
 *
 * This does not move the Matrix player. It only proves that Java can launch the
 * sidecar, send deterministic SM64 input, and receive native Mario state back.
 */
public final class Sm64BridgeProbe {

    private static final int PROTOCOL_VERSION = 1;
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean();

    private Sm64BridgeProbe() {
    }

    public static void startOnce() {
        if (!ATTEMPTED.compareAndSet(false, true)) {
            return;
        }

        final File bridge = resolvePath(
                "matrix3.sm64.bridge",
                "SM64_BRIDGE_EXE",
                defaultBridgePaths());
        final File rom = resolvePath(
                "matrix3.sm64.rom",
                "SM64_ROM",
                defaultRomPaths());

        if (!bridge.isFile()) {
            System.out.println("[SM64 Bridge] Probe skipped; sidecar not found: " + bridge.getAbsolutePath());
            System.out.println("[SM64 Bridge] Build it from native/sm64-bridge before the native proof.");
            return;
        }
        if (!rom.isFile()) {
            System.out.println("[SM64 Bridge] Probe skipped; ROM not found: " + rom.getAbsolutePath());
            System.out.println("[SM64 Bridge] Set -Dmatrix3.sm64.rom=<path> or SM64_ROM. ROM files stay outside Git.");
            return;
        }

        Thread probeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runProbe(bridge, rom);
            }
        }, "sm64-bridge-probe");
        probeThread.setDaemon(true);
        probeThread.start();
    }

    private static void runProbe(File bridge, File rom) {
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    bridge.getAbsolutePath(),
                    rom.getAbsolutePath());
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));

            String ready = reader.readLine();
            if (!("READY " + PROTOCOL_VERSION).equals(ready)) {
                throw new IllegalStateException("unexpected READY: " + ready);
            }

            send(writer, "PING");
            String pong = reader.readLine();
            if (!("PONG " + PROTOCOL_VERSION).equals(pong)) {
                throw new IllegalStateException("unexpected PONG: " + pong);
            }

            NativeState baseline = null;
            for (int i = 0; i < 4; i++) {
                baseline = step(writer, reader, false);
            }
            if (baseline == null) {
                throw new IllegalStateException("no baseline state");
            }

            float maxY = baseline.y;
            long baselineAction = baseline.action;
            long lastAction = baselineAction;
            boolean actionChanged = false;

            NativeState state = step(writer, reader, true);
            if (state.y > maxY) {
                maxY = state.y;
            }
            lastAction = state.action;
            actionChanged |= state.action != baselineAction;

            for (int i = 0; i < 75; i++) {
                state = step(writer, reader, false);
                if (state.y > maxY) {
                    maxY = state.y;
                }
                lastAction = state.action;
                actionChanged |= state.action != baselineAction;
            }

            float rise = maxY - baseline.y;
            if (rise > 5.0F && actionChanged) {
                System.out.println(String.format(Locale.ROOT,
                        "[SM64 Bridge] PASS native SM64 state: y %.2f -> %.2f (rise %.2f), action %d -> %d",
                        baseline.y, maxY, rise, baselineAction, lastAction));
            } else {
                System.out.println(String.format(Locale.ROOT,
                        "[SM64 Bridge] FAIL native state did not prove a jump: baselineY=%.2f maxY=%.2f actionChanged=%s",
                        baseline.y, maxY, Boolean.toString(actionChanged)));
            }

            send(writer, "QUIT");
            String bye = reader.readLine();
            if (!"BYE".equals(bye)) {
                System.out.println("[SM64 Bridge] Warning: unexpected shutdown reply: " + bye);
            }
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (Throwable throwable) {
            System.out.println("[SM64 Bridge] Probe failed: " + throwable.getMessage());
            throwable.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static NativeState step(BufferedWriter writer, BufferedReader reader, boolean buttonA) throws Exception {
        send(writer, String.format(Locale.ROOT,
                "STEP 0.0 -1.0 0.0 0.0 %d 0 0",
                buttonA ? 1 : 0));
        String line = reader.readLine();
        return NativeState.parse(line);
    }

    private static void send(BufferedWriter writer, String command) throws Exception {
        writer.write(command);
        writer.newLine();
        writer.flush();
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
        final long action;

        NativeState(float x, float y, float z, long action) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.action = action;
        }

        static NativeState parse(String line) {
            if (line == null) {
                throw new IllegalStateException("sidecar closed stdout");
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length != 13 || !"STATE".equals(tokens[0])) {
                throw new IllegalStateException("unexpected state packet: " + line);
            }
            return new NativeState(
                    Float.parseFloat(tokens[1]),
                    Float.parseFloat(tokens[2]),
                    Float.parseFloat(tokens[3]),
                    Long.parseLong(tokens[9]));
        }
    }
}
