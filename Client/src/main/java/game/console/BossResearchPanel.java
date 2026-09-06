package game.console;

import game.ClientConsoleBossResearchBridge;
import game.ClientConsoleBossResearchBridge.BrotherPreset;
import game.ClientConsoleBossResearchBridge.Finding;
import game.ClientConsoleRotsBridge;
import game.ClientConsoleRotsGfxBootstrap;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;

/**
 * Dedicated Boss Research workspace. RoTS is the first preset, while command,
 * persistence, cache-evidence, and normal Matrix3 ownership stay in the
 * existing specialist bridges.
 */
final class BossResearchPanel extends JScrollPane {

    private static final long serialVersionUID = -3130969491627280185L;
    private static final int REFRESH_DELAY_MS = 750;

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

    private final JLabel rotsStatus = new JLabel("Waiting for definition loaders...");
    private final JTextArea rotsOutput = new JTextArea();
    private final JButton rotsScanButton = new JButton("Scan RoTS");
    private final JButton rotsDeepScanButton = new JButton("Deep Scan");
    private final AtomicBoolean rotsScanning = new AtomicBoolean();
    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refreshReadiness());

    BossResearchPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("BOSS RESEARCH"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("RoTS runtime probes + persistent findings + cache evidence"));
        content.add(Box.createVerticalStrut(18));
        content.add(createRuntimeResearchCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createCacheEvidenceCard());
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
                refreshReadiness();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
            }
        });

        updateBrotherSelection(BrotherPreset.DHAROK);
        refreshReadiness();
    }

    private JPanel createRuntimeResearchCard() {
        JPanel card = ConsoleTheme.createCard("RoTS runtime research");

        selectedValue.setFont(ConsoleTheme.BODY_FONT);
        selectedValue.setForeground(ConsoleTheme.TEXT);
        selectedValue.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleStatus(status, false);

        card.add(Box.createVerticalStrut(10));
        card.add(createBrotherButtons());
        card.add(Box.createVerticalStrut(8));
        card.add(selectedValue);
        card.add(Box.createVerticalStrut(8));
        card.add(createAppearanceActions());
        card.add(Box.createVerticalStrut(12));
        card.add(createProbeSection());
        card.add(Box.createVerticalStrut(12));
        card.add(createFindingSection());
        card.add(Box.createVerticalStrut(8));
        card.add(status);
        return card;
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
                "Queued transform into " + selectedBrother.getDisplayName() + "."));
        reset.addActionListener(e -> showCommandResult(
                ClientConsoleBossResearchBridge.resetAppearance(),
                "Queued player appearance restore."));

        panel.add(become);
        panel.add(reset);
        return panel;
    }

    private JPanel createProbeSection() {
        JPanel section = createInnerSection("Runtime probes");
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
        JPanel section = createInnerSection("Save finding");
        ConsoleTheme.styleTextField(mechanicField);
        ConsoleTheme.styleTextField(assetIdField);
        ConsoleTheme.styleTextField(noteField);
        ConsoleTheme.styleComboBox(assetType);
        ConsoleTheme.styleComboBox(confidence);

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

    private JPanel createCacheEvidenceCard() {
        JPanel card = ConsoleTheme.createCard("RoTS cache evidence");
        ConsoleTheme.styleStatus(rotsStatus, true);

        rotsOutput.setEditable(false);
        rotsOutput.setLineWrap(false);
        rotsOutput.setWrapStyleWord(false);
        rotsOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rotsOutput.setForeground(ConsoleTheme.TEXT);
        rotsOutput.setBackground(ConsoleTheme.PANEL);
        rotsOutput.setCaretColor(ConsoleTheme.TEXT);
        rotsOutput.setBorder(ConsoleTheme.panelPadding(8, 8, 8, 8));

        JScrollPane outputScroll = new JScrollPane(rotsOutput);
        outputScroll.setAlignmentX(LEFT_ALIGNMENT);
        outputScroll.setPreferredSize(new Dimension(320, 260));
        outputScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        outputScroll.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_AS_NEEDED);
        outputScroll.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(outputScroll);
        outputScroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));

        JButton copyButton = new JButton("Copy All");
        JButton clearButton = new JButton("Clear");
        ConsoleTheme.styleButton(rotsScanButton);
        ConsoleTheme.styleButton(rotsDeepScanButton);
        ConsoleTheme.styleButton(copyButton);
        ConsoleTheme.styleButton(clearButton);
        rotsScanButton.addActionListener(e -> runRotsScan(false));
        rotsDeepScanButton.addActionListener(e -> runRotsScan(true));
        copyButton.addActionListener(e -> copyRotsOutput());
        clearButton.addActionListener(e -> {
            rotsOutput.setText("");
            rotsStatus.setText(ClientConsoleRotsBridge.getReadinessLabel());
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
        buttons.setBackground(ConsoleTheme.CARD);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        buttons.add(rotsScanButton);
        buttons.add(rotsDeepScanButton);
        buttons.add(copyButton);
        buttons.add(clearButton);

        JTextArea note = ConsoleTheme.createWrappedText(
                "Read-only cache evidence. Scan and Deep Scan stay off the Swing thread; Deep Scan correlates render sets and GFX without assigning unverified mechanic names.",
                3);

        card.add(Box.createVerticalStrut(8));
        card.add(rotsStatus);
        card.add(Box.createVerticalStrut(8));
        card.add(buttons);
        card.add(Box.createVerticalStrut(8));
        card.add(outputScroll);
        card.add(Box.createVerticalStrut(7));
        card.add(note);
        return card;
    }

    private void runRotsScan(final boolean deep) {
        if (!rotsScanning.compareAndSet(false, true)) {
            return;
        }

        rotsScanButton.setEnabled(false);
        rotsDeepScanButton.setEnabled(false);
        rotsStatus.setText(deep ? "Deep-scanning RoTS render/GFX relationships..." : "Scanning RoTS cache definitions...");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    if (deep) {
                        ClientConsoleRotsGfxBootstrap.ensureReady();
                    }
                    result = deep
                            ? ClientConsoleRotsBridge.buildDeepResearchDump()
                            : ClientConsoleRotsBridge.buildResearchDump();
                } catch (Throwable ex) {
                    result = deep
                            ? "=== RISE OF THE SIX DEEP CACHE RESEARCH ===\n"
                            : "=== RISE OF THE SIX CLIENT CACHE RESEARCH ===\n";
                    result += "Scan failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n";
                }

                final String completed = result;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        rotsOutput.setText(completed);
                        rotsOutput.setCaretPosition(0);
                        if (deep) {
                            rotsStatus.setText(ClientConsoleRotsBridge.isDeepReady()
                                    ? "Deep Scan complete · ready to Copy All"
                                    : ClientConsoleRotsBridge.getDeepReadinessLabel());
                        } else {
                            rotsStatus.setText(ClientConsoleRotsBridge.isReady()
                                    ? "Scan complete · ready to Copy All"
                                    : ClientConsoleRotsBridge.getReadinessLabel());
                        }
                        rotsScanButton.setEnabled(true);
                        rotsDeepScanButton.setEnabled(true);
                        rotsScanning.set(false);
                    }
                });
            }
        }, deep ? "Matrix3-RoTS-Deep-Cache-Research" : "Matrix3-RoTS-Cache-Research");
        thread.setDaemon(true);
        thread.start();
    }

    private void copyRotsOutput() {
        String text = rotsOutput.getText();
        if (text == null || text.length() == 0) {
            rotsStatus.setText("Nothing to copy yet.");
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            rotsStatus.setText("Copied complete RoTS dump to clipboard.");
        } catch (IllegalStateException ex) {
            rotsStatus.setText("Clipboard is busy. Try Copy All again.");
        }
    }

    private void refreshReadiness() {
        if (!rotsScanning.get() && rotsOutput.getText().length() == 0) {
            rotsStatus.setText(ClientConsoleRotsBridge.getReadinessLabel());
        }
    }

    private JPanel createInnerSection(String titleText) {
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

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 2634844303606030829L;

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
