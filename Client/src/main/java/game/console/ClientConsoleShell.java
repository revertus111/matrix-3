package game.console;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

public final class ClientConsoleShell extends JPanel {

    private static final long serialVersionUID = -7989293793942740080L;

    public static final int DEFAULT_CONSOLE_WIDTH = 360;
    public static final int MIN_CONSOLE_WIDTH = 300;
    public static final int MIN_GAME_WIDTH = 640;
    public static final int MIN_FRAME_HEIGHT = 640;

    private static final int RAIL_WIDTH = 48;
    private static final int DIVIDER_WIDTH = 5;

    private final JPanel dockContainer = new JPanel(new BorderLayout());
    private final JPanel panelHost = new JPanel(new BorderLayout());
    private final JPanel divider = new JPanel();
    private final JToggleButton consoleButton = new JToggleButton("C");
    private final JButton resetLayoutButton = new JButton("Reset Client Console Layout");

    private boolean consoleOpen = true;
    private int expandedConsoleWidth = DEFAULT_CONSOLE_WIDTH;
    private Runnable layoutChangedListener;
    private Runnable resetLayoutAction;

    public ClientConsoleShell(Applet gameApplet) {
        super(new BorderLayout());
        setBackground(ConsoleTheme.WINDOW);
        setOpaque(true);

        JPanel gameHost = new JPanel(new BorderLayout());
        gameHost.setBackground(java.awt.Color.BLACK);
        gameHost.add(gameApplet, BorderLayout.CENTER);
        add(gameHost, BorderLayout.CENTER);

        configureDivider();
        configureDock();
        add(dockContainer, BorderLayout.EAST);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (consoleOpen) {
                    applyConsoleWidth(expandedConsoleWidth, false);
                }
            }
        });

        applyConsoleState(false);
    }

    public static Dimension getMinimumFrameSize() {
        return new Dimension(MIN_GAME_WIDTH + MIN_CONSOLE_WIDTH + DIVIDER_WIDTH, MIN_FRAME_HEIGHT);
    }

    private void configureDock() {
        dockContainer.setBackground(ConsoleTheme.PANEL);
        dockContainer.setOpaque(true);
        dockContainer.add(divider, BorderLayout.WEST);

        JPanel dockBody = new JPanel(new BorderLayout());
        dockBody.setBackground(ConsoleTheme.PANEL);
        dockBody.setOpaque(true);
        dockBody.add(createRail(), BorderLayout.WEST);

        panelHost.setBackground(ConsoleTheme.PANEL);
        panelHost.setOpaque(true);
        panelHost.add(createPlaceholderPanel(), BorderLayout.CENTER);
        dockBody.add(panelHost, BorderLayout.CENTER);
        dockContainer.add(dockBody, BorderLayout.CENTER);
    }

    private JPanel createRail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(ConsoleTheme.RAIL);
        rail.setOpaque(true);
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, 1));
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ConsoleTheme.BORDER));

        JLabel brand = new JLabel("M3", SwingConstants.CENTER);
        brand.setFont(ConsoleTheme.SECTION_FONT);
        brand.setForeground(ConsoleTheme.MUTED_TEXT);
        brand.setAlignmentX(CENTER_ALIGNMENT);
        brand.setMaximumSize(new Dimension(RAIL_WIDTH, 38));
        brand.setPreferredSize(new Dimension(RAIL_WIDTH, 38));

        consoleButton.setToolTipText("Client Console - click again to collapse");
        consoleButton.setAlignmentX(CENTER_ALIGNMENT);
        consoleButton.setMaximumSize(new Dimension(RAIL_WIDTH, 44));
        consoleButton.setPreferredSize(new Dimension(RAIL_WIDTH, 44));
        ConsoleTheme.styleRailButton(consoleButton);
        consoleButton.addActionListener(e -> setConsoleOpen(consoleButton.isSelected()));

        rail.add(Box.createVerticalStrut(8));
        rail.add(brand);
        rail.add(Box.createVerticalStrut(8));
        rail.add(consoleButton);
        rail.add(Box.createVerticalGlue());
        return rail;
    }

    private JScrollPane createPlaceholderPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));

        JLabel title = new JLabel("CLIENT CONSOLE");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel status = new JLabel("Shell foundation");
        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.ACCENT);
        status.setAlignmentX(LEFT_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(status);
        content.add(Box.createVerticalStrut(18));
        content.add(createInfoCard(
                "Docking",
                "Placeholder shell only.",
                "Drag the left edge to resize.",
                "Use C to collapse or reopen."));
        content.add(Box.createVerticalStrut(12));
        content.add(createFocusCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkspaceCard());
        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scrollPane);
        return scrollPane;
    }

    private JPanel createInfoCard(String titleText, String... bodyLines) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(14, 14, 14, 14)));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);

        if (bodyLines.length > 0) {
            card.add(Box.createVerticalStrut(8));
        }
        for (int index = 0; index < bodyLines.length; index++) {
            JLabel body = new JLabel(bodyLines[index]);
            body.setFont(ConsoleTheme.SMALL_FONT);
            body.setForeground(ConsoleTheme.MUTED_TEXT);
            body.setAlignmentX(LEFT_ALIGNMENT);
            card.add(body);
            if (index + 1 < bodyLines.length) {
                card.add(Box.createVerticalStrut(3));
            }
        }
        return card;
    }

    private JPanel createFocusCard() {
        JPanel card = createInfoCard(
                "Focus test",
                "Click the field and type movement keys.",
                "Game input should stay inactive.",
                "Click the game to return control.");

        JTextField field = new JTextField();
        field.setToolTipText("Type here to test console keyboard focus");
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        field.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(field);
        card.add(Box.createVerticalStrut(10));
        card.add(field);
        return card;
    }

    private JPanel createWorkspaceCard() {
        JPanel card = createInfoCard(
                "Workspace",
                "Window and console geometry persist.",
                "Invalid monitor bounds are clamped.",
                "Reset restores known-good defaults.");

        resetLayoutButton.setAlignmentX(LEFT_ALIGNMENT);
        resetLayoutButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        ConsoleTheme.styleButton(resetLayoutButton);
        resetLayoutButton.addActionListener(e -> {
            if (resetLayoutAction != null) {
                resetLayoutAction.run();
            }
        });
        card.add(Box.createVerticalStrut(10));
        card.add(resetLayoutButton);
        return card;
    }

    private void configureDivider() {
        divider.setBackground(ConsoleTheme.BORDER);
        divider.setOpaque(true);
        divider.setPreferredSize(new Dimension(DIVIDER_WIDTH, 1));
        divider.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));

        MouseAdapter dragger = new MouseAdapter() {
            private int dragStartX;
            private int dragStartWidth;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!consoleOpen) {
                    return;
                }
                dragStartX = e.getXOnScreen();
                dragStartWidth = expandedConsoleWidth;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!consoleOpen) {
                    return;
                }
                int delta = dragStartX - e.getXOnScreen();
                applyConsoleWidth(dragStartWidth + delta, false);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (consoleOpen) {
                    notifyLayoutChanged();
                }
            }
        };
        divider.addMouseListener(dragger);
        divider.addMouseMotionListener(dragger);
    }

    public void setConsoleOpen(boolean open) {
        if (consoleOpen == open) {
            consoleButton.setSelected(open);
            return;
        }
        consoleOpen = open;
        applyConsoleState(true);
    }

    public boolean isConsoleOpen() {
        return consoleOpen;
    }

    public void setConsoleWidth(int width) {
        applyConsoleWidth(width, false);
    }

    public int getConsoleWidth() {
        return expandedConsoleWidth;
    }

    public String getActivePanelId() {
        return "shell";
    }

    public void setLayoutChangedListener(Runnable listener) {
        layoutChangedListener = listener;
    }

    public void setResetLayoutAction(Runnable action) {
        resetLayoutAction = action;
        resetLayoutButton.setEnabled(action != null);
    }

    private void applyConsoleState(boolean notify) {
        panelHost.setVisible(consoleOpen);
        divider.setCursor(consoleOpen
                ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
                : Cursor.getDefaultCursor());
        consoleButton.setSelected(consoleOpen);
        applyDockPreferredWidth();
        revalidate();
        repaint();
        if (notify) {
            notifyLayoutChanged();
        }
    }

    private void applyConsoleWidth(int requestedWidth, boolean notify) {
        expandedConsoleWidth = clampConsoleWidth(requestedWidth);
        if (consoleOpen) {
            applyDockPreferredWidth();
            revalidate();
            repaint();
        }
        if (notify) {
            notifyLayoutChanged();
        }
    }

    private int clampConsoleWidth(int requestedWidth) {
        int minimumClamped = Math.max(MIN_CONSOLE_WIDTH, requestedWidth);
        if (getWidth() <= 0) {
            return minimumClamped;
        }
        int maximum = Math.max(MIN_CONSOLE_WIDTH, getWidth() - MIN_GAME_WIDTH - DIVIDER_WIDTH);
        return Math.min(minimumClamped, maximum);
    }

    private void applyDockPreferredWidth() {
        int bodyWidth = consoleOpen ? clampConsoleWidth(expandedConsoleWidth) : RAIL_WIDTH;
        if (consoleOpen) {
            expandedConsoleWidth = bodyWidth;
        }
        dockContainer.setPreferredSize(new Dimension(bodyWidth + DIVIDER_WIDTH, 1));
    }

    private void notifyLayoutChanged() {
        if (layoutChangedListener != null) {
            layoutChangedListener.run();
        }
    }
}
