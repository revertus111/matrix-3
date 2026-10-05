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
 * Single server authority for imported-character manual melee requests.
 *
 * Character identity is irrelevant here: Mario, Link and future imported
 * characters enter through the same request, validation and one-shot combat
 * cycle. No alternate character owns a parallel damage formula or auto-combat
 * action.
 */
public final class AlternateCharacterCombatPacketBridge {

    private AlternateCharacterCombatPacketBridge() {
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
         * Action combat must never turn a contact request into click-to-follow.
         * Reject out-of-range contacts before PlayerCombatNew can route toward the
         * target.
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
         * repeats; existing combat owns accuracy, damage, XP, NPC death and drops.
         */
        oneShot.processWithDelay(player);
    }
}
