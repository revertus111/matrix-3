package game;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Client-side presentation half of data-driven custom item actions.
 *
 * The server remains authoritative for what an option actually does. This class
 * only applies configured item/inventory labels and equipped-item option params
 * to already-decoded Matrix3 ItemDefinitions, plus configured option entries for
 * the bank inventory pane.
 */
public final class CustomItemActionConfig {

    private static final String CONFIG_PATH = "Server/data/items/custom-item-actions.properties";
    private static final String SERVER_RELATIVE_CONFIG_PATH = "data/items/custom-item-actions.properties";

    private static final int BANK_INVENTORY_INTERFACE_HASH = (762 << 16) | 7;
    private static final int INTERFACE_OPTION_OPCODE = 57;
    private static final int INTERFACE_OPTION_SECONDARY_OPCODE = 1007;
    private static final int MAX_INTERFACE_OPTIONS = 10;
    private static final String EXPLICIT_MENU_MODE = "EXPLICIT";

    private static final Properties PROPERTIES = new Properties();
    private static final Set<Integer> ITEM_IDS = new HashSet<Integer>();

    private static boolean loaded;
    private static Class639_Sub5 lastDefinitions;
    private static final Map<Integer, ItemDefinitions> appliedDefinitions =
            new HashMap<Integer, ItemDefinitions>();

    private CustomItemActionConfig() {
    }

    public static void apply() {
        ensureLoaded();
        BackpackInterfaceLayout.apply();
        BackpackInterfaceTrace.dumpOnce();
        applyBankInventoryMenuActions();
        if (ITEM_IDS.isEmpty())
            return;

        Class639_Sub5 definitions = ClientConsoleItemBridge.getRegisteredItemDefinitions();
        if (definitions == null)
            return;
        if (definitions != lastDefinitions) {
            lastDefinitions = definitions;
            appliedDefinitions.clear();
        }

        for (Integer itemIdValue : ITEM_IDS) {
            int itemId = itemIdValue.intValue();
            if (itemId < 0 || itemId >= definitions.method45())
                continue;
            try {
                ItemDefinitions definition = (ItemDefinitions) definitions.getDefinition(itemId, 0);
                if (definition == null || appliedDefinitions.get(itemIdValue) == definition)
                    continue;
                applyInventoryOptions(definition, itemId);
                applyEquipmentOptions(definition, itemId);
                appliedDefinitions.put(itemIdValue, definition);
            } catch (RuntimeException ex) {
                System.err.println("Custom item action config failed for item " + itemId);
                ex.printStackTrace();
                CustomItemActionTrace.log("CONFIG apply-failed item=" + itemId + " error="
                        + ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage()));
            }
        }
    }

    private static void applyInventoryOptions(ItemDefinitions definition, int itemId) {
        if (definition.aStringArray8145 == null || definition.aStringArray8145.length < 5)
            return;
        for (int option = 1; option <= 5; option++) {
            ActionEntry entry = getEntry(itemId, "inventory", option);
            if (entry != null) {
                definition.aStringArray8145[option - 1] = entry.label;
                CustomItemActionTrace.log("APPLY context=inventory item=" + itemId + " option=" + option
                        + " action=" + entry.action + " label=" + entry.label);
            }
        }
    }

    private static void applyEquipmentOptions(ItemDefinitions definition, int itemId) {
        for (int option = 1; option <= 10; option++) {
            ActionEntry entry = getEntry(itemId, "equipment", option);
            if (entry == null)
                continue;
            int key = 528 + option;
            definition.method10145(key, entry.label, (byte) 1);
            CustomItemActionTrace.log("APPLY context=equipment item=" + itemId + " option=" + option
                    + " action=" + entry.action + " label=" + entry.label);
        }
    }

    private static void applyBankInventoryMenuActions() {
        if (ITEM_IDS.isEmpty())
            return;
        InterfaceDefinitions definition;
        try {
            definition = Class512.method6083(BANK_INVENTORY_INTERFACE_HASH, (short) 3691);
        } catch (RuntimeException ex) {
            return;
        }
        if (definition == null || definition.aStringArray867 == null)
            return;
        int itemId = definition.nvmtheindexisotherone * 411192987;
        if (!ITEM_IDS.contains(Integer.valueOf(itemId)))
            return;

        String mode = PROPERTIES.getProperty("item." + itemId + ".bank_inventory.mode");
        boolean explicit = EXPLICIT_MENU_MODE.equalsIgnoreCase(mode == null ? "" : mode.trim());
        boolean[] present = new boolean[MAX_INTERFACE_OPTIONS + 1];
        for (int option = 1; option <= MAX_INTERFACE_OPTIONS; option++) {
            if (option - 1 >= definition.aStringArray867.length)
                break;
            String label = definition.aStringArray867[option - 1];
            if (label != null && label.length() > 0) {
                present[option] = true;
                ActionEntry configured = getEntry(itemId, "bank_inventory", option);
                boolean allowed = !explicit || configured != null;
                CustomItemActionTrace.log("BANK_NATIVE item=" + itemId + " option=" + option + " label=" + label
                        + " explicit=" + explicit + " allowed=" + allowed);
                if (!allowed)
                    definition.aStringArray867[option - 1] = null;
            }
        }

        for (int option = 1; option <= MAX_INTERFACE_OPTIONS; option++) {
            ActionEntry entry = getEntry(itemId, "bank_inventory", option);
            if (entry == null || "STOCK".equalsIgnoreCase(entry.action) || present[option])
                continue;
            if (option - 1 >= definition.aStringArray867.length)
                continue;
            definition.aStringArray867[option - 1] = entry.label;
            CustomItemActionTrace.log("BANK_ADD item=" + itemId + " option=" + option + " action=" + entry.action
                    + " label=" + entry.label);
        }
    }

    static boolean shouldKeepBankInventoryNativeOption(InterfaceDefinitions definition, int option) {
        ensureLoaded();
        if (definition == null || option < 1 || option > MAX_INTERFACE_OPTIONS)
            return true;
        int itemId = definition.nvmtheindexisotherone * 411192987;
        if (!ITEM_IDS.contains(Integer.valueOf(itemId)))
            return true;
        String mode = PROPERTIES.getProperty("item." + itemId + ".bank_inventory.mode");
        if (!EXPLICIT_MENU_MODE.equalsIgnoreCase(mode == null ? "" : mode.trim()))
            return true;
        ActionEntry entry = getEntry(itemId, "bank_inventory", option);
        boolean allowed = entry != null;
        String label = definition.aStringArray867 != null && option - 1 < definition.aStringArray867.length
                ? definition.aStringArray867[option - 1]
                : null;
        CustomItemActionTrace.log("BANK_NATIVE item=" + itemId + " option=" + option + " label=" + label
                + " explicit=true allowed=" + allowed);
        return allowed;
    }

    private static ActionEntry getEntry(int itemId, String context, int option) {
        String value = PROPERTIES.getProperty("item." + itemId + "." + context + "." + option);
        if (value == null)
            return null;
        String trimmed = value.trim();
        if (trimmed.length() == 0)
            return null;
        int split = trimmed.indexOf('|');
        if (split < 0)
            return new ActionEntry(trimmed, trimmed);
        String action = trimmed.substring(0, split).trim();
        String label = trimmed.substring(split + 1).trim();
        if (action.length() == 0 || label.length() == 0)
            return null;
        return new ActionEntry(action, label);
    }

    private static void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;
        File file = resolveConfigFile();
        if (file == null || !file.isFile()) {
            CustomItemActionTrace.log("CONFIG missing");
            return;
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            PROPERTIES.load(input);
            for (String key : PROPERTIES.stringPropertyNames()) {
                if (!key.startsWith("item."))
                    continue;
                int secondDot = key.indexOf('.', 5);
                if (secondDot < 0)
                    continue;
                try {
                    ITEM_IDS.add(Integer.valueOf(Integer.parseInt(key.substring(5, secondDot))));
                } catch (NumberFormatException ignored) {
                    // Skip malformed item ids; the rest of the config remains usable.
                }
            }
            CustomItemActionTrace.log("CONFIG loaded path=" + file.getAbsolutePath() + " itemIds=" + ITEM_IDS.size());
        } catch (IOException ex) {
            CustomItemActionTrace.log("CONFIG read-failed path=" + file.getAbsolutePath() + " error="
                    + ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage()));
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Nothing useful to do here.
                }
            }
        }
    }

    private static File resolveConfigFile() {
        File start = new File(System.getProperty("user.dir", "."));
        File directory = start;
        for (int depth = 0; directory != null && depth < 6; depth++, directory = directory.getParentFile()) {
            File candidate = new File(directory, CONFIG_PATH);
            if (candidate.isFile())
                return candidate;
        }
        File serverRelative = new File(start, SERVER_RELATIVE_CONFIG_PATH);
        if (serverRelative.isFile())
            return serverRelative;
        return null;
    }

    private static final class ActionEntry {
        final String action;
        final String label;

        ActionEntry(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }
}
