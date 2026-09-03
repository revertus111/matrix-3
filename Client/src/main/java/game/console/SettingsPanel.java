package game.console;

import game.ClientConsoleBridge;
import game.DevModeBridge;

import java.awt.Dimension;
import java.awt.event.HierarchyEvent;

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
 * Client Console controls for the Matrix3 combat/interface mode split and
 * session-scoped Dev Mode tooling.
 *
 * The selected combat/interface combination is stored by ConsolePreferences so
 * the console can restore it on a later login. Dev Mode intentionally defaults
 * OFF on each client launch. Server-side Matrix3 state remains authoritative
 * once queued commands are processed.
 */
public final class SettingsPanel extends JScrollPane {

    private static final long serialVersionUID = 2136284824815349007L;

    private final JToggleButton legacyCombatButton = new JToggleButton();
    private final JToggleButton legacyInterfaceButton = new JToggleButton();
    private final JToggleButton devModeButton = new JToggleButton();
    private final JLabel status = new JLabel("Choose a mode combination, then apply it while logged in.");
    private final JLabel devModeStatus = new JLabel("Dev Mode is OFF for this client session.");

    private boolean hasSavedCombination;
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

        JLabel subtitle = new JLabel("Combat / interface split + developer tools");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        hasSavedCombination = ConsolePreferences.hasModeSelection();
        legacyCombatButton.setSelected(ConsolePreferences.isLegacyCombatSelected());
        legacyInterfaceButton.setSelected(ConsolePreferences.isLegacyInterfaceSelected());
        configureToggle(legacyCombatButton, true);
        configureToggle(legacyInterfaceButton, false);
        updateToggleLabels();

        devModeButton.setSelected(DevModeBridge.isEnabled());
        configureDevModeToggle();
        updateDevModeLabel();

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(18));
        content.add(createModeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createDevModeCard());
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

        JLabel description = new JLabel("<html><div style='width:240px'>Combat and interface mode are independent here.<br>"
                + "For Legacy combat with the modern NIS interface: turn Legacy combat ON and Legacy interface OFF.</div></html>");
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

        JLabel note = new JLabel("<html><div style='width:240px'>The in-game Legacy Mode checkbox is still Matrix3's original combined switch. "
                + "Applying these controls normalizes that combined state and restores the split selection.</div></html>");
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

    private JPanel createDevModeCard() {
        JPanel card = createCard("Dev Mode");

        JLabel description = new JLabel("<html><div style='width:240px'>Adds owner-only developer actions to the live game world. "
                + "Phase 1 adds tile spawning without replacing normal Matrix3 right-click options.</div></html>");
        description.setFont(ConsoleTheme.SMALL_FONT);
        description.setForeground(ConsoleTheme.MUTED_TEXT);
        description.setAlignmentX(LEFT_ALIGNMENT);

        devModeButton.setAlignmentX(LEFT_ALIGNMENT);
        devModeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        devModeStatus.setFont(ConsoleTheme.SMALL_FONT);
        devModeStatus.setForeground(ConsoleTheme.ACCENT);
        devModeStatus.setAlignmentX(LEFT_ALIGNMENT);

        JLabel note = new JLabel("<html><div style='width:240px'>Dev Mode is intentionally session-only and starts OFF after every client launch. "
                + "Live spawns are not source-data saves.</div></html>");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        note.setAlignmentX(LEFT_ALIGNMENT);

        card.add(Box.createVerticalStrut(9));
        card.add(description);
        card.add(Box.createVerticalStrut(12));
        card.add(devModeButton);
        card.add(Box.createVerticalStrut(10));
        card.add(devModeStatus);
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

    private void configureDevModeToggle() {
        ConsoleTheme.styleButton(devModeButton);
        devModeButton.addActionListener(e -> {
            DevModeBridge.setEnabled(devModeButton.isSelected());
            updateDevModeLabel();
            if (devModeButton.isSelected() && ClientConsoleBridge.getRights() < 2) {
                devModeStatus.setText("Dev Mode is ON locally, but tile tools appear only for an Admin+ session.");
            } else if (devModeButton.isSelected()) {
                devModeStatus.setText("Dev Mode is ON. Right-click a world tile for Dev > Spawn...");
            } else {
                devModeStatus.setText("Dev Mode is OFF for this client session.");
            }
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

    private void updateDevModeLabel() {
        devModeButton.setText(devModeButton.isSelected()
                ? "Dev Mode: ON"
                : "Dev Mode: OFF");
    }

    private void savePreferences() {
        ConsolePreferences.saveModeSelection(
                legacyCombatButton.isSelected(),
                legacyInterfaceButton.isSelected());
        hasSavedCombination = true;
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
        if (!hasSavedCombination) {
            status.setText("Choose a mode combination, then apply it while logged in.");
            return;
        }
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
