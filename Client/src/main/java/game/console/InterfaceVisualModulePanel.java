package game.console;

import game.ClientConsoleInterfaceBridge;
import game.ClientConsoleInterfaceBridge.ComponentSnapshot;
import game.ClientConsoleInterfaceBridge.InterfaceCatalogSnapshot;
import game.ClientConsoleInterfaceBridge.InterfaceSnapshot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.EnumSet;
import java.util.Locale;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
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
 * Visual Explorer interface module. Inspection is local to the explorer;
 * editing hands off to the existing Interface Editor authority.
 */
public final class InterfaceVisualModulePanel extends JScrollPane implements VisualExplorerModule {

    private static final long serialVersionUID = -6367132584649179449L;

    private final Navigator navigator;
    private final BiConsumer<Integer, Integer> editorOpener;

    private final javax.swing.JTextField targetField = new javax.swing.JTextField();
    private final javax.swing.JTextField searchField = new javax.swing.JTextField();
    private final DefaultListModel<ComponentSnapshot> model = new DefaultListModel<ComponentSnapshot>();
    private final JList<ComponentSnapshot> componentList = new JList<ComponentSnapshot>(model);

    private final JLabel interfaceValue = ConsoleTheme.createValueLabel();
    private final JLabel componentValue = ConsoleTheme.createValueLabel();
    private final JLabel typeValue = ConsoleTheme.createValueLabel();
    private final JLabel parentValue = ConsoleTheme.createValueLabel();
    private final JLabel geometryValue = ConsoleTheme.createValueLabel();
    private final JLabel textValue = ConsoleTheme.createValueLabel();
    private final JLabel spriteValue = ConsoleTheme.createValueLabel();
    private final JLabel status = new JLabel("Choose an interface.");
    private final JButton openSprite = new JButton("Open Sprite");

    private final Timer refreshTimer = new Timer(350, e -> refreshFromBridge());

    private int loadedInterfaceId = -1;
    private int selectedComponentId = -1;
    private long lastSequence = -1L;

    public InterfaceVisualModulePanel(Navigator navigator, BiConsumer<Integer, Integer> editorOpener) {
        this.navigator = navigator;
        this.editorOpener = editorOpener;

        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(14, 14, 14, 14));

        content.add(createBrowserCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createComponentCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createInspectorCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        componentList.setCellRenderer(new ComponentRenderer());
        componentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ComponentSnapshot selected = componentList.getSelectedValue();
                selectedComponentId = selected == null ? -1 : selected.getComponentId();
                populateInspector(selected);
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { rebuildList(); }
            @Override
            public void removeUpdate(DocumentEvent e) { rebuildList(); }
            @Override
            public void changedUpdate(DocumentEvent e) { rebuildList(); }
        });

        refreshTimer.setCoalesce(true);
        refreshTimer.start();
    }

    private JPanel createBrowserCard() {
        JPanel card = ConsoleTheme.createCard("Interface");
        card.add(Box.createVerticalStrut(8));

        JPanel row = new JPanel(new BorderLayout(7, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        ConsoleTheme.styleTextField(targetField);
        targetField.setToolTipText("Interface ID, for example 762.");
        targetField.addActionListener(e -> loadTypedTarget());

        JButton load = new JButton("Load");
        ConsoleTheme.styleButton(load);
        load.addActionListener(e -> loadTypedTarget());

        row.add(targetField, BorderLayout.CENTER);
        row.add(load, BorderLayout.EAST);
        card.add(row);
        card.add(Box.createVerticalStrut(7));

        JButton active = new JButton("Load Active");
        JButton browse = new JButton("Browse All");
        ConsoleTheme.styleButton(active);
        ConsoleTheme.styleButton(browse);

        active.addActionListener(e -> {
            InterfaceCatalogSnapshot catalog = ClientConsoleInterfaceBridge.getLatestCatalog();
            int id = catalog.getActiveInterfaceId();
            if (id < 0) {
                setStatus("No active interface is currently reported.", false);
                return;
            }
            loadInterface(id);
        });

        browse.addActionListener(e -> InterfaceBrowserDialog.open(
                InterfaceVisualModulePanel.this,
                ClientConsoleInterfaceBridge.getLatestCatalog(),
                this::loadInterface));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        buttons.add(active);
        buttons.add(browse);
        card.add(buttons);

        card.add(Box.createVerticalStrut(7));
        ConsoleTheme.styleStatus(status, false);
        card.add(status);
        return card;
    }

    private JPanel createComponentCard() {
        JPanel card = ConsoleTheme.createCard("Components");
        card.add(Box.createVerticalStrut(8));

        ConsoleTheme.styleTextField(searchField);
        searchField.setToolTipText("Search component ID, type, label, text, or sprite ID.");
        card.add(searchField);
        card.add(Box.createVerticalStrut(7));

        componentList.setBackground(ConsoleTheme.INPUT);
        componentList.setForeground(ConsoleTheme.TEXT);
        componentList.setSelectionBackground(ConsoleTheme.ACCENT_DARK);
        componentList.setSelectionForeground(ConsoleTheme.TEXT);
        componentList.setFixedCellHeight(28);

        JScrollPane scroll = new JScrollPane(componentList);
        ConsoleTheme.styleScrollPane(scroll);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(100, 220));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        card.add(scroll);
        return card;
    }

    private JPanel createInspectorCard() {
        JPanel card = ConsoleTheme.createCard("Selected Component");
        card.add(Box.createVerticalStrut(8));
        addValue(card, "Interface", interfaceValue);
        addValue(card, "Component", componentValue);
        addValue(card, "Type", typeValue);
        addValue(card, "Parent", parentValue);
        addValue(card, "Runtime", geometryValue);
        addValue(card, "Text", textValue);
        addValue(card, "Sprite", spriteValue);
        card.add(Box.createVerticalStrut(8));

        JButton edit = new JButton("Open in Interface Editor");
        ConsoleTheme.styleButton(edit);
        ConsoleTheme.styleButton(openSprite);
        openSprite.setEnabled(false);

        edit.addActionListener(e -> {
            if (loadedInterfaceId < 0 || editorOpener == null) {
                return;
            }
            editorOpener.accept(Integer.valueOf(loadedInterfaceId), Integer.valueOf(selectedComponentId));
        });

        openSprite.addActionListener(e -> {
            ComponentSnapshot selected = selectedSnapshot();
            if (selected == null || selected.getSpriteId() < 0 || navigator == null) {
                return;
            }
            navigator.openAsset(VisualExplorerAssetRef.sprite(
                    selected.getSpriteId(),
                    "interface " + selected.getInterfaceId() + ":" + selected.getComponentId()));
        });

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        buttons.add(edit);
        buttons.add(openSprite);
        card.add(buttons);
        return card;
    }

    private void addValue(JPanel card, String name, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel label = new JLabel(name);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setPreferredSize(new Dimension(72, 26));

        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        card.add(row);
    }

    private void loadTypedTarget() {
        String raw = targetField.getText() == null ? "" : targetField.getText().trim();
        try {
            int id = Integer.parseInt(raw);
            if (id < 0 || id > 65535) {
                throw new NumberFormatException();
            }
            loadInterface(id);
        } catch (NumberFormatException ex) {
            setStatus("Interface ID must be 0-65535.", false);
        }
    }

    private void loadInterface(int interfaceId) {
        loadedInterfaceId = interfaceId;
        selectedComponentId = -1;
        lastSequence = -1L;
        targetField.setText(Integer.toString(interfaceId));
        model.clear();
        clearInspector();
        ClientConsoleInterfaceBridge.requestSnapshot(interfaceId);
        setStatus("Loading interface " + interfaceId + "...", true);
    }

    private void refreshFromBridge() {
        if (!isShowing() || loadedInterfaceId < 0) {
            return;
        }

        ClientConsoleInterfaceBridge.requestSnapshot(loadedInterfaceId);
        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        if (snapshot.getInterfaceId() != loadedInterfaceId || snapshot.getSequence() == lastSequence) {
            return;
        }

        lastSequence = snapshot.getSequence();
        rebuildList();
        setStatus(snapshot.getStatus(), true);

        if (selectedComponentId >= 0) {
            selectComponent(selectedComponentId);
            populateInspector(snapshot.findComponent(selectedComponentId));
        }
    }

    private void rebuildList() {
        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        if (snapshot.getInterfaceId() != loadedInterfaceId) {
            return;
        }

        String filter = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);

        model.clear();
        for (ComponentSnapshot component : snapshot.getComponents()) {
            String haystack = component.getSearchText()
                    + " " + component.getText().toLowerCase(Locale.ENGLISH)
                    + " sprite " + component.getSpriteId();
            if (filter.length() == 0 || haystack.contains(filter)) {
                model.addElement(component);
            }
        }
        selectComponent(selectedComponentId);
    }

    private void selectComponent(int componentId) {
        if (componentId < 0) {
            return;
        }
        for (int index = 0; index < model.size(); index++) {
            if (model.get(index).getComponentId() == componentId) {
                componentList.setSelectedIndex(index);
                componentList.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    private ComponentSnapshot selectedSnapshot() {
        ComponentSnapshot selected = componentList.getSelectedValue();
        if (selected != null) {
            return selected;
        }
        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        return snapshot.getInterfaceId() == loadedInterfaceId && selectedComponentId >= 0
                ? snapshot.findComponent(selectedComponentId) : null;
    }

    private void populateInspector(ComponentSnapshot component) {
        if (component == null) {
            clearInspector();
            return;
        }
        interfaceValue.setText(Integer.toString(component.getInterfaceId()));
        componentValue.setText(Integer.toString(component.getComponentId()));
        typeValue.setText(Integer.toString(component.getType()));
        parentValue.setText(component.getParentComponentId() < 0
                ? "root" : Integer.toString(component.getParentComponentId()));
        geometryValue.setText(component.getRuntimeX() + "," + component.getRuntimeY()
                + "  " + component.getRuntimeWidth() + "x" + component.getRuntimeHeight());
        String text = component.getText();
        textValue.setText(text.length() == 0 ? "—" : compact(text, 60));
        spriteValue.setText(component.getSpriteId() < 0
                ? "—" : Integer.toString(component.getSpriteId()));
        openSprite.setEnabled(component.getSpriteId() >= 0);
    }

    private void clearInspector() {
        interfaceValue.setText(loadedInterfaceId < 0 ? "—" : Integer.toString(loadedInterfaceId));
        componentValue.setText("—");
        typeValue.setText("—");
        parentValue.setText("—");
        geometryValue.setText("—");
        textValue.setText("—");
        spriteValue.setText("—");
        openSprite.setEnabled(false);
    }

    private String compact(String value, int max) {
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 3) + "...";
    }

    private void setStatus(String value, boolean accent) {
        status.setText(value == null ? "" : value);
        status.setForeground(accent ? ConsoleTheme.ACCENT : ConsoleTheme.MUTED_TEXT);
    }

    @Override
    public String getModuleId() {
        return "interfaces";
    }

    @Override
    public String getTitle() {
        return "Interfaces";
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public EnumSet<Capability> getCapabilities() {
        return EnumSet.of(Capability.BROWSE, Capability.INSPECT,
                Capability.REFERENCES, Capability.EDIT);
    }

    @Override
    public boolean supports(VisualExplorerAssetRef asset) {
        return asset != null && (asset.getKind() == VisualExplorerAssetRef.Kind.INTERFACE
                || asset.getKind() == VisualExplorerAssetRef.Kind.INTERFACE_COMPONENT);
    }

    @Override
    public void openAsset(VisualExplorerAssetRef asset) {
        if (!supports(asset)) {
            return;
        }
        loadInterface(asset.getPrimaryId());
        if (asset.getKind() == VisualExplorerAssetRef.Kind.INTERFACE_COMPONENT) {
            selectedComponentId = asset.getSecondaryId();
        }
    }

    private static final class ComponentRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 8741688623541777289L;

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focus);
            if (value instanceof ComponentSnapshot) {
                ComponentSnapshot component = (ComponentSnapshot) value;
                String text = "#" + component.getComponentId()
                        + "  t" + component.getType()
                        + "  " + component.getRuntimeWidth() + "x" + component.getRuntimeHeight();
                if (component.getSpriteId() >= 0) {
                    text += "  sprite " + component.getSpriteId();
                }
                String name = component.getLabel();
                if (name.length() > 0) {
                    text += "  " + (name.length() > 26 ? name.substring(0, 26) + "..." : name);
                }
                label.setText(text);
            }
            label.setFont(ConsoleTheme.SMALL_FONT);
            label.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
            return label;
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 3923252299896382703L;

        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(16, extent - 16);
        }
        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }
        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
