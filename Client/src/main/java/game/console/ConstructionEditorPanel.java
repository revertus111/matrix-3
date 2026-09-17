package game.console;

import game.ConstructionPaletteOverlay;
import game.DevModeBridge;
import game.DevSpawnPlacement;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Owner/admin Construction placement prototype for Matrix3.
 *
 * VERIFIED: placement delegates to DevModeBridge/DevSpawnPlacement, which uses
 * Matrix3's existing scene-tile action and server-authoritative devspawn bridge.
 *
 * HYPOTHESIS: the starter object ids/types below are provisional cache candidates
 * until they are visually verified in the active Matrix3 cache. They are exposed
 * in the panel so object type can be corrected without changing placement code.
 *
 * This panel deliberately does not emulate settlement persistence or a 3D ghost
 * renderer. Those remain separate Construction foundation workstreams.
 */
public final class ConstructionEditorPanel extends JScrollPane {

    private static final long serialVersionUID = 1847621040613979535L;

    private PiecePreset selectedPiece = PiecePreset.WOOD_WALL;
    private int rotation;
    private boolean hotkeysInstalled;

    private final JToggleButton wallButton = new JToggleButton("Wall");
    private final JToggleButton floorButton = new JToggleButton("Floor");
    private final JToggleButton doorwayButton = new JToggleButton("Doorway");
    private final JLabel objectIdValue = ConsoleTheme.createValueLabel();
    private final JLabel rotationValue = ConsoleTheme.createValueLabel();
    private final JSpinner objectTypeSpinner = new JSpinner(new SpinnerNumberModel(
            Integer.valueOf(PiecePreset.WOOD_WALL.defaultObjectType), Integer.valueOf(0), Integer.valueOf(22),
            Integer.valueOf(1)));
    private final JComboBox<PlacementChoice> placementMode = new JComboBox<PlacementChoice>(PlacementChoice.values());
    private final JTextArea statusText = ConsoleTheme.createWrappedText("Select a piece, then arm placement.", 4);

    public ConstructionEditorPanel() {
        ViewportWidthPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.PANEL);
        content.setBorder(ConsoleTheme.panelPadding(20, 18, 20, 18));
        content.setMinimumSize(new Dimension(0, 0));

        content.add(ConsoleTheme.titleLabel("CONSTRUCTION EDITOR"));
        content.add(Box.createVerticalStrut(4));
        content.add(ConsoleTheme.subtitleLabel("Matrix3 freeform placement prototype"));
        content.add(Box.createVerticalStrut(18));
        content.add(createPieceCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPlacementCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createControlsCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createStatusCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createScopeCard());
        content.add(Box.createVerticalGlue());

        setViewportView(content);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(this);

        configurePieceButtons();
        ConsoleTheme.styleComboBox(placementMode);
        objectTypeSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        objectTypeSpinner.addChangeListener(e -> rearmIfActive("Object type changed."));
        placementMode.addActionListener(e -> rearmIfActive("Placement mode changed."));

        selectPiece(PiecePreset.WOOD_WALL, false);
        ensureHotkeys();
    }

    private JPanel createPieceCard() {
        JPanel card = ConsoleTheme.createCard("Piece palette");
        card.add(Box.createVerticalStrut(10));

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        row.add(wallButton);
        row.add(Box.createHorizontalStrut(6));
        row.add(floorButton);
        row.add(Box.createHorizontalStrut(6));
        row.add(doorwayButton);
        row.add(Box.createHorizontalGlue());
        card.add(row);
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createWrappedText(
                "Starter ids are provisional cache candidates. Verify the live model before treating a preset as final.", 2));
        card.add(Box.createVerticalStrut(10));

        JButton openPalette = new JButton("Open in-game build palette");
        ConsoleTheme.styleButton(openPalette);
        openPalette.setAlignmentX(LEFT_ALIGNMENT);
        openPalette.addActionListener(e -> {
            ConstructionPaletteOverlay.show();
            setStatus("In-game Construction palette opened. Select a piece there to arm placement.");
        });
        card.add(openPalette);
        return card;
    }

    private JPanel createPlacementCard() {
        JPanel card = ConsoleTheme.createCard("Placement");
        card.add(Box.createVerticalStrut(10));
        card.add(ConsoleTheme.createValueRow("Object id", objectIdValue));
        card.add(Box.createVerticalStrut(8));

        JLabel typeLabel = new JLabel("Object type (0-22)");
        ConsoleTheme.styleLabel(typeLabel, true);
        typeLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(typeLabel);
        card.add(Box.createVerticalStrut(5));
        objectTypeSpinner.setAlignmentX(LEFT_ALIGNMENT);
        card.add(objectTypeSpinner);
        card.add(Box.createVerticalStrut(10));

        card.add(ConsoleTheme.createValueRow("Rotation", rotationValue));
        card.add(Box.createVerticalStrut(8));
        JPanel rotateRow = new JPanel();
        rotateRow.setLayout(new BoxLayout(rotateRow, BoxLayout.X_AXIS));
        rotateRow.setOpaque(false);
        rotateRow.setAlignmentX(LEFT_ALIGNMENT);
        rotateRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JButton rotateLeft = new JButton("Rotate left");
        JButton rotateRight = new JButton("Rotate right");
        ConsoleTheme.styleButton(rotateLeft);
        ConsoleTheme.styleButton(rotateRight);
        rotateLeft.addActionListener(e -> rotate(-1, "Rotation changed."));
        rotateRight.addActionListener(e -> rotate(1, "Rotation changed."));
        rotateRow.add(rotateLeft);
        rotateRow.add(Box.createHorizontalStrut(6));
        rotateRow.add(rotateRight);
        rotateRow.add(Box.createHorizontalGlue());
        card.add(rotateRow);
        card.add(Box.createVerticalStrut(10));

        JLabel modeLabel = new JLabel("Placement mode");
        ConsoleTheme.styleLabel(modeLabel, true);
        modeLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(modeLabel);
        card.add(Box.createVerticalStrut(5));
        placementMode.setAlignmentX(LEFT_ALIGNMENT);
        card.add(placementMode);
        card.add(Box.createVerticalStrut(12));

        JPanel actionRow = new JPanel();
        actionRow.setLayout(new BoxLayout(actionRow, BoxLayout.X_AXIS));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JButton arm = new JButton("Arm placement");
        JButton cancel = new JButton("Cancel");
        ConsoleTheme.styleButton(arm);
        ConsoleTheme.styleButton(cancel);
        arm.addActionListener(e -> armCurrent());
        cancel.addActionListener(e -> setStatus(DevModeBridge.cancelPlacement()));
        actionRow.add(arm);
        actionRow.add(Box.createHorizontalStrut(6));
        actionRow.add(cancel);
        actionRow.add(Box.createHorizontalGlue());
        card.add(actionRow);
        return card;
    }

    private JPanel createControlsCard() {
        JPanel card = ConsoleTheme.createCard("Live controls");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Paint: left-click normal world tiles. Continuous: right-click a tile and choose the Dev placement entry. "
                        + "While armed, R rotates right, Shift+R rotates left, and the mouse wheel rotates when used over the game. "
                        + "Escape cancels. Right-click a Dev-owned placed object for the existing move, rotate, duplicate, inspect, and delete actions.",
                5));
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = ConsoleTheme.createCard("Status");
        card.add(Box.createVerticalStrut(9));
        statusText.setForeground(ConsoleTheme.ACCENT);
        card.add(statusText);
        return card;
    }

    private JPanel createScopeCard() {
        JPanel card = ConsoleTheme.createCard("Prototype boundary");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "This tool currently creates Dev-owned runtime objects through Matrix3's existing Admin+ placement authority. "
                        + "The custom-drawn in-game palette now owns build selection/search/rotation UI, but settlement persistence, "
                        + "occupancy rules, Construction XP, and the true 3D ghost renderer are still separate foundation work.",
                4));
        return card;
    }

    private void configurePieceButtons() {
        ButtonGroup group = new ButtonGroup();
        group.add(wallButton);
        group.add(floorButton);
        group.add(doorwayButton);

        ConsoleTheme.styleButton(wallButton);
        ConsoleTheme.styleButton(floorButton);
        ConsoleTheme.styleButton(doorwayButton);

        wallButton.addActionListener(e -> selectPiece(PiecePreset.WOOD_WALL, true));
        floorButton.addActionListener(e -> selectPiece(PiecePreset.WOOD_FLOOR, true));
        doorwayButton.addActionListener(e -> selectPiece(PiecePreset.DOORWAY, true));
    }

    private void selectPiece(PiecePreset preset, boolean rearm) {
        selectedPiece = preset;
        wallButton.setSelected(preset == PiecePreset.WOOD_WALL);
        floorButton.setSelected(preset == PiecePreset.WOOD_FLOOR);
        doorwayButton.setSelected(preset == PiecePreset.DOORWAY);
        objectIdValue.setText(Integer.toString(preset.objectId));
        objectTypeSpinner.setValue(Integer.valueOf(preset.defaultObjectType));
        refreshRotationLabel();
        if (rearm) {
            rearmIfActive(preset.displayName + " selected.");
        }
    }

    private void rotate(int delta, String statusPrefix) {
        rotation = (rotation + delta) & 0x3;
        refreshRotationLabel();
        if (DevSpawnPlacement.hasActive()) {
            String message = armCurrentInternal();
            setStatus(statusPrefix + " " + message);
        } else {
            setStatus(statusPrefix + " Rotation is now " + rotation + ".");
        }
    }

    private void refreshRotationLabel() {
        rotationValue.setText(rotation + " (" + (rotation * 90) + " deg)");
    }

    private void armCurrent() {
        setStatus(armCurrentInternal());
    }

    private String armCurrentInternal() {
        DevModeBridge.setEnabled(true);
        int objectType = ((Number) objectTypeSpinner.getValue()).intValue();
        DevSpawnPlacement.Request request = DevSpawnPlacement.object(
                selectedPiece.objectId,
                objectType,
                rotation,
                DevSpawnPlacement.RotationMode.FIXED);
        PlacementChoice choice = (PlacementChoice) placementMode.getSelectedItem();
        if (choice == null) {
            choice = PlacementChoice.PAINT;
        }
        return DevModeBridge.armSpawn(request, choice.spawnMode);
    }

    private void rearmIfActive(String statusPrefix) {
        if (!DevSpawnPlacement.hasActive()) {
            return;
        }
        setStatus(statusPrefix + " " + armCurrentInternal());
    }

    private void setStatus(String message) {
        statusText.setText(message == null || message.trim().length() == 0 ? "Ready." : message);
        statusText.setCaretPosition(0);
    }

    private synchronized void ensureHotkeys() {
        if (hotkeysInstalled) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override
                public void eventDispatched(AWTEvent event) {
                    if (ConstructionPaletteOverlay.isVisible() || !isShowing() || !DevSpawnPlacement.hasActive()
                            || isEventFromThisPanel(event)) {
                        return;
                    }
                    if (event instanceof KeyEvent) {
                        KeyEvent keyEvent = (KeyEvent) event;
                        if (keyEvent.getID() == KeyEvent.KEY_PRESSED && keyEvent.getKeyCode() == KeyEvent.VK_R) {
                            rotate(keyEvent.isShiftDown() ? -1 : 1, "Hotkey rotation.");
                            keyEvent.consume();
                        }
                    } else if (event instanceof MouseWheelEvent) {
                        MouseWheelEvent wheelEvent = (MouseWheelEvent) event;
                        int wheel = wheelEvent.getWheelRotation();
                        if (wheel != 0) {
                            rotate(wheel > 0 ? 1 : -1, "Mouse-wheel rotation.");
                            wheelEvent.consume();
                        }
                    }
                }
            }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
            hotkeysInstalled = true;
        } catch (RuntimeException ex) {
            setStatus("Placement buttons are available, but the global rotation hotkey listener could not be installed.");
        }
    }

    private boolean isEventFromThisPanel(AWTEvent event) {
        Object source = event.getSource();
        return source instanceof Component && SwingUtilities.isDescendingFrom((Component) source, this);
    }

    private enum PiecePreset {
        WOOD_WALL("Wooden wall", 13450, 0),
        WOOD_FLOOR("Wooden floor", 13684, 22),
        DOORWAY("Doorway", 13344, 0);

        private final String displayName;
        private final int objectId;
        private final int defaultObjectType;

        PiecePreset(String displayName, int objectId, int defaultObjectType) {
            this.displayName = displayName;
            this.objectId = objectId;
            this.defaultObjectType = defaultObjectType;
        }
    }

    private enum PlacementChoice {
        PAINT("Paint - left-click tiles", DevSpawnPlacement.SpawnMode.PAINT),
        CONTINUOUS("Continuous - right-click tiles", DevSpawnPlacement.SpawnMode.CONTINUOUS);

        private final String displayName;
        private final DevSpawnPlacement.SpawnMode spawnMode;

        PlacementChoice(String displayName, DevSpawnPlacement.SpawnMode spawnMode) {
            this.displayName = displayName;
            this.spawnMode = spawnMode;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = -2575982083291467195L;

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
