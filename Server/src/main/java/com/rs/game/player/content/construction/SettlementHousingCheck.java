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
 * Phase-2 Bundle 2.3 housing/bed capacity confidence check.
 *
 * runSelfTest() is disposable. run(Player) verifies the live three-worker
 * capacity/projection after one persistent housing bed has been added.
 */
public final class SettlementHousingCheck {

    private SettlementHousingCheck() {
    }

    public static String runSelfTest() {
        String stage = "locked";
        try {
            SettlementState state = new SettlementState();
            state.normalize();
            require(state.getHousingBedCount() == 0, "new state bed count is not zero");
            require(!state.addHousingBed(), "bed capacity opened before starter shelter");

            stage = "starter";
            satisfyStarterShelter(state);
            require(state.tryCompleteStarterShelterMilestone(),
                    "starter shelter did not complete");
            SettlementWorkerState first = state.ensureStarterWorker();
            SettlementWorkerState second = state.recruitAdditionalWorker();
            require(first != null && second != null, "starter two-worker population failed");
            require(state.getPopulationCapacity() == 2, "starter capacity is not two");
            require(!state.canRecruitAdditionalWorker(),
                    "third worker opened before housing bed");

            stage = "bed-capacity";
            require(state.addHousingBed(), "housing bed could not be added");
            require(state.getHousingBedCount() == 1, "housing bed count mismatch");
            require(state.getPopulationCapacity() == 3, "one bed did not raise capacity to three");
            require(state.canRecruitAdditionalWorker(),
                    "third worker did not open after housing bed");

            stage = "worker-three";
            SettlementWorkerState third = state.recruitAdditionalWorker();
            require(third != null, "Worker #3 recruitment failed");
            require(third.getWorkerId() != first.getWorkerId()
                    && third.getWorkerId() != second.getWorkerId(),
                    "Worker #3 id is not unique");
            require(third.getHomePlotX() != second.getHomePlotX()
                    || third.getHomePlotY() != second.getHomePlotY(),
                    "Worker #3 reused Worker #2 home slot");
            require(state.hasWorkerHomeAt(
                    third.getHomePlotX(), third.getHomePlotY(), third.getHomePlane()),
                    "Worker #3 home slot is not owned by SettlementState");
            require(!state.canRecruitAdditionalWorker(),
                    "fourth worker bypassed capacity=3");
            require(state.recruitAdditionalWorker() == null,
                    "fourth worker recruited at capacity=3");
            require(!state.removeHousingBed(),
                    "occupied bed capacity was removable below live population");

            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                require(!third.isJobAllowed(job),
                        "Worker #3 did not default " + job.getKey() + " OFF");
            }
            require(!third.isPaused(), "Worker #3 unexpectedly defaulted paused");

            stage = "serialize";
            SettlementState restored = deserialize(serialize(state));
            restored.normalize();
            require(restored.getHousingBedCount() == 1,
                    "housing bed count changed after serialization");
            require(restored.getPopulationCapacity() == 3,
                    "population capacity changed after serialization");
            require(restored.getWorkerCount() == 3,
                    "worker count changed after serialization");

            Set<Long> ids = new HashSet<Long>();
            Set<String> homes = new HashSet<String>();
            for (SettlementWorkerState worker : restored.snapshotWorkers()) {
                require(ids.add(Long.valueOf(worker.getWorkerId())),
                        "duplicate worker id after serialization");
                require(homes.add(worker.getHomePlotX() + ":" + worker.getHomePlotY()
                        + ":" + worker.getHomePlane()),
                        "duplicate worker home after serialization");
            }

            SettlementWorkerState restoredThird = restored.findWorker(third.getWorkerId());
            require(restoredThird != null, "Worker #3 missing after serialization");
            require(restored.hasWorkerHomeAt(
                    restoredThird.getHomePlotX(),
                    restoredThird.getHomePlotY(),
                    restoredThird.getHomePlane()),
                    "Worker #3 home ownership lost after serialization");

            return "PASS: persistent bed capacity 2->3 + unique Worker #3 id/home + default policy + serialization.";
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
            return "FAIL: housing self-test -> " + selfTest;
        }

        SettlementState state = player.getSettlementState();
        if (!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return "NOT READY: starter shelter milestone is not complete.";
        }
        if (state.getHousingBedCount() < 1 || state.getPopulationCapacity() < 3) {
            return "NOT READY: add at least one housing bed. " + state.getHousingSummary();
        }

        List<SettlementWorkerState> workers = state.snapshotWorkers();
        if (workers.size() != 3) {
            return "NOT READY: expected 3 persistent workers, found "
                    + workers.size() + ". " + state.getHousingSummary();
        }

        Set<Long> ids = new HashSet<Long>();
        Set<String> homes = new HashSet<String>();
        for (SettlementWorkerState worker : workers) {
            if (worker == null || worker.getWorkerId() <= 0L
                    || !ids.add(Long.valueOf(worker.getWorkerId()))) {
                return "FAIL: persistent worker ids are invalid/duplicated.";
            }
            String home = worker.getHomePlotX() + ":" + worker.getHomePlotY()
                    + ":" + worker.getHomePlane();
            if (!homes.add(home)) {
                return "FAIL: persistent worker home slots overlap.";
            }
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the settlement so all workers can be projected.";
        }
        if (active.getActiveWorkerCount() != 3) {
            return "FAIL: runtime worker count is "
                    + active.getActiveWorkerCount() + ", expected 3.";
        }
        for (SettlementWorkerState worker : workers) {
            if (!active.hasActiveWorker(worker.getWorkerId())) {
                return "FAIL: Worker #" + worker.getWorkerId()
                        + " has no live NPC projection.";
            }
        }

        return "PASS: " + state.getHousingSummary()
                + " | saved=3/runtime=3 | unique worker ids + home slots + projections.";
    }

    private static void satisfyStarterShelter(SettlementState state) {
        for (int index = 0; index < SettlementState.STARTER_SHELTER_WALLS; index++) {
            require(state.place(SettlementBuildPiece.WOOD_FENCE_TEST,
                    30 + index, 30, SettlementState.PLOT_PLANE, index & 0x3) != null,
                    "failed shelter wall");
        }
        for (int index = 0; index < SettlementState.STARTER_SHELTER_FLOORS; index++) {
            require(state.place(SettlementBuildPiece.FLOOR_DECORATION,
                    30 + index, 30, SettlementState.PLOT_PLANE, 0) != null,
                    "failed shelter floor");
        }
        require(state.place(SettlementBuildPiece.BASIC_DOOR,
                34, 30, SettlementState.PLOT_PLANE, 0) != null,
                "failed shelter door");
        for (SettlementResource resource : SettlementResource.values()) {
            require(state.addResource(resource,
                    SettlementState.STARTER_SHELTER_RESOURCE_EACH)
                    == SettlementState.STARTER_SHELTER_RESOURCE_EACH,
                    "failed shelter resource");
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
            return (SettlementState) input.readObject();
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
