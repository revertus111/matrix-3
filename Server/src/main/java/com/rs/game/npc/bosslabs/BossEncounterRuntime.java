package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.cache.loaders.NPCDefinitions;
import com.rs.game.Entity;
import com.rs.game.World;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;
import com.rs.utils.Utils;

/**
 * Runtime owner for BossLabs encounter contexts.
 *
 * Contexts are keyed by live NPC instance and use weak ownership. Matrix3 still
 * owns NPC lifecycle, spawning, combat, and world scheduling; this class only
 * owns BossLabs encounter bookkeeping and cleanup of content it requested.
 */
public final class BossEncounterRuntime {

	private static final int[][] MINION_DIRECTIONS = {
			{0, 1}, {1, 1}, {1, 0}, {1, -1},
			{0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
	};

	private static final Map<NPC, BossEncounterContext> CONTEXTS =
			Collections.synchronizedMap(new WeakHashMap<NPC, BossEncounterContext>());

	private BossEncounterRuntime() {
	}

	public static BossEncounterContext getOrCreate(NPC boss) {
		if (boss == null)
			throw new IllegalArgumentException("Boss encounter NPC must not be null.");

		BossEncounterContext context;
		synchronized (CONTEXTS) {
			context = CONTEXTS.get(boss);
			if (context == null || !context.isActive()) {
				context = new BossEncounterContext(boss);
				CONTEXTS.put(boss, context);
				startLifecycleWatch(context);
			}
		}
		return context;
	}

	public static BossEncounterContext get(NPC boss) {
		if (boss == null)
			return null;
		synchronized (CONTEXTS) {
			return CONTEXTS.get(boss);
		}
	}

	public static int getActiveContextCount() {
		synchronized (CONTEXTS) {
			return CONTEXTS.size();
		}
	}

	/**
	 * Spawns up to the requested number of encounter-owned minions through
	 * Matrix3's existing World.spawnNPC path. Blocked ring slots are skipped.
	 */
	public static int spawnMinions(BossEncounterContext context, int npcId, int amount, int radius) {
		if (context == null || !context.isActive())
			return 0;
		NPC boss = context.getBoss();
		if (boss == null || boss.hasFinished() || boss.isDead())
			return 0;
		if (npcId < 0 || npcId >= Utils.getNPCDefinitionsSize())
			return 0;
		// Nested BossLabs encounters are intentionally deferred. This also blocks
		// accidental self-recursive summon definitions in the first minion slice.
		if (BossDefinitionRegistry.isRegistered(npcId))
			return 0;

		NPCDefinitions definitions = NPCDefinitions.getNPCDefinitions(npcId);
		if (definitions == null)
			return 0;
		int minionSize = Math.max(1, definitions.size);
		int available = BossEncounterContext.MAX_OWNED_NPCS - context.getOwnedNpcCount();
		int requested = Math.min(Math.min(amount, MINION_DIRECTIONS.length), Math.max(0, available));
		if (requested <= 0)
			return 0;

		Entity inheritedTarget = boss.getCombat().getTarget();
		if (inheritedTarget != null && (inheritedTarget.hasFinished() || inheritedTarget.isDead()))
			inheritedTarget = null;

		int spawned = 0;
		for (int slot = 0; slot < requested; slot++) {
			WorldTile spawnTile = resolveMinionSpawnTile(context, boss, minionSize, radius, slot);
			if (spawnTile == null)
				continue;
			NPC minion = World.spawnNPC(npcId, spawnTile, boss.getMapAreaNameHash(),
					boss.canBeAttackFromOutOfArea(), true);
			if (minion == null)
				continue;
			if (!context.trackOwnedNpc(minion)) {
				minion.finish();
				break;
			}
			if (inheritedTarget != null)
				minion.setTarget(inheritedTarget);
			spawned++;
		}
		return spawned;
	}

	private static WorldTile resolveMinionSpawnTile(BossEncounterContext context, NPC boss,
			int minionSize, int radius, int slot) {
		int bossSize = Math.max(1, boss.getSize());
		int gap = Math.max(0, radius - 1) + Math.max(0, minionSize - 1);
		int westX = boss.getX() - minionSize - gap;
		int eastX = boss.getX() + bossSize + gap;
		int southY = boss.getY() - minionSize - gap;
		int northY = boss.getY() + bossSize + gap;
		int centerX = boss.getX() + (bossSize - minionSize) / 2;
		int centerY = boss.getY() + (bossSize - minionSize) / 2;
		int directionX = MINION_DIRECTIONS[slot][0];
		int directionY = MINION_DIRECTIONS[slot][1];
		int x = directionX < 0 ? westX : directionX > 0 ? eastX : centerX;
		int y = directionY < 0 ? southY : directionY > 0 ? northY : centerY;

		if (!World.isTileFree(boss.getPlane(), x, y, minionSize))
			return null;
		if (!isAreaClearOfOwnedNpcs(context, x, y, minionSize, boss.getPlane()))
			return null;
		return new WorldTile(x, y, boss.getPlane());
	}

	private static boolean isAreaClearOfOwnedNpcs(BossEncounterContext context, int x, int y,
			int size, int plane) {
		for (NPC npc : context.getOwnedNpcsSnapshot()) {
			if (npc == null || npc.hasFinished() || npc.getPlane() != plane)
				continue;
			int npcSize = Math.max(1, npc.getSize());
			if (x < npc.getX() + npcSize && x + size > npc.getX()
					&& y < npc.getY() + npcSize && y + size > npc.getY())
				return false;
		}
		return true;
	}

	/**
	 * Publish operations may run away from the world thread. Context generation
	 * and task tokens are invalidated immediately. NPC entity cleanup is queued
	 * back through Matrix3's WorldTasksManager.
	 */
	public static void requestDefinitionRefresh(int npcId) {
		refreshNpcId(npcId);
	}

	private static void refreshNpcId(int npcId) {
		List<BossEncounterContext> snapshot;
		synchronized (CONTEXTS) {
			snapshot = new ArrayList<BossEncounterContext>(CONTEXTS.values());
		}

		boolean stillRegistered = BossDefinitionRegistry.isRegistered(npcId);
		for (BossEncounterContext context : snapshot) {
			NPC boss = context.getBoss();
			if (boss == null) {
				queueOwnedNpcCleanup(context.finish());
				continue;
			}
			if (boss.getId() != npcId)
				continue;
			if (stillRegistered) {
				queueOwnedNpcCleanup(context.resetTransientRuntime());
			} else {
				removeContext(boss, context);
				queueOwnedNpcCleanup(context.finish());
			}
		}
	}

	private static void startLifecycleWatch(final BossEncounterContext context) {
		WorldTask lifecycleTask = new WorldTask() {
			@Override
			public void run() {
				NPC boss = context.getBoss();
				if (boss == null) {
					finishFromWorld(context, null);
					stop();
					return;
				}
				if (boss.hasFinished() || boss.isDead() || !BossDefinitionRegistry.isRegistered(boss.getId())) {
					finishFromWorld(context, boss);
					stop();
				}
			}
		};
		context.setLifecycleTask(lifecycleTask);
		WorldTasksManager.schedule(lifecycleTask, 0, 0);
	}

	private static void finishFromWorld(BossEncounterContext context, NPC boss) {
		if (boss != null)
			removeContext(boss, context);
		finishOwnedNpcs(context.finish());
	}

	private static void removeContext(NPC boss, BossEncounterContext context) {
		synchronized (CONTEXTS) {
			if (CONTEXTS.get(boss) == context)
				CONTEXTS.remove(boss);
		}
	}

	private static void queueOwnedNpcCleanup(final List<NPC> npcs) {
		if (npcs == null || npcs.isEmpty())
			return;
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				finishOwnedNpcs(npcs);
				stop();
			}
		});
	}

	private static void finishOwnedNpcs(List<NPC> npcs) {
		for (NPC npc : npcs) {
			if (npc != null && !npc.hasFinished())
				npc.finish();
		}
	}
}
