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

	private BossDefinitionRegistry() {
	}

	public static void register(BossDefinition definition) {
		if (definition == null)
			throw new IllegalArgumentException("Boss definition must not be null.");
		DEFINITIONS.put(definition.getNpcId(), definition);
	}

	public static BossDefinition get(int npcId) {
		return DEFINITIONS.get(npcId);
	}

	public static BossDefinition unregister(int npcId) {
		return DEFINITIONS.remove(npcId);
	}

	public static boolean isRegistered(int npcId) {
		return DEFINITIONS.containsKey(npcId);
	}
}
