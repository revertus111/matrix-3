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

    private static final String CONFIG_PATH = "Server/data/items/custom-item-actions.properties";
    private static final String SERVER_RELATIVE_CONFIG_PATH = "data/items/custom-item-actions.properties";

    private static final Map<String, ActionEntry> ACTIONS = new HashMap<String, ActionEntry>();
    private static final Map<Integer, String> HANDLERS = new HashMap<Integer, String>();
    private static boolean loaded;

    private CustomItemActions() {
    }

    /**
     * Records the raw configured-item button before Backpack/controller ownership
     * can consume it. This is diagnostic only and never changes the routing result.
     */
    public static void tracePipelineEntry(Player player, int interfaceId, int componentId,
            int slotId, int slotId2, int packetId) {
        if (player == null)
            return;
        ensureLoaded();
        if (!isConfiguredItem(slotId2))
            return;
        Context context = getContext(interfaceId, componentId);
        CustomItemActionTrace.log("PIPELINE interface=" + interfaceId + " component=" + componentId
                + " slot=" + slotId + " slotId2=" + slotId2 + " packet=" + packetId
                + " context=" + (context == null ? "none" : context.name())
                + " configuredSlotId2=true");
    }

    public static boolean processButtonClick(Player player, int interfaceId, int componentId,
            int slotId, int slotId2, int packetId) {
        if (player == null)
            return false;
        ensureLoaded();

        Context context = getContext(interfaceId, componentId);
        int option = context == null ? -1 : getOption(context, packetId);
        Item item = context == null ? null : getClickedItem(player, context, slotId);
        int resolvedItemId = item == null ? -1 : item.getId();
        boolean relevant = isConfiguredItem(resolvedItemId) || isConfiguredItem(slotId2);

        if (relevant) {
            CustomItemActionTrace.log("CLICK interface=" + interfaceId + " component=" + componentId
                    + " slot=" + slotId + " slotId2=" + slotId2 + " packet=" + packetId
                    + " context=" + (context == null ? "none" : context.name()) + " option=" + option
                    + " resolvedItem=" + resolvedItemId);
        }

        if (context == null) {
            if (relevant)
                CustomItemActionTrace.log("RESULT fallthrough reason=no-context");
            return false;
        }
        if (option == -1) {
            if (relevant)
                CustomItemActionTrace.log("RESULT fallthrough reason=unmapped-packet context=" + context.name()
                        + " packet=" + packetId);
            return false;
        }
        if (item == null) {
            if (relevant)
                CustomItemActionTrace.log("RESULT fallthrough reason=no-item context=" + context.name()
                        + " slot=" + slotId + " slotId2=" + slotId2);
            return false;
        }

        ActionEntry entry = ACTIONS.get(key(item.getId(), context, option));
        if (entry == null) {
            if (relevant)
                CustomItemActionTrace.log("RESULT fallthrough reason=no-configured-action item=" + item.getId()
                        + " context=" + context.name() + " option=" + option);
            return false;
        }
        if ("STOCK".equals(entry.action)) {
            CustomItemActionTrace.log("RESULT fallthrough reason=stock item=" + item.getId()
                    + " context=" + context.name() + " option=" + option + " label=" + entry.label);
            return false;
        }

        String handler = HANDLERS.get(Integer.valueOf(item.getId()));
        if (handler == null) {
            CustomItemActionTrace.log("RESULT fallthrough reason=no-handler item=" + item.getId()
                    + " context=" + context.name() + " option=" + option + " action=" + entry.action);
            return false;
        }

        CustomItemActionTrace.log("DISPATCH item=" + item.getId() + " context=" + context.name()
                + " option=" + option + " action=" + entry.action + " handler=" + handler);
        boolean consumed = execute(player, handler, entry.action, context, slotId, item.getId());
        CustomItemActionTrace.log("RESULT consumed=" + consumed + " item=" + item.getId()
                + " context=" + context.name() + " option=" + option + " action=" + entry.action);
        return consumed;
    }

    private static boolean execute(Player player, String handler, String action,
            Context context, int slotId, int itemId) {
        if ("BACKPACK".equals(handler)) {
            Backpack backpack = player.getInventory().getBackpack();
            if (backpack == null) {
                CustomItemActionTrace.log("EXECUTE backpack-missing item=" + itemId + " action=" + action);
                return true;
            }
            if ("OPEN".equals(action)) {
                if (context == Context.INVENTORY || context == Context.BANK_INVENTORY)
                    backpack.openFromInventory(slotId, itemId);
                else if (context == Context.EQUIPMENT)
                    backpack.openFromEquipment(slotId, itemId);
                else if (context == Context.BANK)
                    backpack.openFromBank(slotId, itemId);
                CustomItemActionTrace.log("EXECUTE backpack-open item=" + itemId + " context=" + context.name()
                        + " slot=" + slotId + " openAfter=" + backpack.isOpen());
                return true;
            }
            if ("EMPTY_TO_BANK".equals(action)) {
                if (context == Context.BANK)
                    backpack.emptyToBankFromBank(slotId, itemId);
                else if (context == Context.BANK_INVENTORY)
                    backpack.emptyToBank();
                else
                    player.getPackets().sendGameMessage("This custom item action is not available here.");
                CustomItemActionTrace.log("EXECUTE backpack-empty-to-bank item=" + itemId
                        + " context=" + context.name() + " slot=" + slotId);
                return true;
            }
        }
        CustomItemActionTrace.log("EXECUTE unsupported handler=" + handler + " action=" + action
                + " item=" + itemId + " context=" + context.name());
        player.getPackets().sendGameMessage("This custom item action is not available yet.");
        return true;
    }

    private static Item getClickedItem(Player player, Context context, int slotId) {
        if (slotId < 0)
            return null;
        if (context == Context.INVENTORY || context == Context.BANK_INVENTORY)
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
        if (interfaceId == 762 && componentId == 7)
            return Context.BANK_INVENTORY;
        if (interfaceId == 762 && componentId == 215)
            return Context.BANK;
        return null;
    }

    private static int getOption(Context context, int packetId) {
        if (context == Context.INVENTORY) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET) return 1;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET) return 2;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET) return 3;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON6_PACKET) return 4;
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON7_PACKET) return 5;
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
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON9_PACKET) return 9;
        if (packetId == WorldPacketsDecoder.ACTION_BUTTON10_PACKET) return 10;
        return -1;
    }

    private static boolean isConfiguredItem(int itemId) {
        return itemId >= 0 && HANDLERS.containsKey(Integer.valueOf(itemId));
    }

    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;
        Properties properties = new Properties();
        File file = findConfig();
        if (file == null) {
            Logger.log("CustomItemActions", "No " + CONFIG_PATH + " found; custom item actions disabled.");
            CustomItemActionTrace.log("CONFIG missing expected=" + CONFIG_PATH + " userDir="
                    + System.getProperty("user.dir", "."));
            return;
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            properties.load(input);
            parse(properties);
            Logger.log("CustomItemActions", "Loaded " + ACTIONS.size() + " custom action(s) from " + file.getPath());
            CustomItemActionTrace.log("CONFIG loaded path=" + file.getPath() + " actions=" + ACTIONS.size()
                    + " handlers=" + HANDLERS.size());
        } catch (IOException e) {
            Logger.handle(e);
            CustomItemActionTrace.log("CONFIG read-failed path=" + file.getPath() + " error="
                    + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
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
        File directory = new File(System.getProperty("user.dir", "."));
        for (int depth = 0; directory != null && depth < 6; depth++, directory = directory.getParentFile()) {
            File repoCandidate = new File(directory, CONFIG_PATH);
            if (repoCandidate.isFile())
                return repoCandidate;
            File serverCandidate = new File(directory, SERVER_RELATIVE_CONFIG_PATH);
            if (serverCandidate.isFile())
                return serverCandidate;
        }
        return null;
    }

    private static String key(int itemId, Context context, int option) {
        return itemId + ":" + context.name() + ":" + option;
    }

    private static final class ActionEntry {
        private final String action;
        private final String label;

        private ActionEntry(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }

    private static enum Context {
        INVENTORY, EQUIPMENT, BANK, BANK_INVENTORY;

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
