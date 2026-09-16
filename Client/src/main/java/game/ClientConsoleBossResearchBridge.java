package game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Narrow Client Console bridge for Boss Research Lab runtime probes and local
 * research persistence. Matrix3 commands remain the authority for player/NPC
 * appearance, animations and graphics.
 */
public final class ClientConsoleBossResearchBridge {

    private static final String BOSS_NAME = "Rise of the Six";
    private static final File FINDINGS_FILE = new File(
            "data/client-console/boss-research-lab/rots-findings.tsv");

    private ClientConsoleBossResearchBridge() {
    }

    public static String becomeNpc(BrotherPreset brother) {
        if (brother == null) {
            return "Choose a RoTS brother first.";
        }
        return ClientConsoleBridge.queueConsoleCommand("tonpc " + brother.getNpcId());
    }

    public static String resetAppearance() {
        return ClientConsoleBridge.queueConsoleCommand("tonpc -1");
    }

    public static String playAnimation(int animationId) {
        return ClientConsoleBridge.queueConsoleCommand("emote " + animationId);
    }

    public static String stopAnimation() {
        return ClientConsoleBridge.queueConsoleCommand("emote -1");
    }

    public static String playGraphics(int gfxId) {
        return ClientConsoleBridge.queueConsoleCommand("gfx " + gfxId);
    }

    public static String saveFinding(
            BrotherPreset brother,
            String mechanic,
            String assetType,
            String assetId,
            String confidence,
            String note) {
        if (brother == null) {
            return "Choose a RoTS brother first.";
        }
        mechanic = clean(mechanic);
        assetType = clean(assetType);
        assetId = clean(assetId);
        confidence = clean(confidence);
        note = clean(note);

        if (mechanic.length() == 0) {
            return "Enter the mechanic this finding belongs to.";
        }
        if (assetType.length() == 0 || assetId.length() == 0) {
            return "Choose an asset type and enter its ID.";
        }
        if (confidence.length() == 0) {
            return "Choose an evidence confidence.";
        }

        File parent = FINDINGS_FILE.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return "Could not create Boss Research Lab data folder.";
        }

        boolean writeHeader = !FINDINGS_FILE.exists() || FINDINGS_FILE.length() == 0L;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(FINDINGS_FILE, true));
            if (writeHeader) {
                writer.write("timestamp\tboss\tnpc\tnpcId\tmechanic\tassetType\tassetId\tconfidence\tnote");
                writer.newLine();
            }
            writer.write(now());
            writer.write('\t');
            writer.write(BOSS_NAME);
            writer.write('\t');
            writer.write(brother.getDisplayName());
            writer.write('\t');
            writer.write(Integer.toString(brother.getNpcId()));
            writer.write('\t');
            writer.write(mechanic);
            writer.write('\t');
            writer.write(assetType);
            writer.write('\t');
            writer.write(assetId);
            writer.write('\t');
            writer.write(confidence);
            writer.write('\t');
            writer.write(note);
            writer.newLine();
            return null;
        } catch (IOException ex) {
            return "Could not save finding: " + ex.getMessage();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static List<Finding> loadFindings(BrotherPreset brother) {
        List<Finding> findings = new ArrayList<Finding>();
        if (brother == null || !FINDINGS_FILE.exists()) {
            return findings;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(FINDINGS_FILE));
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (line.startsWith("timestamp\t")) {
                        continue;
                    }
                }
                String[] parts = line.split("\\t", -1);
                if (parts.length < 9) {
                    continue;
                }
                int npcId;
                try {
                    npcId = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (npcId != brother.getNpcId()) {
                    continue;
                }
                findings.add(new Finding(parts[0], parts[4], parts[5], parts[6], parts[7], parts[8]));
            }
        } catch (IOException ignored) {
            findings.clear();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        return findings;
    }

    public static String getFindingsPath() {
        return FINDINGS_FILE.getPath();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public enum BrotherPreset {
        AHRIM("Ahrim", 18538),
        DHAROK("Dharok", 18540),
        GUTHAN("Guthan", 18541),
        KARIL("Karil", 18543),
        TORAG("Torag", 18544),
        VERAC("Verac", 18545);

        private final String displayName;
        private final int npcId;

        BrotherPreset(String displayName, int npcId) {
            this.displayName = displayName;
            this.npcId = npcId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getNpcId() {
            return npcId;
        }

        @Override
        public String toString() {
            return displayName + " (" + npcId + ")";
        }
    }

    public static final class Finding {
        private final String timestamp;
        private final String mechanic;
        private final String assetType;
        private final String assetId;
        private final String confidence;
        private final String note;

        private Finding(String timestamp, String mechanic, String assetType,
                String assetId, String confidence, String note) {
            this.timestamp = timestamp;
            this.mechanic = mechanic;
            this.assetType = assetType;
            this.assetId = assetId;
            this.confidence = confidence;
            this.note = note;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getMechanic() {
            return mechanic;
        }

        public String getAssetType() {
            return assetType;
        }

        public String getAssetId() {
            return assetId;
        }

        public String getConfidence() {
            return confidence;
        }

        public String getNote() {
            return note;
        }
    }
}
