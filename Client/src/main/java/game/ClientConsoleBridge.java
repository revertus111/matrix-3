package game;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientConsoleBridge {

    private static final int RIGHTS_MULTIPLIER = -1550439133;
    private static final int MAX_QUEUED_COMMANDS = 32;
    private static final int MAX_COMMANDS_PER_CLIENT_CYCLE = 4;
    private static final int MAX_COMMAND_LENGTH = 252;

    private static final Queue<String> COMMAND_QUEUE = new ConcurrentLinkedQueue<String>();

    private ClientConsoleBridge() {
    }

    public static boolean hasLocalPlayer() {
        return Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 != null;
    }

    public static String getDisplayName() {
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null) {
            return "Waiting for player";
        }
        if (player.displayName != null && !player.displayName.trim().isEmpty()) {
            return player.displayName;
        }
        if (player.username != null && !player.username.trim().isEmpty()) {
            return player.username;
        }
        return "Player loaded";
    }

    public static int getRights() {
        return RIGHTS_MULTIPLIER * client.rights;
    }

    public static String getRightsLabel() {
        int rights = getRights();
        if (rights >= 2) {
            return "Owner / Admin (" + rights + ")";
        }
        if (rights == 1) {
            return "Moderator (1)";
        }
        return "Player (" + rights + ")";
    }

    public static String getPlayerStateLabel() {
        return hasLocalPlayer() ? "Player loaded" : "Waiting for login";
    }

    public static String queueConsoleCommand(String rawCommand) {
        if (!hasLocalPlayer()) {
            return "Log in before running a command.";
        }

        String command = normalizeCommand(rawCommand);
        if (command.length() == 0) {
            return "Select a command before running it.";
        }
        if (command.length() > MAX_COMMAND_LENGTH) {
            return "Command is too long for Matrix3's command packet.";
        }
        if (COMMAND_QUEUE.size() >= MAX_QUEUED_COMMANDS) {
            return "Command queue is full. Wait for the client to catch up.";
        }

        COMMAND_QUEUE.offer(command);
        return null;
    }

    public static void flushQueuedCommands() {
        ClientConsoleItemBridge.flushThumbnailRequests();
        if (!hasLocalPlayer()) {
            COMMAND_QUEUE.clear();
            return;
        }
        if (client.aClass195_8589 == null || client.aClass195_8589.aClass650_2340 == null) {
            return;
        }

        for (int sent = 0; sent < MAX_COMMANDS_PER_CLIENT_CYCLE; sent++) {
            String command = COMMAND_QUEUE.poll();
            if (command == null) {
                return;
            }
            try {
                sendConsoleCommand(command);
            } catch (RuntimeException ex) {
                System.err.println("Client Console failed to send command ::" + command);
                ex.printStackTrace();
            }
        }
    }

    private static String normalizeCommand(String rawCommand) {
        if (rawCommand == null) {
            return "";
        }
        String command = rawCommand.trim();
        while (command.startsWith("::") || command.startsWith(";;")) {
            command = command.substring(2).trim();
        }
        return command;
    }

    private static void sendConsoleCommand(String command) {
        Class572_Sub25 packet = Class378.sendOutPacket(
                OutgoingPacket.COMMANDS_PACKET,
                client.aClass195_8589.aClass650_2340,
                -2031262816);

        packet.aRsByteBuffer.writeByte(0, -1384395473);
        int payloadStart = packet.aRsByteBuffer.o * -1585139053;
        packet.aRsByteBuffer.writeByte(0, -1384395473); // clientCommand = false
        packet.aRsByteBuffer.writeByte(0, -1384395473); // unknown = false
        packet.aRsByteBuffer.writeString(command, (byte) -113);
        packet.aRsByteBuffer.method8552(
                packet.aRsByteBuffer.o * -1585139053 - payloadStart,
                -1997224533);

        client.aClass195_8589.method2929(packet, (byte) -35);
    }
}
