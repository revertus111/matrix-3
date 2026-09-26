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
    private static boolean manuallyPositioned;
    private static boolean inputGateInstalled;
    private static boolean drawerExpanded = true;
    private static String activeTool = "EDIT";
    private static boolean modelLeftDragActive;
    private static boolean editorCtrlDown;
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
    private final JLabel transformContextLabel = rsGold("PART");
    private final JLabel cameraStatusLabel = rsGold("RTS");
    private final JLabel cameraSpeedLabel = rsGold("1.0x");
    private final JButton rtsCameraButton = rsButton("RTS");
    private final JButton freeCameraButton = rsButton("FREE");
    private final JButton snapButton = rsButton("SNAP OFF");
    private final JSpinner moveSnapSpinner = spinner(16, 1, 512, 1);
    private final JSpinner angleSnapSpinner = spinner(15, 1, 90, 1);

    private final JButton wholeModeButton = rsButton("Whole [1]");
    private final JButton partModeButton = rsButton("Part [2]");
    private final JButton multiModeButton = rsButton("Multi [3]");
    private final JButton moveModeButton = rsButton("Move [G]");
    private final JButton rotateModeButton = rsButton("Rotate [R]");
    private final JButton scaleModeButton = rsButton("Scale [V]");
    private final JButton freeAxisButton = rsButton("Free [F]");
    private final JButton xAxisButton = rsButton("X");
    private final JButton yAxisButton = rsButton("Y");
    private final JButton zAxisButton = rsButton("Z");
    private final JButton isolateButton = rsButton("Isolate");
    private final JLabel transformReadoutLabel = rsGold("MOVE FREE");
    private final java.util.ArrayList<JButton> railButtons =
            new java.util.ArrayList<JButton>();

    private final JLabel targetLabel = rsValue("-");
    private final JLabel sourceLabel = rsMuted("-");
    private final JLabel statusLabel = rsMuted("Right-click an object -> Dev > Edit Model Live.");
    private final JLabel partStatusLabel = rsGold("Mesh parts not ready.");

    private final JTextField inspectorPosXField = inspectorField("POS_X");
    private final JTextField inspectorPosYField = inspectorField("POS_Y");
    private final JTextField inspectorPosZField = inspectorField("POS_Z");
    private final JTextField inspectorYawField = inspectorField("YAW");
    private final JTextField inspectorScaleXField = inspectorField("SCALE_X");
    private final JTextField inspectorScaleYField = inspectorField("SCALE_Y");
    private final JTextField inspectorScaleZField = inspectorField("SCALE_Z");

    private final DefaultListModel<String> partListModel = new DefaultListModel<String>();
    private final JList<String> partList = new JList<String>(partListModel);

    private static final int[] HUD_ROOT_COMPONENTS = {
            165, 176, 198, 382, 219, 230, 57, 132, 154, 252, 296, 263,
            46, 241, 285, 307, 274, 29, 386, 35, 39, 486, 349, 366, 345,
            471, 403, 337, 481, 68, 78, 87, 96, 105, 114, 318, 123, 374,
            475, 333
    };
    private static final int[] HUD_INTERFACE_IDS = {
            1460, 1452, 1449, 635, 1466, 1220, 1473, 1464, 1458, 550, 1427, 1110,
            590, 1416, 1417, 231, 1519, 1431, 568, 1430, 1465, 1433, 1483, 745, 1485,
            1213, 1448, 557, 1484, 137, 1467, 1472, 1471, 1470, 464, 228, 1529, 182,
            1488, 1215
    };
    private final DefaultListModel<String> hudListModel = new DefaultListModel<String>();
    private final JList<String> hudList = new JList<String>(hudListModel);
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
    private boolean suppressInspectorRefresh;
    private int hoveredListIndex = -1;

    private LiveModelEditorWindow() {
        for (ConstructionPlacementController.BuildPiece piece : constructionPieces) {
            replacementCombo.addItem(piece.getCategory().getDisplayName() + " - "
                    + piece.getDisplayName() + " (#" + piece.getObjectId() + ")");
        }
        replacementCombo.setFont(RS_SMALL_FONT);
        replacementCombo.setForeground(RS_TEXT);
        replacementCombo.setBackground(RS_INPUT);
        for (int i = 0; i < HUD_ROOT_COMPONENTS.length; i++)
            hudListModel.addElement("Root " + HUD_ROOT_COMPONENTS[i]
                    + "  ->  interface " + HUD_INTERFACE_IDS[i]);
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
        editorCtrlDown = false;
        enterEditorRtsCamera();
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
        syncSnapPanel();
        syncControlState();
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
        rail.add(railButton("C", "Camera / view / HUD", "CAMERA"));
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
        collapse.addActionListener(e -> toggleDrawer());
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
        button.putClientProperty("toolName", tool);
        railButtons.add(button);
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
        syncControlState();
        refreshOverlayBounds();
    }

    private void toggleDrawer() {
        drawerExpanded = !drawerExpanded;
        editorDrawer.setVisible(drawerExpanded);
        syncControlState();
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
        selectionModes.add(wholeModeButton);
        selectionModes.add(partModeButton);
        selectionModes.add(multiModeButton);
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
        modes.add(moveModeButton);
        modes.add(rotateModeButton);
        modes.add(scaleModeButton);
        panel.add(modes);
        panel.add(Box.createVerticalStrut(3));

        JPanel axes = actionRow(4);
        axes.add(freeAxisButton);
        axes.add(xAxisButton);
        axes.add(yAxisButton);
        axes.add(zAxisButton);
        panel.add(axes);
        panel.add(Box.createVerticalStrut(3));

        transformReadoutLabel.setToolTipText(
                "Live transform readout. Alt+Arrows nudge; Alt+Shift+Arrows use coarse steps.");
        transformReadoutLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(transformReadoutLabel);
        panel.add(Box.createVerticalStrut(4));

        snapButton.setToolTipText("Normal drag is free. Ctrl temporarily snaps; when SNAP is ON, Ctrl temporarily bypasses snap.");
        JPanel snapMode = actionRow(1);
        snapMode.add(snapButton);
        panel.add(snapMode);
        panel.add(Box.createVerticalStrut(3));
        panel.add(createSpinnerGrid(
                new String[] { "MOVE SNAP", "ANGLE SNAP" },
                new JSpinner[] { moveSnapSpinner, angleSnapSpinner }));
        panel.add(Box.createVerticalStrut(6));

        panel.add(createTransformInspector());
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
        scroll.setPreferredSize(new Dimension(300, 205));
        scroll.setMinimumSize(new Dimension(180, 110));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        scroll.getViewport().setBackground(RS_INPUT);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(5));

        JPanel partActions1 = actionRow(3);
        JButton rebuild = rsButton("Rebuild");
        JButton showAll = rsButton("Show All");
        partActions1.add(rebuild);
        partActions1.add(isolateButton);
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

        wholeModeButton.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE));
        partModeButton.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.PART));
        multiModeButton.addActionListener(e -> setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI));
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

        moveModeButton.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.MOVE));
        rotateModeButton.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.ROTATE));
        scaleModeButton.addActionListener(e -> setEditMode(LiveModelEditorPreview.TransformMode.SCALE));
        freeAxisButton.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.FREE));
        xAxisButton.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.X));
        yAxisButton.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.Y));
        zAxisButton.addActionListener(e -> setEditAxis(LiveModelEditorPreview.AxisConstraint.Z));
        snapButton.addActionListener(e -> {
            LiveModelEditorPreview.setTransformSnapEnabled(
                    !LiveModelEditorPreview.isTransformSnapEnabled());
            syncSnapPanel();
        });

        rebuild.addActionListener(e -> initializeParts());
        isolateButton.addActionListener(e -> toggleIsolateSelection());
        showAll.addActionListener(e -> showAllParts());
        hide.addActionListener(e -> toggleSelectedHidden());
        duplicate.addActionListener(e -> duplicateSelected());
        delete.addActionListener(e -> deleteSelected());
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
        JPanel panel = toolPanel("CAMERA / VIEW");

        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel("Active camera");
        label.setFont(RS_SMALL_FONT);
        label.setForeground(RS_MUTED);
        status.add(label, BorderLayout.WEST);
        status.add(cameraStatusLabel, BorderLayout.EAST);
        panel.add(status);
        panel.add(Box.createVerticalStrut(5));

        JPanel modes = actionRow(2);
        modes.add(rtsCameraButton);
        modes.add(freeCameraButton);
        panel.add(modes);
        panel.add(Box.createVerticalStrut(6));

        JLabel rts = new JLabel("RTS: WASD pan | MMB orbit | wheel zoom | Q/E yaw");
        rts.setFont(RS_SMALL_FONT);
        rts.setForeground(RS_TEXT);
        panel.add(rts);
        panel.add(Box.createVerticalStrut(4));

        JPanel speed = new JPanel(new BorderLayout(5, 0));
        speed.setOpaque(false);
        speed.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton slower = rsButton("-");
        JButton faster = rsButton("+");
        speed.add(slower, BorderLayout.WEST);
        speed.add(cameraSpeedLabel, BorderLayout.CENTER);
        speed.add(faster, BorderLayout.EAST);
        panel.add(speed);
        panel.add(Box.createVerticalStrut(5));

        JLabel free = new JLabel("FREE: unrestricted fly / inspect under geometry");
        free.setFont(RS_SMALL_FONT);
        free.setForeground(RS_TEXT);
        panel.add(free);
        JLabel free2 = new JLabel("WASD move | Q/E vertical | free-camera look");
        free2.setFont(RS_SMALL_FONT);
        free2.setForeground(RS_MUTED);
        panel.add(free2);

        panel.add(Box.createVerticalStrut(10));
        JLabel hudTitle = new JLabel("HUD VISIBILITY");
        hudTitle.setFont(RS_SECTION_FONT);
        hudTitle.setForeground(RS_GOLD);
        hudTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hudTitle);
        panel.add(Box.createVerticalStrut(3));

        JLabel warning = new JLabel("Structural root 8/12 locked ON.");
        warning.setFont(RS_SMALL_FONT);
        warning.setForeground(RS_MUTED);
        panel.add(warning);
        panel.add(Box.createVerticalStrut(5));

        JPanel presetRow = actionRow(2);
        JButton hideMounted = rsButton("Hide Mounted HUD");
        JButton restoreAll = rsButton("Restore All");
        presetRow.add(hideMounted);
        presetRow.add(restoreAll);
        panel.add(presetRow);
        panel.add(Box.createVerticalStrut(5));

        JLabel hint = new JLabel("Select NIS root slots:");
        hint.setFont(RS_SMALL_FONT);
        hint.setForeground(RS_TEXT);
        panel.add(hint);
        panel.add(Box.createVerticalStrut(3));

        hudList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        hudList.setVisibleRowCount(9);
        hudList.setFixedCellHeight(20);
        hudList.setFont(RS_SMALL_FONT);
        hudList.setForeground(RS_TEXT);
        hudList.setBackground(RS_INPUT);
        hudList.setSelectionForeground(RS_GOLD);
        hudList.setSelectionBackground(RS_SELECTED);
        hudList.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JScrollPane scroll = new JScrollPane(hudList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(RS_BORDER));
        scroll.setPreferredSize(new Dimension(300, 190));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 215));
        scroll.getViewport().setBackground(RS_INPUT);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(5));

        JPanel hudRow = actionRow(2);
        JButton hideSelected = rsButton("Hide Selected");
        JButton showSelected = rsButton("Show Selected");
        hudRow.add(hideSelected);
        hudRow.add(showSelected);
        panel.add(hudRow);

        rtsCameraButton.addActionListener(e ->
                setEditorCameraMode(ConstructionBuildCamera.CameraMode.RTS));
        freeCameraButton.addActionListener(e ->
                setEditorCameraMode(ConstructionBuildCamera.CameraMode.FREE_BUILD));
        slower.addActionListener(e -> {
            ConstructionBuildCamera.adjustRtsMoveSpeed(-1);
            syncCameraPanel();
        });
        faster.addActionListener(e -> {
            ConstructionBuildCamera.adjustRtsMoveSpeed(1);
            syncCameraPanel();
        });
        hideMounted.addActionListener(e -> {
            String error = ClientConsoleBridge.queueConsoleCommand(
                    "itembrowser editorhud safehide");
            if (error == null) {
                editorHudRequested = true;
                statusLabel.setText("HUD: hid currently mounted panes.");
            } else {
                statusLabel.setText("HUD command failed: " + error);
            }
        });
        restoreAll.addActionListener(e -> restoreEditorHud());
        hideSelected.addActionListener(e -> setSelectedHudComponentsHidden(true));
        showSelected.addActionListener(e -> setSelectedHudComponentsHidden(false));
        return panel;
    }

    private void setSelectedHudComponentsHidden(boolean hidden) {
        int[] selected = hudList.getSelectedIndices();
        if (selected == null || selected.length == 0) {
            statusLabel.setText("HUD: select one or more root slots first.");
            return;
        }
        String[] commands = new String[selected.length];
        for (int i = 0; i < selected.length; i++)
            commands[i] = "itembrowser editorhud "
                    + (hidden ? "hide " : "show ")
                    + HUD_ROOT_COMPONENTS[selected[i]];
        String error = ClientConsoleBridge.queueConsoleCommands(commands);
        if (error == null) {
            editorHudRequested = true;
            statusLabel.setText("HUD: " + (hidden ? "hid " : "showed ")
                    + selected.length + " selected slot(s).");
        } else statusLabel.setText("HUD command failed: " + error);
    }

    private void restoreEditorHud() {
        String error = ClientConsoleBridge.queueConsoleCommand("itembrowser editorhud restore");
        if (error == null) {
            editorHudRequested = false;
            statusLabel.setText("HUD: restored all editor-hidden components.");
        } else statusLabel.setText("HUD restore failed: " + error);
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
        JButton load = rsButton("Load Project   Ctrl+O");
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
            transformContextLabel.setText("WHOLE");
            loadWholeEditors();
        } else if (mode == LiveModelEditorPreview.SelectionMode.PART) {
            int selected = LiveModelEditorPreview.getSelectedPart();
            transformContextLabel.setText(selected >= 0 ? "PART " + selected : "PART");
            if (selected >= 0) {
                loadSelectedPartEditors();
            }
        } else {
            transformContextLabel.setText("MULTI x" + LiveModelEditorPreview.getSelectedPartCount());
            if (LiveModelEditorPreview.getSelectedPart() >= 0) {
                loadSelectedPartEditors();
            }
        }
        syncTransformInspector();
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

    private void syncSnapPanel() {
        boolean enabled = LiveModelEditorPreview.isTransformSnapEnabled();
        snapButton.setText(enabled ? "SNAP ON  [Ctrl = Free]" : "SNAP OFF  [Ctrl = Snap]");
        snapButton.setBackground(enabled ? RS_SELECTED : RS_PANEL_2);

        int moveStep = LiveModelEditorPreview.getMoveSnapStep();
        int angleStep = LiveModelEditorPreview.getAngleSnapDegrees();
        if (number(moveSnapSpinner) != moveStep) {
            moveSnapSpinner.setValue(Integer.valueOf(moveStep));
        }
        if (number(angleSnapSpinner) != angleStep) {
            angleSnapSpinner.setValue(Integer.valueOf(angleStep));
        }
    }


    private void syncControlState() {
        LiveModelEditorPreview.SelectionMode selection =
                LiveModelEditorPreview.getSelectionMode();
        LiveModelEditorPreview.TransformMode transform =
                LiveModelEditorPreview.getTransformMode();
        LiveModelEditorPreview.AxisConstraint axis =
                LiveModelEditorPreview.getAxisConstraint();

        setActiveButton(wholeModeButton, selection == LiveModelEditorPreview.SelectionMode.WHOLE);
        setActiveButton(partModeButton, selection == LiveModelEditorPreview.SelectionMode.PART);
        setActiveButton(multiModeButton, selection == LiveModelEditorPreview.SelectionMode.MULTI);
        setActiveButton(moveModeButton, transform == LiveModelEditorPreview.TransformMode.MOVE);
        setActiveButton(rotateModeButton, transform == LiveModelEditorPreview.TransformMode.ROTATE);
        setActiveButton(scaleModeButton, transform == LiveModelEditorPreview.TransformMode.SCALE);
        setActiveButton(freeAxisButton, axis == LiveModelEditorPreview.AxisConstraint.FREE);
        setActiveButton(xAxisButton, axis == LiveModelEditorPreview.AxisConstraint.X);
        setActiveButton(yAxisButton, axis == LiveModelEditorPreview.AxisConstraint.Y);
        setActiveButton(zAxisButton, axis == LiveModelEditorPreview.AxisConstraint.Z);
        setActiveButton(isolateButton, LiveModelEditorPreview.isPartIsolated());

        for (JButton button : railButtons) {
            Object tool = button.getClientProperty("toolName");
            setActiveButton(button, drawerExpanded && activeTool.equals(tool));
        }

        transformReadoutLabel.setText(buildTransformReadout());
    }

    private static void setActiveButton(JButton button, boolean active) {
        if (button == null) return;
        button.setBackground(active ? RS_SELECTED : RS_PANEL_2);
        button.setForeground(active ? RS_GOLD : RS_TEXT);
    }

    private String buildTransformReadout() {
        int[] transform = currentInspectorTransform();
        String context;
        LiveModelEditorPreview.SelectionMode selection =
                LiveModelEditorPreview.getSelectionMode();
        if (selection == LiveModelEditorPreview.SelectionMode.WHOLE) {
            context = "WHOLE";
        } else if (selection == LiveModelEditorPreview.SelectionMode.MULTI) {
            context = "MULTI x" + LiveModelEditorPreview.getSelectedPartCount();
        } else {
            int selected = LiveModelEditorPreview.getSelectedPart();
            context = selected >= 0 ? "PART " + selected : "PART";
        }

        LiveModelEditorPreview.TransformMode mode =
                LiveModelEditorPreview.getTransformMode();
        LiveModelEditorPreview.AxisConstraint axis =
                LiveModelEditorPreview.getAxisConstraint();
        if (mode == LiveModelEditorPreview.TransformMode.MOVE) {
            if (axis == LiveModelEditorPreview.AxisConstraint.X)
                return context + "  |  MOVE X  |  " + transform[3];
            if (axis == LiveModelEditorPreview.AxisConstraint.Y)
                return context + "  |  MOVE Y  |  " + transform[4];
            if (axis == LiveModelEditorPreview.AxisConstraint.Z)
                return context + "  |  MOVE Z  |  " + transform[5];
            return context + "  |  MOVE FREE  |  X " + transform[3] + "  Z " + transform[5];
        }
        if (mode == LiveModelEditorPreview.TransformMode.ROTATE) {
            return context + "  |  ROTATE  |  YAW " + transform[6] + " deg";
        }
        if (axis == LiveModelEditorPreview.AxisConstraint.X)
            return context + "  |  SCALE X  |  " + formatScaleRatio(transform[0]);
        if (axis == LiveModelEditorPreview.AxisConstraint.Y)
            return context + "  |  SCALE Y  |  " + formatScaleRatio(transform[1]);
        if (axis == LiveModelEditorPreview.AxisConstraint.Z)
            return context + "  |  SCALE Z  |  " + formatScaleRatio(transform[2]);
        return context + "  |  SCALE FREE  |  "
                + formatScaleRatio(transform[0]) + "/"
                + formatScaleRatio(transform[1]) + "/"
                + formatScaleRatio(transform[2]);
    }

    private JPanel createTransformInspector() {
        JPanel inspector = new JPanel();
        inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
        inspector.setOpaque(false);
        inspector.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel position = new JLabel("POSITION");
        position.setFont(RS_SMALL_FONT);
        position.setForeground(RS_MUTED);
        position.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspector.add(position);
        inspector.add(Box.createVerticalStrut(2));
        inspector.add(createInspectorAxisRow(
                new String[] { "X", "Y", "Z" },
                new JTextField[] { inspectorPosXField, inspectorPosYField, inspectorPosZField }));
        inspector.add(Box.createVerticalStrut(5));

        JLabel rotation = new JLabel("ROTATION");
        rotation.setFont(RS_SMALL_FONT);
        rotation.setForeground(RS_MUTED);
        rotation.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspector.add(rotation);
        inspector.add(Box.createVerticalStrut(2));
        inspector.add(createInspectorAxisRow(
                new String[] { "YAW" },
                new JTextField[] { inspectorYawField }));
        inspector.add(Box.createVerticalStrut(5));

        JLabel scale = new JLabel("SCALE");
        scale.setFont(RS_SMALL_FONT);
        scale.setForeground(RS_MUTED);
        scale.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspector.add(scale);
        inspector.add(Box.createVerticalStrut(2));
        inspector.add(createInspectorAxisRow(
                new String[] { "X", "Y", "Z" },
                new JTextField[] { inspectorScaleXField, inspectorScaleYField, inspectorScaleZField }));

        installInspectorField(inspectorPosXField);
        installInspectorField(inspectorPosYField);
        installInspectorField(inspectorPosZField);
        installInspectorField(inspectorYawField);
        installInspectorField(inspectorScaleXField);
        installInspectorField(inspectorScaleYField);
        installInspectorField(inspectorScaleZField);
        return inspector;
    }

    private JPanel createInspectorAxisRow(String[] labels, JTextField[] fields) {
        JPanel row = new JPanel(new GridLayout(1, labels.length, 5, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        for (int i = 0; i < labels.length; i++) {
            JPanel cell = new JPanel(new BorderLayout(4, 0));
            cell.setOpaque(false);
            JLabel label = new JLabel(labels[i]);
            label.setFont(RS_SMALL_FONT);
            label.setForeground(RS_GOLD_DIM);
            cell.add(label, BorderLayout.WEST);
            cell.add(fields[i], BorderLayout.CENTER);
            row.add(cell);
        }
        return row;
    }

    private void installInspectorField(final JTextField field) {
        field.setToolTipText(
                "Click/type exact. Drag horizontally to scrub. Shift=fine, Ctrl=coarse. Mouse wheel nudges.");
        field.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR));
        field.addActionListener(e -> {
            commitInspectorField(field);
            returnViewportFocusSoon();
        });
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                field.putClientProperty("editorStartValue", field.getText());
                field.selectAll();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commitInspectorField(field);
            }
        });
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelInspectorEdit");
        field.getActionMap().put("cancelInspectorEdit", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                Object start = field.getClientProperty("editorStartValue");
                if (start instanceof String) {
                    field.setText((String) start);
                }
                syncTransformInspector();
                returnViewportFocusSoon();
            }
        });

        MouseAdapter scrub = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || !field.isEnabled()) return;
                field.putClientProperty("scrubStartX", Integer.valueOf(e.getXOnScreen()));
                field.putClientProperty("scrubStartTransform", currentInspectorTransform());
                field.putClientProperty("scrubActive", Boolean.FALSE);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) == 0
                        || !field.isEnabled()) return;
                Object startXValue = field.getClientProperty("scrubStartX");
                Object startTransformValue = field.getClientProperty("scrubStartTransform");
                if (!(startXValue instanceof Integer) || !(startTransformValue instanceof int[])) return;

                int pixels = e.getXOnScreen() - ((Integer) startXValue).intValue();
                boolean active = Boolean.TRUE.equals(field.getClientProperty("scrubActive"));
                if (!active && Math.abs(pixels) < 3) return;

                if (!active) {
                    if (LiveModelEditorPreview.getSelectionMode()
                            != LiveModelEditorPreview.SelectionMode.WHOLE
                            && !LiveModelEditorPreview.beginSelectedPartTransformGesture()) {
                        return;
                    }
                    field.putClientProperty("scrubActive", Boolean.TRUE);
                }

                int[] transform = ((int[]) startTransformValue).clone();
                applyInspectorScrubDelta(field, transform, pixels,
                        e.isShiftDown(), e.isControlDown());
                applyInspectorScrubTransform(transform);
                field.setText(inspectorValueText(field, transform));
                syncTransformInspector();
                syncControlState();
                e.consume();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!Boolean.TRUE.equals(field.getClientProperty("scrubActive"))) return;
                if (LiveModelEditorPreview.getSelectionMode()
                        != LiveModelEditorPreview.SelectionMode.WHOLE) {
                    LiveModelEditorPreview.endSelectedPartTransformGesture();
                    loadSelectedPartEditors();
                }
                field.putClientProperty("scrubActive", Boolean.FALSE);
                field.putClientProperty("scrubStartTransform", null);
                field.putClientProperty("editorStartValue", field.getText());
                syncTransformInspector();
                syncControlState();
                statusLabel.setText("Scrubbed " + String.valueOf(
                        field.getClientProperty("transformKey")).replace('_', ' ').toLowerCase() + ".");
                returnViewportFocusSoon();
                e.consume();
            }
        };
        field.addMouseListener(scrub);
        field.addMouseMotionListener(scrub);
        field.addMouseWheelListener(e -> {
            if (!field.isEnabled()) return;
            int direction = e.getWheelRotation() < 0 ? 1 : -1;
            nudgeInspectorField(field, direction, e.isShiftDown(), e.isControlDown());
            e.consume();
        });
    }

    private void commitInspectorField(JTextField field) {
        if (suppressInspectorRefresh || field == null
                || Boolean.TRUE.equals(field.getClientProperty("scrubActive"))) return;
        if (LiveModelEditorPreview.getSelectionMode() != LiveModelEditorPreview.SelectionMode.WHOLE
                && LiveModelEditorPreview.getSelectedPart() < 0) {
            syncTransformInspector();
            statusLabel.setText("Select a part before editing transform values.");
            return;
        }

        String key = String.valueOf(field.getClientProperty("transformKey"));
        String text = field.getText() == null ? "" : field.getText().trim();
        int[] transform = LiveModelEditorPreview.getSelectionMode()
                == LiveModelEditorPreview.SelectionMode.WHOLE
                ? LiveModelEditorPreview.getWholeTransform()
                : LiveModelEditorPreview.getSelectedPartTransform();

        try {
            if (key.startsWith("SCALE_")) {
                double ratio = Double.parseDouble(text);
                int internal = clamp((int) Math.round(ratio * 100.0), 10, 400);
                if ("SCALE_X".equals(key)) transform[0] = internal;
                else if ("SCALE_Y".equals(key)) transform[1] = internal;
                else transform[2] = internal;
            } else {
                int value = Integer.parseInt(text);
                if ("POS_X".equals(key)) transform[3] = clamp(value, -4096, 4096);
                else if ("POS_Y".equals(key)) transform[4] = clamp(value, -4096, 4096);
                else if ("POS_Z".equals(key)) transform[5] = clamp(value, -4096, 4096);
                else if ("YAW".equals(key)) transform[6] = normalizeInspectorYaw(value);
            }

            applyInspectorTransform(transform);
            field.putClientProperty("editorStartValue", null);
            statusLabel.setText("Applied exact " + key.replace('_', ' ').toLowerCase() + ".");
        } catch (NumberFormatException ex) {
            Object start = field.getClientProperty("editorStartValue");
            if (start instanceof String) {
                field.setText((String) start);
            }
            statusLabel.setText("Invalid transform value; previous value restored.");
        }
        syncTransformInspector();
    }

    private int[] currentInspectorTransform() {
        int[] transform = LiveModelEditorPreview.getSelectionMode()
                == LiveModelEditorPreview.SelectionMode.WHOLE
                ? LiveModelEditorPreview.getWholeTransform()
                : LiveModelEditorPreview.getSelectedPartTransform();
        return transform == null ? new int[] { 100, 100, 100, 0, 0, 0, 0 }
                : transform.clone();
    }

    private void applyInspectorScrubDelta(JTextField field, int[] transform,
            int pixels, boolean fine, boolean coarse) {
        String key = String.valueOf(field.getClientProperty("transformKey"));
        if (key.startsWith("POS_")) {
            int delta = coarse ? pixels * 16 : fine ? pixels / 2 : pixels * 2;
            if ("POS_X".equals(key)) transform[3] = clamp(transform[3] + delta, -4096, 4096);
            else if ("POS_Y".equals(key)) transform[4] = clamp(transform[4] + delta, -4096, 4096);
            else transform[5] = clamp(transform[5] + delta, -4096, 4096);
        } else if ("YAW".equals(key)) {
            int delta = coarse ? pixels * 5 : fine ? pixels / 4 : pixels;
            transform[6] = normalizeInspectorYaw(transform[6] + delta);
        } else if (key.startsWith("SCALE_")) {
            int delta = coarse ? pixels * 5 : fine ? pixels / 4 : pixels;
            if ("SCALE_X".equals(key)) transform[0] = clamp(transform[0] + delta, 10, 400);
            else if ("SCALE_Y".equals(key)) transform[1] = clamp(transform[1] + delta, 10, 400);
            else transform[2] = clamp(transform[2] + delta, 10, 400);
        }
    }

    private void applyInspectorScrubTransform(int[] transform) {
        if (LiveModelEditorPreview.getSelectionMode()
                == LiveModelEditorPreview.SelectionMode.WHOLE) {
            applyInspectorTransform(transform);
            return;
        }
        LiveModelEditorPreview.updateSelectedPartTransformGesture(
                transform[0], transform[1], transform[2],
                transform[3], transform[4], transform[5], transform[6]);
    }

    private void nudgeInspectorField(JTextField field, int direction,
            boolean fine, boolean coarse) {
        int[] transform = currentInspectorTransform();
        String key = String.valueOf(field.getClientProperty("transformKey"));
        if (key.startsWith("POS_")) {
            int step = coarse ? 16 : fine ? 1 : 4;
            if ("POS_X".equals(key)) transform[3] = clamp(transform[3] + direction * step, -4096, 4096);
            else if ("POS_Y".equals(key)) transform[4] = clamp(transform[4] + direction * step, -4096, 4096);
            else transform[5] = clamp(transform[5] + direction * step, -4096, 4096);
        } else if ("YAW".equals(key)) {
            int step = coarse ? 15 : fine ? 1 : 5;
            transform[6] = normalizeInspectorYaw(transform[6] + direction * step);
        } else if (key.startsWith("SCALE_")) {
            int step = coarse ? 10 : 1;
            if ("SCALE_X".equals(key)) transform[0] = clamp(transform[0] + direction * step, 10, 400);
            else if ("SCALE_Y".equals(key)) transform[1] = clamp(transform[1] + direction * step, 10, 400);
            else transform[2] = clamp(transform[2] + direction * step, 10, 400);
        }
        applyInspectorTransform(transform);
        syncTransformInspector();
        syncControlState();
    }

    private static String inspectorValueText(JTextField field, int[] transform) {
        String key = String.valueOf(field.getClientProperty("transformKey"));
        if ("POS_X".equals(key)) return Integer.toString(transform[3]);
        if ("POS_Y".equals(key)) return Integer.toString(transform[4]);
        if ("POS_Z".equals(key)) return Integer.toString(transform[5]);
        if ("YAW".equals(key)) return Integer.toString(transform[6]);
        if ("SCALE_X".equals(key)) return formatScaleRatio(transform[0]);
        if ("SCALE_Y".equals(key)) return formatScaleRatio(transform[1]);
        if ("SCALE_Z".equals(key)) return formatScaleRatio(transform[2]);
        return "";
    }

    private void applyInspectorTransform(int[] transform) {
        if (transform == null || transform.length < 7) return;
        if (LiveModelEditorPreview.getSelectionMode() == LiveModelEditorPreview.SelectionMode.WHOLE) {
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
            refreshPreview();
            return;
        }

        if (LiveModelEditorPreview.getSelectedPart() >= 0
                && LiveModelEditorPreview.setSelectedPartTransform(
                        transform[0], transform[1], transform[2],
                        transform[3], transform[4], transform[5], transform[6])) {
            loadSelectedPartEditors();
            refreshPartList();
        }
    }

    private void syncTransformInspector() {
        if (suppressInspectorRefresh) return;
        int[] transform = LiveModelEditorPreview.getSelectionMode()
                == LiveModelEditorPreview.SelectionMode.WHOLE
                ? LiveModelEditorPreview.getWholeTransform()
                : LiveModelEditorPreview.getSelectedPartTransform();

        boolean editable = LiveModelEditorPreview.getSelectionMode()
                == LiveModelEditorPreview.SelectionMode.WHOLE
                || LiveModelEditorPreview.getSelectedPart() >= 0;
        suppressInspectorRefresh = true;
        try {
            setInspectorFieldsEnabled(editable);
            setInspectorText(inspectorScaleXField, formatScaleRatio(transform[0]));
            setInspectorText(inspectorScaleYField, formatScaleRatio(transform[1]));
            setInspectorText(inspectorScaleZField, formatScaleRatio(transform[2]));
            setInspectorText(inspectorPosXField, Integer.toString(transform[3]));
            setInspectorText(inspectorPosYField, Integer.toString(transform[4]));
            setInspectorText(inspectorPosZField, Integer.toString(transform[5]));
            setInspectorText(inspectorYawField, Integer.toString(transform[6]));
        } finally {
            suppressInspectorRefresh = false;
        }
    }

    private void setInspectorFieldsEnabled(boolean enabled) {
        inspectorPosXField.setEnabled(enabled);
        inspectorPosYField.setEnabled(enabled);
        inspectorPosZField.setEnabled(enabled);
        inspectorYawField.setEnabled(enabled);
        inspectorScaleXField.setEnabled(enabled);
        inspectorScaleYField.setEnabled(enabled);
        inspectorScaleZField.setEnabled(enabled);
    }

    private static void setInspectorText(JTextField field, String value) {
        if (field == null || field.isFocusOwner()) return;
        if (!value.equals(field.getText())) {
            field.setText(value);
        }
    }

    private static String formatScaleRatio(int internalPercent) {
        return String.format(java.util.Locale.US, "%.2f", internalPercent / 100.0);
    }

    private static int normalizeInspectorYaw(int value) {
        int normalized = value % 360;
        if (normalized > 359) normalized -= 360;
        if (normalized < -359) normalized += 360;
        return normalized;
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

        moveSnapSpinner.addChangeListener(e ->
                LiveModelEditorPreview.setMoveSnapStep(number(moveSnapSpinner)));
        angleSnapSpinner.addChangeListener(e ->
                LiveModelEditorPreview.setAngleSnapDegrees(number(angleSnapSpinner)));

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
            syncTransformInspector();
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
        syncTransformInspector();
        statusLabel.setText("Resumed existing edit session for " + objectName + " #" + objectId + ".");
    }

    private static void closeEditorSession() {
        LiveModelEditorPreview.clearPartPreview();
        LiveModelEditorPreview.hide();
        modelLeftDragActive = false;
        editorCtrlDown = false;
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

    private static void exitEditorHud() {
        if (!editorHudRequested) return;
        ClientConsoleBridge.queueConsoleCommand("itembrowser editorhud restore");
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
                        boolean ctrl = mouse.isControlDown() || editorCtrlDown;
                        modelLeftDragActive = LiveModelEditorPreview.beginPointerDrag(
                                mouse.getX(), mouse.getY(),
                                mouse.isShiftDown() || ctrl, ctrl);
                        mouse.consume();
                        if (instance != null) instance.syncRuntimeState();
                        return;
                    }
                    if (id == MouseEvent.MOUSE_DRAGGED) {
                        if (modelLeftDragActive) {
                            LiveModelEditorPreview.dragPointerTo(
                                    mouse.getX(), mouse.getY(),
                                    mouse.isControlDown() || editorCtrlDown);
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
                    if (key.getKeyCode() == KeyEvent.VK_CONTROL) {
                        if (key.getID() == KeyEvent.KEY_PRESSED) {
                            editorCtrlDown = true;
                        } else if (key.getID() == KeyEvent.KEY_RELEASED) {
                            editorCtrlDown = false;
                        }
                    }
                    if (key.getID() != KeyEvent.KEY_PRESSED) {
                        return;
                    }
                    if (isEditorTextEntryFocused() || isEditorModalDialogActive()) {
                        return;
                    }
                    if (handleEditorShortcut(key)) {
                        key.consume();
                        if (instance != null) instance.syncRuntimeState();
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        inputGateInstalled = true;
    }

    private static boolean handleEditorShortcut(KeyEvent key) {
        int code = key.getKeyCode();
        boolean ctrl = key.isControlDown() || editorCtrlDown;

        if (code == KeyEvent.VK_ESCAPE) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    closeEditorSession();
                }
            });
            return true;
        }

        if (key.isAltDown()
                && (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT
                || code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN)) {
            if (instance != null) instance.nudgeTransform(code, key.isShiftDown());
            return true;
        }

        if (ctrl && code == KeyEvent.VK_A) {
            if (instance != null) {
                instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
                LiveModelEditorPreview.selectAllParts();
                instance.refreshPartList();
            }
            return true;
        }
        if (ctrl && code == KeyEvent.VK_S) {
            if (instance != null) {
                if (key.isShiftDown()) instance.saveSelectionAsset();
                else instance.saveProject();
            }
            return true;
        }
        if (ctrl && code == KeyEvent.VK_O) {
            if (instance != null) instance.loadProject();
            return true;
        }
        if (ctrl && code == KeyEvent.VK_Z) {
            if (instance != null) instance.undoPartEdit();
            return true;
        }
        if (ctrl && code == KeyEvent.VK_D) {
            if (instance != null) instance.duplicateSelected();
            return true;
        }

        if (code == KeyEvent.VK_TAB) {
            if (instance != null) instance.toggleDrawer();
            return true;
        }
        if (code == KeyEvent.VK_H && key.isShiftDown()) {
            if (instance != null) instance.showAllParts();
            return true;
        }
        if (code == KeyEvent.VK_I) {
            if (instance != null) instance.toggleIsolateSelection();
            return true;
        }

        if (code == KeyEvent.VK_1) {
            if (instance != null) instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE);
            else LiveModelEditorPreview.setSelectionMode(LiveModelEditorPreview.SelectionMode.WHOLE);
            return true;
        }
        if (code == KeyEvent.VK_2) {
            if (instance != null) instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.PART);
            else LiveModelEditorPreview.setSelectionMode(LiveModelEditorPreview.SelectionMode.PART);
            return true;
        }
        if (code == KeyEvent.VK_3) {
            if (instance != null) instance.setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
            else LiveModelEditorPreview.setSelectionMode(LiveModelEditorPreview.SelectionMode.MULTI);
            return true;
        }
        if (code == KeyEvent.VK_G) {
            if (instance != null) instance.setEditMode(LiveModelEditorPreview.TransformMode.MOVE);
            else LiveModelEditorPreview.setTransformMode(LiveModelEditorPreview.TransformMode.MOVE);
            return true;
        }
        if (code == KeyEvent.VK_R) {
            if (instance != null) instance.setEditMode(LiveModelEditorPreview.TransformMode.ROTATE);
            else LiveModelEditorPreview.setTransformMode(LiveModelEditorPreview.TransformMode.ROTATE);
            return true;
        }
        if (code == KeyEvent.VK_V) {
            if (instance != null) instance.setEditMode(LiveModelEditorPreview.TransformMode.SCALE);
            else LiveModelEditorPreview.setTransformMode(LiveModelEditorPreview.TransformMode.SCALE);
            return true;
        }
        if (code == KeyEvent.VK_X) {
            if (instance != null) instance.setEditAxis(LiveModelEditorPreview.AxisConstraint.X);
            else LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.X);
            return true;
        }
        if (code == KeyEvent.VK_Y) {
            if (instance != null) instance.setEditAxis(LiveModelEditorPreview.AxisConstraint.Y);
            else LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.Y);
            return true;
        }
        if (code == KeyEvent.VK_Z) {
            if (instance != null) instance.setEditAxis(LiveModelEditorPreview.AxisConstraint.Z);
            else LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.Z);
            return true;
        }
        if (code == KeyEvent.VK_F) {
            if (instance != null) instance.setEditAxis(LiveModelEditorPreview.AxisConstraint.FREE);
            else LiveModelEditorPreview.setAxisConstraint(LiveModelEditorPreview.AxisConstraint.FREE);
            return true;
        }
        if (code == KeyEvent.VK_H) {
            if (instance != null) instance.toggleSelectedHidden();
            return true;
        }
        if (code == KeyEvent.VK_DELETE) {
            if (instance != null) instance.deleteSelected();
            return true;
        }
        return false;
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
        syncTransformInspector();
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
        syncSnapPanel();
        syncControlState();
        int hovered = LiveModelEditorPreview.getWorldHoveredPart();
        partStatusLabel.setText(LiveModelEditorPreview.getSelectionMode() + " | "
                + LiveModelEditorPreview.getSelectedPartCount() + " selected"
                + (hovered >= 0 ? " | HOVER P" + hovered : "")
                + " | " + LiveModelEditorPreview.getTransformMode() + " "
                + LiveModelEditorPreview.getAxisConstraint()
                + (LiveModelEditorPreview.isTransformSnapEnabled() ? " | SNAP" : " | FREE")
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

    private void toggleIsolateSelection() {
        LiveModelEditorPreview.toggleIsolatePart();
        refreshPartList();
        syncControlState();
        statusLabel.setText(LiveModelEditorPreview.isPartIsolated()
                ? "Solo/Isolate enabled for current selection."
                : "Solo/Isolate disabled.");
    }

    private void showAllParts() {
        if (LiveModelEditorPreview.showAllParts()) {
            refreshPartList();
            statusLabel.setText("All model parts visible.");
        }
    }

    private void nudgeTransform(int keyCode, boolean coarse) {
        int[] transform = currentInspectorTransform();
        LiveModelEditorPreview.TransformMode mode =
                LiveModelEditorPreview.getTransformMode();
        LiveModelEditorPreview.AxisConstraint axis =
                LiveModelEditorPreview.getAxisConstraint();
        boolean positive = keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_UP;

        if (mode == LiveModelEditorPreview.TransformMode.MOVE) {
            int step = coarse ? 16 : 1;
            int delta = positive ? step : -step;
            if (axis == LiveModelEditorPreview.AxisConstraint.X) transform[3] += delta;
            else if (axis == LiveModelEditorPreview.AxisConstraint.Y) transform[4] += delta;
            else if (axis == LiveModelEditorPreview.AxisConstraint.Z) transform[5] += delta;
            else if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT)
                transform[3] += delta;
            else transform[5] += delta;
            transform[3] = clamp(transform[3], -4096, 4096);
            transform[4] = clamp(transform[4], -4096, 4096);
            transform[5] = clamp(transform[5], -4096, 4096);
        } else if (mode == LiveModelEditorPreview.TransformMode.ROTATE) {
            int step = coarse ? 15 : 1;
            transform[6] = normalizeInspectorYaw(transform[6] + (positive ? step : -step));
        } else {
            int step = coarse ? 10 : 1;
            int delta = positive ? step : -step;
            if (axis == LiveModelEditorPreview.AxisConstraint.X) transform[0] += delta;
            else if (axis == LiveModelEditorPreview.AxisConstraint.Y) transform[1] += delta;
            else if (axis == LiveModelEditorPreview.AxisConstraint.Z) transform[2] += delta;
            else {
                transform[0] += delta;
                transform[1] += delta;
                transform[2] += delta;
            }
            transform[0] = clamp(transform[0], 10, 400);
            transform[1] = clamp(transform[1], 10, 400);
            transform[2] = clamp(transform[2], 10, 400);
        }

        applyInspectorTransform(transform);
        syncTransformInspector();
        syncControlState();
        statusLabel.setText("Nudged " + mode + " " + axis
                + (coarse ? " (coarse)." : "."));
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
            syncTransformInspector();
            statusLabel.setText("Reset whole-model transform.");
            return;
        }
        if (LiveModelEditorPreview.resetSelectedPartTransforms()) {
            loadSelectedPartEditors();
            refreshPartList();
            syncTransformInspector();
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
            syncTransformInspector();
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


    private static JTextField inspectorField(String key) {
        JTextField field = new JTextField();
        field.putClientProperty("transformKey", key);
        field.setFont(RS_SMALL_FONT);
        field.setForeground(RS_TEXT);
        field.setBackground(RS_INPUT);
        field.setCaretColor(RS_GOLD);
        field.setHorizontalAlignment(JTextField.RIGHT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RS_BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        field.setPreferredSize(new Dimension(72, 24));
        field.setMinimumSize(new Dimension(48, 24));
        return field;
    }

    private static boolean isEditorTextEntryFocused() {
        Component focus = java.awt.KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus == null) return false;
        if (focus instanceof JTextField) return true;
        for (Component current = focus; current != null; current = current.getParent()) {
            if (current instanceof JSpinner || current instanceof JComboBox) return true;
        }
        return false;
    }

    private static boolean isEditorModalDialogActive() {
        Window active = java.awt.KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getActiveWindow();
        return active instanceof java.awt.Dialog
                && ((java.awt.Dialog) active).isModal();
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
