package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.rs.game.item.Item;
import com.rs.game.item.ItemsContainer;

/**
 * Persistent item inventory owned by one physical settlement storage build.
 *
 * Stackable items are deliberately capped per physical slot. Once a stack
 * reaches the configured limit, the same item may occupy another slot.
 */
public final class SettlementStorageContainer implements Serializable {

    private static final long serialVersionUID = 3337373324247715412L;

    public static final int BASIC_SLOT_CAPACITY = 16;
    public static final int BASIC_STACK_LIMIT = 100;

    private final long pieceId;
    private int slotCapacity = BASIC_SLOT_CAPACITY;
    private int stackLimit = BASIC_STACK_LIMIT;
    private SettlementStorageMode mode = SettlementStorageMode.STORAGE;
    private Set<Integer> itemFilters = new HashSet<Integer>();
    private ItemsContainer<Item> items =
            new ItemsContainer<Item>(BASIC_SLOT_CAPACITY, false);

    public SettlementStorageContainer(long pieceId) {
        this.pieceId = pieceId;
    }

    public synchronized void normalize() {
        if (slotCapacity <= 0) {
            slotCapacity = BASIC_SLOT_CAPACITY;
        }
        if (stackLimit <= 0) {
            stackLimit = BASIC_STACK_LIMIT;
        }
        if (mode == null) {
            mode = SettlementStorageMode.STORAGE;
        }
        if (itemFilters == null) {
            itemFilters = new HashSet<Integer>();
        }
        if (items == null || items.getSize() != slotCapacity) {
            ItemsContainer<Item> resized =
                    new ItemsContainer<Item>(slotCapacity, false);
            if (items != null) {
                int copy = Math.min(items.getSize(), slotCapacity);
                for (int slot = 0; slot < copy; slot++) {
                    Item item = items.get(slot);
                    if (item != null) {
                        resized.set(slot, new Item(item.getId(), item.getAmount()));
                    }
                }
            }
            items = resized;
        }
    }

    public long getPieceId() {
        return pieceId;
    }

    public synchronized int getSlotCapacity() {
        normalize();
        return slotCapacity;
    }

    public synchronized int getStackLimit() {
        normalize();
        return stackLimit;
    }

    public synchronized SettlementStorageMode getMode() {
        normalize();
        return mode;
    }

    public synchronized void setMode(SettlementStorageMode mode) {
        normalize();
        this.mode = mode == null ? SettlementStorageMode.STORAGE : mode;
    }

    public synchronized void setItemFilter(int itemId, boolean enabled) {
        normalize();
        Integer key = Integer.valueOf(itemId);
        if (enabled) {
            itemFilters.add(key);
        } else {
            itemFilters.remove(key);
        }
    }

    public synchronized boolean acceptsItem(int itemId) {
        normalize();
        return itemFilters.isEmpty()
                || itemFilters.contains(Integer.valueOf(itemId));
    }

    public synchronized Set<Integer> snapshotItemFilters() {
        normalize();
        return new HashSet<Integer>(itemFilters);
    }

    public synchronized void upgradeLimits(int newSlotCapacity, int newStackLimit) {
        normalize();
        if (newSlotCapacity < slotCapacity || newStackLimit < stackLimit) {
            throw new IllegalArgumentException(
                    "Storage upgrades cannot reduce existing limits.");
        }
        if (newSlotCapacity != slotCapacity) {
            ItemsContainer<Item> resized =
                    new ItemsContainer<Item>(newSlotCapacity, false);
            for (int slot = 0; slot < items.getSize(); slot++) {
                Item item = items.get(slot);
                if (item != null) {
                    resized.set(slot, new Item(item.getId(), item.getAmount()));
                }
            }
            items = resized;
            slotCapacity = newSlotCapacity;
        }
        stackLimit = newStackLimit;
    }

    public synchronized int addItem(int itemId, int amount) {
        normalize();
        if (itemId < 0 || amount <= 0
                || (!itemFilters.isEmpty()
                        && !itemFilters.contains(Integer.valueOf(itemId)))) {
            return 0;
        }

        Item probe = new Item(itemId, 1);
        int perSlot = isMultiAmount(probe) ? stackLimit : 1;
        int remaining = amount;

        for (int slot = 0; slot < items.getSize() && remaining > 0; slot++) {
            Item current = items.get(slot);
            if (current == null || current.getId() != itemId
                    || current.getAmount() >= perSlot) {
                continue;
            }
            int accepted = Math.min(remaining, perSlot - current.getAmount());
            items.set(slot, new Item(itemId, current.getAmount() + accepted));
            remaining -= accepted;
        }

        for (int slot = 0; slot < items.getSize() && remaining > 0; slot++) {
            if (items.get(slot) != null) {
                continue;
            }
            int accepted = Math.min(remaining, perSlot);
            items.set(slot, new Item(itemId, accepted));
            remaining -= accepted;
        }
        return amount - remaining;
    }

    public synchronized int removeItemFromSlot(int slot, int amount) {
        normalize();
        if (slot < 0 || slot >= items.getSize() || amount <= 0) {
            return 0;
        }
        Item current = items.get(slot);
        if (current == null) {
            return 0;
        }
        int removed = Math.min(amount, current.getAmount());
        int remainder = current.getAmount() - removed;
        if (remainder <= 0) {
            items.set(slot, null);
            items.shift();
        } else {
            items.set(slot, new Item(current.getId(), remainder));
        }
        return removed;
    }

    public synchronized Item getItem(int slot) {
        normalize();
        if (slot < 0 || slot >= items.getSize()) {
            return null;
        }
        Item item = items.get(slot);
        return item == null ? null : new Item(item.getId(), item.getAmount());
    }

    public synchronized long getItemAmount(int itemId) {
        normalize();
        long total = 0L;
        for (int slot = 0; slot < items.getSize(); slot++) {
            Item item = items.get(slot);
            if (item != null && item.getId() == itemId) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public synchronized int getUsedSlots() {
        normalize();
        return slotCapacity - items.getFreeSlots();
    }

    public synchronized boolean isEmpty() {
        return getUsedSlots() == 0;
    }

    public synchronized Item[] snapshotItems() {
        normalize();
        Item[] copy = new Item[items.getSize()];
        for (int slot = 0; slot < items.getSize(); slot++) {
            Item item = items.get(slot);
            if (item != null) {
                copy[slot] = new Item(item.getId(), item.getAmount());
            }
        }
        return copy;
    }

    private boolean isMultiAmount(Item item) {
        return item != null
                && (item.getDefinitions().isStackable()
                        || item.getDefinitions().isNoted());
    }
}
