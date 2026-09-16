package com.rs.game.npc.bosslabs;

import java.util.Arrays;

/**
 * Read-only BossLabs view of one Matrix3 NPC and its current combat ownership.
 */
public final class BossNpcInspection {

	public enum CombatSource {
		BOSSLABS,
		MATRIX3_SCRIPT,
		MATRIX3_DEFAULT
	}

	private final int npcId;
	private final String name;
	private final int combatLevel;
	private final int size;
	private final int[] modelIds;
	private final int hitpoints;
	private final int attackSpeed;
	private final int attackAnimation;
	private final int defenceAnimation;
	private final int deathAnimation;
	private final int respawnDelay;
	private final int attackGraphic;
	private final int attackProjectile;
	private final boolean aggressive;
	private final int aggressionRange;
	private final boolean poisonImmune;
	private final CombatSource combatSource;
	private final String combatScriptClassName;
	private final BossDefinition bossDefinition;

	public BossNpcInspection(int npcId, String name, int combatLevel, int size, int[] modelIds, int hitpoints,
			int attackSpeed, int attackAnimation, int defenceAnimation, int deathAnimation, int respawnDelay,
			int attackGraphic, int attackProjectile, boolean aggressive, int aggressionRange, boolean poisonImmune,
			CombatSource combatSource, String combatScriptClassName, BossDefinition bossDefinition) {
		this.npcId = npcId;
		this.name = name;
		this.combatLevel = combatLevel;
		this.size = size;
		this.modelIds = modelIds == null ? new int[0] : Arrays.copyOf(modelIds, modelIds.length);
		this.hitpoints = hitpoints;
		this.attackSpeed = attackSpeed;
		this.attackAnimation = attackAnimation;
		this.defenceAnimation = defenceAnimation;
		this.deathAnimation = deathAnimation;
		this.respawnDelay = respawnDelay;
		this.attackGraphic = attackGraphic;
		this.attackProjectile = attackProjectile;
		this.aggressive = aggressive;
		this.aggressionRange = aggressionRange;
		this.poisonImmune = poisonImmune;
		this.combatSource = combatSource;
		this.combatScriptClassName = combatScriptClassName;
		this.bossDefinition = bossDefinition;
	}

	public int getNpcId() {
		return npcId;
	}

	public String getName() {
		return name;
	}

	public int getCombatLevel() {
		return combatLevel;
	}

	public int getSize() {
		return size;
	}

	public int[] getModelIds() {
		return Arrays.copyOf(modelIds, modelIds.length);
	}

	public int getHitpoints() {
		return hitpoints;
	}

	public int getAttackSpeed() {
		return attackSpeed;
	}

	public int getAttackAnimation() {
		return attackAnimation;
	}

	public int getDefenceAnimation() {
		return defenceAnimation;
	}

	public int getDeathAnimation() {
		return deathAnimation;
	}

	public int getRespawnDelay() {
		return respawnDelay;
	}

	public int getAttackGraphic() {
		return attackGraphic;
	}

	public int getAttackProjectile() {
		return attackProjectile;
	}

	public boolean isAggressive() {
		return aggressive;
	}

	public int getAggressionRange() {
		return aggressionRange;
	}

	public boolean isPoisonImmune() {
		return poisonImmune;
	}

	public CombatSource getCombatSource() {
		return combatSource;
	}

	public String getCombatScriptClassName() {
		return combatScriptClassName;
	}

	public String getCombatScriptSimpleName() {
		int separator = combatScriptClassName == null ? -1 : combatScriptClassName.lastIndexOf('.');
		return separator == -1 ? combatScriptClassName : combatScriptClassName.substring(separator + 1);
	}

	public BossDefinition getBossDefinition() {
		return bossDefinition;
	}

	public boolean isBossLabsDefinition() {
		return bossDefinition != null;
	}
}
