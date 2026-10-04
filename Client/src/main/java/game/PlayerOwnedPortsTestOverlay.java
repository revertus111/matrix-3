package game;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.GeneralPath;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Static Player-Owned Ports style reference overlay for UI prototyping.
 *
 * The composition intentionally uses the 576x324 reference coordinate space
 * without scaling so layout comparisons stay deterministic. It is split into
 * three owned JWindows (left, command cluster, right), leaving the uncovered
 * game canvas under the center of the reference fully owned by Matrix3.
 *
 * This is presentation-only test tooling. It owns no Construction, Ports,
 * settlement, inventory or visitor gameplay state.
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

    private static final Color PANEL = new Color(9, 13, 20, 222);
    private static final Color PANEL_SOFT = new Color(20, 22, 27, 214);
    private static final Color BORDER = new Color(106, 103, 93, 235);
    private static final Color BORDER_DARK = new Color(35, 33, 29, 245);
    private static final Color GOLD = new Color(231, 211, 139);
    private static final Color GOLD_MUTED = new Color(191, 164, 91);
    private static final Color TEXT = new Color(231, 224, 199);
    private static final Color MUTED = new Color(183, 177, 157);
    private static final Color GREEN = new Color(140, 205, 95);
    private static final Color RED = new Color(190, 92, 75);

    private static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 13);
    private static final Font BODY_FONT = new Font("Serif", Font.PLAIN, 12);
    private static final Font SMALL_FONT = new Font("Serif", Font.PLAIN, 11);
    private static final Font SMALL_BOLD_FONT = new Font("Serif", Font.BOLD, 11);

    private static final String[][] RESOURCE_VALUES = {
            { "201320", "260" },
            { "9878", "10017" },
            { "1606", "12873" },
            { "37690", "0" },
            { "0", "0" },
            { "0", "0" }
    };

    private static final String[][] TRADE_VALUES = {
            { "78", "76" },
            { "112", "12" },
            { "14", "30" },
            { "32", "0" },
            { "32", "0" }
    };

    private static final Color[] RESOURCE_COLORS = {
            new Color(220, 181, 78),
            new Color(102, 166, 81),
            new Color(114, 109, 95),
            new Color(124, 92, 65),
            new Color(169, 91, 59),
            new Color(84, 149, 183),
            new Color(124, 175, 173),
            new Color(142, 160, 188),
            new Color(174, 161, 109),
            new Color(105, 157, 201),
            new Color(183, 118, 61),
            new Color(96, 142, 178)
    };

    private static final Color[] TRADE_COLORS = {
            new Color(176, 71, 54),
            new Color(223, 200, 131),
            new Color(205, 185, 102),
            new Color(79, 142, 178),
            new Color(116, 186, 184),
            new Color(166, 90, 141),
            new Color(112, 159, 103),
            new Color(197, 136, 68),
            new Color(208, 100, 57),
            new Color(154, 115, 191)
    };

    private static volatile boolean visible;
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
        updateTimerState();
    }

    public static String getStatus() {
        if (!visible) {
            return "Ports reference overlay: hidden.";
        }
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null) {
            return "Ports reference overlay: waiting for Matrix3 canvas.";
        }
        return "Ports reference overlay: visible at exact 576x324 reference coordinates. Canvas="
                + canvas.getWidth() + "x" + canvas.getHeight() + ".";
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
        leftSurface.repaint();
        centerSurface.repaint();
        rightSurface.repaint();
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

    private enum Part {
        LEFT,
        CENTER,
        RIGHT
    }

    private static final class OverlaySurface extends JComponent {

        private static final long serialVersionUID = 2962782872680676786L;

        private final Part part;

        private OverlaySurface(Part part) {
            this.part = part;
            setOpaque(false);
            setFocusable(false);
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
                    paintLeft(g);
                } else if (part == Part.CENTER) {
                    paintCenter(g);
                } else {
                    paintRight(g);
                }
            } finally {
                g.dispose();
            }
        }
    }

    private static void paintLeft(Graphics2D g) {
        paintPanel(g, 0, 0, LEFT_WIDTH, REFERENCE_HEIGHT);
        paintDropdown(g, 34, 1, 158, 22, "Resources");
        drawCentered(g, "Port Resources", TITLE_FONT, GOLD, 0, 31, LEFT_WIDTH);

        int firstY = 49;
        for (int row = 0; row < RESOURCE_VALUES.length; row++) {
            int y = firstY + row * 20;
            int leftColor = row * 2;
            int rightColor = leftColor + 1;
            paintResourceIcon(g, 13, y - 9, RESOURCE_COLORS[leftColor], row);
            paintResourceIcon(g, 101, y - 9, RESOURCE_COLORS[rightColor], row + 6);
            drawText(g, RESOURCE_VALUES[row][0], BODY_FONT, TEXT, 31, y + 1);
            drawText(g, RESOURCE_VALUES[row][1], BODY_FONT, TEXT, 119, y + 1);
        }

        g.setColor(BORDER);
        g.drawLine(8, 174, 183, 174);
        drawCentered(g, "- Trade Goods -", SMALL_BOLD_FONT, GOLD,
                0, 190, LEFT_WIDTH);

        int goodsY = 209;
        for (int row = 0; row < TRADE_VALUES.length; row++) {
            int y = goodsY + row * 21;
            int leftColor = row * 2;
            int rightColor = leftColor + 1;
            paintTradeIcon(g, 13, y - 10, TRADE_COLORS[leftColor], row);
            paintTradeIcon(g, 101, y - 10, TRADE_COLORS[rightColor], row + 5);
            drawText(g, TRADE_VALUES[row][0], BODY_FONT, TEXT, 31, y + 1);
            if (!"0".equals(TRADE_VALUES[row][1])) {
                drawText(g, TRADE_VALUES[row][1], BODY_FONT, TEXT, 119, y + 1);
            }
        }
    }

    private static void paintCenter(Graphics2D g) {
        GeneralPath frame = new GeneralPath();
        frame.moveTo(0, 0);
        frame.lineTo(CENTER_WIDTH, 0);
        frame.lineTo(CENTER_WIDTH - 10, CENTER_HEIGHT - 8);
        frame.lineTo(CENTER_WIDTH - 39, CENTER_HEIGHT);
        frame.lineTo(39, CENTER_HEIGHT);
        frame.lineTo(10, CENTER_HEIGHT - 8);
        frame.closePath();

        g.setColor(new Color(10, 13, 18, 220));
        g.fill(frame);
        g.setColor(BORDER_DARK);
        g.setStroke(new BasicStroke(2F));
        g.draw(frame);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1F));
        g.drawLine(9, 1, CENTER_WIDTH - 10, 1);

        int[] x = { 13, 72, 131 };
        for (int i = 0; i < x.length; i++) {
            paintRoundCommand(g, x[i], 4, i);
            paintRoundCommand(g, x[i], 39, i + 3);
        }
    }

    private static void paintRight(Graphics2D g) {
        paintPanel(g, 0, 0, RIGHT_WIDTH, REFERENCE_HEIGHT);
        paintDropdown(g, 0, 1, RIGHT_WIDTH, 22, "Visitors");

        paintVisitor(g, 7, 31, "O", "Occultist", "Under Way", false);
        paintVisitor(g, 7, 89, "A", "Assassin", "In Port", true);
        paintVisitor(g, 7, 147, "C", "Captain for Hire", "Sebastian Rackham", true);
        paintVisitor(g, 7, 205, "B", "Black Market", "Chinese", true);

        g.setColor(BORDER);
        g.drawLine(8, 270, RIGHT_WIDTH - 9, 270);
        paintReward(g, 45, 286, RED, "3", true);
        paintReward(g, 111, 286, new Color(79, 137, 188), "2", false);
    }

    private static void paintPanel(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(PANEL);
        g.fillRect(x, y, width, height);
        g.setColor(BORDER_DARK);
        g.setStroke(new BasicStroke(2F));
        g.drawRect(x, y, width - 1, height - 1);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1F));
        g.drawRect(x + 2, y + 2, width - 5, height - 5);
    }

    private static void paintDropdown(Graphics2D g, int x, int y, int width, int height,
            String label) {
        GradientPaint paint = new GradientPaint(x, y,
                new Color(117, 92, 50, 245), x, y + height,
                new Color(56, 42, 28, 245));
        g.setPaint(paint);
        g.fillRect(x, y, width, height);
        g.setColor(new Color(160, 127, 67));
        g.drawRect(x, y, width - 1, height - 1);

        GeneralPath triangle = new GeneralPath();
        triangle.moveTo(x + 8, y + 8);
        triangle.lineTo(x + 16, y + 8);
        triangle.lineTo(x + 12, y + 14);
        triangle.closePath();
        g.setColor(GOLD_MUTED);
        g.fill(triangle);

        drawText(g, label, SMALL_BOLD_FONT, TEXT, x + 22, y + 15);
    }

    private static void paintResourceIcon(Graphics2D g, int x, int y, Color color, int type) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillOval(x - 1, y - 1, 17, 17);
        g.setColor(color);
        if (type % 3 == 0) {
            GeneralPath diamond = new GeneralPath();
            diamond.moveTo(x + 8, y);
            diamond.lineTo(x + 15, y + 8);
            diamond.lineTo(x + 8, y + 15);
            diamond.lineTo(x + 1, y + 8);
            diamond.closePath();
            g.fill(diamond);
        } else if (type % 3 == 1) {
            g.fillRoundRect(x + 1, y + 2, 14, 11, 5, 5);
            g.setColor(color.brighter());
            g.drawLine(x + 3, y + 4, x + 13, y + 11);
        } else {
            g.fillOval(x + 2, y + 1, 12, 14);
            g.setColor(color.brighter());
            g.fillOval(x + 5, y + 3, 4, 5);
        }
        g.setColor(new Color(230, 218, 176, 150));
        g.drawOval(x, y, 15, 15);
    }

    private static void paintTradeIcon(Graphics2D g, int x, int y, Color color, int type) {
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRoundRect(x - 1, y - 1, 18, 18, 5, 5);
        g.setColor(color);
        if ((type & 1) == 0) {
            g.fillOval(x + 2, y + 2, 12, 12);
            g.setColor(color.brighter());
            g.drawArc(x + 4, y + 4, 8, 8, 20, 240);
        } else {
            GeneralPath shard = new GeneralPath();
            shard.moveTo(x + 8, y + 1);
            shard.lineTo(x + 14, y + 7);
            shard.lineTo(x + 10, y + 15);
            shard.lineTo(x + 3, y + 11);
            shard.lineTo(x + 4, y + 4);
            shard.closePath();
            g.fill(shard);
        }
        g.setColor(new Color(220, 205, 165, 130));
        g.drawRect(x + 1, y + 1, 13, 13);
    }

    private static void paintRoundCommand(Graphics2D g, int x, int y, int type) {
        int size = 49;
        g.setColor(new Color(18, 14, 12, 235));
        g.fillOval(x, y, size, size);
        g.setColor(new Color(121, 93, 52));
        g.setStroke(new BasicStroke(2F));
        g.drawOval(x + 1, y + 1, size - 3, size - 3);
        g.setColor(new Color(61, 46, 31));
        g.fillOval(x + 6, y + 6, size - 12, size - 12);
        g.setColor(GOLD_MUTED);
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

    private static void paintVisitor(Graphics2D g, int x, int y, String initial,
            String name, String state, boolean inPort) {
        int avatarWidth = 40;
        int avatarHeight = 48;
        g.setColor(new Color(30, 27, 25, 235));
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
        g.setColor(new Color(89, 84, 72, 160));
        g.drawLine(x + 48, y + 43, RIGHT_WIDTH - 9, y + 43);
    }

    private static void paintReward(Graphics2D g, int x, int y, Color color,
            String amount, boolean book) {
        g.setColor(new Color(0, 0, 0, 160));
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
        drawText(g, text, font, color, textX, baseline);
    }

    private static void drawText(Graphics2D g, String text, Font font,
            Color color, int x, int baseline) {
        g.setFont(font);
        g.setColor(new Color(0, 0, 0, 190));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }
}
