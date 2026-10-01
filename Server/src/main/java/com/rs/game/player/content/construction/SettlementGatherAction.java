package com.rs.game.player.content.construction;

import com.rs.game.Animation;
import com.rs.game.player.Player;
import com.rs.game.player.actions.Action;

public final class SettlementGatherAction extends Action {

    private static final int GATHER_DELAY = 2;
    private final SettlementInstance instance;
    private final SettlementResourceNodeState node;

    public SettlementGatherAction(SettlementInstance instance, SettlementResourceNodeState node) {
        this.instance = instance;
        this.node = node;
    }

    @Override
    public boolean start(Player player) {
        if (!isValid(player)) return false;
        if (player.getSettlementState().getStorageRemaining(node.getResource()) <= 0L) {
            player.getPackets().sendGameMessage("Your settlement storage is full.");
            return false;
        }
        player.getPackets().sendGameMessage(
                "You begin gathering " + node.getResource().getDisplayName().toLowerCase() + "...", true);
        setActionDelay(player, GATHER_DELAY);
        return true;
    }

    @Override
    public boolean process(Player player) {
        if (!isValid(player)) return false;
        player.setNextAnimation(new Animation(node.getAnimationId()));
        return true;
    }

    @Override
    public int processWithDelay(Player player) {
        if (!isValid(player)) return -1;
        long added = instance.gatherStarterResource(node);
        if (added <= 0L) {
            player.getPackets().sendGameMessage(node.isDepleted()
                    ? "The " + node.getResource().getDisplayName().toLowerCase() + " source is depleted."
                    : "Your settlement storage is full.");
            return -1;
        }
        player.getPackets().sendGameMessage(
                "You add " + added + " " + node.getResource().getDisplayName().toLowerCase()
                        + " to settlement storage. " + player.getSettlementState().getResourceSummary(), true);
        if (node.isDepleted()) {
            player.getPackets().sendGameMessage(
                    "The " + node.getResource().getDisplayName().toLowerCase() + " source is depleted.");
            return -1;
        }
        if (player.getSettlementState().getStorageRemaining(node.getResource()) <= 0L) {
            player.getPackets().sendGameMessage("Your settlement storage is full.");
            return -1;
        }
        return GATHER_DELAY;
    }

    @Override
    public void stop(Player player) { player.setNextAnimation(new Animation(-1)); }

    private boolean isValid(Player player) {
        return player != null && instance != null && node != null
                && SettlementInstance.getActive(player) == instance
                && instance.isLoaded() && instance.isStarterResourceNodeAvailable(node);
    }
}
