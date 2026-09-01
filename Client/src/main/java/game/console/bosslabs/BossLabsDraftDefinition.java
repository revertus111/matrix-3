package game.console.bosslabs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Mutable client-only BossLabs DRAFT model.
 *
 * The server still constructs and validates the authoritative immutable
 * BossDefinition before anything can become LIVE or SAVED.
 */
public final class BossLabsDraftDefinition {

    private static final int WIRE_VERSION = 1;
    private static final int MAX_PHASES = 64;
    private static final int MAX_ATTACKS_PER_PHASE = 256;
    private static final int MAX_WIRE_BYTES = 16384;

    private String id;
    private String displayName;
    private int npcId;
    private final List<Phase> phases = new ArrayList<Phase>();

    public BossLabsDraftDefinition(String id, String displayName, int npcId) {
        this.id = safe(id);
        this.displayName = safe(displayName);
        this.npcId = npcId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = safe(id); }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = safe(displayName); }
    public int getNpcId() { return npcId; }
    public void setNpcId(int npcId) { this.npcId = npcId; }
    public List<Phase> getPhases() { return phases; }

    public String validate() {
        if (id.trim().length() == 0)
            return "Definition ID is required.";
        if (displayName.trim().length() == 0)
            return "Display name is required.";
        if (npcId < 0)
            return "NPC ID must be zero or greater.";
        if (phases.isEmpty())
            return "Add at least one phase.";

        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            Phase phase = phases.get(phaseIndex);
            if (phase.id.trim().length() == 0)
                return "Phase " + (phaseIndex + 1) + " needs an ID.";
            if (phase.minimumHealthPercent < 0 || phase.minimumHealthPercent > 100
                    || phase.maximumHealthPercent < 0 || phase.maximumHealthPercent > 100)
                return "Phase " + phase.id + " HP range must stay between 0 and 100.";
            if (phase.minimumHealthPercent > phase.maximumHealthPercent)
                return "Phase " + phase.id + " minimum HP cannot exceed maximum HP.";
            if (phase.attacks.isEmpty())
                return "Phase " + phase.id + " needs at least one attack.";

            for (int attackIndex = 0; attackIndex < phase.attacks.size(); attackIndex++) {
                Attack attack = phase.attacks.get(attackIndex);
                if (attack.id.trim().length() == 0)
                    return "Attack " + (attackIndex + 1) + " in " + phase.id + " needs an ID.";
                if (attack.combatStyle < 0 || attack.combatStyle > 2)
                    return "Attack " + attack.id + " has an invalid combat style.";
                if (attack.maxHitOverride < -1)
                    return "Attack " + attack.id + " max hit must be -1 or greater.";
                if (attack.combatDelayOverride < -1)
                    return "Attack " + attack.id + " combat delay must be -1 or greater.";
            }
        }

        for (int left = 0; left < phases.size(); left++) {
            Phase a = phases.get(left);
            for (int right = left + 1; right < phases.size(); right++) {
                Phase b = phases.get(right);
                if (a.minimumHealthPercent <= b.maximumHealthPercent
                        && b.minimumHealthPercent <= a.maximumHealthPercent)
                    return "Phase HP ranges overlap: " + a.id + " and " + b.id + ".";
            }
        }
        return null;
    }

    public String toPayload() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(WIRE_VERSION);
            output.writeUTF(id);
            output.writeUTF(displayName);
            output.writeInt(npcId);
            output.writeInt(phases.size());
            for (Phase phase : phases) {
                output.writeUTF(phase.id);
                output.writeInt(phase.minimumHealthPercent);
                output.writeInt(phase.maximumHealthPercent);
                output.writeInt(phase.attacks.size());
                for (Attack attack : phase.attacks) {
                    output.writeUTF(attack.id);
                    output.writeInt(attack.combatStyle);
                    output.writeInt(attack.animationId);
                    output.writeInt(attack.graphicId);
                    output.writeInt(attack.projectileId);
                    output.writeInt(attack.maxHitOverride);
                    output.writeInt(attack.combatDelayOverride);
                }
            }
            output.flush();
            byte[] data = bytes.toByteArray();
            if (data.length > MAX_WIRE_BYTES)
                throw new IllegalArgumentException("BossLabs draft is too large for the development bridge.");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode BossLabs draft.", e);
        }
    }

    public static BossLabsDraftDefinition fromPayload(String payload) {
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
            if (version != WIRE_VERSION)
                throw new IllegalArgumentException("Unsupported BossLabs definition payload version: " + version);

            BossLabsDraftDefinition definition = new BossLabsDraftDefinition(input.readUTF(), input.readUTF(), input.readInt());
            int phaseCount = readCount(input.readInt(), MAX_PHASES, "phase");
            for (int phaseIndex = 0; phaseIndex < phaseCount; phaseIndex++) {
                Phase phase = new Phase(input.readUTF(), input.readInt(), input.readInt());
                int attackCount = readCount(input.readInt(), MAX_ATTACKS_PER_PHASE, "attack");
                for (int attackIndex = 0; attackIndex < attackCount; attackIndex++) {
                    phase.attacks.add(new Attack(input.readUTF(), input.readInt(), input.readInt(), input.readInt(),
                            input.readInt(), input.readInt(), input.readInt()));
                }
                definition.phases.add(phase);
            }
            if (input.available() != 0)
                throw new IllegalArgumentException("BossLabs definition payload has trailing data.");
            return definition;
        } catch (IOException e) {
            throw new IllegalArgumentException("BossLabs definition payload ended unexpectedly.", e);
        }
    }

    private static int readCount(int value, int maximum, String label) {
        if (value < 0 || value > maximum)
            throw new IllegalArgumentException("Invalid BossLabs " + label + " count: " + value);
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Phase {
        private String id;
        private int minimumHealthPercent;
        private int maximumHealthPercent;
        private final List<Attack> attacks = new ArrayList<Attack>();

        public Phase(String id, int minimumHealthPercent, int maximumHealthPercent) {
            this.id = safe(id);
            this.minimumHealthPercent = minimumHealthPercent;
            this.maximumHealthPercent = maximumHealthPercent;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = safe(id); }
        public int getMinimumHealthPercent() { return minimumHealthPercent; }
        public void setMinimumHealthPercent(int value) { minimumHealthPercent = value; }
        public int getMaximumHealthPercent() { return maximumHealthPercent; }
        public void setMaximumHealthPercent(int value) { maximumHealthPercent = value; }
        public List<Attack> getAttacks() { return attacks; }

        @Override
        public String toString() {
            String label = id.trim().length() == 0 ? "Unnamed phase" : id;
            return label + "  [" + minimumHealthPercent + "-" + maximumHealthPercent + "%]";
        }
    }

    public static final class Attack {
        private String id;
        private int combatStyle;
        private int animationId;
        private int graphicId;
        private int projectileId;
        private int maxHitOverride;
        private int combatDelayOverride;

        public Attack(String id, int combatStyle, int animationId, int graphicId, int projectileId,
                int maxHitOverride, int combatDelayOverride) {
            this.id = safe(id);
            this.combatStyle = combatStyle;
            this.animationId = animationId;
            this.graphicId = graphicId;
            this.projectileId = projectileId;
            this.maxHitOverride = maxHitOverride;
            this.combatDelayOverride = combatDelayOverride;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = safe(id); }
        public int getCombatStyle() { return combatStyle; }
        public void setCombatStyle(int value) { combatStyle = value; }
        public int getAnimationId() { return animationId; }
        public void setAnimationId(int value) { animationId = value; }
        public int getGraphicId() { return graphicId; }
        public void setGraphicId(int value) { graphicId = value; }
        public int getProjectileId() { return projectileId; }
        public void setProjectileId(int value) { projectileId = value; }
        public int getMaxHitOverride() { return maxHitOverride; }
        public void setMaxHitOverride(int value) { maxHitOverride = value; }
        public int getCombatDelayOverride() { return combatDelayOverride; }
        public void setCombatDelayOverride(int value) { combatDelayOverride = value; }

        @Override
        public String toString() {
            String label = id.trim().length() == 0 ? "Unnamed attack" : id;
            return label + "  [" + styleName(combatStyle) + "]";
        }
    }

    public static String styleName(int style) {
        if (style == 0) return "Melee";
        if (style == 1) return "Range";
        if (style == 2) return "Magic";
        return "Unknown";
    }
}
