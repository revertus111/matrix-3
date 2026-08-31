package game.console;

import game.ClientConsoleBridge;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

public final class OwnerPanel extends JScrollPane {

    private static final long serialVersionUID = -7153537295695898090L;
    private static final int REFRESH_DELAY_MS = 750;

    private final JLabel displayNameValue = createValueLabel();
    private final JLabel rightsValue = createValueLabel();
    private final JLabel playerStateValue = createValueLabel();

    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refresh());

    public OwnerPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));

        JLabel title = new JLabel("OWNER");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Development account");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
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
        JPanel card = createCard("Account");
        card.add(Box.createVerticalStrut(10));
        card.add(createRow("Display name", displayNameValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Rights", rightsValue));
        card.add(Box.createVerticalStrut(7));
        card.add(createRow("Client state", playerStateValue));
        return card;
    }

    private JPanel createActionsCard() {
        JPanel card = createCard("Quick actions");

        JLabel message = new JLabel("<html>Read-only for this slice.<br>"
                + "Command-backed Owner actions come next so Matrix3 remains authoritative.</html>");
        message.setFont(ConsoleTheme.SMALL_FONT);
        message.setForeground(ConsoleTheme.MUTED_TEXT);
        message.setAlignmentX(LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(9));
        card.add(message);
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
    }
}
