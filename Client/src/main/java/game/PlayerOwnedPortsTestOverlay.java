package game;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.GeneralPath;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Player-Owned Ports style reference overlay for Matrix3 UI prototyping.
 *
 * The overlay intentionally keeps the original 576x324 reference coordinate
 * space for now. It is split into three owned JWindows so the uncovered center
 * of the live game canvas remains owned by Matrix3.
 *
 * Resource/trade-good rows are data driven. Add a new DisplayEntry to the
 * corresponding array instead of adding new painting code. The public amount
 * setters provide a narrow seam for future live settlement/Ports state wiring.
 *
 * This remains presentation/test tooling and owns no gameplay authority.
 */
public final class PlayerOwnedPortsTestOverlay {

    public static final int REFERENCE_WIDTH = 576;
    public static final int REFERENCE_HEIGHT = 324;

    private static final int LEFT_WIDTH = 192;
    private static final int CENTER_X = 192;
    private static final int CENTER_WIDTH = 193;
    private static final int CENTER_HEIGHT = 77;
    private static final int RIGHT_X = 385;
    private static final int RIGHT_WIDTH = 191;
    private static final int FRAME_MS = 33;

    private static final Rectangle LEFT_DROPDOWN = new Rectangle(34, 1, 158, 22);
    private static final Rectangle LEFT_DROPDOWN_ITEM = new Rectangle(34, 23, 158, 24);
    private static final Rectangle RIGHT_DROPDOWN = new Rectangle(0, 1, RIGHT_WIDTH, 22);
    private static final Rectangle RIGHT_DROPDOWN_ITEM = new Rectangle(0, 23, RIGHT_WIDTH, 24);

    private static final Color PANEL_TOP = new Color(11, 16, 24, 208);
    private static final Color PANEL_BOTTOM = new Color(7, 10, 16, 218);
    private static final Color BORDER = new Color(115, 108, 91, 225);
    private static final Color BORDER_DARK = new Color(31, 29, 25, 235);
    private static final Color GOLD = new Color(231, 211, 139);
    private static final Color GOLD_MUTED = new Color(191, 164, 91);
    private static final Color TEXT = new Color(235, 229, 209);
    private static final Color MUTED = new Color(179, 174, 157);
    private static final Color GREEN = new Color(140, 205, 95);
    private static final Color RED = new Color(190, 92, 75);
    private static final Color HOVER_FILL = new Color(207, 173, 91, 42);
    private static final Color HOVER_BORDER = new Color(226, 198, 121, 125);
    private static final Color ROW_DIVIDER = new Color(104, 99, 86, 95);

    private static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 13);
    private static final Font BODY_FONT = new Font("Serif", Font.PLAIN, 12);
    private static final Font SMALL_FONT = new Font("Serif", Font.PLAIN, 11);
    private static final Font SMALL_BOLD_FONT = new Font("Serif", Font.BOLD, 11);
    private static final Font COIN_FONT = new Font("Serif", Font.BOLD, 9);

    /*
     * Append entries here for future resource types. Painting/layout code does
     * not need to change for additional rows while they fit in the reference.
     */
    private static final DisplayEntry[] PORT_RESOURCES = {
            new DisplayEntry("wood", "Wood", 0L, IconKind.WOOD,
                    new Color(151, 104, 58)),
            new DisplayEntry("ore", "Ore", 0L, IconKind.ORE,
                    new Color(132, 137, 142)),
            new DisplayEntry("food", "Food", 0L, IconKind.FOOD,
                    new Color(188, 93, 58)),
            new DisplayEntry("water", "Water", 0L, IconKind.WATER,
                    new Color(74, 146, 196))
    };

    /*
     * Ports Gold is the only current trade good. Add future trade goods here.
     */
    private static final DisplayEntry[] TRADE_GOODS = {
            new DisplayEntry("ports-gold", "Ports Gold", 0L,
                    IconKind.GOLD_COIN, new Color(224, 185, 67))
    };

    private static final String[] LEFT_DROPDOWN_ITEMS = { "Null" };
    private static final String[] RIGHT_DROPDOWN_ITEMS = { "Null" };

    private static volatile boolean visible;
    private static volatile boolean leftDropdownOpen;
    private static volatile boolean rightDropdownOpen;

    private static Timer paintTimer;
    private static Window owner;
    private static JWindow leftWindow;
    private static JWindow centerWindow;
    private static JWindow rightWindow;
    private static OverlaySurface leftSurface;
    private static OverlaySurface centerSurface;
    private static OverlaySurface rightSurface;

    private PlayerOwnedPortsTestOverlay() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void show() {
        visible = true;
        updateTimerState();
    }

    public static void hide() {
        visible = false;
        leftDropdownOpen = false;
        rightDropdownOpen = false;
        updateTimerState();
    }

    public static boolean setPortResourceAmount(String id, long amount) {
        return setEntryAmount(PORT_RESOURCES, id, amount);
    }

    public static boolean setTradeGoodAmount(String id, long amount) {
        return setEntryAmount(TRADE_GOODS, id, amount);
    }

    private static boolean setEntryAmount(DisplayEntry[] entries, String id, long amount) {
        if (id == null) {
            return false;
        }
        for (DisplayEntry entry : entries) {
            if (entry.id.equalsIgnoreCase(id)) {
                entry.amount = Math.max(0L, amount);
                repaintSurfaces();
                return true;
            }
        }
        return false;
    }

    public static String getStatus() {
        if (!visible) {
            return "Ports reference overlay: hidden.";
        }
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null) {
            return "Ports reference overlay: waiting for Matrix3 canvas.";
        }
        return "Ports reference overlay: interactive 576x324 prototype. Resources="
                + PORT_RESOURCES.length + ", tradeGoods=" + TRADE_GOODS.length
                + ", canvas=" + canvas.getWidth() + "x" + canvas.getHeight() + ".";
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
                paintTimer = new Timer(FRAME_MS, e -> refresh());
                paintTimer.setCoalesce(true);
            }
            if (!paintTimer.isRunning()) {
                paintTimer.start();
            }
            refresh();
        } else {
            if (paintTimer != null) {
                paintTimer.stop();
            }
            setWindowsVisible(false);
        }
    }

    private static void refresh() {
        if (!visible) {
            setWindowsVisible(false);
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            setWindowsVisible(false);
            return;
        }

        if (!ensureWindows(canvas)) {
            setWindowsVisible(false);
            return;
        }

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            setWindowsVisible(false);
            return;
        }

        setBounds(leftWindow, canvasLocation.x, canvasLocation.y,
                LEFT_WIDTH, REFERENCE_HEIGHT);
        setBounds(centerWindow, canvasLocation.x + CENTER_X, canvasLocation.y,
                CENTER_WIDTH, CENTER_HEIGHT);
        setBounds(rightWindow, canvasLocation.x + RIGHT_X, canvasLocation.y,
                RIGHT_WIDTH, REFERENCE_HEIGHT);

        setWindowsVisible(true);
        repaintSurfaces();
    }

    private static boolean ensureWindows(Canvas canvas) {
        Window nextOwner = SwingUtilities.getWindowAncestor(canvas);
        if (nextOwner == null) {
            return false;
        }
        if (owner == nextOwner && leftWindow != null
                && centerWindow != null && rightWindow != null) {
            return true;
        }

        disposeWindows();
        owner = nextOwner;

        leftSurface = new OverlaySurface(Part.LEFT);
        centerSurface = new OverlaySurface(Part.CENTER);
        rightSurface = new OverlaySurface(Part.RIGHT);

        leftWindow = createWindow(owner, leftSurface);
        centerWindow = createWindow(owner, centerSurface);
        rightWindow = createWindow(owner, rightSurface);
        return true;
    }

    private static JWindow createWindow(Window windowOwner, JComponent surface) {
        JWindow window = new JWindow(windowOwner);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setBackground(new Color(0, 0, 0, 0));
        window.getContentPane().setLayout(new BorderLayout());
        window.getContentPane().setBackground(new Color(0, 0, 0, 0));
        window.getContentPane().add(surface, BorderLayout.CENTER);
        return window;
    }

    private static void setBounds(JWindow window, int x, int y, int width, int height) {
        if (window.getX() != x || window.getY() != y
                || window.getWidth() != width || window.getHeight() != height) {
            window.setBounds(x, y, width, height);
        }
    }

    private static void setWindowsVisible(boolean show) {
        setWindowVisible(leftWindow, show);
        setWindowVisible(centerWindow, show);
        setWindowVisible(rightWindow, show);
    }

    private static void setWindowVisible(JWindow window, boolean show) {
        if (window != null && window.isVisible() != show) {
            window.setVisible(show);
        }
    }

    private static void repaintSurfaces() {
        if (leftSurface != null) leftSurface.repaint();
        if (centerSurface != null) centerSurface.repaint();
        if (rightSurface != null) rightSurface.repaint();
    }

    private static void disposeWindows() {
        if (leftWindow != null) leftWindow.dispose();
        if (centerWindow != null) centerWindow.dispose();
        if (rightWindow != null) rightWindow.dispose();
        leftWindow = null;
        centerWindow = null;
        rightWindow = null;
        leftSurface = null;
        centerSurface = null;
        rightSurface = null;
    }

    private static void handleClick(Part part, int x, int y) {
        if (part == Part.LEFT) {
            if (LEFT_DROPDOWN.contains(x, y)) {
                leftDropdownOpen = !leftDropdownOpen;
                rightDropdownOpen = false;
            } else if (leftDropdownOpen && LEFT_DROPDOWN_ITEM.contains(x, y)) {
                leftDropdownOpen = false;
            } else {
                leftDropdownOpen = false;
            }
        } else if (part == Part.RIGHT) {
            if (RIGHT_DROPDOWN.contains(x, y)) {
                rightDropdownOpen = !rightDropdownOpen;
                leftDropdownOpen = false;
            } else if (rightDropdownOpen && RIGHT_DROPDOWN_ITEM.contains(x, y)) {
                rightDropdownOpen = false;
            } else {
                rightDropdownOpen = false;
            }
        } else {
            leftDropdownOpen = false;
            rightDropdownOpen = false;
        }
        repaintSurfaces();
    }

    private static boolean isHandCursor(Part part, int x, int y) {
        if (part == Part.LEFT) {
            return LEFT_DROPDOWN.contains(x, y)
                    || (leftDropdownOpen && LEFT_DROPDOWN_ITEM.contains(x, y));
        }
        if (part == Part.RIGHT) {
            return RIGHT_DROPDOWN.contains(x, y)
                    || (rightDropdownOpen && RIGHT_DROPDOWN_ITEM.contains(x, y));
        }
        return false;
    }

    private enum Part {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum IconKind {
        WOOD,
        ORE,
        FOOD,
        WATER,
        GOLD_COIN,
        GENERIC
    }

    private static final class DisplayEntry {
        private final String id;
        private final String name;
        private final IconKind icon;
        private final Color color;
        private volatile long amount;

        private DisplayEntry(String id, String name, long amount,
                IconKind icon, Color color) {
            this.id = id;
            this.name = name;
            this.amount = Math.max(0L, amount);
            this.icon = icon == null ? IconKind.GENERIC : icon;
            this.color = color == null ? GOLD_MUTED : color;
        }
    }

    private static final class OverlaySurface extends JComponent {

        private static final long serialVersionUID = 2962782872680676786L;

        private final Part part;
        private int hoverX = -1;
        private int hoverY = -1;

        private OverlaySurface(Part part) {
            this.part = part;
            setOpaque(false);
            setFocusable(false);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    hoverX = event.getX();
                    hoverY = event.getY();
                    setCursor(isHandCursor(OverlaySurface.this.part, hoverX, hoverY)
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            : Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    mouseMoved(event);
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent event) {
                    hoverX = -1;
                    hoverY = -1;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1) {
                        handleClick(OverlaySurface.this.part,
                                event.getX(), event.getY());
                        event.consume();
                    }
                }
            });
        }

        private boolean isHovered(Rectangle rectangle) {
            return rectangle != null && rectangle.contains(hoverX, hoverY);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (part == Part.LEFT) {
                    paintLeft(g, this);
                } else if (part == Part.CENTER) {
                    paintCenter(g, this);
                } else {
                    paintRight(g, this);
                }
            } finally {
                g.dispose();
            }
        }
    }

    private static void paintLeft(Graphics2D g, OverlaySurface surface) {
        paintPanel(g, 0, 0, LEFT_WIDTH, REFERENCE_HEIGHT);
        paintDropdown(g, LEFT_DROPDOWN, "Resources",
                surface.isHovered(LEFT_DROPDOWN), leftDropdownOpen);

        drawCentered(g, "Port Resources", TITLE_FONT, GOLD,
                0, 39, LEFT_WIDTH);

        int resourceEnd = paintEntryList(g, surface, PORT_RESOURCES, 48, 27);
        int dividerY = resourceEnd + 5;
        g.setColor(BORDER);
        g.drawLine(8, dividerY, LEFT_WIDTH - 9, dividerY);

        int tradeTitleBaseline = dividerY + 18;
        drawCentered(g, "- Trade Goods -", SMALL_BOLD_FONT, GOLD,
                0, tradeTitleBaseline, LEFT_WIDTH);
        paintEntryList(g, surface, TRADE_GOODS,
                tradeTitleBaseline + 9, 27);

        if (leftDropdownOpen) {
            paintDropdownMenu(g, surface, LEFT_DROPDOWN_ITEM,
                    LEFT_DROPDOWN_ITEMS[0]);
        }
    }

    private static int paintEntryList(Graphics2D g, OverlaySurface surface,
            DisplayEntry[] entries, int startY, int rowHeight) {
        int y = startY;
        for (DisplayEntry entry : entries) {
            Rectangle row = new Rectangle(7, y, LEFT_WIDTH - 14, rowHeight - 2);
            if (surface.isHovered(row)) {
                paintHover(g, row, 5);
            }

            paintEntryIcon(g, row.x + 5, row.y + 5, entry);
            drawText(g, entry.name, BODY_FONT, TEXT,
                    row.x + 30, row.y + 17);
            drawRightText(g, Long.toString(entry.amount), BODY_FONT, TEXT,
                    row.x + row.width - 7, row.y + 17);

            g.setColor(ROW_DIVIDER);
            g.drawLine(row.x + 29, row.y + row.height - 1,
                    row.x + row.width - 5, row.y + row.height - 1);
            y += rowHeight;
        }
        return y;
    }

    private static void paintCenter(Graphics2D g, OverlaySurface surface) {
        GeneralPath frame = new GeneralPath();
        frame.moveTo(0, 0);
        frame.lineTo(CENTER_WIDTH, 0);
        frame.lineTo(CENTER_WIDTH - 10, CENTER_HEIGHT - 8);
        frame.lineTo(CENTER_WIDTH - 39, CENTER_HEIGHT);
        frame.lineTo(39, CENTER_HEIGHT);
        frame.lineTo(10, CENTER_HEIGHT - 8);
        frame.closePath();

        g.setColor(new Color(10, 13, 18, 205));
        g.fill(frame);
        g.setColor(BORDER_DARK);
        g.setStroke(new BasicStroke(2F));
        g.draw(frame);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1F));
        g.drawLine(9, 1, CENTER_WIDTH - 10, 1);

        int[] x = { 13, 72, 131 };
        for (int i = 0; i < x.length; i++) {
            paintRoundCommand(g, surface, x[i], 4, i);
            paintRoundCommand(g, surface, x[i], 39, i + 3);
        }
    }

    private static void paintRight(Graphics2D g, OverlaySurface surface) {
        paintPanel(g, 0, 0, RIGHT_WIDTH, REFERENCE_HEIGHT);
        paintDropdown(g, RIGHT_DROPDOWN, "Visitors",
                surface.isHovered(RIGHT_DROPDOWN), rightDropdownOpen);

        paintVisitor(g, surface, 7, 31, "O", "Occultist", "Under Way", false);
        paintVisitor(g, surface, 7, 89, "A", "Assassin", "In Port", true);
        paintVisitor(g, surface, 7, 147, "C", "Captain for Hire", "Sebastian Rackham", true);
        paintVisitor(g, surface, 7, 205, "B", "Black Market", "Chinese", true);

        g.setColor(BORDER);
        g.drawLine(8, 270, RIGHT_WIDTH - 9, 270);
        paintReward(g, surface, 45, 286, RED, "3", true);
        paintReward(g, surface, 111, 286, new Color(79, 137, 188), "2", false);

        if (rightDropdownOpen) {
            paintDropdownMenu(g, surface, RIGHT_DROPDOWN_ITEM,
                    RIGHT_DROPDOWN_ITEMS[0]);
        }
    }

    private static void paintPanel(Graphics2D g, int x, int y, int width, int height) {
        GradientPaint fill = new GradientPaint(x, y, PANEL_TOP,
                x, y + height, PANEL_BOTTOM);
        g.setPaint(fill);
        g.fillRect(x, y, width, height);

        g.setColor(BORDER_DARK);
        g.setStroke(new BasicStroke(2F));
        g.drawRect(x, y, width - 1, height - 1);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1F));
        g.drawRect(x + 2, y + 2, width - 5, height - 5);
    }

    private static void paintDropdown(Graphics2D g, Rectangle bounds,
            String label, boolean hovered, boolean open) {
        GradientPaint paint = new GradientPaint(bounds.x, bounds.y,
                open || hovered ? new Color(139, 108, 57, 248)
                        : new Color(117, 92, 50, 242),
                bounds.x, bounds.y + bounds.height,
                open || hovered ? new Color(72, 52, 31, 248)
                        : new Color(56, 42, 28, 242));
        g.setPaint(paint);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(open || hovered ? HOVER_BORDER : new Color(160, 127, 67));
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);

        GeneralPath triangle = new GeneralPath();
        if (open) {
            triangle.moveTo(bounds.x + 8, bounds.y + 14);
            triangle.lineTo(bounds.x + 16, bounds.y + 14);
            triangle.lineTo(bounds.x + 12, bounds.y + 8);
        } else {
            triangle.moveTo(bounds.x + 8, bounds.y + 8);
            triangle.lineTo(bounds.x + 16, bounds.y + 8);
            triangle.lineTo(bounds.x + 12, bounds.y + 14);
        }
        triangle.closePath();
        g.setColor(GOLD_MUTED);
        g.fill(triangle);

        drawText(g, label, SMALL_BOLD_FONT, TEXT,
                bounds.x + 22, bounds.y + 15);
    }

    private static void paintDropdownMenu(Graphics2D g, OverlaySurface surface,
            Rectangle bounds, String label) {
        g.setColor(new Color(18, 19, 20, 246));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        if (surface.isHovered(bounds)) {
            paintHover(g, bounds, 0);
        }
        g.setColor(BORDER);
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        drawText(g, label, SMALL_FONT, TEXT,
                bounds.x + 11, bounds.y + 16);
    }

    private static void paintHover(Graphics2D g, Rectangle bounds, int arc) {
        g.setColor(HOVER_FILL);
        if (arc > 0) {
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
        } else {
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        g.setColor(HOVER_BORDER);
        if (arc > 0) {
            g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1,
                    bounds.height - 1, arc, arc);
        } else {
            g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        }
    }

    private static void paintEntryIcon(Graphics2D g, int x, int y, DisplayEntry entry) {
        g.setColor(new Color(0, 0, 0, 135));
        g.fillOval(x - 1, y - 1, 18, 18);
        g.setColor(new Color(222, 210, 171, 115));
        g.drawOval(x, y, 15, 15);

        Color color = entry.color;
        switch (entry.icon) {
        case WOOD:
            g.setColor(color);
            g.fillRoundRect(x + 2, y + 5, 12, 7, 4, 4);
            g.setColor(color.brighter());
            g.drawOval(x + 9, y + 5, 5, 7);
            g.drawLine(x + 4, y + 6, x + 10, y + 10);
            break;
        case ORE:
            GeneralPath rock = new GeneralPath();
            rock.moveTo(x + 3, y + 11);
            rock.lineTo(x + 5, y + 4);
            rock.lineTo(x + 10, y + 2);
            rock.lineTo(x + 14, y + 7);
            rock.lineTo(x + 12, y + 13);
            rock.lineTo(x + 6, y + 14);
            rock.closePath();
            g.setColor(color);
            g.fill(rock);
            g.setColor(color.brighter());
            g.drawLine(x + 6, y + 6, x + 10, y + 4);
            break;
        case FOOD:
            g.setColor(color);
            g.fillOval(x + 3, y + 4, 11, 10);
            g.setColor(new Color(89, 138, 62));
            g.fillOval(x + 9, y + 2, 5, 3);
            g.setColor(new Color(97, 68, 43));
            g.drawLine(x + 8, y + 5, x + 9, y + 2);
            break;
        case WATER:
            GeneralPath drop = new GeneralPath();
            drop.moveTo(x + 8, y + 1);
            drop.curveTo(x + 6, y + 5, x + 3, y + 8, x + 3, y + 11);
            drop.curveTo(x + 3, y + 15, x + 13, y + 15, x + 13, y + 11);
            drop.curveTo(x + 13, y + 8, x + 10, y + 5, x + 8, y + 1);
            drop.closePath();
            g.setColor(color);
            g.fill(drop);
            g.setColor(color.brighter());
            g.drawLine(x + 6, y + 8, x + 8, y + 5);
            break;
        case GOLD_COIN:
            g.setColor(color.darker());
            g.fillOval(x + 1, y + 1, 14, 14);
            g.setColor(color);
            g.fillOval(x + 2, y + 2, 12, 12);
            g.setColor(color.brighter());
            g.drawOval(x + 4, y + 4, 8, 8);
            drawCentered(g, "P", COIN_FONT, new Color(108, 71, 18),
                    x + 2, y + 11, 12);
            break;
        default:
            GeneralPath diamond = new GeneralPath();
            diamond.moveTo(x + 8, y + 1);
            diamond.lineTo(x + 14, y + 8);
            diamond.lineTo(x + 8, y + 14);
            diamond.lineTo(x + 2, y + 8);
            diamond.closePath();
            g.setColor(color);
            g.fill(diamond);
            break;
        }
    }

    private static void paintRoundCommand(Graphics2D g, OverlaySurface surface,
            int x, int y, int type) {
        int size = 49;
        Rectangle hit = new Rectangle(x, y, size, size);
        boolean hovered = surface.isHovered(hit);

        g.setColor(hovered ? new Color(45, 35, 22, 242)
                : new Color(18, 14, 12, 228));
        g.fillOval(x, y, size, size);
        g.setColor(hovered ? new Color(198, 153, 76)
                : new Color(121, 93, 52));
        g.setStroke(new BasicStroke(hovered ? 3F : 2F));
        g.drawOval(x + 1, y + 1, size - 3, size - 3);
        g.setColor(hovered ? new Color(83, 61, 35)
                : new Color(61, 46, 31));
        g.fillOval(x + 6, y + 6, size - 12, size - 12);
        g.setColor(hovered ? GOLD : GOLD_MUTED);
        g.setStroke(new BasicStroke(2F));

        int cx = x + size / 2;
        int cy = y + size / 2;
        switch (type) {
        case 0:
            g.drawLine(cx, cy + 10, cx, cy - 9);
            g.drawLine(cx, cy - 9, cx - 6, cy - 2);
            g.drawLine(cx, cy - 9, cx + 6, cy - 2);
            break;
        case 1:
            g.fillOval(cx - 10, cy - 7, 7, 7);
            g.fillOval(cx - 3, cy - 11, 7, 7);
            g.fillOval(cx + 4, cy - 7, 7, 7);
            g.drawArc(cx - 13, cy - 1, 25, 17, 0, 180);
            break;
        case 2:
            GeneralPath sail = new GeneralPath();
            sail.moveTo(cx - 7, cy + 7);
            sail.lineTo(cx + 9, cy + 7);
            sail.lineTo(cx + 4, cy + 12);
            sail.lineTo(cx - 10, cy + 12);
            sail.closePath();
            g.fill(sail);
            g.drawLine(cx - 1, cy + 6, cx - 1, cy - 11);
            g.drawLine(cx - 1, cy - 10, cx + 8, cy + 2);
            break;
        case 3:
            g.drawLine(cx - 9, cy - 9, cx + 9, cy + 9);
            g.drawLine(cx + 9, cy - 9, cx - 9, cy + 9);
            g.drawOval(cx - 3, cy - 3, 6, 6);
            break;
        case 4:
            g.drawOval(cx - 9, cy - 9, 18, 18);
            g.drawOval(cx - 3, cy - 3, 6, 6);
            for (int a = 0; a < 8; a++) {
                double angle = Math.PI * a / 4.0;
                int x1 = cx + (int) (Math.cos(angle) * 10);
                int y1 = cy + (int) (Math.sin(angle) * 10);
                int x2 = cx + (int) (Math.cos(angle) * 14);
                int y2 = cy + (int) (Math.sin(angle) * 14);
                g.drawLine(x1, y1, x2, y2);
            }
            break;
        default:
            g.drawOval(cx - 3, cy - 10, 6, 6);
            g.drawLine(cx, cy - 3, cx - 5, cy + 5);
            g.drawLine(cx - 2, cy, cx + 7, cy + 3);
            g.drawLine(cx - 5, cy + 5, cx - 10, cy + 11);
            g.drawLine(cx - 5, cy + 5, cx + 3, cy + 11);
            break;
        }
        g.setStroke(new BasicStroke(1F));
    }

    private static void paintVisitor(Graphics2D g, OverlaySurface surface,
            int x, int y, String initial, String name, String state, boolean inPort) {
        Rectangle row = new Rectangle(5, y - 2, RIGHT_WIDTH - 10, 54);
        if (surface.isHovered(row)) {
            paintHover(g, row, 5);
        }

        int avatarWidth = 40;
        int avatarHeight = 48;
        g.setColor(new Color(30, 27, 25, 225));
        g.fillRect(x, y, avatarWidth, avatarHeight);
        g.setColor(BORDER);
        g.drawRect(x, y, avatarWidth, avatarHeight);

        g.setColor(new Color(76, 69, 58));
        g.fillOval(x + 12, y + 6, 16, 16);
        g.fillRoundRect(x + 7, y + 22, 26, 23, 9, 9);
        drawCentered(g, initial, SMALL_BOLD_FONT, GOLD,
                x, y + 20, avatarWidth);

        drawText(g, name, SMALL_BOLD_FONT, GOLD, x + 48, y + 15);
        drawText(g, state, SMALL_FONT, inPort ? GREEN : MUTED,
                x + 48, y + 33);
        g.setColor(ROW_DIVIDER);
        g.drawLine(x + 48, y + 43, RIGHT_WIDTH - 9, y + 43);
    }

    private static void paintReward(Graphics2D g, OverlaySurface surface,
            int x, int y, Color color, String amount, boolean book) {
        Rectangle hit = new Rectangle(x - 8, y - 8, 54, 35);
        if (surface.isHovered(hit)) {
            paintHover(g, hit, 7);
        }

        g.setColor(new Color(0, 0, 0, 145));
        g.fillOval(x - 5, y - 5, 30, 30);
        g.setColor(color);
        if (book) {
            g.fillRoundRect(x, y, 18, 19, 3, 3);
            g.setColor(color.brighter());
            g.drawLine(x + 5, y + 2, x + 5, y + 17);
        } else {
            GeneralPath token = new GeneralPath();
            token.moveTo(x + 9, y);
            token.lineTo(x + 18, y + 8);
            token.lineTo(x + 13, y + 19);
            token.lineTo(x + 3, y + 17);
            token.lineTo(x, y + 7);
            token.closePath();
            g.fill(token);
            g.setColor(GOLD);
            g.drawLine(x + 4, y + 11, x + 14, y + 7);
        }
        drawText(g, "x" + amount, SMALL_BOLD_FONT, TEXT, x + 22, y + 16);
    }

    private static void drawCentered(Graphics2D g, String text, Font font,
            Color color, int x, int baseline, int width) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        int textX = x + Math.max(0, (width - metrics.stringWidth(text)) / 2);
        g.setColor(color);
        g.drawString(text, textX, baseline);
    }

    private static void drawText(Graphics2D g, String text, Font font,
            Color color, int x, int baseline) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static void drawRightText(Graphics2D g, String text, Font font,
            Color color, int rightX, int baseline) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        g.setColor(color);
        g.drawString(text, rightX - metrics.stringWidth(text), baseline);
    }
}
