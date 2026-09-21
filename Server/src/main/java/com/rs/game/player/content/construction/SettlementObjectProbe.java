package com.rs.game.player.content.construction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.rs.cache.loaders.ObjectDefinitions;
import com.rs.game.Region;
import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;

/**
 * Read-only developer probe for identifying live Matrix3 world objects that may
 * be useful as Construction/automation assets.
 *
 * Effective Region object slots are inspected so runtime replacements/removals
 * are respected. The probe never mutates the world or cache definitions.
 */
public final class SettlementObjectProbe {

    private static final int MAX_CHAT_ENTRIES = 24;
    private static final String CATALOG_PATH = "data/construction/object_catalog.txt";

    private SettlementObjectProbe() {
    }

    public static List<String> scan(WorldTile center, int radius) {
        int boundedRadius = radius <= 0 ? 0 : 1;
        List<ProbeEntry> entries = collect(center, boundedRadius);
        List<String> lines = new ArrayList<String>();

        if (center == null) {
            lines.add("Object Probe: no tile is available.");
            return lines;
        }

        String scope = boundedRadius == 0 ? "current tile" : "nearby 3x3";
        lines.add("Object Probe " + scope + " @ "
                + center.getX() + "," + center.getY() + "," + center.getPlane()
                + " | objects=" + entries.size() + ".");

        if (entries.isEmpty()) {
            lines.add("No effective world objects found in this probe area.");
            return lines;
        }

        int shown = 0;
        for (ProbeEntry entry : entries) {
            if (shown >= MAX_CHAT_ENTRIES) {
                lines.add("... " + (entries.size() - shown)
                        + " more object(s); use Log to capture the full result.");
                break;
            }
            lines.add(entry.toLine());
            shown++;
        }
        return lines;
    }

    public static synchronized String appendToCatalog(WorldTile center, int radius) {
        if (center == null) {
            return "Object Probe log failed: no tile is available.";
        }

        int boundedRadius = radius <= 0 ? 0 : 1;
        List<ProbeEntry> entries = collect(center, boundedRadius);
        Path file = Paths.get(CATALOG_PATH);

        List<String> lines = new ArrayList<String>();
        lines.add("=== OBJECT PROBE ===");
        lines.add("timestamp=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        lines.add("scope=" + (boundedRadius == 0 ? "current-tile" : "nearby-3x3")
                + " center=" + center.getX() + "," + center.getY() + "," + center.getPlane()
                + " region=" + center.getRegionId()
                + " objects=" + entries.size());
        if (entries.isEmpty()) {
            lines.add("objects=none");
        } else {
            for (ProbeEntry entry : entries) {
                lines.add(entry.toLine());
            }
        }
        lines.add("");

        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Object Probe logged " + entries.size()
                    + " object(s) to Server/" + CATALOG_PATH + ".";
        } catch (IOException ex) {
            return "Object Probe log failed: " + ex.getMessage();
        }
    }

    private static List<ProbeEntry> collect(WorldTile center, int radius) {
        List<ProbeEntry> entries = new ArrayList<ProbeEntry>();
        if (center == null) {
            return entries;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                WorldTile tile = new WorldTile(
                        center.getX() + dx,
                        center.getY() + dy,
                        center.getPlane());
                Region region = World.getRegion(tile.getRegionId());
                for (int slot = 0; slot < 4; slot++) {
                    WorldObject object = region.getObjectWithSlot(
                            tile.getPlane(), tile.getXInRegion(), tile.getYInRegion(), slot);
                    if (object == null) {
                        continue;
                    }
                    entries.add(new ProbeEntry(slot, object, object.getDefinitions()));
                }
            }
        }
        return entries;
    }

    private static String slotName(int slot) {
        switch (slot) {
        case Region.OBJECT_SLOT_WALL:
            return "wall";
        case Region.OBJECT_SLOT_WALL_DECORATION:
            return "wall-decoration";
        case Region.OBJECT_SLOT_FLOOR:
            return "floor";
        case Region.OBJECT_SLOT_FLOOR_DECORATION:
            return "floor-decoration";
        default:
            return "slot-" + slot;
        }
    }

    private static String displayName(ObjectDefinitions definition, int id) {
        if (definition == null || definition.name == null
                || definition.name.trim().length() == 0
                || "null".equalsIgnoreCase(definition.name.trim())) {
            return "id-" + id;
        }
        return definition.name.replace('|', '/');
    }

    private static String options(ObjectDefinitions definition) {
        if (definition == null) {
            return "[]";
        }
        StringBuilder options = new StringBuilder("[");
        boolean any = false;
        for (int optionIndex = 1; optionIndex <= 5; optionIndex++) {
            String option = definition.getOption(optionIndex);
            if (option == null || option.trim().length() == 0) {
                continue;
            }
            if (any) {
                options.append(", ");
            }
            options.append(optionIndex).append(":").append(option.replace('|', '/'));
            any = true;
        }
        return options.append("]").toString();
    }

    private static final class ProbeEntry {
        private final int slot;
        private final WorldObject object;
        private final ObjectDefinitions definition;

        private ProbeEntry(int slot, WorldObject object, ObjectDefinitions definition) {
            this.slot = slot;
            this.object = object;
            this.definition = definition;
        }

        private String toLine() {
            int sizeX = definition == null ? -1 : definition.sizeX;
            int sizeY = definition == null ? -1 : definition.sizeY;
            int clipType = definition == null ? -1 : definition.clipType;
            int animation = definition == null ? -1 : definition.objectAnimation;
            int mapIcon = definition == null ? -1 : definition.mapIconId;
            int mapSprite = definition == null ? -1 : definition.mapSpriteId;

            return "slot=" + slot + "(" + slotName(slot) + ")"
                    + " id=" + object.getId()
                    + " name=" + displayName(definition, object.getId())
                    + " type=" + object.getType()
                    + " rot=" + object.getRotation()
                    + " tile=" + object.getX() + "," + object.getY() + "," + object.getPlane()
                    + " size=" + sizeX + "x" + sizeY
                    + " clip=" + clipType
                    + " anim=" + animation
                    + " mapIcon=" + mapIcon
                    + " mapSprite=" + mapSprite
                    + " options=" + options(definition);
        }
    }
}
