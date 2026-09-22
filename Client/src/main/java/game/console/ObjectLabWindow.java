package game.console;

import game.AssetStudioCapture;
import game.AssetStudioEvidenceCapture;
import game.AssetStudioCapture.CaptureBatch;
import game.AssetStudioCapture.CaptureEntry;
import game.ClientConsoleBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;
import game.ObjectLabPreview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Matrix3 Asset Studio v1.
 *
 * Captures live scene objects without object interaction, browses them as a
 * session, auto-populates exact ID/type/rotation into the existing direct-render
 * preview, and supports optional curated catalog saves.
 */
public final class ObjectLabWindow {

    private static JFrame frame;
    private static ObjectLabWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final List<CaptureEntry> entries = new ArrayList<CaptureEntry>();
    private final Set<String> entryKeys = new HashSet<String>();
    private final SessionTableModel tableModel = new SessionTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<SessionTableModel> sorter =
            new TableRowSorter<SessionTableModel>(tableModel);

    private final JLabel sessionLabel = valueLabel("Session: 0 objects");
    private final JLabel nameLabel = valueLabel("-");
    private final JLabel idLabel = valueLabel("-");
    private final JLabel typeLabel = valueLabel("-");
    private final JLabel rotationLabel = valueLabel("-");
    private final JLabel slotLabel = valueLabel("-");
    private final JLabel tileLabel = valueLabel("-");
    private final JLabel sizeLabel = valueLabel("-");
    private final JLabel suggestedTagLabel = valueLabel("-");

    private final JSpinner previewRotation =
            new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
    private final JSpinner offsetX =
            new JSpinner(new SpinnerNumberModel(6, -20, 20, 1));
    private final JSpinner offsetY =
            new JSpinner(new SpinnerNumberModel(0, -20, 20, 1));
    private final JCheckBox autoPreview = new JCheckBox("Auto-preview selected object", true);
    private final JCheckBox railOnly = new JCheckBox("Rail candidates only");
    private final JComboBox<TagChoice> tagBox =
            new JComboBox<TagChoice>(TagChoice.values());
    private final JTextArea notes = new JTextArea(3, 18);
    private final JLabel statusLabel =
            new JLabel("Open a capture session. No object interaction is required.");

    private CaptureEntry selectedEntry;
    private volatile boolean evidenceCaptureActive;

    private ObjectLabWindow() {
        buildUi();
    }

    public static void openEmpty() {
        ensureWindow();
        showWindow();
    }

    public static void open(DevTarget target) {
        ensureWindow();
        if (target != null && target.getType() == TargetType.OBJECT) {
            CaptureBatch batch = AssetStudioCapture.captureWorldArea(
                    target.getWorldX(), target.getWorldY(), target.getPlane(), 0);
            instance.addBatch(batch, "Dev target tile");
            instance.selectFirstMatchingId(target.getId());
        }
        showWindow();
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new ObjectLabWindow();
        frame = new JFrame("Matrix3 Asset Studio");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(980, 640));
        frame.setSize(new Dimension(1180, 760));
        frame.setLocationByPlatform(true);
    }

    private static void showWindow() {
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(14, 14, 14, 14));

        root.add(buildHeader(), BorderLayout.NORTH);

        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(ConsoleTheme.CARD);
        table.setForeground(ConsoleTheme.TEXT);
        table.setGridColor(ConsoleTheme.BORDER);
        table.setRowHeight(24);
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);

        JScrollPane tableScroll = new JScrollPane(table);
        ConsoleTheme.styleScrollPane(tableScroll);

        JPanel browser = ConsoleTheme.createCard("Capture session");
        browser.setLayout(new BorderLayout(0, 8));
        browser.add(buildSessionToolbar(), BorderLayout.NORTH);
        browser.add(tableScroll, BorderLayout.CENTER);

        JPanel inspector = buildInspector();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browser, inspector);
        split.setResizeWeight(0.68);
        split.setDividerLocation(760);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        root.add(statusLabel, BorderLayout.SOUTH);

        autoPreview.setOpaque(false);
        autoPreview.setForeground(ConsoleTheme.TEXT);
        railOnly.setOpaque(false);
        railOnly.setForeground(ConsoleTheme.TEXT);
        railOnly.addActionListener(e -> applyRailFilter());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ConsoleTheme.WINDOW);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);

        JLabel title = new JLabel("MATRIX3 ASSET STUDIO");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        JLabel subtitle = new JLabel(
                "Capture once -> browse -> classify -> feed future A-to-B rail auto-tiling");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        sessionLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(sessionLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildSessionToolbar() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row1.setOpaque(false);
        JButton current = button("Capture Current Tile");
        JButton plusX = button("Capture +X Tile");
        JButton three = button("Capture 3x3");
        JButton nine = button("Capture 9x9");
        current.addActionListener(e -> capture(AssetStudioCapture.capturePlayerArea(0), "Current Tile"));
        plusX.addActionListener(e -> capture(AssetStudioCapture.capturePlayerOffset(1, 0, 0), "+X Tile"));
        three.addActionListener(e -> capture(AssetStudioCapture.capturePlayerArea(1), "3x3"));
        nine.addActionListener(e -> capture(AssetStudioCapture.capturePlayerArea(4), "9x9"));
        row1.add(current);
        row1.add(plusX);
        row1.add(three);
        row1.add(nine);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row2.setOpaque(false);
        JButton evidence = button("Capture / Log Rail Evidence");
        JButton classifier = button("Open Rail Classifier");
        JButton export = button("Export Session");
        JButton clear = button("Clear Session");
        evidence.addActionListener(e -> captureEvidence());
        classifier.addActionListener(e ->
                RailKitClassifierWindow.open(new ArrayList<CaptureEntry>(entries)));
        export.addActionListener(e -> exportSession());
        clear.addActionListener(e -> clearSession());
        row2.add(evidence);
        row2.add(classifier);
        row2.add(export);
        row2.add(clear);
        row2.add(railOnly);

        wrapper.add(row1);
        wrapper.add(Box.createVerticalStrut(6));
        wrapper.add(row2);
        return wrapper;
    }

    private JPanel buildInspector() {
        JPanel inspector = new JPanel();
        inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
        inspector.setBackground(ConsoleTheme.WINDOW);

        JPanel identity = ConsoleTheme.createCard("Selected object");
        identity.add(Box.createVerticalStrut(8));
        JPanel grid = new JPanel(new GridLayout(8, 2, 8, 6));
        grid.setOpaque(false);
        addRow(grid, "Name", nameLabel);
        addRow(grid, "ID", idLabel);
        addRow(grid, "Type", typeLabel);
        addRow(grid, "Captured rotation", rotationLabel);
        addRow(grid, "Slot", slotLabel);
        addRow(grid, "Tile", tileLabel);
        addRow(grid, "Size", sizeLabel);
        addRow(grid, "Suggested tag", suggestedTagLabel);
        identity.add(grid);

        JPanel preview = ConsoleTheme.createCard("Direct-render viewer");
        preview.add(Box.createVerticalStrut(8));
        JPanel controls = new JPanel(new GridLayout(3, 2, 7, 7));
        controls.setOpaque(false);
        controls.add(smallLabel("Preview rotation"));
        controls.add(previewRotation);
        controls.add(smallLabel("Offset X"));
        controls.add(offsetX);
        controls.add(smallLabel("Offset Y"));
        controls.add(offsetY);
        preview.add(controls);
        preview.add(Box.createVerticalStrut(7));
        preview.add(autoPreview);
        preview.add(Box.createVerticalStrut(7));

        JButton show = button("Preview Selected");
        JButton hide = button("Hide Preview");
        JButton previous = button("< Previous");
        JButton next = button("Next >");
        show.addActionListener(e -> showSelectedPreview());
        hide.addActionListener(e -> {
            ObjectLabPreview.hide();
            setStatus("Preview hidden.");
        });
        previous.addActionListener(e -> moveSelection(-1));
        next.addActionListener(e -> moveSelection(1));

        JPanel previewButtons = new JPanel(new GridLayout(2, 2, 7, 7));
        previewButtons.setOpaque(false);
        previewButtons.add(show);
        previewButtons.add(hide);
        previewButtons.add(previous);
        previewButtons.add(next);
        preview.add(previewButtons);

        JPanel catalog = ConsoleTheme.createCard("Classification / curated catalog");
        catalog.add(Box.createVerticalStrut(8));
        tagBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        catalog.add(smallLabel("Tag"));
        catalog.add(Box.createVerticalStrut(3));
        catalog.add(tagBox);
        catalog.add(Box.createVerticalStrut(7));

        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBackground(ConsoleTheme.CARD);
        notes.setForeground(ConsoleTheme.TEXT);
        notes.setCaretColor(ConsoleTheme.TEXT);
        notes.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        JScrollPane noteScroll = new JScrollPane(notes);
        noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        ConsoleTheme.styleScrollPane(noteScroll);
        catalog.add(smallLabel("Optional notes"));
        catalog.add(Box.createVerticalStrut(3));
        catalog.add(noteScroll);
        catalog.add(Box.createVerticalStrut(7));

        JButton save = button("Save Selected to Catalog");
        JButton list = button("List Curated Catalog");
        save.addActionListener(e -> saveSelected());
        list.addActionListener(e -> queue("itembrowser objectlab list",
                "Curated Object Lab catalog list queued. Check game chat."));
        JPanel catalogButtons = new JPanel(new GridLayout(2, 1, 0, 7));
        catalogButtons.setOpaque(false);
        catalogButtons.add(save);
        catalogButtons.add(list);
        catalog.add(catalogButtons);

        inspector.add(identity);
        inspector.add(Box.createVerticalStrut(10));
        inspector.add(preview);
        inspector.add(Box.createVerticalStrut(10));
        inspector.add(catalog);
        return inspector;
    }

    private void captureEvidence() {
        if (evidenceCaptureActive) {
            setStatus("An evidence capture is already in progress.");
            return;
        }

        final CaptureBatch batch = AssetStudioCapture.capturePlayerArea(4);
        if (batch == null || !batch.isSuccess()) {
            setStatus("Evidence capture failed: "
                    + (batch == null ? "unknown error" : batch.getError()));
            return;
        }
        if (batch.getEntries().isEmpty()) {
            setStatus("Evidence capture found no live scene objects in the 9x9 area.");
            return;
        }

        addBatch(batch, "Evidence 9x9");
        ObjectLabPreview.hide();
        evidenceCaptureActive = true;
        setStatus("Capturing paired Matrix3 PNG + TSV evidence...");

        final boolean restoreWindow = frame != null && frame.isVisible();
        if (frame != null) {
            frame.setVisible(false);
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final AssetStudioEvidenceCapture.Result result =
                        AssetStudioEvidenceCapture.capture(batch);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        evidenceCaptureActive = false;
                        if (restoreWindow && frame != null) {
                            showWindow();
                        }
                        if (result.isSuccess()) {
                            setStatus("Evidence " + result.getCaptureId() + " saved: "
                                    + result.getObjectCount() + " object(s), "
                                    + result.getPng().toString() + " + "
                                    + result.getTsv().toString() + ".");
                        } else {
                            setStatus(result.getError());
                        }
                    }
                });
            }
        }, "matrix3-asset-evidence");
        worker.setDaemon(true);
        worker.start();
    }

    private void capture(CaptureBatch batch, String label) {
        addBatch(batch, label);
        if (batch != null && batch.isSuccess() && !batch.getEntries().isEmpty()
                && table.getSelectedRow() < 0 && table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void addBatch(CaptureBatch batch, String label) {
        if (batch == null || !batch.isSuccess()) {
            setStatus("Capture failed: " + (batch == null ? "unknown error" : batch.getError()));
            return;
        }
        int added = 0;
        for (CaptureEntry entry : batch.getEntries()) {
            if (entryKeys.add(entry.getKey())) {
                entries.add(entry);
                added++;
            }
        }
        tableModel.fireTableDataChanged();
        sessionLabel.setText("Session: " + entries.size() + " objects");
        setStatus(label + " captured " + batch.getEntries().size() + " live object(s), "
                + added + " new. Center=" + batch.getCenterX() + ","
                + batch.getCenterY() + "," + batch.getPlane() + ".");
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            setSelected(null);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= entries.size()) {
            setSelected(null);
            return;
        }
        setSelected(entries.get(modelRow));
    }

    private void setSelected(CaptureEntry entry) {
        selectedEntry = entry;
        if (entry == null) {
            nameLabel.setText("-");
            idLabel.setText("-");
            typeLabel.setText("-");
            rotationLabel.setText("-");
            slotLabel.setText("-");
            tileLabel.setText("-");
            sizeLabel.setText("-");
            suggestedTagLabel.setText("-");
            return;
        }
        nameLabel.setText(entry.getName());
        idLabel.setText(Integer.toString(entry.getId()));
        typeLabel.setText(Integer.toString(entry.getType()));
        rotationLabel.setText(Integer.toString(entry.getRotation()));
        slotLabel.setText(entry.getSlot());
        tileLabel.setText(entry.getTileText());
        sizeLabel.setText(entry.getSizeText());
        suggestedTagLabel.setText(entry.getSuggestedTag());
        previewRotation.setValue(Integer.valueOf(entry.getRotation()));
        tagBox.setSelectedItem(TagChoice.forKey(entry.getSuggestedTag()));
        notes.setText("");
        if (autoPreview.isSelected()) {
            showSelectedPreview();
        }
    }

    private void showSelectedPreview() {
        CaptureEntry entry = selectedEntry;
        if (entry == null) {
            setStatus("Select a captured object first.");
            return;
        }
        ObjectLabPreview.showPreview(entry.getName(), entry.getId(), entry.getType(),
                number(previewRotation), entry.getWorldX(), entry.getWorldY(), entry.getPlane(),
                number(offsetX), number(offsetY));
        setStatus(ObjectLabPreview.getStatus());
    }

    private void moveSelection(int delta) {
        int count = table.getRowCount();
        if (count <= 0) {
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            row = 0;
        } else {
            row = Math.max(0, Math.min(count - 1, row + delta));
        }
        table.setRowSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    private void applyRailFilter() {
        if (!railOnly.isSelected()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(new RowFilter<SessionTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends SessionTableModel, ? extends Integer> entry) {
                int modelRow = entry.getIdentifier().intValue();
                return modelRow >= 0 && modelRow < entries.size()
                        && entries.get(modelRow).isRailCandidate();
            }
        });
        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void clearSession() {
        ObjectLabPreview.hide();
        entries.clear();
        entryKeys.clear();
        tableModel.fireTableDataChanged();
        sessionLabel.setText("Session: 0 objects");
        setSelected(null);
        setStatus("Capture session cleared.");
    }

    private void exportSession() {
        if (entries.isEmpty()) {
            setStatus("Capture something before exporting a session.");
            return;
        }
        Path dir = Paths.get("data/construction/asset_studio/captures");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path file = dir.resolve("capture_" + stamp + ".tsv");
        List<String> lines = new ArrayList<String>();
        lines.add("id\tname\ttype\trotation\tslot\tx\ty\tplane\tsizeX\tsizeY\tsuggestedTag");
        for (CaptureEntry entry : entries) {
            lines.add(entry.getId() + "\t" + safe(entry.getName()) + "\t"
                    + entry.getType() + "\t" + entry.getRotation() + "\t"
                    + safe(entry.getSlot()) + "\t"
                    + entry.getWorldX() + "\t" + entry.getWorldY() + "\t"
                    + entry.getPlane() + "\t" + entry.getSizeX() + "\t"
                    + entry.getSizeY() + "\t" + entry.getSuggestedTag());
        }
        try {
            Files.createDirectories(dir);
            Files.write(file, lines, StandardCharsets.UTF_8);
            setStatus("Exported " + entries.size() + " objects to " + file.toString() + ".");
        } catch (Exception ex) {
            setStatus("Session export failed: " + ex.getMessage());
        }
    }

    private void saveSelected() {
        CaptureEntry entry = selectedEntry;
        if (entry == null) {
            setStatus("Select a captured object first.");
            return;
        }
        TagChoice tag = (TagChoice) tagBox.getSelectedItem();
        String noteText = notes.getText() == null ? "" : notes.getText().trim();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(noteText.getBytes(StandardCharsets.UTF_8));
        if (encoded.length() == 0) {
            encoded = "-";
        }
        String command = "itembrowser objectlab save "
                + entry.getId() + " " + entry.getType() + " " + entry.getRotation()
                + " " + (tag == null ? "UNKNOWN" : tag.key)
                + " " + entry.getWorldX() + " " + entry.getWorldY() + " "
                + entry.getPlane() + " " + encoded;
        queue(command, "Curated catalog save queued for " + entry.getId()
                + " / " + entry.getName() + ".");
    }

    private void selectFirstMatchingId(int id) {
        for (int modelRow = 0; modelRow < entries.size(); modelRow++) {
            if (entries.get(modelRow).getId() == id) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    return;
                }
            }
        }
    }

    private void queue(String command, String success) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        setStatus(error == null ? success : error);
    }

    private void setStatus(String text) {
        statusLabel.setText(text == null ? "" : text);
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

    private void addRow(JPanel grid, String label, JLabel value) {
        grid.add(smallLabel(label));
        grid.add(value);
    }

    private int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ');
    }

    private final class SessionTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {
                "ID", "Name", "Type", "Rot", "Slot", "Tile", "Suggested"
        };

        @Override
        public int getRowCount() {
            return entries.size();
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
            CaptureEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
            case 0: return Integer.valueOf(entry.getId());
            case 1: return entry.getName();
            case 2: return Integer.valueOf(entry.getType());
            case 3: return Integer.valueOf(entry.getRotation());
            case 4: return entry.getSlot();
            case 5: return entry.getTileText();
            case 6: return entry.getSuggestedTag();
            default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 || columnIndex == 2 || columnIndex == 3
                    ? Integer.class : String.class;
        }
    }

    private enum TagChoice {
        UNKNOWN("Unknown", "UNKNOWN"),
        RAIL_CANDIDATE("Rail Candidate", "RAIL_CANDIDATE"),
        STRAIGHT_RAIL("Straight Rail", "STRAIGHT_RAIL"),
        CURVE("Curve", "CURVE"),
        SWITCH("Switch / Turnout", "SWITCH"),
        JUNCTION("Junction", "JUNCTION"),
        BUFFER("Buffer", "BUFFER"),
        CART("Cart", "CART"),
        LOADING_POINT("Loading Point", "LOADING_POINT"),
        SIGNAL("Signal", "SIGNAL"),
        FAVORITE("Favorite", "FAVORITE");

        private final String display;
        private final String key;

        TagChoice(String display, String key) {
            this.display = display;
            this.key = key;
        }

        private static TagChoice forKey(String key) {
            if (key != null) {
                for (TagChoice value : values()) {
                    if (value.key.equalsIgnoreCase(key)) {
                        return value;
                    }
                }
            }
            return UNKNOWN;
        }

        @Override
        public String toString() {
            return display;
        }
    }
}
