package com.rs.game.player.content.construction;

import java.io.Serializable;

/**
 * Persistent item payload carried by one SettlementConveyorRun.
 *
 * Distance is measured in tiles from Point A toward Point B. Transport,
 * spacing and endpoint ownership remain server-authoritative.
 */
public final class SettlementConveyorPayload implements Serializable {

    private static final long serialVersionUID = -6122789959195580757L;

    private final long payloadId;
    private final int itemId;
    private final int amount;
    private double distanceTiles;

    public SettlementConveyorPayload(
            long payloadId, int itemId, int amount, double distanceTiles) {
        this.payloadId = payloadId;
        this.itemId = itemId;
        this.amount = amount;
        this.distanceTiles = distanceTiles;
    }

    public long getPayloadId() {
        return payloadId;
    }

    public int getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public double getDistanceTiles() {
        return distanceTiles;
    }

    void setDistanceTiles(double distanceTiles) {
        this.distanceTiles = distanceTiles;
    }

    boolean normalize(double runLengthTiles) {
        if (payloadId <= 0L || itemId < 0 || amount <= 0
                || Double.isNaN(distanceTiles)
                || Double.isInfinite(distanceTiles)) {
            return false;
        }
        if (distanceTiles < 0.0) {
            distanceTiles = 0.0;
        }
        if (distanceTiles > runLengthTiles) {
            distanceTiles = runLengthTiles;
        }
        return true;
    }
}
