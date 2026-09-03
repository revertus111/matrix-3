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
import com.rs.game.Hit;
import com.rs.game.Hit.HitLook;
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

		BossEncounterContext encounter = BossEncounterRuntime.getOrCreate(npc);
		if (target instanceof Player)
			encounter.registerParticipant((Player) target);

		BossAttackDefinition attack = selectAttack(npc, definition, phase, encounter);
		if (attack == null)
			return npc.getAttackSpeed();

		Entity resolvedTarget = resolveAttackTarget(npc, target, attack);
		if (resolvedTarget == null)
			return npc.getAttackSpeed();
		if (resolvedTarget instanceof Player)
			encounter.registerParticipant((Player) resolvedTarget);
		return executeAttack(npc, resolvedTarget, attack, encounter);
	}

	private BossAttackDefinition selectAttack(NPC npc, BossDefinition definition, BossPhaseDefinition phase,
			BossEncounterContext encounter) {
		RotationState state;
		synchronized (ROTATION_STATES) {
			state = ROTATION_STATES.get(npc);
			if (state == null) {
				state = new RotationState();
				ROTATION_STATES.put(npc, state);
			}
		}

		synchronized (state) {
			if (state.definition != definition || state.phase != phase) {
				boolean hasTransitionActions = (state.phase != null && !state.phase.getExitActions().isEmpty())
						|| !phase.getEntryActions().isEmpty();
				transitionPhase(npc, state, definition, phase, encounter);
				// Only authored transition presentation consumes an attack opportunity.
				// Empty legacy phase action lists preserve the previous combat timing.
				if (hasTransitionActions)
					return null;
			}

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

	private void transitionPhase(NPC npc, RotationState state, BossDefinition definition, BossPhaseDefinition phase,
			BossEncounterContext encounter) {
		BossPhaseDefinition previousPhase = state.phase;
		if (previousPhase != null)
			executePhaseActions(npc, previousPhase.getExitActions(), encounter);

		state.reset(definition, phase);
		if (!npc.hasFinished() && !npc.isDead())
			executePhaseActions(npc, phase.getEntryActions(), encounter);
	}

	private void executePhaseActions(NPC npc, List<BossPhaseActionDefinition> actions,
			BossEncounterContext encounter) {
		for (BossPhaseActionDefinition action : actions) {
			if (npc.hasFinished() || npc.isDead())
				return;
			if (action.getType() == BossPhaseActionDefinition.PLAY_ANIMATION) {
				npc.setNextAnimation(new Animation(action.getValue()));
			} else if (action.getType() == BossPhaseActionDefinition.PLAY_GRAPHIC) {
				npc.setNextGraphics(new Graphics(action.getValue()));
			} else if (action.getType() == BossPhaseActionDefinition.HEAL_BOSS) {
				npc.heal(action.getValue(), 0, 0, true);
			} else if (action.getType() == BossPhaseActionDefinition.SPAWN_MINIONS) {
				BossEncounterRuntime.spawnMinions(encounter, action.getValue(), action.getQuantity(), action.getRadius());
			}
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

		return currentTarget;
	}

	private boolean isEligibleRandomTarget(NPC npc, Player player, int range) {
		return player != null && player.hasStarted() && !player.hasFinished() && !player.isDead()
				&& player.withinDistance(npc, range);
	}

	private int executeAttack(NPC npc, Entity target, BossAttackDefinition attack, BossEncounterContext encounter) {
		if (attack.hasTilePattern())
			return executeTileAttack(npc, target, attack, encounter);
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

	private int executeTileAttack(final NPC npc, Entity target, final BossAttackDefinition attack,
			final BossEncounterContext encounter) {
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
			impactTileAttack(npc, attack, tiles, encounter);
		} else {
			final long generation = encounter.getGeneration();
			WorldTask impactTask = new WorldTask() {
				@Override
				public void run() {
					encounter.untrackTask(this);
					if (!encounter.isGenerationActive(generation) || npc.hasFinished() || npc.isDead()) {
						stop();
						return;
					}
					impactTileAttack(npc, attack, tiles, encounter);
					stop();
				}
			};
			encounter.trackTask(impactTask);
			WorldTasksManager.schedule(impactTask, Math.max(0, attack.getTelegraphTicks() - 1));
		}

		return Math.max(resolveCombatDelay(npc, attack), attack.getTelegraphTicks() + 1);
	}

	private void impactTileAttack(NPC npc, BossAttackDefinition attack, List<WorldTile> tiles,
			BossEncounterContext encounter) {
		if (attack.getImpactGraphicId() != -1) {
			for (WorldTile tile : tiles)
				World.sendGraphics(npc, new Graphics(attack.getImpactGraphicId()), tile);
		}

		applyTileEffect(npc, attack, tiles, attack.getImpactTileEffectType(),
				attack.getMaxHitOverride(), attack.usesNpcMaxHit(), encounter);

		if (attack.hasLingeringHazard())
			startLingeringHazard(npc, attack, tiles, encounter);
	}

	private void startLingeringHazard(final NPC npc, final BossAttackDefinition attack,
			final List<WorldTile> tiles, final BossEncounterContext encounter) {
		if (attack.getHazardGraphicId() != -1) {
			for (WorldTile tile : tiles)
				World.sendGraphics(npc, new Graphics(attack.getHazardGraphicId()), tile);
		}

		final int duration = attack.getHazardDurationTicks();
		final int interval = attack.getHazardTickInterval();
		final long generation = encounter.getGeneration();
		WorldTask hazardTask = new WorldTask() {
			private int elapsedTicks;

			@Override
			public void run() {
				if (!encounter.isGenerationActive(generation) || npc.hasFinished() || npc.isDead()) {
					encounter.untrackTask(this);
					stop();
					return;
				}

				elapsedTicks += interval;
				if (elapsedTicks > duration) {
					encounter.untrackTask(this);
					stop();
					return;
				}

				if (attack.getHazardGraphicId() != -1) {
					for (WorldTile tile : tiles)
						World.sendGraphics(npc, new Graphics(attack.getHazardGraphicId()), tile);
				}

				applyTileEffect(npc, attack, tiles, attack.getHazardTileEffectType(),
						attack.getHazardMaxHitOverride(), attack.usesNpcHazardMaxHit(), encounter);

				if (elapsedTicks >= duration) {
					encounter.untrackTask(this);
					stop();
				}
			}
		};
		encounter.trackTask(hazardTask);
		WorldTasksManager.schedule(hazardTask, interval - 1, interval - 1);
	}

	private void applyTileEffect(NPC npc, BossAttackDefinition attack, List<WorldTile> tiles,
			int effectType, int amountOverride, boolean useNpcMaxHit, BossEncounterContext encounter) {
		if (effectType == BossAttackDefinition.TILE_EFFECT_DAMAGE_PLAYERS) {
			for (Player player : World.getPlayers()) {
				if (!isEligiblePatternPlayer(player, tiles))
					continue;
				encounter.registerParticipant(player);
				applyPatternHit(npc, attack, player, amountOverride, useNpcMaxHit);
			}
			return;
		}

		int amount = resolveTileEffectAmount(npc, attack, amountOverride, useNpcMaxHit);
		if (amount <= 0)
			return;

		if (effectType == BossAttackDefinition.TILE_EFFECT_HEAL_PLAYERS) {
			for (Player player : World.getPlayers()) {
				if (!isEligiblePatternPlayer(player, tiles))
					continue;
				encounter.registerParticipant(player);
				player.heal(amount, 0, 0, true);
			}
			return;
		}

		if (!occupiesPatternTile(npc, tiles))
			return;

		if (effectType == BossAttackDefinition.TILE_EFFECT_DAMAGE_BOSS) {
			npc.applyHit(new Hit(npc, amount, HitLook.REGULAR_DAMAGE));
		} else if (effectType == BossAttackDefinition.TILE_EFFECT_HEAL_BOSS) {
			npc.heal(amount, 0, 0, true);
		}
	}

	private int resolveTileEffectAmount(NPC npc, BossAttackDefinition attack,
			int amountOverride, boolean useNpcMaxHit) {
		return Math.max(0, useNpcMaxHit ? npc.getMaxHit(attack.getCombatStyle()) : amountOverride);
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

	private boolean occupiesPatternTile(Entity entity, List<WorldTile> tiles) {
		for (WorldTile tile : tiles) {
			if (entity.getPlane() != tile.getPlane())
				continue;
			if (Utils.isOnRange(entity.getX(), entity.getY(), entity.getSize(),
					tile.getX(), tile.getY(), 1, 0))
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

		private void reset(BossDefinition definition, BossPhaseDefinition phase) {
			this.definition = definition;
			this.phase = phase;
			lastAttack = null;
			cooldowns.clear();
		}
	}
}
