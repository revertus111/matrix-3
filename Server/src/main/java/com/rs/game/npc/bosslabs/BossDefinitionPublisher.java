package com.rs.game.npc.bosslabs;

import java.io.IOException;

/**
 * Stable BossLabs content publish API used by future developer tooling.
 *
 * Draft editing belongs to the tool. This class only publishes validated,
 * immutable BossDefinition instances to live runtime and/or persistent storage.
 */
public final class BossDefinitionPublisher {

	private BossDefinitionPublisher() {
	}

	/**
	 * Publishes the definition to live runtime only. The previous live state is
	 * retained for one-level rollback.
	 */
	public static void applyLive(BossDefinition definition) {
		BossDefinitionRegistry.applyLive(definition);
		BossEncounterRuntime.requestDefinitionRefresh(definition.getNpcId());
	}

	/**
	 * Persists first. Only after persistence succeeds is the exact same immutable
	 * definition published to live runtime.
	 */
	public static void saveAndApply(BossDefinition definition) throws IOException {
		BossDefinitionStore.save(definition);
		BossDefinitionRegistry.applyLive(definition);
		BossEncounterRuntime.requestDefinitionRefresh(definition.getNpcId());
	}

	/**
	 * Restores the state that existed before the most recent live apply for this
	 * NPC id. Returns false when no live rollback is available.
	 */
	public static boolean undoLastApply(int npcId) {
		if (!BossDefinitionRegistry.hasRollback(npcId))
			return false;
		BossDefinitionRegistry.rollbackLastApply(npcId);
		BossEncounterRuntime.requestDefinitionRefresh(npcId);
		return true;
	}

	/**
	 * Re-publishes the persisted definition for an NPC id without rewriting it.
	 */
	public static boolean applySaved(int npcId) {
		BossDefinition saved = BossDefinitionStore.getSaved(npcId);
		if (saved == null)
			return false;
		BossDefinitionRegistry.applyLive(saved);
		BossEncounterRuntime.requestDefinitionRefresh(npcId);
		return true;
	}

	public static boolean hasLiveRollback(int npcId) {
		return BossDefinitionRegistry.hasRollback(npcId);
	}

	public static BossDefinition getLive(int npcId) {
		return BossDefinitionRegistry.get(npcId);
	}

	public static BossDefinition getSaved(int npcId) {
		return BossDefinitionStore.getSaved(npcId);
	}
}
