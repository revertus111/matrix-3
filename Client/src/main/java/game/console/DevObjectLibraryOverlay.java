package game.console;

import game.Class584;
import game.DevModeBridge;
import game.DevObjectLibrary;
import game.DevObjectLibrary.SavedObject;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * Compact in-client browser for object-definition snapshots saved from Live Place.
 */
public final class DevObjectLibraryOverlay {

    private static final int WIDTH = 430;
    private static final int HEIGHT = 500;
    private static final int MARGIN = 12;

    private static JWindow window;
    private static Window owner;

    private static final JPanel root = new JPanel(new BorderLayout());
    private static final DefaultListModel<SavedObject> model = new DefaultListModel<SavedObject>();
    private static final JList<SavedObject> list = new JList<SavedObject>(model);

    private static final JLabel nameLabel = valueLabel();
    private static final JLabel idLabel = valueLabel();
    private static final JLabel typeLabel = valueLabel();
    private static final JLabel typesLabel = valueLabel();
    private static final JLabel modelsLabel = valueLabel();
    private static final JLabel animationsLabel = valueLabel();
    private static final JLabel sizeLabel = valueLabel();
    private static final JLabel tileLabel = valueLabel();
    private static final JLabel statusLabel = new JLabel("Right-click a Live Place preview and choose Save Object Definition.");

    private static boolean built;

    private DevObjectLibraryOverlay() {
    }

    public static void open(final int preferredObjectId) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    open(preferredObjectId);
                }
            });
            return;
        }
        ensureWindow();
        if (window == null) {
            return;
        }
        reload(preferredObjectId);
        positionWindow();
        window.setVisible(true);
        window.toFront();
        list.requestFocusInWindow();
    }

    public static void close() {
        if (window != null) {
            window.setVisible(false);
        }
        if (Class584.aCanvas7745 != null) {
            Class584.aCanvas7745.requestFocusInWindow();
        }
    }

    private static void ensureWindow() {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null) {
            return;
        }
        Window newOwner = SwingUtilities.getWindowAncestor(canvas);
        if (newOwner == null) {
            return;
        }
        if (!built) {
            buildUi();
            built = true;
        }
        if (window != null && owner == newOwner) {
            return;
        }
        if (window != null) {
            window.dispose();
        }
        owner = newOwner;
        window = new JWindow(owner);
        window.setFocusableWindowState(true);
        window.setAutoRequestFocus(true);
        window.setContentPane(root);
        window.setSize(WIDTH, HEIGHT);
    }

    private static void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.ACCENT_DARK),
                ConsoleTheme.panelPadding(10, 10, 10, 10)));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("SAVED OBJECT DEFINITIONS");
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);

        JLabel hint = new JLabel("F8 editor   Enter place   Delete remove   Esc close");
        hint.setFont(ConsoleTheme.SMALL_FONT);
        hint.setForeground(ConsoleTheme.MUTED_TEXT);

        header.add(title);
        header.add(Box.createVerticalStrut(3));
        header.add(hint);
        header.add(Box.createVerticalStrut(8));
        root.add(header, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(ConsoleTheme.SMALL_FONT);
        list.setBackground(ConsoleTheme.WINDOW);
        list.setForeground(ConsoleTheme.TEXT);
        list.setSelectionBackground(ConsoleTheme.ACCENT_DARK);
        list.setSelectionForeground(ConsoleTheme.TEXT);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetails(list.getSelectedValue());
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(ConsoleTheme.ACCENT_DARK));

        JPanel details = new JPanel(new GridLayout(8, 2, 7, 5));
        details.setOpaque(false);
        addRow(details, "Name", nameLabel);
        addRow(details, "Definition ID", idLabel);
        addRow(details, "Saved type", typeLabel);
        addRow(details, "Valid types", typesLabel);
        addRow(details, "Model IDs", modelsLabel);
        addRow(details, "Animation IDs", animationsLabel);
        addRow(details, "Size", sizeLabel);
        addRow(details, "Saved tile", tileLabel);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.add(scroll);
        center.add(Box.createVerticalStrut(8));
        center.add(details);
        root.add(center, BorderLayout.CENTER);

        JButton place = new JButton("Place");
        JButton editor = new JButton("F8 - Live Model Editor");
        JButton remove = new JButton("Remove");
        JButton close = new JButton("Close");
        ConsoleTheme.styleButton(place);
        ConsoleTheme.styleButton(editor);
        ConsoleTheme.styleButton(remove);
        ConsoleTheme.styleButton(close);

        place.addActionListener(e -> placeSelected());
        editor.addActionListener(e -> openSelectedInEditor());
        remove.addActionListener(e -> removeSelected());
        close.addActionListener(e -> close());

        JPanel buttons = new JPanel(new GridLayout(1, 4, 6, 0));
        buttons.setOpaque(false);
        buttons.add(place);
        buttons.add(editor);
        buttons.add(remove);
        buttons.add(close);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        south.add(Box.createVerticalStrut(8));
        south.add(statusLabel);
        south.add(Box.createVerticalStrut(6));
        south.add(buttons);
        root.add(south, BorderLayout.SOUTH);

        bind(KeyEvent.VK_F8, "editor", new Runnable() {
            @Override
            public void run() {
                openSelectedInEditor();
            }
        });
        bind(KeyEvent.VK_ENTER, "place", new Runnable() {
            @Override
            public void run() {
                placeSelected();
            }
        });
        bind(KeyEvent.VK_DELETE, "remove", new Runnable() {
            @Override
            public void run() {
                removeSelected();
            }
        });
        bind(KeyEvent.VK_ESCAPE, "close", new Runnable() {
            @Override
            public void run() {
                close();
            }
        });
    }

    private static void bind(int keyCode, String name, final Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static void reload(int preferredObjectId) {
        model.clear();
        List<SavedObject> entries = DevObjectLibrary.load();
        int preferredIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            SavedObject entry = entries.get(i);
            model.addElement(entry);
            if (entry.getId() == preferredObjectId) {
                preferredIndex = i;
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(preferredIndex >= 0 ? preferredIndex : 0);
        } else {
            updateDetails(null);
            statusLabel.setText("No saved definitions yet.");
        }
    }

    private static void placeSelected() {
        SavedObject entry = list.getSelectedValue();
        if (entry == null) {
            statusLabel.setText("Select a saved object first.");
            return;
        }
        String result = DevModeBridge.armLiveObjectPlacementAt(
                entry.getId(), entry.getRotation(),
                entry.getWorldX(), entry.getWorldY(), entry.getPlane());
        statusLabel.setText(result);
        close();
    }

    private static void openSelectedInEditor() {
        SavedObject entry = list.getSelectedValue();
        if (entry == null) {
            statusLabel.setText("Select a saved object first.");
            return;
        }
        if (!DevModeBridge.openSavedObjectInLiveModelEditor(
                entry.getId(), entry.getWorldX(), entry.getWorldY(), entry.getPlane())) {
            statusLabel.setText("Live Model Editor could not open that saved definition.");
            return;
        }
        if (window != null) {
            window.setVisible(false);
        }
    }

    private static void removeSelected() {
        SavedObject entry = list.getSelectedValue();
        if (entry == null) {
            statusLabel.setText("Select a saved object first.");
            return;
        }
        int removedId = entry.getId();
        if (DevObjectLibrary.remove(removedId)) {
            reload(-1);
            statusLabel.setText("Removed object #" + removedId + " from the saved library.");
        }
    }

    private static void updateDetails(SavedObject entry) {
        if (entry == null) {
            set(nameLabel, "-");
            set(idLabel, "-");
            set(typeLabel, "-");
            set(typesLabel, "-");
            set(modelsLabel, "-");
            set(animationsLabel, "-");
            set(sizeLabel, "-");
            set(tileLabel, "-");
            return;
        }
        set(nameLabel, entry.getName());
        set(idLabel, Integer.toString(entry.getId()));
        set(typeLabel, Integer.toString(entry.getSelectedType()));
        set(typesLabel, join(entry.getTypes()));
        set(modelsLabel, join(entry.getModelIds()));
        set(animationsLabel, join(entry.getAnimationIds()));
        set(sizeLabel, entry.getSizeX() + " x " + entry.getSizeY());
        set(tileLabel, entry.getWorldX() + ", " + entry.getWorldY() + ", " + entry.getPlane());
        statusLabel.setText("Saved definition snapshot. F8 opens this entry in Live Model Editor.");
    }

    private static void addRow(JPanel panel, String labelText, JLabel value) {
        JLabel label = new JLabel(labelText);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        panel.add(label);
        panel.add(value);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private static void set(JLabel label, String text) {
        label.setText(text == null || text.length() == 0 ? "-" : text);
        label.setToolTipText(label.getText());
    }

    private static String join(int[] values) {
        if (values == null || values.length == 0) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        int count = Math.min(values.length, 8);
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(", ");
            out.append(values[i]);
        }
        if (values.length > count) {
            out.append(" +").append(values.length - count);
        }
        return out.toString();
    }

    private static void positionWindow() {
        Canvas canvas = Class584.aCanvas7745;
        if (window == null || canvas == null) {
            return;
        }
        try {
            Point screen = canvas.getLocationOnScreen();
            int height = Math.min(HEIGHT, Math.max(360, canvas.getHeight() - MARGIN * 2));
            window.setBounds(screen.x + MARGIN, screen.y + MARGIN, WIDTH, height);
        } catch (IllegalComponentStateException ex) {
            // The overlay will be positioned on the next successful open.
        }
    }
}
