package com.rs.game.npc.combat.impl;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.Graphics;
import com.rs.game.Projectile;
import com.rs.game.World;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.CombatScript;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.game.npc.rots.RiseOfTheSixBrother.Brother;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.utils.Utils;

/**
 * Base empowered-brother auto attacks for Rise of the Six.
 *
 * Encounter specials remain custom RoTS mechanics and are deliberately not
 * approximated here. This script only establishes the correct combat family
 * while Matrix3 NPCCombat remains the target/movement/delay authority.
 */
public final class RiseOfTheSixCombat extends CombatScript {

	@Override
	public Object[] getKeys() {
		return new Object[] { 18538, 18539, 18540, 18541, 18542, 18543, 18544, 18545 };
	}

	@Override
	public int attack(NPC npc, Entity target) {
		Brother brother = Brother.forNpcId(npc.getId());
		if (brother == Brother.AHRIM)
			return mageAttack(npc, target);
		if (brother == Brother.KARIL)
			return rangeAttack(npc, target);
		return meleeAttack(npc, target, brother);
	}

	private int meleeAttack(NPC npc, Entity target, Brother brother) {
		NPCCombatDefinitions defs = npc.getCombatDefinitions();
		npc.setNextAnimation(new Animation(defs.getAttackEmote()));
		int damage = getMaxHit(npc, NPCCombatDefinitions.MELEE, target);

		if (brother == Brother.DHAROK && damage != 0 && npc.getMaxHitpoints() > 0) {
			double missingHealth = 1.0 - ((double) npc.getHitpoints() / (double) npc.getMaxHitpoints());
			damage += (int) (missingHealth * 3800.0);
		}
		else if (brother == Brother.GUTHAN && damage != 0 && Utils.random(3) == 0) {
			target.setNextGraphics(new Graphics(398));
			npc.heal(damage);
		}

		delayHit(npc, 0, target, getMeleeHit(npc, damage));
		return npc.getAttackSpeed();
	}

	private int mageAttack(NPC npc, Entity target) {
		NPCCombatDefinitions defs = npc.getCombatDefinitions();
		npc.setNextAnimation(new Animation(defs.getAttackEmote()));
		int damage = getMaxHit(npc, NPCCombatDefinitions.MAGE, target);
		if (damage != 0 && target instanceof Player && Utils.random(8) == 0) {
			target.setNextGraphics(new Graphics(400, 0, 100));
			Player targetPlayer = (Player) target;
			int currentLevel = targetPlayer.getSkills().getLevel(Skills.STRENGTH);
			targetPlayer.getSkills().set(Skills.STRENGTH, currentLevel < 5 ? 0 : currentLevel - 5);
		}
		Projectile projectile = World.sendProjectileNew(target, npc, defs.getAttackProjectile(), 41, 16, 35, 2, 16, Utils.random(5));
		npc.setNextGraphics(new Graphics(defs.getAttackGfx()));
		delayHit(npc, Utils.projectileTimeToCycles(projectile.getEndTime()), target, getMagicHit(npc, damage));
		return npc.getAttackSpeed();
	}

	private int rangeAttack(NPC npc, Entity target) {
		NPCCombatDefinitions defs = npc.getCombatDefinitions();
		npc.setNextAnimation(new Animation(defs.getAttackEmote()));
		int damage = getMaxHit(npc, NPCCombatDefinitions.RANGE, target);
		Projectile projectile = World.sendProjectileNew(npc, target, defs.getAttackProjectile(), 41, 16, 35, 3, Utils.random(5), 5);
		delayHit(npc, Math.max(0, Utils.projectileTimeToCycles(projectile.getEndTime()) - 1), target, getRangeHit(npc, damage));
		return npc.getAttackSpeed();
	}
}
