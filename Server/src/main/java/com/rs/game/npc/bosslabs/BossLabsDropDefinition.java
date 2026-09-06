package com.rs.game.npc.bosslabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rs.cache.loaders.ItemDefinitions;
import com.rs.game.npc.Drop;
import com.rs.game.npc.Drops;

/**
 * Immutable BossLabs representation of one NPC drop-table override.
 *
 * Matrix3 Drops/NPCDrops remain the runtime authority. This class only stores
 * creator-authored content in Matrix3's existing rarity/min/max model.
 */
public final class BossLabsDropDefinition {

	public static final int MAX_ENTRIES = 200;
	public static final int MAX_AMOUNT = 1000000000;

	private final int npcId;
	private final boolean accessRareDropTable;
	private final List<Entry> entries;

	public BossLabsDropDefinition(int npcId, boolean accessRareDropTable, List<Entry> entries) {
		if (npcId < 0)
			throw new IllegalArgumentException("BossLabs drop NPC id must be zero or greater.");
		List<Entry> safe = entries == null ? Collections.<Entry>emptyList() : entries;
		if (safe.size() > MAX_ENTRIES)
			throw new IllegalArgumentException("BossLabs drop table exceeds " + MAX_ENTRIES + " entries.");
		for (Entry entry : safe) {
			if (entry == null)
				throw new IllegalArgumentException("BossLabs drop table must not contain null entries.");
		}
		this.npcId = npcId;
		this.accessRareDropTable = accessRareDropTable;
		this.entries = Collections.unmodifiableList(new ArrayList<Entry>(safe));
	}

	public int getNpcId() {
		return npcId;
	}

	public boolean canAccessRareDropTable() {
		return accessRareDropTable;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public void validateItems() {
		for (Entry entry : entries) {
			String validation = entry.validate();
			if (validation != null)
				throw new IllegalArgumentException(validation);
			ItemDefinitions definitions = ItemDefinitions.getItemDefinitions(entry.getItemId());
			if (definitions == null || !definitions.isLoaded())
				throw new IllegalArgumentException("Unknown/unloaded item id in BossLabs drops: " + entry.getItemId());
		}
	}

	@SuppressWarnings("unchecked")
	public Drops toMatrixDrops() {
		Drops drops = new Drops(accessRareDropTable);
		List<Drop>[] lists = new ArrayList[Drops.VERY_RARE + 1];
		for (Entry entry : entries) {
			String validation = entry.validate();
			if (validation != null)
				throw new IllegalArgumentException(validation);
			if (lists[entry.getRarity()] == null)
				lists[entry.getRarity()] = new ArrayList<Drop>();
			lists[entry.getRarity()].add(new Drop(entry.getItemId(), entry.getMinAmount(), entry.getMaxAmount()));
		}
		drops.addDrops(lists);
		return drops;
	}

	public static BossLabsDropDefinition fromMatrixDrops(int npcId, Drops drops) {
		List<Entry> entries = new ArrayList<Entry>();
		if (drops != null) {
			for (int rarity = Drops.ALWAYS; rarity <= Drops.VERY_RARE; rarity++) {
				Drop[] matrixDrops = drops.getAllDropsForEditing(rarity);
				if (matrixDrops == null)
					continue;
				for (Drop drop : matrixDrops) {
					if (drop != null)
						entries.add(Entry.fromMatrix(rarity, drop.getItemId(), drop.getMinAmount(), drop.getMaxAmount()));
				}
			}
		}
		return new BossLabsDropDefinition(npcId, drops != null && drops.canAccessRareDropTable(), entries);
	}

	public static String rarityName(int rarity) {
		switch (rarity) {
		case Drops.ALWAYS:
			return "Always";
		case Drops.COMMOM:
			return "Common";
		case Drops.UNCOMMON:
			return "Uncommon";
		case Drops.RARE:
			return "Rare";
		case Drops.VERY_RARE:
			return "Very Rare";
		default:
			return "Unknown";
		}
	}

	public static final class Entry {
		private final int rarity;
		private final int itemId;
		private final int minAmount;
		private final int maxAmount;

		public Entry(int rarity, int itemId, int minAmount, int maxAmount) {
			this(rarity, itemId, minAmount, maxAmount, true);
		}

		private Entry(int rarity, int itemId, int minAmount, int maxAmount, boolean validateForPublish) {
			if (rarity < Drops.ALWAYS || rarity > Drops.VERY_RARE)
				throw new IllegalArgumentException("Unsupported Matrix3 drop rarity: " + rarity);
			if (itemId < 0)
				throw new IllegalArgumentException("Drop item id must be zero or greater.");
			if (minAmount < 0 || minAmount > MAX_AMOUNT)
				throw new IllegalArgumentException("Drop minimum amount must be between 0 and " + MAX_AMOUNT + ".");
			if (maxAmount < 0 || maxAmount > MAX_AMOUNT)
				throw new IllegalArgumentException("Drop maximum amount must be between 0 and " + MAX_AMOUNT + ".");
			this.rarity = rarity;
			this.itemId = itemId;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			if (validateForPublish) {
				String validation = validate();
				if (validation != null)
					throw new IllegalArgumentException(validation);
			}
		}

		private static Entry fromMatrix(int rarity, int itemId, int minAmount, int maxAmount) {
			return new Entry(rarity, itemId, minAmount, maxAmount, false);
		}

		public int getRarity() {
			return rarity;
		}

		public int getItemId() {
			return itemId;
		}

		public int getMinAmount() {
			return minAmount;
		}

		public int getMaxAmount() {
			return maxAmount;
		}

		private String validate() {
			if (maxAmount < minAmount)
				return "Drop maximum amount must be at least the minimum amount.";
			return null;
		}
	}
}
