package game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Read-only Mario 64 runtime diagnostics published from the existing Matrix
 * client/controller tick. This recorder never owns input, rendering, movement,
 * combat, native stepping or player suppression; it only snapshots those owners.
 */
public final class Mario64Diagnostics {

    private static final int MAX_EVENTS = 256;
    private static final long DIRECTION_PROBE_INTERVAL_MILLIS = 500L;
    private static final float DIRECTION_PROBE_MIN_SPEED = 0.5F;
    private static final Object EVENT_LOCK = new Object();
    private static final Deque<String> EVENTS = new ArrayDeque<String>(MAX_EVENTS);

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile long eventVersion;

    private static volatile float moveX;
    private static volatile float moveY;
    private static volatile float cameraForwardX;
    private static volatile float cameraForwardZ = -1.0F;
    private static volatile boolean physicalJump;
    private static volatile boolean physicalPrimary;
    private static volatile boolean physicalModifier;
    private static volatile boolean sentA;
    private static volatile boolean sentB;
    private static volatile boolean sentZ;

    // Transition memory is written only by the established Mario client tick.
    private static boolean runtimeInitialized;
    private static String lastMode;
    private static boolean lastBridgeReady;
    private static boolean lastBridgeFailed;
    private static boolean lastGeometryPresent;
    private static boolean lastSuppressRuneScape;
    private static long lastSequence = -1L;
    private static long lastAction = -1L;
    private static int lastAnimId = Integer.MIN_VALUE;
    private static boolean controlsInitialized;
    private static boolean lastPhysicalJump;
    private static boolean lastSentA;
    private static boolean lastSentB;
    private static boolean lastSentZ;
    private static String lastFailureReason;
    private static long lastDirectionProbeMillis;

    private Mario64Diagnostics() {
    }

    /**
     * Captures the exact controls sampled by the Mario driver plus the post-entry-
     * guard A/B/Z values actually sent to libsm64 for the next native step.
     */
    static void observeControls(AlternateCharacterController.ControlState controls,
            boolean forwardedA, boolean forwardedB, boolean forwardedZ) {
        if (controls == null) {
            return;
        }

        moveX = controls.moveX;
        moveY = controls.moveY;
        cameraForwardX = controls.cameraForward == null ? 0.0F : controls.cameraForward.x;
        cameraForwardZ = controls.cameraForward == null ? -1.0F : controls.cameraForward.z;
        physicalJump = controls.jump;
        physicalPrimary = controls.primaryAction;
        physicalModifier = controls.modifierAction;
        sentA = forwardedA;
        sentB = forwardedB;
        sentZ = forwardedZ;

        if (!controlsInitialized) {
            controlsInitialized = true;
            lastPhysicalJump = controls.jump;
            lastSentA = forwardedA;
            lastSentB = forwardedB;
            lastSentZ = forwardedZ;
            return;
        }

        if (controls.jump != lastPhysicalJump) {
            appendEvent(controls.jump ? "SPACE_DOWN" : "SPACE_UP");
            lastPhysicalJump = controls.jump;
        }
        if (forwardedA != lastSentA) {
            appendEvent(forwardedA ? "A_SEND_DOWN" : "A_SEND_UP");
            lastSentA = forwardedA;
        }
        if (forwardedB != lastSentB) {
            appendEvent(forwardedB ? "B_SEND_DOWN" : "B_SEND_UP");
            lastSentB = forwardedB;
        }
        if (forwardedZ != lastSentZ) {
            appendEvent(forwardedZ ? "Z_SEND_DOWN" : "Z_SEND_UP");
            lastSentZ = forwardedZ;
        }
    }

    /**
     * Publishes one immutable runtime snapshot and records only meaningful state
     * transitions. Called from the Mario client tick, not from Swing, so brief
     * presentation changes are not lost to a slower diagnostics refresh timer.
     */
    static void observeRuntime(Player player) {
        PlayerControllerMode.Mode controllerMode = PlayerControllerMode.getMode();
        String mode = controllerMode == null ? "UNKNOWN" : controllerMode.name();
        AlternateCharacterController.CharacterId character =
                AlternateCharacterController.getActiveCharacter();
        boolean bridgeReady = Sm64BridgeSession.isReady();
        boolean bridgeFailed = Sm64BridgeSession.hasFailed();
        String failureReason = Sm64BridgeSession.getFailureReason();

        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        boolean geometryPresent = frame != null && frame.state != null && frame.triangleCount > 0;
        long sequence = geometryPresent ? frame.sequence : -1L;
        long frameAgeMillis = geometryPresent
                ? nanosToMillisClamped(System.nanoTime() - frame.state.receivedNanos)
                : -1L;

        long action = geometryPresent ? frame.state.action : -1L;
        int animId = geometryPresent ? frame.state.animId : -1;
        int animFrame = geometryPresent ? frame.state.animFrame : -1;
        int triangles = geometryPresent ? frame.triangleCount : 0;
        float x = geometryPresent ? frame.state.x : Float.NaN;
        float y = geometryPresent ? frame.state.y : Float.NaN;
        float z = geometryPresent ? frame.state.z : Float.NaN;
        float vx = geometryPresent ? frame.state.vx : Float.NaN;
        float vy = geometryPresent ? frame.state.vy : Float.NaN;
        float vz = geometryPresent ? frame.state.vz : Float.NaN;
        float faceAngle = geometryPresent ? frame.state.faceAngle : Float.NaN;
        float forwardVelocity = geometryPresent ? frame.state.forwardVelocity : Float.NaN;

        boolean suppressRuneScape = MarioVisualRenderer.shouldSuppressLocalPlayer(player);
        boolean airborne = MarioJumpController.isAirborne();
        float heightOffset = MarioJumpController.getHeightOffset();

        Snapshot next = new Snapshot(
                System.currentTimeMillis(),
                client.cycles,
                mode,
                character == null ? "NONE" : character.name(),
                player != null,
                bridgeReady,
                bridgeFailed,
                failureReason,
                geometryPresent,
                sequence,
                frameAgeMillis,
                action,
                animId,
                animFrame,
                triangles,
                x, y, z,
                vx, vy, vz,
                faceAngle,
                forwardVelocity,
                moveX,
                moveY,
                cameraForwardX,
                cameraForwardZ,
                physicalJump,
                physicalPrimary,
                physicalModifier,
                sentA,
                sentB,
                sentZ,
                suppressRuneScape,
                airborne,
                heightOffset);
        snapshot = next;

        recordRuntimeTransitions(next);
        recordDirectionProbe(next);
    }

    static void noteFallback(String reason) {
        appendEvent("FALLBACK -> RUNESCAPE reason=" + safe(reason));
    }

    public static Snapshot getSnapshot() {
        return snapshot;
    }

    public static long getEventVersion() {
        return eventVersion;
    }

    public static void clearEvents() {
        synchronized (EVENT_LOCK) {
            EVENTS.clear();
            eventVersion++;
        }
    }

    public static String getEventLogText() {
        synchronized (EVENT_LOCK) {
            StringBuilder builder = new StringBuilder(EVENTS.size() * 72);
            for (String event : EVENTS) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(event);
            }
            return builder.toString();
        }
    }

    public static String formatSnapshot(Snapshot value) {
        if (value == null) {
            value = Snapshot.empty();
        }
        StringBuilder builder = new StringBuilder(768);
        builder.append("Mario 64 / Matrix3 diagnostics\n");
        builder.append("cycle=").append(value.cycle)
                .append(" mode=").append(value.mode)
                .append(" character=").append(value.activeCharacter).append('\n');
        builder.append("playerPresent=").append(value.localPlayerPresent)
                .append(" suppressRuneScape=").append(value.suppressRuneScape)
                .append(" airborne=").append(value.airborne)
                .append(" heightOffset=").append(formatFloat(value.heightOffset)).append('\n');
        builder.append("bridgeReady=").append(value.bridgeReady)
                .append(" bridgeFailed=").append(value.bridgeFailed)
                .append(" failure=").append(safe(value.failureReason)).append('\n');
        builder.append("geometryPresent=").append(value.geometryPresent)
                .append(" sequence=").append(value.frameSequence)
                .append(" frameAgeMs=").append(value.frameAgeMillis)
                .append(" triangles=").append(value.triangleCount).append('\n');
        builder.append("action=").append(formatAction(value.action))
                .append(" anim=").append(value.animId)
                .append(" animFrame=").append(value.animFrame).append('\n');
        builder.append("pos=").append(formatVector(value.x, value.y, value.z))
                .append(" vel=").append(formatVector(value.vx, value.vy, value.vz)).append('\n');
        builder.append("forwardVelocity=").append(formatFloat(value.forwardVelocity))
                .append(" faceAngle=").append(formatFloat(value.faceAngle)).append('\n');
        builder.append("cameraForward=").append(formatVector2(value.cameraForwardX, value.cameraForwardZ))
                .append(" move=").append(formatVector2(value.moveX, value.moveY)).append('\n');
        builder.append("physical Space/F/Shift=")
                .append(value.physicalJump).append('/')
                .append(value.physicalPrimary).append('/')
                .append(value.physicalModifier).append('\n');
        builder.append("forwarded A/B/Z=")
                .append(value.sentA).append('/')
                .append(value.sentB).append('/')
                .append(value.sentZ);
        return builder.toString();
    }

    private static void recordRuntimeTransitions(Snapshot value) {
        if (!runtimeInitialized) {
            runtimeInitialized = true;
            lastMode = value.mode;
            lastBridgeReady = value.bridgeReady;
            lastBridgeFailed = value.bridgeFailed;
            lastGeometryPresent = value.geometryPresent;
            lastSuppressRuneScape = value.suppressRuneScape;
            lastSequence = value.frameSequence;
            lastAction = value.action;
            lastAnimId = value.animId;
            lastFailureReason = value.failureReason;
            appendEvent("RECORDER_ATTACH mode=" + value.mode
                    + " bridgeReady=" + value.bridgeReady
                    + " suppressRuneScape=" + value.suppressRuneScape);
            return;
        }

        if (!same(lastMode, value.mode)) {
            appendEvent("MODE " + safe(lastMode) + " -> " + value.mode);
            lastMode = value.mode;
        }
        if (lastBridgeReady != value.bridgeReady) {
            appendEvent("BRIDGE_READY -> " + value.bridgeReady);
            lastBridgeReady = value.bridgeReady;
        }
        if (lastBridgeFailed != value.bridgeFailed) {
            appendEvent("BRIDGE_FAILED -> " + value.bridgeFailed
                    + (value.failureReason == null ? "" : " reason=" + value.failureReason));
            lastBridgeFailed = value.bridgeFailed;
        }
        if (!same(lastFailureReason, value.failureReason) && value.failureReason != null) {
            appendEvent("BRIDGE_FAILURE_REASON " + value.failureReason);
            lastFailureReason = value.failureReason;
        }
        if (lastGeometryPresent != value.geometryPresent) {
            appendEvent("GEOMETRY_PRESENT -> " + value.geometryPresent
                    + " frameAgeMs=" + value.frameAgeMillis);
            lastGeometryPresent = value.geometryPresent;
        }
        if (value.geometryPresent && lastSequence >= 0L && value.frameSequence > lastSequence + 1L) {
            appendEvent("FRAME_SEQUENCE_GAP " + lastSequence + " -> " + value.frameSequence);
        }
        if (value.geometryPresent) {
            lastSequence = value.frameSequence;
        }
        if (value.action >= 0L && value.action != lastAction) {
            appendEvent("ACTION " + formatAction(lastAction) + " -> " + formatAction(value.action));
            lastAction = value.action;
        }
        if (value.animId >= 0 && value.animId != lastAnimId) {
            appendEvent("ANIM " + lastAnimId + " -> " + value.animId
                    + " frame=" + value.animFrame);
            lastAnimId = value.animId;
        }
        if (lastSuppressRuneScape != value.suppressRuneScape) {
            appendEvent("SUPPRESS_RS -> " + value.suppressRuneScape
                    + " seq=" + value.frameSequence
                    + " frameAgeMs=" + value.frameAgeMillis
                    + " space=" + value.physicalJump
                    + " sentA=" + value.sentA);
            lastSuppressRuneScape = value.suppressRuneScape;
        }
    }

    /**
     * Pure-W diagnostic for the unresolved Matrix-camera/libsm64 coordinate seam.
     * It compares the requested camera-forward direction with Mario's actual
     * horizontal native velocity. dot~=+1 is aligned, -1 is reversed, and dot~=0
     * with a large cross magnitude indicates an approximately 90-degree rotation.
     * This is read-only and deliberately rate-limited so it cannot become a new
     * movement/control owner or flood the console/event recorder.
     */
    private static void recordDirectionProbe(Snapshot value) {
        if (value == null || !"MARIO".equals(value.mode) || !value.bridgeReady
                || !value.geometryPresent || Math.abs(value.moveX) > 0.05F
                || value.moveY < 0.95F) {
            return;
        }

        float speed = (float) Math.sqrt(value.vx * value.vx + value.vz * value.vz);
        if (Float.isNaN(speed) || Float.isInfinite(speed) || speed < DIRECTION_PROBE_MIN_SPEED) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDirectionProbeMillis < DIRECTION_PROBE_INTERVAL_MILLIS) {
            return;
        }
        lastDirectionProbeMillis = now;

        float velocityX = value.vx / speed;
        float velocityZ = value.vz / speed;
        float dot = value.cameraForwardX * velocityX + value.cameraForwardZ * velocityZ;
        float cross = value.cameraForwardX * velocityZ - value.cameraForwardZ * velocityX;
        String message = "DIRECTION_W cam="
                + formatVector2(value.cameraForwardX, value.cameraForwardZ)
                + " nativeVel=" + formatVector2(velocityX, velocityZ)
                + " dot=" + formatFloat(dot)
                + " cross=" + formatFloat(cross);
        appendEvent(message);
        System.out.println("[SM64 Direction] " + message);
    }

    private static void appendEvent(String message) {
        long now = System.currentTimeMillis();
        String event = String.format(Locale.ROOT,
                "%1$tH:%1$tM:%1$tS.%1$tL cycle=%2$d  %3$s",
                Long.valueOf(now), Integer.valueOf(client.cycles), safe(message));
        synchronized (EVENT_LOCK) {
            while (EVENTS.size() >= MAX_EVENTS) {
                EVENTS.removeFirst();
            }
            EVENTS.addLast(event);
            eventVersion++;
        }
    }

    private static long nanosToMillisClamped(long nanos) {
        if (nanos < 0L) {
            return 0L;
        }
        return nanos / 1000000L;
    }

    private static String formatAction(long action) {
        if (action < 0L) {
            return "-";
        }
        return String.format(Locale.ROOT, "0x%08X (%d)",
                Long.valueOf(action & 0xffffffffL), Long.valueOf(action & 0xffffffffL));
    }

    private static String formatVector(float a, float b, float c) {
        return "(" + formatFloat(a) + ", " + formatFloat(b) + ", " + formatFloat(c) + ")";
    }

    private static String formatVector2(float a, float b) {
        return "(" + formatFloat(a) + ", " + formatFloat(b) + ")";
    }

    private static String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.3f", Float.valueOf(value));
    }

    private static String safe(String value) {
        return value == null || value.length() == 0 ? "-" : value;
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public static final class Snapshot {
        public final long capturedMillis;
        public final int cycle;
        public final String mode;
        public final String activeCharacter;
        public final boolean localPlayerPresent;
        public final boolean bridgeReady;
        public final boolean bridgeFailed;
        public final String failureReason;
        public final boolean geometryPresent;
        public final long frameSequence;
        public final long frameAgeMillis;
        public final long action;
        public final int animId;
        public final int animFrame;
        public final int triangleCount;
        public final float x;
        public final float y;
        public final float z;
        public final float vx;
        public final float vy;
        public final float vz;
        public final float faceAngle;
        public final float forwardVelocity;
        public final float moveX;
        public final float moveY;
        public final float cameraForwardX;
        public final float cameraForwardZ;
        public final boolean physicalJump;
        public final boolean physicalPrimary;
        public final boolean physicalModifier;
        public final boolean sentA;
        public final boolean sentB;
        public final boolean sentZ;
        public final boolean suppressRuneScape;
        public final boolean airborne;
        public final float heightOffset;

        private Snapshot(long capturedMillis, int cycle,
                String mode, String activeCharacter,
                boolean localPlayerPresent,
                boolean bridgeReady, boolean bridgeFailed, String failureReason,
                boolean geometryPresent, long frameSequence, long frameAgeMillis,
                long action, int animId, int animFrame, int triangleCount,
                float x, float y, float z,
                float vx, float vy, float vz,
                float faceAngle, float forwardVelocity,
                float moveX, float moveY,
                float cameraForwardX, float cameraForwardZ,
                boolean physicalJump, boolean physicalPrimary, boolean physicalModifier,
                boolean sentA, boolean sentB, boolean sentZ,
                boolean suppressRuneScape, boolean airborne, float heightOffset) {
            this.capturedMillis = capturedMillis;
            this.cycle = cycle;
            this.mode = mode;
            this.activeCharacter = activeCharacter;
            this.localPlayerPresent = localPlayerPresent;
            this.bridgeReady = bridgeReady;
            this.bridgeFailed = bridgeFailed;
            this.failureReason = failureReason;
            this.geometryPresent = geometryPresent;
            this.frameSequence = frameSequence;
            this.frameAgeMillis = frameAgeMillis;
            this.action = action;
            this.animId = animId;
            this.animFrame = animFrame;
            this.triangleCount = triangleCount;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.faceAngle = faceAngle;
            this.forwardVelocity = forwardVelocity;
            this.moveX = moveX;
            this.moveY = moveY;
            this.cameraForwardX = cameraForwardX;
            this.cameraForwardZ = cameraForwardZ;
            this.physicalJump = physicalJump;
            this.physicalPrimary = physicalPrimary;
            this.physicalModifier = physicalModifier;
            this.sentA = sentA;
            this.sentB = sentB;
            this.sentZ = sentZ;
            this.suppressRuneScape = suppressRuneScape;
            this.airborne = airborne;
            this.heightOffset = heightOffset;
        }

        private static Snapshot empty() {
            return new Snapshot(
                    0L, 0,
                    "RUNESCAPE", "NONE",
                    false,
                    false, false, null,
                    false, -1L, -1L,
                    -1L, -1, -1, 0,
                    Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN,
                    0.0F, 0.0F,
                    0.0F, -1.0F,
                    false, false, false,
                    false, false, false,
                    false, false, 0.0F);
        }
    }
}
