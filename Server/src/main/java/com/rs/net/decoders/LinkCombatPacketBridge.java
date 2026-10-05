package com.rs.net.decoders;

import com.rs.game.World;
import com.rs.game.item.Item;
import com.rs.game.npc.NPC;
import com.rs.game.npc.familiar.Familiar;
import com.rs.game.npc.others.DoorSupport;
import com.rs.game.player.Equipment;
import com.rs.game.player.Player;
import com.rs.game.player.actions.PlayerCombatNew;
import com.rs.game.player.content.Combat;

/**
 * Server authority for imported OoT Link action-combat requests.
 *
 * The client marks an NPC-examine packet with reserved run-flag value 2 when a
 * native Link sword animation reaches its contact frame. NPCHandler diverts only
 * that reserved value here; normal examine flags 0/1 keep their stock behavior.
 * This class does not install PlayerCombatNew into ActionManager, so a contact
 * request can never become RuneScape's normal repeating auto-combat loop.
 */
public final class LinkCombatPacketBridge {

    private LinkCombatPacketBridge() {
    }

    public static void requestManualMelee(Player player, int npcIndex) {
        if (!player.hasStarted() || !player.clientHasLoadedMapRegion()
                || player.isDead() || player.isLocked()
                || player.getEmotesManager().isDoingEmote()) {
            return;
        }

        NPC npc = World.getNPCs().get(npcIndex);
        if (npc == null || npc.isDead() || npc.hasFinished()
                || !player.getMapRegionsIds().contains(npc.getRegionId())
                || !npc.getDefinitions().hasAttackOption()) {
            return;
        }
        if (!player.getControlerManager().canAttack(npc)) {
            return;
        }

        Item mainHand = player.getEquipment().getItem(Equipment.SLOT_WEAPON);
        if (mainHand != null
                && player.getCombatDefinitions().getType(Equipment.SLOT_WEAPON)
                        != Combat.MELEE_TYPE) {
            return;
        }

        if (npc instanceof Familiar) {
            Familiar familiar = (Familiar) npc;
            if (familiar == player.getFamiliar()) {
                player.getPackets().sendGameMessage("You can't attack your own familiar.");
                return;
            }
            if (!familiar.canAttack(player)) {
                player.getPackets().sendGameMessage("You can't attack this npc.");
                return;
            }
        } else if (npc instanceof DoorSupport
                && !((DoorSupport) npc).canDestroy(player)) {
            player.getPackets().sendGameMessage("You cannot see a way to open this door...");
            return;
        }

        /*
         * Pre-check range before start(). PlayerCombatNew.process() normally owns
         * follow/path setup when out of range; action combat must never turn a
         * sword-contact request into click-to-follow behavior.
         */
        if (!PlayerCombatNew.isWithinDistance(player, npc)) {
            return;
        }

        PlayerCombatNew oneShot = new PlayerCombatNew(npc);
        if (!oneShot.start(player)) {
            return;
        }

        /*
         * One direct combat cycle only. Existing weapon delays reject early
         * repeat requests and existing hit generation owns accuracy, damage, XP,
         * NPC hitpoints/death and downstream drops.
         */
        oneShot.processWithDelay(player);
    }
}
