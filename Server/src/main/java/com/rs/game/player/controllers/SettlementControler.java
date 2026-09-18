package com.rs.game.player.controllers;

import com.rs.Settings;
import com.rs.game.player.content.construction.SettlementInstance;

/**
 * Runtime lifecycle boundary for a player's freeform Construction settlement.
 */
public final class SettlementControler extends Controller {

    private SettlementInstance instance;

    @Override
    public void start() {
        Object[] arguments = getArguments();
        if (arguments != null && arguments.length > 0 && arguments[0] instanceof SettlementInstance) {
            instance = (SettlementInstance) arguments[0];
            arguments[0] = null; // runtime instance must never be saved in controller arguments
        }
    }

    public SettlementInstance getInstance() {
        return instance;
    }

    @Override
    public boolean logout() {
        if (instance != null) {
            instance.leaveForLogout();
        } else {
            removeControler();
        }
        return false;
    }

    @Override
    public boolean login() {
        // A server restart cannot restore transient dynamic-map ownership.
        player.setNextWorldTile(Settings.START_PLAYER_LOCATION);
        removeControler();
        return false;
    }

    @Override
    public void magicTeleported(int type) {
        if (instance != null) {
            instance.leaveForTeleport();
        } else {
            removeControler();
        }
    }

    @Override
    public void forceClose() {
        if (instance != null) {
            instance.leaveForTeleport();
        }
    }
}
