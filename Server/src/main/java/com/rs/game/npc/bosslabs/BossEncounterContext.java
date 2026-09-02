package com.rs.game.npc.bosslabs;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.rs.game.npc.NPC;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;

/**
 * Runtime-only context for one live BossLabs NPC instance.
 *
 * Saved boss content remains in BossDefinition. This context only tracks
 * instance-scoped runtime state that must be invalidated or cleaned up safely.
 */
public final class BossEncounterContext {

	private final WeakReference<NPC> bossReference;
	private final Set<Player> participants = Collections.newSetFromMap(new WeakHashMap<Player, Boolean>());
	private final Set<WorldTask> ownedTasks = Collections.newSetFromMap(new IdentityHashMap<WorldTask, Boolean>());

	private long generation = 1L;
	private boolean active = true;
	private WorldTask lifecycleTask;

	BossEncounterContext(NPC boss) {
		if (boss == null)
			throw new IllegalArgumentException("Boss encounter NPC must not be null.");
		bossReference = new WeakReference<NPC>(boss);
	}

	public NPC getBoss() {
		return bossReference.get();
	}

	public synchronized boolean isActive() {
		return active;
	}

	public synchronized long getGeneration() {
		return generation;
	}

	public synchronized boolean isGenerationActive(long expectedGeneration) {
		return active && generation == expectedGeneration;
	}

	public synchronized void registerParticipant(Player player) {
		if (!active || player == null)
			return;
		participants.add(player);
	}

	public synchronized int getParticipantCount() {
		return participants.size();
	}

	public synchronized List<Player> getParticipantsSnapshot() {
		return Collections.unmodifiableList(new ArrayList<Player>(participants));
	}

	public synchronized int getOwnedTaskCount() {
		return ownedTasks.size();
	}

	synchronized void trackTask(WorldTask task) {
		if (!active || task == null)
			return;
		ownedTasks.add(task);
	}

	synchronized void untrackTask(WorldTask task) {
		if (task != null)
			ownedTasks.remove(task);
	}

	synchronized void setLifecycleTask(WorldTask task) {
		lifecycleTask = task;
	}

	/**
	 * Invalidates BossLabs-owned delayed runtime work while preserving the
	 * observed participant set for the still-live encounter instance.
	 */
	synchronized void resetTransientRuntime() {
		if (!active)
			return;
		generation++;
		for (WorldTask task : ownedTasks)
			task.stop();
		ownedTasks.clear();
	}

	/**
	 * Final cleanup for an encounter instance that is no longer BossLabs-owned
	 * or whose NPC has died/finished.
	 */
	synchronized void finish() {
		if (!active)
			return;
		active = false;
		generation++;
		for (WorldTask task : ownedTasks)
			task.stop();
		ownedTasks.clear();
		participants.clear();
		if (lifecycleTask != null)
			lifecycleTask.stop();
		lifecycleTask = null;
	}
}
