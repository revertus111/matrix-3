package game;

/**
 * Shared alternate-character -> RuneScape combat intent bridge.
 *
 * Character drivers never calculate authoritative damage client-side. This V1
 * bridge selects a nearby loaded NPC and sends Matrix3's normal NPC attack
 * packet, leaving target validation, range/pathing, stats, equipment bonuses,
 * damage, XP, death and drops to the existing server combat engine.
 */
public final class AlternateCharacterCombatBridge {

    private static final float MAX_SOFT_TARGET_DISTANCE = 12.0F * 512.0F;
    private static int lastTargetIndex = -1;

    private AlternateCharacterCombatBridge() {
    }

    static void reset() {
        lastTargetIndex = -1;
    }

    /**
     * First vertical slice: an alternate character's primary melee action starts
     * the same server-owned NPC combat action as the stock Attack menu option.
     */
    static boolean requestPrimaryMeleeAttack() {
        if (!AlternateCharacterController.supportsCombatStyle(
                AlternateCharacterController.CombatStyle.MELEE)) {
            return false;
        }
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null || client.aClass195_8589 == null || client.aClass676_8622 == null) {
            return false;
        }

        int targetIndex = findNearestNpcIndex(player);
        if (targetIndex < 0) {
            return false;
        }
        LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) targetIndex);
        if (link == null || !(link.anObject9081 instanceof NPC)) {
            return false;
        }
        NPC npc = (NPC) link.anObject9081;

        Class572_Sub25 packet = Class378.sendOutPacket(
                OutgoingPacket.aClass312_3702,
                client.aClass195_8589.aClass650_2340,
                -923633357);
        packet.aRsByteBuffer.writeByteC(
                Class445.method5339(-1944836885) ? 1 : 0,
                (byte) -8);
        packet.aRsByteBuffer.writeShort128(targetIndex, -16711936);
        client.aClass195_8589.method2929(packet, (byte) -28);

        if (npc.screenX != null && npc.screenY != null
                && npc.screenX.length > 0 && npc.screenY.length > 0) {
            Class616.method7377(npc.screenX[0], npc.screenY[0], -1416428986);
        }
        if (targetIndex != lastTargetIndex) {
            lastTargetIndex = targetIndex;
            System.out.println("[Alt Character Combat] MELEE -> stock NPC attack index="
                    + targetIndex);
        }
        return true;
    }

    private static int findNearestNpcIndex(Player player) {
        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null
                || client.anIntArray8626 == null) {
            return -1;
        }
        Class240 playerPosition = playerTransform.aClass240_2647;
        float maxDistanceSquared = MAX_SOFT_TARGET_DISTANCE * MAX_SOFT_TARGET_DISTANCE;
        float bestDistanceSquared = maxDistanceSquared;
        int bestIndex = -1;

        int count = Math.min(client.anInt8625 * 765313669, client.anIntArray8626.length);
        for (int i = 0; i < count; i++) {
            int index = client.anIntArray8626[i];
            LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) index);
            if (link == null || !(link.anObject9081 instanceof NPC)) {
                continue;
            }
            NPC npc = (NPC) link.anObject9081;
            Class238 npcTransform = npc.method5394();
            if (npcTransform == null || npcTransform.aClass240_2647 == null) {
                continue;
            }
            Class240 npcPosition = npcTransform.aClass240_2647;
            float dx = npcPosition.aFloat2653 - playerPosition.aFloat2653;
            float dz = npcPosition.aFloat2657 - playerPosition.aFloat2657;
            float distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestIndex = index;
            }
        }
        return bestIndex;
    }
}
