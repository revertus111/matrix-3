package com.rs.game.map.bossInstance.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.rs.game.map.bossInstance.BossInstance;
import com.rs.game.map.bossInstance.BossInstanceHandler;
import com.rs.game.map.bossInstance.InstanceSettings;
import com.rs.game.npc.rots.RiseOfTheSixBrother;
import com.rs.game.npc.rots.RiseOfTheSixBrother.Brother;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

/**
 * Native Matrix3 instance owner for The Barrows: Rise of the Six.
 *
 * This class owns only encounter-wide state. NPC targeting, movement and damage
 * continue through Matrix3 NPCCombat/CombatScript.
 */
public class RiseOfTheSixInstance extends BossInstance {

	private static final int REVIVE_DELAY_TICKS = 50;
	private static final int REVIVE_HITPOINTS = 25000;
	private static final int SURVIVOR_HEAL = 5000;

	/*
	 * Nocturne's RS3 RoTS implementation copies this exact 8x8-chunk source map.
	 * Matrix3 MapInstance expresses that as one 64x64 ratio cell.
	 */
	private static final int SOURCE_CHUNK_X = 290;
	private static final int SOURCE_CHUNK_Y = 753;

	private List<RiseOfTheSixBrother> brothers;
	private int reviveGeneration;
	private boolean fightComplete;

	public RiseOfTheSixInstance(Player owner, InstanceSettings settings) {
		super(owner, settings);
		if (brothers == null)
			brothers = new CopyOnWriteArrayList<RiseOfTheSixBrother>();
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
		fightComplete = false;
		reviveGeneration++;

		/*
		 * Donor code spawns all six on source offset y+10 with x randomized from
		 * +6..+16. These fixed positions stay inside that donor-proven band while
		 * making tests reproducible. Exact live-RS formation is still HYPOTHESIS.
		 */
		spawnBrother(Brother.AHRIM, 2326, 6034, 1);
		spawnBrother(Brother.GUTHAN, 2328, 6034, 1);
		spawnBrother(Brother.VERAC, 2330, 6034, 1);
		spawnBrother(Brother.KARIL, 2332, 6034, 1);
		spawnBrother(Brother.TORAG, 2334, 6034, 1);
		spawnBrother(Brother.DHAROK, 2336, 6034, 1);
	}

	private void spawnBrother(Brother brother, int x, int y, int plane) {
		RiseOfTheSixBrother npc = new RiseOfTheSixBrother(brother, getTile(x, y, plane), this);
		brothers.add(npc);
	}

	public void onBrotherSubdued(RiseOfTheSixBrother subduedBrother) {
		synchronized (BossInstanceHandler.LOCK) {
			if (fightComplete || subduedBrother == null)
				return;

			broadcast("As you defeat " + subduedBrother.getBrother().getDisplayName()
					+ ", the shadow engulfs the remaining wights!");
			healActiveBrothers();

			if (getSubduedCount() == Brother.values().length) {
				fightComplete = true;
				reviveGeneration++;
				broadcast("All six brothers are subdued. The shadow bond has been broken.");
				return;
			}

			final int generation = ++reviveGeneration;
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
			brother.revive(REVIVE_HITPOINTS);
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
}
