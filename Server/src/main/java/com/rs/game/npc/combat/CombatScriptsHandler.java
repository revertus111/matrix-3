package com.rs.game.npc.combat;

import java.util.HashMap;

import com.rs.cache.loaders.NPCDefinitions;
import com.rs.game.Entity;
import com.rs.game.npc.NPC;
import com.rs.game.npc.bosslabs.BossCombatScript;
import com.rs.game.npc.bosslabs.BossDefinitionRegistry;
import com.rs.game.npc.bosslabs.BossDefinitionStore;
import com.rs.utils.Logger;
import com.rs.utils.Utils;

public class CombatScriptsHandler {

	private static final HashMap<Object, CombatScript> cachedCombatScripts = new HashMap<Object, CombatScript>();
	private static final CombatScript DEFAULT_SCRIPT = new Default();

	@SuppressWarnings("rawtypes")
	public static final void init() {
		BossDefinitionStore.init();
		try {
			Class[] classes = Utils.getClasses("com.rs.game.npc.combat.impl");
			for (Class c : classes) {
				if (c.isAnonymousClass()) // next
					continue;
				Object o = c.newInstance();
				if (!(o instanceof CombatScript))
					continue;
				CombatScript script = (CombatScript) o;
				for (Object key : script.getKeys())
					cachedCombatScripts.put(key, script);
			}
		} catch (Throwable e) {
			Logger.handle(e);
		}
	}

	public static int specialAttack(final NPC npc, final Entity target) {
		if (BossDefinitionRegistry.isRegistered(npc.getId()))
			return BossCombatScript.INSTANCE.attack(npc, target);

		return resolveMatrix3Script(npc.getId(), npc.getDefinitions().getName()).attack(npc, target);
	}

	public static String getResolvedScriptClassName(int npcId) {
		if (BossDefinitionRegistry.isRegistered(npcId))
			return BossCombatScript.class.getName();
		NPCDefinitions definitions = NPCDefinitions.getNPCDefinitions(npcId);
		return resolveMatrix3Script(npcId, definitions.name).getClass().getName();
	}

	public static boolean isUsingDefaultScript(int npcId) {
		if (BossDefinitionRegistry.isRegistered(npcId))
			return false;
		NPCDefinitions definitions = NPCDefinitions.getNPCDefinitions(npcId);
		return resolveMatrix3Script(npcId, definitions.name) == DEFAULT_SCRIPT;
	}

	private static CombatScript resolveMatrix3Script(int npcId, String npcName) {
		CombatScript script = cachedCombatScripts.get(npcId);
		if (script == null && npcName != null)
			script = cachedCombatScripts.get(npcName);
		return script == null ? DEFAULT_SCRIPT : script;
	}
}
