package game.console;

import game.DevDefinitionBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;
import game.LiveModelEditorPreview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;

/**
 * Live Model Editor Bundle 1.
 *
 * This window edits a client-only runtime clone that is rendered inside the live
 * Matrix3 scene. JSON is the authoring/project format. No cache or server-world
 * bytes are written by this bundle.
 */
public final class LiveModelEditorWindow {

    private static final int PROJECT_VERSION = 1;
    private static final File PROJECT_DIR = new File("dev-model-projects");

    private static JFrame frame;
    private static LiveModelEditorWindow instance;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLabel nameLabel = valueLabel();
    private final JLabel objectIdLabel = valueLabel();
    private final JLabel modelIdsLabel = valueLabel();
    private final JLabel sourceTileLabel = valueLabel();
    private final JLabel statusLabel = new JLabel("Right-click an object -> Dev > Edit Model Live.");

    private final JSpinner typeSpinner = spinner(10, 0, 22, 1);
    private final JSpinner objectRotationSpinner = spinner(0, 0, 3, 1);
    private final JSpinner tileOffsetXSpinner = spinner(2, -12, 12, 1);
    private final JSpinner tileOffsetYSpinner = spinner(0, -12, 12, 1);

    private final JSpinner scaleXSpinner = spinner(100, 10, 400, 5);
    private final JSpinner scaleYSpinner = spinner(100, 10, 400, 5);
    private final JSpinner scaleZSpinner = spinner(100, 10, 400, 5);
    private final JSpinner moveXSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner moveYSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner moveZSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner yawSpinner = spinner(0, -359, 359, 5);

    private int objectId = -1;
    private String objectName = "Object";
    private int[] sourceModelIds = new int[0];
    private int sourceX;
    private int sourceY;
    private int sourcePlane;
    private boolean hasSource;
    private boolean suppressLiveRefresh;

    private LiveModelEditorWindow() {
        buildUi();
    }

    public static void open(DevTarget target) {
        if (target == null || target.getType() != TargetType.OBJECT) {
            return;
        }
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
        instance = new LiveModelEditorWindow();
        frame = new JFrame("Matrix3 Live Model Editor");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(instance.root);
        frame.setMinimumSize(new Dimension(650, 650));
        frame.setSize(new Dimension(760, 820));
        frame.setLocationByPlatform(true);
    }

    private void buildUi() {
        root.setBackground(ConsoleTheme.WINDOW);
        root.setBorder(ConsoleTheme.panelPadding(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ConsoleTheme.WINDOW);
        JLabel title = new JLabel("LIVE MODEL EDITOR - BUNDLE 1");
        title.setFont(ConsoleTheme.TITLE_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("In-world runtime clone / live transforms / JSON project");
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
        content.add(createTargetCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createPlacementCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createTransformCard());
        content.add(Box.createVerticalStrut(12));
        content.add(createProjectCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ConsoleTheme.styleScrollPane(scroll);
        root.add(scroll, BorderLayout.CENTER);

        statusLabel.setFont(ConsoleTheme.SMALL_FONT);
        statusLabel.setForeground(ConsoleTheme.MUTED_TEXT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);

        ChangeListener live = e -> refreshIfActive();
        typeSpinner.addChangeListener(live);
        objectRotationSpinner.addChangeListener(live);
        tileOffsetXSpinner.addChangeListener(live);
        tileOffsetYSpinner.addChangeListener(live);
        scaleXSpinner.addChangeListener(live);
        scaleYSpinner.addChangeListener(live);
        scaleZSpinner.addChangeListener(live);
        moveXSpinner.addChangeListener(live);
        moveYSpinner.addChangeListener(live);
        moveZSpinner.addChangeListener(live);
        yawSpinner.addChangeListener(live);
    }

    private JPanel createTargetCard() {
        JPanel card = ConsoleTheme.createCard("Runtime target");
        card.add(Box.createVerticalStrut(9));
        JPanel grid = new JPanel(new GridLayout(4, 2, 8, 7));
        grid.setOpaque(false);
        addRow(grid, "Object", nameLabel);
        addRow(grid, "Definition ID", objectIdLabel);
        addRow(grid, "Source model ID(s)", modelIdsLabel);
        addRow(grid, "Source tile", sourceTileLabel);
        card.add(grid);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Bundle 1 draws a private editable model clone inside the live client. "
                + "The original scene object remains authoritative and unchanged. "
                + "The default clone is two tiles east to avoid overlap; set preview offsets to 0/0 "
                + "to overlay it on the selected object.", 5));
        return card;
    }

    private JPanel createPlacementCard() {
        JPanel card = ConsoleTheme.createCard("Object render context");
        card.add(Box.createVerticalStrut(9));
        JPanel grid = new JPanel(new GridLayout(2, 4, 7, 7));
        grid.setOpaque(false);
        grid.add(label("Object type"));
        grid.add(typeSpinner);
        grid.add(label("Object rotation"));
        grid.add(objectRotationSpinner);
        grid.add(label("Preview tile X"));
        grid.add(tileOffsetXSpinner);
        grid.add(label("Preview tile Y"));
        grid.add(tileOffsetYSpinner);
        card.add(grid);
        card.add(Box.createVerticalStrut(8));
        card.add(ConsoleTheme.createWrappedText(
                "Type/rotation remain explicit because the current Dev target carries definition ID and tile, "
                + "not a proven live scene-shape/rotation value. Type 10 / rotation 0 is the safe default for "
                + "the Smelter proof; adjust if a different object needs another shape.", 5));
        return card;
    }

    private JPanel createTransformCard() {
        JPanel card = ConsoleTheme.createCard("Live model transforms");
        card.add(Box.createVerticalStrut(9));

        JPanel scale = new JPanel(new GridLayout(2, 3, 7, 7));
        scale.setOpaque(false);
        scale.add(label("Scale X %"));
        scale.add(label("Scale Y %"));
        scale.add(label("Scale Z %"));
        scale.add(scaleXSpinner);
        scale.add(scaleYSpinner);
        scale.add(scaleZSpinner);
        card.add(scale);

        card.add(Box.createVerticalStrut(9));
        JPanel move = new JPanel(new GridLayout(2, 4, 7, 7));
        move.setOpaque(false);
        move.add(label("Move X"));
        move.add(label("Move Y"));
        move.add(label("Move Z"));
        move.add(label("Yaw degrees"));
        move.add(moveXSpinner);
        move.add(moveYSpinner);
        move.add(moveZSpinner);
        move.add(yawSpinner);
        card.add(move);

        card.add(Box.createVerticalStrut(9));
        JButton show = button("Show / Refresh Runtime Clone");
        JButton hide = button("Hide Runtime Clone");
        JButton reset = button("Reset Transforms");
        show.addActionListener(e -> refreshPreview());
        hide.addActionListener(e -> {
            LiveModelEditorPreview.hide();
            statusLabel.setText("Runtime clone hidden. Source object was not changed.");
        });
        reset.addActionListener(e -> resetTransforms());

        JPanel actions = new JPanel(new GridLayout(1, 3, 7, 0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        actions.add(show);
        actions.add(hide);
        actions.add(reset);
        card.add(actions);
        return card;
    }

    private JPanel createProjectCard() {
        JPanel card = ConsoleTheme.createCard("JSON project");
        card.add(Box.createVerticalStrut(9));
        card.add(ConsoleTheme.createWrappedText(
                "Save stores the source object/model IDs plus editor transforms under dev-model-projects. "
                + "Load restores the authoring state and redraws the runtime clone. "
                + "This bundle never writes revision-830 cache bytes.", 5));
        card.add(Box.createVerticalStrut(9));

        JButton save = button("Save Project");
        JButton load = button("Load Project...");
        save.addActionListener(e -> saveProject());
        load.addActionListener(e -> loadProject());

        JPanel actions = new JPanel(new GridLayout(1, 2, 7, 0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        actions.add(save);
        actions.add(load);
        card.add(actions);
        return card;
    }

    private void capture(DevTarget target) {
        objectId = target.getId();
        objectName = target.getName() == null ? "Object" : target.getName();
        sourceModelIds = DevDefinitionBridge.getObjectModelIds(objectId);
        sourceX = target.getWorldX();
        sourceY = target.getWorldY();
        sourcePlane = target.getPlane();
        hasSource = objectId >= 0;

        suppressLiveRefresh = true;
        try {
            nameLabel.setText(objectName);
            objectIdLabel.setText(Integer.toString(objectId));
            modelIdsLabel.setText(joinIds(sourceModelIds));
            sourceTileLabel.setText(sourceX + ", " + sourceY + ", " + sourcePlane);
            resetEditorsOnly();
        } finally {
            suppressLiveRefresh = false;
        }

        refreshPreview();
        statusLabel.setText("Runtime clone opened for " + objectName + " (" + objectId + ")"
                + (sourceModelIds.length == 0 ? "." : " model=" + sourceModelIds[0] + "."));
    }

    private void refreshIfActive() {
        if (!suppressLiveRefresh && hasSource && LiveModelEditorPreview.isActive()
                && LiveModelEditorPreview.getObjectId() == objectId) {
            refreshPreview();
        }
    }

    private void refreshPreview() {
        if (!hasSource || objectId < 0) {
            statusLabel.setText("Capture a valid object target first.");
            return;
        }
        LiveModelEditorPreview.show(objectName, objectId,
                number(typeSpinner), number(objectRotationSpinner),
                sourceX, sourceY, sourcePlane,
                number(tileOffsetXSpinner), number(tileOffsetYSpinner),
                number(scaleXSpinner), number(scaleYSpinner), number(scaleZSpinner),
                number(moveXSpinner), number(moveYSpinner), number(moveZSpinner),
                number(yawSpinner));
        statusLabel.setText(LiveModelEditorPreview.getStatus());
    }

    private void resetTransforms() {
        suppressLiveRefresh = true;
        try {
            resetEditorsOnly();
        } finally {
            suppressLiveRefresh = false;
        }
        refreshPreview();
    }

    private void resetEditorsOnly() {
        typeSpinner.setValue(Integer.valueOf(10));
        objectRotationSpinner.setValue(Integer.valueOf(0));
        tileOffsetXSpinner.setValue(Integer.valueOf(2));
        tileOffsetYSpinner.setValue(Integer.valueOf(0));
        scaleXSpinner.setValue(Integer.valueOf(100));
        scaleYSpinner.setValue(Integer.valueOf(100));
        scaleZSpinner.setValue(Integer.valueOf(100));
        moveXSpinner.setValue(Integer.valueOf(0));
        moveYSpinner.setValue(Integer.valueOf(0));
        moveZSpinner.setValue(Integer.valueOf(0));
        yawSpinner.setValue(Integer.valueOf(0));
    }

    private void saveProject() {
        if (!hasSource) {
            statusLabel.setText("No live object target is loaded.");
            return;
        }
        try {
            if (!PROJECT_DIR.exists() && !PROJECT_DIR.mkdirs()) {
                throw new IllegalStateException("Could not create " + PROJECT_DIR.getPath());
            }
            int primaryModel = sourceModelIds.length == 0 ? -1 : sourceModelIds[0];
            File file = new File(PROJECT_DIR,
                    "object_" + objectId + "_model_" + primaryModel + ".json");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8));
            try {
                writer.write(toJson());
            } finally {
                writer.close();
            }
            statusLabel.setText("Saved JSON project: " + file.getPath());
        } catch (Exception ex) {
            statusLabel.setText("Save failed: " + rootMessage(ex));
        }
    }

    private void loadProject() {
        if (!PROJECT_DIR.exists()) {
            PROJECT_DIR.mkdirs();
        }
        JFileChooser chooser = new JFileChooser(PROJECT_DIR);
        chooser.setDialogTitle("Load Matrix3 live model project");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            String json = readFile(file);
            int version = readInt(json, "version", -1);
            if (version != PROJECT_VERSION) {
                throw new IllegalArgumentException("Unsupported project version " + version);
            }

            suppressLiveRefresh = true;
            try {
                objectId = readInt(json, "objectId", -1);
                objectName = readString(json, "objectName", "Object");
                sourceModelIds = readIntArray(json, "sourceModelIds");
                sourceX = readInt(json, "sourceX", 0);
                sourceY = readInt(json, "sourceY", 0);
                sourcePlane = readInt(json, "sourcePlane", 0);
                hasSource = objectId >= 0;

                typeSpinner.setValue(Integer.valueOf(readInt(json, "objectType", 10)));
                objectRotationSpinner.setValue(Integer.valueOf(readInt(json, "objectRotation", 0)));
                tileOffsetXSpinner.setValue(Integer.valueOf(readInt(json, "previewOffsetX", 2)));
                tileOffsetYSpinner.setValue(Integer.valueOf(readInt(json, "previewOffsetY", 0)));
                scaleXSpinner.setValue(Integer.valueOf(readInt(json, "scaleXPercent", 100)));
                scaleYSpinner.setValue(Integer.valueOf(readInt(json, "scaleYPercent", 100)));
                scaleZSpinner.setValue(Integer.valueOf(readInt(json, "scaleZPercent", 100)));
                moveXSpinner.setValue(Integer.valueOf(readInt(json, "translateX", 0)));
                moveYSpinner.setValue(Integer.valueOf(readInt(json, "translateY", 0)));
                moveZSpinner.setValue(Integer.valueOf(readInt(json, "translateZ", 0)));
                yawSpinner.setValue(Integer.valueOf(readInt(json, "yawDegrees", 0)));

                nameLabel.setText(objectName);
                objectIdLabel.setText(Integer.toString(objectId));
                modelIdsLabel.setText(joinIds(sourceModelIds));
                sourceTileLabel.setText(sourceX + ", " + sourceY + ", " + sourcePlane);
            } finally {
                suppressLiveRefresh = false;
            }

            refreshPreview();
            statusLabel.setText("Loaded JSON project: " + file.getPath());
        } catch (Exception ex) {
            statusLabel.setText("Load failed: " + rootMessage(ex));
        }
    }

    private String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"format\": \"matrix3-live-model-project\",\n");
        out.append("  \"version\": ").append(PROJECT_VERSION).append(",\n");
        out.append("  \"objectId\": ").append(objectId).append(",\n");
        out.append("  \"objectName\": \"").append(escapeJson(objectName)).append("\",\n");
        out.append("  \"sourceModelIds\": ").append(intArrayJson(sourceModelIds)).append(",\n");
        out.append("  \"sourceX\": ").append(sourceX).append(",\n");
        out.append("  \"sourceY\": ").append(sourceY).append(",\n");
        out.append("  \"sourcePlane\": ").append(sourcePlane).append(",\n");
        out.append("  \"objectType\": ").append(number(typeSpinner)).append(",\n");
        out.append("  \"objectRotation\": ").append(number(objectRotationSpinner)).append(",\n");
        out.append("  \"previewOffsetX\": ").append(number(tileOffsetXSpinner)).append(",\n");
        out.append("  \"previewOffsetY\": ").append(number(tileOffsetYSpinner)).append(",\n");
        out.append("  \"scaleXPercent\": ").append(number(scaleXSpinner)).append(",\n");
        out.append("  \"scaleYPercent\": ").append(number(scaleYSpinner)).append(",\n");
        out.append("  \"scaleZPercent\": ").append(number(scaleZSpinner)).append(",\n");
        out.append("  \"translateX\": ").append(number(moveXSpinner)).append(",\n");
        out.append("  \"translateY\": ").append(number(moveYSpinner)).append(",\n");
        out.append("  \"translateZ\": ").append(number(moveZSpinner)).append(",\n");
        out.append("  \"yawDegrees\": ").append(number(yawSpinner)).append("\n");
        out.append("}\n");
        return out.toString();
    }

    private static String readFile(File file) throws Exception {
        StringBuilder out = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return out.toString();
    }

    private static int readInt(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String readString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : fallback;
    }

    private static int[] readIntArray(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!matcher.find()) {
            return new int[0];
        }
        String body = matcher.group(1).trim();
        if (body.length() == 0) {
            return new int[0];
        }
        String[] parts = body.split(",");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i].trim());
        }
        return values;
    }

    private static String intArrayJson(int[] values) {
        StringBuilder out = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(", ");
                out.append(values[i]);
            }
        }
        return out.append(']').toString();
    }

    private static String joinIds(int[] values) {
        if (values == null || values.length == 0) {
            return "None / unresolved";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(", ");
            out.append(values[i]);
        }
        return out.toString();
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        ConsoleTheme.styleButton(button);
        button.setFocusable(false);
        return button;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);
        return label;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.BODY_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private static void addRow(JPanel panel, String name, Component value) {
        panel.add(label(name));
        panel.add(value);
    }
}
