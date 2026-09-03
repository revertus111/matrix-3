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
		setSize(1180, 760);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());

		JLabel path = new JLabel("  Cache: " + session.getCacheDirectory().getAbsolutePath());
		path.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		add(path, BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
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
		panel.add(new JLabel(name + " editor will use the shared cache session in a later slice.", JLabel.CENTER),
				BorderLayout.CENTER);
		tabs.addTab(name, panel);
		tabs.setEnabledAt(tabs.getTabCount() - 1, false);
	}
}
