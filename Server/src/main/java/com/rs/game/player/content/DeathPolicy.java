package com.rs.game.player.content;

import com.rs.game.player.Player;

/**
 * Authoritative item-retention policy for player death.
 *
 * The normal/main game keeps inventory and equipment. Future game modes such as
 * Hardcore can branch here once their player-mode state exists, without
 * duplicating death-item rules across controllers.
 */
public final class DeathPolicy {

    private DeathPolicy() {
    }

    public static boolean keepsInventoryAndEquipment(Player player) {
        return true;
    }
}
