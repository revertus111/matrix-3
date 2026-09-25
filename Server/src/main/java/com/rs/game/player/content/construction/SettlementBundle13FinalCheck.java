package com.rs.game.player.content.construction;

import com.rs.game.player.Player;

/**
 * Aggregate Bundle 1.3 acceptance check.
 *
 * This does not fabricate settlement progress. It may only latch the starter
 * shelter milestone when the player's real saved state already satisfies the
 * authoritative requirements.
 */
public final class SettlementBundle13FinalCheck {

    private SettlementBundle13FinalCheck() {
    }

    public static String run(Player player) {
        if (player == null) {
            return "FAIL: player is null.";
        }

        String resourceTest = SettlementResourceSelfTest.run();
        if (resourceTest == null || !resourceTest.startsWith("PASS:")) {
            return "FAIL: resource self-test -> " + resourceTest;
        }

        String shelterTest = SettlementShelterSelfTest.run();
        if (shelterTest == null || !shelterTest.startsWith("PASS:")) {
            return "FAIL: shelter self-test -> " + shelterTest;
        }

        SettlementState state = player.getSettlementState();
        String audit = SettlementStateAudit.run(state);
        if (audit == null || !audit.startsWith("PASS:")) {
            return "FAIL: saved-state audit -> " + audit;
        }

        for (SettlementResource resource : SettlementResource.values()) {
            if (!resource.isStarterResource()) {
                continue;
            }
            if (state.getResourceAmount(resource) < SettlementState.STARTER_SHELTER_RESOURCE_EACH) {
                return "NOT READY: " + state.getStarterShelterStatus();
            }
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the settlement first. " + state.getStarterShelterStatus();
        }

        for (SettlementResourceNode node : SettlementResourceNode.values()) {
            if (!active.isStarterResourceNodeAvailable(node)) {
                return "FAIL: live starter node unavailable: " + node.getKey() + ".";
            }
        }

        if (!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            if (!state.meetsStarterShelterRequirements()) {
                return "NOT READY: " + state.getStarterShelterStatus();
            }
            if (!state.tryCompleteStarterShelterMilestone()) {
                return "FAIL: qualifying starter shelter did not latch completion.";
            }
        }

        if (!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return "FAIL: starter shelter milestone is not complete after qualification.";
        }

        return "PASS: resources + live nodes + real-save audit + starter shelter COMPLETE; "
                + state.getStarterShelterStatus();
    }
}
