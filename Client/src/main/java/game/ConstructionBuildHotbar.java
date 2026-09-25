package game;

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
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import game.ConstructionPlacementController.BuildPiece;

/**
 * Dedicated Construction build hotbar.
 *
 * This is intentionally separate from Matrix3's combat/action bar. It is a
 * lightweight Construction UX surface that forwards actions to the existing
 * placement/settlement owners rather than owning gameplay state itself.
 */
public final class ConstructionBuildHotbar {

    private static final int SLOT_COUNT = 9;
    private static final int HOTBAR_WIDTH = 612;
    private static final int HOTBAR_HEIGHT = 72;
    private static final int OUTER_PAD = 7;
    private static final int SLOT_GAP = 4;

    private static final Color PANEL = new Color(25, 28, 31, 238);
    private static final Color SLOT = new Color(46, 51, 58);
    private static final Color SLOT_ACTIVE = new Color(83, 124, 165);
    private static final Color SLOT_RESERVED = new Color(34, 37, 42);
    private static final Color BORDER = new Color(92, 101, 112);
    private static final Color TEXT = new Color(240, 242, 245);
    private static final Color MUTED = new Color(154, 163, 174);
    private static final Color ACCENT = new Color(126, 188, 244);

    private static final Font NUMBER_FONT = new Font("SansSerif", Font.BOLD, 10);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Font STATUS_FONT = new Font("SansSerif", Font.PLAIN, 10);

    private static final Slot[] SLOTS = {
            new Slot("Rail", true),
            new Slot("Junction", false),
            new Slot("Cross", false),
            new Slot("Splitter", false),
            new Slot("Object", true),
            new Slot("Erase", true),
            new Slot("Rotate", true),
            new Slot("Undo", true),
            new Slot("Favorites", false)
    };

    private static volatile boolean visible;
    private static volatile Rectangle[] latestSlotBounds = new Rectangle[0];
    private static volatile String lastObjectKey;
    private static volatile String lastObjectName = "Object";
    private static volatile String status = "Construction hotbar 1-9";

    private static JWindow hotbarWindow;
    private static HotbarSurface hotbarSurface;
    private static Window hotbarOwner;

    private ConstructionBuildHotbar() {
    }

    public static void show() {
        visible = true;
    }

    public static void hide() {
        visible = false;
        hideWindowSurface();
    }

    public static void refresh(Canvas canvas) {
        if (!visible || canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            hideWindowSurface();
            return;
        }
        rememberSelectedObject();

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

        int width = Math.min(HOTBAR_WIDTH, Math.max(320, canvas.getWidth() - 24));
        int height = Math.min(HOTBAR_HEIGHT, Math.max(54, canvas.getHeight() - 12));
        int localX = Math.max(4, (canvas.getWidth() - width) / 2);
        int localY = Math.max(4, canvas.getHeight() - height - 18);
        Rectangle desired = new Rectangle(
                canvasLocation.x + localX, canvasLocation.y + localY, width, height);

        if (!desired.equals(hotbarWindow.getBounds())) {
            hotbarWindow.setBounds(desired);
        }
        if (!hotbarWindow.isVisible()) {
            hotbarWindow.setVisible(true);
        }
        hotbarSurface.repaint();
    }

    public static boolean handleKey(KeyEvent event) {
        if (!visible || event == null || event.getID() != KeyEvent.KEY_PRESSED
                || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }

        int index = -1;
        switch (event.getKeyCode()) {
        case KeyEvent.VK_1: index = 0; break;
        case KeyEvent.VK_2: index = 1; break;
        case KeyEvent.VK_3: index = 2; break;
        case KeyEvent.VK_4: index = 3; break;
        case KeyEvent.VK_5: index = 4; break;
        case KeyEvent.VK_6: index = 5; break;
        case KeyEvent.VK_7: index = 6; break;
        case KeyEvent.VK_8: index = 7; break;
        case KeyEvent.VK_9: index = 8; break;
        default:
            return false;
        }

        activate(index);
        event.consume();
        repaint();
        return true;
    }

    private static void activate(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            return;
        }
        switch (index) {
        case 0:
            BuildPiece rail = findPiece("basic-rail");
            if (rail == null) {
                status = "Rail tool is unavailable.";
                return;
            }
            ConstructionPlacementController.select(rail);
            status = "Rail armed. Normal rail stops at existing track.";
            return;
        case 1:
            status = "Junction reserved: accepted junction art is still required.";
            return;
        case 2:
            status = "Crossing reserved: normal Rail cannot overlap existing track.";
            return;
        case 3:
            status = "Splitter reserved: routing asset/semantics are still pending.";
            return;
        case 4:
            rememberSelectedObject();
            BuildPiece object = findPiece(lastObjectKey);
            if (object == null) {
                status = "Select a non-rail build object in the palette first.";
                return;
            }
            ConstructionPlacementController.select(object);
            status = lastObjectName + " armed from Object slot.";
            return;
        case 5:
            ConstructionPlacementController.setEraserMode(true);
            status = "Eraser armed.";
            return;
        case 6:
            if (!ConstructionPlacementController.isArmed()
                    || ConstructionPlacementController.isEraserMode()) {
                status = "Rotate needs an armed build piece.";
                return;
            }
            ConstructionPlacementController.rotate(1);
            status = "Rotated selected build piece +90 degrees.";
            return;
        case 7:
            String error = ClientConsoleBridge.queueConsoleCommand("itembrowser settlement undo");
            status = error == null ? "Undo queued." : error;
            return;
        case 8:
            status = "Favorites reserved for assignable build slots.";
            return;
        default:
            return;
        }
    }

    private static void rememberSelectedObject() {
        BuildPiece selected = ConstructionPlacementController.getSelectedPiece();
        if (selected == null || "basic-rail".equals(selected.getKey())) {
            return;
        }
        lastObjectKey = selected.getKey();
        lastObjectName = selected.getDisplayName();
    }

    private static BuildPiece findPiece(String key) {
        if (key == null) {
            return null;
        }
        for (BuildPiece piece : ConstructionPlacementController.getPieces()) {
            if (key.equals(piece.getKey())) {
                return piece;
            }
        }
        return null;
    }

    private static boolean ensureWindow(Canvas canvas) {
        Window owner = SwingUtilities.getWindowAncestor(canvas);
        if (owner == null) {
            return false;
        }
        if (hotbarWindow != null && hotbarOwner == owner) {
            return true;
        }

        if (hotbarWindow != null) {
            hotbarWindow.dispose();
        }

        hotbarOwner = owner;
        hotbarSurface = new HotbarSurface();
        hotbarSurface.setOpaque(false);
        hotbarSurface.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!visible || event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                Rectangle[] bounds = latestSlotBounds;
                for (int i = 0; i < bounds.length; i++) {
                    if (bounds[i].contains(event.getPoint())) {
                        activate(i);
                        event.consume();
                        repaint();
                        return;
                    }
                }
            }
        });

        hotbarWindow = new JWindow(owner);
        hotbarWindow.setBackground(new Color(0, 0, 0, 0));
        hotbarWindow.setFocusableWindowState(false);
        hotbarWindow.setAutoRequestFocus(false);
        hotbarWindow.getContentPane().add(hotbarSurface);
        return true;
    }

    private static void hideWindowSurface() {
        if (hotbarWindow != null && hotbarWindow.isVisible()) {
            hotbarWindow.setVisible(false);
        }
    }

    private static void repaint() {
        if (hotbarSurface != null) {
            hotbarSurface.repaint();
        }
    }

    private static Rectangle[] layoutSlots(int width, int height) {
        Rectangle[] result = new Rectangle[SLOT_COUNT];
        int y = 22;
        int usable = Math.max(SLOT_COUNT, width - (OUTER_PAD * 2) - (SLOT_GAP * (SLOT_COUNT - 1)));
        int slotWidth = Math.max(30, usable / SLOT_COUNT);
        int x = OUTER_PAD;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int remaining = width - OUTER_PAD - x;
            int w = i == SLOT_COUNT - 1 ? Math.max(30, remaining) : slotWidth;
            result[i] = new Rectangle(x, y, w, Math.max(34, height - y - OUTER_PAD));
            x += w + SLOT_GAP;
        }
        return result;
    }

    private static boolean isActive(int index) {
        if (index == 0) {
            return ConstructionPlacementController.isRailRouteSelected()
                    && !ConstructionPlacementController.isEraserMode();
        }
        if (index == 4) {
            BuildPiece selected = ConstructionPlacementController.getSelectedPiece();
            return selected != null && !"basic-rail".equals(selected.getKey())
                    && !ConstructionPlacementController.isEraserMode();
        }
        if (index == 5) {
            return ConstructionPlacementController.isEraserMode();
        }
        return false;
    }

    private static void paintSlot(Graphics2D g, Rectangle bounds, int index) {
        Slot slot = SLOTS[index];
        boolean active = isActive(index);
        g.setColor(!slot.enabled ? SLOT_RESERVED : (active ? SLOT_ACTIVE : SLOT));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
        g.setColor(active ? ACCENT : BORDER);
        g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 6, 6);

        g.setFont(NUMBER_FONT);
        g.setColor(active ? TEXT : MUTED);
        g.drawString(Integer.toString(index + 1), bounds.x + 5, bounds.y + 12);

        String label = slot.label;
        if (index == 4 && lastObjectKey != null) {
            label = lastObjectName;
        }
        g.setFont(LABEL_FONT);
        g.setColor(slot.enabled ? TEXT : MUTED);
        drawCenteredClipped(g, label, bounds, bounds.y + 27);

        if (!slot.enabled) {
            g.setFont(STATUS_FONT);
            g.setColor(MUTED);
            drawCenteredClipped(g, "reserved", bounds, bounds.y + bounds.height - 6);
        }
    }

    private static void drawCenteredClipped(Graphics2D g, String text, Rectangle bounds, int baseline) {
        String shown = text == null ? "" : text;
        FontMetrics fm = g.getFontMetrics();
        int maxWidth = Math.max(6, bounds.width - 8);
        while (shown.length() > 1 && fm.stringWidth(shown) > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        int x = bounds.x + Math.max(2, (bounds.width - fm.stringWidth(shown)) / 2);
        g.drawString(shown, x, baseline);
    }

    private static final class HotbarSurface extends JComponent {
        private static final long serialVersionUID = -4969241162279548027L;

        @Override
        protected void paintComponent(Graphics graphics) {
            if (!visible || !(graphics instanceof Graphics2D)) {
                return;
            }
            rememberSelectedObject();

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(PANEL);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.setColor(BORDER);
                g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 8, 8);

                g.setFont(STATUS_FONT);
                g.setColor(MUTED);
                String header = "BUILD HOTBAR  |  " + status;
                if (g.getFontMetrics().stringWidth(header) > getWidth() - 12) {
                    header = "BUILD HOTBAR  |  1-9 tools";
                }
                g.drawString(header, 7, 15);

                Rectangle[] bounds = layoutSlots(getWidth(), getHeight());
                latestSlotBounds = bounds;
                for (int i = 0; i < bounds.length; i++) {
                    paintSlot(g, bounds[i], i);
                }
            } finally {
                g.dispose();
            }
        }
    }

    private static final class Slot {
        private final String label;
        private final boolean enabled;

        private Slot(String label, boolean enabled) {
            this.label = label;
            this.enabled = enabled;
        }
    }
}
