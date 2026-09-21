package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Client-only capture contract for Visual Explorer.
 *
 * This class owns no renderer, cache, interface, combat, world, or gameplay
 * state. Instrumented render paths may publish lightweight draw records while
 * capture is enabled. A one-click picker then resolves the top-most recorded
 * candidates whose final screen bounds contain the picked point.
 */
public final class VisualExplorerCapture {

    public enum VisualType {
        INTERFACE("Interface"),
        SPRITE("Sprite"),
        GFX("GFX"),
        MODEL("Model"),
        TEXTURE("Texture / Material"),
        FONT("Font / Text"),
        CURSOR("Cursor"),
        OVERLAY("Overlay"),
        OTHER("Other");

        private final String displayName;

        VisualType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final int MAX_RECORDS_PER_FRAME = 1024;
    private static final int MAX_PICK_CANDIDATES = 32;

    private static final List<DrawRecord> FRAME_RECORDS = new ArrayList<DrawRecord>();

    private static volatile boolean captureEnabled;
    private static volatile boolean pickArmed;
    private static volatile boolean suppressPickGesture;
    private static volatile boolean listenerInstalled;

    private static int recordCycle = Integer.MIN_VALUE;
    private static int nextDrawOrder;
    private static int droppedRecordCount;
    private static PickSnapshot lastPick = PickSnapshot.empty();

    private VisualExplorerCapture() {
    }

    public static synchronized void setCaptureEnabled(boolean enabled) {
        if (enabled) {
            ensureListener();
            captureEnabled = true;
        } else {
            captureEnabled = false;
            pickArmed = false;
            suppressPickGesture = false;
        }
    }

    public static boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public static synchronized void armOneClickPick() {
        ensureListener();
        captureEnabled = true;
        pickArmed = true;
    }

    public static synchronized void cancelPick() {
        pickArmed = false;
        suppressPickGesture = false;
    }

    public static boolean isPickArmed() {
        return pickArmed;
    }

    public static synchronized void clear() {
        FRAME_RECORDS.clear();
        recordCycle = Integer.MIN_VALUE;
        nextDrawOrder = 0;
        droppedRecordCount = 0;
        lastPick = PickSnapshot.empty();
    }

    /**
     * Future render providers call this only after Matrix3 has resolved final
     * screen bounds. The hook must not change rendering behavior.
     */
    public static synchronized void recordDraw(VisualType type, int assetId,
            int x, int y, int width, int height, String source) {
        if (!captureEnabled) {
            return;
        }

        int cycle = client.cycles;
        if (recordCycle != cycle) {
            FRAME_RECORDS.clear();
            recordCycle = cycle;
            nextDrawOrder = 0;
            droppedRecordCount = 0;
        }

        if (FRAME_RECORDS.size() >= MAX_RECORDS_PER_FRAME) {
            droppedRecordCount++;
            return;
        }

        int normalizedX = width >= 0 ? x : x + width;
        int normalizedY = height >= 0 ? y : y + height;
        int normalizedWidth = Math.max(1, Math.abs(width));
        int normalizedHeight = Math.max(1, Math.abs(height));

        FRAME_RECORDS.add(new DrawRecord(
                type == null ? VisualType.OTHER : type,
                assetId,
                normalizedX,
                normalizedY,
                normalizedWidth,
                normalizedHeight,
                source == null ? "" : source,
                nextDrawOrder++,
                cycle));
    }

    public static synchronized Snapshot getSnapshot() {
        return new Snapshot(
                captureEnabled,
                pickArmed,
                recordCycle,
                FRAME_RECORDS.size(),
                droppedRecordCount,
                lastPick);
    }

    private static synchronized void pick(int x, int y) {
        List<DrawRecord> candidates = new ArrayList<DrawRecord>();
        for (DrawRecord record : FRAME_RECORDS) {
            if (record.contains(x, y)) {
                candidates.add(record);
            }
        }

        Collections.sort(candidates, new Comparator<DrawRecord>() {
            @Override
            public int compare(DrawRecord left, DrawRecord right) {
                return right.drawOrder - left.drawOrder;
            }
        });

        if (candidates.size() > MAX_PICK_CANDIDATES) {
            candidates = new ArrayList<DrawRecord>(
                    candidates.subList(0, MAX_PICK_CANDIDATES));
        }

        lastPick = new PickSnapshot(
                true,
                x,
                y,
                client.cycles,
                System.currentTimeMillis(),
                candidates);
        pickArmed = false;
    }

    private static synchronized void ensureListener() {
        if (listenerInstalled) {
            return;
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent) {
                    handleMouseEvent((MouseEvent) event);
                } else if (event instanceof KeyEvent) {
                    handleKeyEvent((KeyEvent) event);
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

        listenerInstalled = true;
    }

    private static void handleMouseEvent(MouseEvent mouse) {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || mouse.getSource() != canvas) {
            return;
        }

        if (mouse.getID() == MouseEvent.MOUSE_PRESSED && pickArmed) {
            synchronized (VisualExplorerCapture.class) {
                pick(mouse.getX(), mouse.getY());
                suppressPickGesture = true;
            }
            mouse.consume();
            return;
        }

        if (suppressPickGesture
                && (mouse.getID() == MouseEvent.MOUSE_RELEASED
                    || mouse.getID() == MouseEvent.MOUSE_CLICKED)) {
            mouse.consume();
            if (mouse.getID() == MouseEvent.MOUSE_CLICKED) {
                suppressPickGesture = false;
            }
        }
    }

    private static void handleKeyEvent(KeyEvent key) {
        if (!pickArmed || key.getID() != KeyEvent.KEY_PRESSED
                || key.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return;
        }

        cancelPick();
        key.consume();
    }

    public static final class DrawRecord {
        private final VisualType type;
        private final int assetId;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String source;
        private final int drawOrder;
        private final int cycle;

        private DrawRecord(VisualType type, int assetId, int x, int y,
                int width, int height, String source, int drawOrder, int cycle) {
            this.type = type;
            this.assetId = assetId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.source = source;
            this.drawOrder = drawOrder;
            this.cycle = cycle;
        }

        private boolean contains(int pointX, int pointY) {
            return pointX >= x && pointY >= y
                    && pointX < x + width && pointY < y + height;
        }

        public VisualType getType() {
            return type;
        }

        public int getAssetId() {
            return assetId;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public String getSource() {
            return source;
        }

        public int getDrawOrder() {
            return drawOrder;
        }

        public int getCycle() {
            return cycle;
        }

        @Override
        public String toString() {
            return type + " id=" + assetId
                    + " bounds=" + x + "," + y + " " + width + "x" + height
                    + " order=" + drawOrder
                    + (source.length() == 0 ? "" : " source=" + source);
        }
    }

    public static final class PickSnapshot {
        private final boolean hasPick;
        private final int x;
        private final int y;
        private final int cycle;
        private final long timestamp;
        private final List<DrawRecord> candidates;

        private PickSnapshot(boolean hasPick, int x, int y, int cycle,
                long timestamp, List<DrawRecord> candidates) {
            this.hasPick = hasPick;
            this.x = x;
            this.y = y;
            this.cycle = cycle;
            this.timestamp = timestamp;
            this.candidates = Collections.unmodifiableList(
                    new ArrayList<DrawRecord>(candidates));
        }

        private static PickSnapshot empty() {
            return new PickSnapshot(false, -1, -1, Integer.MIN_VALUE, 0L,
                    Collections.<DrawRecord>emptyList());
        }

        public boolean hasPick() {
            return hasPick;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getCycle() {
            return cycle;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public List<DrawRecord> getCandidates() {
            return candidates;
        }
    }

    public static final class Snapshot {
        private final boolean enabled;
        private final boolean armed;
        private final int recordCycle;
        private final int recordCount;
        private final int droppedRecordCount;
        private final PickSnapshot lastPick;

        private Snapshot(boolean enabled, boolean armed, int recordCycle,
                int recordCount, int droppedRecordCount, PickSnapshot lastPick) {
            this.enabled = enabled;
            this.armed = armed;
            this.recordCycle = recordCycle;
            this.recordCount = recordCount;
            this.droppedRecordCount = droppedRecordCount;
            this.lastPick = lastPick;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isArmed() {
            return armed;
        }

        public int getRecordCycle() {
            return recordCycle;
        }

        public int getRecordCount() {
            return recordCount;
        }

        public int getDroppedRecordCount() {
            return droppedRecordCount;
        }

        public PickSnapshot getLastPick() {
            return lastPick;
        }
    }
}
