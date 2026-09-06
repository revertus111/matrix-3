package game.console;

import game.ClientConsoleBridge;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Read-only Client Console visibility for the local Matrix3 player.
 * Gameplay state remains owned by the normal client/server systems.
 */
public final class PlayerPanel extends JScrollPane {

    private static final long serialVersionUID = 4867224871629009468L;
    private static final int REFRESH_DELAY_MS = 500;

    private final JLabel displayNameValue = ConsoleTheme.createValueLabel();
    private final JLabel rightsValue = ConsoleTheme.createValueLabel();
    private final JLabel playerStateValue = ConsoleTheme.createValueLabel();
    private final JLabel positionValue = ConsoleTheme.createValueLabel();
    private final JLabel planeValue = ConsoleTheme.createValueLabel();
    private final JLabel regionValue = ConsoleTheme.createValueLabel();

    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refresh());

    public PlayerPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("PLAYER"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Live local-player visibility"));
        content.add(Box.createVerticalStrut(18));
        content.add(createIdentityCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPositionCard());
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

        refresh();
    }

    private JPanel createIdentityCard() {
        JPanel card = ConsoleTheme.createCard("Identity");
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createValueRow("Display name", displayNameValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Rights", rightsValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Client state", playerStateValue));
        return card;
    }

    private JPanel createPositionCard() {
        JPanel card = ConsoleTheme.createCard("World position");
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createValueRow("Tile", positionValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Plane", planeValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Region", regionValue));
        return card;
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
        private static final long serialVersionUID = 2059821050187498612L;

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
