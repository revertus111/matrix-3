package com.rs.game.player.content.commands;

import com.rs.cache.loaders.ItemDefinitions;
import com.rs.cache.loaders.NPCDefinitions;
import com.rs.cache.loaders.ObjectDefinitions;
import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;
import com.rs.game.item.Item;
import com.rs.game.npc.NPC;
import com.rs.game.player.CombatDefinitions;
import com.rs.game.player.Player;

/**
 * Owner-only server authority bridge for Client Console Item Browser,
 * development settings, and Dev Mode live placement actions.
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
        if (cmd != null && cmd.length >= 3 && "backpack".equalsIgnoreCase(cmd[1])
                && "open".equalsIgnoreCase(cmd[2])) {
            player.getInventory().getBackpack().open();
            return true;
        }
        if (cmd != null && cmd.length >= 2 && "settings".equalsIgnoreCase(cmd[1])) {
            return processSettings(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "devspawn".equalsIgnoreCase(cmd[1])) {
            return processDevSpawn(player, cmd);
        }
        if (cmd == null || cmd.length < 4) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser <inventory|bank> <itemId> <amount> or ::itembrowser backpack open");
            return true;
        }

        final boolean bank;
        if ("inventory".equalsIgnoreCase(cmd[1])) {
            bank = false;
        } else if ("bank".equalsIgnoreCase(cmd[1])) {
            bank = true;
        } else {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser <inventory|bank> <itemId> <amount> or ::itembrowser backpack open");
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

    private static boolean processDevSpawn(Player player, String[] cmd) {
        if (cmd.length < 6) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser devspawn <npc|object|item> <id> <x> <y> <plane> [type rotation|amount]");
            return true;
        }

        final String kind = cmd[2].toLowerCase();
        final int id;
        final int x;
        final int y;
        final int plane;
        try {
            id = Integer.parseInt(cmd[3]);
            x = Integer.parseInt(cmd[4]);
            y = Integer.parseInt(cmd[5]);
            plane = Integer.parseInt(cmd[6]);
        } catch (NumberFormatException ex) {
            player.getPackets().sendGameMessage("Dev spawn id and tile coordinates must be whole numbers.");
            return true;
        } catch (ArrayIndexOutOfBoundsException ex) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser devspawn <npc|object|item> <id> <x> <y> <plane> [type rotation|amount]");
            return true;
        }

        if (id < 0 || x < 0 || x > 16383 || y < 0 || y > 16383 || plane < 0 || plane > 3) {
            player.getPackets().sendGameMessage("Dev spawn id/tile is outside the supported Matrix3 world range.");
            return true;
        }

        WorldTile tile = new WorldTile(x, y, plane);

        if ("npc".equals(kind)) {
            NPCDefinitions definition = NPCDefinitions.getNPCDefinitions(id);
            if (definition == null || (definition.modelIds.length == 0 && !hasName(definition.name))) {
                player.getPackets().sendGameMessage("Unable to spawn unknown NPC id " + id + ".");
                return true;
            }
            NPC npc = World.spawnNPC(id, tile, -1, true, true);
            if (npc == null) {
                player.getPackets().sendGameMessage("Matrix3 could not spawn NPC id " + id + " on that tile.");
                return true;
            }
            player.getPackets().sendGameMessage("Dev Mode spawned NPC " + displayName(definition.name, id)
                    + " at " + x + ", " + y + ", " + plane + ".");
            return true;
        }

        if ("object".equals(kind)) {
            if (cmd.length < 9) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser devspawn object <id> <x> <y> <plane> <type> <rotation>");
                return true;
            }
            final int type;
            final int rotation;
            try {
                type = Integer.parseInt(cmd[7]);
                rotation = Integer.parseInt(cmd[8]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Object type and rotation must be whole numbers.");
                return true;
            }
            if (type < 0 || type > 22 || rotation < 0 || rotation > 3) {
                player.getPackets().sendGameMessage("Object type must be 0-22 and rotation must be 0-3.");
                return true;
            }
            ObjectDefinitions definition = ObjectDefinitions.getObjectDefinitions(id);
            if (definition == null || (definition.modelIds == null && !hasName(definition.name))) {
                player.getPackets().sendGameMessage("Unable to spawn unknown object id " + id + ".");
                return true;
            }
            World.spawnObject(new WorldObject(id, type, rotation, tile));
            player.getPackets().sendGameMessage("Dev Mode spawned object " + displayName(definition.name, id)
                    + " at " + x + ", " + y + ", " + plane + ".");
            return true;
        }

        if ("item".equals(kind)) {
            if (cmd.length < 8) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser devspawn item <id> <x> <y> <plane> <amount>");
                return true;
            }
            final int amount;
            try {
                amount = Integer.parseInt(cmd[7]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Ground item amount must be a whole number.");
                return true;
            }
            if (amount <= 0) {
                player.getPackets().sendGameMessage("Ground item amount must be greater than zero.");
                return true;
            }
            ItemDefinitions definition = ItemDefinitions.getItemDefinitions(id);
            if (definition == null || !definition.isLoaded() || !hasName(definition.name)) {
                player.getPackets().sendGameMessage("Unable to spawn unknown item id " + id + ".");
                return true;
            }
            World.addGroundItem(new Item(id, amount), tile, player, true, 180);
            player.getPackets().sendGameMessage("Dev Mode spawned " + amount + " x " + definition.name
                    + " at " + x + ", " + y + ", " + plane + ".");
            return true;
        }

        player.getPackets().sendGameMessage("Dev spawn type must be npc, object, or item.");
        return true;
    }

    private static boolean hasName(String name) {
        return name != null && name.trim().length() > 0 && !"null".equalsIgnoreCase(name.trim());
    }

    private static String displayName(String name, int id) {
        return hasName(name) ? name + " (" + id + ")" : "id " + id;
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
