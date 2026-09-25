package com.rs.game.player.content.construction;

/**
 * Stable settlement-only resource identities.
 *
 * These are not normal RuneScape inventory/bank items. They belong only to
 * SettlementState storage and later settlement production chains.
 */
public enum SettlementResource {

    WOOD("wood", "Wood", 100, true),
    FOOD("food", "Food", 100, true),
    STONE("stone", "Stone", 100, true),
    BASIC_ORE("basic-ore", "Basic ore", 100, true),
    PLANKS("planks", "Planks", 100, false);

    private final String key;
    private final String displayName;
    private final int starterStorageCapacity;
    private final boolean starterResource;

    SettlementResource(String key, String displayName, int starterStorageCapacity,
            boolean starterResource) {
        this.key = key;
        this.displayName = displayName;
        this.starterStorageCapacity = starterStorageCapacity;
        this.starterResource = starterResource;
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

    /**
     * Starter resources are the raw resources supplied by the Phase-1 nodes.
     * Processed resources share SettlementState storage without silently
     * becoming new starter-shelter requirements.
     */
    public boolean isStarterResource() {
        return starterResource;
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
