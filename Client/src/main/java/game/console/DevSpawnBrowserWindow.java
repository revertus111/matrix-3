package game.console;

import game.DevModeBridge;
import game.DevSpawnPlacement;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Dark themed Dev Mode placement window opened from an in-game tile target.
 *
 * Definition search is lazy and cache-backed: typing text starts background name
 * indexing; numeric searches resolve a direct id immediately. Item thumbnails
 * reuse the existing Client Console renderer. NPC/object model thumbnails remain
 * a separate render-bridge task and are not guessed here.
 */
public final class DevSpawnBrowserWindow {

    private static JFrame frame;
    private static DevSpawnBrowserWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel cards = new JPanel(new CardLayout());
    private final JLabel targetLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Choose a type and placement mode.");

    private final JComboBox<String> placementModeBox = new JComboBox<String>(new String[] {
            "Once", "Continuous", "Paint"
    });
    private final JComboBox<String> objectRotationModeBox = new JComboBox<String>(new String[] {
            "Fixed", "Cycle", "Random"
    });

    private final JTextField npcIdField = new JTextField();
    private final JTextField objectIdField = new JTextField();
    private final JTextField objectTypeField = new JTextField("10");
    private final JTextField objectRotationField = new JTextField("0");
    private final JTextField itemIdField = new JTextField();
    private final JTextField itemAmountField = new JTextField("1");

    private final JButton npcSpawnButton = new JButton();
    private final JButton objectSpawnButton = new JButton();
    private final JButton itemSpawnButton = new JButton();

    private final DevSpawnSearchPanel npcSearch = new DevSpawnSearchPanel(
            DevSpawnSearchPanel.NPC,
            new DevSpawnSearchPanel.SelectionListener() {
                @Override
                public void selected(int id, String name) {
                    npcIdField.setText(Integer.toString(id));
                    statusLabel.setText("Selected NPC: " + name + " (" + id + ").");
                }
            });

    private final DevSpawnSearchPanel objectSearch = new DevSpawnSearchPanel(
            DevSpawnSearchPanel.OBJECT,
            new DevSpawnSearchPanel.SelectionListener() {
                @Override
                public void selected(int id, String name) {
                    objectIdField.setText(Integer.toString(id));
                    statusLabel.setText("Selected object: " + name + " (" + id + ").");
                }
            });

    private final DevSpawnSearchPanel itemSearch = new DevSpawnSearchPanel(
            DevSpawnSearchPanel.ITEM,
            new DevSpawnSearchPanel.SelectionListener() {
                @Override
                public void selected(int id, String name) {
                    itemIdField.setText(Integer.toString(id));
                    statusLabel.setText("Selected item: " + name + " (" + id + ").");
                }
            });

    private int targetX;
    private int targetY;
    private int targetPlane;

    private DevSpawnBrowserWindow() {
        buildUi();
    }

    public static void open(int x, int y, int plane) {
        ensureWindow();
        instance.setTarget(x, y, plane);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    public static void showStatus(String message) {
        if (instance != null && message != null) {
            instance.statusLabel.setText(message);
        }
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new DevSpawnBrowserWindow();
        frame = new JFrame("Matrix3 Dev Mode - Spawn Browser");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(680, 690));
        frame.setSize(new Dimension(820, 800));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.WINDOW);

        JLabel title = new JLabel("DEV SPAWN");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        targetLabel.setFont(ConsoleTheme.SMALL_FONT);
        targetLabel.setForeground(ConsoleTheme.ACCENT);
        targetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(targetLabel);
        header.add(Box.createVerticalStrut(14));
        header.add(createTypeBar());
        header.add(Box.createVerticalStrut(9));
        header.add(createPlacementBar());
        header.add(Box.createVerticalStrut(12));
        root.add(header, BorderLayout.NORTH);

        cards.setBackground(ConsoleTheme.PANEL);
        cards.add(wrapCard(createNpcCard()), "npc");
        cards.add(wrapCard(createObjectCard()), "object");
        cards.add(wrapCard(createItemCard()), "item");
        root.add(cards, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);

        placementModeBox.addActionListener(e -> updateSpawnButtonLabels());
        updateSpawnButtonLabels();
    }

    private JScrollPane wrapCard(JPanel card) {
        JScrollPane pane = new JScrollPane(card);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        ConsoleTheme.styleScrollPane(pane);
        return pane;
    }

    private JPanel createTypeBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setBackground(ConsoleTheme.WINDOW);

        JToggleButton npcButton = createTypeButton("NPC", "npc");
        JToggleButton objectButton = createTypeButton("OBJECT", "object");
        JToggleButton itemButton = createTypeButton("ITEM", "item");

        ButtonGroup group = new ButtonGroup();
        group.add(npcButton);
        group.add(objectButton);
        group.add(itemButton);
        npcButton.setSelected(true);

        bar.add(npcButton);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(objectButton);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(itemButton);
        return bar;
    }

    private JPanel createPlacementBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setBackground(ConsoleTheme.WINDOW);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Placement mode");
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);

        placementModeBox.setPreferredSize(new Dimension(150, 36));
        ConsoleTheme.styleComboBox(placementModeBox);

        JButton cancel = new JButton("Cancel active placement");
        ConsoleTheme.styleButton(cancel);
        cancel.addActionListener(e -> statusLabel.setText(DevModeBridge.cancelPlacement()));

        bar.add(label);
        bar.add(placementModeBox);
        bar.add(cancel);
        return bar;
    }

    private JToggleButton createTypeButton(String label, final String cardId) {
        JToggleButton button = new JToggleButton(label);
        ConsoleTheme.styleButton(button);
        button.setPreferredSize(new Dimension(112, 38));
        button.addActionListener(e -> ((CardLayout) cards.getLayout()).show(cards, cardId));
        return button;
    }

    private JPanel createNpcCard() {
        JPanel card = createCard("Spawn NPC",
                "Once places on the selected tile. Continuous stays armed for repeated right-click placement. Paint stays armed and places on normal left-clicked world tiles while Walk Here remains active.");
        npcSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(npcSearch);
        card.add(Box.createVerticalStrut(14));
        addField(card, "Selected NPC ID", npcIdField);
        card.add(Box.createVerticalStrut(14));
        configureSpawnButton(npcSpawnButton, new Runnable() {
            @Override
            public void run() {
                Integer npcId = parseNonNegative(npcIdField, "NPC ID");
                if (npcId == null) {
                    return;
                }
                place(DevSpawnPlacement.npc(npcId.intValue()));
            }
        });
        card.add(npcSpawnButton);
        return card;
    }

    private JPanel createObjectCard() {
        JPanel card = createCard("Spawn Object",
                "Place an object with explicit type plus Fixed, Cycle, or Random rotation behavior for repeated placement.");
        objectSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(objectSearch);
        card.add(Box.createVerticalStrut(14));
        addField(card, "Selected Object ID", objectIdField);
        card.add(Box.createVerticalStrut(9));
        addField(card, "Type (0-22)", objectTypeField);
        card.add(Box.createVerticalStrut(9));
        addField(card, "Starting rotation (0-3)", objectRotationField);
        card.add(Box.createVerticalStrut(9));
        addCombo(card, "Rotation behavior", objectRotationModeBox);
        card.add(Box.createVerticalStrut(14));
        configureSpawnButton(objectSpawnButton, new Runnable() {
            @Override
            public void run() {
                Integer objectId = parseNonNegative(objectIdField, "Object ID");
                Integer type = parseRange(objectTypeField, "Object type", 0, 22);
                Integer rotation = parseRange(objectRotationField, "Object rotation", 0, 3);
                if (objectId == null || type == null || rotation == null) {
                    return;
                }
                place(DevSpawnPlacement.object(objectId.intValue(), type.intValue(), rotation.intValue(),
                        selectedRotationMode()));
            }
        });
        card.add(objectSpawnButton);
        return card;
    }

    private JPanel createItemCard() {
        JPanel card = createCard("Spawn Ground Item",
                "Item results reuse the real Item Browser thumbnail renderer. Continuous/Paint can place repeated ground-item stacks with the selected amount.");
        itemSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(itemSearch);
        card.add(Box.createVerticalStrut(14));
        addField(card, "Selected Item ID", itemIdField);
        card.add(Box.createVerticalStrut(9));
        addField(card, "Amount", itemAmountField);
        card.add(Box.createVerticalStrut(14));
        configureSpawnButton(itemSpawnButton, new Runnable() {
            @Override
            public void run() {
                Integer itemId = parseNonNegative(itemIdField, "Item ID");
                Integer amount = parseRange(itemAmountField, "Item amount", 1, Integer.MAX_VALUE);
                if (itemId == null || amount == null) {
                    return;
                }
                place(DevSpawnPlacement.item(itemId.intValue(), amount.intValue()));
            }
        });
        card.add(itemSpawnButton);
        return card;
    }

    private JPanel createCard(String titleText, String descriptionText) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ConsoleTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                ConsoleTheme.panelPadding(16, 16, 16, 16)));

        JLabel title = new JLabel(titleText);
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel("<html><div style='width:590px'>" + descriptionText + "</div></html>");
        description.setFont(ConsoleTheme.SMALL_FONT);
        description.setForeground(ConsoleTheme.MUTED_TEXT);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(title);
        card.add(Box.createVerticalStrut(5));
        card.add(description);
        card.add(Box.createVerticalStrut(15));
        return card;
    }

    private void addField(JPanel card, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText, SwingConstants.LEFT);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(field);

        card.add(label);
        card.add(Box.createVerticalStrut(5));
        card.add(field);
    }

    private void addCombo(JPanel card, String labelText, JComboBox<String> combo) {
        JLabel label = new JLabel(labelText, SwingConstants.LEFT);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        ConsoleTheme.styleComboBox(combo);
        card.add(label);
        card.add(Box.createVerticalStrut(5));
        card.add(combo);
    }

    private void configureSpawnButton(JButton button, final Runnable action) {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(button);
        button.addActionListener(e -> action.run());
    }

    private void updateSpawnButtonLabels() {
        DevSpawnPlacement.SpawnMode mode = selectedSpawnMode();
        if (mode == DevSpawnPlacement.SpawnMode.CONTINUOUS) {
            npcSpawnButton.setText("Arm Continuous NPC");
            objectSpawnButton.setText("Arm Continuous Object");
            itemSpawnButton.setText("Arm Continuous Item");
        } else if (mode == DevSpawnPlacement.SpawnMode.PAINT) {
            npcSpawnButton.setText("Arm NPC Paint");
            objectSpawnButton.setText("Arm Object Paint");
            itemSpawnButton.setText("Arm Item Paint");
        } else {
            npcSpawnButton.setText("Spawn NPC");
            objectSpawnButton.setText("Spawn Object");
            itemSpawnButton.setText("Spawn Item");
        }
    }

    private void place(DevSpawnPlacement.Request request) {
        DevSpawnPlacement.SpawnMode mode = selectedSpawnMode();
        if (mode == DevSpawnPlacement.SpawnMode.ONCE) {
            statusLabel.setText(DevModeBridge.spawnOnce(request, targetX, targetY, targetPlane));
        } else {
            statusLabel.setText(DevModeBridge.armSpawn(request, mode));
        }
    }

    private DevSpawnPlacement.SpawnMode selectedSpawnMode() {
        int index = placementModeBox.getSelectedIndex();
        if (index == 1) {
            return DevSpawnPlacement.SpawnMode.CONTINUOUS;
        }
        if (index == 2) {
            return DevSpawnPlacement.SpawnMode.PAINT;
        }
        return DevSpawnPlacement.SpawnMode.ONCE;
    }

    private DevSpawnPlacement.RotationMode selectedRotationMode() {
        int index = objectRotationModeBox.getSelectedIndex();
        if (index == 1) {
            return DevSpawnPlacement.RotationMode.CYCLE;
        }
        if (index == 2) {
            return DevSpawnPlacement.RotationMode.RANDOM;
        }
        return DevSpawnPlacement.RotationMode.FIXED;
    }

    private void setTarget(int x, int y, int plane) {
        targetX = x;
        targetY = y;
        targetPlane = plane;
        targetLabel.setText("Target tile: " + x + ", " + y + ", plane " + plane);
        statusLabel.setText("Ready. Once spawns here; Continuous/Paint stay armed until cancelled.");
    }

    private Integer parseNonNegative(JTextField field, String label) {
        return parseRange(field, label, 0, Integer.MAX_VALUE);
    }

    private Integer parseRange(JTextField field, String label, int minimum, int maximum) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.length() == 0) {
            statusLabel.setText(label + " is required.");
            field.requestFocusInWindow();
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                statusLabel.setText(label + " must be between " + minimum + " and " + maximum + ".");
                field.requestFocusInWindow();
                return null;
            }
            return Integer.valueOf(parsed);
        } catch (NumberFormatException ex) {
            statusLabel.setText(label + " must be a whole number.");
            field.requestFocusInWindow();
            return null;
        }
    }
}
