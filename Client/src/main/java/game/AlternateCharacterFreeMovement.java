package game;

/**
 * Shared horizontal movement state, owned only by AlternateCharacterController.
 * The legacy name is retained. Free movement is default; optional clipping uses
 * one stock tile-authority handoff for every native character.
 */
final class AlternateCharacterFreeMovement {
    private static final float COLLISION_LEAD = 256.0F;
    private static final float WALK_LEAD = 128.0F;
    private static final float MOVE_DEADZONE = 0.20F;
    private static final int WALK_RETRY_CYCLES = 10;

    private Player owner;
    private boolean valid, clipping, pendingAuthority;
    private float baselineX, baselineZ, appliedX, appliedZ, nativeX, nativeZ;
    private float groundX, groundZ;
    private int collisionTileX, collisionTileZ;
    private int lastWalkX = Integer.MIN_VALUE, lastWalkZ = Integer.MIN_VALUE;
    private int lastWalkCycle = Integer.MIN_VALUE;

    void apply(Player player, float x, float z, float scale) {
        apply(player, x, z, scale, 0.0F, 0.0F, false);
    }

    void apply(Player player, float x, float z, float scale,
            float worldMoveX, float worldMoveZ, boolean useClipping) {
        if (player == null || !finite(x) || !finite(z) || !finite(scale)
                || scale <= 0.0F || !finite(worldMoveX) || !finite(worldMoveZ)) {
            return;
        }
        if (valid && owner != player) {
            reset(); // Never restore another player instance after a lifecycle change.
        }
        if (valid && clipping != useClipping) {
            restore(player); // One toggle/reset path, independent of native engine.
        }
        Class240 position = player.method5394().aClass240_2647;
        if (!valid) {
            owner = player;
            clipping = useClipping;
            baselineX = appliedX = position.aFloat2653;
            baselineZ = appliedZ = position.aFloat2657;
            nativeX = x;
            nativeZ = z;
            if (clipping) {
                collisionTileX = player.screenX[0];
                collisionTileZ = player.screenY[0];
                groundX = tileCenter(player, collisionTileX);
                groundZ = tileCenter(player, collisionTileZ);
            }
            valid = true;
        }

        float deltaX = (x - nativeX) * scale;
        float deltaZ = (z - nativeZ) * scale;
        nativeX = x;
        nativeZ = z;
        if (!clipping) {
            // External Matrix corrections remain authoritative; do not mistake
            // our previous presentation write for a new server/world baseline.
            if (Math.abs(position.aFloat2653 - appliedX) > 0.5F) {
                baselineX = appliedX = position.aFloat2653;
            }
            if (Math.abs(position.aFloat2657 - appliedZ) > 0.5F) {
                baselineZ = appliedZ = position.aFloat2657;
            }
            appliedX += deltaX;
            appliedZ += deltaZ;
            // No tile rounding, clamp, walk packet or tile-approval wait.
        } else {
            float desiredX = appliedX + deltaX;
            float desiredZ = appliedZ + deltaZ;
            if (syncAuthority(player, desiredX, desiredZ)) {
                // A stock multi-tile correction is a rebase, not a native delta.
                desiredX = groundX;
                desiredZ = groundZ;
            }
            requestStep(player, desiredX, desiredZ, worldMoveX, worldMoveZ);
            appliedX = clamp(desiredX, groundX - COLLISION_LEAD, groundX + COLLISION_LEAD);
            appliedZ = clamp(desiredZ, groundZ - COLLISION_LEAD, groundZ + COLLISION_LEAD);
        }
        player.method5395(appliedX, position.aFloat2656, appliedZ);
    }

    /** Adjacent approvals remain pending until presentation reaches the boundary. */
    private boolean syncAuthority(Player player, float desiredX, float desiredZ) {
        int tileX = player.screenX[0], tileZ = player.screenY[0];
        int dx = tileX - collisionTileX, dz = tileZ - collisionTileZ;
        if (dx == 0 && dz == 0) {
            pendingAuthority = false;
            return false;
        }
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
            adoptTile(player, tileX, tileZ);
            return true;
        }
        pendingAuthority = true;
        float boundaryX = groundX + dx * COLLISION_LEAD;
        float boundaryZ = groundZ + dz * COLLISION_LEAD;
        boolean crossedX = dx == 0 || (dx > 0 ? desiredX >= boundaryX : desiredX <= boundaryX);
        boolean crossedZ = dz == 0 || (dz > 0 ? desiredZ >= boundaryZ : desiredZ <= boundaryZ);
        if (crossedX && crossedZ) {
            adoptTile(player, tileX, tileZ);
        }
        return false;
    }

    private void adoptTile(Player player, int tileX, int tileZ) {
        collisionTileX = tileX;
        collisionTileZ = tileZ;
        groundX = tileCenter(player, tileX);
        groundZ = tileCenter(player, tileZ);
        pendingAuthority = false;
        resetWalk();
    }

    private void requestStep(Player player, float desiredX, float desiredZ,
            float worldMoveX, float worldMoveZ) {
        if (client.aClass195_8589 == null || client.aClass613_8605 == null || pendingAuthority) {
            return;
        }
        float leadX = desiredX - groundX, leadZ = desiredZ - groundZ;
        int dx = worldMoveX > MOVE_DEADZONE && leadX >= WALK_LEAD ? 1
                : worldMoveX < -MOVE_DEADZONE && leadX <= -WALK_LEAD ? -1 : 0;
        int dz = worldMoveZ > MOVE_DEADZONE && leadZ >= WALK_LEAD ? 1
                : worldMoveZ < -MOVE_DEADZONE && leadZ <= -WALK_LEAD ? -1 : 0;
        if (dx == 0 && dz == 0) {
            return;
        }
        int targetX = collisionTileX + dx, targetZ = collisionTileZ + dz;
        if (targetX < 0 || targetZ < 0
                || targetX >= client.aClass613_8605.method7347(-520836217)
                || targetZ >= client.aClass613_8605.method7278(277214477)) {
            return;
        }
        boolean changed = targetX != lastWalkX || targetZ != lastWalkZ;
        boolean retry = lastWalkCycle == Integer.MIN_VALUE
                || client.cycles - lastWalkCycle >= WALK_RETRY_CYCLES;
        if (!changed && !retry) {
            return;
        }
        Class572_Sub25 packet = IncomingPacket.method4108(targetX, targetZ, 0, 0);
        if (packet != null) {
            client.aClass195_8589.method2929(packet, (byte) -1);
            lastWalkX = targetX;
            lastWalkZ = targetZ;
            lastWalkCycle = client.cycles;
        }
    }

    void restore(Player player) {
        if (valid && player != null && owner == player) {
            Class240 position = player.method5394().aClass240_2647;
            float x = clipping ? tileCenter(player, player.screenX[0])
                    : Math.abs(position.aFloat2653 - appliedX) > 0.5F ? position.aFloat2653 : baselineX;
            float z = clipping ? tileCenter(player, player.screenY[0])
                    : Math.abs(position.aFloat2657 - appliedZ) > 0.5F ? position.aFloat2657 : baselineZ;
            player.method5395(x, position.aFloat2656, z);
        }
        reset();
    }

    void reset() {
        valid = false;
        owner = null;
        pendingAuthority = false;
        resetWalk();
    }

    private void resetWalk() {
        lastWalkX = lastWalkZ = Integer.MIN_VALUE;
        lastWalkCycle = Integer.MIN_VALUE;
    }

    private static float tileCenter(Player player, int tile) {
        return tile * 512.0F + player.method10556((short) -23679) * 256.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
