package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.rs.cache.Cache;
import com.rs.cache.loaders.NPCDefinitions;
import com.rs.game.npc.bosslabs.BossNpcInspection.CombatSource;
import com.rs.game.npc.combat.CombatScriptsHandler;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.utils.NPCCombatDefinitionsL;
import com.rs.utils.Utils;

/**
 * BossLabs NPC lookup/inspection service.
 *
 * Numeric input is treated as an NPC id automatically. Non-numeric input is
 * treated as a case-insensitive NPC-name search. The name index is built lazily
 * from Matrix3's cache-backed NPC definition range and should be initialized or
 * queried away from a Swing event-dispatch thread by the future BossLabs UI.
 */
public final class BossNpcSearchService {

	private static volatile List<NameEntry> nameIndex;
	private static final Object INDEX_LOCK = new Object();

	private BossNpcSearchService() {
	}

	public static List<BossNpcInspection> search(String query, int limit) {
		if (query == null || limit <= 0)
			return Collections.emptyList();

		String trimmed = query.trim();
		if (trimmed.isEmpty())
			return Collections.emptyList();

		if (isNumeric(trimmed)) {
			Integer npcId = parseNpcId(trimmed);
			if (npcId == null)
				return Collections.emptyList();
			BossNpcInspection inspection = inspect(npcId.intValue());
			return inspection == null ? Collections.<BossNpcInspection>emptyList()
					: Collections.singletonList(inspection);
		}

		String normalizedQuery = normalize(trimmed);
		List<NameEntry> matches = new ArrayList<NameEntry>();
		for (NameEntry entry : getOrBuildNameIndex()) {
			if (entry.normalizedName.contains(normalizedQuery))
				matches.add(entry);
		}

		Collections.sort(matches, new NameMatchComparator(normalizedQuery));
		int resultCount = Math.min(limit, matches.size());
		List<BossNpcInspection> results = new ArrayList<BossNpcInspection>(resultCount);
		for (int i = 0; i < resultCount; i++) {
			BossNpcInspection inspection = inspect(matches.get(i).npcId);
			if (inspection != null)
				results.add(inspection);
		}
		return Collections.unmodifiableList(results);
	}

	public static BossNpcInspection inspect(int npcId) {
		if (!npcExists(npcId))
			return null;

		NPCDefinitions npcDefinitions = NPCDefinitions.getNPCDefinitions(npcId);
		NPCCombatDefinitions combatDefinitions = NPCCombatDefinitionsL.getNPCCombatDefinitions(npcId);
		BossDefinition bossDefinition = BossDefinitionRegistry.get(npcId);

		CombatSource combatSource;
		if (bossDefinition != null)
			combatSource = CombatSource.BOSSLABS;
		else if (CombatScriptsHandler.isUsingDefaultScript(npcId))
			combatSource = CombatSource.MATRIX3_DEFAULT;
		else
			combatSource = CombatSource.MATRIX3_SCRIPT;

		return new BossNpcInspection(npcId, safeName(npcDefinitions.name), npcDefinitions.combatLevel,
				npcDefinitions.size, npcDefinitions.modelIds, combatDefinitions.getHitpoints(),
				getAttackSpeed(npcDefinitions), combatDefinitions.getAttackEmote(), combatDefinitions.getDefenceEmote(),
				combatDefinitions.getDeathEmote(), combatDefinitions.getRespawnDelay(), combatDefinitions.getAttackGfx(),
				combatDefinitions.getAttackProjectile(), combatDefinitions.isAgressive(), combatDefinitions.getAgroRatio(),
				combatDefinitions.isPoisonImmune(), combatSource,
				CombatScriptsHandler.getResolvedScriptClassName(npcId), bossDefinition);
	}

	public static void invalidateNameIndex() {
		synchronized (INDEX_LOCK) {
			nameIndex = null;
		}
	}

	private static List<NameEntry> getOrBuildNameIndex() {
		List<NameEntry> current = nameIndex;
		if (current != null)
			return current;

		synchronized (INDEX_LOCK) {
			if (nameIndex != null)
				return nameIndex;

			int definitionCount = Utils.getNPCDefinitionsSize();
			List<NameEntry> built = new ArrayList<NameEntry>();
			for (int npcId = 0; npcId < definitionCount; npcId++) {
				if (!npcExists(npcId))
					continue;
				NPCDefinitions definitions = NPCDefinitions.getNPCDefinitions(npcId);
				String name = safeName(definitions.name);
				if (name.isEmpty())
					continue;
				built.add(new NameEntry(npcId, name));
			}
			nameIndex = Collections.unmodifiableList(built);
			return nameIndex;
		}
	}

	private static int getAttackSpeed(NPCDefinitions definitions) {
		Map<Integer, Object> data = definitions.clientScriptData;
		if (data != null) {
			Object value = data.get(14);
			if (value instanceof Integer)
				return ((Integer) value).intValue();
		}
		return 4;
	}

	private static boolean npcExists(int npcId) {
		if (npcId < 0 || Cache.STORE == null || Cache.STORE.getIndexes().length <= 18
				|| Cache.STORE.getIndexes()[18] == null)
			return false;
		return Cache.STORE.getIndexes()[18].fileExists(npcId >>> 7, npcId & 0x7f);
	}

	private static boolean isNumeric(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i)))
				return false;
		}
		return !value.isEmpty();
	}

	private static Integer parseNpcId(String value) {
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String safeName(String name) {
		return name == null ? "" : name.trim();
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ENGLISH);
	}

	private static final class NameEntry {
		private final int npcId;
		private final String name;
		private final String normalizedName;

		private NameEntry(int npcId, String name) {
			this.npcId = npcId;
			this.name = name;
			this.normalizedName = normalize(name);
		}
	}

	private static final class NameMatchComparator implements Comparator<NameEntry> {
		private final String query;

		private NameMatchComparator(String query) {
			this.query = query;
		}

		@Override
		public int compare(NameEntry first, NameEntry second) {
			int firstRank = rank(first.normalizedName);
			int secondRank = rank(second.normalizedName);
			if (firstRank != secondRank)
				return firstRank - secondRank;

			int nameCompare = first.name.compareToIgnoreCase(second.name);
			if (nameCompare != 0)
				return nameCompare;
			return first.npcId - second.npcId;
		}

		private int rank(String name) {
			if (name.equals(query))
				return 0;
			if (name.startsWith(query))
				return 1;
			return 2;
		}
	}
}
