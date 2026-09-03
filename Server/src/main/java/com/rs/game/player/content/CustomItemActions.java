package com.rs.game.player.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.rs.game.item.Item;
import com.rs.game.player.Backpack;
import com.rs.game.player.Inventory;
import com.rs.game.player.Player;
import com.rs.net.decoders.WorldPacketsDecoder;
import com.rs.utils.Logger;

/**
 * Config-driven server authority for custom item context actions.
 *
 * Menu placement/text is data. Handler/action names are stable identifiers; the
 * config never contains Java method names. Unconfigured items fall through to
 * Matrix3's normal item handlers unchanged.
 */
public final class CustomItemActions {

    private static final String CONFIG_PATH = "data/items/custom-item-actions.properties";
    private static final String[] CONFIG_PATHS = {
            CONFIG_PATH,
            "../" + CONFIG_PATH,
            "../../" + CONFIG_PATH
    };

    private static final Map<String, ActionEntry> ACTIONS = new HashMap<String, ActionEntry>();
    private static final Map<Integer, String> HANDLERS = new HashMap<Integer, String>();
    private static boolean loaded;

    private CustomItemActions() {
    }

    public static boolean processButtonClick(Player player, int interfaceId, int componentId,
            int slotId, int slotId2, int packetId) {
        if (player == null)
            return false;
        ensureLoaded();

        Context context = getContext(interfaceId, componentId);
        if (context == null)
            return false;

        int option = getOption(context, packetId);
        if (option == -1)
            return false;

        Item item = getClickedItem(player, context, slotId);
        if (item == null)
            return false;

        ActionEntry entry = ACTIONS.get(key(item.getId(), context, option));
        if (entry == null)
            return false;

        String handler = HANDLERS.get(Integer.valueOf(item.getId()));
        if (handler == null)
            return false;
        return execute(player, handler, entry.action, context, slotId, item.getId());
    }

    private static boolean execute(Player player, String handler, String action,
            Context context, int slotId, int itemId) {
        if ("BACKPACK".equals(handler)) {
            Backpack backpack = player.getInventory().getBackpack();
            if (backpack == null)
                return true;
            if ("OPEN".equals(action)) {
                if (context == Context.INVENTORY)
                    backpack.openFromInventory(slotId, itemId);
                else if (context == Context.EQUIPMENT)
                    backpack.openFromEquipment(slotId, itemId);
                else if (context == Context.BANK)
                    backpack.openFromBank(slotId, itemId);
                return true;
            }
            if ("EMPTY_TO_BANK".equals(action) && context == Context.BANK) {
                backpack.emptyToBankFromBank(slotId, itemId);
                return true;
            }
        }
        player.getPackets().sendGameMessage("This custom item action is not available yet.");
        return true;
    }

    private static Item getClickedItem(Player player, Context context, int slotId) {
        if (slotId < 0)
            return null;
        if (context == Context.INVENTORY)
            return player.getInventory().getItem(slotId);
        if (context == Context.EQUIPMENT)
            return player.getEquipment().getItem(slotId);
        if (context == Context.BANK) {
            int[] realSlot = player.getBank().getRealSlot(slotId);
            return player.getBank().getItem(realSlot);
        }
        return null;
    }

    private static Context getContext(int interfaceId, int componentId) {
        if ((interfaceId == Inventory.INVENTORY_INTERFACE && componentId == 34)
                || (interfaceId == Inventory.INVENTORY_INTERFACE_2 && componentId == 15))
            return Context.INVENTORY;
        if ((interfaceId == 1462 && componentId == 14)
                || (interfaceId == 1464 && componentId == 15))
            return Context.EQUIPMENT;
        if (interfaceId == 762 && componentId == 215)
            return Context.BANK;
        return null;
    }

    private static int getOption(Context context, int packetId) {
        if (context == Context.INVENTORY) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET) return 1;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET) return 2;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET) return 3;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON4_PACKET) return 4;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON5_PACKET) return 5;
            return -1;
        }
        if (context == Context.EQUIPMENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET) return 1;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET) return 2;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON4_PACKET) return 3;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON5_PACKET) return 4;
            return -1;
        }
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET) return 1;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET) return 2;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET) return 3;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON4_PACKET) return 4;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON5_PACKET) return 5;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON6_PACKET) return 6;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON7_PACKET) return 7;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON8_PACKET) return 8;
        return -1;
    }

    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;
        Properties properties = new Properties();
        File file = findConfig();
        if (file == null) {
            Logger.log("CustomItemActions", "No " + CONFIG_PATH + " found; custom item actions disabled.");
            return;
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            properties.load(input);
            parse(properties);
        } catch (IOException e) {
            Logger.handle(e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Logger.handle(e);
                }
            }
        }
    }

    private static void parse(Properties properties) {
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith("item."))
                continue;
            String[] parts = propertyName.split("\\.");
            if (parts.length < 3)
                continue;
            int itemId;
            try {
                itemId = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            String value = properties.getProperty(propertyName, "").trim();
            if ("handler".equals(parts[2])) {
                if (value.length() > 0)
                    HANDLERS.put(Integer.valueOf(itemId), value.toUpperCase());
                continue;
            }
            if (parts.length != 4)
                continue;
            Context context = Context.forName(parts[2]);
            if (context == null)
                continue;
            int option;
            try {
                option = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                continue;
            }
            int split = value.indexOf('|');
            String action = split == -1 ? value : value.substring(0, split);
            String label = split == -1 ? value : value.substring(split + 1);
            action = action.trim().toUpperCase();
            label = label.trim();
            if (action.length() == 0 || label.length() == 0)
                continue;
            ACTIONS.put(key(itemId, context, option), new ActionEntry(action, label));
        }
    }

    private static File findConfig() {
        for (String path : CONFIG_PATHS) {
            File file = new File(path);
            if (file.isFile())
                return file;
        }
        return null;
    }

    private static String key(int itemId, Context context, int option) {
        return itemId + ":" + context.name() + ":" + option;
    }

    private static final class ActionEntry {
        private final String action;
        @SuppressWarnings("unused")
        private final String label;

        private ActionEntry(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }

    private static enum Context {
        INVENTORY, EQUIPMENT, BANK;

        private static Context forName(String value) {
            if (value == null)
                return null;
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
