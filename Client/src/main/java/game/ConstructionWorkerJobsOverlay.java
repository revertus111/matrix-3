package game;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Player-facing Allowed Jobs editor for settlement workers.
 *
 * Persistent worker policy remains server-owned by SettlementWorkerState.
 */
public final class ConstructionWorkerJobsOverlay {

    private static final String[] JOB_KEYS = {
        "gather-wood", "gather-food", "gather-stone",
        "gather-basic-ore", "process-wood", "haul"
    };

    private static final String[] JOB_LABELS = {
        "Gather Wood", "Gather Food", "Gather Stone",
        "Gather Basic Ore", "Process Wood", "Haul"
    };

    private static final Color PANEL = new Color(19, 23, 29);
    private static final Color TEXT = new Color(236, 239, 243);
    private static final Color MUTED = new Color(165, 174, 185);
    private static final Color ACCENT = new Color(111, 174, 235);

    private static JWindow window;
    private static Window owner;
    private static JLabel titleLabel;
    private static JLabel statusLabel;
    private static JCheckBox[] jobChecks;

    private static int targetNpcIndex = -1;
    private static boolean selectionScope;
    private static int selectionCount;

    private ConstructionWorkerJobsOverlay() {
    }

    public static void showForNpc(int npcIndex, boolean applyToSelection, int workerCount) {
        targetNpcIndex = npcIndex;
        selectionScope = applyToSelection;
        selectionCount = Math.max(1, workerCount);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showNow();
            }
        });
    }

    private static void showNow() {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            return;
        }
        Window currentOwner = SwingUtilities.getWindowAncestor(canvas);
        if (currentOwner == null) {
            return;
        }
        if (window == null || owner != currentOwner) {
            buildWindow(currentOwner);
        }

        titleLabel.setText(selectionScope
                ? "ALLOWED JOBS - " + selectionCount + " SELECTED WORKERS"
                : "ALLOWED JOBS - WORKER");
        statusLabel.setText("Choose the complete Allowed Jobs set, then Apply.");
        for (JCheckBox check : jobChecks) {
            check.setSelected(false);
        }

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }

        int width = Math.min(340, Math.max(280, canvas.getWidth() - 24));
        int height = Math.min(382, Math.max(300, canvas.getHeight() - 24));
        int x = canvasLocation.x + Math.max(12, canvas.getWidth() - width - 18);
        int y = canvasLocation.y + Math.min(80, Math.max(12, canvas.getHeight() - height));
        window.setBounds(x, y, width, height);
        window.setVisible(true);
        window.toFront();
    }

    private static void buildWindow(Window currentOwner) {
        if (window != null) {
            window.dispose();
        }
        owner = currentOwner;
        window = new JWindow(owner);
        window.setFocusableWindowState(true);
        window.setAutoRequestFocus(false);

        JPanel root = new JPanel();
        root.setBackground(PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(73, 84, 98)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        titleLabel = new JLabel();
        titleLabel.setForeground(TEXT);
        titleLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(titleLabel);
        root.add(Box.createVerticalStrut(5));

        JLabel hint = new JLabel("Checked jobs become the complete autonomous policy.");
        hint.setForeground(MUTED);
        hint.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(hint);
        root.add(Box.createVerticalStrut(10));

        JPanel checks = new JPanel(new GridLayout(0, 1, 3, 3));
        checks.setOpaque(false);
        checks.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        jobChecks = new JCheckBox[JOB_KEYS.length];
        for (int i = 0; i < JOB_KEYS.length; i++) {
            JCheckBox check = new JCheckBox(JOB_LABELS[i]);
            check.setOpaque(false);
            check.setForeground(TEXT);
            jobChecks[i] = check;
            checks.add(check);
        }
        root.add(checks);
        root.add(Box.createVerticalStrut(10));

        JButton allOn = button("All On");
        JButton allOff = button("All Off");
        allOn.addActionListener(e -> setAll(true));
        allOff.addActionListener(e -> setAll(false));

        JPanel bulk = new JPanel(new GridLayout(1, 2, 6, 0));
        bulk.setOpaque(false);
        bulk.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        bulk.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        bulk.add(allOn);
        bulk.add(allOff);
        root.add(bulk);
        root.add(Box.createVerticalStrut(7));

        JButton apply = button("Apply Checked Jobs");
        JButton close = button("Close");
        apply.addActionListener(e -> applyJobs());
        close.addActionListener(e -> window.setVisible(false));

        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        actions.add(apply);
        actions.add(close);
        root.add(actions);
        root.add(Box.createVerticalStrut(9));

        statusLabel = new JLabel();
        statusLabel.setForeground(ACCENT);
        statusLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(statusLabel);

        window.getContentPane().setLayout(new BorderLayout());
        window.getContentPane().add(root, BorderLayout.CENTER);
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        return button;
    }

    private static void setAll(boolean selected) {
        for (JCheckBox check : jobChecks) {
            check.setSelected(selected);
        }
    }

    private static void applyJobs() {
        StringBuilder jobs = new StringBuilder();
        for (int i = 0; i < jobChecks.length; i++) {
            if (!jobChecks[i].isSelected()) {
                continue;
            }
            if (jobs.length() > 0) {
                jobs.append(',');
            }
            jobs.append(JOB_KEYS[i]);
        }
        if (jobs.length() == 0) {
            jobs.append("none");
        }

        String command = selectionScope
                ? "itembrowser settlement workerselectionjobsreplace " + jobs
                : "itembrowser settlement workernpcjobsreplace "
                        + targetNpcIndex + " " + jobs;
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        statusLabel.setText(error == null
                ? (selectionScope
                        ? "Applied to " + selectionCount + " selected workers."
                        : "Applied to worker.")
                : error);
    }
}
