package com.rs.net.decoders;

import java.nio.charset.StandardCharsets;

import com.rs.game.player.Player;
import com.rs.game.player.content.commands.ItemBrowserCommandBridge;

/**
 * Narrow packet-boundary observer for Client Console Item Browser commands.
 * Matrix3 still owns packet framing and normal command processing.
 */
public final class ItemBrowserPacketBridge {

    private static final int COMMANDS_PACKET = 24;
    private static final String COMMAND_PREFIX = "itembrowser";

    private ItemBrowserPacketBridge() {
    }

    public static void inspect(int opcode, Player player, byte[] buffer, int offset, int remaining) {
        if (opcode != COMMANDS_PACKET || player == null || buffer == null) {
            return;
        }
        if (offset < 0 || offset >= buffer.length || remaining < 4) {
            return;
        }

        int payloadLength = buffer[offset] & 0xff;
        if (payloadLength < 3 || remaining < payloadLength + 1) {
            return;
        }

        int payloadStart = offset + 1;
        int payloadEnd = payloadStart + payloadLength;
        if (payloadEnd > buffer.length) {
            return;
        }

        int commandStart = payloadStart + 2;
        int commandEnd = commandStart;
        while (commandEnd < payloadEnd && buffer[commandEnd] != 0) {
            commandEnd++;
        }
        if (commandEnd <= commandStart) {
            return;
        }

        String command = new String(
                buffer,
                commandStart,
                commandEnd - commandStart,
                StandardCharsets.US_ASCII).trim();
        if (!command.equalsIgnoreCase(COMMAND_PREFIX)
                && !command.regionMatches(true, 0, COMMAND_PREFIX + " ", 0, COMMAND_PREFIX.length() + 1)) {
            return;
        }

        ItemBrowserCommandBridge.process(player, command.split(" "));
    }
}
