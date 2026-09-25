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
            SettlementResource.WOOD, 3L, 12.0),
    WOODEN_WORKBENCH("wooden-workbench", "Wooden workbench",
            SettlementBuildRole.WORKSTATION, 13704, 10,
            SettlementResource.WOOD, 5L, 20.0),
    BASIC_RAIL("basic-rail", "Rail", SettlementBuildRole.RAIL, 46353, 22,
            null, 0L, 4.0),
    RAIL_CURVE_A("rail-curve-a", "Rail curve approach", SettlementBuildRole.RAIL, 46377, 22,
            null, 0L, 4.0),
    RAIL_CURVE_ELBOW("rail-curve-elbow", "Rail curve elbow", SettlementBuildRole.RAIL, 46379, 22,
            null, 0L, 4.0),
    RAIL_CURVE_B("rail-curve-b", "Rail curve exit", SettlementBuildRole.RAIL, 46381, 22,
            null, 0L, 4.0),
    RAIL_LOADER("rail-loader", "Rail Loader", SettlementBuildRole.RAIL_LOADER, 13450, 0,
            null, 0L, 0.0),
    RAIL_UNLOADER("rail-unloader", "Rail Unloader", SettlementBuildRole.RAIL_UNLOADER, 13344, 0,
            null, 0L, 0.0);

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
