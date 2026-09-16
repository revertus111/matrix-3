package com.rs.net.decoders;

import java.nio.charset.StandardCharsets;

import com.rs.game.npc.bosslabs.BossLabsCommandBridge;
import com.rs.game.player.Player;

/**
 * Narrow packet-boundary observer for BossLabs developer commands.
 *
 * Matrix3's normal world decoder still owns packet framing and command
 * processing. This observer only reads a complete packet-24 payload without
 * consuming or rewriting it, then forwards the BossLabs-prefixed command to
 * the owner-only BossLabs bridge.
 */
public final class BossLabsPacketBridge {

	private static final int COMMANDS_PACKET = 24;
	private static final String COMMAND_PREFIX = "bosslabs";

	private BossLabsPacketBridge() {
	}

	public static void inspect(int opcode, Player player, byte[] buffer, int offset, int remaining) {
		if (opcode != COMMANDS_PACKET || player == null || buffer == null)
			return;
		if (offset < 0 || offset >= buffer.length || remaining < 4)
			return;

		int payloadLength = buffer[offset] & 0xff;
		if (payloadLength < 3 || remaining < payloadLength + 1)
			return;

		int payloadStart = offset + 1;
		int payloadEnd = payloadStart + payloadLength;
		if (payloadEnd > buffer.length)
			return;

		// Command packet payload: clientCommand byte, unknown byte, NUL string.
		int commandStart = payloadStart + 2;
		int commandEnd = commandStart;
		while (commandEnd < payloadEnd && buffer[commandEnd] != 0)
			commandEnd++;
		if (commandEnd <= commandStart)
			return;

		String command = new String(buffer, commandStart, commandEnd - commandStart, StandardCharsets.US_ASCII).trim();
		if (!command.equalsIgnoreCase(COMMAND_PREFIX)
				&& !command.regionMatches(true, 0, COMMAND_PREFIX + " ", 0, COMMAND_PREFIX.length() + 1))
			return;

		BossLabsCommandBridge.process(player, command.split(" "));
	}
}
