package game.console;

import game.ClientConsoleBridge;
import game.ConstructionPaletteOverlay;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * One-click runtime harness for the Construction Revamp workstream.
 *
 * Buttons call the same owner-only server commands used by the raw command
 * harness. This panel owns no settlement logic.
 */
public final class ConstructionRevampTestPanel extends JScrollPane {

    private static final long serialVersionUID = -8031161601344297457L;

    private final JTextArea status = ConsoleTheme.createWrappedText(
            "Ready. Use Enter Settlement to start the Bundle 1.2 persistence pass.", 4);

    public ConstructionRevampTestPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(18, 16, 18, 16));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("CON REVAMP"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Construction Revamp runtime harness"));
        content.add(Box.createVerticalStrut(16));
        content.add(createRuntimeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPersistenceCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStatusCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);
    }

    private JPanel createRuntimeCard() {
        JPanel card = ConsoleTheme.createCard("Settlement runtime");
        card.add(Box.createVerticalStrut(10));

        JButton enter = new JButton("Enter Settlement");
        JButton settlementStatus = new JButton("Settlement Status");
        JButton exit = new JButton("Exit Settlement");
        JButton palette = new JButton("Open Build Palette");

        ConsoleTheme.styleButton(enter);
        ConsoleTheme.styleButton(settlementStatus);
        ConsoleTheme.styleButton(exit);
        ConsoleTheme.styleButton(palette);

        enter.addActionListener(e -> queue(
                "itembrowser settlement enter",
                "Enter Settlement queued."));
        settlementStatus.addActionListener(e -> queue(
                "itembrowser settlement status",
                "Settlement Status queued. Check the game chat response."));
        exit.addActionListener(e -> queue(
                "itembrowser settlement exit",
                "Exit Settlement queued."));
        palette.addActionListener(e -> {
            ConstructionPaletteOverlay.show();
            setStatus("Construction build palette opened.");
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(enter);
        buttons.add(settlementStatus);
        buttons.add(exit);
        buttons.add(palette);
        card.add(buttons);

        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createWrappedText(
                "These buttons use the existing server-authoritative settlement command bridge. No settlement behavior is duplicated in the Client Console.",
                3));
        return card;
    }

    private JPanel createPersistenceCard() {
        JPanel card = ConsoleTheme.createCard("Bundle 1.2 persistence pass");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "1. Enter Settlement.\n"
                + "2. Open Build Palette and place/rotate/move/duplicate/delete pieces.\n"
                + "3. Use Settlement Status to check the saved-piece count.\n"
                + "4. Exit, re-enter and verify the exact layout rebuilds.\n"
                + "5. Logout/relog, re-enter and verify the layout still rebuilds.",
                7));
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Test output");
        card.add(Box.createVerticalStrut(9));
        status.setForeground(ConsoleTheme.ACCENT);
        card.add(status);
        return card;
    }

    private void queue(String command, String success) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        setStatus(error == null ? success : error);
    }

    private void setStatus(String message) {
        status.setText(message == null ? "" : message);
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = -4531105733940656014L;

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
