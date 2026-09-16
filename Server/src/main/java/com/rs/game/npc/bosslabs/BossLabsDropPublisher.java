package com.rs.game.npc.bosslabs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.rs.game.npc.Drops;
import com.rs.utils.NPCDrops;

/**
 * Live BossLabs drop override authority over Matrix3's existing NPCDrops map.
 * Matrix3 drop generation remains unchanged.
 */
public final class BossLabsDropPublisher {

	private static final Object LOCK = new Object();
	private static final Map<Integer, Drops> MATRIX_BASELINES = new HashMap<Integer, Drops>();
	private static final Set<Integer> BASELINE_CAPTURED = new HashSet<Integer>();
	private static final Map<Integer, BossLabsDropDefinition> LIVE = new HashMap<Integer, BossLabsDropDefinition>();
	private static final Map<Integer, RollbackState> ROLLBACK = new HashMap<Integer, RollbackState>();

	private BossLabsDropPublisher() {
	}

	public static void applyLive(BossLabsDropDefinition definition) {
		if (definition == null)
			throw new IllegalArgumentException("BossLabs drop definition must not be null.");
		definition.validateItems();
		int npcId = definition.getNpcId();
		synchronized (LOCK) {
			captureBaseline(npcId);
			ROLLBACK.put(npcId, new RollbackState(NPCDrops.getDrops(npcId), LIVE.get(npcId)));
			NPCDrops.addDrops(npcId, definition.toMatrixDrops());
			LIVE.put(npcId, definition);
		}
	}

	static void bootstrapSaved(BossLabsDropDefinition definition) {
		if (definition == null)
			return;
		definition.validateItems();
		int npcId = definition.getNpcId();
		synchronized (LOCK) {
			captureBaseline(npcId);
			NPCDrops.addDrops(npcId, definition.toMatrixDrops());
			LIVE.put(npcId, definition);
			ROLLBACK.remove(npcId);
		}
	}

	public static boolean applySaved(int npcId) {
		BossLabsDropDefinition saved = BossLabsDropStore.getSaved(npcId);
		if (saved == null)
			return false;
		applyLive(saved);
		return true;
	}

	public static boolean restoreMatrix(int npcId) {
		synchronized (LOCK) {
			captureBaseline(npcId);
			BossLabsDropDefinition current = LIVE.get(npcId);
			Drops currentDrops = NPCDrops.getDrops(npcId);
			if (current == null && currentDrops == MATRIX_BASELINES.get(npcId))
				return false;
			ROLLBACK.put(npcId, new RollbackState(currentDrops, current));
			restoreDrops(npcId, MATRIX_BASELINES.get(npcId));
			LIVE.remove(npcId);
			return true;
		}
	}

	public static boolean undoLastApply(int npcId) {
		synchronized (LOCK) {
			RollbackState state = ROLLBACK.remove(npcId);
			if (state == null)
				return false;
			restoreDrops(npcId, state.previousDrops);
			if (state.previousDefinition == null)
				LIVE.remove(npcId);
			else
				LIVE.put(npcId, state.previousDefinition);
			return true;
		}
	}

	public static BossLabsDropDefinition getLive(int npcId) {
		synchronized (LOCK) {
			return LIVE.get(npcId);
		}
	}

	public static boolean hasLiveOverride(int npcId) {
		synchronized (LOCK) {
			return LIVE.containsKey(npcId);
		}
	}

	public static boolean hasRollback(int npcId) {
		synchronized (LOCK) {
			return ROLLBACK.containsKey(npcId);
		}
	}

	public static BossLabsDropDefinition inspectCurrent(int npcId) {
		synchronized (LOCK) {
			return BossLabsDropDefinition.fromMatrixDrops(npcId, NPCDrops.getDrops(npcId));
		}
	}

	private static void captureBaseline(int npcId) {
		if (BASELINE_CAPTURED.add(Integer.valueOf(npcId)))
			MATRIX_BASELINES.put(Integer.valueOf(npcId), NPCDrops.getDrops(npcId));
	}

	private static void restoreDrops(int npcId, Drops drops) {
		if (drops == null)
			NPCDrops.removeDrops(npcId);
		else
			NPCDrops.addDrops(npcId, drops);
	}

	private static final class RollbackState {
		private final Drops previousDrops;
		private final BossLabsDropDefinition previousDefinition;

		private RollbackState(Drops previousDrops, BossLabsDropDefinition previousDefinition) {
			this.previousDrops = previousDrops;
			this.previousDefinition = previousDefinition;
		}
	}
}
