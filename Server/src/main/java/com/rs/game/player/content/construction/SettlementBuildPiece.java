package com.rs.game.player.content.construction;

/**
 * Stable server-side definitions for the first freeform Construction pieces.
 *
 * Object ids are still provisional art choices. The stable key is what saved
 * settlement state owns, so runtime world coordinates never become identity.
 */
public enum SettlementBuildPiece {

    WOOD_FENCE_TEST("wood-fence-test", "Wooden fence", 13450, 0),
    FLOOR_DECORATION("floor-decoration", "Floor decoration", 13684, 22),
    BASIC_DOOR("basic-door", "Door", 13344, 0);

    private final String key;
    private final String displayName;
    private final int objectId;
    private final int objectType;

    SettlementBuildPiece(String key, String displayName, int objectId, int objectType) {
        this.key = key;
        this.displayName = displayName;
        this.objectId = objectId;
        this.objectType = objectType;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getObjectId() {
        return objectId;
    }

    public int getObjectType() {
        return objectType;
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
