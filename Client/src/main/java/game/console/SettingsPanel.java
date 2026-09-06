package game.console;

import game.ClientConsoleBridge;
import game.DevModeBridge;
import game.QolSettings;

import java.awt.Dimension;
import java.awt.event.HierarchyEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Timer;

/**
 * Client Console controls for the Matrix3 combat/interface mode split,
 * quality-of-life preferences, and session-scoped Dev Mode tooling.
 *
 * The selected combat/interface combination is stored by ConsolePreferences so
 * the console can restore it on a later login. QOL preferences are stored by
 * QolSettings. Dev Mode intentionally defaults OFF on each client launch.
 * Server-side Matrix3 state remains authoritative once queued commands are
 * processed.
 */
public final class SettingsPanel extends JScrollPane {

    private static final long serialVersionUID = 2136284824815349007L;

    private final JToggleButton legacyCombatButton = new JToggleButton();
    private final JToggleButton legacyInterfaceButton = new JToggleButton();
    private final JToggleButton shiftClickDropButton = new JToggleButton();
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

        hasSavedCombination = ConsolePreferences.hasModeSelection();
        legacyCombatButton.setSelected(ConsolePreferences.isLegacyCombatSelected());
        legacyInterfaceButton.setSelected(ConsolePreferences.isLegacyInterfaceSelected());
        configureToggle(legacyCombatButton, true);
        configureToggle(legacyInterfaceButton, false);
        updateToggleLabels();

        shiftClickDropButton.setSelected(QolSettings.isShiftClickDropEnabled());
        configureShiftClickDropToggle();
        updateShiftClickDropLabel();

        devModeButton.setSelected(DevModeBridge.isEnabled());
        configureDevModeToggle();
        updateDevModeLabel();

        content.add(ConsoleTheme.titleLabel("SETTINGS"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Game modes + quality of life + developer tools"));
        content.add(Box.createVerticalStrut(18));
        content.add(createModeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createQolCard());
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
        JPanel card = ConsoleTheme.createCard("Game modes");
        JTextArea description = ConsoleTheme.createWrappedText(
                "Combat and interface mode are independent here. For Legacy combat with the modern NIS interface, turn Legacy combat ON and Legacy interface OFF.",
                3);

        legacyCombatButton.setAlignmentX(LEFT_ALIGNMENT);
        legacyCombatButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        legacyInterfaceButton.setAlignmentX(LEFT_ALIGNMENT);
        legacyInterfaceButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton applyButton = new JButton("Apply saved combination");
        applyButton.setAlignmentX(LEFT_ALIGNMENT);
        applyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(applyButton);
        applyButton.addActionListener(e -> applyCombination(false));
        ConsoleTheme.styleStatus(status, true);

        JTextArea note = ConsoleTheme.createWrappedText(
                "The in-game Legacy Mode checkbox is still Matrix3's original combined switch. Applying these controls normalizes that combined state and restores the split selection.",
                3);

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

    private JPanel createQolCard() {
        JPanel card = ConsoleTheme.createCard("Quality of life");

        JLabel section = new JLabel("Inventory");
        section.setFont(ConsoleTheme.SMALL_FONT);
        section.setForeground(ConsoleTheme.ACCENT);
        section.setAlignmentX(LEFT_ALIGNMENT);

        JTextArea description = ConsoleTheme.createWrappedText(
                "Shift-click Drop changes only the left-click choice while Shift is held. The normal Matrix3 right-click menu and item action handling stay intact.",
                3);

        shiftClickDropButton.setAlignmentX(LEFT_ALIGNMENT);
        shiftClickDropButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        card.add(Box.createVerticalStrut(9));
        card.add(section);
        card.add(Box.createVerticalStrut(7));
        card.add(description);
        card.add(Box.createVerticalStrut(12));
        card.add(shiftClickDropButton);
        return card;
    }

    private JPanel createDevModeCard() {
        JPanel card = ConsoleTheme.createCard("Dev Mode");
        JTextArea description = ConsoleTheme.createWrappedText(
                "Adds owner-only developer actions to the live game world. Phase 1 adds tile spawning without replacing normal Matrix3 right-click options.",
                3);

        devModeButton.setAlignmentX(LEFT_ALIGNMENT);
        devModeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleStatus(devModeStatus, true);

        JTextArea note = ConsoleTheme.createWrappedText(
                "Dev Mode is intentionally session-only and starts OFF after every client launch. Live spawns are not source-data saves.",
                3);

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

    private void configureToggle(final JToggleButton button, final boolean combat) {
        ConsoleTheme.styleButton(button);
        button.addActionListener(e -> {
            savePreferences();
            updateToggleLabels();
            queueSingleMode(combat);
        });
    }

    private void configureShiftClickDropToggle() {
        ConsoleTheme.styleButton(shiftClickDropButton);
        shiftClickDropButton.addActionListener(e -> {
            QolSettings.setShiftClickDropEnabled(shiftClickDropButton.isSelected());
            updateShiftClickDropLabel();
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

    private void updateShiftClickDropLabel() {
        shiftClickDropButton.setText(shiftClickDropButton.isSelected()
                ? "Shift-click Drop: ON"
                : "Shift-click Drop: OFF");
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
