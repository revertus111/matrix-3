package game.console;

import game.atlas.AtlasInvestigationIndex;
import game.atlas.AtlasTraceCatalog;
import game.atlas.AtlasTraceCatalog.TraceEntry;
import game.atlas.AtlasTraceCorrelationEngine;
import game.atlas.AtlasTraceCorrelationEngine.CorrelationResult;
import game.atlas.AtlasWorkspace;
import game.atlas.ClientAtlasTraceControl;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/**
 * Client Console UI over the existing Client Atlas runtime-trace/correlation
 * authorities. Trace control, persistence, and correlation remain owned by
 * game.atlas; this panel only browses and invokes them.
 */
public final class AtlasRuntimeEvidencePanel extends JScrollPane {

    private static final long serialVersionUID = 4297581995747693369L;

    private final DefaultListModel<TraceEntry> traceModel = new DefaultListModel<TraceEntry>();
    private final JList<TraceEntry> traceList = new JList<TraceEntry>(traceModel);
    private final JTextArea summaryArea = createReadOnlyArea();
    private final JLabel statusLabel = new JLabel("Runtime evidence is loaded on demand.");

    private final JButton traceControlButton = new JButton("Runtime Trace Control");
    private final JButton refreshButton = new JButton("Refresh traces");
    private final JButton correlateSelectedButton = new JButton("Correlate selected");
    private final JButton correlateLatestButton = new JButton("Correlate latest");

    private final Object workspaceLock = new Object();
    private volatile AtlasWorkspace workspace;
    private boolean firstShow = true;

    public AtlasRuntimeEvidencePanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(16, 14, 18, 14));
        content.setMinimumSize(new Dimension(0, 0));

        JLabel title = ConsoleTheme.titleLabel("RUNTIME EVIDENCE");
        JLabel subtitle = ConsoleTheme.subtitleLabel("Browse saved traces and run the existing Atlas correlation gate");
        ConsoleTheme.styleStatus(statusLabel, false);

        content.add(title);
        content.add(Box.createVerticalStrut(3));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(12));
        content.add(buildTraceCard());
        content.add(Box.createVerticalStrut(10));
        content.add(buildSummaryCard());
        content.add(Box.createVerticalStrut(8));
        content.add(statusLabel);
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        configureInteractions();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0 || !isShowing()) {
                return;
            }
            if (firstShow) {
                firstShow = false;
                refreshTraces();
            }
        });
    }

    private JPanel buildTraceCard() {
        JPanel card = ConsoleTheme.createCard("Saved traces");

        traceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        traceList.setFont(ConsoleTheme.SMALL_FONT);
        traceList.setForeground(ConsoleTheme.TEXT);
        traceList.setBackground(ConsoleTheme.INPUT);
        traceList.setSelectionForeground(ConsoleTheme.TEXT);
        traceList.setSelectionBackground(ConsoleTheme.ACCENT_DARK);
        traceList.setFixedCellHeight(30);
        traceList.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        traceList.setCellRenderer(new TraceRenderer());

        JScrollPane traceScroll = new JScrollPane(traceList);
        traceScroll.setAlignmentX(LEFT_ALIGNMENT);
        traceScroll.setPreferredSize(new Dimension(1, 180));
        traceScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        ConsoleTheme.styleScrollPane(traceScroll);
        traceScroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));

        ConsoleTheme.styleButton(traceControlButton);
        ConsoleTheme.styleButton(refreshButton);
        ConsoleTheme.styleButton(correlateSelectedButton);
        ConsoleTheme.styleButton(correlateLatestButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.add(traceControlButton);
        actions.add(refreshButton);
        actions.add(correlateSelectedButton);
        actions.add(correlateLatestButton);

        card.add(Box.createVerticalStrut(10));
        card.add(traceScroll);
        card.add(Box.createVerticalStrut(10));
        card.add(actions);
        return card;
    }

    private JPanel buildSummaryCard() {
        JPanel card = ConsoleTheme.createCard("Correlation summary");
        summaryArea.setText(
                "Select a saved trace and correlate it against the current Atlas index.\n"
                + "Correlation remains read-only and does not create semantic evidence.");
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setAlignmentX(LEFT_ALIGNMENT);
        summaryScroll.setPreferredSize(new Dimension(1, 210));
        summaryScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        ConsoleTheme.styleScrollPane(summaryScroll);
        summaryScroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        card.add(Box.createVerticalStrut(10));
        card.add(summaryScroll);
        return card;
    }

    private void configureInteractions() {
        traceControlButton.addActionListener(e -> ClientAtlasTraceControl.launch());
        refreshButton.addActionListener(e -> refreshTraces());
        correlateSelectedButton.addActionListener(e -> correlateSelected());
        correlateLatestButton.addActionListener(e -> correlateLatest());

        traceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    correlateSelected();
                }
            }
        });
    }

    private AtlasWorkspace ensureWorkspace() throws IOException {
        synchronized (workspaceLock) {
            if (workspace != null) {
                return workspace;
            }
            Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
            AtlasWorkspace loaded = new AtlasWorkspace(clientRoot);
            loaded.ensureLayout();
            workspace = loaded;
            return loaded;
        }
    }

    private void refreshTraces() {
        setTraceBusy(true);
        setStatus("Loading saved traces...", false);
        final Path previousSelection = selectedTracePath();

        new SwingWorker<List<TraceEntry>, Void>() {
            private Exception failure;

            @Override
            protected List<TraceEntry> doInBackground() {
                try {
                    return AtlasTraceCatalog.list(ensureWorkspace());
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (failure != null) {
                        traceModel.clear();
                        setStatus("Trace refresh failed: " + failure.getMessage(), true);
                        return;
                    }
                    List<TraceEntry> entries = get();
                    traceModel.clear();
                    for (TraceEntry entry : entries) {
                        traceModel.addElement(entry);
                    }
                    if (previousSelection != null) {
                        selectTrace(previousSelection);
                    }
                    if (traceList.getSelectedIndex() < 0 && traceModel.size() > 0) {
                        traceList.setSelectedIndex(0);
                    }
                    if (entries.isEmpty()) {
                        summaryArea.setText("No saved Client Atlas traces exist yet.\nOpen Runtime Trace Control to create one.");
                    }
                    setStatus(entries.size() + " saved trace" + (entries.size() == 1 ? "" : "s")
                            + " shown (max " + AtlasTraceCatalog.MAX_VISIBLE_TRACES + ").", false);
                } catch (Exception ex) {
                    setStatus("Trace refresh failed: " + ex.getMessage(), true);
                } finally {
                    setTraceBusy(false);
                }
            }
        }.execute();
    }

    private void correlateSelected() {
        TraceEntry entry = traceList.getSelectedValue();
        if (entry == null) {
            setStatus("Select a saved trace first.", true);
            return;
        }
        correlate(entry.getPath(), false);
    }

    private void correlateLatest() {
        correlate(null, true);
    }

    private void correlate(final Path requestedTrace, final boolean latest) {
        setTraceBusy(true);
        setStatus(latest ? "Correlating latest saved trace..." : "Correlating selected trace...", false);

        new SwingWorker<CorrelationResult, Void>() {
            private Exception failure;
            private Path tracePath;

            @Override
            protected CorrelationResult doInBackground() {
                try {
                    AtlasWorkspace loadedWorkspace = ensureWorkspace();
                    tracePath = latest
                            ? AtlasTraceCorrelationEngine.latestTrace(loadedWorkspace)
                            : requestedTrace;
                    AtlasInvestigationIndex currentIndex = AtlasInvestigationIndex.load(
                            loadedWorkspace, loadedWorkspace.defaultClassRoot());
                    return new AtlasTraceCorrelationEngine(currentIndex).correlate(tracePath);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (failure != null) {
                        summaryArea.setText("Correlation failed:\n" + failure.getMessage());
                        summaryArea.setCaretPosition(0);
                        setStatus("Correlation failed: " + failure.getMessage(), true);
                        return;
                    }
                    CorrelationResult result = get();
                    showCorrelation(result);
                    if (tracePath != null) {
                        selectTrace(tracePath);
                    }
                } catch (Exception ex) {
                    summaryArea.setText("Correlation failed:\n" + ex.getMessage());
                    summaryArea.setCaretPosition(0);
                    setStatus("Correlation failed: " + ex.getMessage(), true);
                } finally {
                    setTraceBusy(false);
                }
            }
        }.execute();
    }

    private void showCorrelation(CorrelationResult result) {
        StringBuilder out = new StringBuilder(768);
        out.append("Trace: ").append(result.getTracePath().getFileName()).append('\n');
        out.append("Status: ").append(result.getStatus()).append('\n');
        out.append("Accepted: ").append(result.isAccepted()).append('\n');
        out.append("Events: ").append(result.getTotalEvents()).append('\n');
        out.append("Dropped: ").append(result.getDroppedCount()).append('\n');
        out.append("Correlated preview events: ").append(result.getExportedEventCount());
        if (result.isEventsTruncated()) {
            out.append(" (bounded preview)");
        }
        out.append('\n');
        out.append("Trace fingerprint: ").append(result.getTraceFingerprint()).append('\n');
        out.append("Atlas fingerprint: ").append(result.getAtlasFingerprint()).append('\n');
        out.append('\n');
        out.append(result.isAccepted()
                ? "CURRENT + accepted: trace fingerprint, event count, source IDs, and owner IDs passed."
                : "Not accepted. Use the status above as the authoritative correlation diagnostic.");

        summaryArea.setText(out.toString());
        summaryArea.setCaretPosition(0);
        setStatus(result.isAccepted()
                ? "Correlation CURRENT + Accepted true."
                : "Correlation completed with status " + result.getStatus() + ".",
                !result.isAccepted());
    }

    private Path selectedTracePath() {
        TraceEntry selected = traceList.getSelectedValue();
        return selected == null ? null : selected.getPath();
    }

    private void selectTrace(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        for (int i = 0; i < traceModel.size(); i++) {
            TraceEntry entry = traceModel.get(i);
            if (entry.getPath().equals(normalized)) {
                traceList.setSelectedIndex(i);
                traceList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void setTraceBusy(boolean busy) {
        refreshButton.setEnabled(!busy);
        correlateSelectedButton.setEnabled(!busy);
        correlateLatestButton.setEnabled(!busy);
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setForeground(error ? new java.awt.Color(235, 120, 120) : ConsoleTheme.MUTED_TEXT);
    }

    private static JTextArea createReadOnlyArea() {
        JTextArea area = new JTextArea(10, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setForeground(ConsoleTheme.TEXT);
        area.setBackground(ConsoleTheme.INPUT);
        area.setCaretColor(ConsoleTheme.TEXT);
        area.setBorder(ConsoleTheme.panelPadding(8, 9, 8, 9));
        return area;
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        long kib = bytes / 1024L;
        if (kib < 1024L) {
            return kib + " KiB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f MiB", Double.valueOf(bytes / (1024.0D * 1024.0D)));
    }

    private static final class TraceRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 5662933869112304382L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof TraceEntry) {
                TraceEntry entry = (TraceEntry) value;
                label.setText(entry.getFileName() + "  [" + readableSize(entry.getSizeBytes()) + "]");
                label.setToolTipText(entry.getPath().toString());
            }
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setForeground(ConsoleTheme.TEXT);
            label.setBackground(isSelected ? ConsoleTheme.ACCENT_DARK : ConsoleTheme.INPUT);
            label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return label;
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 4729704963045822214L;

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
