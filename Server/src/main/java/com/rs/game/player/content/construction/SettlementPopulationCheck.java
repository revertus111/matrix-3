package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.rs.game.player.Player;

/**
 * Phase-2 Bundle 2.1 population/recruitment confidence check.
 *
 * runSelfTest() is disposable and never touches a Player save. run(Player)
 * verifies the real settlement after Worker #2 has been recruited.
 */
public final class SettlementPopulationCheck {

    private SettlementPopulationCheck() {
    }

    public static String runSelfTest() {
        String stage = "locked";
        try {
            SettlementState state = new SettlementState();
            state.normalize();

            require(state.getPopulationCapacity() == 0,
                    "new settlement population capacity is not locked");
            require(!state.canRecruitAdditionalWorker(),
                    "recruitment opened before starter shelter");
            require(state.recruitAdditionalWorker() == null,
                    "worker recruited before starter shelter");

            stage = "shelter";
            satisfyStarterShelter(state);
            require(state.tryCompleteStarterShelterMilestone(),
                    "starter shelter did not complete");
            require(state.getPopulationCapacity()
                    == SettlementState.STARTER_SHELTER_POPULATION_CAPACITY,
                    "starter population capacity mismatch");

            stage = "starter";
            SettlementWorkerState starter = state.ensureStarterWorker();
            require(starter != null, "starter worker missing");
            require(starter.getWorkerId() > 0L, "starter worker id invalid");
            require(SettlementWorkerDefinition.STARTER_SETTLER.getKey()
                    .equals(starter.getDefinitionKey()),
                    "starter worker definition mismatch");
            require(state.getWorkerCount() == 1,
                    "starter worker count mismatch");
            require(state.canRecruitAdditionalWorker(),
                    "Worker #2 recruitment should be ready");

            stage = "recruit";
            SettlementWorkerState recruited = state.recruitAdditionalWorker();
            require(recruited != null, "Worker #2 recruitment failed");
            require(recruited.getWorkerId() > 0L
                    && recruited.getWorkerId() != starter.getWorkerId(),
                    "Worker #2 stable id is not unique");
            require(SettlementWorkerDefinition.RECRUITED_SETTLER.getKey()
                    .equals(recruited.getDefinitionKey()),
                    "Worker #2 definition mismatch");
            require(SettlementWorkerDefinition.isReservedArrivalTile(
                    recruited.getHomePlotX(),
                    recruited.getHomePlotY(),
                    recruited.getHomePlane()),
                    "Worker #2 home slot is not reserved");
            require(state.getWorkerCount() == 2,
                    "population count did not reach two workers");
            require(!state.canRecruitAdditionalWorker(),
                    "recruitment remained open at capacity");
            require(state.recruitAdditionalWorker() == null,
                    "third worker bypassed starter capacity");

            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                require(!recruited.isJobAllowed(job),
                        "new Worker #2 did not default " + job.getKey() + " OFF");
            }
            require(!recruited.isPaused(),
                    "new Worker #2 unexpectedly defaulted paused");

            stage = "serialize";
            byte[] encoded = serialize(state);
            SettlementState restored = deserialize(encoded);
            restored.normalize();

            require(restored.getPopulationCapacity()
                    == SettlementState.STARTER_SHELTER_POPULATION_CAPACITY,
                    "population capacity changed after serialization");
            require(restored.getWorkerCount() == 2,
                    "worker count changed after serialization");

            Set<Long> ids = new HashSet<Long>();
            boolean foundStarter = false;
            boolean foundRecruited = false;
            for (SettlementWorkerState worker : restored.snapshotWorkers()) {
                require(ids.add(Long.valueOf(worker.getWorkerId())),
                        "duplicate worker id after serialization");
                if (SettlementWorkerDefinition.STARTER_SETTLER.getKey()
                        .equals(worker.getDefinitionKey())) {
                    foundStarter = true;
                } else if (SettlementWorkerDefinition.RECRUITED_SETTLER.getKey()
                        .equals(worker.getDefinitionKey())) {
                    foundRecruited = true;
                }
            }
            require(foundStarter && foundRecruited,
                    "worker definitions changed after serialization");
            require(!restored.canRecruitAdditionalWorker(),
                    "restored full population reopened recruitment");
            SettlementWorkerState restoredRecruited =
                    restored.findWorker(recruited.getWorkerId());
            require(restoredRecruited != null && !restoredRecruited.isPaused(),
                    "Worker #2 default pause state changed after serialization");
            require(restored.ensureStarterWorker().getWorkerId()
                    == starter.getWorkerId(),
                    "restored state duplicated/replaced Worker #1");

            return "PASS: shelter capacity=2 + gated recruitment + unique Worker #2 id + default jobs OFF + serialization.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
        }
    }

    public static String run(Player player) {
        if (player == null) {
            return "FAIL: player is null.";
        }

        String selfTest = runSelfTest();
        if (selfTest == null || !selfTest.startsWith("PASS:")) {
            return "FAIL: population self-test -> " + selfTest;
        }

        SettlementState state = player.getSettlementState();
        if (!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return "NOT READY: starter shelter milestone is not complete.";
        }
        if (state.getPopulationCapacity()
                != SettlementState.STARTER_SHELTER_POPULATION_CAPACITY) {
            return "FAIL: live population capacity is "
                    + state.getPopulationCapacity() + ".";
        }

        List<SettlementWorkerState> workers = state.snapshotWorkers();
        if (workers.size() != 2) {
            return "NOT READY: expected 2 persistent workers, found "
                    + workers.size() + ". " + state.getPopulationSummary();
        }

        Set<Long> ids = new HashSet<Long>();
        boolean foundStarter = false;
        boolean foundRecruited = false;
        for (SettlementWorkerState worker : workers) {
            if (worker == null || worker.getWorkerId() <= 0L
                    || !ids.add(Long.valueOf(worker.getWorkerId()))) {
                return "FAIL: persistent worker ids are invalid/duplicated.";
            }
            if (SettlementWorkerDefinition.STARTER_SETTLER.getKey()
                    .equals(worker.getDefinitionKey())) {
                foundStarter = true;
            } else if (SettlementWorkerDefinition.RECRUITED_SETTLER.getKey()
                    .equals(worker.getDefinitionKey())) {
                foundRecruited = true;
            }
            if (!SettlementState.isValidPlotLocation(
                    worker.getHomePlotX(),
                    worker.getHomePlotY(),
                    worker.getHomePlane())) {
                return "FAIL: Worker #" + worker.getWorkerId()
                        + " has an invalid home slot.";
            }
        }
        if (!foundStarter || !foundRecruited) {
            return "FAIL: expected starter-settler + recruited-settler.";
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the settlement so both workers can be projected.";
        }
        if (active.getActiveWorkerCount() != 2) {
            return "FAIL: runtime worker count is "
                    + active.getActiveWorkerCount() + ", expected 2.";
        }
        for (SettlementWorkerState worker : workers) {
            if (!active.hasActiveWorker(worker.getWorkerId())) {
                return "FAIL: Worker #" + worker.getWorkerId()
                        + " has no live NPC projection.";
            }
        }

        return "PASS: " + state.getPopulationSummary()
                + " | saved=2/runtime=2 | unique Worker #1/#2 ids and projections.";
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
