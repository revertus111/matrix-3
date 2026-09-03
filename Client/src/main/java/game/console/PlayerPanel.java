package game.console;

import game.ClientConsoleBridge;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;

import javax.swing.BorderFactory;
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

    private final JLabel displayNameValue = createValueLabel();
    private final JLabel rightsValue = createValueLabel();
    private final JLabel playerStateValue = createValueLabel();
    private final JLabel positionValue = createValueLabel();
    private final JLabel planeValue = createValueLabel();
    private final JLabel regionValue = createValueLabel();

    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refresh());

    public PlayerPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        JLabel title = new JLabel("PLAYER");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Live local-player visibility");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
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
        JPanel card = createCard("Identity");
        card.add(Box.createVerticalStrut(10));
        card.add(createRow("Display name", displayNameValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Rights", rightsValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Client state", playerStateValue));
        return card;
    }

    private JPanel createPositionCard() {
        JPanel card = createCard("World position");
        card.add(Box.createVerticalStrut(10));
        card.add(createRow("Tile", positionValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Plane", planeValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Region", regionValue));
        return card;
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
