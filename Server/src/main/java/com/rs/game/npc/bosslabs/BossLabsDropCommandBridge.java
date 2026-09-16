package com.rs.game.npc.bosslabs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.executor.GameExecutorManager;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

/** Admin-only BossLabs bridge adapter for Matrix3 drop-table authoring. */
public final class BossLabsDropCommandBridge {

	private static final String RESPONSE_PREFIX = "bosslabs|";
	private static final int MAX_UPLOAD_CHUNKS = 32;
	private static final int MAX_UPLOAD_CHUNK_LENGTH = 190;
	private static final int RESPONSE_CHUNK_LENGTH = 160;
	private static final Map<Player, Upload> UPLOADS =
			Collections.synchronizedMap(new WeakHashMap<Player, Upload>());

	private BossLabsDropCommandBridge() {
	}

	/** cmd layout starts with: bosslabs drops operation ... */
	public static void process(final Player player, final String[] cmd) {
		if (cmd == null || cmd.length < 3) {
			sendAction(player, 0, false, -1, "Missing BossLabs drops operation.");
			return;
		}
		String operation = cmd[2].toLowerCase();
		try {
			if ("inspect".equals(operation)) {
				if (cmd.length < 5)
					throw new IllegalArgumentException("Drops inspect requires request and NPC ids.");
				final int requestId = parseInt(cmd[3], "request id");
				final int npcId = parseInt(cmd[4], "NPC id");
				world(new Runnable() {
					@Override
					public void run() {
						sendState(player, requestId, npcId);
					}
				});
				return;
			}
			if ("uploadbegin".equals(operation)) {
				processUploadBegin(player, cmd);
				return;
			}
			if ("uploadchunk".equals(operation)) {
				processUploadChunk(player, cmd);
				return;
			}
			if ("uploadcommit".equals(operation)) {
				processUploadCommit(player, cmd);
				return;
			}
			if ("applysaved".equals(operation) || "undo".equals(operation)
					|| "restore".equals(operation) || "deletesaved".equals(operation)) {
				processStateAction(player, cmd, operation);
				return;
			}
			sendAction(player, readRequestId(cmd, 3), false, -1, "Unknown BossLabs drops operation: " + operation);
		} catch (RuntimeException e) {
			sendAction(player, readRequestId(cmd, 3), false, -1, safeMessage(e));
		}
	}

	private static void processUploadBegin(Player player, String[] cmd) {
		if (cmd.length < 6)
			throw new IllegalArgumentException("Drops upload begin is incomplete.");
		int requestId = parseInt(cmd[3], "request id");
		boolean save = "1".equals(cmd[4]);
		int chunks = parseInt(cmd[5], "chunk count");
		if (chunks <= 0 || chunks > MAX_UPLOAD_CHUNKS)
			throw new IllegalArgumentException("BossLabs drop draft exceeds the supported bridge size.");
		UPLOADS.put(player, new Upload(requestId, save, chunks));
	}

	private static void processUploadChunk(Player player, String[] cmd) {
		if (cmd.length < 6)
			throw new IllegalArgumentException("Drops upload chunk is incomplete.");
		int requestId = parseInt(cmd[3], "request id");
		int index = parseInt(cmd[4], "chunk index");
		String data = cmd[5];
		if (data.length() == 0 || data.length() > MAX_UPLOAD_CHUNK_LENGTH)
			throw new IllegalArgumentException("BossLabs drop chunk has an invalid size.");
		Upload upload = UPLOADS.get(player);
		if (upload == null || upload.requestId != requestId)
			throw new IllegalArgumentException("BossLabs drop upload is no longer active.");
		if (!upload.setChunk(index, data))
			throw new IllegalArgumentException("BossLabs drop chunk index is invalid.");
	}

	private static void processUploadCommit(final Player player, String[] cmd) {
		if (cmd.length < 4)
			throw new IllegalArgumentException("Drops upload commit is incomplete.");
		final int requestId = parseInt(cmd[3], "request id");
		final Upload upload = UPLOADS.remove(player);
		if (upload == null || upload.requestId != requestId)
			throw new IllegalArgumentException("BossLabs drop upload is no longer active.");
		String payload = upload.join();
		if (payload == null)
			throw new IllegalArgumentException("BossLabs drop upload is incomplete.");
		final BossLabsDropDefinition definition = BossLabsDropWireCodec.decode(payload);
		definition.validateItems();

		if (!upload.save) {
			world(new Runnable() {
				@Override
				public void run() {
					try {
						BossLabsDropPublisher.applyLive(definition);
						sendAction(player, requestId, true, definition.getNpcId(), "Applied BossLabs drops live.");
						sendState(player, requestId, definition.getNpcId());
					} catch (RuntimeException e) {
						sendAction(player, requestId, false, definition.getNpcId(), safeMessage(e));
					}
				}
			});
			return;
		}

		GameExecutorManager.slowExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					BossLabsDropStore.save(definition);
					world(new Runnable() {
						@Override
						public void run() {
							try {
								BossLabsDropPublisher.applyLive(definition);
								sendAction(player, requestId, true, definition.getNpcId(), "Saved and applied BossLabs drops.");
								sendState(player, requestId, definition.getNpcId());
							} catch (RuntimeException e) {
								sendAction(player, requestId, false, definition.getNpcId(), safeMessage(e));
							}
						}
					});
				} catch (IOException e) {
					sendAction(player, requestId, false, definition.getNpcId(), "Drop save failed: " + safeMessage(e));
				}
			}
		});
	}

	private static void processStateAction(final Player player, String[] cmd, final String operation) {
		if (cmd.length < 5)
			throw new IllegalArgumentException("Drops action requires request and NPC ids.");
		final int requestId = parseInt(cmd[3], "request id");
		final int npcId = parseInt(cmd[4], "NPC id");

		if ("deletesaved".equals(operation)) {
			GameExecutorManager.slowExecutor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						final boolean deleted = BossLabsDropStore.delete(npcId);
						world(new Runnable() {
							@Override
							public void run() {
								boolean restored = BossLabsDropPublisher.restoreMatrix(npcId);
								sendAction(player, requestId, deleted || restored, npcId,
										deleted ? "Deleted saved BossLabs drops and restored Matrix3 live drops."
												: restored ? "No saved override existed; restored Matrix3 live drops."
														: "No saved/live BossLabs drop override exists.");
								sendState(player, requestId, npcId);
							}
						});
					} catch (IOException e) {
						sendAction(player, requestId, false, npcId, "Drop delete failed: " + safeMessage(e));
					}
				}
			});
			return;
		}

		world(new Runnable() {
			@Override
			public void run() {
				try {
					boolean changed;
					String success;
					if ("applysaved".equals(operation)) {
						changed = BossLabsDropPublisher.applySaved(npcId);
						success = "Applied saved BossLabs drops live.";
					} else if ("undo".equals(operation)) {
						changed = BossLabsDropPublisher.undoLastApply(npcId);
						success = "Restored the previous live drop table.";
					} else {
						changed = BossLabsDropPublisher.restoreMatrix(npcId);
						success = "Restored the captured Matrix3 drop table live.";
					}
					sendAction(player, requestId, changed, npcId, changed ? success : "No matching drop state is available.");
					sendState(player, requestId, npcId);
				} catch (RuntimeException e) {
					sendAction(player, requestId, false, npcId, safeMessage(e));
				}
			}
		});
	}

	private static void sendState(Player player, int requestId, int npcId) {
		try {
			BossLabsDropDefinition definition = BossLabsDropPublisher.inspectCurrent(npcId);
			String payload = BossLabsDropWireCodec.encode(definition);
			int chunks = (payload.length() + RESPONSE_CHUNK_LENGTH - 1) / RESPONSE_CHUNK_LENGTH;
			String source = BossLabsDropPublisher.hasLiveOverride(npcId) ? "BossLabs" : "Matrix3";
			send(player, RESPONSE_PREFIX + "drop-begin|" + requestId + "|" + npcId + "|" + source + "|"
					+ (BossLabsDropStore.isSaved(npcId) ? 1 : 0) + "|" + (BossLabsDropPublisher.hasRollback(npcId) ? 1 : 0)
					+ "|" + chunks);
			for (int index = 0; index < chunks; index++) {
				int start = index * RESPONSE_CHUNK_LENGTH;
				int end = Math.min(payload.length(), start + RESPONSE_CHUNK_LENGTH);
				send(player, RESPONSE_PREFIX + "drop-chunk|" + requestId + "|" + npcId + "|" + index + "|"
						+ payload.substring(start, end));
			}
			send(player, RESPONSE_PREFIX + "drop-end|" + requestId + "|" + npcId);
		} catch (RuntimeException e) {
			sendAction(player, requestId, false, npcId, "Drop inspection failed: " + safeMessage(e));
		}
	}

	private static void sendAction(Player player, int requestId, boolean success, int npcId, String message) {
		send(player, RESPONSE_PREFIX + "drop-action|" + requestId + "|" + (success ? 1 : 0) + "|" + npcId + "|"
				+ encode(message));
	}

	private static void send(Player player, String response) {
		if (player != null && !player.hasFinished())
			player.getPackets().sendMessage(99, response, null);
	}

	private static void world(final Runnable runnable) {
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				try {
					runnable.run();
				} finally {
					stop();
				}
			}
		});
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
		return Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
	}

	private static String safeMessage(Throwable throwable) {
		String message = throwable == null ? null : throwable.getMessage();
		return message == null || message.trim().length() == 0
				? throwable == null ? "BossLabs drops request failed." : throwable.getClass().getSimpleName()
				: message;
	}

	private static final class Upload {
		private final int requestId;
		private final boolean save;
		private final String[] chunks;

		private Upload(int requestId, boolean save, int chunkCount) {
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
			StringBuilder value = new StringBuilder();
			for (String chunk : chunks) {
				if (chunk == null)
					return null;
				value.append(chunk);
			}
			return value.toString();
		}
	}
}
