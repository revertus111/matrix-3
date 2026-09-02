package com.rs.game.npc.rots;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.ForceTalk;
import com.rs.game.Graphics;
import com.rs.game.Hit;
import com.rs.game.Hit.HitLook;
import com.rs.game.World;
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
	private static final int HURRICANE_PULSES = 10;
	private static final int HURRICANE_RADIUS = 1;
	private static final int WALL_SLAM_RADIUS = 2;
	private static final int WALL_SCAN_DISTANCE = 12;

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

	public boolean isSpecialActive() {
		return dharokCharging || toragWhacking || hurricaning || wallSlamming;
	}

	public boolean isMeleeBrother() {
		return brother == Brother.DHAROK || brother == Brother.GUTHAN
				|| brother == Brother.TORAG || brother == Brother.VERAC;
	}

	public void noteNormalMeleeAttack() {
		if (!isMeleeBrother() || subdued || isSpecialActive())
			return;
		if (meleeAutosUntilSpecial > 0)
			meleeAutosUntilSpecial--;
	}

	/**
	 * Starts the next currently implemented melee special.
	 *
	 * Dharok and Torag have all three slots represented:
	 * Hurricane -> unique special -> Wall Slam.
	 * Guthan and Verac still have incomplete rotations until their remaining
	 * brother-specific specials are implemented.
	 */
	public boolean tryStartMeleeSpecial(Entity target) {
		if (!isMeleeBrother() || subdued || hasFinished() || isSpecialActive()
				|| instance == null || instance.isFightComplete() || meleeAutosUntilSpecial > 0
				|| !(target instanceof Player))
			return false;

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
			started = startHurricane(target);
			break;
		case VERAC:
			started = index == 0 ? startHurricane(target) : startWallSlam(target);
			break;
		default:
			started = false;
			break;
		}

		if (started) {
			meleeSpecialIndex = (index + 1) % specialCount;
			/*
			 * The exact number of normal autos between RoTS specials is not yet
			 * source-verified in this cache. Keep a short 3-5 auto gate so specials
			 * do not chain back-to-back while the real rotation timing is verified.
			 */
			meleeAutosUntilSpecial = 3 + Utils.random(3);
		}
		return started;
	}

	private int getImplementedMeleeSpecialCount() {
		if (brother == Brother.DHAROK || brother == Brother.TORAG)
			return 3;
		if (brother == Brother.VERAC)
			return 2;
		if (brother == Brother.GUTHAN)
			return 1;
		return 0;
	}

	private void resetMeleeSpecialRotation(boolean forceHurricane) {
		if (!isMeleeBrother())
			return;
		meleeAutosUntilSpecial = forceHurricane ? 0 : 3 + Utils.random(3);
		meleeSpecialIndex = forceHurricane ? 0 : -1;
	}

	@Override
	public void handleIngoingHit(Hit hit) {
		if (hit == null)
			return;

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
		return true;
	}

	private boolean startToragWhack(Entity target) {
		if (brother != Brother.TORAG || toragWhacking || subdued || hasFinished()
				|| !(target instanceof Player))
			return false;

		final Player victim = (Player) target;
		if (!isValidSpecialTarget(victim))
			return false;

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

		/*
		 * Shared Hurricane behavior is verified-static, but the exact empowered
		 * brother spin animation id is not yet established in this cache. Reuse
		 * the brother's normal attack emote as a temporary visual rather than
		 * inventing an animation id.
		 */
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

		/*
		 * The captured-tile 5x5 impact is verified-static. Exact wall anchors,
		 * run-up animation and fling animation are still HYPOTHESIS, so Matrix3
		 * pathing drives the run-to-edge / rush-back sequence without fake ids.
		 */
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

		/*
		 * Extremely defensive fallback: remain on the current tile rather than
		 * forcing an unclipped teleport if the copied arena gives no route.
		 */
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
		boolean wasDharokCharging = dharokCharging;
		dharokCharging = false;
		dharokStoredDamage = 0;
		if (wasDharokCharging) {
			setCantFollowUnderCombat(false);
			setForceFollowClose(true);
		}

		if (toragWhacking || toragVictim != null)
			releaseToragVictim(false);

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
		resetMeleeSpecialRotation(true);
	}

	@Override
	public void finish() {
		resetSpecialState();
		super.finish();
	}
}
