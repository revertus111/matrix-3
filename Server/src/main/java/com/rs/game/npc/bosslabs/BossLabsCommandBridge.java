package com.rs.game.npc.bosslabs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.executor.GameExecutorManager;
import com.rs.game.map.bossInstance.BossInstanceHandler;
import com.rs.game.map.bossInstance.BossInstanceHandler.Boss;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

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
	private static final int MAX_UPLOAD_CHUNKS = 28;
	private static final int MAX_UPLOAD_CHUNK_LENGTH = 190;
	private static final int RESPONSE_CHUNK_LENGTH = 160;

	private static final Map<Player, DraftUpload> DRAFT_UPLOADS =
			Collections.synchronizedMap(new WeakHashMap<Player, DraftUpload>());

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
			if ("uploadbegin".equals(action)) {
				processUploadBegin(player, cmd);
				return true;
			}
			if ("uploadchunk".equals(action)) {
				processUploadChunk(player, cmd);
				return true;
			}
			if ("uploadcommit".equals(action)) {
				processUploadCommit(player, cmd);
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
			if ("testing".equals(action)) {
				processTesting(player, cmd);
				return true;
			}
			if ("rots".equals(action) || "riseofthesix".equals(action)) {
				processRiseOfTheSix(player, cmd);
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

	private static void processUploadBegin(Player player, String[] cmd) {
		if (cmd.length < 5) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Draft upload begin is incomplete.");
			return;
		}
		int requestId = parseInt(cmd[2], "request id");
		boolean save = "1".equals(cmd[3]);
		int chunkCount = parseInt(cmd[4], "chunk count");
		if (chunkCount <= 0 || chunkCount > MAX_UPLOAD_CHUNKS) {
			sendAction(player, requestId, false, -1, "BossLabs draft exceeds the supported bridge transaction size.");
			return;
		}
		DRAFT_UPLOADS.put(player, new DraftUpload(requestId, save, chunkCount));
	}

	private static void processUploadChunk(Player player, String[] cmd) {
		if (cmd.length < 5) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Draft upload chunk is incomplete.");
			return;
		}
		int requestId = parseInt(cmd[2], "request id");
		int index = parseInt(cmd[3], "chunk index");
		String data = cmd[4];
		if (data.length() == 0 || data.length() > MAX_UPLOAD_CHUNK_LENGTH) {
			sendAction(player, requestId, false, -1, "BossLabs draft chunk has an invalid size.");
			return;
		}
		DraftUpload upload = DRAFT_UPLOADS.get(player);
		if (upload == null || upload.requestId != requestId) {
			sendAction(player, requestId, false, -1, "BossLabs draft upload is no longer active.");
			return;
		}
		if (!upload.setChunk(index, data))
			sendAction(player, requestId, false, -1, "BossLabs draft chunk index is invalid.");
	}

	private static void processUploadCommit(final Player player, String[] cmd) {
		if (cmd.length < 3) {
			sendAction(player, readRequestId(cmd, 2), false, -1, "Draft upload commit is incomplete.");
			return;
		}
		final int requestId = parseInt(cmd[2], "request id");
		final DraftUpload upload = DRAFT_UPLOADS.remove(player);
		if (upload == null || upload.requestId != requestId) {
			sendAction(player, requestId, false, -1, "BossLabs draft upload is no longer active.");
			return;
		}
		String payload = upload.join();
		if (payload == null) {
			sendAction(player, requestId, false, -1, "BossLabs draft upload is incomplete.");
			return;
		}

		final BossDefinition definition = BossLabsDefinitionWireCodec.decode(payload);
		if (BossNpcSearchService.inspect(definition.getNpcId()) == null) {
			sendAction(player, requestId, false, definition.getNpcId(), "BossLabs draft references an unknown NPC id.");
			return;
		}

		if (!upload.save) {
			BossDefinitionPublisher.applyLive(definition);
			sendAction(player, requestId, true, definition.getNpcId(), "Applied complete BossLabs draft live.");
			sendInspection(player, requestId, definition.getNpcId());
			return;
		}

		GameExecutorManager.slowExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					BossDefinitionPublisher.saveAndApply(definition);
					sendAction(player, requestId, true, definition.getNpcId(), "Saved and applied complete BossLabs draft.");
					sendInspection(player, requestId, definition.getNpcId());
				} catch (IOException e) {
					sendAction(player, requestId, false, definition.getNpcId(), "Save failed: " + safeMessage(e));
				} catch (RuntimeException e) {
					sendAction(player, requestId, false, definition.getNpcId(), safeMessage(e));
				}
			}
		});
	}

	/**
	 * Retained for the prior identity-only bridge path. New authoring publishes
	 * complete definitions through the upload transaction above.
	 */
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

		final BossDefinition next = new BossDefinition(definitionId, displayName, npcId, current.getPhases());
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

	private static void processTesting(final Player player, final String[] cmd) {
		if (cmd.length < 5) {
			sendAction(player, readRequestId(cmd, 2), false, -1,
					"Testing requires a request id, operation, and BossLabs NPC id.");
			return;
		}
		final int requestId = parseInt(cmd[2], "request id");
		final String operation = cmd[3].toLowerCase();
		final int npcId = parseInt(cmd[4], "NPC id");
		if (!isTestingOperation(operation)) {
			sendAction(player, requestId, false, npcId, "Unknown BossLabs testing operation: " + operation);
			return;
		}

		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				try {
					String message;
					if ("spawn".equals(operation)) {
						message = BossLabsTestingService.spawnBoss(player, npcId);
					} else if ("reset".equals(operation)) {
						message = BossLabsTestingService.resetEncounter(player, npcId);
					} else if ("sethp".equals(operation)) {
						if (cmd.length < 6)
							throw new IllegalArgumentException("Set HP requires a percent.");
						message = BossLabsTestingService.setHealthPercent(player, npcId,
								parseInt(cmd[5], "health percent"));
					} else if ("forcephase".equals(operation)) {
						if (cmd.length < 6)
							throw new IllegalArgumentException("Force Phase requires a phase id.");
						message = BossLabsTestingService.forcePhase(player, npcId, decode(cmd[5]));
					} else if ("forceattack".equals(operation)) {
						if (cmd.length < 7)
							throw new IllegalArgumentException("Force Attack requires phase and attack ids.");
						message = BossLabsTestingService.triggerAttack(player, npcId, decode(cmd[5]), decode(cmd[6]));
					} else if ("clearhazards".equals(operation)) {
						message = BossLabsTestingService.clearHazards(player, npcId);
					} else {
						message = BossLabsTestingService.clearMinions(player, npcId);
					}
					sendAction(player, requestId, true, npcId, message);
				} catch (RuntimeException e) {
					sendAction(player, requestId, false, npcId, safeMessage(e));
				} finally {
					stop();
				}
			}
		});
	}

	private static boolean isTestingOperation(String operation) {
		return "spawn".equals(operation) || "reset".equals(operation) || "sethp".equals(operation)
				|| "forcephase".equals(operation) || "forceattack".equals(operation)
				|| "clearhazards".equals(operation) || "clearminions".equals(operation);
	}

	private static void processRiseOfTheSix(Player player, String[] cmd) {
		int requestId = readRequestId(cmd, 2);
		BossInstanceHandler.enterInstance(player, Boss.Rise_of_the_Six);
		sendAction(player, requestId, true, -1, "Opened the Rise of the Six encounter setup.");
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
		sendDefinition(player, requestId, npcId, definition);
	}

	private static void sendDefinition(Player player, int requestId, int npcId, BossDefinition definition) {
		if (definition == null) {
			send(player, RESPONSE_PREFIX + "definition-empty|" + requestId + "|" + npcId);
			return;
		}
		String payload = BossLabsDefinitionWireCodec.encode(definition);
		int chunkCount = (payload.length() + RESPONSE_CHUNK_LENGTH - 1) / RESPONSE_CHUNK_LENGTH;
		send(player, RESPONSE_PREFIX + "definition-begin|" + requestId + "|" + npcId + "|" + chunkCount);
		for (int index = 0; index < chunkCount; index++) {
			int start = index * RESPONSE_CHUNK_LENGTH;
			int end = Math.min(payload.length(), start + RESPONSE_CHUNK_LENGTH);
			send(player, RESPONSE_PREFIX + "definition-chunk|" + requestId + "|" + npcId + "|" + index + "|"
					+ payload.substring(start, end));
		}
		send(player, RESPONSE_PREFIX + "definition-end|" + requestId + "|" + npcId);
	}

	private static void sendAction(Player player, int requestId, boolean success, int npcId, String message) {
		send(player, RESPONSE_PREFIX + "action|" + requestId + "|" + (success ? 1 : 0) + "|" + npcId + "|"
				+ encode(message));
	}

	private static void send(Player player, String response) {
		if (player == null || player.hasFinished())
			return;
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

	private static final class DraftUpload {
		private final int requestId;
		private final boolean save;
		private final String[] chunks;

		private DraftUpload(int requestId, boolean save, int chunkCount) {
			this.requestId = requestId;
			this.save = save;
			this.chunks = new String[chunkCount];
		}

		private boolean setChunk(int index, String data) {
			if (index < 0 || index >= chunks.length)
				return false;
			chunks[index] = data;
			return true;
		}

		private String join() {
			StringBuilder builder = new StringBuilder();
			for (String chunk : chunks) {
				if (chunk == null)
					return null;
				builder.append(chunk);
			}
			return builder.toString();
		}
	}
}
