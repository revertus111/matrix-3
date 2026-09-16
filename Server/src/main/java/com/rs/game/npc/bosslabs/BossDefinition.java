package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BossLabs root content definition for one Matrix3 boss.
 *
 * The definition intentionally does not spawn NPCs, select targets, apply
 * damage, or own drops. Those responsibilities remain with Matrix3 runtime
 * systems.
 */
public final class BossDefinition {

	private final String id;
	private final String displayName;
	private final int npcId;
	private final List<BossPhaseDefinition> phases;

	public BossDefinition(String id, String displayName, int npcId, List<BossPhaseDefinition> phases) {
		if (id == null || id.trim().isEmpty())
			throw new IllegalArgumentException("Boss definition id must not be blank.");
		if (displayName == null || displayName.trim().isEmpty())
			throw new IllegalArgumentException("Boss display name must not be blank.");
		if (npcId < 0)
			throw new IllegalArgumentException("npcId must be zero or greater.");
		if (phases == null || phases.isEmpty())
			throw new IllegalArgumentException("Boss definition must contain at least one phase.");
		for (BossPhaseDefinition phase : phases) {
			if (phase == null)
				throw new IllegalArgumentException("Boss phases must not contain null entries.");
		}
		validatePhaseRanges(phases);

		this.id = id;
		this.displayName = displayName;
		this.npcId = npcId;
		this.phases = Collections.unmodifiableList(new ArrayList<BossPhaseDefinition>(phases));
	}

	private static void validatePhaseRanges(List<BossPhaseDefinition> phases) {
		for (int i = 0; i < phases.size(); i++) {
			BossPhaseDefinition phase = phases.get(i);
			for (int j = i + 1; j < phases.size(); j++) {
				BossPhaseDefinition other = phases.get(j);
				if (phase.getMinimumHealthPercent() <= other.getMaximumHealthPercent()
						&& other.getMinimumHealthPercent() <= phase.getMaximumHealthPercent())
					throw new IllegalArgumentException("Boss phase health ranges must not overlap: "
							+ phase.getId() + " and " + other.getId());
			}
		}
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getNpcId() {
		return npcId;
	}

	public List<BossPhaseDefinition> getPhases() {
		return phases;
	}

	public BossPhaseDefinition getPhaseForHealth(int currentHitpoints, int maximumHitpoints) {
		if (maximumHitpoints <= 0)
			throw new IllegalArgumentException("maximumHitpoints must be greater than zero.");

		int clampedCurrent = Math.max(0, Math.min(currentHitpoints, maximumHitpoints));
		int healthPercent = (int) ((clampedCurrent * 100L) / maximumHitpoints);
		for (BossPhaseDefinition phase : phases) {
			if (phase.containsHealthPercent(healthPercent))
				return phase;
		}
		return null;
	}
}
