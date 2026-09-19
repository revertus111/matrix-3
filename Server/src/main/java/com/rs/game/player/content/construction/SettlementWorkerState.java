package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Persistent settlement worker identity/state.
 *
 * Runtime NPC/world coordinates and active action/path state do not belong here.
 * Home coordinates are settlement-plot relative. Allowed Jobs, needs and worker
 * skill XP are persistent progression state and survive instance rebuilds.
 */
public final class SettlementWorkerState implements Serializable {

    private static final long serialVersionUID = 2699219744107299506L;

    public static final int MAX_NEED = 100;
    public static final int CRITICAL_HUNGER = 80;
    public static final int CRITICAL_THIRST = 80;
    public static final int CRITICAL_ENERGY = 20;

    private static final int WORK_HUNGER_COST = 4;
    private static final int WORK_THIRST_COST = 5;
    private static final int WORK_ENERGY_COST = 6;
    private static final int MEAL_RECOVERY = 60;
    private static final int DRINK_RECOVERY = 70;
    private static final int REST_RECOVERY = 70;

    private final long workerId;
    private final String definitionKey;
    private String name;
    private int homePlotX;
    private int homePlotY;
    private int homePlane;
    private Set<String> allowedJobs = new HashSet<String>();

    // Hunger/thirst are pressure values: 0 = satisfied, 100 = critical.
    // Energy is reserve: 100 = rested, 0 = exhausted.
    private int hunger;
    private int thirst;
    private int energy = MAX_NEED;
    private boolean needsInitialized = true;

    // Stable skill keys -> permanent personal XP.
    private Map<String, Long> skillXp = new HashMap<String, Long>();

    public SettlementWorkerState(long workerId, String definitionKey, String name,
            int homePlotX, int homePlotY, int homePlane) {
        this.workerId = workerId;
        this.definitionKey = definitionKey;
        this.name = name;
        this.homePlotX = homePlotX;
        this.homePlotY = homePlotY;
        this.homePlane = homePlane;
    }

    public long getWorkerId() {
        return workerId;
    }

    public String getDefinitionKey() {
        return definitionKey;
    }

    public String getName() {
        return name;
    }

    public int getHomePlotX() {
        return homePlotX;
    }

    public int getHomePlotY() {
        return homePlotY;
    }

    public int getHomePlane() {
        return homePlane;
    }

    public boolean isJobAllowed(SettlementWorkerJob job) {
        normalizeJobs();
        return job != null && allowedJobs.contains(job.getKey());
    }

    public void setJobAllowed(SettlementWorkerJob job, boolean allowed) {
        normalizeJobs();
        if (job == null) {
            return;
        }
        if (allowed) {
            allowedJobs.add(job.getKey());
        } else {
            allowedJobs.remove(job.getKey());
        }
    }

    public Set<String> snapshotAllowedJobKeys() {
        normalizeJobs();
        return new HashSet<String>(allowedJobs);
    }

    public String getAllowedJobsSummary() {
        normalizeJobs();
        StringBuilder summary = new StringBuilder();
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(job.getDisplayName()).append("=")
                    .append(isJobAllowed(job) ? "ON" : "OFF");
        }
        return summary.toString();
    }

    public int getNeed(SettlementWorkerNeed need) {
        normalizeNeeds();
        if (need == null) {
            return 0;
        }
        switch (need) {
        case HUNGER:
            return hunger;
        case THIRST:
            return thirst;
        case ENERGY:
            return energy;
        default:
            return 0;
        }
    }

    public void setNeed(SettlementWorkerNeed need, int value) {
        normalizeNeeds();
        if (need == null) {
            return;
        }
        int clamped = clamp(value);
        switch (need) {
        case HUNGER:
            hunger = clamped;
            break;
        case THIRST:
            thirst = clamped;
            break;
        case ENERGY:
            energy = clamped;
            break;
        default:
            break;
        }
    }

    public void resetNeeds() {
        needsInitialized = true;
        hunger = 0;
        thirst = 0;
        energy = MAX_NEED;
    }

    public void applyWorkCycleCost() {
        normalizeNeeds();
        hunger = clamp(hunger + WORK_HUNGER_COST);
        thirst = clamp(thirst + WORK_THIRST_COST);
        energy = clamp(energy - WORK_ENERGY_COST);
    }

    public boolean needsFood() {
        normalizeNeeds();
        return hunger >= CRITICAL_HUNGER;
    }

    public boolean needsWater() {
        normalizeNeeds();
        return thirst >= CRITICAL_THIRST;
    }

    public boolean needsRest() {
        normalizeNeeds();
        return energy <= CRITICAL_ENERGY;
    }

    public void recoverFromMeal() {
        normalizeNeeds();
        hunger = clamp(hunger - MEAL_RECOVERY);
    }

    public void recoverFromDrink() {
        normalizeNeeds();
        thirst = clamp(thirst - DRINK_RECOVERY);
    }

    public void recoverFromRest() {
        normalizeNeeds();
        energy = clamp(energy + REST_RECOVERY);
    }

    public String getNeedsSummary() {
        normalizeNeeds();
        return "Hunger=" + hunger + "/" + MAX_NEED
                + ", Thirst=" + thirst + "/" + MAX_NEED
                + ", Energy=" + energy + "/" + MAX_NEED;
    }

    public long getSkillXp(SettlementWorkerSkill skill) {
        normalizeSkills();
        if (skill == null) {
            return 0L;
        }
        Long xp = skillXp.get(skill.getKey());
        return xp == null ? 0L : xp.longValue();
    }

    public long addSkillXp(SettlementWorkerSkill skill, long amount) {
        normalizeSkills();
        if (skill == null || amount <= 0L) {
            return 0L;
        }
        long current = getSkillXp(skill);
        long next = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
        skillXp.put(skill.getKey(), Long.valueOf(next));
        return next - current;
    }

    public int getSkillLevel(SettlementWorkerSkill skill) {
        return SettlementWorkerSkill.getLevelForXp(getSkillXp(skill));
    }

    public String getSkillsSummary() {
        normalizeSkills();
        StringBuilder summary = new StringBuilder();
        for (SettlementWorkerSkill skill : SettlementWorkerSkill.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(skill.getDisplayName())
                    .append(" L").append(getSkillLevel(skill))
                    .append(" (").append(getSkillXp(skill)).append(" xp)");
        }
        return summary.toString();
    }

    void normalize(SettlementWorkerDefinition definition) {
        if (definition == null) {
            return;
        }
        if (name == null || name.trim().isEmpty()) {
            name = definition.getDisplayName();
        }
        if (!SettlementState.isValidPlotLocation(homePlotX, homePlotY, homePlane)) {
            homePlotX = definition.getArrivalPlotX();
            homePlotY = definition.getArrivalPlotY();
            homePlane = definition.getArrivalPlane();
        }
        normalizeJobs();
        normalizeNeeds();
        normalizeSkills();
    }

    private void normalizeJobs() {
        if (allowedJobs == null) {
            allowedJobs = new HashSet<String>();
        }
        Iterator<String> iterator = allowedJobs.iterator();
        while (iterator.hasNext()) {
            if (SettlementWorkerJob.forKey(iterator.next()) == null) {
                iterator.remove();
            }
        }
    }

    private void normalizeNeeds() {
        if (!needsInitialized) {
            hunger = 0;
            thirst = 0;
            energy = MAX_NEED;
            needsInitialized = true;
        }
        hunger = clamp(hunger);
        thirst = clamp(thirst);
        energy = clamp(energy);
    }

    private void normalizeSkills() {
        if (skillXp == null) {
            skillXp = new HashMap<String, Long>();
        }
        Iterator<Map.Entry<String, Long>> iterator = skillXp.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (SettlementWorkerSkill.forKey(entry.getKey()) == null
                    || entry.getValue() == null || entry.getValue().longValue() < 0L) {
                iterator.remove();
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_NEED, value));
    }
}
