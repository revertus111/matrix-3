package com.rs.game.player.content.commands;

import com.rs.cache.loaders.ItemDefinitions;
import com.rs.game.player.Player;

/**
 * Owner-only server authority bridge for Client Console Item Browser actions
 * that cannot be expressed by an existing Matrix3 command.
 */
public final class ItemBrowserCommandBridge {

    private ItemBrowserCommandBridge() {
    }

    public static boolean process(Player player, String[] cmd) {
        if (player == null) {
            return false;
        }
        if (player.getRights() < 2) {
            player.getPackets().sendGameMessage("Admin+ only!");
            return true;
        }
        if (cmd == null || cmd.length < 2 || !"bank".equalsIgnoreCase(cmd[1])) {
            player.getPackets().sendGameMessage("Use: ::itembrowser bank <itemId> <amount>");
            return true;
        }
        if (cmd.length < 4) {
            player.getPackets().sendGameMessage("Use: ::itembrowser bank <itemId> <amount>");
            return true;
        }

        final int itemId;
        final int amount;
        try {
            itemId = Integer.parseInt(cmd[2]);
            amount = Integer.parseInt(cmd[3]);
        } catch (NumberFormatException ex) {
            player.getPackets().sendGameMessage("Item id and amount must be whole numbers.");
            return true;
        }

        if (itemId < 0 || amount <= 0) {
            player.getPackets().sendGameMessage("Item id must be valid and amount must be greater than zero.");
            return true;
        }

        ItemDefinitions definition = ItemDefinitions.getItemDefinitions(itemId);
        if (definition == null || !definition.isLoaded() || definition.name == null
                || definition.name.trim().length() == 0 || "null".equalsIgnoreCase(definition.name.trim())) {
            player.getPackets().sendGameMessage("Unable to spawn unknown item id " + itemId + ".");
            return true;
        }

        boolean added = player.getBank().addItem(itemId, amount, true);
        if (added) {
            player.getPackets().sendGameMessage("Spawned " + amount + " x " + definition.name + " to your bank.");
        } else {
            player.getPackets().sendGameMessage("Unable to add that item to your bank (bank may be full).");
        }
        return true;
    }
}
