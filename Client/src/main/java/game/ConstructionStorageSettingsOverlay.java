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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Player-facing policy controls for one physical settlement storage chest.
 *
 * Server state remains authoritative. Buttons are idempotent policy commands,
 * so this overlay does not mirror or own persistent chest state.
 */
public final class ConstructionStorageSettingsOverlay {

    private static final Color PANEL = new Color(19, 23, 29);
    private static final Color TEXT = new Color(236, 239, 243);
    private static final Color MUTED = new Color(165, 174, 185);
    private static final Color ACCENT = new Color(111, 174, 235);

    private static JWindow window;
    private static Window owner;
    private static JLabel targetLabel;
    private static JLabel statusLabel;

    private static int worldX;
    private static int worldY;
    private static int plane;

    private ConstructionStorageSettingsOverlay() {
    }

    public static boolean isVisible() {
        return window != null && window.isVisible();
    }

    public static void showForChest(int x, int y, int targetPlane) {
        worldX = x;
        worldY = y;
        plane = targetPlane;
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

        targetLabel.setText("Chest @ " + worldX + ", " + worldY + ", " + plane);
        statusLabel.setText("Choose a policy. Use Status to inspect the server-owned state.");

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }

        int width = Math.min(360, Math.max(300, canvas.getWidth() - 24));
        int height = Math.min(430, Math.max(360, canvas.getHeight() - 24));
        int x = canvasLocation.x + Math.max(12, canvas.getWidth() - width - 18);
        int y = canvasLocation.y + Math.min(70, Math.max(12, canvas.getHeight() - height));
        window.setBounds(x, y, width, height);
        window.setVisible(true);
        window.toFront();
        queue("status", null, "Storage policy status requested.");
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

        JLabel title = new JLabel("STORAGE SETTINGS");
        title.setForeground(TEXT);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(4));

        targetLabel = new JLabel();
        targetLabel.setForeground(MUTED);
        targetLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        root.add(targetLabel);
        root.add(Box.createVerticalStrut(10));

        root.add(sectionLabel("Mode"));
        JPanel modes = row(4);
        modes.add(action("Storage", "mode", "storage"));
        modes.add(action("Supply", "mode", "supply"));
        modes.add(action("Request", "mode", "request"));
        modes.add(action("Buffer", "mode", "buffer"));
        root.add(modes);
        root.add(Box.createVerticalStrut(8));

        root.add(sectionLabel("Logistics Priority"));
        JPanel priority = row(3);
        priority.add(action("Lower", "priority", "down"));
        priority.add(action("Reset", "priority", "reset"));
        priority.add(action("Higher", "priority", "up"));
        root.add(priority);
        root.add(Box.createVerticalStrut(8));

        root.add(sectionLabel("Worker Access"));
        JPanel deposit = row(2);
        deposit.add(action("Deposit ON", "deposit", "on"));
        deposit.add(action("Deposit OFF", "deposit", "off"));
        root.add(deposit);
        JPanel withdraw = row(2);
        withdraw.add(action("Withdraw ON", "withdraw", "on"));
        withdraw.add(action("Withdraw OFF", "withdraw", "off"));
        root.add(withdraw);
        root.add(Box.createVerticalStrut(8));

        root.add(sectionLabel("Item Filter"));
        JPanel filters = row(3);
        filters.add(action("Allow Any", "filter", "any"));
        filters.add(action("Logs Only", "filter", "logs"));
        filters.add(action("Planks Only", "filter", "planks"));
        root.add(filters);
        root.add(Box.createVerticalStrut(10));

        JPanel actions = row(2);
        actions.add(action("Status", "status", null));
        JButton close = button("Close");
        close.addActionListener(e -> closeNow());
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

    private static void closeNow() {
        JWindow current = window;
        window = null;
        owner = null;
        targetLabel = null;
        statusLabel = null;
        if (current != null) {
            current.setVisible(false);
            current.dispose();
        }
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel row(int columns) {
        JPanel panel = new JPanel(new GridLayout(1, columns, 5, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return panel;
    }

    private static JButton action(String text, String setting, String value) {
        JButton button = button(text);
        button.addActionListener(e -> queue(setting, value, text + " queued."));
        return button;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        return button;
    }

    private static void queue(String setting, String value, String success) {
        StringBuilder command = new StringBuilder(
                "itembrowser settlement storageconfig ")
                .append(worldX).append(' ')
                .append(worldY).append(' ')
                .append(plane).append(' ')
                .append(setting);
        if (value != null && value.length() > 0) {
            command.append(' ').append(value);
        }
        String error = ClientConsoleBridge.queueConsoleCommand(command.toString());
        statusLabel.setText(error == null ? success : error);
    }
}
