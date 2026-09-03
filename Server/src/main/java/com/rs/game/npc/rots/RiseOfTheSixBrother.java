package com.rs.game.npc.rots;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.ForceMovement;
import com.rs.game.ForceTalk;
import com.rs.game.Graphics;
import com.rs.game.Hit;
import com.rs.game.Hit.HitLook;
import com.rs.game.Projectile;
import com.rs.game.World;
import com.rs.game.WorldTile;
import com.rs.game.map.bossInstance.impl.RiseOfTheSixInstance;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;
import com.rs.utils.Utils;

/**
 * Empowered Barrows brother used only by the Rise of the Six encounter.
 *
 * Standard BarrowsBrother is intentionally not reused because RoTS brothers use
 * a logical subdued state and can be restored by the shared shadow bond.
 */
public final class RiseOfTheSixBrother extends NPC {

	private static final long serialVersionUID = 1L;

	private static final int SUBDUED_VISIBLE_HITPOINTS = 1;
	private static final int TORAG_RELEASE_DAMAGE = 2500;
	private static final int HURRICANE_ANIMATION = 21941;
	private static final int HURRICANE_PULSES = 10;
	private static final int HURRICANE_RADIUS = 1;
	private static final int WALL_SLAM_RADIUS = 2;
	private static final int WALL_SCAN_DISTANCE = 12;
	private static final int WALL_SLAM_APPROACH_TICKS = 7;
	private static final int WALL_SLAM_LEAP_TICKS = 2;

	private static final int GUTHAN_SPEAR_PROJECTILE = 4411;
	private static final int GUTHAN_SPEAR_THROW_ANIMATION = 21944;
	private static final int GUTHAN_IMPALED_ANIMATION = 21945;
	private static final int GUTHAN_SPEAR_RETRIEVE_ANIMATION = 21947;
	private static final int GUTHAN_BLEED_GFX = 4411;
	private static final int GUTHAN_BLEED_SECONDARY_GFX = 4407;
	private static final int GUTHAN_BLEED_MIN_DAMAGE = 400;
	private static final int GUTHAN_BLEED_MAX_DAMAGE = 500;
	private static final int GUTHAN_RETRIEVE_DAMAGE = 1000;
	private static final int GUTHAN_RETRIEVE_RANGE = 1;

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

	private boolean hurricaning;
	private transient Player hurricaneTarget;
	private boolean wallSlamming;
	private transient WorldTile wallSlamCapturedTile;
	private boolean movementSpecialPreviousRun;

	private boolean guthanImpaleLaunching;
	private boolean guthanSpearAway;
	private boolean guthanRetrievingSpear;
	private int guthanImpaleGeneration;
	private transient Player guthanImpaleVictim;
	private transient Player guthanPrimaryTarget;

	private boolean forceReviveHurricane;
	private int meleeAutosUntilSpecial;
	private int meleeSpecialIndex;

	public RiseOfTheSixBrother(Brother brother, WorldTile tile, RiseOfTheSixInstance instance) {
		super(brother.getNpcId(), tile, -1, true, true);
		this.brother = brother;
		this.instance = instance;
		setBossInstance(instance);
		setForceAgressive(true);
		resetMeleeSpecialRotation(false);
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

	public boolean isHurricaning() {
		return hurricaning;
	}

	public boolean isWallSlamming() {
		return wallSlamming;
	}

	public boolean isGuthanSpearAway() {
		return brother == Brother.GUTHAN && guthanSpearAway;
	}

	public Player getGuthanImpaleVictim() {
		return guthanImpaleVictim;
	}

	public boolean isSpecialActive() {
		return dharokCharging || toragWhacking || hurricaning || wallSlamming
				|| guthanImpaleLaunching || guthanRetrievingSpear;
	}

	public boolean isMeleeBrother() {
		return brother == Brother.DHAROK || brother == Brother.GUTHAN
				|| brother == Brother.TORAG || brother == Brother.VERAC;
	}

	public void noteNormalMeleeAttack() {
		if (!isMeleeBrother() || subdued || isSpecialActive())
			return;
		if (brother == Brother.GUTHAN && guthanSpearAway)
			return;
		if (meleeAutosUntilSpecial > 0)
			meleeAutosUntilSpecial--;
	}

	/**
	 * Starts the next currently implemented melee special. Verac deliberately
	 * does not receive ordinary Hurricane; the only path that can force Verac
	 * into Hurricane is the shared post-revival melee rule.
	 */
	public boolean tryStartMeleeSpecial(Entity target) {
		if (!isMeleeBrother() || subdued || hasFinished() || isSpecialActive()
				|| instance == null || instance.isFightComplete() || meleeAutosUntilSpecial > 0
				|| !(target instanceof Player))
			return false;
		if (brother == Brother.GUTHAN && guthanSpearAway)
			return false;

		if (forceReviveHurricane) {
			boolean started = startHurricane(target);
			if (started) {
				forceReviveHurricane = false;
				meleeAutosUntilSpecial = 3 + Utils.random(3);
				meleeSpecialIndex = getImplementedMeleeSpecialCount() > 1 ? 1 : 0;
			}
			return started;
		}

		int specialCount = getImplementedMeleeSpecialCount();
		if (specialCount <= 0)
			return false;

		int index = meleeSpecialIndex;
		if (index < 0 || index >= specialCount)
			index = Utils.random(specialCount);

		boolean started;
		switch (brother) {
		case DHAROK:
			started = index == 0 ? startHurricane(target)
					: index == 1 ? startDharokCharge() : startWallSlam(target);
			break;
		case TORAG:
			started = index == 0 ? startHurricane(target)
					: index == 1 ? startToragWhack(target) : startWallSlam(target);
			break;
		case GUTHAN:
			started = index == 0 ? startHurricane(target) : startGuthanImpale(target);
			break;
		case VERAC:
			started = startWallSlam(target);
			break;
		default:
			started = false;
			break;
		}

		if (started) {
			meleeSpecialIndex = (index + 1) % specialCount;
			/*
			 * Exact normal-auto spacing is still HYPOTHESIS. This short gate prevents
			 * specials from chaining while classic cadence is being verified.
			 */
			meleeAutosUntilSpecial = 3 + Utils.random(3);
		}
		return started;
	}

	private int getImplementedMeleeSpecialCount() {
		if (brother == Brother.DHAROK || brother == Brother.TORAG)
			return 3;
		if (brother == Brother.GUTHAN)
			return 2;
		if (brother == Brother.VERAC)
			return 1;
		return 0;
	}

	private void resetMeleeSpecialRotation(boolean forceHurricane) {
		if (!isMeleeBrother())
			return;
		forceReviveHurricane = forceHurricane;
		meleeAutosUntilSpecial = forceHurricane ? 0 : 3 + Utils.random(3);
		meleeSpecialIndex = -1;
	}

	@Override
	public void handleIngoingHit(Hit hit) {
		if (hit == null)
			return;

		int incomingDamage = Math.max(0, hit.getDamage());
		if (subdued) {
			RiseOfTheSixDebugLog.event(this, "INCOMING_HIT_SUPPRESSED",
					"damage=" + incomingDamage + " reason=subdued");
			hit.setDamage(0);
			return;
		}

		if (brother == Brother.DHAROK && dharokCharging) {
			dharokStoredDamage += incomingDamage;
			RiseOfTheSixDebugLog.event(this, "GREATEST_AXE_ABSORB",
					"incoming=" + incomingDamage + " storedTotal=" + dharokStoredDamage + " hp=" + getHitpoints());
			hit.setDamage(0);
			return;
		}
		if (brother == Brother.TORAG && toragWhacking) {
			toragReleaseDamage += incomingDamage;
			RiseOfTheSixDebugLog.event(this, "TORAG_RESCUE_DAMAGE",
					"incoming=" + incomingDamage + " accumulated=" + toragReleaseDamage
							+ " threshold=" + TORAG_RELEASE_DAMAGE);
			hit.setDamage(0);
			if (toragReleaseDamage >= TORAG_RELEASE_DAMAGE)
				releaseToragVictim(true);
			return;
		}

		RiseOfTheSixDebugLog.event(this, "INCOMING_HIT",
				"damage=" + incomingDamage + " hpBefore=" + getHitpoints());
		super.handleIngoingHit(hit);
	}

	@Override
	public Hit handleOutgoingHit(Hit hit, Entity target) {
		if (hit == null)
			return null;
		if (brother == Brother.DHAROK && !isSpecialActive() && dharokStoredDamage > 0) {
			int stored = dharokStoredDamage;
			int baseDamage = hit.getDamage();
			hit.setDamage(baseDamage + stored);
			dharokStoredDamage = 0;
			RiseOfTheSixDebugLog.event(this, "GREATEST_AXE_RELEASE",
					"baseDamage=" + baseDamage + " storedDamage=" + stored + " finalDamage=" + hit.getDamage());
		}
		return hit;
	}

	private boolean startDharokCharge() {
		if (brother != Brother.DHAROK || dharokCharging || subdued || hasFinished())
			return false;

		dharokCharging = true;
		dharokStoredDamage = 0;
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		setNextForceTalk(new ForceTalk("Give me everything!"));
		RiseOfTheSixDebugLog.event(this, "GREATEST_AXE_START",
				"forceTalk=Give me everything! animationDelayTicks=2 chargeEndTicks=18");

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!dharokCharging || subdued || hasFinished())
					return;
				setNextAnimation(new Animation(21940));
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "ANIMATION",
						"mechanic=GREATEST_AXE id=21940");
			}
		}, 2);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!dharokCharging)
					return;
				dharokCharging = false;
				setCantFollowUnderCombat(false);
				setForceFollowClose(true);
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "GREATEST_AXE_CHARGE_END",
						"storedDamage=" + dharokStoredDamage);
			}
		}, 18);
		return true;
	}

	private boolean startToragWhack(Entity target) {
		if (brother != Brother.TORAG || toragWhacking || subdued || hasFinished()
				|| !(target instanceof Player))
			return false;

		final Player victim = (Player) target;
		if (!isValidSpecialTarget(victim))
			return false;

		if (instance != null)
			instance.onToragWhackStarted(victim);

		toragWhacking = true;
		toragReleaseDamage = 0;
		toragVictim = victim;
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		victim.lock();
		RiseOfTheSixDebugLog.event(this, "TORAG_WHACK_START",
				"victim=" + victim.getDisplayName() + " openingDelayTicks=2 timeoutTicks=18");

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isValidToragVictim(victim)) {
					releaseToragVictim(false);
					return;
				}
				setNextAnimation(new Animation(21933));
				victim.setNextAnimation(new Animation(21934));
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "TORAG_WHACK_OPEN",
						"toragAnimation=21933 victimAnimation=21934 victim=" + victim.getDisplayName());
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
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "TORAG_PUMMEL",
						"animation=21935 victim=" + victim.getDisplayName()
								+ " rescueDamage=" + toragReleaseDamage + "/" + TORAG_RELEASE_DAMAGE);
			}
		}, 4, 1);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (toragWhacking && toragVictim == victim) {
					RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "TORAG_WHACK_TIMEOUT",
							"victim=" + victim.getDisplayName());
					releaseToragVictim(false);
				}
			}
		}, 18);
		return true;
	}

	private boolean startGuthanImpale(Entity target) {
		if (brother != Brother.GUTHAN || guthanSpearAway || isSpecialActive()
				|| subdued || hasFinished() || !(target instanceof Player))
			return false;

		final Player primaryTarget = (Player) target;
		if (!isValidSpecialTarget(primaryTarget))
			return false;

		final Player victim = selectGuthanImpaleVictim(primaryTarget);
		if (victim == null)
			return false;

		guthanImpaleLaunching = true;
		guthanSpearAway = true;
		guthanRetrievingSpear = false;
		guthanPrimaryTarget = primaryTarget;
		guthanImpaleVictim = victim;
		final int generation = ++guthanImpaleGeneration;

		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		setNextFaceEntity(victim);
		setNextAnimation(new Animation(GUTHAN_SPEAR_THROW_ANIMATION));

		primaryTarget.getPackets().sendGameMessage("Guthan prepares to throw his spear!");
		victim.getPackets().sendGameMessage("Guthan throws his spear at you!");

		Projectile projectile = World.sendProjectileNew(this, victim, GUTHAN_SPEAR_PROJECTILE,
				41, 25, 20, 1, 15, Utils.random(5));
		setNextNPCTransformation(Brother.GUTHAN.getAlternateNpcId());
		int impactDelay = Math.max(1, Utils.projectileTimeToCycles(projectile.getEndTime()));
		RiseOfTheSixDebugLog.event(this, "GUTHAN_IMPALE_START",
				"primary=" + primaryTarget.getDisplayName() + " victim=" + victim.getDisplayName()
						+ " animation=" + GUTHAN_SPEAR_THROW_ANIMATION
						+ " projectile=" + GUTHAN_SPEAR_PROJECTILE
						+ " transform=" + Brother.GUTHAN.getNpcId() + "->" + Brother.GUTHAN.getAlternateNpcId()
						+ " impactDelay=" + impactDelay);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isCurrentGuthanImpale(victim, generation))
					return;
				if (!isValidImpaleVictim(victim)) {
					clearGuthanImpale(false);
					return;
				}

				guthanImpaleLaunching = false;
				setCantFollowUnderCombat(false);
				setForceFollowClose(true);
				victim.lock(3);
				victim.setNextAnimation(new Animation(GUTHAN_IMPALED_ANIMATION));
				if (isValidSpecialTarget(primaryTarget))
					setTarget(primaryTarget);
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "GUTHAN_IMPALE_IMPACT",
						"victim=" + victim.getDisplayName() + " victimAnimation=" + GUTHAN_IMPALED_ANIMATION
								+ " retarget=" + primaryTarget.getDisplayName());
				startGuthanBleedTask(victim, generation);
			}
		}, impactDelay);
		return true;
	}

	private Player selectGuthanImpaleVictim(Player primaryTarget) {
		if (instance == null)
			return null;

		Player selected = null;
		int eligibleCount = 0;
		for (Player player : instance.getPlayers()) {
			if (player == null || player == primaryTarget || !isValidSpecialTarget(player)
					|| player.getPlane() != getPlane()
					|| !instance.isPlayerOnBrotherSide(player, this))
				continue;
			eligibleCount++;
			if (Utils.random(eligibleCount) == 0)
				selected = player;
		}

		if (selected != null)
			return selected;
		return instance.isPlayerOnBrotherSide(primaryTarget, this) ? primaryTarget : null;
	}

	private void startGuthanBleedTask(final Player victim, final int generation) {
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isCurrentGuthanImpale(victim, generation)) {
					stop();
					return;
				}
				if (!isValidImpaleVictim(victim)) {
					clearGuthanImpale(false);
					stop();
					return;
				}
				if (isWithinGuthanRetrieveRange(victim)) {
					startGuthanSpearRetrieval(victim, generation);
					stop();
					return;
				}

				victim.setNextGraphics(new Graphics(GUTHAN_BLEED_GFX, 1, 120, 0, true));
				victim.setNextGraphics(new Graphics(GUTHAN_BLEED_SECONDARY_GFX, 1, 100, 0, true));
				int damage = GUTHAN_BLEED_MIN_DAMAGE
						+ Utils.random(GUTHAN_BLEED_MAX_DAMAGE - GUTHAN_BLEED_MIN_DAMAGE + 1);
				victim.applyHit(new Hit(RiseOfTheSixBrother.this, damage, HitLook.REGULAR_DAMAGE));
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "GUTHAN_BLEED",
						"victim=" + victim.getDisplayName() + " damage=" + damage
								+ " gfx=" + GUTHAN_BLEED_GFX + "," + GUTHAN_BLEED_SECONDARY_GFX);
			}
		}, 1, 1);
	}

	private void startGuthanSpearRetrieval(final Player victim, final int generation) {
		if (!isCurrentGuthanImpale(victim, generation) || guthanRetrievingSpear)
			return;

		guthanRetrievingSpear = true;
		guthanImpaleLaunching = false;
		resetWalkSteps();
		setCantInteract(true);
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		setNextFaceEntity(victim);
		victim.lock(4);
		RiseOfTheSixDebugLog.event(this, "GUTHAN_RETRIEVE_START",
				"victim=" + victim.getDisplayName() + " range=" + GUTHAN_RETRIEVE_RANGE);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isCurrentGuthanImpale(victim, generation))
					return;
				if (!isValidImpaleVictim(victim)) {
					clearGuthanImpale(false);
					return;
				}
				setNextAnimation(new Animation(GUTHAN_SPEAR_RETRIEVE_ANIMATION));
				setNextNPCTransformation(Brother.GUTHAN.getNpcId());
				victim.setNextAnimation(new Animation(GUTHAN_IMPALED_ANIMATION));
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "GUTHAN_RETRIEVE_ANIMATION",
						"animation=" + GUTHAN_SPEAR_RETRIEVE_ANIMATION
								+ " transform=" + Brother.GUTHAN.getAlternateNpcId() + "->" + Brother.GUTHAN.getNpcId()
								+ " victimAnimation=" + GUTHAN_IMPALED_ANIMATION);
			}
		}, 1);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isCurrentGuthanImpale(victim, generation))
					return;
				if (isValidImpaleVictim(victim)) {
					victim.applyHit(new Hit(RiseOfTheSixBrother.this,
							GUTHAN_RETRIEVE_DAMAGE, HitLook.REGULAR_DAMAGE));
					RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "GUTHAN_RETRIEVE_HIT",
							"victim=" + victim.getDisplayName() + " damage=" + GUTHAN_RETRIEVE_DAMAGE);
				}
				clearGuthanImpale(true);
			}
		}, 2);
	}

	private boolean isWithinGuthanRetrieveRange(Player victim) {
		return victim != null && victim.getPlane() == getPlane()
				&& Math.abs(victim.getX() - getX()) <= GUTHAN_RETRIEVE_RANGE
				&& Math.abs(victim.getY() - getY()) <= GUTHAN_RETRIEVE_RANGE;
	}

	private boolean isCurrentGuthanImpale(Player victim, int generation) {
		return brother == Brother.GUTHAN && guthanSpearAway
				&& guthanImpaleVictim == victim && guthanImpaleGeneration == generation
				&& !subdued && !hasFinished();
	}

	private boolean isValidImpaleVictim(Player victim) {
		return isValidSpecialTarget(victim) && victim.getPlane() == getPlane();
	}

	public void onPlayerPummeled(Player victim) {
		if (brother == Brother.GUTHAN && guthanSpearAway && guthanImpaleVictim == victim) {
			RiseOfTheSixDebugLog.event(this, "GUTHAN_AUTO_RETURN",
					"reason=torag-pummel victim=" + (victim == null ? "null" : victim.getDisplayName()));
			clearGuthanImpale(true);
		}
	}

	public void moveForSideEmpowerment(WorldTile destination) {
		if (destination == null || subdued || hasFinished())
			return;
		boolean preserveReviveHurricane = forceReviveHurricane;
		resetSpecialState();
		forceReviveHurricane = preserveReviveHurricane;
		resetWalkSteps();
		getCombat().removeTarget();
		setNextGraphics(new Graphics(4413));
		setNextWorldTile(new WorldTile(destination));
		setCantInteract(false);
		setForceAgressive(true);
		setForceFollowClose(true);
		RiseOfTheSixDebugLog.event(this, "SIDE_EMPOWERMENT_MOVE",
				"gfx=4413 destination=" + destination.getX() + "," + destination.getY() + "," + destination.getPlane());
	}

	private void clearGuthanImpale(boolean retargetPrimary) {
		Player primary = guthanPrimaryTarget;
		Player victim = guthanImpaleVictim;
		RiseOfTheSixDebugLog.event(this, "GUTHAN_IMPALE_CLEAR",
				"retargetPrimary=" + retargetPrimary
						+ " primary=" + (primary == null ? "null" : primary.getDisplayName())
						+ " victim=" + (victim == null ? "null" : victim.getDisplayName()));
		guthanImpaleGeneration++;
		guthanImpaleLaunching = false;
		guthanSpearAway = false;
		guthanRetrievingSpear = false;
		guthanImpaleVictim = null;
		guthanPrimaryTarget = null;
		if (brother == Brother.GUTHAN && getId() != Brother.GUTHAN.getNpcId())
			setNextNPCTransformation(Brother.GUTHAN.getNpcId());
		setCantInteract(false);
		setCantFollowUnderCombat(false);
		setForceFollowClose(true);
		if (retargetPrimary && isValidSpecialTarget(primary))
			setTarget(primary);
	}

	private boolean startHurricane(Entity target) {
		if (!isMeleeBrother() || isSpecialActive() || subdued || hasFinished()
				|| !(target instanceof Player))
			return false;

		final Player victim = (Player) target;
		if (!isValidSpecialTarget(victim))
			return false;

		hurricaning = true;
		hurricaneTarget = victim;
		movementSpecialPreviousRun = getRun();
		setRun(false);
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		setNextFaceEntity(victim);
		setNextAnimation(new Animation(HURRICANE_ANIMATION));
		RiseOfTheSixDebugLog.event(this, "HURRICANE_START",
				"target=" + victim.getDisplayName() + " animation=" + HURRICANE_ANIMATION
						+ " pulses=" + HURRICANE_PULSES + " radius=" + HURRICANE_RADIUS
						+ " previousRun=" + movementSpecialPreviousRun);

		WorldTasksManager.schedule(new WorldTask() {
			private int pulse;

			@Override
			public void run() {
				if (!hurricaning || hurricaneTarget != victim || !isValidSpecialTarget(victim)) {
					endHurricane(victim, false);
					stop();
					return;
				}

				resetWalkSteps();
				calcFollow(victim, true);

				int damage = Math.min(2500, 250 * (pulse + 1));
				RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "HURRICANE_PULSE",
						"pulse=" + (pulse + 1) + "/" + HURRICANE_PULSES + " damage=" + damage
								+ " target=" + victim.getDisplayName());
				hitHurricanePlayers(damage);
				pulse++;

				if (pulse >= HURRICANE_PULSES) {
					endHurricane(victim, true);
					stop();
					return;
				}
			}
		}, 0, 0);
		return true;
	}

	private void hitHurricanePlayers(int damage) {
		if (instance == null)
			return;
		for (Player player : instance.getPlayers()) {
			if (!isValidSpecialTarget(player) || player.getPlane() != getPlane())
				continue;
			if (Math.abs(player.getX() - getX()) > HURRICANE_RADIUS
					|| Math.abs(player.getY() - getY()) > HURRICANE_RADIUS)
				continue;
			RiseOfTheSixDebugLog.event(this, "HURRICANE_HIT",
					"player=" + player.getDisplayName() + " damage=" + damage
							+ " playerTile=" + player.getX() + "," + player.getY() + "," + player.getPlane());
			player.applyHit(new Hit(this, damage, HitLook.REGULAR_DAMAGE));
		}
	}

	private void endHurricane(Player victim, boolean retarget) {
		if (!hurricaning && hurricaneTarget == null)
			return;
		RiseOfTheSixDebugLog.event(this, "HURRICANE_END",
				"retarget=" + retarget + " victim=" + (victim == null ? "null" : victim.getDisplayName()));
		hurricaning = false;
		hurricaneTarget = null;
		restoreMovementSpecialState();
		if (retarget && isValidSpecialTarget(victim)) {
			setNextFaceEntity(victim);
			setTarget(victim);
		}
	}

	private boolean startWallSlam(Entity target) {
		if (!canWallSlam() || isSpecialActive() || subdued || hasFinished()
				|| !(target instanceof Player))
			return false;

		final Player victim = (Player) target;
		if (!isValidSpecialTarget(victim))
			return false;

		final WorldTile capturedTile = new WorldTile(victim);
		final WorldTile wallTile = findWallApproachTile();
		if (wallTile == null)
			return false;

		wallSlamming = true;
		wallSlamCapturedTile = capturedTile;
		movementSpecialPreviousRun = getRun();
		setRun(true);
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);
		setNextFaceWorldTile(wallTile);
		RiseOfTheSixDebugLog.event(this, "WALL_SLAM_START",
				"victim=" + victim.getDisplayName()
						+ " capturedTile=" + capturedTile.getX() + "," + capturedTile.getY() + "," + capturedTile.getPlane()
						+ " wallTile=" + wallTile.getX() + "," + wallTile.getY() + "," + wallTile.getPlane());
		if (!isAtTile(wallTile) && !calcFollow(wallTile, true)) {
			RiseOfTheSixDebugLog.event(this, "WALL_SLAM_PATH_FAIL", "wallTile unreachable");
			endWallSlam(victim, false);
			return false;
		}

		WorldTasksManager.schedule(new WorldTask() {
			private int tick;
			private boolean launched;

			@Override
			public void run() {
				if (!wallSlamming || wallSlamCapturedTile != capturedTile
						|| !isValidSpecialTarget(victim)) {
					endWallSlam(victim, false);
					stop();
					return;
				}

				if (!launched) {
					if (isAtTile(wallTile)) {
						resetWalkSteps();
						setNextFaceWorldTile(capturedTile);
						int attackAnimation = getCombatDefinitions().getAttackEmote();
						setNextAnimation(new Animation(attackAnimation));
						setNextForceMovement(new ForceMovement(new WorldTile(capturedTile),
								WALL_SLAM_LEAP_TICKS, getForceMovementDirection(capturedTile)));
						RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "WALL_SLAM_LAUNCH",
								"animation=" + attackAnimation + " leapTicks=" + WALL_SLAM_LEAP_TICKS
										+ " destination=" + capturedTile.getX() + "," + capturedTile.getY() + "," + capturedTile.getPlane());
						launched = true;
						tick = 0;
						return;
					}

					if (!hasWalkSteps())
						calcFollow(wallTile, true);
					tick++;
					if (tick >= WALL_SLAM_APPROACH_TICKS) {
						RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "WALL_SLAM_APPROACH_TIMEOUT",
								"ticks=" + tick);
						endWallSlam(victim, true);
						stop();
					}
					return;
				}

				tick++;
				if (tick >= WALL_SLAM_LEAP_TICKS) {
					setNextWorldTile(new WorldTile(capturedTile));
					int attackAnimation = getCombatDefinitions().getAttackEmote();
					setNextAnimation(new Animation(attackAnimation));
					RiseOfTheSixDebugLog.event(RiseOfTheSixBrother.this, "WALL_SLAM_IMPACT",
							"animation=" + attackAnimation + " center=" + capturedTile.getX() + ","
									+ capturedTile.getY() + "," + capturedTile.getPlane());
					hitWallSlamPlayers(capturedTile);
					endWallSlam(victim, true);
					stop();
				}
			}
		}, 0, 0);
		return true;
	}

	private boolean canWallSlam() {
		return brother == Brother.DHAROK || brother == Brother.TORAG || brother == Brother.VERAC;
	}

	private boolean isAtTile(WorldTile tile) {
		return tile != null && getPlane() == tile.getPlane()
				&& getX() == tile.getX() && getY() == tile.getY();
	}

	private int getForceMovementDirection(WorldTile destination) {
		int dx = destination.getX() - getX();
		int dy = destination.getY() - getY();
		if (Math.abs(dx) > Math.abs(dy))
			return dx >= 0 ? ForceMovement.EAST : ForceMovement.WEST;
		return dy >= 0 ? ForceMovement.NORTH : ForceMovement.SOUTH;
	}

	private WorldTile findWallApproachTile() {
		int[][] directions = {
				{ 1, 0 },
				{ -1, 0 },
				{ 0, 1 },
				{ 0, -1 }
		};

		WorldTile nearestBlocked = null;
		int nearestBlockedDistance = Integer.MAX_VALUE;

		for (int[] direction : directions) {
			int dx = direction[0];
			int dy = direction[1];
			int moveDirection = Utils.getMoveDirection(dx, dy);
			int x = getX();
			int y = getY();
			int moved = 0;

			while (moved < WALL_SCAN_DISTANCE
					&& World.checkWalkStep(getPlane(), x, y, moveDirection, getSize())) {
				x += dx;
				y += dy;
				moved++;
			}

			boolean blockedAhead = !World.checkWalkStep(getPlane(), x, y, moveDirection, getSize());
			if (blockedAhead && moved < nearestBlockedDistance) {
				nearestBlockedDistance = moved;
				nearestBlocked = new WorldTile(x, y, getPlane());
			}
		}

		return nearestBlocked;
	}

	private void hitWallSlamPlayers(WorldTile capturedTile) {
		if (instance == null || capturedTile == null)
			return;
		for (Player player : instance.getPlayers()) {
			if (!isValidSpecialTarget(player) || player.getPlane() != capturedTile.getPlane())
				continue;
			if (Math.abs(player.getX() - capturedTile.getX()) > WALL_SLAM_RADIUS
					|| Math.abs(player.getY() - capturedTile.getY()) > WALL_SLAM_RADIUS)
				continue;
			int damage = 500 + Utils.random(2501);
			RiseOfTheSixDebugLog.event(this, "WALL_SLAM_HIT",
					"player=" + player.getDisplayName() + " damage=" + damage + " radius=" + WALL_SLAM_RADIUS);
			player.applyHit(new Hit(this, damage, HitLook.REGULAR_DAMAGE));
		}
	}

	private void endWallSlam(Player victim, boolean retarget) {
		if (!wallSlamming && wallSlamCapturedTile == null)
			return;
		RiseOfTheSixDebugLog.event(this, "WALL_SLAM_END",
				"retarget=" + retarget + " victim=" + (victim == null ? "null" : victim.getDisplayName()));
		wallSlamming = false;
		wallSlamCapturedTile = null;
		restoreMovementSpecialState();
		if (retarget && isValidSpecialTarget(victim))
			setTarget(victim);
	}

	private void restoreMovementSpecialState() {
		resetWalkSteps();
		setRun(movementSpecialPreviousRun);
		setCantFollowUnderCombat(false);
		setForceFollowClose(true);
	}

	private boolean isValidSpecialTarget(Player victim) {
		return victim != null && !victim.hasFinished() && !victim.isDead()
				&& instance != null && !instance.isFinished() && instance.isPlayerInside(victim)
				&& !subdued && !hasFinished();
	}

	private boolean isValidToragVictim(Player victim) {
		return isValidSpecialTarget(victim);
	}

	private void releaseToragVictim(boolean brokenByDamage) {
		Player victim = toragVictim;
		int accumulated = toragReleaseDamage;
		toragVictim = null;
		toragWhacking = false;
		toragReleaseDamage = 0;
		setCantFollowUnderCombat(false);
		setForceFollowClose(true);
		RiseOfTheSixDebugLog.event(this, "TORAG_RELEASE",
				"victim=" + (victim == null ? "null" : victim.getDisplayName())
						+ " brokenByDamage=" + brokenByDamage + " accumulatedRescueDamage=" + accumulated
						+ " victimAnimation=21938");
		if (victim != null && !victim.hasFinished()) {
			victim.unlock();
			victim.setNextAnimation(new Animation(21938));
			if (brokenByDamage)
				victim.getPackets().sendGameMessage("The assault on Torag breaks you free from his grasp.");
			if (isValidSpecialTarget(victim))
				setTarget(victim);
		}
	}

	private void resetSpecialState() {
		if (isSpecialActive() || forceReviveHurricane || dharokStoredDamage > 0 || guthanSpearAway)
			RiseOfTheSixDebugLog.event(this, "SPECIAL_STATE_RESET",
					"dharokCharging=" + dharokCharging + " stored=" + dharokStoredDamage
							+ " toragWhacking=" + toragWhacking + " hurricaning=" + hurricaning
							+ " wallSlamming=" + wallSlamming + " guthanSpearAway=" + guthanSpearAway
							+ " forceReviveHurricane=" + forceReviveHurricane);
		forceReviveHurricane = false;

		boolean wasDharokCharging = dharokCharging;
		dharokCharging = false;
		dharokStoredDamage = 0;
		if (wasDharokCharging) {
			setCantFollowUnderCombat(false);
			setForceFollowClose(true);
		}

		if (toragWhacking || toragVictim != null)
			releaseToragVictim(false);

		if (brother == Brother.GUTHAN && (guthanSpearAway || guthanImpaleLaunching
				|| guthanRetrievingSpear || getId() == Brother.GUTHAN.getAlternateNpcId()))
			clearGuthanImpale(false);

		if (hurricaning || wallSlamming) {
			hurricaning = false;
			wallSlamming = false;
			hurricaneTarget = null;
			wallSlamCapturedTile = null;
			restoreMovementSpecialState();
		}
		else {
			hurricaneTarget = null;
			wallSlamCapturedTile = null;
		}
	}

	@Override
	public void sendDeath(Entity source) {
		if (subdued || hasFinished() || instance == null || instance.isFightComplete())
			return;

		RiseOfTheSixDebugLog.event(this, "SUBDUE_TRIGGER",
				"hpBefore=" + getHitpoints() + " source=" + (source == null ? "null" : source.getClass().getSimpleName()));
		resetSpecialState();
		subdued = true;
		resetWalkSteps();
		getCombat().removeTarget();
		setHitpoints(SUBDUED_VISIBLE_HITPOINTS);
		setCantInteract(true);
		giveXP();
		RiseOfTheSixDebugLog.event(this, "SUBDUED",
				"hp=" + getHitpoints() + " cantInteract=true visibleShell=true");
		instance.onBrotherSubdued(this);
	}

	public void revive(int hitpoints) {
		if (!subdued || hasFinished())
			return;
		RiseOfTheSixDebugLog.event(this, "REVIVE_BEGIN", "requestedHp=" + hitpoints);
		resetSpecialState();
		reset();
		setHitpoints(Math.min(getMaxHitpoints(), Math.max(1, hitpoints)));
		setCantInteract(false);
		subdued = false;
		resetMeleeSpecialRotation(true);
		RiseOfTheSixDebugLog.event(this, "REVIVE_STATE",
				"hp=" + getHitpoints() + " forceReviveHurricane=" + isMeleeBrother());
	}

	@Override
	public void finish() {
		RiseOfTheSixDebugLog.event(this, "BROTHER_FINISH",
				"id=" + getId() + " hp=" + getHitpoints() + " subdued=" + subdued);
		resetSpecialState();
		super.finish();
	}
}
