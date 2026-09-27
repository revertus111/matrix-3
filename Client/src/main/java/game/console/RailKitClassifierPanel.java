package game.console;

import game.AssetStudioCapture;
import game.AssetStudioCapture.CaptureBatch;
import game.AssetStudioCapture.CaptureEntry;
import game.ObjectLabPreview;
import game.ObjectCompositePreview;
import game.RailRoutePreview;
import game.RailCompositeLibrary;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;

/**
 * Docked Test Console rail primitive review tool.
 *
 * The panel is intentionally client-only. It uses ObjectLabPreview for temporary
 * visual inspection, stores user-reviewed geometry flags locally, and never
 * registers or mutates a real world object.
 */
public final class RailKitClassifierPanel extends JScrollPane {

    private static final long serialVersionUID = 1L;

    private static final Path RAIL_KIT_FILE =
            Paths.get("data/construction/asset_studio/rail_kit.tsv");

    /**
     * VERIFIED from the 2026-09-21 Asset Studio evidence captures as type-22
     * floor-decoration rail candidates. Geometry is deliberately left for visual
     * classification rather than inferred from object ids.
     */
    private final List<Candidate> candidates = new ArrayList<Candidate>();
    private final Map<String, ClassificationRecord> records =
            new LinkedHashMap<String, ClassificationRecord>();

    private final CandidateTableModel tableModel = new CandidateTableModel();
    private final JTable table = new JTable(tableModel);

    private final JLabel progressLabel = valueLabel("0 / 0 classified");
    private final JLabel idLabel = valueLabel("-");
    private final JLabel typeLabel = valueLabel("-");
    private final JLabel nameLabel = valueLabel("-");
    private final JLabel rotationsLabel = valueLabel("-");

    private final JSpinner previewRotation =
            new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));

    private final DefaultListModel<String> workbenchModel = new DefaultListModel<String>();
    private final JList<String> workbenchList = new JList<String>(workbenchModel);
    private final List<LayoutPart> workbenchParts = new ArrayList<LayoutPart>();
    private final JLabel activeWorkbenchPiece =
            ConsoleTheme.titleLabel("ACTIVE RAIL: none");
    private final JCheckBox workbenchHotkeys = new JCheckBox(
            "Workbench hotkeys: [ / ] select, arrows move, R rotate, Del remove, Ctrl+D duplicate", true);
    private final JTextField workbenchNameField = new JTextField("RAIL_RESEARCH_01");
    private final JComboBox<RailCompositeLibrary.Role> workbenchRole =
            new JComboBox<RailCompositeLibrary.Role>(RailCompositeLibrary.Role.values());
    private int workbenchAnchorX = Integer.MIN_VALUE;
    private int workbenchAnchorY = Integer.MIN_VALUE;
    private int workbenchAnchorPlane = -1;
    private boolean workbenchHotkeysInstalled;

    private final JComboBox<RailRoutePreview.RouteOrder> routeOrder =
            new JComboBox<RailRoutePreview.RouteOrder>(RailRoutePreview.RouteOrder.values());
    private final JLabel routePieceLabel = valueLabel("Straight rail: not configured");
    private final JLabel routeCurveLabel = valueLabel("Curve composite: not loaded");
    private final JSpinner curveMapOffset =
            new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));

    private final JCheckBox straight = check("Straight");
    private final JCheckBox curve = check("Curve");
    private final JCheckBox merge = check("Merge");
    private final JCheckBox split = check("Split");
    private final JCheckBox endBuffer = check("End / Buffer");
    private final JCheckBox crossing = check("Crossing");
    private final JCheckBox notRail = check("Not Rail");
    private final JCheckBox unsure = check("Unsure");

    private final JLabel statusLabel =
            ConsoleTheme.subtitleLabel("Double-click or use arrow keys to spawn the selected preview.");

    private Candidate selected;
    private boolean loadingChecks;

    public RailKitClassifierPanel() {
        loadRecords();
        buildUi();
        replaceCandidates(null, false);
        installWorkbenchHotkeys();
    }

    private void buildUi() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(16, 14, 16, 14));
        content.setMinimumSize(new Dimension(0, 0));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.add(ConsoleTheme.titleLabel("RAIL CLASSIFIER"));
        header.add(Box.createVerticalStrut(3));
        header.add(ConsoleTheme.subtitleLabel(
                "Select a candidate -> drop/move/rotate it -> classify/save without typing object IDs."));
        header.add(Box.createVerticalStrut(4));
        progressLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(progressLabel);
        content.add(header);
        content.add(Box.createVerticalStrut(12));

        content.add(createCandidateCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createSelectedCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createPreviewCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createWorkbenchCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createRoutePreviewCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createClassificationCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createStatusCard());
        content.add(Box.createVerticalGlue());

        bindCheckbox(straight);
        bindCheckbox(curve);
        bindCheckbox(merge);
        bindCheckbox(split);
        bindCheckbox(endBuffer);
        bindCheckbox(crossing);
        bindCheckbox(notRail);
        bindCheckbox(unsure);
        installNavigationBindings();

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);
    }

    private JPanel createCandidateCard() {
        JPanel card = ConsoleTheme.createCard("Rail candidates");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "27 evidence-seeded rail ids are always available. Refresh Live 9x9 Rails merges exact "
                + "ids/types/rotations from the scene around your player without opening Asset Studio.",
                4));
        card.add(Box.createVerticalStrut(8));

        JButton refresh = button("Refresh Live 9x9 Rails");
        refresh.addActionListener(e -> refreshLiveRails());
        refresh.setAlignmentX(LEFT_ALIGNMENT);
        refresh.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(refresh);
        card.add(Box.createVerticalStrut(8));

        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(ConsoleTheme.CARD);
        table.setForeground(ConsoleTheme.TEXT);
        table.setGridColor(ConsoleTheme.BORDER);
        table.setRowHeight(24);
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    previewSelected();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setAlignmentX(LEFT_ALIGNMENT);
        tableScroll.setPreferredSize(new Dimension(240, 210));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));
        ConsoleTheme.styleScrollPane(tableScroll);
        card.add(tableScroll);
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Double-click = spawn preview.  Left/Up = previous + spawn.  Right/Down = next + spawn.",
                3));
        return card;
    }

    private JPanel createSelectedCard() {
        JPanel card = ConsoleTheme.createCard("Selected rail");
        card.add(Box.createVerticalStrut(8));

        JPanel grid = new JPanel(new GridLayout(4, 2, 6, 5));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        addRow(grid, "ID", idLabel);
        addRow(grid, "Type", typeLabel);
        addRow(grid, "Name", nameLabel);
        addRow(grid, "Seen rot", rotationsLabel);
        card.add(grid);
        return card;
    }

    private JPanel createPreviewCard() {
        JPanel card = ConsoleTheme.createCard("Spawn preview");
        card.add(Box.createVerticalStrut(8));

        JPanel rotationRow = new JPanel(new GridLayout(1, 2, 6, 0));
        rotationRow.setOpaque(false);
        rotationRow.setAlignmentX(LEFT_ALIGNMENT);
        rotationRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        rotationRow.add(smallLabel("Rotation"));
        rotationRow.add(previewRotation);
        card.add(rotationRow);
        card.add(Box.createVerticalStrut(7));

        JPanel rotationButtons = new JPanel(new GridLayout(1, 4, 5, 0));
        rotationButtons.setOpaque(false);
        rotationButtons.setAlignmentX(LEFT_ALIGNMENT);
        rotationButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        for (int rotation = 0; rotation < 4; rotation++) {
            final int value = rotation;
            JButton button = button("R" + rotation);
            button.addActionListener(e -> {
                previewRotation.setValue(Integer.valueOf(value));
                previewSelected();
            });
            rotationButtons.add(button);
        }
        card.add(rotationButtons);
        card.add(Box.createVerticalStrut(7));

        JButton spawn = button("Spawn Preview");
        JButton hide = button("Hide Preview");
        spawn.addActionListener(e -> previewSelected());
        hide.addActionListener(e -> {
            ObjectLabPreview.hide();
            setStatus("Preview hidden.");
        });

        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        actions.add(spawn);
        actions.add(hide);
        card.add(actions);
        return card;
    }


    private JPanel createWorkbenchCard() {
        JPanel card = ConsoleTheme.createCard("Rail Classifier Workbench");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Drop the currently selected classifier candidate into a client-only layout. "
                + "The active dropped rail automatically becomes the classifier selection and supplies "
                + "its object ID, type and rotation to classification/save actions.",
                5));
        card.add(Box.createVerticalStrut(7));

        activeWorkbenchPiece.setAlignmentX(LEFT_ALIGNMENT);
        card.add(activeWorkbenchPiece);
        card.add(Box.createVerticalStrut(7));

        workbenchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workbenchList.setBackground(ConsoleTheme.PANEL);
        workbenchList.setForeground(ConsoleTheme.TEXT);
        workbenchList.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        workbenchList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                syncClassifierToActiveWorkbenchPart();
                refreshActiveWorkbenchLabel();
            }
        });
        JScrollPane listScroll = new JScrollPane(workbenchList);
        listScroll.setAlignmentX(LEFT_ALIGNMENT);
        listScroll.setPreferredSize(new Dimension(240, 150));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        ConsoleTheme.styleScrollPane(listScroll);
        card.add(listScroll);
        card.add(Box.createVerticalStrut(7));

        JButton drop = button("Drop Selected Rail");
        JButton dropNext = button("Drop + Next");
        JButton duplicate = button("Duplicate");
        JButton remove = button("Delete");
        drop.addActionListener(e -> dropSelectedCandidate(false));
        dropNext.addActionListener(e -> dropSelectedCandidate(true));
        duplicate.addActionListener(e -> duplicateWorkbenchPart());
        remove.addActionListener(e -> removeWorkbenchPart());

        JPanel primary = new JPanel(new GridLayout(2, 2, 6, 6));
        primary.setOpaque(false);
        primary.setAlignmentX(LEFT_ALIGNMENT);
        primary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        primary.add(drop);
        primary.add(dropNext);
        primary.add(duplicate);
        primary.add(remove);
        card.add(primary);
        card.add(Box.createVerticalStrut(7));

        JButton previous = button("[ Previous");
        JButton up = button("Up +Y");
        JButton next = button("Next ]");
        JButton left = button("Left -X");
        JButton rotate = button("Rotate R");
        JButton right = button("Right +X");
        JButton resetAnchor = button("Reset Anchor");
        JButton down = button("Down -Y");
        JButton clear = button("Clear Layout");

        previous.addActionListener(e -> selectWorkbenchPart(-1));
        next.addActionListener(e -> selectWorkbenchPart(1));
        up.addActionListener(e -> moveWorkbenchPart(0, 1));
        down.addActionListener(e -> moveWorkbenchPart(0, -1));
        left.addActionListener(e -> moveWorkbenchPart(-1, 0));
        right.addActionListener(e -> moveWorkbenchPart(1, 0));
        rotate.addActionListener(e -> rotateWorkbenchPart());
        resetAnchor.addActionListener(e -> resetWorkbenchAnchor());
        clear.addActionListener(e -> clearWorkbench());

        JPanel placement = new JPanel(new GridLayout(3, 3, 6, 6));
        placement.setOpaque(false);
        placement.setAlignmentX(LEFT_ALIGNMENT);
        placement.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        placement.add(previous);
        placement.add(up);
        placement.add(next);
        placement.add(left);
        placement.add(rotate);
        placement.add(right);
        placement.add(resetAnchor);
        placement.add(down);
        placement.add(clear);
        card.add(placement);
        card.add(Box.createVerticalStrut(7));

        workbenchHotkeys.setOpaque(false);
        workbenchHotkeys.setForeground(ConsoleTheme.TEXT);
        workbenchHotkeys.setFocusable(false);
        workbenchHotkeys.setAlignmentX(LEFT_ALIGNMENT);
        card.add(workbenchHotkeys);
        card.add(Box.createVerticalStrut(8));

        ConsoleTheme.styleTextField(workbenchNameField);
        workbenchNameField.setAlignmentX(LEFT_ALIGNMENT);
        workbenchNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(smallLabel("Layout / composite name"));
        card.add(Box.createVerticalStrut(3));
        card.add(workbenchNameField);
        card.add(Box.createVerticalStrut(6));

        workbenchRole.setFocusable(false);
        workbenchRole.setAlignmentX(LEFT_ALIGNMENT);
        workbenchRole.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        workbenchRole.setSelectedItem(RailCompositeLibrary.Role.CUSTOM);
        card.add(smallLabel("Layout role"));
        card.add(Box.createVerticalStrut(3));
        card.add(workbenchRole);
        card.add(Box.createVerticalStrut(6));

        JButton save = button("Save Layout Evidence");
        save.setAlignmentX(LEFT_ALIGNMENT);
        save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        save.addActionListener(e -> saveWorkbenchLayout());
        card.add(save);
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createWrappedText(
                "Classification checkboxes still auto-save to rail_kit.tsv. Save Layout Evidence writes "
                + "the complete arranged ID/type/rotation + dX/dY layout to rail_composites.tsv.",
                4));
        return card;
    }

    private JPanel createRoutePreviewCard() {
        JPanel card = ConsoleTheme.createCard("A -> B Rail Route Preview V2");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Drag Point A to Point B. Straight sections use the checked Straight rail; "
                + "the bend prefers the first saved CURVE composite from Object Explorer. "
                + "A checked single Curve rail remains fallback only.",
                5));
        card.add(Box.createVerticalStrut(7));

        routePieceLabel.setAlignmentX(LEFT_ALIGNMENT);
        routeCurveLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(routePieceLabel);
        card.add(Box.createVerticalStrut(3));
        card.add(routeCurveLabel);
        card.add(Box.createVerticalStrut(6));

        JPanel useButtons = new JPanel(new GridLayout(1, 2, 5, 0));
        useButtons.setOpaque(false);
        useButtons.setAlignmentX(LEFT_ALIGNMENT);
        useButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JButton useSelectedStraight = button("Use Selected Straight");
        JButton reloadComposite = button("Reload CURVE Composite");
        useSelectedStraight.addActionListener(e -> configureSelectedRouteRail());
        reloadComposite.addActionListener(e -> reloadCurveComposite());
        useButtons.add(useSelectedStraight);
        useButtons.add(reloadComposite);
        card.add(useButtons);
        card.add(Box.createVerticalStrut(7));

        routeOrder.setFocusable(false);
        routeOrder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        routeOrder.setAlignmentX(LEFT_ALIGNMENT);
        routeOrder.setSelectedItem(RailRoutePreview.getRouteOrder());
        routeOrder.addActionListener(e -> {
            Object selectedOrder = routeOrder.getSelectedItem();
            if (selectedOrder instanceof RailRoutePreview.RouteOrder) {
                RailRoutePreview.setRouteOrder((RailRoutePreview.RouteOrder) selectedOrder);
                setStatus(RailRoutePreview.getStatus());
            }
        });
        card.add(smallLabel("Route order"));
        card.add(Box.createVerticalStrut(3));
        card.add(routeOrder);
        card.add(Box.createVerticalStrut(7));

        JPanel curveMapRow = new JPanel(new GridLayout(1, 2, 6, 0));
        curveMapRow.setOpaque(false);
        curveMapRow.setAlignmentX(LEFT_ALIGNMENT);
        curveMapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        curveMapRow.add(smallLabel("Curve map offset R0-R3"));
        curveMapOffset.setValue(Integer.valueOf(RailRoutePreview.getCurveRotationOffset()));
        curveMapRow.add(curveMapOffset);
        card.add(curveMapRow);
        card.add(Box.createVerticalStrut(5));

        JButton applyCurveMap = button("Apply Curve Rotation Offset");
        applyCurveMap.setAlignmentX(LEFT_ALIGNMENT);
        applyCurveMap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        applyCurveMap.addActionListener(e -> {
            RailRoutePreview.setCurveRotationOffset(number(curveMapOffset));
            setStatus(RailRoutePreview.getStatus());
        });
        card.add(applyCurveMap);
        card.add(Box.createVerticalStrut(7));

        JButton enable = button("Enable A->B Preview");
        JButton disable = button("Disable Preview");
        JButton clear = button("Clear Route");
        JButton routeStatus = button("Route Status");

        enable.addActionListener(e -> {
            ensureRouteRailConfigured();
            RailRoutePreview.setCurveRotationOffset(number(curveMapOffset));
            RailRoutePreview.setEnabled(true);
            setStatus(RailRoutePreview.getStatus());
        });
        disable.addActionListener(e -> {
            RailRoutePreview.setEnabled(false);
            setStatus(RailRoutePreview.getStatus());
        });
        clear.addActionListener(e -> {
            RailRoutePreview.clearRoute();
            setStatus(RailRoutePreview.getStatus());
        });
        routeStatus.addActionListener(e -> setStatus(RailRoutePreview.getStatus()));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 5, 5));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        buttons.add(enable);
        buttons.add(disable);
        buttons.add(clear);
        buttons.add(routeStatus);
        card.add(buttons);
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "If the curve bends into the wrong quadrant, change Curve map offset R0-R3 and Apply. "
                + "That rotates the complete four-corner mapping without changing the saved classifier data.",
                4));
        return card;
    }

    private JPanel createClassificationCard() {
        JPanel card = ConsoleTheme.createCard("Geometry classification");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Independent checkboxes. Check every role that visually applies; check/uncheck saves instantly.",
                3));
        card.add(Box.createVerticalStrut(7));

        JPanel checks = new JPanel(new GridLayout(0, 2, 5, 5));
        checks.setOpaque(false);
        checks.setAlignmentX(LEFT_ALIGNMENT);
        checks.add(straight);
        checks.add(curve);
        checks.add(merge);
        checks.add(split);
        checks.add(endBuffer);
        checks.add(crossing);
        checks.add(notRail);
        checks.add(unsure);
        card.add(checks);
        card.add(Box.createVerticalStrut(8));

        JPanel promote = new JPanel(new GridLayout(1, 3, 5, 0));
        promote.setOpaque(false);
        promote.setAlignmentX(LEFT_ALIGNMENT);
        promote.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JButton acceptJunction = button("Accept Junction");
        JButton acceptCrossing = button("Accept Crossing");
        JButton acceptSplitter = button("Accept Splitter");
        acceptJunction.addActionListener(e -> promoteSelectedSpecial(
                RailCompositeLibrary.ACCEPTED_JUNCTION_NAME,
                RailCompositeLibrary.Role.TURNOUT, "Junction"));
        acceptCrossing.addActionListener(e -> promoteSelectedSpecial(
                RailCompositeLibrary.ACCEPTED_CROSSING_NAME,
                RailCompositeLibrary.Role.CROSSING, "Crossing"));
        acceptSplitter.addActionListener(e -> promoteSelectedSpecial(
                RailCompositeLibrary.ACCEPTED_SPLITTER_NAME,
                RailCompositeLibrary.Role.TURNOUT, "Splitter"));
        promote.add(acceptJunction);
        promote.add(acceptCrossing);
        promote.add(acceptSplitter);
        card.add(promote);
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Accepted special buttons save the selected preview rotation as the canonical "
                + "single-object special asset. Use Object Explorer composites instead when the "
                + "special node needs multiple objects.",
                4));
        card.add(Box.createVerticalStrut(7));
        card.add(ConsoleTheme.createWrappedText(
                "Auto-save: Client/data/construction/asset_studio/rail_kit.tsv", 2));
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Rail classifier status");
        card.add(Box.createVerticalStrut(8));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(statusLabel);
        return card;
    }

    private void promoteSelectedSpecial(
            String canonicalName, RailCompositeLibrary.Role role, String label) {
        Candidate candidate = selected;
        if (candidate == null) {
            setStatus("Select and preview a rail candidate first.");
            return;
        }
        int rotation = number(previewRotation);
        String error = RailCompositeLibrary.promoteSingleSpecial(
                canonicalName, role, candidate.id, candidate.type, rotation);
        if (error != null) {
            setStatus(error);
            return;
        }
        RailRoutePreview.reloadSpecialComposites();
        setStatus(label + " accepted: ID " + candidate.id + " type "
                + candidate.type + " rot " + rotation + " -> " + canonicalName + ".");
    }

    private void configureSelectedRouteRail() {
        Candidate candidate = selected;
        if (candidate == null) {
            setStatus("Select a rail candidate first.");
            return;
        }

        ClassificationRecord record = recordFor(candidate);
        int rotation = number(previewRotation);
        RailRoutePreview.configure(candidate.name, candidate.id, candidate.type,
                rotation, selectedRouteOrder());
        routePieceLabel.setText("Straight rail: ID " + candidate.id
                + " type " + candidate.type + " N/S rot " + rotation
                + (record.straight ? " [checked Straight]" : " [manual]"));
        setStatus(RailRoutePreview.getStatus());
    }

    private void reloadCurveComposite() {
        boolean loaded = RailRoutePreview.reloadCurveComposite();
        routeCurveLabel.setText(loaded
                ? "Curve composite: " + RailRoutePreview.getConfiguredCurveCompositeName()
                : "Curve composite: none [single-object fallback]");
        setStatus(RailRoutePreview.getStatus());
    }

    private void configureSelectedCurveRail() {
        Candidate candidate = selected;
        if (candidate == null) {
            setStatus("Select a rail candidate first.");
            return;
        }

        ClassificationRecord record = recordFor(candidate);
        int rotation = number(previewRotation);
        RailRoutePreview.configureCurve(candidate.name, candidate.id, candidate.type, rotation);
        routeCurveLabel.setText("Curve rail: ID " + candidate.id
                + " type " + candidate.type + " base rot " + rotation
                + (record.curve ? " [checked Curve]" : " [manual]"));
        setStatus(RailRoutePreview.getStatus());
    }

    private void ensureRouteRailConfigured() {
        if (RailRoutePreview.getConfiguredObjectId() < 0) {
            for (Candidate candidate : candidates) {
                ClassificationRecord record = records.get(candidate.key());
                if (record != null && record.straight) {
                    RailRoutePreview.configure(candidate.name, candidate.id, candidate.type,
                            record.lastPreviewRotation, selectedRouteOrder());
                    routePieceLabel.setText("Straight rail: ID " + candidate.id
                            + " type " + candidate.type + " N/S rot "
                            + (record.lastPreviewRotation & 0x3) + " [first checked Straight]");
                    break;
                }
            }
        }

        reloadCurveComposite();

        if ("none".equals(RailRoutePreview.getConfiguredCurveCompositeName())
                && RailRoutePreview.getConfiguredCurveObjectId() < 0) {
            for (Candidate candidate : candidates) {
                ClassificationRecord record = records.get(candidate.key());
                if (record != null && record.curve) {
                    RailRoutePreview.configureCurve(candidate.name, candidate.id, candidate.type,
                            record.lastPreviewRotation);
                    routeCurveLabel.setText("Curve fallback: ID " + candidate.id
                            + " type " + candidate.type + " base rot "
                            + (record.lastPreviewRotation & 0x3) + " [first checked Curve]");
                    break;
                }
            }
        }

        if (RailRoutePreview.getConfiguredObjectId() < 0 && selected != null) {
            configureSelectedRouteRail();
        }
    }

    private RailRoutePreview.RouteOrder selectedRouteOrder() {
        Object value = routeOrder.getSelectedItem();
        return value instanceof RailRoutePreview.RouteOrder
                ? (RailRoutePreview.RouteOrder) value
                : RailRoutePreview.RouteOrder.X_THEN_Y;
    }

    private void refreshLiveRails() {
        CaptureBatch batch = AssetStudioCapture.capturePlayerArea(4);
        if (batch == null || !batch.isSuccess()) {
            setStatus("9x9 rail refresh failed: "
                    + (batch == null ? "live player/scene unavailable" : batch.getError()));
            return;
        }

        int before = candidates.size();
        replaceCandidates(batch.getEntries(), true);
        int railRows = 0;
        for (CaptureEntry entry : batch.getEntries()) {
            if (entry != null && entry.isRailCandidate()) {
                railRows++;
            }
        }
        setStatus("Live 9x9 refresh: " + railRows + " rail candidate row(s), "
                + Math.max(0, candidates.size() - before) + " new id/type candidate(s).");
    }

    private void replaceCandidates(List<CaptureEntry> sourceEntries, boolean preserveSelection) {
        String selectedKey = selected == null ? null : selected.key();
        Map<String, Candidate> merged = new LinkedHashMap<String, Candidate>();

        for (int id : RailCompositeLibrary.getEvidenceSeedObjectIds()) {
            Candidate candidate = new Candidate(id, 22, "id-" + id);
            ClassificationRecord saved = records.get(candidate.key());
            if (saved != null) {
                candidate.name = saved.name == null || saved.name.trim().isEmpty()
                        ? candidate.name : saved.name;
                candidate.addRotationText(saved.observedRotations);
            }
            merged.put(candidate.key(), candidate);
        }

        for (ClassificationRecord saved : records.values()) {
            String key = saved.key();
            if (!merged.containsKey(key)) {
                Candidate candidate = new Candidate(saved.id, saved.type, saved.name);
                candidate.addRotationText(saved.observedRotations);
                merged.put(key, candidate);
            }
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

        int rowToSelect = 0;
        if (preserveSelection && selectedKey != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (selectedKey.equals(candidates.get(i).key())) {
                    rowToSelect = i;
                    break;
                }
            }
        }
        if (!candidates.isEmpty()) {
            table.setRowSelectionInterval(rowToSelect, rowToSelect);
            table.scrollRectToVisible(table.getCellRect(rowToSelect, 0, true));
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
                ? "Saved ID " + selected.id + ": "
                        + (record.summary().length() == 0 ? "no classifications" : record.summary())
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
        String saveError = saveRecords();

        setStatus(saveError == null
                ? "Preview ID " + candidate.id + " type " + candidate.type + " rot " + rotation
                        + " at player +3 X. " + ObjectLabPreview.getStatus()
                : saveError);
    }


    private void dropSelectedCandidate(boolean advance) {
        Candidate candidate = selected;
        if (candidate == null) {
            setStatus("Select a rail candidate first.");
            return;
        }
        if (workbenchParts.size() >= 32) {
            setStatus("Rail Classifier Workbench is capped at 32 visual pieces.");
            return;
        }

        int slot = workbenchParts.size();
        int column = slot % 6;
        int row = slot / 6;
        int offsetX = clamp(-10 + column * 4, -12, 12);
        int offsetY = clamp(-8 + row * 4, -12, 12);
        LayoutPart part = new LayoutPart(candidate.name, candidate.id, candidate.type,
                number(previewRotation), offsetX, offsetY);
        workbenchParts.add(part);
        refreshWorkbenchList();
        workbenchList.setSelectedIndex(workbenchParts.size() - 1);
        workbenchList.ensureIndexIsVisible(workbenchParts.size() - 1);
        refreshActiveWorkbenchLabel();

        if (ensureWorkbenchAnchor()) {
            refreshWorkbenchPreview();
        }

        setStatus("Dropped ID " + part.id + " T" + part.type + " R" + part.rotation
                + " at dX " + part.offsetX + ", dY " + part.offsetY
                + ". Active piece now owns classifier ID/rotation.");

        if (advance) {
            moveSelection(1, false);
        }
    }

    private void refreshWorkbenchList() {
        workbenchModel.clear();
        for (int i = 0; i < workbenchParts.size(); i++) {
            workbenchModel.addElement("#" + (i + 1) + "  " + workbenchParts.get(i).describe());
        }
    }

    private int selectedWorkbenchIndex() {
        int index = workbenchList.getSelectedIndex();
        return index >= 0 && index < workbenchParts.size() ? index : -1;
    }

    private void refreshActiveWorkbenchLabel() {
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            activeWorkbenchPiece.setText("ACTIVE RAIL: none");
            return;
        }
        LayoutPart part = workbenchParts.get(index);
        activeWorkbenchPiece.setText("ACTIVE #" + (index + 1)
                + "  |  OBJECT ID " + part.id
                + "  |  T" + part.type
                + "  |  R" + part.rotation
                + "  |  dX " + part.offsetX
                + "  |  dY " + part.offsetY);
    }

    private void syncClassifierToActiveWorkbenchPart() {
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            return;
        }
        LayoutPart part = workbenchParts.get(index);
        for (int row = 0; row < candidates.size(); row++) {
            Candidate candidate = candidates.get(row);
            if (candidate.id == part.id && candidate.type == part.type) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                previewRotation.setValue(Integer.valueOf(part.rotation));
                ClassificationRecord record = recordFor(candidate);
                record.lastPreviewRotation = part.rotation;
                record.updatedAt = timestamp();
                saveRecords();
                return;
            }
        }
    }

    private void selectWorkbenchPart(int delta) {
        if (workbenchParts.isEmpty()) {
            return;
        }
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            index = delta < 0 ? workbenchParts.size() - 1 : 0;
        } else {
            index = (index + delta + workbenchParts.size()) % workbenchParts.size();
        }
        workbenchList.setSelectedIndex(index);
        workbenchList.ensureIndexIsVisible(index);
        syncClassifierToActiveWorkbenchPart();
        refreshActiveWorkbenchLabel();
        setStatus("Selected workbench rail #" + (index + 1) + ": "
                + workbenchParts.get(index).describe());
    }

    private void duplicateWorkbenchPart() {
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            setStatus("Select a workbench rail to duplicate.");
            return;
        }
        if (workbenchParts.size() >= 32) {
            setStatus("Rail Classifier Workbench is capped at 32 visual pieces.");
            return;
        }
        LayoutPart source = workbenchParts.get(index);
        LayoutPart copy = new LayoutPart(source.name, source.id, source.type, source.rotation,
                source.offsetX + 1, source.offsetY);
        workbenchParts.add(copy);
        refreshWorkbenchList();
        workbenchList.setSelectedIndex(workbenchParts.size() - 1);
        syncClassifierToActiveWorkbenchPart();
        refreshActiveWorkbenchLabel();
        refreshWorkbenchPreview();
        setStatus("Duplicated ID " + copy.id + " to dX " + copy.offsetX + ", dY " + copy.offsetY + ".");
    }

    private void removeWorkbenchPart() {
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            setStatus("Select a workbench rail to remove.");
            return;
        }
        LayoutPart removed = workbenchParts.remove(index);
        refreshWorkbenchList();
        if (workbenchParts.isEmpty()) {
            activeWorkbenchPiece.setText("ACTIVE RAIL: none");
            ObjectCompositePreview.hide();
        } else {
            workbenchList.setSelectedIndex(Math.min(index, workbenchParts.size() - 1));
            syncClassifierToActiveWorkbenchPart();
            refreshActiveWorkbenchLabel();
            refreshWorkbenchPreview();
        }
        setStatus("Removed workbench rail: " + removed.describe());
    }

    private void moveWorkbenchPart(int dx, int dy) {
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            setStatus("Select a workbench rail to move.");
            return;
        }
        LayoutPart part = workbenchParts.get(index);
        part.offsetX = clamp(part.offsetX + dx, -12, 12);
        part.offsetY = clamp(part.offsetY + dy, -12, 12);
        refreshWorkbenchList();
        workbenchList.setSelectedIndex(index);
        refreshActiveWorkbenchLabel();
        refreshWorkbenchPreview();
        setStatus("Moved ID " + part.id + " to dX " + part.offsetX + ", dY " + part.offsetY + ".");
    }

    private void rotateWorkbenchPart() {
        int index = selectedWorkbenchIndex();
        if (index < 0) {
            setStatus("Select a workbench rail to rotate.");
            return;
        }
        LayoutPart part = workbenchParts.get(index);
        part.rotation = (part.rotation + 1) & 0x3;
        refreshWorkbenchList();
        workbenchList.setSelectedIndex(index);
        previewRotation.setValue(Integer.valueOf(part.rotation));
        syncClassifierToActiveWorkbenchPart();
        refreshActiveWorkbenchLabel();
        refreshWorkbenchPreview();
        setStatus("Rotated ID " + part.id + " to R" + part.rotation
                + "; classifier rotation updated automatically.");
    }

    private boolean ensureWorkbenchAnchor() {
        if (workbenchAnchorPlane >= 0) {
            return true;
        }
        CaptureBatch anchor = AssetStudioCapture.capturePlayerArea(0);
        if (anchor == null || !anchor.isSuccess()) {
            setStatus("Workbench anchor failed: "
                    + (anchor == null ? "live player/scene unavailable" : anchor.getError()));
            return false;
        }
        workbenchAnchorX = anchor.getCenterX();
        workbenchAnchorY = anchor.getCenterY();
        workbenchAnchorPlane = anchor.getPlane();
        return true;
    }

    private void resetWorkbenchAnchor() {
        workbenchAnchorPlane = -1;
        if (ensureWorkbenchAnchor()) {
            refreshWorkbenchPreview();
            setStatus("Rail Classifier Workbench moved beside the current player position.");
        }
    }

    private void refreshWorkbenchPreview() {
        if (workbenchParts.isEmpty() || !ensureWorkbenchAnchor()) {
            return;
        }
        List<RailCompositeLibrary.Component> components =
                new ArrayList<RailCompositeLibrary.Component>();
        for (LayoutPart part : workbenchParts) {
            components.add(new RailCompositeLibrary.Component(
                    part.id, part.type, part.rotation, part.offsetX, part.offsetY));
        }
        ObjectLabPreview.hide();
        String name = workbenchNameField.getText() == null
                ? "Rail Classifier Workbench" : workbenchNameField.getText().trim();
        ObjectCompositePreview.showComposite(name, components,
                workbenchAnchorX, workbenchAnchorY, workbenchAnchorPlane, 3, 0);
    }

    private void clearWorkbench() {
        workbenchParts.clear();
        refreshWorkbenchList();
        activeWorkbenchPiece.setText("ACTIVE RAIL: none");
        ObjectCompositePreview.hide();
        setStatus("Rail Classifier Workbench cleared.");
    }

    private void saveWorkbenchLayout() {
        if (workbenchParts.isEmpty()) {
            setStatus("Drop at least one rail before saving layout evidence.");
            return;
        }
        List<RailCompositeLibrary.Component> components =
                new ArrayList<RailCompositeLibrary.Component>();
        for (LayoutPart part : workbenchParts) {
            components.add(new RailCompositeLibrary.Component(
                    part.id, part.type, part.rotation, part.offsetX, part.offsetY));
        }
        Object selectedRole = workbenchRole.getSelectedItem();
        RailCompositeLibrary.Role role = selectedRole instanceof RailCompositeLibrary.Role
                ? (RailCompositeLibrary.Role) selectedRole
                : RailCompositeLibrary.Role.CUSTOM;
        String error = RailCompositeLibrary.saveComposite(
                workbenchNameField.getText(), role, components);
        setStatus(error == null
                ? "Saved " + workbenchNameField.getText().trim() + " [" + role + "] with "
                        + components.size() + " rail piece(s); IDs/rotations came from classifier selection."
                : error);
    }

    private void installWorkbenchHotkeys() {
        if (workbenchHotkeysInstalled) {
            return;
        }
        workbenchHotkeysInstalled = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event.getID() != KeyEvent.KEY_PRESSED
                                || !workbenchHotkeys.isSelected()
                                || !RailKitClassifierPanel.this.isShowing()
                                || workbenchParts.isEmpty()) {
                            return false;
                        }

                        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                .getFocusOwner();
                        if (focus instanceof JTextField
                                || focus != null && SwingUtilities.getAncestorOfClass(
                                        JSpinner.class, focus) != null
                                || focus != null && SwingUtilities.getAncestorOfClass(
                                        JComboBox.class, focus) != null
                                || focus != null && SwingUtilities.getAncestorOfClass(
                                        JTable.class, focus) != null) {
                            return false;
                        }

                        int key = event.getKeyCode();
                        if (event.isControlDown() && key == KeyEvent.VK_D) {
                            duplicateWorkbenchPart();
                            return true;
                        }
                        if (key == KeyEvent.VK_OPEN_BRACKET) {
                            selectWorkbenchPart(-1);
                            return true;
                        }
                        if (key == KeyEvent.VK_CLOSE_BRACKET) {
                            selectWorkbenchPart(1);
                            return true;
                        }
                        if (key == KeyEvent.VK_R) {
                            rotateWorkbenchPart();
                            return true;
                        }
                        if (key == KeyEvent.VK_LEFT) {
                            moveWorkbenchPart(-1, 0);
                            return true;
                        }
                        if (key == KeyEvent.VK_RIGHT) {
                            moveWorkbenchPart(1, 0);
                            return true;
                        }
                        if (key == KeyEvent.VK_UP) {
                            moveWorkbenchPart(0, 1);
                            return true;
                        }
                        if (key == KeyEvent.VK_DOWN) {
                            moveWorkbenchPart(0, -1);
                            return true;
                        }
                        if (key == KeyEvent.VK_DELETE) {
                            removeWorkbenchPart();
                            return true;
                        }
                        return false;
                    }
                });
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
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, 0);

        table.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionKey);

        AbstractAction action = new AbstractAction() {
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
        };

        table.getActionMap().put(actionKey, action);
    }

    private ClassificationRecord recordFor(Candidate candidate) {
        ClassificationRecord record = records.get(candidate.key());
        if (record == null) {
            record = new ClassificationRecord(candidate.id, candidate.type);
            record.name = candidate.name;
            record.observedRotations = candidate.rotationsText();
            records.put(record.key(), record);
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
                    // Skip malformed user-edited rows while preserving valid rows.
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
            lines.add("# Matrix3 Test Console rail kit classifier");
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

    private void setStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
    }

    private static JCheckBox check(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(ConsoleTheme.TEXT);
        box.setFont(ConsoleTheme.BODY_FONT);
        box.setFocusable(false);
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

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
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
        private final String[] columns = {"ID", "Rot", "Class"};

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
            case 0:
                return Integer.valueOf(candidate.id);
            case 1:
                return candidate.rotationsText();
            case 2:
                return record == null ? "" : record.summary();
            default:
                return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Integer.class : String.class;
        }
    }


    private static final class LayoutPart {
        private final String name;
        private final int id;
        private final int type;
        private int rotation;
        private int offsetX;
        private int offsetY;

        private LayoutPart(String name, int id, int type, int rotation,
                int offsetX, int offsetY) {
            this.name = name == null || name.trim().isEmpty() ? "id-" + id : name;
            this.id = id;
            this.type = type;
            this.rotation = rotation & 0x3;
            this.offsetX = clamp(offsetX, -12, 12);
            this.offsetY = clamp(offsetY, -12, 12);
        }

        private String describe() {
            return "OBJECT ID " + id + " | T" + type + " | R" + rotation
                    + " | dX " + offsetX + " | dY " + offsetY
                    + (name.startsWith("id-") ? "" : " | " + name);
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
            return RailKitClassifierPanel.key(id, type);
        }

        private void addRotation(int rotation) {
            rotationMask |= 1 << (rotation & 0x3);
        }

        private void addRotationText(String rotations) {
            if (rotations == null || rotations.trim().isEmpty() || "-".equals(rotations.trim())) {
                return;
            }
            String[] values = rotations.split(",");
            for (String value : values) {
                try {
                    addRotation(Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
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
            return RailKitClassifierPanel.key(id, type);
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

    private static final class ViewportWidthPanel extends JPanel
            implements javax.swing.Scrollable {

        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(
                Rectangle visibleRect, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL
                    ? visibleRect.height : visibleRect.width;
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
