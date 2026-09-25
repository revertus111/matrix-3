package com.rs.game.player.content.construction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Convenience presets for the existing authoritative Allowed Jobs policy.
 *
 * Presets are not persistent worker roles. Applying one only rewrites the
 * worker's saved Allowed Jobs set; pause, needs, identity and progression are
 * intentionally untouched.
 */
public enum SettlementWorkerRolePreset {

    LUMBERJACK("lumberjack", "Lumberjack",
            SettlementWorkerJob.GATHER_WOOD, SettlementWorkerJob.HAUL),
    FORAGER("forager", "Forager",
            SettlementWorkerJob.GATHER_FOOD, SettlementWorkerJob.HAUL),
    STONE_MINER("stone-miner", "Stone Miner",
            SettlementWorkerJob.GATHER_STONE, SettlementWorkerJob.HAUL),
    ORE_MINER("ore-miner", "Ore Miner",
            SettlementWorkerJob.GATHER_BASIC_ORE, SettlementWorkerJob.HAUL),
    CARPENTER("carpenter", "Carpenter",
            SettlementWorkerJob.PROCESS_WOOD),
    HAULER_ONLY("hauler-only", "Hauler Only",
            SettlementWorkerJob.HAUL),
    IDLE("idle", "Idle");

    private final String key;
    private final String displayName;
    private final Set<SettlementWorkerJob> allowedJobs;

    SettlementWorkerRolePreset(String key, String displayName,
            SettlementWorkerJob... allowedJobs) {
        this.key = key;
        this.displayName = displayName;
        this.allowedJobs = EnumSet.noneOf(SettlementWorkerJob.class);
        if (allowedJobs != null) {
            for (SettlementWorkerJob job : allowedJobs) {
                if (job != null) {
                    this.allowedJobs.add(job);
                }
            }
        }
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void applyTo(SettlementWorkerState worker) {
        if (worker == null) {
            return;
        }
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            worker.setJobAllowed(job, allowedJobs.contains(job));
        }
    }

    public boolean matches(SettlementWorkerState worker) {
        if (worker == null) {
            return false;
        }
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (worker.isJobAllowed(job) != allowedJobs.contains(job)) {
                return false;
            }
        }
        return true;
    }

    public static SettlementWorkerRolePreset forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementWorkerRolePreset preset : values()) {
            if (preset.key.equalsIgnoreCase(key)) {
                return preset;
            }
        }
        return null;
    }

    public static SettlementWorkerRolePreset findMatching(SettlementWorkerState worker) {
        if (worker == null) {
            return null;
        }
        for (SettlementWorkerRolePreset preset : values()) {
            if (preset.matches(worker)) {
                return preset;
            }
        }
        return null;
    }
}
