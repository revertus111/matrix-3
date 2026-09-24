package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.List;

import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;

/**
 * One transient Rail Logistics V1 cart. Persistent rail/layout/resource
 * ownership remains in SettlementState; the cart exists only while the
 * settlement instance is loaded.
 */
public final class SettlementRailCartNpc extends NPC {

    private static final long serialVersionUID = -3131498995964663401L;

    private final SettlementInstance settlement;
    private final List<WorldTile> route;
    private int routeIndex;
    private boolean delivered;

    public SettlementRailCartNpc(SettlementInstance settlement, int npcId,
            WorldTile start, List<WorldTile> route) {
        super(npcId, start, -1, false, true);
        this.settlement = settlement;
        this.route = route == null ? new ArrayList<WorldTile>() : route;
        setName("Rail cart");
        setRandomWalk(0);
        setCantInteract(true);
        setNoDistanceCheck(true);
    }

    @Override
    public void processNPC() {
        super.processNPC();
        if (hasFinished() || delivered || settlement == null || !settlement.isLoaded()) {
            return;
        }
        if (routeIndex >= route.size()) {
            delivered = true;
            settlement.completeRailWoodShipment(this);
            return;
        }

        WorldTile target = route.get(routeIndex);
        if (target == null) {
            delivered = true;
            settlement.failRailWoodShipment(this, "Rail route lost a tile.");
            return;
        }
        if (getPlane() == target.getPlane() && getX() == target.getX() && getY() == target.getY()) {
            routeIndex++;
            return;
        }
        if (!hasWalkSteps() && !addWalkSteps(target.getX(), target.getY(), 25, true)) {
            delivered = true;
            settlement.failRailWoodShipment(this, "Cart could not move along the rail route.");
        }
    }
}
