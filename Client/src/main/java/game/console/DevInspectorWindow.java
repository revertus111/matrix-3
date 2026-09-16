package game.console;

import game.DevModeBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * Shared contextual Inspector for Dev Mode entity targets.
 *
 * Runtime manipulation routes through DevModeBridge and the server's guarded Dev
 * ownership manager. Definition/map persistence remains intentionally separate.
 */
public final class DevInspectorWindow {

    private static JFrame frame;
    private static DevInspectorWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLabel typeLabel = valueLabel();
    private final JLabel nameLabel = valueLabel();
    private final JLabel idLabel = valueLabel();
    private final JLabel tileLabel = valueLabel();
    private final JLabel runtimeLabel = valueLabel();
    private final JLabel modeLabel = valueLabel();
    private final JLabel statusLabel = new JLabel("Select an NPC or object in the live game.");

    private final JButton moveButton = new JButton("Move to tile...");
    private final JButton duplicateButton = new JButton("Duplicate to tile...");
    private final JButton rotateLeftButton = new JButton("Rotate left");
    private final JButton rotateRightButton = new JButton("Rotate right");
    private final JButton deleteButton = new JButton("Delete Development Spawn");
    private final JButton cancelPlacementButton = new JButton("Cancel placement");
    private final JButton copyIdButton = new JButton("Copy ID");
    private final JButton copyTileButton = new JButton("Copy tile coordinates");

    private DevTarget target;

    private DevInspectorWindow() {
        buildUi();
    }

    public static void open(DevTarget target, boolean editIntent) {
        if (target == null) {
            return;
        }
        ensureWindow();
        instance.setTarget(target, editIntent);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    public static void showStatus(String message) {
        if (instance != null && message != null) {
            instance.statusLabel.setText(message);
        }
    }

    public static void refreshTarget(DevTarget target) {
        if (instance != null && target != null) {
            instance.setTarget(target, true);
        }
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new DevInspectorWindow();
        frame = new JFrame("Matrix3 Dev Mode - Inspector");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(540, 560));
        frame.setSize(new Dimension(640, 690));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.WINDOW);

        JLabel title = new JLabel("INSPECTOR");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Shared live Dev target / contextual world tools");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(14));
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(ConsoleTheme.WINDOW);
        center.add(createIdentityCard());
        center.add(Box.createVerticalStrut(12));
        center.add(createModeCard());
        center.add(Box.createVerticalStrut(12));
        center.add(createManipulationCard());
        center.add(Box.createVerticalStrut(12));
        center.add(createUtilityCard());
        root.add(center, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createIdentityCard() {
        JPanel card = card("Selected target");
        JPanel grid = new JPanel(new GridLayout(5, 2, 10, 7));
        grid.setOpaque(false);
        addRow(grid, "Type", typeLabel);
        addRow(grid, "Name", nameLabel);
        addRow(grid, "Definition ID", idLabel);
        addRow(grid, "World tile", tileLabel);
        addRow(grid, "Runtime reference", runtimeLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(grid);
        return card;
    }

    private JPanel createModeCard() {
        JPanel card = card("Route state");
        card.add(Box.createVerticalStrut(9));
        card.add(modeLabel);

        JLabel note = new JLabel("<html><div style='width:520px'>Move/Duplicate use the live world as the placement surface. "
                + "NPC movement is runtime-only. Object Move/Rotate/Delete are allowed only for server-tracked Dev placements; "
                + "ordinary map objects are protected. Duplicate leaves the source untouched and creates a new Dev-owned copy. "
                + "No persistent map/definition save path is used by this bundle.</div></html>");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(8));
        card.add(note);
        return card;
    }

    private JPanel createManipulationCard() {
        JPanel card = card("World manipulation");

        configureActionButton(moveButton, new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(DevModeBridge.armMove(target));
            }
        });
        configureActionButton(duplicateButton, new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(DevModeBridge.armDuplicate(target));
            }
        });
        configureActionButton(rotateLeftButton, new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(DevModeBridge.rotateTarget(target, -1));
            }
        });
        configureActionButton(rotateRightButton, new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(DevModeBridge.rotateTarget(target, 1));
            }
        });
        configureActionButton(deleteButton, new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(DevModeBridge.deleteTarget(target));
            }
        });
        configureActionButton(cancelPlacementButton, new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(DevModeBridge.cancelPlacement());
            }
        });

        card.add(Box.createVerticalStrut(9));
        card.add(moveButton);
        card.add(Box.createVerticalStrut(7));
        card.add(duplicateButton);
        card.add(Box.createVerticalStrut(7));

        JPanel rotations = new JPanel(new GridLayout(1, 2, 7, 0));
        rotations.setOpaque(false);
        rotations.setAlignmentX(Component.LEFT_ALIGNMENT);
        rotations.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        rotations.add(rotateLeftButton);
        rotations.add(rotateRightButton);
        card.add(rotations);
        card.add(Box.createVerticalStrut(7));
        card.add(deleteButton);
        card.add(Box.createVerticalStrut(7));
        card.add(cancelPlacementButton);
        return card;
    }

    private JPanel createUtilityCard() {
        JPanel card = card("Target utilities");

        configureActionButton(copyIdButton, new Runnable() {
            @Override
            public void run() {
                copyId();
            }
        });
        configureActionButton(copyTileButton, new Runnable() {
            @Override
            public void run() {
                copyTile();
            }
        });

        card.add(Box.createVerticalStrut(9));
        card.add(copyIdButton);
        card.add(Box.createVerticalStrut(7));
        card.add(copyTileButton);
        return card;
    }

    private void configureActionButton(JButton button, final Runnable action) {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(button);
        button.addActionListener(e -> action.run());
    }

    private JPanel card(String titleText) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(14, 14, 14, 14)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        return card;
    }

    private void addRow(JPanel grid, String labelText, JLabel value) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        grid.add(label);
        grid.add(value);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void setTarget(DevTarget value, boolean editIntent) {
        target = value;
        typeLabel.setText(value.getType().getDisplayName());
        nameLabel.setText(value.getName());
        idLabel.setText(value.getId() >= 0 ? Integer.toString(value.getId()) : "Unresolved");
        tileLabel.setText(value.getWorldX() + ", " + value.getWorldY() + ", " + value.getPlane());
        runtimeLabel.setText(value.getRuntimeIndex() >= 0
                ? "NPC index " + value.getRuntimeIndex()
                : "Scene object target");
        modeLabel.setText(editIntent
                ? "EDIT ROUTE - runtime world tools enabled"
                : "INSPECT ROUTE - runtime tools available below");

        boolean validId = value.getId() >= 0;
        boolean object = value.getType() == TargetType.OBJECT;
        moveButton.setEnabled(validId);
        duplicateButton.setEnabled(validId);
        rotateLeftButton.setEnabled(validId && object);
        rotateRightButton.setEnabled(validId && object);
        deleteButton.setEnabled(validId);
        cancelPlacementButton.setEnabled(true);
        copyIdButton.setEnabled(validId);
        copyTileButton.setEnabled(true);
        statusLabel.setText("Target updated from the live Matrix3 right-click menu.");
    }

    private void copyId() {
        if (DevModeBridge.copyTargetId(target)) {
            statusLabel.setText("Copied definition ID: " + target.getId());
        } else {
            statusLabel.setText("Definition ID is not available for this target.");
        }
    }

    private void copyTile() {
        if (DevModeBridge.copyTargetTile(target)) {
            statusLabel.setText("Copied tile: " + target.getWorldX() + ", " + target.getWorldY() + ", " + target.getPlane());
        } else {
            statusLabel.setText("No live Dev target is selected.");
        }
    }
}
