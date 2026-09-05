package game.console.bosslabs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Mutable client-local DRAFT for one Matrix3 NPC drop-table override. */
public final class BossLabsDropDraftDefinition {

    public static final int VERSION = 1;
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_AMOUNT = 1000000000;

    public static final int ALWAYS = 0;
    public static final int COMMON = 1;
    public static final int UNCOMMON = 2;
    public static final int RARE = 3;
    public static final int VERY_RARE = 4;

    private int npcId;
    private boolean accessRareDropTable;
    private final List<Entry> entries = new ArrayList<Entry>();

    public BossLabsDropDraftDefinition(int npcId) {
        this.npcId = npcId;
    }

    public int getNpcId() {
        return npcId;
    }

    public void setNpcId(int npcId) {
        this.npcId = npcId;
    }

    public boolean canAccessRareDropTable() {
        return accessRareDropTable;
    }

    public void setAccessRareDropTable(boolean accessRareDropTable) {
        this.accessRareDropTable = accessRareDropTable;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public String validate() {
        if (npcId < 0)
            return "Drop NPC id must be zero or greater.";
        if (entries.size() > MAX_ENTRIES)
            return "Drop table exceeds " + MAX_ENTRIES + " entries.";
        for (Entry entry : entries) {
            if (entry == null)
                return "Drop table contains an empty entry.";
            String error = entry.validate();
            if (error != null)
                return error;
        }
        return null;
    }

    public String toPayload() {
        String validation = validate();
        if (validation != null)
            throw new IllegalArgumentException(validation);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            output.writeInt(npcId);
            output.writeBoolean(accessRareDropTable);
            output.writeInt(entries.size());
            for (Entry entry : entries) {
                output.writeInt(entry.getRarity());
                output.writeInt(entry.getItemId());
                output.writeInt(entry.getMinAmount());
                output.writeInt(entry.getMaxAmount());
            }
            output.close();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode BossLabs drop draft.", e);
        }
    }

    public static BossLabsDropDraftDefinition fromPayload(String payload) {
        if (payload == null || payload.length() == 0)
            throw new IllegalArgumentException("BossLabs drop payload is empty.");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            int version = input.readInt();
            if (version != VERSION)
                throw new IllegalArgumentException("Unsupported BossLabs drop payload version: " + version);
            BossLabsDropDraftDefinition draft = new BossLabsDropDraftDefinition(input.readInt());
            draft.setAccessRareDropTable(input.readBoolean());
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid BossLabs drop entry count: " + count);
            for (int index = 0; index < count; index++)
                draft.entries.add(new Entry(input.readInt(), input.readInt(), input.readInt(), input.readInt()));
            if (input.available() != 0)
                throw new IllegalArgumentException("BossLabs drop payload contains trailing data.");
            input.close();
            String validation = draft.validate();
            if (validation != null)
                throw new IllegalArgumentException(validation);
            return draft;
        } catch (IOException e) {
            throw new IllegalArgumentException("BossLabs drop payload is incomplete.", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("BossLabs drop payload is invalid.", e);
        }
    }

    public static String rarityName(int rarity) {
        switch (rarity) {
        case ALWAYS:
            return "Always";
        case COMMON:
            return "Common";
        case UNCOMMON:
            return "Uncommon";
        case RARE:
            return "Rare";
        case VERY_RARE:
            return "Very Rare";
        default:
            return "Unknown";
        }
    }

    public static String rarityRateLabel(int rarity) {
        switch (rarity) {
        case ALWAYS:
            return "all entries";
        case COMMON:
            return "90% bucket";
        case UNCOMMON:
            return "70% bucket";
        case RARE:
            return "0.6% bucket";
        case VERY_RARE:
            return "0.36% bucket";
        default:
            return "unknown";
        }
    }

    public static final class Entry {
        private final int rarity;
        private final int itemId;
        private final int minAmount;
        private final int maxAmount;

        public Entry(int rarity, int itemId, int minAmount, int maxAmount) {
            this.rarity = rarity;
            this.itemId = itemId;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            String validation = validate();
            if (validation != null)
                throw new IllegalArgumentException(validation);
        }

        public int getRarity() {
            return rarity;
        }

        public int getItemId() {
            return itemId;
        }

        public int getMinAmount() {
            return minAmount;
        }

        public int getMaxAmount() {
            return maxAmount;
        }

        private String validate() {
            if (rarity < ALWAYS || rarity > VERY_RARE)
                return "Unsupported Matrix3 drop rarity: " + rarity;
            if (itemId < 0)
                return "Drop item id must be zero or greater.";
            if (minAmount < 1 || minAmount > MAX_AMOUNT)
                return "Drop minimum must be between 1 and " + MAX_AMOUNT + ".";
            if (maxAmount < minAmount || maxAmount > MAX_AMOUNT)
                return "Drop maximum must be at least the minimum and no greater than " + MAX_AMOUNT + ".";
            return null;
        }
    }
}
