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
 * to already-decoded Matrix3 ItemDefinitions.
 */
public final class CustomItemActionConfig {

    private static final String CONFIG_PATH = "data/items/custom-item-actions.properties";
    private static final String[] CONFIG_PATHS = {
            CONFIG_PATH,
            "../" + CONFIG_PATH,
            "../../" + CONFIG_PATH
    };

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
            }
        }
    }

    private static void applyInventoryOptions(ItemDefinitions definition, int itemId) {
        if (definition.aStringArray8145 == null || definition.aStringArray8145.length < 5)
            return;
        for (int option = 1; option <= 5; option++) {
            ActionEntry entry = getEntry(itemId, "inventory", option);
            if (entry != null)
                definition.aStringArray8145[option - 1] = entry.label;
        }
    }

    private static void applyEquipmentOptions(ItemDefinitions definition, int itemId) {
        for (int option = 1; option <= 4; option++) {
            ActionEntry entry = getEntry(itemId, "equipment", option);
            if (entry != null)
                setStringParam(definition, 527 + option, entry.label);
        }
    }

    private static void setStringParam(ItemDefinitions definition, int paramId, String value) {
        if (definition.aClass676_8185 == null)
            definition.aClass676_8185 = new Class676(4);
        Class572 existing = definition.aClass676_8185.get((long) paramId);
        if (existing != null)
            existing.method6794((byte) 0);
        definition.aClass676_8185.put(new LinkableObject(value), (long) paramId);
    }

    private static ActionEntry getEntry(int itemId, String context, int option) {
        String value = PROPERTIES.getProperty("item." + itemId + "." + context + "." + option);
        if (value == null)
            return null;
        value = value.trim();
        int split = value.indexOf('|');
        String action = split == -1 ? value : value.substring(0, split);
        String label = split == -1 ? value : value.substring(split + 1);
        action = action.trim();
        label = label.trim();
        if (action.length() == 0 || label.length() == 0)
            return null;
        return new ActionEntry(action, label);
    }

    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;
        File file = findConfig();
        if (file == null) {
            System.err.println("Custom item actions: " + CONFIG_PATH + " was not found.");
            return;
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            PROPERTIES.load(input);
            for (String name : PROPERTIES.stringPropertyNames()) {
                if (!name.startsWith("item."))
                    continue;
                String[] parts = name.split("\\.");
                if (parts.length < 3)
                    continue;
                try {
                    ITEM_IDS.add(Integer.valueOf(Integer.parseInt(parts[1])));
                } catch (NumberFormatException e) {
                    // Ignore malformed item ids and leave normal cache behavior intact.
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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

    private static final class ActionEntry {
        @SuppressWarnings("unused")
        private final String action;
        private final String label;

        private ActionEntry(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }
}
