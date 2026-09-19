package com.rs.game.player.content.construction;

/**
 * Stable persistent worker-need identities.
 */
public enum SettlementWorkerNeed {

    HUNGER("hunger", "Hunger"),
    THIRST("thirst", "Thirst"),
    ENERGY("energy", "Energy");

    private final String key;
    private final String displayName;

    SettlementWorkerNeed(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SettlementWorkerNeed forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementWorkerNeed need : values()) {
            if (need.key.equalsIgnoreCase(key)) {
                return need;
            }
        }
        return null;
    }
}
