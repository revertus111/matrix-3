package com.rs.game.player.content.construction;

import java.io.Serializable;

/**
 * Stable persistent identity for one settlement logistics port.
 *
 * Runtime world coordinates are intentionally excluded. Piece/conveyor identity
 * plus a stable port key survives settlement instance rebuilds and dynamic-map
 * relocation.
 */
public final class SettlementLogisticsEndpointRef implements Serializable {

    private static final long serialVersionUID = 2574509566136034342L;

    public enum OwnerType {
        PIECE,
        CONVEYOR
    }

    private final OwnerType ownerType;
    private final long ownerId;
    private final String portKey;

    public SettlementLogisticsEndpointRef(
            OwnerType ownerType, long ownerId, String portKey) {
        if (ownerType == null) {
            throw new IllegalArgumentException("Logistics endpoint owner type is required.");
        }
        if (ownerId <= 0L) {
            throw new IllegalArgumentException("Logistics endpoint owner id must be positive.");
        }
        if (portKey == null || portKey.trim().length() == 0) {
            throw new IllegalArgumentException("Logistics endpoint port key is required.");
        }
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.portKey = portKey.trim();
    }

    public static SettlementLogisticsEndpointRef piece(long pieceId, String portKey) {
        return new SettlementLogisticsEndpointRef(OwnerType.PIECE, pieceId, portKey);
    }

    public static SettlementLogisticsEndpointRef conveyor(long runId, String portKey) {
        return new SettlementLogisticsEndpointRef(OwnerType.CONVEYOR, runId, portKey);
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public String getPortKey() {
        return portKey;
    }

    @Override
    public int hashCode() {
        int result = ownerType.hashCode();
        result = 31 * result + (int) (ownerId ^ (ownerId >>> 32));
        result = 31 * result + portKey.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SettlementLogisticsEndpointRef)) {
            return false;
        }
        SettlementLogisticsEndpointRef other = (SettlementLogisticsEndpointRef) obj;
        return ownerType == other.ownerType
                && ownerId == other.ownerId
                && portKey.equals(other.portKey);
    }

    @Override
    public String toString() {
        return ownerType.name() + ":" + ownerId + "/" + portKey;
    }
}
