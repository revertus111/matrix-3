package game;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import game.ConstructionPlacementController.BuildPiece;
import game.ConstructionPlacementController.Category;
import game.ConstructionPlacementController.HoverTile;
import game.ConstructionPlacementController.PlacementMode;

/**
 * Custom-drawn in-game Construction object palette.
 *
 * The palette is hosted in a small owned JWindow above Matrix3's heavyweight
 * game Canvas. This avoids racing the renderer through Canvas.getGraphics(),
 * which is not persistent and visibly flickers as the game repaints.
 *
 * The window covers only the palette rectangle, so normal world input remains
 * owned by Matrix3 everywhere else. World placement still routes through
 * ConstructionPlacementController -> DevModeBridge -> server devspawn.
 *
 * The true 3D ghost is intentionally not faked here; the palette only exposes
 * the verified hovered world tile until a safe scene-render seam is proven.
 */
public final class ConstructionPaletteOverlay {

    private static final int FRAME_MS = 33;
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 492;
    private static final int CARD_HEIGHT = 66;
    private static final int CARD_GAP = 7;
    private static final int MAX_VISIBLE_CARDS = 4;

    private static final Color PANEL = new Color(19, 23, 29);
    private static final Color CARD = new Color(34, 40, 49);
    private static final Color CARD_SELECTED = new Color(58, 83, 112);
    private static final Color BORDER = new Color(73, 84, 98);
    private static final Color TEXT = new Color(236, 239, 243);
    private static final Color MUTED = new Color(165, 174, 185);
    private static final Color ACCENT = new Color(111, 174, 235);
    private static final Color INPUT = new Color(22, 27, 34);
    private static final Color BUTTON = new Color(44, 51, 62);
    private static final Color BUTTON_ACTIVE = new Color(78, 123, 169);

    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 16);
    private static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font BOLD_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 11);
    private static final BasicStroke SELECTED_STROKE = new BasicStroke(2.0F);

    private static volatile boolean visible;
    private static volatile Category category = Category.WALLS;
    private static volatile String search = "";
    private static volatile boolean searchFocused;
    private static volatile int scrollOffset;
    private static volatile LayoutSnapshot latestLayout = LayoutSnapshot.empty();
    private static volatile long clearBuildsConfirmUntil;

    private static Timer paintTimer;
    private static boolean inputListenerInstalled;
    private static JWindow paletteWindow;
    private static PaletteSurface paletteSurface;
    private static Window paletteOwner;

    private ConstructionPaletteOverlay() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void installRtsInputListener() {
        ensureInputListener();
    }

    public static void show() {
        visible = true;
        searchFocused = false;
        scrollOffset = 0;
        ConstructionPlacementController.beginPaletteSession();
        ensureInputListener();
        updateTimerState();
    }

    public static void hide() {
        hide(true);
    }

    private static void hide(boolean cancelPlacement) {
        visible = false;
        searchFocused = false;
        ConstructionPlacementController.endPaletteSession(cancelPlacement);
        updateTimerState();
    }

    private static void updateTimerState() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateTimerState();
                }
            });
            return;
        }

        if (visible) {
            if (paintTimer == null) {
                paintTimer = new Timer(FRAME_MS, e -> refreshWindow());
                paintTimer.setCoalesce(true);
            }
            if (!paintTimer.isRunning()) {
                paintTimer.start();
            }
            refreshWindow();
        } else {
            if (paintTimer != null) {
                paintTimer.stop();
            }
            hideWindowSurface();
        }
    }

    private static synchronized void ensureInputListener() {
        if (inputListenerInstalled) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override
                public void eventDispatched(AWTEvent event) {
                    if (event instanceof MouseWheelEvent) {
                        if (visible || ConstructionBuildCamera.isSettlementAutoMode()) {
                            handleWorldWheel((MouseWheelEvent) event);
                        }
                        return;
                    }
                    if ((visible || ConstructionBuildCamera.isSettlementAutoMode()) && event instanceof KeyEvent) {
                        handleKey((KeyEvent) event);
                    }
                }
            }, AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
            inputListenerInstalled = true;
        } catch (RuntimeException ex) {
            visible = false;
            ConstructionPlacementController.endPaletteSession(true);
            hideWindowSurface();
        }
    }

    private static void refreshWindow() {
        if (!visible) {
            hideWindowSurface();
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            hideWindowSurface();
            return;
        }

        if (!ensureWindow(canvas)) {
            hideWindowSurface();
            return;
        }

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            hideWindowSurface();
            return;
        }

        int availableWidth = Math.max(1, canvas.getWidth() - 20);
        int availableHeight = Math.max(1, canvas.getHeight() - 20);
        int width = Math.min(PANEL_WIDTH, availableWidth);
        int height = Math.min(PANEL_HEIGHT, availableHeight);
        int localX = Math.min(10, Math.max(0, canvas.getWidth() - width));
        int localY = Math.max(0, Math.min(54, canvas.getHeight() - height));
        Rectangle desired = new Rectangle(canvasLocation.x + localX, canvasLocation.y + localY, width, height);

        if (!desired.equals(paletteWindow.getBounds())) {
            paletteWindow.setBounds(desired);
        }
        if (!paletteWindow.isVisible()) {
            paletteWindow.setVisible(true);
        }
        paletteSurface.repaint();
    }

    private static boolean ensureWindow(Canvas canvas) {
        Window owner = SwingUtilities.getWindowAncestor(canvas);
        if (owner == null) {
            return false;
        }
        if (paletteWindow != null && paletteOwner == owner) {
            return true;
        }

        if (paletteWindow != null) {
            paletteWindow.dispose();
        }

        paletteOwner = owner;
        paletteSurface = new PaletteSurface();
        paletteSurface.setOpaque(true);
        paletteSurface.setBackground(PANEL);
        paletteSurface.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (visible && event.getButton() == MouseEvent.BUTTON1) {
                    handlePaletteClick(event.getX(), event.getY());
                    event.consume();
                }
            }
        });
        paletteSurface.addMouseWheelListener(event -> {
            if (visible) {
                handlePaletteWheel(event);
            }
        });

        paletteWindow = new JWindow(owner);
        paletteWindow.setFocusableWindowState(false);
        paletteWindow.setAutoRequestFocus(false);
        paletteWindow.getContentPane().setLayout(new BorderLayout());
        paletteWindow.getContentPane().add(paletteSurface, BorderLayout.CENTER);
        return true;
    }

    private static void hideWindowSurface() {
        if (paletteWindow != null && paletteWindow.isVisible()) {
            paletteWindow.setVisible(false);
        }
    }

    private static void handlePaletteClick(int x, int y) {
        LayoutSnapshot layout = latestLayout;
        if (!layout.panel.contains(x, y)) {
            searchFocused = false;
            return;
        }
        if (layout.close.contains(x, y)) {
            hide(true);
            return;
        }
        if (layout.search.contains(x, y)) {
            searchFocused = true;
            return;
        }
        searchFocused = false;

        if (layout.cameraFree.contains(x, y)) {
            ConstructionBuildCamera.setMode(ConstructionBuildCamera.CameraMode.FREE_BUILD);
            repaintSurface();
            return;
        }
        if (layout.cameraRts.contains(x, y)) {
            ConstructionBuildCamera.setMode(ConstructionBuildCamera.CameraMode.RTS);
            repaintSurface();
            return;
        }
        if (layout.rtsSpeedDown.contains(x, y)) {
            ConstructionBuildCamera.adjustRtsMoveSpeed(-1);
            repaintSurface();
            return;
        }
        if (layout.rtsSpeedUp.contains(x, y)) {
            ConstructionBuildCamera.adjustRtsMoveSpeed(1);
            repaintSurface();
            return;
        }

        for (int i = 0; i < layout.tabs.length; i++) {
            if (layout.tabs[i].contains(x, y)) {
                category = Category.values()[i];
                scrollOffset = 0;
                repaintSurface();
                return;
            }
        }
        for (CardHitbox card : layout.cards) {
            if (card.bounds.contains(x, y)) {
                ConstructionPlacementController.select(card.piece);
                repaintSurface();
                return;
            }
        }
        if (layout.rotateLeft.contains(x, y)) {
            ConstructionPlacementController.rotate(-1);
            repaintSurface();
            return;
        }
        if (layout.rotateRight.contains(x, y)) {
            ConstructionPlacementController.rotate(1);
            repaintSurface();
            return;
        }
        if (layout.paintMode.contains(x, y)) {
            ConstructionPlacementController.setPlacementMode(PlacementMode.PAINT);
            repaintSurface();
            return;
        }
        if (layout.continuousMode.contains(x, y)) {
            ConstructionPlacementController.setPlacementMode(PlacementMode.CONTINUOUS);
            repaintSurface();
            return;
        }
        if (layout.eraser.contains(x, y)) {
            ConstructionPlacementController.setEraserMode(!ConstructionPlacementController.isEraserMode());
            clearBuildsConfirmUntil = 0L;
            repaintSurface();
            return;
        }
        if (layout.clearBuilds.contains(x, y)) {
            long now = System.currentTimeMillis();
            if (now <= clearBuildsConfirmUntil) {
                ClientConsoleBridge.queueConsoleCommand("itembrowser settlement clearbuilds");
                /*
                 * Clear Builds destroys the server-owned physical rails. Reset
                 * the client topology in the same confirmed action so the next
                 * rail commit cannot try to atomically replace rails that no
                 * longer exist on the server.
                 */
                RailRoutePreview.clearRoute();
                clearBuildsConfirmUntil = 0L;
            } else {
                clearBuildsConfirmUntil = now + 3500L;
            }
            repaintSurface();
            return;
        }
        if (layout.railDebug.contains(x, y) && ConstructionPlacementController.isRailRouteSelected()) {
            RailRoutePreview.setDebugEnabled(!RailRoutePreview.isDebugEnabled());
            repaintSurface();
            return;
        }
        if (layout.copyRailDebug.contains(x, y) && ConstructionPlacementController.isRailRouteSelected()) {
            RailRoutePreview.copyDebugReportToClipboard();
            repaintSurface();
            return;
        }
        if (layout.cancel.contains(x, y)) {
            ConstructionPlacementController.cancel();
            repaintSurface();
        }
    }

    private static void handlePaletteWheel(MouseWheelEvent event) {
        int wheel = event.getWheelRotation();
        if (wheel == 0) {
            return;
        }
        LayoutSnapshot layout = latestLayout;
        int max = Math.max(0, layout.totalMatchingPieces - MAX_VISIBLE_CARDS);
        scrollOffset = clamp(scrollOffset + (wheel > 0 ? 1 : -1), 0, max);
        event.consume();
        repaintSurface();
    }

    private static void handleWorldWheel(MouseWheelEvent event) {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || event.getSource() != canvas) {
            return;
        }
        int wheel = event.getWheelRotation();
        if (wheel == 0) {
            return;
        }
        if (ConstructionBuildCamera.handleWorldWheel(wheel)) {
            event.consume();
            repaintSurface();
            return;
        }
        if (ConstructionPlacementController.isArmed()) {
            ConstructionPlacementController.rotate(wheel > 0 ? 1 : -1);
            event.consume();
            repaintSurface();
        }
    }

    private static void handleKey(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            if (event.getKeyCode() == KeyEvent.VK_Z && event.isControlDown()
                    && ConstructionBuildCamera.isSettlementAutoMode()) {
                ClientConsoleBridge.queueConsoleCommand("itembrowser settlement undo");
                event.consume();
                repaintSurface();
                return;
            }
            if (!visible) {
                return;
            }
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                if (searchFocused) {
                    searchFocused = false;
                    repaintSurface();
                } else {
                    hide(true);
                }
                event.consume();
                return;
            }
            if (searchFocused) {
                if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE && search.length() > 0) {
                    search = search.substring(0, search.length() - 1);
                    scrollOffset = 0;
                    event.consume();
                    repaintSurface();
                } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    searchFocused = false;
                    event.consume();
                    repaintSurface();
                }
                return;
            }
            if (event.getKeyCode() == KeyEvent.VK_R && ConstructionPlacementController.isArmed()) {
                ConstructionPlacementController.rotate(event.isShiftDown() ? -1 : 1);
                event.consume();
                repaintSurface();
            }
        } else if (event.getID() == KeyEvent.KEY_TYPED && searchFocused) {
            char c = event.getKeyChar();
            if (!Character.isISOControl(c) && search.length() < 32) {
                search += c;
                scrollOffset = 0;
                event.consume();
                repaintSurface();
            }
        }
    }

    private static void repaintSurface() {
        if (paletteSurface != null) {
            paletteSurface.repaint();
        }
    }

    private static LayoutSnapshot buildLayout(int width, int height) {
        Rectangle panel = new Rectangle(0, 0, width, height);
        Rectangle close = new Rectangle(width - 36, 10, 24, 24);
        Rectangle cameraFree = new Rectangle(width - 218, 10, 44, 24);
        Rectangle cameraRts = new Rectangle(width - 170, 10, 68, 24);
        Rectangle rtsSpeedDown = new Rectangle(width - 96, 10, 24, 24);
        Rectangle rtsSpeedUp = new Rectangle(width - 66, 10, 24, 24);
        Rectangle searchBox = new Rectangle(14, 48, width - 28, 32);

        Rectangle[] tabs = new Rectangle[Category.values().length];
        int tabY = 90;
        int tabWidth = (width - 28 - (tabs.length - 1) * 6) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i] = new Rectangle(14 + i * (tabWidth + 6), tabY, tabWidth, 30);
        }

        List<BuildPiece> matches = matchingPieces();
        int maxOffset = Math.max(0, matches.size() - MAX_VISIBLE_CARDS);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        List<CardHitbox> cards = new ArrayList<CardHitbox>();
        int cardY = 132;
        int end = Math.min(matches.size(), scrollOffset + MAX_VISIBLE_CARDS);
        for (int i = scrollOffset; i < end; i++) {
            Rectangle bounds = new Rectangle(14, cardY, width - 28, CARD_HEIGHT);
            cards.add(new CardHitbox(bounds, matches.get(i)));
            cardY += CARD_HEIGHT + CARD_GAP;
        }

        int controlsY = height - 104;
        Rectangle rotateLeft = new Rectangle(14, controlsY, 86, 30);
        Rectangle rotateRight = new Rectangle(106, controlsY, 86, 30);
        Rectangle paintMode = new Rectangle(202, controlsY, 66, 30);
        Rectangle continuous = new Rectangle(274, controlsY, Math.max(1, width - 288), 30);
        Rectangle eraser = new Rectangle(14, height - 38, 52, 26);
        Rectangle clearBuilds = new Rectangle(70, height - 38, 58, 26);
        Rectangle railDebug = new Rectangle(132, height - 38, 58, 26);
        Rectangle copyRailDebug = new Rectangle(194, height - 38, 58, 26);
        Rectangle cancel = new Rectangle(width - 72, height - 38, 58, 26);
        return new LayoutSnapshot(panel, close, cameraFree, cameraRts, rtsSpeedDown, rtsSpeedUp,
                searchBox, tabs, cards.toArray(new CardHitbox[cards.size()]),
                rotateLeft, rotateRight, paintMode, continuous,
                eraser, clearBuilds, railDebug, copyRailDebug, cancel, matches.size());
    }

    private static List<BuildPiece> matchingPieces() {
        List<BuildPiece> matches = new ArrayList<BuildPiece>();
        String filter = search.trim();
        for (BuildPiece piece : ConstructionPlacementController.getPieces()) {
            if ((filter.length() > 0 || piece.getCategory() == category) && piece.matches(filter)) {
                matches.add(piece);
            }
        }
        return matches;
    }

    private static void paintPanel(Graphics2D g, LayoutSnapshot layout) {
        Rectangle panel = layout.panel;
        g.setColor(PANEL);
        g.fillRect(panel.x, panel.y, panel.width, panel.height);
        g.setColor(BORDER);
        g.drawRect(panel.x, panel.y, Math.max(0, panel.width - 1), Math.max(0, panel.height - 1));

        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        g.drawString("CONSTRUCTION", panel.x + 14, panel.y + 28);
        g.setFont(BOLD_FONT);
        g.setColor(MUTED);
        centerText(g, "X", layout.close);
        paintButton(g, layout.cameraFree, "Free",
                ConstructionBuildCamera.getMode() == ConstructionBuildCamera.CameraMode.FREE_BUILD);
        paintButton(g, layout.cameraRts, "RTS " + ConstructionBuildCamera.getRtsMoveSpeedLabel(),
                ConstructionBuildCamera.getMode() == ConstructionBuildCamera.CameraMode.RTS);
        paintButton(g, layout.rtsSpeedDown, "-", false);
        paintButton(g, layout.rtsSpeedUp, "+", false);

        paintSearch(g, layout.search);
        paintTabs(g, layout.tabs);
        paintCards(g, layout.cards);
        if (ConstructionPlacementController.isRailRouteSelected() && RailRoutePreview.isDebugEnabled()) {
            paintRailDebugEvidence(g, layout);
        }
        paintControls(g, layout);
    }

    private static void paintSearch(Graphics2D g, Rectangle bounds) {
        g.setColor(INPUT);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 7, 7);
        g.setColor(searchFocused ? ACCENT : BORDER);
        g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 7, 7);
        g.setFont(BODY_FONT);
        String text = search.length() == 0 ? "Search build pieces..." : search;
        g.setColor(search.length() == 0 ? MUTED : TEXT);
        g.drawString(text + (searchFocused ? "|" : ""), bounds.x + 9, bounds.y + 21);
    }

    private static void paintTabs(Graphics2D g, Rectangle[] tabs) {
        Category[] categories = Category.values();
        for (int i = 0; i < tabs.length; i++) {
            Rectangle tab = tabs[i];
            boolean active = search.trim().length() == 0 && categories[i] == category;
            g.setColor(active ? BUTTON_ACTIVE : BUTTON);
            g.fillRoundRect(tab.x, tab.y, tab.width, tab.height, 6, 6);
            g.setColor(active ? TEXT : MUTED);
            g.setFont(BOLD_FONT);
            centerText(g, categories[i].getDisplayName(), tab);
        }
    }

    private static void paintCards(Graphics2D g, CardHitbox[] cards) {
        BuildPiece selected = ConstructionPlacementController.getSelectedPiece();
        for (CardHitbox card : cards) {
            boolean isSelected = selected != null && selected.getKey().equals(card.piece.getKey());
            g.setColor(isSelected ? CARD_SELECTED : CARD);
            g.fillRoundRect(card.bounds.x, card.bounds.y, card.bounds.width, card.bounds.height, 8, 8);
            g.setColor(isSelected ? ACCENT : BORDER);
            g.setStroke(isSelected ? SELECTED_STROKE : new BasicStroke(1.0F));
            g.drawRoundRect(card.bounds.x, card.bounds.y, card.bounds.width - 1, card.bounds.height - 1, 8, 8);

            g.setFont(BOLD_FONT);
            g.setColor(TEXT);
            g.drawString(card.piece.getDisplayName(), card.bounds.x + 10, card.bounds.y + 18);
            g.setFont(SMALL_FONT);
            g.setColor(MUTED);
            g.drawString("Object " + card.piece.getObjectId() + "  •  type " + card.piece.getObjectType(),
                    card.bounds.x + 10, card.bounds.y + 35);
            String note = trimToWidth(g, card.piece.getNote(), card.bounds.width - 20);
            g.drawString(note, card.bounds.x + 10, card.bounds.y + 52);
        }
    }

    private static void paintRailDebugEvidence(Graphics2D g, LayoutSnapshot layout) {
        java.util.List<String> lines = RailRoutePreview.getDebugOverlayLines();
        int x = layout.panel.x + 8;
        int y = layout.panel.y + 128;
        int width = layout.panel.width - 16;
        int bottom = layout.panel.y + layout.panel.height - 112;
        int height = Math.max(90, bottom - y);

        g.setColor(new Color(4, 9, 14, 238));
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setColor(ACCENT);
        g.drawRoundRect(x, y, width - 1, height - 1, 8, 8);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(150, 210, 255));
        g.drawString("RAIL DEBUG - LATEST ROUTE INTENT", x + 8, y + 15);

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(TEXT);
        int lineY = y + 29;
        int maxChars = Math.max(28, (width - 16) / 6);
        for (String line : lines) {
            String shown = line;
            if (shown.length() > maxChars) {
                shown = shown.substring(0, Math.max(1, maxChars - 3)) + "...";
            }
            g.drawString(shown, x + 8, lineY);
            lineY += 12;
            if (lineY > y + height - 7) {
                break;
            }
        }
    }

    private static void paintControls(Graphics2D g, LayoutSnapshot layout) {
        paintButton(g, layout.rotateLeft, "Rotate -", false);
        paintButton(g, layout.rotateRight, "Rotate +", false);
        paintButton(g, layout.paintMode, "Paint",
                ConstructionPlacementController.getPlacementMode() == PlacementMode.PAINT);
        paintButton(g, layout.continuousMode, "Cont.",
                ConstructionPlacementController.getPlacementMode() == PlacementMode.CONTINUOUS);

        int textY = layout.panel.y + layout.panel.height - 72;
        BuildPiece selected = ConstructionPlacementController.getSelectedPiece();
        g.setFont(BODY_FONT);
        g.setColor(TEXT);
        String selectedText = selected == null ? "Selected: none" : "Selected: " + selected.getDisplayName()
                + "  •  rot " + ConstructionPlacementController.getRotation();
        g.drawString(selectedText, layout.panel.x + 14, textY);

        HoverTile tile = ConstructionPlacementController.getHoveredTile();
        g.setFont(SMALL_FONT);
        g.setColor(tile == null ? MUTED : ACCENT);
        String target = tile == null ? "Target tile: move cursor over the world"
                : "Target tile: " + tile.getWorldX() + ", " + tile.getWorldY() + ", " + tile.getPlane();
        g.drawString(target, layout.panel.x + 14, textY + 18);

        g.setColor(ACCENT);
        String ghostState = trimToWidth(g, "Ghost: " + ConstructionGhostPreview.getLatestDebugState(),
                Math.max(1, layout.panel.width - 124));
        g.drawString(ghostState, layout.panel.x + 14, textY + 36);

        String state;
        if (ConstructionPlacementController.isRailRouteSelected()) {
            state = "Rail route • hold Left at A • drag to B • release to build • Esc cancels drag";
        } else if (ConstructionBuildCamera.isRtsMode()) {
            state = "RTS " + ConstructionBuildCamera.getRtsMoveSpeedLabel()
                    + " • WASD/arrows pan • Q/E rotate • wheel zoom • R rotates piece";
        } else if (ConstructionPlacementController.isArmed()) {
            state = "Free • click world to place • R / Shift+R or wheel rotates";
        } else {
            state = "Free • WASD/arrows move • Q/E height • mouse-look";
        }
        g.setFont(SMALL_FONT);
        g.setColor(MUTED);
        g.drawString(trimToWidth(g, state, Math.max(1, layout.panel.width - 112)),
                layout.panel.x + 14, layout.panel.y + layout.panel.height - 14);
        paintButton(g, layout.eraser, "Erase", ConstructionPlacementController.isEraserMode());
        paintButton(g, layout.clearBuilds,
                System.currentTimeMillis() <= clearBuildsConfirmUntil ? "SURE?" : "Clear", false);
        if (ConstructionPlacementController.isRailRouteSelected()) {
            paintButton(g, layout.railDebug, "Debug", RailRoutePreview.isDebugEnabled());
            paintButton(g, layout.copyRailDebug, "Copy", false);
        } else {
            paintButton(g, layout.railDebug, "-", false);
            paintButton(g, layout.copyRailDebug, "-", false);
        }
        paintButton(g, layout.cancel, "Cancel", false);
    }

    private static void paintButton(Graphics2D g, Rectangle bounds, String text, boolean active) {
        g.setColor(active ? BUTTON_ACTIVE : BUTTON);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
        g.setColor(active ? TEXT : MUTED);
        g.setFont(BOLD_FONT);
        centerText(g, text, bounds);
    }

    private static void centerText(Graphics2D g, String text, Rectangle bounds) {
        FontMetrics fm = g.getFontMetrics();
        int x = bounds.x + Math.max(0, (bounds.width - fm.stringWidth(text)) / 2);
        int y = bounds.y + (bounds.height - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
    }

    private static String trimToWidth(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class PaletteSurface extends JComponent {
        private static final long serialVersionUID = 7191062631655302910L;

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!visible || !(graphics instanceof Graphics2D)) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                LayoutSnapshot layout = buildLayout(getWidth(), getHeight());
                latestLayout = layout;
                paintPanel(g, layout);
            } finally {
                g.dispose();
            }
        }
    }

    private static final class CardHitbox {
        private final Rectangle bounds;
        private final BuildPiece piece;

        private CardHitbox(Rectangle bounds, BuildPiece piece) {
            this.bounds = bounds;
            this.piece = piece;
        }
    }

    private static final class LayoutSnapshot {
        private final Rectangle panel;
        private final Rectangle close;
        private final Rectangle cameraFree;
        private final Rectangle cameraRts;
        private final Rectangle rtsSpeedDown;
        private final Rectangle rtsSpeedUp;
        private final Rectangle search;
        private final Rectangle[] tabs;
        private final CardHitbox[] cards;
        private final Rectangle rotateLeft;
        private final Rectangle rotateRight;
        private final Rectangle paintMode;
        private final Rectangle continuousMode;
        private final Rectangle eraser;
        private final Rectangle clearBuilds;
        private final Rectangle railDebug;
        private final Rectangle copyRailDebug;
        private final Rectangle cancel;
        private final int totalMatchingPieces;

        private LayoutSnapshot(Rectangle panel, Rectangle close, Rectangle cameraFree, Rectangle cameraRts,
                Rectangle rtsSpeedDown, Rectangle rtsSpeedUp, Rectangle search, Rectangle[] tabs,
                CardHitbox[] cards, Rectangle rotateLeft, Rectangle rotateRight, Rectangle paintMode,
                Rectangle continuousMode, Rectangle eraser, Rectangle clearBuilds,
                Rectangle railDebug, Rectangle copyRailDebug, Rectangle cancel,
                int totalMatchingPieces) {
            this.panel = panel;
            this.close = close;
            this.cameraFree = cameraFree;
            this.cameraRts = cameraRts;
            this.rtsSpeedDown = rtsSpeedDown;
            this.rtsSpeedUp = rtsSpeedUp;
            this.search = search;
            this.tabs = tabs;
            this.cards = cards;
            this.rotateLeft = rotateLeft;
            this.rotateRight = rotateRight;
            this.paintMode = paintMode;
            this.continuousMode = continuousMode;
            this.eraser = eraser;
            this.clearBuilds = clearBuilds;
            this.railDebug = railDebug;
            this.copyRailDebug = copyRailDebug;
            this.cancel = cancel;
            this.totalMatchingPieces = totalMatchingPieces;
        }

        private static LayoutSnapshot empty() {
            Rectangle zero = new Rectangle();
            return new LayoutSnapshot(zero, zero, zero, zero, zero, zero, zero,
                    new Rectangle[0], new CardHitbox[0], zero, zero, zero, zero,
                    zero, zero, zero, zero, zero, 0);
        }
    }
}
