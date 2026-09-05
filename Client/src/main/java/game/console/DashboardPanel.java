package game.console;

import game.ClientConsoleBridge;
import game.console.bosslabs.BossLabsWindow;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Read-only Client Console home/dashboard. It surfaces existing Matrix3 client
 * state and launches specialist tools without taking ownership of gameplay.
 */
public final class DashboardPanel extends JScrollPane {

    private static final long serialVersionUID = 7818686426616483880L;
    private static final int REFRESH_DELAY_MS = 500;

    private final JLabel displayNameValue = createValueLabel();
    private final JLabel rightsValue = createValueLabel();
    private final JLabel playerStateValue = createValueLabel();
    private final JLabel positionValue = createValueLabel();
    private final JLabel planeValue = createValueLabel();
    private final JLabel regionValue = createValueLabel();
    private final JLabel cacheEditorStatus = new JLabel("Ready");
    private final JButton resetLayoutButton = new JButton("Reset Client Console Layout");
    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refresh());

    private Runnable resetLayoutAction;

    public DashboardPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        JLabel title = new JLabel("MATRIX3");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Developer dashboard");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(18));
        content.add(createSessionCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorldCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createToolsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createWorkspaceCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        refreshTimer.setCoalesce(true);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (isShowing()) {
                refresh();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
            }
        });

        configureResetLayoutButton();
        refresh();
    }

    public void setResetLayoutAction(Runnable action) {
        resetLayoutAction = action;
        resetLayoutButton.setEnabled(action != null);
    }

    private JPanel createSessionCard() {
        JPanel card = createCard("Session");
        card.add(Box.createVerticalStrut(10));
        card.add(createRow("Display name", displayNameValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Rights", rightsValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Client state", playerStateValue));
        return card;
    }

    private JPanel createWorldCard() {
        JPanel card = createCard("World");
        card.add(Box.createVerticalStrut(10));
        card.add(createRow("Tile", positionValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Plane", planeValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Region", regionValue));
        return card;
    }

    private JPanel createToolsCard() {
        JPanel card = createCard("Developer tools");

        JButton bossLabsButton = new JButton("Open BossLabs");
        JButton cacheEditorButton = new JButton("Open RS3 CacheEditor");
        ConsoleTheme.styleButton(bossLabsButton);
        ConsoleTheme.styleButton(cacheEditorButton);
        bossLabsButton.addActionListener(e -> BossLabsWindow.open());
        cacheEditorButton.addActionListener(e -> openCacheEditor());

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.setBackground(ConsoleTheme.CARD);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(bossLabsButton);
        buttons.add(cacheEditorButton);

        cacheEditorStatus.setFont(ConsoleTheme.SMALL_FONT);
        cacheEditorStatus.setForeground(ConsoleTheme.MUTED_TEXT);
        cacheEditorStatus.setAlignmentX(LEFT_ALIGNMENT);

        card.add(Box.createVerticalStrut(10));
        card.add(buttons);
        card.add(Box.createVerticalStrut(8));
        card.add(cacheEditorStatus);
        return card;
    }

    private JPanel createWorkspaceCard() {
        JPanel card = createCard("Workspace");

        JLabel note = new JLabel("Window, console width, and active panel restore after a clean exit.");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        note.setAlignmentX(LEFT_ALIGNMENT);

        resetLayoutButton.setAlignmentX(LEFT_ALIGNMENT);
        resetLayoutButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        card.add(Box.createVerticalStrut(9));
        card.add(note);
        card.add(Box.createVerticalStrut(10));
        card.add(resetLayoutButton);
        return card;
    }

    private void configureResetLayoutButton() {
        ConsoleTheme.styleButton(resetLayoutButton);
        resetLayoutButton.setEnabled(false);
        resetLayoutButton.addActionListener(e -> {
            if (resetLayoutAction != null) {
                resetLayoutAction.run();
            }
        });
    }

    private void openCacheEditor() {
        try {
            cacheEditorStatus.setText(CacheEditorProcessLauncher.open()
                    ? "CacheEditor launch requested."
                    : "CacheEditor is already running.");
        } catch (IOException ex) {
            ex.printStackTrace();
            cacheEditorStatus.setText("CacheEditor launch failed; see client console output.");
        }
    }

    private JPanel createCard(String titleText) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(14, 14, 14, 14)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);
        return card;
    }

    private JPanel createRow(String labelText, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(ConsoleTheme.CARD);
        row.setOpaque(true);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);

        row.add(label, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private static JLabel createValueLabel() {
        JLabel label = new JLabel();
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private void refresh() {
        displayNameValue.setText(ClientConsoleBridge.getDisplayName());
        rightsValue.setText(ClientConsoleBridge.getRightsLabel());
        playerStateValue.setText(ClientConsoleBridge.getPlayerStateLabel());
        positionValue.setText(ClientConsoleBridge.getWorldPositionLabel());
        planeValue.setText(ClientConsoleBridge.getPlaneLabel());
        regionValue.setText(ClientConsoleBridge.getRegionLabel());
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 8350055474236326322L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(16, extent - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
