package game.console;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Consolidated developer/testing workspace.
 *
 * Primary and secondary navigation is owned by the Test Console flyout menu.
 * This panel only owns lazy tool creation and the active content surface.
 */
public final class TestConsolePanel extends JPanel {

    private static final long serialVersionUID = -3097526505843407244L;

    public static final String TOOL_CON_REVAMP = "conRevamp";
    public static final String TOOL_RAIL_STUDIO = "railStudio";
    public static final String TOOL_RAIL_CLASSIFIER = "railClassifier";
    public static final String TOOL_OBJECT_EXPLORER = "objectExplorer";
    public static final String TOOL_LIVE_INSPECT = "liveInspect";
    public static final String TOOL_CONSTRUCTION = "construction";
    public static final String TOOL_PLAYER = "player";
    public static final String TOOL_ITEMS = "items";
    public static final String TOOL_INTERFACES = "interfaces";
    public static final String TOOL_VISUAL_EXPLORER = "visualExplorer";
    public static final String TOOL_N64 = "n64";
    public static final String TOOL_ATLAS = "atlas";
    public static final String TOOL_BOSS_RESEARCH = "bossResearch";
    public static final String TOOL_PORTS_UI = "portsUi";

    public static final String SECTION_SETTLEMENT = "Settlement";
    public static final String SECTION_WORKERS = "Workers";
    public static final String SECTION_PRODUCTION = "Production";
    public static final String SECTION_CONVEYORS = "Conveyors";
    public static final String SECTION_DEBUG = "Debug";
    public static final String SECTION_TOOLS = "Tools";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentHost = new JPanel(cardLayout);
    private final Set<String> addedToolIds = new HashSet<String>();

    private String selectedToolId = TOOL_CON_REVAMP;
    private String selectedConRevampSection = SECTION_SETTLEMENT;

    private JComponent conRevampPanel;
    private JTabbedPane conRevampSectionTabs;
    private JComponent railStudioPanel;
    private JComponent railClassifierPanel;
    private JComponent objectExplorerPanel;
    private JComponent liveInspectPanel;
    private JComponent constructionPanel;
    private JComponent playerPanel;
    private JComponent itemPanel;
    private JComponent interfacePanel;
    private JComponent visualExplorerPanel;
    private JComponent n64Panel;
    private JComponent atlasPanel;
    private JComponent bossResearchPanel;
    private JComponent portsUiPanel;

    public TestConsolePanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);

        contentHost.setBackground(ConsoleTheme.PANEL);
        contentHost.setOpaque(true);
        contentHost.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        add(contentHost, BorderLayout.CENTER);

        showTool(selectedToolId, selectedConRevampSection);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ConsoleTheme.PANEL);
        header.setOpaque(true);
        header.setBorder(ConsoleTheme.panelPadding(16, 18, 8, 18));

        JPanel labels = new JPanel();
        labels.setLayout(new javax.swing.BoxLayout(labels, javax.swing.BoxLayout.Y_AXIS));
        labels.setOpaque(false);
        labels.add(ConsoleTheme.titleLabel("TEST CONSOLE"));
        labels.add(javax.swing.Box.createVerticalStrut(3));
        labels.add(ConsoleTheme.subtitleLabel("Matrix3 developer tools and runtime test workspaces"));
        header.add(labels, BorderLayout.CENTER);
        return header;
    }

    public void showTool(String toolId, String sectionId) {
        String normalized = normalizeToolId(toolId);
        JComponent component = getOrCreate(normalized);
        if (component == null) {
            return;
        }

        if (!addedToolIds.contains(normalized)) {
            contentHost.add(component, normalized);
            addedToolIds.add(normalized);
        }

        selectedToolId = normalized;
        cardLayout.show(contentHost, normalized);

        if (TOOL_CON_REVAMP.equals(normalized)) {
            if (sectionId != null && sectionId.trim().length() > 0) {
                selectedConRevampSection = normalizeConRevampSection(sectionId);
            }
            selectConRevampSection(selectedConRevampSection);
        }

        contentHost.revalidate();
        contentHost.repaint();
    }

    public String getSelectedToolId() {
        return selectedToolId;
    }

    public String getSelectedConRevampSection() {
        return selectedConRevampSection;
    }

    private JComponent getOrCreate(String toolId) {
        try {
            if (TOOL_CON_REVAMP.equals(toolId)) {
                if (conRevampPanel == null) {
                    conRevampPanel = new ConstructionRevampTestPanel();
                    conRevampSectionTabs = findTabbedPane(conRevampPanel);
                    hideConRevampTabStrip(conRevampSectionTabs);
                }
                return conRevampPanel;
            }
            if (TOOL_RAIL_STUDIO.equals(toolId)) {
                if (railStudioPanel == null) {
                    railStudioPanel = new RailAssemblyStudioPanel();
                }
                return railStudioPanel;
            }
            if (TOOL_RAIL_CLASSIFIER.equals(toolId)) {
                if (railClassifierPanel == null) {
                    railClassifierPanel = new RailKitClassifierPanel();
                }
                return railClassifierPanel;
            }
            if (TOOL_OBJECT_EXPLORER.equals(toolId)) {
                if (objectExplorerPanel == null) {
                    objectExplorerPanel = new ObjectExplorerPanel();
                }
                return objectExplorerPanel;
            }
            if (TOOL_LIVE_INSPECT.equals(toolId)) {
                if (liveInspectPanel == null) {
                    liveInspectPanel = new LiveInspectPanel();
                }
                return liveInspectPanel;
            }
            if (TOOL_CONSTRUCTION.equals(toolId)) {
                if (constructionPanel == null) {
                    constructionPanel = new ConstructionEditorPanel();
                }
                return constructionPanel;
            }
            if (TOOL_PLAYER.equals(toolId)) {
                if (playerPanel == null) {
                    playerPanel = new PlayerPanel();
                }
                return playerPanel;
            }
            if (TOOL_ITEMS.equals(toolId)) {
                if (itemPanel == null) {
                    itemPanel = new ItemBrowserPanel();
                }
                return itemPanel;
            }
            if (TOOL_INTERFACES.equals(toolId)) {
                if (interfacePanel == null) {
                    interfacePanel = new InterfaceEditorPanel();
                }
                return interfacePanel;
            }
            if (TOOL_VISUAL_EXPLORER.equals(toolId)) {
                if (visualExplorerPanel == null) {
                    visualExplorerPanel = new VisualExplorerPanel(new Runnable() {
                        @Override
                        public void run() {
                            showTool(TOOL_INTERFACES, null);
                        }
                    });
                }
                return visualExplorerPanel;
            }
            if (TOOL_N64.equals(toolId)) {
                if (n64Panel == null) {
                    n64Panel = new N64Panel();
                }
                return n64Panel;
            }
            if (TOOL_ATLAS.equals(toolId)) {
                if (atlasPanel == null) {
                    atlasPanel = new AtlasWorkspacePanel();
                }
                return atlasPanel;
            }
            if (TOOL_BOSS_RESEARCH.equals(toolId)) {
                if (bossResearchPanel == null) {
                    bossResearchPanel = new BossResearchPanel();
                }
                return bossResearchPanel;
            }
            if (TOOL_PORTS_UI.equals(toolId)) {
                if (portsUiPanel == null) {
                    portsUiPanel = new PortsOverlayTestPanel();
                }
                return portsUiPanel;
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return createError("Test workspace failed to initialize.");
        }
        return createError("Unknown Test Console workspace.");
    }

    private void selectConRevampSection(String sectionTitle) {
        if (conRevampSectionTabs == null) {
            return;
        }
        int index = conRevampSectionTabs.indexOfTab(sectionTitle);
        if (index >= 0) {
            conRevampSectionTabs.setSelectedIndex(index);
        }
    }

    private void hideConRevampTabStrip(JTabbedPane tabs) {
        if (tabs == null) {
            return;
        }
        tabs.setFocusable(false);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override
            protected int calculateTabAreaHeight(int tabPlacement,
                    int horizRunCount, int maxTabHeight) {
                return 0;
            }

            @Override
            protected void paintTabArea(Graphics g, int tabPlacement,
                    int selectedIndex) {
                // Navigation is owned by TestConsoleFlyoutMenu.
            }
        });
    }

    private JTabbedPane findTabbedPane(Component component) {
        if (component instanceof JTabbedPane) {
            return (JTabbedPane) component;
        }
        if (component instanceof Container) {
            Component[] children = ((Container) component).getComponents();
            for (Component child : children) {
                JTabbedPane found = findTabbedPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String normalizeToolId(String toolId) {
        if (TOOL_RAIL_STUDIO.equals(toolId)
                || TOOL_RAIL_CLASSIFIER.equals(toolId)
                || TOOL_OBJECT_EXPLORER.equals(toolId)
                || TOOL_LIVE_INSPECT.equals(toolId)
                || TOOL_CONSTRUCTION.equals(toolId)
                || TOOL_PLAYER.equals(toolId)
                || TOOL_ITEMS.equals(toolId)
                || TOOL_INTERFACES.equals(toolId)
                || TOOL_VISUAL_EXPLORER.equals(toolId)
                || TOOL_N64.equals(toolId)
                || TOOL_ATLAS.equals(toolId)
                || TOOL_BOSS_RESEARCH.equals(toolId)
                || TOOL_PORTS_UI.equals(toolId)) {
            return toolId;
        }
        return TOOL_CON_REVAMP;
    }

    private String normalizeConRevampSection(String sectionId) {
        if (SECTION_WORKERS.equalsIgnoreCase(sectionId)) return SECTION_WORKERS;
        if (SECTION_PRODUCTION.equalsIgnoreCase(sectionId)) return SECTION_PRODUCTION;
        if (SECTION_CONVEYORS.equalsIgnoreCase(sectionId)) return SECTION_CONVEYORS;
        if (SECTION_DEBUG.equalsIgnoreCase(sectionId)) return SECTION_DEBUG;
        if (SECTION_TOOLS.equalsIgnoreCase(sectionId)) return SECTION_TOOLS;
        return SECTION_SETTLEMENT;
    }

    private JComponent createError(String message) {
        JPanel error = new JPanel(new BorderLayout());
        error.setBackground(ConsoleTheme.PANEL);
        error.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));

        JLabel label = new JLabel("<html><b>Panel unavailable</b><br>" + message + "</html>");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        error.add(label, BorderLayout.NORTH);
        return error;
    }
}
