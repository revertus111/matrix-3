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

    // Interface 671's main BoB grid is bound to the native client container key 530.
    // This is only the UI/protocol key; Backpack.items remains separate player-owned storage.
    private static final int ITEMS_KEY = 530;
    private static final int STORAGE_INTERFACE = 671;
    private static final int STORAGE_COMPONENT = 27;
    private static final int INVENTORY_INTERFACE = 665;
    private static final int INVENTORY_COMPONENT = 0;

    private ItemsContainer<Item> items;

    private transient Player player;
    private transient boolean open;
    private transient int accessItemId = -1;

    public Backpack() {
        items = new ItemsContainer<Item>(CAPACITY, false);
    }

    public void setPlayer(Player player) {
        this.player = player;
        ensureCapacity();
        open = false;
        accessItemId = -1;
    }

    private void ensureCapacity() {
        if (items == null) {
            items = new ItemsContainer<Item>(CAPACITY, false);
            return;
        }
        if (items.getSize() >= CAPACITY)
            return;
        ItemsContainer<Item> resized = new ItemsContainer<Item>(CAPACITY, false);
        for (int slot = 0; slot < items.getSize(); slot++)
            resized.set(slot, items.get(slot));
        items = resized;
    }

    public boolean isEquipped() {
        if (player == null)
            return false;
        Item cape = player.getEquipment().getItem(Equipment.SLOT_CAPE);
        return cape != null && cape.getId() == ITEM_ID;
    }

    /**
     * Owner-console fallback kept intentionally equipment-only. Normal contextual
     * access is validated by openFromInventory/openFromEquipment/openFromBank.
     */
    public void open() {
        if (player == null)
            return;
        if (!isEquipped()) {
            player.getPackets().sendGameMessage("Equip the Rambler's backpack before opening it.");
            return;
        }
        openInternal(ITEM_ID);
    }

    public void openFromInventory(int slotId, int itemId) {
        if (player == null)
            return;
        Item item = player.getInventory().getItem(slotId);
        if (item == null || item.getId() != itemId)
            return;
        openInternal(itemId);
    }

    public void openFromEquipment(int slotId, int itemId) {
        if (player == null)
            return;
        Item item = player.getEquipment().getItem(slotId);
        if (item == null || item.getId() != itemId)
            return;
        openInternal(itemId);
    }

    public void openFromBank(int fakeSlot, int itemId) {
        if (player == null)
            return;
        Item item = player.getBank().getItem(player.getBank().getRealSlot(fakeSlot));
        if (item == null || item.getId() != itemId)
            return;
        openInternal(itemId);
    }

    private void openInternal(int itemId) {
        player.stopAll();
        player.getInterfaceManager().sendCentralInterface(STORAGE_INTERFACE);
        player.getInterfaceManager().sendInventoryInterface(INVENTORY_INTERFACE);
        accessItemId = itemId;
        open = true;
        player.setCloseInterfacesEvent(new Runnable() {
            @Override
            public void run() {
                open = false;
                accessItemId = -1;
            }
        });
        sendItems();
        sendOptions();
    }

    public boolean isOpen() {
        return open && player != null && hasAccessItem(accessItemId)
                && player.getInterfaceManager().containsInterface(STORAGE_INTERFACE)
                && player.getInterfaceManager().containsInterface(INVENTORY_INTERFACE);
    }

    private boolean hasAccessItem(int itemId) {
        if (player == null || itemId < 0)
            return false;
        if (player.getInventory().containsOneItem(itemId))
            return true;
        for (int slot = 0; slot < player.getEquipment().getItems().getSize(); slot++) {
            Item item = player.getEquipment().getItem(slot);
            if (item != null && item.getId() == itemId)
                return true;
        }
        return player.getBank().containsItem(itemId);
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
        Item moving = new Item(source.getId(), amount == Integer.MAX_VALUE
                ? availableAmount : Math.min(amount, availableAmount));
        if (!player.getControlerManager().canDeleteInventoryItem(moving.getId(), moving.getAmount()))
            return;

        int freeSpace = items.getFreeSlots();
        if (!moving.getDefinitions().isStackable() && !moving.getDefinitions().isNoted()) {
            if (freeSpace == 0) {
                player.getPackets().sendGameMessage("Not enough space in your backpack.");
                return;
            }
            if (moving.getAmount() > freeSpace) {
                moving.setAmount(freeSpace);
                player.getPackets().sendGameMessage("Not enough space in your backpack.");
            }
        } else if (freeSpace == 0 && !items.containsOne(moving)) {
            player.getPackets().sendGameMessage("Not enough space in your backpack.");
            return;
        }

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
        Item moving = new Item(source.getId(), amount == Integer.MAX_VALUE
                ? availableAmount : Math.min(amount, availableAmount));

        int freeSpace = player.getInventory().getFreeSlots();
        if (!moving.getDefinitions().isStackable() && !moving.getDefinitions().isNoted()) {
            if (freeSpace == 0) {
                player.getPackets().sendGameMessage("Not enough space in your inventory.");
                return;
            }
            if (moving.getAmount() > freeSpace) {
                moving.setAmount(freeSpace);
                player.getPackets().sendGameMessage("Not enough space in your inventory.");
            }
        } else if (freeSpace == 0 && !player.getInventory().containsItem(moving.getId(), 1)) {
            player.getPackets().sendGameMessage("Not enough space in your inventory.");
            return;
        }

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
            int beforeAmount = player.getInventory().getAmountOf(item.getId());
            if (!player.getInventory().addItem(new Item(item.getId(), item.getAmount()))) {
                int added = player.getInventory().getAmountOf(item.getId()) - beforeAmount;
                if (added > 0)
                    items.remove(slot, new Item(item.getId(), added));
                break;
            }
            items.set(slot, null);
        }
        items.shift();
        sendItems();
    }

    public void emptyToBankFromBank(int fakeSlot, int itemId) {
        if (player == null)
            return;
        Item accessItem = player.getBank().getItem(player.getBank().getRealSlot(fakeSlot));
        if (accessItem == null || accessItem.getId() != itemId)
            return;
        emptyToBank();
    }

    /**
     * Moves only successfully banked quantities out of player-owned Backpack
     * storage. The physical access item remains wherever it already is.
     */
    public void emptyToBank() {
        if (player == null)
            return;
        boolean movedAnything = false;
        boolean full = false;
        for (int slot = 0; slot < items.getSize(); slot++) {
            Item item = items.get(slot);
            if (item == null)
                continue;
            int before = getBankAmount(item.getId());
            player.getBank().addItem(item.getId(), item.getAmount(), true);
            int after = getBankAmount(item.getId());
            int moved = after - before;
            if (moved <= 0) {
                full = true;
                break;
            }
            movedAnything = true;
            if (moved >= item.getAmount())
                items.set(slot, null);
            else {
                items.set(slot, new Item(item.getId(), item.getAmount() - moved));
                full = true;
                break;
            }
        }
        items.shift();
        if (open)
            sendItems();
        if (full)
            player.getPackets().sendGameMessage("Your bank does not have enough space for all backpack items.");
        else if (movedAnything)
            player.getPackets().sendGameMessage("You empty your backpack into your bank.");
    }

    private int getBankAmount(int itemId) {
        Item item = player.getBank().getItem(itemId);
        return item == null ? 0 : item.getAmount();
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
