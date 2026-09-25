package game.console;

import game.Class584;
import game.DevDefinitionBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;
import game.LiveModelEditorPreview;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;

/**
 * Matrix3 Live Model Editor in-client overlay.
 *
 * The overlay is an owned JWindow positioned over the heavyweight game Canvas,
 * matching the proven Construction palette approach. It keeps model editing in
 * the same visual workspace as the live world while the actual preview remains
 * rendered by LiveModelEditorPreview inside Matrix3's scene.
 */
public final class LiveModelEditorWindow {

    private static final int PROJECT_VERSION = 2;
    private static final File PROJECT_DIR = new File("dev-model-projects");

    private static final int OVERLAY_WIDTH = 438;
    private static final int OVERLAY_HEIGHT = 690;
    private static final int OVERLAY_MARGIN = 10;
    private static final int OVERLAY_TOP = 48;

    private static final Color RS_BG = new Color(25, 22, 18);
    private static final Color RS_PANEL = new Color(42, 36, 29);
    private static final Color RS_PANEL_2 = new Color(51, 43, 34);
    private static final Color RS_INPUT = new Color(20, 18, 15);
    private static final Color RS_BORDER = new Color(117, 92, 54);
    private static final Color RS_GOLD = new Color(214, 176, 92);
    private static final Color RS_GOLD_DIM = new Color(151, 120, 68);
    private static final Color RS_TEXT = new Color(236, 226, 204);
    private static final Color RS_MUTED = new Color(170, 156, 130);
    private static final Color RS_SELECTED = new Color(91, 69, 39);
    private static final Color RS_HOVER = new Color(67, 56, 42);
    private static final Color RS_DANGER = new Color(122, 58, 44);

    private static final Font RS_TITLE_FONT = new Font("Serif", Font.BOLD, 16);
    private static final Font RS_SECTION_FONT = new Font("Serif", Font.BOLD, 13);
    private static final Font RS_BODY_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font RS_SMALL_FONT = new Font("SansSerif", Font.PLAIN, 11);

    private static JWindow overlayWindow;
    private static Window overlayOwner;
    private static LiveModelEditorWindow instance;
    private static Timer overlayTimer;
    private static boolean manuallyPositioned;
    private static boolean inputGateInstalled;
    private static int manualLocalX;
    private static int manualLocalY;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLabel targetLabel = rsValue("-");
    private final JLabel sourceLabel = rsMuted("-");
    private final JLabel statusLabel = rsMuted("Right-click an object -> Dev > Edit Model Live.");
    private final JLabel partStatusLabel = rsGold("Mesh parts not ready.");

    private final DefaultListModel<String> partListModel = new DefaultListModel<String>();
    private final JList<String> partList = new JList<String>(partListModel);

    private final JSpinner typeSpinner = spinner(10, 0, 22, 1);
    private final JSpinner objectRotationSpinner = spinner(0, 0, 3, 1);
    private final JSpinner tileOffsetXSpinner = spinner(0, -12, 12, 1);
    private final JSpinner tileOffsetYSpinner = spinner(0, -12, 12, 1);

    private final JSpinner scaleXSpinner = spinner(100, 10, 400, 5);
    private final JSpinner scaleYSpinner = spinner(100, 10, 400, 5);
    private final JSpinner scaleZSpinner = spinner(100, 10, 400, 5);
    private final JSpinner moveXSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner moveYSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner moveZSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner yawSpinner = spinner(0, -359, 359, 5);

    private final JSpinner partScaleXSpinner = spinner(100, 10, 400, 5);
    private final JSpinner partScaleYSpinner = spinner(100, 10, 400, 5);
    private final JSpinner partScaleZSpinner = spinner(100, 10, 400, 5);
    private final JSpinner partMoveXSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner partMoveYSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner partMoveZSpinner = spinner(0, -4096, 4096, 16);
    private final JSpinner partYawSpinner = spinner(0, -359, 359, 5);

    private int objectId = -1;
    private String objectName = "Object";
    private int[] sourceModelIds = new int[0];
    private int sourceX;
    private int sourceY;
    private int sourcePlane;
    private boolean hasSource;
    private boolean suppressLiveRefresh;
    private boolean suppressPartRefresh;
    private int hoveredListIndex = -1;

    private LiveModelEditorWindow() {
        buildUi();
    }

    public static void open(final DevTarget target) {
        if (target == null || target.getType() != TargetType.OBJECT) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    open(target);
                }
            });
            return;
        }
        ensureOverlayWindow();
        if (instance == null || overlayWindow == null) {
            return;
        }
        installInputGate();
        if (instance.matchesTarget(target)) {
            instance.resumeSession();
        } else {
            instance.capture(target);
        }
        refreshOverlayBounds();
        overlayWindow.setVisible(true);
        overlayWindow.toFront();
        startOverlayTimer();
    }

    private static void ensureOverlayWindow() {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(canvas);
        if (owner == null) {
            return;
        }
        if (instance == null) {
            instance = new LiveModelEditorWindow();
        }
        if (overlayWindow != null && overlayOwner == owner) {
            return;
        }
        if (overlayWindow != null) {
            overlayWindow.dispose();
        }

        overlayOwner = owner;
        overlayWindow = new JWindow(owner);
        overlayWindow.setFocusableWindowState(true);
        overlayWindow.setAutoRequestFocus(false);
        overlayWindow.getContentPane().setLayout(new BorderLayout());
        overlayWindow.getContentPane().add(instance.root, BorderLayout.CENTER);
        refreshOverlayBounds();
    }

    private static void startOverlayTimer() {
        if (overlayTimer == null) {
            overlayTimer = new Timer(100, e -> refreshOverlayBounds());
            overlayTimer.setCoalesce(true);
        }
        if (!overlayTimer.isRunning()) {
            overlayTimer.start();
        }
    }

    private static void refreshOverlayBounds() {
        if (overlayWindow == null) {
            return;
        }
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            overlayWindow.setVisible(false);
            return;
        }

        Point screen;
        try {
            screen = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }

        int width = Math.min(OVERLAY_WIDTH, Math.max(280, canvas.getWidth() - 20));
        int height = Math.min(OVERLAY_HEIGHT, Math.max(420, canvas.getHeight() - 20));

        int localX;
        int localY;
        if (manuallyPositioned) {
            localX = clamp(manualLocalX, 0, Math.max(0, canvas.getWidth() - width));
            localY = clamp(manualLocalY, 0, Math.max(0, canvas.getHeight() - height));
        } else {
            localX = Math.max(0, canvas.getWidth() - width - OVERLAY_MARGIN);
            localY = clamp(OVERLAY_TOP, 0, Math.max(0, canvas.getHeight() - height));
        }

        Rectangle desired = new Rectangle(screen.x + localX, screen.y + localY, width, height);
        if (!desired.equals(overlayWindow.getBounds())) {
            overlayWindow.setBounds(desired);
        }
    }

    private void buildUi() {
        root.setBackground(RS_BG);
        root.setBorder(BorderFactory.createLineBorder(RS_BORDER, 2));
        root.add(createTitleBar(), BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(RS_BG);
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        center.add(createTargetStrip());
        center.add(Box.createVerticalStrut(7));
        center.add(createPartsCard());
        center.add(Box.createVerticalStrut(7));
        center.add(createGlobalCard());
        center.add(Box.createVerticalStrut(7));
        center.add(createProjectStrip());
        root.add(center, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 9, 7, 9));
        root.add(statusLabel, BorderLayout.SOUTH);

        installListeners();
    }

    private JPanel createTitleBar() {
        final JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(RS_PANEL_2);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, RS_BORDER));
        bar.setPreferredSize(new Dimension(OVERLAY_WIDTH, 42));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBorder(BorderFactory.createEmptyBorder(5, 9, 4, 0));

        JLabel title = new JLabel("LIVE MODEL EDITOR");
        title.setFont(RS_TITLE_FONT);
        title.setForeground(RS_GOLD);
        JLabel subtitle = new JLabel("Mesh Parts / Live Scene");
        subtitle.setFont(RS_SMALL_FONT);
        subtitle.setForeground(RS_MUTED);
        text.add(title);
        text.add(subtitle);
        bar.add(text, BorderLayout.CENTER);

        JButton close = rsButton("X");
        close.setPreferredSize(new Dimension(38, 32));
        close.addActionListener(e -> closeEditorSession());
        JPanel closeWrap = new JPanel(new BorderLayout());
        closeWrap.setOpaque(false);
        closeWrap.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 6));
        closeWrap.add(close, BorderLayout.CENTER);
        bar.add(closeWrap, BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            private int startX;
            private int startY;
            private int windowX;
            private int windowY;

            @Override
            public void mousePressed(MouseEvent e) {
                if (overlayWindow == null) return;
                startX = e.getXOnScreen();
                startY = e.getYOnScreen();
                windowX = overlayWindow.getX();
                windowY = overlayWindow.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (overlayWindow == null) return;
                int nx = windowX + e.getXOnScreen() - startX;
                int ny = windowY + e.getYOnScreen() - startY;
                overlayWindow.setLocation(nx, ny);
                rememberManualOverlayPosition();
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        text.addMouseListener(drag);
        text.addMouseMotionListener(drag);
        return bar;
    }

    private JPanel createTargetStrip() {
        JPanel card = rsCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        targetLabel.setFont(RS_SECTION_FONT);
        card.add(targetLabel);
        card.add(Box.createVerticalStrut(2));
        card.add(sourceLabel);
        return card;
    }

    private JPanel createPartsCard() {
        JPanel card = rsCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JPanel heading = new JPanel(new BorderLayout(6, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel("MODEL PARTS");
        title.setFont(RS_SECTION_FONT);
        title.setForeground(RS_GOLD);
        heading.add(title, BorderLayout.WEST);
        heading.add(partStatusLabel, BorderLayout.EAST);
        card.add(heading);
        card.add(Box.createVerticalStrut(6));

        partList.setVisibleRowCount(8);
        partList.setFixedCellHeight(25);
        partList.setFont(RS_BODY_FONT);
        partList.setForeground(RS_TEXT);
        partList.setBackground(RS_INPUT);
        partList.setSelectionForeground(RS_GOLD);
        partList.setSelectionBackground(RS_SELECTED);
        partList.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        partList.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, false);
                label.setFont(RS_BODY_FONT);
                label.setForeground(isSelected ? RS_GOLD : RS_TEXT);
                label.setBackground(isSelected ? RS_SELECTED
                        : index == hoveredListIndex ? RS_HOVER : RS_INPUT);
                label.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
                return label;
            }
        });

        JScrollPane scroll = new JScrollPane(partList);
        scroll.setBorder(BorderFactory.createLineBorder(RS_BORDER));
        scroll.setPreferredSize(new Dimension(400, 208));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 208));
        scroll.getViewport().setBackground(RS_INPUT);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        card.add(scroll);

        card.add(Box.createVerticalStrut(7));
        card.add(createSpinnerGrid(
                new String[] { "SCALE X", "SCALE Y", "SCALE Z" },
                new JSpinner[] { partScaleXSpinner, partScaleYSpinner, partScaleZSpinner }));
        card.add(Box.createVerticalStrut(5));
        card.add(createSpinnerGrid(
                new String[] { "MOVE X", "MOVE Y", "MOVE Z", "YAW" },
                new JSpinner[] { partMoveXSpinner, partMoveYSpinner, partMoveZSpinner, partYawSpinner }));

        card.add(Box.createVerticalStrut(7));
        JPanel row1 = actionRow(3);
        JButton rebuild = rsButton("Rebuild");
        JButton isolate = rsButton("Isolate");
        JButton showAll = rsButton("Show All");
        row1.add(rebuild);
        row1.add(isolate);
        row1.add(showAll);
        card.add(row1);

        card.add(Box.createVerticalStrut(5));
        JPanel row2 = actionRow(4);
        JButton hide = rsButton("Hide");
        JButton duplicate = rsButton("Duplicate");
        JButton delete = rsButton("Delete");
        JButton undo = rsButton("Undo");
        delete.setBackground(RS_DANGER);
        row2.add(hide);
        row2.add(duplicate);
        row2.add(delete);
        row2.add(undo);
        card.add(row2);

        rebuild.addActionListener(e -> initializeParts());
        isolate.addActionListener(e -> {
            LiveModelEditorPreview.toggleIsolatePart();
            refreshPartList();
            partStatusLabel.setText(LiveModelEditorPreview.isPartIsolated() ? "ISOLATE ON" : "READY");
        });
        showAll.addActionListener(e -> {
            LiveModelEditorPreview.showAllParts();
            refreshPartList();
        });
        hide.addActionListener(e -> {
            if (LiveModelEditorPreview.toggleSelectedPartHidden()) {
                refreshPartList();
                statusLabel.setText("Toggled selected part visibility.");
            }
        });
        duplicate.addActionListener(e -> {
            if (LiveModelEditorPreview.duplicateSelectedPart()) {
                refreshPartList();
                loadSelectedPartEditors();
                statusLabel.setText("Duplicated selected part (+128 model X).");
            }
        });
        delete.addActionListener(e -> {
            if (LiveModelEditorPreview.deleteSelectedPart()) {
                refreshPartList();
                statusLabel.setText("Deleted selected project part. Ctrl+Z restores it.");
            }
        });
        undo.addActionListener(e -> undoPartEdit());
        return card;
    }

    private JPanel createGlobalCard() {
        JPanel card = rsCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("WHOLE CLONE");
        title.setFont(RS_SECTION_FONT);
        title.setForeground(RS_GOLD);
        heading.add(title, BorderLayout.WEST);
        JLabel context = new JLabel("type / rot / tile offset");
        context.setFont(RS_SMALL_FONT);
        context.setForeground(RS_MUTED);
        heading.add(context, BorderLayout.EAST);
        card.add(heading);
        card.add(Box.createVerticalStrut(5));

        card.add(createSpinnerGrid(
                new String[] { "TYPE", "ROT", "TILE X", "TILE Y" },
                new JSpinner[] { typeSpinner, objectRotationSpinner, tileOffsetXSpinner, tileOffsetYSpinner }));
        card.add(Box.createVerticalStrut(5));
        card.add(createSpinnerGrid(
                new String[] { "SCALE X", "SCALE Y", "SCALE Z", "YAW" },
                new JSpinner[] { scaleXSpinner, scaleYSpinner, scaleZSpinner, yawSpinner }));
        card.add(Box.createVerticalStrut(5));
        card.add(createSpinnerGrid(
                new String[] { "MOVE X", "MOVE Y", "MOVE Z" },
                new JSpinner[] { moveXSpinner, moveYSpinner, moveZSpinner }));

        card.add(Box.createVerticalStrut(6));
        JPanel actions = actionRow(3);
        JButton show = rsButton("Refresh");
        JButton hide = rsButton("Exit Edit");
        JButton reset = rsButton("Reset");
        actions.add(show);
        actions.add(hide);
        actions.add(reset);
        card.add(actions);

        show.addActionListener(e -> refreshPreview());
        hide.addActionListener(e -> closeEditorSession());
        reset.addActionListener(e -> resetTransforms());
        return card;
    }

    private JPanel createProjectStrip() {
        JPanel card = rsCard();
        card.setLayout(new BorderLayout(6, 0));

        JLabel label = new JLabel("PROJECT JSON v2");
        label.setFont(RS_SECTION_FONT);
        label.setForeground(RS_GOLD);
        card.add(label, BorderLayout.WEST);

        JPanel actions = new JPanel(new GridLayout(1, 2, 5, 0));
        actions.setOpaque(false);
        JButton save = rsButton("Save");
        JButton load = rsButton("Load");
        save.addActionListener(e -> saveProject());
        load.addActionListener(e -> loadProject());
        actions.add(save);
        actions.add(load);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private JPanel createSpinnerGrid(String[] names, JSpinner[] spinners) {
        JPanel panel = new JPanel(new GridLayout(2, names.length, 4, 3));
        panel.setOpaque(false);
        for (String name : names) {
            JLabel label = new JLabel(name);
            label.setFont(RS_SMALL_FONT);
            label.setForeground(RS_MUTED);
            panel.add(label);
        }
        for (JSpinner spinner : spinners) {
            styleSpinner(spinner);
            panel.add(spinner);
        }
        return panel;
    }

    private void installListeners() {
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

        ChangeListener partLive = e -> updateSelectedPartTransform();
        partScaleXSpinner.addChangeListener(partLive);
        partScaleYSpinner.addChangeListener(partLive);
        partScaleZSpinner.addChangeListener(partLive);
        partMoveXSpinner.addChangeListener(partLive);
        partMoveYSpinner.addChangeListener(partLive);
        partMoveZSpinner.addChangeListener(partLive);
        partYawSpinner.addChangeListener(partLive);

        partList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        partList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressPartRefresh) {
                int index = partList.getSelectedIndex();
                if (index >= 0 && LiveModelEditorPreview.selectPart(index)) {
                    loadSelectedPartEditors();
                    statusLabel.setText("Selected Part " + index + ".");
                }
            }
        });

        partList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = partList.locationToIndex(e.getPoint());
                Rectangle bounds = index >= 0 ? partList.getCellBounds(index, index) : null;
                if (bounds == null || !bounds.contains(e.getPoint())) {
                    index = -1;
                }
                setHoveredPart(index);
            }
        });
        partList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                setHoveredPart(-1);
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "liveModelUndo");
        root.getActionMap().put("liveModelUndo", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                undoPartEdit();
            }
        });
    }

    private void setHoveredPart(int index) {
        if (hoveredListIndex == index) {
            return;
        }
        hoveredListIndex = index;
        if (index >= 0) {
            LiveModelEditorPreview.previewPart(index);
            partStatusLabel.setText("HOVER Part " + index);
        } else {
            LiveModelEditorPreview.clearPartPreview();
            partStatusLabel.setText(LiveModelEditorPreview.isPartIsolated() ? "ISOLATE ON" : "READY");
        }
        partList.repaint();
    }

    private void undoPartEdit() {
        if (LiveModelEditorPreview.undoPartEdit()) {
            refreshPartList();
            loadSelectedPartEditors();
            statusLabel.setText("Undo: restored previous part edit.");
        } else {
            statusLabel.setText("Nothing to undo.");
        }
    }

    private boolean matchesTarget(DevTarget target) {
        return target != null && hasSource
                && objectId == target.getId()
                && sourceX == target.getWorldX()
                && sourceY == target.getWorldY()
                && sourcePlane == target.getPlane();
    }

    private void resumeSession() {
        hoveredListIndex = -1;
        LiveModelEditorPreview.clearPartPreview();
        refreshPreview();
        refreshPartList();
        loadSelectedPartEditors();
        updateTargetLabels();
        statusLabel.setText("Resumed existing edit session for " + objectName + " #" + objectId + ".");
    }

    private static void closeEditorSession() {
        LiveModelEditorPreview.clearPartPreview();
        LiveModelEditorPreview.hide();
        if (instance != null) {
            instance.hoveredListIndex = -1;
            instance.statusLabel.setText("Edit session paused. Original scene object restored.");
        }
        if (overlayWindow != null) {
            overlayWindow.setVisible(false);
        }
    }

    private static synchronized void installInputGate() {
        if (inputGateInstalled) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (!LiveModelEditorPreview.isEditSessionActive()) {
                    return;
                }
                if (event instanceof MouseEvent) {
                    MouseEvent mouse = (MouseEvent) event;
                    Canvas canvas = Class584.aCanvas7745;
                    if (canvas != null && mouse.getSource() == canvas
                            && mouse.getButton() == MouseEvent.BUTTON1
                            && (mouse.getID() == MouseEvent.MOUSE_PRESSED
                                    || mouse.getID() == MouseEvent.MOUSE_RELEASED
                                    || mouse.getID() == MouseEvent.MOUSE_CLICKED)) {
                        mouse.consume();
                    }
                    return;
                }
                if (event instanceof KeyEvent) {
                    KeyEvent key = (KeyEvent) event;
                    if (key.getID() == KeyEvent.KEY_PRESSED && key.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        key.consume();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                closeEditorSession();
                            }
                        });
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        inputGateInstalled = true;
    }

    private void capture(DevTarget target) {
        objectId = target.getId();
        objectName = target.getName() == null ? "Object" : target.getName();
        sourceModelIds = DevDefinitionBridge.getObjectModelIds(objectId);
        sourceX = target.getWorldX();
        sourceY = target.getWorldY();
        sourcePlane = target.getPlane();
        hasSource = objectId >= 0;

        hoveredListIndex = -1;
        LiveModelEditorPreview.clearPartPreview();

        suppressLiveRefresh = true;
        try {
            resetEditorsOnly();
        } finally {
            suppressLiveRefresh = false;
        }

        updateTargetLabels();
        refreshPreview();
        initializeParts();
        statusLabel.setText("Live editor ready for " + objectName + " #" + objectId + ".");
    }

    private void updateTargetLabels() {
        targetLabel.setText(objectName + "   #" + objectId
                + "   model " + joinIds(sourceModelIds));
        sourceLabel.setText("World " + sourceX + ", " + sourceY + ", " + sourcePlane
                + "   |   SOURCE REPLACED   |   hover = preview   |   click = select");
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

    private void initializeParts() {
        int count = LiveModelEditorPreview.initializeParts();
        refreshPartList();
        if (count > 0 && LiveModelEditorPreview.getSelectedPart() < 0) {
            suppressPartRefresh = true;
            try {
                partList.setSelectedIndex(0);
                LiveModelEditorPreview.selectPart(0);
            } finally {
                suppressPartRefresh = false;
            }
            loadSelectedPartEditors();
        }
        partStatusLabel.setText(count > 0 ? count + " PARTS" : "NO PARTS");
    }

    private void refreshPartList() {
        String[] labels = LiveModelEditorPreview.getPartLabels();
        int selected = LiveModelEditorPreview.getSelectedPart();
        suppressPartRefresh = true;
        try {
            partListModel.clear();
            for (String label : labels) {
                partListModel.addElement(label);
            }
            if (selected >= 0 && selected < labels.length) {
                partList.setSelectedIndex(selected);
                partList.ensureIndexIsVisible(selected);
            } else {
                partList.clearSelection();
            }
        } finally {
            suppressPartRefresh = false;
        }
        if (labels.length == 0) {
            partStatusLabel.setText("NO PARTS");
        } else {
            partStatusLabel.setText(labels.length + " PARTS"
                    + (LiveModelEditorPreview.isPartIsolated() ? " / ISOLATE" : ""));
        }
        partList.repaint();
    }

    private void loadSelectedPartEditors() {
        int[] transform = LiveModelEditorPreview.getSelectedPartTransform();
        suppressPartRefresh = true;
        try {
            partScaleXSpinner.setValue(Integer.valueOf(transform[0]));
            partScaleYSpinner.setValue(Integer.valueOf(transform[1]));
            partScaleZSpinner.setValue(Integer.valueOf(transform[2]));
            partMoveXSpinner.setValue(Integer.valueOf(transform[3]));
            partMoveYSpinner.setValue(Integer.valueOf(transform[4]));
            partMoveZSpinner.setValue(Integer.valueOf(transform[5]));
            partYawSpinner.setValue(Integer.valueOf(transform[6]));
        } finally {
            suppressPartRefresh = false;
        }
    }

    private void updateSelectedPartTransform() {
        if (suppressPartRefresh || LiveModelEditorPreview.getSelectedPart() < 0) {
            return;
        }
        if (LiveModelEditorPreview.setSelectedPartTransform(
                number(partScaleXSpinner), number(partScaleYSpinner), number(partScaleZSpinner),
                number(partMoveXSpinner), number(partMoveYSpinner), number(partMoveZSpinner),
                number(partYawSpinner))) {
            statusLabel.setText("Applied transform to selected mesh part.");
        }
    }

    private void resetTransforms() {
        suppressLiveRefresh = true;
        try {
            resetEditorsOnly();
        } finally {
            suppressLiveRefresh = false;
        }
        refreshPreview();
        initializeParts();
    }

    private void resetEditorsOnly() {
        typeSpinner.setValue(Integer.valueOf(10));
        objectRotationSpinner.setValue(Integer.valueOf(0));
        tileOffsetXSpinner.setValue(Integer.valueOf(0));
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
            statusLabel.setText("Saved " + file.getPath());
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
        if (chooser.showOpenDialog(overlayWindow) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            String json = readFile(file);
            int version = readInt(json, "version", -1);
            if (version < 1 || version > PROJECT_VERSION) {
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
                tileOffsetXSpinner.setValue(Integer.valueOf(readInt(json, "previewOffsetX", 0)));
                tileOffsetYSpinner.setValue(Integer.valueOf(readInt(json, "previewOffsetY", 0)));
                scaleXSpinner.setValue(Integer.valueOf(readInt(json, "scaleXPercent", 100)));
                scaleYSpinner.setValue(Integer.valueOf(readInt(json, "scaleYPercent", 100)));
                scaleZSpinner.setValue(Integer.valueOf(readInt(json, "scaleZPercent", 100)));
                moveXSpinner.setValue(Integer.valueOf(readInt(json, "translateX", 0)));
                moveYSpinner.setValue(Integer.valueOf(readInt(json, "translateY", 0)));
                moveZSpinner.setValue(Integer.valueOf(readInt(json, "translateZ", 0)));
                yawSpinner.setValue(Integer.valueOf(readInt(json, "yawDegrees", 0)));
            } finally {
                suppressLiveRefresh = false;
            }

            hoveredListIndex = -1;
            LiveModelEditorPreview.clearPartPreview();
            updateTargetLabels();
            refreshPreview();
            initializeParts();
            if (version >= 2) {
                LiveModelEditorPreview.loadPartProjectJson(json);
                refreshPartList();
                loadSelectedPartEditors();
            }
            statusLabel.setText("Loaded " + file.getPath());
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
        out.append("  \"yawDegrees\": ").append(number(yawSpinner)).append(",\n");
        out.append(LiveModelEditorPreview.getPartProjectJsonFields()).append("\n");
        out.append("}\n");
        return out.toString();
    }

    private static JPanel rsCard() {
        JPanel panel = new JPanel();
        panel.setBackground(RS_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RS_BORDER),
                BorderFactory.createEmptyBorder(7, 7, 7, 7)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private static JPanel actionRow(int columns) {
        JPanel row = new JPanel(new GridLayout(1, columns, 5, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return row;
    }

    private static JButton rsButton(String text) {
        JButton button = new JButton(text);
        button.setFont(RS_SMALL_FONT);
        button.setForeground(RS_TEXT);
        button.setBackground(RS_PANEL_2);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(RS_GOLD_DIM));
        button.setOpaque(true);
        return button;
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setFont(RS_SMALL_FONT);
        spinner.setBackground(RS_INPUT);
        spinner.setBorder(BorderFactory.createLineBorder(RS_BORDER));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
            field.setFont(RS_SMALL_FONT);
            field.setForeground(RS_TEXT);
            field.setBackground(RS_INPUT);
            field.setCaretColor(RS_GOLD);
            field.setHorizontalAlignment(JTextField.RIGHT);
            field.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
        }
    }

    private static JLabel rsValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(RS_BODY_FONT);
        label.setForeground(RS_TEXT);
        return label;
    }

    private static JLabel rsMuted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(RS_SMALL_FONT);
        label.setForeground(RS_MUTED);
        return label;
    }

    private static JLabel rsGold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(RS_SMALL_FONT);
        label.setForeground(RS_GOLD);
        return label;
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static int number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static void rememberManualOverlayPosition() {
        if (overlayWindow == null) {
            return;
        }
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null) {
            return;
        }
        try {
            Point screen = canvas.getLocationOnScreen();
            manualLocalX = overlayWindow.getX() - screen.x;
            manualLocalY = overlayWindow.getY() - screen.y;
            manuallyPositioned = true;
        } catch (IllegalComponentStateException ignored) {
        }
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
            return "unresolved";
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

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
