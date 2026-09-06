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
 * Owner/account visibility and future owner-only controls. Specialist boss and
 * cache research belongs to the dedicated Boss Research workspace.
 */
public final class OwnerPanel extends JScrollPane {

    private static final long serialVersionUID = -7153537295695898090L;
    private static final int REFRESH_DELAY_MS = 750;

    private final JLabel displayNameValue = ConsoleTheme.createValueLabel();
    private final JLabel rightsValue = ConsoleTheme.createValueLabel();
    private final JLabel playerStateValue = ConsoleTheme.createValueLabel();
    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refresh());

    public OwnerPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("OWNER"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Development account + owner controls"));
        content.add(Box.createVerticalStrut(18));
        content.add(createAccountCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createActionsCard());
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

    private JPanel createAccountCard() {
        JPanel card = ConsoleTheme.createCard("Account");
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createValueRow("Display name", displayNameValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Rights", rightsValue));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createValueRow("Client state", playerStateValue));
        return card;
    }

    private JPanel createActionsCard() {
        JPanel card = ConsoleTheme.createCard("Owner tools");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Owner stays focused on account, rights, and genuine owner/admin controls. Boss probes and RoTS cache evidence now live in the dedicated Boss Research panel.",
                3));
        return card;
    }

    private void refresh() {
        displayNameValue.setText(ClientConsoleBridge.getDisplayName());
        rightsValue.setText(ClientConsoleBridge.getRightsLabel());
        playerStateValue.setText(ClientConsoleBridge.getPlayerStateLabel());
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 7995041916861782179L;

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
