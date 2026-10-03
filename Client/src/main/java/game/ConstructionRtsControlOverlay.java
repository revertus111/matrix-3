package game;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Compact player-facing RTS control interface.
 *
 * The visual language is intentionally RuneScape-like: one dense movable panel,
 * shallow tabs, compact hitboxes and no repeated form rows. The owned overlay
 * surface remains the proven heavyweight-canvas-safe host used by Construction
 * tools; all visible controls are custom drawn here rather than Swing widgets.
 *
 * V1 control groups remain settlement-session scoped. Runtime NPC indexes are
 * never persisted as worker identity; the server resolves recalled selections
 * back to stable worker ids for authoritative orders.
 */
public final class ConstructionRtsControlOverlay {

    private enum Tab {
        UNITS("UNITS"),
        GROUPS("GROUPS"),
        CAMERA("CAMERA");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int GROUP_COUNT = 9;
    private static final long DOUBLE_TAP_MS = 400L;
    private static final long REFRESH_THROTTLE_MS = 50L;

    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 152;
    private static final int MIN_WIDTH = 280;
    private static final int MIN_HEIGHT = 138;
    private static final int MAX_WIDTH = 440;
    private static final int MAX_HEIGHT = 220;

    private static final int TITLE_HEIGHT = 21;
    private static final int TAB_HEIGHT = 21;
    private static final int STATUS_HEIGHT = 17;
    private static final int PAD = 5;

    private static final Color PANEL = new Color(24, 29, 35);
    private static final Color TITLE = new Color(31, 37, 44);
    private static final Color CONTENT = new Color(20, 25, 31);
    private static final Color ROW_ALT = new Color(29, 35, 42);
    private static final Color BORDER = new Color(93, 104, 116);
    private static final Color INNER_BORDER = new Color(48, 57, 67);
    private static final Color TEXT = new Color(238, 238, 238);
    private static final Color MUTED = new Color(174, 181, 188);
    private static final Color ACCENT = new Color(195, 161, 82);
    private static final Color TAB_ACTIVE = new Color(64, 73, 82);
    private static final Color BUTTON = new Color(45, 53, 62);
    private static final Color BUTTON_HOVER = new Color(62, 72, 83);
    private static final Color GROUP_SAVED = new Color(63, 70, 52);

    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font BOLD_FONT = new Font("SansSerif", Font.BOLD, 10);
    private static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 9);

    private static final int[][] groupNpcIndexes = new int[GROUP_COUNT][];
    private static final boolean[] groupPlayerSelected = new boolean[GROUP_COUNT];
    private static final boolean[] numberKeyDown = new boolean[GROUP_COUNT];

    private static JWindow window;
    private static Window owner;
    private static ControlSurface surface;

    private static volatile long lastRefreshRequestMillis;
    private static volatile int lastRecallSlot = -1;
    private static volatile long lastRecallMillis;
    private static volatile String status = "L=GO  CTRL+L=SET  R=CLEAR";
    private static volatile Tab activeTab = Tab.GROUPS;

    // Panel geometry persists for the lifetime of this client session.
    private static boolean boundsInitialized;
    private static int overlayX;
    private static int overlayY;
    private static int overlayWidth = DEFAULT_WIDTH;
    private static int overlayHeight = DEFAULT_HEIGHT;

    private static volatile LayoutSnapshot latestLayout = LayoutSnapshot.empty();

    private ConstructionRtsControlOverlay() {
    }

    static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshRequestMillis < REFRESH_THROTTLE_MS) {
            return;
        }
        lastRefreshRequestMillis = now;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshNow();
            }
        });
    }

    static boolean handleKey(KeyEvent event) {
        int slot = slotForKeyCode(event.getKeyCode());
        if (slot < 0) {
            return false;
        }

        if (event.getID() == KeyEvent.KEY_RELEASED) {
            numberKeyDown[slot] = false;
            if (isHotkeyContextAvailable()) {
                event.consume();
                return true;
            }
            return false;
        }
        if (event.getID() != KeyEvent.KEY_PRESSED || !isHotkeyContextAvailable()) {
            return false;
        }
        if (numberKeyDown[slot]) {
            event.consume();
            return true;
        }
        numberKeyDown[slot] = true;

        if (event.isControlDown()) {
            saveGroup(slot);
            lastRecallSlot = -1;
            lastRecallMillis = 0L;
        } else {
            long now = System.currentTimeMillis();
            boolean focus = slot == lastRecallSlot
                    && lastRecallMillis > 0L
                    && now - lastRecallMillis <= DOUBLE_TAP_MS;
            recallGroup(slot, focus);
            lastRecallSlot = slot;
            lastRecallMillis = now;
        }
        event.consume();
        repaintSurface();
        return true;
    }

    static synchronized void resetForSettlementBoundary() {
        for (int i = 0; i < GROUP_COUNT; i++) {
            groupNpcIndexes[i] = null;
            groupPlayerSelected[i] = false;
            numberKeyDown[i] = false;
        }
        lastRecallSlot = -1;
        lastRecallMillis = 0L;
        status = "L=GO  CTRL+L=SET  R=CLEAR";
        activeTab = Tab.GROUPS;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                hideNow();
            }
        });
    }

    private static boolean isHotkeyContextAvailable() {
        return ConstructionBuildCamera.isSettlementAutoMode()
                && ConstructionBuildCamera.isRtsMode()
                && !ConstructionPaletteOverlay.isVisible()
                && !ConstructionPlacementController.isArmed()
                && !ConstructionPlacementController.isEraserMode()
                && !ConstructionWorkerJobsOverlay.isVisible()
                && !ConstructionStorageSettingsOverlay.isVisible()
                && !LiveModelEditorPreview.isEditSessionActive();
    }

    private static int slotForKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9) {
            return keyCode - KeyEvent.VK_1;
        }
        if (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return keyCode - KeyEvent.VK_NUMPAD1;
        }
        return -1;
    }

    private static synchronized void saveGroup(int slot) {
        int[] selected = ConstructionRadialSelection.snapshotCommittedWorkerNpcIndexes();
        boolean self = ConstructionRadialSelection.isLocalPlayerSelected();
        if (selected.length == 0 && !self) {
            groupNpcIndexes[slot] = null;
            groupPlayerSelected[slot] = false;
            status = "GROUP " + (slot + 1) + " CLEARED - NOTHING SELECTED";
        } else {
            groupNpcIndexes[slot] = java.util.Arrays.copyOf(selected, selected.length);
            groupPlayerSelected[slot] = self;
            status = "GROUP " + (slot + 1) + " SAVED: " + selected.length + " WORKER"
                    + (selected.length == 1 ? "" : "S") + (self ? " + SELF" : "");
        }
        repaintSurface();
    }

    private static synchronized void clearGroup(int slot) {
        if (slot < 0 || slot >= GROUP_COUNT) {
            return;
        }
        groupNpcIndexes[slot] = null;
        groupPlayerSelected[slot] = false;
        if (lastRecallSlot == slot) {
            lastRecallSlot = -1;
            lastRecallMillis = 0L;
        }
        status = "GROUP " + (slot + 1) + " REMOVED";
        repaintSurface();
    }

    private static synchronized void recallGroup(int slot, boolean focusCamera) {
        int[] saved = groupNpcIndexes[slot];
        boolean self = groupPlayerSelected[slot];
        if ((saved == null || saved.length == 0) && !self) {
            status = "GROUP " + (slot + 1) + " IS EMPTY";
            repaintSurface();
            return;
        }

        int[] copy = saved == null ? new int[0]
                : java.util.Arrays.copyOf(saved, saved.length);
        boolean applied = ConstructionRadialSelection.applyControlGroupSelection(copy, self);
        if (!applied) {
            status = "GROUP " + (slot + 1) + " HAS NO ACTIVE UNITS";
            repaintSurface();
            return;
        }

        status = "GROUP " + (slot + 1) + " SELECTED";
        if (focusCamera) {
            focusCurrentSelection("GROUP " + (slot + 1));
        }
        repaintSurface();
    }

    private static void focusCurrentSelection(String source) {
        int[] center = ConstructionRadialSelection.getCommittedSelectionCenterLocalTile();
        if (center == null) {
            status = "NO ACTIVE SELECTION TO FOCUS";
            return;
        }
        if (ConstructionBuildCamera.focusRtsAtLocalTileFromControlGroup(center[0], center[1])) {
            status = source + " - CAMERA MOVING";
        }
    }

    private static void centerSettlement() {
        if (client.aClass613_8605 == null) {
            status = "SETTLEMENT SCENE NOT READY";
            return;
        }
        int width = client.aClass613_8605.method7347(-740581830);
        int height = client.aClass613_8605.method7278(277214477);
        if (width <= 0 || height <= 0) {
            status = "SETTLEMENT SCENE NOT READY";
            return;
        }
        if (ConstructionBuildCamera.focusRtsAtLocalTileFromControlGroup(width / 2, height / 2)) {
            status = "CAMERA MOVING TO SETTLEMENT CENTER";
        }
    }

    private static void refreshNow() {
        if (!ConstructionBuildCamera.isSettlementAutoMode()
                || !ConstructionBuildCamera.isRtsMode()) {
            hideNow();
            return;
        }
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            hideNow();
            return;
        }
        if (!ensureWindow(canvas)) {
            hideNow();
            return;
        }

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            hideNow();
            return;
        }
        Rectangle canvasBounds = new Rectangle(
                canvasLocation.x, canvasLocation.y,
                Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()));

        if (!boundsInitialized) {
            overlayWidth = Math.min(DEFAULT_WIDTH, Math.max(MIN_WIDTH, canvasBounds.width - 8));
            overlayHeight = Math.min(DEFAULT_HEIGHT, Math.max(MIN_HEIGHT, canvasBounds.height - 8));
            overlayX = canvasBounds.x + 5;
            overlayY = canvasBounds.y + Math.min(65,
                    Math.max(4, canvasBounds.height - overlayHeight - 4));
            boundsInitialized = true;
        }

        Rectangle clamped = clampBounds(
                new Rectangle(overlayX, overlayY, overlayWidth, overlayHeight),
                canvasBounds);
        overlayX = clamped.x;
        overlayY = clamped.y;
        overlayWidth = clamped.width;
        overlayHeight = clamped.height;

        if (!clamped.equals(window.getBounds())) {
            window.setBounds(clamped);
        }
        if (!window.isVisible()) {
            window.setVisible(true);
        }
        repaintSurface();
    }

    private static boolean ensureWindow(Canvas canvas) {
        Window currentOwner = SwingUtilities.getWindowAncestor(canvas);
        if (currentOwner == null) {
            return false;
        }
        if (window != null && owner == currentOwner && surface != null) {
            return true;
        }
        if (window != null) {
            window.dispose();
        }

        owner = currentOwner;
        surface = new ControlSurface();
        surface.setOpaque(true);
        surface.setBackground(PANEL);
        installSurfaceInput(surface);

        window = new JWindow(owner);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setContentPane(surface);
        return true;
    }

    private static void installSurfaceInput(final JComponent component) {
        MouseAdapter mouse = new MouseAdapter() {
            private Point dragStartScreen;
            private Point dragStartWindow;
            private Dimension resizeStart;
            private boolean moving;
            private boolean resizing;

            @Override
            public void mousePressed(MouseEvent event) {
                LayoutSnapshot layout = latestLayout;
                if (event.getButton() == MouseEvent.BUTTON1 && layout.resize.contains(event.getPoint())) {
                    resizing = true;
                    resizeStart = window == null ? null : window.getSize();
                    dragStartScreen = event.getLocationOnScreen();
                    event.consume();
                    return;
                }
                if (event.getButton() == MouseEvent.BUTTON1 && layout.title.contains(event.getPoint())) {
                    moving = true;
                    dragStartScreen = event.getLocationOnScreen();
                    dragStartWindow = window == null ? null : window.getLocation();
                    event.consume();
                    return;
                }

                for (int i = 0; i < layout.tabs.length; i++) {
                    if (layout.tabs[i].contains(event.getPoint())) {
                        if (event.getButton() == MouseEvent.BUTTON1) {
                            activeTab = Tab.values()[i];
                            status = activeTab == Tab.GROUPS
                                    ? "L=GO  CTRL+L=SET  R=CLEAR"
                                    : activeTab.label;
                            repaintSurface();
                        }
                        event.consume();
                        return;
                    }
                }

                if (activeTab == Tab.GROUPS) {
                    for (int i = 0; i < layout.groups.length; i++) {
                        if (!layout.groups[i].contains(event.getPoint())) {
                            continue;
                        }
                        if (event.getButton() == MouseEvent.BUTTON3) {
                            clearGroup(i);
                        } else if (event.getButton() == MouseEvent.BUTTON1) {
                            if (event.isControlDown()) {
                                saveGroup(i);
                            } else {
                                // Mouse group recall behaves like classic RTS UI: select + focus.
                                recallGroup(i, true);
                            }
                        }
                        event.consume();
                        return;
                    }
                } else if (activeTab == Tab.UNITS && event.getButton() == MouseEvent.BUTTON1) {
                    if (layout.unitsSelectAll.contains(event.getPoint())) {
                        ConstructionRadialSelection.selectAllWorkers();
                        status = "ALL ACTIVE WORKERS SELECTED";
                    } else if (layout.unitsClear.contains(event.getPoint())) {
                        ConstructionRadialSelection.clearCommittedRadius();
                        status = "SELECTION CLEARED";
                    } else if (layout.unitsFocus.contains(event.getPoint())) {
                        focusCurrentSelection("SELECTION");
                    } else {
                        return;
                    }
                    repaintSurface();
                    event.consume();
                    return;
                } else if (activeTab == Tab.CAMERA && event.getButton() == MouseEvent.BUTTON1) {
                    if (layout.speedDown.contains(event.getPoint())) {
                        ConstructionBuildCamera.adjustRtsMoveSpeed(-1);
                        status = "CAMERA SPEED " + ConstructionBuildCamera.getRtsMoveSpeedLabel();
                    } else if (layout.speedUp.contains(event.getPoint())) {
                        ConstructionBuildCamera.adjustRtsMoveSpeed(1);
                        status = "CAMERA SPEED " + ConstructionBuildCamera.getRtsMoveSpeedLabel();
                    } else if (layout.cameraFocus.contains(event.getPoint())) {
                        focusCurrentSelection("SELECTION");
                    } else if (layout.cameraCenter.contains(event.getPoint())) {
                        centerSettlement();
                    } else {
                        return;
                    }
                    repaintSurface();
                    event.consume();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (window == null || dragStartScreen == null) {
                    return;
                }
                Point now = event.getLocationOnScreen();
                if (moving && dragStartWindow != null) {
                    int x = dragStartWindow.x + now.x - dragStartScreen.x;
                    int y = dragStartWindow.y + now.y - dragStartScreen.y;
                    setOverlayBoundsFromUser(x, y, window.getWidth(), window.getHeight());
                    event.consume();
                } else if (resizing && resizeStart != null) {
                    int width = resizeStart.width + now.x - dragStartScreen.x;
                    int height = resizeStart.height + now.y - dragStartScreen.y;
                    setOverlayBoundsFromUser(window.getX(), window.getY(), width, height);
                    event.consume();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                moving = false;
                resizing = false;
                dragStartScreen = null;
                dragStartWindow = null;
                resizeStart = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                if (surface != null) {
                    surface.hoverX = event.getX();
                    surface.hoverY = event.getY();
                    surface.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (surface != null) {
                    surface.hoverX = -1;
                    surface.hoverY = -1;
                    surface.repaint();
                }
            }
        };
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
    }

    private static void setOverlayBoundsFromUser(int x, int y, int width, int height) {
        Canvas canvas = Class584.aCanvas7745;
        if (window == null || canvas == null || !canvas.isShowing()) {
            return;
        }
        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }
        Rectangle canvasBounds = new Rectangle(
                canvasLocation.x, canvasLocation.y,
                Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()));
        Rectangle clamped = clampBounds(
                new Rectangle(x, y, width, height), canvasBounds);
        overlayX = clamped.x;
        overlayY = clamped.y;
        overlayWidth = clamped.width;
        overlayHeight = clamped.height;
        boundsInitialized = true;
        window.setBounds(clamped);
        repaintSurface();
    }

    private static Rectangle clampBounds(Rectangle desired, Rectangle canvas) {
        int maxWidth = Math.max(1, Math.min(MAX_WIDTH, canvas.width - 6));
        int maxHeight = Math.max(1, Math.min(MAX_HEIGHT, canvas.height - 6));
        int minWidth = Math.min(MIN_WIDTH, maxWidth);
        int minHeight = Math.min(MIN_HEIGHT, maxHeight);
        int width = Math.max(minWidth, Math.min(desired.width, maxWidth));
        int height = Math.max(minHeight, Math.min(desired.height, maxHeight));

        int minX = canvas.x + 3;
        int minY = canvas.y + 3;
        int maxX = canvas.x + canvas.width - width - 3;
        int maxY = canvas.y + canvas.height - height - 3;
        int x = Math.max(minX, Math.min(desired.x, Math.max(minX, maxX)));
        int y = Math.max(minY, Math.min(desired.y, Math.max(minY, maxY)));
        return new Rectangle(x, y, width, height);
    }

    private static void repaintSurface() {
        if (surface != null) {
            surface.repaint();
        }
    }

    private static void hideNow() {
        if (window != null && window.isVisible()) {
            window.setVisible(false);
        }
    }

    private static final class ControlSurface extends JComponent {
        private static final long serialVersionUID = 1L;
        int hoverX = -1;
        int hoverY = -1;

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(1.0F));

                int width = getWidth();
                int height = getHeight();
                g.setColor(PANEL);
                g.fillRect(0, 0, width, height);
                g.setColor(BORDER);
                g.drawRect(0, 0, Math.max(0, width - 1), Math.max(0, height - 1));

                LayoutSnapshot layout = layout(width, height);
                latestLayout = layout;

                drawTitle(g, layout);
                drawTabs(g, layout);
                if (activeTab == Tab.UNITS) {
                    drawUnits(g, layout);
                } else if (activeTab == Tab.GROUPS) {
                    drawGroups(g, layout);
                } else {
                    drawCamera(g, layout);
                }
                drawStatus(g, layout);
            } finally {
                g.dispose();
            }
        }

        private void drawTitle(Graphics2D g, LayoutSnapshot layout) {
            g.setColor(TITLE);
            g.fillRect(layout.title.x, layout.title.y, layout.title.width, layout.title.height);
            g.setColor(INNER_BORDER);
            g.drawLine(0, TITLE_HEIGHT - 1, getWidth(), TITLE_HEIGHT - 1);

            g.setFont(TITLE_FONT);
            g.setColor(TEXT);
            g.drawString("RTS CONTROL", PAD + 2, 14);

            int selected = ConstructionRadialSelection.getCommittedWorkerCount();
            int total = ConstructionRadialSelection.getTotalSettlementWorkerCount();
            String summary = "SEL " + selected + "/" + total
                    + (ConstructionRadialSelection.isLocalPlayerSelected() ? " +SELF" : "");
            FontMetrics fm = g.getFontMetrics();
            g.setColor(MUTED);
            g.drawString(summary, Math.max(PAD + 78, getWidth() - fm.stringWidth(summary) - 8), 14);
        }

        private void drawTabs(Graphics2D g, LayoutSnapshot layout) {
            g.setFont(BOLD_FONT);
            for (int i = 0; i < layout.tabs.length; i++) {
                Rectangle tab = layout.tabs[i];
                boolean active = activeTab.ordinal() == i;
                boolean hover = tab.contains(hoverX, hoverY);
                g.setColor(active ? TAB_ACTIVE : (hover ? BUTTON_HOVER : BUTTON));
                g.fillRect(tab.x, tab.y, tab.width, tab.height);
                g.setColor(active ? ACCENT : INNER_BORDER);
                g.drawRect(tab.x, tab.y, tab.width, tab.height);
                drawCentered(g, Tab.values()[i].label, tab,
                        active ? TEXT : MUTED, BOLD_FONT);
            }
        }

        private void drawUnits(Graphics2D g, LayoutSnapshot layout) {
            g.setColor(CONTENT);
            g.fillRect(layout.content.x, layout.content.y,
                    layout.content.width, layout.content.height);

            int selected = ConstructionRadialSelection.getCommittedWorkerCount();
            int total = ConstructionRadialSelection.getTotalSettlementWorkerCount();
            boolean self = ConstructionRadialSelection.isLocalPlayerSelected();

            g.setFont(BODY_FONT);
            g.setColor(TEXT);
            g.drawString("WORKERS  " + total + "    SELECTED  " + selected
                    + "    SELF  " + (self ? "YES" : "NO"),
                    layout.content.x + 6, layout.content.y + 18);

            drawButton(g, layout.unitsSelectAll, "SELECT ALL");
            drawButton(g, layout.unitsClear, "CLEAR");
            drawButton(g, layout.unitsFocus, "FOCUS");
        }

        private void drawGroups(Graphics2D g, LayoutSnapshot layout) {
            g.setColor(CONTENT);
            g.fillRect(layout.content.x, layout.content.y,
                    layout.content.width, layout.content.height);

            synchronized (ConstructionRtsControlOverlay.class) {
                for (int i = 0; i < GROUP_COUNT; i++) {
                    Rectangle cell = layout.groups[i];
                    int count = groupNpcIndexes[i] == null ? 0 : groupNpcIndexes[i].length;
                    boolean self = groupPlayerSelected[i];
                    boolean populated = count > 0 || self;
                    boolean hover = cell.contains(hoverX, hoverY);
                    g.setColor(populated ? GROUP_SAVED : ((i & 1) == 0 ? BUTTON : ROW_ALT));
                    if (hover) {
                        g.setColor(BUTTON_HOVER);
                    }
                    g.fillRect(cell.x, cell.y, cell.width, cell.height);
                    g.setColor(populated ? ACCENT : INNER_BORDER);
                    g.drawRect(cell.x, cell.y, cell.width, cell.height);

                    String label = (i + 1) + "   " + (count == 0 ? "-" : count + "W")
                            + (self ? "+S" : "");
                    drawCentered(g, label, cell, populated ? TEXT : MUTED, BOLD_FONT);
                }
            }
        }

        private void drawCamera(Graphics2D g, LayoutSnapshot layout) {
            g.setColor(CONTENT);
            g.fillRect(layout.content.x, layout.content.y,
                    layout.content.width, layout.content.height);

            g.setFont(BODY_FONT);
            g.setColor(TEXT);
            g.drawString("SPEED", layout.content.x + 8, layout.content.y + 18);
            drawButton(g, layout.speedDown, "-");
            drawButton(g, layout.speedUp, "+");

            Rectangle speedLabel = new Rectangle(
                    layout.speedDown.x + layout.speedDown.width + 3,
                    layout.speedDown.y,
                    Math.max(42, layout.speedUp.x - layout.speedDown.x - layout.speedDown.width - 6),
                    layout.speedDown.height);
            drawCentered(g, ConstructionBuildCamera.getRtsMoveSpeedLabel(),
                    speedLabel, ACCENT, BOLD_FONT);

            drawButton(g, layout.cameraFocus, "FOCUS SELECTION");
            drawButton(g, layout.cameraCenter, "CENTER SETTLEMENT");

            g.setFont(SMALL_FONT);
            g.setColor(MUTED);
            g.drawString("MINIMAP DRAG: ON   Q/E ROTATE   MMB ORBIT",
                    layout.content.x + 8,
                    Math.min(layout.status.y - 5, layout.content.y + layout.content.height - 5));
        }

        private void drawStatus(Graphics2D g, LayoutSnapshot layout) {
            g.setColor(TITLE);
            g.fillRect(layout.status.x, layout.status.y,
                    layout.status.width, layout.status.height);
            g.setColor(INNER_BORDER);
            g.drawLine(layout.status.x, layout.status.y,
                    layout.status.x + layout.status.width, layout.status.y);
            g.setFont(SMALL_FONT);
            g.setColor(MUTED);
            String clipped = clipText(g, status,
                    Math.max(20, layout.status.width - 22));
            g.drawString(clipped, layout.status.x + 5, layout.status.y + 12);

            g.setColor(MUTED);
            int gx = layout.resize.x + 2;
            int gy = layout.resize.y + 2;
            g.drawLine(gx + 3, gy + 7, gx + 7, gy + 3);
            g.drawLine(gx + 5, gy + 7, gx + 7, gy + 5);
        }

        private void drawButton(Graphics2D g, Rectangle bounds, String label) {
            boolean hover = bounds.contains(hoverX, hoverY);
            g.setColor(hover ? BUTTON_HOVER : BUTTON);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g.setColor(hover ? ACCENT : INNER_BORDER);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            drawCentered(g, label, bounds, TEXT, BOLD_FONT);
        }
    }

    private static LayoutSnapshot layout(int width, int height) {
        Rectangle title = new Rectangle(1, 1,
                Math.max(0, width - 2), TITLE_HEIGHT - 1);

        int tabY = TITLE_HEIGHT;
        int usable = Math.max(3, width - 2);
        int baseTabWidth = usable / 3;
        Rectangle[] tabs = new Rectangle[3];
        for (int i = 0; i < 3; i++) {
            int x = 1 + i * baseTabWidth;
            int w = i == 2 ? width - 1 - x : baseTabWidth;
            tabs[i] = new Rectangle(x, tabY, Math.max(1, w), TAB_HEIGHT);
        }

        int contentY = TITLE_HEIGHT + TAB_HEIGHT + 1;
        int statusY = Math.max(contentY + 1, height - STATUS_HEIGHT - 1);
        Rectangle content = new Rectangle(1, contentY,
                Math.max(1, width - 2), Math.max(1, statusY - contentY));
        Rectangle statusBounds = new Rectangle(1, statusY,
                Math.max(1, width - 2), Math.max(1, height - statusY - 1));
        Rectangle resize = new Rectangle(Math.max(0, width - 13),
                Math.max(0, height - 13), 12, 12);

        Rectangle[] groups = new Rectangle[GROUP_COUNT];
        int gridX = content.x + PAD;
        int gridY = content.y + 4;
        int gridW = Math.max(3, content.width - PAD * 2);
        int gridH = Math.max(3, content.height - 8);
        int gap = 3;
        int cellW = Math.max(1, (gridW - gap * 2) / 3);
        int cellH = Math.max(1, (gridH - gap * 2) / 3);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int x = gridX + col * (cellW + gap);
                int y = gridY + row * (cellH + gap);
                int w = col == 2 ? gridX + gridW - x : cellW;
                int h = row == 2 ? gridY + gridH - y : cellH;
                groups[index] = new Rectangle(x, y, Math.max(1, w), Math.max(1, h));
            }
        }

        int unitY = content.y + 31;
        int unitGap = 4;
        int unitW = Math.max(46, (content.width - PAD * 2 - unitGap * 2) / 3);
        Rectangle unitsSelectAll = new Rectangle(content.x + PAD, unitY, unitW, 23);
        Rectangle unitsClear = new Rectangle(unitsSelectAll.x + unitW + unitGap, unitY, unitW, 23);
        Rectangle unitsFocus = new Rectangle(unitsClear.x + unitW + unitGap, unitY,
                Math.max(1, content.x + content.width - PAD - (unitsClear.x + unitW + unitGap)), 23);

        int cameraY = content.y + 7;
        Rectangle speedDown = new Rectangle(content.x + 52, cameraY, 25, 22);
        Rectangle speedUp = new Rectangle(content.x + 128, cameraY, 25, 22);
        int buttonY = cameraY + 29;
        int cameraButtonGap = 5;
        int cameraButtonW = Math.max(70, (content.width - PAD * 2 - cameraButtonGap) / 2);
        Rectangle cameraFocus = new Rectangle(content.x + PAD, buttonY, cameraButtonW, 23);
        Rectangle cameraCenter = new Rectangle(cameraFocus.x + cameraButtonW + cameraButtonGap,
                buttonY,
                Math.max(1, content.x + content.width - PAD
                        - (cameraFocus.x + cameraButtonW + cameraButtonGap)), 23);

        return new LayoutSnapshot(
                title, tabs, content, statusBounds, resize, groups,
                unitsSelectAll, unitsClear, unitsFocus,
                speedDown, speedUp, cameraFocus, cameraCenter);
    }

    private static void drawCentered(Graphics2D g, String text, Rectangle bounds,
            Color color, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        String clipped = clipText(g, text, Math.max(1, bounds.width - 6));
        int x = bounds.x + Math.max(2, (bounds.width - fm.stringWidth(clipped)) / 2);
        int y = bounds.y + Math.max(fm.getAscent() + 1,
                (bounds.height - fm.getHeight()) / 2 + fm.getAscent());
        g.setColor(color);
        g.drawString(clipped, x, y);
    }

    private static String clipText(Graphics2D g, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int limit = Math.max(0, maxWidth - fm.stringWidth(suffix));
        String value = text;
        while (value.length() > 0 && fm.stringWidth(value) > limit) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private static final class LayoutSnapshot {
        final Rectangle title;
        final Rectangle[] tabs;
        final Rectangle content;
        final Rectangle status;
        final Rectangle resize;
        final Rectangle[] groups;
        final Rectangle unitsSelectAll;
        final Rectangle unitsClear;
        final Rectangle unitsFocus;
        final Rectangle speedDown;
        final Rectangle speedUp;
        final Rectangle cameraFocus;
        final Rectangle cameraCenter;

        LayoutSnapshot(Rectangle title, Rectangle[] tabs, Rectangle content,
                Rectangle status, Rectangle resize, Rectangle[] groups,
                Rectangle unitsSelectAll, Rectangle unitsClear, Rectangle unitsFocus,
                Rectangle speedDown, Rectangle speedUp,
                Rectangle cameraFocus, Rectangle cameraCenter) {
            this.title = title;
            this.tabs = tabs;
            this.content = content;
            this.status = status;
            this.resize = resize;
            this.groups = groups;
            this.unitsSelectAll = unitsSelectAll;
            this.unitsClear = unitsClear;
            this.unitsFocus = unitsFocus;
            this.speedDown = speedDown;
            this.speedUp = speedUp;
            this.cameraFocus = cameraFocus;
            this.cameraCenter = cameraCenter;
        }

        static LayoutSnapshot empty() {
            Rectangle empty = new Rectangle();
            return new LayoutSnapshot(
                    empty,
                    new Rectangle[] { empty, empty, empty },
                    empty, empty, empty,
                    new Rectangle[] { empty, empty, empty, empty, empty,
                            empty, empty, empty, empty },
                    empty, empty, empty, empty, empty, empty, empty);
        }
    }
}
