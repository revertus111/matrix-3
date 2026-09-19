package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Disposable confidence test for milestone-gated starter-worker persistence.
 * It never reads or mutates a Player save.
 */
public final class SettlementWorkerSelfTest {

    private SettlementWorkerSelfTest() {
    }

    public static String run() {
        String stage = "gate";
        try {
            SettlementState state = new SettlementState();
            state.normalize();

            require(state.getWorkerCount() == 0, "new settlement already has a worker");
            require(state.ensureStarterWorker() == null,
                    "starter worker arrived before shelter milestone");

            stage = "milestone";
            satisfyStarterShelter(state);
            require(state.tryCompleteStarterShelterMilestone(),
                    "starter shelter did not complete");
            require(state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER),
                    "starter shelter flag missing");

            stage = "arrival";
            SettlementWorkerState worker = state.ensureStarterWorker();
            require(worker != null, "starter worker was not created");
            require(worker.getWorkerId() > 0L, "worker id is invalid");
            require(SettlementWorkerDefinition.STARTER_SETTLER.getKey()
                    .equals(worker.getDefinitionKey()), "worker definition key mismatch");
            require(SettlementWorkerDefinition.STARTER_SETTLER.getDisplayName()
                    .equals(worker.getName()), "worker default name mismatch");
            require(SettlementState.isValidPlotLocation(
                    worker.getHomePlotX(), worker.getHomePlotY(), worker.getHomePlane()),
                    "worker home tile is invalid");
            require(SettlementWorkerDefinition.isReservedArrivalTile(
                    worker.getHomePlotX(), worker.getHomePlotY(), worker.getHomePlane()),
                    "worker home tile is not reserved");

            SettlementWorkerState again = state.ensureStarterWorker();
            require(again != null && again.getWorkerId() == worker.getWorkerId(),
                    "repeated ensure changed worker identity");
            require(state.getWorkerCount() == 1,
                    "repeated ensure created a duplicate worker");

            stage = "serialize";
            byte[] encoded = serialize(state);
            SettlementState restored = deserialize(encoded);
            restored.normalize();

            require(restored.getWorkerCount() == 1,
                    "worker count changed after serialization");
            SettlementWorkerState restoredWorker = restored.snapshotWorkers().get(0);
            require(restoredWorker.getWorkerId() == worker.getWorkerId(),
                    "worker id changed after serialization");
            require(worker.getDefinitionKey().equals(restoredWorker.getDefinitionKey()),
                    "worker definition changed after serialization");
            require(worker.getName().equals(restoredWorker.getName()),
                    "worker name changed after serialization");
            require(restored.ensureStarterWorker().getWorkerId() == worker.getWorkerId(),
                    "restored state created a duplicate starter worker");

            return "PASS: milestone gate + stable worker identity + no duplicates + serialization.";
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
