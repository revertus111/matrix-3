package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.game.npc.NPC;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

/**
 * Runtime owner for BossLabs encounter contexts.
 *
 * Contexts are keyed by live NPC instance and use weak ownership. Matrix3 still
 * owns NPC lifecycle and world scheduling; this class only observes lifecycle
 * and invalidates BossLabs-owned transient work when required.
 */
public final class BossEncounterRuntime {

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
	 * Publish operations may run away from the world thread. Context state is
	 * synchronized and deliberately invalidated immediately so already-queued
	 * BossLabs tasks cannot perform one stale impact/tick before the next world
	 * cycle. No Matrix3 world entity mutation is performed here.
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
				context.finish();
				continue;
			}
			if (boss.getId() != npcId)
				continue;
			if (stillRegistered)
				context.resetTransientRuntime();
			else
				finish(boss);
		}
	}

	private static void startLifecycleWatch(final BossEncounterContext context) {
		WorldTask lifecycleTask = new WorldTask() {
			@Override
			public void run() {
				NPC boss = context.getBoss();
				if (boss == null) {
					context.finish();
					stop();
					return;
				}
				if (boss.hasFinished() || boss.isDead() || !BossDefinitionRegistry.isRegistered(boss.getId())) {
					finish(boss);
					stop();
				}
			}
		};
		context.setLifecycleTask(lifecycleTask);
		WorldTasksManager.schedule(lifecycleTask, 0, 0);
	}

	private static void finish(NPC boss) {
		BossEncounterContext context;
		synchronized (CONTEXTS) {
			context = CONTEXTS.remove(boss);
		}
		if (context != null)
			context.finish();
	}
}
