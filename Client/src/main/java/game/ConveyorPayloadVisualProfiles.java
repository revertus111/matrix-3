package game;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Client-side presentation profiles for ConveyorRun payload item models.
 *
 * Resolution order is exact item override -> category profile -> global default.
 * These profiles own only presentation (offset/scale/orientation). ConveyorRun
 * owns movement speed and transport behavior.
 */
public final class ConveyorPayloadVisualProfiles {

    public enum Category {
        LOGS,
        PLANKS,
        ORES,
        BARS,
        STONE,
        FISH,
        FOOD,
        HERBS,
        SEEDS,
        POTIONS,
        WEAPONS,
        ARMOUR,
        TOOLS,
        RUNES,
        GEMS,
        BONES,
        CLOTH,
        MISC
    }

    public static final class Profile {
        public final int alongOffset;
        public final int sideOffset;
        public final int heightOffset;
        public final int scalePercent;
        public final int pitchDegrees;
        public final int yawDegrees;
        public final int rollDegrees;

        public Profile(int alongOffset, int sideOffset, int heightOffset,
                int scalePercent, int pitchDegrees, int yawDegrees,
                int rollDegrees) {
            this.alongOffset = clamp(alongOffset, -4096, 4096);
            this.sideOffset = clamp(sideOffset, -4096, 4096);
            this.heightOffset = clamp(heightOffset, -2048, 2048);
            this.scalePercent = clamp(scalePercent, 10, 400);
            this.pitchDegrees = normalizeDegrees(pitchDegrees);
            this.yawDegrees = normalizeDegrees(yawDegrees);
            this.rollDegrees = normalizeDegrees(rollDegrees);
        }
    }

    public static final class Anchor {
        public final int alongOffset;
        public final int sideOffset;
        public final int heightOffset;

        public Anchor(int alongOffset, int sideOffset, int heightOffset) {
            this.alongOffset = clamp(alongOffset, -4096, 4096);
            this.sideOffset = clamp(sideOffset, -4096, 4096);
            this.heightOffset = clamp(heightOffset, -2048, 2048);
        }
    }

    public static final class Resolution {
        public final int itemId;
        public final String itemName;
        public final Category category;
        public final Profile profile;
        public final String source;

        Resolution(int itemId, String itemName, Category category,
                Profile profile, String source) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.category = category;
            this.profile = profile;
            this.source = source;
        }
    }

    private static final File PROFILE_FILE =
            new File("data/construction/conveyor_payload_profiles.properties");
    private static final File LEGACY_TUNING_FILE =
            new File("data/construction/conveyor_payload_tuning.properties");

    private static final Profile DEFAULT_PROFILE =
            new Profile(0, 0, -96, 100, 0, 0, 0);
    private static final Anchor DEFAULT_ANCHOR = new Anchor(0, 0, 0);

    private static final Map<Category, Profile> CATEGORY_PROFILES =
            new LinkedHashMap<Category, Profile>();
    private static final Map<Integer, Profile> ITEM_PROFILES =
            new LinkedHashMap<Integer, Profile>();
    private static final Map<Integer, Category> ITEM_CATEGORIES =
            new LinkedHashMap<Integer, Category>();

    private static Profile globalProfile = DEFAULT_PROFILE;
    private static Anchor globalAnchor = DEFAULT_ANCHOR;
    private static int previewItemId = -1;
    private static Profile previewProfile;

    static {
        if (!loadFromDisk()) {
            migrateLegacyTuning();
        }
    }

    private ConveyorPayloadVisualProfiles() {
    }

    public static String[] getCategoryNames() {
        Category[] values = Category.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    public static synchronized Resolution resolve(int itemId) {
        return resolveInternal(Math.max(0, itemId), false);
    }

    public static synchronized Resolution resolveForRender(int itemId) {
        return resolveInternal(Math.max(0, itemId), true);
    }

    public static synchronized void setPreview(int itemId, Profile profile) {
        previewItemId = Math.max(0, itemId);
        previewProfile = profile == null ? DEFAULT_PROFILE : profile;
    }

    public static synchronized void clearPreview() {
        previewItemId = -1;
        previewProfile = null;
    }

    public static synchronized Anchor getGlobalAnchor() {
        return globalAnchor == null ? DEFAULT_ANCHOR : globalAnchor;
    }

    public static synchronized String saveGlobalAnchor(
            int alongOffset, int sideOffset, int heightOffset) {
        globalAnchor = new Anchor(alongOffset, sideOffset, heightOffset);
        if (!saveToDisk()) {
            return "Belt anchor changed, but disk save failed.";
        }
        return "Saved BELT ANCHOR forward=" + globalAnchor.alongOffset
                + " side=" + globalAnchor.sideOffset
                + " height=" + globalAnchor.heightOffset + ".";
    }

    public static synchronized String resetGlobalAnchor() {
        globalAnchor = DEFAULT_ANCHOR;
        if (!saveToDisk()) {
            return "Belt anchor reset in memory, but disk save failed.";
        }
        return "Reset BELT ANCHOR to 0/0/0.";
    }

    public static synchronized String describeAnchor() {
        Anchor anchor = getGlobalAnchor();
        return "Belt anchor forward=" + anchor.alongOffset
                + " side=" + anchor.sideOffset
                + " height=" + anchor.heightOffset;
    }

    public static synchronized String saveGlobal(Profile profile) {
        globalProfile = profile == null ? DEFAULT_PROFILE : profile;
        if (!saveToDisk()) {
            return "Global payload profile changed, but disk save failed.";
        }
        return "Saved GLOBAL payload visual profile.";
    }

    public static synchronized String saveCategory(
            String categoryName, Profile profile) {
        Category category = categoryFromName(categoryName);
        if (category == null) {
            return "Choose a valid payload category first.";
        }
        CATEGORY_PROFILES.put(category,
                profile == null ? DEFAULT_PROFILE : profile);
        clearPreview();
        if (!saveToDisk()) {
            return "Category profile changed, but disk save failed.";
        }
        return "Saved " + category.name() + " payload visual profile.";
    }

    public static synchronized Profile getCategoryProfile(String categoryName) {
        Category category = categoryFromName(categoryName);
        if (category == null) {
            return globalProfile == null ? DEFAULT_PROFILE : globalProfile;
        }
        Profile profile = CATEGORY_PROFILES.get(category);
        return profile == null
                ? (globalProfile == null ? DEFAULT_PROFILE : globalProfile)
                : profile;
    }

    public static synchronized Profile getGlobalProfile() {
        return globalProfile == null ? DEFAULT_PROFILE : globalProfile;
    }

    public static synchronized String saveItemOverride(int itemId, Profile profile) {
        int id = Math.max(0, itemId);
        ITEM_PROFILES.put(Integer.valueOf(id),
                profile == null ? DEFAULT_PROFILE : profile);
        clearPreview();
        if (!saveToDisk()) {
            return "Item override changed, but disk save failed.";
        }
        return "Saved item " + id + " payload visual override.";
    }

    public static synchronized String removeItemOverride(int itemId) {
        int id = Math.max(0, itemId);
        boolean removed = ITEM_PROFILES.remove(Integer.valueOf(id)) != null;
        clearPreview();
        if (!saveToDisk()) {
            return "Item override changed, but disk save failed.";
        }
        return removed
                ? "Removed item " + id + " visual override; category/global inheritance is active."
                : "Item " + id + " has no exact visual override.";
    }

    public static synchronized String assignItemCategory(
            int itemId, String categoryName) {
        Category category = categoryFromName(categoryName);
        if (category == null) {
            return "Choose a valid payload category first.";
        }
        int id = Math.max(0, itemId);
        ITEM_CATEGORIES.put(Integer.valueOf(id), category);
        clearPreview();
        if (!saveToDisk()) {
            return "Item category changed, but disk save failed.";
        }
        return "Assigned item " + id + " -> " + category.name() + ".";
    }

    public static synchronized String useAutomaticCategory(int itemId) {
        int id = Math.max(0, itemId);
        ITEM_CATEGORIES.remove(Integer.valueOf(id));
        clearPreview();
        if (!saveToDisk()) {
            return "Item category changed, but disk save failed.";
        }
        return "Item " + id + " now uses automatic category detection.";
    }

    public static synchronized String reload() {
        clearPreview();
        if (!loadFromDisk()) {
            return "No saved conveyor payload profile file found.";
        }
        return "Reloaded conveyor payload visual profiles.";
    }

    public static synchronized String describe(int itemId) {
        Resolution resolution = resolveInternal(Math.max(0, itemId), false);
        return "Payload item=" + resolution.itemId
                + " name=" + resolution.itemName
                + " category=" + resolution.category.name()
                + " source=" + resolution.source
                + " | along=" + resolution.profile.alongOffset
                + " side=" + resolution.profile.sideOffset
                + " height=" + resolution.profile.heightOffset
                + " scale=" + resolution.profile.scalePercent + "%"
                + " rot=(" + resolution.profile.pitchDegrees
                + "," + resolution.profile.yawDegrees
                + "," + resolution.profile.rollDegrees + ")"
                + " | " + describeAnchor();
    }

    private static Resolution resolveInternal(int itemId, boolean includePreview) {
        String itemName = itemName(itemId);
        Category category = categoryForItem(itemId, itemName);

        if (includePreview && previewProfile != null && previewItemId == itemId) {
            return new Resolution(itemId, itemName, category,
                    previewProfile, "LIVE PREVIEW");
        }

        Profile item = ITEM_PROFILES.get(Integer.valueOf(itemId));
        if (item != null) {
            return new Resolution(itemId, itemName, category, item,
                    "ITEM " + itemId);
        }

        Profile categoryProfile = CATEGORY_PROFILES.get(category);
        if (categoryProfile != null) {
            return new Resolution(itemId, itemName, category,
                    categoryProfile, "CATEGORY " + category.name());
        }

        return new Resolution(itemId, itemName, category,
                globalProfile == null ? DEFAULT_PROFILE : globalProfile,
                "GLOBAL");
    }

    private static Category categoryForItem(int itemId, String itemName) {
        Category manual = ITEM_CATEGORIES.get(Integer.valueOf(itemId));
        if (manual != null) {
            return manual;
        }
        return inferCategory(itemName);
    }

    private static Category inferCategory(String itemName) {
        String name = itemName == null ? "" : itemName.trim().toLowerCase();

        if (name.contains("plank")) return Category.PLANKS;
        if (name.endsWith(" logs") || "logs".equals(name)
                || name.endsWith(" log")) return Category.LOGS;
        if (name.contains(" ore")) return Category.ORES;
        if (name.endsWith(" bar") || name.contains(" bar ")) return Category.BARS;
        if (name.contains("rune") && !name.contains("armour")) return Category.RUNES;
        if (name.contains("seed")) return Category.SEEDS;
        if (name.contains("herb")) return Category.HERBS;
        if (name.contains("potion") || name.contains("dose")) return Category.POTIONS;
        if (name.contains("gem") || name.contains("diamond")
                || name.contains("ruby") || name.contains("emerald")
                || name.contains("sapphire") || name.contains("opal")
                || name.contains("jade") || name.contains("topaz")) {
            return Category.GEMS;
        }
        if (name.contains("bone")) return Category.BONES;
        if (name.contains("fish") || name.contains("salmon")
                || name.contains("trout") || name.contains("lobster")
                || name.contains("shark") || name.contains("tuna")
                || name.contains("swordfish")) {
            return Category.FISH;
        }
        if (name.contains("platebody") || name.contains("platelegs")
                || name.contains("plateskirt") || name.contains("chainbody")
                || name.contains("helm") || name.contains("shield")
                || name.contains("boots") || name.contains("gloves")
                || name.contains("armour")) {
            return Category.ARMOUR;
        }
        if (name.contains("sword") || name.contains("dagger")
                || name.contains("scimitar") || name.contains("mace")
                || name.contains("bow") || name.contains("crossbow")
                || name.contains("spear") || name.contains("halberd")
                || name.contains("staff") || name.contains("wand")) {
            return Category.WEAPONS;
        }
        if (name.contains("pickaxe") || name.contains("hatchet")
                || name.contains("hammer") || name.contains("chisel")
                || name.contains("knife") || name.contains("saw")) {
            return Category.TOOLS;
        }
        if (name.contains("cloth") || name.contains("silk")
                || name.contains("wool")) return Category.CLOTH;
        if (name.contains("stone") || name.contains("rock")
                || name.contains("granite") || name.contains("sandstone")) {
            return Category.STONE;
        }
        if (name.contains("bread") || name.contains("cake")
                || name.contains("meat") || name.contains("pie")
                || name.contains("stew") || name.contains("potato")
                || name.contains("cheese")) {
            return Category.FOOD;
        }
        return Category.MISC;
    }

    private static String itemName(int itemId) {
        ClientConsoleItemBridge.ItemInfo info =
                ClientConsoleItemBridge.getItemInfo(itemId);
        return info == null ? "unknown" : info.getName();
    }

    private static Category categoryFromName(String value) {
        if (value == null) return null;
        try {
            return Category.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static synchronized boolean loadFromDisk() {
        if (!PROFILE_FILE.isFile()) {
            return false;
        }

        Properties properties = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(PROFILE_FILE);
            properties.load(in);

            CATEGORY_PROFILES.clear();
            ITEM_PROFILES.clear();
            ITEM_CATEGORIES.clear();
            globalProfile = readProfile(properties, "global", DEFAULT_PROFILE);
            globalAnchor = new Anchor(
                    parseInt(properties, "anchor.alongOffset", 0),
                    parseInt(properties, "anchor.sideOffset", 0),
                    parseInt(properties, "anchor.heightOffset", 0));

            for (Category category : Category.values()) {
                String prefix = "category." + category.name();
                if (properties.containsKey(prefix + ".scalePercent")) {
                    CATEGORY_PROFILES.put(category,
                            readProfile(properties, prefix, DEFAULT_PROFILE));
                }
            }

            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("item.")) continue;
                String remainder = key.substring("item.".length());
                int dot = remainder.indexOf('.');
                if (dot <= 0) continue;

                int itemId;
                try {
                    itemId = Integer.parseInt(remainder.substring(0, dot));
                } catch (NumberFormatException ex) {
                    continue;
                }

                String suffix = remainder.substring(dot + 1);
                if ("category".equals(suffix)) {
                    Category category =
                            categoryFromName(properties.getProperty(key));
                    if (category != null) {
                        ITEM_CATEGORIES.put(Integer.valueOf(itemId), category);
                    }
                } else if ("scalePercent".equals(suffix)) {
                    ITEM_PROFILES.put(Integer.valueOf(itemId),
                            readProfile(properties, "item." + itemId,
                                    DEFAULT_PROFILE));
                }
            }
            return true;
        } catch (IOException ex) {
            System.err.println("[ConveyorPayloadVisualProfiles] Load failed: "
                    + ex.getMessage());
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Best-effort developer-tool cleanup.
                }
            }
        }
    }

    private static synchronized boolean saveToDisk() {
        Properties properties = new Properties();
        writeProfile(properties, "global", globalProfile);
        Anchor anchor = getGlobalAnchor();
        properties.setProperty("anchor.alongOffset",
                Integer.toString(anchor.alongOffset));
        properties.setProperty("anchor.sideOffset",
                Integer.toString(anchor.sideOffset));
        properties.setProperty("anchor.heightOffset",
                Integer.toString(anchor.heightOffset));

        for (Map.Entry<Category, Profile> entry : CATEGORY_PROFILES.entrySet()) {
            writeProfile(properties, "category." + entry.getKey().name(),
                    entry.getValue());
        }

        for (Map.Entry<Integer, Category> entry : ITEM_CATEGORIES.entrySet()) {
            properties.setProperty("item." + entry.getKey().intValue()
                    + ".category", entry.getValue().name());
        }

        for (Map.Entry<Integer, Profile> entry : ITEM_PROFILES.entrySet()) {
            writeProfile(properties, "item." + entry.getKey().intValue(),
                    entry.getValue());
        }

        File parent = PROFILE_FILE.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(PROFILE_FILE);
            properties.store(out,
                    "Matrix3 Construction conveyor payload visual profiles");
            return true;
        } catch (IOException ex) {
            System.err.println("[ConveyorPayloadVisualProfiles] Save failed: "
                    + ex.getMessage());
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Best-effort developer-tool cleanup.
                }
            }
        }
    }

    private static void migrateLegacyTuning() {
        if (!LEGACY_TUNING_FILE.isFile()) {
            return;
        }

        Properties properties = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(LEGACY_TUNING_FILE);
            properties.load(in);
            Profile legacy = new Profile(
                    parseInt(properties, "alongOffset", 0),
                    parseInt(properties, "sideOffset", 0),
                    parseInt(properties, "heightOffset", -96),
                    parseInt(properties, "scalePercent", 100),
                    parseInt(properties, "pitchDegrees", 0),
                    parseInt(properties, "yawDegrees", 0),
                    parseInt(properties, "rollDegrees", 0));
            CATEGORY_PROFILES.put(Category.LOGS, legacy);
            ITEM_CATEGORIES.put(Integer.valueOf(1511), Category.LOGS);
            saveToDisk();
        } catch (IOException ex) {
            System.err.println("[ConveyorPayloadVisualProfiles] Legacy import failed: "
                    + ex.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Best-effort developer-tool cleanup.
                }
            }
        }
    }

    private static Profile readProfile(
            Properties properties, String prefix, Profile fallback) {
        return new Profile(
                parseInt(properties, prefix + ".alongOffset",
                        fallback.alongOffset),
                parseInt(properties, prefix + ".sideOffset",
                        fallback.sideOffset),
                parseInt(properties, prefix + ".heightOffset",
                        fallback.heightOffset),
                parseInt(properties, prefix + ".scalePercent",
                        fallback.scalePercent),
                parseInt(properties, prefix + ".pitchDegrees",
                        fallback.pitchDegrees),
                parseInt(properties, prefix + ".yawDegrees",
                        fallback.yawDegrees),
                parseInt(properties, prefix + ".rollDegrees",
                        fallback.rollDegrees));
    }

    private static void writeProfile(
            Properties properties, String prefix, Profile profile) {
        Profile value = profile == null ? DEFAULT_PROFILE : profile;
        properties.setProperty(prefix + ".alongOffset",
                Integer.toString(value.alongOffset));
        properties.setProperty(prefix + ".sideOffset",
                Integer.toString(value.sideOffset));
        properties.setProperty(prefix + ".heightOffset",
                Integer.toString(value.heightOffset));
        properties.setProperty(prefix + ".scalePercent",
                Integer.toString(value.scalePercent));
        properties.setProperty(prefix + ".pitchDegrees",
                Integer.toString(value.pitchDegrees));
        properties.setProperty(prefix + ".yawDegrees",
                Integer.toString(value.yawDegrees));
        properties.setProperty(prefix + ".rollDegrees",
                Integer.toString(value.rollDegrees));
    }

    private static int parseInt(
            Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(
                    key, Integer.toString(fallback)).trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static int normalizeDegrees(int value) {
        int normalized = value % 360;
        if (normalized > 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
