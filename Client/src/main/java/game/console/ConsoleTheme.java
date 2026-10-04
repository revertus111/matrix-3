package game.console;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

public final class ConsoleTheme {

    public static final Color WINDOW = new Color(11, 14, 18);
    public static final Color RAIL = new Color(15, 18, 22);
    public static final Color PANEL = new Color(16, 20, 25);
    public static final Color CARD = new Color(21, 25, 30);
    public static final Color CARD_HOVER = new Color(55, 42, 25);
    public static final Color INPUT = new Color(12, 16, 20);
    public static final Color BORDER = new Color(112, 82, 40);
    public static final Color TEXT = new Color(235, 227, 205);
    public static final Color MUTED_TEXT = new Color(158, 164, 171);
    public static final Color ACCENT = new Color(111, 169, 221);
    public static final Color ACCENT_DARK = new Color(67, 111, 153);
    public static final Color GOLD = new Color(224, 177, 77);
    public static final Color GOLD_BRIGHT = new Color(246, 208, 119);
    public static final Color GOLD_DARK = new Color(88, 59, 26);

    public static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 18);
    public static final Font SECTION_FONT = new Font("Serif", Font.BOLD, 14);
    public static final Font BODY_FONT = new Font("Serif", Font.PLAIN, 13);
    public static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private ConsoleTheme() {
    }

    public static Border panelPadding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TITLE_FONT);
        label.setForeground(GOLD_BRIGHT);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    public static JLabel subtitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(ACCENT);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    public static JPanel createCard(String titleText) {
        JPanel card = new JPanel();
        card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                panelPadding(14, 14, 14, 14)));
        card.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel title = new JLabel(titleText);
        title.setFont(SECTION_FONT);
        title.setForeground(GOLD_BRIGHT);
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        card.add(title);
        return card;
    }

    public static JLabel createValueLabel() {
        JLabel label = new JLabel();
        label.setFont(BODY_FONT);
        label.setForeground(TEXT);
        return label;
    }

    public static JPanel createValueRow(String labelText, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(CARD);
        row.setOpaque(true);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel label = new JLabel(labelText);
        label.setFont(SMALL_FONT);
        label.setForeground(MUTED_TEXT);

        row.add(label, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    public static JTextArea createWrappedText(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 1);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setFont(SMALL_FONT);
        area.setForeground(MUTED_TEXT);
        area.setBorder(null);
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, area.getPreferredSize().height));
        return area;
    }

    public static void styleButton(final AbstractButton button) {
        button.setFont(BODY_FONT);
        button.setForeground(TEXT);
        button.setBackground(CARD);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        button.getModel().addChangeListener(e -> refreshButton(button));
        refreshButton(button);
    }

    public static void styleRailButton(final AbstractButton button) {
        styleButton(button);
        button.setFont(new Font("Serif", Font.BOLD, 14));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(9, 0, 9, 0)));
    }

    private static void refreshButton(AbstractButton button) {
        if (!button.isEnabled()) {
            button.setForeground(MUTED_TEXT);
            button.setBackground(PANEL);
        } else if (button.getModel().isPressed()) {
            button.setForeground(GOLD_BRIGHT);
            button.setBackground(GOLD_DARK);
        } else if (button.getModel().isSelected()) {
            button.setForeground(GOLD_BRIGHT);
            button.setBackground(new Color(67, 48, 27));
        } else if (button.getModel().isRollover()) {
            button.setForeground(GOLD_BRIGHT);
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

    public static void styleComboBox(JComboBox<?> combo) {
        combo.setFont(BODY_FONT);
        combo.setForeground(TEXT);
        combo.setBackground(INPUT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBorder(BorderFactory.createLineBorder(BORDER));
    }

    public static void styleStatus(JLabel label, boolean accent) {
        label.setFont(SMALL_FONT);
        label.setForeground(accent ? ACCENT : MUTED_TEXT);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    }

    public static void styleLabel(JLabel label, boolean muted) {
        label.setFont(BODY_FONT);
        label.setForeground(muted ? MUTED_TEXT : TEXT);
    }

    public static void styleList(JList<?> list) {
        list.setFont(BODY_FONT);
        list.setForeground(TEXT);
        list.setBackground(PANEL);
        list.setSelectionForeground(GOLD_BRIGHT);
        list.setSelectionBackground(GOLD_DARK);
        list.setBorder(BorderFactory.createEmptyBorder());
        list.setOpaque(true);
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(PANEL);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setBackground(PANEL);
    }

    public static void stylePopupMenu(JPopupMenu popup) {
        popup.setBackground(CARD);
        popup.setBorder(BorderFactory.createLineBorder(BORDER));
    }

    public static void styleMenu(JMenu menu) {
        menu.setFont(BODY_FONT);
        menu.setForeground(TEXT);
        menu.setBackground(CARD);
        menu.setOpaque(true);
    }

    public static void styleMenuItem(JMenuItem item) {
        item.setFont(BODY_FONT);
        item.setForeground(TEXT);
        item.setBackground(CARD);
        item.setOpaque(true);
    }

    public static void makeTransparent(JComponent component) {
        component.setOpaque(false);
    }
}
