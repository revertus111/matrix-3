package com.rs.tools.cacheeditor;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;

public final class CacheEditorTheme {

	public static final Color WINDOW = new Color(20, 23, 28);
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

	private CacheEditorTheme() {
	}

	public static void install() {
		UIManager.put("Panel.background", PANEL);
		UIManager.put("Label.foreground", TEXT);
		UIManager.put("Button.background", CARD);
		UIManager.put("Button.foreground", TEXT);
		UIManager.put("Button.select", ACCENT_DARK);
		UIManager.put("TextField.background", INPUT);
		UIManager.put("TextField.foreground", TEXT);
		UIManager.put("TextField.caretForeground", TEXT);
		UIManager.put("TextArea.background", INPUT);
		UIManager.put("TextArea.foreground", TEXT);
		UIManager.put("List.background", PANEL);
		UIManager.put("List.foreground", TEXT);
		UIManager.put("List.selectionBackground", CARD_HOVER);
		UIManager.put("List.selectionForeground", TEXT);
		UIManager.put("Table.background", PANEL);
		UIManager.put("Table.foreground", TEXT);
		UIManager.put("Table.selectionBackground", CARD_HOVER);
		UIManager.put("Table.selectionForeground", TEXT);
		UIManager.put("Table.gridColor", BORDER);
		UIManager.put("TableHeader.background", CARD);
		UIManager.put("TableHeader.foreground", TEXT);
		UIManager.put("ScrollPane.background", PANEL);
		UIManager.put("Viewport.background", PANEL);
		UIManager.put("TabbedPane.background", WINDOW);
		UIManager.put("TabbedPane.foreground", TEXT);
		UIManager.put("TabbedPane.selected", CARD_HOVER);
		UIManager.put("TabbedPane.contentAreaColor", PANEL);
		UIManager.put("SplitPane.background", BORDER);
		UIManager.put("OptionPane.background", PANEL);
		UIManager.put("OptionPane.messageForeground", TEXT);
		UIManager.put("FileChooser.background", PANEL);
	}

	public static Border panelPadding(int top, int left, int bottom, int right) {
		return BorderFactory.createEmptyBorder(top, left, bottom, right);
	}

	public static Border cardBorder() {
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), panelPadding(12, 12, 12, 12));
	}

	public static void styleButton(final AbstractButton button) {
		button.setFont(BODY_FONT);
		button.setForeground(TEXT);
		button.setBackground(CARD);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				BorderFactory.createEmptyBorder(7, 10, 7, 10)));
		button.getModel().addChangeListener(e -> refreshButton(button));
		refreshButton(button);
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
		field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				BorderFactory.createEmptyBorder(8, 9, 8, 9)));
	}

	public static void styleTextArea(JTextArea area) {
		area.setFont(BODY_FONT);
		area.setForeground(TEXT);
		area.setCaretColor(TEXT);
		area.setSelectionColor(ACCENT_DARK);
		area.setSelectedTextColor(TEXT);
		area.setBackground(INPUT);
	}

	public static void styleList(JList<?> list) {
		list.setFont(BODY_FONT);
		list.setForeground(TEXT);
		list.setBackground(PANEL);
		list.setSelectionForeground(TEXT);
		list.setSelectionBackground(CARD_HOVER);
	}

	public static void styleScrollPane(JScrollPane scrollPane) {
		scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
		scrollPane.getViewport().setBackground(PANEL);
		scrollPane.getVerticalScrollBar().setUnitIncrement(18);
		scrollPane.setBackground(PANEL);
	}

	public static void makeTransparent(JComponent component) {
		component.setOpaque(false);
	}
}
