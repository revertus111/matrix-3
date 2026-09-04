package com.rs.game.combat.framework;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Selects which optional Combat Framework capabilities are available.
 *
 * Profiles configure capabilities only; Matrix3 remains the combat owner.
 */
public enum CombatProfile {

    STANDARD(),
    POE_STYLE(CombatFeature.ACCURACY_MODIFIERS),
    RTS();

    private final Set<CombatFeature> enabledFeatures;

    private CombatProfile(CombatFeature... features) {
        if (features == null || features.length == 0) {
            enabledFeatures = Collections.emptySet();
            return;
        }
        EnumSet<CombatFeature> enabled = EnumSet.noneOf(CombatFeature.class);
        for (CombatFeature feature : features) {
            if (feature != null)
                enabled.add(feature);
        }
        enabledFeatures = Collections.unmodifiableSet(enabled);
    }

    public boolean isEnabled(CombatFeature feature) {
        return feature != null && enabledFeatures.contains(feature);
    }

    public Set<CombatFeature> getEnabledFeatures() {
        return enabledFeatures;
    }
}
