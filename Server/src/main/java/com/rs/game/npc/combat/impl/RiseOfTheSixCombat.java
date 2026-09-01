package com.rs.game.npc.combat.impl;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.Graphics;
import com.rs.game.Projectile;
import com.rs.game.World;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.CombatScript;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.game.npc.rots.RiseOfTheSixBrother;
import com.rs.game.npc.rots.RiseOfTheSixBrother.Brother;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.utils.Utils;

/**
 * Empowered-brother auto attacks and first verified-static special hooks for
 * Rise of the Six. Matrix3 NPCCombat remains the target/movement/delay owner.
 */
public final class RiseOfTheSixCombat extends CombatScript {

	@Override
	public Object[] getKeys() {
		return new Object[] { 18538, 18539, 18540, 18541, 18542, 18543, 18544, 18545 };
	}

	@Override
	public int attack(NPC npc, Entity target) {
		Brother brother = Brother.forNpcId(npc.getId());
		RiseOfTheSixBrother rotsBrother = npc instanceof RiseOfTheSixBrother ? (RiseOfTheSixBrother) npc : null;

		if (rotsBrother != null) {
			if (rotsBrother.isDharokCharging() || rotsBrother.isToragWhacking())
				return 1;
			if (brother == Brother.TORAG && rotsBrother.tryStartToragWhack(target))
				return 7;
		}

		if (brother == Brother.AHRIM)
			return mageAttack(npc, target);
		if (brother == Brother.KARIL)
			return rangeAttack(npc, target);
		return meleeAttack(npc, target, brother);
	}

	private int meleeAttack(NPC npc, Entity target, Brother brother) {
		npc.setNextAnimation(new Animation(npc.getCombatDefinitions().getAttackEmote()));
		int maxHit = getMeleeMaxHit(npc, brother);
		int damage = getMaxHit(npc, maxHit, NPCCombatDefinitions.MELEE, target);

		if (brother == Brother.GUTHAN && damage != 0 && Utils.random(8) == 0) {
			target.setNextGraphics(new Graphics(398));
			npc.heal(damage);
		}

		delayHit(npc, 0, target, getMeleeHit(npc, damage));
		return brother == Brother.VERAC ? npc.getAttackSpeed() : 7;
	}

	private int getMeleeMaxHit(NPC npc, Brother brother) {
		if (brother != Brother.DHAROK)
			return 3500;
		int hp = npc.getHitpoints();
		if (hp <= 5000)
			return 7000;
		if (hp <= 10000)
			return 6000;
		if (hp <= 20000)
			return 5000;
		if (hp <= 30000)
			return 4000;
		if (hp <= 40000)
			return 3000;
		return 2000;
	}

	private int mageAttack(NPC npc, Entity target) {
		npc.setNextAnimation(new Animation(npc.getId() == 18539 ? 21925 : 18288));
		int damage = getMaxHit(npc, 3000, NPCCombatDefinitions.MAGE, target);
		if (damage != 0 && target instanceof Player && Utils.random(8) == 0) {
			Player targetPlayer = (Player) target;
			int currentLevel = targetPlayer.getSkills().getLevel(Skills.STRENGTH);
			targetPlayer.getSkills().set(Skills.STRENGTH, currentLevel < 5 ? 0 : currentLevel - 5);
		}
		Projectile projectile = World.sendProjectileNew(npc, target, 559, 41, 16, 35, 2, 16, Utils.random(5));
		target.setNextGraphics(new Graphics(377));
		delayHit(npc, Math.max(3, Utils.projectileTimeToCycles(projectile.getEndTime())), target, getMagicHit(npc, damage));
		return 5;
	}

	private int rangeAttack(NPC npc, Entity target) {
		npc.setNextAnimation(new Animation(18232));
		int damage = getMaxHit(npc, 3000, NPCCombatDefinitions.RANGE, target);
		Projectile projectile = World.sendProjectileNew(npc, target, 955, 41, 16, 35, 3, Utils.random(5), 5);
		delayHit(npc, Math.max(3, Utils.projectileTimeToCycles(projectile.getEndTime())), target, getRangeHit(npc, damage));
		return 7;
	}
}
