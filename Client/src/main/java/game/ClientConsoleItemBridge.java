package game;

import java.awt.image.BufferedImage;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.SwingUtilities;

/**
 * Narrow client-thread bridge for Client Console item metadata and thumbnails.
 * Matrix3 item definitions/rendering remain authoritative.
 */
public final class ClientConsoleItemBridge {

    private static final int THUMBNAIL_WIDTH = 36;
    private static final int THUMBNAIL_HEIGHT = 32;
    private static final int MAX_QUEUED_THUMBNAILS = 96;
    private static final int MAX_THUMBNAILS_PER_CLIENT_CYCLE = 2;

    private static final Queue<ThumbnailRequest> THUMBNAIL_QUEUE =
            new ConcurrentLinkedQueue<ThumbnailRequest>();

    private static volatile Class639_Sub5 itemDefinitions;
    private static volatile RenderContext renderContext;

    private ClientConsoleItemBridge() {
    }

    static void registerItemDefinitions(Class639_Sub5 definitions) {
        if (definitions != null) {
            itemDefinitions = definitions;
        }
    }

    static void captureItemRenderContext(
            Class639_Sub5 definitions,
            Class106 primaryRenderer,
            Class106 secondaryRenderer,
            int outline,
            int shadow,
            boolean drawAmount,
            int style,
            Class102 font,
            Class474 class474,
            Class484 class484) {
        registerItemDefinitions(definitions);
        if (primaryRenderer == null || secondaryRenderer == null) {
            return;
        }
        RenderContext current = renderContext;
        if (current == null
                || current.primaryRenderer != primaryRenderer
                || current.secondaryRenderer != secondaryRenderer) {
            renderContext = new RenderContext(
                    primaryRenderer,
                    secondaryRenderer,
                    outline,
                    shadow,
                    drawAmount,
                    style,
                    font,
                    class474,
                    class484);
        }
    }

    public static boolean isItemDefinitionsReady() {
        return itemDefinitions != null;
    }

    public static boolean isThumbnailRendererReady() {
        return itemDefinitions != null && renderContext != null;
    }

    public static int getItemCount() {
        Class639_Sub5 definitions = itemDefinitions;
        return definitions == null ? 0 : Math.max(0, definitions.method45());
    }

    public static ItemInfo getItemInfo(int itemId) {
        Class639_Sub5 definitions = itemDefinitions;
        if (definitions == null || itemId < 0 || itemId >= definitions.method45()) {
            return null;
        }
        try {
            ItemDefinitions definition = (ItemDefinitions) definitions.getDefinition(itemId, 0);
            if (definition == null) {
                return null;
            }
            String name = definition.aString8180;
            if (name == null) {
                return null;
            }
            name = name.trim();
            if (name.length() == 0 || "null".equalsIgnoreCase(name)) {
                return null;
            }
            return new ItemInfo(itemId, name);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static boolean requestThumbnail(int itemId, int amount, ThumbnailCallback callback) {
        if (callback == null || itemId < 0) {
            return false;
        }
        if (THUMBNAIL_QUEUE.size() >= MAX_QUEUED_THUMBNAILS) {
            return false;
        }
        THUMBNAIL_QUEUE.offer(new ThumbnailRequest(itemId, Math.max(1, amount), callback));
        return true;
    }

    public static void flushThumbnailRequests() {
        Class639_Sub5 definitions = itemDefinitions;
        RenderContext context = renderContext;
        if (definitions == null || context == null) {
            return;
        }

        for (int rendered = 0; rendered < MAX_THUMBNAILS_PER_CLIENT_CYCLE; rendered++) {
            final ThumbnailRequest request = THUMBNAIL_QUEUE.poll();
            if (request == null) {
                return;
            }

            BufferedImage image = null;
            try {
                image = renderThumbnail(definitions, context, request.itemId, request.amount);
            } catch (RuntimeException ex) {
                System.err.println("Client Console Item Browser failed to render item " + request.itemId);
                ex.printStackTrace();
            }

            final BufferedImage completedImage = image;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    request.callback.thumbnailReady(request.itemId, completedImage);
                }
            });
        }
    }

    private static BufferedImage renderThumbnail(
            Class639_Sub5 definitions,
            RenderContext context,
            int itemId,
            int amount) {
        if (itemId < 0 || itemId >= definitions.method45()) {
            return null;
        }

        ItemDefinitions definition = (ItemDefinitions) definitions.getDefinition(itemId, 0);
        if (definition == null) {
            return null;
        }

        int[] pixels = definition.method7528(
                context.primaryRenderer,
                context.secondaryRenderer,
                amount,
                context.outline,
                context.shadow,
                context.drawAmount,
                context.style,
                context.font,
                context.class474,
                context.class484,
                (byte) 1);

        if (pixels == null || pixels.length < THUMBNAIL_WIDTH * THUMBNAIL_HEIGHT) {
            return null;
        }

        BufferedImage image = new BufferedImage(
                THUMBNAIL_WIDTH,
                THUMBNAIL_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < THUMBNAIL_HEIGHT; y++) {
            for (int x = 0; x < THUMBNAIL_WIDTH; x++) {
                int rgb = pixels[y * THUMBNAIL_WIDTH + x];
                image.setRGB(x, y, rgb == 0 ? 0 : 0xFF000000 | rgb & 0x00FFFFFF);
            }
        }
        return image;
    }

    public interface ThumbnailCallback {
        void thumbnailReady(int itemId, BufferedImage image);
    }

    public static final class ItemInfo {
        private final int itemId;
        private final String name;

        private ItemInfo(int itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }

        public int getItemId() {
            return itemId;
        }

        public String getName() {
            return name;
        }
    }

    private static final class ThumbnailRequest {
        private final int itemId;
        private final int amount;
        private final ThumbnailCallback callback;

        private ThumbnailRequest(int itemId, int amount, ThumbnailCallback callback) {
            this.itemId = itemId;
            this.amount = amount;
            this.callback = callback;
        }
    }

    private static final class RenderContext {
        private final Class106 primaryRenderer;
        private final Class106 secondaryRenderer;
        private final int outline;
        private final int shadow;
        private final boolean drawAmount;
        private final int style;
        private final Class102 font;
        private final Class474 class474;
        private final Class484 class484;

        private RenderContext(
                Class106 primaryRenderer,
                Class106 secondaryRenderer,
                int outline,
                int shadow,
                boolean drawAmount,
                int style,
                Class102 font,
                Class474 class474,
                Class484 class484) {
            this.primaryRenderer = primaryRenderer;
            this.secondaryRenderer = secondaryRenderer;
            this.outline = outline;
            this.shadow = shadow;
            this.drawAmount = drawAmount;
            this.style = style;
            this.font = font;
            this.class474 = class474;
            this.class484 = class484;
        }
    }
}
