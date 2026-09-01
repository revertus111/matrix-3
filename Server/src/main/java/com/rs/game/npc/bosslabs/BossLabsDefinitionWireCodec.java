package com.rs.game.npc.bosslabs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Small versioned wire codec used only by the BossLabs developer bridge.
 *
 * Persistent storage remains BossDefinitionStore. This codec exists only to
 * move one complete draft definition between the client tool and server.
 */
public final class BossLabsDefinitionWireCodec {

	private static final int VERSION = 2;
	private static final int MIN_SUPPORTED_VERSION = 1;
	private static final int MAX_PHASES = 64;
	private static final int MAX_ATTACKS_PER_PHASE = 256;
	private static final int MAX_PATTERN_TILES = 128;
	private static final int MAX_WIRE_BYTES = 16384;

	private BossLabsDefinitionWireCodec() {
	}

	public static String encode(BossDefinition definition) {
		if (definition == null)
			throw new IllegalArgumentException("Boss definition must not be null.");
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes);
			output.writeInt(VERSION);
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
				}
			}
			output.flush();
			byte[] data = bytes.toByteArray();
			if (data.length > MAX_WIRE_BYTES)
				throw new IllegalArgumentException("BossLabs definition is too large for the development bridge.");
			return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to encode BossLabs definition.", e);
		}
	}

	public static BossDefinition decode(String payload) {
		if (payload == null || payload.length() == 0)
			throw new IllegalArgumentException("BossLabs definition payload is empty.");

		byte[] data;
		try {
			data = Base64.getUrlDecoder().decode(payload);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("BossLabs definition payload is invalid.");
		}
		if (data.length > MAX_WIRE_BYTES)
			throw new IllegalArgumentException("BossLabs definition payload is too large.");

		try {
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
			int version = input.readInt();
			if (version < MIN_SUPPORTED_VERSION || version > VERSION)
				throw new IllegalArgumentException("Unsupported BossLabs definition payload version: " + version);

			String id = input.readUTF();
			String displayName = input.readUTF();
			int npcId = input.readInt();
			int phaseCount = readCount(input.readInt(), MAX_PHASES, "phase");
			List<BossPhaseDefinition> phases = new ArrayList<BossPhaseDefinition>(phaseCount);
			for (int phaseIndex = 0; phaseIndex < phaseCount; phaseIndex++) {
				String phaseId = input.readUTF();
				int minimumHealthPercent = input.readInt();
				int maximumHealthPercent = input.readInt();
				int attackCount = readCount(input.readInt(), MAX_ATTACKS_PER_PHASE, "attack");
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
					int tileCount = readCount(input.readInt(), MAX_PATTERN_TILES, "pattern tile");
					List<BossTileOffset> pattern = new ArrayList<BossTileOffset>(tileCount);
					for (int tileIndex = 0; tileIndex < tileCount; tileIndex++)
						pattern.add(new BossTileOffset(input.readInt(), input.readInt()));
					attacks.add(new BossAttackDefinition(attackId, combatStyle, animationId, graphicId, projectileId,
							maxHitOverride, combatDelayOverride, telegraphGraphicId, impactGraphicId, telegraphTicks, pattern));
				}
				phases.add(new BossPhaseDefinition(phaseId, minimumHealthPercent, maximumHealthPercent, attacks));
			}
			if (input.available() != 0)
				throw new IllegalArgumentException("BossLabs definition payload has trailing data.");
			return new BossDefinition(id, displayName, npcId, phases);
		} catch (IOException e) {
			throw new IllegalArgumentException("BossLabs definition payload ended unexpectedly.", e);
		}
	}

	private static int readCount(int value, int maximum, String label) {
		if (value < 0 || value > maximum)
			throw new IllegalArgumentException("Invalid BossLabs " + label + " count: " + value);
		return value;
	}
}
