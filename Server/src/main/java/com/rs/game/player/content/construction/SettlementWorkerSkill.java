package com.rs.game.player.content.construction;

/**
 * Stable permanent worker skill identities.
 *
 * The starter Food node uses Food Gathering as a provisional generalist skill.
 * Later Fishing/Hunting/Farming jobs can receive their own stable skills without
 * rewriting existing starter-worker progression.
 */
public enum SettlementWorkerSkill {

    WOODCUTTING("woodcutting", "Woodcutting"),
    FOOD_GATHERING("food-gathering", "Food Gathering"),
    MINING("mining", "Mining"),
    CRAFTING("crafting", "Crafting"),
    HAULING("hauling", "Hauling");

    private final String key;
    private final String displayName;

    SettlementWorkerSkill(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SettlementWorkerSkill forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementWorkerSkill skill : values()) {
            if (skill.key.equalsIgnoreCase(key)) {
                return skill;
            }
        }
        return null;
    }

    public static int getLevelForXp(long xp) {
        long safeXp = Math.max(0L, xp);
        int points = 0;
        for (int level = 1; level <= 99; level++) {
            points += (int) Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0));
            int output = (int) Math.floor(points / 4.0);
            if ((output - 1L) >= safeXp) {
                return level;
            }
        }
        return 99;
    }
}
