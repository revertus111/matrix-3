package game.console.bosslabs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import game.console.ConsoleTheme;

/**
 * Developer-only BossLabs encounter testing controls.
 *
 * The server owns the exact test-spawned boss instance. This panel only sends
 * explicit admin testing requests through the existing BossLabs bridge.
 */
public final class BossLabsTestingPanel extends JPanel implements BossLabsClientBridge.TestingListener {

    private static final long serialVersionUID = 4906224790438247976L;

    private final JTextField healthPercentField = new JTextField("50");
    private final JLabel selectedPhaseValue = createValueLabel("No phase selected");
    private final JLabel selectedAttackValue = createValueLabel("No attack selected");
    private final JLabel status = new JLabel("Select an NPC to enable encounter testing.");

    private final JButton spawnButton = new JButton("Spawn Boss Here");
    private final JButton resetButton = new JButton("Reset Encounter");
    private final JButton setHealthButton = new JButton("Set Boss HP %");
    private final JButton forcePhaseButton = new JButton("Enter Selected Phase");
    private final JButton forceAttackButton = new JButton("Test Selected Attack");
    private final JButton clearHazardsButton = new JButton("Clear Hazards");
    private final JButton clearMinionsButton = new JButton("Clear Minions");

    private int selectedNpcId = -1;
    private boolean liveBossLabsDefinition;
    private String selectedPhaseId = "";
    private String selectedAttackId = "";

    public BossLabsTestingPanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        add(buildScrollContent(), BorderLayout.CENTER);
        installActions();
        updateEnabledState();
    }

    @Override
    public void onTestingSelection(int npcId, boolean liveBossLabs) {
        setSelection(npcId, liveBossLabs);
    }

    @Override
    public void onTestingSelectionCleared() {
        clearSelection();
    }

    @Override
    public void onTestingActionResult(BossLabsClientBridge.ActionResult result) {
        handleActionResult(result);
    }

    public void setSelection(int npcId, boolean liveBossLabs) {
        selectedNpcId = npcId;
        liveBossLabsDefinition = liveBossLabs && npcId >= 0;
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText(liveBossLabsDefinition
                ? "Ready. Select a phase or attack in the editor, then test it here without retyping IDs."
                : "Ready for Matrix3 spawn/reset/HP. Apply a BossLabs definition live to test authored phases and attacks.");
        updateEnabledState();
    }

    public void setAuthoringSelection(String phaseId, String attackId) {
        selectedPhaseId = safe(phaseId);
        selectedAttackId = safe(attackId);
        selectedPhaseValue.setText(selectedPhaseId.length() == 0 ? "No phase selected" : creatorLabel(selectedPhaseId));
        selectedAttackValue.setText(selectedAttackId.length() == 0 ? "No attack selected" : creatorLabel(selectedAttackId));
        updateEnabledState();
    }

    public void clearSelection() {
        selectedNpcId = -1;
        liveBossLabsDefinition = false;
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("Select an NPC to enable encounter testing.");
        updateEnabledState();
    }

    public boolean handleActionResult(BossLabsClientBridge.ActionResult result) {
        if (result == null)
            return false;
        if (result.getNpcId() >= 0 && selectedNpcId >= 0 && result.getNpcId() != selectedNpcId)
            return false;
        status.setForeground(result.isSuccess() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
        status.setText(result.getMessage());
        return true;
    }

    private JComponent buildScrollContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(12, 12, 12, 12));

        content.add(createControlCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createInfoCard());
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(scroll);
        return scroll;
    }

    private JComponent createControlCard() {
        JPanel card = createCard("Encounter testing");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        styleField(healthPercentField, "1-100. Changes only the controlled test NPC HP.");
        addFormRow(form, 0, "Boss HP %", healthPercentField);
        addFormRow(form, 1, "Selected phase", selectedPhaseValue);
        addFormRow(form, 2, "Selected attack", selectedAttackValue);

        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        addButton(buttons, spawnButton, 0, 0);
        addButton(buttons, resetButton, 1, 0);
        addButton(buttons, setHealthButton, 0, 1);
        addButton(buttons, forcePhaseButton, 1, 1);
        addButton(buttons, forceAttackButton, 0, 2);
        addButton(buttons, clearHazardsButton, 1, 2);
        addButton(buttons, clearMinionsButton, 0, 3);

        forcePhaseButton.setToolTipText("Uses the phase currently selected in BossLabs. Apply Live first if the draft selection is new or renamed.");
        forceAttackButton.setToolTipText("Uses the attack currently selected in BossLabs. Apply Live first if the draft selection is new or renamed.");

        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.MUTED_TEXT);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        body.add(form);
        body.add(Box.createVerticalStrut(10));
        body.add(buttons);
        body.add(Box.createVerticalStrut(8));
        body.add(status);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent createInfoCard() {
        JPanel card = createCard("Testing rules");
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        addInfoLine(body, "Spawn, Reset, and Set Boss HP work for any valid inspected Matrix3 NPC. Selected-phase/attack testing, hazards, and minions require a live BossLabs definition.");
        addInfoLine(body, "Phase and attack testing follow the current BossLabs editor selection. You should never need to copy an internal Phase ID or Attack ID into this tab.");
        addInfoLine(body, "Controls affect only the NPC copy spawned by your own Testing tab session; BossLabs never searches for an arbitrary world NPC by ID.");
        addInfoLine(body, "Enter Selected Phase changes HP into that phase. Normal entry/exit actions run on the next normal BossLabs combat opportunity.");
        addInfoLine(body, "Test Selected Attack executes that authored attack immediately through the normal BossLabs attack path without changing weighted rotation/cooldown history.");
        addInfoLine(body, "Clear Hazards invalidates BossLabs-owned delayed tile work. Clear Minions removes only NPCs owned by this exact test encounter.");
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void installActions() {
        spawnButton.addActionListener(e -> {
            setPendingStatus("Spawning controlled test NPC...");
            BossLabsClientBridge.requestTestingSpawn(selectedNpcId);
        });
        resetButton.addActionListener(e -> {
            setPendingStatus("Resetting controlled test NPC...");
            BossLabsClientBridge.requestTestingReset(selectedNpcId);
        });
        setHealthButton.addActionListener(e -> setHealth());
        forcePhaseButton.addActionListener(e -> forceSelectedPhase());
        forceAttackButton.addActionListener(e -> forceSelectedAttack());
        clearHazardsButton.addActionListener(e -> {
            setPendingStatus("Clearing BossLabs delayed tile work...");
            BossLabsClientBridge.requestTestingClearHazards(selectedNpcId);
        });
        clearMinionsButton.addActionListener(e -> {
            setPendingStatus("Clearing encounter-owned minions...");
            BossLabsClientBridge.requestTestingClearMinions(selectedNpcId);
        });
    }

    private void setHealth() {
        Integer percent = parseInteger(healthPercentField.getText());
        if (percent == null || percent.intValue() < 1 || percent.intValue() > 100) {
            setLocalError("Boss HP percent must be a whole number between 1 and 100.");
            return;
        }
        setPendingStatus("Setting controlled test NPC HP...");
        BossLabsClientBridge.requestTestingSetHealth(selectedNpcId, percent.intValue());
    }

    private void forceSelectedPhase() {
        if (selectedPhaseId.length() == 0) {
            setLocalError("Select a phase in BossLabs first.");
            return;
        }
        setPendingStatus("Entering selected phase " + creatorLabel(selectedPhaseId) + "...");
        BossLabsClientBridge.requestTestingForcePhase(selectedNpcId, selectedPhaseId);
    }

    private void forceSelectedAttack() {
        if (selectedPhaseId.length() == 0 || selectedAttackId.length() == 0) {
            setLocalError("Select an attack in BossLabs first.");
            return;
        }
        setPendingStatus("Testing selected attack " + creatorLabel(selectedAttackId) + "...");
        BossLabsClientBridge.requestTestingForceAttack(selectedNpcId, selectedPhaseId, selectedAttackId);
    }

    private void setPendingStatus(String message) {
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText(message);
    }

    private void setLocalError(String message) {
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText(message);
    }

    private void updateEnabledState() {
        boolean selected = selectedNpcId >= 0;
        boolean bossLabsEnabled = selected && liveBossLabsDefinition;
        spawnButton.setEnabled(selected);
        resetButton.setEnabled(selected);
        setHealthButton.setEnabled(selected);
        forcePhaseButton.setEnabled(bossLabsEnabled && selectedPhaseId.length() > 0);
        forceAttackButton.setEnabled(bossLabsEnabled && selectedPhaseId.length() > 0 && selectedAttackId.length() > 0);
        clearHazardsButton.setEnabled(bossLabsEnabled);
        clearMinionsButton.setEnabled(bossLabsEnabled);
    }

    private JPanel createCard(String titleText) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(12, 12, 12, 12)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        card.add(title, BorderLayout.NORTH);
        return card;
    }

    private void addInfoLine(JPanel body, String text) {
        if (body.getComponentCount() > 0)
            body.add(Box.createVerticalStrut(6));
        JLabel line = new JLabel("<html>" + escapeHtml(text) + "</html>");
        line.setFont(ConsoleTheme.SMALL_FONT);
        line.setForeground(ConsoleTheme.MUTED_TEXT);
        line.setAlignmentX(LEFT_ALIGNMENT);
        body.add(line);
    }

    private void addButton(JPanel panel, JButton button, int x, int y) {
        ConsoleTheme.styleButton(button);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 4, 4, 4);
        panel.add(button, constraints);
    }

    private void styleField(JTextField field, String tooltip) {
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(240, 34));
        ConsoleTheme.styleTextField(field);
    }

    private void addFormRow(JPanel panel, int row, String labelText, JComponent component) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(5, 0, 5, 12);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(5, 0, 5, 0);
        panel.add(component, fieldConstraints);
    }

    private static JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(trim(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String creatorLabel(String id) {
        String value = safe(id).trim().replace('_', ' ').replace('-', ' ');
        if (value.length() == 0)
            return "Unnamed";
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character)) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != ' ')
                    result.append(' ');
                capitalize = true;
            } else {
                result.append(capitalize ? Character.toUpperCase(character) : character);
                capitalize = false;
            }
        }
        return result.toString();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
