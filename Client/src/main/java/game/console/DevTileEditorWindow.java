package game.console;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * Dark Dev Mode editor shell for one selected world tile.
 *
 * This first slice intentionally edits tile contents through the existing Spawn
 * Browser while exposing exact Matrix3 world/region/chunk coordinates. Terrain,
 * height and clipping mutation remain separate verified engine tasks.
 */
public final class DevTileEditorWindow {

    private static JFrame frame;
    private static DevTileEditorWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLabel tileLabel = valueLabel();
    private final JLabel regionLabel = valueLabel();
    private final JLabel regionLocalLabel = valueLabel();
    private final JLabel chunkLabel = valueLabel();
    private final JLabel chunkLocalLabel = valueLabel();
    private final JLabel planeLabel = valueLabel();
    private final JLabel statusLabel = new JLabel("Select a tile in game to edit its live contents.");

    private int targetX;
    private int targetY;
    private int targetPlane;

    private DevTileEditorWindow() {
        buildUi();
    }

    public static void open(int x, int y, int plane) {
        ensureWindow();
        instance.setTarget(x, y, plane);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new DevTileEditorWindow();
        frame = new JFrame("Matrix3 Dev Mode - Tile Editor");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(520, 430));
        frame.setSize(new Dimension(620, 520));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.WINDOW);

        JLabel title = new JLabel("TILE EDITOR");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Live tile target / world placement workspace");
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
        center.add(createActionsCard());
        center.add(Box.createVerticalStrut(12));
        center.add(createTerrainCard());
        root.add(center, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createIdentityCard() {
        JPanel card = card("Tile identity");
        JPanel grid = new JPanel(new GridLayout(6, 2, 10, 7));
        grid.setOpaque(false);
        addRow(grid, "World tile", tileLabel);
        addRow(grid, "Plane", planeLabel);
        addRow(grid, "Region ID", regionLabel);
        addRow(grid, "Local in region", regionLocalLabel);
        addRow(grid, "World chunk", chunkLabel);
        addRow(grid, "Local in chunk", chunkLocalLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(grid);
        return card;
    }

    private JPanel createActionsCard() {
        JPanel card = card("Live contents");

        JLabel description = new JLabel("<html><div style='width:500px'>Spawn NPCs, objects, or ground items directly onto this exact tile using the existing Matrix3 Dev Spawn bridge.</div></html>");
        description.setFont(ConsoleTheme.SMALL_FONT);
        description.setForeground(ConsoleTheme.MUTED_TEXT);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton spawn = new JButton("Open Spawn Browser for this tile");
        spawn.setAlignmentX(Component.LEFT_ALIGNMENT);
        spawn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(spawn);
        spawn.addActionListener(e -> DevSpawnBrowserWindow.open(targetX, targetY, targetPlane));

        JButton copy = new JButton("Copy tile coordinates");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(copy);
        copy.addActionListener(e -> copyTile());

        card.add(Box.createVerticalStrut(9));
        card.add(description);
        card.add(Box.createVerticalStrut(12));
        card.add(spawn);
        card.add(Box.createVerticalStrut(7));
        card.add(copy);
        return card;
    }

    private JPanel createTerrainCard() {
        JPanel card = card("Terrain / clipping");
        JLabel note = new JLabel("<html><div style='width:500px'>Terrain height, overlays/underlays, movement clipping and projectile clipping are intentionally read-only/not exposed yet. Those require a verified Matrix3 map/clipping mutation path before Dev Mode is allowed to change them.</div></html>");
        note.setFont(ConsoleTheme.SMALL_FONT);
        note.setForeground(ConsoleTheme.MUTED_TEXT);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(9));
        card.add(note);
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
        return label;
    }

    private void setTarget(int x, int y, int plane) {
        targetX = x;
        targetY = y;
        targetPlane = plane;

        int regionX = x >> 6;
        int regionY = y >> 6;
        int regionId = regionX << 8 | regionY;
        int chunkX = x >> 3;
        int chunkY = y >> 3;

        tileLabel.setText(x + ", " + y);
        planeLabel.setText(Integer.toString(plane));
        regionLabel.setText(regionId + "  (" + regionX + ", " + regionY + ")");
        regionLocalLabel.setText((x & 0x3f) + ", " + (y & 0x3f));
        chunkLabel.setText(chunkX + ", " + chunkY);
        chunkLocalLabel.setText((x & 0x7) + ", " + (y & 0x7));
        statusLabel.setText("Target updated from the live world right-click.");
    }

    private void copyTile() {
        String value = targetX + ", " + targetY + ", " + targetPlane;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
            statusLabel.setText("Copied tile: " + value);
        } catch (RuntimeException ex) {
            statusLabel.setText("Unable to access the system clipboard.");
        }
    }
}
