package com.rs.game.npc.rots;

import com.rs.game.Animation;
import com.rs.game.Entity;
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
	private static final int HURRICANE_PULSES = 10;
	private static final int HURRICANE_RADIUS = 1;
	private static final int WALL_SLAM_RADIUS = 2;
	private static final int WALL_SCAN_DISTANCE = 12;

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

		/*
		 * RoTS incapacitation is logical rather than a normal zero-HP NPC death.
		 * Keep delayed hits that were already queued before subdual from dropping
		 * the visible 1-HP shell back to zero and making the NPC disappear.
		 */
		if (subdued) {
			hit.setDamage(0);
			return;
		}

		if (brother == Brother.DHAROK && dharokCharging) {
			dharokStoredDamage += Math.max(0, hit.getDamage());
			hit.setDamage(0);
			return;
		}
		if (brother == Brother.TORAG && toragWhacking) {
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
		if (brother == Brother.DHAROK && !isSpecialActive() && dharokStoredDamage > 0) {
			hit.setDamage(hit.getDamage() + dharokStoredDamage);
			dharokStoredDamage = 0;
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

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!dharokCharging || subdued || hasFinished())
					return;
				setNextGraphics(new Graphics(4406, 0, 0, 5, true));
				setNextAnimation(new Animation(21940));
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

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isValidToragVictim(victim)) {
					releaseToragVictim(false);
					return;
				}
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

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (toragWhacking && toragVictim == victim)
					releaseToragVictim(false);
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

		/*
		 * Classic behavior prefers a non-primary player on Guthan's current side.
		 * If none exists, his current target is only valid when it is also on the
		 * same side. This prevents the temporary midpoint model from creating a
		 * cross-portal Impale while portal collision is still being completed.
		 */
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
			}
		}, 1);

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (!isCurrentGuthanImpale(victim, generation))
					return;
				if (isValidImpaleVictim(victim))
					victim.applyHit(new Hit(RiseOfTheSixBrother.this,
							GUTHAN_RETRIEVE_DAMAGE, HitLook.REGULAR_DAMAGE));
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

	/**
	 * Torag pummelling an impaled victim is a documented automatic spear-return
	 * condition. The instance calls this hook when Whack begins.
	 */
	public void onPlayerPummeled(Player victim) {
		if (brother == Brother.GUTHAN && guthanSpearAway && guthanImpaleVictim == victim)
			clearGuthanImpale(true);
	}

	/**
	 * Clears brother-owned special state and relocates this active brother as part
	 * of the encounter-owned empty-side empowerment hop. GFX 4413 is donor-backed
	 * for the RoTS transition; exact live hop animation/landing spacing is still a
	 * runtime fidelity item.
	 */
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
	}

	private void clearGuthanImpale(boolean retargetPrimary) {
		Player primary = guthanPrimaryTarget;
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
		setNextAnimation(new Animation(getCombatDefinitions().getAttackEmote()));

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
				setNextFaceEntity(victim);
				setNextAnimation(new Animation(getCombatDefinitions().getAttackEmote()));

				int damage = Math.min(2500, 250 * (pulse + 1));
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
			player.applyHit(new Hit(this, damage, HitLook.REGULAR_DAMAGE));
		}
	}

	private void endHurricane(Player victim, boolean retarget) {
		if (!hurricaning && hurricaneTarget == null)
			return;
		hurricaning = false;
		hurricaneTarget = null;
		restoreMovementSpecialState();
		if (retarget && isValidSpecialTarget(victim))
			setTarget(victim);
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

		wallSlamming = true;
		wallSlamCapturedTile = capturedTile;
		movementSpecialPreviousRun = getRun();
		setRun(true);
		resetWalkSteps();
		setCantFollowUnderCombat(true);
		setForceFollowClose(false);

		if (wallTile != null) {
			setNextFaceWorldTile(wallTile);
			calcFollow(wallTile, true);
		}

		WorldTasksManager.schedule(new WorldTask() {
			private int tick;

			@Override
			public void run() {
				if (!wallSlamming || wallSlamCapturedTile != capturedTile
						|| !isValidSpecialTarget(victim)) {
					endWallSlam(victim, false);
					stop();
					return;
				}

				if (tick == 5) {
					resetWalkSteps();
					setNextFaceWorldTile(capturedTile);
					calcFollow(capturedTile, true);
				}
				else if (tick >= 10) {
					setNextFaceWorldTile(capturedTile);
					setNextAnimation(new Animation(getCombatDefinitions().getAttackEmote()));
					hitWallSlamPlayers(capturedTile);
					endWallSlam(victim, true);
					stop();
					return;
				}
				tick++;
			}
		}, 0, 0);
		return true;
	}

	private boolean canWallSlam() {
		return brother == Brother.DHAROK || brother == Brother.TORAG || brother == Brother.VERAC;
	}

	private WorldTile findWallApproachTile() {
		int[][] directions = {
				{ 1, 0 },
				{ -1, 0 },
				{ 0, 1 },
				{ 0, -1 }
		};

		WorldTile bestBlocked = null;
		int bestBlockedDistance = -1;
		WorldTile bestOpen = null;
		int bestOpenDistance = -1;

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

			if (moved > bestOpenDistance) {
				bestOpenDistance = moved;
				bestOpen = new WorldTile(x, y, getPlane());
			}

			boolean blockedAhead = !World.checkWalkStep(getPlane(), x, y, moveDirection, getSize());
			if (blockedAhead && moved >= 3 && moved > bestBlockedDistance) {
				bestBlockedDistance = moved;
				bestBlocked = new WorldTile(x, y, getPlane());
			}
		}

		if (bestBlocked != null)
			return bestBlocked;
		if (bestOpen != null && bestOpenDistance > 0)
			return bestOpen;
		return new WorldTile(this);
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
			player.applyHit(new Hit(this, damage, HitLook.REGULAR_DAMAGE));
		}
	}

	private void endWallSlam(Player victim, boolean retarget) {
		if (!wallSlamming && wallSlamCapturedTile == null)
			return;
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
			if (isValidSpecialTarget(victim))
				setTarget(victim);
		}
	}

	private void resetSpecialState() {
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

		resetSpecialState();
		subdued = true;
		resetWalkSteps();
		getCombat().removeTarget();
		/*
		 * Runtime video confirmed that Matrix3's generic NPC death emote eventually
		 * hides the rendered brother even while the RoTS 1-HP shell and type-5
		 * revival bar remain alive. RoTS subdual therefore deliberately avoids the
		 * normal death animation. Until the authentic revision-830 kneeling pose is
		 * established, the active model stays visible/frozen and non-interactable.
		 */
		setHitpoints(SUBDUED_VISIBLE_HITPOINTS);
		setCantInteract(true);
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
		resetMeleeSpecialRotation(true);
	}

	@Override
	public void finish() {
		resetSpecialState();
		super.finish();
	}
}