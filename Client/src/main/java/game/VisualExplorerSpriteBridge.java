package game;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-thread sprite preview bridge for Visual Explorer.
 *
 * Swing only requests an ID and consumes immutable snapshots. Matrix3 cache
 * access and sprite decoding stay on the normal client cycle.
 */
public final class VisualExplorerSpriteBridge {

    private static final AtomicInteger REQUESTED_SPRITE = new AtomicInteger(-1);

    private static volatile SpriteSnapshot latest = SpriteSnapshot.empty();
    private static long sequence;

    private VisualExplorerSpriteBridge() {
    }

    public static void requestSprite(int spriteId) {
        if (spriteId < 0) {
            latest = SpriteSnapshot.error(spriteId, "Sprite ID must be zero or greater.", nextSequence());
            return;
        }
        REQUESTED_SPRITE.set(spriteId);
    }

    public static SpriteSnapshot getLatestSnapshot() {
        return latest;
    }

    /** Called from the normal Matrix3 logged-in client cycle. */
    public static void flushRequests() {
        int spriteId = REQUESTED_SPRITE.getAndSet(-1);
        if (spriteId < 0) {
            return;
        }

        Class248 archive = Class419.aClass248_4949;
        if (archive == null) {
            latest = SpriteSnapshot.error(spriteId, "Sprite archive is not ready.", nextSequence());
            return;
        }

        int totalGroups;
        try {
            totalGroups = archive.method3412();
        } catch (RuntimeException ex) {
            latest = SpriteSnapshot.error(spriteId,
                    "Sprite archive metadata failed: " + ex.getClass().getSimpleName(), nextSequence());
            return;
        }

        if (spriteId >= totalGroups) {
            latest = SpriteSnapshot.error(spriteId,
                    "Sprite " + spriteId + " is outside cache group range 0-" + Math.max(0, totalGroups - 1) + ".",
                    nextSequence(), totalGroups);
            return;
        }

        try {
            Class87 sprite = Class160.method2571(archive, spriteId, 0);
            if (sprite == null) {
                latest = SpriteSnapshot.error(spriteId,
                        "Sprite " + spriteId + " has no decodable file 0.", nextSequence(), totalGroups);
                return;
            }

            int fullWidth = Math.max(1, sprite.method1283());
            int fullHeight = Math.max(1, sprite.method1305());
            int contentWidth = Math.max(0, sprite.method1329());
            int contentHeight = Math.max(0, sprite.method1330());
            int[] pixels = sprite.method1285(true);

            if (pixels == null || pixels.length < fullWidth * fullHeight) {
                latest = SpriteSnapshot.error(spriteId,
                        "Sprite decoded but pixel buffer was incomplete.", nextSequence(), totalGroups);
                return;
            }

            int[] copy = new int[fullWidth * fullHeight];
            System.arraycopy(pixels, 0, copy, 0, copy.length);
            latest = new SpriteSnapshot(spriteId, true, fullWidth, fullHeight,
                    contentWidth, contentHeight, totalGroups, copy,
                    "Loaded sprite " + spriteId + " (" + fullWidth + "x" + fullHeight + ").",
                    nextSequence());
        } catch (RuntimeException ex) {
            latest = SpriteSnapshot.error(spriteId,
                    "Sprite " + spriteId + " decode failed: " + ex.getClass().getSimpleName(),
                    nextSequence(), totalGroups);
        }
    }

    private static synchronized long nextSequence() {
        return ++sequence;
    }

    public static final class SpriteSnapshot {
        private final int spriteId;
        private final boolean loaded;
        private final int width;
        private final int height;
        private final int contentWidth;
        private final int contentHeight;
        private final int totalGroups;
        private final int[] pixels;
        private final String status;
        private final long sequence;

        private SpriteSnapshot(int spriteId, boolean loaded,
                int width, int height, int contentWidth, int contentHeight,
                int totalGroups, int[] pixels, String status, long sequence) {
            this.spriteId = spriteId;
            this.loaded = loaded;
            this.width = width;
            this.height = height;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
            this.totalGroups = totalGroups;
            this.pixels = pixels == null ? new int[0] : pixels;
            this.status = status == null ? "" : status;
            this.sequence = sequence;
        }

        private static SpriteSnapshot empty() {
            return new SpriteSnapshot(-1, false, 0, 0, 0, 0, 0,
                    new int[0], "Enter a sprite ID.", 0L);
        }

        private static SpriteSnapshot error(int spriteId, String status, long sequence) {
            return error(spriteId, status, sequence, 0);
        }

        private static SpriteSnapshot error(int spriteId, String status, long sequence, int totalGroups) {
            return new SpriteSnapshot(spriteId, false, 0, 0, 0, 0, totalGroups,
                    new int[0], status, sequence);
        }

        public int getSpriteId() {
            return spriteId;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getContentWidth() {
            return contentWidth;
        }

        public int getContentHeight() {
            return contentHeight;
        }

        public int getTotalGroups() {
            return totalGroups;
        }

        public int[] getPixels() {
            return pixels.clone();
        }

        public String getStatus() {
            return status;
        }

        public long getSequence() {
            return sequence;
        }
    }
}
