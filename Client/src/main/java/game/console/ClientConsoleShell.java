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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

public final class ClientConsoleShell extends JPanel {

    private static final long serialVersionUID = -7989293793942740080L;

    public static final int DEFAULT_CONSOLE_WIDTH = 360;
    public static final int MIN_CONSOLE_WIDTH = 300;
    public static final int MIN_GAME_WIDTH = 640;
    public static final int MIN_FRAME_HEIGHT = 640;

    public static final String PANEL_SHELL = "shell";
    public static final String PANEL_OWNER = "owner";
    public static final String PANEL_COMMANDS = "commands";
    public static final String PANEL_PLAYER = "player";
    public static final String PANEL_ITEMS = "items";
    public static final String PANEL_ATLAS = "atlas";
    public static final String PANEL_SETTINGS = "settings";

    private static final int RAIL_WIDTH = 48;
    private static final int DIVIDER_WIDTH = 5;

    private final JPanel dockContainer = new JPanel(new BorderLayout());
    private final JPanel panelHost = new JPanel(new BorderLayout());
    private final JPanel divider = new JPanel();
    private final JToggleButton consoleButton = new JToggleButton(ConsoleIcons.home());
    private final JToggleButton ownerButton = new JToggleButton(ConsoleIcons.owner());
    private final JToggleButton commandsButton = new JToggleButton(ConsoleIcons.commands());
    private final JToggleButton playerButton = new JToggleButton(ConsoleIcons.player());
    private final JToggleButton itemButton = new JToggleButton(ConsoleIcons.items());
    private final JToggleButton atlasButton = new JToggleButton(ConsoleIcons.atlas());
    private final JToggleButton settingsButton = new JToggleButton(ConsoleIcons.settings());

    private final DashboardPanel shellPanel;
    private JComponent ownerPanel;
    private JComponent commandsPanel;
    private JComponent playerPanel;
    private JComponent itemBrowserPanel;
    private JComponent atlasPanel;
    private JComponent settingsPanel;

    private boolean consoleOpen = true;
    private int expandedConsoleWidth = DEFAULT_CONSOLE_WIDTH;
    private String activePanelId = PANEL_SHELL;
    private Runnable layoutChangedListener;

    public ClientConsoleShell(Applet gameApplet) {
        super(new BorderLayout());
        setBackground(ConsoleTheme.WINDOW);
        setOpaque(true);

        JPanel gameHost = new JPanel(new BorderLayout());
        gameHost.setBackground(java.awt.Color.BLACK);
        gameHost.add(gameApplet, BorderLayout.CENTER);
        add(gameHost, BorderLayout.CENTER);

        shellPanel = new DashboardPanel();
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

        showActivePanel();
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

        configureRailButton(consoleButton, "Client Console", PANEL_SHELL);
        configureRailButton(ownerButton, "Owner", PANEL_OWNER);
        configureRailButton(commandsButton, "Commands", PANEL_COMMANDS);
        configureRailButton(playerButton, "Player", PANEL_PLAYER);
        configureRailButton(itemButton, "Item Browser", PANEL_ITEMS);
        configureRailButton(atlasButton, "Client Atlas", PANEL_ATLAS);
        configureRailButton(settingsButton, "Settings", PANEL_SETTINGS);

        rail.add(Box.createVerticalStrut(8));
        rail.add(brand);
        rail.add(Box.createVerticalStrut(8));
        rail.add(consoleButton);
        rail.add(Box.createVerticalStrut(4));
        rail.add(ownerButton);
        rail.add(Box.createVerticalStrut(4));
        rail.add(commandsButton);
        rail.add(Box.createVerticalStrut(4));
        rail.add(playerButton);
        rail.add(Box.createVerticalStrut(4));
        rail.add(itemButton);
        rail.add(Box.createVerticalStrut(4));
        rail.add(atlasButton);
        rail.add(Box.createVerticalStrut(4));
        rail.add(settingsButton);
        rail.add(Box.createVerticalGlue());
        return rail;
    }

    private void configureRailButton(JToggleButton button, String tooltip, String panelId) {
        button.setToolTipText(tooltip + " - click active panel again to collapse");
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setAlignmentX(CENTER_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setIconTextGap(0);
        button.setMaximumSize(new Dimension(RAIL_WIDTH, 44));
        button.setPreferredSize(new Dimension(RAIL_WIDTH, 44));
        ConsoleTheme.styleRailButton(button);
        button.addActionListener(e -> activatePanel(panelId));
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

    private void activatePanel(String panelId) {
        String normalizedPanelId = normalizePanelId(panelId);
        if (consoleOpen && normalizedPanelId.equals(activePanelId)) {
            setConsoleOpen(false);
            return;
        }

        activePanelId = normalizedPanelId;
        showActivePanel();
        if (!consoleOpen) {
            consoleOpen = true;
        }
        applyConsoleState(true);
    }

    private void showActivePanel() {
        panelHost.removeAll();
        panelHost.add(getOrCreatePanel(activePanelId), BorderLayout.CENTER);
        panelHost.revalidate();
        panelHost.repaint();
    }

    private JComponent getOrCreatePanel(String panelId) {
        if (PANEL_OWNER.equals(panelId)) {
            if (ownerPanel == null) {
                try {
                    ownerPanel = new OwnerPanel();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    ownerPanel = createPanelError("Owner panel failed to initialize.");
                }
            }
            return ownerPanel;
        }
        if (PANEL_COMMANDS.equals(panelId)) {
            if (commandsPanel == null) {
                try {
                    commandsPanel = new CommandsPanel();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    commandsPanel = createPanelError("Commands panel failed to initialize.");
                }
            }
            return commandsPanel;
        }
        if (PANEL_PLAYER.equals(panelId)) {
            if (playerPanel == null) {
                try {
                    playerPanel = new PlayerPanel();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    playerPanel = createPanelError("Player panel failed to initialize.");
                }
            }
            return playerPanel;
        }
        if (PANEL_ITEMS.equals(panelId)) {
            if (itemBrowserPanel == null) {
                try {
                    itemBrowserPanel = new ItemBrowserPanel();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    itemBrowserPanel = createPanelError("Item Browser failed to initialize.");
                }
            }
            return itemBrowserPanel;
        }
        if (PANEL_ATLAS.equals(panelId)) {
            if (atlasPanel == null) {
                try {
                    atlasPanel = new AtlasPanel();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    atlasPanel = createPanelError("Client Atlas panel failed to initialize.");
                }
            }
            return atlasPanel;
        }
        if (PANEL_SETTINGS.equals(panelId)) {
            if (settingsPanel == null) {
                try {
                    settingsPanel = new SettingsPanel();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    settingsPanel = createPanelError("Settings panel failed to initialize.");
                }
            }
            return settingsPanel;
        }
        return shellPanel;
    }

    private JComponent createPanelError(String message) {
        JPanel error = new JPanel(new BorderLayout());
        error.setBackground(ConsoleTheme.PANEL);
        error.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));

        JLabel label = new JLabel("<html><b>Panel unavailable</b><br>" + message + "</html>");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        error.add(label, BorderLayout.NORTH);
        return error;
    }

    private String normalizePanelId(String panelId) {
        if (PANEL_OWNER.equals(panelId)) {
            return PANEL_OWNER;
        }
        if (PANEL_COMMANDS.equals(panelId)) {
            return PANEL_COMMANDS;
        }
        if (PANEL_PLAYER.equals(panelId)) {
            return PANEL_PLAYER;
        }
        if (PANEL_ITEMS.equals(panelId)) {
            return PANEL_ITEMS;
        }
        if (PANEL_ATLAS.equals(panelId)) {
            return PANEL_ATLAS;
        }
        if (PANEL_SETTINGS.equals(panelId)) {
            return PANEL_SETTINGS;
        }
        return PANEL_SHELL;
    }

    public void setConsoleOpen(boolean open) {
        if (consoleOpen == open) {
            updateRailSelection();
            return;
        }
        consoleOpen = open;
        if (consoleOpen) {
            showActivePanel();
        }
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
        return activePanelId;
    }

    public void setActivePanelId(String panelId) {
        activePanelId = normalizePanelId(panelId);
        if (consoleOpen) {
            showActivePanel();
        }
        updateRailSelection();
    }

    public void setLayoutChangedListener(Runnable listener) {
        layoutChangedListener = listener;
    }

    public void setResetLayoutAction(Runnable action) {
        shellPanel.setResetLayoutAction(action);
    }

    private void applyConsoleState(boolean notify) {
        panelHost.setVisible(consoleOpen);
        divider.setCursor(consoleOpen
                ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
                : Cursor.getDefaultCursor());
        updateRailSelection();
        applyDockPreferredWidth();
        revalidate();
        repaint();
        if (notify) {
            notifyLayoutChanged();
        }
    }

    private void updateRailSelection() {
        consoleButton.setSelected(consoleOpen && PANEL_SHELL.equals(activePanelId));
        ownerButton.setSelected(consoleOpen && PANEL_OWNER.equals(activePanelId));
        commandsButton.setSelected(consoleOpen && PANEL_COMMANDS.equals(activePanelId));
        playerButton.setSelected(consoleOpen && PANEL_PLAYER.equals(activePanelId));
        itemButton.setSelected(consoleOpen && PANEL_ITEMS.equals(activePanelId));
        atlasButton.setSelected(consoleOpen && PANEL_ATLAS.equals(activePanelId));
        settingsButton.setSelected(consoleOpen && PANEL_SETTINGS.equals(activePanelId));
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
