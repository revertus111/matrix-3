package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.alex.utils.Constants;
import com.rs.cache.loaders.ItemDefinitions;

public final class ItemBrowserPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int MAX_RESULTS = 250;
	private static final int SEARCH_DEBOUNCE_MS = 250;

	private final CacheSession session;
	private final JTextField searchField = new JTextField();
	private final Timer searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> search());
	private final DefaultListModel<ItemEntry> resultModel = new DefaultListModel<ItemEntry>();
	private final JList<ItemEntry> results = new JList<ItemEntry>(resultModel);
	private final JLabel status = new JLabel("Indexing item definitions...");
	private final JLabel resultCount = new JLabel("0 items");
	private final DetailPanel detailContent = new DetailPanel();
	private volatile List<ItemEntry> itemIndex;
	private volatile boolean indexBuilding;

	public ItemBrowserPanel(CacheSession session) {
		super(new BorderLayout());
		this.session = session;
		setBackground(CacheEditorTheme.PANEL);
		searchDebounce.setRepeats(false);

		add(createHeader(), BorderLayout.NORTH);
		add(createBrowser(), BorderLayout.CENTER);
		installListeners();
		showEmptyDetails();
		buildIndex();
	}

	private JPanel createHeader() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(CacheEditorTheme.PANEL);
		panel.setBorder(CacheEditorTheme.panelPadding(18, 16, 12, 16));

		JLabel title = new JLabel("ITEM BROWSER");
		title.setFont(CacheEditorTheme.TITLE_FONT);
		title.setForeground(CacheEditorTheme.TEXT);
		title.setAlignmentX(LEFT_ALIGNMENT);

		status.setFont(CacheEditorTheme.SMALL_FONT);
		status.setForeground(CacheEditorTheme.ACCENT);
		status.setAlignmentX(LEFT_ALIGNMENT);

		searchField.setToolTipText("Search by item ID or name");
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		searchField.setAlignmentX(LEFT_ALIGNMENT);
		CacheEditorTheme.styleTextField(searchField);

		panel.add(title);
		panel.add(Box.createVerticalStrut(3));
		panel.add(status);
		panel.add(Box.createVerticalStrut(12));
		panel.add(searchField);
		return panel;
	}

	private JSplitPane createBrowser() {
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createResultsPanel(), createDetailsPanel());
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setDividerSize(5);
		split.setResizeWeight(0.34);
		split.setBackground(CacheEditorTheme.BORDER);
		return split;
	}

	private JPanel createResultsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(CacheEditorTheme.PANEL);
		panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CacheEditorTheme.BORDER));
		panel.setPreferredSize(new Dimension(360, 560));

		JLabel heading = new JLabel("RESULTS");
		heading.setFont(CacheEditorTheme.SECTION_FONT);
		heading.setForeground(CacheEditorTheme.MUTED_TEXT);
		heading.setBorder(CacheEditorTheme.panelPadding(10, 16, 8, 16));
		panel.add(heading, BorderLayout.NORTH);

		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		results.setFixedCellHeight(54);
		results.setCellRenderer(new ItemRenderer());
		CacheEditorTheme.styleList(results);
		JScrollPane scroll = new JScrollPane(results);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		CacheEditorTheme.styleScrollPane(scroll);
		scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, CacheEditorTheme.BORDER));
		panel.add(scroll, BorderLayout.CENTER);

		resultCount.setFont(CacheEditorTheme.SMALL_FONT);
		resultCount.setForeground(CacheEditorTheme.MUTED_TEXT);
		resultCount.setBorder(CacheEditorTheme.panelPadding(8, 16, 10, 16));
		panel.add(resultCount, BorderLayout.SOUTH);
		return panel;
	}

	private JScrollPane createDetailsPanel() {
		detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
		detailContent.setBackground(CacheEditorTheme.PANEL);
		detailContent.setBorder(CacheEditorTheme.panelPadding(12, 14, 18, 14));
		JScrollPane scroll = new JScrollPane(detailContent);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		CacheEditorTheme.styleScrollPane(scroll);
		scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CacheEditorTheme.BORDER));
		return scroll;
	}

	private void installListeners() {
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				scheduleSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				scheduleSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				scheduleSearch();
			}
		});
		searchField.addActionListener(e -> {
			searchDebounce.stop();
			search();
		});
		results.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				ItemEntry selected = results.getSelectedValue();
				if (selected != null) {
					showItem(selected.id);
				}
			}
		});
	}

	private void scheduleSearch() {
		searchDebounce.restart();
	}

	private void search() {
		String query = searchField.getText() == null ? "" : searchField.getText().trim();
		if (isInteger(query)) {
			loadExactId(query);
			return;
		}
		if (itemIndex == null) {
			status.setText(indexBuilding ? "Indexing item definitions in the background..." : "Item index is not ready.");
			return;
		}
		filterIndex(query);
	}

	private void loadExactId(String query) {
		try {
			int id = Integer.parseInt(query);
			resultModel.clear();
			ItemDefinitions def = ItemDefinitions.getItemDefinitions(id);
			if (!def.isLoaded()) {
				status.setText("Item " + id + " is not present in this cache.");
				resultCount.setText("0 items");
				showEmptyDetails();
				return;
			}
			ItemEntry entry = new ItemEntry(id, safeName(def));
			resultModel.addElement(entry);
			resultCount.setText("1 item");
			results.setSelectedIndex(0);
			status.setText("Loaded item " + id + ".");
		} catch (RuntimeException e) {
			showError("Failed to decode item " + query + ": " + e.getMessage());
		}
	}

	private void buildIndex() {
		if (indexBuilding || itemIndex != null) {
			return;
		}
		indexBuilding = true;
		status.setText("Indexing item definitions in the background...");
		new SwingWorker<IndexBuildResult, Void>() {
			@Override
			protected IndexBuildResult doInBackground() {
				List<ItemEntry> entries = new ArrayList<ItemEntry>();
				int skipped = 0;
				int lastArchive = session.getLastArchiveId(Constants.ITEM_DEFINITIONS_INDEX);
				long maxExclusiveLong = ((long) lastArchive + 1L) << 8;
				int maxExclusive = maxExclusiveLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxExclusiveLong;
				for (int id = 0; id < maxExclusive; id++) {
					try {
						ItemDefinitions def = ItemDefinitions.getItemDefinitions(id);
						if (def.isLoaded()) {
							entries.add(new ItemEntry(id, safeName(def)));
						}
					} catch (RuntimeException e) {
						skipped++;
					}
				}
				return new IndexBuildResult(Collections.unmodifiableList(entries), skipped);
			}

			@Override
			protected void done() {
				indexBuilding = false;
				try {
					IndexBuildResult built = get();
					itemIndex = built.entries;
					status.setText("Indexed " + built.entries.size() + " items; skipped " + built.skipped
							+ " definitions that failed to decode.");
					search();
				} catch (Exception e) {
					showError("Failed to build item index: " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void filterIndex(final String query) {
		final String normalized = query.toLowerCase();
		status.setText(normalized.length() == 0 ? "Showing the first cache items." : "Searching items...");
		new SwingWorker<List<ItemEntry>, Void>() {
			@Override
			protected List<ItemEntry> doInBackground() {
				List<ItemEntry> matches = new ArrayList<ItemEntry>();
				for (ItemEntry entry : itemIndex) {
					if (normalized.length() == 0 || entry.name.toLowerCase().contains(normalized)) {
						matches.add(entry);
						if (matches.size() >= MAX_RESULTS) {
							break;
						}
					}
				}
				return matches;
			}

			@Override
			protected void done() {
				try {
					String current = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
					if (!normalized.equals(current) || isInteger(current)) {
						return;
					}
					List<ItemEntry> matches = get();
					resultModel.clear();
					for (ItemEntry match : matches) {
						resultModel.addElement(match);
					}
					resultCount.setText(matches.size() + (matches.size() == MAX_RESULTS ? "+" : "") + " items");
					status.setText(normalized.length() == 0
							? "Browse by ID order or type an item ID/name to filter."
							: matches.size() + (matches.size() == MAX_RESULTS ? "+" : "") + " matching items.");
					if (!matches.isEmpty()) {
						results.setSelectedIndex(0);
					} else {
						showEmptyDetails();
					}
				} catch (Exception e) {
					showError("Item search failed: " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void showItem(int id) {
		try {
			ItemDefinitions def = ItemDefinitions.getItemDefinitions(id);
			detailContent.removeAll();
			detailContent.add(createSelectionCard(id, safeName(def), def.modelId));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("SUMMARY", new Object[][] {
					{ "ID", Integer.valueOf(id) }, { "Name", def.name }, { "Value", Integer.valueOf(def.value) },
					{ "Members only", Boolean.valueOf(def.membersOnly) }, { "Stackable", Integer.valueOf(def.stackable) } }));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("MODELS", new Object[][] {
					{ "Inventory model", Integer.valueOf(def.modelId) }, { "Model zoom", Integer.valueOf(def.modelZoom) },
					{ "Rotation 1", Integer.valueOf(def.modelRotation1) }, { "Rotation 2", Integer.valueOf(def.modelRotation2) },
					{ "Offset 1", Integer.valueOf(def.modelOffset1) }, { "Offset 2", Integer.valueOf(def.modelOffset2) },
					{ "Male equip 1", Integer.valueOf(def.maleEquip1) }, { "Male equip 2", Integer.valueOf(def.maleEquip2) },
					{ "Male equip 3", Integer.valueOf(def.maleEquipModelId3) }, { "Female equip 1", Integer.valueOf(def.femaleEquip1) },
					{ "Female equip 2", Integer.valueOf(def.femaleEquip2) }, { "Female equip 3", Integer.valueOf(def.femaleEquipModelId3) } }));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("EQUIPMENT", new Object[][] {
					{ "Equip slot", Integer.valueOf(def.equipSlot) }, { "Hide slot", Integer.valueOf(def.equipLookHideSlot) },
					{ "Hide slot 2", Integer.valueOf(def.equipLookHideSlot2) }, { "Team ID", Integer.valueOf(def.teamId) } }));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("OPTIONS", new Object[][] { { "Ground options", Arrays.toString(def.groundOptions) },
					{ "Inventory options", Arrays.toString(def.inventoryOptions) } }));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("APPEARANCE", new Object[][] {
					{ "Original model colors", arrayToString(def.originalModelColors) },
					{ "Modified model colors", arrayToString(def.modifiedModelColors) },
					{ "Original texture colors", arrayToString(def.originalTextureColors) },
					{ "Modified texture colors", arrayToString(def.modifiedTextureColors) } }));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("VARIANTS", new Object[][] { { "Cert ID", Integer.valueOf(def.certId) },
					{ "Cert template", Integer.valueOf(def.certTemplateId) }, { "Lend ID", Integer.valueOf(def.lendId) },
					{ "Lend template", Integer.valueOf(def.lendTemplateId) }, { "Bind ID", Integer.valueOf(def.bindId) },
					{ "Bind template", Integer.valueOf(def.bindTemplateId) } }));
			detailContent.add(Box.createVerticalStrut(10));
			detailContent.add(createCard("CLIENT SCRIPT PARAMS", new Object[][] {
					{ "Params", def.clientScriptData == null ? "{}" : def.clientScriptData.toString() } }));
			detailContent.add(Box.createVerticalGlue());
			detailContent.revalidate();
			detailContent.repaint();
		} catch (RuntimeException e) {
			showError("Failed to decode item " + id + ": " + e.getMessage());
		}
	}

	private JPanel createSelectionCard(int id, String name, int modelId) {
		JPanel card = new JPanel(new BorderLayout(12, 0));
		card.setBackground(CacheEditorTheme.CARD);
		card.setBorder(CacheEditorTheme.cardBorder());
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

		JLabel badge = new JLabel("#" + id, SwingConstants.CENTER);
		badge.setPreferredSize(new Dimension(76, 54));
		badge.setFont(CacheEditorTheme.SECTION_FONT);
		badge.setForeground(CacheEditorTheme.ACCENT);
		badge.setOpaque(true);
		badge.setBackground(CacheEditorTheme.INPUT);
		badge.setBorder(BorderFactory.createLineBorder(CacheEditorTheme.BORDER));
		card.add(badge, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(CacheEditorTheme.CARD);
		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(CacheEditorTheme.TITLE_FONT);
		nameLabel.setForeground(CacheEditorTheme.TEXT);
		nameLabel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel meta = new JLabel("Inventory model " + modelId + "   ·   read-only definition");
		meta.setFont(CacheEditorTheme.SMALL_FONT);
		meta.setForeground(CacheEditorTheme.MUTED_TEXT);
		meta.setAlignmentX(LEFT_ALIGNMENT);
		text.add(Box.createVerticalGlue());
		text.add(nameLabel);
		text.add(Box.createVerticalStrut(4));
		text.add(meta);
		text.add(Box.createVerticalGlue());
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private JPanel createCard(String titleText, Object[][] rows) {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CacheEditorTheme.CARD);
		card.setBorder(CacheEditorTheme.cardBorder());
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JLabel title = new JLabel(titleText);
		title.setFont(CacheEditorTheme.SECTION_FONT);
		title.setForeground(CacheEditorTheme.TEXT);
		title.setAlignmentX(LEFT_ALIGNMENT);
		card.add(title);
		card.add(Box.createVerticalStrut(8));

		for (int index = 0; index < rows.length; index++) {
			card.add(createDetailRow(String.valueOf(rows[index][0]), rows[index][1]));
			if (index + 1 < rows.length) {
				card.add(Box.createVerticalStrut(5));
			}
		}
		return card;
	}

	private JPanel createDetailRow(String field, Object value) {
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setBackground(CacheEditorTheme.CARD);
		row.setAlignmentX(LEFT_ALIGNMENT);

		JLabel fieldLabel = new JLabel(field);
		fieldLabel.setFont(CacheEditorTheme.SMALL_FONT);
		fieldLabel.setForeground(CacheEditorTheme.MUTED_TEXT);
		fieldLabel.setPreferredSize(new Dimension(155, 20));
		row.add(fieldLabel, BorderLayout.WEST);

		JLabel valueLabel = new JLabel("<html>" + escapeHtml(value == null ? "" : String.valueOf(value)) + "</html>");
		valueLabel.setFont(CacheEditorTheme.BODY_FONT);
		valueLabel.setForeground(CacheEditorTheme.TEXT);
		row.add(valueLabel, BorderLayout.CENTER);
		return row;
	}

	private void showEmptyDetails() {
		detailContent.removeAll();
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CacheEditorTheme.CARD);
		card.setBorder(CacheEditorTheme.cardBorder());
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
		JLabel title = new JLabel("SELECT AN ITEM");
		title.setFont(CacheEditorTheme.SECTION_FONT);
		title.setForeground(CacheEditorTheme.TEXT);
		JLabel body = new JLabel("Search by ID or name, then select a result to inspect its cache definition.");
		body.setFont(CacheEditorTheme.BODY_FONT);
		body.setForeground(CacheEditorTheme.MUTED_TEXT);
		card.add(title);
		card.add(Box.createVerticalStrut(8));
		card.add(body);
		detailContent.add(card);
		detailContent.add(Box.createVerticalGlue());
		detailContent.revalidate();
		detailContent.repaint();
	}

	private static boolean isInteger(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return value.length() > 0;
	}

	private static String safeName(ItemDefinitions def) {
		return def.name == null ? "null" : def.name;
	}

	private static String arrayToString(int[] values) {
		return values == null ? "[]" : Arrays.toString(values);
	}

	private static String arrayToString(short[] values) {
		return values == null ? "[]" : Arrays.toString(values);
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void showError(String message) {
		status.setText(message);
		JOptionPane.showMessageDialog(this, message, "RS3 CacheEditor", JOptionPane.ERROR_MESSAGE);
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.toString() : current.getMessage();
	}

	private static final class ItemRenderer extends JPanel implements ListCellRenderer<ItemEntry> {
		private static final long serialVersionUID = 1L;
		private final JLabel name = new JLabel();
		private final JLabel meta = new JLabel();

		private ItemRenderer() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBorder(CacheEditorTheme.panelPadding(7, 12, 7, 12));
			setOpaque(true);
			name.setFont(CacheEditorTheme.SECTION_FONT);
			name.setForeground(CacheEditorTheme.TEXT);
			name.setAlignmentX(LEFT_ALIGNMENT);
			meta.setFont(CacheEditorTheme.SMALL_FONT);
			meta.setForeground(CacheEditorTheme.MUTED_TEXT);
			meta.setAlignmentX(LEFT_ALIGNMENT);
			add(name);
			add(Box.createVerticalStrut(3));
			add(meta);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends ItemEntry> list, ItemEntry value, int index,
				boolean isSelected, boolean cellHasFocus) {
			name.setText(value.name);
			meta.setText("ID " + value.id);
			setBackground(isSelected ? CacheEditorTheme.CARD_HOVER : CacheEditorTheme.PANEL);
			return this;
		}
	}

	private static final class ItemEntry {
		private final int id;
		private final String name;

		private ItemEntry(int id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	private static final class IndexBuildResult {
		private final List<ItemEntry> entries;
		private final int skipped;

		private IndexBuildResult(List<ItemEntry> entries, int skipped) {
			this.entries = entries;
			this.skipped = skipped;
		}
	}

	private static final class DetailPanel extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return 18;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return Math.max(18, visibleRect.height - 18);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}
}
