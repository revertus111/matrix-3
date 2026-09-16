package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

public final class CacheEditorFrame extends JFrame {

	private static final long serialVersionUID = 1L;

	private final CacheSession session;
	private final RawCachePanel rawCachePanel;
	private final CardLayout contentLayout = new CardLayout();
	private final JPanel content = new JPanel(contentLayout);
	private final JLabel modeStatus = new JLabel("READ ONLY");
	private final JButton modeButton = new JButton("Enable Edit Mode");

	public CacheEditorFrame(CacheSession session) {
		super("RS3 CacheEditor");
		this.session = session;
		this.rawCachePanel = new RawCachePanel(session);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(1280, 800);
		setMinimumSize(new Dimension(1040, 680));
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		getContentPane().setBackground(CacheEditorTheme.WINDOW);

		add(createHeader(), BorderLayout.NORTH);
		add(createSidebar(), BorderLayout.WEST);
		add(createContent(), BorderLayout.CENTER);
		setEditMode(false);
	}

	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(CacheEditorTheme.WINDOW);
		header.setBorder(CacheEditorTheme.panelPadding(11, 16, 10, 16));

		JLabel title = new JLabel("RS3 CACHE EDITOR");
		title.setFont(CacheEditorTheme.TITLE_FONT);
		title.setForeground(CacheEditorTheme.TEXT);
		header.add(title, BorderLayout.WEST);

		JLabel path = new JLabel(session.getCacheDirectory().getAbsolutePath());
		path.setFont(CacheEditorTheme.SMALL_FONT);
		path.setForeground(CacheEditorTheme.MUTED_TEXT);
		header.add(path, BorderLayout.EAST);
		return header;
	}

	private JPanel createSidebar() {
		JPanel sidebar = new JPanel(new BorderLayout());
		sidebar.setBackground(CacheEditorTheme.WINDOW);
		sidebar.setPreferredSize(new Dimension(190, 0));
		sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, CacheEditorTheme.BORDER));

		JPanel navigation = new JPanel();
		navigation.setLayout(new javax.swing.BoxLayout(navigation, javax.swing.BoxLayout.Y_AXIS));
		navigation.setBackground(CacheEditorTheme.WINDOW);
		navigation.setBorder(CacheEditorTheme.panelPadding(12, 10, 10, 10));
		ButtonGroup group = new ButtonGroup();

		addSection(navigation, "CACHE");
		JToggleButton raw = addNavigation(navigation, group, "Raw Cache", "raw", true);
		raw.setSelected(true);

		addSection(navigation, "DEFINITIONS");
		addNavigation(navigation, group, "Items", "items", true);
		addNavigation(navigation, group, "NPCs", "npcs", false);
		addNavigation(navigation, group, "Objects", "objects", false);

		addSection(navigation, "ASSETS");
		addNavigation(navigation, group, "Models", "models", false);
		addNavigation(navigation, group, "Sprites", "sprites", false);
		addNavigation(navigation, group, "Interfaces", "interfaces", false);

		addSection(navigation, "EFFECTS");
		addNavigation(navigation, group, "Animations", "animations", false);
		addNavigation(navigation, group, "GFX", "gfx", false);
		sidebar.add(navigation, BorderLayout.NORTH);

		JPanel safety = new JPanel();
		safety.setLayout(new javax.swing.BoxLayout(safety, javax.swing.BoxLayout.Y_AXIS));
		safety.setBackground(CacheEditorTheme.WINDOW);
		safety.setBorder(CacheEditorTheme.panelPadding(10, 10, 14, 10));
		modeStatus.setFont(CacheEditorTheme.SECTION_FONT);
		modeStatus.setAlignmentX(LEFT_ALIGNMENT);
		modeButton.setAlignmentX(LEFT_ALIGNMENT);
		modeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		CacheEditorTheme.styleButton(modeButton);
		modeButton.addActionListener(e -> toggleEditMode());
		safety.add(modeStatus);
		safety.add(javax.swing.Box.createVerticalStrut(7));
		safety.add(modeButton);
		sidebar.add(safety, BorderLayout.SOUTH);
		return sidebar;
	}

	private JPanel createContent() {
		content.setBackground(CacheEditorTheme.PANEL);
		content.add(rawCachePanel, "raw");
		content.add(new ItemBrowserPanel(session), "items");
		content.add(createPlaceholder("NPC Editor"), "npcs");
		content.add(createPlaceholder("Object Editor"), "objects");
		content.add(createPlaceholder("Model Viewer / Editor"), "models");
		content.add(createPlaceholder("Sprite Editor"), "sprites");
		content.add(createPlaceholder("Interface Editor"), "interfaces");
		content.add(createPlaceholder("Animation Viewer / Editor"), "animations");
		content.add(createPlaceholder("GFX Viewer / Editor"), "gfx");
		contentLayout.show(content, "raw");
		return content;
	}

	private void addSection(JPanel navigation, String text) {
		if (navigation.getComponentCount() > 0) {
			navigation.add(javax.swing.Box.createVerticalStrut(14));
		}
		JLabel label = new JLabel(text);
		label.setFont(CacheEditorTheme.SMALL_FONT);
		label.setForeground(CacheEditorTheme.MUTED_TEXT);
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 4, 5, 0));
		navigation.add(label);
	}

	private JToggleButton addNavigation(JPanel navigation, ButtonGroup group, String text, final String card, boolean enabled) {
		JToggleButton button = new JToggleButton(text);
		button.setHorizontalAlignment(JToggleButton.LEFT);
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 37));
		button.setEnabled(enabled);
		CacheEditorTheme.styleButton(button);
		button.addActionListener(e -> contentLayout.show(content, card));
		group.add(button);
		navigation.add(button);
		navigation.add(javax.swing.Box.createVerticalStrut(4));
		return button;
	}

	private JPanel createPlaceholder(String name) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(CacheEditorTheme.PANEL);
		panel.setBorder(CacheEditorTheme.panelPadding(24, 24, 24, 24));
		JLabel label = new JLabel(name + " is planned but not enabled in this phase.", JLabel.CENTER);
		label.setFont(CacheEditorTheme.BODY_FONT);
		label.setForeground(CacheEditorTheme.MUTED_TEXT);
		panel.add(label, BorderLayout.CENTER);
		return panel;
	}

	private void toggleEditMode() {
		if (session.isWriteEnabled()) {
			setEditMode(false);
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
				"Enable cache writes for this session?\n\nRaw cache writes can corrupt a cache if the wrong coordinates or bytes are used.\n"
						+ "Use a disposable cache copy until your workflow is proven. Existing bytes are backed up before replacement.",
				"Enable Edit Mode", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer == JOptionPane.YES_OPTION) {
			setEditMode(true);
		}
	}

	private void setEditMode(boolean enabled) {
		session.setWriteEnabled(enabled);
		rawCachePanel.setWriteEnabled(enabled);
		modeStatus.setText(enabled ? "EDIT MODE" : "READ ONLY");
		modeStatus.setForeground(enabled ? CacheEditorTheme.ACCENT : CacheEditorTheme.MUTED_TEXT);
		modeButton.setText(enabled ? "Return to Read Only" : "Enable Edit Mode");
	}
}
