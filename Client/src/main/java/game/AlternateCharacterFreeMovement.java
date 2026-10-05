package game;

/** Local-only continuous X/Z presentation shared by native character drivers. */
final class AlternateCharacterFreeMovement {
    private Player owner;
    private boolean valid;
    private float baselineX, baselineZ;
    private float appliedX, appliedZ;
    private float nativeX, nativeZ;

    void apply(Player player, float x, float z, float scale) {
        if (player == null || !finite(x) || !finite(z) || !finite(scale) || scale <= 0.0F) {
            return;
        }
        Class240 position = player.method5394().aClass240_2647;
        if (!valid || owner != player) {
            owner = player;
            baselineX = appliedX = position.aFloat2653;
            baselineZ = appliedZ = position.aFloat2657;
            nativeX = x;
            nativeZ = z;
            valid = true;
        }
        // External Matrix corrections remain authoritative; never mistake our
        // own previous presentation write for a new server/world baseline.
        if (Math.abs(position.aFloat2653 - appliedX) > 0.5F) {
            baselineX = appliedX = position.aFloat2653;
        }
        if (Math.abs(position.aFloat2657 - appliedZ) > 0.5F) {
            baselineZ = appliedZ = position.aFloat2657;
        }
        appliedX += (x - nativeX) * scale;
        appliedZ += (z - nativeZ) * scale;
        nativeX = x;
        nativeZ = z;
        // No tile rounding, boundary clamp, walk packet or tile-approval wait.
        player.method5395(appliedX, position.aFloat2656, appliedZ);
    }

    void restore(Player player) {
        if (valid && player != null && owner == player) {
            Class240 position = player.method5394().aClass240_2647;
            float x = Math.abs(position.aFloat2653 - appliedX) > 0.5F
                    ? position.aFloat2653 : baselineX;
            float z = Math.abs(position.aFloat2657 - appliedZ) > 0.5F
                    ? position.aFloat2657 : baselineZ;
            player.method5395(x, position.aFloat2656, z);
        }
        reset();
    }

    void reset() {
        valid = false;
        owner = null;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
