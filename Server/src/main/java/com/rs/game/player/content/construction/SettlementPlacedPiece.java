package com.rs.game.player.content.construction;

import java.io.Serializable;

/**
 * Persistent freeform placement identity.
 *
 * Coordinates are always settlement-plot relative. Dynamic-region/world
 * coordinates must never be serialized into this record.
 */
public final class SettlementPlacedPiece implements Serializable {

    private static final long serialVersionUID = -7041748208075080241L;

    private final long pieceId;
    private final String definitionKey;
    private final int plotX;
    private final int plotY;
    private final int plane;
    private final int rotation;

    public SettlementPlacedPiece(long pieceId, String definitionKey,
            int plotX, int plotY, int plane, int rotation) {
        this.pieceId = pieceId;
        this.definitionKey = definitionKey;
        this.plotX = plotX;
        this.plotY = plotY;
        this.plane = plane;
        this.rotation = rotation & 0x3;
    }

    public long getPieceId() {
        return pieceId;
    }

    public String getDefinitionKey() {
        return definitionKey;
    }

    public int getPlotX() {
        return plotX;
    }

    public int getPlotY() {
        return plotY;
    }

    public int getPlane() {
        return plane;
    }

    public int getRotation() {
        return rotation;
    }

    public SettlementPlacedPiece withPosition(int x, int y, int newPlane) {
        return new SettlementPlacedPiece(pieceId, definitionKey, x, y, newPlane, rotation);
    }

    public SettlementPlacedPiece withRotation(int newRotation) {
        return new SettlementPlacedPiece(pieceId, definitionKey, plotX, plotY, plane, newRotation);
    }
}
