package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Player-owned persistent settlement data.
 *
 * Only plot-relative data is saved here. Runtime instance bounds and projected
 * Matrix3 world coordinates belong to SettlementInstance.
 */
public final class SettlementState implements Serializable {

    private static final long serialVersionUID = -3303744987274861157L;

    public static final int PLOT_TILES = 64;
    public static final int PLOT_PLANE = 0;

    private int schemaVersion = 1;
    private long nextPieceId = 1L;
    private List<SettlementPlacedPiece> pieces = new ArrayList<SettlementPlacedPiece>();

    public synchronized void normalize() {
        if (schemaVersion <= 0) {
            schemaVersion = 1;
        }
        if (pieces == null) {
            pieces = new ArrayList<SettlementPlacedPiece>();
        }
        long highestId = 0L;
        for (SettlementPlacedPiece piece : pieces) {
            if (piece != null && piece.getPieceId() > highestId) {
                highestId = piece.getPieceId();
            }
        }
        if (nextPieceId <= highestId) {
            nextPieceId = highestId + 1L;
        }
        if (nextPieceId <= 0L) {
            nextPieceId = 1L;
        }
    }

    public synchronized int size() {
        normalize();
        return pieces.size();
    }

    public synchronized List<SettlementPlacedPiece> snapshotPieces() {
        normalize();
        return new ArrayList<SettlementPlacedPiece>(pieces);
    }

    public synchronized SettlementPlacedPiece place(SettlementBuildPiece definition,
            int plotX, int plotY, int plane, int rotation) {
        normalize();
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), -1L)) {
            return null;
        }
        SettlementPlacedPiece piece = new SettlementPlacedPiece(
                nextPieceId++, definition.getKey(), plotX, plotY, plane, rotation);
        pieces.add(piece);
        return piece;
    }

    public synchronized SettlementPlacedPiece find(int objectId, int plotX, int plotY, int plane) {
        normalize();
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getObjectId() == objectId) {
                return piece;
            }
        }
        return null;
    }

    public synchronized SettlementPlacedPiece move(long pieceId, int plotX, int plotY, int plane) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), pieceId)) {
            return null;
        }
        SettlementPlacedPiece moved = current.withPosition(plotX, plotY, plane);
        pieces.set(index, moved);
        return moved;
    }

    public synchronized SettlementPlacedPiece rotate(long pieceId, int delta) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0 || (delta != -1 && delta != 1)) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementPlacedPiece rotated = current.withRotation((current.getRotation() + delta) & 0x3);
        pieces.set(index, rotated);
        return rotated;
    }

    public synchronized SettlementPlacedPiece duplicate(long pieceId, int plotX, int plotY, int plane) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), -1L)) {
            return null;
        }
        SettlementPlacedPiece duplicate = new SettlementPlacedPiece(
                nextPieceId++, current.getDefinitionKey(), plotX, plotY, plane, current.getRotation());
        pieces.add(duplicate);
        return duplicate;
    }

    public synchronized SettlementPlacedPiece remove(long pieceId) {
        normalize();
        int index = indexOf(pieceId);
        return index < 0 ? null : pieces.remove(index);
    }

    private int indexOf(long pieceId) {
        for (int index = 0; index < pieces.size(); index++) {
            SettlementPlacedPiece piece = pieces.get(index);
            if (piece != null && piece.getPieceId() == pieceId) {
                return index;
            }
        }
        return -1;
    }

    public static boolean isValidPlotLocation(int plotX, int plotY, int plane) {
        return plane == PLOT_PLANE
                && plotX >= 0 && plotX < PLOT_TILES
                && plotY >= 0 && plotY < PLOT_TILES;
    }

    private boolean isOccupied(int plotX, int plotY, int plane, int objectType, long ignoredPieceId) {
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPieceId() == ignoredPieceId
                    || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getObjectType() == objectType) {
                return true;
            }
        }
        return false;
    }
}
