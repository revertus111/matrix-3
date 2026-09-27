package com.rs.game.player.content.construction;

public enum SettlementWorkerBehaviorMode {

    AUTONOMOUS("autonomous", "Autonomous"),
    RALLY_RESTRICTED("rally", "Rally Restricted"),
    DIRECT_ORDERS_ONLY("orders", "Direct Orders Only");

    private final String key;
    private final String displayName;

    SettlementWorkerBehaviorMode(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SettlementWorkerBehaviorMode forKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (SettlementWorkerBehaviorMode mode : values()) {
            if (mode.key.equals(normalized)
                    || mode.name().equalsIgnoreCase(normalized)
                    || mode.displayName.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
