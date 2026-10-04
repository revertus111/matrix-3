package game.console;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;

/**
 * RuneScape-style cascading navigation for the Test Console.
 *
 * The rail opens a heavyweight owned window instead of a lightweight popup so
 * the menu can cross the Matrix3 AWT game canvas without being clipped or
 * painted behind it. Workspaces with their own pages may open a second owned
 * window to the left without reintroducing horizontal tab bars.
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
    private static final int DISMISS_DELAY_MS = 180;

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
    private final Timer dismissTimer;

    private Window owner;
    private JWindow rootWindow;
    private JWindow conRevampWindow;
    private JComponent anchor;
    private JButton conRevampButton;

    public TestConsoleFlyoutMenu(Handler handler) {
        this.handler = handler;
        dismissTimer = new Timer(DISMISS_DELAY_MS, e -> {
            if (!isPointerInside(anchor)
                    && !isPointerInside(rootWindow)
                    && !isPointerInside(conRevampWindow)) {
                hideAll();
            }
        });
        dismissTimer.setRepeats(false);
    }

    public void showFor(JComponent source) {
        if (source == null || !source.isShowing() || !ensureWindows(source)) {
            return;
        }

        source.setToolTipText(null);
        anchor = source;
        cancelScheduledHide();
        rebuildRoot();
        conRevampWindow.setVisible(false);

        rootWindow.pack();
        Point sourcePoint = source.getLocationOnScreen();
        Rectangle screen = getScreenBounds(source);
        int x = clamp(sourcePoint.x - rootWindow.getWidth(),
                screen.x, screen.x + screen.width - rootWindow.getWidth());
        int y = clamp(sourcePoint.y,
                screen.y, screen.y + screen.height - rootWindow.getHeight());

        rootWindow.setLocation(x, y);
        rootWindow.setVisible(true);
        rootWindow.toFront();
    }

    public void scheduleHide() {
        dismissTimer.restart();
    }

    public void hideAll() {
        dismissTimer.stop();
        if (conRevampWindow != null) {
            conRevampWindow.setVisible(false);
        }
        if (rootWindow != null) {
            rootWindow.setVisible(false);
        }
    }

    private boolean ensureWindows(JComponent source) {
        Window newOwner = SwingUtilities.getWindowAncestor(source);
        if (newOwner == null) {
            return false;
        }
        if (rootWindow != null && owner == newOwner) {
            return true;
        }

        disposeWindows();
        owner = newOwner;
        rootWindow = createWindow(owner);
        conRevampWindow = createWindow(owner);
        return true;
    }

    private JWindow createWindow(Window windowOwner) {
        JWindow window = new JWindow(windowOwner);
        window.setFocusableWindowState(false);
        window.setBackground(POPUP_BG);
        return window;
    }

    private void disposeWindows() {
        if (rootWindow != null) {
            rootWindow.dispose();
        }
        if (conRevampWindow != null) {
            conRevampWindow.dispose();
        }
        rootWindow = null;
        conRevampWindow = null;
        owner = null;
        conRevampButton = null;
    }

    private void rebuildRoot() {
        JPanel rows = createRowsPanel(ROOT_WIDTH);
        String selectedTool = handler.getSelectedToolId();

        conRevampButton = createRow(ROOT_WIDTH, "Con Revamp", true,
                TestConsolePanel.TOOL_CON_REVAMP.equals(selectedTool),
                new Runnable() {
                    @Override
                    public void run() {
                        showConRevampFlyout();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        showConRevampFlyout();
                    }
                });
        rows.add(conRevampButton);

        rows.add(createToolRow("Rail Studio",
                TestConsolePanel.TOOL_RAIL_STUDIO, selectedTool));
        rows.add(createToolRow("Rail Classifier",
                TestConsolePanel.TOOL_RAIL_CLASSIFIER, selectedTool));
        rows.add(createDivider(ROOT_WIDTH));
        rows.add(createToolRow("Object Explorer",
                TestConsolePanel.TOOL_OBJECT_EXPLORER, selectedTool));
        rows.add(createToolRow("Live Inspect",
                TestConsolePanel.TOOL_LIVE_INSPECT, selectedTool));
        rows.add(createToolRow("Construction",
                TestConsolePanel.TOOL_CONSTRUCTION, selectedTool));
        rows.add(createDivider(ROOT_WIDTH));
        rows.add(createToolRow("Player",
                TestConsolePanel.TOOL_PLAYER, selectedTool));
        rows.add(createToolRow("Items",
                TestConsolePanel.TOOL_ITEMS, selectedTool));
        rows.add(createToolRow("Interfaces",
                TestConsolePanel.TOOL_INTERFACES, selectedTool));
        rows.add(createToolRow("Visual Explorer",
                TestConsolePanel.TOOL_VISUAL_EXPLORER, selectedTool));
        rows.add(createDivider(ROOT_WIDTH));
        rows.add(createToolRow("Atlas",
                TestConsolePanel.TOOL_ATLAS, selectedTool));
        rows.add(createToolRow("Boss Research",
                TestConsolePanel.TOOL_BOSS_RESEARCH, selectedTool));
        rows.add(createToolRow("Ports UI",
                TestConsolePanel.TOOL_PORTS_UI, selectedTool));

        setWindowContent(rootWindow, rows);
    }

    private JButton createToolRow(String label, String toolId,
            String selectedTool) {
        return createRow(ROOT_WIDTH, label, false,
                toolId.equals(selectedTool),
                selectTool(toolId),
                new Runnable() {
                    @Override
                    public void run() {
                        hideConRevampFlyout();
                    }
                });
    }

    private void showConRevampFlyout() {
        if (rootWindow == null || !rootWindow.isVisible()
                || conRevampButton == null || !conRevampButton.isShowing()) {
            return;
        }

        cancelScheduledHide();
        JPanel rows = createRowsPanel(SUB_WIDTH);
        String selected = handler.getSelectedConRevampSection();

        rows.add(createSectionRow(TestConsolePanel.SECTION_SETTLEMENT, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_WORKERS, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_PRODUCTION, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_CONVEYORS, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_DEBUG, selected));
        rows.add(createSectionRow(TestConsolePanel.SECTION_TOOLS, selected));

        setWindowContent(conRevampWindow, rows);
        conRevampWindow.pack();

        Point rowPoint = conRevampButton.getLocationOnScreen();
        Rectangle screen = getScreenBounds(conRevampButton);
        int desiredX = rootWindow.getX() - conRevampWindow.getWidth();
        int x = clamp(desiredX,
                screen.x, screen.x + screen.width - conRevampWindow.getWidth());
        int y = clamp(rowPoint.y,
                screen.y, screen.y + screen.height - conRevampWindow.getHeight());

        conRevampWindow.setLocation(x, y);
        conRevampWindow.setVisible(true);
        conRevampWindow.toFront();
        rootWindow.toFront();
    }

    private void hideConRevampFlyout() {
        if (conRevampWindow != null) {
            conRevampWindow.setVisible(false);
        }
    }

    private JButton createSectionRow(final String sectionId,
            String selectedSection) {
        return createRow(SUB_WIDTH, sectionId, false,
                sectionId.equals(selectedSection),
                new Runnable() {
                    @Override
                    public void run() {
                        handler.select(TestConsolePanel.TOOL_CON_REVAMP, sectionId);
                        hideAll();
                    }
                },
                null);
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

    private void setWindowContent(JWindow window, JPanel rows) {
        window.getContentPane().removeAll();
        window.getContentPane().add(rows);
        window.getContentPane().validate();
    }

    private JPanel createRowsPanel(int width) {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(true);
        rows.setBackground(POPUP_BG);
        rows.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        rows.setMinimumSize(new Dimension(width, 1));
        rows.setMaximumSize(new Dimension(width, Integer.MAX_VALUE));

        MouseAdapter hoverGuard = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                cancelScheduledHide();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                scheduleHide();
            }
        };
        rows.addMouseListener(hoverGuard);
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
            final boolean selected, final Runnable clickAction,
            final Runnable hoverAction) {
        final JButton button =
                new JButton(label + (hasChildren ? "    >" : ""));
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
        button.setCursor(java.awt.Cursor.getPredefinedCursor(
                java.awt.Cursor.HAND_CURSOR));
        button.setBorder(rowBorder(selected));

        button.addActionListener(e -> clickAction.run());
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                cancelScheduledHide();
                button.setBackground(ROW_HOVER);
                button.setForeground(GOLD_BRIGHT);
                if (hoverAction != null) {
                    hoverAction.run();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(selected ? ROW_SELECTED : ROW_BG);
                button.setForeground(selected ? GOLD_BRIGHT : TEXT);
                scheduleHide();
            }
        });
        return button;
    }

    private void cancelScheduledHide() {
        dismissTimer.stop();
    }

    private boolean isPointerInside(Component component) {
        if (component == null || !component.isShowing()) {
            return false;
        }
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            return false;
        }
        Point point = pointerInfo.getLocation();
        if (component instanceof Window) {
            return ((Window) component).getBounds().contains(point);
        }
        Point location = component.getLocationOnScreen();
        return new Rectangle(location.x, location.y,
                component.getWidth(), component.getHeight()).contains(point);
    }

    private Rectangle getScreenBounds(Component component) {
        GraphicsConfiguration configuration = component.getGraphicsConfiguration();
        if (configuration != null) {
            return configuration.getBounds();
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private Border rowBorder(boolean selected) {
        Border outer = BorderFactory.createMatteBorder(0,
                selected ? 3 : 1, 1, 1,
                selected ? GOLD : DIVIDER);
        Border inner = BorderFactory.createEmptyBorder(0, 10, 0, 8);
        return BorderFactory.createCompoundBorder(outer, inner);
    }
}
