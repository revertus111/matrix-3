package com.rs.game.player.content.construction;

import java.io.Serializable;

import com.rs.game.item.Item;
import com.rs.game.item.ItemsContainer;

/**
 * Persistent item buffers owned by one physical settlement workstation.
 *
 * The machine owns only local input/output inventory. It never reaches into
 * arbitrary settlement storage; workers or later transport systems must move
 * items into and out of these buffers explicitly.
 */
public final class SettlementMachineBuffer implements Serializable {

    private static final long serialVersionUID = -5146226409421615037L;

    public static final int INPUT_SLOT_CAPACITY = 4;
    public static final int OUTPUT_SLOT_CAPACITY = 4;
    public static final int STACK_LIMIT = 100;

    private final long pieceId;
    private ItemBuffer input =
            new ItemBuffer(INPUT_SLOT_CAPACITY, STACK_LIMIT);
    private ItemBuffer output =
            new ItemBuffer(OUTPUT_SLOT_CAPACITY, STACK_LIMIT);

    public SettlementMachineBuffer(long pieceId) {
        this.pieceId = pieceId;
    }

    public synchronized void normalize() {
        if (input == null) {
            input = new ItemBuffer(INPUT_SLOT_CAPACITY, STACK_LIMIT);
        }
        if (output == null) {
            output = new ItemBuffer(OUTPUT_SLOT_CAPACITY, STACK_LIMIT);
        }
        input.normalize();
        output.normalize();
    }

    public long getPieceId() {
        return pieceId;
    }

    public synchronized long getInputAmount(int itemId) {
        normalize();
        return input.getItemAmount(itemId);
    }

    public synchronized long getOutputAmount(int itemId) {
        normalize();
        return output.getItemAmount(itemId);
    }

    public synchronized long getInputCapacityForItem(int itemId) {
        normalize();
        return input.getAvailableCapacityForItem(itemId);
    }

    public synchronized long getOutputCapacityForItem(int itemId) {
        normalize();
        return output.getAvailableCapacityForItem(itemId);
    }

    public synchronized int addInput(int itemId, int amount) {
        normalize();
        return input.addItem(itemId, amount);
    }

    public synchronized int removeInput(int itemId, int amount) {
        normalize();
        return input.removeItem(itemId, amount);
    }

    public synchronized int addOutput(int itemId, int amount) {
        normalize();
        return output.addItem(itemId, amount);
    }

    public synchronized int removeOutput(int itemId, int amount) {
        normalize();
        return output.removeItem(itemId, amount);
    }

    public synchronized boolean isEmpty() {
        normalize();
        return input.isEmpty() && output.isEmpty();
    }

    public synchronized String getSummary() {
        normalize();
        return "machine#" + pieceId
                + " inputSlots=" + input.getUsedSlots() + "/" + INPUT_SLOT_CAPACITY
                + ", outputSlots=" + output.getUsedSlots() + "/" + OUTPUT_SLOT_CAPACITY;
    }

    private static final class ItemBuffer implements Serializable {

        private static final long serialVersionUID = 1589874579803249687L;

        private final int slotCapacity;
        private final int stackLimit;
        private ItemsContainer<Item> items;

        private ItemBuffer(int slotCapacity, int stackLimit) {
            this.slotCapacity = Math.max(1, slotCapacity);
            this.stackLimit = Math.max(1, stackLimit);
            this.items = new ItemsContainer<Item>(this.slotCapacity, false);
        }

        private void normalize() {
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

        private long getAvailableCapacityForItem(int itemId) {
            normalize();
            if (itemId < 0) {
                return 0L;
            }
            Item probe = new Item(itemId, 1);
            int perSlot = isMultiAmount(probe) ? stackLimit : 1;
            long capacity = 0L;
            for (int slot = 0; slot < items.getSize(); slot++) {
                Item current = items.get(slot);
                if (current == null) {
                    capacity += perSlot;
                } else if (current.getId() == itemId && current.getAmount() < perSlot) {
                    capacity += perSlot - current.getAmount();
                }
            }
            return capacity;
        }

        private int addItem(int itemId, int amount) {
            normalize();
            if (itemId < 0 || amount <= 0) {
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

        private int removeItem(int itemId, int amount) {
            normalize();
            if (itemId < 0 || amount <= 0) {
                return 0;
            }
            int remaining = amount;
            int slot = 0;
            while (slot < items.getSize() && remaining > 0) {
                Item current = items.get(slot);
                if (current == null || current.getId() != itemId) {
                    slot++;
                    continue;
                }
                int removed = Math.min(remaining, current.getAmount());
                int next = current.getAmount() - removed;
                remaining -= removed;
                if (next <= 0) {
                    items.set(slot, null);
                    items.shift();
                } else {
                    items.set(slot, new Item(itemId, next));
                    slot++;
                }
            }
            return amount - remaining;
        }

        private long getItemAmount(int itemId) {
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

        private int getUsedSlots() {
            normalize();
            return slotCapacity - items.getFreeSlots();
        }

        private boolean isEmpty() {
            return getUsedSlots() == 0;
        }

        private boolean isMultiAmount(Item item) {
            return item != null
                    && (item.getDefinitions().isStackable()
                            || item.getDefinitions().isNoted());
        }
    }
}
