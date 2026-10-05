package com.rs.net.decoders;

import com.rs.game.player.Player;

/**
 * Legacy packet-router compatibility shim.
 *
 * NPCHandler still references this historical Link-specific class name. All
 * imported-character combat behavior lives in
 * {@link AlternateCharacterCombatPacketBridge}; do not add combat logic here.
 */
@Deprecated
public final class LinkCombatPacketBridge {

    private LinkCombatPacketBridge() {
    }

    public static void requestManualMelee(Player player, int npcIndex) {
        AlternateCharacterCombatPacketBridge.requestManualMelee(player, npcIndex);
    }
}
