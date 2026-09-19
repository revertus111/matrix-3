package com.rs.game.player.content.construction;

/**
 * Stable one-time settlement progression milestones.
 */
public enum SettlementMilestone {

    STARTER_SHELTER("starter-shelter", "Starter shelter");

    private final String key;
    private final String displayName;

    SettlementMilestone(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SettlementMilestone forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementMilestone milestone : values()) {
            if (milestone.key.equals(key)) {
                return milestone;
            }
        }
        return null;
    }
}
