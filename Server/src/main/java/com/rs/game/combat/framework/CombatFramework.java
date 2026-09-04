package com.rs.game.combat.framework;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.game.Entity;
import com.rs.game.player.Player;

/**
 * Stable entry point for optional combat extensions.
 *
 * Matrix3 calculates the native combat value first. This class may then apply
 * explicitly enabled framework modifiers. Runtime state is intentionally kept
 * outside Player serialization until a real game-mode selector owns persistence.
 */
public final class CombatFramework {

    private static final Map<Player, CombatProfile> ACTIVE_PROFILES = Collections
            .synchronizedMap(new WeakHashMap<Player, CombatProfile>());
    private static final Map<Player, Double> ACCURACY_MULTIPLIERS = Collections
            .synchronizedMap(new WeakHashMap<Player, Double>());

    public static CombatProfile getProfile(Player player) {
        if (player == null)
            return CombatProfile.STANDARD;
        CombatProfile profile = ACTIVE_PROFILES.get(player);
        return profile == null ? CombatProfile.STANDARD : profile;
    }

    public static void setProfile(Player player, CombatProfile profile) {
        if (player == null)
            return;
        if (profile == null || profile == CombatProfile.STANDARD)
            ACTIVE_PROFILES.remove(player);
        else
            ACTIVE_PROFILES.put(player, profile);
    }

    public static double getAccuracyMultiplier(Player player) {
        if (player == null)
            return 1.0D;
        Double multiplier = ACCURACY_MULTIPLIERS.get(player);
        return multiplier == null ? 1.0D : multiplier.doubleValue();
    }

    public static void setAccuracyMultiplier(Player player, double multiplier) {
        if (player == null)
            return;
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier <= 0.0D)
            throw new IllegalArgumentException("Accuracy multiplier must be finite and greater than zero.");
        if (multiplier == 1.0D)
            ACCURACY_MULTIPLIERS.remove(player);
        else
            ACCURACY_MULTIPLIERS.put(player, Double.valueOf(multiplier));
    }

    public static double resolveHitChance(Player player, Entity target, double baseHitChance) {
        CombatProfile profile = getProfile(player);
        if (!profile.isEnabled(CombatFeature.ACCURACY_MODIFIERS))
            return baseHitChance;
        return baseHitChance * getAccuracyMultiplier(player);
    }

    public static void clearRuntimeState(Player player) {
        if (player == null)
            return;
        ACTIVE_PROFILES.remove(player);
        ACCURACY_MULTIPLIERS.remove(player);
    }

    private CombatFramework() {
    }
}
