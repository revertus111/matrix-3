package game.console;

import game.ClientConsoleBridge;
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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class OwnerPanel extends JScrollPane {

    private static final long serialVersionUID = -7153537295695898090L;
    private static final int REFRESH_DELAY_MS = 750;

    private final JLabel displayNameValue = createValueLabel();
    private final JLabel rightsValue = createValueLabel();
    private final JLabel playerStateValue = createValueLabel();

    private final JLabel rotsStatus = new JLabel("Waiting for definition loaders...");
    private final JTextArea rotsOutput = new JTextArea();
    private final JButton rotsScanButton = new JButton("Scan RoTS");
    private final JButton rotsDeepScanButton = new JButton("Deep Scan");
    private final AtomicBoolean rotsScanning = new AtomicBoolean();

    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refresh());

    public OwnerPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

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
        content.add(Box.createVerticalStrut(12));
        content.add(new BossResearchPanel());
        content.add(Box.createVerticalStrut(12));
        content.add(createRotsResearchCard());
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

        JTextArea message = createWrappedText(
                "Core account actions stay read-only here. Boss Research Lab below uses the existing Matrix3 command queue for targeted runtime probes.", 2);
        card.add(Box.createVerticalStrut(9));
        card.add(message);
        return card;
    }

    private JPanel createRotsResearchCard() {
        JPanel card = createCard("RoTS cache research");

        rotsStatus.setFont(ConsoleTheme.SMALL_FONT);
        rotsStatus.setForeground(ConsoleTheme.ACCENT);
        rotsStatus.setAlignmentX(LEFT_ALIGNMENT);

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
        outputScroll.setPreferredSize(new Dimension(320, 290));
        outputScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
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

        JTextArea note = createWrappedText(
                "Read-only cache evidence. Both scans run off the Swing thread; Deep Scan correlates render sets and GFX without naming mechanics.",
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

    private JPanel createCard(String titleText) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(14, 14, 14, 14)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

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

    private JTextArea createWrappedText(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 1);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setFont(ConsoleTheme.SMALL_FONT);
        area.setForeground(ConsoleTheme.MUTED_TEXT);
        area.setBorder(null);
        area.setAlignmentX(LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, area.getPreferredSize().height));
        return area;
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
        if (!rotsScanning.get() && rotsOutput.getText().length() == 0) {
            rotsStatus.setText(ClientConsoleRotsBridge.getReadinessLabel());
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 7995041916861782179L;

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
