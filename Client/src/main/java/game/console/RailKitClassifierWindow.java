package game.console;

import game.AssetStudioCapture;
import game.AssetStudioCapture.CaptureBatch;
import game.AssetStudioCapture.CaptureEntry;
import game.ObjectLabPreview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;

/**
 * Small client-only rail primitive review tool.
 *
 * It previews captured/evidence-seeded rail objects through ObjectLabPreview,
 * stores independent visual classifications as checkboxes, and writes the
 * resulting rail kit to a local TSV for later auto-tiler authoring.
 */
public final class RailKitClassifierWindow {

    private static final Path RAIL_KIT_FILE =
            Paths.get("data/construction/asset_studio/rail_kit.tsv");

    /**
     * VERIFIED from the user's 2026-09-21 Asset Studio evidence captures as
     * type-22 floor-decoration rail candidates. Exact geometry remains user-
     * classified by this tool rather than guessed from ids.
     */
    private static final int[] EVIDENCE_SEED_IDS = {
        4770, 4796, 14500, 14501, 14502,
        46352, 46353, 46354, 46355, 46356, 46357, 46358, 46359, 46361,
        46363, 46364, 46365, 46366, 46367, 46368, 46369, 46370,
        46376, 46378, 46380, 46381, 46382
    };

    private static JFrame frame;
    private static RailKitClassifierWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final List<Candidate> candidates = new ArrayList<Candidate>();
    private final Map<String, ClassificationRecord> records =
            new LinkedHashMap<String, ClassificationRecord>();

    private final CandidateTableModel tableModel = new CandidateTableModel();
    private final JTable table = new JTable(tableModel);

    private final JLabel progressLabel = valueLabel("0 / 0");
    private final JLabel idLabel = valueLabel("-");
    private final JLabel typeLabel = valueLabel("-");
    private final JLabel nameLabel = valueLabel("-");
    private final JLabel rotationsLabel = valueLabel("-");
    private final JLabel statusLabel =
            new JLabel("Double-click a row or use the arrow keys to spawn a preview.");

    private final JSpinner previewRotation =
            new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));

    private final JCheckBox straight = check("Straight");
    private final JCheckBox curve = check("Curve");
    private final JCheckBox merge = check("Merge");
    private final JCheckBox split = check("Split");
    private final JCheckBox endBuffer = check("End / Buffer");
    private final JCheckBox crossing = check("Crossing");
    private final JCheckBox notRail = check("Not Rail");
    private final JCheckBox unsure = check("Unsure");

    private Candidate selected;
    private boolean loadingChecks;

    private RailKitClassifierWindow() {
        loadRecords();
        buildUi();
    }

    public static void open(List<CaptureEntry> sourceEntries) {
        ensureWindow();
        instance.loadRecords();
        instance.replaceCandidates(sourceEntries);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        instance.table.requestFocusInWindow();
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new RailKitClassifierWindow();
        frame = new JFrame("Matrix3 Rail Kit Classifier");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(820, 560));
        frame.setSize(new Dimension(980, 660));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(14, 14, 14, 14));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("RAIL KIT CLASSIFIER");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        JLabel subtitle = new JLabel(
                "Double-click / arrow-key browse -> spawn preview -> check every geometry that applies");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);
        progressLabel.setHorizontalAlignment(JLabel.RIGHT);
        header.add(progressLabel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        root.add(header, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(ConsoleTheme.CARD);
        table.setForeground(ConsoleTheme.TEXT);
        table.setGridColor(ConsoleTheme.BORDER);
        table.setRowHeight(25);
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    previewSelected();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        ConsoleTheme.styleScrollPane(scroll);

        JPanel listCard = ConsoleTheme.createCard("Rail candidates");
        listCard.setLayout(new BorderLayout(0, 8));
        JLabel help = smallLabel(
                "Seeded from captured evidence; new Asset Studio rail candidates are merged automatically.");
        listCard.add(help, BorderLayout.NORTH);
        listCard.add(scroll, BorderLayout.CENTER);

        JPanel detail = buildDetailPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, detail);
        splitPane.setResizeWeight(0.57);
        splitPane.setDividerLocation(520);
        splitPane.setBorder(null);
        root.add(splitPane, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        root.add(statusLabel, BorderLayout.SOUTH);

        bindCheckbox(straight);
        bindCheckbox(curve);
        bindCheckbox(merge);
        bindCheckbox(split);
        bindCheckbox(endBuffer);
        bindCheckbox(crossing);
        bindCheckbox(notRail);
        bindCheckbox(unsure);

        installNavigationBindings();
    }

    private JPanel buildDetailPanel() {
        JPanel detail = new JPanel();
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBackground(ConsoleTheme.WINDOW);

        JPanel identity = ConsoleTheme.createCard("Selected rail");
        identity.add(Box.createVerticalStrut(8));
        JPanel identityGrid = new JPanel(new GridLayout(4, 2, 8, 6));
        identityGrid.setOpaque(false);
        addRow(identityGrid, "ID", idLabel);
        addRow(identityGrid, "Type", typeLabel);
        addRow(identityGrid, "Name", nameLabel);
        addRow(identityGrid, "Observed rotations", rotationsLabel);
        identity.add(identityGrid);

        JPanel preview = ConsoleTheme.createCard("Spawn preview");
        preview.add(Box.createVerticalStrut(8));
        JPanel rotationRow = new JPanel(new BorderLayout(7, 0));
        rotationRow.setOpaque(false);
        rotationRow.add(smallLabel("Preview rotation"), BorderLayout.WEST);
        rotationRow.add(previewRotation, BorderLayout.CENTER);
        preview.add(rotationRow);
        preview.add(Box.createVerticalStrut(7));

        JPanel rotationButtons = new JPanel(new GridLayout(1, 4, 6, 0));
        rotationButtons.setOpaque(false);
        for (int rotation = 0; rotation < 4; rotation++) {
            final int value = rotation;
            JButton button = button("R" + rotation);
            button.addActionListener(e -> {
                previewRotation.setValue(Integer.valueOf(value));
                previewSelected();
            });
            rotationButtons.add(button);
        }
        preview.add(rotationButtons);
        preview.add(Box.createVerticalStrut(7));

        JButton spawn = button("Spawn Preview");
        JButton hide = button("Hide Preview");
        spawn.addActionListener(e -> previewSelected());
        hide.addActionListener(e -> {
            ObjectLabPreview.hide();
            setStatus("Preview hidden.");
        });
        JPanel previewActions = new JPanel(new GridLayout(1, 2, 7, 0));
        previewActions.setOpaque(false);
        previewActions.add(spawn);
        previewActions.add(hide);
        preview.add(previewActions);

        JPanel classify = ConsoleTheme.createCard("Geometry classification");
        classify.add(Box.createVerticalStrut(8));
        JLabel instruction = smallLabel(
                "Independent checkboxes: check every role that visually applies. Changes save instantly.");
        instruction.setAlignmentX(Component.LEFT_ALIGNMENT);
        classify.add(instruction);
        classify.add(Box.createVerticalStrut(8));

        JPanel checks = new JPanel(new GridLayout(4, 2, 8, 6));
        checks.setOpaque(false);
        checks.add(straight);
        checks.add(curve);
        checks.add(merge);
        checks.add(split);
        checks.add(endBuffer);
        checks.add(crossing);
        checks.add(notRail);
        checks.add(unsure);
        classify.add(checks);

        classify.add(Box.createVerticalStrut(8));
        JLabel savePath = smallLabel("Auto-saves: Client/data/construction/asset_studio/rail_kit.tsv");
        savePath.setAlignmentX(Component.LEFT_ALIGNMENT);
        classify.add(savePath);

        JPanel navigation = ConsoleTheme.createCard("Browse");
        navigation.add(Box.createVerticalStrut(8));
        JButton previous = button("< Previous");
        JButton next = button("Next >");
        previous.addActionListener(e -> moveSelection(-1, true));
        next.addActionListener(e -> moveSelection(1, true));
        JPanel browseButtons = new JPanel(new GridLayout(1, 2, 7, 0));
        browseButtons.setOpaque(false);
        browseButtons.add(previous);
        browseButtons.add(next);
        navigation.add(browseButtons);
        navigation.add(Box.createVerticalStrut(6));
        JLabel keyHelp = smallLabel("Arrow keys: Left/Up = previous, Right/Down = next + spawn.");
        keyHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigation.add(keyHelp);

        detail.add(identity);
        detail.add(Box.createVerticalStrut(10));
        detail.add(preview);
        detail.add(Box.createVerticalStrut(10));
        detail.add(classify);
        detail.add(Box.createVerticalStrut(10));
        detail.add(navigation);
        return detail;
    }

    private void replaceCandidates(List<CaptureEntry> sourceEntries) {
        Map<String, Candidate> merged = new LinkedHashMap<String, Candidate>();

        for (int id : EVIDENCE_SEED_IDS) {
            Candidate candidate = new Candidate(id, 22, "id-" + id);
            merged.put(candidate.key(), candidate);
        }

        if (sourceEntries != null) {
            for (CaptureEntry entry : sourceEntries) {
                if (entry == null || !entry.isRailCandidate()) {
                    continue;
                }
                String key = key(entry.getId(), entry.getType());
                Candidate candidate = merged.get(key);
                if (candidate == null) {
                    candidate = new Candidate(entry.getId(), entry.getType(), entry.getName());
                    merged.put(key, candidate);
                } else if (candidate.name.startsWith("id-")
                        && entry.getName() != null && !entry.getName().trim().isEmpty()) {
                    candidate.name = entry.getName();
                }
                candidate.addRotation(entry.getRotation());
            }
        }

        candidates.clear();
        candidates.addAll(merged.values());
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                if (a.id != b.id) {
                    return a.id < b.id ? -1 : 1;
                }
                return a.type < b.type ? -1 : a.type == b.type ? 0 : 1;
            }
        });

        for (Candidate candidate : candidates) {
            ClassificationRecord record = recordFor(candidate);
            record.name = candidate.name;
            record.observedRotations = candidate.rotationsText();
        }

        tableModel.fireTableDataChanged();
        updateProgress();

        if (!candidates.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        } else {
            setSelected(null);
        }
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        int row = table.getSelectedRow();
        setSelected(row >= 0 && row < candidates.size() ? candidates.get(row) : null);
    }

    private void setSelected(Candidate candidate) {
        selected = candidate;
        loadingChecks = true;
        try {
            if (candidate == null) {
                idLabel.setText("-");
                typeLabel.setText("-");
                nameLabel.setText("-");
                rotationsLabel.setText("-");
                straight.setSelected(false);
                curve.setSelected(false);
                merge.setSelected(false);
                split.setSelected(false);
                endBuffer.setSelected(false);
                crossing.setSelected(false);
                notRail.setSelected(false);
                unsure.setSelected(false);
                return;
            }

            ClassificationRecord record = recordFor(candidate);
            idLabel.setText(Integer.toString(candidate.id));
            typeLabel.setText(Integer.toString(candidate.type));
            nameLabel.setText(candidate.name);
            rotationsLabel.setText(candidate.rotationsText());
            int restoredRotation = record.updatedAt == null || record.updatedAt.length() == 0
                    ? candidate.preferredRotation()
                    : record.lastPreviewRotation;
            previewRotation.setValue(Integer.valueOf(restoredRotation & 0x3));

            straight.setSelected(record.straight);
            curve.setSelected(record.curve);
            merge.setSelected(record.merge);
            split.setSelected(record.split);
            endBuffer.setSelected(record.endBuffer);
            crossing.setSelected(record.crossing);
            notRail.setSelected(record.notRail);
            unsure.setSelected(record.unsure);
        } finally {
            loadingChecks = false;
        }
    }

    private void bindCheckbox(JCheckBox box) {
        box.addActionListener(e -> classificationChanged());
    }

    private void classificationChanged() {
        if (loadingChecks || selected == null) {
            return;
        }

        ClassificationRecord record = recordFor(selected);
        record.name = selected.name;
        record.observedRotations = selected.rotationsText();
        record.straight = straight.isSelected();
        record.curve = curve.isSelected();
        record.merge = merge.isSelected();
        record.split = split.isSelected();
        record.endBuffer = endBuffer.isSelected();
        record.crossing = crossing.isSelected();
        record.notRail = notRail.isSelected();
        record.unsure = unsure.isSelected();
        record.lastPreviewRotation = number(previewRotation);
        record.updatedAt = timestamp();

        String error = saveRecords();
        tableModel.fireTableDataChanged();
        updateProgress();
        setStatus(error == null
                ? "Saved " + selected.id + ": " + record.summary()
                : error);
    }

    private void previewSelected() {
        Candidate candidate = selected;
        if (candidate == null) {
            setStatus("Select a rail candidate first.");
            return;
        }

        CaptureBatch anchor = AssetStudioCapture.capturePlayerArea(0);
        if (anchor == null || !anchor.isSuccess()) {
            setStatus("Preview spawn failed: "
                    + (anchor == null ? "live player/scene unavailable" : anchor.getError()));
            return;
        }

        int rotation = number(previewRotation);
        ObjectLabPreview.showPreview(candidate.name, candidate.id, candidate.type,
                rotation, anchor.getCenterX(), anchor.getCenterY(), anchor.getPlane(), 3, 0);

        ClassificationRecord record = recordFor(candidate);
        record.lastPreviewRotation = rotation;
        record.updatedAt = timestamp();
        saveRecords();

        setStatus("Spawned client-only preview: ID " + candidate.id
                + " type " + candidate.type + " rot " + rotation
                + " at player +3 X. " + ObjectLabPreview.getStatus());
    }

    private void moveSelection(int delta, boolean preview) {
        if (candidates.isEmpty()) {
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            row = delta < 0 ? candidates.size() - 1 : 0;
        } else {
            row = Math.max(0, Math.min(candidates.size() - 1, row + delta));
        }
        table.setRowSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
        if (preview) {
            previewSelected();
        }
    }

    private void installNavigationBindings() {
        bindNavigation("rail-prev-up", KeyEvent.VK_UP, -1);
        bindNavigation("rail-prev-left", KeyEvent.VK_LEFT, -1);
        bindNavigation("rail-next-down", KeyEvent.VK_DOWN, 1);
        bindNavigation("rail-next-right", KeyEvent.VK_RIGHT, 1);
    }

    private void bindNavigation(String actionKey, int keyCode, final int delta) {
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(keyCode, 0), actionKey);
        root.getActionMap().put(actionKey, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                Component focus = KeyboardFocusManager
                        .getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus != null
                        && SwingUtilities.getAncestorOfClass(JSpinner.class, focus) != null) {
                    return;
                }
                moveSelection(delta, true);
            }
        });
    }

    private ClassificationRecord recordFor(Candidate candidate) {
        String key = candidate.key();
        ClassificationRecord record = records.get(key);
        if (record == null) {
            record = new ClassificationRecord(candidate.id, candidate.type);
            record.name = candidate.name;
            record.observedRotations = candidate.rotationsText();
            records.put(key, record);
        }
        return record;
    }

    private void loadRecords() {
        records.clear();
        if (!Files.exists(RAIL_KIT_FILE)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(RAIL_KIT_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()
                        || line.startsWith("#") || line.startsWith("id\t")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 14) {
                    continue;
                }
                try {
                    int id = Integer.parseInt(parts[0]);
                    int type = Integer.parseInt(parts[1]);
                    ClassificationRecord record = new ClassificationRecord(id, type);
                    record.name = parts[2];
                    record.observedRotations = parts[3];
                    record.straight = Boolean.parseBoolean(parts[4]);
                    record.curve = Boolean.parseBoolean(parts[5]);
                    record.merge = Boolean.parseBoolean(parts[6]);
                    record.split = Boolean.parseBoolean(parts[7]);
                    record.endBuffer = Boolean.parseBoolean(parts[8]);
                    record.crossing = Boolean.parseBoolean(parts[9]);
                    record.notRail = Boolean.parseBoolean(parts[10]);
                    record.unsure = Boolean.parseBoolean(parts[11]);
                    record.lastPreviewRotation = parseInt(parts[12], 0);
                    record.updatedAt = parts[13];
                    records.put(record.key(), record);
                } catch (NumberFormatException ignored) {
                    // Skip malformed user-edited rows while retaining valid rows.
                }
            }
        } catch (Exception ex) {
            setStatus("Rail kit load failed: " + ex.getMessage());
        }
    }

    private String saveRecords() {
        try {
            Files.createDirectories(RAIL_KIT_FILE.getParent());
            List<ClassificationRecord> ordered =
                    new ArrayList<ClassificationRecord>(records.values());
            Collections.sort(ordered, new Comparator<ClassificationRecord>() {
                @Override
                public int compare(ClassificationRecord a, ClassificationRecord b) {
                    if (a.id != b.id) {
                        return a.id < b.id ? -1 : 1;
                    }
                    return a.type < b.type ? -1 : a.type == b.type ? 0 : 1;
                }
            });

            List<String> lines = new ArrayList<String>();
            lines.add("# Matrix3 Asset Studio rail kit classifier");
            lines.add("# Independent geometry flags are user-reviewed visual evidence.");
            lines.add("id\ttype\tname\tobservedRotations\tstraight\tcurve\tmerge\tsplit"
                    + "\tendBuffer\tcrossing\tnotRail\tunsure\tlastPreviewRotation\tupdatedAt");
            for (ClassificationRecord record : ordered) {
                lines.add(record.id + "\t" + record.type + "\t"
                        + safe(record.name) + "\t" + safe(record.observedRotations) + "\t"
                        + record.straight + "\t" + record.curve + "\t"
                        + record.merge + "\t" + record.split + "\t"
                        + record.endBuffer + "\t" + record.crossing + "\t"
                        + record.notRail + "\t" + record.unsure + "\t"
                        + record.lastPreviewRotation + "\t" + safe(record.updatedAt));
            }
            Files.write(RAIL_KIT_FILE, lines, StandardCharsets.UTF_8);
            return null;
        } catch (Exception ex) {
            return "Rail kit save failed: " + ex.getMessage();
        }
    }

    private void updateProgress() {
        int classified = 0;
        for (Candidate candidate : candidates) {
            ClassificationRecord record = records.get(candidate.key());
            if (record != null && record.hasAnyClassification()) {
                classified++;
            }
        }
        progressLabel.setText(classified + " / " + candidates.size() + " classified");
    }

    private void setStatus(String text) {
        statusLabel.setText(text == null ? "" : text);
    }

    private static JCheckBox check(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(ConsoleTheme.TEXT);
        box.setFocusable(true);
        return box;
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        ConsoleTheme.styleButton(button);
        button.setFocusable(false);
        return button;
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        return label;
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private void addRow(JPanel panel, String label, JLabel value) {
        panel.add(smallLabel(label));
        panel.add(value);
    }

    private int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String key(int id, int type) {
        return id + ":" + type;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ');
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private final class CandidateTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"ID", "Type", "Seen rot", "Classification"};

        @Override
        public int getRowCount() {
            return candidates.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Candidate candidate = candidates.get(rowIndex);
            ClassificationRecord record = records.get(candidate.key());
            switch (columnIndex) {
            case 0: return Integer.valueOf(candidate.id);
            case 1: return Integer.valueOf(candidate.type);
            case 2: return candidate.rotationsText();
            case 3: return record == null ? "" : record.summary();
            default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 || columnIndex == 1 ? Integer.class : String.class;
        }
    }

    private static final class Candidate {
        private final int id;
        private final int type;
        private String name;
        private int rotationMask;

        private Candidate(int id, int type, String name) {
            this.id = id;
            this.type = type;
            this.name = name == null || name.trim().isEmpty() ? "id-" + id : name;
        }

        private String key() {
            return RailKitClassifierWindow.key(id, type);
        }

        private void addRotation(int rotation) {
            rotationMask |= 1 << (rotation & 0x3);
        }

        private int preferredRotation() {
            for (int rotation = 0; rotation < 4; rotation++) {
                if ((rotationMask & (1 << rotation)) != 0) {
                    return rotation;
                }
            }
            return 0;
        }

        private String rotationsText() {
            StringBuilder builder = new StringBuilder();
            for (int rotation = 0; rotation < 4; rotation++) {
                if ((rotationMask & (1 << rotation)) == 0) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(rotation);
            }
            return builder.length() == 0 ? "-" : builder.toString();
        }
    }

    private static final class ClassificationRecord {
        private final int id;
        private final int type;
        private String name = "";
        private String observedRotations = "";
        private boolean straight;
        private boolean curve;
        private boolean merge;
        private boolean split;
        private boolean endBuffer;
        private boolean crossing;
        private boolean notRail;
        private boolean unsure;
        private int lastPreviewRotation;
        private String updatedAt = "";

        private ClassificationRecord(int id, int type) {
            this.id = id;
            this.type = type;
        }

        private String key() {
            return RailKitClassifierWindow.key(id, type);
        }

        private boolean hasAnyClassification() {
            return straight || curve || merge || split || endBuffer
                    || crossing || notRail || unsure;
        }

        private String summary() {
            List<String> values = new ArrayList<String>();
            if (straight) values.add("STRAIGHT");
            if (curve) values.add("CURVE");
            if (merge) values.add("MERGE");
            if (split) values.add("SPLIT");
            if (endBuffer) values.add("END");
            if (crossing) values.add("CROSSING");
            if (notRail) values.add("NOT_RAIL");
            if (unsure) values.add("UNSURE");
            if (values.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (String value : values) {
                if (builder.length() > 0) {
                    builder.append(" + ");
                }
                builder.append(value);
            }
            return builder.toString();
        }
    }
}
