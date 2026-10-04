package com.rs.game.player.content.construction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable prefab-local logistics port metadata.
 *
 * Local offsets are resolved from the owning placed-piece anchor. Rotation is
 * applied in quarter turns with the placed piece so the port position and facing
 * remain attached to the prefab rather than to a temporary runtime world tile.
 */
public final class SettlementLogisticsPortDefinition {

    private final String portKey;
    private final SettlementLogisticsEndpoint.Direction direction;
    private final int localX;
    private final int localY;
    private final SettlementLogisticsEndpoint.Facing facing;
    private final Set<Integer> itemFilter;

    public SettlementLogisticsPortDefinition(String portKey,
            SettlementLogisticsEndpoint.Direction direction,
            int localX, int localY,
            SettlementLogisticsEndpoint.Facing facing,
            int... acceptedItemIds) {
        if (portKey == null || portKey.trim().length() == 0) {
            throw new IllegalArgumentException("Logistics port key is required.");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Logistics port direction is required.");
        }
        if (facing == null) {
            throw new IllegalArgumentException("Logistics port facing is required.");
        }
        this.portKey = portKey.trim();
        this.direction = direction;
        this.localX = localX;
        this.localY = localY;
        this.facing = facing;

        Set<Integer> filter = new HashSet<Integer>();
        if (acceptedItemIds != null) {
            for (int itemId : acceptedItemIds) {
                if (itemId >= 0) {
                    filter.add(Integer.valueOf(itemId));
                }
            }
        }
        this.itemFilter = Collections.unmodifiableSet(filter);
    }

    public String getPortKey() {
        return portKey;
    }

    public SettlementLogisticsEndpoint.Direction getDirection() {
        return direction;
    }

    public int getLocalX() {
        return localX;
    }

    public int getLocalY() {
        return localY;
    }

    public SettlementLogisticsEndpoint.Facing getFacing() {
        return facing;
    }

    public boolean supportsItem(int itemId) {
        return itemId >= 0
                && (itemFilter.isEmpty() || itemFilter.contains(Integer.valueOf(itemId)));
    }

    public Set<Integer> getItemFilter() {
        return itemFilter;
    }

    public int resolvePlotX(SettlementPlacedPiece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Placed piece is required.");
        }
        return piece.getPlotX() + rotateLocalX(localX, localY, piece.getRotation());
    }

    public int resolvePlotY(SettlementPlacedPiece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Placed piece is required.");
        }
        return piece.getPlotY() + rotateLocalY(localX, localY, piece.getRotation());
    }

    public SettlementLogisticsEndpoint.Facing resolveFacing(SettlementPlacedPiece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Placed piece is required.");
        }
        return facing.rotateClockwise(piece.getRotation());
    }

    private static int rotateLocalX(int x, int y, int rotation) {
        switch (rotation & 0x3) {
        case 1:
            return y;
        case 2:
            return -x;
        case 3:
            return -y;
        default:
            return x;
        }
    }

    private static int rotateLocalY(int x, int y, int rotation) {
        switch (rotation & 0x3) {
        case 1:
            return -x;
        case 2:
            return -y;
        case 3:
            return x;
        default:
            return y;
        }
    }
}
