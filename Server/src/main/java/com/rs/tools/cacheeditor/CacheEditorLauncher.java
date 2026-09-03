package com.rs.tools.cacheeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class CacheEditorLauncher {

	private CacheEditorLauncher() {
	}

	public static void main(final String[] args) {
		CacheEditorTheme.install();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				File directory = resolveCacheDirectory(args);
				if (directory != null) {
					open(directory);
				}
			}
		});
	}

	private static File resolveCacheDirectory(String[] args) {
		if (args != null && args.length > 0) {
			File supplied = new File(args[0]);
			if (supplied.isDirectory()) {
				return supplied;
			}
			JOptionPane.showMessageDialog(null, "Cache path is not a directory:\n" + supplied.getAbsolutePath(),
					"RS3 CacheEditor", JOptionPane.ERROR_MESSAGE);
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Open RS3 cache directory");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		return chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	private static void open(final File directory) {
		final JFrame loading = new JFrame("RS3 CacheEditor");
		loading.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		loading.setLayout(new BorderLayout());
		loading.getContentPane().setBackground(CacheEditorTheme.WINDOW);
		JLabel loadingLabel = new JLabel("Opening cache: " + directory.getAbsolutePath(), SwingConstants.CENTER);
		loadingLabel.setFont(CacheEditorTheme.BODY_FONT);
		loadingLabel.setForeground(CacheEditorTheme.TEXT);
		loading.add(loadingLabel, BorderLayout.CENTER);
		loading.setPreferredSize(new Dimension(620, 110));
		loading.pack();
		loading.setLocationRelativeTo(null);
		loading.setVisible(true);

		new SwingWorker<CacheSession, Void>() {
			@Override
			protected CacheSession doInBackground() throws Exception {
				return new CacheSession(directory);
			}

			@Override
			protected void done() {
				loading.dispose();
				try {
					CacheEditorFrame frame = new CacheEditorFrame(get());
					frame.setVisible(true);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(null, "Failed to open cache:\n" + e.getMessage(), "RS3 CacheEditor",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}
}
