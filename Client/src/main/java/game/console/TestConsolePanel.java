package game.console;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Consolidated developer/testing workspace.
 *
 * Owner, Commands and Settings remain top-level Client Console panels. Tooling
 * and runtime test utilities live here as sub-tabs.
 */
public final class TestConsolePanel extends JPanel {

    private static final long serialVersionUID = -3097526505843407244L;

    private static final int TAB_CON_REVAMP = 0;
    private static final int TAB_RAIL_CLASSIFIER = 1;
    private static final int TAB_OBJECT_EXPLORER = 2;
    private static final int TAB_CONSTRUCTION = 3;
    private static final int TAB_PLAYER = 4;
    private static final int TAB_ITEMS = 5;
    private static final int TAB_INTERFACES = 6;
    private static final int TAB_VISUAL_EXPLORER = 7;
    private static final int TAB_ATLAS = 8;
    private static final int TAB_BOSS_RESEARCH = 9;

    private final JTabbedPane tabs = new JTabbedPane();

    private JComponent conRevampPanel;
    private JComponent railClassifierPanel;
    private JComponent objectExplorerPanel;
    private JComponent constructionPanel;
    private JComponent playerPanel;
    private JComponent itemPanel;
    private JComponent interfacePanel;
    private JComponent visualExplorerPanel;
    private JComponent atlasPanel;
    private JComponent bossResearchPanel;

    public TestConsolePanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setFont(ConsoleTheme.SMALL_FONT);
        tabs.setForeground(ConsoleTheme.TEXT);
        tabs.setBackground(ConsoleTheme.PANEL);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        tabs.addTab("Con Revamp", placeholder());
        tabs.addTab("Rail Classifier", placeholder());
        tabs.addTab("Object Explorer", placeholder());
        tabs.addTab("Construction", placeholder());
        tabs.addTab("Player", placeholder());
        tabs.addTab("Items", placeholder());
        tabs.addTab("Interfaces", placeholder());
        tabs.addTab("Visual Explorer", placeholder());
        tabs.addTab("Atlas", placeholder());
        tabs.addTab("Boss Research", placeholder());

        tabs.addChangeListener(e -> ensureSelectedTab());
        add(tabs, BorderLayout.CENTER);

        ensureSelectedTab();
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

    private JPanel placeholder() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ConsoleTheme.PANEL);
        panel.setOpaque(true);
        return panel;
    }

    private void ensureSelectedTab() {
        int index = tabs.getSelectedIndex();
        if (index < 0) {
            return;
        }

        JComponent component = getOrCreate(index);
        if (component != null && tabs.getComponentAt(index) != component) {
            tabs.setComponentAt(index, component);
        }
    }

    private JComponent getOrCreate(int index) {
        try {
            switch (index) {
            case TAB_CON_REVAMP:
                if (conRevampPanel == null) {
                    conRevampPanel = new ConstructionRevampTestPanel();
                }
                return conRevampPanel;
            case TAB_RAIL_CLASSIFIER:
                if (railClassifierPanel == null) {
                    railClassifierPanel = new RailKitClassifierPanel();
                }
                return railClassifierPanel;
            case TAB_OBJECT_EXPLORER:
                if (objectExplorerPanel == null) {
                    objectExplorerPanel = new ObjectExplorerPanel();
                }
                return objectExplorerPanel;
            case TAB_CONSTRUCTION:
                if (constructionPanel == null) {
                    constructionPanel = new ConstructionEditorPanel();
                }
                return constructionPanel;
            case TAB_PLAYER:
                if (playerPanel == null) {
                    playerPanel = new PlayerPanel();
                }
                return playerPanel;
            case TAB_ITEMS:
                if (itemPanel == null) {
                    itemPanel = new ItemBrowserPanel();
                }
                return itemPanel;
            case TAB_INTERFACES:
                if (interfacePanel == null) {
                    interfacePanel = new InterfaceEditorPanel();
                }
                return interfacePanel;
            case TAB_VISUAL_EXPLORER:
                if (visualExplorerPanel == null) {
                    visualExplorerPanel = new VisualExplorerPanel(new Runnable() {
                        @Override
                        public void run() {
                            tabs.setSelectedIndex(TAB_INTERFACES);
                        }
                    });
                }
                return visualExplorerPanel;
            case TAB_ATLAS:
                if (atlasPanel == null) {
                    atlasPanel = new AtlasWorkspacePanel();
                }
                return atlasPanel;
            case TAB_BOSS_RESEARCH:
                if (bossResearchPanel == null) {
                    bossResearchPanel = new BossResearchPanel();
                }
                return bossResearchPanel;
            default:
                return placeholder();
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return createError("Test sub-tab failed to initialize.");
        }
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
