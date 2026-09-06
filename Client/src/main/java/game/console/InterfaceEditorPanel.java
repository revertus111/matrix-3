package game.console;

import game.ClientConsoleInterfaceBridge;
import game.ClientConsoleInterfaceBridge.ComponentOverride;
import game.ClientConsoleInterfaceBridge.ComponentSnapshot;
import game.ClientConsoleInterfaceBridge.InterfaceSnapshot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
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
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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

    private final javax.swing.JTextField targetField = new javax.swing.JTextField("671:27");
    private final javax.swing.JTextField searchField = new javax.swing.JTextField();
    private final DefaultListModel<ComponentSnapshot> componentModel = new DefaultListModel<ComponentSnapshot>();
    private final JList<ComponentSnapshot> componentList = new JList<ComponentSnapshot>(componentModel);

    private final JLabel typeValue = ConsoleTheme.createValueLabel();
    private final JLabel parentValue = ConsoleTheme.createValueLabel();
    private final JLabel itemValue = ConsoleTheme.createValueLabel();
    private final JLabel childrenValue = ConsoleTheme.createValueLabel();

    private final javax.swing.JTextField baseXField = numberField();
    private final javax.swing.JTextField baseYField = numberField();
    private final javax.swing.JTextField baseWidthField = numberField();
    private final javax.swing.JTextField baseHeightField = numberField();

    private final javax.swing.JTextField runtimeXField = numberField();
    private final javax.swing.JTextField runtimeYField = numberField();
    private final javax.swing.JTextField runtimeWidthField = numberField();
    private final javax.swing.JTextField runtimeHeightField = numberField();

    private final javax.swing.JTextField xAlignField = numberField();
    private final javax.swing.JTextField yAlignField = numberField();
    private final javax.swing.JTextField widthAlignField = numberField();
    private final javax.swing.JTextField heightAlignField = numberField();

    private final JCheckBox pinRuntimeCheck = checkBox("Pin runtime X/Y/W/H every client cycle");
    private final JCheckBox overrideTextCheck = checkBox("Override text");
    private final javax.swing.JTextField textField = new javax.swing.JTextField();
    private final JCheckBox overrideSpriteCheck = checkBox("Override sprite");
    private final javax.swing.JTextField spriteField = numberField();

    private final JLabel statusLabel = new JLabel("Enter an interface ID or interface:component target.");

    private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refreshFromBridge());

    private int loadedInterfaceId = -1;
    private int pendingSelectComponent = -1;
    private int selectedComponentId = -1;
    private long lastSnapshotSequence = -1L;
    private boolean populating;
    private boolean dirty;

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
                "Load an interface, select a component, edit values, and apply them live. "
                + "All overrides are reversible and remain client-side.", 3));
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
        refreshTimer.setCoalesce(true);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (isShowing()) {
                loadTarget();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
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

        card.add(Box.createVerticalStrut(12));
        card.add(sectionLabel("Base geometry"));
        card.add(Box.createVerticalStrut(6));
        card.add(createQuadRow(
                new String[] { "X", "Y", "W", "H" },
                new javax.swing.JTextField[] { baseXField, baseYField, baseWidthField, baseHeightField }));

        card.add(Box.createVerticalStrut(12));
        card.add(sectionLabel("Runtime geometry"));
        card.add(Box.createVerticalStrut(6));
        card.add(createQuadRow(
                new String[] { "X", "Y", "W", "H" },
                new javax.swing.JTextField[] { runtimeXField, runtimeYField, runtimeWidthField, runtimeHeightField }));
        card.add(Box.createVerticalStrut(7));
        styleCheck(pinRuntimeCheck);
        card.add(pinRuntimeCheck);

        card.add(Box.createVerticalStrut(12));
        card.add(sectionLabel("Alignment"));
        card.add(Box.createVerticalStrut(6));
        card.add(createQuadRow(
                new String[] { "X", "Y", "W", "H" },
                new javax.swing.JTextField[] { xAlignField, yAlignField, widthAlignField, heightAlignField }));

        card.add(Box.createVerticalStrut(12));
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

    private JPanel createActionRow() {
        JPanel row = transparentRow(new GridLayout(1, 2, 7, 0));

        JButton apply = new JButton("Apply Live");
        ConsoleTheme.styleButton(apply);
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
            if (e.getValueIsAdjusting()) {
                return;
            }
            ComponentSnapshot selected = componentList.getSelectedValue();
            if (selected != null) {
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
                baseXField, baseYField, baseWidthField, baseHeightField,
                runtimeXField, runtimeYField, runtimeWidthField, runtimeHeightField,
                xAlignField, yAlignField, widthAlignField, heightAlignField,
                textField, spriteField
        };
        for (javax.swing.JTextField field : editableFields) {
            field.getDocument().addDocumentListener(dirtyListener);
        }

        pinRuntimeCheck.addItemListener(e -> markDirty());
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
        pinRuntimeCheck.setSelected(true);
    }

    private void loadTarget() {
        Target target = parseTarget(targetField.getText());
        if (target == null) {
            setStatus("Target must be an interface ID or interface:component, for example 671:27.", false);
            return;
        }
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
        } finally {
            populating = false;
        }

        if (preserveSelection >= 0) {
            selectComponent(preserveSelection);
        }
    }

    private void selectComponent(int componentId) {
        for (int index = 0; index < componentModel.size(); index++) {
            ComponentSnapshot component = componentModel.getElementAt(index);
            if (component.getComponentId() == componentId) {
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

            setNumber(baseXField, component.getBaseX());
            setNumber(baseYField, component.getBaseY());
            setNumber(baseWidthField, component.getBaseWidth());
            setNumber(baseHeightField, component.getBaseHeight());

            setNumber(runtimeXField, component.getRuntimeX());
            setNumber(runtimeYField, component.getRuntimeY());
            setNumber(runtimeWidthField, component.getRuntimeWidth());
            setNumber(runtimeHeightField, component.getRuntimeHeight());

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
            javax.swing.JTextField[] fields = {
                    baseXField, baseYField, baseWidthField, baseHeightField,
                    runtimeXField, runtimeYField, runtimeWidthField, runtimeHeightField,
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

        try {
            int baseX = parseInt(baseXField, "Base X");
            int baseY = parseInt(baseYField, "Base Y");
            int baseWidth = parseInt(baseWidthField, "Base width");
            int baseHeight = parseInt(baseHeightField, "Base height");

            int runtimeX = parseInt(runtimeXField, "Runtime X");
            int runtimeY = parseInt(runtimeYField, "Runtime Y");
            int runtimeWidth = parseInt(runtimeWidthField, "Runtime width");
            int runtimeHeight = parseInt(runtimeHeightField, "Runtime height");

            int xAlign = parseByte(xAlignField, "X alignment");
            int yAlign = parseByte(yAlignField, "Y alignment");
            int widthAlign = parseByte(widthAlignField, "Width alignment");
            int heightAlign = parseByte(heightAlignField, "Height alignment");

            int spriteId = parseInt(spriteField, "Sprite ID");

            ComponentOverride override = new ComponentOverride(
                    loadedInterfaceId,
                    selected.getComponentId(),
                    baseX, baseY, baseWidth, baseHeight,
                    runtimeX, runtimeY, runtimeWidth, runtimeHeight,
                    xAlign, yAlign, widthAlign, heightAlign,
                    pinRuntimeCheck.isSelected(),
                    overrideTextCheck.isSelected(), textField.getText(),
                    overrideSpriteCheck.isSelected(), spriteId);

            String error = ClientConsoleInterfaceBridge.queueApply(override);
            if (error != null) {
                setStatus(error, false);
                return;
            }

            dirty = false;
            ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
            setStatus("Queued live override for " + loadedInterfaceId + ":" + selected.getComponentId() + ".", true);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), false);
        }
    }

    private void resetSelected() {
        ComponentSnapshot selected = currentSelectedComponent();
        if (selected == null) {
            setStatus("Select a component first.", false);
            return;
        }
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
        copy.append("base=").append(baseXField.getText()).append(',')
                .append(baseYField.getText()).append(',')
                .append(baseWidthField.getText()).append(',')
                .append(baseHeightField.getText()).append('\n');
        copy.append("runtime=").append(runtimeXField.getText()).append(',')
                .append(runtimeYField.getText()).append(',')
                .append(runtimeWidthField.getText()).append(',')
                .append(runtimeHeightField.getText()).append('\n');
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
