package com.rs.game.npc.bosslabs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.rs.executor.GameExecutorManager;
import com.rs.game.player.Player;

/**
 * Server-side BossLabs developer bridge carried over Matrix3's existing command
 * packet and panel-message reply packet.
 *
 * Requests are admitted only through the existing command/rights authority.
 * This bridge delegates content lookup/publishing to BossLabs owners and never
 * becomes a second NPC/combat/world authority.
 */
public final class BossLabsCommandBridge {

	private static final String RESPONSE_PREFIX = "bosslabs|";
	private static final int SEARCH_LIMIT = 20;
	private static final int MAX_TEXT_LENGTH = 80;

	private BossLabsCommandBridge() {
	}

	public static boolean process(final Player player, final String[] cmd) {
		if (player == null || player.getRights() < 2)
			return false;
		if (cmd == null || cmd.length < 2) {
			sendAction(player, 0, false, -1, "Missing BossLabs action.");
			return true;
		}

		String action = cmd[1].toLowerCase();
		try {
			if ("search".equals(action)) {
				processSearch(player, cmd);
				return true;
			}
			if ("inspect".equals(action)) {
				processInspect(player, cmd);
				return true;
			}
			if ("apply".equals(action)) {
				processApply(player, cmd, false);
				return true;
			}
			if ("saveapply".equals(action)) {
				processApply(player, cmd, true);
				return true;
			}
			if ("undo".equals(action)) {
				processUndo(player, cmd);
				return true;
			}
			if ("applysaved".equals(action)) {
				processApplySaved(player, cmd);
				return true;
			}
			sendAction(player, readRequestId(cmd, 2), false, -1, "Unknown BossLabs action: " + action);
		} catch (RuntimeException e) {
			sendAction(player, readRequestId(cmd, 2), false, -1, safeMessage(e));
		}
		return true;
	}

	private static void processSearch(final Player player, String[] cmd) {
		if (cmd.length < 4) {
			sendSearchError(player, readRequestId(cmd, 2), "Search requires a query.");
			return;
		}
		final int requestId = parseInt(cmd[2], "request id");
		final String query = decode(cmd[3]);
		send(player, RESPONSE_PREFIX + "search-begin|" + requestId);

		GameExecutorManager.slowExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					List<BossNpcInspection> results = BossNpcSearchService.search(query, SEARCH_LIMIT);
					for (BossNpcInspection result : results)
						sendSearchResult(player, requestId, result);
					send(player, RESPONSE_PREFIX + "search-end|" + requestId + "|" + results.size());
				} catch (RuntimeException e) {
					sendSearchError(player, requestId, safeMessage(e));
				}
			}
		});
	}

	private static void processInspect(Player player, String[] cmd) {
		if (cmd.length < 4) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Inspect requires an NPC id.");
			return;
		}
		int requestId = parseInt(cmd[2], "request id");
		int npcId = parseInt(cmd[3], "NPC id");
		sendInspection(player, requestId, npcId);
	}

	private static void processApply(final Player player, String[] cmd, final boolean save) {
		if (cmd.length < 6) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Apply requires NPC id, definition id, and display name.");
			return;
		}
		final int requestId = parseInt(cmd[2], "request id");
		final int npcId = parseInt(cmd[3], "NPC id");
		final String definitionId = decode(cmd[4]);
		final String displayName = decode(cmd[5]);
		final BossDefinition current = BossDefinitionPublisher.getLive(npcId);
		if (current == null) {
			sendAction(player, requestId, false, npcId,
					"This NPC has no BossLabs definition yet. Add its first phase/attack definition before publishing.");
			return;
		}

		final BossDefinition next;
		try {
			next = new BossDefinition(definitionId, displayName, npcId, current.getPhases());
		} catch (IllegalArgumentException e) {
			sendAction(player, requestId, false, npcId, safeMessage(e));
			return;
		}

		if (!save) {
			BossDefinitionPublisher.applyLive(next);
			sendAction(player, requestId, true, npcId, "Applied draft to live BossLabs runtime.");
			sendInspection(player, requestId, npcId);
			return;
		}

		GameExecutorManager.slowExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					BossDefinitionPublisher.saveAndApply(next);
					sendAction(player, requestId, true, npcId, "Saved and applied BossLabs definition.");
					sendInspection(player, requestId, npcId);
				} catch (IOException e) {
					sendAction(player, requestId, false, npcId, "Save failed: " + safeMessage(e));
				} catch (RuntimeException e) {
					sendAction(player, requestId, false, npcId, safeMessage(e));
				}
			}
		});
	}

	private static void processUndo(Player player, String[] cmd) {
		if (cmd.length < 4) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Undo requires an NPC id.");
			return;
		}
		int requestId = parseInt(cmd[2], "request id");
		int npcId = parseInt(cmd[3], "NPC id");
		boolean restored = BossDefinitionPublisher.undoLastApply(npcId);
		sendAction(player, requestId, restored, npcId,
				restored ? "Restored the previous live definition." : "No live BossLabs rollback is available.");
		sendInspection(player, requestId, npcId);
	}

	private static void processApplySaved(Player player, String[] cmd) {
		if (cmd.length < 4) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Apply Saved requires an NPC id.");
			return;
		}
		int requestId = parseInt(cmd[2], "request id");
		int npcId = parseInt(cmd[3], "NPC id");
		boolean applied = BossDefinitionPublisher.applySaved(npcId);
		sendAction(player, requestId, applied, npcId,
				applied ? "Re-applied the saved BossLabs definition." : "No saved BossLabs definition exists for this NPC.");
		sendInspection(player, requestId, npcId);
	}

	private static void sendSearchResult(Player player, int requestId, BossNpcInspection inspection) {
		String response = RESPONSE_PREFIX + "search-result|" + requestId + "|" + inspection.getNpcId() + "|"
				+ encode(inspection.getName()) + "|" + inspection.getCombatLevel() + "|"
				+ inspection.getCombatSource().name() + "|" + (inspection.isBossLabsDefinition() ? 1 : 0);
		send(player, response);
	}

	private static void sendSearchError(Player player, int requestId, String message) {
		send(player, RESPONSE_PREFIX + "search-error|" + requestId + "|" + encode(message));
	}

	private static void sendInspection(Player player, int requestId, int npcId) {
		BossNpcInspection inspection = BossNpcSearchService.inspect(npcId);
		if (inspection == null) {
			send(player, RESPONSE_PREFIX + "inspect-missing|" + requestId + "|" + npcId);
			return;
		}

		String core = RESPONSE_PREFIX + "inspect|" + requestId + "|" + inspection.getNpcId() + "|"
				+ encode(inspection.getName()) + "|" + inspection.getCombatLevel() + "|" + inspection.getSize() + "|"
				+ inspection.getHitpoints() + "|" + inspection.getAttackSpeed() + "|" + inspection.getAttackAnimation()
				+ "|" + inspection.getDefenceAnimation() + "|" + inspection.getDeathAnimation() + "|"
				+ inspection.getRespawnDelay() + "|" + inspection.getAttackGraphic() + "|"
				+ inspection.getAttackProjectile() + "|" + (inspection.isAggressive() ? 1 : 0) + "|"
				+ inspection.getAggressionRange() + "|" + (inspection.isPoisonImmune() ? 1 : 0) + "|"
				+ inspection.getCombatSource().name();
		send(player, core);

		BossDefinition definition = inspection.getBossDefinition();
		String ownership = RESPONSE_PREFIX + "ownership|" + requestId + "|" + npcId + "|"
				+ encode(inspection.getCombatScriptSimpleName()) + "|" + (definition != null ? 1 : 0) + "|"
				+ encode(definition == null ? "" : definition.getId()) + "|"
				+ encode(definition == null ? "" : definition.getDisplayName()) + "|"
				+ (BossDefinitionStore.isSaved(npcId) ? 1 : 0) + "|"
				+ (BossDefinitionPublisher.hasLiveRollback(npcId) ? 1 : 0);
		send(player, ownership);
	}

	private static void sendAction(Player player, int requestId, boolean success, int npcId, String message) {
		send(player, RESPONSE_PREFIX + "action|" + requestId + "|" + (success ? 1 : 0) + "|" + npcId + "|"
				+ encode(message));
	}

	private static void send(Player player, String response) {
		if (player == null || player.hasFinished())
			return;
		// Type 99 already has a dedicated small client console-message path. The
		// BossLabs prefix is consumed before normal console rendering.
		player.getPackets().sendMessage(99, response, null);
	}

	private static int readRequestId(String[] cmd, int index) {
		if (cmd == null || index < 0 || index >= cmd.length)
			return 0;
		try {
			return Integer.parseInt(cmd[index]);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int parseInt(String value, String label) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid " + label + ": " + value);
		}
	}

	private static String encode(String value) {
		String safe = value == null ? "" : value;
		if (safe.length() > MAX_TEXT_LENGTH)
			safe = safe.substring(0, MAX_TEXT_LENGTH);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String value) {
		if (value == null || value.length() == 0)
			return "";
		try {
			return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid BossLabs text payload.");
		}
	}

	private static String safeMessage(Throwable throwable) {
		if (throwable == null)
			return "BossLabs request failed.";
		String message = throwable.getMessage();
		return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
	}
}
