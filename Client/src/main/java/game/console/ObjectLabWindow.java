package game.console;

import game.ClientConsoleBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;
import game.ObjectLabPreview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

/** Object Lab OBJLAB-1/2: capture, preview, tag and catalog object assets. */
public final class ObjectLabWindow {

    private static JFrame frame;
    private static ObjectLabWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JTextField idField = new JTextField();
    private final JLabel nameLabel = valueLabel();
    private final JLabel sourceTileLabel = valueLabel();
    private final JSpinner typeSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 22, 1));
    private final JSpinner rotationSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
    private final JSpinner offsetXSpinner = new JSpinner(new SpinnerNumberModel(3, -12, 12, 1));
    private final JSpinner offsetYSpinner = new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));
    private final JComboBox<TagChoice> tagBox = new JComboBox<TagChoice>(TagChoice.values());
    private final JTextArea notesArea = new JTextArea(3, 24);
    private final JLabel statusLabel = new JLabel("Capture a live object to begin.");

    private int sourceX;
    private int sourceY;
    private int sourcePlane;
    private String capturedName = "Object";
    private boolean hasSource;

    private ObjectLabWindow() {
        buildUi();
    }

    public static void open(DevTarget target) {
        ensureWindow();
        instance.capture(target);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private static void ensureWindow() {
        if (frame != null) {
            return;
        }
        instance = new ObjectLabWindow();
        frame = new JFrame("Matrix3 Object Lab");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(610, 620));
        frame.setSize(new Dimension(720, 760));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.WINDOW);
        JLabel title = new JLabel("OBJECT LAB");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Capture / direct-render preview / catalog");
        subtitle.setFont(ConsoleTheme.SMALL_FONT);
        subtitle.setForeground(ConsoleTheme.ACCENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(14));
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ConsoleTheme.WINDOW);
        content.add(createCaptureCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPreviewCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createCatalogCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        ConsoleTheme.styleScrollPane(scroll);
        root.add(scroll, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createCaptureCard() {
        JPanel card = ConsoleTheme.createCard("Captured object");
        card.add(Box.createVerticalStrut(9));
        JPanel identity = new JPanel(new GridLayout(3, 2, 8, 7));
        identity.setOpaque(false);
        addRow(identity, "Name", nameLabel);
        addRow(identity, "Source tile", sourceTileLabel);
        addRow(identity, "Object ID", idField);
        card.add(identity);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "The Dev target provides ID/name/tile only. Exact live type/rotation stay explicit controls "
                + "instead of being guessed; use Object Probe evidence when those values matter.",
                4));
        return card;
    }

    private JPanel createPreviewCard() {
        JPanel card = ConsoleTheme.createCard("Direct-render preview");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Preview uses Matrix3's normal object-model factory at Source Tile + Offset. "
                + "It is never registered as a world object, so it owns no collision or persistence.",
                4));
        card.add(Box.createVerticalStrut(8));

        JPanel values = new JPanel(new GridLayout(2, 4, 7, 7));
        values.setOpaque(false);
        values.add(label("Type"));
        values.add(typeSpinner);
        values.add(label("Rotation"));
        values.add(rotationSpinner);
        values.add(label("Offset X"));
        values.add(offsetXSpinner);
        values.add(label("Offset Y"));
        values.add(offsetYSpinner);
        card.add(values);
        card.add(Box.createVerticalStrut(9));

        JButton show = new JButton("Show / Refresh Preview");
        JButton hide = new JButton("Hide Preview");
        JButton rotateLeft = new JButton("Rotate -");
        JButton rotateRight = new JButton("Rotate +");
        styleButton(show);
        styleButton(hide);
        styleButton(rotateLeft);
        styleButton(rotateRight);

        show.addActionListener(e -> refreshPreview());
        hide.addActionListener(e -> {
            ObjectLabPreview.hide();
            statusLabel.setText("Object Lab preview hidden.");
        });
        rotateLeft.addActionListener(e -> rotatePreview(-1));
        rotateRight.addActionListener(e -> rotatePreview(1));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 7));
        buttons.setOpaque(false);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        buttons.add(show);
        buttons.add(hide);
        buttons.add(rotateLeft);
        buttons.add(rotateRight);
        card.add(buttons);

        typeSpinner.addChangeListener(e -> refreshPreviewIfActive());
        rotationSpinner.addChangeListener(e -> refreshPreviewIfActive());
        offsetXSpinner.addChangeListener(e -> refreshPreviewIfActive());
        offsetYSpinner.addChangeListener(e -> refreshPreviewIfActive());
        return card;
    }

    private JPanel createCatalogCard() {
        JPanel card = ConsoleTheme.createCard("Object catalog");
        card.add(Box.createVerticalStrut(9));
        tagBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        tagBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label("Tag"));
        card.add(Box.createVerticalStrut(4));
        card.add(tagBox);
        card.add(Box.createVerticalStrut(8));

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBackground(ConsoleTheme.CARD);
        notesArea.setForeground(ConsoleTheme.TEXT);
        notesArea.setCaretColor(ConsoleTheme.TEXT);
        notesArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 7, 6, 7)));
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        notesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        ConsoleTheme.styleScrollPane(notesScroll);
        card.add(label("Notes"));
        card.add(Box.createVerticalStrut(4));
        card.add(notesScroll);
        card.add(Box.createVerticalStrut(9));

        JButton save = new JButton("Save Captured Object");
        JButton list = new JButton("List Recent Catalog");
        styleButton(save);
        styleButton(list);
        save.addActionListener(e -> saveCatalog());
        list.addActionListener(e -> queue(
                "itembrowser objectlab list",
                "Object Lab catalog list queued. Check game chat."));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 0));
        buttons.setOpaque(false);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttons.add(save);
        buttons.add(list);
        card.add(buttons);
        return card;
    }

    private void capture(DevTarget target) {
        if (target == null || target.getType() != TargetType.OBJECT) {
            statusLabel.setText("Object Lab captures object targets only.");
            return;
        }
        capturedName = target.getName() == null ? "Object" : target.getName();
        sourceX = target.getWorldX();
        sourceY = target.getWorldY();
        sourcePlane = target.getPlane();
        hasSource = true;
        idField.setText(Integer.toString(target.getId()));
        nameLabel.setText(capturedName);
        sourceTileLabel.setText(sourceX + ", " + sourceY + ", " + sourcePlane);
        statusLabel.setText("Captured " + capturedName + " (" + target.getId()
                + "). Set type/rotation, then preview or save.");
    }

    private void refreshPreview() {
        Integer id = parseId();
        if (id == null || !hasSource) {
            statusLabel.setText("Capture a valid live object first.");
            return;
        }
        ObjectLabPreview.showPreview(capturedName, id.intValue(),
                number(typeSpinner), number(rotationSpinner),
                sourceX, sourceY, sourcePlane,
                number(offsetXSpinner), number(offsetYSpinner));
        statusLabel.setText(ObjectLabPreview.getStatus());
    }

    private void refreshPreviewIfActive() {
        if (!ObjectLabPreview.isActive()) {
            return;
        }
        Integer id = parseId();
        if (id == null || id.intValue() != ObjectLabPreview.getObjectId()) {
            return;
        }
        ObjectLabPreview.updateTypeRotation(number(typeSpinner), number(rotationSpinner));
        ObjectLabPreview.updateOffset(number(offsetXSpinner), number(offsetYSpinner));
        statusLabel.setText(ObjectLabPreview.getStatus());
    }

    private void rotatePreview(int delta) {
        rotationSpinner.setValue(Integer.valueOf((number(rotationSpinner) + delta) & 0x3));
        refreshPreview();
    }

    private void saveCatalog() {
        Integer id = parseId();
        if (id == null || !hasSource) {
            statusLabel.setText("Capture a valid object before saving it.");
            return;
        }
        TagChoice tag = (TagChoice) tagBox.getSelectedItem();
        String notes = notesArea.getText() == null ? "" : notesArea.getText().trim();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(notes.getBytes(StandardCharsets.UTF_8));
        if (encoded.length() == 0) {
            encoded = "-";
        }
        String command = "itembrowser objectlab save "
                + id + " " + number(typeSpinner) + " " + number(rotationSpinner)
                + " " + (tag == null ? "UNKNOWN" : tag.key)
                + " " + sourceX + " " + sourceY + " " + sourcePlane + " " + encoded;
        queue(command, "Object Lab catalog save queued for ID " + id + ".");
    }

    private Integer parseId() {
        try {
            int id = Integer.parseInt(idField.getText().trim());
            return id < 0 ? null : Integer.valueOf(id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void queue(String command, String success) {
        String error = ClientConsoleBridge.queueConsoleCommand(command);
        statusLabel.setText(error == null ? success : error);
    }

    private int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private void styleButton(JButton button) {
        ConsoleTheme.styleButton(button);
        button.setFocusable(false);
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        return label;
    }

    private void addRow(JPanel panel, String text, Component value) {
        panel.add(label(text));
        panel.add(value);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private enum TagChoice {
        UNKNOWN("Unknown", "UNKNOWN"),
        STRAIGHT_RAIL("Straight Rail", "STRAIGHT_RAIL"),
        CURVE("Curve", "CURVE"),
        SWITCH("Switch / Turnout", "SWITCH"),
        JUNCTION("Junction", "JUNCTION"),
        BUFFER("Buffer", "BUFFER"),
        CART("Cart", "CART"),
        LOADING_POINT("Loading Point", "LOADING_POINT"),
        SIGNAL("Signal", "SIGNAL"),
        FAVORITE("Favorite", "FAVORITE");

        private final String display;
        private final String key;

        TagChoice(String display, String key) {
            this.display = display;
            this.key = key;
        }

        @Override
        public String toString() {
            return display;
        }
    }
}
