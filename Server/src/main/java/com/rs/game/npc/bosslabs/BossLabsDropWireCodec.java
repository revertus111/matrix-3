package com.rs.game.npc.bosslabs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Bounded versioned transport codec for BossLabs drop DRAFT data. */
public final class BossLabsDropWireCodec {

	private static final int VERSION = 1;

	private BossLabsDropWireCodec() {
	}

	public static String encode(BossLabsDropDefinition definition) {
		if (definition == null)
			throw new IllegalArgumentException("BossLabs drop definition must not be null.");
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes);
			output.writeInt(VERSION);
			output.writeInt(definition.getNpcId());
			output.writeBoolean(definition.canAccessRareDropTable());
			output.writeInt(definition.getEntries().size());
			for (BossLabsDropDefinition.Entry entry : definition.getEntries()) {
				output.writeInt(entry.getRarity());
				output.writeInt(entry.getItemId());
				output.writeInt(entry.getMinAmount());
				output.writeInt(entry.getMaxAmount());
			}
			output.close();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException("Unable to encode BossLabs drops.", e);
		}
	}

	public static BossLabsDropDefinition decode(String payload) {
		if (payload == null || payload.length() == 0)
			throw new IllegalArgumentException("BossLabs drop payload is empty.");
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(payload);
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
			int version = input.readInt();
			if (version != VERSION)
				throw new IllegalArgumentException("Unsupported BossLabs drop payload version: " + version);
			int npcId = input.readInt();
			boolean rareTable = input.readBoolean();
			int count = input.readInt();
			if (count < 0 || count > BossLabsDropDefinition.MAX_ENTRIES)
				throw new IllegalArgumentException("Invalid BossLabs drop entry count: " + count);
			List<BossLabsDropDefinition.Entry> entries = new ArrayList<BossLabsDropDefinition.Entry>(count);
			for (int i = 0; i < count; i++) {
				entries.add(new BossLabsDropDefinition.Entry(input.readInt(), input.readInt(), input.readInt(), input.readInt()));
			}
			if (input.available() != 0)
				throw new IllegalArgumentException("BossLabs drop payload contains trailing data.");
			input.close();
			return new BossLabsDropDefinition(npcId, rareTable, entries);
		} catch (IOException e) {
			throw new IllegalArgumentException("BossLabs drop payload is incomplete.", e);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("BossLabs drop payload is invalid.", e);
		}
	}
}
