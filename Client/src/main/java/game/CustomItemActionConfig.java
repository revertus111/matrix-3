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
        for (int option = 1; option <= 4; option++) {
            ActionEntry entry = getEntry(itemId, "equipment", option);
            if (entry != null) {
                setStringParam(definition, 527 + option, entry.label);
                CustomItemActionTrace.log("APPLY context=equipment item=" + itemId + " option=" + option
                        + " action=" + entry.action + " label=" + entry.label);
            }
        }
    }

    /**
     * Called from Matrix3's existing menu-entry constructor before an entry is
     * grouped/inserted. Explicit configured bank-inventory menus suppress only
     * unlisted stock option slots for the clicked configured item.
     */
    static boolean shouldSuppressBankInventoryMenuEntry(int opcode, long optionValue,
            int child, int widgetHash) {
        ensureLoaded();
        int normalizedOpcode = opcode >= 2000 ? opcode - 2000 : opcode;
        if (normalizedOpcode != INTERFACE_OPTION_OPCODE
                && normalizedOpcode != INTERFACE_OPTION_SECONDARY_OPCODE)
            return false;
        if (widgetHash != BANK_INVENTORY_INTERFACE_HASH)
            return false;

        InterfaceDefinitions component = Class530.method6338(widgetHash, child, -582563422);
        if (component == null)
            return false;

        int itemId = component.nvmtheindexisotherone * 411192987;
        if (!ITEM_IDS.contains(Integer.valueOf(itemId)))
            return false;

        int option = (int) optionValue;
        boolean explicit = isExplicitBankInventoryMenu(itemId);
        ActionEntry configured = option >= 1 && option <= MAX_INTERFACE_OPTIONS
                ? getEntry(itemId, "bank_inventory", option) : null;
        boolean suppress = explicit && option >= 1 && option <= MAX_INTERFACE_OPTIONS && configured == null;

        CustomItemActionTrace.log("BANK_NATIVE widget=762:7 child=" + child + " item=" + itemId
                + " option=" + option + " explicit=" + explicit
                + " configured=" + describe(configured) + " suppress=" + suppress);
        return suppress;
    }

    /**
     * Adds configured bank-inventory-pane option slots that Matrix3 did not
     * create natively. Existing configured STOCK slots remain untouched.
     */
    private static void applyBankInventoryMenuActions() {
        if (ITEM_IDS.isEmpty() || Class25.aBool165 || Class25.aClass675_174 == null)
            return;

        Map<Integer, BankMenuTarget> targets = new HashMap<Integer, BankMenuTarget>();
        Set<String> existingOptions = new HashSet<String>();
        Class675 entries = Class25.aClass675_174;

        for (Class572 node = entries.aClass572_8547.aClass572_6433;
                node != entries.aClass572_8547;
                node = node.aClass572_6433) {
            Class572_Sub12_Sub10 menuEntry = (Class572_Sub12_Sub10) node;
            int opcode = menuEntry.anInt11402 * -44467871;
            if (opcode >= 2000)
                opcode -= 2000;
            if (opcode != INTERFACE_OPTION_OPCODE && opcode != INTERFACE_OPTION_SECONDARY_OPCODE)
                continue;

            int widgetHash = menuEntry.anInt11392 * 200110927;
            if (widgetHash != BANK_INVENTORY_INTERFACE_HASH)
                continue;

            int child = menuEntry.anInt11397 * 740323685;
            InterfaceDefinitions component = Class530.method6338(widgetHash, child, -582563422);
            if (component == null)
                continue;

            int itemId = component.nvmtheindexisotherone * 411192987;
            if (!hasBankInventoryEntries(itemId))
                continue;

            Integer targetKey = Integer.valueOf(child);
            if (!targets.containsKey(targetKey))
                targets.put(targetKey, new BankMenuTarget(itemId, menuEntry));

            int option = (int) (menuEntry.aLong11395 * -6760453999157901937L);
            existingOptions.add(bankMenuKey(child, option));
        }

        for (BankMenuTarget target : targets.values()) {
            int child = target.anchor.anInt11397 * 740323685;
            for (int option = MAX_INTERFACE_OPTIONS; option >= 1; option--) {
                ActionEntry configured = getEntry(target.itemId, "bank_inventory", option);
                if (configured == null || existingOptions.contains(bankMenuKey(child, option)))
                    continue;
                if (357782167 * Class25.anInt172 >= 504) {
                    CustomItemActionTrace.log("BANK_ADD aborted reason=menu-capacity item=" + target.itemId
                            + " child=" + child + " option=" + option + " configured=" + describe(configured));
                    return;
                }
                addBankInventoryEntry(target.itemId, target.anchor, option, configured);
                existingOptions.add(bankMenuKey(child, option));
            }
        }
    }

    private static boolean isExplicitBankInventoryMenu(int itemId) {
        String mode = PROPERTIES.getProperty("item." + itemId + ".bank_inventory.mode");
        return mode != null && EXPLICIT_MENU_MODE.equals(mode.trim().toUpperCase());
    }

    private static boolean hasBankInventoryEntries(int itemId) {
        for (int option = 1; option <= MAX_INTERFACE_OPTIONS; option++) {
            if (getEntry(itemId, "bank_inventory", option) != null)
                return true;
        }
        return false;
    }

    private static String bankMenuKey(int child, int option) {
        return child + ":" + option;
    }

    private static void addBankInventoryEntry(int itemId, Class572_Sub12_Sub10 anchor,
            int option, ActionEntry configured) {
        int child = anchor.anInt11397 * 740323685;
        int widgetHash = anchor.anInt11392 * 200110927;
        int opcode = option <= 5 ? INTERFACE_OPTION_OPCODE : INTERFACE_OPTION_SECONDARY_OPCODE;

        Class572_Sub12_Sub10 menuEntry = new Class572_Sub12_Sub10(
                configured.label,
                anchor.aString11391,
                client.anInt8751 * -646491435,
                opcode,
                -1,
                option,
                child,
                widgetHash,
                anchor.aBool11398,
                anchor.aBool11399,
                0L,
                anchor.aBool11401);

        // Keep the stock bank entry's target/group metadata while preserving the
        // configured label, option slot, and interface-option opcode above.
        menuEntry.anInt11396 = anchor.anInt11396;
        menuEntry.anInt11394 = anchor.anInt11394;
        menuEntry.anInt11397 = anchor.anInt11397;
        menuEntry.anInt11392 = anchor.anInt11392;
        menuEntry.aBool11398 = anchor.aBool11398;
        menuEntry.aBool11399 = anchor.aBool11399;
        menuEntry.aLong11400 = anchor.aLong11400;
        menuEntry.aBool11401 = anchor.aBool11401;
        menuEntry.aString11403 = anchor.aString11403;
        Class412.method5075(menuEntry, 722976984);
        CustomItemActionTrace.log("BANK_ADD widget=762:7 child=" + child + " item=" + itemId
                + " option=" + option + " action=" + configured.action + " label=" + configured.label);
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

    private static String describe(ActionEntry entry) {
        return entry == null ? "none" : entry.action + "|" + entry.label;
    }

    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;
        File file = findConfig();
        if (file == null) {
            System.err.println("Custom item actions: " + CONFIG_PATH + " was not found.");
            CustomItemActionTrace.log("CONFIG missing expected=" + CONFIG_PATH + " userDir="
                    + System.getProperty("user.dir", "."));
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
            CustomItemActionTrace.log("CONFIG loaded path=" + file.getPath() + " itemIds=" + ITEM_IDS.size());
        } catch (IOException e) {
            e.printStackTrace();
            CustomItemActionTrace.log("CONFIG read-failed path=" + file.getPath() + " error="
                    + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
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

    private static final class BankMenuTarget {
        private final int itemId;
        private final Class572_Sub12_Sub10 anchor;

        private BankMenuTarget(int itemId, Class572_Sub12_Sub10 anchor) {
            this.itemId = itemId;
            this.anchor = anchor;
        }
    }

    private static final class ActionEntry {
        private final String action;
        private final String label;

        private ActionEntry(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }
}
