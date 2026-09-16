package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

public final class RawCachePanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int MAX_PREVIEW_BYTES = 256 * 1024;

	private final CacheSession session;
	private final JTextField indexField = new JTextField("0", 4);
	private final JTextField archiveField = new JTextField("0", 7);
	private final JTextField fileField = new JTextField("0", 5);
	private final JTextArea preview = new JTextArea();
	private final JLabel status = new JLabel();
	private final JLabel loadedSelection = new JLabel("No file loaded");
	private final DefaultListModel<Integer> indexModel = new DefaultListModel<Integer>();
	private final DefaultListModel<Integer> archiveModel = new DefaultListModel<Integer>();
	private final JList<Integer> indexList = new JList<Integer>(indexModel);
	private final JList<Integer> archiveList = new JList<Integer>(archiveModel);
	private final JButton exportButton = new JButton("Export Raw");
	private final JButton replaceButton = new JButton("Replace Raw");
	private LoadedFile loadedFile;
	private boolean writeEnabled;

	public RawCachePanel(CacheSession session) {
		super(new BorderLayout(8, 8));
		this.session = session;
		setBackground(CacheEditorTheme.PANEL);
		setBorder(CacheEditorTheme.panelPadding(12, 12, 12, 12));

		add(createControls(), BorderLayout.NORTH);
		add(createWorkspace(), BorderLayout.CENTER);

		status.setText("Ready. Indexes: " + session.getIndexCount());
		status.setFont(CacheEditorTheme.SMALL_FONT);
		status.setForeground(CacheEditorTheme.ACCENT);
		status.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
		add(status, BorderLayout.SOUTH);

		populateIndexes();
		setWriteEnabled(session.isWriteEnabled());
	}

	public void setWriteEnabled(boolean enabled) {
		writeEnabled = enabled;
		updateActions();
	}

	private JPanel createControls() {
		JPanel wrapper = new JPanel(new BorderLayout(6, 6));
		wrapper.setBackground(CacheEditorTheme.PANEL);

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		controls.setBackground(CacheEditorTheme.PANEL);
		addField(controls, "Index", indexField);
		addField(controls, "Archive", archiveField);
		addField(controls, "File", fileField);

		JButton loadButton = new JButton("Go To / Load");
		CacheEditorTheme.styleButton(loadButton);
		CacheEditorTheme.styleButton(exportButton);
		CacheEditorTheme.styleButton(replaceButton);
		controls.add(loadButton);
		controls.add(exportButton);
		controls.add(replaceButton);
		wrapper.add(controls, BorderLayout.NORTH);

		loadedSelection.setFont(CacheEditorTheme.SMALL_FONT);
		loadedSelection.setForeground(CacheEditorTheme.MUTED_TEXT);
		loadedSelection.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
		wrapper.add(loadedSelection, BorderLayout.SOUTH);

		loadButton.addActionListener(e -> loadSelected());
		exportButton.addActionListener(e -> exportCurrent());
		replaceButton.addActionListener(e -> replaceLoaded());
		return wrapper;
	}

	private JSplitPane createWorkspace() {
		preview.setEditable(false);
		preview.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		preview.setLineWrap(false);
		CacheEditorTheme.styleTextArea(preview);
		JScrollPane previewScroll = new JScrollPane(preview);
		CacheEditorTheme.styleScrollPane(previewScroll);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createBrowser(), previewScroll);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setDividerSize(5);
		split.setResizeWeight(0.22);
		split.setBackground(CacheEditorTheme.BORDER);
		return split;
	}

	private JPanel createBrowser() {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBackground(CacheEditorTheme.CARD);
		panel.setBorder(CacheEditorTheme.cardBorder());
		panel.setPreferredSize(new Dimension(245, 500));

		JPanel header = new JPanel();
		header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
		header.setBackground(CacheEditorTheme.CARD);
		JLabel title = new JLabel("CACHE BROWSER");
		title.setFont(CacheEditorTheme.SECTION_FONT);
		title.setForeground(CacheEditorTheme.TEXT);
		JLabel note = new JLabel("Browse index/archive; enter file ID above.");
		note.setFont(CacheEditorTheme.SMALL_FONT);
		note.setForeground(CacheEditorTheme.MUTED_TEXT);
		header.add(title);
		header.add(javax.swing.Box.createVerticalStrut(3));
		header.add(note);
		panel.add(header, BorderLayout.NORTH);

		JPanel lists = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		lists.setBackground(CacheEditorTheme.CARD);
		lists.add(createListPanel("INDEX", indexList));
		lists.add(createListPanel("ARCHIVE", archiveList));
		panel.add(lists, BorderLayout.CENTER);

		indexList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && indexList.getSelectedValue() != null) {
				int indexId = indexList.getSelectedValue().intValue();
				indexField.setText(Integer.toString(indexId));
				populateArchives(indexId);
			}
		});
		archiveList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && archiveList.getSelectedValue() != null) {
				archiveField.setText(Integer.toString(archiveList.getSelectedValue().intValue()));
			}
		});
		return panel;
	}

	private JPanel createListPanel(String title, JList<Integer> list) {
		JPanel panel = new JPanel(new BorderLayout(0, 5));
		panel.setBackground(CacheEditorTheme.CARD);
		JLabel label = new JLabel(title);
		label.setFont(CacheEditorTheme.SMALL_FONT);
		label.setForeground(CacheEditorTheme.MUTED_TEXT);
		panel.add(label, BorderLayout.NORTH);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		CacheEditorTheme.styleList(list);
		JScrollPane scroll = new JScrollPane(list);
		CacheEditorTheme.styleScrollPane(scroll);
		panel.add(scroll, BorderLayout.CENTER);
		return panel;
	}

	private void populateIndexes() {
		indexModel.clear();
		for (int i = 0; i < session.getIndexCount(); i++) {
			try {
				session.getLastArchiveId(i);
				indexModel.addElement(Integer.valueOf(i));
			} catch (IllegalArgumentException ignored) {
				// Sparse/missing Store index: do not advertise it as browseable.
			}
		}
		if (!indexModel.isEmpty()) {
			indexList.setSelectedIndex(0);
		}
	}

	private void populateArchives(final int indexId) {
		archiveModel.clear();
		status.setText("Loading archives for index " + indexId + "...");
		new SwingWorker<List<Integer>, Void>() {
			@Override
			protected List<Integer> doInBackground() {
				int last = session.getLastArchiveId(indexId);
				List<Integer> ids = new ArrayList<Integer>(Math.max(0, last + 1));
				for (int archive = 0; archive <= last; archive++) {
					ids.add(Integer.valueOf(archive));
				}
				return ids;
			}

			@Override
			protected void done() {
				try {
					if (indexList.getSelectedValue() == null || indexList.getSelectedValue().intValue() != indexId) {
						return;
					}
					for (Integer id : get()) {
						archiveModel.addElement(id);
					}
					status.setText("Index " + indexId + ": " + archiveModel.size() + " archive IDs available for navigation.");
				} catch (Exception e) {
					showError("Failed to browse index " + indexId + ": " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void addField(JPanel controls, String text, JTextField field) {
		JLabel label = new JLabel(text);
		label.setFont(CacheEditorTheme.SMALL_FONT);
		label.setForeground(CacheEditorTheme.MUTED_TEXT);
		controls.add(label);
		CacheEditorTheme.styleTextField(field);
		controls.add(field);
	}

	private void loadSelected() {
		final int[] ids;
		try {
			ids = readIds();
		} catch (IllegalArgumentException e) {
			showError(e.getMessage());
			return;
		}
		status.setText("Loading index " + ids[0] + ", archive " + ids[1] + ", file " + ids[2] + "...");
		new SwingWorker<byte[], Void>() {
			@Override
			protected byte[] doInBackground() {
				return session.readFile(ids[0], ids[1], ids[2]);
			}

			@Override
			protected void done() {
				try {
					byte[] data = get();
					if (data == null) {
						loadedFile = null;
						preview.setText("");
						loadedSelection.setText("No file loaded");
						status.setText("No file at index " + ids[0] + ", archive " + ids[1] + ", file " + ids[2] + ".");
						updateActions();
						return;
					}
					loadedFile = new LoadedFile(ids[0], ids[1], ids[2], data);
					preview.setText(toHex(data));
					preview.setCaretPosition(0);
					loadedSelection.setText("LOADED  index " + ids[0] + " / archive " + ids[1] + " / file " + ids[2]);
					status.setText("Loaded " + data.length + " bytes.");
					updateActions();
				} catch (Exception e) {
					showError("Failed to read cache file: " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void exportCurrent() {
		if (loadedFile == null) {
			showError("Load a cache file first.");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new File("index_" + loadedFile.indexId + "_archive_" + loadedFile.archiveId + "_file_" + loadedFile.fileId + ".bin"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			Files.write(chooser.getSelectedFile().toPath(), loadedFile.data);
			status.setText("Exported " + loadedFile.data.length + " bytes to " + chooser.getSelectedFile().getAbsolutePath());
		} catch (Exception e) {
			showError("Export failed: " + e.getMessage());
		}
	}

	private void replaceLoaded() {
		if (!writeEnabled || !session.isWriteEnabled()) {
			showError("Enable Edit Mode before replacing raw cache bytes.");
			return;
		}
		if (loadedFile == null) {
			showError("Load the target cache file first. Replace Raw always targets the loaded selection.");
			return;
		}
		final LoadedFile target = loadedFile;
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose replacement raw file");
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final File replacement = chooser.getSelectedFile();
		int answer = JOptionPane.showConfirmDialog(this,
				"Replace LOADED file index " + target.indexId + ", archive " + target.archiveId + ", file " + target.fileId
						+ "?\nThe current bytes will be backed up first and the write will be verified by readback.",
				"Confirm raw cache write", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION) {
			return;
		}
		status.setText("Writing and verifying replacement...");
		new SwingWorker<File, Void>() {
			private byte[] replacementData;

			@Override
			protected File doInBackground() throws Exception {
				replacementData = Files.readAllBytes(replacement.toPath());
				return session.writeFileWithBackup(target.indexId, target.archiveId, target.fileId, replacementData);
			}

			@Override
			protected void done() {
				try {
					File backup = get();
					byte[] verified = session.readFile(target.indexId, target.archiveId, target.fileId);
					loadedFile = new LoadedFile(target.indexId, target.archiveId, target.fileId, verified);
					preview.setText(toHex(verified));
					preview.setCaretPosition(0);
					status.setText("Verified " + verified.length + "-byte write. Backup: "
							+ (backup == null ? "new file; no previous bytes" : backup.getAbsolutePath()));
				} catch (Exception e) {
					showError("Write failed: " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void updateActions() {
		exportButton.setEnabled(loadedFile != null);
		replaceButton.setEnabled(writeEnabled && loadedFile != null);
		replaceButton.setToolTipText(writeEnabled ? "Replace the currently loaded file" : "Enable Edit Mode to write cache bytes");
	}

	private int[] readIds() {
		try {
			int index = Integer.parseInt(indexField.getText().trim());
			int archive = Integer.parseInt(archiveField.getText().trim());
			int file = Integer.parseInt(fileField.getText().trim());
			if (index < 0 || archive < 0 || file < 0) {
				throw new NumberFormatException();
			}
			return new int[] { index, archive, file };
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Index, archive, and file must be non-negative integers.");
		}
	}

	private String toHex(byte[] data) {
		int length = Math.min(data.length, MAX_PREVIEW_BYTES);
		StringBuilder text = new StringBuilder(length * 4);
		for (int offset = 0; offset < length; offset += 16) {
			text.append(String.format("%08X  ", offset));
			for (int i = 0; i < 16; i++) {
				int pos = offset + i;
				if (pos < length) {
					text.append(String.format("%02X ", data[pos] & 0xff));
				} else {
					text.append("   ");
				}
				if (i == 7) {
					text.append(' ');
				}
			}
			text.append(" |");
			for (int i = 0; i < 16 && offset + i < length; i++) {
				int value = data[offset + i] & 0xff;
				text.append(value >= 32 && value <= 126 ? (char) value : '.');
			}
			text.append("|\n");
		}
		if (data.length > length) {
			text.append("\nPreview truncated at ").append(MAX_PREVIEW_BYTES).append(" bytes of ").append(data.length)
					.append(" total bytes.\n");
		}
		return text.toString();
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

	private static final class LoadedFile {
		private final int indexId;
		private final int archiveId;
		private final int fileId;
		private final byte[] data;

		private LoadedFile(int indexId, int archiveId, int fileId, byte[] data) {
			this.indexId = indexId;
			this.archiveId = archiveId;
			this.fileId = fileId;
			this.data = data;
		}
	}
}
