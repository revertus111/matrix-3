package game.console;

import game.Class584;
import game.ClientConsoleBridge;
import game.ConstructionBuildCamera;
import game.ConstructionPaletteOverlay;
import game.ConstructionPlacementController;
import game.DevDefinitionBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;
import game.LiveModelEditorPreview;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
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

    private static final int PROJECT_VERSION = 4;
    private static final File PROJECT_DIR = new File("dev-model-projects");
    private static final File ASSET_DIR = new File("dev-model-assets");

    private static final int OVERLAY_WIDTH = 390;
    private static final int OVERLAY_HEIGHT = 720;
    private static final int MIN_OVERLAY_WIDTH = 350;
    private static final int MIN_OVERLAY_HEIGHT = 560;
    private static final int TAB_RAIL_WIDTH = 42;
    private static final int OVERLAY_MARGIN = 10;
    private static final int OVERLAY_TOP = 8;

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
    private static long lastHudReassertMillis;
    private static boolean manuallyPositioned;
    private static boolean inputGateInstalled;
    private static boolean drawerExpanded = true;
    private static String activeTool = "EDIT";
    private static boolean modelLeftDragActive;
    private static boolean editorCameraSessionActive;
    private static boolean editorStartedRtsCamera;
    private static boolean editorHudRequested;
    private static boolean editorCameraWasAlreadyActive;
    private static ConstructionBuildCamera.CameraMode editorPreviousCameraMode;
    private static int manualLocalX;
    private static int manualLocalY;
    private static int manualWidth = OVERLAY_WIDTH;
    private static int manualHeight = OVERLAY_HEIGHT;
    private static boolean manuallySized;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel editorDrawer = new JPanel(new BorderLayout());
    private final CardLayout toolCardLayout = new CardLayout();
    private final JPanel toolCards = new JPanel(toolCardLayout);
    private final CardLayout transformCardLayout = new CardLayout();
    private final JPanel transformCards = new JPanel(transformCardLayout);
    private final JLabel transformContextLabel = rsGold("PART");
    private final JLabel cameraStatusLabel = rsGold("RTS");
    private final JLabel cameraSpeedLabel = rsGold("1.0x");
    private final JButton rtsCameraButton = rsButton("RTS");
    private final JButton freeCameraButton = rsButton("FREE");
    private final JLabel targetLabel = rsValue("-");
    private final JLabel sourceLabel = rsMuted("-");
    private final JLabel statusLabel = rsMuted("Right-click an object -> Dev > Edit Model Live.");
    private final JLabel partStatusLabel = rsGold("Mesh parts not ready.");

    private final DefaultListModel<String> partListModel = new DefaultListModel<String>();
    private final JList<String> partList = new JList<String>(partListModel);
    private final ConstructionPlacementController.BuildPiece[] constructionPieces =
            ConstructionPlacementController.getPieces();
    private final JComboBox<String> replacementCombo = new JComboBox<String>();

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
        for (ConstructionPlacementController.BuildPiece piece : constructionPieces) {
            replacementCombo.addItem(piece.getCategory().getDisplayName() + " - "
                    + piece.getDisplayName() + " (#" + piece.getObjectId() + ")");
        }
        replacementCombo.setFont(RS_SMALL_FONT);
        replacementCombo.setForeground(RS_TEXT);
        replacementCombo.setBackground(RS_INPUT);
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
        enterEditorRtsCamera();
        enterEditorHud();
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

        int requestedWidth = drawerExpanded ? (manuallySized ? manualWidth : OVERLAY_WIDTH) : TAB_RAIL_WIDTH;
        int requestedHeight = manuallySized ? manualHeight : OVERLAY_HEIGHT;
        int minWidth = drawerExpanded ? MIN_OVERLAY_WIDTH : TAB_RAIL_WIDTH;
        int width = Math.min(requestedWidth, Math.max(minWidth, canvas.getWidth() - 20));
        int height = Math.min(requestedHeight, Math.max(MIN_OVERLAY_HEIGHT, canvas.getHeight() - 20));

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
        if (instance != null && overlayWindow.isVisible()) {
            instance.syncRuntimeState();
            long now = System.currentTimeMillis();
            if (editorHudRequested && now - lastHudReassertMillis >= 1000L) {
                ClientConsoleBridge.queueConsoleCommand("itembrowser editorhud enter");
                lastHudReassertMillis = now;
            }
        }
    }

    private void buildUi() {
        root.setBackground(RS_BG);
        root.setBorder(BorderFactory.createLineBorder(RS_BORDER, 1));

        editorDrawer.setBackground(RS_BG);
        editorDrawer.add(createTitleBar(), BorderLayout.NORTH);

        toolCards.setBackground(RS_BG);
        toolCards.add(createEditPanel(), "EDIT");
        toolCards.add(createMaterialPanel(), "MATERIAL");
        toolCards.add(createCameraPanel(), "CAMERA");
        toolCards.add(createObjectPanel(), "OBJECT");
        toolCards.add(createProjectPanel(), "PROJECT");
        editorDrawer.add(toolCards, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 7, 5, 7));
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(RS_PANEL_2);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RS_BORDER));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(createResizeGrip(), BorderLayout.EAST);
        editorDrawer.add(footer, BorderLayout.SOUTH);

        root.add(editorDrawer, BorderLayout.CENTER);
        root.add(createToolRail(), BorderLayout.EAST);
        toolCardLayout.show(toolCards, activeTool);
        editorDrawer.setVisible(drawerExpanded);

        installListeners();
        refreshTransformContext();
        syncCameraPanel();
    }

    private JPanel createTitleBar() {
        final JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(RS_PANEL_2);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, RS_BORDER));
        bar.setPreferredSize(new Dimension(MIN_OVERLAY_WIDTH, 36));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBorder(BorderFactory.createEmptyBorder(3, 7, 2, 0));

        JLabel title = new JLabel("LIVE MODEL EDITOR");
        title.setFont(RS_TITLE_FONT);
        title.setForeground(RS_GOLD);
        JLabel subtitle = new JLabel("Compact live workspace");
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

    private JPanel createToolRail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(RS_PANEL_2);
        rail.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, RS_BORDER));
        rail.setPreferredSize(new Dimension(TAB_RAIL_WIDTH, OVERLAY_HEIGHT));

        rail.add(railButton("E", "Edit / parts + transform", "EDIT"));
        rail.add(Box.createVerticalStrut(3));
        rail.add(railButton("M", "Material replacement", "MATERIAL"));
        rail.add(Box.createVerticalStrut(3));
        rail.add(railButton("C", "Camera", "CAMERA"));
        rail.add(Box.createVerticalStrut(3));
        rail.add(railButton("O", "Object / source", "OBJECT"));
        rail.add(Box.createVerticalStrut(3));
        rail.add(railButton("S", "Save / project", "PROJECT"));
        rail.add(Box.createVerticalGlue());

        JButton collapse = rsButton("<");
        collapse.setToolTipText("Collapse / expand editor drawer");
        collapse.setPreferredSize(new Dimension(TAB_RAIL_WIDTH - 6, 30));
        collapse.setMaximumSize(new Dimension(TAB_RAIL_WIDTH - 6, 30));
        collapse.setAlignmentX(Component.CENTER_ALIGNMENT);
        collapse.addActionListener(e -> {
            drawerExpanded = !drawerExpanded;
            editorDrawer.setVisible(drawerExpanded);
            refreshOverlayBounds();
        });
        rail.add(collapse);
        rail.add(Box.createVerticalStrut(3));

        JButton close = rsButton("X");
        close.setToolTipText("Exit Live Model Editor");
        close.setBackground(RS_DANGER);
        close.setPreferredSize(new Dimension(TAB_RAIL_WIDTH - 6, 30));
        close.setMaximumSize(new Dimension(TAB_RAIL_WIDTH - 6, 30));
        close.setAlignmentX(Component.CENTER_ALIGNMENT);
        close.addActionListener(e -> closeEditorSession());
        rail.add(close);
        rail.add(Box.createVerticalStrut(5));
        return rail;
    }

    private JButton railButton(String text, String tooltip, final String tool) {
        JButton button = rsButton(text);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(TAB_RAIL_WIDTH - 6, 34));
        button.setMaximumSize(new Dimension(TAB_RAIL_WIDTH - 6, 34));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> showTool(tool));
        return button;
    }

    private void showTool(String tool) {
        if (tool == null) return;
        if (drawerExpanded && tool.equals(activeTool)) {
            drawerExpanded = false;
            editorDrawer.setVisible(false);
        } else {
            activeTool = tool;
            drawerExpanded = true;
            editorDrawer.setVisible(true);
            toolCardLayout.show(toolCards, tool);
        }
        refreshOverlayBounds();
    }

    private JPanel toolPanel(String titleText) {
        JPanel panel = rsCard();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
        JLabel title = new JLabel(titleText);
        title.setFont(RS_SECTION_FONT);
        title.setForeground(RS_GOLD);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(6));
        return panel;
    }

    private JPanel createEditPanel() {
        JPanel panel = toolPanel("EDIT / PARTS + TRANSFORM");

        JPanel selectionHeading = new JPanel(new BorderLayout(4, 0));
        selectionHeading.setOpaque(false);
        selectionHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel selectionHint = new JLabel("Selection");
        selectionHint.setFont(RS_SMALL_FONT);
        selectionHint.setForeground(RS_MUTED);
        selectionHeading.add(selectionHint, BorderLayout.WEST);
        selectionHeading.add(partStatusLabel, BorderLayout.EAST);
        panel.add(selectionHeading);
        panel.add(Box.createVerticalStrut(4));

        JPanel selectionModes = actionRow(3);
        JButton wholeMode = rsButton("Whole [1]");
        JButton partMode = rsButton("Part [2]");
        JButton multiMode = rsButton("Multi [3]");
        selectionModes.add(wholeMode);
        selectionModes.add(partMode);
        selectionModes.add(multiMode);
        panel.add(selectionModes);
        panel.add(Box.createVerticalStrut(3));

        JPanel selectionActions = actionRow(3);
        JButton selectAll = rsButton("All");
        JButton clearSelection = rsButton("Clear");
        JButton resetSelection = rsButton("Reset");
        selectionActions.add(selectAll);
        selectionActions.add(clearSelection);
        selectionActions.add(resetSelection);
        panel.add(selectionActions);
        panel.add(Box.createVerticalStrut(6));

        JPanel transformHeading = new JPanel(new BorderLayout(4, 0));
        transformHeading.setOpaque(false);
        transformHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel transformHint = new JLabel("Transform");
        transformHint.setFont(RS_SMALL_FONT);
        transformHint.setForeground(RS_MUTED);
        transformHeading.add(transformHint, BorderLayout.WEST);
        transformHeading.add(transformContextLabel, BorderLayout.EAST);
        panel.add(transformHeading);
        panel.add(Box.createVerticalStrut(4));

        JPanel modes = actionRow(3);
        JButton moveMode = rsButton("Move [G]");
        JButton rotateMode = rsButton("Rotate [R]");
        JButton scaleMode = rsButton("Scale [V]");
        modes.add(moveMode);
        modes.add(rotateMode);
        modes.add(scaleMode);
        panel.add(modes);
        panel.add(Box.createVerticalStrut(3));

        JPanel axes = actionRow(4);
        JButton freeAxis = rsButton("Free [F]");
        JButton xAxis = rsButton("X");
        JButton yAxis = rsButton("Y");
        JButton zAxis = rsButton("Z");
        axes.add(freeAxis);
        axes.add(xAxis);
        axes.add(yAxis);
        axes.add(zAxis);
        panel.add(axes);
        panel.add(Box.createVerticalStrut(6));

        transformCards.setOpaque(false);

        JPanel whole = new JPanel();
        whole.setLayout(new BoxLayout(whole, BoxLayout.Y_AXIS));
        whole.setOpaque(false);
        whole.add(createSpinnerGrid(
                new String[] { "S X", "S Y", "S Z" },
                new JSpinner[] { scaleXSpinner, scaleYSpinner, scaleZSpinner }));
        whole.add(Box.createVerticalStrut(4));
        whole.add(createSpinnerGrid(
                new String[] { "X", "Y", "Z" },
                new JSpinner[] { moveXSpinner, moveYSpinner, moveZSpinner }));
        whole.add(Box.createVerticalStrut(4));
        whole.add(createSpinnerGrid(new String[] { "YAW" }, new JSpinner[] { yawSpinner }));

        JPanel parts = new JPanel();
        parts.setLayout(new BoxLayout(parts, BoxLayout.Y_AXIS));
        parts.setOpaque(false);
        parts.add(createSpinnerGrid(
                new String[] { "S X", "S Y", "S Z" },
                new JSpinner[] { partScaleXSpinner, partScaleYSpinner, partScaleZSpinner }));
        parts.add(Box.createVerticalStrut(4));
        parts.add(createSpinnerGrid(
                new String[] { "X", "Y", "Z" },
                new JSpinner[] { partMoveXSpinner, partMoveYSpinner, partMoveZSpinner }));
        parts.add(Box.createVerticalStrut(4));
        parts.add(createSpinnerGrid(new String[] { "YAW" }, new JSpinner[] { partYawSpinner }));

        transformCards.add(whole, "WHOLE");
        transformCards.add(parts, "PARTS");
        panel.add(transformCards);
        panel.add(Box.createVerticalStrut(6));

        partList.setVisibleRowCount(8);
        partList.setFixedCellHeight(22);
        partList.setFont(RS_SMALL_FONT);
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
                label.setFont(RS_SMALL_FONT);
                label.setForeground(isSelected ? RS_GOLD : RS_TEXT);
                label.setBackground(isSelected ? RS_SELECTED
                        : index == hoveredListIndex ? RS_HOVER : RS_INPUT);
                label.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
                return label;
            }
        });

        JScrollPane scroll = new JScrollPane(partList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(RS_BORDER));
        scroll.setPreferredSize(new Dimension(300, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        scroll.getViewport().setBackground(RS_INPUT);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(5));

        JPanel partActions1 = actionRow(3);
        JButton rebuild = rsButton("Rebuild");
        JButton isolate = rsButton("Isolate");
        JButton showAll = rsButton("Show All");
        partActions1.add(rebuild);
        partActions1.add(isolate);
        partActions1.add(showAll);
        panel.add(partActions1);
        panel.add(Box.createVerticalStrut(3));

        JPanel partActions2 = actionRow(4);
        JButton hide = rsButton("Hide");
        JButton duplicate = rsButton("Dup");
        JButton delete = rsButton("Delete");
        JButton undo = rsButton("Undo");
        delete.setBackground(RS_DANGER);
        partActions2.add(hide);
        partActions2.add(duplicate);
        partActions2.add(delete);
        partActions2.add(undo);
        panel.add(partActions2);

        wholeMode.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE));
        partMode.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.PART));
        multiMode.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI));
        selectAll.addActionListener(e -> {
            setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
            LiveModelEditorPreview.selectAllParts();
            refreshPartList();
        });
        clearSelection.addActionListener(e -> {
            setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
            LiveModelEditorPreview.clearPartSelection();
            refreshPartList();
        });
        resetSelection.addActionListener(e -> resetSelectedTransforms());

        moveMode.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.MOVE));
        rotateMode.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.ROTATE));
        scaleMode.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.SCALE));
        freeAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.FREE));
        xAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.X));
        yAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.Y));
        zAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.Z));

        rebuild.addActionListener(e -> initializeParts());
        isolate.addActionListener(e -> {
            LiveModelEditorPreview.toggleIsolatePart();
            refreshPartList();
        });
        showAll.addActionListener(e -> {
            LiveModelEditorPreview.showAllParts();
            refreshPartList();
        });
        hide.addActionListener(e -> toggleSelectedHidden());
        duplicate.addActionListener(e -> duplicateSelected());
        delete.addActionListener(e -> deleteSelected());
        undo.addActionListener(e -> undoPartEdit());
        return panel;
    }

    private JPanel createPartsPanel() {
        JPanel panel = toolPanel("PARTS / SELECTION");

        JPanel heading = new JPanel(new BorderLayout(4, 0));
        heading.setOpaque(false);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = new JLabel("Whole / Part / Multi");
        hint.setFont(RS_SMALL_FONT);
        hint.setForeground(RS_MUTED);
        heading.add(hint, BorderLayout.WEST);
        heading.add(partStatusLabel, BorderLayout.EAST);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(5));

        JPanel selectionModes = actionRow(3);
        JButton wholeMode = rsButton("Whole [1]");
        JButton partMode = rsButton("Part [2]");
        JButton multiMode = rsButton("Multi [3]");
        selectionModes.add(wholeMode); selectionModes.add(partMode); selectionModes.add(multiMode);
        panel.add(selectionModes);
        panel.add(Box.createVerticalStrut(4));

        JPanel selectionActions = actionRow(3);
        JButton selectAll = rsButton("All");
        JButton clearSelection = rsButton("Clear");
        JButton resetSelection = rsButton("Reset");
        selectionActions.add(selectAll); selectionActions.add(clearSelection); selectionActions.add(resetSelection);
        panel.add(selectionActions);
        panel.add(Box.createVerticalStrut(5));

        partList.setVisibleRowCount(10);
        partList.setFixedCellHeight(22);
        partList.setFont(RS_SMALL_FONT);
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
                label.setFont(RS_SMALL_FONT);
                label.setForeground(isSelected ? RS_GOLD : RS_TEXT);
                label.setBackground(isSelected ? RS_SELECTED
                        : index == hoveredListIndex ? RS_HOVER : RS_INPUT);
                label.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
                return label;
            }
        });

        JScrollPane scroll = new JScrollPane(partList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(RS_BORDER));
        scroll.setPreferredSize(new Dimension(220, 230));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        scroll.getViewport().setBackground(RS_INPUT);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(5));

        JPanel row1 = actionRow(3);
        JButton rebuild = rsButton("Rebuild");
        JButton isolate = rsButton("Isolate");
        JButton showAll = rsButton("Show All");
        row1.add(rebuild); row1.add(isolate); row1.add(showAll);
        panel.add(row1);
        panel.add(Box.createVerticalStrut(4));

        JPanel row2 = actionRow(4);
        JButton hide = rsButton("Hide");
        JButton duplicate = rsButton("Dup");
        JButton delete = rsButton("Delete");
        JButton undo = rsButton("Undo");
        delete.setBackground(RS_DANGER);
        row2.add(hide); row2.add(duplicate); row2.add(delete); row2.add(undo);
        panel.add(row2);

        wholeMode.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE));
        partMode.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.PART));
        multiMode.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI));
        selectAll.addActionListener(e -> {
            setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
            LiveModelEditorPreview.selectAllParts();
            refreshPartList();
        });
        clearSelection.addActionListener(e -> {
            setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
            LiveModelEditorPreview.clearPartSelection();
            refreshPartList();
        });
        resetSelection.addActionListener(e -> resetSelectedTransforms());
        rebuild.addActionListener(e -> initializeParts());
        isolate.addActionListener(e -> {
            LiveModelEditorPreview.toggleIsolatePart();
            refreshPartList();
        });
        showAll.addActionListener(e -> {
            LiveModelEditorPreview.showAllParts();
            refreshPartList();
        });
        hide.addActionListener(e -> toggleSelectedHidden());
        duplicate.addActionListener(e -> duplicateSelected());
        delete.addActionListener(e -> deleteSelected());
        undo.addActionListener(e -> undoPartEdit());
        return panel;
    }

    private JPanel createTransformPanel() {
        JPanel panel = toolPanel("TRANSFORM");

        JPanel contextRow = new JPanel(new BorderLayout());
        contextRow.setOpaque(false);
        contextRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = new JLabel("Selection context");
        hint.setFont(RS_SMALL_FONT);
        hint.setForeground(RS_MUTED);
        contextRow.add(hint, BorderLayout.WEST);
        contextRow.add(transformContextLabel, BorderLayout.EAST);
        panel.add(contextRow);
        panel.add(Box.createVerticalStrut(5));

        JPanel modes = actionRow(3);
        JButton moveMode = rsButton("Move [G]");
        JButton rotateMode = rsButton("Rotate [R]");
        JButton scaleMode = rsButton("Scale [V]");
        modes.add(moveMode); modes.add(rotateMode); modes.add(scaleMode);
        panel.add(modes);
        panel.add(Box.createVerticalStrut(4));

        JPanel axes = actionRow(4);
        JButton freeAxis = rsButton("Free [F]");
        JButton xAxis = rsButton("X");
        JButton yAxis = rsButton("Y");
        JButton zAxis = rsButton("Z");
        axes.add(freeAxis); axes.add(xAxis); axes.add(yAxis); axes.add(zAxis);
        panel.add(axes);
        panel.add(Box.createVerticalStrut(7));

        transformCards.setOpaque(false);

        JPanel whole = new JPanel();
        whole.setLayout(new BoxLayout(whole, BoxLayout.Y_AXIS));
        whole.setOpaque(false);
        whole.add(createSpinnerGrid(
                new String[] { "S X", "S Y", "S Z" },
                new JSpinner[] { scaleXSpinner, scaleYSpinner, scaleZSpinner }));
        whole.add(Box.createVerticalStrut(5));
        whole.add(createSpinnerGrid(
                new String[] { "X", "Y", "Z" },
                new JSpinner[] { moveXSpinner, moveYSpinner, moveZSpinner }));
        whole.add(Box.createVerticalStrut(5));
        whole.add(createSpinnerGrid(new String[] { "YAW" }, new JSpinner[] { yawSpinner }));

        JPanel parts = new JPanel();
        parts.setLayout(new BoxLayout(parts, BoxLayout.Y_AXIS));
        parts.setOpaque(false);
        parts.add(createSpinnerGrid(
                new String[] { "S X", "S Y", "S Z" },
                new JSpinner[] { partScaleXSpinner, partScaleYSpinner, partScaleZSpinner }));
        parts.add(Box.createVerticalStrut(5));
        parts.add(createSpinnerGrid(
                new String[] { "X", "Y", "Z" },
                new JSpinner[] { partMoveXSpinner, partMoveYSpinner, partMoveZSpinner }));
        parts.add(Box.createVerticalStrut(5));
        parts.add(createSpinnerGrid(new String[] { "YAW" }, new JSpinner[] { partYawSpinner }));

        transformCards.add(whole, "WHOLE");
        transformCards.add(parts, "PARTS");
        panel.add(transformCards);
        panel.add(Box.createVerticalStrut(7));

        JPanel actions = actionRow(2);
        JButton reset = rsButton("Reset Selection");
        JButton undo = rsButton("Undo");
        actions.add(reset); actions.add(undo);
        panel.add(actions);

        moveMode.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.MOVE));
        rotateMode.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.ROTATE));
        scaleMode.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.SCALE));
        freeAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.FREE));
        xAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.X));
        yAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.Y));
        zAxis.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.Z));
        reset.addActionListener(e -> resetSelectedTransforms());
        undo.addActionListener(e -> undoPartEdit());
        return panel;
    }

    private JPanel createMaterialPanel() {
        JPanel panel = toolPanel("MATERIAL / REPLACE");
        JLabel hint = new JLabel("Construction catalog source");
        hint.setFont(RS_SMALL_FONT);
        hint.setForeground(RS_MUTED);
        panel.add(hint);
        panel.add(Box.createVerticalStrut(5));

        replacementCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        replacementCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        replacementCombo.addActionListener(e -> returnViewportFocusSoon());
        panel.add(replacementCombo);
        panel.add(Box.createVerticalStrut(6));

        JPanel row1 = actionRow(1);
        JButton replaceOne = rsButton("Replace Selected");
        row1.add(replaceOne);
        panel.add(row1);
        panel.add(Box.createVerticalStrut(4));

        JPanel row2 = actionRow(1);
        JButton replaceMatching = rsButton("Replace Matching Parts");
        row2.add(replaceMatching);
        panel.add(row2);
        panel.add(Box.createVerticalStrut(4));

        JPanel row3 = actionRow(1);
        JButton restorePart = rsButton("Restore Original");
        row3.add(restorePart);
        panel.add(row3);

        replaceOne.addActionListener(e -> replaceSelected(false));
        replaceMatching.addActionListener(e -> replaceSelected(true));
        restorePart.addActionListener(e -> {
            if (LiveModelEditorPreview.clearSelectedReplacement()) {
                refreshPartList();
                statusLabel.setText("Restored the original connected component.");
            }
        });
        return panel;
    }

    private JPanel createCameraPanel() {
        JPanel panel = toolPanel("CAMERA");

        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel("Active mode");
        label.setFont(RS_SMALL_FONT);
        label.setForeground(RS_MUTED);
        status.add(label, BorderLayout.WEST);
        status.add(cameraStatusLabel, BorderLayout.EAST);
        panel.add(status);
        panel.add(Box.createVerticalStrut(6));

        JPanel modes = actionRow(2);
        modes.add(rtsCameraButton);
        modes.add(freeCameraButton);
        panel.add(modes);
        panel.add(Box.createVerticalStrut(8));

        JLabel rts = new JLabel("RTS: WASD pan | MMB orbit | wheel zoom | Q/E yaw");
        rts.setFont(RS_SMALL_FONT);
        rts.setForeground(RS_TEXT);
        panel.add(rts);
        panel.add(Box.createVerticalStrut(5));

        JPanel speed = new JPanel(new BorderLayout(5, 0));
        speed.setOpaque(false);
        speed.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton slower = rsButton("-");
        JButton faster = rsButton("+");
        speed.add(slower, BorderLayout.WEST);
        speed.add(cameraSpeedLabel, BorderLayout.CENTER);
        speed.add(faster, BorderLayout.EAST);
        panel.add(speed);
        panel.add(Box.createVerticalStrut(10));

        JLabel free = new JLabel("FREE: unrestricted fly / inspect under geometry");
        free.setFont(RS_SMALL_FONT);
        free.setForeground(RS_TEXT);
        panel.add(free);
        JLabel free2 = new JLabel("WASD move | Q/E vertical | normal free-camera look");
        free2.setFont(RS_SMALL_FONT);
        free2.setForeground(RS_MUTED);
        panel.add(free2);

        rtsCameraButton.addActionListener(e -> setEditorCameraMode(ConstructionBuildCamera.CameraMode.RTS));
        freeCameraButton.addActionListener(e -> setEditorCameraMode(ConstructionBuildCamera.CameraMode.FREE_BUILD));
        slower.addActionListener(e -> {
            ConstructionBuildCamera.adjustRtsMoveSpeed(-1);
            syncCameraPanel();
        });
        faster.addActionListener(e -> {
            ConstructionBuildCamera.adjustRtsMoveSpeed(1);
            syncCameraPanel();
        });
        return panel;
    }

    private JPanel createObjectPanel() {
        JPanel panel = toolPanel("OBJECT / SOURCE");
        targetLabel.setFont(RS_SECTION_FONT);
        panel.add(targetLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(sourceLabel);
        panel.add(Box.createVerticalStrut(8));

        panel.add(createSpinnerGrid(
                new String[] { "TYPE", "ROT" },
                new JSpinner[] { typeSpinner, objectRotationSpinner }));
        panel.add(Box.createVerticalStrut(5));
        panel.add(createSpinnerGrid(
                new String[] { "TILE X", "TILE Y" },
                new JSpinner[] { tileOffsetXSpinner, tileOffsetYSpinner }));
        panel.add(Box.createVerticalStrut(7));

        JPanel row1 = actionRow(2);
        JButton refresh = rsButton("Refresh");
        JButton rebuild = rsButton("Rebuild Parts");
        row1.add(refresh); row1.add(rebuild);
        panel.add(row1);
        panel.add(Box.createVerticalStrut(4));

        JPanel row2 = actionRow(2);
        JButton reset = rsButton("Reset Object");
        JButton exit = rsButton("Exit Edit");
        exit.setBackground(RS_DANGER);
        row2.add(reset); row2.add(exit);
        panel.add(row2);

        refresh.addActionListener(e -> refreshPreview());
        rebuild.addActionListener(e -> initializeParts());
        reset.addActionListener(e -> resetTransforms());
        exit.addActionListener(e -> closeEditorSession());
        return panel;
    }

    private JPanel createProjectPanel() {
        JPanel panel = toolPanel("PROJECT / ASSET");
        JLabel hint = new JLabel("JSON authoring + reusable selection assets");
        hint.setFont(RS_SMALL_FONT);
        hint.setForeground(RS_MUTED);
        panel.add(hint);
        panel.add(Box.createVerticalStrut(8));

        JPanel row1 = actionRow(1);
        JButton save = rsButton("Save Project   Ctrl+S");
        row1.add(save);
        panel.add(row1);
        panel.add(Box.createVerticalStrut(5));

        JPanel row2 = actionRow(1);
        JButton load = rsButton("Load Project");
        load.putClientProperty("keepEditorFocus", Boolean.TRUE);
        row2.add(load);
        panel.add(row2);
        panel.add(Box.createVerticalStrut(5));

        JPanel row3 = actionRow(1);
        JButton saveAsset = rsButton("Save Selection Asset");
        saveAsset.putClientProperty("keepEditorFocus", Boolean.TRUE);
        row3.add(saveAsset);
        panel.add(row3);

        save.addActionListener(e -> saveProject());
        load.addActionListener(e -> loadProject());
        saveAsset.addActionListener(e -> saveSelectionAsset());
        return panel;
    }

    private void refreshTransformContext() {
        LiveModelEditorPreview.SelectionMode mode = LiveModelEditorPreview.getSelectionMode();
        if (mode == LiveModelEditorPreview.SelectionMode.WHOLE) {
            transformCardLayout.show(transformCards, "WHOLE");
            transformContextLabel.setText("WHOLE");
            loadWholeEditors();
        } else {
            transformCardLayout.show(transformCards, "PARTS");
            transformContextLabel.setText(mode + " x" + LiveModelEditorPreview.getSelectedPartCount());
            if (LiveModelEditorPreview.getSelectedPart() >= 0) {
                loadSelectedPartEditors();
            }
        }
    }

    private void setEditorCameraMode(ConstructionBuildCamera.CameraMode mode) {
        if (!ConstructionBuildCamera.isRequested()) {
            ConstructionBuildCamera.enter();
        }
        ConstructionBuildCamera.setMode(mode);
        syncCameraPanel();
        statusLabel.setText("Camera: " + mode.getDisplayName() + ".");
    }

    private void syncCameraPanel() {
        ConstructionBuildCamera.CameraMode mode = ConstructionBuildCamera.getMode();
        boolean rts = mode == ConstructionBuildCamera.CameraMode.RTS;
        cameraStatusLabel.setText(mode.getDisplayName());
        cameraSpeedLabel.setText("RTS PAN " + ConstructionBuildCamera.getRtsMoveSpeedLabel());
        rtsCameraButton.setBackground(rts ? RS_SELECTED : RS_PANEL_2);
        freeCameraButton.setBackground(!rts ? RS_SELECTED : RS_PANEL_2);
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
            if (e.getValueIsAdjusting() || suppressPartRefresh) return;
            LiveModelEditorPreview.SelectionMode mode = LiveModelEditorPreview.getSelectionMode();
            if (mode == LiveModelEditorPreview.SelectionMode.WHOLE) return;
            if (mode == LiveModelEditorPreview.SelectionMode.MULTI) {
                LiveModelEditorPreview.setPartSelection(partList.getSelectedIndices());
            } else {
                int index = partList.getSelectedIndex();
                if (index >= 0) LiveModelEditorPreview.selectPart(index);
            }
            loadSelectedPartEditors();
            statusLabel.setText("Selected " + LiveModelEditorPreview.getSelectedPartCount()
                    + " part(s) in " + mode + " mode.");
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

            @Override
            public void mouseReleased(MouseEvent e) {
                returnViewportFocusSoon();
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
        modelLeftDragActive = false;
        exitEditorHud();
        exitEditorRtsCamera();
        if (instance != null) {
            instance.hoveredListIndex = -1;
            instance.statusLabel.setText("Edit session paused. Original scene object restored.");
        }
        if (overlayWindow != null) {
            overlayWindow.setVisible(false);
        }
    }

    private static void enterEditorHud() {
        if (editorHudRequested) {
            return;
        }
        String error = ClientConsoleBridge.queueConsoleCommand("itembrowser editorhud enter");
        editorHudRequested = error == null;
        if (error != null && instance != null) {
            instance.statusLabel.setText("Editor HUD request failed: " + error);
        }
    }

    private static void exitEditorHud() {
        if (!editorHudRequested) {
            return;
        }
        ClientConsoleBridge.queueConsoleCommand("itembrowser editorhud exit");
        editorHudRequested = false;
    }

    private static void enterEditorRtsCamera() {
        if (editorCameraSessionActive) {
            return;
        }
        editorCameraSessionActive = true;
        editorCameraWasAlreadyActive = ConstructionBuildCamera.isRequested();
        editorPreviousCameraMode = ConstructionBuildCamera.getMode();
        ConstructionPaletteOverlay.installRtsInputListener();

        if (editorCameraWasAlreadyActive) {
            ConstructionBuildCamera.setMode(ConstructionBuildCamera.CameraMode.RTS);
            editorStartedRtsCamera = false;
        } else {
            ConstructionBuildCamera.enter();
            editorStartedRtsCamera = ConstructionBuildCamera.isRequested();
        }
    }

    private static void exitEditorRtsCamera() {
        if (!editorCameraSessionActive) {
            return;
        }
        if (editorStartedRtsCamera && ConstructionBuildCamera.isRequested()) {
            if (!ConstructionBuildCamera.isSettlementAutoMode()
                    && !ConstructionPaletteOverlay.isVisible()) {
                ConstructionBuildCamera.exit();
            }
        } else if (editorCameraWasAlreadyActive
                && ConstructionBuildCamera.isRequested()
                && !ConstructionBuildCamera.isSettlementAutoMode()
                && editorPreviousCameraMode != null) {
            ConstructionBuildCamera.setMode(editorPreviousCameraMode);
        }

        editorCameraSessionActive = false;
        editorStartedRtsCamera = false;
        editorCameraWasAlreadyActive = false;
        editorPreviousCameraMode = null;
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
                    if (canvas == null || mouse.getSource() != canvas) {
                        return;
                    }

                    int id = mouse.getID();
                    if (id == MouseEvent.MOUSE_PRESSED && mouse.getButton() == MouseEvent.BUTTON2) {
                        requestGameCanvasFocus();
                        return;
                    }
                    if (id == MouseEvent.MOUSE_MOVED) {
                        LiveModelEditorPreview.pointerMoved(mouse.getX(), mouse.getY());
                        mouse.consume();
                        return;
                    }
                    if (id == MouseEvent.MOUSE_EXITED) {
                        LiveModelEditorPreview.pointerExited();
                        return;
                    }
                    if (id == MouseEvent.MOUSE_PRESSED
                            && mouse.getButton() == MouseEvent.BUTTON1) {
                        modelLeftDragActive = LiveModelEditorPreview.beginPointerDrag(
                                mouse.getX(), mouse.getY(),
                                mouse.isShiftDown() || mouse.isControlDown(), mouse.isControlDown());
                        mouse.consume();
                        if (instance != null) instance.syncRuntimeState();
                        return;
                    }
                    if (id == MouseEvent.MOUSE_DRAGGED) {
                        if (modelLeftDragActive) {
                            LiveModelEditorPreview.dragPointerTo(mouse.getX(), mouse.getY());
                            mouse.consume();
                            if (instance != null) instance.syncRuntimeState();
                        }
                        return;
                    }
                    if (id == MouseEvent.MOUSE_RELEASED
                            && mouse.getButton() == MouseEvent.BUTTON1) {
                        if (modelLeftDragActive) {
                            LiveModelEditorPreview.endPointerDrag();
                        }
                        modelLeftDragActive = false;
                        mouse.consume();
                        if (instance != null) instance.syncRuntimeState();
                        return;
                    }
                    if (id == MouseEvent.MOUSE_CLICKED
                            && mouse.getButton() == MouseEvent.BUTTON1) {
                        mouse.consume();
                    }
                    return;
                }

                if (event instanceof KeyEvent) {
                    KeyEvent key = (KeyEvent) event;
                    if (key.getID() != KeyEvent.KEY_PRESSED) {
                        return;
                    }
                    int code = key.getKeyCode();
                    if (code == KeyEvent.VK_ESCAPE) {
                        key.consume();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                closeEditorSession();
                            }
                        });
                        return;
                    }
                    if (code == KeyEvent.VK_1) {
                        if (instance != null) instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE);
                        else LiveModelEditorPreview.setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE);
                    } else if (code == KeyEvent.VK_2) {
                        if (instance != null) instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.PART);
                        else LiveModelEditorPreview.setSelectionMode(LiveModelEditorPreview.SelectionMode.PART);
                    } else if (code == KeyEvent.VK_3) {
                        if (instance != null) instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
                        else LiveModelEditorPreview.setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
                    } else if (code == KeyEvent.VK_A && key.isControlDown()) {
                        if (instance != null) {
                            instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
                            LiveModelEditorPreview.selectAllParts();
                            instance.refreshPartList();
                        }
                    } else if (code == KeyEvent.VK_S && key.isControlDown()) {
                        if (instance != null) {
                            if (key.isShiftDown()) instance.saveSelectionAsset();
                            else instance.saveProject();
                        }
                    } else if (code == KeyEvent.VK_G) {
                        LiveModelEditorPreview.setTransformMode(LiveModelEditorPreview.TransformMode.MOVE);
                    } else if (code == KeyEvent.VK_R) {
                        LiveModelEditorPreview.setTransformMode(LiveModelEditorPreview.TransformMode.ROTATE);
                    } else if (code == KeyEvent.VK_V) {
                        LiveModelEditorPreview.setTransformMode(LiveModelEditorPreview.TransformMode.SCALE);
                    } else if (code == KeyEvent.VK_X) {
                        LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.X);
                    } else if (code == KeyEvent.VK_Y) {
                        LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.Y);
                    } else if (code == KeyEvent.VK_Z) {
                        if (key.isControlDown()) {
                            if (instance != null) instance.undoPartEdit();
                            key.consume();
                            return;
                        }
                        LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.Z);
                    } else if (code == KeyEvent.VK_F) {
                        LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.FREE);
                    } else if (code == KeyEvent.VK_H) {
                        if (instance != null) instance.toggleSelectedHidden();
                    } else if (code == KeyEvent.VK_DELETE) {
                        if (instance != null) instance.deleteSelected();
                    } else if (code == KeyEvent.VK_D && key.isControlDown()) {
                        if (instance != null) instance.duplicateSelected();
                    } else {
                        return;
                    }
                    key.consume();
                    if (instance != null) instance.syncRuntimeState();
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
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
                + "   |   LMB edit   |   MMB orbit   |   wheel zoom   |   WASD pan");
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
        if (count > 0 && LiveModelEditorPreview.getSelectedPart() < 0
                && LiveModelEditorPreview.getSelectionMode() == LiveModelEditorPreview.SelectionMode.PART) {
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
        int[] selected = LiveModelEditorPreview.getSelectedParts();
        applySelectionModeToList();
        suppressPartRefresh = true;
        try {
            partListModel.clear();
            for (String label : labels) partListModel.addElement(label);
            partList.setSelectedIndices(selected);
            int primary = LiveModelEditorPreview.getSelectedPart();
            if (primary >= 0 && primary < labels.length) partList.ensureIndexIsVisible(primary);
        } finally {
            suppressPartRefresh = false;
        }
        if (labels.length == 0) {
            partStatusLabel.setText("NO PARTS");
        } else {
            partStatusLabel.setText(LiveModelEditorPreview.getSelectionMode() + " | "
                    + LiveModelEditorPreview.getSelectedPartCount() + "/" + labels.length + " SELECTED"
                    + (LiveModelEditorPreview.isPartIsolated() ? " | ISOLATE" : ""));
        }
        partList.repaint();
    }

    private void syncRuntimeState() {
        int[] selected = LiveModelEditorPreview.getSelectedParts();
        if (!Arrays.equals(partList.getSelectedIndices(), selected)) {
            suppressPartRefresh = true;
            try {
                partList.setSelectedIndices(selected);
                int primary = LiveModelEditorPreview.getSelectedPart();
                if (primary >= 0 && primary < partListModel.size()) partList.ensureIndexIsVisible(primary);
            } finally {
                suppressPartRefresh = false;
            }
        }
        refreshTransformContext();
        syncCameraPanel();
        int hovered = LiveModelEditorPreview.getWorldHoveredPart();
        partStatusLabel.setText(LiveModelEditorPreview.getSelectionMode() + " | "
                + LiveModelEditorPreview.getSelectedPartCount() + " selected"
                + (hovered >= 0 ? " | HOVER P" + hovered : "")
                + " | " + LiveModelEditorPreview.getTransformMode() + " "
                + LiveModelEditorPreview.getAxisConstraint()
                + (LiveModelEditorPreview.isPartIsolated() ? " | ISOLATE" : ""));
    }

    private void setSelectionMode(LiveModelEditorPreview.SelectionMode mode) {
        LiveModelEditorPreview.setSelectionMode(mode);
        applySelectionModeToList();
        refreshPartList();
        refreshTransformContext();
        statusLabel.setText("Selection mode: " + mode + ".");
    }

    private void applySelectionModeToList() {
        LiveModelEditorPreview.SelectionMode mode = LiveModelEditorPreview.getSelectionMode();
        boolean previous = suppressPartRefresh;
        suppressPartRefresh = true;
        try {
            partList.setSelectionMode(mode == LiveModelEditorPreview.SelectionMode.PART
                    ? ListSelectionModel.SINGLE_SELECTION
                    : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            partList.setEnabled(mode != LiveModelEditorPreview.SelectionMode.WHOLE);
        } finally {
            suppressPartRefresh = previous;
        }
    }

    private void setEditMode(LiveModelEditorPreview.TransformMode mode) {
        LiveModelEditorPreview.setTransformMode(mode);
        syncRuntimeState();
    }

    private void setEditAxis(LiveModelEditorPreview.AxisConstraint axis) {
        LiveModelEditorPreview.setAxisConstraint(axis);
        syncRuntimeState();
    }

    private void toggleSelectedHidden() {
        if (LiveModelEditorPreview.toggleSelectedPartHidden()) {
            refreshPartList();
            statusLabel.setText("Toggled visibility for "
                    + LiveModelEditorPreview.getSelectedPartCount() + " selected part(s).");
        }
    }

    private void duplicateSelected() {
        if (LiveModelEditorPreview.duplicateSelectedPart()) {
            refreshPartList();
            loadSelectedPartEditors();
            statusLabel.setText("Duplicated selected mesh part(s).");
        }
    }

    private void deleteSelected() {
        if (LiveModelEditorPreview.deleteSelectedPart()) {
            refreshPartList();
            statusLabel.setText("Deleted selected project part(s). Ctrl+Z restores them.");
        }
    }

    private void replaceSelected(boolean allMatching) {
        int index = replacementCombo.getSelectedIndex();
        if (index < 0 || index >= constructionPieces.length) {
            statusLabel.setText("Choose a Construction material first.");
            return;
        }
        ConstructionPlacementController.BuildPiece piece = constructionPieces[index];
        if (LiveModelEditorPreview.replaceSelectedWithConstructionPiece(piece, allMatching)) {
            refreshPartList();
            statusLabel.setText((allMatching ? "Replaced matching components with " : "Replaced selected component with ")
                    + piece.getDisplayName() + " #" + piece.getObjectId() + ".");
        } else {
            statusLabel.setText("Replacement could not be applied to the selected part.");
        }
    }

    private void resetSelectedTransforms() {
        if (LiveModelEditorPreview.getSelectionMode() == LiveModelEditorPreview.SelectionMode.WHOLE) {
            suppressLiveRefresh = true;
            try {
                scaleXSpinner.setValue(Integer.valueOf(100));
                scaleYSpinner.setValue(Integer.valueOf(100));
                scaleZSpinner.setValue(Integer.valueOf(100));
                moveXSpinner.setValue(Integer.valueOf(0));
                moveYSpinner.setValue(Integer.valueOf(0));
                moveZSpinner.setValue(Integer.valueOf(0));
                yawSpinner.setValue(Integer.valueOf(0));
            } finally {
                suppressLiveRefresh = false;
            }
            refreshPreview();
            loadWholeEditors();
            statusLabel.setText("Reset whole-model transform.");
            return;
        }
        if (LiveModelEditorPreview.resetSelectedPartTransforms()) {
            loadSelectedPartEditors();
            refreshPartList();
            statusLabel.setText("Reset selected part transform(s).");
        }
    }

    private void loadWholeEditors() {
        int[] transform = LiveModelEditorPreview.getWholeTransform();
        suppressLiveRefresh = true;
        try {
            scaleXSpinner.setValue(Integer.valueOf(transform[0]));
            scaleYSpinner.setValue(Integer.valueOf(transform[1]));
            scaleZSpinner.setValue(Integer.valueOf(transform[2]));
            moveXSpinner.setValue(Integer.valueOf(transform[3]));
            moveYSpinner.setValue(Integer.valueOf(transform[4]));
            moveZSpinner.setValue(Integer.valueOf(transform[5]));
            yawSpinner.setValue(Integer.valueOf(transform[6]));
        } finally {
            suppressLiveRefresh = false;
        }
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
        if (suppressPartRefresh || LiveModelEditorPreview.getSelectedPart() < 0
                || LiveModelEditorPreview.getSelectionMode() == LiveModelEditorPreview.SelectionMode.WHOLE) {
            return;
        }
        if (LiveModelEditorPreview.setSelectedPartTransform(
                number(partScaleXSpinner), number(partScaleYSpinner), number(partScaleZSpinner),
                number(partMoveXSpinner), number(partMoveYSpinner), number(partMoveZSpinner),
                number(partYawSpinner))) {
            statusLabel.setText("Applied transform to selected mesh part(s).");
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

    private void saveSelectionAsset() {
        String json = LiveModelEditorPreview.getSelectionAssetJson();
        if (json == null) {
            statusLabel.setText("Select one or more live model parts first.");
            return;
        }
        try {
            if (!ASSET_DIR.exists() && !ASSET_DIR.mkdirs()) {
                throw new IllegalStateException("Could not create " + ASSET_DIR.getPath());
            }
            JFileChooser chooser = new JFileChooser(ASSET_DIR);
            chooser.setDialogTitle("Save Matrix3 custom model selection");
            chooser.setSelectedFile(new File(ASSET_DIR,
                    safeFileStem(objectName) + "_" + objectId + "_selection.json"));
            if (chooser.showSaveDialog(overlayWindow) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getParentFile(), file.getName() + ".json");
            }
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8));
            try { writer.write(json); } finally { writer.close(); }
            statusLabel.setText("Saved custom selection asset: " + file.getPath());
        } catch (Exception ex) {
            statusLabel.setText("Selection save failed: " + rootMessage(ex));
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

    private static JComponent createResizeGrip() {
        final JLabel grip = new JLabel("  //  ");
        grip.setFont(RS_SMALL_FONT);
        grip.setForeground(RS_GOLD);
        grip.setToolTipText("Drag to resize Live Model Editor");
        grip.setBorder(BorderFactory.createEmptyBorder(4, 4, 5, 5));

        MouseAdapter resize = new MouseAdapter() {
            private int startX, startY, startWidth, startHeight;

            @Override
            public void mousePressed(MouseEvent e) {
                if (overlayWindow == null) return;
                startX = e.getXOnScreen();
                startY = e.getYOnScreen();
                startWidth = overlayWindow.getWidth();
                startHeight = overlayWindow.getHeight();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Canvas canvas = Class584.aCanvas7745;
                if (overlayWindow == null || canvas == null) return;
                int maxWidth = Math.max(MIN_OVERLAY_WIDTH, canvas.getWidth() - 20);
                int maxHeight = Math.max(MIN_OVERLAY_HEIGHT, canvas.getHeight() - 20);
                manualWidth = clamp(startWidth + e.getXOnScreen() - startX,
                        MIN_OVERLAY_WIDTH, maxWidth);
                manualHeight = clamp(startHeight + e.getYOnScreen() - startY,
                        MIN_OVERLAY_HEIGHT, maxHeight);
                manuallySized = true;
                refreshOverlayBounds();
            }
        };
        grip.addMouseListener(resize);
        grip.addMouseMotionListener(resize);
        return grip;
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
        button.addActionListener(e -> {
            if (!Boolean.TRUE.equals(button.getClientProperty("keepEditorFocus"))) {
                returnViewportFocusSoon();
            }
        });
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

    private static void returnViewportFocusSoon() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                requestGameCanvasFocus();
            }
        });
    }

    private static void requestGameCanvasFocus() {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable()) {
            return;
        }
        canvas.requestFocusInWindow();
        canvas.requestFocus();
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

    private static String safeFileStem(String value) {
        if (value == null || value.trim().length() == 0) return "model_asset";
        String safe = value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.length() == 0 ? "model_asset" : safe;
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
