package game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent developer-owned library of object-definition snapshots captured
 * from Dev Live Place. This never mutates cache definitions or world state.
 */
public final class DevObjectLibrary {

    private static final File DIRECTORY = new File("dev-object-library");
    private static final File FILE = new File(DIRECTORY, "objects.tsv");
    private static final String HEADER = "# Matrix3 Dev Object Library v2";

    private DevObjectLibrary() {
    }

    public static synchronized SavedObject saveCurrentPreview() {
        if (!DevObjectPlacementPreview.isActive() || !DevObjectPlacementPreview.hasHoveredTile()) {
            return null;
        }

        int id = DevObjectPlacementPreview.getObjectId();
        DevDefinitionBridge.DefinitionInfo namedInfo = DevDefinitionBridge.getObjectInfo(id);
        DevDefinitionBridge.DefinitionInfo anyInfo = DevDefinitionBridge.getObjectInfoAny(id);
        String cacheName = namedInfo == null || namedInfo.getName() == null ? "" : namedInfo.getName();
        int[] size = DevDefinitionBridge.getObjectSize(id);
        int[] animations = anyInfo == null ? new int[0] : anyInfo.getAnimationIds();

        List<SavedObject> entries = loadInternal();
        String label = defaultLabel(id, cacheName);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).id == id) {
                label = entries.get(i).label;
                entries.remove(i);
            }
        }

        SavedObject saved = new SavedObject(
                id,
                label,
                cacheName,
                DevObjectPlacementPreview.getObjectType(),
                DevObjectPlacementPreview.getRotation(),
                DevDefinitionBridge.getObjectTypes(id),
                DevDefinitionBridge.getObjectModelIds(id),
                animations,
                size.length > 0 ? size[0] : 1,
                size.length > 1 ? size[1] : 1,
                DevObjectPlacementPreview.getHoveredWorldX(),
                DevObjectPlacementPreview.getHoveredWorldY(),
                DevObjectPlacementPreview.getHoveredPlane());

        entries.add(0, saved);
        writeInternal(entries);
        return saved;
    }

    public static synchronized List<SavedObject> load() {
        return Collections.unmodifiableList(new ArrayList<SavedObject>(loadInternal()));
    }

    public static synchronized boolean remove(int objectId) {
        List<SavedObject> entries = loadInternal();
        boolean removed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).id == objectId) {
                entries.remove(i);
                removed = true;
            }
        }
        if (removed) {
            writeInternal(entries);
        }
        return removed;
    }

    public static synchronized SavedObject updateLabel(int objectId, String requestedLabel) {
        List<SavedObject> entries = loadInternal();
        for (int i = 0; i < entries.size(); i++) {
            SavedObject entry = entries.get(i);
            if (entry.id != objectId) {
                continue;
            }
            String label = requestedLabel == null ? "" : requestedLabel.trim();
            if (label.length() == 0) {
                label = defaultLabel(entry.id, entry.cacheName);
            }
            SavedObject updated = entry.withLabel(label);
            entries.set(i, updated);
            writeInternal(entries);
            return updated;
        }
        return null;
    }

    private static List<SavedObject> loadInternal() {
        List<SavedObject> entries = new ArrayList<SavedObject>();
        if (!FILE.isFile()) {
            return entries;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(FILE), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                SavedObject parsed = parse(line);
                if (parsed != null) {
                    entries.add(parsed);
                }
            }
        } catch (Exception ex) {
            System.out.println("[DevObjectLibrary] load failed: " + ex.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        return entries;
    }

    private static void writeInternal(List<SavedObject> entries) {
        BufferedWriter writer = null;
        try {
            if (!DIRECTORY.exists() && !DIRECTORY.mkdirs()) {
                throw new IllegalStateException("Could not create " + DIRECTORY.getPath());
            }
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(FILE), StandardCharsets.UTF_8));
            writer.write(HEADER);
            writer.newLine();
            for (SavedObject entry : entries) {
                writer.write(format(entry));
                writer.newLine();
            }
        } catch (Exception ex) {
            System.out.println("[DevObjectLibrary] save failed: " + ex.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String format(SavedObject e) {
        return e.id + "\t"
                + escape(e.label) + "\t"
                + escape(e.cacheName) + "\t"
                + e.selectedType + "\t"
                + e.rotation + "\t"
                + join(e.types) + "\t"
                + join(e.modelIds) + "\t"
                + join(e.animationIds) + "\t"
                + e.sizeX + "\t"
                + e.sizeY + "\t"
                + e.worldX + "\t"
                + e.worldY + "\t"
                + e.plane;
    }

    private static SavedObject parse(String line) {
        String[] parts = line.split("\\t", -1);
        try {
            if (parts.length >= 13) {
                return new SavedObject(
                        Integer.parseInt(parts[0]),
                        unescape(parts[1]),
                        unescape(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        parseInts(parts[5]),
                        parseInts(parts[6]),
                        parseInts(parts[7]),
                        Integer.parseInt(parts[8]),
                        Integer.parseInt(parts[9]),
                        Integer.parseInt(parts[10]),
                        Integer.parseInt(parts[11]),
                        Integer.parseInt(parts[12]));
            }
            if (parts.length >= 12) {
                int id = Integer.parseInt(parts[0]);
                String legacyName = unescape(parts[1]);
                String cacheName = legacyName.equals("id-" + id) ? "" : legacyName;
                return new SavedObject(
                        id,
                        legacyName,
                        cacheName,
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        parseInts(parts[4]),
                        parseInts(parts[5]),
                        parseInts(parts[6]),
                        Integer.parseInt(parts[7]),
                        Integer.parseInt(parts[8]),
                        Integer.parseInt(parts[9]),
                        Integer.parseInt(parts[10]),
                        Integer.parseInt(parts[11]));
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String join(int[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(values[i]);
        }
        return out.toString();
    }

    private static int[] parseInts(String text) {
        if (text == null || text.length() == 0) {
            return new int[0];
        }
        String[] values = text.split(",");
        int[] parsed = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            parsed[i] = Integer.parseInt(values[i]);
        }
        return parsed;
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    out.append(c);
                }
                continue;
            }
            if (c == 't') out.append('\t');
            else if (c == 'r') out.append('\r');
            else if (c == 'n') out.append('\n');
            else out.append(c);
            escaped = false;
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static String defaultLabel(int id, String cacheName) {
        return cacheName == null || cacheName.trim().length() == 0
                ? "id-" + id
                : cacheName.trim();
    }

    public static final class SavedObject {
        private final int id;
        private final String label;
        private final String cacheName;
        private final int selectedType;
        private final int rotation;
        private final int[] types;
        private final int[] modelIds;
        private final int[] animationIds;
        private final int sizeX;
        private final int sizeY;
        private final int worldX;
        private final int worldY;
        private final int plane;

        private SavedObject(int id, String label, String cacheName, int selectedType, int rotation,
                int[] types, int[] modelIds, int[] animationIds,
                int sizeX, int sizeY, int worldX, int worldY, int plane) {
            this.id = id;
            this.cacheName = cacheName == null ? "" : cacheName.trim();
            String cleanLabel = label == null ? "" : label.trim();
            this.label = cleanLabel.length() == 0 ? defaultLabel(id, this.cacheName) : cleanLabel;
            this.selectedType = selectedType;
            this.rotation = rotation & 0x3;
            this.types = types == null ? new int[0] : types.clone();
            this.modelIds = modelIds == null ? new int[0] : modelIds.clone();
            this.animationIds = animationIds == null ? new int[0] : animationIds.clone();
            this.sizeX = Math.max(1, sizeX);
            this.sizeY = Math.max(1, sizeY);
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
        }

        public int getId() { return id; }
        public String getName() { return label; }
        public String getLabel() { return label; }
        public String getCacheName() { return cacheName; }
        public int getSelectedType() { return selectedType; }
        public int getRotation() { return rotation; }
        public int[] getTypes() { return types.clone(); }
        public int[] getModelIds() { return modelIds.clone(); }
        public int[] getAnimationIds() { return animationIds.clone(); }
        public int getSizeX() { return sizeX; }
        public int getSizeY() { return sizeY; }
        public int getWorldX() { return worldX; }
        public int getWorldY() { return worldY; }
        public int getPlane() { return plane; }

        private SavedObject withLabel(String newLabel) {
            return new SavedObject(id, newLabel, cacheName, selectedType, rotation,
                    types, modelIds, animationIds, sizeX, sizeY, worldX, worldY, plane);
        }

        @Override
        public String toString() {
            return label + "   #" + id + "   type " + selectedType;
        }
    }
}
