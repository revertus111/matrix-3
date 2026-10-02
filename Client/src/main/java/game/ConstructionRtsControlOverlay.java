package game;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Lightweight player-facing RTS worker control overlay.
 *
 * V1 control groups are deliberately client-session scoped because the current
 * server bridge accepts transient runtime NPC indexes and resolves them into
 * stable worker IDs, but there is no server -> client group-membership sync
 * protocol yet. Never persist runtime NPC indexes as worker identity.
 */
public final class ConstructionRtsControlOverlay {

    private static final int GROUP_COUNT = 9;
    private static final long DOUBLE_TAP_MS = 400L;
    private static final long REFRESH_THROTTLE_MS = 100L;

    private static final Color PANEL = new Color(19, 23, 29);
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
            status = "Group " + (slot + 1) + " cleared (nothing selected).";
            return;
        }
        groupNpcIndexes[slot] = java.util.Arrays.copyOf(selected, selected.length);
        groupPlayerSelected[slot] = self;
        status = "Saved Group " + (slot + 1) + ": "
                + selected.length + " worker(s)" + (self ? " + self." : ".");
    }

    private static synchronized void clearGroup(int slot) {
        groupNpcIndexes[slot] = null;
        groupPlayerSelected[slot] = false;
        status = "Group " + (slot + 1) + " cleared.";
        refresh();
    }

    private static synchronized void recallGroup(int slot, boolean focusCamera) {
        int[] saved = groupNpcIndexes[slot];
        boolean self = groupPlayerSelected[slot];
        if ((saved == null || saved.length == 0) && !self) {
            status = "Group " + (slot + 1) + " is empty.";
            return;
        }

        int[] copy = saved == null ? new int[0]
                : java.util.Arrays.copyOf(saved, saved.length);
        boolean applied = ConstructionRadialSelection.applyControlGroupSelection(copy, self);
        if (!applied) {
            status = "Group " + (slot + 1)
                    + " has no active units in this settlement session.";
            return;
        }

        status = "Recalled Group " + (slot + 1) + ".";
        if (focusCamera) {
            int[] center = ConstructionRadialSelection.getCommittedSelectionCenterLocalTile();
            if (center != null
                    && ConstructionBuildCamera.focusRtsAtLocalTileFromControlGroup(
                            center[0], center[1])) {
                status = "Recalled + focused Group " + (slot + 1) + ".";
            }
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

        int selectedWorkers = ConstructionRadialSelection.getCommittedWorkerCount();
        int totalWorkers = ConstructionRadialSelection.getTotalSettlementWorkerCount();
        selectedLabel.setText("Selected: " + selectedWorkers + " / " + totalWorkers
                + " workers"
                + (ConstructionRadialSelection.isLocalPlayerSelected() ? " + self" : ""));

        synchronized (ConstructionRtsControlOverlay.class) {
            for (int i = 0; i < GROUP_COUNT; i++) {
                int count = groupNpcIndexes[i] == null ? 0 : groupNpcIndexes[i].length;
                groupLabels[i].setText((i + 1) + ": " + count + " worker"
                        + (count == 1 ? "" : "s")
                        + (groupPlayerSelected[i] ? " + self" : ""));
            }
        }
        statusLabel.setText(status);

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            hideNow();
            return;
        }

        int width = Math.min(270, Math.max(230, canvas.getWidth() - 24));
        int height = Math.min(360, Math.max(300, canvas.getHeight() - 24));
        int x = canvasLocation.x + Math.max(12, canvas.getWidth() - width - 18);
        int y = canvasLocation.y + Math.min(
                Math.max(180, canvas.getHeight() / 4),
                Math.max(12, canvas.getHeight() - height - 12));
        window.setBounds(x, y, width, height);
        if (!window.isVisible()) {
            window.setVisible(true);
        }
        window.toFront();
    }

    private static void buildWindow(Window currentOwner) {
        if (window != null) {
            window.dispose();
        }
        owner = currentOwner;
        window = new JWindow(owner);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);

        JPanel root = new JPanel();
        root.setBackground(PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(9, 9, 9, 9)));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("RTS WORKER CONTROL");
        title.setForeground(TEXT);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(4));

        selectedLabel = new JLabel();
        selectedLabel.setForeground(TEXT);
        selectedLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(selectedLabel);
        root.add(Box.createVerticalStrut(6));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JButton all = button("Select All");
        JButton clear = button("Clear");
        all.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConstructionRadialSelection.selectAllWorkers();
                status = "Selected all active settlement workers.";
                refresh();
            }
        });
        clear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConstructionRadialSelection.clearCommittedRadius();
                status = "Selection cleared.";
                refresh();
            }
        });
        actions.add(all);
        actions.add(clear);
        root.add(actions);
        root.add(Box.createVerticalStrut(7));

        JPanel groups = new JPanel(new GridLayout(GROUP_COUNT, 1, 0, 2));
        groups.setOpaque(false);
        groups.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        groupLabels = new JLabel[GROUP_COUNT];

        for (int i = 0; i < GROUP_COUNT; i++) {
            final int slot = i;
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);

            JLabel label = new JLabel();
            label.setForeground(TEXT);
            label.setPreferredSize(new Dimension(108, 22));
            groupLabels[i] = label;
            row.add(label, BorderLayout.CENTER);

            JPanel rowActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
            rowActions.setOpaque(false);
            JButton recall = button("Go");
            JButton save = button("Set");
            JButton remove = button("X");
            recall.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    recallGroup(slot, false);
                    refresh();
                }
            });
            save.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    saveGroup(slot);
                    refresh();
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
        root.add(groups);
        root.add(Box.createVerticalStrut(6));

        statusLabel = new JLabel();
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(statusLabel);

        window.setContentPane(root);
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.setForeground(TEXT);
        button.setBackground(BUTTON);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        return button;
    }

    private static void hideNow() {
        if (window != null) {
            window.setVisible(false);
        }
    }
}
