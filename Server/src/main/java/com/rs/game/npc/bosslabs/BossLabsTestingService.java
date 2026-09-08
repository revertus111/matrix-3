package com.rs.game.npc.bosslabs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.cache.loaders.NPCDefinitions;
import com.rs.game.Entity;
import com.rs.game.World;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.NPCCombatDefinitions;
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

	private static final int PREFAB_TEST_STEPS = 8;
	private static final String PREFAB_PHASE_ID = "prefab_phase";
	private static final String PREFAB_ATTACK_ID = "prefab_corner";

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
		removeCurrentTestBoss(player);

		WorldTile tile = findSpawnTile(player, npcId);
		if (tile == null)
			throw new IllegalArgumentException("No free BossLabs test spawn tile was found around you.");

		NPC boss = World.spawnNPC(npcId, tile, -1, true, true);
		if (boss == null)
			throw new IllegalArgumentException("Matrix3 could not spawn the selected NPC.");

		boolean bossLabsOwned = BossDefinitionRegistry.isRegistered(npcId);
		TEST_BOSSES.put(player, boss);
		startSessionWatch(player, boss, bossLabsOwned);
		if (bossLabsOwned) {
			BossEncounterContext encounter = BossEncounterRuntime.getOrCreate(boss);
			encounter.registerParticipant(player);
		}
		boss.setTarget(player);
		return "Spawned test NPC " + boss.getName() + " [" + npcId + "] near you."
				+ (bossLabsOwned ? " BossLabs encounter controls are available." : " Using Matrix3 combat ownership.");
	}

	public static String resetEncounter(Player player, int npcId) {
		return spawnBoss(player, npcId).replace("Spawned", "Reset and spawned");
	}

	/**
	 * Runs a small deterministic BossLabs smoke test against one exact controlled
	 * NPC instance. The prefab definition is passed directly to the testing hook;
	 * it is never registered globally, never saved, and never changes Drops.
	 */
	public static String runPrefabSelfTest(Player player, int npcId) {
		requirePlayer(player);
		BossDefinition liveBefore = BossDefinitionRegistry.get(npcId);
		BossDefinition savedBefore = BossDefinitionStore.getSaved(npcId);
		boolean rollbackBefore = BossDefinitionRegistry.hasRollback(npcId);
		int passed = 0;
		String step = "controlled test spawn";

		try {
			spawnBoss(player, npcId);
			NPC boss = requireTestBoss(player, npcId);
			passed++;

			step = "disposable prefab definition";
			BossDefinition prefab = createPrefabDefinition(npcId, boss.getName());
			if (prefab.getNpcId() != npcId || prefab.getPhases().size() != 1)
				throw new IllegalStateException("Prefab definition did not build as expected.");
			passed++;

			step = "HP and phase resolution";
			setHealthPercent(player, npcId, 50);
			BossPhaseDefinition phase = prefab.getPhaseForHealth(boss.getHitpoints(), Math.max(1, boss.getMaxHitpoints()));
			if (phase == null || !PREFAB_PHASE_ID.equals(phase.getId()))
				throw new IllegalStateException("Prefab phase did not resolve after the HP checkpoint.");
			passed++;

			step = "encounter context ownership";
			BossEncounterContext encounter = BossEncounterRuntime.getOrCreate(boss);
			encounter.registerParticipant(player);
			if (encounter.getParticipantCount() < 1)
				throw new IllegalStateException("Testing player was not registered in the prefab encounter context.");
			passed++;

			step = "owned task tracking";
			WorldTask cleanupProbe = new WorldTask() {
				@Override
				public void run() {
					stop();
				}
			};
			encounter.trackTask(cleanupProbe);
			if (encounter.getOwnedTaskCount() < 1)
				throw new IllegalStateException("Prefab cleanup probe was not tracked by the encounter.");
			passed++;

			step = "real asymmetric tile attack path";
			int delay = BossCombatScript.INSTANCE.executeAttackForTesting(
					boss, player, prefab, PREFAB_PHASE_ID, PREFAB_ATTACK_ID);
			if (delay < 1)
				throw new IllegalStateException("Prefab attack returned an invalid combat delay.");
			passed++;

			step = "encounter-owned cleanup";
			int clearedTasks = BossEncounterRuntime.clearOwnedTasks(boss);
			BossEncounterRuntime.clearOwnedNpcs(boss);
			if (clearedTasks < 1 || encounter.getOwnedTaskCount() != 0)
				throw new IllegalStateException("Prefab encounter-owned task cleanup did not complete.");
			passed++;

			step = "session cleanup and global-state isolation";
			removeCurrentTestBoss(player);
			if (TEST_BOSSES.get(player) != null)
				throw new IllegalStateException("Controlled prefab NPC session was not removed.");
			if (BossDefinitionRegistry.get(npcId) != liveBefore
					|| BossDefinitionRegistry.hasRollback(npcId) != rollbackBefore
					|| BossDefinitionStore.getSaved(npcId) != savedBefore)
				throw new IllegalStateException("Prefab self-test changed global LIVE/SAVED/rollback state.");
			passed++;

			return "Prefab self-test PASS " + passed + "/" + PREFAB_TEST_STEPS
					+ " — spawn, disposable definition, HP/phase, context, owned-task tracking, asymmetric tile attack, cleanup, and global-state isolation passed.";
		} catch (RuntimeException e) {
			throw new IllegalStateException("Prefab self-test FAIL " + passed + "/" + PREFAB_TEST_STEPS
					+ " at " + step + ": " + safeMessage(e), e);
		} finally {
			removeCurrentTestBoss(player);
		}
	}

	private static BossDefinition createPrefabDefinition(int npcId, String npcName) {
		List<BossTileOffset> pattern = Arrays.asList(
				new BossTileOffset(0, 0),
				new BossTileOffset(0, 1),
				new BossTileOffset(1, 0));
		BossAttackDefinition attack = new BossAttackDefinition(
				PREFAB_ATTACK_ID,
				NPCCombatDefinitions.MELEE,
				BossAttackDefinition.USE_NPC_DEFAULT,
				BossAttackDefinition.USE_NPC_DEFAULT,
				BossAttackDefinition.USE_NPC_DEFAULT,
				0,
				1,
				-1,
				-1,
				0,
				pattern,
				-1,
				0,
				1,
				0,
				BossAttackDefinition.TARGET_CURRENT,
				14,
				1,
				0,
				true,
				BossAttackDefinition.TILE_EFFECT_HEAL_BOSS,
				BossAttackDefinition.TILE_EFFECT_HEAL_BOSS);
		BossPhaseDefinition phase = new BossPhaseDefinition(
				PREFAB_PHASE_ID, 1, 100, Arrays.asList(attack));
		String name = npcName == null || npcName.trim().isEmpty() ? "NPC " + npcId : npcName.trim();
		return new BossDefinition("prefab_self_test_" + npcId, name + " Prefab Self-Test", npcId,
				Arrays.asList(phase));
	}

	private static String safeMessage(RuntimeException e) {
		String message = e == null ? null : e.getMessage();
		return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
	}

	public static String setHealthPercent(Player player, int npcId, int percent) {
		NPC boss = requireTestBoss(player, npcId);
		if (percent < 1 || percent > 100)
			throw new IllegalArgumentException("Boss health percent must be between 1 and 100.");
		int maximum = Math.max(1, boss.getMaxHitpoints());
		int hitpoints = Math.max(1, (int) ((maximum * (long) percent) / 100L));
		boss.setHitpoints(hitpoints);
		int actualPercent = (int) ((hitpoints * 100L) / maximum);
		return "Set test NPC HP to " + hitpoints + "/" + maximum + " (" + actualPercent + "%).";
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
		requireLiveDefinition(npcId);
		Entity target = boss.getCombat().getTarget();
		if (target == null || target.hasFinished() || target.isDead())
			target = player;
		int delay = BossCombatScript.INSTANCE.executeAttackForTesting(boss, target, phaseId, attackId);
		return "Triggered " + attackId + " from phase " + phaseId
				+ " through the normal BossLabs attack path (reported delay " + delay + ").";
	}

	public static String clearHazards(Player player, int npcId) {
		NPC boss = requireTestBoss(player, npcId);
		requireLiveDefinition(npcId);
		int count = BossEncounterRuntime.clearOwnedTasks(boss);
		return "Cleared " + count + " BossLabs delayed tile task" + (count == 1 ? "" : "s")
				+ " (hazards/pending telegraphs).";
	}

	public static String clearMinions(Player player, int npcId) {
		NPC boss = requireTestBoss(player, npcId);
		requireLiveDefinition(npcId);
		int count = BossEncounterRuntime.clearOwnedNpcs(boss);
		return "Cleared " + count + " encounter-owned minion" + (count == 1 ? "" : "s") + ".";
	}

	private static BossDefinition requireLiveDefinition(int npcId) {
		BossDefinition definition = BossDefinitionRegistry.get(npcId);
		if (definition == null)
			throw new IllegalArgumentException("Apply a live BossLabs definition before using this BossLabs-only testing control.");
		return definition;
	}

	private static NPC requireTestBoss(Player player, int npcId) {
		requirePlayer(player);
		NPC boss = TEST_BOSSES.get(player);
		if (boss == null || boss.hasFinished() || boss.isDead()) {
			clearSessionReference(player, boss);
			throw new IllegalArgumentException("Spawn this NPC from the Testing tab first.");
		}
		if (boss.getId() != npcId)
			throw new IllegalArgumentException("The active test NPC belongs to a different selected NPC. Spawn this NPC first.");
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

	private static void startSessionWatch(final Player player, final NPC boss, final boolean requireBossLabsRegistration) {
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
				if (player.hasFinished()
						|| (requireBossLabsRegistration && !BossDefinitionRegistry.isRegistered(boss.getId()))) {
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
		if (definitions == null)
			return null;
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
