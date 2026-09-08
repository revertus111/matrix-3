package game.console;

import game.ClientConsoleInterfaceBridge;
import game.ClientConsoleInterfaceBridge.ComponentOverride;
import game.ClientConsoleInterfaceBridge.ComponentSnapshot;
import game.ClientConsoleInterfaceBridge.InterfaceSnapshot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.ItemEvent;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Professional live Matrix3 interface/component inspector and override editor.
 */
public final class InterfaceEditorPanel extends JScrollPane {

    private static final long serialVersionUID = -8864251023502314325L;
    private static final int REFRESH_DELAY_MS = 350;
    private static final int LIVE_APPLY_DELAY_MS = 50;

    private static final RangeOption RANGE_FINE = new RangeOption("Fine +/-64", 64);
    private static final RangeOption RANGE_NORMAL = new RangeOption("Normal +/-256", 256);
    private static final RangeOption RANGE_WIDE = new RangeOption("Wide +/-1024", 1024);

    private final javax.swing.JTextField targetField = new javax.swing.JTextField("671:27");
    private final javax.swing.JTextField searchField = new javax.swing.JTextField();
    private final DefaultListModel<ComponentSnapshot> componentModel = new DefaultListModel<ComponentSnapshot>();
    private final JList<ComponentSnapshot> componentList = new JList<ComponentSnapshot>(componentModel);

    private final JLabel typeValue = ConsoleTheme.createValueLabel();
    private final JLabel parentValue = ConsoleTheme.createValueLabel();
    private final JLabel itemValue = ConsoleTheme.createValueLabel();
    private final JLabel childrenValue = ConsoleTheme.createValueLabel();

    private final LiveGeometryControl baseXControl = new LiveGeometryControl("X", false);
    private final LiveGeometryControl baseYControl = new LiveGeometryControl("Y", false);
    private final LiveGeometryControl baseWidthControl = new LiveGeometryControl("Width", true);
    private final LiveGeometryControl baseHeightControl = new LiveGeometryControl("Height", true);

    private final LiveGeometryControl runtimeXControl = new LiveGeometryControl("X", false);
    private final LiveGeometryControl runtimeYControl = new LiveGeometryControl("Y", false);
    private final LiveGeometryControl runtimeWidthControl = new LiveGeometryControl("Width", true);
    private final LiveGeometryControl runtimeHeightControl = new LiveGeometryControl("Height", true);

    private final javax.swing.JTextField xAlignField = numberField();
    private final javax.swing.JTextField yAlignField = numberField();
    private final javax.swing.JTextField widthAlignField = numberField();
    private final javax.swing.JTextField heightAlignField = numberField();

    private final JComboBox<RangeOption> geometryRangeCombo = new JComboBox<RangeOption>(
            new RangeOption[] { RANGE_FINE, RANGE_NORMAL, RANGE_WIDE });
    private final JCheckBox liveGeometryCheck = checkBox("Live geometry - sliders and +/- apply instantly");
    private final JCheckBox pinRuntimeCheck = checkBox("Pin runtime X/Y/W/H every client cycle");
    private final JCheckBox overrideTextCheck = checkBox("Override text");
    private final javax.swing.JTextField textField = new javax.swing.JTextField();
    private final JCheckBox overrideSpriteCheck = checkBox("Override sprite");
    private final javax.swing.JTextField spriteField = numberField();

    private final JLabel statusLabel = new JLabel("Enter an interface ID or interface:component target.");

    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refreshFromBridge());
    private final Timer liveApplyTimer = new Timer(LIVE_APPLY_DELAY_MS, e -> flushPendingLiveApply());

    private int loadedInterfaceId = -1;
    private int pendingSelectComponent = -1;
    private int selectedComponentId = -1;
    private long lastSnapshotSequence = -1L;
    private boolean populating;
    private boolean dirty;
    private boolean pendingLiveApply;

    public InterfaceEditorPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("INTERFACE EDITOR"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Live component geometry and visual overrides"));
        content.add(Box.createVerticalStrut(6));
        content.add(ConsoleTheme.createWrappedText(
                "Drag geometry sliders and watch the interface update while you tune it. "
                + "Exact values, reset, and copy/export remain available for final cleanup.", 3));
        content.add(Box.createVerticalStrut(16));

        content.add(createTargetCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createComponentsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createInspectorCard());
        content.add(Box.createVerticalStrut(10));

        ConsoleTheme.styleStatus(statusLabel, false);
        content.add(statusLabel);
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        installListeners();
        populating = true;
        try {
            geometryRangeCombo.setSelectedItem(RANGE_NORMAL);
            liveGeometryCheck.setSelected(true);
            pinRuntimeCheck.setSelected(true);
        } finally {
            populating = false;
        }

        refreshTimer.setCoalesce(true);
        liveApplyTimer.setCoalesce(true);
        liveApplyTimer.setRepeats(true);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (isShowing()) {
                loadTarget();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
                cancelPendingLiveApply();
            }
        });
    }

    private JPanel createTargetCard() {
        JPanel card = ConsoleTheme.createCard("Target");
        card.add(Box.createVerticalStrut(9));

        JPanel row = transparentRow(new BorderLayout(8, 0));
        ConsoleTheme.styleTextField(targetField);
        targetField.setToolTipText("Examples: 671 or 671:27");

        JButton loadButton = new JButton("Load");
        ConsoleTheme.styleButton(loadButton);
        loadButton.addActionListener(e -> loadTarget());

        row.add(targetField, BorderLayout.CENTER);
        row.add(loadButton, BorderLayout.EAST);
        card.add(row);
        card.add(Box.createVerticalStrut(6));
        card.add(ConsoleTheme.createWrappedText(
                "Use interface:component to jump straight to a known child.", 2));
        return card;
    }

    private JPanel createComponentsCard() {
        JPanel card = ConsoleTheme.createCard("Components");
        card.add(Box.createVerticalStrut(9));

        ConsoleTheme.styleTextField(searchField);
        searchField.setToolTipText("Filter by component ID, type, or visible/static text.");
        card.add(searchField);
        card.add(Box.createVerticalStrut(8));

        componentList.setBackground(ConsoleTheme.INPUT);
        componentList.setForeground(ConsoleTheme.TEXT);
        componentList.setSelectionBackground(ConsoleTheme.ACCENT_DARK);
        componentList.setSelectionForeground(ConsoleTheme.TEXT);
        componentList.setFont(ConsoleTheme.SMALL_FONT);
        componentList.setFixedCellHeight(30);
        componentList.setCellRenderer(new ComponentRenderer());

        JScrollPane listScroll = new JScrollPane(componentList);
        listScroll.setPreferredSize(new Dimension(1, 180));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        ConsoleTheme.styleScrollPane(listScroll);
        listScroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.BORDER));
        card.add(listScroll);
        return card;
    }

    private JPanel createInspectorCard() {
        JPanel card = ConsoleTheme.createCard("Selected component");
        card.add(Box.createVerticalStrut(9));

        card.add(ConsoleTheme.createValueRow("Type", typeValue));
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createValueRow("Parent", parentValue));
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createValueRow("Item ID", itemValue));
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createValueRow("Children", childrenValue));

        card.add(Box.createVerticalStrut(14));
        card.add(createGeometryToolbar());

        card.add(Box.createVerticalStrut(14));
        card.add(sectionLabel("Base geometry"));
        card.add(Box.createVerticalStrut(6));
        card.add(baseXControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        card.add(baseYControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        card.add(baseWidthControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        card.add(baseHeightControl.getPanel());

        card.add(Box.createVerticalStrut(14));
        card.add(sectionLabel("Runtime geometry"));
        card.add(Box.createVerticalStrut(6));
        card.add(runtimeXControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        card.add(runtimeYControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        card.add(runtimeWidthControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        card.add(runtimeHeightControl.getPanel());
        card.add(Box.createVerticalStrut(7));
        styleCheck(pinRuntimeCheck);
        card.add(pinRuntimeCheck);

        card.add(Box.createVerticalStrut(14));
        card.add(sectionLabel("Alignment"));
        card.add(Box.createVerticalStrut(6));
        card.add(createQuadRow(
                new String[] { "X", "Y", "W", "H" },
                new javax.swing.JTextField[] { xAlignField, yAlignField, widthAlignField, heightAlignField }));

        card.add(Box.createVerticalStrut(14));
        card.add(sectionLabel("Visual"));
        card.add(Box.createVerticalStrut(6));
        styleCheck(overrideTextCheck);
        card.add(overrideTextCheck);
        card.add(Box.createVerticalStrut(5));
        ConsoleTheme.styleTextField(textField);
        card.add(textField);

        card.add(Box.createVerticalStrut(8));
        styleCheck(overrideSpriteCheck);
        card.add(overrideSpriteCheck);
        card.add(Box.createVerticalStrut(5));
        ConsoleTheme.styleTextField(spriteField);
        card.add(spriteField);

        card.add(Box.createVerticalStrut(14));
        card.add(createActionRow());
        card.add(Box.createVerticalStrut(8));
        card.add(createResetRow());
        return card;
    }

    private JPanel createGeometryToolbar() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(LEFT_ALIGNMENT);
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

        JPanel rangeRow = transparentRow(new BorderLayout(8, 0));
        JLabel rangeLabel = new JLabel("Slider range");
        rangeLabel.setFont(ConsoleTheme.SMALL_FONT);
        rangeLabel.setForeground(ConsoleTheme.MUTED_TEXT);

        ConsoleTheme.styleComboBox(geometryRangeCombo);
        geometryRangeCombo.setPreferredSize(new Dimension(150, 32));
        geometryRangeCombo.setMaximumSize(new Dimension(180, 34));

        rangeRow.add(rangeLabel, BorderLayout.WEST);
        rangeRow.add(geometryRangeCombo, BorderLayout.EAST);
        container.add(rangeRow);
        container.add(Box.createVerticalStrut(5));

        styleCheck(liveGeometryCheck);
        container.add(liveGeometryCheck);
        return container;
    }

    private JPanel createActionRow() {
        JPanel row = transparentRow(new GridLayout(1, 2, 7, 0));

        JButton apply = new JButton("Apply Exact / Visual");
        ConsoleTheme.styleButton(apply);
        apply.setToolTipText("Apply all exact fields plus optional text/sprite overrides immediately.");
        apply.addActionListener(e -> applySelected());

        JButton copy = new JButton("Copy Values");
        ConsoleTheme.styleButton(copy);
        copy.addActionListener(e -> copyValues());

        row.add(apply);
        row.add(copy);
        return row;
    }

    private JPanel createResetRow() {
        JPanel row = transparentRow(new GridLayout(1, 2, 7, 0));

        JButton resetComponent = new JButton("Reset Selected");
        ConsoleTheme.styleButton(resetComponent);
        resetComponent.addActionListener(e -> resetSelected());

        JButton resetInterface = new JButton("Reset Interface");
        ConsoleTheme.styleButton(resetInterface);
        resetInterface.addActionListener(e -> resetInterface());

        row.add(resetComponent);
        row.add(resetInterface);
        return row;
    }

    private JPanel createQuadRow(String[] labels, javax.swing.JTextField[] fields) {
        JPanel row = transparentRow(new GridLayout(1, 4, 6, 0));
        for (int index = 0; index < fields.length; index++) {
            JPanel cell = new JPanel();
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            cell.setOpaque(false);

            JLabel label = new JLabel(labels[index]);
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setForeground(ConsoleTheme.MUTED_TEXT);
            label.setAlignmentX(LEFT_ALIGNMENT);

            ConsoleTheme.styleTextField(fields[index]);
            fields[index].setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            cell.add(label);
            cell.add(Box.createVerticalStrut(3));
            cell.add(fields[index]);
            row.add(cell);
        }
        return row;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SECTION_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private void installListeners() {
        targetField.addActionListener(e -> loadTarget());

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildComponentList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildComponentList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildComponentList();
            }
        });

        componentList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || populating) {
                return;
            }
            ComponentSnapshot selected = componentList.getSelectedValue();
            if (selected != null) {
                cancelPendingLiveApply();
                selectedComponentId = selected.getComponentId();
                populateInspector(selected);
            }
        });

        DocumentListener dirtyListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        };

        javax.swing.JTextField[] editableFields = {
                baseXControl.field, baseYControl.field, baseWidthControl.field, baseHeightControl.field,
                runtimeXControl.field, runtimeYControl.field, runtimeWidthControl.field, runtimeHeightControl.field,
                xAlignField, yAlignField, widthAlignField, heightAlignField,
                textField, spriteField
        };
        for (javax.swing.JTextField field : editableFields) {
            field.getDocument().addDocumentListener(dirtyListener);
        }

        geometryRangeCombo.addActionListener(e -> updateGeometryRanges());
        liveGeometryCheck.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                cancelPendingLiveApply();
            }
        });
        pinRuntimeCheck.addItemListener(e -> {
            markDirty();
            scheduleLiveApply();
        });
        overrideTextCheck.addItemListener(e -> {
            textField.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            markDirty();
        });
        overrideSpriteCheck.addItemListener(e -> {
            spriteField.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            markDirty();
        });

        textField.setEnabled(false);
        spriteField.setEnabled(false);
    }

    private void loadTarget() {
        Target target = parseTarget(targetField.getText());
        if (target == null) {
            setStatus("Target must be an interface ID or interface:component, for example 671:27.", false);
            return;
        }
        cancelPendingLiveApply();
        loadedInterfaceId = target.interfaceId;
        pendingSelectComponent = target.componentId;
        selectedComponentId = -1;
        lastSnapshotSequence = -1L;
        dirty = false;
        componentModel.clear();
        clearInspector();
        ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
        setStatus("Loading interface " + loadedInterfaceId + "...", true);
    }

    private void refreshFromBridge() {
        if (loadedInterfaceId >= 0) {
            ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
        }

        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        if (snapshot.getInterfaceId() != loadedInterfaceId || snapshot.getSequence() == lastSnapshotSequence) {
            return;
        }

        lastSnapshotSequence = snapshot.getSequence();
        rebuildComponentList();
        setStatus(snapshot.getStatus(), true);

        if (pendingSelectComponent >= 0) {
            selectComponent(pendingSelectComponent);
            pendingSelectComponent = -1;
            ComponentSnapshot selected = snapshot.findComponent(selectedComponentId);
            if (selected != null) {
                populateInspector(selected);
            }
        } else if (selectedComponentId >= 0) {
            ComponentSnapshot selected = snapshot.findComponent(selectedComponentId);
            if (selected != null && !dirty) {
                populateInspector(selected);
            }
        }
    }

    private void rebuildComponentList() {
        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        if (snapshot.getInterfaceId() != loadedInterfaceId) {
            return;
        }

        int preserveSelection = selectedComponentId;
        String filter = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);

        populating = true;
        try {
            componentModel.clear();
            for (ComponentSnapshot component : snapshot.getComponents()) {
                if (filter.length() == 0 || component.getSearchText().contains(filter)) {
                    componentModel.addElement(component);
                }
            }
            if (preserveSelection >= 0) {
                selectComponent(preserveSelection);
            }
        } finally {
            populating = false;
        }
    }

    private void selectComponent(int componentId) {
        for (int index = 0; index < componentModel.size(); index++) {
            ComponentSnapshot component = componentModel.getElementAt(index);
            if (component.getComponentId() == componentId) {
                selectedComponentId = componentId;
                componentList.setSelectedIndex(index);
                componentList.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    private void populateInspector(ComponentSnapshot component) {
        if (populating || component == null) {
            return;
        }

        populating = true;
        try {
            typeValue.setText(Integer.toString(component.getType()));
            parentValue.setText(component.getParentHash() == -1
                    ? "root" : component.getParentComponentId() + " [" + component.getParentHash() + "]");
            itemValue.setText(Integer.toString(component.getItemId()));
            childrenValue.setText(component.getStaticChildren() + " / " + component.getDynamicChildren());

            int range = getGeometryRange();
            baseXControl.setModelValue(component.getBaseX(), range);
            baseYControl.setModelValue(component.getBaseY(), range);
            baseWidthControl.setModelValue(component.getBaseWidth(), range);
            baseHeightControl.setModelValue(component.getBaseHeight(), range);

            runtimeXControl.setModelValue(component.getRuntimeX(), range);
            runtimeYControl.setModelValue(component.getRuntimeY(), range);
            runtimeWidthControl.setModelValue(component.getRuntimeWidth(), range);
            runtimeHeightControl.setModelValue(component.getRuntimeHeight(), range);

            setNumber(xAlignField, component.getXAlignment());
            setNumber(yAlignField, component.getYAlignment());
            setNumber(widthAlignField, component.getWidthAlignment());
            setNumber(heightAlignField, component.getHeightAlignment());

            textField.setText(component.getText());
            spriteField.setText(Integer.toString(component.getSpriteId()));
            overrideTextCheck.setSelected(false);
            overrideSpriteCheck.setSelected(false);
            textField.setEnabled(false);
            spriteField.setEnabled(false);
            pinRuntimeCheck.setSelected(true);
            dirty = false;
        } finally {
            populating = false;
        }
    }

    private void clearInspector() {
        populating = true;
        try {
            typeValue.setText("-");
            parentValue.setText("-");
            itemValue.setText("-");
            childrenValue.setText("-");
            baseXControl.clear();
            baseYControl.clear();
            baseWidthControl.clear();
            baseHeightControl.clear();
            runtimeXControl.clear();
            runtimeYControl.clear();
            runtimeWidthControl.clear();
            runtimeHeightControl.clear();
            javax.swing.JTextField[] fields = {
                    xAlignField, yAlignField, widthAlignField, heightAlignField,
                    textField, spriteField
            };
            for (javax.swing.JTextField field : fields) {
                field.setText("");
            }
            overrideTextCheck.setSelected(false);
            overrideSpriteCheck.setSelected(false);
            textField.setEnabled(false);
            spriteField.setEnabled(false);
        } finally {
            populating = false;
        }
    }

    private void applySelected() {
        ComponentSnapshot selected = currentSelectedComponent();
        if (selected == null) {
            setStatus("Select a component first.", false);
            return;
        }

        cancelPendingLiveApply();
        try {
            ComponentOverride override = buildOverride(selected);
            String error = ClientConsoleInterfaceBridge.queueApply(override);
            if (error != null) {
                setStatus(error, false);
                return;
            }

            dirty = false;
            ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
            setStatus("Queued exact override for " + loadedInterfaceId + ":" + selected.getComponentId() + ".", true);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), false);
        }
    }

    private ComponentOverride buildOverride(ComponentSnapshot selected) {
        int spriteId = overrideSpriteCheck.isSelected()
                ? parseInt(spriteField, "Sprite ID") : selected.getSpriteId();

        return new ComponentOverride(
                loadedInterfaceId,
                selected.getComponentId(),
                baseXControl.getValue("Base X"),
                baseYControl.getValue("Base Y"),
                baseWidthControl.getValue("Base width"),
                baseHeightControl.getValue("Base height"),
                runtimeXControl.getValue("Runtime X"),
                runtimeYControl.getValue("Runtime Y"),
                runtimeWidthControl.getValue("Runtime width"),
                runtimeHeightControl.getValue("Runtime height"),
                parseByte(xAlignField, "X alignment"),
                parseByte(yAlignField, "Y alignment"),
                parseByte(widthAlignField, "Width alignment"),
                parseByte(heightAlignField, "Height alignment"),
                pinRuntimeCheck.isSelected(),
                overrideTextCheck.isSelected(), textField.getText(),
                overrideSpriteCheck.isSelected(), spriteId);
    }

    private void scheduleLiveApply() {
        if (populating || !liveGeometryCheck.isSelected() || selectedComponentId < 0) {
            return;
        }
        dirty = true;
        pendingLiveApply = true;
        if (!liveApplyTimer.isRunning()) {
            liveApplyTimer.start();
        }
    }

    private void flushPendingLiveApply() {
        if (!pendingLiveApply) {
            liveApplyTimer.stop();
            return;
        }
        pendingLiveApply = false;

        ComponentSnapshot selected = currentSelectedComponent();
        if (selected == null) {
            liveApplyTimer.stop();
            return;
        }

        try {
            String error = ClientConsoleInterfaceBridge.queueApply(buildOverride(selected));
            if (error != null) {
                setStatus(error, false);
                liveApplyTimer.stop();
                return;
            }
            setStatus("Live tuning " + loadedInterfaceId + ":" + selected.getComponentId()
                    + " - drag, nudge, or enter an exact geometry value.", true);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), false);
            liveApplyTimer.stop();
        }
    }

    private void cancelPendingLiveApply() {
        if (populating) {
            return;
        }
        pendingLiveApply = false;
        liveApplyTimer.stop();
    }

    private void updateGeometryRanges() {
        if (populating) {
            return;
        }
        int range = getGeometryRange();
        populating = true;
        try {
            baseXControl.recenterRange(range);
            baseYControl.recenterRange(range);
            baseWidthControl.recenterRange(range);
            baseHeightControl.recenterRange(range);
            runtimeXControl.recenterRange(range);
            runtimeYControl.recenterRange(range);
            runtimeWidthControl.recenterRange(range);
            runtimeHeightControl.recenterRange(range);
        } finally {
            populating = false;
        }
    }

    private int getGeometryRange() {
        Object selected = geometryRangeCombo.getSelectedItem();
        return selected instanceof RangeOption ? ((RangeOption) selected).range : RANGE_NORMAL.range;
    }

    private void resetSelected() {
        ComponentSnapshot selected = currentSelectedComponent();
        if (selected == null) {
            setStatus("Select a component first.", false);
            return;
        }
        cancelPendingLiveApply();
        String error = ClientConsoleInterfaceBridge.queueResetComponent(
                loadedInterfaceId, selected.getComponentId());
        if (error != null) {
            setStatus(error, false);
            return;
        }
        dirty = false;
        ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
        setStatus("Reset queued for " + loadedInterfaceId + ":" + selected.getComponentId() + ".", true);
    }

    private void resetInterface() {
        if (loadedInterfaceId < 0) {
            setStatus("Load an interface first.", false);
            return;
        }
        cancelPendingLiveApply();
        String error = ClientConsoleInterfaceBridge.queueResetInterface(loadedInterfaceId);
        if (error != null) {
            setStatus(error, false);
            return;
        }
        dirty = false;
        ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
        setStatus("Reset queued for interface " + loadedInterfaceId + ".", true);
    }

    private void copyValues() {
        ComponentSnapshot selected = currentSelectedComponent();
        if (selected == null) {
            setStatus("Select a component first.", false);
            return;
        }

        StringBuilder copy = new StringBuilder();
        copy.append("Interface ").append(loadedInterfaceId)
                .append(':').append(selected.getComponentId()).append('\n');
        copy.append("type=").append(typeValue.getText())
                .append(" parent=").append(parentValue.getText()).append('\n');
        copy.append("base=").append(baseXControl.getText()).append(',')
                .append(baseYControl.getText()).append(',')
                .append(baseWidthControl.getText()).append(',')
                .append(baseHeightControl.getText()).append('\n');
        copy.append("runtime=").append(runtimeXControl.getText()).append(',')
                .append(runtimeYControl.getText()).append(',')
                .append(runtimeWidthControl.getText()).append(',')
                .append(runtimeHeightControl.getText()).append('\n');
        copy.append("align=").append(xAlignField.getText()).append(',')
                .append(yAlignField.getText()).append(',')
                .append(widthAlignField.getText()).append(',')
                .append(heightAlignField.getText()).append('\n');
        copy.append("text=").append(textField.getText()).append('\n');
        copy.append("sprite=").append(spriteField.getText());

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(copy.toString()), null);
        setStatus("Copied component values to clipboard.", true);
    }

    private ComponentSnapshot currentSelectedComponent() {
        ComponentSnapshot selected = componentList.getSelectedValue();
        if (selected != null) {
            return selected;
        }
        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        return snapshot.getInterfaceId() == loadedInterfaceId && selectedComponentId >= 0
                ? snapshot.findComponent(selectedComponentId) : null;
    }

    private Target parseTarget(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() == 0) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length > 2) {
            return null;
        }
        try {
            int interfaceId = Integer.parseInt(parts[0].trim());
            int componentId = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : -1;
            if (interfaceId < 0 || interfaceId > 65535
                    || (parts.length == 2 && (componentId < 0 || componentId > 65535))) {
                return null;
            }
            return new Target(interfaceId, componentId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parseInt(javax.swing.JTextField field, String label) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.length() == 0) {
            throw new IllegalArgumentException(label + " is empty.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private int parseByte(javax.swing.JTextField field, String label) {
        int value = parseInt(field, label);
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be between -128 and 127.");
        }
        return value;
    }

    private void markDirty() {
        if (!populating) {
            dirty = true;
        }
    }

    private void setStatus(String text, boolean accent) {
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setForeground(accent ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
    }

    private static javax.swing.JTextField numberField() {
        javax.swing.JTextField field = new javax.swing.JTextField();
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        return field;
    }

    private static JCheckBox checkBox(String text) {
        return new JCheckBox(text);
    }

    private static void styleCheck(JCheckBox checkBox) {
        checkBox.setFont(ConsoleTheme.SMALL_FONT);
        checkBox.setForeground(ConsoleTheme.TEXT);
        checkBox.setBackground(ConsoleTheme.CARD);
        checkBox.setOpaque(true);
        checkBox.setFocusPainted(false);
        checkBox.setAlignmentX(LEFT_ALIGNMENT);
    }

    private static JPanel transparentRow(java.awt.LayoutManager layout) {
        JPanel row = new JPanel(layout);
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return row;
    }

    private static void setNumber(javax.swing.JTextField field, int value) {
        field.setText(Integer.toString(value));
    }

    private final class LiveGeometryControl {
        private final String label;
        private final boolean nonNegative;
        private final JPanel panel = new JPanel();
        private final javax.swing.JTextField field = numberField();
        private final JSlider slider = new JSlider();

        private LiveGeometryControl(String label, boolean nonNegative) {
            this.label = label;
            this.nonNegative = nonNegative;

            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);
            panel.setAlignmentX(LEFT_ALIGNMENT);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

            JPanel top = new JPanel(new BorderLayout(7, 0));
            top.setOpaque(false);
            top.setAlignmentX(LEFT_ALIGNMENT);
            top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            JLabel name = new JLabel(label);
            name.setFont(ConsoleTheme.SMALL_FONT);
            name.setForeground(ConsoleTheme.MUTED_TEXT);
            name.setPreferredSize(new Dimension(48, 28));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            controls.setOpaque(false);

            JButton minus = compactButton("-");
            JButton plus = compactButton("+");

            ConsoleTheme.styleTextField(field);
            field.setPreferredSize(new Dimension(76, 32));
            field.setMaximumSize(new Dimension(76, 32));
            field.setToolTipText("Type an exact value and press Enter, or use the slider / +/- buttons.");

            minus.addActionListener(e -> nudge(-1));
            plus.addActionListener(e -> nudge(1));
            field.addActionListener(e -> commitExactField());
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitExactField();
                }
            });

            controls.add(minus);
            controls.add(field);
            controls.add(plus);

            top.add(name, BorderLayout.WEST);
            top.add(controls, BorderLayout.EAST);
            panel.add(top);
            panel.add(Box.createVerticalStrut(3));

            slider.setBackground(ConsoleTheme.CARD);
            slider.setForeground(ConsoleTheme.ACCENT);
            slider.setOpaque(true);
            slider.setPaintTicks(false);
            slider.setPaintLabels(false);
            slider.setFocusable(false);
            slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            slider.addChangeListener(e -> sliderChanged());
            panel.add(slider);
        }

        private JPanel getPanel() {
            return panel;
        }

        private void setModelValue(int value, int range) {
            int safeValue = nonNegative ? Math.max(0, value) : value;
            configureRange(safeValue, range);
            slider.setValue(safeValue);
            field.setText(Integer.toString(safeValue));
        }

        private void recenterRange(int range) {
            Integer value = tryParseField();
            int current = value == null ? slider.getValue() : value.intValue();
            if (nonNegative) {
                current = Math.max(0, current);
            }
            configureRange(current, range);
            slider.setValue(current);
            field.setText(Integer.toString(current));
        }

        private void configureRange(int center, int range) {
            long low = (long) center - (long) range;
            long high = (long) center + (long) range;
            if (nonNegative && low < 0L) {
                low = 0L;
            }
            low = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, low));
            high = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, high));
            if (high <= low) {
                high = Math.min((long) Integer.MAX_VALUE, low + 1L);
            }
            slider.setMinimum((int) low);
            slider.setMaximum((int) high);
        }

        private void sliderChanged() {
            if (populating) {
                return;
            }
            int value = slider.getValue();
            setFieldSilently(value);
            markDirty();
            scheduleLiveApply();
        }

        private void nudge(int delta) {
            Integer parsed = tryParseField();
            int current = parsed == null ? slider.getValue() : parsed.intValue();
            long candidate = (long) current + delta;
            if (nonNegative) {
                candidate = Math.max(0L, candidate);
            }
            candidate = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, candidate));
            setUserValue((int) candidate, true);
        }

        private void commitExactField() {
            if (populating || field.getText() == null || field.getText().trim().length() == 0) {
                return;
            }
            try {
                int value = Integer.parseInt(field.getText().trim());
                if (nonNegative && value < 0) {
                    setStatus(label + " cannot be negative.", false);
                    return;
                }
                setUserValue(value, true);
            } catch (NumberFormatException ex) {
                setStatus(label + " must be a whole number.", false);
            }
        }

        private void setUserValue(int value, boolean live) {
            int range = getGeometryRange();
            populating = true;
            try {
                if (value < slider.getMinimum() || value > slider.getMaximum()) {
                    configureRange(value, range);
                }
                slider.setValue(value);
                field.setText(Integer.toString(value));
            } finally {
                populating = false;
            }
            markDirty();
            if (live) {
                scheduleLiveApply();
            }
        }

        private void setFieldSilently(int value) {
            populating = true;
            try {
                field.setText(Integer.toString(value));
            } finally {
                populating = false;
            }
        }

        private Integer tryParseField() {
            try {
                String raw = field.getText();
                return raw == null || raw.trim().length() == 0
                        ? null : Integer.valueOf(Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private int getValue(String fieldLabel) {
            String raw = field.getText() == null ? "" : field.getText().trim();
            if (raw.length() == 0) {
                throw new IllegalArgumentException(fieldLabel + " is empty.");
            }
            try {
                int value = Integer.parseInt(raw);
                if (nonNegative && value < 0) {
                    throw new IllegalArgumentException(fieldLabel + " cannot be negative.");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(fieldLabel + " must be a whole number.");
            }
        }

        private String getText() {
            return field.getText() == null ? "" : field.getText().trim();
        }

        private void clear() {
            field.setText("");
            slider.setMinimum(0);
            slider.setMaximum(1);
            slider.setValue(0);
        }

        private JButton compactButton(String text) {
            JButton button = new JButton(text);
            ConsoleTheme.styleButton(button);
            button.setPreferredSize(new Dimension(34, 32));
            button.setMinimumSize(new Dimension(34, 32));
            button.setMaximumSize(new Dimension(34, 32));
            button.setToolTipText("Adjust " + label + " by 1 pixel.");
            return button;
        }
    }

    private static final class RangeOption {
        private final String label;
        private final int range;

        private RangeOption(String label, int range) {
            this.label = label;
            this.range = range;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class Target {
        private final int interfaceId;
        private final int componentId;

        private Target(int interfaceId, int componentId) {
            this.interfaceId = interfaceId;
            this.componentId = componentId;
        }
    }

    private static final class ComponentRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 8430739393778241331L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            if (value instanceof ComponentSnapshot) {
                ComponentSnapshot component = (ComponentSnapshot) value;
                String name = component.getLabel();
                if (name.length() > 28) {
                    name = name.substring(0, 28) + "...";
                }
                String marker = component.isOverridden() ? "* " : "";
                label.setText(marker + "#" + component.getComponentId()
                        + "  t" + component.getType()
                        + "  " + component.getRuntimeWidth() + "x" + component.getRuntimeHeight()
                        + (name.length() == 0 ? "" : "  " + name));
            }

            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            if (isSelected) {
                label.setBackground(ConsoleTheme.ACCENT_DARK);
                label.setForeground(ConsoleTheme.TEXT);
            } else {
                label.setBackground(index % 2 == 0 ? ConsoleTheme.INPUT : ConsoleTheme.CARD);
                label.setForeground(ConsoleTheme.TEXT);
            }
            return label;
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = -3430227408823953895L;

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
