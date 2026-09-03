package com.rs.game.map.bossInstance.impl;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.rs.game.Animation;
import com.rs.game.WorldTile;
import com.rs.game.map.bossInstance.BossInstance;
import com.rs.game.map.bossInstance.BossInstanceHandler;
import com.rs.game.map.bossInstance.InstanceSettings;
import com.rs.game.npc.rots.RiseOfTheSixBrother;
import com.rs.game.npc.rots.RiseOfTheSixBrother.Brother;
import com.rs.game.npc.rots.RiseOfTheSixReviveBar;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

/**
 * Native Matrix3 instance owner for The Barrows: Rise of the Six.
 *
 * This class owns encounter-wide and cross-brother state. NPC targeting,
 * movement and damage continue through Matrix3 NPCCombat/CombatScript except
 * while an explicit RoTS special owns them.
 */
public class RiseOfTheSixInstance extends BossInstance {

	public static enum ArenaSide {
		WEST,
		EAST
	}

	private static final class Rotation {
		private final Brother[] west;
		private final Brother[] east;

		private Rotation(Brother west1, Brother west2, Brother west3,
				Brother east1, Brother east2, Brother east3) {
			west = new Brother[] { west1, west2, west3 };
			east = new Brother[] { east1, east2, east3 };
		}
	}

	private static final int REVIVE_DELAY_TICKS = 50;
	private static final int REVIVE_HITPOINTS = 25000;
	private static final int REVIVE_BAR_MAX = 255;
	private static final int REVIVE_ANIMATION = 21914;
	private static final int SURVIVOR_HEAL = 5000;
	private static final int EMPTY_SIDE_WARNING_TICKS = 40; // about 24 seconds
	private static final int EMPTY_SIDE_HOP_DELAY_TICKS = 5; // about 3 seconds

	/*
	 * Nocturne's RS3 RoTS implementation copies this exact 8x8-chunk source map.
	 * Matrix3 MapInstance expresses that as one 64x64 ratio cell.
	 */
	private static final int SOURCE_CHUNK_X = 290;
	private static final int SOURCE_CHUNK_Y = 753;

	/*
	 * The current six runtime-confirmed brother tiles all lie on this source row.
	 * Until exact classic north/middle/south spots are runtime-verified, preserve
	 * those known-good tiles and divide them into three west + three east slots.
	 */
	private static final int BROTHER_SPAWN_Y = 6034;
	private static final int BROTHER_SPAWN_PLANE = 1;
	private static final int[] WEST_SPAWN_X = { 2326, 2328, 2330 };
	private static final int[] EAST_SPAWN_X = { 2332, 2334, 2336 };
	private static final int SIDE_SPLIT_SOURCE_X = 2331;

	/*
	 * RuneScape uses a twenty-day west/east formation cycle. A dated RuneScape
	 * rotation record for 2025-06-11 is rotation 11 below:
	 * West Ahrim/Torag/Guthan, East Karil/Dharok/Verac.
	 *
	 * Keep UTC ownership so the daily rotation advances at the game-day reset
	 * boundary rather than the host machine's local timezone.
	 */
	private static final LocalDate ROTATION_ANCHOR_DATE = LocalDate.of(2025, 6, 11);
	private static final int ROTATION_ANCHOR_INDEX = 10;

	private static final Rotation[] ROTATIONS = {
		new Rotation(Brother.DHAROK, Brother.TORAG, Brother.VERAC,
				Brother.KARIL, Brother.AHRIM, Brother.GUTHAN),
		new Rotation(Brother.KARIL, Brother.TORAG, Brother.GUTHAN,
				Brother.AHRIM, Brother.DHAROK, Brother.VERAC),
		new Rotation(Brother.KARIL, Brother.GUTHAN, Brother.VERAC,
				Brother.AHRIM, Brother.TORAG, Brother.DHAROK),
		new Rotation(Brother.GUTHAN, Brother.TORAG, Brother.VERAC,
				Brother.KARIL, Brother.AHRIM, Brother.DHAROK),
		new Rotation(Brother.KARIL, Brother.TORAG, Brother.VERAC,
				Brother.AHRIM, Brother.GUTHAN, Brother.DHAROK),
		new Rotation(Brother.AHRIM, Brother.GUTHAN, Brother.DHAROK,
				Brother.KARIL, Brother.TORAG, Brother.VERAC),
		new Rotation(Brother.KARIL, Brother.AHRIM, Brother.DHAROK,
				Brother.GUTHAN, Brother.TORAG, Brother.VERAC),
		new Rotation(Brother.AHRIM, Brother.TORAG, Brother.DHAROK,
				Brother.KARIL, Brother.GUTHAN, Brother.VERAC),
		new Rotation(Brother.AHRIM, Brother.DHAROK, Brother.VERAC,
				Brother.KARIL, Brother.TORAG, Brother.GUTHAN),
		new Rotation(Brother.KARIL, Brother.AHRIM, Brother.GUTHAN,
				Brother.TORAG, Brother.DHAROK, Brother.VERAC),
		new Rotation(Brother.AHRIM, Brother.TORAG, Brother.GUTHAN,
				Brother.KARIL, Brother.DHAROK, Brother.VERAC),
		new Rotation(Brother.AHRIM, Brother.GUTHAN, Brother.VERAC,
				Brother.KARIL, Brother.TORAG, Brother.DHAROK),
		new Rotation(Brother.KARIL, Brother.AHRIM, Brother.TORAG,
				Brother.GUTHAN, Brother.DHAROK, Brother.VERAC),
		new Rotation(Brother.KARIL, Brother.AHRIM, Brother.VERAC,
				Brother.DHAROK, Brother.TORAG, Brother.GUTHAN),
		new Rotation(Brother.AHRIM, Brother.TORAG, Brother.VERAC,
				Brother.KARIL, Brother.DHAROK, Brother.GUTHAN),
		new Rotation(Brother.KARIL, Brother.DHAROK, Brother.GUTHAN,
				Brother.AHRIM, Brother.TORAG, Brother.VERAC),
		new Rotation(Brother.DHAROK, Brother.TORAG, Brother.GUTHAN,
				Brother.KARIL, Brother.AHRIM, Brother.VERAC),
		new Rotation(Brother.GUTHAN, Brother.DHAROK, Brother.VERAC,
				Brother.KARIL, Brother.AHRIM, Brother.TORAG),
		new Rotation(Brother.KARIL, Brother.TORAG, Brother.DHAROK,
				Brother.AHRIM, Brother.GUTHAN, Brother.VERAC),
		new Rotation(Brother.KARIL, Brother.DHAROK, Brother.VERAC,
				Brother.AHRIM, Brother.TORAG, Brother.GUTHAN)
	};

	private List<RiseOfTheSixBrother> brothers;
	private EnumMap<Brother, ArenaSide> currentBrotherSides;
	private int reviveGeneration;
	private boolean fightComplete;
	private Rotation activeRotation;
	private int activeRotationIndex;
	private int sideHopGeneration;
	private boolean sideHopPending;
	private boolean sideHopComplete;
	private ArenaSide sideHopFrom;
	private ArenaSide sideHopTo;

	public RiseOfTheSixInstance(Player owner, InstanceSettings settings) {
		super(owner, settings);
		if (brothers == null)
			brothers = new CopyOnWriteArrayList<RiseOfTheSixBrother>();
		if (currentBrotherSides == null)
			currentBrotherSides = new EnumMap<Brother, ArenaSide>(Brother.class);
	}

	@Override
	public int[] getMapPos() {
		return new int[] { SOURCE_CHUNK_X, SOURCE_CHUNK_Y };
	}

	@Override
	public int[] getMapSize() {
		return new int[] { 1, 1 };
	}

	@Override
	public String getInstanceName() {
		return "The Barrows: Rise of the Six";
	}

	@Override
	public void loadMapInstance() {
		if (brothers == null)
			brothers = new CopyOnWriteArrayList<RiseOfTheSixBrother>();
		brothers.clear();
		if (currentBrotherSides == null)
			currentBrotherSides = new EnumMap<Brother, ArenaSide>(Brother.class);
		else
			currentBrotherSides.clear();
		fightComplete = false;
		reviveGeneration++;
		sideHopGeneration++;
		sideHopPending = false;
		sideHopComplete = false;
		sideHopFrom = null;
		sideHopTo = null;

		activeRotationIndex = resolveDailyRotationIndex();
		activeRotation = ROTATIONS[activeRotationIndex];
		initializeBrotherSides();
		spawnSide(activeRotation.west, WEST_SPAWN_X);
		spawnSide(activeRotation.east, EAST_SPAWN_X);
		startSideEmpowermentMonitor();
	}

	private int resolveDailyRotationIndex() {
		long days = ChronoUnit.DAYS.between(ROTATION_ANCHOR_DATE,
				LocalDate.now(ZoneOffset.UTC));
		long offset = days % ROTATIONS.length;
		int index = (int) ((ROTATION_ANCHOR_INDEX + offset) % ROTATIONS.length);
		if (index < 0)
			index += ROTATIONS.length;
		return index;
	}

	private void initializeBrotherSides() {
		for (Brother brother : activeRotation.west)
			currentBrotherSides.put(brother, ArenaSide.WEST);
		for (Brother brother : activeRotation.east)
			currentBrotherSides.put(brother, ArenaSide.EAST);
	}

	private void spawnSide(Brother[] side, int[] xSlots) {
		for (int slot = 0; slot < side.length && slot < xSlots.length; slot++)
			spawnBrother(side[slot], xSlots[slot], BROTHER_SPAWN_Y, BROTHER_SPAWN_PLANE);
	}

	private void spawnBrother(Brother brother, int x, int y, int plane) {
		RiseOfTheSixBrother npc = new RiseOfTheSixBrother(brother, getTile(x, y, plane), this);
		brothers.add(npc);
	}

	public int getActiveRotationNumber() {
		return activeRotationIndex + 1;
	}

	public String getActiveRotationDescription() {
		if (activeRotation == null)
			return "RoTS rotation not loaded";
		return "West: " + join(activeRotation.west) + " | East: " + join(activeRotation.east);
	}

	private String join(Brother[] side) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < side.length; i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(side[i].getDisplayName());
		}
		return builder.toString();
	}

	public ArenaSide getBrotherSide(RiseOfTheSixBrother brother) {
		return brother == null ? null : getBrotherSide(brother.getBrother());
	}

	public ArenaSide getBrotherSide(Brother brother) {
		if (brother == null)
			return null;
		ArenaSide current = currentBrotherSides == null ? null : currentBrotherSides.get(brother);
		if (current != null)
			return current;
		if (activeRotation == null)
			return null;
		if (contains(activeRotation.west, brother))
			return ArenaSide.WEST;
		if (contains(activeRotation.east, brother))
			return ArenaSide.EAST;
		return null;
	}

	private boolean contains(Brother[] side, Brother brother) {
		for (Brother candidate : side) {
			if (candidate == brother)
				return true;
		}
		return false;
	}

	/**
	 * Runtime side classification for players uses the midpoint between the two
	 * current known-good brother slot groups. Exact classic arena sub-area bounds
	 * remain a runtime/map verification item.
	 */
	public ArenaSide getPlayerSide(Player player) {
		if (player == null || player.hasFinished())
			return null;
		WorldTile split = getTile(SIDE_SPLIT_SOURCE_X, BROTHER_SPAWN_Y, BROTHER_SPAWN_PLANE);
		if (split == null || player.getPlane() != split.getPlane())
			return null;
		return player.getX() <= split.getX() ? ArenaSide.WEST : ArenaSide.EAST;
	}

	public boolean isPlayerOnBrotherSide(Player player, RiseOfTheSixBrother brother) {
		ArenaSide playerSide = getPlayerSide(player);
		ArenaSide brotherSide = getBrotherSide(brother);
		return playerSide != null && playerSide == brotherSide;
	}

	public boolean areBrothersOnSameSide(RiseOfTheSixBrother first, RiseOfTheSixBrother second) {
		ArenaSide firstSide = getBrotherSide(first);
		ArenaSide secondSide = getBrotherSide(second);
		return firstSide != null && firstSide == secondSide;
	}

	private int getActivePlayerCount(ArenaSide side) {
		int count = 0;
		for (Player player : getPlayers()) {
			if (player == null || player.hasFinished() || player.isDead() || !isPlayerInside(player))
				continue;
			if (getPlayerSide(player) == side)
				count++;
		}
		return count;
	}

	private ArenaSide getEmptySideWithOccupiedOpposite() {
		int westPlayers = getActivePlayerCount(ArenaSide.WEST);
		int eastPlayers = getActivePlayerCount(ArenaSide.EAST);
		if (westPlayers == 0 && eastPlayers > 0)
			return ArenaSide.WEST;
		if (eastPlayers == 0 && westPlayers > 0)
			return ArenaSide.EAST;
		return null;
	}

	private ArenaSide opposite(ArenaSide side) {
		return side == ArenaSide.WEST ? ArenaSide.EAST : ArenaSide.WEST;
	}

	private void startSideEmpowermentMonitor() {
		final int generation = sideHopGeneration;
		WorldTasksManager.schedule(new WorldTask() {
			private ArenaSide observedEmptySide;
			private int emptyTicks;

			@Override
			public void run() {
				if (generation != sideHopGeneration || isFinished() || fightComplete || sideHopComplete) {
					stop();
					return;
				}
				if (sideHopPending)
					return;

				/*
				 * Live RoTS blocks the hop while any brother is incapacitated. Karil
				 * Shadow Dash and the second-barrier edge case are also blockers, but
				 * those systems do not exist in this checkpoint and remain explicit
				 * follow-up gates rather than invented approximations.
				 */
				if (getSubduedCount() != 0) {
					observedEmptySide = null;
					emptyTicks = 0;
					return;
				}

				ArenaSide emptySide = getEmptySideWithOccupiedOpposite();
				if (emptySide == null) {
					observedEmptySide = null;
					emptyTicks = 0;
					return;
				}
				if (observedEmptySide != emptySide) {
					observedEmptySide = emptySide;
					emptyTicks = 1;
					return;
				}

				emptyTicks++;
				if (emptyTicks >= EMPTY_SIDE_WARNING_TICKS) {
					beginSideEmpowerment(emptySide, opposite(emptySide), generation);
					observedEmptySide = null;
					emptyTicks = 0;
				}
			}
		}, 1, 1);
	}

	private void beginSideEmpowerment(final ArenaSide emptySide, final ArenaSide occupiedSide,
			final int generation) {
		if (sideHopPending || sideHopComplete || emptySide == null || occupiedSide == null)
			return;
		sideHopPending = true;
		sideHopFrom = emptySide;
		sideHopTo = occupiedSide;
		broadcast("As there is no one on the other side of the portal, it empowers the Barrows Brothers to destroy everyone!");

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (generation != sideHopGeneration || isFinished() || fightComplete)
					return;
				if (!canCompleteSideEmpowerment(emptySide, occupiedSide)) {
					sideHopPending = false;
					sideHopFrom = null;
					sideHopTo = null;
					return;
				}
				completeSideEmpowerment(emptySide, occupiedSide);
			}
		}, EMPTY_SIDE_HOP_DELAY_TICKS);
	}

	private boolean canCompleteSideEmpowerment(ArenaSide emptySide, ArenaSide occupiedSide) {
		return getSubduedCount() == 0 && getActivePlayerCount(emptySide) == 0
				&& getActivePlayerCount(occupiedSide) > 0;
	}

	private void completeSideEmpowerment(ArenaSide emptySide, ArenaSide occupiedSide) {
		int[] occupiedSlots = occupiedSide == ArenaSide.WEST ? WEST_SPAWN_X : EAST_SPAWN_X;
		int slot = 0;
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother == null || brother.hasFinished() || brother.isSubdued()
					|| getBrotherSide(brother) != emptySide)
				continue;
			/*
			 * Reuse the already runtime-proven occupied-side slots. Incoming brothers
			 * may temporarily stack with the resident trio; exact "slightly away"
			 * live landing tiles are still HYPOTHESIS and should replace these only
			 * after map/runtime evidence establishes them.
			 */
			WorldTile destination = getTile(occupiedSlots[slot % occupiedSlots.length],
					BROTHER_SPAWN_Y, BROTHER_SPAWN_PLANE);
			brother.moveForSideEmpowerment(destination);
			currentBrotherSides.put(brother.getBrother(), occupiedSide);
			slot++;
		}
		if (slot > 0) {
			sideHopComplete = true;
			sideHopPending = false;
		}
		else {
			sideHopPending = false;
			sideHopFrom = null;
			sideHopTo = null;
		}
	}

	public boolean isSideHopPending() {
		return sideHopPending;
	}

	public boolean isSideHopComplete() {
		return sideHopComplete;
	}

	public ArenaSide getSideHopFrom() {
		return sideHopFrom;
	}

	public ArenaSide getSideHopTo() {
		return sideHopTo;
	}

	public void onBrotherSubdued(RiseOfTheSixBrother subduedBrother) {
		synchronized (BossInstanceHandler.LOCK) {
			if (fightComplete || subduedBrother == null)
				return;

			/*
			 * Runtime verification on revision 830 showed donor inactive ids
			 * 18546-18551 render as invisible models in this client/cache. Keep the
			 * proven empowered model visible while the logical subdued state owns the
			 * NPC, and let the encounter-specific type-5 bar provide the countdown.
			 */
			broadcast("As you defeat " + subduedBrother.getBrother().getDisplayName()
					+ ", the shadow engulfs the remaining wights!");
			healActiveBrothers();

			if (getSubduedCount() == Brother.values().length) {
				fightComplete = true;
				reviveGeneration++;
				sideHopGeneration++;
				sideHopPending = false;
				clearReviveBars();
				broadcast("All six brothers are subdued. The shadow bond has been broken.");
				return;
			}

			final int generation = ++reviveGeneration;
			startReviveBarTask(generation);
			WorldTasksManager.schedule(new WorldTask() {
				@Override
				public void run() {
					synchronized (BossInstanceHandler.LOCK) {
						if (fightComplete || generation != reviveGeneration || isFinished())
							return;
						reviveSubduedBrothers();
					}
				}
			}, REVIVE_DELAY_TICKS);
		}
	}

	private void startReviveBarTask(final int generation) {
		queueReviveBars(0);
		WorldTasksManager.schedule(new WorldTask() {
			private int elapsedTicks;

			@Override
			public void run() {
				if (fightComplete || generation != reviveGeneration || isFinished()) {
					stop();
					return;
				}
				elapsedTicks++;
				int percentage = Math.min(REVIVE_BAR_MAX,
						(elapsedTicks * REVIVE_BAR_MAX) / REVIVE_DELAY_TICKS);
				queueReviveBars(percentage);
				if (elapsedTicks >= REVIVE_DELAY_TICKS)
					stop();
			}
		}, 1, 1);
	}

	private void queueReviveBars(int percentage) {
		if (brothers == null)
			return;
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother == null || !brother.isSubdued() || brother.hasFinished())
				continue;
			/*
			 * Clearing first deliberately replaces the ordinary 1/50000 HP bar that
			 * Matrix3 would otherwise add for the hit that caused incapacitation.
			 */
			brother.getNextHitBars().clear();
			brother.getNextHitBars().add(new RiseOfTheSixReviveBar(percentage));
		}
	}

	private void clearReviveBars() {
		if (brothers == null)
			return;
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother != null)
				brother.getNextHitBars().clear();
		}
	}

	/**
	 * Cross-brother callback used by the documented Guthan/Torag interaction:
	 * an impaled victim being pummeled by Torag automatically returns Guthan's
	 * spear.
	 */
	public void onToragWhackStarted(Player victim) {
		if (victim == null || brothers == null)
			return;
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother != null && !brother.hasFinished())
				brother.onPlayerPummeled(victim);
		}
	}

	/**
	 * verified-static Throw pairing matrix from the RoTS Beasts descriptions.
	 * Throw eligibility is now additionally constrained to the active daily side.
	 */
	public boolean isVerifiedThrowPair(RiseOfTheSixBrother thrower, RiseOfTheSixBrother target) {
		if (thrower == null || target == null || thrower == target || thrower.isSubdued()
				|| target.isSubdued() || thrower.hasFinished() || target.hasFinished()
				|| !areBrothersOnSameSide(thrower, target))
			return false;

		Brother from = thrower.getBrother();
		Brother to = target.getBrother();
		if (from == Brother.GUTHAN || from == Brother.TORAG)
			return to == Brother.AHRIM || to == Brother.KARIL || to == Brother.VERAC;
		if (from == Brother.VERAC)
			return to == Brother.AHRIM || to == Brother.KARIL;
		return false;
	}

	/**
	 * Returns the nearest active same-side brother from the verified Throw matrix.
	 * Actual Throw flight/landing is still intentionally dormant until its exact
	 * assets/timing are established.
	 */
	public RiseOfTheSixBrother findVerifiedThrowTarget(RiseOfTheSixBrother thrower) {
		if (thrower == null || brothers == null)
			return null;

		RiseOfTheSixBrother nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (RiseOfTheSixBrother target : brothers) {
			if (!isVerifiedThrowPair(thrower, target) || target.getPlane() != thrower.getPlane())
				continue;
			int distance = Math.max(Math.abs(target.getX() - thrower.getX()),
					Math.abs(target.getY() - thrower.getY()));
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = target;
			}
		}
		return nearest;
	}

	private void healActiveBrothers() {
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother == null || brother.isSubdued() || brother.hasFinished())
				continue;
			brother.heal(SURVIVOR_HEAL);
		}
	}

	private void reviveSubduedBrothers() {
		boolean revived = false;
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother == null || !brother.isSubdued() || brother.hasFinished())
				continue;
			brother.getNextHitBars().clear();
			brother.revive(REVIVE_HITPOINTS);
			brother.setNextNPCTransformation(brother.getBrother().getNpcId());
			brother.setNextAnimation(new Animation(REVIVE_ANIMATION));
			revived = true;
		}
		if (revived)
			broadcast("The shadow bond is restored between the brothers, bringing back lost combatants.");
	}

	public int getSubduedCount() {
		int count = 0;
		if (brothers == null)
			return count;
		for (RiseOfTheSixBrother brother : brothers) {
			if (brother != null && brother.isSubdued())
				count++;
		}
		return count;
	}

	public boolean isFightComplete() {
		return fightComplete;
	}

	public List<RiseOfTheSixBrother> getBrothers() {
		return brothers;
	}

	private void broadcast(String message) {
		for (Player player : getPlayers()) {
			if (player != null && !player.hasFinished())
				player.getPackets().sendGameMessage(message);
		}
	}

	@Override
	public void finish() {
		synchronized (BossInstanceHandler.LOCK) {
			if (isFinished())
				return;
			reviveGeneration++;
		sideHopGeneration++;
		activeRotation = null;
		if (currentBrotherSides != null)
			currentBrotherSides.clear();
		sideHopPending = false;
		sideHopComplete = false;
		sideHopFrom = null;
		sideHopTo = null;
		clearReviveBars();
		if (brothers != null) {
			for (RiseOfTheSixBrother brother : brothers) {
				if (brother != null && !brother.hasFinished())
					brother.finish();
			}
			brothers.clear();
		}
		super.finish();
	}
}
