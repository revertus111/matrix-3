package com.rs.game.player.content.construction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only invariant audit for a real saved SettlementState.
 *
 * This class never mutates the supplied state.
 */
public final class SettlementStateAudit {

    private SettlementStateAudit() {
    }

    public static String run(SettlementState state) {
        if (state == null) {
            return "FAIL: settlement state is null.";
        }

        List<SettlementPlacedPiece> pieces = state.snapshotPieces();
        Set<Long> ids = new HashSet<Long>();
        Set<String> occupiedLayers = new HashSet<String>();

        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null) {
                return "FAIL: saved piece list contains null.";
            }
            if (piece.getPieceId() <= 0L) {
                return "FAIL: invalid piece id " + piece.getPieceId() + ".";
            }
            if (!ids.add(Long.valueOf(piece.getPieceId()))) {
                return "FAIL: duplicate piece id " + piece.getPieceId() + ".";
            }

            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition == null) {
                return "FAIL: unknown definition key " + piece.getDefinitionKey() + ".";
            }

            if (!SettlementState.isValidPlotLocation(
                    piece.getPlotX(), piece.getPlotY(), piece.getPlane())) {
                return "FAIL: piece #" + piece.getPieceId() + " is outside the settlement plot.";
            }

            if (piece.getRotation() < 0 || piece.getRotation() > 3) {
                return "FAIL: piece #" + piece.getPieceId() + " has invalid rotation.";
            }

            String layerKey = piece.getPlotX() + ":" + piece.getPlotY() + ":"
                    + piece.getPlane() + ":" + definition.getObjectType();
            if (!occupiedLayers.add(layerKey)) {
                return "FAIL: same-layer occupancy collision at plot "
                        + piece.getPlotX() + "," + piece.getPlotY() + ".";
            }
        }

        return "PASS: " + pieces.size()
                + " saved piece(s); ids/definitions/locations/rotations/occupancy valid.";
    }
}
