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
         * Imported-character combat is manual action combat. If stock RuneScape
         * combat was already running before the native swing/contact arrived,
         * stop only that repeating combat action so it cannot continue attacking
         * after this one keypress. Do not cancel unrelated skilling/actions.
         */
        if (player.getActionManager().getAction() instanceof PlayerCombatNew) {
            player.getActionManager().forceStop();
        }

        /*
         * Never call PlayerCombatNew.start() here. start() immediately invokes
         * process(), whose stock click-to-attack path may calcFollow/add walk
         * steps/diagonal corrections. Native action combat must not move the
         * player toward a target. If contact occurs out of legal melee range,
         * the swing simply produces no combat cycle.
         */
        if (!PlayerCombatNew.isWithinDistance(player, npc)) {
            return;
        }

        PlayerCombatNew oneShot = new PlayerCombatNew(npc);

        /*
         * Exactly one direct combat cycle for exactly one native contact event.
         * processWithDelay() preserves the existing weapon delay, controller
         * keepCombating check, accuracy, damage, XP, NPC death and drops, but the
         * action is never installed in ActionManager and therefore cannot repeat.
         */
        oneShot.processWithDelay(player);
    }
}
