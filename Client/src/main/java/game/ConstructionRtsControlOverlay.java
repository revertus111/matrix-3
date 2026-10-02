package game;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Lightweight player-facing RTS worker control overlay.
 *
 * V1 control groups are deliberately client-session scoped because the current
 * server bridge accepts transient runtime NPC indexes and resolves them into
 * stable worker IDs, but there is no server -> client group-membership sync
 * protocol yet. Never persist runtime NPC indexes as worker identity.
 *
 * The overlay behaves like an in-game panel rather than a fixed pop-up:
 * it stays inside the Matrix3 canvas, has a draggable title bar, supports
 * bottom-right resizing, and scrolls its group list instead of clipping rows.
 */
public final class ConstructionRtsControlOverlay {

    private static final int GROUP_COUNT = 9;
    private static final long DOUBLE_TAP_MS = 400L;
    private static final long REFRESH_THROTTLE_MS = 100L;

    private static final int DEFAULT_WIDTH = 270;
    private static final int DEFAULT_HEIGHT = 330;
    private static final int MIN_WIDTH = 225;
    private static final int MIN_HEIGHT = 235;

    private static final Color PANEL = new Color(19, 23, 29);
    private static final Color PANEL_ALT = new Color(25, 30, 37);
    private static final Color TEXT = new Color(236, 239, 243);
    private static final Color MUTED = new Color(165, 174, 185);
    private static final Color BUTTON = new Color(44, 51, 62);
    private static final Color BORDER = new Color(73, 84, 98);

    private static final int[][] groupNpcIndexes = new int[GROUP_COUNT][];
    private static final boolean[] groupPlayerSelected = new boolean[GROUP_COUNT];
    private static final boolean[] numberKeyDown = new boolean[GROUP_COUNT];

    private static JWindow window;
    private static Window owner;
    private static JLabel selectedLabel;
    private static JLabel statusLabel;
    private static JLabel[] groupLabels;

    private static volatile long lastRefreshRequestMillis;
    private static volatile int lastRecallSlot = -1;
    private static volatile long lastRecallMillis;
    private static volatile String status = "Ctrl+1-9 saves | 1-9 recalls | double-tap focuses";

    // User-controlled panel geometry is kept for the client session.
    private static boolean boundsInitialized;
    private static int overlayX;
    private static int overlayY;
    private static int overlayWidth = DEFAULT_WIDTH;
    private static int overlayHeight = DEFAULT_HEIGHT;

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
        refresh();
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
        status = "Ctrl+1-9 saves | 1-9 recalls | double-tap focuses";
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
            status = "Group " + (slot + 1) + " cleared - nothing selected.";
        } else {
            groupNpcIndexes[slot] = java.util.Arrays.copyOf(selected, selected.length);
            groupPlayerSelected[slot] = self;
            status = "Saved Group " + (slot + 1) + ": "
                    + selected.length + " worker(s)" + (self ? " + self." : ".");
        }
        refreshGroupLabelsNow();
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
        status = "Group " + (slot + 1) + " removed.";
        refreshGroupLabelsNow();
        refreshStatusNow();
    }

    private static synchronized void recallGroup(int slot, boolean focusCamera) {
        int[] saved = groupNpcIndexes[slot];
        boolean self = groupPlayerSelected[slot];
        if ((saved == null || saved.length == 0) && !self) {
            status = "Group " + (slot + 1) + " is empty.";
            refreshStatusNow();
            return;
        }

        int[] copy = saved == null ? new int[0]
                : java.util.Arrays.copyOf(saved, saved.length);
        boolean applied = ConstructionRadialSelection.applyControlGroupSelection(copy, self);
        if (!applied) {
            status = "Group " + (slot + 1)
                    + " has no active units in this settlement session.";
            refreshStatusNow();
            return;
        }

        status = "Recalled Group " + (slot + 1) + ".";
        if (focusCamera) {
            focusCurrentSelection(slot);
        }
        refreshStatusNow();
    }

    private static void focusCurrentSelection(int slot) {
        int[] center = ConstructionRadialSelection.getCommittedSelectionCenterLocalTile();
        if (center != null
                && ConstructionBuildCamera.focusRtsAtLocalTileFromControlGroup(
                        center[0], center[1])) {
            status = "Group " + (slot + 1) + " selected - camera moving to group.";
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

        Window currentOwner = SwingUtilities.getWindowAncestor(canvas);
        if (currentOwner == null) {
            hideNow();
            return;
        }
        if (window == null || owner != currentOwner) {
            buildWindow(currentOwner);
        }

        refreshSelectionCountNow();
        refreshGroupLabelsNow();
        refreshStatusNow();

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
            overlayWidth = Math.min(DEFAULT_WIDTH, Math.max(MIN_WIDTH, canvasBounds.width - 24));
            overlayHeight = Math.min(DEFAULT_HEIGHT, Math.max(MIN_HEIGHT, canvasBounds.height - 24));
            overlayX = canvasBounds.x + 14;
            overlayY = canvasBounds.y + Math.min(90, Math.max(12, canvasBounds.height - overlayHeight - 12));
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
    }

    private static void buildWindow(Window currentOwner) {
        if (window != null) {
            window.dispose();
        }
        owner = currentOwner;
        window = new JWindow(owner);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);

        JPanel root = new JPanel(new BorderLayout(0, 5));
        root.setBackground(PANEL);
        root.setBorder(BorderFactory.createLineBorder(BORDER));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(PANEL_ALT);
        titleBar.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
        JLabel title = new JLabel("RTS WORKER CONTROL");
        title.setForeground(TEXT);
        titleBar.add(title, BorderLayout.CENTER);
        installTitleDrag(titleBar);
        installTitleDrag(title);
        root.add(titleBar, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        selectedLabel = new JLabel();
        selectedLabel.setForeground(TEXT);
        selectedLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        body.add(selectedLabel);
        body.add(Box.createVerticalStrut(5));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JButton all = button("Select All", 74);
        JButton clear = button("Clear", 52);
        all.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConstructionRadialSelection.selectAllWorkers();
                status = "Selected all active settlement workers.";
                refreshSelectionCountNow();
                refreshStatusNow();
            }
        });
        clear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConstructionRadialSelection.clearCommittedRadius();
                status = "Selection cleared.";
                refreshSelectionCountNow();
                refreshStatusNow();
            }
        });
        actions.add(all);
        actions.add(clear);
        body.add(actions);
        body.add(Box.createVerticalStrut(5));

        JPanel groups = new JPanel(new GridLayout(GROUP_COUNT, 1, 0, 2));
        groups.setBackground(PANEL);
        groupLabels = new JLabel[GROUP_COUNT];

        for (int i = 0; i < GROUP_COUNT; i++) {
            final int slot = i;
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setBackground((i & 1) == 0 ? PANEL : PANEL_ALT);
            row.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 2));

            JLabel label = new JLabel();
            label.setForeground(TEXT);
            groupLabels[i] = label;
            row.add(label, BorderLayout.CENTER);

            JPanel rowActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
            rowActions.setOpaque(false);
            JButton recall = button("Go", 34);
            JButton save = button("Set", 36);
            JButton remove = button("X", 28);

            recall.setToolTipText("Select this group and move the RTS camera to it.");
            save.setToolTipText("Replace this slot with the current selection.");
            remove.setToolTipText("Remove this saved group.");

            recall.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // UI Go always recalls AND smoothly focuses the camera.
                    recallGroup(slot, true);
                    refreshSelectionCountNow();
                }
            });
            save.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    saveGroup(slot);
                }
            });
            remove.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    clearGroup(slot);
                }
            });

            rowActions.add(recall);
            rowActions.add(save);
            rowActions.add(remove);
            row.add(rowActions, BorderLayout.EAST);
            groups.add(row);
        }

        JScrollPane groupScroll = new JScrollPane(groups);
        groupScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        groupScroll.setBackground(PANEL);
        groupScroll.getViewport().setBackground(PANEL);
        groupScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        groupScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        body.add(groupScroll);

        root.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(5, 0));
        footer.setBackground(PANEL_ALT);
        footer.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 3));
        statusLabel = new JLabel();
        statusLabel.setForeground(MUTED);
        footer.add(statusLabel, BorderLayout.CENTER);

        JLabel resizeGrip = new JLabel("///");
        resizeGrip.setForeground(MUTED);
        resizeGrip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        resizeGrip.setToolTipText("Drag to resize");
        installResizeGrip(resizeGrip);
        footer.add(resizeGrip, BorderLayout.EAST);

        root.add(footer, BorderLayout.SOUTH);
        window.setContentPane(root);
    }

    private static void installTitleDrag(java.awt.Component component) {
        MouseAdapter drag = new MouseAdapter() {
            private Point pressScreen;
            private Point startWindow;

            @Override
            public void mousePressed(MouseEvent e) {
                if (window == null || e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                pressScreen = e.getLocationOnScreen();
                startWindow = window.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (window == null || pressScreen == null || startWindow == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int x = startWindow.x + now.x - pressScreen.x;
                int y = startWindow.y + now.y - pressScreen.y;
                setOverlayBoundsFromUser(x, y, window.getWidth(), window.getHeight());
            }
        };
        component.addMouseListener(drag);
        component.addMouseMotionListener(drag);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private static void installResizeGrip(java.awt.Component component) {
        MouseAdapter resize = new MouseAdapter() {
            private Point pressScreen;
            private Dimension startSize;

            @Override
            public void mousePressed(MouseEvent e) {
                if (window == null || e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                pressScreen = e.getLocationOnScreen();
                startSize = window.getSize();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (window == null || pressScreen == null || startSize == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int width = startSize.width + now.x - pressScreen.x;
                int height = startSize.height + now.y - pressScreen.y;
                setOverlayBoundsFromUser(window.getX(), window.getY(), width, height);
            }
        };
        component.addMouseListener(resize);
        component.addMouseMotionListener(resize);
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
    }

    private static Rectangle clampBounds(Rectangle desired, Rectangle canvas) {
        int maxWidth = Math.max(1, canvas.width - 8);
        int maxHeight = Math.max(1, canvas.height - 8);
        int width = Math.min(maxWidth, Math.max(Math.min(MIN_WIDTH, maxWidth), desired.width));
        int height = Math.min(maxHeight, Math.max(Math.min(MIN_HEIGHT, maxHeight), desired.height));

        int minX = canvas.x + 4;
        int minY = canvas.y + 4;
        int maxX = canvas.x + canvas.width - width - 4;
        int maxY = canvas.y + canvas.height - height - 4;
        int x = Math.max(minX, Math.min(desired.x, Math.max(minX, maxX)));
        int y = Math.max(minY, Math.min(desired.y, Math.max(minY, maxY)));
        return new Rectangle(x, y, width, height);
    }

    private static void refreshSelectionCountNow() {
        if (selectedLabel == null) {
            return;
        }
        int selectedWorkers = ConstructionRadialSelection.getCommittedWorkerCount();
        int totalWorkers = ConstructionRadialSelection.getTotalSettlementWorkerCount();
        selectedLabel.setText("Selected: " + selectedWorkers + " / " + totalWorkers
                + " workers"
                + (ConstructionRadialSelection.isLocalPlayerSelected() ? " + self" : ""));
    }

    private static synchronized void refreshGroupLabelsNow() {
        if (groupLabels == null) {
            return;
        }
        for (int i = 0; i < GROUP_COUNT; i++) {
            if (groupLabels[i] == null) {
                continue;
            }
            int count = groupNpcIndexes[i] == null ? 0 : groupNpcIndexes[i].length;
            groupLabels[i].setText((i + 1) + ": " + count + " worker"
                    + (count == 1 ? "" : "s")
                    + (groupPlayerSelected[i] ? " + self" : ""));
        }
    }

    private static void refreshStatusNow() {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(status);
        statusLabel.setToolTipText(status);
    }

    private static JButton button(String text, int width) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.setForeground(TEXT);
        button.setBackground(BUTTON);
        button.setPreferredSize(new Dimension(width, 22));
        button.setMinimumSize(new Dimension(width, 22));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));
        return button;
    }

    private static void hideNow() {
        if (window != null) {
            window.setVisible(false);
        }
    }
}
