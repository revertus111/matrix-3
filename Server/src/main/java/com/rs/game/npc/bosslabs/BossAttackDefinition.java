package com.rs.game.npc.bosslabs;

import com.rs.game.npc.combat.NPCCombatDefinitions;

/**
 * BossLabs content data for one boss attack.
 *
 * This class describes attack presentation/tuning only. Matrix3 combat remains
 * authoritative for target validation, accuracy, damage application, movement,
 * timing, and NPC lifecycle.
 */
public final class BossAttackDefinition {

	public static final int USE_NPC_DEFAULT = -1;

	private final String id;
	private final int combatStyle;
	private final int animationId;
	private final int graphicId;
	private final int projectileId;
	private final int maxHitOverride;
	private final int combatDelayOverride;

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride) {
		if (id == null || id.trim().isEmpty())
			throw new IllegalArgumentException("Boss attack id must not be blank.");
		if (combatStyle != NPCCombatDefinitions.MELEE && combatStyle != NPCCombatDefinitions.RANGE
				&& combatStyle != NPCCombatDefinitions.MAGE)
			throw new IllegalArgumentException("Unsupported NPC combat style: " + combatStyle);
		if (maxHitOverride < USE_NPC_DEFAULT)
			throw new IllegalArgumentException("maxHitOverride must be -1 or greater.");
		if (combatDelayOverride < USE_NPC_DEFAULT)
			throw new IllegalArgumentException("combatDelayOverride must be -1 or greater.");

		this.id = id;
		this.combatStyle = combatStyle;
		this.animationId = animationId;
		this.graphicId = graphicId;
		this.projectileId = projectileId;
		this.maxHitOverride = maxHitOverride;
		this.combatDelayOverride = combatDelayOverride;
	}

	public String getId() {
		return id;
	}

	public int getCombatStyle() {
		return combatStyle;
	}

	public int getAnimationId() {
		return animationId;
	}

	public int getGraphicId() {
		return graphicId;
	}

	public int getProjectileId() {
		return projectileId;
	}

	public int getMaxHitOverride() {
		return maxHitOverride;
	}

	public boolean usesNpcMaxHit() {
		return maxHitOverride == USE_NPC_DEFAULT;
	}

	public int getCombatDelayOverride() {
		return combatDelayOverride;
	}

	public boolean usesNpcCombatDelay() {
		return combatDelayOverride == USE_NPC_DEFAULT;
	}
}
