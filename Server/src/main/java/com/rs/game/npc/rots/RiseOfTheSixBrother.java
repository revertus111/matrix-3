package com.rs.game.npc.rots;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.ForceTalk;
import com.rs.game.Graphics;
import com.rs.game.Hit;
import com.rs.game.WorldTile;
import com.rs.game.map.bossInstance.impl.RiseOfTheSixInstance;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;
import com.rs.utils.Utils;

/**
 * Empowered Barrows brother used only by the Rise of the Six encounter.
 *
 * Standard BarrowsBrother is intentionally not reused because RoTS brothers are
 * incapacitated at zero HP and can be restored by the shared shadow bond.
 */
public final class RiseOfTheSixBrother extends NPC {

	private static final long serialVersionUID = 1L;
	private static final int TORAG_RELEASE_DAMAGE = 2500;

	public static enum Brother {
		AHRIM("Ahrim the Blighted", 18538, 18539),
		DHAROK("Dharok the Wretched", 18540, -1),
		GUTHAN("Guthan the Infested", 18541, 18542),
		KARIL("Karil the Tainted", 18543, -1),
		TORAG("Torag the Corrupted", 18544, -1),
		VERAC("Verac the Defiled", 18545, -1);

		private final String displayName;
		private final int npcId;
		private final int alternateNpcId;

		private Brother(String displayName, int npcId, int alternateNpcId) {
			this.displayName = displayName;
			this.npcId = npcId;
			this.alternateNpcId = alternateNpcId;
		}

		public String getDisplayName() {
			return displayName;
		}

		public int getNpcId() {
			return npcId;
		}

		public int getAlternateNpcId() {
			return alternateNpcId;
		}

		public static Brother forNpcId(int npcId) {
			for (Brother brother : values()) {
				if (brother.npcId == npcId || brother.alternateNpcId == npcId)
					return brother;
			}
			return null;
		}
	}

	private final Brother brother;
	private final transient RiseOfTheSixInstance instance;
	private boolean subdued;
	private boolean dharokCharging;
	private int dharokStoredDamage;
	private boolean toragWhacking;
	private int toragReleaseDamage;
	private transient Player toragVictim;

	public RiseOfTheSixBrother(Brother brother, WorldTile tile, RiseOfTheSixInstance instance) {
		super(brother.getNpcId(), tile, -1, true, true);
		this.brother = brother;
		this.instance = instance;
		setBossInstance(instance);
		setForceAgressive(true);
	}

	public Brother getBrother() {
		return brother;
	}

	public boolean isSubdued() {
		return subdued;
	}

	public boolean isDharokCharging() {
		return dharokCharging;
	}

	public boolean isToragWhacking() {
		return toragWhacking;
	}

	@Override
	public void handleIngoingHit(Hit hit) {
		if (hit == null)
			return;

		if (brother == Brother.DHAROK) {
			if (dharokCharging) {
				dharokStoredDamage += Math.max(0, hit.getDamage());
				hit.setDamage(0);
				return;
			}
			if (!subdued && dharokStoredDamage == 0 && isUnderCombat() && Utils.random(40) == 5)
				startDharokCharge();
		}
		else if (brother == Brother.TORAG && toragWhacking) {
			toragReleaseDamage += Math.max(0, hit.getDamage());
			hit.setDamage(0);
			if (toragReleaseDamage >= TORAG_RELEASE_DAMAGE)
				releaseToragVictim(true);
			return;
		}

		super.handleIngoingHit(hit);
	}

	@Override
	public Hit handleOutgoingHit(Hit hit, Entity target) {
		if (hit == null)
			return null;
		if (brother == Brother.DHAROK && !dharokCharging && dharokStoredDamage > 0) {
			hit.setDamage(hit.getDamage() + dharokStoredDamage);
			dharokStoredDamage = 0;
		}
		return hit;
	}

	private void startDharokCharge() {
		if (dharokCharging || subdued || hasFinished())
			return;
		dharokCharging = true;
		dharokStoredDamage = 0;
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		setNextForceTalk(new ForceTalk("Give me everything!"));

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!dharokCharging || subdued || hasFinished())
					return;
				setNextGraphics(new Graphics(4406, 0, 0, 5, true));
				setNextAnimation(new Animation(21940));
			}
		}, 2);

		// Donor behavior holds the charge for about eleven seconds total.
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!dharokCharging)
					return;
				dharokCharging = false;
				setCantFollowUnderCombat(false);
				setForceFollowClose(true);
			}
		}, 18);
	}

	public boolean tryStartToragWhack(Entity target) {
		if (brother != Brother.TORAG || subdued || hasFinished())
			return false;
		if (toragWhacking)
			return true;
		if (!(target instanceof Player) || Utils.random(30) != 1)
			return false;

		final Player victim = (Player) target;
		toragWhacking = true;
		toragReleaseDamage = 0;
		toragVictim = victim;
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		victim.lock();

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isValidToragVictim(victim)) {
					releaseToragVictim(false);
					return;
				setNextAnimation(new Animation(21933));
				victim.setNextAnimation(new Animation(21934));
			}
		}, 2);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!toragWhacking || !isValidToragVictim(victim)) {
					releaseToragVictim(false);
					stop();
					return;
				}
				setNextFaceEntity(victim);
				setNextAnimation(new Animation(21935));
			}
		}, 4, 1);

		/*
		 * Safety release prevents a solo runtime test from being permanently
		 * locked. The authentic teammate damage release remains authoritative;
		 * exact natural timeout behavior is still a fidelity verification item.
		 */
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (toragWhacking && toragVictim == victim)
					releaseToragVictim(false);
			}
		}, 18);
		return true;
	}

	private boolean isValidToragVictim(Player victim) {
		return victim != null && !victim.hasFinished() && !victim.isDead() && !subdued && !hasFinished();
	}

	private void releaseToragVictim(boolean brokenByDamage) {
		Player victim = toragVictim;
		toragVictim = null;
		toragWhacking = false;
		toragReleaseDamage = 0;
		setCantFollowUnderCombat(false);
		setForceFollowClose(true);
		if (victim != null && !victim.hasFinished()) {
			victim.unlock();
			victim.setNextAnimation(new Animation(21938));
			if (brokenByDamage)
				victim.getPackets().sendGameMessage("The assault on Torag breaks you free from his grasp.");
			if (!victim.isDead() && !subdued && !hasFinished())
				setTarget(victim);
		}
	}

	private void resetSpecialState() {
		dharokCharging = false;
		dharokStoredDamage = 0;
		if (toragWhacking || toragVictim != null)
			releaseToragVictim(false);
	}

	@Override
	public void sendDeath(Entity source) {
		if (subdued || hasFinished() || instance == null || instance.isFightComplete())
			return;

		resetSpecialState();
		subdued = true;
		NPCCombatDefinitions defs = getCombatDefinitions();
		resetWalkSteps();
		getCombat().removeTarget();
		if (!isDead())
			setHitpoints(0);
		setCantInteract(true);
		setNextAnimation(new Animation(defs.getDeathEmote()));
		giveXP();
		instance.onBrotherSubdued(this);
	}

	public void revive(int hitpoints) {
		if (!subdued || hasFinished())
			return;
		resetSpecialState();
		reset();
		setHitpoints(Math.min(getMaxHitpoints(), Math.max(1, hitpoints)));
		setCantInteract(false);
		subdued = false;
	}

	@Override
	public void finish() {
		resetSpecialState();
		super.finish();
	}
}
