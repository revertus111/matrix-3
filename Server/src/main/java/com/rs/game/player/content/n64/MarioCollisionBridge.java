package com.rs.game.player.content.n64;

import java.util.concurrent.atomic.AtomicInteger;

import com.rs.game.World;
import com.rs.game.player.Player;

/**
 * Temporary Mario/Matrix collision bridge.
 *
 * The server remains the collision authority. This bridge exposes a small read-only
 * window of the same vanilla {@link World#checkWalkStep(int, int, int, int, int, int)}
 * decisions already used by normal Matrix movement, so the client can clip native
 * Mario presentation immediately instead of waiting on one network round trip per
 * tile crossing.
 */
public final class MarioCollisionBridge {

    private static final String REQUEST = "__m64clip";
    private static final String PREFIX = "M64C";
    private static final int VERSION = 1;
    private static final int RADIUS = 10;
    private static final int ROWS_PER_CHUNK = 4;
    private static final AtomicInteger NEXT_SEQUENCE = new AtomicInteger();

    // Bit order shared with Client/game/MarioRs3CollisionMap.
    private static final int[][] STEP_OFFSETS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
    };

    private MarioCollisionBridge() {
    }

    /**
     * Handles only the private Matrix3 Mario collision request. The request has no
     * coordinates: a client can only receive collision around its own server-owned
     * player tile.
     */
    public static boolean handleCommand(Player player, String command) {
        if (!REQUEST.equals(command))
            return false;
        if (player == null || player.hasFinished())
            return true;

        sendSnapshot(player);
        return true;
    }

    private static void sendSnapshot(Player player) {
        final int sequence = NEXT_SEQUENCE.incrementAndGet();
        final int plane = player.getPlane();
        final int centerX = player.getX();
        final int centerY = player.getY();
        final int width = RADIUS * 2 + 1;

        for (int rowStart = 0; rowStart < width; rowStart += ROWS_PER_CHUNK) {
            final int rowCount = Math.min(ROWS_PER_CHUNK, width - rowStart);
            StringBuilder hex = new StringBuilder(rowCount * width * 2);

            for (int row = rowStart; row < rowStart + rowCount; row++) {
                int worldY = centerY - RADIUS + row;
                for (int col = 0; col < width; col++) {
                    int worldX = centerX - RADIUS + col;
                    int passMask = 0;
                    for (int bit = 0; bit < STEP_OFFSETS.length; bit++) {
                        int[] offset = STEP_OFFSETS[bit];
                        if (World.checkWalkStep(plane, worldX, worldY,
                                offset[0], offset[1], 1))
                            passMask |= 1 << bit;
                    }
                    appendHexByte(hex, passMask);
                }
            }

            // GAME_MESSAGE type 99 is already the Matrix3 hidden tool-protocol lane.
            // Four 21-byte rows stay comfortably under the var-byte packet limit.
            String payload = PREFIX + "|" + VERSION + "|" + sequence + "|"
                    + plane + "|" + centerX + "|" + centerY + "|" + RADIUS + "|"
                    + rowStart + "|" + rowCount + "|" + hex.toString();
            player.getPackets().sendMessage(99, payload, null);
        }
    }

    private static void appendHexByte(StringBuilder builder, int value) {
        final char[] digits = "0123456789ABCDEF".toCharArray();
        builder.append(digits[(value >>> 4) & 0xF]);
        builder.append(digits[value & 0xF]);
    }
}
