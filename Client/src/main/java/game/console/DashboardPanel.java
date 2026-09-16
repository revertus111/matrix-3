package game.console;

import game.ClientConsoleBridge;
import game.console.bosslabs.BossLabsWindow;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.io.IOException;

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

    private final JLabel displayNameValue = ConsoleTheme.createValueLabel();
    private final JLabel rightsValue = ConsoleTheme.createValueLabel();
    private final JLabel playerStateValue = ConsoleTheme.createValueLabel();
    private final JLabel positionValue = ConsoleTheme.createValueLabel();
    private final JLabel planeValue = ConsoleTheme.createValueLabel();
    private final JLabel regionValue = ConsoleTheme.createValueLabel();
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

        content.add(ConsoleTheme.titleLabel("MATRIX3"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Developer dashboard"));
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
        JPanel card = ConsoleTheme.createCard("Session");
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createValueRow("Display name", displayNameValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Rights", rightsValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Client state", playerStateValue));
        return card;
    }

    private JPanel createWorldCard() {
        JPanel card = ConsoleTheme.createCard("World");
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createValueRow("Tile", positionValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Plane", planeValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Region", regionValue));
        return card;
    }

    private JPanel createToolsCard() {
        JPanel card = ConsoleTheme.createCard("Developer tools");

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

        ConsoleTheme.styleStatus(cacheEditorStatus, false);

        card.add(Box.createVerticalStrut(10));
        card.add(buttons);
        card.add(Box.createVerticalStrut(8));
        card.add(cacheEditorStatus);
        return card;
    }

    private JPanel createWorkspaceCard() {
        JPanel card = ConsoleTheme.createCard("Workspace");
        JLabel note = new JLabel("Layout restores after a clean exit.");
        ConsoleTheme.styleStatus(note, false);

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
