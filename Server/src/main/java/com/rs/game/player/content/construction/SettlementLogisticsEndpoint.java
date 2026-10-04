package com.rs.game.player.content.construction;

/**
 * Server-owned logistics boundary used by conveyors and production buildings.
 *
 * Implementations adapt existing persistent owners; this interface never owns a
 * second inventory. INPUT endpoints accept items, OUTPUT endpoints extract items.
 */
public interface SettlementLogisticsEndpoint {

    public enum Direction {
        INPUT,
        OUTPUT
    }

    public enum Facing {
        NORTH,
        EAST,
        SOUTH,
        WEST;

        public Facing rotateClockwise(int quarterTurns) {
            int turns = quarterTurns & 0x3;
            return values()[(ordinal() + turns) & 0x3];
        }
    }

    SettlementLogisticsEndpointRef getRef();

    Direction getDirection();

    int getPlotX();

    int getPlotY();

    int getPlane();

    Facing getFacing();

    boolean supportsItem(int itemId);

    long getAvailableAmount(int itemId);

    long getAvailableCapacity(int itemId);

    boolean canExtract(int itemId, int amount);

    int extract(int itemId, int amount);

    boolean canAccept(int itemId, int amount);

    int accept(int itemId, int amount);
}
