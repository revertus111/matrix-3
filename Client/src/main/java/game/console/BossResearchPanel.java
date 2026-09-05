package game.console;

import game.ClientConsoleBossResearchBridge;
import game.ClientConsoleBossResearchBridge.BrotherPreset;
import game.ClientConsoleBossResearchBridge.Finding;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * First Boss Research Lab vertical slice. RoTS is intentionally the first
 * preset; reusable command/persistence ownership lives in the generic bridge.
 */
final class BossResearchPanel extends JPanel {

    private static final long serialVersionUID = -3130969491627280185L;

    private BrotherPreset selectedBrother = BrotherPreset.DHAROK;

    private final JLabel selectedValue = new JLabel();
    private final JLabel status = new JLabel("Ready.");
    private final JTextField animationField = new JTextField("21941");
    private final JTextField gfxField = new JTextField();
    private final JTextField mechanicField = new JTextField("Greatest Axe");
    private final JComboBox<String> assetType = new JComboBox<String>(new String[] {
            "animation", "GFX", "projectile", "NPC/form", "sound", "timing observation"
    });
    private final JTextField assetIdField = new JTextField("21941");
    private final JComboBox<String> confidence = new JComboBox<String>(new String[] {
            "HYPOTHESIS", "verified-static", "VERIFIED"
    });
    private final JTextField noteField = new JTextField();
    private final JTextArea findingsOutput = new JTextArea();

    BossResearchPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ConsoleTheme.CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(14, 14, 14, 14)));
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel title = new JLabel("Boss Research Lab · RoTS");
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Fast runtime probes + persistent evidence");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        selectedValue.setFont(ConsoleTheme.BODY_FONT);
        selectedValue.setForeground(ConsoleTheme.TEXT);
        selectedValue.setAlignmentX(LEFT_ALIGNMENT);

        status.setFont(ConsoleTheme.SMALL_FONT);
        status.setForeground(ConsoleTheme.MUTED_TEXT);
        status.setAlignmentX(LEFT_ALIGNMENT);

        add(title);
        add(Box.createVerticalStrut(3));
        add(subtitle);
        add(Box.createVerticalStrut(10));
        add(createBrotherButtons());
        add(Box.createVerticalStrut(8));
        add(selectedValue);
        add(Box.createVerticalStrut(8));
        add(createAppearanceActions());
        add(Box.createVerticalStrut(12));
        add(createProbeSection());
        add(Box.createVerticalStrut(12));
        add(createFindingSection());
        add(Box.createVerticalStrut(8));
        add(status);

        updateBrotherSelection(BrotherPreset.DHAROK);
    }

    private JPanel createBrotherButtons() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 6, 6));
        panel.setBackground(ConsoleTheme.CARD);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        for (final BrotherPreset brother : BrotherPreset.values()) {
            JButton button = new JButton(brother.getDisplayName());
            ConsoleTheme.styleButton(button);
            button.addActionListener(e -> updateBrotherSelection(brother));
            panel.add(button);
        }
        return panel;
    }

    private JPanel createAppearanceActions() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 6, 0));
        panel.setBackground(ConsoleTheme.CARD);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JButton become = new JButton("Become NPC");
        JButton reset = new JButton("Reset Appearance");
        ConsoleTheme.styleButton(become);
        ConsoleTheme.styleButton(reset);

        become.addActionListener(e -> showCommandResult(
                ClientConsoleBossResearchBridge.becomeNpc(selectedBrother),
                "Queued transform into " + selectedBrother + "."));
        reset.addActionListener(e -> showCommandResult(
                ClientConsoleBossResearchBridge.resetAppearance(),
                "Queued player appearance restore."));

        panel.add(become);
        panel.add(reset);
        return panel;
    }

    private JPanel createProbeSection() {
        JPanel section = createInnerCard("Runtime probes");
        ConsoleTheme.styleTextField(animationField);
        ConsoleTheme.styleTextField(gfxField);

        section.add(Box.createVerticalStrut(7));
        section.add(createProbeRow("Animation", animationField, new ProbeAction() {
            @Override
            public void run(int id) {
                assetType.setSelectedItem("animation");
                assetIdField.setText(Integer.toString(id));
                showCommandResult(ClientConsoleBossResearchBridge.playAnimation(id),
                        "Queued animation " + id + " on player.");
            }
        }, true));
        section.add(Box.createVerticalStrut(7));
        section.add(createProbeRow("GFX", gfxField, new ProbeAction() {
            @Override
            public void run(int id) {
                assetType.setSelectedItem("GFX");
                assetIdField.setText(Integer.toString(id));
                showCommandResult(ClientConsoleBossResearchBridge.playGraphics(id),
                        "Queued GFX " + id + " on player.");
            }
        }, false));
        return section;
    }

    private JPanel createProbeRow(String labelText, final JTextField field,
            final ProbeAction action, boolean includeStop) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ConsoleTheme.CARD);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setPreferredSize(new Dimension(64, 34));

        JPanel buttons = new JPanel(new GridLayout(1, includeStop ? 4 : 3, 4, 0));
        buttons.setBackground(ConsoleTheme.CARD);

        JButton previous = new JButton("-");
        JButton play = new JButton("Play");
        JButton next = new JButton("+");
        ConsoleTheme.styleButton(previous);
        ConsoleTheme.styleButton(play);
        ConsoleTheme.styleButton(next);

        previous.addActionListener(e -> adjustAndRun(field, -1, action));
        play.addActionListener(e -> runProbe(field, action));
        next.addActionListener(e -> adjustAndRun(field, 1, action));
        buttons.add(previous);
        buttons.add(play);
        buttons.add(next);

        if (includeStop) {
            JButton stop = new JButton("Stop");
            ConsoleTheme.styleButton(stop);
            stop.addActionListener(e -> showCommandResult(
                    ClientConsoleBossResearchBridge.stopAnimation(),
                    "Queued animation stop."));
            buttons.add(stop);
        }

        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private JPanel createFindingSection() {
        JPanel section = createInnerCard("Save finding");
        ConsoleTheme.styleTextField(mechanicField);
        ConsoleTheme.styleTextField(assetIdField);
        ConsoleTheme.styleTextField(noteField);
        styleCombo(assetType);
        styleCombo(confidence);

        section.add(Box.createVerticalStrut(7));
        section.add(createFieldRow("Mechanic", mechanicField));
        section.add(Box.createVerticalStrut(6));

        JPanel assetRow = new JPanel(new GridLayout(1, 2, 6, 0));
        assetRow.setBackground(ConsoleTheme.CARD);
        assetRow.setAlignmentX(LEFT_ALIGNMENT);
        assetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        assetRow.add(assetType);
        assetRow.add(assetIdField);
        section.add(assetRow);
        section.add(Box.createVerticalStrut(6));
        section.add(confidence);
        section.add(Box.createVerticalStrut(6));
        section.add(createFieldRow("Note", noteField));
        section.add(Box.createVerticalStrut(7));

        JButton save = new JButton("Save Finding");
        ConsoleTheme.styleButton(save);
        save.setAlignmentX(LEFT_ALIGNMENT);
        save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        save.addActionListener(e -> saveFinding());
        section.add(save);
        section.add(Box.createVerticalStrut(8));

        findingsOutput.setEditable(false);
        findingsOutput.setLineWrap(true);
        findingsOutput.setWrapStyleWord(true);
        findingsOutput.setFont(ConsoleTheme.SMALL_FONT);
        findingsOutput.setForeground(ConsoleTheme.TEXT);
        findingsOutput.setBackground(ConsoleTheme.INPUT);
        findingsOutput.setCaretColor(ConsoleTheme.TEXT);
        findingsOutput.setBorder(ConsoleTheme.panelPadding(7, 7, 7, 7));

        JScrollPane scroll = new JScrollPane(findingsOutput);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(320, 120));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        ConsoleTheme.styleScrollPane(scroll);
        scroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        section.add(scroll);
        return section;
    }

    private JPanel createInnerCard(String titleText) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ConsoleTheme.CARD);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel label = new JLabel(titleText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.ACCENT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(label);
        return panel;
    }

    private JPanel createFieldRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ConsoleTheme.CARD);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setPreferredSize(new Dimension(64, 34));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setFont(ConsoleTheme.BODY_FONT);
        combo.setForeground(ConsoleTheme.TEXT);
        combo.setBackground(ConsoleTheme.INPUT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    }

    private void updateBrotherSelection(BrotherPreset brother) {
        selectedBrother = brother;
        selectedValue.setText("Selected: " + brother.getDisplayName() + " · NPC " + brother.getNpcId());
        if (brother == BrotherPreset.DHAROK) {
            mechanicField.setText("Greatest Axe");
        } else if (brother == BrotherPreset.GUTHAN) {
            mechanicField.setText("Impale");
        } else {
            mechanicField.setText("");
        }
        refreshFindings();
        status.setText("Ready · " + brother.getDisplayName());
    }

    private void adjustAndRun(JTextField field, int delta, ProbeAction action) {
        Integer value = parseId(field);
        if (value == null) {
            return;
        }
        int next = Math.max(-1, value.intValue() + delta);
        field.setText(Integer.toString(next));
        action.run(next);
    }

    private void runProbe(JTextField field, ProbeAction action) {
        Integer value = parseId(field);
        if (value != null) {
            action.run(value.intValue());
        }
    }

    private Integer parseId(JTextField field) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.length() == 0) {
            status.setText("Enter an ID first.");
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            status.setText("ID must be a number.");
            return null;
        }
    }

    private void saveFinding() {
        String result = ClientConsoleBossResearchBridge.saveFinding(
                selectedBrother,
                mechanicField.getText(),
                (String) assetType.getSelectedItem(),
                assetIdField.getText(),
                (String) confidence.getSelectedItem(),
                noteField.getText());
        if (result != null) {
            status.setText(result);
            return;
        }
        noteField.setText("");
        refreshFindings();
        status.setText("Saved finding · " + ClientConsoleBossResearchBridge.getFindingsPath());
    }

    private void refreshFindings() {
        List<Finding> findings = ClientConsoleBossResearchBridge.loadFindings(selectedBrother);
        if (findings.isEmpty()) {
            findingsOutput.setText("No saved findings for " + selectedBrother.getDisplayName() + " yet.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (Finding finding : findings) {
            builder.append(finding.getConfidence()).append(" · ")
                    .append(finding.getMechanic()).append(" · ")
                    .append(finding.getAssetType()).append(' ')
                    .append(finding.getAssetId());
            if (finding.getNote() != null && finding.getNote().length() > 0) {
                builder.append(" · ").append(finding.getNote());
            }
            builder.append("\n");
        }
        findingsOutput.setText(builder.toString());
        findingsOutput.setCaretPosition(0);
    }

    private void showCommandResult(String error, String success) {
        status.setText(error == null ? success : error);
    }

    private interface ProbeAction {
        void run(int id);
    }
}
