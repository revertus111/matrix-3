package com.rs.game.player.content.construction;

import java.util.List;

import com.rs.game.player.Player;

/**
 * Read-only/disposable aggregate confidence check for the Bundle 1.2 server
 * foundation. It never mutates the player's real SettlementState.
 */
public final class SettlementBundle12FinalCheck {

    private SettlementBundle12FinalCheck() {
    }

    public static String run(Player player) {
        if (player == null) {
            return "FAIL: player is null.";
        }

        String selfTest = SettlementStateSelfTest.run();
        if (selfTest == null || !selfTest.startsWith("PASS:")) {
            return "FAIL: state self-test -> " + selfTest;
        }

        SettlementState state = player.getSettlementState();
        String audit = SettlementStateAudit.run(state);
        if (audit == null || !audit.startsWith("PASS:")) {
            return "FAIL: saved-state audit -> " + audit;
        }

        List<SettlementPlacedPiece> pieces = state.snapshotPieces();
        if (state.size() != pieces.size()) {
            return "FAIL: saved-piece count mismatch state=" + state.size()
                    + " snapshot=" + pieces.size() + ".";
        }

        String definitionFailure = validateDefinitions();
        if (definitionFailure != null) {
            return "FAIL: " + definitionFailure;
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        return "PASS: self-test + saved-state audit + count + definitions; "
                + pieces.size() + " saved piece(s), runtime "
                + (active == null ? "inactive." : (active.isLoaded() ? "loaded." : "loading."));
    }

    private static String validateDefinitions() {
        SettlementBuildPiece[] values = SettlementBuildPiece.values();
        if (values.length == 0) {
            return "no settlement build definitions exist.";
        }

        for (int index = 0; index < values.length; index++) {
            SettlementBuildPiece current = values[index];
            if (current == null || current.getKey() == null || current.getKey().trim().isEmpty()) {
                return "settlement build definition has no stable key.";
            }
            if (current.getObjectId() < 0 || current.getObjectType() < 0
                    || current.getObjectType() > 22) {
                return "definition " + current.getKey() + " has invalid object identity.";
            }

            if (SettlementBuildPiece.forKey(current.getKey()) != current) {
                return "definition key lookup failed for " + current.getKey() + ".";
            }
            if (SettlementBuildPiece.forObject(
                    current.getObjectId(), current.getObjectType()) != current) {
                return "object lookup failed for " + current.getKey() + ".";
            }

            for (int otherIndex = index + 1; otherIndex < values.length; otherIndex++) {
                SettlementBuildPiece other = values[otherIndex];
                if (current.getKey().equals(other.getKey())) {
                    return "duplicate stable definition key " + current.getKey() + ".";
                }
                if (current.getObjectId() == other.getObjectId()
                        && current.getObjectType() == other.getObjectType()) {
                    return "duplicate object/type definition identity for "
                            + current.getKey() + " and " + other.getKey() + ".";
                }
            }
        }
        return null;
    }
}
