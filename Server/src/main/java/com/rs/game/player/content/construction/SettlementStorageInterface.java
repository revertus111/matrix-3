package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rs.game.item.Item;
import com.rs.game.player.Bank;
import com.rs.game.player.Inventory;
import com.rs.game.player.Player;
import com.rs.net.decoders.WorldPacketsDecoder;
import com.rs.utils.ItemExamines;

/**
 * Bank-interface presentation bridge for physical settlement storage.
 *
 * Interface 762 is a presentation shell only. The player's real Bank remains
 * independent and all clicks are consumed here while this session is active.
 */
public final class SettlementStorageInterface {

    private static final int BANK_INTERFACE = 762;
    private static final int BANK_INVENTORY_COMPONENT = 7;
    private static final int BANK_ITEMS_COMPONENT = 215;
    private static final int BANK_AUX_COMPONENT = 112;
    private static final int BANK_AUX_INTERFACE = 1463;
    private static final int BANK_ITEMS_KEY = 95;
    private static final int INVENTORY_ITEMS_KEY = 93;

    private static final String SESSION_KEY = "settlement_storage_session";
    private static final String X_SLOT_KEY = "settlement_storage_x_slot";
    private static final String X_WITHDRAW_KEY = "settlement_storage_x_withdraw";

    private SettlementStorageInterface() {
    }

    private static final class Session {
        private final long pieceId;
        private final boolean overview;

        private Session(long pieceId, boolean overview) {
            this.pieceId = pieceId;
            this.overview = overview;
        }
    }

    public static void openChest(Player player, long pieceId) {
        if (player == null) {
            return;
        }
        SettlementStorageContainer container =
                player.getSettlementState().findStorageContainer(pieceId);
        if (container == null) {
            player.getPackets().sendGameMessage(
                    "That settlement chest is no longer available.");
            return;
        }
        open(player, new Session(pieceId, false));
    }

    public static void openOverview(Player player) {
        if (player == null) {
            return;
        }
        open(player, new Session(-1L, true));
        player.getPackets().sendGameMessage(
                "Settlement Storage Overview is read-only. Open a physical chest to move items.");
    }

    private static void open(final Player player, Session session) {
        player.closeInterfaces();
        player.stopAll();

        player.getInterfaceManager().sendBankInterface(BANK_INTERFACE);
        player.getInterfaceManager().setInterface(
                true, BANK_INTERFACE, BANK_AUX_COMPONENT, BANK_AUX_INTERFACE);
        if (!player.hasEmailRestrictions()) {
            player.getPackets().sendCSVarInteger(1324, 3);
        }

        player.getTemporaryAttributtes().put(SESSION_KEY, session);
        preparePresentation(player);
        sendItems(player);
        sendOptions(player, session);

        player.setCloseInterfacesEvent(new Runnable() {
            @Override
            public void run() {
                player.getTemporaryAttributtes().remove(SESSION_KEY);
                player.getTemporaryAttributtes().remove(X_SLOT_KEY);
                player.getTemporaryAttributtes().remove(X_WITHDRAW_KEY);
                restoreBankPresentationState(player);
            }
        });
    }

    public static boolean processButtonClick(Player player, int interfaceId,
            int componentId, int slotId, int packetId) {
        Session session = getOpenSession(player);
        if (session == null || interfaceId != BANK_INTERFACE) {
            return false;
        }

        if (session.overview) {
            if (componentId == BANK_ITEMS_COMPONENT
                    && packetId == WorldPacketsDecoder.ACTION_BUTTON8_PACKET) {
                examineDisplayItem(player, slotId, buildOverviewItems(player));
            }
            return true;
        }

        SettlementStorageContainer container =
                player.getSettlementState().findStorageContainer(session.pieceId);
        if (container == null) {
            player.getPackets().sendGameMessage(
                    "That settlement chest is no longer available.");
            player.closeInterfaces();
            return true;
        }

        if (componentId == BANK_INVENTORY_COMPONENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET) {
                deposit(player, container, slotId, 1, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET) {
                deposit(player, container, slotId, 5, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET) {
                deposit(player, container, slotId, 10, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON5_PACKET) {
                requestX(player, slotId, false);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON9_PACKET) {
                deposit(player, container, slotId, Integer.MAX_VALUE, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON8_PACKET) {
                Item item = player.getInventory().getItem(slotId);
                if (item != null) {
                    player.getPackets().sendGameMessage(
                            ItemExamines.getExamine(item));
                }
            }
            return true;
        }

        if (componentId == BANK_ITEMS_COMPONENT) {
            if (packetId == WorldPacketsDecoder.ACTION_BUTTON1_PACKET) {
                withdraw(player, container, slotId, 1, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON2_PACKET) {
                withdraw(player, container, slotId, 5, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON3_PACKET) {
                withdraw(player, container, slotId, 10, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON5_PACKET) {
                requestX(player, slotId, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON9_PACKET) {
                withdraw(player, container, slotId, Integer.MAX_VALUE, true);
            } else if (packetId == WorldPacketsDecoder.ACTION_BUTTON8_PACKET) {
                Item item = container.getItem(slotId);
                if (item != null) {
                    player.getPackets().sendGameMessage(
                            ItemExamines.getExamine(item));
                }
            }
            return true;
        }

        if (componentId == 83) {
            depositAllInventory(player, container);
            return true;
        }

        return true;
    }

    public static boolean processIntegerInput(Player player, int value) {
        Session session = getOpenSession(player);
        if (session == null || session.overview) {
            return false;
        }
        Integer slot = (Integer) player.getTemporaryAttributtes().remove(X_SLOT_KEY);
        Boolean withdraw =
                (Boolean) player.getTemporaryAttributtes().remove(X_WITHDRAW_KEY);
        if (slot == null) {
            return false;
        }
        SettlementStorageContainer container =
                player.getSettlementState().findStorageContainer(session.pieceId);
        if (container == null) {
            player.closeInterfaces();
            return true;
        }
        if (withdraw == Boolean.TRUE) {
            withdraw(player, container, slot.intValue(), value, true);
        } else {
            deposit(player, container, slot.intValue(), value, true);
        }
        return true;
    }

    private static void requestX(Player player, int slotId, boolean withdraw) {
        player.getTemporaryAttributtes().put(
                X_SLOT_KEY, Integer.valueOf(slotId));
        if (withdraw) {
            player.getTemporaryAttributtes().put(
                    X_WITHDRAW_KEY, Boolean.TRUE);
        } else {
            player.getTemporaryAttributtes().remove(X_WITHDRAW_KEY);
        }
        player.getPackets().sendInputIntegerScript("Enter Amount:");
    }

    private static void depositAllInventory(Player player,
            SettlementStorageContainer container) {
        boolean moved = false;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (player.getInventory().getItem(slot) == null) {
                continue;
            }
            if (deposit(player, container, slot, Integer.MAX_VALUE, false) > 0) {
                moved = true;
            }
        }
        if (!moved) {
            player.getPackets().sendGameMessage(
                    "No inventory items could be stored in this chest.");
        }
        sendItems(player);
        refreshTotalSize(player);
    }

    private static int deposit(Player player,
            SettlementStorageContainer container, int inventorySlot,
            int amount, boolean refresh) {
        if (amount <= 0) {
            return 0;
        }
        Item source = player.getInventory().getItem(inventorySlot);
        if (source == null) {
            return 0;
        }
        int requested = amount == Integer.MAX_VALUE
                ? source.getAmount() : Math.min(amount, source.getAmount());
        if (requested <= 0
                || !player.getControlerManager().canDeleteInventoryItem(
                        source.getId(), requested)) {
            return 0;
        }

        int accepted = container.addItem(source.getId(), requested);
        if (accepted <= 0) {
            player.getPackets().sendGameMessage(
                    "That settlement chest is full.");
            return 0;
        }

        player.getInventory().deleteItem(
                inventorySlot, new Item(source.getId(), accepted));
        if (accepted < requested) {
            player.getPackets().sendGameMessage(
                    "The chest filled up; only " + accepted
                    + " item(s) were stored.");
        }
        if (refresh) {
            sendItems(player);
            refreshTotalSize(player);
        }
        return accepted;
    }

    private static int withdraw(Player player,
            SettlementStorageContainer container, int chestSlot,
            int amount, boolean refresh) {
        if (amount <= 0) {
            return 0;
        }
        Item source = container.getItem(chestSlot);
        if (source == null) {
            return 0;
        }

        int requested = amount == Integer.MAX_VALUE
                ? source.getAmount() : Math.min(amount, source.getAmount());
        boolean multiAmount =
                source.getDefinitions().isStackable()
                || source.getDefinitions().isNoted();
        int fit;
        if (multiAmount) {
            fit = player.getInventory().containsItem(source.getId(), 1)
                    || player.getInventory().hasFreeSlots()
                    ? requested : 0;
        } else {
            fit = Math.min(requested, player.getInventory().getFreeSlots());
        }
        if (fit <= 0) {
            player.getPackets().sendGameMessage(
                    "Not enough space in your inventory.");
            return 0;
        }

        int removed = container.removeItemFromSlot(chestSlot, fit);
        if (removed <= 0) {
            return 0;
        }
        if (!player.getInventory().addItem(new Item(source.getId(), removed))) {
            int restored = container.addItem(source.getId(), removed);
            if (restored != removed) {
                throw new IllegalStateException(
                        "Settlement storage rollback failed for item "
                        + source.getId());
            }
            return 0;
        }

        if (removed < requested) {
            player.getPackets().sendGameMessage(
                    "Your inventory filled up; only " + removed
                    + " item(s) were withdrawn.");
        }
        if (refresh) {
            sendItems(player);
            refreshTotalSize(player);
        }
        return removed;
    }

    private static Session getOpenSession(Player player) {
        if (player == null
                || !player.getInterfaceManager().containsBankInterface()
                || !player.getInterfaceManager().containsInterface(BANK_INTERFACE)) {
            return null;
        }
        Object value =
                player.getTemporaryAttributtes().get(SESSION_KEY);
        return value instanceof Session ? (Session) value : null;
    }

    private static void preparePresentation(Player player) {
        player.getVarsManager().sendVar(4145, 2);
        player.getVarsManager().sendVarBit(288, 1);
        for (int varBit = 280; varBit <= 287; varBit++) {
            player.getVarsManager().sendVarBit(varBit, 0);
        }
        player.getVarsManager().sendVarBit(942, 0);
        player.getPackets().sendCSVarInteger(190, 0);
        refreshTotalSize(player);
    }

    private static void restoreBankPresentationState(Player player) {
        if (player == null) {
            return;
        }
        Bank bank = player.getBank();
        if (bank == null) {
            return;
        }
        bank.refreshViewingTab();
        bank.refreshTabs();
        bank.refreshLastX();
        bank.refreshBank50Rows();
        bank.refreshTotalSize();
        player.getPackets().sendCSVarInteger(190, 0);
    }

    private static void sendItems(Player player) {
        Session session = getOpenSession(player);
        if (session == null) {
            return;
        }
        Item[] display;
        if (session.overview) {
            display = buildOverviewItems(player);
        } else {
            SettlementStorageContainer container =
                    player.getSettlementState().findStorageContainer(
                            session.pieceId);
            display = container == null
                    ? new Item[0] : container.snapshotItems();
        }
        player.getPackets().sendItems(BANK_ITEMS_KEY, display);
        player.getPackets().sendItems(
                INVENTORY_ITEMS_KEY, player.getInventory().getItems());
    }

    private static void sendOptions(Player player, Session session) {
        if (session.overview) {
            Item[] display = buildOverviewItems(player);
            if (display.length > 0) {
                player.getPackets().sendUnlockIComponentOptionSlots(
                        BANK_INTERFACE, BANK_ITEMS_COMPONENT, 0,
                        display.length - 1, 7);
            }
            return;
        }

        SettlementStorageContainer container =
                player.getSettlementState().findStorageContainer(
                        session.pieceId);
        int capacity =
                container == null ? 0 : container.getSlotCapacity();
        player.getPackets().sendUnlockIComponentOptionSlots(
                BANK_INTERFACE, BANK_INVENTORY_COMPONENT,
                0, Inventory.INVENTORY_SIZE - 1,
                0, 1, 2, 4, 7, 8);
        if (capacity > 0) {
            player.getPackets().sendUnlockIComponentOptionSlots(
                    BANK_INTERFACE, BANK_ITEMS_COMPONENT,
                    0, capacity - 1,
                    0, 1, 2, 4, 7, 8);
        }
    }

    private static Item[] buildOverviewItems(Player player) {
        Map<Integer, Long> totals = new HashMap<Integer, Long>();
        for (SettlementStorageContainer container
                : player.getSettlementState().snapshotStorageContainers()) {
            if (container == null) {
                continue;
            }
            for (Item item : container.snapshotItems()) {
                if (item == null) {
                    continue;
                }
                Integer key = Integer.valueOf(item.getId());
                Long current = totals.get(key);
                totals.put(key, Long.valueOf(
                        (current == null ? 0L : current.longValue())
                        + item.getAmount()));
            }
        }

        List<Integer> ids = new ArrayList<Integer>(totals.keySet());
        Collections.sort(ids);
        List<Item> display = new ArrayList<Item>();
        for (Integer id : ids) {
            long remaining = totals.get(id).longValue();
            while (remaining > 0L) {
                int amount = (int) Math.min(
                        (long) Integer.MAX_VALUE, remaining);
                display.add(new Item(id.intValue(), amount));
                remaining -= amount;
            }
        }
        return display.toArray(new Item[display.size()]);
    }

    private static void examineDisplayItem(
            Player player, int slot, Item[] display) {
        if (display == null || slot < 0 || slot >= display.length) {
            return;
        }
        Item item = display[slot];
        if (item != null) {
            player.getPackets().sendGameMessage(
                    ItemExamines.getExamine(item));
        }
    }

    private static void refreshTotalSize(Player player) {
        Session session = getOpenSession(player);
        if (session == null) {
            return;
        }
        int used;
        if (session.overview) {
            used = buildOverviewItems(player).length;
        } else {
            SettlementStorageContainer container =
                    player.getSettlementState().findStorageContainer(
                            session.pieceId);
            used = container == null ? 0 : container.getUsedSlots();
        }
        int displayed = used > 403 ? 403 : used;
        player.getPackets().sendCSVarInteger(1038, displayed);
        player.getPackets().sendCSVarInteger(192, used);
    }
}
