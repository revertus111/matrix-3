package com.rs.game.player;

import java.io.Serializable;

import com.rs.game.item.Item;
import com.rs.game.item.ItemsContainer;
import com.rs.net.decoders.WorldPacketsDecoder;

/**
 * Player-owned carried storage backed by the existing Beast of Burden interfaces.
 *
 * This is intentionally independent from Familiar/BeastOfBurden state: the
 * backpack persists with the player's Inventory object and only reuses the
 * proven 671/665 interface layout and container interaction pattern.
 */
public final class Backpack implements Serializable {

    private static final long serialVersionUID = 4939807819226985432L;

    public static final int ITEM_ID = 21445;
    public static final int CAPACITY = 30;

    private static final int ITEMS_KEY = 531;
    private static final int STORAGE_INTERFACE = 671;
    private static final int STORAGE_COMPONENT = 27;
    private static final int INVENTORY_INTERFACE = 665;
    private static final int INVENTORY_COMPONENT = 0;

    private ItemsContainer<Item> items;

    private transient Player player;
    private transient boolean open;

    public Backpack() {
        items = new ItemsContainer<Item>(CAPACITY, false);
    }

    public void setPlayer(Player player) {
        this.player = player;
        ensureCapacity();
        open = false;
    }

    private void ensureCapacity() {
        if (items == null) {
            items = new ItemsContainer<Item>(CAPACITY, false);
            return;
        }
        if (items.getSize() == CAPACITY)
            return;
        ItemsContainer<Item> resized = new ItemsContainer<Item>(CAPACITY, false);
        int copySize = Math.min(items.getSize(), CAPACITY);
        for (int slot = 0; slot < copySize; slot++)
            resized.set(slot, items.get(slot));
        items = resized;
    }

    public boolean isEquipped() {
        if (player == null)
            return false;
        Item cape = player.getEquipment().getItem(Equipment.SLOT_CAPE);
        return cape != null && cape.getId() == ITEM_ID;
    }

    public void open() {
        if (player == null)
            return;
        if (!isEquipped()) {
            player.getPackets().sendGameMessage("Equip the Rambler's backpack before opening it.");
            return;
        }
        player.stopAll();
        player.getInterfaceManager().sendCentralInterface(STORAGE_INTERFACE);
        player.getInterfaceManager().sendInventoryInterface(INVENTORY_INTERFACE);
        open = true;
        player.setCloseInterfacesEvent(new Runnable() {
            @Override
            public void run() {
                open = false;
            }
        });
        sendItems();
        sendOptions();
    }

    public boolean isOpen() {
        return open && player != null
                && player.getInterfaceManager().containsInterface(STORAGE_INTERFACE)
                && player.getInterfaceManager().containsInterface(INVENTORY_INTERFACE);
    }

    public boolean processButtonClick(int interfaceId, int componentId, int slotId, int packetId) {
        if (!isOpen())
            return false;
        if (interfaceId == INVENTORY_INTERFACE && componentId == INVENTORY_COMPONENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET)
                addItem(slotId, 1);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET)
                addItem(slotId, 5);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET)
                addItem(slotId, 10);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON4_PACKET)
                addItem(slotId, Integer.MAX_VALUE);
            else
                return false;
            return true;
        }
        if (interfaceId == STORAGE_INTERFACE && componentId == STORAGE_COMPONENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET)
                removeItem(slotId, 1);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET)
                removeItem(slotId, 5);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET)
                removeItem(slotId, 10);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON4_PACKET)
                removeItem(slotId, Integer.MAX_VALUE);
            else
                return false;
            return true;
        }
        if (interfaceId == STORAGE_INTERFACE && componentId == 29
                && packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET) {
            takeAll();
            return true;
        }
        return false;
    }

    public void addItem(int inventorySlot, int amount) {
        if (player == null || amount <= 0)
            return;
        Item source = player.getInventory().getItem(inventorySlot);
        if (source == null)
            return;
        if (source.getId() == ITEM_ID) {
            player.getPackets().sendGameMessage("You cannot put the backpack inside itself.");
            return;
        }

        int availableAmount = player.getInventory().getItems().getNumberOf(source);
        Item moving = new Item(source.getId(), Math.min(amount, availableAmount));
        if (amount == Integer.MAX_VALUE)
            moving.setAmount(availableAmount);

        Item[] before = items.getItemsCopy();
        if (!items.add(moving)) {
            player.getPackets().sendGameMessage("Not enough space in your backpack.");
            return;
        }
        player.getInventory().deleteItem(inventorySlot, moving);
        refreshItems(before);
    }

    public void removeItem(int backpackSlot, int amount) {
        if (player == null || amount <= 0)
            return;
        Item source = items.get(backpackSlot);
        if (source == null)
            return;

        int availableAmount = items.getNumberOf(source);
        Item moving = new Item(source.getId(), Math.min(amount, availableAmount));
        if (amount == Integer.MAX_VALUE)
            moving.setAmount(availableAmount);

        if (!player.getInventory().addItem(moving))
            return;

        Item[] before = items.getItemsCopy();
        items.remove(backpackSlot, moving);
        items.shift();
        refreshItems(before);
    }

    public void takeAll() {
        if (player == null)
            return;
        for (int slot = 0; slot < items.getSize(); slot++) {
            Item item = items.get(slot);
            if (item == null)
                continue;
            if (!player.getInventory().addItem(new Item(item.getId(), item.getAmount())))
                break;
            items.set(slot, null);
        }
        items.shift();
        sendItems();
    }

    /**
     * Transactional migration target for the temporary 36-slot inventory build.
     * The live backpack is only replaced when every overflow item fits.
     */
    public boolean migrateOverflow(Item[] overflow) {
        if (overflow == null || overflow.length == 0)
            return true;
        ItemsContainer<Item> migrated = new ItemsContainer<Item>(CAPACITY, false);
        for (int slot = 0; slot < items.getSize(); slot++) {
            Item item = items.get(slot);
            if (item != null && !migrated.add(new Item(item.getId(), item.getAmount())))
                return false;
        }
        for (Item item : overflow) {
            if (item != null && !migrated.add(new Item(item.getId(), item.getAmount())))
                return false;
        }
        items = migrated;
        return true;
    }

    private void refreshItems(Item[] before) {
        int[] changed = new int[before.length];
        int count = 0;
        for (int slot = 0; slot < before.length; slot++) {
            if (before[slot] != items.getItems()[slot])
                changed[count++] = slot;
        }
        int[] finalChanged = new int[count];
        System.arraycopy(changed, 0, finalChanged, 0, count);
        player.getPackets().sendUpdateItems(ITEMS_KEY, items, finalChanged);
    }

    private void sendItems() {
        player.getPackets().sendItems(ITEMS_KEY, items);
        player.getPackets().sendItems(93, player.getInventory().getItems());
    }

    private void sendOptions() {
        player.getPackets().sendUnlockIComponentOptionSlots(
                INVENTORY_INTERFACE, INVENTORY_COMPONENT, 0, Inventory.INVENTORY_SIZE - 1, 0, 1, 2, 3);
        player.getPackets().sendInterSetItemsOptionsScript(
                INVENTORY_INTERFACE, INVENTORY_COMPONENT, 93, 4, 7,
                "Store", "Store-5", "Store-10", "Store-All");

        player.getPackets().sendUnlockIComponentOptionSlots(
                STORAGE_INTERFACE, STORAGE_COMPONENT, 0, CAPACITY - 1, 0, 1, 2, 3);
        player.getPackets().sendInterSetItemsOptionsScript(
                STORAGE_INTERFACE, STORAGE_COMPONENT, ITEMS_KEY, 6, 5,
                "Withdraw", "Withdraw-5", "Withdraw-10", "Withdraw-All");
    }

    public ItemsContainer<Item> getItems() {
        return items;
    }
}
