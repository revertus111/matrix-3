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

/**
 * Versioned persistent storage for BossLabs definitions.
 *
 * This store owns only BossLabs content serialization. It never spawns NPCs or
 * mutates combat/world state. Saved definitions are loaded into the runtime
 * registry during server startup.
 */
public final class BossDefinitionStore {

	private static final int MAGIC = 0x424C4431; // BLD1
	private static final int VERSION = 4;
	private static final int MIN_SUPPORTED_VERSION = 1;
	private static final int MAX_DEFINITIONS = 10000;
	private static final int MAX_PHASES = 1000;
	private static final int MAX_ATTACKS = 10000;
	private static final int MAX_PATTERN_TILES = 128;

	private static final File DIRECTORY = new File("data/bosslabs");
	private static final File STORE_FILE = new File(DIRECTORY, "definitions.bld");
	private static final File TEMP_FILE = new File(DIRECTORY, "definitions.bld.tmp");
	private static final Object STORE_LOCK = new Object();
	private static final Map<Integer, BossDefinition> SAVED_DEFINITIONS = new LinkedHashMap<Integer, BossDefinition>();

	private BossDefinitionStore() {
	}

	public static void init() {
		synchronized (STORE_LOCK) {
			SAVED_DEFINITIONS.clear();
			if (!STORE_FILE.exists())
				return;

			try {
				Map<Integer, BossDefinition> loaded = readStore();
				SAVED_DEFINITIONS.putAll(loaded);
				for (BossDefinition definition : loaded.values())
					BossDefinitionRegistry.register(definition);
			} catch (Throwable e) {
				Logger.handle(e);
			}
		}
	}

	public static void save(BossDefinition definition) throws IOException {
		if (definition == null)
			throw new IllegalArgumentException("Boss definition must not be null.");

		synchronized (STORE_LOCK) {
			Map<Integer, BossDefinition> next = new LinkedHashMap<Integer, BossDefinition>(SAVED_DEFINITIONS);
			next.put(definition.getNpcId(), definition);
			writeStore(next);
			SAVED_DEFINITIONS.clear();
			SAVED_DEFINITIONS.putAll(next);
		}
	}

	public static BossDefinition getSaved(int npcId) {
		synchronized (STORE_LOCK) {
			return SAVED_DEFINITIONS.get(npcId);
		}
	}

	public static boolean isSaved(int npcId) {
		synchronized (STORE_LOCK) {
			return SAVED_DEFINITIONS.containsKey(npcId);
		}
	}

	private static Map<Integer, BossDefinition> readStore() throws IOException {
		DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(STORE_FILE)));
		try {
			int magic = input.readInt();
			if (magic != MAGIC)
				throw new IOException("Invalid BossLabs definition store header.");
			int version = input.readInt();
			if (version < MIN_SUPPORTED_VERSION || version > VERSION)
				throw new IOException("Unsupported BossLabs definition store version: " + version);

			int definitionCount = readCount(input, MAX_DEFINITIONS, "definition");
			Map<Integer, BossDefinition> loaded = new LinkedHashMap<Integer, BossDefinition>();
			for (int i = 0; i < definitionCount; i++) {
				BossDefinition definition = readDefinition(input, version);
				if (loaded.put(definition.getNpcId(), definition) != null)
					throw new IOException("Duplicate BossLabs NPC id in definition store: " + definition.getNpcId());
			}
			return loaded;
		} catch (EOFException e) {
			throw new IOException("BossLabs definition store ended unexpectedly.", e);
		} finally {
			input.close();
		}
	}

	private static BossDefinition readDefinition(DataInputStream input, int version) throws IOException {
		String id = input.readUTF();
		String displayName = input.readUTF();
		int npcId = input.readInt();
		int phaseCount = readCount(input, MAX_PHASES, "phase");
		List<BossPhaseDefinition> phases = new ArrayList<BossPhaseDefinition>(phaseCount);
		for (int i = 0; i < phaseCount; i++) {
			String phaseId = input.readUTF();
			int minimumHealthPercent = input.readInt();
			int maximumHealthPercent = input.readInt();
			int attackCount = readCount(input, MAX_ATTACKS, "attack");
			List<BossAttackDefinition> attacks = new ArrayList<BossAttackDefinition>(attackCount);
			for (int attackIndex = 0; attackIndex < attackCount; attackIndex++) {
				String attackId = input.readUTF();
				int combatStyle = input.readInt();
				int animationId = input.readInt();
				int graphicId = input.readInt();
				int projectileId = input.readInt();
				int maxHitOverride = input.readInt();
				int combatDelayOverride = input.readInt();
				if (version == 1) {
					attacks.add(new BossAttackDefinition(attackId, combatStyle, animationId, graphicId, projectileId,
							maxHitOverride, combatDelayOverride));
					continue;
				}

				int telegraphGraphicId = input.readInt();
				int impactGraphicId = input.readInt();
				int telegraphTicks = input.readInt();
				int tileCount = readCount(input, MAX_PATTERN_TILES, "pattern tile");
				List<BossTileOffset> pattern = new ArrayList<BossTileOffset>(tileCount);
				for (int tileIndex = 0; tileIndex < tileCount; tileIndex++)
					pattern.add(new BossTileOffset(input.readInt(), input.readInt()));

				if (version == 2) {
					attacks.add(new BossAttackDefinition(attackId, combatStyle, animationId, graphicId, projectileId,
							maxHitOverride, combatDelayOverride, telegraphGraphicId, impactGraphicId, telegraphTicks, pattern));
					continue;
				}

				int hazardGraphicId = input.readInt();
				int hazardDurationTicks = input.readInt();
				int hazardTickInterval = input.readInt();
				int hazardMaxHitOverride = input.readInt();
				if (version == 3) {
					attacks.add(new BossAttackDefinition(attackId, combatStyle, animationId, graphicId, projectileId,
							maxHitOverride, combatDelayOverride, telegraphGraphicId, impactGraphicId, telegraphTicks, pattern,
							hazardGraphicId, hazardDurationTicks, hazardTickInterval, hazardMaxHitOverride));
					continue;
				}

				int targetMode = input.readInt();
				int targetRange = input.readInt();
				attacks.add(new BossAttackDefinition(attackId, combatStyle, animationId, graphicId, projectileId,
						maxHitOverride, combatDelayOverride, telegraphGraphicId, impactGraphicId, telegraphTicks, pattern,
						hazardGraphicId, hazardDurationTicks, hazardTickInterval, hazardMaxHitOverride,
						targetMode, targetRange));
			}
			phases.add(new BossPhaseDefinition(phaseId, minimumHealthPercent, maximumHealthPercent, attacks));
		}
		return new BossDefinition(id, displayName, npcId, phases);
	}

	private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
		int count = input.readInt();
		if (count < 0 || count > maximum)
			throw new IOException("Invalid BossLabs " + label + " count: " + count);
		return count;
	}

	private static void writeStore(Map<Integer, BossDefinition> definitions) throws IOException {
		if (!DIRECTORY.exists() && !DIRECTORY.mkdirs() && !DIRECTORY.exists())
			throw new IOException("Unable to create BossLabs data directory: " + DIRECTORY.getPath());

		FileOutputStream fileOutput = new FileOutputStream(TEMP_FILE);
		DataOutputStream output = new DataOutputStream(new BufferedOutputStream(fileOutput));
		boolean completed = false;
		try {
			output.writeInt(MAGIC);
			output.writeInt(VERSION);
			output.writeInt(definitions.size());
			for (BossDefinition definition : definitions.values())
				writeDefinition(output, definition);
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

	private static void writeDefinition(DataOutputStream output, BossDefinition definition) throws IOException {
		output.writeUTF(definition.getId());
		output.writeUTF(definition.getDisplayName());
		output.writeInt(definition.getNpcId());
		output.writeInt(definition.getPhases().size());
		for (BossPhaseDefinition phase : definition.getPhases()) {
			output.writeUTF(phase.getId());
			output.writeInt(phase.getMinimumHealthPercent());
			output.writeInt(phase.getMaximumHealthPercent());
			output.writeInt(phase.getAttacks().size());
			for (BossAttackDefinition attack : phase.getAttacks()) {
				output.writeUTF(attack.getId());
				output.writeInt(attack.getCombatStyle());
				output.writeInt(attack.getAnimationId());
				output.writeInt(attack.getGraphicId());
				output.writeInt(attack.getProjectileId());
				output.writeInt(attack.getMaxHitOverride());
				output.writeInt(attack.getCombatDelayOverride());
				output.writeInt(attack.getTelegraphGraphicId());
				output.writeInt(attack.getImpactGraphicId());
				output.writeInt(attack.getTelegraphTicks());
				output.writeInt(attack.getTilePattern().size());
				for (BossTileOffset tile : attack.getTilePattern()) {
					output.writeInt(tile.getX());
					output.writeInt(tile.getY());
				}
				output.writeInt(attack.getHazardGraphicId());
				output.writeInt(attack.getHazardDurationTicks());
				output.writeInt(attack.getHazardTickInterval());
				output.writeInt(attack.getHazardMaxHitOverride());
				output.writeInt(attack.getTargetMode());
				output.writeInt(attack.getTargetRange());
			}
		}
	}
}
