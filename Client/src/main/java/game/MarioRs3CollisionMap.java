package game;

/**
 * Read-only client cache of vanilla Matrix3 walk-step decisions for Mario mode.
 *
 * The server produces the bits from World.checkWalkStep(...). Native Mario X/Z can
 * therefore be clipped immediately on the client without waiting for a server
 * response at every tile edge. Normal RuneScape walk packets still make the real
 * server player follow behind; this cache is presentation/collision prediction,
 * never gameplay authority.
 */
public final class MarioRs3CollisionMap {

    private static final String REQUEST = "__m64clip";
    private static final String PREFIX = "M64C|";
    private static final int VERSION = 1;
    private static final int REQUEST_RETRY_CYCLES = 25;
    private static final int SNAPSHOT_REFRESH_CYCLES = 50;
    private static final int EDGE_REFRESH_TILES = 3;
    private static final float MAX_SUBSTEP = 64.0F;
    private static final float EDGE_EPSILON = 0.01F;

    // Server bit order.
    private static final int WEST = 0;
    private static final int EAST = 1;
    private static final int SOUTH = 2;
    private static final int NORTH = 3;
    private static final int SOUTH_WEST = 4;
    private static final int SOUTH_EAST = 5;
    private static final int NORTH_WEST = 6;
    private static final int NORTH_EAST = 7;

    private static volatile Snapshot snapshot;
    private static Assembly assembly;
    private static int lastRequestCycle = Integer.MIN_VALUE;
    private static int lastFollowTargetX = Integer.MIN_VALUE;
    private static int lastFollowTargetY = Integer.MIN_VALUE;
    private static int lastFollowRequestCycle = Integer.MIN_VALUE;

    private MarioRs3CollisionMap() {
    }

    public static void reset() {
        snapshot = null;
        assembly = null;
        lastRequestCycle = Integer.MIN_VALUE;
        lastFollowTargetX = Integer.MIN_VALUE;
        lastFollowTargetY = Integer.MIN_VALUE;
        lastFollowRequestCycle = Integer.MIN_VALUE;
    }

    public static void tick(Player player, float sceneX, float sceneZ) {
        requestSnapshotIfNeeded(player, sceneX, sceneZ);
        followServerPlayer(player, sceneX, sceneZ);
    }

    /**
     * Consumes hidden GAME_MESSAGE type-99 chunks dispatched through Class118.
     */
    public static boolean handleServerCommand(String message) {
        if (message == null || !message.startsWith(PREFIX))
            return false;

        try {
            String[] parts = message.split("\\|", 10);
            if (parts.length != 10 || !"M64C".equals(parts[0]))
                return true;

            int version = Integer.parseInt(parts[1]);
            int sequence = Integer.parseInt(parts[2]);
            int plane = Integer.parseInt(parts[3]);
            int centerX = Integer.parseInt(parts[4]);
            int centerY = Integer.parseInt(parts[5]);
            int radius = Integer.parseInt(parts[6]);
            int rowStart = Integer.parseInt(parts[7]);
            int rowCount = Integer.parseInt(parts[8]);
            String hex = parts[9];

            if (version != VERSION || radius < 1 || radius > 32)
                return true;
            int width = radius * 2 + 1;
            if (rowStart < 0 || rowCount < 1 || rowStart + rowCount > width
                    || hex.length() != rowCount * width * 2)
                return true;

            if (assembly == null || assembly.sequence != sequence) {
                assembly = new Assembly(sequence, plane, centerX, centerY, radius);
            }
            if (!assembly.matches(plane, centerX, centerY, radius))
                return true;

            int hexIndex = 0;
            for (int row = rowStart; row < rowStart + rowCount; row++) {
                int offset = row * width;
                for (int col = 0; col < width; col++) {
                    int hi = hexDigit(hex.charAt(hexIndex++));
                    int lo = hexDigit(hex.charAt(hexIndex++));
                    if (hi < 0 || lo < 0)
                        return true;
                    assembly.pass[offset + col] = (byte) ((hi << 4) | lo);
                }
                if (!assembly.rows[row]) {
                    assembly.rows[row] = true;
                    assembly.receivedRows++;
                }
            }

            if (assembly.receivedRows == width) {
                snapshot = new Snapshot(assembly.plane, assembly.centerX,
                        assembly.centerY, assembly.radius, assembly.pass,
                        client.cycles);
                assembly = null;
            }
        } catch (RuntimeException ignored) {
            // Protocol is developer-only and fail-closed. Malformed chunks are
            // swallowed instead of leaking into the normal client console.
        }
        return true;
    }

    /**
     * Resolve one continuous native-X/Z move against cached vanilla walk-step
     * legality. Same-tile motion is always smooth. Tile-edge crossings are tested
     * in <=64-unit substeps so high native speed cannot tunnel across a blocker.
     */
    public static Position resolve(Player player, float startX, float startZ,
            float desiredX, float desiredZ) {
        if (player == null)
            return new Position(startX, startZ);

        float deltaX = desiredX - startX;
        float deltaZ = desiredZ - startZ;
        int steps = (int) Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaZ)) / MAX_SUBSTEP);
        if (steps < 1)
            steps = 1;

        float stepX = deltaX / steps;
        float stepZ = deltaZ / steps;
        float x = startX;
        float z = startZ;

        for (int i = 0; i < steps; i++) {
            float nextX = x + stepX;
            float nextZ = z + stepZ;
            int sourceTileX = floorTile(x);
            int sourceTileY = floorTile(z);
            int targetTileX = floorTile(nextX);
            int targetTileY = floorTile(nextZ);
            int dx = Integer.signum(targetTileX - sourceTileX);
            int dz = Integer.signum(targetTileY - sourceTileY);

            if (dx == 0 && dz == 0) {
                x = nextX;
                z = nextZ;
                continue;
            }

            if (dx != 0 && dz != 0) {
                if (canCross(player, sourceTileX, sourceTileY, dx, dz)) {
                    x = nextX;
                    z = nextZ;
                    continue;
                }

                // Native Mario should slide along a wall/corner when one cardinal
                // component remains legal instead of sticking to the square grid.
                boolean xAllowed = canCross(player, sourceTileX, sourceTileY, dx, 0);
                boolean zAllowed = canCross(player, sourceTileX, sourceTileY, 0, dz);
                if (xAllowed)
                    x = nextX;
                else
                    x = clampToBoundary(x, sourceTileX, dx);
                if (zAllowed)
                    z = nextZ;
                else
                    z = clampToBoundary(z, sourceTileY, dz);
                continue;
            }

            if (canCross(player, sourceTileX, sourceTileY, dx, dz)) {
                x = nextX;
                z = nextZ;
            } else if (dx != 0) {
                x = clampToBoundary(x, sourceTileX, dx);
                z = nextZ;
            } else {
                x = nextX;
                z = clampToBoundary(z, sourceTileY, dz);
            }
        }
        return new Position(x, z);
    }

    private static boolean canCross(Player player, int localTileX, int localTileY,
            int dx, int dy) {
        Snapshot local = snapshot;
        if (local == null || player.aByte9009 != local.plane)
            return false;

        Class497 base = client.aClass613_8605.method7280((byte) -44);
        int worldX = base.localX * -2109597897 + localTileX;
        int worldY = base.localY * 417324155 + localTileY;
        return local.canStep(worldX, worldY, dx, dy);
    }

    private static float clampToBoundary(float current, int sourceTile, int direction) {
        if (direction > 0)
            return Math.max(current, (sourceTile + 1) * 512.0F - EDGE_EPSILON);
        return Math.min(current, sourceTile * 512.0F + EDGE_EPSILON);
    }

    private static void requestSnapshotIfNeeded(Player player, float sceneX, float sceneZ) {
        if (player == null || client.aClass195_8589 == null)
            return;

        Snapshot local = snapshot;
        boolean need = local == null || local.plane != player.aByte9009;
        if (!need && client.cycles - local.receivedCycle >= SNAPSHOT_REFRESH_CYCLES)
            need = true;

        if (!need) {
            Class497 base = client.aClass613_8605.method7280((byte) -43);
            int worldX = base.localX * -2109597897 + floorTile(sceneX);
            int worldY = base.localY * 417324155 + floorTile(sceneZ);
            need = worldX - local.minX() <= EDGE_REFRESH_TILES
                    || local.maxX() - worldX <= EDGE_REFRESH_TILES
                    || worldY - local.minY() <= EDGE_REFRESH_TILES
                    || local.maxY() - worldY <= EDGE_REFRESH_TILES;
        }

        if (!need)
            return;
        if (lastRequestCycle != Integer.MIN_VALUE
                && client.cycles - lastRequestCycle < REQUEST_RETRY_CYCLES)
            return;

        Class195 connection = client.aClass195_8589;
        Class572_Sub25 packet = Class378.sendOutPacket(OutgoingPacket.COMMANDS_PACKET,
                connection.aClass650_2340, -1072981984);
        packet.aRsByteBuffer.writeByte(REQUEST.length() + 3, -1384395473);
        packet.aRsByteBuffer.writeByte(0, -1384395473);
        packet.aRsByteBuffer.writeByte(0, -1384395473);
        packet.aRsByteBuffer.writeString(REQUEST, (byte) -113);
        connection.method2929(packet, (byte) -124);
        lastRequestCycle = client.cycles;
    }

    /**
     * Keep the real RuneScape player following the collision-valid visual tile.
     * This is deliberately asynchronous: network cadence never gates visible Mario.
     */
    private static void followServerPlayer(Player player, float sceneX, float sceneZ) {
        if (player == null || client.aClass195_8589 == null)
            return;

        int targetX = floorTile(sceneX);
        int targetY = floorTile(sceneZ);
        if (targetX < 0 || targetY < 0
                || targetX >= client.aClass613_8605.method7347(-520836217)
                || targetY >= client.aClass613_8605.method7278(277214477))
            return;
        if (targetX == player.screenX[0] && targetY == player.screenY[0])
            return;

        boolean changed = targetX != lastFollowTargetX || targetY != lastFollowTargetY;
        boolean retry = lastFollowRequestCycle == Integer.MIN_VALUE
                || client.cycles - lastFollowRequestCycle >= 10;
        if (!changed && !retry)
            return;

        Class572_Sub25 packet = IncomingPacket.method4108(targetX, targetY, 0, 0);
        if (packet == null)
            return;
        client.aClass195_8589.method2929(packet, (byte) -1);
        lastFollowTargetX = targetX;
        lastFollowTargetY = targetY;
        lastFollowRequestCycle = client.cycles;
    }

    private static int floorTile(float sceneCoordinate) {
        return (int) Math.floor(sceneCoordinate / 512.0F);
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9')
            return c - '0';
        if (c >= 'A' && c <= 'F')
            return c - 'A' + 10;
        if (c >= 'a' && c <= 'f')
            return c - 'a' + 10;
        return -1;
    }

    private static int directionBit(int dx, int dy) {
        if (dx < 0 && dy == 0) return WEST;
        if (dx > 0 && dy == 0) return EAST;
        if (dx == 0 && dy < 0) return SOUTH;
        if (dx == 0 && dy > 0) return NORTH;
        if (dx < 0 && dy < 0) return SOUTH_WEST;
        if (dx > 0 && dy < 0) return SOUTH_EAST;
        if (dx < 0 && dy > 0) return NORTH_WEST;
        if (dx > 0 && dy > 0) return NORTH_EAST;
        return -1;
    }

    public static final class Position {
        public final float x;
        public final float z;

        Position(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class Snapshot {
        final int plane;
        final int centerX;
        final int centerY;
        final int radius;
        final int width;
        final byte[] pass;
        final int receivedCycle;

        Snapshot(int plane, int centerX, int centerY, int radius,
                byte[] pass, int receivedCycle) {
            this.plane = plane;
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.width = radius * 2 + 1;
            this.pass = pass.clone();
            this.receivedCycle = receivedCycle;
        }

        int minX() { return centerX - radius; }
        int maxX() { return centerX + radius; }
        int minY() { return centerY - radius; }
        int maxY() { return centerY + radius; }

        boolean canStep(int worldX, int worldY, int dx, int dy) {
            int bit = directionBit(dx, dy);
            if (bit < 0)
                return false;
            int localX = worldX - minX();
            int localY = worldY - minY();
            if (localX < 0 || localY < 0 || localX >= width || localY >= width)
                return false;
            int mask = pass[localY * width + localX] & 0xFF;
            return (mask & (1 << bit)) != 0;
        }
    }

    private static final class Assembly {
        final int sequence;
        final int plane;
        final int centerX;
        final int centerY;
        final int radius;
        final byte[] pass;
        final boolean[] rows;
        int receivedRows;

        Assembly(int sequence, int plane, int centerX, int centerY, int radius) {
            this.sequence = sequence;
            this.plane = plane;
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            int width = radius * 2 + 1;
            this.pass = new byte[width * width];
            this.rows = new boolean[width];
        }

        boolean matches(int plane, int centerX, int centerY, int radius) {
            return this.plane == plane && this.centerX == centerX
                    && this.centerY == centerY && this.radius == radius;
        }
    }
}
