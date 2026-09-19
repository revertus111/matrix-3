package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Disposable confidence test for persistent per-worker Allowed Jobs.
 *
 * It never reads or mutates a Player save.
 */
public final class SettlementWorkerJobsSelfTest {

    private SettlementWorkerJobsSelfTest() {
    }

    public static String run() {
        String stage = "definitions";
        try {
            Set<String> keys = new HashSet<String>();
            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                require(job != null, "null job definition");
                require(job.getKey() != null && !job.getKey().trim().isEmpty(),
                        "job has no stable key");
                require(keys.add(job.getKey()), "duplicate job key " + job.getKey());
                require(SettlementWorkerJob.forKey(job.getKey()) == job,
                        "job lookup failed for " + job.getKey());
                if (job.isGatheringJob()) {
                    require(job.getResource() != null,
                            "gathering job has no resource " + job.getKey());
                }
            }

            stage = "worker";
            SettlementState state = new SettlementState();
            satisfyStarterShelter(state);
            require(state.tryCompleteStarterShelterMilestone(),
                    "starter shelter did not complete");
            SettlementWorkerState worker = state.ensureStarterWorker();
            require(worker != null, "starter worker was not created");

            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                require(!worker.isJobAllowed(job),
                        "new worker unexpectedly allows " + job.getKey());
            }

            stage = "permissions";
            worker.setJobAllowed(SettlementWorkerJob.GATHER_WOOD, true);
            worker.setJobAllowed(SettlementWorkerJob.HAUL, true);
            require(worker.isJobAllowed(SettlementWorkerJob.GATHER_WOOD),
                    "wood permission did not enable");
            require(worker.isJobAllowed(SettlementWorkerJob.HAUL),
                    "haul permission did not enable");
            require(!worker.isJobAllowed(SettlementWorkerJob.GATHER_FOOD),
                    "unselected food permission enabled");

            stage = "serialize";
            byte[] encoded = serialize(state);
            SettlementState restored = deserialize(encoded);
            restored.normalize();
            SettlementWorkerState restoredWorker = restored.getStarterWorker();
            require(restoredWorker != null, "worker missing after serialization");
            require(restoredWorker.isJobAllowed(SettlementWorkerJob.GATHER_WOOD),
                    "wood permission did not persist");
            require(restoredWorker.isJobAllowed(SettlementWorkerJob.HAUL),
                    "haul permission did not persist");
            require(!restoredWorker.isJobAllowed(SettlementWorkerJob.GATHER_FOOD),
                    "food permission changed after serialization");
            require(restoredWorker.getAllowedJobsSummary().contains("Gather Wood=ON"),
                    "permission summary missing wood state");

            stage = "disable";
            restoredWorker.setJobAllowed(SettlementWorkerJob.GATHER_WOOD, false);
            require(!restoredWorker.isJobAllowed(SettlementWorkerJob.GATHER_WOOD),
                    "wood permission did not disable");
            require(restoredWorker.isJobAllowed(SettlementWorkerJob.HAUL),
                    "disabling wood changed haul permission");

            return "PASS: default-off + per-job toggle + stable keys + serialization.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
        }
    }

    private static void satisfyStarterShelter(SettlementState state) {
        for (int index = 0; index < SettlementState.STARTER_SHELTER_WALLS; index++) {
            require(state.place(
                    SettlementBuildPiece.WOOD_FENCE_TEST,
                    30 + index, 30, SettlementState.PLOT_PLANE, index & 0x3) != null,
                    "failed to place shelter wall");
        }
        for (int index = 0; index < SettlementState.STARTER_SHELTER_FLOORS; index++) {
            require(state.place(
                    SettlementBuildPiece.FLOOR_DECORATION,
                    30 + index, 30, SettlementState.PLOT_PLANE, 0) != null,
                    "failed to place shelter floor");
        }
        require(state.place(
                SettlementBuildPiece.BASIC_DOOR,
                34, 30, SettlementState.PLOT_PLANE, 0) != null,
                "failed to place shelter door");
        for (SettlementResource resource : SettlementResource.values()) {
            require(state.addResource(
                    resource, SettlementState.STARTER_SHELTER_RESOURCE_EACH)
                    == SettlementState.STARTER_SHELTER_RESOURCE_EACH,
                    "failed to add shelter resource " + resource.getKey());
        }
    }

    private static byte[] serialize(SettlementState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        try {
            output.writeObject(state);
            output.flush();
            return bytes.toByteArray();
        } finally {
            output.close();
        }
    }

    private static SettlementState deserialize(byte[] encoded) throws Exception {
        ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(encoded));
        try {
            Object value = input.readObject();
            require(value instanceof SettlementState,
                    "deserialized value is not SettlementState");
            return (SettlementState) value;
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
