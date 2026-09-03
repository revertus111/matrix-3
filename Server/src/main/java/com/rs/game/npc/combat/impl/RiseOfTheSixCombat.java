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
import com.rs.game.npc.rots.RiseOfTheSixDebugLog;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.utils.Utils;

/**
 * Empowered-brother auto attacks and verified-static special hooks for Rise of
 * the Six. Matrix3 NPCCombat remains the target/movement/delay owner outside
 * active RoTS special states.
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
			RiseOfTheSixDebugLog.attach(rotsBrother);
			if (rotsBrother.isSpecialActive()) {
				RiseOfTheSixDebugLog.logCombatWait(rotsBrother, target);
				return 1;
			}
			if (rotsBrother.isMeleeBrother() && rotsBrother.tryStartMeleeSpecial(target)) {
				RiseOfTheSixDebugLog.logSpecialDispatch(rotsBrother, target);
				return 1;
			}
		}

		if (brother == Brother.AHRIM)
			return mageAttack(npc, target, rotsBrother);
		if (brother == Brother.KARIL)
			return rangeAttack(npc, target, rotsBrother);

		int delay = meleeAttack(npc, target, brother, rotsBrother);
		if (rotsBrother != null)
			rotsBrother.noteNormalMeleeAttack();
		return delay;
	}

	private int meleeAttack(NPC npc, Entity target, Brother brother, RiseOfTheSixBrother rotsBrother) {
		int animation = npc.getCombatDefinitions().getAttackEmote();
		npc.setNextAnimation(new Animation(animation));
		int maxHit = getMeleeMaxHit(npc, brother);
		int damage = getMaxHit(npc, maxHit, NPCCombatDefinitions.MELEE, target);
		int healed = 0;

		if (brother == Brother.GUTHAN && damage != 0 && Utils.random(8) == 0) {
			target.setNextGraphics(new Graphics(398));
			npc.heal(damage);
			healed = damage;
		}

		delayHit(npc, 0, target, getMeleeHit(npc, damage));
		int returnDelay = brother == Brother.VERAC ? npc.getAttackSpeed() : 7;
		if (rotsBrother != null)
			RiseOfTheSixDebugLog.logNormalAttack(rotsBrother, target, "MELEE", animation, -1,
					healed > 0 ? 398 : -1, maxHit, damage, 0, returnDelay,
					healed > 0 ? "guthanHeal=" + healed : "");
		return returnDelay;
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

	private int mageAttack(NPC npc, Entity target, RiseOfTheSixBrother rotsBrother) {
		int animation = npc.getId() == 18539 ? 21925 : 18288;
		npc.setNextAnimation(new Animation(animation));
		int damage = getMaxHit(npc, 3000, NPCCombatDefinitions.MAGE, target);
		String extra = "";
		if (damage != 0 && target instanceof Player && Utils.random(8) == 0) {
			Player targetPlayer = (Player) target;
			int currentLevel = targetPlayer.getSkills().getLevel(Skills.STRENGTH);
			int newLevel = currentLevel < 5 ? 0 : currentLevel - 5;
			targetPlayer.getSkills().set(Skills.STRENGTH, newLevel);
			extra = "strengthDrain=" + currentLevel + "->" + newLevel;
		}
		Projectile projectile = World.sendProjectileNew(npc, target, 559, 41, 16, 35, 2, 16, Utils.random(5));
		target.setNextGraphics(new Graphics(377));
		int hitDelay = Math.max(3, Utils.projectileTimeToCycles(projectile.getEndTime()));
		delayHit(npc, hitDelay, target, getMagicHit(npc, damage));
		if (rotsBrother != null)
			RiseOfTheSixDebugLog.logNormalAttack(rotsBrother, target, "MAGE", animation, 559, 377,
					3000, damage, hitDelay, 5, extra);
		return 5;
	}

	private int rangeAttack(NPC npc, Entity target, RiseOfTheSixBrother rotsBrother) {
		int animation = 18232;
		npc.setNextAnimation(new Animation(animation));
		int damage = getMaxHit(npc, 3000, NPCCombatDefinitions.RANGE, target);
		Projectile projectile = World.sendProjectileNew(npc, target, 955, 41, 16, 35, 3, Utils.random(5), 5);
		int hitDelay = Math.max(3, Utils.projectileTimeToCycles(projectile.getEndTime()));
		delayHit(npc, hitDelay, target, getRangeHit(npc, damage));
		if (rotsBrother != null)
			RiseOfTheSixDebugLog.logNormalAttack(rotsBrother, target, "RANGE", animation, 955, -1,
					3000, damage, hitDelay, 7, "");
		return 7;
	}
}
