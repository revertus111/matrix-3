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
	public static final int MAX_PATTERN_TILES = 128;
	public static final int MAX_TELEGRAPH_TICKS = 50;

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

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride) {
		this(id, combatStyle, animationId, graphicId, projectileId, maxHitOverride, combatDelayOverride,
				-1, -1, 0, Collections.<BossTileOffset>emptyList());
	}

	public BossAttackDefinition(String id, int combatStyle, int animationId, int graphicId, int projectileId,
			int maxHitOverride, int combatDelayOverride, int telegraphGraphicId, int impactGraphicId,
			int telegraphTicks, List<BossTileOffset> tilePattern) {
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
}
