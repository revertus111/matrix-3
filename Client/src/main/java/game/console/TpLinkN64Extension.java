package game.console;

import game.AlternateCharacterCombatBridge;

import java.awt.Component;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Adds shared N64 combat tools and the TP Link workspace without changing Mario. */
final class TpLinkN64Extension {

    private TpLinkN64Extension() {
    }

    static JComponent create() {
        N64Panel panel = new N64Panel();
        JTabbedPane tabs = findTopLevelTabs(panel);
        if (tabs != null) {
            tabs.addTab("Combat Test", createCombatTestPanel());
            tabs.addTab("TP Link", new TpLinkPanel());
        } else {
            System.err.println("[N64 Console] game tabs not found; combat/TP workspaces unavailable");
        }
        return panel;
    }

    private static JComponent createCombatTestPanel() {
        JPanel host = new JPanel();
        host.setLayout(new BoxLayout(host, BoxLayout.Y_AXIS));
        host.setBackground(ConsoleTheme.PANEL);
        host.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        JPanel card = ConsoleTheme.createCard("Deterministic Combat Dummy");
        card.add(ConsoleTheme.createWrappedText(
                "Server-side 10,000,000 HP target for vanilla RuneScape combat and imported Mario/Link combat. It stays still, never retaliates and creates no drops.",
                3));
        card.add(Box.createVerticalStrut(8));

        JLabel status = new JLabel("Ready");
        ConsoleTheme.styleStatus(status, true);

        JButton spawn = new JButton("Spawn Combat Dummy");
        JButton clear = new JButton("Remove Combat Dummies");
        ConsoleTheme.styleButton(spawn);
        ConsoleTheme.styleButton(clear);

        spawn.addActionListener(e -> {
            boolean sent = AlternateCharacterCombatBridge.requestCombatDummySpawn();
            status.setText(sent ? "Spawn request sent" : "Not connected to the game server");
        });
        clear.addActionListener(e -> {
            boolean sent = AlternateCharacterCombatBridge.requestCombatDummyClear();
            status.setText(sent ? "Remove request sent" : "Not connected to the game server");
        });

        card.add(spawn);
        card.add(Box.createVerticalStrut(6));
        card.add(clear);
        card.add(Box.createVerticalStrut(8));
        card.add(status);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Server console diagnostics distinguish invalid target, range rejection, non-melee weapon, controller rejection, executed combat, and blocked/cooldown contacts.",
                3));

        host.add(card);
        host.add(Box.createVerticalGlue());
        return host;
    }

    private static JTabbedPane findTopLevelTabs(N64Panel panel) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JTabbedPane) {
                return (JTabbedPane) component;
            }
        }
        return null;
    }
}
