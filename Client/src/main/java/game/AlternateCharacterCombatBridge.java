package game;

/**
 * Single shared action-combat owner for every imported/alternate character.
 *
 * Character adapters may expose native animation/contact state, but they do not
 * own NPC selection, target lock, attack lifecycle, packet routing or damage.
 * Matrix/server combat remains authoritative for legality, range, weapon style,
 * cooldown, accuracy, damage, XP, death and drops.
 */
public final class AlternateCharacterCombatBridge {

    private static final int MANUAL_MELEE_EXAMINE_FLAG = 2;
    private static final float Z_TARGET_MAX_DISTANCE = 12.0F * 512.0F;
    private static final float Z_TARGET_DROP_DISTANCE = 14.0F * 512.0F;
    private static final float MELEE_FALLBACK_DISTANCE = 3.0F * 512.0F;
    private static final float Z_ACQUIRE_MIN_DOT = 0.0F;
    private static final float MELEE_FALLBACK_MIN_DOT = 0.35F;

    private static int lockedTargetIndex = -1;
    private static boolean nativeAttackWasDown;
    private static boolean nativeAttackArmed;
    private static long armedSequence = -1L;
    private static int armedAction;
    private static int armedAnimId;
    private static long lastObservedSequence = -1L;

    private AlternateCharacterCombatBridge() {
    }

    static void reset() {
        lockedTargetIndex = -1;
        nativeAttackWasDown = false;
        nativeAttackArmed = false;
        armedSequence = -1L;
        armedAction = 0;
        armedAnimId = 0;
        lastObservedSequence = -1L;
    }

    /**
     * Shared Matrix-owned target lock. Character adapters consume the returned
     * direction as their native lock/camera basis; they never implement their own
     * NPC search or lock retention policy.
     */
    static AlternateCharacterController.PlanarDirection updateTargeting(
            Player player,
            boolean targetDown,
            AlternateCharacterController.PlanarDirection cameraForward) {
        if (player == null || cameraForward == null) {
            clearTarget();
            return cameraForward;
        }

        if (!targetDown) {
            clearTarget();
            return cameraForward;
        }

        if (lockedTargetIndex < 0
                || !targetStillValid(player, lockedTargetIndex, Z_TARGET_DROP_DISTANCE)) {
            int next = findBestNpcIndex(
                    player,
                    cameraForward,
                    Z_TARGET_MAX_DISTANCE,
                    Z_ACQUIRE_MIN_DOT);
            if (next != lockedTargetIndex) {
                lockedTargetIndex = next;
                if (next >= 0) {
                    System.out.println("[Alt Character Combat] target lock -> NPC index=" + next);
                } else {
                    System.out.println("[Alt Character Combat] target lock -> no target");
                }
            }
        }

        AlternateCharacterController.PlanarDirection locked =
                directionToNpc(player, lockedTargetIndex);
        return locked == null ? cameraForward : locked;
    }

    /**
     * Generic native-animation contact gate. A character adapter supplies its
     * native sequence/action/animation/frame values and tuning threshold; this
     * shared owner handles edge arming, one-shot protection, target resolution
     * and the common server intent.
     */
    static void updateNativeMeleeContact(
            Player player,
            AlternateCharacterController.CharacterId character,
            boolean attackDown,
            AlternateCharacterController.PlanarDirection cameraForward,
            long sequence,
            int action,
            int animId,
            float animFrame,
            float contactFrame,
            long maxArmedNativeTicks) {
        boolean attackEdge = attackDown && !nativeAttackWasDown;
        nativeAttackWasDown = attackDown;

        if (attackEdge) {
            nativeAttackArmed = true;
            if (sequence < 0L) {
                armedSequence = -1L;
            } else {
                armedSequence = sequence;
                armedAction = action;
                armedAnimId = animId;
            }
        }

        if (!nativeAttackArmed || sequence < 0L || sequence == lastObservedSequence) {
            return;
        }
        lastObservedSequence = sequence;

        if (armedSequence < 0L) {
            armedSequence = sequence;
            armedAction = action;
            armedAnimId = animId;
            return;
        }

        long nativeTicks = sequence - armedSequence;
        if (nativeTicks <= 0L) {
            return;
        }
        if (nativeTicks > maxArmedNativeTicks) {
            nativeAttackArmed = false;
            return;
        }

        boolean nativeAttackTransition = action != armedAction || animId != armedAnimId;
        if (!nativeAttackTransition || animFrame < contactFrame) {
            return;
        }

        nativeAttackArmed = false;
        int targetIndex = resolveMeleeTarget(player, cameraForward);
        if (targetIndex < 0) {
            return;
        }

        if (sendManualMeleeIntent(targetIndex)) {
            System.out.println("[Alt Character Combat] " + character
                    + " contact anim=" + animId
                    + " frame=" + animFrame
                    + " target=" + targetIndex
                    + (targetIndex == lockedTargetIndex ? " LOCKED" : " FORWARD"));
        }
    }

    /**
     * Immediate-contact adapter used by native controllers that already emit a
     * discrete attack edge. It uses the exact same target resolver and one-shot
     * server intent as animation-gated characters; it never starts stock RS
     * click-to-attack/auto-combat.
     */
    static boolean requestPrimaryMeleeAttack() {
        if (!AlternateCharacterController.supportsCombatStyle(
                AlternateCharacterController.CombatStyle.MELEE)) {
            return false;
        }
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null) {
            return false;
        }
        int targetIndex = resolveMeleeTarget(
                player,
                AlternateCharacterController.getCameraForward());
        if (targetIndex < 0) {
            return false;
        }
        boolean sent = sendManualMeleeIntent(targetIndex);
        if (sent) {
            System.out.println("[Alt Character Combat] "
                    + AlternateCharacterController.getActiveCharacter()
                    + " immediate contact -> target=" + targetIndex);
        }
        return sent;
    }

    static int getLockedTargetIndex() {
        return lockedTargetIndex;
    }

    private static int resolveMeleeTarget(Player player,
            AlternateCharacterController.PlanarDirection cameraForward) {
        if (player == null || cameraForward == null) {
            return -1;
        }
        if (lockedTargetIndex >= 0
                && targetStillValid(player, lockedTargetIndex, Z_TARGET_DROP_DISTANCE)) {
            return lockedTargetIndex;
        }
        return findBestNpcIndex(
                player,
                cameraForward,
                MELEE_FALLBACK_DISTANCE,
                MELEE_FALLBACK_MIN_DOT);
    }

    /**
     * Shared imported-character manual-melee envelope. Packet 0 normally carries
     * NPC examine with transformed flag 0/1; reserved flag 2 is routed by the
     * server to one direct PlayerCombatNew cycle rather than repeating auto-combat.
     */
    private static boolean sendManualMeleeIntent(int targetIndex) {
        if (targetIndex < 0 || client.aClass195_8589 == null) {
            return false;
        }
        Class572_Sub25 packet = Class378.sendOutPacket(
                OutgoingPacket.aClass312_3683,
                client.aClass195_8589.aClass650_2340,
                -923633357);
        if (packet == null || packet.aRsByteBuffer == null) {
            return false;
        }
        packet.aRsByteBuffer.writeByteC(MANUAL_MELEE_EXAMINE_FLAG, (byte) -8);
        packet.aRsByteBuffer.writeShort128(targetIndex, -16711936);
        client.aClass195_8589.method2929(packet, (byte) -28);
        return true;
    }

    private static void clearTarget() {
        if (lockedTargetIndex >= 0) {
            System.out.println("[Alt Character Combat] target lock released");
        }
        lockedTargetIndex = -1;
    }

    private static int findBestNpcIndex(Player player,
            AlternateCharacterController.PlanarDirection forward,
            float maxDistance,
            float minDot) {
        if (player == null || forward == null || client.aClass676_8622 == null
                || client.anIntArray8626 == null) {
            return -1;
        }
        Class238 playerTransform = player.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null) {
            return -1;
        }
        Class240 playerPosition = playerTransform.aClass240_2647;
        float maxDistanceSquared = maxDistance * maxDistance;
        float bestScore = Float.MAX_VALUE;
        int bestIndex = -1;

        int count = Math.min(client.anInt8625 * 765313669, client.anIntArray8626.length);
        for (int i = 0; i < count; i++) {
            int index = client.anIntArray8626[i];
            NPC npc = getNpc(index);
            if (npc == null) {
                continue;
            }
            Class238 npcTransform = npc.method5394();
            if (npcTransform == null || npcTransform.aClass240_2647 == null) {
                continue;
            }
            Class240 npcPosition = npcTransform.aClass240_2647;
            float dx = npcPosition.aFloat2653 - playerPosition.aFloat2653;
            float dz = npcPosition.aFloat2657 - playerPosition.aFloat2657;
            float distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= 0.0001F || distanceSquared > maxDistanceSquared) {
                continue;
            }
            float distance = (float) Math.sqrt(distanceSquared);
            float dot = (dx / distance) * forward.x + (dz / distance) * forward.z;
            if (dot < minDot) {
                continue;
            }
            float score = distanceSquared * (1.25F - 0.75F * dot);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static boolean targetStillValid(Player player, int index, float maxDistance) {
        AlternateCharacterController.PlanarDirection direction = directionToNpc(player, index);
        if (direction == null) {
            return false;
        }
        Class238 playerTransform = player.method5394();
        NPC npc = getNpc(index);
        Class238 npcTransform = npc == null ? null : npc.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null
                || npcTransform == null || npcTransform.aClass240_2647 == null) {
            return false;
        }
        float dx = npcTransform.aClass240_2647.aFloat2653
                - playerTransform.aClass240_2647.aFloat2653;
        float dz = npcTransform.aClass240_2647.aFloat2657
                - playerTransform.aClass240_2647.aFloat2657;
        return dx * dx + dz * dz <= maxDistance * maxDistance;
    }

    private static AlternateCharacterController.PlanarDirection directionToNpc(
            Player player, int index) {
        if (player == null) {
            return null;
        }
        NPC npc = getNpc(index);
        if (npc == null) {
            return null;
        }
        Class238 playerTransform = player.method5394();
        Class238 npcTransform = npc.method5394();
        if (playerTransform == null || playerTransform.aClass240_2647 == null
                || npcTransform == null || npcTransform.aClass240_2647 == null) {
            return null;
        }
        float dx = npcTransform.aClass240_2647.aFloat2653
                - playerTransform.aClass240_2647.aFloat2653;
        float dz = npcTransform.aClass240_2647.aFloat2657
                - playerTransform.aClass240_2647.aFloat2657;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (Float.isNaN(length) || Float.isInfinite(length) || length < 0.001F) {
            return null;
        }
        return new AlternateCharacterController.PlanarDirection(dx / length, dz / length);
    }

    private static NPC getNpc(int index) {
        if (index < 0 || client.aClass676_8622 == null) {
            return null;
        }
        LinkableObject link = (LinkableObject) client.aClass676_8622.get((long) index);
        return link != null && link.anObject9081 instanceof NPC
                ? (NPC) link.anObject9081 : null;
    }
}
