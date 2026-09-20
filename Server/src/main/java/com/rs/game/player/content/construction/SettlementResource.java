package com.rs.game.player.content.construction;

/**
 * Stable settlement-only resource identities.
 *
 * These are not normal RuneScape inventory/bank items. They belong only to
 * SettlementState storage and later settlement production chains.
 */
public enum SettlementResource {

    WOOD("wood", "Wood", 100),
    FOOD("food", "Food", 100),
    STONE("stone", "Stone", 100),
    BASIC_ORE("basic-ore", "Basic ore", 100);

    private final String key;
    private final String displayName;
    private final int starterStorageCapacity;

    SettlementResource(String key, String displayName, int starterStorageCapacity) {
        this.key = key;
        this.displayName = displayName;
        this.starterStorageCapacity = starterStorageCapacity;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getStarterStorageCapacity() {
        return starterStorageCapacity;
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
