package com.rs.game.player.content.construction;

import com.rs.game.Animation;
import com.rs.game.player.Player;
import com.rs.game.player.actions.Action;

/**
 * Short player-driven gather action for starter settlement resources.
 *
 * Resources go directly to SettlementState storage; no inventory/bank item is
 * created by this action.
 */
public final class SettlementGatherAction extends Action {

    private final SettlementInstance instance;
    private final SettlementResourceNode node;

    public SettlementGatherAction(SettlementInstance instance, SettlementResourceNode node) {
        this.instance = instance;
        this.node = node;
    }

    @Override
    public boolean start(Player player) {
        if (!isValid(player)) {
            return false;
        }
        if (player.getSettlementState().getStorageRemaining() <= 0L) {
            player.getPackets().sendGameMessage("Your settlement storage is full.");
            return false;
        }
        player.getPackets().sendGameMessage(
                "You begin gathering " + node.getResource().getDisplayName().toLowerCase() + "...",
                true);
        setActionDelay(player, 2);
        return true;
    }

    @Override
    public boolean process(Player player) {
        if (!isValid(player)) {
            return false;
        }
        player.setNextAnimation(new Animation(node.getAnimationId()));
        return true;
    }

    @Override
    public int processWithDelay(Player player) {
        if (!isValid(player)) {
            return -1;
        }
        long added = instance.gatherStarterResource(node);
        if (added <= 0L) {
            player.getPackets().sendGameMessage("Your settlement storage is full.");
        } else {
            player.getPackets().sendGameMessage(
                    "You add " + added + " " + node.getResource().getDisplayName().toLowerCase()
                            + " to settlement storage. "
                            + player.getSettlementState().getResourceSummary(),
                    true);
        }
        return -1;
    }

    @Override
    public void stop(Player player) {
        player.setNextAnimation(new Animation(-1));
    }

    private boolean isValid(Player player) {
        return player != null
                && instance != null
                && node != null
                && SettlementInstance.getActive(player) == instance
                && instance.isLoaded()
                && instance.isStarterResourceNodeAvailable(node);
    }
}
