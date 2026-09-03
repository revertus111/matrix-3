package game.console;

import game.ClientConsoleBridge;

import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Timer;

/**
 * Client Console controls for the Matrix3 combat/interface mode split.
 *
 * The selected combination is stored locally so the console can restore it on a
 * later login. Server-side Matrix3 state remains authoritative once the queued
 * commands are processed.
 */
public final class SettingsPanel extends JScrollPane {

    private static final long serialVersionUID = 2136284824815349007L;

    private static final String KEY_LEGACY_COMBAT = "legacyCombat";
    private static final String KEY_LEGACY_INTERFACE = "legacyInterface";

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(SettingsPanel.class).node("mode-settings");

    private final JToggleButton legacyCombatButton = new JToggleButton();
    private final JToggleButton legacyInterfaceButton = new JToggleButton();
    private final JLabel status = new JLabel("Saved locally. Log in to apply the selected combination.");

    private boolean appliedWhileShowing;
    private final Timer loginApplyTimer = new Timer(750, e -> refreshLoginApplyState());

    public SettingsPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));

        JLabel title = new JLabel("SETTINGS");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Combat / interface split");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        legacyCombatButton.setSelected(PREFS.getBoolean(KEY_LEGACY_COMBAT, false));
        legacyInterfaceButton.setSelected(PREFS.getBoolean(KEY_LEGACY_INTERFACE, false));
        configureToggle(legacyCombatButton, true);
        configureToggle(legacyInterfaceButton, false);
        updateToggleLabels();

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(18));
        content.add(createModeCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        loginApplyTimer.setCoalesce(true);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (isShowing()) {
                appliedWhileShowing = false;
                refreshLoginApplyState();
                loginApplyTimer.start();
            } else {
                loginApplyTimer.stop();
            }
        });
    }

    private JPanel createModeCard() {
        JPanel card = createCard("Game modes");

        JLabel description = new JLabel("<html>Combat and interface mode are independent here.<br>"
                + "For Legacy combat with the modern NIS interface: turn Legacy combat ON and Legacy interface OFF.</html>");
        description.setFont(ConsoleTheme.SMALL_FONT);
        description.setForeground(ConsoleTheme.MUTED_TEXT);
        description.setAlignmentX(LEFT_ALIGNMENT);

        legacyCombatButton.setAlignmentX(LEFT_ALIGNMENT);
        legacyCombatButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        legacyInterfaceButton.setAlignmentX(LEFT_ALIGNMENT);
        legacyInterfaceButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton applyButton = new JButton("Apply saved combination");
        applyButton.setAlignmentX(LEFT_ALIGNMENT);
        applyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(applyButton);
        applyButton.addActionListener(e -> applyCombination(false));

        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.ACCENT);
        status.setAlignmentX(LEFT_ALIGNMENT);

        JLabel note = new JLabel("<html>The in-game Legacy Mode checkbox is still Matrix3's original combined switch. "
                + "Applying these controls normalizes that combined state and restores the split selection.</html>");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        note.setAlignmentX(LEFT_ALIGNMENT);

        card.add(Box.createVerticalStrut(9));
        card.add(description);
        card.add(Box.createVerticalStrut(12));
        card.add(legacyCombatButton);
        card.add(Box.createVerticalStrut(7));
        card.add(legacyInterfaceButton);
        card.add(Box.createVerticalStrut(12));
        card.add(applyButton);
        card.add(Box.createVerticalStrut(10));
        card.add(status);
        card.add(Box.createVerticalStrut(10));
        card.add(note);
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

    private void configureToggle(final JToggleButton button, final boolean combat) {
        ConsoleTheme.styleButton(button);
        button.addActionListener(e -> {
            savePreferences();
            updateToggleLabels();
            queueSingleMode(combat);
        });
    }

    private void updateToggleLabels() {
        legacyCombatButton.setText(legacyCombatButton.isSelected()
                ? "Legacy combat: ON"
                : "Legacy combat: OFF (EoC manual)");
        legacyInterfaceButton.setText(legacyInterfaceButton.isSelected()
                ? "Legacy interface: ON"
                : "Legacy interface: OFF (NIS)");
    }

    private void savePreferences() {
        PREFS.putBoolean(KEY_LEGACY_COMBAT, legacyCombatButton.isSelected());
        PREFS.putBoolean(KEY_LEGACY_INTERFACE, legacyInterfaceButton.isSelected());
    }

    private String combatCommand() {
        return "itembrowser settings combat " + (legacyCombatButton.isSelected() ? "legacy" : "eoc");
    }

    private String interfaceCommand() {
        return "itembrowser settings interface " + (legacyInterfaceButton.isSelected() ? "legacy" : "nis");
    }

    private void queueSingleMode(boolean combat) {
        String error = ClientConsoleBridge.queueConsoleCommand(combat ? combatCommand() : interfaceCommand());
        if (error != null) {
            status.setText(error + " Selection was still saved locally.");
            appliedWhileShowing = false;
            return;
        }
        appliedWhileShowing = true;
        status.setText((combat ? "Combat" : "Interface")
                + " mode queued. Matrix3 will confirm the change in game.");
    }

    private void applyCombination(boolean automatic) {
        savePreferences();
        if (!ClientConsoleBridge.hasLocalPlayer()) {
            status.setText("Waiting for login. The selected combination is saved locally.");
            appliedWhileShowing = false;
            return;
        }

        String error = ClientConsoleBridge.queueConsoleCommands(new String[] {
                combatCommand(),
                interfaceCommand()
        });
        if (error != null) {
            status.setText(error);
            appliedWhileShowing = false;
            return;
        }

        appliedWhileShowing = true;
        status.setText(automatic
                ? "Saved combat/interface combination queued after login."
                : "Combat/interface combination queued. Matrix3 will confirm both changes in game.");
    }

    private void refreshLoginApplyState() {
        if (!ClientConsoleBridge.hasLocalPlayer()) {
            appliedWhileShowing = false;
            status.setText("Waiting for login. The selected combination is saved locally.");
            return;
        }
        if (!appliedWhileShowing) {
            applyCombination(true);
        }
    }
}
