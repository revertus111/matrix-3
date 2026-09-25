package com.rs.game.player.content.construction;

/**
 * Stable per-worker job permissions.
 *
 * These keys are persistent policy only. Runtime AI decides how an allowed job
 * is executed; changing AI behavior must not rewrite saved permissions.
 */
public enum SettlementWorkerJob {

    GATHER_WOOD("gather-wood", "Gather Wood", SettlementResource.WOOD,
            SettlementWorkerSkill.WOODCUTTING, 12),
    GATHER_FOOD("gather-food", "Gather Food", SettlementResource.FOOD,
            SettlementWorkerSkill.FOOD_GATHERING, 12),
    GATHER_STONE("gather-stone", "Gather Stone", SettlementResource.STONE,
            SettlementWorkerSkill.MINING, 12),
    GATHER_BASIC_ORE("gather-basic-ore", "Gather Basic Ore", SettlementResource.BASIC_ORE,
            SettlementWorkerSkill.MINING, 12),
    PROCESS_WOOD("process-wood", "Process Wood", null,
            SettlementWorkerSkill.CRAFTING, 18),
    HAUL("haul", "Haul", null, SettlementWorkerSkill.HAULING, 6);

    private final String key;
    private final String displayName;
    private final SettlementResource resource;
    private final SettlementWorkerSkill skill;
    private final int workerXp;

    SettlementWorkerJob(String key, String displayName, SettlementResource resource,
            SettlementWorkerSkill skill, int workerXp) {
        this.key = key;
        this.displayName = displayName;
        this.resource = resource;
        this.skill = skill;
        this.workerXp = workerXp;
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

    public SettlementWorkerSkill getSkill() {
        return skill;
    }

    public int getWorkerXp() {
        return workerXp;
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
