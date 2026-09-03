package com.rs.game.player.content.commands;

import com.rs.cache.loaders.ItemDefinitions;
import com.rs.game.player.CombatDefinitions;
import com.rs.game.player.Player;

/**
 * Owner-only server authority bridge for Client Console Item Browser and
 * development settings actions.
 */
public final class ItemBrowserCommandBridge {

    private ItemBrowserCommandBridge() {
    }

    public static boolean process(Player player, String[] cmd) {
        if (player == null) {
            return false;
        }
        if (!player.hasStarted() || !player.isRunning() || player.hasFinished()) {
            return true;
        }
        if (player.getRights() < 2) {
            player.getPackets().sendGameMessage("Admin+ only!");
            return true;
        }
        if (cmd != null && cmd.length >= 2 && "settings".equalsIgnoreCase(cmd[1])) {
            return processSettings(player, cmd);
        }
        if (cmd == null || cmd.length < 4) {
            player.getPackets().sendGameMessage("Use: ::itembrowser <inventory|bank> <itemId> <amount>");
            return true;
        }

        final boolean bank;
        if ("inventory".equalsIgnoreCase(cmd[1])) {
            bank = false;
        } else if ("bank".equalsIgnoreCase(cmd[1])) {
            bank = true;
        } else {
            player.getPackets().sendGameMessage("Use: ::itembrowser <inventory|bank> <itemId> <amount>");
            return true;
        }

        final int itemId;
        final int amount;
        try {
            itemId = Integer.parseInt(cmd[2]);
            amount = Integer.parseInt(cmd[3]);
        } catch (NumberFormatException ex) {
            player.getPackets().sendGameMessage("Item id and amount must be whole numbers.");
            return true;
        }

        if (itemId < 0 || amount <= 0) {
            player.getPackets().sendGameMessage("Item id must be valid and amount must be greater than zero.");
            return true;
        }

        ItemDefinitions definition = ItemDefinitions.getItemDefinitions(itemId);
        if (definition == null || !definition.isLoaded() || definition.name == null
                || definition.name.trim().length() == 0 || "null".equalsIgnoreCase(definition.name.trim())) {
            player.getPackets().sendGameMessage("Unable to spawn unknown item id " + itemId + ".");
            return true;
        }

        boolean added = bank
                ? player.getBank().addItem(itemId, amount, true)
                : player.getInventory().addItem(itemId, amount);
        if (added) {
            player.getPackets().sendGameMessage("Spawned " + amount + " x " + definition.name
                    + (bank ? " to your bank." : " to your inventory."));
        } else {
            player.getPackets().sendGameMessage(bank
                    ? "Unable to add that item to your bank (bank may be full)."
                    : "Unable to add that item to your inventory (inventory may be full or restricted)." );
        }
        return true;
    }

    private static boolean processSettings(Player player, String[] cmd) {
        if (cmd.length < 4) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser settings <combat|interface> <legacy|eoc|nis>");
            return true;
        }

        ensureIndependentModes(player);

        if ("combat".equalsIgnoreCase(cmd[2])) {
            if ("legacy".equalsIgnoreCase(cmd[3])) {
                setCombatMode(player, CombatDefinitions.LEGACY_COMBAT_MODE);
                player.getPackets().sendGameMessage("Client Console combat mode: Legacy combat.");
                return true;
            }
            if ("eoc".equalsIgnoreCase(cmd[3]) || "manual".equalsIgnoreCase(cmd[3])) {
                setCombatMode(player, CombatDefinitions.MANUAL_COMBAT_MODE);
                player.getPackets().sendGameMessage("Client Console combat mode: EoC manual combat.");
                return true;
            }
            player.getPackets().sendGameMessage("Use: ::itembrowser settings combat <legacy|eoc>");
            return true;
        }

        if ("interface".equalsIgnoreCase(cmd[2])) {
            if ("legacy".equalsIgnoreCase(cmd[3])) {
                applyLegacyInterface(player);
                player.getPackets().sendGameMessage(
                        "Client Console interface mode: Legacy interface. Combat mode was left unchanged.");
                return true;
            }
            if ("nis".equalsIgnoreCase(cmd[3]) || "eoc".equalsIgnoreCase(cmd[3])) {
                player.refreshInterfaceVars();
                player.getPackets().sendGameMessage(
                        "Client Console interface mode: NIS. Combat mode was left unchanged.");
                return true;
            }
            player.getPackets().sendGameMessage("Use: ::itembrowser settings interface <legacy|nis>");
            return true;
        }

        player.getPackets().sendGameMessage(
                "Use: ::itembrowser settings <combat|interface> <legacy|eoc|nis>");
        return true;
    }

    /**
     * Matrix3's original legacyMode flag couples combat and interface state. The
     * Client Console split controls normalize that master flag off while preserving
     * the currently effective combat mode, then drive combat and interface state
     * independently.
     */
    private static void ensureIndependentModes(Player player) {
        if (!player.isLegacyMode()) {
            return;
        }
        int effectiveCombatMode = player.getCombatDefinitions().getCombatMode();
        player.switchLegacyMode();
        setCombatMode(player, effectiveCombatMode);
    }

    private static void setCombatMode(Player player, int mode) {
        CombatDefinitions definitions = player.getCombatDefinitions();
        definitions.setCombatMode(mode);

        boolean legacyCombat = mode == CombatDefinitions.LEGACY_COMBAT_MODE;
        definitions.setMagicAbilityMenu(legacyCombat ? 0 : 1);
        if (legacyCombat) {
            // These two CombatDefinitions refreshers normally key off the original
            // coupled Player.legacyMode flag. Reproduce their legacy-combat state
            // explicitly while that master flag is intentionally false for NIS.
            player.getVarsManager().sendVarBit(21686, 0);
            player.getVarsManager().sendVarBit(21684, 1);
        } else {
            definitions.refreshShowCombatModeIcon();
            definitions.refreshAllowAbilityQueueing();
        }
    }

    /**
     * Applies only the legacy-interface var state while keeping Player.legacyMode
     * false. This preserves the selected CombatDefinitions mode instead of using
     * Matrix3's original all-in-one legacy switch.
     */
    private static void applyLegacyInterface(Player player) {
        player.refreshInterfaceVars();
        player.getVarsManager().sendVarBit(22874, 1); // map icons
        player.getVarsManager().sendVarBit(19924, 1); // slim headers forced in legacy UI
        player.getVarsManager().sendVarBit(19925, 1); // interface customization locked
        player.getVarsManager().sendVarBit(20188, 1); // click-through chatboxes
        player.getVarsManager().sendVarBit(19928, 1); // hide title bars when locked
        player.getVarsManager().sendVarBit(19929, 1); // target reticules unavailable
        player.getVarsManager().sendVarBit(19927, 0); // target information legacy state
        player.getVarsManager().sendVarBit(22310, 1); // always-on chat legacy state
        player.getVarsManager().sendVarBit(22875, 1); // legacy gameframe
        player.getVarsManager().sendVarBit(22872, 1); // legacy interface mode
    }
}
