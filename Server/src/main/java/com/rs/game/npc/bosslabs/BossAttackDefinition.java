package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	public static final int TARGET_CURRENT = 0;
	public static final int TARGET_RANDOM_NEARBY_PLAYER = 1;
	public static final int MAX_TARGET_RANGE = 32;
	public static final int MAX_PATTERN_TILES = 128;
	public static final int MAX_TELEGRAPH_TICKS = 50;
	public static final int MAX_HAZARD_DURATION_TICKS = 100;
	public static final int MAX_HAZARD_TICK_INTERVAL = 50;
	public static final int MAX_ROTATION_WEIGHT = 1000;
	public static final int MAX_COOLDOWN_ATTACKS = 100;

	private final String id;
	private final int combatStyle;
	private final int animationId;
	private final int graphicId;
	private final int projectileId;
	private final int maxHitOverride;
	private final int combatDelayOverride;
	private final int telegraphGraphicId;
	private final int impactGraphicId;
	private final int telegraphTicks;
	private final List<BossTileOffset> tilePattern;
	private final int hazardGraphicId;
	private final int hazardDurationTicks;
	private final int hazardTickInterval;
	private final int hazardMaxHitOverride;
	private final int targetMode;
	private final int targetRange;
	private final int rotationWeight;
	private final int cooldownAttacks;
	private final boolean allowImmediateRepeat;

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride) {
		this(id, combatStyle, animationId, graphicId, projectileId, maxHitOverride, combatDelayOverride,
				-1, -1, 0, Collections.<BossTileOffset>emptyList());
	}

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride, int telegraphGraphicId, int impactGraphicId,
			int telegraphTicks, List<BossTileOffset> tilePattern) {
		this(id, combatStyle, animationId, graphicId, projectileId, maxHitOverride, combatDelayOverride,
				telegraphGraphicId, impactGraphicId, telegraphTicks, tilePattern, -1, 0, 1, -1);
	}

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride, int telegraphGraphicId, int impactGraphicId,
			int telegraphTicks, List<BossTileOffset> tilePattern, int hazardGraphicId,
			int hazardDurationTicks, int hazardTickInterval, int hazardMaxHitOverride) {
		this(id, combatStyle, animationId, graphicId, projectileId, maxHitOverride, combatDelayOverride,
				telegraphGraphicId, impactGraphicId, telegraphTicks, tilePattern, hazardGraphicId,
				hazardDurationTicks, hazardTickInterval, hazardMaxHitOverride, TARGET_CURRENT, 14);
	}

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride, int telegraphGraphicId, int impactGraphicId,
			int telegraphTicks, List<BossTileOffset> tilePattern, int hazardGraphicId,
			int hazardDurationTicks, int hazardTickInterval, int hazardMaxHitOverride,
			int targetMode, int targetRange) {
		this(id, combatStyle, animationId, graphicId, projectileId, maxHitOverride, combatDelayOverride,
				telegraphGraphicId, impactGraphicId, telegraphTicks, tilePattern, hazardGraphicId,
				hazardDurationTicks, hazardTickInterval, hazardMaxHitOverride, targetMode, targetRange,
				1, 0, true);
	}

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride, int telegraphGraphicId, int impactGraphicId,
			int telegraphTicks, List<BossTileOffset> tilePattern, int hazardGraphicId,
			int hazardDurationTicks, int hazardTickInterval, int hazardMaxHitOverride,
			int targetMode, int targetRange, int rotationWeight, int cooldownAttacks,
			boolean allowImmediateRepeat) {
		if (id == null || id.trim().isEmpty())
			throw new IllegalArgumentException("Boss attack id must not be blank.");
		if (combatStyle != NPCCombatDefinitions.MELEE && combatStyle != NPCCombatDefinitions.RANGE
				&& combatStyle != NPCCombatDefinitions.MAGE)
			throw new IllegalArgumentException("Unsupported NPC combat style: " + combatStyle);
		if (maxHitOverride < USE_NPC_DEFAULT)
			throw new IllegalArgumentException("maxHitOverride must be -1 or greater.");
		if (combatDelayOverride < USE_NPC_DEFAULT)
			throw new IllegalArgumentException("combatDelayOverride must be -1 or greater.");
		if (telegraphGraphicId < -1)
			throw new IllegalArgumentException("telegraphGraphicId must be -1 or greater.");
		if (impactGraphicId < -1)
			throw new IllegalArgumentException("impactGraphicId must be -1 or greater.");
		if (telegraphTicks < 0 || telegraphTicks > MAX_TELEGRAPH_TICKS)
			throw new IllegalArgumentException("telegraphTicks must be between 0 and " + MAX_TELEGRAPH_TICKS + ".");
		if (hazardGraphicId < -1)
			throw new IllegalArgumentException("hazardGraphicId must be -1 or greater.");
		if (hazardDurationTicks < 0 || hazardDurationTicks > MAX_HAZARD_DURATION_TICKS)
			throw new IllegalArgumentException("hazardDurationTicks must be between 0 and " + MAX_HAZARD_DURATION_TICKS + ".");
		if (hazardTickInterval < 1 || hazardTickInterval > MAX_HAZARD_TICK_INTERVAL)
			throw new IllegalArgumentException("hazardTickInterval must be between 1 and " + MAX_HAZARD_TICK_INTERVAL + ".");
		if (hazardMaxHitOverride < USE_NPC_DEFAULT)
			throw new IllegalArgumentException("hazardMaxHitOverride must be -1 or greater.");
		if (hazardDurationTicks > 0 && hazardTickInterval > hazardDurationTicks)
			throw new IllegalArgumentException("hazardTickInterval must not exceed hazardDurationTicks when a hazard is enabled.");
		if (targetMode != TARGET_CURRENT && targetMode != TARGET_RANDOM_NEARBY_PLAYER)
			throw new IllegalArgumentException("Unsupported BossLabs target mode: " + targetMode);
		if (targetRange < 1 || targetRange > MAX_TARGET_RANGE)
			throw new IllegalArgumentException("targetRange must be between 1 and " + MAX_TARGET_RANGE + ".");
		if (rotationWeight < 1 || rotationWeight > MAX_ROTATION_WEIGHT)
			throw new IllegalArgumentException("rotationWeight must be between 1 and " + MAX_ROTATION_WEIGHT + ".");
		if (cooldownAttacks < 0 || cooldownAttacks > MAX_COOLDOWN_ATTACKS)
			throw new IllegalArgumentException("cooldownAttacks must be between 0 and " + MAX_COOLDOWN_ATTACKS + ".");

		List<BossTileOffset> safePattern = tilePattern == null
				? Collections.<BossTileOffset>emptyList() : tilePattern;
		if (safePattern.size() > MAX_PATTERN_TILES)
			throw new IllegalArgumentException("Boss attack tile pattern exceeds " + MAX_PATTERN_TILES + " tiles.");
		Set<BossTileOffset> unique = new HashSet<BossTileOffset>();
		for (BossTileOffset tile : safePattern) {
			if (tile == null)
				throw new IllegalArgumentException("Boss attack tile pattern must not contain null entries.");
			if (!unique.add(tile))
				throw new IllegalArgumentException("Boss attack tile pattern contains duplicate offsets.");
		}
		if (hazardDurationTicks > 0 && safePattern.isEmpty())
			throw new IllegalArgumentException("Lingering hazards require at least one tile pattern offset.");

		this.id = id;
		this.combatStyle = combatStyle;
		this.animationId = animationId;
		this.graphicId = graphicId;
		this.projectileId = projectileId;
		this.maxHitOverride = maxHitOverride;
		this.combatDelayOverride = combatDelayOverride;
		this.telegraphGraphicId = telegraphGraphicId;
		this.impactGraphicId = impactGraphicId;
		this.telegraphTicks = telegraphTicks;
		this.tilePattern = Collections.unmodifiableList(new ArrayList<BossTileOffset>(safePattern));
		this.hazardGraphicId = hazardGraphicId;
		this.hazardDurationTicks = hazardDurationTicks;
		this.hazardTickInterval = hazardTickInterval;
		this.hazardMaxHitOverride = hazardMaxHitOverride;
		this.targetMode = targetMode;
		this.targetRange = targetRange;
		this.rotationWeight = rotationWeight;
		this.cooldownAttacks = cooldownAttacks;
		this.allowImmediateRepeat = allowImmediateRepeat;
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

	public int getTelegraphGraphicId() {
		return telegraphGraphicId;
	}

	public int getImpactGraphicId() {
		return impactGraphicId;
	}

	public int getTelegraphTicks() {
		return telegraphTicks;
	}

	public List<BossTileOffset> getTilePattern() {
		return tilePattern;
	}

	public boolean hasTilePattern() {
		return !tilePattern.isEmpty();
	}

	public int getHazardGraphicId() {
		return hazardGraphicId;
	}

	public int getHazardDurationTicks() {
		return hazardDurationTicks;
	}

	public int getHazardTickInterval() {
		return hazardTickInterval;
	}

	public int getHazardMaxHitOverride() {
		return hazardMaxHitOverride;
	}

	public boolean usesNpcHazardMaxHit() {
		return hazardMaxHitOverride == USE_NPC_DEFAULT;
	}

	public boolean hasLingeringHazard() {
		return hazardDurationTicks > 0 && !tilePattern.isEmpty();
	}

	public int getTargetMode() {
		return targetMode;
	}

	public int getTargetRange() {
		return targetRange;
	}

	public boolean usesRandomNearbyPlayerTarget() {
		return targetMode == TARGET_RANDOM_NEARBY_PLAYER;
	}

	public int getRotationWeight() {
		return rotationWeight;
	}

	public int getCooldownAttacks() {
		return cooldownAttacks;
	}

	public boolean isImmediateRepeatAllowed() {
		return allowImmediateRepeat;
	}
}
