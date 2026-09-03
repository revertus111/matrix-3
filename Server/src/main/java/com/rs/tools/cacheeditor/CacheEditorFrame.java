package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public final class CacheEditorFrame extends JFrame {

	private static final long serialVersionUID = 1L;

	public CacheEditorFrame(CacheSession session) {
		super("RS3 CacheEditor");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(1220, 780);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		getContentPane().setBackground(CacheEditorTheme.WINDOW);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(CacheEditorTheme.WINDOW);
		header.setBorder(CacheEditorTheme.panelPadding(10, 14, 8, 14));
		JLabel path = new JLabel("CACHE  " + session.getCacheDirectory().getAbsolutePath());
		path.setFont(CacheEditorTheme.SMALL_FONT);
		path.setForeground(CacheEditorTheme.MUTED_TEXT);
		header.add(path, BorderLayout.WEST);
		add(header, BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.setBackground(CacheEditorTheme.WINDOW);
		tabs.setForeground(CacheEditorTheme.TEXT);
		tabs.setFont(CacheEditorTheme.BODY_FONT);
		tabs.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		tabs.addTab("Raw Cache", new RawCachePanel(session));
		tabs.addTab("Items", new ItemBrowserPanel(session));
		addPlaceholder(tabs, "NPCs");
		addPlaceholder(tabs, "Objects");
		addPlaceholder(tabs, "Models");
		addPlaceholder(tabs, "GFX");
		addPlaceholder(tabs, "Animations");
		addPlaceholder(tabs, "Sprites");
		addPlaceholder(tabs, "Interfaces");
		add(tabs, BorderLayout.CENTER);
	}

	private static void addPlaceholder(JTabbedPane tabs, String name) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(CacheEditorTheme.PANEL);
		panel.setBorder(CacheEditorTheme.panelPadding(24, 24, 24, 24));
		JLabel label = new JLabel(name + " editor will use the shared cache session in a later slice.", JLabel.CENTER);
		label.setFont(CacheEditorTheme.BODY_FONT);
		label.setForeground(CacheEditorTheme.MUTED_TEXT);
		panel.add(label, BorderLayout.CENTER);
		tabs.addTab(name, panel);
		tabs.setEnabledAt(tabs.getTabCount() - 1, false);
	}
}
