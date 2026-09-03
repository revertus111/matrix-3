package com.rs.game.player;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.rs.Settings;
import com.rs.game.item.Item;
import com.rs.game.player.content.ItemConstants;
import com.rs.game.player.dialogues.Dialogue;
import com.rs.utils.Logger;

/**
 * Server-side backing for the existing Gear -> Presets interface.
 *
 * The cache already supplies interfaces 579/577/627, but Matrix3 never
 * completed the server-side preset storage/load path. This class deliberately
 * owns only the preset data and item transaction; the normal Bank, Inventory
 * and Equipment classes remain authoritative for those containers.
 */
public final class PresetManager {

    private static final int PRESET_INTERFACE = 579;
    private static final int PRESET_COUNT = 10;
    private static final int FILE_VERSION = 1;

    // Runtime map: row 1 starts at component 184 and row 2 starts at 233.
    // Their matching controls are exactly 49 components apart. Rows 3-10 use
    // that stride as a HYPOTHESIS until the retained mapper confirms them.
    private static final int PRESET_ROW_FIRST_COMPONENT = 184;
    private static final int PRESET_ROW_COMPONENT_STRIDE = 49;
    private static final int PRESET_SELECTOR_OFFSET = 0;
    private static final int PRESET_SAVE_OFFSET = 9;
    private static final int PRESET_LOAD_OFFSET = 17;
    private static final int PRESET_SETTINGS_OFFSET = 33;
    private static final int PRESET_DELETE_OFFSET = 41;
    private static final int PRESET_RIGHT_OPTION_ONE_OFFSET = 47;
    private static final int PRESET_RIGHT_OPTION_TWO_OFFSET = 48;

    private static final File SAVE_DIRECTORY = new File("data/presets");
    private static final ConcurrentHashMap<String, PresetProfile> PROFILES = new ConcurrentHashMap<String, PresetProfile>();
    private static final ConcurrentHashMap<String, Integer> SELECTED_PRESETS = new ConcurrentHashMap<String, Integer>();
    private static final ConcurrentHashMap<String, Boolean> NATIVE_CLICK_SIGNATURES = new ConcurrentHashMap<String, Boolean>();

    private PresetManager() {
    }

    /**
     * @return true when this click belongs to the preset interface and was
     *         consumed here.
     */
    public static boolean processButtonClick(final Player player, int interfaceId, int componentId, int slotId,
            int slotId2, int packetId) {
        if (interfaceId != PRESET_INTERFACE)
            return false;

        recordNativeClick(player, componentId, slotId, slotId2, packetId);

        int componentDelta = componentId - PRESET_ROW_FIRST_COMPONENT;
        if (componentDelta < 0)
            return false;

        int presetIndex = componentDelta / PRESET_ROW_COMPONENT_STRIDE;
        int actionOffset = componentDelta % PRESET_ROW_COMPONENT_STRIDE;
        if (presetIndex < 0 || presetIndex >= PRESET_COUNT || !isMappedPresetAction(actionOffset)) {
            if (Settings.DEBUG)
                Logger.log(PresetManager.class, "Unmapped preset click: component=" + componentId + ", slot=" + slotId
                        + ", slot2=" + slotId2 + ", packet=" + packetId);
            return false;
        }

        SELECTED_PRESETS.put(getPlayerKey(player), presetIndex);

        if (Settings.DEBUG)
            Logger.log(PresetManager.class, "Preset click: preset=" + (presetIndex + 1) + ", actionOffset="
                    + actionOffset + ", component=" + componentId + ", slot=" + slotId + ", slot2=" + slotId2
                    + ", packet=" + packetId);

        if (actionOffset == PRESET_SELECTOR_OFFSET) {
            openPresetActions(player, presetIndex);
            return true;
        }
        if (actionOffset == PRESET_SAVE_OFFSET) {
            savePreset(player, presetIndex);
            return true;
        }
        if (actionOffset == PRESET_LOAD_OFFSET) {
            loadPreset(player, presetIndex);
            return true;
        }
        if (actionOffset == PRESET_DELETE_OFFSET) {
            clearPreset(player, presetIndex);
            return true;
        }

        // Settings and the two right-side circles are identified by component
        // position, but their native semantics are not verified yet. Consume
        // them without inventing behavior while the mapper remains enabled.
        return actionOffset == PRESET_SETTINGS_OFFSET || actionOffset == PRESET_RIGHT_OPTION_ONE_OFFSET
                || actionOffset == PRESET_RIGHT_OPTION_TWO_OFFSET;
    }

    private static boolean isMappedPresetAction(int actionOffset) {
        return actionOffset == PRESET_SELECTOR_OFFSET || actionOffset == PRESET_SAVE_OFFSET
                || actionOffset == PRESET_LOAD_OFFSET || actionOffset == PRESET_SETTINGS_OFFSET
                || actionOffset == PRESET_DELETE_OFFSET || actionOffset == PRESET_RIGHT_OPTION_ONE_OFFSET
                || actionOffset == PRESET_RIGHT_OPTION_TWO_OFFSET;
    }

    private static void recordNativeClick(Player player, int componentId, int slotId, int slotId2, int packetId) {
        String signature = componentId + ":" + slotId + ":" + slotId2 + ":" + packetId;
        if (NATIVE_CLICK_SIGNATURES.putIfAbsent(signature, Boolean.TRUE) != null)
            return;
        Logger.log(PresetManager.class, "[PRESET-MAP] player=" + player.getUsername() + ", component=" + componentId
                + ", slot=" + slotId + ", slot2=" + slotId2 + ", packet=" + packetId);
    }

    private static void openPresetActions(final Player player, final int presetIndex) {
        final PresetProfile profile = getProfile(player);
        final boolean saved = profile.presets[presetIndex] != null;
        player.getDialogueManager().startDialogue(new Dialogue() {

            @Override
            public void start() {
                if (saved)
                    sendOptionsDialogue("Preset " + (presetIndex + 1), "Overwrite with current setup", "Load preset",
                            "Clear preset", "Cancel");
                else
                    sendOptionsDialogue("Preset " + (presetIndex + 1), "Save current setup", "Cancel");
            }

            @Override
            public void run(int interfaceId, int componentId) {
                if (!saved) {
                    if (componentId == OPTION_1)
                        savePreset(player, presetIndex);
                    end();
                    return;
                }

                if (componentId == OPTION_1)
                    savePreset(player, presetIndex);
                else if (componentId == OPTION_2)
                    loadPreset(player, presetIndex);
                else if (componentId == OPTION_3)
                    clearPreset(player, presetIndex);
                end();
            }

            @Override
            public void finish() {
            }
        });
    }

    private static void savePreset(Player player, int presetIndex) {
        PresetProfile profile = getProfile(player);
        Preset previous = profile.presets[presetIndex];
        Preset preset = new Preset();
        preset.name = "Preset " + (presetIndex + 1);
        preset.inventory = copyItems(player.getInventory().getItems().getItemsCopy());
        preset.equipment = copyItems(player.getEquipment().getItems().getItemsCopy());
        profile.presets[presetIndex] = preset;

        if (!saveProfile(player, profile)) {
            profile.presets[presetIndex] = previous;
            player.getPackets().sendGameMessage("The preset could not be saved.");
            return;
        }
        player.getPackets().sendGameMessage(preset.name + " saved.");
    }

    private static void clearPreset(Player player, int presetIndex) {
        PresetProfile profile = getProfile(player);
        Preset previous = profile.presets[presetIndex];
        profile.presets[presetIndex] = null;
        if (!saveProfile(player, profile)) {
            profile.presets[presetIndex] = previous;
            player.getPackets().sendGameMessage("The preset could not be cleared.");
            return;
        }
        player.getPackets().sendGameMessage("Preset " + (presetIndex + 1) + " cleared.");
    }

    private static void loadPreset(Player player, int presetIndex) {
        Preset preset = getProfile(player).presets[presetIndex];
        if (preset == null) {
            player.getPackets().sendGameMessage("That preset is empty.");
            return;
        }
        if (!player.getInterfaceManager().containsBankInterface()) {
            player.getPackets().sendGameMessage("Open your bank before loading a preset.");
            return;
        }
        if (player.isUnderCombat()) {
            player.getPackets().sendGameMessage("You can't load a preset while in combat.");
            return;
        }

        int inventorySize = player.getInventory().getItems().getSize();
        int equipmentSize = player.getEquipment().getItems().getSize();
        Item[] desiredInventory = normalizeItems(preset.inventory, inventorySize);
        Item[] desiredEquipment = normalizeItems(preset.equipment, equipmentSize);
        if (desiredInventory == null || desiredEquipment == null) {
            player.getPackets().sendGameMessage("This preset no longer fits the current inventory/equipment layout.");
            return;
        }

        if (!validateContainerRules(player, desiredInventory, desiredEquipment))
            return;

        Item[] currentInventory = copyItems(player.getInventory().getItems().getItemsCopy());
        Item[] currentEquipment = copyItems(player.getEquipment().getItems().getItemsCopy());
        Map<Integer, Long> carried = countItems(currentInventory, currentEquipment);
        Map<Integer, Long> desired = countItems(desiredInventory, desiredEquipment);
        Map<Integer, Long> bankBefore = countItems(player.getBank().generateContainer());
        Set<Integer> affected = new HashSet<Integer>();
        affected.addAll(carried.keySet());
        affected.addAll(desired.keySet());

        int finalBankSlots = player.getBank().getBankSize();
        for (Integer itemId : affected) {
            long carriedAmount = amount(carried, itemId.intValue());
            long desiredAmount = amount(desired, itemId.intValue());
            long bankAmount = amount(bankBefore, itemId.intValue());
            long withdraw = Math.max(desiredAmount - carriedAmount, 0L);
            long deposit = Math.max(carriedAmount - desiredAmount, 0L);
            if (withdraw > bankAmount) {
                player.getPackets().sendGameMessage("Your bank does not contain all items required by this preset.");
                return;
            }
            long finalAmount = bankAmount - withdraw + deposit;
            if (finalAmount > Integer.MAX_VALUE) {
                player.getPackets().sendGameMessage("A bank stack would be too large to load this preset safely.");
                return;
            }
            if (bankAmount == 0 && finalAmount > 0)
                finalBankSlots++;
            else if (bankAmount > 0 && finalAmount == 0)
                finalBankSlots--;
        }
        if (finalBankSlots > player.getBank().getMaxBankSize()) {
            player.getPackets().sendGameMessage("Not enough space in your bank to load this preset.");
            return;
        }

        try {
            // Remove bank deficits first. This makes room before carried surplus is deposited,
            // so a full bank can still perform a valid one-for-one preset swap.
            for (Integer itemId : affected) {
                long carriedAmount = amount(carried, itemId.intValue());
                long desiredAmount = amount(desired, itemId.intValue());
                int withdraw = checkedInt(Math.max(desiredAmount - carriedAmount, 0L));
                if (withdraw > 0)
                    player.getBank().removeItem(player.getBank().getItemSlot(itemId.intValue()), withdraw, false, false);
            }

            for (Integer itemId : affected) {
                long carriedAmount = amount(carried, itemId.intValue());
                long desiredAmount = amount(desired, itemId.intValue());
                int deposit = checkedInt(Math.max(carriedAmount - desiredAmount, 0L));
                if (deposit > 0 && !player.getBank().addItem(itemId.intValue(), deposit, false))
                    throw new IllegalStateException("Bank rejected validated preset deposit for item " + itemId);
            }

            Item currentAura = player.getEquipment().getItem(Equipment.SLOT_AURA);
            if (currentAura != null)
                player.getAuraManager().removeAura();

            player.getInventory().getItems().reset();
            player.getEquipment().getItems().reset();
            setItems(player.getInventory().getItems(), desiredInventory);
            setItems(player.getEquipment().getItems(), desiredEquipment);
            refreshAfterLoad(player);
            player.getPackets().sendGameMessage("Preset " + (presetIndex + 1) + " loaded.");
        } catch (Throwable e) {
            restoreBank(player, bankBefore, affected);
            restoreCarried(player, currentInventory, currentEquipment);
            refreshAfterLoad(player);
            Logger.handle(e);
            player.getPackets().sendGameMessage("Preset load failed; your items were restored.");
        }
    }

    private static boolean validateContainerRules(Player player, Item[] desiredInventory, Item[] desiredEquipment) {
        for (Item item : player.getInventory().getItems().getItems()) {
            if (item != null && !player.getControlerManager().canDeleteInventoryItem(item.getId(), item.getAmount())) {
                player.getPackets().sendGameMessage("You can't change your inventory here.");
                return false;
            }
        }
        for (Item item : desiredInventory) {
            if (item != null && !player.getControlerManager().canAddInventoryItem(item.getId(), item.getAmount())) {
                player.getPackets().sendGameMessage("You can't load that inventory here.");
                return false;
            }
        }
        for (int slot = 0; slot < player.getEquipment().getItems().getSize(); slot++) {
            Item current = player.getEquipment().getItem(slot);
            if (current != null && !player.getControlerManager().canRemoveEquip(slot, current.getId())) {
                player.getPackets().sendGameMessage("You can't remove your current equipment here.");
                return false;
            }
        }
        for (int slot = 0; slot < desiredEquipment.length; slot++) {
            Item item = desiredEquipment[slot];
            if (item == null)
                continue;
            if (!ItemConstants.canWear(item, player) || !player.getControlerManager().canEquip(slot, item.getId())) {
                player.getPackets().sendGameMessage("You no longer meet the requirements for part of this preset.");
                return false;
            }
        }
        return true;
    }

    private static void refreshAfterLoad(Player player) {
        player.getInventory().refresh();
        player.getEquipment().init();
        player.getAppearence().generateAppearenceData();
        player.getCombatDefinitions().desecreaseSpecialAttack(0);
        player.getActionbar().refreshButtons();
        player.getBank().refreshTabs();
        player.getBank().refreshItems();
        player.getBank().refreshTotalSize();
    }

    private static void restoreCarried(Player player, Item[] inventory, Item[] equipment) {
        player.getInventory().getItems().reset();
        player.getEquipment().getItems().reset();
        setItems(player.getInventory().getItems(), inventory);
        setItems(player.getEquipment().getItems(), equipment);
    }

    private static void restoreBank(Player player, Map<Integer, Long> bankBefore, Set<Integer> affected) {
        for (Integer itemId : affected) {
            int id = itemId.intValue();
            long wanted = amount(bankBefore, id);
            int[] slot = player.getBank().getItemSlot(id);
            long current = slot == null ? 0L : player.getBank().getItem(slot).getAmount();
            if (current > wanted) {
                player.getBank().removeItem(slot, checkedInt(current - wanted), false, false);
            } else if (current < wanted) {
                player.getBank().addItem(id, checkedInt(wanted - current), false);
            }
        }
    }

    private static void setItems(com.rs.game.item.ItemsContainer<Item> container, Item[] items) {
        int count = Math.min(container.getSize(), items.length);
        for (int slot = 0; slot < count; slot++) {
            Item item = items[slot];
            if (item != null)
                container.set(slot, new Item(item));
        }
    }

    private static Item[] normalizeItems(Item[] saved, int size) {
        if (saved == null)
            return new Item[size];
        if (saved.length > size) {
            for (int slot = size; slot < saved.length; slot++)
                if (saved[slot] != null)
                    return null;
        }
        Item[] normalized = new Item[size];
        int count = Math.min(size, saved.length);
        for (int slot = 0; slot < count; slot++)
            if (saved[slot] != null)
                normalized[slot] = new Item(saved[slot]);
        return normalized;
    }

    private static Map<Integer, Long> countItems(Item[]... containers) {
        Map<Integer, Long> counts = new HashMap<Integer, Long>();
        for (Item[] items : containers) {
            if (items == null)
                continue;
            for (Item item : items) {
                if (item == null)
                    continue;
                Long old = counts.get(item.getId());
                counts.put(item.getId(), (old == null ? 0L : old.longValue()) + item.getAmount());
            }
        }
        return counts;
    }

    private static long amount(Map<Integer, Long> counts, int itemId) {
        Long value = counts.get(itemId);
        return value == null ? 0L : value.longValue();
    }

    private static int checkedInt(long value) {
        if (value < 0 || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Item amount outside integer range: " + value);
        return (int) value;
    }

    private static Item[] copyItems(Item[] source) {
        if (source == null)
            return null;
        Item[] copy = new Item[source.length];
        for (int slot = 0; slot < source.length; slot++)
            if (source[slot] != null)
                copy[slot] = new Item(source[slot]);
        return copy;
    }

    private static PresetProfile getProfile(Player player) {
        String key = getPlayerKey(player);
        PresetProfile profile = PROFILES.get(key);
        if (profile != null)
            return profile;
        PresetProfile loaded = loadProfile(key);
        PresetProfile previous = PROFILES.putIfAbsent(key, loaded);
        return previous == null ? loaded : previous;
    }

    private static PresetProfile loadProfile(String key) {
        File file = new File(SAVE_DIRECTORY, key + ".dat");
        if (!file.exists())
            return new PresetProfile();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int version = input.readInt();
            if (version != FILE_VERSION)
                throw new IOException("Unsupported preset file version " + version);
            int count = input.readInt();
            if (count < 0 || count > PRESET_COUNT)
                throw new IOException("Invalid preset count " + count);
            PresetProfile profile = new PresetProfile();
            for (int slot = 0; slot < count; slot++) {
                if (!input.readBoolean())
                    continue;
                Preset preset = new Preset();
                preset.name = input.readUTF();
                preset.inventory = readItems(input);
                preset.equipment = readItems(input);
                profile.presets[slot] = preset;
            }
            return profile;
        } catch (Throwable e) {
            Logger.log(PresetManager.class, "Unable to read preset file " + file.getPath() + ": " + e.getMessage());
            return new PresetProfile();
        }
    }

    private static boolean saveProfile(Player player, PresetProfile profile) {
        String key = getPlayerKey(player);
        try {
            Files.createDirectories(SAVE_DIRECTORY.toPath());
            File file = new File(SAVE_DIRECTORY, key + ".dat");
            File temp = new File(SAVE_DIRECTORY, key + ".dat.tmp");
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                output.writeInt(FILE_VERSION);
                output.writeInt(PRESET_COUNT);
                for (int slot = 0; slot < PRESET_COUNT; slot++) {
                    Preset preset = profile.presets[slot];
                    output.writeBoolean(preset != null);
                    if (preset == null)
                        continue;
                    output.writeUTF(preset.name == null ? "Preset " + (slot + 1) : preset.name);
                    writeItems(output, preset.inventory);
                    writeItems(output, preset.equipment);
                }
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Throwable e) {
            Logger.handle(e);
            return false;
        }
    }

    private static void writeItems(DataOutputStream output, Item[] items) throws IOException {
        if (items == null) {
            output.writeInt(0);
            return;
        }
        output.writeInt(items.length);
        for (Item item : items) {
            output.writeBoolean(item != null);
            if (item != null) {
                output.writeInt(item.getId());
                output.writeInt(item.getAmount());
            }
        }
    }

    private static Item[] readItems(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 128)
            throw new IOException("Invalid preset container length " + length);
        Item[] items = new Item[length];
        for (int slot = 0; slot < length; slot++) {
            if (input.readBoolean())
                items[slot] = new Item(input.readInt(), input.readInt());
        }
        return items;
    }

    private static String getPlayerKey(Player player) {
        String username = player.getUsername() == null ? "unknown" : player.getUsername().toLowerCase();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(username.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PresetProfile {
        private final Preset[] presets = new Preset[PRESET_COUNT];
    }

    private static final class Preset {
        private String name;
        private Item[] inventory;
        private Item[] equipment;
    }
}
