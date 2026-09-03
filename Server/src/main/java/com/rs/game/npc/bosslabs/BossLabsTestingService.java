package com.rs.game.npc.bosslabs;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.cache.loaders.NPCDefinitions;
import com.rs.game.Entity;
import com.rs.game.World;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;
import com.rs.utils.Utils;

/**
 * Developer-only runtime controls for BossLabs encounter testing.
 *
 * Each admin player owns at most one test-spawned boss instance. Every mutating
 * operation targets that exact instance; this service never searches the world
 * by NPC id and therefore cannot accidentally reset another encounter.
 *
 * Callers must enter through Matrix3's world-task path before invoking methods
 * that mutate NPC/world state.
 */
public final class BossLabsTestingService {

	private static final int[][] SPAWN_DIRECTIONS = {
			{0, 1}, {1, 1}, {1, 0}, {1, -1},
			{0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
	};

	private static final Map<Player, NPC> TEST_BOSSES =
			Collections.synchronizedMap(new WeakHashMap<Player, NPC>());
	private static final Map<Player, WorldTask> TEST_WATCHERS =
			Collections.synchronizedMap(new WeakHashMap<Player, WorldTask>());

	private BossLabsTestingService() {
	}

	public static String spawnBoss(Player player, int npcId) {
		requirePlayer(player);
		requireLiveDefinition(npcId);
		removeCurrentTestBoss(player);

		WorldTile tile = findSpawnTile(player, npcId);
		if (tile == null)
			throw new IllegalArgumentException("No free BossLabs test spawn tile was found around you.");

		NPC boss = World.spawnNPC(npcId, tile, -1, true, true);
		if (boss == null)
			throw new IllegalArgumentException("Matrix3 could not spawn the selected BossLabs NPC.");

		TEST_BOSSES.put(player, boss);
		startSessionWatch(player, boss);
		BossEncounterContext encounter = BossEncounterRuntime.getOrCreate(boss);
		encounter.registerParticipant(player);
		boss.setTarget(player);
		return "Spawned BossLabs test boss " + boss.getName() + " [" + npcId + "] near you.";
	}

	public static String resetEncounter(Player player, int npcId) {
		return spawnBoss(player, npcId).replace("Spawned", "Reset and spawned");
	}

	public static String setHealthPercent(Player player, int npcId, int percent) {
		NPC boss = requireTestBoss(player, npcId);
		if (percent < 1 || percent > 100)
			throw new IllegalArgumentException("Boss health percent must be between 1 and 100.");
		int maximum = Math.max(1, boss.getMaxHitpoints());
		int hitpoints = Math.max(1, (int) ((maximum * (long) percent) / 100L));
		boss.setHitpoints(hitpoints);
		int actualPercent = (int) ((hitpoints * 100L) / maximum);
		return "Set test boss HP to " + hitpoints + "/" + maximum + " (" + actualPercent + "%).";
	}

	public static String forcePhase(Player player, int npcId, String phaseId) {
		NPC boss = requireTestBoss(player, npcId);
		BossDefinition definition = requireLiveDefinition(npcId);
		BossPhaseDefinition phase = findPhase(definition, phaseId);
		int maximum = Math.max(1, boss.getMaxHitpoints());

		for (int percent = Math.min(100, phase.getMaximumHealthPercent());
				percent >= Math.max(1, phase.getMinimumHealthPercent()); percent--) {
			int hitpoints = Math.max(1, (int) ((maximum * (long) percent + 99L) / 100L));
			if (definition.getPhaseForHealth(hitpoints, maximum) == phase) {
				boss.setHitpoints(hitpoints);
				return "Forced HP into phase " + phase.getId()
						+ "; its normal transition actions run on the next BossLabs combat opportunity.";
			}
		}
		throw new IllegalArgumentException("Phase " + phase.getId()
				+ " has no living HP checkpoint representable by this NPC's max hitpoints.");
	}

	public static String triggerAttack(Player player, int npcId, String phaseId, String attackId) {
		NPC boss = requireTestBoss(player, npcId);
		Entity target = boss.getCombat().getTarget();
		if (target == null || target.hasFinished() || target.isDead())
			target = player;
		int delay = BossCombatScript.INSTANCE.executeAttackForTesting(boss, target, phaseId, attackId);
		return "Triggered " + attackId + " from phase " + phaseId
				+ " through the normal BossLabs attack path (reported delay " + delay + ").";
	}

	public static String clearHazards(Player player, int npcId) {
		NPC boss = requireTestBoss(player, npcId);
		int count = BossEncounterRuntime.clearOwnedTasks(boss);
		return "Cleared " + count + " BossLabs delayed tile task" + (count == 1 ? "" : "s")
				+ " (hazards/pending telegraphs).";
	}

	public static String clearMinions(Player player, int npcId) {
		NPC boss = requireTestBoss(player, npcId);
		int count = BossEncounterRuntime.clearOwnedNpcs(boss);
		return "Cleared " + count + " encounter-owned minion" + (count == 1 ? "" : "s") + ".";
	}

	private static BossDefinition requireLiveDefinition(int npcId) {
		BossDefinition definition = BossDefinitionRegistry.get(npcId);
		if (definition == null)
			throw new IllegalArgumentException("Apply a live BossLabs definition before using encounter testing controls.");
		return definition;
	}

	private static NPC requireTestBoss(Player player, int npcId) {
		requirePlayer(player);
		NPC boss = TEST_BOSSES.get(player);
		if (boss == null || boss.hasFinished() || boss.isDead()) {
			clearSessionReference(player, boss);
			throw new IllegalArgumentException("Spawn this BossLabs boss from the Testing tab first.");
		}
		if (boss.getId() != npcId)
			throw new IllegalArgumentException("The active test boss belongs to a different selected NPC. Spawn this boss first.");
		requireLiveDefinition(npcId);
		return boss;
	}

	private static void requirePlayer(Player player) {
		if (player == null || player.hasFinished() || player.isDead())
			throw new IllegalArgumentException("The testing player is not active.");
	}

	private static BossPhaseDefinition findPhase(BossDefinition definition, String phaseId) {
		String wanted = phaseId == null ? "" : phaseId.trim();
		if (wanted.length() == 0)
			throw new IllegalArgumentException("Phase ID is required.");
		for (BossPhaseDefinition phase : definition.getPhases()) {
			if (phase.getId().equalsIgnoreCase(wanted))
				return phase;
		}
		throw new IllegalArgumentException("Unknown BossLabs phase id: " + wanted);
	}

	private static void startSessionWatch(final Player player, final NPC boss) {
		WorldTask previous = TEST_WATCHERS.remove(player);
		if (previous != null)
			previous.stop();

		WorldTask watcher = new WorldTask() {
			@Override
			public void run() {
				NPC current = TEST_BOSSES.get(player);
				if (current != boss) {
					removeWatcher(player, this);
					stop();
					return;
				}
				if (boss.hasFinished() || boss.isDead()) {
					TEST_BOSSES.remove(player);
					removeWatcher(player, this);
					BossEncounterRuntime.finishEncounter(boss);
					stop();
					return;
				}
				if (player.hasFinished() || !BossDefinitionRegistry.isRegistered(boss.getId())) {
					TEST_BOSSES.remove(player);
					removeWatcher(player, this);
					BossEncounterRuntime.finishEncounter(boss);
					if (!boss.hasFinished())
						boss.finish();
					stop();
				}
			}
		};
		TEST_WATCHERS.put(player, watcher);
		WorldTasksManager.schedule(watcher, 0, 0);
	}

	private static void removeWatcher(Player player, WorldTask expected) {
		WorldTask current = TEST_WATCHERS.get(player);
		if (current == expected)
			TEST_WATCHERS.remove(player);
	}

	private static void clearSessionReference(Player player, NPC boss) {
		NPC current = TEST_BOSSES.get(player);
		if (current == boss)
			TEST_BOSSES.remove(player);
		WorldTask watcher = TEST_WATCHERS.remove(player);
		if (watcher != null)
			watcher.stop();
	}

	private static void removeCurrentTestBoss(Player player) {
		WorldTask watcher = TEST_WATCHERS.remove(player);
		if (watcher != null)
			watcher.stop();
		NPC existing = TEST_BOSSES.remove(player);
		if (existing == null)
			return;
		BossEncounterRuntime.finishEncounter(existing);
		if (!existing.hasFinished())
			existing.finish();
	}

	private static WorldTile findSpawnTile(Player player, int npcId) {
		if (npcId < 0 || npcId >= Utils.getNPCDefinitionsSize())
			return null;
		NPCDefinitions definitions = NPCDefinitions.getNPCDefinitions(npcId);
		int bossSize = Math.max(1, definitions.size);
		int playerSize = Math.max(1, player.getSize());
		int gap = 1;
		int westX = player.getX() - bossSize - gap;
		int eastX = player.getX() + playerSize + gap;
		int southY = player.getY() - bossSize - gap;
		int northY = player.getY() + playerSize + gap;
		int centerX = player.getX() + (playerSize - bossSize) / 2;
		int centerY = player.getY() + (playerSize - bossSize) / 2;

		for (int[] direction : SPAWN_DIRECTIONS) {
			int x = direction[0] < 0 ? westX : direction[0] > 0 ? eastX : centerX;
			int y = direction[1] < 0 ? southY : direction[1] > 0 ? northY : centerY;
			if (World.isTileFree(player.getPlane(), x, y, bossSize))
				return new WorldTile(x, y, player.getPlane());
		}
		return null;
	}
}
