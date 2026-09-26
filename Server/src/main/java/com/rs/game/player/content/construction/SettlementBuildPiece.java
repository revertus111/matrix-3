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
    RAIL_ASSET_4770("rail-asset-4770", "Rail asset 4770", SettlementBuildRole.RAIL, 4770, 22,
            null, 0L, 4.0),
    RAIL_ASSET_4796("rail-asset-4796", "Rail asset 4796", SettlementBuildRole.RAIL, 4796, 22,
            null, 0L, 4.0),
    RAIL_ASSET_14500("rail-asset-14500", "Rail asset 14500", SettlementBuildRole.RAIL, 14500, 22,
            null, 0L, 4.0),
    RAIL_ASSET_14501("rail-asset-14501", "Rail asset 14501", SettlementBuildRole.RAIL, 14501, 22,
            null, 0L, 4.0),
    RAIL_ASSET_14502("rail-asset-14502", "Rail asset 14502", SettlementBuildRole.RAIL, 14502, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46352("rail-asset-46352", "Rail asset 46352", SettlementBuildRole.RAIL, 46352, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46354("rail-asset-46354", "Rail asset 46354", SettlementBuildRole.RAIL, 46354, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46355("rail-asset-46355", "Rail asset 46355", SettlementBuildRole.RAIL, 46355, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46356("rail-asset-46356", "Rail asset 46356", SettlementBuildRole.RAIL, 46356, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46357("rail-asset-46357", "Rail asset 46357", SettlementBuildRole.RAIL, 46357, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46358("rail-asset-46358", "Rail asset 46358", SettlementBuildRole.RAIL, 46358, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46359("rail-asset-46359", "Rail asset 46359", SettlementBuildRole.RAIL, 46359, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46361("rail-asset-46361", "Rail asset 46361", SettlementBuildRole.RAIL, 46361, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46363("rail-asset-46363", "Rail asset 46363", SettlementBuildRole.RAIL, 46363, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46364("rail-asset-46364", "Rail asset 46364", SettlementBuildRole.RAIL, 46364, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46365("rail-asset-46365", "Rail asset 46365", SettlementBuildRole.RAIL, 46365, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46366("rail-asset-46366", "Rail asset 46366", SettlementBuildRole.RAIL, 46366, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46367("rail-asset-46367", "Rail asset 46367", SettlementBuildRole.RAIL, 46367, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46368("rail-asset-46368", "Rail asset 46368", SettlementBuildRole.RAIL, 46368, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46369("rail-asset-46369", "Rail asset 46369", SettlementBuildRole.RAIL, 46369, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46370("rail-asset-46370", "Rail asset 46370", SettlementBuildRole.RAIL, 46370, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46376("rail-asset-46376", "Rail asset 46376", SettlementBuildRole.RAIL, 46376, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46378("rail-asset-46378", "Rail asset 46378", SettlementBuildRole.RAIL, 46378, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46380("rail-asset-46380", "Rail asset 46380", SettlementBuildRole.RAIL, 46380, 22,
            null, 0L, 4.0),
    RAIL_ASSET_46382("rail-asset-46382", "Rail asset 46382", SettlementBuildRole.RAIL, 46382, 22,
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
