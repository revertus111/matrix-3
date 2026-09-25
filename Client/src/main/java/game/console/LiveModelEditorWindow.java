package game.console;

import game.DevDefinitionBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;
import game.LiveModelEditorPreview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;

/**
 * Live Model Editor Bundle 2.
 *
 * This window edits a client-only runtime clone that is rendered inside the live
 * Matrix3 scene. JSON is the authoring/project format. No cache or server-world
 * bytes are written by this bundle. Connected mesh components can now be edited independently.
 */
public final class LiveModelEditorWindow {

    private static final int PROJECT_VERSION = 2;
    private static final File PROJECT_DIR = new File("dev-model-projects");

    private static JFrame frame;
    private static LiveModelEditorWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLabel nameLabel = valueLabel();
    private final JLabel objectIdLabel = valueLabel();
    private final JLabel modelIdsLabel = valueLabel();
    private final JLabel sourceTileLabel = valueLabel();
    private final JLabel statusLabel = new JLabel("Right-click an object -> Dev > Edit Model Live.");
    private final JLabel partStatusLabel = new JLabel("Parts not detected yet.");
    private final DefaultListModel<String> partListModel = new DefaultListModel<String>();
    private final JList<String> partList = new JList<String>(partListModel);

    private final JSpinner typeSpinner = spinner(10, 0, 22, 1);
    private final JSpinner objectRotationSpinner = spinner(0, 0, 3, 1);
    private final JSpinner tileOffsetXSpinner = spinner(2, -12, 12, 1);
    private final JSpinner tileOffsetYSpinner = spinner(0, -12, 12, 1);

    private final JSpinner scaleXSpinner = spinner(100, 10, 400, 5);
    private final JSpinner scaleYSpinner = spinner(100, 10, 400, 5);
    private final JSpinner scaleZSpinner = spinner(100, 10, 400, 5);
    private final JSpinner moveXSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner moveYSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner moveZSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner yawSpinner = spinner(0, -359, 359, 5);

    private final JSpinner partScaleXSpinner = spinner(100, 10, 400, 5);
    private final JSpinner partScaleYSpinner = spinner(100, 10, 400, 5);
    private final JSpinner partScaleZSpinner = spinner(100, 10, 400, 5);
    private final JSpinner partMoveXSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner partMoveYSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner partMoveZSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner partYawSpinner = spinner(0, -359, 359, 5);

    private int objectId = -1;
    private String objectName = "Object";
    private int[] sourceModelIds = new int[0];
    private int sourceX;
    private int sourceY;
    private int sourcePlane;
    private boolean hasSource;
    private boolean suppressLiveRefresh;
    private boolean suppressPartRefresh;

    private LiveModelEditorWindow() {
        buildUi();
    }

    public static void open(DevTarget target) {
        if (target == null || target.getType() != TargetType.OBJECT) {
            return;
        }
        ensureWindow();
        instance.capture(target);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new LiveModelEditorWindow();
        frame = new JFrame("Matrix3 Live Model Editor");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(650, 650));
        frame.setSize(new Dimension(760, 820));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.WINDOW);
        JLabel title = new JLabel("LIVE MODEL EDITOR - BUNDLE 2");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Connected mesh parts / live per-part transforms / JSON project");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(14));
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.WINDOW);
        content.add(createTargetCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPlacementCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createTransformCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPartsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createProjectCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ConsoleTheme.styleScrollPane(scroll);
        root.add(scroll, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);

        ChangeListener live = e -> refreshIfActive();
        typeSpinner.addChangeListener(live);
        objectRotationSpinner.addChangeListener(live);
        tileOffsetXSpinner.addChangeListener(live);
        tileOffsetYSpinner.addChangeListener(live);
        scaleXSpinner.addChangeListener(live);
        scaleYSpinner.addChangeListener(live);
        scaleZSpinner.addChangeListener(live);
        moveXSpinner.addChangeListener(live);
        moveYSpinner.addChangeListener(live);
        moveZSpinner.addChangeListener(live);
        yawSpinner.addChangeListener(live);

        ChangeListener partLive = e -> updateSelectedPartTransform();
        partScaleXSpinner.addChangeListener(partLive);
        partScaleYSpinner.addChangeListener(partLive);
        partScaleZSpinner.addChangeListener(partLive);
        partMoveXSpinner.addChangeListener(partLive);
        partMoveYSpinner.addChangeListener(partLive);
        partMoveZSpinner.addChangeListener(partLive);
        partYawSpinner.addChangeListener(partLive);

        partList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        partList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressPartRefresh) {
                int index = partList.getSelectedIndex();
                if (index >= 0 && LiveModelEditorPreview.selectPart(index)) {
                    loadSelectedPartEditors();
                    statusLabel.setText("Selected mesh part " + index + " in the live model.");
                }
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "liveModelUndo");
        root.getActionMap().put("liveModelUndo", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                if (LiveModelEditorPreview.undoPartEdit()) {
                    refreshPartList();
                    loadSelectedPartEditors();
                    statusLabel.setText("Undo: restored previous mesh-part edit.");
                } else {
                    statusLabel.setText("Nothing to undo.");
                }
            }
        });
    }

    private JPanel createTargetCard() {
        JPanel card = ConsoleTheme.createCard("Runtime target");
        card.add(Box.createVerticalStrut(9));
        JPanel grid = new JPanel(new GridLayout(4, 2, 8, 7));
        grid.setOpaque(false);
        addRow(grid, "Object", nameLabel);
        addRow(grid, "Definition ID", objectIdLabel);
        addRow(grid, "Source model ID(s)", modelIdsLabel);
        addRow(grid, "Source tile", sourceTileLabel);
        card.add(grid);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Bundle 1 draws a private editable model clone inside the live client. "
                + "The original scene object remains authoritative and unchanged. "
                + "The default clone is two tiles east to avoid overlap; set preview offsets to 0/0 "
                + "to overlay it on the selected object.", 5));
        return card;
    }

    private JPanel createPlacementCard() {
        JPanel card = ConsoleTheme.createCard("Object render context");
        card.add(Box.createVerticalStrut(9));
        JPanel grid = new JPanel(new GridLayout(2, 4, 7, 7));
        grid.setOpaque(false);
        grid.add(label("Object type"));
        grid.add(typeSpinner);
        grid.add(label("Object rotation"));
        grid.add(objectRotationSpinner);
        grid.add(label("Preview tile X"));
        grid.add(tileOffsetXSpinner);
        grid.add(label("Preview tile Y"));
        grid.add(tileOffsetYSpinner);
        card.add(grid);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Type/rotation remain explicit because the current Dev target carries definition ID and tile, "
                + "not a proven live scene-shape/rotation value. Type 10 / rotation 0 is the safe default for "
                + "the Smelter proof; adjust if a different object needs another shape.", 5));
        return card;
    }

    private JPanel createTransformCard() {
        JPanel card = ConsoleTheme.createCard("Live model transforms");
        card.add(Box.createVerticalStrut(9));

        JPanel scale = new JPanel(new GridLayout(2, 3, 7, 7));
        scale.setOpaque(false);
        scale.add(label("Scale X %"));
        scale.add(label("Scale Y %"));
        scale.add(label("Scale Z %"));
        scale.add(scaleXSpinner);
        scale.add(scaleYSpinner);
        scale.add(scaleZSpinner);
        card.add(scale);

        card.add(Box.createVerticalStrut(9));
        JPanel move = new JPanel(new GridLayout(2, 4, 7, 7));
        move.setOpaque(false);
        move.add(label("Move X"));
        move.add(label("Move Y"));
        move.add(label("Move Z"));
        move.add(label("Yaw degrees"));
        move.add(moveXSpinner);
        move.add(moveYSpinner);
        move.add(moveZSpinner);
        move.add(yawSpinner);
        card.add(move);

        card.add(Box.createVerticalStrut(9));
        JButton show = button("Show / Refresh Runtime Clone");
        JButton hide = button("Hide Runtime Clone");
        JButton reset = button("Reset Transforms");
        show.addActionListener(e -> refreshPreview());
        hide.addActionListener(e -> {
            LiveModelEditorPreview.hide();
            statusLabel.setText("Runtime clone hidden. Source object was not changed.");
        });
        reset.addActionListener(e -> resetTransforms());

        JPanel actions = new JPanel(new GridLayout(1, 3, 7, 0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        actions.add(show);
        actions.add(hide);
        actions.add(reset);
        card.add(actions);
        return card;
    }

    private JPanel createPartsCard() {
        JPanel card = ConsoleTheme.createCard("Mesh parts - connected components");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Detect Parts splits the source Class159 mesh by triangle connectivity. Select a part to highlight it "
                + "white in-world, then move/rotate/scale only that component. Hide/Delete/Duplicate and Ctrl+Z are "
                + "session-safe and are stored in the JSON project. In-world mouse picking is the remaining carryover; "
                + "this bundle selects parts from the list while rendering the edit directly in the live scene.", 6));
        card.add(Box.createVerticalStrut(9));

        partStatusLabel.setFont(ConsoleTheme.SMALL_FONT);
        partStatusLabel.setForeground(ConsoleTheme.ACCENT);
        partStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(partStatusLabel);
        card.add(Box.createVerticalStrut(7));

        partList.setVisibleRowCount(7);
        ConsoleTheme.styleList(partList);
        JScrollPane partScroll = new JScrollPane(partList);
        partScroll.setPreferredSize(new Dimension(620, 155));
        partScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));
        ConsoleTheme.styleScrollPane(partScroll);
        card.add(partScroll);

        card.add(Box.createVerticalStrut(9));
        JPanel scale = new JPanel(new GridLayout(2, 3, 7, 7));
        scale.setOpaque(false);
        scale.add(label("Part Scale X %"));
        scale.add(label("Part Scale Y %"));
        scale.add(label("Part Scale Z %"));
        scale.add(partScaleXSpinner);
        scale.add(partScaleYSpinner);
        scale.add(partScaleZSpinner);
        card.add(scale);

        card.add(Box.createVerticalStrut(7));
        JPanel move = new JPanel(new GridLayout(2, 4, 7, 7));
        move.setOpaque(false);
        move.add(label("Part Move X"));
        move.add(label("Part Move Y"));
        move.add(label("Part Move Z"));
        move.add(label("Part Yaw"));
        move.add(partMoveXSpinner);
        move.add(partMoveYSpinner);
        move.add(partMoveZSpinner);
        move.add(partYawSpinner);
        card.add(move);

        card.add(Box.createVerticalStrut(9));
        JButton detect = button("Detect / Rebuild Parts");
        JButton isolate = button("Toggle Isolate");
        JButton showAll = button("Show All");
        JButton hide = button("Hide / Show Selected");
        JButton duplicate = button("Duplicate Selected");
        JButton delete = button("Delete Selected");
        JButton undo = button("Undo (Ctrl+Z)");

        detect.addActionListener(e -> initializeParts());
        isolate.addActionListener(e -> {
            LiveModelEditorPreview.toggleIsolatePart();
            partStatusLabel.setText(LiveModelEditorPreview.isPartIsolated()
                    ? "Isolate ON - only the selected part is rendered."
                    : "Isolate OFF - all visible parts are rendered.");
        });
        showAll.addActionListener(e -> {
            LiveModelEditorPreview.showAllParts();
            refreshPartList();
        });
        hide.addActionListener(e -> {
            if (LiveModelEditorPreview.toggleSelectedPartHidden()) {
                refreshPartList();
                statusLabel.setText("Toggled selected part visibility.");
            }
        });
        duplicate.addActionListener(e -> {
            if (LiveModelEditorPreview.duplicateSelectedPart()) {
                refreshPartList();
                loadSelectedPartEditors();
                statusLabel.setText("Duplicated selected mesh part with a +128 X offset.");
            }
        });
        delete.addActionListener(e -> {
            if (LiveModelEditorPreview.deleteSelectedPart()) {
                refreshPartList();
                statusLabel.setText("Deleted selected mesh part from this authoring project. Ctrl+Z restores it.");
            }
        });
        undo.addActionListener(e -> {
            if (LiveModelEditorPreview.undoPartEdit()) {
                refreshPartList();
                loadSelectedPartEditors();
                statusLabel.setText("Undo: restored previous mesh-part edit.");
            } else {
                statusLabel.setText("Nothing to undo.");
            }
        });

        JPanel first = new JPanel(new GridLayout(1, 3, 7, 0));
        first.setOpaque(false);
        first.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        first.add(detect); first.add(isolate); first.add(showAll);
        card.add(first);
        card.add(Box.createVerticalStrut(7));

        JPanel second = new JPanel(new GridLayout(1, 4, 7, 0));
        second.setOpaque(false);
        second.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        second.add(hide); second.add(duplicate); second.add(delete); second.add(undo);
        card.add(second);
        return card;
    }

    private JPanel createProjectCard() {
        JPanel card = ConsoleTheme.createCard("JSON project");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Save stores source object/model IDs, whole-model transforms and mesh-part edits under dev-model-projects. "
                + "Load restores the authoring state and redraws the runtime clone. "
                + "This bundle never writes revision-830 cache bytes.", 5));
        card.add(Box.createVerticalStrut(9));

        JButton save = button("Save Project");
        JButton load = button("Load Project...");
        save.addActionListener(e -> saveProject());
        load.addActionListener(e -> loadProject());

        JPanel actions = new JPanel(new GridLayout(1, 2, 7, 0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        actions.add(save);
        actions.add(load);
        card.add(actions);
        return card;
    }

    private void capture(DevTarget target) {
        objectId = target.getId();
        objectName = target.getName() == null ? "Object" : target.getName();
        sourceModelIds = DevDefinitionBridge.getObjectModelIds(objectId);
        sourceX = target.getWorldX();
        sourceY = target.getWorldY();
        sourcePlane = target.getPlane();
        hasSource = objectId >= 0;

        suppressLiveRefresh = true;
        try {
            nameLabel.setText(objectName);
            objectIdLabel.setText(Integer.toString(objectId));
            modelIdsLabel.setText(joinIds(sourceModelIds));
            sourceTileLabel.setText(sourceX + ", " + sourceY + ", " + sourcePlane);
            resetEditorsOnly();
        } finally {
            suppressLiveRefresh = false;
        }

        refreshPreview();
        initializeParts();
        statusLabel.setText("Runtime clone opened for " + objectName + " (" + objectId + ")"
                + (sourceModelIds.length == 0 ? "." : " model=" + sourceModelIds[0] + "."));
    }

    private void refreshIfActive() {
        if (!suppressLiveRefresh && hasSource && LiveModelEditorPreview.isActive()
                && LiveModelEditorPreview.getObjectId() == objectId) {
            refreshPreview();
        }
    }

    private void refreshPreview() {
        if (!hasSource || objectId < 0) {
            statusLabel.setText("Capture a valid object target first.");
            return;
        }
        LiveModelEditorPreview.show(objectName, objectId,
                number(typeSpinner), number(objectRotationSpinner),
                sourceX, sourceY, sourcePlane,
                number(tileOffsetXSpinner), number(tileOffsetYSpinner),
                number(scaleXSpinner), number(scaleYSpinner), number(scaleZSpinner),
                number(moveXSpinner), number(moveYSpinner), number(moveZSpinner),
                number(yawSpinner));
        statusLabel.setText(LiveModelEditorPreview.getStatus());
        if (partListModel.size() > 0 && LiveModelEditorPreview.getPartLabels().length == 0) {
            refreshPartList();
        }
    }

    private void initializeParts() {
        int count = LiveModelEditorPreview.initializeParts();
        refreshPartList();
        if (count > 0 && LiveModelEditorPreview.getSelectedPart() < 0) {
            suppressPartRefresh = true;
            try {
                partList.setSelectedIndex(0);
                LiveModelEditorPreview.selectPart(0);
            } finally {
                suppressPartRefresh = false;
            }
            loadSelectedPartEditors();
        }
        partStatusLabel.setText(count > 0
                ? count + " connected components detected. Selected part is highlighted in-world."
                : LiveModelEditorPreview.getStatus());
    }

    private void refreshPartList() {
        String[] labels = LiveModelEditorPreview.getPartLabels();
        int selected = LiveModelEditorPreview.getSelectedPart();
        suppressPartRefresh = true;
        try {
            partListModel.clear();
            for (String label : labels) partListModel.addElement(label);
            if (selected >= 0 && selected < labels.length) partList.setSelectedIndex(selected);
            else partList.clearSelection();
        } finally {
            suppressPartRefresh = false;
        }
        if (labels.length == 0) partStatusLabel.setText("Parts not detected for the current object type.");
        else partStatusLabel.setText(labels.length + " editable part entries"
                + (LiveModelEditorPreview.isPartIsolated() ? " - isolate ON" : ""));
    }

    private void loadSelectedPartEditors() {
        int[] transform = LiveModelEditorPreview.getSelectedPartTransform();
        suppressPartRefresh = true;
        try {
            partScaleXSpinner.setValue(Integer.valueOf(transform[0]));
            partScaleYSpinner.setValue(Integer.valueOf(transform[1]));
            partScaleZSpinner.setValue(Integer.valueOf(transform[2]));
            partMoveXSpinner.setValue(Integer.valueOf(transform[3]));
            partMoveYSpinner.setValue(Integer.valueOf(transform[4]));
            partMoveZSpinner.setValue(Integer.valueOf(transform[5]));
            partYawSpinner.setValue(Integer.valueOf(transform[6]));
        } finally {
            suppressPartRefresh = false;
        }
    }

    private void updateSelectedPartTransform() {
        if (suppressPartRefresh || LiveModelEditorPreview.getSelectedPart() < 0) return;
        if (LiveModelEditorPreview.setSelectedPartTransform(
                number(partScaleXSpinner), number(partScaleYSpinner), number(partScaleZSpinner),
                number(partMoveXSpinner), number(partMoveYSpinner), number(partMoveZSpinner),
                number(partYawSpinner))) {
            statusLabel.setText("Applied live transform to selected mesh part.");
        }
    }

    private void resetTransforms() {
        suppressLiveRefresh = true;
        try {
            resetEditorsOnly();
        } finally {
            suppressLiveRefresh = false;
        }
        refreshPreview();
    }

    private void resetEditorsOnly() {
        typeSpinner.setValue(Integer.valueOf(10));
        objectRotationSpinner.setValue(Integer.valueOf(0));
        tileOffsetXSpinner.setValue(Integer.valueOf(2));
        tileOffsetYSpinner.setValue(Integer.valueOf(0));
        scaleXSpinner.setValue(Integer.valueOf(100));
        scaleYSpinner.setValue(Integer.valueOf(100));
        scaleZSpinner.setValue(Integer.valueOf(100));
        moveXSpinner.setValue(Integer.valueOf(0));
        moveYSpinner.setValue(Integer.valueOf(0));
        moveZSpinner.setValue(Integer.valueOf(0));
        yawSpinner.setValue(Integer.valueOf(0));
    }

    private void saveProject() {
        if (!hasSource) {
            statusLabel.setText("No live object target is loaded.");
            return;
        }
        try {
            if (!PROJECT_DIR.exists() && !PROJECT_DIR.mkdirs()) {
                throw new IllegalStateException("Could not create " + PROJECT_DIR.getPath());
            }
            int primaryModel = sourceModelIds.length == 0 ? -1 : sourceModelIds[0];
            File file = new File(PROJECT_DIR,
                    "object_" + objectId + "_model_" + primaryModel + ".json");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8));
            try {
                writer.write(toJson());
            } finally {
                writer.close();
            }
            statusLabel.setText("Saved JSON project: " + file.getPath());
        } catch (Exception ex) {
            statusLabel.setText("Save failed: " + rootMessage(ex));
        }
    }

    private void loadProject() {
        if (!PROJECT_DIR.exists()) {
            PROJECT_DIR.mkdirs();
        }
        JFileChooser chooser = new JFileChooser(PROJECT_DIR);
        chooser.setDialogTitle("Load Matrix3 live model project");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            String json = readFile(file);
            int version = readInt(json, "version", -1);
            if (version < 1 || version > PROJECT_VERSION) {
                throw new IllegalArgumentException("Unsupported project version " + version);
            }

            suppressLiveRefresh = true;
            try {
                objectId = readInt(json, "objectId", -1);
                objectName = readString(json, "objectName", "Object");
                sourceModelIds = readIntArray(json, "sourceModelIds");
                sourceX = readInt(json, "sourceX", 0);
                sourceY = readInt(json, "sourceY", 0);
                sourcePlane = readInt(json, "sourcePlane", 0);
                hasSource = objectId >= 0;

                typeSpinner.setValue(Integer.valueOf(readInt(json, "objectType", 10)));
                objectRotationSpinner.setValue(Integer.valueOf(readInt(json, "objectRotation", 0)));
                tileOffsetXSpinner.setValue(Integer.valueOf(readInt(json, "previewOffsetX", 2)));
                tileOffsetYSpinner.setValue(Integer.valueOf(readInt(json, "previewOffsetY", 0)));
                scaleXSpinner.setValue(Integer.valueOf(readInt(json, "scaleXPercent", 100)));
                scaleYSpinner.setValue(Integer.valueOf(readInt(json, "scaleYPercent", 100)));
                scaleZSpinner.setValue(Integer.valueOf(readInt(json, "scaleZPercent", 100)));
                moveXSpinner.setValue(Integer.valueOf(readInt(json, "translateX", 0)));
                moveYSpinner.setValue(Integer.valueOf(readInt(json, "translateY", 0)));
                moveZSpinner.setValue(Integer.valueOf(readInt(json, "translateZ", 0)));
                yawSpinner.setValue(Integer.valueOf(readInt(json, "yawDegrees", 0)));

                nameLabel.setText(objectName);
                objectIdLabel.setText(Integer.toString(objectId));
                modelIdsLabel.setText(joinIds(sourceModelIds));
                sourceTileLabel.setText(sourceX + ", " + sourceY + ", " + sourcePlane);
            } finally {
                suppressLiveRefresh = false;
            }

            refreshPreview();
            initializeParts();
            if (version >= 2) {
                LiveModelEditorPreview.loadPartProjectJson(json);
                refreshPartList();
                loadSelectedPartEditors();
            }
            statusLabel.setText("Loaded JSON project: " + file.getPath());
        } catch (Exception ex) {
            statusLabel.setText("Load failed: " + rootMessage(ex));
        }
    }

    private String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"format\": \"matrix3-live-model-project\",\n");
        out.append("  \"version\": ").append(PROJECT_VERSION).append(",\n");
        out.append("  \"objectId\": ").append(objectId).append(",\n");
        out.append("  \"objectName\": \"").append(escapeJson(objectName)).append("\",\n");
        out.append("  \"sourceModelIds\": ").append(intArrayJson(sourceModelIds)).append(",\n");
        out.append("  \"sourceX\": ").append(sourceX).append(",\n");
        out.append("  \"sourceY\": ").append(sourceY).append(",\n");
        out.append("  \"sourcePlane\": ").append(sourcePlane).append(",\n");
        out.append("  \"objectType\": ").append(number(typeSpinner)).append(",\n");
        out.append("  \"objectRotation\": ").append(number(objectRotationSpinner)).append(",\n");
        out.append("  \"previewOffsetX\": ").append(number(tileOffsetXSpinner)).append(",\n");
        out.append("  \"previewOffsetY\": ").append(number(tileOffsetYSpinner)).append(",\n");
        out.append("  \"scaleXPercent\": ").append(number(scaleXSpinner)).append(",\n");
        out.append("  \"scaleYPercent\": ").append(number(scaleYSpinner)).append(",\n");
        out.append("  \"scaleZPercent\": ").append(number(scaleZSpinner)).append(",\n");
        out.append("  \"translateX\": ").append(number(moveXSpinner)).append(",\n");
        out.append("  \"translateY\": ").append(number(moveYSpinner)).append(",\n");
        out.append("  \"translateZ\": ").append(number(moveZSpinner)).append(",\n");
        out.append("  \"yawDegrees\": ").append(number(yawSpinner)).append(",\n");
        out.append(LiveModelEditorPreview.getPartProjectJsonFields()).append("\n");
        out.append("}\n");
        return out.toString();
    }

    private static String readFile(File file) throws Exception {
        StringBuilder out = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return out.toString();
    }

    private static int readInt(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String readString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : fallback;
    }

    private static int[] readIntArray(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!matcher.find()) {
            return new int[0];
        }
        String body = matcher.group(1).trim();
        if (body.length() == 0) {
            return new int[0];
        }
        String[] parts = body.split(",");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i].trim());
        }
        return values;
    }

    private static String intArrayJson(int[] values) {
        StringBuilder out = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(", ");
                out.append(values[i]);
            }
        }
        return out.append(']').toString();
    }

    private static String joinIds(int[] values) {
        if (values == null || values.length == 0) {
            return "None / unresolved";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(", ");
            out.append(values[i]);
        }
        return out.toString();
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        ConsoleTheme.styleButton(button);
        button.setFocusable(false);
        return button;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        return label;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private static void addRow(JPanel panel, String name, Component value) {
        panel.add(label(name));
        panel.add(value);
    }
}
