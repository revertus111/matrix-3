package game.console.bosslabs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mutable client-only BossLabs DRAFT model.
 *
 * The server still constructs and validates the authoritative immutable
 * BossDefinition before anything can become LIVE or SAVED.
 */
public final class BossLabsDraftDefinition {

    public static final int TARGET_CURRENT = 0;
    public static final int TARGET_RANDOM_NEARBY_PLAYER = 1;

    private static final int WIRE_VERSION = 5;
    private static final int MIN_WIRE_VERSION = 1;
    private static final int MAX_PHASES = 64;
    private static final int MAX_ATTACKS_PER_PHASE = 256;
    private static final int MAX_PATTERN_TILES = 128;
    private static final int MAX_TILE_OFFSET = 16;
    private static final int MAX_TELEGRAPH_TICKS = 50;
    private static final int MAX_HAZARD_DURATION_TICKS = 100;
    private static final int MAX_HAZARD_TICK_INTERVAL = 50;
    private static final int MAX_TARGET_RANGE = 32;
    private static final int MAX_ROTATION_WEIGHT = 1000;
    private static final int MAX_COOLDOWN_ATTACKS = 100;
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
                if (attack.targetMode != TARGET_CURRENT && attack.targetMode != TARGET_RANDOM_NEARBY_PLAYER)
                    return "Attack " + attack.id + " has an invalid target mode.";
                if (attack.targetRange < 1 || attack.targetRange > MAX_TARGET_RANGE)
                    return "Attack " + attack.id + " target range must be between 1 and " + MAX_TARGET_RANGE + ".";
                if (attack.rotationWeight < 1 || attack.rotationWeight > MAX_ROTATION_WEIGHT)
                    return "Attack " + attack.id + " weight must be between 1 and " + MAX_ROTATION_WEIGHT + ".";
                if (attack.cooldownAttacks < 0 || attack.cooldownAttacks > MAX_COOLDOWN_ATTACKS)
                    return "Attack " + attack.id + " cooldown turns must be between 0 and " + MAX_COOLDOWN_ATTACKS + ".";
                if (attack.maxHitOverride < -1)
                    return "Attack " + attack.id + " max hit must be -1 or greater.";
                if (attack.combatDelayOverride < -1)
                    return "Attack " + attack.id + " combat delay must be -1 or greater.";
                if (attack.telegraphGraphicId < -1 || attack.impactGraphicId < -1)
                    return "Attack " + attack.id + " tile graphics must be -1 or greater.";
                if (attack.telegraphTicks < 0 || attack.telegraphTicks > MAX_TELEGRAPH_TICKS)
                    return "Attack " + attack.id + " warning ticks must be between 0 and " + MAX_TELEGRAPH_TICKS + ".";
                if (attack.hazardGraphicId < -1)
                    return "Attack " + attack.id + " hazard GFX must be -1 or greater.";
                if (attack.hazardDurationTicks < 0 || attack.hazardDurationTicks > MAX_HAZARD_DURATION_TICKS)
                    return "Attack " + attack.id + " hazard duration must be between 0 and " + MAX_HAZARD_DURATION_TICKS + " ticks.";
                if (attack.hazardTickInterval < 1 || attack.hazardTickInterval > MAX_HAZARD_TICK_INTERVAL)
                    return "Attack " + attack.id + " hazard interval must be between 1 and " + MAX_HAZARD_TICK_INTERVAL + " ticks.";
                if (attack.hazardMaxHitOverride < -1)
                    return "Attack " + attack.id + " hazard max hit must be -1 or greater.";
                if (attack.hazardDurationTicks > 0 && attack.hazardTickInterval > attack.hazardDurationTicks)
                    return "Attack " + attack.id + " hazard interval cannot exceed its duration.";
                if (attack.tilePattern.size() > MAX_PATTERN_TILES)
                    return "Attack " + attack.id + " has too many pattern tiles.";
                Set<TileOffset> unique = new HashSet<TileOffset>();
                for (TileOffset tile : attack.tilePattern) {
                    if (tile == null)
                        return "Attack " + attack.id + " contains an invalid pattern tile.";
                    if (Math.abs(tile.x) > MAX_TILE_OFFSET || Math.abs(tile.y) > MAX_TILE_OFFSET)
                        return "Attack " + attack.id + " tile offsets must stay within +/-" + MAX_TILE_OFFSET + ".";
                    if (!unique.add(tile))
                        return "Attack " + attack.id + " contains duplicate pattern tiles.";
                }
                if (attack.hazardDurationTicks > 0 && attack.tilePattern.isEmpty())
                    return "Attack " + attack.id + " needs painted tiles before a lingering hazard can be enabled.";
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
                    output.writeInt(attack.telegraphGraphicId);
                    output.writeInt(attack.impactGraphicId);
                    output.writeInt(attack.telegraphTicks);
                    output.writeInt(attack.tilePattern.size());
                    for (TileOffset tile : attack.tilePattern) {
                        output.writeInt(tile.x);
                        output.writeInt(tile.y);
                    }
                    output.writeInt(attack.hazardGraphicId);
                    output.writeInt(attack.hazardDurationTicks);
                    output.writeInt(attack.hazardTickInterval);
                    output.writeInt(attack.hazardMaxHitOverride);
                    output.writeInt(attack.targetMode);
                    output.writeInt(attack.targetRange);
                    output.writeInt(attack.rotationWeight);
                    output.writeInt(attack.cooldownAttacks);
                    output.writeBoolean(attack.allowImmediateRepeat);
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
            if (version < MIN_WIRE_VERSION || version > WIRE_VERSION)
                throw new IllegalArgumentException("Unsupported BossLabs definition payload version: " + version);

            BossLabsDraftDefinition definition = new BossLabsDraftDefinition(input.readUTF(), input.readUTF(), input.readInt());
            int phaseCount = readCount(input.readInt(), MAX_PHASES, "phase");
            for (int phaseIndex = 0; phaseIndex < phaseCount; phaseIndex++) {
                Phase phase = new Phase(input.readUTF(), input.readInt(), input.readInt());
                int attackCount = readCount(input.readInt(), MAX_ATTACKS_PER_PHASE, "attack");
                for (int attackIndex = 0; attackIndex < attackCount; attackIndex++) {
                    String attackId = input.readUTF();
                    int combatStyle = input.readInt();
                    int animationId = input.readInt();
                    int graphicId = input.readInt();
                    int projectileId = input.readInt();
                    int maxHitOverride = input.readInt();
                    int combatDelayOverride = input.readInt();
                    if (version == 1) {
                        phase.attacks.add(new Attack(attackId, combatStyle, animationId, graphicId,
                                projectileId, maxHitOverride, combatDelayOverride));
                        continue;
                    }
                    int telegraphGraphicId = input.readInt();
                    int impactGraphicId = input.readInt();
                    int telegraphTicks = input.readInt();
                    int tileCount = readCount(input.readInt(), MAX_PATTERN_TILES, "pattern tile");
                    Attack attack = new Attack(attackId, combatStyle, animationId, graphicId,
                            projectileId, maxHitOverride, combatDelayOverride, telegraphGraphicId,
                            impactGraphicId, telegraphTicks);
                    for (int tileIndex = 0; tileIndex < tileCount; tileIndex++)
                        attack.tilePattern.add(new TileOffset(input.readInt(), input.readInt()));
                    if (version >= 3) {
                        attack.hazardGraphicId = input.readInt();
                        attack.hazardDurationTicks = input.readInt();
                        attack.hazardTickInterval = input.readInt();
                        attack.hazardMaxHitOverride = input.readInt();
                    }
                    if (version >= 4) {
                        attack.targetMode = input.readInt();
                        attack.targetRange = input.readInt();
                    }
                    if (version >= 5) {
                        attack.rotationWeight = input.readInt();
                        attack.cooldownAttacks = input.readInt();
                        attack.allowImmediateRepeat = input.readBoolean();
                    }
                    phase.attacks.add(attack);
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
        private int telegraphGraphicId;
        private int impactGraphicId;
        private int telegraphTicks;
        private final List<TileOffset> tilePattern = new ArrayList<TileOffset>();
        private int hazardGraphicId = -1;
        private int hazardDurationTicks;
        private int hazardTickInterval = 1;
        private int hazardMaxHitOverride = -1;
        private int targetMode = TARGET_CURRENT;
        private int targetRange = 14;
        private int rotationWeight = 1;
        private int cooldownAttacks;
        private boolean allowImmediateRepeat = true;

        public Attack(String id, int combatStyle, int animationId, int graphicId, int projectileId,
                int maxHitOverride, int combatDelayOverride) {
            this(id, combatStyle, animationId, graphicId, projectileId, maxHitOverride, combatDelayOverride,
                    -1, -1, 0);
        }

        public Attack(String id, int combatStyle, int animationId, int graphicId, int projectileId,
                int maxHitOverride, int combatDelayOverride, int telegraphGraphicId,
                int impactGraphicId, int telegraphTicks) {
            this.id = safe(id);
            this.combatStyle = combatStyle;
            this.animationId = animationId;
            this.graphicId = graphicId;
            this.projectileId = projectileId;
            this.maxHitOverride = maxHitOverride;
            this.combatDelayOverride = combatDelayOverride;
            this.telegraphGraphicId = telegraphGraphicId;
            this.impactGraphicId = impactGraphicId;
            this.telegraphTicks = telegraphTicks;
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
        public int getTelegraphGraphicId() { return telegraphGraphicId; }
        public void setTelegraphGraphicId(int value) { telegraphGraphicId = value; }
        public int getImpactGraphicId() { return impactGraphicId; }
        public void setImpactGraphicId(int value) { impactGraphicId = value; }
        public int getTelegraphTicks() { return telegraphTicks; }
        public void setTelegraphTicks(int value) { telegraphTicks = value; }
        public List<TileOffset> getTilePattern() { return tilePattern; }
        public int getHazardGraphicId() { return hazardGraphicId; }
        public void setHazardGraphicId(int value) { hazardGraphicId = value; }
        public int getHazardDurationTicks() { return hazardDurationTicks; }
        public void setHazardDurationTicks(int value) { hazardDurationTicks = value; }
        public int getHazardTickInterval() { return hazardTickInterval; }
        public void setHazardTickInterval(int value) { hazardTickInterval = value; }
        public int getHazardMaxHitOverride() { return hazardMaxHitOverride; }
        public void setHazardMaxHitOverride(int value) { hazardMaxHitOverride = value; }
        public int getTargetMode() { return targetMode; }
        public void setTargetMode(int value) { targetMode = value; }
        public int getTargetRange() { return targetRange; }
        public void setTargetRange(int value) { targetRange = value; }
        public int getRotationWeight() { return rotationWeight; }
        public void setRotationWeight(int value) { rotationWeight = value; }
        public int getCooldownAttacks() { return cooldownAttacks; }
        public void setCooldownAttacks(int value) { cooldownAttacks = value; }
        public boolean isImmediateRepeatAllowed() { return allowImmediateRepeat; }
        public void setImmediateRepeatAllowed(boolean value) { allowImmediateRepeat = value; }

        @Override
        public String toString() {
            String label = id.trim().length() == 0 ? "Unnamed attack" : id;
            String area = tilePattern.isEmpty() ? "" : "  " + tilePattern.size() + " tiles";
            String hazard = hazardDurationTicks > 0 ? "  hazard " + hazardDurationTicks + "t" : "";
            String targeting = targetMode == TARGET_RANDOM_NEARBY_PLAYER ? "  random target" : "";
            String rotation = "  w" + rotationWeight + (cooldownAttacks > 0 ? " cd" + cooldownAttacks : "")
                    + (allowImmediateRepeat ? "" : " no-repeat");
            return label + "  [" + styleName(combatStyle) + "]" + area + hazard + targeting + rotation;
        }
    }

    public static final class TileOffset {
        private final int x;
        private final int y;

        public TileOffset(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() { return x; }
        public int getY() { return y; }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (!(object instanceof TileOffset))
                return false;
            TileOffset other = (TileOffset) object;
            return x == other.x && y == other.y;
        }
    }

    public static String styleName(int style) {
        if (style == 0) return "Melee";
        if (style == 1) return "Range";
        if (style == 2) return "Magic";
        return "Unknown";
    }
}
