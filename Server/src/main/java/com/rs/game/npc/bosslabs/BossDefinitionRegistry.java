package com.rs.game.npc.bosslabs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small runtime registry for BossLabs boss definitions.
 *
 * This registry only maps an NPC id to immutable BossLabs content data. It does
 * not own NPC spawning, combat timing, targeting, damage, death, or drops.
 */
public final class BossDefinitionRegistry {

	private static final Map<Integer, BossDefinition> DEFINITIONS = new ConcurrentHashMap<Integer, BossDefinition>();
	private static final Map<Integer, PreviousDefinitionState> PREVIOUS_DEFINITIONS = new ConcurrentHashMap<Integer, PreviousDefinitionState>();

	private BossDefinitionRegistry() {
	}

	/**
	 * Registers a definition without creating live-edit rollback history. This is
	 * intended for initial/startup content loading.
	 */
	public static synchronized void register(BossDefinition definition) {
		validate(definition);
		DEFINITIONS.put(definition.getNpcId(), definition);
		PREVIOUS_DEFINITIONS.remove(definition.getNpcId());
	}

	/**
	 * Atomically replaces the active BossLabs definition and keeps one previous
	 * live state so a developer can undo the last apply.
	 */
	public static synchronized BossDefinition applyLive(BossDefinition definition) {
		validate(definition);
		int npcId = definition.getNpcId();
		BossDefinition previous = DEFINITIONS.get(npcId);
		PREVIOUS_DEFINITIONS.put(npcId, new PreviousDefinitionState(previous));
		DEFINITIONS.put(npcId, definition);
		return previous;
	}

	/**
	 * Restores the state that existed immediately before the last live apply.
	 * A null return may mean the rollback restored the NPC to an unregistered
	 * Matrix3-script/default state; call hasRollback() before invoking when the
	 * distinction matters to UI code.
	 */
	public static synchronized BossDefinition rollbackLastApply(int npcId) {
		PreviousDefinitionState previousState = PREVIOUS_DEFINITIONS.remove(npcId);
		if (previousState == null)
			return null;
		if (previousState.definition == null)
			DEFINITIONS.remove(npcId);
		else
			DEFINITIONS.put(npcId, previousState.definition);
		return previousState.definition;
	}

	public static boolean hasRollback(int npcId) {
		return PREVIOUS_DEFINITIONS.containsKey(npcId);
	}

	public static BossDefinition get(int npcId) {
		return DEFINITIONS.get(npcId);
	}

	public static synchronized BossDefinition unregister(int npcId) {
		PREVIOUS_DEFINITIONS.remove(npcId);
		return DEFINITIONS.remove(npcId);
	}

	public static boolean isRegistered(int npcId) {
		return DEFINITIONS.containsKey(npcId);
	}

	private static void validate(BossDefinition definition) {
		if (definition == null)
			throw new IllegalArgumentException("Boss definition must not be null.");
	}

	private static final class PreviousDefinitionState {
		private final BossDefinition definition;

		private PreviousDefinitionState(BossDefinition definition) {
			this.definition = definition;
		}
	}
}
