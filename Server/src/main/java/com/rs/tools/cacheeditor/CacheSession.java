package com.rs.tools.cacheeditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import com.alex.store.Index;
import com.alex.store.ReferenceTable;
import com.alex.store.Store;
import com.rs.cache.Cache;
import com.rs.cache.loaders.ItemDefinitions;
import com.rs.cache.loaders.ObjectDefinitions;

public final class CacheSession {

	public static final int MODEL_INDEX = 7;

	private final File cacheDirectory;
	private final Store store;
	private volatile boolean writeEnabled;

	public CacheSession(File cacheDirectory) throws IOException {
		if (cacheDirectory == null || !cacheDirectory.isDirectory()) {
			throw new IllegalArgumentException("Cache directory does not exist: " + cacheDirectory);
		}
		this.cacheDirectory = cacheDirectory.getAbsoluteFile();
		ReferenceTable.NEW_PROTOCOL = true;
		this.store = new Store(this.cacheDirectory.getAbsolutePath() + File.separator);
		Cache.STORE = store;
		ItemDefinitions.clearItemsDefinitions();
		ObjectDefinitions.clearObjectDefinitions();
	}

	public File getCacheDirectory() {
		return cacheDirectory;
	}

	public Store getStore() {
		return store;
	}

	public int getIndexCount() {
		return store.getIndexes().length;
	}

	public int getLastArchiveId(int indexId) {
		return getIndex(indexId).getLastArchiveId();
	}

	public byte[] readFile(int indexId, int archiveId, int fileId) {
		return getIndex(indexId).getFile(archiveId, fileId);
	}

	public int getLastModelId() {
		return getLastArchiveId(MODEL_INDEX);
	}

	public byte[] readModelBytes(int modelId) {
		if (modelId < 0)
			throw new IllegalArgumentException("Model id must be zero or greater.");
		return readFile(MODEL_INDEX, modelId, 0);
	}

	public ObjectDefinitions getObjectDefinition(int objectId) {
		if (objectId < 0)
			throw new IllegalArgumentException("Object id must be zero or greater.");
		return ObjectDefinitions.getObjectDefinitions(objectId);
	}

	public boolean isWriteEnabled() {
		return writeEnabled;
	}

	public void setWriteEnabled(boolean writeEnabled) {
		this.writeEnabled = writeEnabled;
	}

	public File writeFileWithBackup(int indexId, int archiveId, int fileId, byte[] data) throws IOException {
		if (!writeEnabled) {
			throw new IllegalStateException("Cache writes are disabled. Enable Edit Mode first.");
		}
		if (data == null) {
			throw new IllegalArgumentException("Replacement data cannot be null.");
		}
		Index index = getIndex(indexId);
		byte[] original = index.getFile(archiveId, fileId);
		File backupFile = null;
		if (original != null) {
			backupFile = createBackupFile(indexId, archiveId, fileId);
			Files.createDirectories(backupFile.getParentFile().toPath());
			Files.write(backupFile.toPath(), original);
		}

		// Alex FileStore variants do not all expose the same putFile return type.
		// Treat an immediate byte-for-byte readback as the authoritative success check.
		index.putFile(archiveId, fileId, data);
		byte[] verified = index.getFile(archiveId, fileId);
		if (!Arrays.equals(data, verified)) {
			throw new IOException("Cache write verification failed for index " + indexId + ", archive " + archiveId + ", file " + fileId + ".");
		}
		return backupFile;
	}

	private Index getIndex(int indexId) {
		if (indexId < 0 || indexId >= store.getIndexes().length || store.getIndexes()[indexId] == null) {
			throw new IllegalArgumentException("Invalid cache index: " + indexId);
		}
		return store.getIndexes()[indexId];
	}

	private File createBackupFile(int indexId, int archiveId, int fileId) {
		File parent = cacheDirectory.getParentFile();
		if (parent == null) {
			parent = cacheDirectory;
		}
		String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
		return new File(new File(new File(parent, "cache-editor-backups"), stamp),
				"index_" + indexId + File.separator + "archive_" + archiveId + "_file_" + fileId + ".bin");
	}
}
