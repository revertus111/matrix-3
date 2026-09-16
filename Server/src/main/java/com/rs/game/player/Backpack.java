package com.rs.game.player;

import java.io.Serializable;

import com.rs.game.item.Item;
import com.rs.game.item.ItemsContainer;
import com.rs.net.decoders.WorldPacketsDecoder;
import com.rs.utils.ItemExamines;

/**
 * Player-owned carried storage presented through Matrix3's Bank interface.
 *
 * Backpack.items remains completely independent from Bank.bankTabs. Interface
 * 762 is reused only as the presentation surface; Backpack intercepts its own
 * grid/inventory clicks before Matrix3's normal Bank button handler can run.
 */
public final class Backpack implements Serializable {

    private static final long serialVersionUID = 4939807819226985432L;

    public static final int ITEM_ID = 21445;
    public static final int CAPACITY = 30;

    private static final int BANK_INTERFACE = 762;
    private static final int BANK_INVENTORY_COMPONENT = 7;
    private static final int BANK_ITEMS_COMPONENT = 215;
    private static final int BANK_AUX_COMPONENT = 112;
    private static final int BANK_AUX_INTERFACE = 1463;
    private static final int BANK_ITEMS_KEY = 95;
    private static final int INVENTORY_ITEMS_KEY = 93;

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

        // Reuse Matrix3's Bank presentation only. Do not call Bank.openBank():
        // that would make Bank.bankTabs the displayed/interaction authority.
        player.getInterfaceManager().sendBankInterface(BANK_INTERFACE);
        player.getInterfaceManager().setInterface(true, BANK_INTERFACE, BANK_AUX_COMPONENT, BANK_AUX_INTERFACE);
        if (!player.hasEmailRestrictions())
            player.getPackets().sendCSVarInteger(1324, 3);

        accessItemId = itemId;
        open = true;
        prepareBackpackPresentation();
        sendItems();
        sendOptions();

        player.setCloseInterfacesEvent(new Runnable() {
            @Override
            public void run() {
                open = false;
                accessItemId = -1;
                restoreBankPresentationState();
            }
        });
    }

    /**
     * Bank tab/search counters are presentation state, not Backpack ownership.
     * Zero them while Backpack is open so stale real-bank tab state is not shown.
     */
    private void prepareBackpackPresentation() {
        player.getVarsManager().sendVar(4145, 2);
        player.getVarsManager().sendVarBit(288, 1);
        for (int varBit = 280; varBit <= 287; varBit++)
            player.getVarsManager().sendVarBit(varBit, 0);
        player.getPackets().sendCSVarInteger(190, 0);
        refreshTotalSize();
    }

    /** Restore Matrix3 Bank presentation vars for the next real-bank open. */
    private void restoreBankPresentationState() {
        if (player == null)
            return;
        Bank bank = player.getBank();
        if (bank == null)
            return;
        bank.refreshViewingTab();
        bank.refreshTabs();
        bank.refreshLastX();
        bank.refreshBank50Rows();
        bank.refreshTotalSize();
        player.getPackets().sendCSVarInteger(190, 0);
    }

    public boolean isOpen() {
        return open && player != null && hasAccessItem(accessItemId)
                && player.getInterfaceManager().containsBankInterface()
                && player.getInterfaceManager().containsInterface(BANK_INTERFACE);
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

    /**
     * Consumes interface 762 while Backpack mode is open so no click can fall
     * through into Matrix3 Bank.bankTabs. Only the intentionally exposed V1
     * deposit/withdraw/examine actions are executed.
     */
    public boolean processButtonClick(int interfaceId, int componentId, int slotId, int packetId) {
        if (!isOpen() || interfaceId != BANK_INTERFACE)
            return false;

        Item tracedItem = null;
        if (componentId == BANK_ITEMS_COMPONENT && slotId >= 0 && slotId < items.getSize())
            tracedItem = items.get(slotId);
        else if (componentId == BANK_INVENTORY_COMPONENT && slotId >= 0)
            tracedItem = player.getInventory().getItem(slotId);
        BackpackTrace.log("CLICK interface=" + interfaceId + " component=" + componentId
                + " slot=" + slotId + " packet=" + packetId + " item="
                + (tracedItem == null ? -1 : tracedItem.getId()));

        if (componentId == BANK_INVENTORY_COMPONENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET)
                addItem(slotId, 1);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET)
                addItem(slotId, 5);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET)
                addItem(slotId, 10);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON9_PACKET)
                addItem(slotId, Integer.MAX_VALUE);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON8_PACKET)
                examineInventory(slotId);
            return true;
        }

        if (componentId == BANK_ITEMS_COMPONENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET)
                removeItem(slotId, 1);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET)
                removeItem(slotId, 5);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET)
                removeItem(slotId, 10);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON9_PACKET)
                removeItem(slotId, Integer.MAX_VALUE);
            else if (packetId == WorldPacketsDecoder.ACTION_BUTTON8_PACKET)
                examineBackpack(slotId);
            return true;
        }

        // Backpack mode owns the entire 762 interaction surface while open.
        // Unsupported bank-only controls are intentionally inert in V1.
        return true;
    }

    private void examineInventory(int slotId) {
        Item item = player.getInventory().getItem(slotId);
        if (item != null)
            player.getPackets().sendGameMessage(ItemExamines.getExamine(item));
    }

    private void examineBackpack(int slotId) {
        if (slotId < 0 || slotId >= items.getSize())
            return;
        Item item = items.get(slotId);
        if (item != null)
            player.getPackets().sendGameMessage(ItemExamines.getExamine(item));
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
        refreshTotalSize();
    }

    public void removeItem(int backpackSlot, int amount) {
        if (player == null || amount <= 0 || backpackSlot < 0 || backpackSlot >= items.getSize())
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
        refreshTotalSize();
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
        refreshTotalSize();
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
        if (open) {
            sendItems();
            refreshTotalSize();
        }
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
        player.getPackets().sendUpdateItems(BANK_ITEMS_KEY, items, finalChanged);
    }

    private void sendItems() {
        player.getPackets().sendItems(BANK_ITEMS_KEY, items);
        player.getPackets().sendItems(INVENTORY_ITEMS_KEY, player.getInventory().getItems());
    }

    private void sendOptions() {
        // Expose only V1-owned actions. Using the narrow option-slot helper also
        // avoids enabling native Bank drag/reorder, wear, X, all-but-one, tabs,
        // search, note-mode, and other controls against the wrong backend.
        player.getPackets().sendUnlockIComponentOptionSlots(
                BANK_INTERFACE, BANK_INVENTORY_COMPONENT, 0, Inventory.INVENTORY_SIZE - 1,
                0, 1, 2, 7, 8);
        player.getPackets().sendUnlockIComponentOptionSlots(
                BANK_INTERFACE, BANK_ITEMS_COMPONENT, 0, CAPACITY - 1,
                0, 1, 2, 7, 8);
    }

    private void refreshTotalSize() {
        int usedSlots = CAPACITY - items.getFreeSlots();
        int displayed = usedSlots > 403 ? 403 : usedSlots;
        player.getPackets().sendCSVarInteger(1038, displayed);
        player.getPackets().sendCSVarInteger(192, usedSlots);
    }

    public ItemsContainer<Item> getItems() {
        return items;
    }
}
