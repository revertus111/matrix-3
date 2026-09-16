package com.rs.game.npc.bosslabs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rs.utils.Logger;

/** Versioned BossLabs-owned persistence for Matrix3 drop-table overrides. */
public final class BossLabsDropStore {

	private static final int MAGIC = 0x424C4450; // BLDP
	private static final int VERSION = 1;
	private static final int MAX_DEFINITIONS = 10000;
	private static final File DIRECTORY = new File("data/bosslabs");
	private static final File STORE_FILE = new File(DIRECTORY, "drops.bld");
	private static final File TEMP_FILE = new File(DIRECTORY, "drops.bld.tmp");
	private static final Object STORE_LOCK = new Object();
	private static final Map<Integer, BossLabsDropDefinition> SAVED =
			new LinkedHashMap<Integer, BossLabsDropDefinition>();

	private BossLabsDropStore() {
	}

	/** Called after Matrix3 NPCDrops.init(), so packed tables are the baseline. */
	public static void init() {
		synchronized (STORE_LOCK) {
			SAVED.clear();
			if (!STORE_FILE.exists())
				return;
			try {
				Map<Integer, BossLabsDropDefinition> loaded = readStore();
				SAVED.putAll(loaded);
				for (BossLabsDropDefinition definition : loaded.values())
					BossLabsDropPublisher.bootstrapSaved(definition);
			} catch (Throwable e) {
				Logger.handle(e);
			}
		}
	}

	public static void save(BossLabsDropDefinition definition) throws IOException {
		if (definition == null)
			throw new IllegalArgumentException("BossLabs drop definition must not be null.");
		definition.validateItems();
		synchronized (STORE_LOCK) {
			Map<Integer, BossLabsDropDefinition> next = new LinkedHashMap<Integer, BossLabsDropDefinition>(SAVED);
			next.put(Integer.valueOf(definition.getNpcId()), definition);
			writeStore(next);
			SAVED.clear();
			SAVED.putAll(next);
		}
	}

	public static boolean delete(int npcId) throws IOException {
		synchronized (STORE_LOCK) {
			if (!SAVED.containsKey(Integer.valueOf(npcId)))
				return false;
			Map<Integer, BossLabsDropDefinition> next = new LinkedHashMap<Integer, BossLabsDropDefinition>(SAVED);
			next.remove(Integer.valueOf(npcId));
			writeStore(next);
			SAVED.clear();
			SAVED.putAll(next);
			return true;
		}
	}

	public static BossLabsDropDefinition getSaved(int npcId) {
		synchronized (STORE_LOCK) {
			return SAVED.get(Integer.valueOf(npcId));
		}
	}

	public static boolean isSaved(int npcId) {
		synchronized (STORE_LOCK) {
			return SAVED.containsKey(Integer.valueOf(npcId));
		}
	}

	private static Map<Integer, BossLabsDropDefinition> readStore() throws IOException {
		DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(STORE_FILE)));
		try {
			if (input.readInt() != MAGIC)
				throw new IOException("Invalid BossLabs drop store header.");
			int version = input.readInt();
			if (version != VERSION)
				throw new IOException("Unsupported BossLabs drop store version: " + version);
			int count = input.readInt();
			if (count < 0 || count > MAX_DEFINITIONS)
				throw new IOException("Invalid BossLabs drop definition count: " + count);
			Map<Integer, BossLabsDropDefinition> loaded = new LinkedHashMap<Integer, BossLabsDropDefinition>();
			for (int i = 0; i < count; i++) {
				int npcId = input.readInt();
				boolean rareTable = input.readBoolean();
				int entryCount = input.readInt();
				if (entryCount < 0 || entryCount > BossLabsDropDefinition.MAX_ENTRIES)
					throw new IOException("Invalid BossLabs drop entry count: " + entryCount);
				List<BossLabsDropDefinition.Entry> entries = new ArrayList<BossLabsDropDefinition.Entry>(entryCount);
				for (int entry = 0; entry < entryCount; entry++) {
					entries.add(new BossLabsDropDefinition.Entry(input.readInt(), input.readInt(), input.readInt(), input.readInt()));
				}
				BossLabsDropDefinition definition = new BossLabsDropDefinition(npcId, rareTable, entries);
				if (loaded.put(Integer.valueOf(npcId), definition) != null)
					throw new IOException("Duplicate NPC id in BossLabs drop store: " + npcId);
			}
			return loaded;
		} catch (EOFException e) {
			throw new IOException("BossLabs drop store ended unexpectedly.", e);
		} finally {
			input.close();
		}
	}

	private static void writeStore(Map<Integer, BossLabsDropDefinition> definitions) throws IOException {
		if (!DIRECTORY.exists() && !DIRECTORY.mkdirs() && !DIRECTORY.exists())
			throw new IOException("Unable to create BossLabs data directory: " + DIRECTORY.getPath());
		FileOutputStream fileOutput = new FileOutputStream(TEMP_FILE);
		DataOutputStream output = new DataOutputStream(new BufferedOutputStream(fileOutput));
		boolean completed = false;
		try {
			output.writeInt(MAGIC);
			output.writeInt(VERSION);
			output.writeInt(definitions.size());
			for (BossLabsDropDefinition definition : definitions.values()) {
				output.writeInt(definition.getNpcId());
				output.writeBoolean(definition.canAccessRareDropTable());
				output.writeInt(definition.getEntries().size());
				for (BossLabsDropDefinition.Entry entry : definition.getEntries()) {
					output.writeInt(entry.getRarity());
					output.writeInt(entry.getItemId());
					output.writeInt(entry.getMinAmount());
					output.writeInt(entry.getMaxAmount());
				}
			}
			output.flush();
			fileOutput.getFD().sync();
			completed = true;
		} finally {
			output.close();
			if (!completed)
				TEMP_FILE.delete();
		}
		try {
			Files.move(TEMP_FILE.toPath(), STORE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(TEMP_FILE.toPath(), STORE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
