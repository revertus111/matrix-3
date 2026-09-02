package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.Graphics;
import com.rs.game.World;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.CombatScript;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;
import com.rs.utils.Utils;

/**
 * Matrix3 CombatScript adapter for registered BossLabs definitions.
 *
 * Matrix3 remains authoritative for target validation, movement, combat delay,
 * accuracy, hit application, death, respawn, and drops. This adapter only
 * resolves the active BossLabs phase/attack and expresses it through existing
 * Matrix3 CombatScript/world helpers.
 */
public final class BossCombatScript extends CombatScript {

	public static final BossCombatScript INSTANCE = new BossCombatScript();

	private static final Map<NPC, RotationState> ROTATION_STATES =
			Collections.synchronizedMap(new WeakHashMap<NPC, RotationState>());

	private BossCombatScript() {
	}

	@Override
	public Object[] getKeys() {
		return new Object[0];
	}

	@Override
	public int attack(NPC npc, Entity target) {
		BossDefinition definition = BossDefinitionRegistry.get(npc.getId());
		if (definition == null)
			return npc.getAttackSpeed();

		BossPhaseDefinition phase = definition.getPhaseForHealth(npc.getHitpoints(), npc.getMaxHitpoints());
		if (phase == null)
			return npc.getAttackSpeed();

		BossAttackDefinition attack = selectAttack(npc, definition, phase);
		if (attack == null)
			return npc.getAttackSpeed();

		Entity resolvedTarget = resolveAttackTarget(npc, target, attack);
		if (resolvedTarget == null)
			return npc.getAttackSpeed();
		return executeAttack(npc, resolvedTarget, attack);
	}

	private BossAttackDefinition selectAttack(NPC npc, BossDefinition definition, BossPhaseDefinition phase) {
		RotationState state;
		synchronized (ROTATION_STATES) {
			state = ROTATION_STATES.get(npc);
			if (state == null) {
				state = new RotationState(definition, phase);
				ROTATION_STATES.put(npc, state);
			}
		}

		synchronized (state) {
			if (state.definition != definition || state.phase != phase)
				state.reset(definition, phase);

			List<BossAttackDefinition> ready = new ArrayList<BossAttackDefinition>();
			for (BossAttackDefinition attack : phase.getAttacks()) {
				Integer remaining = state.cooldowns.get(attack);
				if (remaining == null || remaining.intValue() <= 0)
					ready.add(attack);
			}

			if (ready.isEmpty()) {
				advanceCooldowns(state);
				return null;
			}

			List<BossAttackDefinition> candidates = ready;
			if (ready.size() > 1 && state.lastAttack != null && !state.lastAttack.isImmediateRepeatAllowed()) {
				List<BossAttackDefinition> alternatives = new ArrayList<BossAttackDefinition>(ready.size() - 1);
				for (BossAttackDefinition attack : ready) {
					if (attack != state.lastAttack)
						alternatives.add(attack);
				}
				if (!alternatives.isEmpty())
					candidates = alternatives;
			}

			BossAttackDefinition selected = selectWeighted(candidates);
			advanceCooldowns(state);
			state.lastAttack = selected;
			if (selected.getCooldownAttacks() > 0)
				state.cooldowns.put(selected, Integer.valueOf(selected.getCooldownAttacks()));
			return selected;
		}
	}

	private BossAttackDefinition selectWeighted(List<BossAttackDefinition> attacks) {
		int totalWeight = 0;
		for (BossAttackDefinition attack : attacks)
			totalWeight += attack.getRotationWeight();

		int roll = Utils.random(totalWeight);
		for (BossAttackDefinition attack : attacks) {
			if (roll < attack.getRotationWeight())
				return attack;
			roll -= attack.getRotationWeight();
		}
		return attacks.get(attacks.size() - 1);
	}

	private void advanceCooldowns(RotationState state) {
		Iterator<Map.Entry<BossAttackDefinition, Integer>> iterator = state.cooldowns.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BossAttackDefinition, Integer> entry = iterator.next();
			int remaining = entry.getValue().intValue();
			if (remaining <= 1)
				iterator.remove();
			else
				entry.setValue(Integer.valueOf(remaining - 1));
		}
	}

	private Entity resolveAttackTarget(NPC npc, Entity currentTarget, BossAttackDefinition attack) {
		if (!attack.usesRandomNearbyPlayerTarget())
			return currentTarget;

		List<Player> alternatePlayers = new ArrayList<Player>();
		for (Player player : World.getPlayers()) {
			if (!isEligibleRandomTarget(npc, player, attack.getTargetRange()))
				continue;
			if (player == currentTarget)
				continue;
			alternatePlayers.add(player);
		}
		if (!alternatePlayers.isEmpty())
			return alternatePlayers.get(Utils.random(alternatePlayers.size()));

		// Solo fights and encounters with no other eligible player retain the
		// authoritative NPCCombat target instead of cancelling the attack.
		return currentTarget;
	}

	private boolean isEligibleRandomTarget(NPC npc, Player player, int range) {
		return player != null && player.hasStarted() && !player.hasFinished() && !player.isDead()
				&& player.withinDistance(npc, range);
	}

	private int executeAttack(NPC npc, Entity target, BossAttackDefinition attack) {
		if (attack.hasTilePattern())
			return executeTileAttack(npc, target, attack);
		return executeSingleTargetAttack(npc, target, attack);
	}

	private int executeSingleTargetAttack(NPC npc, Entity target, BossAttackDefinition attack) {
		NPCCombatDefinitions defs = npc.getCombatDefinitions();
		int attackStyle = attack.getCombatStyle();
		int animationId = resolveAnimation(defs, attack);
		int graphicId = resolveGraphic(defs, attack);
		int projectileId = resolveProjectile(defs, attack);
		int configuredMaxHit = attack.usesNpcMaxHit() ? npc.getMaxHit(attackStyle) : attack.getMaxHitOverride();
		int damage = getMaxHit(npc, configuredMaxHit, attackStyle, target);

		if (attackStyle == NPCCombatDefinitions.MELEE) {
			delayHit(npc, 0, target, getMeleeHit(npc, damage));
		} else {
			if (projectileId == -1 && graphicId == -1 && attackStyle == NPCCombatDefinitions.MAGE)
				projectileId = 2730;

			int delay = 2;
			if (projectileId != -1)
				delay = Utils.projectileTimeToCycles(
						World.sendProjectileNew(npc, target, projectileId, 40, 39, 30, 2, 16, 5).getEndTime()) - 1;

			delayHit(npc, delay, target,
					attackStyle == NPCCombatDefinitions.RANGE ? getRangeHit(npc, damage) : getMagicHit(npc, damage));
		}

		playNpcPresentation(npc, attackStyle, animationId, graphicId);
		return resolveCombatDelay(npc, attack);
	}

	private int executeTileAttack(final NPC npc, Entity target, final BossAttackDefinition attack) {
		NPCCombatDefinitions defs = npc.getCombatDefinitions();
		int animationId = resolveAnimation(defs, attack);
		int graphicId = resolveGraphic(defs, attack);
		int projectileId = resolveProjectile(defs, attack);
		final WorldTile origin = new WorldTile(target);
		final List<WorldTile> tiles = resolvePatternTiles(origin, attack.getTilePattern());

		playNpcPresentation(npc, attack.getCombatStyle(), animationId, graphicId);
		if (projectileId != -1)
			World.sendProjectileNew(npc, origin, projectileId, 40, 0, 30, 2, 16, 5);
		if (attack.getTelegraphGraphicId() != -1) {
			for (WorldTile tile : tiles)
				World.sendGraphics(npc, new Graphics(attack.getTelegraphGraphicId()), tile);
		}

		if (attack.getTelegraphTicks() == 0) {
			impactTileAttack(npc, attack, tiles);
		} else {
			WorldTasksManager.schedule(new WorldTask() {
				@Override
				public void run() {
					if (npc.hasFinished() || npc.isDead())
						return;
					impactTileAttack(npc, attack, tiles);
				}
			}, Math.max(0, attack.getTelegraphTicks() - 1));
		}

		return Math.max(resolveCombatDelay(npc, attack), attack.getTelegraphTicks() + 1);
	}

	private void impactTileAttack(NPC npc, BossAttackDefinition attack, List<WorldTile> tiles) {
		if (attack.getImpactGraphicId() != -1) {
			for (WorldTile tile : tiles)
				World.sendGraphics(npc, new Graphics(attack.getImpactGraphicId()), tile);
		}

		for (Player player : World.getPlayers()) {
			if (!isEligiblePatternPlayer(player, tiles))
				continue;
			applyPatternHit(npc, attack, player, attack.getMaxHitOverride(), attack.usesNpcMaxHit());
		}

		if (attack.hasLingeringHazard())
			startLingeringHazard(npc, attack, tiles);
	}

	private void startLingeringHazard(final NPC npc, final BossAttackDefinition attack, final List<WorldTile> tiles) {
		if (attack.getHazardGraphicId() != -1) {
			for (WorldTile tile : tiles)
				World.sendGraphics(npc, new Graphics(attack.getHazardGraphicId()), tile);
		}

		final int duration = attack.getHazardDurationTicks();
		final int interval = attack.getHazardTickInterval();
		WorldTasksManager.schedule(new WorldTask() {
			private int elapsedTicks;

			@Override
			public void run() {
				if (npc.hasFinished() || npc.isDead()) {
					stop();
					return;
				}

				elapsedTicks += interval;
				if (elapsedTicks > duration) {
					stop();
					return;
				}

				if (attack.getHazardGraphicId() != -1) {
					for (WorldTile tile : tiles)
						World.sendGraphics(npc, new Graphics(attack.getHazardGraphicId()), tile);
				}

				for (Player player : World.getPlayers()) {
					if (!isEligiblePatternPlayer(player, tiles))
						continue;
					applyPatternHit(npc, attack, player, attack.getHazardMaxHitOverride(), attack.usesNpcHazardMaxHit());
				}

				if (elapsedTicks >= duration)
					stop();
			}
		}, interval - 1, interval - 1);
	}

	private boolean isEligiblePatternPlayer(Player player, List<WorldTile> tiles) {
		return player != null && player.hasStarted() && !player.hasFinished() && !player.isDead()
				&& occupiesPatternTile(player, tiles);
	}

	private void applyPatternHit(NPC npc, BossAttackDefinition attack, Player player,
			int maxHitOverride, boolean useNpcMaxHit) {
		int attackStyle = attack.getCombatStyle();
		int configuredMaxHit = useNpcMaxHit ? npc.getMaxHit(attackStyle) : maxHitOverride;
		int damage = getMaxHit(npc, configuredMaxHit, attackStyle, player);
		if (attackStyle == NPCCombatDefinitions.MELEE)
			delayHit(npc, 0, player, getMeleeHit(npc, damage));
		else if (attackStyle == NPCCombatDefinitions.RANGE)
			delayHit(npc, 0, player, getRangeHit(npc, damage));
		else
			delayHit(npc, 0, player, getMagicHit(npc, damage));
	}

	private boolean occupiesPatternTile(Player player, List<WorldTile> tiles) {
		for (WorldTile tile : tiles) {
			if (player.matches(tile))
				return true;
		}
		return false;
	}

	private List<WorldTile> resolvePatternTiles(WorldTile origin, List<BossTileOffset> pattern) {
		List<WorldTile> tiles = new ArrayList<WorldTile>(pattern.size());
		for (BossTileOffset offset : pattern)
			tiles.add(origin.transform(offset.getX(), offset.getY(), 0));
		return tiles;
	}

	private int resolveAnimation(NPCCombatDefinitions defs, BossAttackDefinition attack) {
		return attack.getAnimationId() == BossAttackDefinition.USE_NPC_DEFAULT
				? defs.getAttackEmote() : attack.getAnimationId();
	}

	private int resolveGraphic(NPCCombatDefinitions defs, BossAttackDefinition attack) {
		return attack.getGraphicId() == BossAttackDefinition.USE_NPC_DEFAULT
				? defs.getAttackGfx() : attack.getGraphicId();
	}

	private int resolveProjectile(NPCCombatDefinitions defs, BossAttackDefinition attack) {
		return attack.getProjectileId() == BossAttackDefinition.USE_NPC_DEFAULT
				? defs.getAttackProjectile() : attack.getProjectileId();
	}

	private int resolveCombatDelay(NPC npc, BossAttackDefinition attack) {
		return attack.usesNpcCombatDelay() ? npc.getAttackSpeed() : attack.getCombatDelayOverride();
	}

	private void playNpcPresentation(NPC npc, int attackStyle, int animationId, int graphicId) {
		if (graphicId != -1)
			npc.setNextGraphics(new Graphics(graphicId, 0, attackStyle == NPCCombatDefinitions.RANGE ? 100 : 0));
		if (animationId != -1)
			npc.setNextAnimation(new Animation(animationId));
	}

	private static final class RotationState {
		private BossDefinition definition;
		private BossPhaseDefinition phase;
		private BossAttackDefinition lastAttack;
		private final Map<BossAttackDefinition, Integer> cooldowns =
				new HashMap<BossAttackDefinition, Integer>();

		private RotationState(BossDefinition definition, BossPhaseDefinition phase) {
			reset(definition, phase);
		}

		private void reset(BossDefinition definition, BossPhaseDefinition phase) {
			this.definition = definition;
			this.phase = phase;
			lastAttack = null;
			cooldowns.clear();
		}
	}
}
