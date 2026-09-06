package game.console;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.Icon;

public final class ConsoleIcons {

    private static final int SIZE = 22;

    private ConsoleIcons() {
    }

    public static Icon home() {
        return new GlyphIcon(Glyph.HOME);
    }

    public static Icon owner() {
        return new GlyphIcon(Glyph.OWNER);
    }

    public static Icon commands() {
        return new GlyphIcon(Glyph.COMMANDS);
    }

    public static Icon player() {
        return new GlyphIcon(Glyph.PLAYER);
    }

    public static Icon items() {
        return new GlyphIcon(Glyph.ITEMS);
    }

    public static Icon atlas() {
        return new GlyphIcon(Glyph.ATLAS);
    }

    public static Icon bossResearch() {
        return new GlyphIcon(Glyph.BOSS_RESEARCH);
    }

    public static Icon settings() {
        return new GlyphIcon(Glyph.SETTINGS);
    }

    private enum Glyph {
        HOME,
        OWNER,
        COMMANDS,
        PLAYER,
        ITEMS,
        ATLAS,
        BOSS_RESEARCH,
        SETTINGS
    }

    private static final class GlyphIcon implements Icon {

        private final Glyph glyph;

        private GlyphIcon(Glyph glyph) {
            this.glyph = glyph;
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(iconColor(component));

                switch (glyph) {
                case HOME:
                    paintHome(g, x, y);
                    break;
                case OWNER:
                    paintOwner(g, x, y);
                    break;
                case COMMANDS:
                    paintCommands(g, x, y);
                    break;
                case PLAYER:
                    paintPlayer(g, x, y);
                    break;
                case ITEMS:
                    paintItems(g, x, y);
                    break;
                case ATLAS:
                    paintAtlas(g, x, y);
                    break;
                case BOSS_RESEARCH:
                    paintBossResearch(g, x, y);
                    break;
                case SETTINGS:
                    paintSettings(g, x, y);
                    break;
                default:
                    break;
                }
            } finally {
                g.dispose();
            }
        }

        private Color iconColor(Component component) {
            if (!(component instanceof AbstractButton)) {
                return ConsoleTheme.TEXT;
            }
            AbstractButton button = (AbstractButton) component;
            if (!button.isEnabled()) {
                return ConsoleTheme.MUTED_TEXT;
            }
            if (button.getModel().isSelected() || button.getModel().isPressed() || button.getModel().isRollover()) {
                return ConsoleTheme.TEXT;
            }
            return ConsoleTheme.MUTED_TEXT;
        }

        private void paintHome(Graphics2D g, int x, int y) {
            int cx = x + SIZE / 2;
            Polygon roof = new Polygon(
                    new int[] { x + 3, cx, x + SIZE - 3 },
                    new int[] { y + 10, y + 3, y + 10 },
                    3);
            g.drawPolyline(roof.xpoints, roof.ypoints, roof.npoints);
            g.drawLine(x + 5, y + 9, x + 5, y + 19);
            g.drawLine(x + SIZE - 5, y + 9, x + SIZE - 5, y + 19);
            g.drawLine(x + 5, y + 19, x + SIZE - 5, y + 19);
            g.drawRect(cx - 2, y + 13, 4, 6);
        }

        private void paintOwner(Graphics2D g, int x, int y) {
            Polygon crown = new Polygon(
                    new int[] { x + 4, x + 7, x + 11, x + 15, x + 18, x + 17, x + 5 },
                    new int[] { y + 7, y + 12, y + 6, y + 12, y + 7, y + 17, y + 17 },
                    7);
            g.drawPolygon(crown);
            g.drawLine(x + 6, y + 19, x + 16, y + 19);
        }

        private void paintCommands(Graphics2D g, int x, int y) {
            g.drawRoundRect(x + 2, y + 4, 18, 14, 3, 3);
            g.drawLine(x + 6, y + 8, x + 9, y + 11);
            g.drawLine(x + 9, y + 11, x + 6, y + 14);
            g.drawLine(x + 11, y + 14, x + 16, y + 14);
        }

        private void paintPlayer(Graphics2D g, int x, int y) {
            g.drawOval(x + 7, y + 3, 8, 8);
            g.drawArc(x + 4, y + 11, 14, 9, 0, 180);
            g.drawLine(x + 4, y + 16, x + 4, y + 19);
            g.drawLine(x + 18, y + 16, x + 18, y + 19);
        }

        private void paintItems(Graphics2D g, int x, int y) {
            g.drawArc(x + 7, y + 2, 8, 7, 0, 180);
            g.drawRoundRect(x + 4, y + 6, 14, 14, 4, 4);
            g.drawLine(x + 7, y + 11, x + 15, y + 11);
            g.drawRoundRect(x + 7, y + 13, 8, 4, 2, 2);
        }

        private void paintAtlas(Graphics2D g, int x, int y) {
            int cx = x + SIZE / 2;
            int cy = y + SIZE / 2;
            g.drawOval(x + 3, y + 3, SIZE - 6, SIZE - 6);
            g.drawLine(cx, y + 5, cx, y + SIZE - 5);
            g.drawLine(x + 5, cy, x + SIZE - 5, cy);
            g.drawArc(x + 7, y + 4, 8, 14, 90, 180);
            g.drawArc(x + 7, y + 4, 8, 14, 270, 180);
            g.fillOval(cx - 2, cy - 2, 4, 4);
        }

        private void paintBossResearch(Graphics2D g, int x, int y) {
            int cx = x + SIZE / 2;
            int cy = y + SIZE / 2;
            g.drawOval(cx - 7, cy - 7, 14, 14);
            g.drawOval(cx - 3, cy - 3, 6, 6);
            g.drawLine(cx, y + 2, cx, y + 6);
            g.drawLine(cx, y + SIZE - 6, cx, y + SIZE - 2);
            g.drawLine(x + 2, cy, x + 6, cy);
            g.drawLine(x + SIZE - 6, cy, x + SIZE - 2, cy);
        }

        private void paintSettings(Graphics2D g, int x, int y) {
            int cx = x + SIZE / 2;
            int cy = y + SIZE / 2;
            g.drawOval(cx - 6, cy - 6, 12, 12);
            g.drawOval(cx - 2, cy - 2, 4, 4);
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                int x1 = cx + (int) Math.round(Math.cos(radians) * 7.0);
                int y1 = cy + (int) Math.round(Math.sin(radians) * 7.0);
                int x2 = cx + (int) Math.round(Math.cos(radians) * 9.0);
                int y2 = cy + (int) Math.round(Math.sin(radians) * 9.0);
                g.drawLine(x1, y1, x2, y2);
            }
        }
    }
}
