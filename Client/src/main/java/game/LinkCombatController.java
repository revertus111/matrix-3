package game;

/**
 * OoT Link combat presentation/input controller.
 *
 * Matrix owns NPC selection and only sends a one-shot melee intent after the
 * native OoT simulation has visibly transitioned into the sword animation and
 * reached the configured contact frame. The server remains authoritative for
 * target legality, range, weapon cooldown, accuracy, damage, XP and death.
 */
final class LinkCombatController {

    private static final int LINK_MANUAL_MELEE_SUBTYPE = 1;
    private static final float Z_TARGET_MAX_DISTANCE = 12.0F * 512.0F;
    private static final float Z_TARGET_DROP_DISTANCE = 14.0F * 512.0F;
    private static final float MELEE_FALLBACK_DISTANCE = 3.0F * 512.0F;
    private static final float Z_ACQUIRE_MIN_DOT = 0.0F;
    private static final float MELEE_FALLBACK_MIN_DOT = 0.35F;
    private static final long MAX_ARMED_NATIVE_TICKS = 12L;
    private static final float CONTACT_FRAME = positiveFloatProperty(
            "matrix3.oot.combatContactFrame", 2.0F);

    private static int lockedTargetIndex = -1;
    private static boolean attackWasDown;
    private static boolean attackArmed;
    private static long armedSequence = -1L;
    private static int armedAction;
    private static int armedAnimId;
    private static long lastObservedSequence = -1L;

    private LinkCombatController() {
    }

    static void reset() {
        lockedTargetIndex = -1;
        attackWasDown = false;
        attackArmed = false;
        armedSequence = -1L;
        armedAction = 0;
        armedAnimId = 0;
        lastObservedSequence = -1L;
    }

    /**
     * Updates Matrix-owned Z targeting and the native-animation contact gate.
     * Returns the planar direction that should be supplied to liboot as its live
     * camera-look basis. While Z is held this points at the locked Matrix NPC so
     * OoT movement/action state is target-relative instead of camera-relative.
     */
    static AlternateCharacterController.PlanarDirection update(
            Player player,
            boolean attackDown,
            boolean targetDown,
            AlternateCharacterController.PlanarDirection cameraForward) {
        if (player == null || cameraForward == null) {
            clearTarget();
            updateAttackEdge(attackDown, null, player, cameraForward);
            return cameraForward;
        }

        updateTarget(player, targetDown, cameraForward);
        OotBridgeSession.LinkFrame frame = OotBridgeSession.getLatestFrame();
        updateAttackEdge(attackDown, frame, player, cameraForward);

        if (targetDown && lockedTargetIndex >= 0) {
            AlternateCharacterController.PlanarDirection locked =
                    directionToNpc(player, lockedTargetIndex);
            if (locked != null) {
                return locked;
            }
        }
        return cameraForward;
    }

    static int getLockedTargetIndex() {
        return lockedTargetIndex;
    }

    private static void updateTarget(Player player, boolean targetDown,
            AlternateCharacterController.PlanarDirection cameraForward) {
        if (!targetDown) {
            clearTarget();
            return;
        }

        if (lockedTargetIndex >= 0
                && targetStillValid(player, lockedTargetIndex, Z_TARGET_DROP_DISTANCE)) {
            return;
        }

        int next = findBestNpcIndex(
                player,
                cameraForward,
                Z_TARGET_MAX_DISTANCE,
                Z_ACQUIRE_MIN_DOT);
        if (next != lockedTargetIndex) {
            lockedTargetIndex = next;
            if (next >= 0) {
                System.out.println("[OoT Combat] Z lock -> NPC index=" + next);
            } else {
                System.out.println("[OoT Combat] Z lock -> no target");
            }
        }
    }

    private static void clearTarget() {
        if (lockedTargetIndex >= 0) {
            System.out.println("[OoT Combat] Z lock released");
        }
        lockedTargetIndex = -1;
    }

    private static void updateAttackEdge(boolean attackDown,
            OotBridgeSession.LinkFrame frame,
            Player player,
            AlternateCharacterController.PlanarDirection cameraForward) {
        boolean attackEdge = attackDown && !attackWasDown;
        attackWasDown = attackDown;

        if (attackEdge) {
            attackArmed = true;
            if (frame == null) {
                armedSequence = -1L;
            } else {
                armedSequence = frame.sequence;
                armedAction = frame.action;
                armedAnimId = frame.animId;
            }
        }

        if (!attackArmed || frame == null || frame.sequence == lastObservedSequence) {
            return;
        }
        lastObservedSequence = frame.sequence;

        if (armedSequence < 0L) {
            armedSequence = frame.sequence;
            armedAction = frame.action;
            armedAnimId = frame.animId;
            return;
        }

        long nativeTicks = frame.sequence - armedSequence;
        if (nativeTicks <= 0L) {
            return;
        }
        if (nativeTicks > MAX_ARMED_NATIVE_TICKS) {
            attackArmed = false;
            return;
        }

        boolean nativeAttackTransition = frame.action != armedAction
                || frame.animId != armedAnimId;
        if (!nativeAttackTransition || frame.animFrame < CONTACT_FRAME) {
            return;
        }

        int targetIndex = lockedTargetIndex;
        if (targetIndex < 0 || !targetStillValid(player, targetIndex, Z_TARGET_DROP_DISTANCE)) {
            targetIndex = findBestNpcIndex(
                    player,
                    cameraForward,
                    MELEE_FALLBACK_DISTANCE,
                    MELEE_FALLBACK_MIN_DOT);
        }

        attackArmed = false;
        if (targetIndex < 0) {
            return;
        }

        if (sendManualMeleeIntent(targetIndex)) {
            System.out.println("[OoT Combat] sword contact anim=" + frame.animId
                    + " frame=" + frame.animFrame
                    + " target=" + targetIndex
                    + (targetIndex == lockedTargetIndex ? " Z-LOCK" : " FORWARD"));
        }
    }

    /**
     * Uses Matrix3's previously-unused variable packet 26 as a custom-action
     * envelope. Subtype 1 is Link manual melee. Stock ATTACK_NPC_PACKET remains
     * untouched so this cannot accidentally start the repeating RS combat loop.
     */
    private static boolean sendManualMeleeIntent(int targetIndex) {
        if (targetIndex < 0 || client.aClass195_8589 == null) {
            return false;
        }
        Class572_Sub25 packet = Class378.sendOutPacket(
                OutgoingPacket.aClass312_3672,
                client.aClass195_8589.aClass650_2340,
                -923633357);
        if (packet == null || packet.aRsByteBuffer == null) {
            return false;
        }
        packet.aRsByteBuffer.writeByteC(LINK_MANUAL_MELEE_SUBTYPE, (byte) -8);
        packet.aRsByteBuffer.writeShort128(targetIndex, -16711936);
        client.aClass195_8589.method2929(packet, (byte) -28);
        return true;
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
            // Favor centered targets without allowing a far centered NPC to beat
            // a substantially nearer valid target.
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

    private static float positiveFloatProperty(String name, float defaultValue) {
        try {
            String value = System.getProperty(name);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            float parsed = Float.parseFloat(value.trim());
            return Float.isNaN(parsed) || Float.isInfinite(parsed) || parsed < 0.0F
                    ? defaultValue : parsed;
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }
}
