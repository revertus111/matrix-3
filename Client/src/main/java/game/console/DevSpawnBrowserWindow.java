package game.console;

import game.ClientConsoleBridge;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Dark themed Dev Mode placement window opened from an in-game tile target.
 * Phase 1 intentionally uses direct IDs. Search indexes and rendered thumbnails
 * can be layered onto these same cards without changing the server placement
 * contract.
 */
public final class DevSpawnBrowserWindow {

    private static JFrame frame;
    private static DevSpawnBrowserWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel cards = new JPanel(new CardLayout());
    private final JLabel targetLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Choose a type and spawn it on the selected tile.");

    private final JTextField npcIdField = new JTextField();
    private final JTextField objectIdField = new JTextField();
    private final JTextField objectTypeField = new JTextField("10");
    private final JTextField objectRotationField = new JTextField("0");
    private final JTextField itemIdField = new JTextField();
    private final JTextField itemAmountField = new JTextField("1");

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

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new DevSpawnBrowserWindow();
        frame = new JFrame("Matrix3 Dev Mode - Spawn Browser");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(430, 430));
        frame.setSize(new Dimension(500, 500));
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
        title.setAlignmentX(LEFT_ALIGNMENT);

        targetLabel.setFont(ConsoleTheme.SMALL_FONT);
        targetLabel.setForeground(ConsoleTheme.ACCENT);
        targetLabel.setAlignmentX(LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(targetLabel);
        header.add(Box.createVerticalStrut(14));
        header.add(createTypeBar());
        header.add(Box.createVerticalStrut(12));
        root.add(header, BorderLayout.NORTH);

        cards.setBackground(ConsoleTheme.PANEL);
        cards.add(createNpcCard(), "npc");
        cards.add(createObjectCard(), "object");
        cards.add(createItemCard(), "item");
        root.add(cards, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);
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

    private JToggleButton createTypeButton(String label, final String cardId) {
        JToggleButton button = new JToggleButton(label);
        ConsoleTheme.styleButton(button);
        button.setPreferredSize(new Dimension(112, 38));
        button.addActionListener(e -> ((CardLayout) cards.getLayout()).show(cards, cardId));
        return button;
    }

    private JPanel createNpcCard() {
        JPanel card = createCard("Spawn NPC", "Spawn a runtime NPC on the exact tile selected in game.");
        addField(card, "NPC ID", npcIdField);
        card.add(Box.createVerticalStrut(14));
        card.add(createSpawnButton("Spawn NPC", new Runnable() {
            @Override
            public void run() {
                Integer npcId = parseNonNegative(npcIdField, "NPC ID");
                if (npcId == null) {
                    return;
                }
                queue("itembrowser devspawn npc " + npcId + " " + targetX + " " + targetY + " " + targetPlane);
            }
        }));
        return card;
    }

    private JPanel createObjectCard() {
        JPanel card = createCard("Spawn Object", "Place a runtime Matrix3 world object with an explicit type and rotation.");
        addField(card, "Object ID", objectIdField);
        card.add(Box.createVerticalStrut(9));
        addField(card, "Type (0-22)", objectTypeField);
        card.add(Box.createVerticalStrut(9));
        addField(card, "Rotation (0-3)", objectRotationField);
        card.add(Box.createVerticalStrut(14));
        card.add(createSpawnButton("Spawn Object", new Runnable() {
            @Override
            public void run() {
                Integer objectId = parseNonNegative(objectIdField, "Object ID");
                Integer type = parseRange(objectTypeField, "Object type", 0, 22);
                Integer rotation = parseRange(objectRotationField, "Object rotation", 0, 3);
                if (objectId == null || type == null || rotation == null) {
                    return;
                }
                queue("itembrowser devspawn object " + objectId + " " + targetX + " " + targetY + " "
                        + targetPlane + " " + type + " " + rotation);
            }
        }));
        return card;
    }

    private JPanel createItemCard() {
        JPanel card = createCard("Spawn Ground Item", "Spawn an owner-visible temporary ground item on the selected tile.");
        addField(card, "Item ID", itemIdField);
        card.add(Box.createVerticalStrut(9));
        addField(card, "Amount", itemAmountField);
        card.add(Box.createVerticalStrut(14));
        card.add(createSpawnButton("Spawn Item", new Runnable() {
            @Override
            public void run() {
                Integer itemId = parseNonNegative(itemIdField, "Item ID");
                Integer amount = parseRange(itemAmountField, "Item amount", 1, Integer.MAX_VALUE);
                if (itemId == null || amount == null) {
                    return;
                }
                queue("itembrowser devspawn item " + itemId + " " + targetX + " " + targetY + " "
                        + targetPlane + " " + amount);
            }
        }));
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
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel description = new JLabel("<html><div style='width:330px'>" + descriptionText + "</div></html>");
        description.setFont(ConsoleTheme.SMALL_FONT);
        description.setForeground(ConsoleTheme.MUTED_TEXT);
        description.setAlignmentX(LEFT_ALIGNMENT);

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
        label.setAlignmentX(LEFT_ALIGNMENT);

        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setAlignmentX(LEFT_ALIGNMENT);
        ConsoleTheme.styleTextField(field);

        card.add(label);
        card.add(Box.createVerticalStrut(5));
        card.add(field);
    }

    private JButton createSpawnButton(String text, final Runnable action) {
        JButton button = new JButton(text);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ConsoleTheme.styleButton(button);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void setTarget(int x, int y, int plane) {
        targetX = x;
        targetY = y;
        targetPlane = plane;
        targetLabel.setText("Target tile: " + x + ", " + y + ", plane " + plane);
        statusLabel.setText("Ready. Spawns are live runtime edits and are not saved to source data.");
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

    private void queue(String command) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        if (error != null) {
            statusLabel.setText(error);
            return;
        }
        statusLabel.setText("Spawn queued for tile " + targetX + ", " + targetY + ", plane " + targetPlane + ".");
    }
}
