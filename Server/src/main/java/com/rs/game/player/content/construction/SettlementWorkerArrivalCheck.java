package com.rs.game.player.content.construction;

import java.util.List;

import com.rs.game.player.Player;

/**
 * Aggregate acceptance for the first Bundle 1.4 worker-arrival slice.
 *
 * This check never creates worker progress. It verifies the automatic
 * milestone-gated arrival already produced persistent and runtime ownership.
 */
public final class SettlementWorkerArrivalCheck {

    private SettlementWorkerArrivalCheck() {
    }

    public static String run(Player player) {
        if (player == null) {
            return "FAIL: player is null.";
        }

        String selfTest = SettlementWorkerSelfTest.run();
        if (selfTest == null || !selfTest.startsWith("PASS:")) {
            return "FAIL: worker self-test -> " + selfTest;
        }

        SettlementState state = player.getSettlementState();
        if (!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return "NOT READY: starter shelter milestone is not complete.";
        }

        List<SettlementWorkerState> workers = state.snapshotWorkers();
        if (workers.size() != 1) {
            return "FAIL: expected exactly 1 persistent starter worker, found "
                    + workers.size() + ".";
        }

        SettlementWorkerState worker = workers.get(0);
        SettlementWorkerDefinition definition = SettlementWorkerDefinition.forKey(
                worker.getDefinitionKey());
        if (definition != SettlementWorkerDefinition.STARTER_SETTLER) {
            return "FAIL: persistent worker definition is not starter-settler.";
        }
        if (worker.getWorkerId() <= 0L) {
            return "FAIL: persistent worker id is invalid.";
        }
        if (!SettlementState.isValidPlotLocation(
                worker.getHomePlotX(), worker.getHomePlotY(), worker.getHomePlane())) {
            return "FAIL: persistent worker home tile is invalid.";
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the settlement so Worker #1 can be projected.";
        }
        if (active.getActiveWorkerCount() != 1
                || !active.hasActiveWorker(worker.getWorkerId())) {
            return "FAIL: persistent Worker #" + worker.getWorkerId()
                    + " is not represented by exactly one live settlement NPC.";
        }

        return "PASS: Worker #" + worker.getWorkerId() + " " + worker.getName()
                + " persists and has exactly one live NPC projection.";
    }
}
