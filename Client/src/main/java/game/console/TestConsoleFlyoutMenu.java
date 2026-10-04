package game.console;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * RuneScape-style cascading navigation for the Test Console.
 *
 * The rail opens the primary workspace menu. Workspaces with their own pages
 * may open a second flyout without reintroducing horizontal tab bars.
 */
public final class TestConsoleFlyoutMenu {

    public interface Handler {
        void select(String toolId, String sectionId);
        String getSelectedToolId();
        String getSelectedConRevampSection();
    }

    private static final int ROOT_WIDTH = 205;
    private static final int SUB_WIDTH = 190;
    private static final int ROW_HEIGHT = 38;

    private static final Color POPUP_BG = new Color(17, 20, 24);
    private static final Color ROW_BG = new Color(23, 27, 32);
    private static final Color ROW_HOVER = new Color(90, 61, 28);
    private static final Color ROW_SELECTED = new Color(61, 47, 27);
    private static final Color GOLD = new Color(224, 177, 77);
    private static final Color GOLD_BRIGHT = new Color(245, 208, 120);
    private static final Color TEXT = new Color(231, 224, 205);
    private static final Color BORDER = new Color(112, 82, 40);
    private static final Color DIVIDER = new Color(62, 58, 50);

    private static final Font ROW_FONT = new Font("Serif", Font.PLAIN, 13);
    private static final Font ROW_FONT_BOLD = new Font("Serif", Font.BOLD, 13);

    private final Handler handler;
    private final JPopupMenu rootPopup = createPopup();
    private final JPopupMenu conRevampPopup = createPopup();

    private JComponent anchor;

    public TestConsoleFlyoutMenu(Handler handler) {
        this.handler = handler;
        rootPopup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                conRevampPopup.setVisible(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                conRevampPopup.setVisible(false);
            }
        });
    }

    public void showFor(JComponent source) {
        if (source == null || !source.isShowing()) {
            return;
        }
        anchor = source;
        rebuildRoot();
        conRevampPopup.setVisible(false);
        rootPopup.show(source, -ROOT_WIDTH, 0);
    }

    public void hideAll() {
        conRevampPopup.setVisible(false);
        rootPopup.setVisible(false);
    }

    private void rebuildRoot() {
        rootPopup.removeAll();
        JPanel rows = createRowsPanel(ROOT_WIDTH);
        String selectedTool = handler.getSelectedToolId();

        rows.add(createRow(ROOT_WIDTH, "Con Revamp", true,
                TestConsolePanel.TOOL_CON_REVAMP.equals(selectedTool),
                new Runnable() {
                    @Override
                    public void run() {
                        showConRevampFlyout();
                    }
                }, true));
        rows.add(createRow(ROOT_WIDTH, "Rail Studio", false,
                TestConsolePanel.TOOL_RAIL_STUDIO.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_RAIL_STUDIO), false));
        rows.add(createRow(ROOT_WIDTH, "Rail Classifier", false,
                TestConsolePanel.TOOL_RAIL_CLASSIFIER.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_RAIL_CLASSIFIER), false));
        rows.add(createDivider(ROOT_WIDTH));
        rows.add(createRow(ROOT_WIDTH, "Object Explorer", false,
                TestConsolePanel.TOOL_OBJECT_EXPLORER.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_OBJECT_EXPLORER), false));
        rows.add(createRow(ROOT_WIDTH, "Live Inspect", false,
                TestConsolePanel.TOOL_LIVE_INSPECT.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_LIVE_INSPECT), false));
        rows.add(createRow(ROOT_WIDTH, "Construction", false,
                TestConsolePanel.TOOL_CONSTRUCTION.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_CONSTRUCTION), false));
        rows.add(createDivider(ROOT_WIDTH));
        rows.add(createRow(ROOT_WIDTH, "Player", false,
                TestConsolePanel.TOOL_PLAYER.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_PLAYER), false));
        rows.add(createRow(ROOT_WIDTH, "Items", false,
                TestConsolePanel.TOOL_ITEMS.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_ITEMS), false));
        rows.add(createRow(ROOT_WIDTH, "Interfaces", false,
                TestConsolePanel.TOOL_INTERFACES.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_INTERFACES), false));
        rows.add(createRow(ROOT_WIDTH, "Visual Explorer", false,
                TestConsolePanel.TOOL_VISUAL_EXPLORER.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_VISUAL_EXPLORER), false));
        rows.add(createDivider(ROOT_WIDTH));
        rows.add(createRow(ROOT_WIDTH, "Atlas", false,
                TestConsolePanel.TOOL_ATLAS.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_ATLAS), false));
        rows.add(createRow(ROOT_WIDTH, "Boss Research", false,
                TestConsolePanel.TOOL_BOSS_RESEARCH.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_BOSS_RESEARCH), false));
        rows.add(createRow(ROOT_WIDTH, "Ports UI", false,
                TestConsolePanel.TOOL_PORTS_UI.equals(selectedTool),
                selectTool(TestConsolePanel.TOOL_PORTS_UI), false));

        rootPopup.add(rows);
        rootPopup.pack();
    }

    private void showConRevampFlyout() {
        if (!rootPopup.isVisible()) {
            return;
        }

        conRevampPopup.removeAll();
        JPanel rows = createRowsPanel(SUB_WIDTH);
        String selected = handler.getSelectedConRevampSection();

        rows.add(createSectionRow(TestConsolePanel.SECTION_SETTLEMENT, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_WORKERS, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_PRODUCTION, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_CONVEYORS, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_DEBUG, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_TOOLS, selected));

        conRevampPopup.add(rows);
        conRevampPopup.pack();

        Component invoker = findConRevampInvoker();
        if (invoker instanceof JComponent && invoker.isShowing()) {
            conRevampPopup.show((JComponent) invoker, -SUB_WIDTH, 0);
        }
    }

    private Component findConRevampInvoker() {
        if (rootPopup.getComponentCount() == 0) {
            return anchor;
        }
        Component container = rootPopup.getComponent(0);
        if (container instanceof JPanel) {
            Component[] components = ((JPanel) container).getComponents();
            for (Component component : components) {
                if (component instanceof JButton) {
                    JButton button = (JButton) component;
                    if (button.getText() != null && button.getText().startsWith("Con Revamp")) {
                        return button;
                    }
                }
            }
        }
        return anchor;
    }

    private JButton createSectionRow(final String sectionId, String selectedSection) {
        return createRow(SUB_WIDTH, sectionId, false,
                sectionId.equals(selectedSection),
                new Runnable() {
                    @Override
                    public void run() {
                        handler.select(TestConsolePanel.TOOL_CON_REVAMP, sectionId);
                        hideAll();
                    }
                }, false);
    }

    private Runnable selectTool(final String toolId) {
        return new Runnable() {
            @Override
            public void run() {
                handler.select(toolId, null);
                hideAll();
            }
        };
    }

    private JPopupMenu createPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.setOpaque(true);
        popup.setBackground(POPUP_BG);
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        return popup;
    }

    private JPanel createRowsPanel(int width) {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(true);
        rows.setBackground(POPUP_BG);
        rows.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        rows.setMinimumSize(new Dimension(width, 1));
        rows.setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
        return rows;
    }

    private Component createDivider(int width) {
        JPanel divider = new JPanel();
        divider.setOpaque(true);
        divider.setBackground(DIVIDER);
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setPreferredSize(new Dimension(width - 8, 1));
        divider.setMaximumSize(new Dimension(width - 8, 1));
        return divider;
    }

    private JButton createRow(int width, String label, boolean hasChildren,
            final boolean selected, final Runnable action,
            final boolean openChildrenOnHover) {
        final JButton button = new JButton(label + (hasChildren ? "    >" : ""));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(selected ? ROW_FONT_BOLD : ROW_FONT);
        button.setForeground(selected ? GOLD_BRIGHT : TEXT);
        button.setBackground(selected ? ROW_SELECTED : ROW_BG);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(width, ROW_HEIGHT));
        button.setMaximumSize(new Dimension(width, ROW_HEIGHT));
        button.setMinimumSize(new Dimension(width, ROW_HEIGHT));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setBorder(rowBorder(selected));

        button.addActionListener(e -> action.run());
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(ROW_HOVER);
                button.setForeground(GOLD_BRIGHT);
                if (openChildrenOnHover) {
                    action.run();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(selected ? ROW_SELECTED : ROW_BG);
                button.setForeground(selected ? GOLD_BRIGHT : TEXT);
            }
        });
        return button;
    }

    private Border rowBorder(boolean selected) {
        Border outer = BorderFactory.createMatteBorder(0,
                selected ? 3 : 1, 1, 1,
                selected ? GOLD : DIVIDER);
        Border inner = BorderFactory.createEmptyBorder(0, 10, 0, 8);
        return BorderFactory.createCompoundBorder(outer, inner);
    }
}
