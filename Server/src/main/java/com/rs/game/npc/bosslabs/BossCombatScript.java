package com.rs.game.npc.bosslabs;

import java.util.List;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.Graphics;
import com.rs.game.World;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.CombatScript;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.utils.Utils;

/**
 * Matrix3 CombatScript adapter for registered BossLabs definitions.
 *
 * Matrix3 remains authoritative for target validation, movement, combat delay,
 * accuracy, hit application, death, respawn, and drops. This adapter only
 * resolves the active BossLabs phase/attack and expresses it through existing
 * Matrix3 CombatScript helpers.
 */
public final class BossCombatScript extends CombatScript {

	public static final BossCombatScript INSTANCE = new BossCombatScript();

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

		List<BossAttackDefinition> attacks = phase.getAttacks();
		BossAttackDefinition attack = attacks.get(Utils.random(attacks.size()));
		return executeAttack(npc, target, attack);
	}

	private int executeAttack(NPC npc, Entity target, BossAttackDefinition attack) {
		NPCCombatDefinitions defs = npc.getCombatDefinitions();
		int attackStyle = attack.getCombatStyle();

		int animationId = attack.getAnimationId() == BossAttackDefinition.USE_NPC_DEFAULT
				? defs.getAttackEmote() : attack.getAnimationId();
		int graphicId = attack.getGraphicId() == BossAttackDefinition.USE_NPC_DEFAULT
				? defs.getAttackGfx() : attack.getGraphicId();
		int projectileId = attack.getProjectileId() == BossAttackDefinition.USE_NPC_DEFAULT
				? defs.getAttackProjectile() : attack.getProjectileId();

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

		if (graphicId != -1)
			npc.setNextGraphics(new Graphics(graphicId, 0, attackStyle == NPCCombatDefinitions.RANGE ? 100 : 0));
		if (animationId != -1)
			npc.setNextAnimation(new Animation(animationId));

		return attack.usesNpcCombatDelay() ? npc.getAttackSpeed() : attack.getCombatDelayOverride();
	}
}
