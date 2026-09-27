package game;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local authoring store for rail layouts/composites.
 *
 * Each component keeps a stock Matrix3 object id/type/rotation plus a relative
 * tile offset from the layout origin. Old six-column same-tile rows remain
 * readable and default to offset 0,0. This is developer authoring data only; it
 * does not register extra Class523 scene objects or alter cache definitions.
 */
public final class RailCompositeLibrary {

    private static final Path FILE =
            Paths.get("data/construction/asset_studio/rail_composites.tsv");

    public static final String ACCEPTED_CURVE_NAME = "CURVE_RAIL_LAYOUT_01";
    public static final String ACCEPTED_JUNCTION_NAME = "JUNCTION_RAIL_LAYOUT_01";
    public static final String ACCEPTED_CROSSING_NAME = "CROSSING_RAIL_LAYOUT_01";
    public static final String ACCEPTED_SPLITTER_NAME = "SPLITTER_RAIL_LAYOUT_01";

    public static final int PORT_NORTH = 1;
    public static final int PORT_EAST = 2;
    public static final int PORT_SOUTH = 4;
    public static final int PORT_WEST = 8;

    private static final int[] EVIDENCE_SEED_OBJECT_IDS = {
        4770, 4796, 14500, 14501, 14502,
        46352, 46353, 46354, 46355, 46356, 46357, 46358, 46359, 46360, 46361,
        46363, 46364, 46365, 46366, 46367, 46368, 46369, 46370,
        46376, 46378, 46380, 46381, 46382
    };

    public enum Role {
        STRAIGHT,
        CURVE,
        TURNOUT,
        CROSSING,
        END,
        CUSTOM
    }

    private RailCompositeLibrary() {
    }

    public static int[] getEvidenceSeedObjectIds() {
        return EVIDENCE_SEED_OBJECT_IDS.clone();
    }

    public static Path getFile() {
        return FILE;
    }

    public static synchronized String saveComposite(String name, Role role,
            List<Component> components) {
        return saveComposite(name, role, components, 0);
    }

    public static synchronized String saveComposite(String name, Role role,
            List<Component> components, int portMask) {
        String cleanedName = cleanName(name);
        if (cleanedName.length() == 0) {
            return "Composite name is required.";
        }
        if (components == null || components.isEmpty()) {
            return "Add at least one component before saving.";
        }

        Role actualRole = role == null ? Role.CUSTOM : role;
        List<CompositeDefinition> all = loadAll();
        List<CompositeDefinition> next = new ArrayList<CompositeDefinition>();
        for (CompositeDefinition definition : all) {
            if (!definition.name.equalsIgnoreCase(cleanedName)) {
                next.add(definition);
            }
        }
        next.add(new CompositeDefinition(cleanedName, actualRole,
                new ArrayList<Component>(components), portMask));

        Collections.sort(next, new Comparator<CompositeDefinition>() {
            @Override
            public int compare(CompositeDefinition a, CompositeDefinition b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        try {
            Files.createDirectories(FILE.getParent());
            List<String> lines = new ArrayList<String>();
            lines.add("# Matrix3 rail layout/composite library");
            lines.add("# One row per visual component; offset_x/offset_y are relative tiles from the saved layout origin.");
            lines.add("# port_mask is optional metadata: N=1, E=2, S=4, W=8. Legacy rows without it remain readable.");
            lines.add("name\trole\tindex\tid\ttype\trotation\toffset_x\toffset_y\tport_mask");
            for (CompositeDefinition definition : next) {
                for (int i = 0; i < definition.components.size(); i++) {
                    Component component = definition.components.get(i);
                    lines.add(safe(definition.name) + "\t" + definition.role.name()
                            + "\t" + (i + 1)
                            + "\t" + component.id
                            + "\t" + component.type
                            + "\t" + component.rotation
                            + "\t" + component.offsetX
                            + "\t" + component.offsetY
                            + "\t" + definition.portMask);
                }
            }
            Files.write(FILE, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return null;
        } catch (Exception ex) {
            return "Composite save failed: " + ex.getMessage();
        }
    }

    public static synchronized List<CompositeDefinition> loadAll() {
        List<CompositeDefinition> result = new ArrayList<CompositeDefinition>();
        if (!Files.exists(FILE)) {
            return result;
        }

        Map<String, MutableComposite> grouped =
                new LinkedHashMap<String, MutableComposite>();
        try {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()
                        || line.startsWith("#") || line.startsWith("name\t")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 6) {
                    continue;
                }
                try {
                    String name = parts[0].trim();
                    Role role = parseRole(parts[1]);
                    int index = Integer.parseInt(parts[2]);
                    int id = Integer.parseInt(parts[3]);
                    int type = Integer.parseInt(parts[4]);
                    int rotation = Integer.parseInt(parts[5]);
                    int offsetX = parts.length >= 8 ? Integer.parseInt(parts[6]) : 0;
                    int offsetY = parts.length >= 8 ? Integer.parseInt(parts[7]) : 0;
                    int portMask = parts.length >= 9 ? Integer.parseInt(parts[8]) & 0xF : 0;

                    String key = name.toLowerCase(Locale.ENGLISH);
                    MutableComposite mutable = grouped.get(key);
                    if (mutable == null) {
                        mutable = new MutableComposite(name, role, portMask);
                        grouped.put(key, mutable);
                    } else if (mutable.portMask == 0 && portMask != 0) {
                        mutable.portMask = portMask;
                    }
                    mutable.components.add(new IndexedComponent(index,
                            new Component(id, type, rotation, offsetX, offsetY)));
                } catch (Exception ignored) {
                    // Keep valid rows if the local authoring TSV was hand-edited badly.
                }
            }
        } catch (Exception ex) {
            return result;
        }

        for (MutableComposite mutable : grouped.values()) {
            Collections.sort(mutable.components, new Comparator<IndexedComponent>() {
                @Override
                public int compare(IndexedComponent a, IndexedComponent b) {
                    return a.index < b.index ? -1 : a.index == b.index ? 0 : 1;
                }
            });
            List<Component> components = new ArrayList<Component>();
            for (IndexedComponent indexed : mutable.components) {
                components.add(indexed.component);
            }
            if (!components.isEmpty()) {
                result.add(new CompositeDefinition(
                        mutable.name, mutable.role, components, mutable.portMask));
            }
        }
        return result;
    }

    public static CompositeDefinition findFirst(Role role) {
        Role target = role == null ? Role.CUSTOM : role;
        for (CompositeDefinition definition : loadAll()) {
            if (definition.role == target) {
                return definition;
            }
        }
        return null;
    }

    public static CompositeDefinition findByName(String name) {
        String target = cleanName(name);
        if (target.length() == 0) {
            return null;
        }
        for (CompositeDefinition definition : loadAll()) {
            if (definition.name.equalsIgnoreCase(target)) {
                return definition;
            }
        }
        return null;
    }

    public static CompositeDefinition findAcceptedJunctionForRoute() {
        return findPreferredSpecial(ACCEPTED_JUNCTION_NAME, "junction");
    }

    public static CompositeDefinition findAcceptedCrossingForRoute() {
        return findPreferredSpecial(ACCEPTED_CROSSING_NAME, "crossing");
    }

    public static CompositeDefinition findAcceptedSplitterForRoute() {
        return findPreferredSpecial(ACCEPTED_SPLITTER_NAME, "splitter");
    }

    /*
     * Authored multi-object layouts are preferred over an older one-object
     * canonical placeholder. This lets existing research layouts such as
     * junction_LAYOUT_01 immediately feed the logical Junction resolver without
     * forcing the author to rebuild the assembly.
     */
    private static CompositeDefinition findPreferredSpecial(
            String canonicalName, String authoredKeyword) {
        CompositeDefinition canonical = findByName(canonicalName);
        if (canonical != null && canonical.components.size() > 1) {
            return canonical;
        }

        String keyword = authoredKeyword == null
                ? "" : authoredKeyword.toLowerCase(Locale.ENGLISH);
        CompositeDefinition best = null;
        for (CompositeDefinition definition : loadAll()) {
            if (definition == null || definition.components.size() <= 1
                    || definition.name.equalsIgnoreCase(canonicalName)) {
                continue;
            }
            if (definition.name.toLowerCase(Locale.ENGLISH).contains(keyword)) {
                if (best == null
                        || definition.components.size() > best.components.size()) {
                    best = definition;
                }
            }
        }
        return best != null ? best : canonical;
    }

    public static synchronized String promoteSingleSpecial(
            String canonicalName, Role role, int id, int type, int rotation) {
        if (canonicalName == null || canonicalName.trim().isEmpty()) {
            return "Canonical rail special name is required.";
        }
        List<Component> components = new ArrayList<Component>();
        components.add(new Component(id, type, rotation, 0, 0));
        return saveComposite(canonicalName, role, components);
    }

    public static CompositeDefinition findAcceptedCurveForRoute() {
        CompositeDefinition accepted = findByName(ACCEPTED_CURVE_NAME);
        if (accepted != null) {
            return new CompositeDefinition(accepted.name, Role.CURVE,
                    new ArrayList<Component>(accepted.components), accepted.portMask);
        }

        CompositeDefinition classified = findFirst(Role.CURVE);
        if (classified != null) {
            return classified;
        }

        List<Component> components = new ArrayList<Component>();
        components.add(new Component(46377, 22, 0, 2, -1));
        components.add(new Component(46379, 22, 0, 3, -1));
        components.add(new Component(46381, 22, 0, 3, 0));
        return new CompositeDefinition(ACCEPTED_CURVE_NAME, Role.CURVE, components, 0);
    }

    private static Role parseRole(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        } catch (Exception ex) {
            return Role.CUSTOM;
        }
    }

    private static String cleanName(String value) {
        return value == null ? "" : value.trim().replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ');
    }

    private static String safe(String value) {
        return cleanName(value);
    }

    public static final class Component {
        private final int id;
        private final int type;
        private final int rotation;
        private final int offsetX;
        private final int offsetY;

        public Component(int id, int type, int rotation) {
            this(id, type, rotation, 0, 0);
        }

        public Component(int id, int type, int rotation, int offsetX, int offsetY) {
            this.id = Math.max(0, id);
            this.type = clamp(type, 0, 22);
            this.rotation = rotation & 0x3;
            this.offsetX = clamp(offsetX, -12, 12);
            this.offsetY = clamp(offsetY, -12, 12);
        }

        public int getId() {
            return id;
        }

        public int getType() {
            return type;
        }

        public int getRotation() {
            return rotation;
        }

        public int getOffsetX() {
            return offsetX;
        }

        public int getOffsetY() {
            return offsetY;
        }

        public String describe() {
            return "ID " + id + " | T" + type + " | R" + rotation
                    + " | dX " + offsetX + " | dY " + offsetY;
        }
    }

    public static final class CompositeDefinition {
        private final String name;
        private final Role role;
        private final List<Component> components;
        private final int portMask;

        private CompositeDefinition(String name, Role role, List<Component> components,
                int portMask) {
            this.name = name;
            this.role = role;
            this.components = components;
            this.portMask = portMask & 0xF;
        }

        public String getName() {
            return name;
        }

        public Role getRole() {
            return role;
        }

        public List<Component> getComponents() {
            return new ArrayList<Component>(components);
        }

        public int getPortMask() {
            return portMask;
        }

        public String getPortsText() {
            if (portMask == 0) {
                return "auto";
            }
            StringBuilder out = new StringBuilder();
            if ((portMask & PORT_NORTH) != 0) out.append("N");
            if ((portMask & PORT_EAST) != 0) out.append(out.length() == 0 ? "E" : "/E");
            if ((portMask & PORT_SOUTH) != 0) out.append(out.length() == 0 ? "S" : "/S");
            if ((portMask & PORT_WEST) != 0) out.append(out.length() == 0 ? "W" : "/W");
            return out.toString();
        }

        public String describe() {
            return name + " [" + role + "] " + components.size() + " component(s)"
                    + " ports=" + getPortsText();
        }
    }

    private static final class MutableComposite {
        private final String name;
        private final Role role;
        private int portMask;
        private final List<IndexedComponent> components =
                new ArrayList<IndexedComponent>();

        private MutableComposite(String name, Role role, int portMask) {
            this.name = name;
            this.role = role;
            this.portMask = portMask & 0xF;
        }
    }

    private static final class IndexedComponent {
        private final int index;
        private final Component component;

        private IndexedComponent(int index, Component component) {
            this.index = index;
            this.component = component;
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
