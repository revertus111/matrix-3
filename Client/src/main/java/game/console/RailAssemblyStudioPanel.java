package game.console;

import game.AssetStudioCapture;
import game.AssetStudioCapture.CaptureBatch;
import game.ConstructionPlacementController;
import game.DevDefinitionBridge;
import game.ObjectCompositePreview;
import game.RailCompositeLibrary;
import game.RailRoutePreview;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Professional client-only rail prefab authoring workstation.
 *
 * This panel deliberately does not own rail topology. It authors reusable
 * visual composites and explicit connection-port metadata consumed by the
 * existing RailRoutePreview runtime resolver.
 */
public final class RailAssemblyStudioPanel extends JScrollPane {

    private static final long serialVersionUID = 1L;
    private static final int MAX_PARTS = 64;
    private static final int HISTORY_LIMIT = 50;

    private enum PrefabType {
        CURVE("Curve"),
        JUNCTION("Junction"),
        SPLITTER("Splitter"),
        CROSSING("Crossing"),
        CUSTOM("Custom");

        private final String label;

        PrefabType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final int[] candidateIds = RailCompositeLibrary.getEvidenceSeedObjectIds();
    private final List<Integer> filteredCandidateIds = new ArrayList<Integer>();
    private final DefaultListModel<String> candidateModel = new DefaultListModel<String>();
    private final JList<String> candidateList = new JList<String>(candidateModel);
    private final JTextField candidateSearch = new JTextField();
    private final JLabel candidateLabel = valueLabel("Candidate: none");

    private final List<Part> parts = new ArrayList<Part>();
    private final DefaultListModel<String> assemblyModel = new DefaultListModel<String>();
    private final JList<String> assemblyList = new JList<String>(assemblyModel);
    private final JLabel activeLabel = valueLabel("ACTIVE: none");
    private final JCheckBox evidenceLabels =
            new JCheckBox("Evidence labels: ID / type / rotation / dX / dY", true);

    private final JTextField prefabName = new JTextField("RAIL_PREFAB_01");
    private final JComboBox<PrefabType> prefabType =
            new JComboBox<PrefabType>(PrefabType.values());
    private final JCheckBox portNorth = check("North");
    private final JCheckBox portEast = check("East");
    private final JCheckBox portSouth = check("South");
    private final JCheckBox portWest = check("West");

    private final JComboBox<String> savedPrefabs = new JComboBox<String>();
    private final JLabel validationLabel = new JLabel();
    private final JLabel statusLabel =
            ConsoleTheme.subtitleLabel("Rail Assembly Studio ready.");

    private final Deque<StudioSnapshot> undo = new ArrayDeque<StudioSnapshot>();
    private final Deque<StudioSnapshot> redo = new ArrayDeque<StudioSnapshot>();

    private Part anchorPart;
    private int previewTurns;
    private int previewWorldX;
    private int previewWorldY;
    private int previewPlane = -1;
    private boolean hotkeysInstalled;

    public RailAssemblyStudioPanel() {
        buildUi();
        rebuildCandidateFilter();
        refreshSavedPrefabs();
        installHotkeys();
        updateValidation();
    }

    private void buildUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(16, 14, 16, 14));
        content.setMinimumSize(new Dimension(0, 0));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.add(ConsoleTheme.titleLabel("RAIL ASSEMBLY STUDIO"));
        header.add(Box.createVerticalStrut(3));
        header.add(ConsoleTheme.subtitleLabel(
                "Author multi-object Junction / Splitter / Crossing / Curve prefabs without typing object IDs."));
        content.add(header);
        content.add(Box.createVerticalStrut(10));

        content.add(createCandidateCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createWorkspaceCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createDefinitionCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createSavedCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createValidationCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        getVerticalScrollBar().setUnitIncrement(18);
        setBorder(null);
    }

    private JPanel createCandidateCard() {
        JPanel card = ConsoleTheme.createCard("1. Rail Asset Browser");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Shared evidence-seeded rail catalog. Search by ID/name, double-click to add, or browse "
                + "with Prev/Next. Added parts begin on the assembly anchor so intentional overlays are easy.",
                4));
        card.add(Box.createVerticalStrut(7));

        ConsoleTheme.styleTextField(candidateSearch);
        candidateSearch.setAlignmentX(LEFT_ALIGNMENT);
        candidateSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        candidateSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuildCandidateFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuildCandidateFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuildCandidateFilter(); }
        });
        card.add(candidateSearch);
        card.add(Box.createVerticalStrut(6));

        ConsoleTheme.styleList(candidateList);
        candidateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshCandidateLabel();
            }
        });
        candidateList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    addSelectedCandidate(false);
                }
            }
        });

        JScrollPane candidateScroll = new JScrollPane(candidateList);
        candidateScroll.setAlignmentX(LEFT_ALIGNMENT);
        candidateScroll.setPreferredSize(new Dimension(240, 145));
        candidateScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 165));
        ConsoleTheme.styleScrollPane(candidateScroll);
        card.add(candidateScroll);
        card.add(Box.createVerticalStrut(6));

        candidateLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(candidateLabel);
        card.add(Box.createVerticalStrut(6));

        JPanel actions = new JPanel(new GridLayout(1, 4, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JButton previous = button("< Prev");
        JButton add = button("Add Selected");
        JButton addNext = button("Add + Next");
        JButton next = button("Next >");
        previous.addActionListener(e -> stepCandidate(-1));
        add.addActionListener(e -> addSelectedCandidate(false));
        addNext.addActionListener(e -> addSelectedCandidate(true));
        next.addActionListener(e -> stepCandidate(1));
        actions.add(previous);
        actions.add(add);
        actions.add(addNext);
        actions.add(next);
        card.add(actions);
        return card;
    }

    private JPanel createWorkspaceCard() {
        JPanel card = ConsoleTheme.createCard("2. Assembly Workspace");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Multi-select with Ctrl/Shift. Arrows move selected parts, R rotates them in place, "
                + "Ctrl+D duplicates, Delete removes, [ / ] steps the active part, Ctrl+Z/Ctrl+Y undo/redo. "
                + "Same-tile overlap is intentionally allowed inside one prefab.",
                5));
        card.add(Box.createVerticalStrut(7));

        activeLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(activeLabel);
        card.add(Box.createVerticalStrut(6));

        ConsoleTheme.styleList(assemblyList);
        assemblyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        assemblyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshActiveLabel();
            }
        });
        JScrollPane assemblyScroll = new JScrollPane(assemblyList);
        assemblyScroll.setAlignmentX(LEFT_ALIGNMENT);
        assemblyScroll.setPreferredSize(new Dimension(240, 190));
        assemblyScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        ConsoleTheme.styleScrollPane(assemblyScroll);
        card.add(assemblyScroll);
        card.add(Box.createVerticalStrut(6));

        evidenceLabels.setOpaque(false);
        evidenceLabels.setForeground(ConsoleTheme.TEXT);
        evidenceLabels.setFocusable(false);
        evidenceLabels.setAlignmentX(LEFT_ALIGNMENT);
        evidenceLabels.addActionListener(e -> refreshAssemblyList(selectedIndices()));
        card.add(evidenceLabels);
        card.add(Box.createVerticalStrut(6));

        JPanel row1 = new JPanel(new GridLayout(1, 5, 6, 0));
        row1.setOpaque(false);
        row1.setAlignmentX(LEFT_ALIGNMENT);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JButton previous = button("[ Prev");
        JButton duplicate = button("Duplicate");
        JButton setAnchor = button("Set Anchor");
        JButton normalize = button("Normalize");
        JButton next = button("Next ]");
        previous.addActionListener(e -> stepPart(-1));
        duplicate.addActionListener(e -> duplicateSelected());
        setAnchor.addActionListener(e -> setSelectedAnchor());
        normalize.addActionListener(e -> normalizeAroundAnchor());
        next.addActionListener(e -> stepPart(1));
        row1.add(previous);
        row1.add(duplicate);
        row1.add(setAnchor);
        row1.add(normalize);
        row1.add(next);
        card.add(row1);
        card.add(Box.createVerticalStrut(6));

        JPanel row2 = new JPanel(new GridLayout(2, 4, 6, 6));
        row2.setOpaque(false);
        row2.setAlignmentX(LEFT_ALIGNMENT);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        JButton up = button("Up +Y");
        JButton down = button("Down -Y");
        JButton left = button("Left -X");
        JButton right = button("Right +X");
        JButton rotate = button("Rotate R");
        JButton delete = button("Delete");
        JButton undoButton = button("Undo");
        JButton redoButton = button("Redo");
        up.addActionListener(e -> moveSelected(0, 1));
        down.addActionListener(e -> moveSelected(0, -1));
        left.addActionListener(e -> moveSelected(-1, 0));
        right.addActionListener(e -> moveSelected(1, 0));
        rotate.addActionListener(e -> rotateSelected());
        delete.addActionListener(e -> deleteSelected());
        undoButton.addActionListener(e -> undo());
        redoButton.addActionListener(e -> redo());
        row2.add(up);
        row2.add(down);
        row2.add(left);
        row2.add(right);
        row2.add(rotate);
        row2.add(delete);
        row2.add(undoButton);
        row2.add(redoButton);
        card.add(row2);
        card.add(Box.createVerticalStrut(7));

        JPanel preview = new JPanel(new GridLayout(2, 4, 6, 6));
        preview.setOpaque(false);
        preview.setAlignmentX(LEFT_ALIGNMENT);
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        JButton r0 = button("Preview R0");
        JButton r1 = button("Preview R1");
        JButton r2 = button("Preview R2");
        JButton r3 = button("Preview R3");
        JButton reset = button("Reset World Anchor");
        JButton hide = button("Hide Preview");
        JButton clear = button("Clear Assembly");
        JButton refresh = button("Refresh Preview");
        r0.addActionListener(e -> setPreviewTurns(0));
        r1.addActionListener(e -> setPreviewTurns(1));
        r2.addActionListener(e -> setPreviewTurns(2));
        r3.addActionListener(e -> setPreviewTurns(3));
        reset.addActionListener(e -> resetWorldAnchor());
        hide.addActionListener(e -> ObjectCompositePreview.hide());
        clear.addActionListener(e -> clearAssembly());
        refresh.addActionListener(e -> refreshPreview());
        preview.add(r0);
        preview.add(r1);
        preview.add(r2);
        preview.add(r3);
        preview.add(reset);
        preview.add(hide);
        preview.add(clear);
        preview.add(refresh);
        card.add(preview);
        return card;
    }

    private JPanel createDefinitionCard() {
        JPanel card = ConsoleTheme.createCard("3. Prefab Definition");
        card.add(Box.createVerticalStrut(8));

        ConsoleTheme.styleTextField(prefabName);
        prefabName.setAlignmentX(LEFT_ALIGNMENT);
        prefabName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(smallLabel("Prefab name"));
        card.add(Box.createVerticalStrut(3));
        card.add(prefabName);
        card.add(Box.createVerticalStrut(6));

        ConsoleTheme.styleComboBox(prefabType);
        prefabType.setAlignmentX(LEFT_ALIGNMENT);
        prefabType.addActionListener(e -> updateValidation());
        card.add(smallLabel("Authoring type"));
        card.add(Box.createVerticalStrut(3));
        card.add(prefabType);
        card.add(Box.createVerticalStrut(7));

        JPanel ports = new JPanel(new GridLayout(2, 2, 6, 3));
        ports.setOpaque(false);
        ports.setAlignmentX(LEFT_ALIGNMENT);
        ports.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        ports.add(portNorth);
        ports.add(portEast);
        ports.add(portSouth);
        ports.add(portWest);
        card.add(smallLabel("Logical connection ports (base R0 orientation)"));
        card.add(Box.createVerticalStrut(3));
        card.add(ports);
        card.add(Box.createVerticalStrut(7));

        java.awt.event.ActionListener metadataChanged = e -> updateValidation();
        portNorth.addActionListener(metadataChanged);
        portEast.addActionListener(metadataChanged);
        portSouth.addActionListener(metadataChanged);
        portWest.addActionListener(metadataChanged);

        JPanel saveRow = new JPanel(new GridLayout(1, 2, 6, 0));
        saveRow.setOpaque(false);
        saveRow.setAlignmentX(LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JButton saveDraft = button("Save / Update Draft");
        JButton publish = button("Publish To Runtime");
        saveDraft.addActionListener(e -> saveDraft());
        publish.addActionListener(e -> publishRuntime());
        saveRow.add(saveDraft);
        saveRow.add(publish);
        card.add(saveRow);
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Saving normalizes every component around the selected anchor. Publishing writes the "
                + "canonical runtime name for Junction/Splitter/Crossing/Curve and reloads the existing "
                + "rail resolver. Custom prefabs are draft-only.",
                4));
        return card;
    }

    private JPanel createSavedCard() {
        JPanel card = ConsoleTheme.createCard("4. Saved Prefabs");
        card.add(Box.createVerticalStrut(8));
        ConsoleTheme.styleComboBox(savedPrefabs);
        savedPrefabs.setAlignmentX(LEFT_ALIGNMENT);
        card.add(savedPrefabs);
        card.add(Box.createVerticalStrut(6));

        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JButton load = button("Load Selected Prefab");
        JButton refresh = button("Refresh Saved List");
        load.addActionListener(e -> loadSelectedPrefab());
        refresh.addActionListener(e -> refreshSavedPrefabs());
        row.add(load);
        row.add(refresh);
        card.add(row);
        return card;
    }

    private JPanel createValidationCard() {
        JPanel card = ConsoleTheme.createCard("5. Validation");
        card.add(Box.createVerticalStrut(8));
        validationLabel.setFont(ConsoleTheme.SMALL_FONT);
        validationLabel.setForeground(ConsoleTheme.TEXT);
        validationLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(validationLabel);
        card.add(Box.createVerticalStrut(8));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(statusLabel);
        return card;
    }

    private void rebuildCandidateFilter() {
        filteredCandidateIds.clear();
        candidateModel.clear();
        String query = candidateSearch.getText() == null
                ? "" : candidateSearch.getText().trim().toLowerCase();
        for (int id : candidateIds) {
            String name = objectName(id);
            String haystack = (id + " " + name).toLowerCase();
            if (query.length() == 0 || haystack.contains(query)) {
                filteredCandidateIds.add(Integer.valueOf(id));
                candidateModel.addElement("ID " + id + "  |  " + name);
            }
        }
        if (!filteredCandidateIds.isEmpty()) {
            candidateList.setSelectedIndex(0);
        }
        refreshCandidateLabel();
    }

    private void refreshCandidateLabel() {
        int index = candidateList.getSelectedIndex();
        if (index < 0 || index >= filteredCandidateIds.size()) {
            candidateLabel.setText("Candidate: none");
            return;
        }
        int id = filteredCandidateIds.get(index).intValue();
        candidateLabel.setText("Candidate " + (index + 1) + " / " + filteredCandidateIds.size()
                + "  |  ID " + id + "  |  T22  |  " + objectName(id));
    }

    private void stepCandidate(int delta) {
        if (filteredCandidateIds.isEmpty()) {
            setStatus("No rail candidates match the current search.");
            return;
        }
        int index = candidateList.getSelectedIndex();
        if (index < 0) index = 0;
        index = (index + delta + filteredCandidateIds.size()) % filteredCandidateIds.size();
        candidateList.setSelectedIndex(index);
        candidateList.ensureIndexIsVisible(index);
    }

    private void addSelectedCandidate(boolean advance) {
        int index = candidateList.getSelectedIndex();
        if (index < 0 || index >= filteredCandidateIds.size()) {
            setStatus("Select a rail candidate first.");
            return;
        }
        if (parts.size() >= MAX_PARTS) {
            setStatus("Assembly is capped at " + MAX_PARTS + " parts.");
            return;
        }
        pushUndo();
        int id = filteredCandidateIds.get(index).intValue();
        int x = anchorPart == null ? 0 : anchorPart.offsetX;
        int y = anchorPart == null ? 0 : anchorPart.offsetY;
        Part part = new Part(objectName(id), id, 22, 0, x, y);
        parts.add(part);
        refreshAssemblyList(new int[] { parts.size() - 1 });
        if (anchorPart == null) {
            anchorPart = part;
            refreshAssemblyList(new int[] { parts.size() - 1 });
        }
        refreshPreview();
        updateValidation();
        setStatus("Added rail ID " + id + " at dX " + x + ", dY " + y + ".");
        if (advance) {
            stepCandidate(1);
        }
    }

    private void duplicateSelected() {
        int[] selected = selectedIndices();
        if (selected.length == 0) {
            setStatus("Select one or more assembly parts to duplicate.");
            return;
        }
        if (parts.size() + selected.length > MAX_PARTS) {
            setStatus("Duplicate would exceed the " + MAX_PARTS + "-part cap.");
            return;
        }
        pushUndo();
        List<Part> copies = new ArrayList<Part>();
        for (int index : selected) {
            Part source = parts.get(index);
            copies.add(new Part(source.name, source.id, source.type, source.rotation,
                    clamp(source.offsetX + 1, -12, 12), source.offsetY));
        }
        int first = parts.size();
        parts.addAll(copies);
        int[] newSelection = new int[copies.size()];
        for (int i = 0; i < newSelection.length; i++) newSelection[i] = first + i;
        refreshAssemblyList(newSelection);
        refreshPreview();
        updateValidation();
        setStatus("Duplicated " + copies.size() + " selected part(s).");
    }

    private void deleteSelected() {
        int[] selected = selectedIndices();
        if (selected.length == 0) {
            setStatus("Select one or more assembly parts to delete.");
            return;
        }
        pushUndo();
        for (int i = selected.length - 1; i >= 0; i--) {
            Part removed = parts.remove(selected[i]);
            if (removed == anchorPart) {
                anchorPart = null;
            }
        }
        if (anchorPart == null && !parts.isEmpty()) {
            anchorPart = parts.get(0);
        }
        int next = parts.isEmpty() ? -1 : Math.min(selected[0], parts.size() - 1);
        refreshAssemblyList(next < 0 ? new int[0] : new int[] { next });
        refreshPreview();
        updateValidation();
        setStatus("Deleted " + selected.length + " part(s).");
    }

    private void moveSelected(int dx, int dy) {
        int[] selected = selectedIndices();
        if (selected.length == 0) {
            setStatus("Select one or more assembly parts to move.");
            return;
        }
        pushUndo();
        for (int index : selected) {
            Part part = parts.get(index);
            part.offsetX = clamp(part.offsetX + dx, -12, 12);
            part.offsetY = clamp(part.offsetY + dy, -12, 12);
        }
        refreshAssemblyList(selected);
        refreshPreview();
        updateValidation();
    }

    private void rotateSelected() {
        int[] selected = selectedIndices();
        if (selected.length == 0) {
            setStatus("Select one or more assembly parts to rotate.");
            return;
        }
        pushUndo();
        for (int index : selected) {
            parts.get(index).rotation = (parts.get(index).rotation + 1) & 0x3;
        }
        refreshAssemblyList(selected);
        refreshPreview();
        updateValidation();
    }

    private void setSelectedAnchor() {
        int[] selected = selectedIndices();
        if (selected.length == 0) {
            setStatus("Select the component that should own logical (0,0).");
            return;
        }
        pushUndo();
        anchorPart = parts.get(selected[0]);
        refreshAssemblyList(selected);
        refreshPreview();
        updateValidation();
        setStatus("Anchor set to ID " + anchorPart.id + ". Save/publish will normalize around this part.");
    }

    private void normalizeAroundAnchor() {
        if (anchorPart == null) {
            setStatus("Set an anchor first.");
            return;
        }
        pushUndo();
        int ax = anchorPart.offsetX;
        int ay = anchorPart.offsetY;
        for (Part part : parts) {
            part.offsetX = clamp(part.offsetX - ax, -12, 12);
            part.offsetY = clamp(part.offsetY - ay, -12, 12);
        }
        refreshAssemblyList(selectedIndices());
        refreshPreview();
        updateValidation();
        setStatus("Assembly normalized around anchor (0,0).");
    }

    private void stepPart(int delta) {
        if (parts.isEmpty()) return;
        int lead = assemblyList.getLeadSelectionIndex();
        if (lead < 0 || lead >= parts.size()) lead = 0;
        int next = (lead + delta + parts.size()) % parts.size();
        assemblyList.setSelectedIndex(next);
        assemblyList.ensureIndexIsVisible(next);
    }

    private void clearAssembly() {
        if (parts.isEmpty()) return;
        pushUndo();
        parts.clear();
        anchorPart = null;
        refreshAssemblyList(new int[0]);
        ObjectCompositePreview.hide();
        updateValidation();
        setStatus("Assembly cleared.");
    }

    private void setPreviewTurns(int turns) {
        previewTurns = turns & 0x3;
        refreshPreview();
        setStatus("Whole-prefab preview set to R" + previewTurns + ".");
    }

    private boolean ensureWorldAnchor() {
        if (previewPlane >= 0) return true;
        CaptureBatch batch = AssetStudioCapture.capturePlayerArea(0);
        if (batch == null || !batch.isSuccess()) {
            setStatus("Preview anchor failed: "
                    + (batch == null ? "live player/scene unavailable" : batch.getError()));
            return false;
        }
        previewWorldX = batch.getCenterX();
        previewWorldY = batch.getCenterY();
        previewPlane = batch.getPlane();
        return true;
    }

    private void resetWorldAnchor() {
        previewPlane = -1;
        if (ensureWorldAnchor()) {
            refreshPreview();
            setStatus("Preview moved beside current player position.");
        }
    }

    private void refreshPreview() {
        if (parts.isEmpty()) {
            ObjectCompositePreview.hide();
            return;
        }
        if (!ensureWorldAnchor()) return;

        Part anchor = anchorPart == null ? parts.get(0) : anchorPart;
        List<RailCompositeLibrary.Component> components =
                new ArrayList<RailCompositeLibrary.Component>();
        for (Part part : parts) {
            int dx = part.offsetX - anchor.offsetX;
            int dy = part.offsetY - anchor.offsetY;
            int[] rotated = rotateOffset(dx, dy, previewTurns);
            components.add(new RailCompositeLibrary.Component(
                    part.id, part.type, (part.rotation + previewTurns) & 0x3,
                    rotated[0], rotated[1]));
        }
        ObjectCompositePreview.showComposite(
                prefabName.getText(), components,
                previewWorldX, previewWorldY, previewPlane, 3, 0);
    }

    private void saveDraft() {
        Validation validation = validateAssembly();
        if (!validation.errors.isEmpty()) {
            setStatus("Save blocked: " + validation.errors.get(0));
            return;
        }
        String name = prefabName.getText() == null ? "" : prefabName.getText().trim();
        String error = RailCompositeLibrary.saveComposite(
                name, roleForType(selectedPrefabType()),
                normalizedComponents(), portMask());
        if (error != null) {
            setStatus(error);
            return;
        }
        refreshSavedPrefabs();
        setStatus("Saved prefab " + name + " with " + parts.size()
                + " part(s), anchor normalized and ports=" + portsText(portMask()) + ".");
    }

    private void publishRuntime() {
        PrefabType type = selectedPrefabType();
        if (type == PrefabType.CUSTOM) {
            setStatus("Custom prefabs are draft-only. Choose Curve/Junction/Splitter/Crossing to publish.");
            return;
        }
        Validation validation = validateAssembly();
        if (!validation.errors.isEmpty()) {
            setStatus("Publish blocked: " + validation.errors.get(0));
            return;
        }

        String canonical = canonicalName(type);
        String error = RailCompositeLibrary.saveComposite(
                canonical, roleForType(type), normalizedComponents(), portMask());
        if (error != null) {
            setStatus(error);
            return;
        }
        RailRoutePreview.reloadSpecialComposites();
        RailRoutePreview.reloadCurveComposite();
        refreshSavedPrefabs();
        setStatus("Published " + type + " -> " + canonical
                + " | pieces=" + parts.size() + " | ports=" + portsText(portMask()) + ".");
    }

    private List<RailCompositeLibrary.Component> normalizedComponents() {
        List<RailCompositeLibrary.Component> components =
                new ArrayList<RailCompositeLibrary.Component>();
        if (parts.isEmpty()) return components;
        Part anchor = anchorPart == null ? parts.get(0) : anchorPart;
        for (Part part : parts) {
            components.add(new RailCompositeLibrary.Component(
                    part.id, part.type, part.rotation,
                    part.offsetX - anchor.offsetX,
                    part.offsetY - anchor.offsetY));
        }
        return components;
    }

    private void refreshSavedPrefabs() {
        String selected = savedPrefabs.getSelectedItem() instanceof String
                ? (String) savedPrefabs.getSelectedItem() : null;
        savedPrefabs.removeAllItems();
        for (RailCompositeLibrary.CompositeDefinition definition : RailCompositeLibrary.loadAll()) {
            savedPrefabs.addItem(definition.getName());
        }
        if (selected != null) {
            savedPrefabs.setSelectedItem(selected);
        }
    }

    private void loadSelectedPrefab() {
        Object selected = savedPrefabs.getSelectedItem();
        if (!(selected instanceof String)) {
            setStatus("No saved prefab selected.");
            return;
        }
        RailCompositeLibrary.CompositeDefinition definition =
                RailCompositeLibrary.findByName((String) selected);
        if (definition == null) {
            setStatus("Saved prefab could not be loaded.");
            return;
        }

        pushUndo();
        parts.clear();
        anchorPart = null;
        for (RailCompositeLibrary.Component component : definition.getComponents()) {
            Part part = new Part(objectName(component.getId()), component.getId(),
                    component.getType(), component.getRotation(),
                    component.getOffsetX(), component.getOffsetY());
            parts.add(part);
            if (anchorPart == null && component.getOffsetX() == 0
                    && component.getOffsetY() == 0) {
                anchorPart = part;
            }
        }
        if (anchorPart == null && !parts.isEmpty()) anchorPart = parts.get(0);

        prefabName.setText(definition.getName());
        prefabType.setSelectedItem(typeForDefinition(definition));
        setPortMask(definition.getPortMask());
        previewTurns = 0;
        refreshAssemblyList(parts.isEmpty() ? new int[0] : new int[] { 0 });
        refreshPreview();
        updateValidation();
        setStatus("Loaded " + definition.describe() + ".");
    }

    private void refreshAssemblyList(int[] selection) {
        assemblyModel.clear();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            String prefix = part == anchorPart ? "[ANCHOR] " : "";
            String text = evidenceLabels.isSelected()
                    ? prefix + "#" + (i + 1) + "  " + part.describe()
                    : prefix + "#" + (i + 1) + "  " + part.name;
            assemblyModel.addElement(text);
        }
        if (selection != null) {
            for (int index : selection) {
                if (index >= 0 && index < parts.size()) {
                    assemblyList.addSelectionInterval(index, index);
                }
            }
        }
        refreshActiveLabel();
    }

    private void refreshActiveLabel() {
        int[] selected = selectedIndices();
        if (selected.length == 0) {
            activeLabel.setText("ACTIVE: none");
            return;
        }
        Part part = parts.get(selected[0]);
        activeLabel.setText((part == anchorPart ? "ANCHOR / " : "")
                + "ACTIVE #" + (selected[0] + 1)
                + " | ID " + part.id + " | T" + part.type
                + " | R" + part.rotation
                + " | dX " + part.offsetX + " | dY " + part.offsetY
                + (selected.length > 1 ? " | selected=" + selected.length : ""));
    }

    private int[] selectedIndices() {
        int[] selected = assemblyList.getSelectedIndices();
        return selected == null ? new int[0] : selected;
    }

    private Validation validateAssembly() {
        Validation result = new Validation();
        if (parts.isEmpty()) {
            result.errors.add("Assembly has no parts.");
            return result;
        }
        if (anchorPart == null || !parts.contains(anchorPart)) {
            result.errors.add("Choose an anchor component.");
        }

        List<RailCompositeLibrary.Component> normalized = normalizedComponents();
        LinkedHashSet<String> exact = new LinkedHashSet<String>();
        Map<String, Integer> tileCounts = new LinkedHashMap<String, Integer>();
        for (RailCompositeLibrary.Component component : normalized) {
            if (!ConstructionPlacementController.isPersistableRailObject(
                    component.getId(), component.getType())) {
                result.errors.add("ID " + component.getId() + " T" + component.getType()
                        + " is not registered as a persistent rail asset.");
            }
            String exactKey = component.getId() + ":" + component.getType() + ":"
                    + component.getRotation() + ":" + component.getOffsetX() + ":"
                    + component.getOffsetY();
            if (!exact.add(exactKey)) {
                result.errors.add("Exact duplicate: " + component.describe());
            }
            String tile = component.getOffsetX() + ":" + component.getOffsetY();
            Integer count = tileCounts.get(tile);
            tileCounts.put(tile, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }

        int overlapTiles = 0;
        for (Integer count : tileCounts.values()) {
            if (count.intValue() > 1) overlapTiles++;
        }
        if (overlapTiles > 0) {
            result.info.add(overlapTiles + " intentionally overlapping tile(s) detected.");
        }

        int ports = Integer.bitCount(portMask());
        PrefabType type = selectedPrefabType();
        if (type == PrefabType.CURVE && ports != 2) {
            result.errors.add("Curve requires exactly 2 connection ports.");
        } else if (type == PrefabType.JUNCTION && ports != 3) {
            result.errors.add("Junction requires exactly 3 connection ports.");
        } else if (type == PrefabType.CROSSING && ports != 4) {
            result.errors.add("Crossing requires all 4 connection ports.");
        } else if (type == PrefabType.SPLITTER && ports < 3) {
            result.errors.add("Splitter requires at least 3 connection ports.");
        }
        if (type == PrefabType.SPLITTER && ports == 4) {
            result.info.add("4-port splitter authored; current runtime Splitter topology is still degree-3 until the placeable Splitter-item runtime slice.");
        }
        if (type == PrefabType.CUSTOM && ports == 0) {
            result.info.add("Custom prefab has no logical ports; visual-only draft is valid.");
        }

        result.info.add(parts.size() + " component(s); ports=" + portsText(portMask())
                + "; preview=R" + previewTurns + ".");
        return result;
    }

    private void updateValidation() {
        Validation validation = validateAssembly();
        StringBuilder html = new StringBuilder("<html>");
        if (validation.errors.isEmpty()) {
            html.append("<b>PASS</b> — publishable structure");
        } else {
            html.append("<b>BLOCKED</b> — ").append(validation.errors.size()).append(" issue(s)");
        }
        for (String error : validation.errors) {
            html.append("<br>✗ ").append(escapeHtml(error));
        }
        for (String info : validation.info) {
            html.append("<br>• ").append(escapeHtml(info));
        }
        html.append("</html>");
        validationLabel.setText(html.toString());
    }

    private void pushUndo() {
        undo.push(snapshot());
        while (undo.size() > HISTORY_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    private StudioSnapshot snapshot() {
        List<Part> copy = new ArrayList<Part>();
        int anchorIndex = -1;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            copy.add(part.copy());
            if (part == anchorPart) anchorIndex = i;
        }
        return new StudioSnapshot(copy, anchorIndex, previewTurns);
    }

    private void restore(StudioSnapshot snapshot) {
        parts.clear();
        for (Part part : snapshot.parts) {
            parts.add(part.copy());
        }
        anchorPart = snapshot.anchorIndex >= 0 && snapshot.anchorIndex < parts.size()
                ? parts.get(snapshot.anchorIndex) : null;
        previewTurns = snapshot.previewTurns;
        refreshAssemblyList(parts.isEmpty() ? new int[0] : new int[] { 0 });
        refreshPreview();
        updateValidation();
    }

    private void undo() {
        if (undo.isEmpty()) {
            setStatus("Nothing to undo.");
            return;
        }
        redo.push(snapshot());
        restore(undo.pop());
        setStatus("Undo.");
    }

    private void redo() {
        if (redo.isEmpty()) {
            setStatus("Nothing to redo.");
            return;
        }
        undo.push(snapshot());
        restore(redo.pop());
        setStatus("Redo.");
    }

    private void installHotkeys() {
        if (hotkeysInstalled) return;
        hotkeysInstalled = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event.getID() != KeyEvent.KEY_PRESSED
                                || !RailAssemblyStudioPanel.this.isShowing()) {
                            return false;
                        }
                        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                .getFocusOwner();
                        if (focus instanceof JTextField
                                || focus != null && SwingUtilities.getAncestorOfClass(
                                        JComboBox.class, focus) != null) {
                            return false;
                        }

                        int key = event.getKeyCode();
                        if (event.isControlDown() && key == KeyEvent.VK_Z) {
                            undo();
                            return true;
                        }
                        if (event.isControlDown() && key == KeyEvent.VK_Y) {
                            redo();
                            return true;
                        }
                        if (event.isControlDown() && key == KeyEvent.VK_D) {
                            duplicateSelected();
                            return true;
                        }
                        if (key == KeyEvent.VK_OPEN_BRACKET) {
                            stepPart(-1);
                            return true;
                        }
                        if (key == KeyEvent.VK_CLOSE_BRACKET) {
                            stepPart(1);
                            return true;
                        }
                        if (key == KeyEvent.VK_R) {
                            rotateSelected();
                            return true;
                        }
                        if (key == KeyEvent.VK_LEFT) {
                            moveSelected(-1, 0);
                            return true;
                        }
                        if (key == KeyEvent.VK_RIGHT) {
                            moveSelected(1, 0);
                            return true;
                        }
                        if (key == KeyEvent.VK_UP) {
                            moveSelected(0, 1);
                            return true;
                        }
                        if (key == KeyEvent.VK_DOWN) {
                            moveSelected(0, -1);
                            return true;
                        }
                        if (key == KeyEvent.VK_DELETE) {
                            deleteSelected();
                            return true;
                        }
                        return false;
                    }
                });
    }

    private int portMask() {
        int mask = 0;
        if (portNorth.isSelected()) mask |= RailCompositeLibrary.PORT_NORTH;
        if (portEast.isSelected()) mask |= RailCompositeLibrary.PORT_EAST;
        if (portSouth.isSelected()) mask |= RailCompositeLibrary.PORT_SOUTH;
        if (portWest.isSelected()) mask |= RailCompositeLibrary.PORT_WEST;
        return mask;
    }

    private void setPortMask(int mask) {
        portNorth.setSelected((mask & RailCompositeLibrary.PORT_NORTH) != 0);
        portEast.setSelected((mask & RailCompositeLibrary.PORT_EAST) != 0);
        portSouth.setSelected((mask & RailCompositeLibrary.PORT_SOUTH) != 0);
        portWest.setSelected((mask & RailCompositeLibrary.PORT_WEST) != 0);
    }

    private PrefabType selectedPrefabType() {
        Object value = prefabType.getSelectedItem();
        return value instanceof PrefabType ? (PrefabType) value : PrefabType.CUSTOM;
    }

    private static RailCompositeLibrary.Role roleForType(PrefabType type) {
        if (type == PrefabType.CURVE) return RailCompositeLibrary.Role.CURVE;
        if (type == PrefabType.JUNCTION || type == PrefabType.SPLITTER) {
            return RailCompositeLibrary.Role.TURNOUT;
        }
        if (type == PrefabType.CROSSING) return RailCompositeLibrary.Role.CROSSING;
        return RailCompositeLibrary.Role.CUSTOM;
    }

    private static String canonicalName(PrefabType type) {
        if (type == PrefabType.CURVE) return RailCompositeLibrary.ACCEPTED_CURVE_NAME;
        if (type == PrefabType.JUNCTION) return RailCompositeLibrary.ACCEPTED_JUNCTION_NAME;
        if (type == PrefabType.SPLITTER) return RailCompositeLibrary.ACCEPTED_SPLITTER_NAME;
        if (type == PrefabType.CROSSING) return RailCompositeLibrary.ACCEPTED_CROSSING_NAME;
        return "";
    }

    private static PrefabType typeForDefinition(
            RailCompositeLibrary.CompositeDefinition definition) {
        String name = definition.getName().toLowerCase();
        if (name.contains("splitter")) return PrefabType.SPLITTER;
        if (name.contains("junction")) return PrefabType.JUNCTION;
        if (name.contains("crossing")) return PrefabType.CROSSING;
        if (definition.getRole() == RailCompositeLibrary.Role.CURVE
                || name.contains("curve")) return PrefabType.CURVE;
        return PrefabType.CUSTOM;
    }

    private String objectName(int id) {
        DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfoAny(id);
        if (info == null || info.getName() == null || info.getName().trim().isEmpty()) {
            return "id-" + id;
        }
        return info.getName();
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

    private static JCheckBox check(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(ConsoleTheme.TEXT);
        box.setFont(ConsoleTheme.BODY_FONT);
        box.setFocusable(false);
        return box;
    }

    private static JLabel smallLabel(String text) {
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

    private static int[] rotateOffset(int x, int y, int turns) {
        switch (turns & 0x3) {
        case 1: return new int[] { y, -x };
        case 2: return new int[] { -x, -y };
        case 3: return new int[] { -y, x };
        default: return new int[] { x, y };
        }
    }

    private static String portsText(int mask) {
        if (mask == 0) return "none";
        StringBuilder out = new StringBuilder();
        if ((mask & RailCompositeLibrary.PORT_NORTH) != 0) out.append("N");
        if ((mask & RailCompositeLibrary.PORT_EAST) != 0) out.append(out.length() == 0 ? "E" : "/E");
        if ((mask & RailCompositeLibrary.PORT_SOUTH) != 0) out.append(out.length() == 0 ? "S" : "/S");
        if ((mask & RailCompositeLibrary.PORT_WEST) != 0) out.append(out.length() == 0 ? "W" : "/W");
        return out.toString();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class Part {
        private final String name;
        private final int id;
        private final int type;
        private int rotation;
        private int offsetX;
        private int offsetY;

        private Part(String name, int id, int type, int rotation, int offsetX, int offsetY) {
            this.name = name == null || name.trim().isEmpty() ? "id-" + id : name;
            this.id = id;
            this.type = type;
            this.rotation = rotation & 0x3;
            this.offsetX = clamp(offsetX, -12, 12);
            this.offsetY = clamp(offsetY, -12, 12);
        }

        private Part copy() {
            return new Part(name, id, type, rotation, offsetX, offsetY);
        }

        private String describe() {
            return "ID " + id + " | T" + type + " | R" + rotation
                    + " | dX " + offsetX + " | dY " + offsetY
                    + " | " + name;
        }
    }

    private static final class StudioSnapshot {
        private final List<Part> parts;
        private final int anchorIndex;
        private final int previewTurns;

        private StudioSnapshot(List<Part> parts, int anchorIndex, int previewTurns) {
            this.parts = parts;
            this.anchorIndex = anchorIndex;
            this.previewTurns = previewTurns;
        }
    }

    private static final class Validation {
        private final List<String> errors = new ArrayList<String>();
        private final List<String> info = new ArrayList<String>();
    }
}
