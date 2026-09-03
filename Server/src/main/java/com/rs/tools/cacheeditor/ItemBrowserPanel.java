package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.alex.utils.Constants;
import com.rs.cache.loaders.ItemDefinitions;

public final class ItemBrowserPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int MAX_RESULTS = 250;

	private final CacheSession session;
	private final JTextField searchField = new JTextField();
	private final DefaultListModel<ItemEntry> resultModel = new DefaultListModel<ItemEntry>();
	private final JList<ItemEntry> results = new JList<ItemEntry>(resultModel);
	private final DefaultTableModel detailModel = new DefaultTableModel(new Object[] { "Field", "Value" }, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JLabel status = new JLabel("Enter an item ID or name. Definition editing is read-only in this foundation slice.");
	private volatile List<ItemEntry> itemIndex;
	private volatile boolean indexBuilding;

	public ItemBrowserPanel(CacheSession session) {
		super(new BorderLayout(6, 6));
		this.session = session;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel search = new JPanel(new BorderLayout(6, 0));
		search.add(searchField, BorderLayout.CENTER);
		JButton searchButton = new JButton("Search");
		search.add(searchButton, BorderLayout.EAST);
		add(search, BorderLayout.NORTH);

		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane resultScroll = new JScrollPane(results);
		resultScroll.setPreferredSize(new Dimension(330, 500));

		JTable details = new JTable(detailModel);
		details.setFillsViewportHeight(true);
		JPanel right = new JPanel(new BorderLayout());
		right.add(new JLabel("Item definition", JLabel.CENTER), BorderLayout.NORTH);
		right.add(new JScrollPane(details), BorderLayout.CENTER);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultScroll, right);
		split.setResizeWeight(0.3);
		add(split, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		bottom.add(status);
		add(bottom, BorderLayout.SOUTH);

		searchButton.addActionListener(e -> search());
		searchField.addActionListener(e -> search());
		results.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				ItemEntry selected = results.getSelectedValue();
				if (selected != null) {
					showItem(selected.id);
				}
			}
		});
	}

	private void search() {
		String query = searchField.getText().trim();
		if (query.length() == 0) {
			return;
		}
		if (isInteger(query)) {
			try {
				int id = Integer.parseInt(query);
				resultModel.clear();
				ItemDefinitions def = ItemDefinitions.getItemDefinitions(id);
				if (!def.isLoaded()) {
					status.setText("Item " + id + " is not present in this cache.");
					detailModel.setRowCount(0);
					return;
				}
				ItemEntry entry = new ItemEntry(id, safeName(def));
				resultModel.addElement(entry);
				results.setSelectedIndex(0);
				status.setText("Loaded item " + id + ".");
			} catch (RuntimeException e) {
				showError("Failed to decode item " + query + ": " + e.getMessage());
			}
			return;
		}

		if (itemIndex == null) {
			buildIndexAndSearch(query);
		} else {
			filterIndex(query);
		}
	}

	private void buildIndexAndSearch(final String query) {
		if (indexBuilding) {
			status.setText("Item name index is still building...");
			return;
		}
		indexBuilding = true;
		status.setText("Building item name index in the background...");
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
					filterIndex(query);
				} catch (Exception e) {
					showError("Failed to build item index: " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void filterIndex(final String query) {
		status.setText("Searching items...");
		new SwingWorker<List<ItemEntry>, Void>() {
			@Override
			protected List<ItemEntry> doInBackground() {
				String needle = query.toLowerCase();
				List<ItemEntry> matches = new ArrayList<ItemEntry>();
				for (ItemEntry entry : itemIndex) {
					if (entry.name.toLowerCase().contains(needle)) {
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
					List<ItemEntry> matches = get();
					resultModel.clear();
					for (ItemEntry match : matches) {
						resultModel.addElement(match);
					}
					status.setText(matches.size() + (matches.size() == MAX_RESULTS ? "+" : "") + " matching items.");
					if (!matches.isEmpty()) {
						results.setSelectedIndex(0);
					} else {
						detailModel.setRowCount(0);
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
			detailModel.setRowCount(0);
			addDetail("ID", id);
			addDetail("Name", def.name);
			addDetail("Inventory model", def.modelId);
			addDetail("Model zoom", def.modelZoom);
			addDetail("Rotation 1", def.modelRotation1);
			addDetail("Rotation 2", def.modelRotation2);
			addDetail("Offset 1", def.modelOffset1);
			addDetail("Offset 2", def.modelOffset2);
			addDetail("Stackable", def.stackable);
			addDetail("Value", def.value);
			addDetail("Members only", def.membersOnly);
			addDetail("Male equip 1", def.maleEquip1);
			addDetail("Male equip 2", def.maleEquip2);
			addDetail("Male equip 3", def.maleEquipModelId3);
			addDetail("Female equip 1", def.femaleEquip1);
			addDetail("Female equip 2", def.femaleEquip2);
			addDetail("Female equip 3", def.femaleEquipModelId3);
			addDetail("Equip slot", def.equipSlot);
			addDetail("Hide slot", def.equipLookHideSlot);
			addDetail("Hide slot 2", def.equipLookHideSlot2);
			addDetail("Ground options", Arrays.toString(def.groundOptions));
			addDetail("Inventory options", Arrays.toString(def.inventoryOptions));
			addDetail("Original model colors", arrayToString(def.originalModelColors));
			addDetail("Modified model colors", arrayToString(def.modifiedModelColors));
			addDetail("Original texture colors", arrayToString(def.originalTextureColors));
			addDetail("Modified texture colors", arrayToString(def.modifiedTextureColors));
			addDetail("Cert ID", def.certId);
			addDetail("Cert template", def.certTemplateId);
			addDetail("Lend ID", def.lendId);
			addDetail("Lend template", def.lendTemplateId);
			addDetail("Bind ID", def.bindId);
			addDetail("Bind template", def.bindTemplateId);
			addDetail("Team ID", def.teamId);
			addDetail("Client script params", def.clientScriptData == null ? "{}" : def.clientScriptData.toString());
		} catch (RuntimeException e) {
			showError("Failed to decode item " + id + ": " + e.getMessage());
		}
	}

	private void addDetail(String field, Object value) {
		detailModel.addRow(new Object[] { field, value == null ? "" : value });
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

	private static final class ItemEntry {
		private final int id;
		private final String name;

		private ItemEntry(int id, String name) {
			this.id = id;
			this.name = name;
		}

		@Override
		public String toString() {
			return id + " - " + name;
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
}
