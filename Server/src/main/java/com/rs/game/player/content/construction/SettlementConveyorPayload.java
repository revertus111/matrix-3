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
    /*
     * Added in schema v16. Old serialized payloads deserialize false and remain
     * development/test payloads; only true payloads may enter physical storage.
     */
    private boolean physicalInventoryOwned;
    private double distanceTiles;

    public SettlementConveyorPayload(
            long payloadId, int itemId, int amount, double distanceTiles) {
        this(payloadId, itemId, amount, distanceTiles, false);
    }

    public SettlementConveyorPayload(
            long payloadId, int itemId, int amount, double distanceTiles,
            boolean physicalInventoryOwned) {
        this.payloadId = payloadId;
        this.itemId = itemId;
        this.amount = amount;
        this.distanceTiles = distanceTiles;
        this.physicalInventoryOwned = physicalInventoryOwned;
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

    public boolean isPhysicalInventoryOwned() {
        return physicalInventoryOwned;
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
