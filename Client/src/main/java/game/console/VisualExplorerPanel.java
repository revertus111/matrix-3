package game.console;

import game.ClientConsoleInterfaceBridge;
import game.VisualExplorerCapture;
import game.VisualExplorerCapture.DrawRecord;
import game.VisualExplorerCapture.PickSnapshot;
import game.VisualExplorerCapture.Snapshot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.Scrollable;

/**
 * Unified Matrix3 visual-asset exploration workspace.
 *
 * Bundle 1.1 establishes the shell, existing Interface Browser handoff, and
 * bounded Live Pick capture contract. Asset-specific render providers are
 * added later without making this panel a renderer.
 */
public final class VisualExplorerPanel extends JScrollPane {

    private static final long serialVersionUID = 5139784996103175288L;

    private final Runnable openInterfaceEditorAction;
    private final JLabel status = new JLabel();
    private final JTextArea pickOutput = new JTextArea();
    private final JLabel selectedInterface = new JLabel("No interface selected.");
    private final Timer refreshTimer;

    public VisualExplorerPanel(Runnable openInterfaceEditorAction) {
        this.openInterfaceEditorAction = openInterfaceEditorAction;

        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(18, 16, 18, 16));

        content.add(ConsoleTheme.titleLabel("VISUAL EXPLORER"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel(
                "Interfaces, sprites, GFX, models, materials, fonts, cursors and live screen picks"));
        content.add(Box.createVerticalStrut(16));

        content.add(createLivePickCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createAssetFamiliesCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createInterfaceBridgeCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createArchitectureCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        refreshTimer = new Timer(250, e -> refreshCaptureStatus());
        refreshTimer.setRepeats(true);
        refreshTimer.start();
        refreshCaptureStatus();
    }

    private JPanel createLivePickCard() {
        JPanel card = ConsoleTheme.createCard("Live Pick");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Enable capture, arm one pick, then click the visual in the game canvas. "
                + "The armed click is consumed so it should inspect instead of issuing the normal game action. "
                + "Escape cancels an armed pick. Bundle 1.1 records the point and resolves any registered draw candidates; "
                + "sprite/cursor/overlay render providers are the next priority.",
                6));
        card.add(Box.createVerticalStrut(8));

        JButton enable = new JButton("Enable Capture");
        JButton disable = new JButton("Disable Capture");
        JButton arm = new JButton("Arm One-Click Pick");
        JButton clear = new JButton("Clear Capture");

        styleButton(enable);
        styleButton(disable);
        styleButton(arm);
        styleButton(clear);

        enable.addActionListener(e -> {
            VisualExplorerCapture.setCaptureEnabled(true);
            refreshCaptureStatus();
        });
        disable.addActionListener(e -> {
            VisualExplorerCapture.setCaptureEnabled(false);
            refreshCaptureStatus();
        });
        arm.addActionListener(e -> {
            VisualExplorerCapture.armOneClickPick();
            refreshCaptureStatus();
        });
        clear.addActionListener(e -> {
            VisualExplorerCapture.clear();
            refreshCaptureStatus();
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(enable);
        buttons.add(disable);
        buttons.add(arm);
        buttons.add(clear);
        card.add(buttons);

        card.add(Box.createVerticalStrut(8));
        ConsoleTheme.styleStatus(status, false);
        status.setAlignmentX(LEFT_ALIGNMENT);
        card.add(status);

        card.add(Box.createVerticalStrut(8));
        pickOutput.setEditable(false);
        pickOutput.setRows(8);
        pickOutput.setLineWrap(false);
        pickOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        pickOutput.setBackground(ConsoleTheme.INPUT);
        pickOutput.setForeground(ConsoleTheme.TEXT);
        pickOutput.setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 7, 7, 7));

        JScrollPane outputScroll = new JScrollPane(pickOutput);
        outputScroll.setAlignmentX(LEFT_ALIGNMENT);
        outputScroll.setPreferredSize(new Dimension(100, 170));
        outputScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        ConsoleTheme.styleScrollPane(outputScroll);
        card.add(outputScroll);
        return card;
    }

    private JPanel createAssetFamiliesCard() {
        JPanel card = ConsoleTheme.createCard("Asset Families");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "One workspace, separate providers. Visual Explorer aggregates candidates instead of assuming every on-screen visual is an interface.",
                4));
        card.add(Box.createVerticalStrut(8));

        addFamily(card, "Interfaces", "FOUNDATION", "Existing cache/open-interface browser is wired below.");
        addFamily(card, "Sprites", "NEXT", "Direct sprite browsing + draw attribution.");
        addFamily(card, "Cursors / interaction icons", "NEXT", "Priority path for the NPC hover/Attack visual.");
        addFamily(card, "Overlays / headbars", "PLANNED", "NPC/player overhead and world-overlay attribution.");
        addFamily(card, "GFX", "PLANNED", "Spot-animation definition + world draw attribution.");
        addFamily(card, "Models", "PLANNED", "Model identity/definition context and preview handoff.");
        addFamily(card, "Textures / materials", "PLANNED", "Material/texture identity and usage context.");
        addFamily(card, "Fonts / text", "PLANNED", "Font/text draw attribution.");
        return card;
    }

    private JPanel createInterfaceBridgeCard() {
        JPanel card = ConsoleTheme.createCard("Interfaces — Existing Authority");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Visual Explorer reuses the current Interface Browser/Editor instead of creating a second interface-definition owner.",
                3));
        card.add(Box.createVerticalStrut(8));

        selectedInterface.setFont(ConsoleTheme.SMALL_FONT);
        selectedInterface.setForeground(ConsoleTheme.MUTED_TEXT);
        selectedInterface.setAlignmentX(LEFT_ALIGNMENT);
        card.add(selectedInterface);
        card.add(Box.createVerticalStrut(7));

        JButton browse = new JButton("Browse All Interfaces");
        JButton openEditor = new JButton("Open Interface Editor");
        styleButton(browse);
        styleButton(openEditor);

        browse.addActionListener(e -> InterfaceBrowserDialog.open(
                VisualExplorerPanel.this,
                ClientConsoleInterfaceBridge.getLatestCatalog(),
                interfaceId -> {
                    selectedInterface.setText("Selected interface " + interfaceId
                            + " - snapshot requested.");
                    ClientConsoleInterfaceBridge.requestSnapshot(interfaceId);
                }));

        openEditor.addActionListener(e -> {
            if (openInterfaceEditorAction != null) {
                openInterfaceEditorAction.run();
            }
        });

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(browse);
        buttons.add(openEditor);
        card.add(buttons);
        return card;
    }

    private JPanel createArchitectureCard() {
        JPanel card = ConsoleTheme.createCard("Capture Contract");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Render hooks publish only visual type, asset ID, final screen bounds, source label and draw order. "
                + "Visual Explorer keeps at most 1,024 records for the current client cycle and never becomes the renderer/cache owner. "
                + "A pick returns every recorded visual under that pixel in top-most draw order so layered UI/world visuals can be peeled apart.",
                6));
        return card;
    }

    private void addFamily(JPanel card, String name, String state, String detail) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JLabel left = new JLabel(name);
        left.setFont(ConsoleTheme.BODY_FONT);
        left.setForeground(ConsoleTheme.TEXT);

        JLabel right = new JLabel(state);
        right.setFont(ConsoleTheme.SMALL_FONT);
        right.setForeground(ConsoleTheme.ACCENT);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        card.add(row);

        JLabel detailLabel = new JLabel("<html>" + detail + "</html>");
        detailLabel.setFont(ConsoleTheme.SMALL_FONT);
        detailLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        detailLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(detailLabel);
        card.add(Box.createVerticalStrut(6));
    }

    private void refreshCaptureStatus() {
        Snapshot snapshot = VisualExplorerCapture.getSnapshot();

        StringBuilder statusText = new StringBuilder();
        statusText.append(snapshot.isEnabled() ? "Capture ON" : "Capture OFF");
        statusText.append(snapshot.isArmed() ? " | PICK ARMED" : " | pick idle");
        statusText.append(" | frame records=").append(snapshot.getRecordCount());
        if (snapshot.getDroppedRecordCount() > 0) {
            statusText.append(" | dropped=").append(snapshot.getDroppedRecordCount());
        }
        status.setText(statusText.toString());
        status.setForeground(snapshot.isArmed() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);

        PickSnapshot pick = snapshot.getLastPick();
        if (pick == null || !pick.hasPick()) {
            pickOutput.setText(
                    "No pick captured yet.\n"
                    + "Enable Capture -> Arm One-Click Pick -> click a game visual.");
            return;
        }

        StringBuilder output = new StringBuilder(512);
        output.append("PICK x=").append(pick.getX())
                .append(" y=").append(pick.getY())
                .append(" cycle=").append(pick.getCycle()).append('\n');

        List<DrawRecord> candidates = pick.getCandidates();
        if (candidates.isEmpty()) {
            output.append("No registered draw candidate intersected this pixel.\n")
                    .append("This is expected until the relevant render family is instrumented.\n")
                    .append("Next priority: sprite/cursor/interaction-overlay attribution.");
        } else {
            output.append("Candidates (top-most first): ").append(candidates.size()).append('\n');
            int index = 1;
            for (DrawRecord candidate : candidates) {
                output.append(index++).append(". ").append(candidate).append('\n');
            }
        }

        pickOutput.setText(output.toString());
        pickOutput.setCaretPosition(0);
    }

    private void styleButton(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setFocusable(false);
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = -5455080804705943786L;

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
