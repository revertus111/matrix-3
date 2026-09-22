package game.console;

import game.AssetStudioCapture;
import game.AssetStudioCapture.CaptureBatch;
import game.DevDefinitionBridge;
import game.ObjectCompositePreview;
import game.RailCompositeLibrary;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ListSelectionModel;

/**
 * Generic client-only Matrix3 object definition browser/preview tool.
 *
 * It is intentionally docked under Test so object research can happen beside
 * the live game. Normal preview and same-tile overlay proof both direct-render
 * models without attaching anything to the Matrix3 scene.
 */
public final class ObjectExplorerPanel extends JScrollPane {

    private static final long serialVersionUID = 1L;
    private static final Path RESEARCH_FILE =
            Paths.get("data/tools/object_explorer.tsv");

    private final JSpinner idSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JSpinner typeSpinner =
            new JSpinner(new SpinnerNumberModel(10, 0, 22, 1));
    private final JSpinner rotationSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
    private final JSpinner offsetXSpinner =
            new JSpinner(new SpinnerNumberModel(3, -12, 12, 1));
    private final JSpinner offsetYSpinner =
            new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));

    private final JLabel selectedName = valueLabel("No object selected");
    private final JLabel status =
            ConsoleTheme.subtitleLabel("Search by name/ID or browse definitions.");

    private final JTextField tagField = new JTextField("OBJECT_RESEARCH");
    private final JTextField compositeNameField = new JTextField("RAIL_LAYOUT_01");
    private final JComboBox<RailCompositeLibrary.Role> compositeRole =
            new JComboBox<RailCompositeLibrary.Role>(RailCompositeLibrary.Role.values());
    private final DefaultListModel<String> compositeModel = new DefaultListModel<String>();
    private final JList<String> compositeList = new JList<String>(compositeModel);
    private final List<LayoutPart> compositeParts = new ArrayList<LayoutPart>();
    private final JCheckBox layoutHotkeys = new JCheckBox(
            "Layout hotkeys: [ / ] select, arrows move, R rotate, Del remove, Ctrl+D duplicate", true);

    private int layoutAnchorX = Integer.MIN_VALUE;
    private int layoutAnchorY = Integer.MIN_VALUE;
    private int layoutAnchorPlane = -1;
    private boolean layoutHotkeysInstalled;

    private String currentName = "id-0";

    private final DevSpawnSearchPanel search =
            new DevSpawnSearchPanel(DevSpawnSearchPanel.OBJECT,
                    new DevSpawnSearchPanel.SelectionListener() {
                        @Override
                        public void selected(int id, String name) {
                            selectObject(id, name, false);
                        }

                        @Override
                        public void activated(int id, String name) {
                            selectObject(id, name, true);
                        }
                    });

    public ObjectExplorerPanel() {
        buildUi();
        installLayoutHotkeys();
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
        header.add(ConsoleTheme.titleLabel("OBJECT EXPLORER"));
        header.add(Box.createVerticalStrut(3));
        header.add(ConsoleTheme.subtitleLabel(
                "Browse any Matrix3 object definition and direct-render it beside the game."));
        content.add(header);
        content.add(Box.createVerticalStrut(12));

        content.add(createSearchCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createPreviewCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createCompositeCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createResearchCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createStatusCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);
    }

    private JPanel createSearchCard() {
        JPanel card = ConsoleTheme.createCard("Find object");
        card.add(Box.createVerticalStrut(8));
        search.setAlignmentX(LEFT_ALIGNMENT);
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 235));
        card.add(search);
        card.add(Box.createVerticalStrut(8));

        selectedName.setAlignmentX(LEFT_ALIGNMENT);
        card.add(selectedName);
        card.add(Box.createVerticalStrut(6));

        JPanel idRow = new JPanel(new GridLayout(1, 3, 6, 0));
        idRow.setOpaque(false);
        idRow.setAlignmentX(LEFT_ALIGNMENT);
        idRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JButton previous = button("< Prev Definition");
        JButton next = button("Next Definition >");
        previous.addActionListener(e -> browseDefinition(-1));
        next.addActionListener(e -> browseDefinition(1));
        idSpinner.addChangeListener(e -> loadTypedId());

        idRow.add(previous);
        idRow.add(idSpinner);
        idRow.add(next);
        card.add(idRow);
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createWrappedText(
                "Prev/Next uses the full object-definition range, including unnamed cache objects "
                + "that ordinary name search cannot identify.", 3));
        return card;
    }

    private JPanel createPreviewCard() {
        JPanel card = ConsoleTheme.createCard("Object preview");
        card.add(Box.createVerticalStrut(8));

        JPanel settings = new JPanel(new GridLayout(2, 3, 6, 6));
        settings.setOpaque(false);
        settings.setAlignmentX(LEFT_ALIGNMENT);
        settings.add(smallLabel("Type"));
        settings.add(smallLabel("Rotation"));
        settings.add(smallLabel("Offset X / Y"));
        settings.add(typeSpinner);
        settings.add(rotationSpinner);

        JPanel offsets = new JPanel(new GridLayout(1, 2, 4, 0));
        offsets.setOpaque(false);
        offsets.add(offsetXSpinner);
        offsets.add(offsetYSpinner);
        settings.add(offsets);
        card.add(settings);
        card.add(Box.createVerticalStrut(7));

        JPanel rotations = new JPanel(new GridLayout(1, 4, 5, 0));
        rotations.setOpaque(false);
        rotations.setAlignmentX(LEFT_ALIGNMENT);
        rotations.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        for (int rotation = 0; rotation < 4; rotation++) {
            final int value = rotation;
            JButton button = button("R" + rotation);
            button.addActionListener(e -> {
                rotationSpinner.setValue(Integer.valueOf(value));
                spawnCurrent();
            });
            rotations.add(button);
        }
        card.add(rotations);
        card.add(Box.createVerticalStrut(7));

        JButton spawn = button("Spawn Preview");
        JButton hide = button("Hide Preview");
        spawn.addActionListener(e -> spawnCurrent());
        hide.addActionListener(e -> {
            ObjectCompositePreview.hide();
            setStatus(ObjectCompositePreview.getStatus());
        });

        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        actions.add(spawn);
        actions.add(hide);
        card.add(actions);
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createWrappedText(
                "Double-click a search result to spawn it immediately. Type 10 is a useful generic "
                + "starting point; rail floor decorations generally use type 22.", 3));
        return card;
    }

    private JPanel createCompositeCard() {
        JPanel card = ConsoleTheme.createCard("Rail Layout Lab");
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Assemble stock rail pieces in a live client-only work area. Each piece keeps its own "
                + "ID/type/rotation and relative X/Y tile position. Arrange the geometry visually first, "
                + "then save the exact layout evidence for reuse or upload.",
                6));
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createWrappedText(
                "Known curve-family evidence: 46360/R2, 46361/R0, 46353/R2, 46377/R0, "
                + "46379/R0, 46382/R0, 46381/R0. Do not assume all belong in the final curve.",
                4));
        card.add(Box.createVerticalStrut(7));

        compositeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        compositeList.setBackground(ConsoleTheme.PANEL);
        compositeList.setForeground(ConsoleTheme.TEXT);
        compositeList.setSelectionBackground(ConsoleTheme.CARD_HOVER);
        JScrollPane listScroll = new JScrollPane(compositeList);
        listScroll.setAlignmentX(LEFT_ALIGNMENT);
        listScroll.setPreferredSize(new Dimension(240, 170));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        ConsoleTheme.styleScrollPane(listScroll);
        card.add(listScroll);
        card.add(Box.createVerticalStrut(7));

        JButton add = button("Add Current Piece");
        JButton duplicate = button("Duplicate Selected");
        JButton remove = button("Delete Selected");
        JButton preview = button("Preview Layout");
        add.addActionListener(e -> addCompositePart());
        duplicate.addActionListener(e -> duplicateSelectedLayoutPart());
        remove.addActionListener(e -> removeCompositePart());
        preview.addActionListener(e -> previewComposite());

        JPanel primary = new JPanel(new GridLayout(2, 2, 6, 6));
        primary.setOpaque(false);
        primary.setAlignmentX(LEFT_ALIGNMENT);
        primary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        primary.add(add);
        primary.add(duplicate);
        primary.add(remove);
        primary.add(preview);
        card.add(primary);
        card.add(Box.createVerticalStrut(7));

        JButton previous = button("[ Previous Piece");
        JButton up = button("Up +Y");
        JButton next = button("Next Piece ]");
        JButton left = button("Left -X");
        JButton rotate = button("Rotate R");
        JButton right = button("Right +X");
        JButton resetAnchor = button("Reset Anchor");
        JButton down = button("Down -Y");
        JButton clear = button("Clear Layout");

        previous.addActionListener(e -> selectLayoutPart(-1));
        next.addActionListener(e -> selectLayoutPart(1));
        up.addActionListener(e -> moveSelectedLayoutPart(0, 1));
        down.addActionListener(e -> moveSelectedLayoutPart(0, -1));
        left.addActionListener(e -> moveSelectedLayoutPart(-1, 0));
        right.addActionListener(e -> moveSelectedLayoutPart(1, 0));
        rotate.addActionListener(e -> rotateSelectedLayoutPart());
        resetAnchor.addActionListener(e -> resetLayoutAnchor());
        clear.addActionListener(e -> clearComposite());

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

        layoutHotkeys.setOpaque(false);
        layoutHotkeys.setForeground(ConsoleTheme.TEXT);
        layoutHotkeys.setFocusable(false);
        layoutHotkeys.setAlignmentX(LEFT_ALIGNMENT);
        card.add(layoutHotkeys);
        card.add(Box.createVerticalStrut(8));

        ConsoleTheme.styleTextField(compositeNameField);
        compositeNameField.setAlignmentX(LEFT_ALIGNMENT);
        compositeNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(smallLabel("Layout / composite name"));
        card.add(Box.createVerticalStrut(3));
        card.add(compositeNameField);
        card.add(Box.createVerticalStrut(6));

        compositeRole.setFocusable(false);
        compositeRole.setAlignmentX(LEFT_ALIGNMENT);
        compositeRole.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        compositeRole.setSelectedItem(RailCompositeLibrary.Role.CUSTOM);
        card.add(smallLabel("Role"));
        card.add(Box.createVerticalStrut(3));
        card.add(compositeRole);
        card.add(Box.createVerticalStrut(6));

        JButton save = button("Save Layout Evidence");
        save.setAlignmentX(LEFT_ALIGNMENT);
        save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        save.addActionListener(e -> saveComposite());
        card.add(save);
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createWrappedText(
                "Saved TSV records every piece as ID/type/rotation + relative dX/dY. "
                + "Use CUSTOM while experimenting; switch to CURVE only after the layout is visually accepted. "
                + "File: Client/data/construction/asset_studio/rail_composites.tsv.",
                4));
        return card;
    }

    private JPanel createResearchCard() {
        JPanel card = ConsoleTheme.createCard("Research list");
        card.add(Box.createVerticalStrut(8));
        ConsoleTheme.styleTextField(tagField);
        tagField.setAlignmentX(LEFT_ALIGNMENT);
        tagField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(tagField);
        card.add(Box.createVerticalStrut(6));

        JButton save = button("Add Current to Research TSV");
        save.setAlignmentX(LEFT_ALIGNMENT);
        save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        save.addActionListener(e -> saveResearch());
        card.add(save);
        card.add(Box.createVerticalStrut(5));
        card.add(ConsoleTheme.createWrappedText(
                "Saves ID/name/type/rotation/tag to Client/data/tools/object_explorer.tsv.", 2));
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Object Explorer status");
        card.add(Box.createVerticalStrut(8));
        status.setAlignmentX(LEFT_ALIGNMENT);
        card.add(status);
        return card;
    }

    private void selectObject(int id, String name, boolean spawn) {
        idSpinner.setValue(Integer.valueOf(Math.max(0, id)));
        currentName = name == null || name.trim().isEmpty() ? "id-" + id : name;
        selectedName.setText(currentName + "  [ID " + id + "]");
        if (spawn) {
            spawnCurrent();
        }
    }

    private void loadTypedId() {
        int id = number(idSpinner);
        DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfoAny(id);
        if (info == null) {
            currentName = "id-" + id;
            selectedName.setText(currentName + "  [definition unavailable]");
            return;
        }
        currentName = info.getName();
        selectedName.setText(currentName + "  [ID " + id + "]");
    }

    private void browseDefinition(int direction) {
        if (!DevDefinitionBridge.isObjectDefinitionsReady()) {
            setStatus("Waiting for Matrix3 object definitions...");
            return;
        }
        int count = DevDefinitionBridge.getObjectCount();
        if (count <= 0) {
            setStatus("Object definition count unavailable.");
            return;
        }

        int current = number(idSpinner);
        int candidate = current + (direction < 0 ? -1 : 1);
        while (candidate >= 0 && candidate < count) {
            DevDefinitionBridge.DefinitionInfo info =
                    DevDefinitionBridge.getObjectInfoAny(candidate);
            if (info != null) {
                selectObject(candidate, info.getName(), true);
                return;
            }
            candidate += direction < 0 ? -1 : 1;
        }
        setStatus(direction < 0 ? "Reached first object definition." : "Reached last object definition.");
    }

    private void spawnCurrent() {
        CaptureBatch anchor = AssetStudioCapture.capturePlayerArea(0);
        if (anchor == null || !anchor.isSuccess()) {
            setStatus("Preview failed: "
                    + (anchor == null ? "live player/scene unavailable" : anchor.getError()));
            return;
        }

        int id = number(idSpinner);
        ObjectCompositePreview.showSingle(currentName, id, number(typeSpinner),
                number(rotationSpinner), anchor.getCenterX(), anchor.getCenterY(),
                anchor.getPlane(), number(offsetXSpinner), number(offsetYSpinner));
        setStatus(ObjectCompositePreview.getStatus());
    }

    private void addCompositePart() {
        if (compositeParts.size() >= 32) {
            setStatus("Rail Layout Lab is capped at 32 visual pieces.");
            return;
        }
        Snapshot snapshot = currentSnapshot();
        LayoutPart part = new LayoutPart(
                snapshot.name, snapshot.id, snapshot.type, snapshot.rotation, 0, 0);
        compositeParts.add(part);
        refreshCompositeList();
        compositeList.setSelectedIndex(compositeParts.size() - 1);
        if (ensureLayoutAnchor()) {
            refreshLayoutPreview();
        }
        setStatus("Added layout piece " + compositeParts.size() + ": " + part.describe());
    }

    private void duplicateSelectedLayoutPart() {
        int index = selectedLayoutIndex();
        if (index < 0) {
            setStatus("Select a layout piece to duplicate.");
            return;
        }
        if (compositeParts.size() >= 32) {
            setStatus("Rail Layout Lab is capped at 32 visual pieces.");
            return;
        }
        LayoutPart source = compositeParts.get(index);
        LayoutPart copy = new LayoutPart(
                source.name, source.id, source.type, source.rotation,
                source.offsetX + 1, source.offsetY);
        compositeParts.add(copy);
        refreshCompositeList();
        compositeList.setSelectedIndex(compositeParts.size() - 1);
        refreshLayoutPreview();
        setStatus("Duplicated selected piece to dX " + copy.offsetX + ", dY " + copy.offsetY + ".");
    }

    private void removeCompositePart() {
        int index = selectedLayoutIndex();
        if (index < 0) {
            setStatus("Select a layout piece to remove.");
            return;
        }
        LayoutPart removed = compositeParts.remove(index);
        refreshCompositeList();
        if (!compositeParts.isEmpty()) {
            compositeList.setSelectedIndex(Math.min(index, compositeParts.size() - 1));
            refreshLayoutPreview();
        } else {
            ObjectCompositePreview.hide();
        }
        setStatus("Removed layout piece: " + removed.describe());
    }

    private void clearComposite() {
        compositeParts.clear();
        refreshCompositeList();
        ObjectCompositePreview.hide();
        setStatus("Rail layout cleared.");
    }

    private void refreshCompositeList() {
        compositeModel.clear();
        for (int i = 0; i < compositeParts.size(); i++) {
            compositeModel.addElement("#" + (i + 1) + "  " + compositeParts.get(i).describe());
        }
    }

    private int selectedLayoutIndex() {
        int index = compositeList.getSelectedIndex();
        return index >= 0 && index < compositeParts.size() ? index : -1;
    }

    private void selectLayoutPart(int delta) {
        if (compositeParts.isEmpty()) {
            return;
        }
        int index = selectedLayoutIndex();
        if (index < 0) {
            index = delta < 0 ? compositeParts.size() - 1 : 0;
        } else {
            index = (index + delta + compositeParts.size()) % compositeParts.size();
        }
        compositeList.setSelectedIndex(index);
        compositeList.ensureIndexIsVisible(index);
        setStatus("Selected layout piece #" + (index + 1) + ": " + compositeParts.get(index).describe());
    }

    private void moveSelectedLayoutPart(int dx, int dy) {
        int index = selectedLayoutIndex();
        if (index < 0) {
            setStatus("Select a layout piece to move.");
            return;
        }
        LayoutPart part = compositeParts.get(index);
        part.offsetX = clamp(part.offsetX + dx, -12, 12);
        part.offsetY = clamp(part.offsetY + dy, -12, 12);
        refreshCompositeList();
        compositeList.setSelectedIndex(index);
        refreshLayoutPreview();
        setStatus("Moved #" + (index + 1) + " to dX " + part.offsetX + ", dY " + part.offsetY + ".");
    }

    private void rotateSelectedLayoutPart() {
        int index = selectedLayoutIndex();
        if (index < 0) {
            setStatus("Select a layout piece to rotate.");
            return;
        }
        LayoutPart part = compositeParts.get(index);
        part.rotation = (part.rotation + 1) & 0x3;
        refreshCompositeList();
        compositeList.setSelectedIndex(index);
        refreshLayoutPreview();
        setStatus("Rotated #" + (index + 1) + " to R" + part.rotation + ".");
    }

    private boolean ensureLayoutAnchor() {
        if (layoutAnchorPlane >= 0) {
            return true;
        }
        CaptureBatch anchor = AssetStudioCapture.capturePlayerArea(0);
        if (anchor == null || !anchor.isSuccess()) {
            setStatus("Layout anchor failed: "
                    + (anchor == null ? "live player/scene unavailable" : anchor.getError()));
            return false;
        }
        layoutAnchorX = anchor.getCenterX();
        layoutAnchorY = anchor.getCenterY();
        layoutAnchorPlane = anchor.getPlane();
        return true;
    }

    private void resetLayoutAnchor() {
        layoutAnchorPlane = -1;
        if (ensureLayoutAnchor()) {
            refreshLayoutPreview();
            setStatus("Layout anchor reset beside the current player position.");
        }
    }

    private void previewComposite() {
        if (compositeParts.isEmpty()) {
            setStatus("Add at least one layout piece first.");
            return;
        }
        if (!ensureLayoutAnchor()) {
            return;
        }
        refreshLayoutPreview();
        setStatus(ObjectCompositePreview.getStatus());
    }

    private void refreshLayoutPreview() {
        if (compositeParts.isEmpty() || !ensureLayoutAnchor()) {
            return;
        }

        List<RailCompositeLibrary.Component> components =
                new ArrayList<RailCompositeLibrary.Component>();
        for (LayoutPart part : compositeParts) {
            components.add(new RailCompositeLibrary.Component(
                    part.id, part.type, part.rotation, part.offsetX, part.offsetY));
        }

        String name = compositeNameField.getText() == null
                ? "Rail Layout" : compositeNameField.getText().trim();
        ObjectCompositePreview.showComposite(name, components,
                layoutAnchorX, layoutAnchorY, layoutAnchorPlane,
                number(offsetXSpinner), number(offsetYSpinner));
    }

    private void saveComposite() {
        if (compositeParts.isEmpty()) {
            setStatus("Add at least one layout piece before saving.");
            return;
        }

        List<RailCompositeLibrary.Component> components =
                new ArrayList<RailCompositeLibrary.Component>();
        for (LayoutPart part : compositeParts) {
            components.add(new RailCompositeLibrary.Component(
                    part.id, part.type, part.rotation, part.offsetX, part.offsetY));
        }

        Object selectedRole = compositeRole.getSelectedItem();
        RailCompositeLibrary.Role role = selectedRole instanceof RailCompositeLibrary.Role
                ? (RailCompositeLibrary.Role) selectedRole
                : RailCompositeLibrary.Role.CUSTOM;
        String error = RailCompositeLibrary.saveComposite(
                compositeNameField.getText(), role, components);
        setStatus(error == null
                ? "Saved " + compositeNameField.getText().trim() + " [" + role + "] with "
                        + components.size() + " piece(s), including relative X/Y offsets."
                : error);
    }

    private void installLayoutHotkeys() {
        if (layoutHotkeysInstalled) {
            return;
        }
        layoutHotkeysInstalled = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event.getID() != KeyEvent.KEY_PRESSED
                                || !layoutHotkeys.isSelected()
                                || !ObjectExplorerPanel.this.isShowing()
                                || compositeParts.isEmpty()) {
                            return false;
                        }

                        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                .getFocusOwner();
                        if (focus instanceof JTextField
                                || focus != null && SwingUtilities.getAncestorOfClass(
                                        JSpinner.class, focus) != null
                                || focus != null && SwingUtilities.getAncestorOfClass(
                                        JComboBox.class, focus) != null) {
                            return false;
                        }

                        int key = event.getKeyCode();
                        if (event.isControlDown() && key == KeyEvent.VK_D) {
                            duplicateSelectedLayoutPart();
                            return true;
                        }
                        if (key == KeyEvent.VK_OPEN_BRACKET) {
                            selectLayoutPart(-1);
                            return true;
                        }
                        if (key == KeyEvent.VK_CLOSE_BRACKET) {
                            selectLayoutPart(1);
                            return true;
                        }
                        if (key == KeyEvent.VK_R) {
                            rotateSelectedLayoutPart();
                            return true;
                        }
                        if (key == KeyEvent.VK_LEFT) {
                            moveSelectedLayoutPart(-1, 0);
                            return true;
                        }
                        if (key == KeyEvent.VK_RIGHT) {
                            moveSelectedLayoutPart(1, 0);
                            return true;
                        }
                        if (key == KeyEvent.VK_UP) {
                            moveSelectedLayoutPart(0, 1);
                            return true;
                        }
                        if (key == KeyEvent.VK_DOWN) {
                            moveSelectedLayoutPart(0, -1);
                            return true;
                        }
                        if (key == KeyEvent.VK_DELETE) {
                            removeCompositePart();
                            return true;
                        }
                        return false;
                    }
                });
    }

    private Snapshot currentSnapshot() {
        return new Snapshot(currentName, number(idSpinner), number(typeSpinner),
                number(rotationSpinner));
    }

    private void saveResearch() {
        Snapshot snapshot = currentSnapshot();
        String tag = tagField.getText() == null ? "" : tagField.getText().trim();
        if (tag.length() == 0) {
            tag = "OBJECT_RESEARCH";
        }

        try {
            Files.createDirectories(RESEARCH_FILE.getParent());
            boolean writeHeader = !Files.exists(RESEARCH_FILE);
            List<String> lines = new ArrayList<String>();
            if (writeHeader) {
                lines.add("timestamp\tid\tname\ttype\trotation\ttag");
            }
            lines.add(timestamp() + "\t" + snapshot.id + "\t" + safe(snapshot.name)
                    + "\t" + snapshot.type + "\t" + snapshot.rotation + "\t" + safe(tag));
            Files.write(RESEARCH_FILE, lines, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            setStatus("Saved research: " + snapshot.describe() + " [" + tag + "]");
        } catch (Exception ex) {
            setStatus("Research save failed: " + ex.getMessage());
        }
    }

    private void setStatus(String text) {
        status.setText(text == null ? "" : text);
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

    private int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ');
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
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
            return name + " | ID " + id + " | T" + type + " | R" + rotation
                    + " | dX " + offsetX + " | dY " + offsetY;
        }
    }

    private static final class Snapshot {
        private final String name;
        private final int id;
        private final int type;
        private final int rotation;

        private Snapshot(String name, int id, int type, int rotation) {
            this.name = name == null || name.trim().isEmpty() ? "id-" + id : name;
            this.id = id;
            this.type = type;
            this.rotation = rotation;
        }

        private String describe() {
            return name + " | ID " + id + " | T" + type + " | R" + rotation;
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
