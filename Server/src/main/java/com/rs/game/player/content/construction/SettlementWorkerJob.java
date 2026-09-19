package com.rs.game.player.content.construction;

/**
 * Stable per-worker job permissions.
 *
 * These keys are persistent policy only. Runtime AI decides how an allowed job
 * is executed; changing AI behavior must not rewrite saved permissions.
 */
public enum SettlementWorkerJob {

    GATHER_WOOD("gather-wood", "Gather Wood", SettlementResource.WOOD),
    GATHER_FOOD("gather-food", "Gather Food", SettlementResource.FOOD),
    GATHER_STONE("gather-stone", "Gather Stone", SettlementResource.STONE),
    GATHER_BASIC_ORE("gather-basic-ore", "Gather Basic Ore", SettlementResource.BASIC_ORE),
    HAUL("haul", "Haul", null);

    private final String key;
    private final String displayName;
    private final SettlementResource resource;

    SettlementWorkerJob(String key, String displayName, SettlementResource resource) {
        this.key = key;
        this.displayName = displayName;
        this.resource = resource;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SettlementResource getResource() {
        return resource;
    }

    public boolean isGatheringJob() {
        return resource != null;
    }

    public static SettlementWorkerJob forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementWorkerJob job : values()) {
            if (job.key.equals(key)) {
                return job;
            }
        }
        return null;
    }
}
