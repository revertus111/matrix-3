package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
	private byte[] currentData;

	public RawCachePanel(CacheSession session) {
		super(new BorderLayout(6, 6));
		this.session = session;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(new JLabel("Index"));
		controls.add(indexField);
		controls.add(new JLabel("Archive"));
		controls.add(archiveField);
		controls.add(new JLabel("File"));
		controls.add(fileField);

		JButton load = new JButton("Load");
		JButton export = new JButton("Export Raw");
		JButton replace = new JButton("Replace Raw");
		controls.add(load);
		controls.add(export);
		controls.add(replace);
		add(controls, BorderLayout.NORTH);

		preview.setEditable(false);
		preview.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		add(new JScrollPane(preview), BorderLayout.CENTER);

		status.setText("Ready. Indexes: " + session.getIndexCount());
		add(status, BorderLayout.SOUTH);

		load.addActionListener(e -> loadSelected());
		export.addActionListener(e -> exportCurrent());
		replace.addActionListener(e -> replaceSelected());
	}

	private void loadSelected() {
		final int[] ids;
		try {
			ids = readIds();
		} catch (IllegalArgumentException e) {
			showError(e.getMessage());
			return;
		}
		status.setText("Loading...");
		new SwingWorker<byte[], Void>() {
			@Override
			protected byte[] doInBackground() {
				return session.readFile(ids[0], ids[1], ids[2]);
			}

			@Override
			protected void done() {
				try {
					currentData = get();
					if (currentData == null) {
						preview.setText("");
						status.setText("No file at index " + ids[0] + ", archive " + ids[1] + ", file " + ids[2] + ".");
						return;
					}
					preview.setText(toHex(currentData));
					preview.setCaretPosition(0);
					status.setText("Loaded " + currentData.length + " bytes.");
				} catch (Exception e) {
					showError("Failed to read cache file: " + rootMessage(e));
				}
			}
		}.execute();
	}

	private void exportCurrent() {
		if (currentData == null) {
			showError("Load a cache file first.");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new File("cache_file.bin"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			Files.write(chooser.getSelectedFile().toPath(), currentData);
			status.setText("Exported " + currentData.length + " bytes to " + chooser.getSelectedFile().getAbsolutePath());
		} catch (Exception e) {
			showError("Export failed: " + e.getMessage());
		}
	}

	private void replaceSelected() {
		final int[] ids;
		try {
			ids = readIds();
		} catch (IllegalArgumentException e) {
			showError(e.getMessage());
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose replacement raw file");
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final File replacement = chooser.getSelectedFile();
		int answer = JOptionPane.showConfirmDialog(this,
				"Replace index " + ids[0] + ", archive " + ids[1] + ", file " + ids[2]
						+ "?\nThe current file will be backed up first when it exists.",
				"Confirm raw cache write", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION) {
			return;
		}
		status.setText("Writing replacement...");
		new SwingWorker<File, Void>() {
			private byte[] replacementData;

			@Override
			protected File doInBackground() throws Exception {
				replacementData = Files.readAllBytes(replacement.toPath());
				return session.writeFileWithBackup(ids[0], ids[1], ids[2], replacementData);
			}

			@Override
			protected void done() {
				try {
					File backup = get();
					currentData = replacementData;
					preview.setText(toHex(currentData));
					preview.setCaretPosition(0);
					status.setText("Wrote " + currentData.length + " bytes. Backup: "
							+ (backup == null ? "new file; no previous bytes" : backup.getAbsolutePath()));
				} catch (Exception e) {
					showError("Write failed: " + rootMessage(e));
				}
			}
		}.execute();
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
}
