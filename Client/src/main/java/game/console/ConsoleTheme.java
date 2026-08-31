package game.console;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.Border;

public final class ConsoleTheme {

    public static final Color WINDOW = new Color(20, 23, 28);
    public static final Color RAIL = new Color(24, 28, 34);
    public static final Color PANEL = new Color(28, 32, 39);
    public static final Color CARD = new Color(34, 39, 47);
    public static final Color CARD_HOVER = new Color(42, 48, 58);
    public static final Color INPUT = new Color(23, 27, 33);
    public static final Color BORDER = new Color(52, 59, 69);
    public static final Color TEXT = new Color(232, 235, 239);
    public static final Color MUTED_TEXT = new Color(153, 162, 173);
    public static final Color ACCENT = new Color(92, 155, 230);
    public static final Color ACCENT_DARK = new Color(67, 117, 178);

    public static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 17);
    public static final Font SECTION_FONT = new Font("SansSerif", Font.BOLD, 13);
    public static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private ConsoleTheme() {
    }

    public static Border panelPadding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static void styleButton(final AbstractButton button) {
        button.setFont(BODY_FONT);
        button.setForeground(TEXT);
        button.setBackground(CARD);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        button.getModel().addChangeListener(e -> refreshButton(button));
        refreshButton(button);
    }

    public static void styleRailButton(final AbstractButton button) {
        styleButton(button);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
    }

    private static void refreshButton(AbstractButton button) {
        if (!button.isEnabled()) {
            button.setForeground(MUTED_TEXT);
            button.setBackground(PANEL);
        } else if (button.getModel().isPressed()) {
            button.setForeground(TEXT);
            button.setBackground(ACCENT_DARK);
        } else if (button.getModel().isSelected()) {
            button.setForeground(TEXT);
            button.setBackground(ACCENT);
        } else if (button.getModel().isRollover()) {
            button.setForeground(TEXT);
            button.setBackground(CARD_HOVER);
        } else {
            button.setForeground(TEXT);
            button.setBackground(CARD);
        }
    }

    public static void styleTextField(JTextField field) {
        field.setFont(BODY_FONT);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setSelectionColor(ACCENT_DARK);
        field.setSelectedTextColor(TEXT);
        field.setBackground(INPUT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));
    }

    public static void styleLabel(JLabel label, boolean muted) {
        label.setFont(BODY_FONT);
        label.setForeground(muted ? MUTED_TEXT : TEXT);
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(PANEL);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setBackground(PANEL);
    }

    public static void makeTransparent(JComponent component) {
        component.setOpaque(false);
    }
}
