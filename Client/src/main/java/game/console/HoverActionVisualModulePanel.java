package game.console;

import game.VisualExplorerCapture;
import game.VisualExplorerCapture.DrawRecord;
import game.VisualExplorerCapture.PickSnapshot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.EnumSet;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

/**
 * Hover/action module shell. It consumes real Live Pick evidence but deliberately
 * does not invent cursor/action semantics before the owning render path is traced.
 */
public final class HoverActionVisualModulePanel extends JPanel implements VisualExplorerModule {

    private static final long serialVersionUID = 2046291084081246201L;

    private final JLabel status = new JLabel();
    private final JTextArea output = new JTextArea();
    private final Timer refreshTimer = new Timer(250, e -> refresh());

    public HoverActionVisualModulePanel() {
        super(new BorderLayout());
        setBackground(ConsoleTheme.PANEL);
        setBorder(ConsoleTheme.panelPadding(14, 14, 14, 14));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);

        JPanel card = ConsoleTheme.createCard("Hover / Action Evidence");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "This module is intentionally evidence-first. Use Live Pick on the NPC hover/Attack visual. "
                + "Once the real cursor/interaction render provider is wired, its candidate will appear here and can cross-link to the owning sprite or cursor asset. "
                + "Until then this panel will not guess an ID or asset family.",
                6));
        card.add(Box.createVerticalStrut(8));

        ConsoleTheme.styleStatus(status, false);
        card.add(status);
        card.add(Box.createVerticalStrut(7));

        output.setEditable(false);
        output.setRows(10);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        output.setBackground(ConsoleTheme.INPUT);
        output.setForeground(ConsoleTheme.TEXT);
        output.setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 7, 7, 7));

        JScrollPane scroll = new JScrollPane(output);
        ConsoleTheme.styleScrollPane(scroll);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(100, 220));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        card.add(scroll);

        content.add(card);
        content.add(Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);

        refreshTimer.setCoalesce(true);
        refreshTimer.start();
        refresh();
    }

    private void refresh() {
        if (!isShowing()) {
            return;
        }

        VisualExplorerCapture.Snapshot snapshot = VisualExplorerCapture.getSnapshot();
        PickSnapshot pick = snapshot.getLastPick();

        status.setText(snapshot.isArmed()
                ? "Live Pick armed — click the hover/action visual."
                : snapshot.isEnabled() ? "Capture enabled." : "Capture disabled.");
        status.setForeground(snapshot.isArmed() ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);

        if (pick == null || !pick.hasPick()) {
            output.setText("No pick captured yet.");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("pick ").append(pick.getX()).append(',').append(pick.getY())
                .append(" cycle=").append(pick.getCycle()).append('\n');

        List<DrawRecord> candidates = pick.getCandidates();
        if (candidates.isEmpty()) {
            text.append("No registered visual candidate yet.\n")
                    .append("Cursor/interaction provider is still the next trace target.");
        } else {
            int index = 1;
            for (DrawRecord candidate : candidates) {
                text.append(index++).append(". ").append(candidate).append('\n');
            }
        }

        output.setText(text.toString());
        output.setCaretPosition(0);
    }

    @Override
    public String getModuleId() {
        return "hoverActions";
    }

    @Override
    public String getTitle() {
        return "Hover / Actions";
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public EnumSet<Capability> getCapabilities() {
        return EnumSet.of(Capability.INSPECT, Capability.REFERENCES);
    }

    @Override
    public boolean supports(VisualExplorerAssetRef asset) {
        return asset != null && (asset.getKind() == VisualExplorerAssetRef.Kind.HOVER_ACTION
                || asset.getKind() == VisualExplorerAssetRef.Kind.CURSOR);
    }

    @Override
    public void openAsset(VisualExplorerAssetRef asset) {
        // Read-only until a real provider establishes cursor/action IDs.
    }
}
