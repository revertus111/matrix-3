package com.rs.game.npc.others;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;

/**
 * Developer-owned deterministic combat target for vanilla and imported combat.
 * It is deliberately passive, stationary, high-HP and reward-free.
 */
public final class CombatDummy extends NPC {

    private static final long serialVersionUID = 1L;
    private static final int BASE_NPC_ID = 1;
    private static final int MAX_HITPOINTS = 10_000_000;
    private static final List<CombatDummy> ACTIVE = new CopyOnWriteArrayList<CombatDummy>();

    private CombatDummy(WorldTile tile) {
        super(BASE_NPC_ID, tile, -1, true, true);
        setName("Combat Dummy");
        setRandomWalk(0);
        setForceAgressive(false);
        setCantFollowUnderCombat(true);
        setHitpoints(MAX_HITPOINTS);
    }

    public static void spawnNear(Player player) {
        if (!isDeveloper(player)) {
            return;
        }
        CombatDummy dummy = new CombatDummy(new WorldTile(
                player.getX() + 1, player.getY(), player.getPlane()));
        if (!dummy.getDefinitions().hasAttackOption()) {
            dummy.finish();
            player.getPackets().sendGameMessage(
                    "Combat dummy base NPC is not attackable in this cache.");
            return;
        }
        ACTIVE.add(dummy);
        player.getPackets().sendGameMessage(
                "Combat Dummy spawned next to you (10,000,000 HP, passive, no drops)." );
        System.out.println("[Combat Dummy] spawned index=" + dummy.getIndex()
                + " tile=" + dummy.getX() + "," + dummy.getY() + "," + dummy.getPlane());
    }

    public static void removeAll(Player player) {
        if (!isDeveloper(player)) {
            return;
        }
        int count = 0;
        for (CombatDummy dummy : ACTIVE) {
            if (dummy != null && !dummy.hasFinished()) {
                dummy.finish();
                count++;
            }
        }
        ACTIVE.clear();
        player.getPackets().sendGameMessage("Removed " + count + " Combat Dummy"
                + (count == 1 ? "." : " instances."));
        System.out.println("[Combat Dummy] removed count=" + count);
    }

    private static boolean isDeveloper(Player player) {
        if (player == null || player.getRights() < 2) {
            if (player != null) {
                player.getPackets().sendGameMessage("Combat Dummy controls are admin-only.");
            }
            return false;
        }
        return true;
    }

    @Override
    public int getMaxHitpoints() {
        return MAX_HITPOINTS;
    }

    @Override
    public void processNPC() {
        resetWalkSteps();
        removeTarget();
    }

    @Override
    public void drop() {
        // Developer target: never create loot.
    }

    @Override
    public void giveXP() {
        // Developer target: never award kill XP/rewards.
    }

    @Override
    public void finish() {
        ACTIVE.remove(this);
        super.finish();
    }
}
