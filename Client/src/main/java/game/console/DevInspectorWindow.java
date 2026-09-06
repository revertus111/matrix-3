package game.console;

import game.DevModeBridge;
import game.DevModeBridge.DevTarget;

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
 * Shared contextual Inspector shell for Dev Mode entity targets.
 *
 * This foundation is intentionally read-only. Inspect and Edit menu routes both
 * select the same shared target; Edit only marks mutation intent until verified
 * editor/save owners are added in later slices.
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

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new DevInspectorWindow();
        frame = new JFrame("Matrix3 Dev Mode - Inspector");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(520, 390));
        frame.setSize(new Dimension(610, 470));
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

        JLabel subtitle = new JLabel("Shared live Dev target / contextual editor foundation");
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
        center.add(createActionsCard());
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

        JLabel note = new JLabel("<html><div style='width:500px'>The shared target/Inspector path is active. "
                + "This slice does not mutate NPC definitions, object definitions, spawns, or map data. "
                + "Edit is a routing intent only until each authoritative save path is verified.</div></html>");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(8));
        card.add(note);
        return card;
    }

    private JPanel createActionsCard() {
        JPanel card = card("Target actions");

        copyIdButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyIdButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(copyIdButton);
        copyIdButton.addActionListener(e -> copyId());

        copyTileButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyTileButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(copyTileButton);
        copyTileButton.addActionListener(e -> copyTile());

        card.add(Box.createVerticalStrut(9));
        card.add(copyIdButton);
        card.add(Box.createVerticalStrut(7));
        card.add(copyTileButton);
        return card;
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
                ? "EDIT ROUTE - read-only foundation"
                : "INSPECT ROUTE - read-only");
        copyIdButton.setEnabled(value.getId() >= 0);
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
