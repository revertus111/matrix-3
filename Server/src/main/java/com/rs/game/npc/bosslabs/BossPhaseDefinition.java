package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BossLabs content data for one health-based boss phase.
 */
public final class BossPhaseDefinition {

	private final String id;
	private final int minimumHealthPercent;
	private final int maximumHealthPercent;
	private final List<BossAttackDefinition> attacks;

	public BossPhaseDefinition(String id, int minimumHealthPercent, int maximumHealthPercent,
			List<BossAttackDefinition> attacks) {
		if (id == null || id.trim().isEmpty())
			throw new IllegalArgumentException("Boss phase id must not be blank.");
		if (minimumHealthPercent < 0 || minimumHealthPercent > 100)
			throw new IllegalArgumentException("minimumHealthPercent must be between 0 and 100.");
		if (maximumHealthPercent < 0 || maximumHealthPercent > 100)
			throw new IllegalArgumentException("maximumHealthPercent must be between 0 and 100.");
		if (minimumHealthPercent > maximumHealthPercent)
			throw new IllegalArgumentException("minimumHealthPercent must not exceed maximumHealthPercent.");
		if (attacks == null || attacks.isEmpty())
			throw new IllegalArgumentException("Boss phase must contain at least one attack.");
		for (BossAttackDefinition attack : attacks) {
			if (attack == null)
				throw new IllegalArgumentException("Boss phase attacks must not contain null entries.");
		}

		this.id = id;
		this.minimumHealthPercent = minimumHealthPercent;
		this.maximumHealthPercent = maximumHealthPercent;
		this.attacks = Collections.unmodifiableList(new ArrayList<BossAttackDefinition>(attacks));
	}

	public String getId() {
		return id;
	}

	public int getMinimumHealthPercent() {
		return minimumHealthPercent;
	}

	public int getMaximumHealthPercent() {
		return maximumHealthPercent;
	}

	public List<BossAttackDefinition> getAttacks() {
		return attacks;
	}

	public boolean containsHealthPercent(int healthPercent) {
		return healthPercent >= minimumHealthPercent && healthPercent <= maximumHealthPercent;
	}
}
