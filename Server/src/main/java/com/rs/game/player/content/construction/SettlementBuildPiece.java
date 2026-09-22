package com.rs.game.player.content.construction;

/**
 * Stable server-side definitions for the first freeform Construction pieces.
 *
 * Object ids are still provisional art choices. The stable key is what saved
 * settlement state owns, so runtime world coordinates never become identity.
 */
public enum SettlementBuildPiece {

    WOOD_FENCE_TEST("wood-fence-test", "Wooden fence", SettlementBuildRole.WALL, 13450, 0,
            SettlementResource.WOOD, 1L, 4.0),
    FLOOR_DECORATION("floor-decoration", "Floor decoration", SettlementBuildRole.FLOOR, 13684, 22,
            SettlementResource.WOOD, 1L, 4.0),
    BASIC_DOOR("basic-door", "Door", SettlementBuildRole.DOOR, 13344, 0,
            SettlementResource.WOOD, 2L, 8.0),
    BASIC_BED("basic-bed", "Bed", SettlementBuildRole.BED, 14872, 10,
            SettlementResource.WOOD, 3L, 12.0);

    private final String key;
    private final String displayName;
    private final SettlementBuildRole role;
    private final int objectId;
    private final int objectType;
    private final SettlementResource buildResource;
    private final long buildCost;
    private final double constructionXp;

    SettlementBuildPiece(String key, String displayName, SettlementBuildRole role,
            int objectId, int objectType, SettlementResource buildResource,
            long buildCost, double constructionXp) {
        this.key = key;
        this.displayName = displayName;
        this.role = role;
        this.objectId = objectId;
        this.objectType = objectType;
        this.buildResource = buildResource;
        this.buildCost = buildCost;
        this.constructionXp = constructionXp;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SettlementBuildRole getRole() {
        return role;
    }

    public int getObjectId() {
        return objectId;
    }

    public int getObjectType() {
        return objectType;
    }

    public SettlementResource getBuildResource() {
        return buildResource;
    }

    public long getBuildCost() {
        return buildCost;
    }

    public double getConstructionXp() {
        return constructionXp;
    }

    public static SettlementBuildPiece forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementBuildPiece piece : values()) {
            if (piece.key.equals(key)) {
                return piece;
            }
        }
        return null;
    }

    public static SettlementBuildPiece forObject(int objectId, int objectType) {
        for (SettlementBuildPiece piece : values()) {
            if (piece.objectId == objectId && piece.objectType == objectType) {
                return piece;
            }
        }
        return null;
    }
}
