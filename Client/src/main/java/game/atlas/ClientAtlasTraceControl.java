package game.atlas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import game.atlas.AtlasTraceControl.Command;
import game.atlas.AtlasTraceControl.RuntimeStatus;

/**
 * Small standalone cross-process control window for runtime trace sessions.
 */
public final class ClientAtlasTraceControl {

    private static final String TITLE = "Client Atlas Runtime Trace";

    private final AtlasWorkspace workspace;
    private JFrame frame;
    private JLabel runtimeValue;
    private JLabel sessionValue;
    private JLabel eventsValue;
    private JLabel droppedValue;
    private JLabel suppressedValue;
    private JLabel savedValue;
    private JLabel messageValue;
    private Timer refreshTimer;

    private ClientAtlasTraceControl(AtlasWorkspace workspace) {
        this.workspace = workspace;
    }

    public static void main(String[] args) {
        launch();
    }

    public static void launch() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                installDarkDefaults();
                try {
                    Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
                    AtlasWorkspace workspace = new AtlasWorkspace(clientRoot);
                    workspace.ensureLayout();
                    new ClientAtlasTraceControl(workspace).showWindow();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(), TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void showWindow() {
        frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(650, 350));
        frame.setSize(720, 400);
        frame.setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(buildStatusPanel(), BorderLayout.CENTER);
        root.add(buildButtons(), BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setVisible(true);

        refreshTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshStatus();
            }
        });
        refreshTimer.start();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (refreshTimer != null) {
                    refreshTimer.stop();
                }
            }
        });
        refreshStatus();
    }

    private JPanel buildStatusPanel() {
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        outer.setBorder(BorderFactory.createTitledBorder("Runtime trace status"));

        JPanel grid = new JPanel(new GridLayout(6, 2, 8, 7));
        runtimeValue = valueLabel("Checking...");
        sessionValue = valueLabel("-");
        eventsValue = valueLabel("0");
        droppedValue = valueLabel("0");
        suppressedValue = valueLabel("0");
        savedValue = valueLabel("-");
        addRow(grid, "Client runtime", runtimeValue);
        addRow(grid, "Session", sessionValue);
        addRow(grid, "Events", eventsValue);
        addRow(grid, "Dropped", droppedValue);
        addRow(grid, "Suppressed", suppressedValue);
        addRow(grid, "Last save", savedValue);

        messageValue = new JLabel("Tracing is off by default. Start the Matrix3 client, then start a named trace.");
        outer.add(grid, BorderLayout.CENTER);
        outer.add(messageValue, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JButton start = new JButton("Start Trace");
        start.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = JOptionPane.showInputDialog(frame, "Trace session name:", "trace");
                if (name != null) {
                    queue(Command.START, name);
                }
            }
        });
        panel.add(start);

        panel.add(commandButton("Stop", Command.STOP));
        panel.add(commandButton("Save", Command.SAVE));
        panel.add(commandButton("Stop + Save", Command.STOP_SAVE));

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshStatus();
            }
        });
        panel.add(refresh);

        JButton open = new JButton("Open Traces");
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openTraces();
            }
        });
        panel.add(open);
        return panel;
    }

    private JButton commandButton(String text, final Command command) {
        JButton button = new JButton(text);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                queue(command, null);
            }
        });
        return button;
    }

    private void queue(Command command, String sessionName) {
        try {
            String requestId = AtlasTraceControl.queue(workspace, command, sessionName);
            messageValue.setText("Queued " + command.name() + " request " + requestId + ".");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void refreshStatus() {
        try {
            RuntimeStatus status = AtlasTraceControl.readRuntimeStatus(workspace);
            if (status == null || !status.isRuntimePresent()) {
                runtimeValue.setText("OFFLINE / waiting for client bridge");
                sessionValue.setText("-");
                eventsValue.setText("0");
                droppedValue.setText("0");
                suppressedValue.setText("0");
                savedValue.setText("-");
                return;
            }
            runtimeValue.setText(status.isActive() ? "CONNECTED / TRACING" : "CONNECTED / IDLE");
            sessionValue.setText(empty(status.getSessionName(), "-"));
            eventsValue.setText(Long.toString(status.getEventCount()));
            droppedValue.setText(Long.toString(status.getDroppedCount()));
            suppressedValue.setText(Long.toString(status.getSuppressedCount()));
            savedValue.setText(empty(status.getLastSavedPath(), "-"));
            if (status.getLastError() != null && status.getLastError().length() > 0) {
                messageValue.setText("Runtime trace error: " + status.getLastError());
            }
        } catch (Exception ex) {
            runtimeValue.setText("STATUS ERROR");
            messageValue.setText(ex.getMessage());
        }
    }

    private void openTraces() {
        try {
            workspace.ensureLayout();
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Desktop open is not supported on this system.");
            }
            Desktop.getDesktop().open(workspace.tracesDirectory().toFile());
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static void addRow(JPanel panel, String name, JLabel value) {
        panel.add(new JLabel(name + ":"));
        panel.add(value);
    }

    private static String empty(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static void installDarkDefaults() {
        Color panel = new Color(43, 47, 53);
        Color foreground = new Color(225, 228, 232);
        UIManager.put("Panel.background", panel);
        UIManager.put("Label.foreground", foreground);
        UIManager.put("Button.background", new Color(57, 62, 70));
        UIManager.put("Button.foreground", foreground);
        UIManager.put("TitledBorder.titleColor", foreground);
    }
}
