package com.rs.game.player.content.construction;

import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;

/**
 * Transient Matrix3 NPC projection for one persistent settlement worker.
 *
 * The worker is stationary/non-combat until the later Bundle 1.4 AI slice
 * supplies explicit force-walk/gather/haul behavior.
 */
public final class SettlementWorkerNpc extends NPC {

    private static final long serialVersionUID = 4457138259614473421L;

    private final long workerId;

    public SettlementWorkerNpc(SettlementWorkerDefinition definition,
            SettlementWorkerState state, WorldTile tile) {
        super(definition.getNpcId(), tile, -1, false, true);
        this.workerId = state.getWorkerId();
        setName(state.getName());
        setRandomWalk(0);
        setCantInteract(true);
        setNoDistanceCheck(true);
        setForceAgressive(false);
    }

    public long getWorkerId() {
        return workerId;
    }
}
