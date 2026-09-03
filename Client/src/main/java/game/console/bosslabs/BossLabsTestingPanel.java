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
    private final JTextField phaseIdField = new JTextField();
    private final JTextField attackIdField = new JTextField();
    private final JLabel status = new JLabel("Select a live BossLabs definition to enable encounter testing.");

    private final JButton spawnButton = new JButton("Spawn Boss Here");
    private final JButton resetButton = new JButton("Reset Encounter");
    private final JButton setHealthButton = new JButton("Set Boss HP %");
    private final JButton forcePhaseButton = new JButton("Force Phase");
    private final JButton forceAttackButton = new JButton("Trigger Attack");
    private final JButton clearHazardsButton = new JButton("Clear Hazards");
    private final JButton clearMinionsButton = new JButton("Clear Minions");

    private int selectedNpcId = -1;
    private boolean liveBossLabsDefinition;

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
                ? "Ready. Spawn a controlled test copy of this BossLabs boss near your player."
                : "Apply a live BossLabs definition before using encounter testing controls.");
        updateEnabledState();
    }

    public void clearSelection() {
        selectedNpcId = -1;
        liveBossLabsDefinition = false;
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setText("Select a live BossLabs definition to enable encounter testing.");
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
        styleField(healthPercentField, "1-100. Changes only the controlled test boss HP.");
        styleField(phaseIdField, "Exact BossLabs phase ID, for example phase_2");
        styleField(attackIdField, "Exact attack ID inside the phase above");
        addFormRow(form, 0, "Boss HP %", healthPercentField);
        addFormRow(form, 1, "Phase ID", phaseIdField);
        addFormRow(form, 2, "Attack ID", attackIdField);

        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        addButton(buttons, spawnButton, 0, 0);
        addButton(buttons, resetButton, 1, 0);
        addButton(buttons, setHealthButton, 0, 1);
        addButton(buttons, forcePhaseButton, 1, 1);
        addButton(buttons, forceAttackButton, 0, 2);
        addButton(buttons, clearHazardsButton, 1, 2);
        addButton(buttons, clearMinionsButton, 0, 3);

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
        addInfoLine(body, "Controls affect only the boss copy spawned by your own Testing tab session; BossLabs never searches for an arbitrary world NPC by ID.");
        addInfoLine(body, "Force Phase changes HP into the requested phase. Normal entry/exit actions run on the next normal BossLabs combat opportunity.");
        addInfoLine(body, "Trigger Attack executes the selected authored attack immediately through the normal BossLabs attack path without changing weighted rotation/cooldown history.");
        addInfoLine(body, "Clear Hazards invalidates BossLabs-owned delayed tile work, including a pending telegraphed impact. It does not own normal Matrix3 single-target delayed hits.");
        addInfoLine(body, "Clear Minions finish-removes only NPCs owned by this exact test encounter. Cleanup removal does not create death drops.");
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void installActions() {
        spawnButton.addActionListener(e -> {
            setPendingStatus("Spawning controlled BossLabs test boss...");
            BossLabsClientBridge.requestTestingSpawn(selectedNpcId);
        });
        resetButton.addActionListener(e -> {
            setPendingStatus("Resetting controlled BossLabs test encounter...");
            BossLabsClientBridge.requestTestingReset(selectedNpcId);
        });
        setHealthButton.addActionListener(e -> setHealth());
        forcePhaseButton.addActionListener(e -> forcePhase());
        forceAttackButton.addActionListener(e -> forceAttack());
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
        setPendingStatus("Setting controlled test boss HP...");
        BossLabsClientBridge.requestTestingSetHealth(selectedNpcId, percent.intValue());
    }

    private void forcePhase() {
        String phaseId = trim(phaseIdField.getText());
        if (phaseId.length() == 0) {
            setLocalError("Enter a Phase ID first.");
            return;
        }
        setPendingStatus("Forcing test boss HP into phase " + phaseId + "...");
        BossLabsClientBridge.requestTestingForcePhase(selectedNpcId, phaseId);
    }

    private void forceAttack() {
        String phaseId = trim(phaseIdField.getText());
        String attackId = trim(attackIdField.getText());
        if (phaseId.length() == 0 || attackId.length() == 0) {
            setLocalError("Enter both Phase ID and Attack ID first.");
            return;
        }
        setPendingStatus("Triggering attack " + attackId + " from phase " + phaseId + "...");
        BossLabsClientBridge.requestTestingForceAttack(selectedNpcId, phaseId, attackId);
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
        boolean enabled = liveBossLabsDefinition && selectedNpcId >= 0;
        spawnButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        setHealthButton.setEnabled(enabled);
        forcePhaseButton.setEnabled(enabled);
        forceAttackButton.setEnabled(enabled);
        clearHazardsButton.setEnabled(enabled);
        clearMinionsButton.setEnabled(enabled);
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

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(trim(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
