package com.rs.game.player.content.construction;

/**
 * Stable settlement-only resource identities.
 *
 * These are not normal RuneScape inventory/bank items. They belong only to
 * SettlementState storage and later settlement production chains.
 */
public enum SettlementResource {

    WOOD("wood", "Wood"),
    FOOD("food", "Food"),
    STONE("stone", "Stone"),
    BASIC_ORE("basic-ore", "Basic ore");

    private final String key;
    private final String displayName;

    SettlementResource(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SettlementResource forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementResource resource : values()) {
            if (resource.key.equals(key)) {
                return resource;
            }
        }
        return null;
    }
}
