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

/** Developer-owned tagged catalog for Object Lab asset discoveries. */
public final class SettlementObjectCatalog {

    private static final Path CATALOG =
            Paths.get("data/construction/object_lab_catalog.tsv");
    private static final String HEADER =
            "timestamp\tid\tname\ttype\trotation\ttag\tsourceX\tsourceY\tplane\tnotes";

    private SettlementObjectCatalog() {
    }

    public static synchronized String save(int id, int type, int rotation, String tag,
            int x, int y, int plane, String notes) {
        if (id < 0)
            return "Object Lab save rejected: object id must be non-negative.";
        if (type < 0 || type > 22)
            return "Object Lab save rejected: object type must be 0-22.";
        if (rotation < 0 || rotation > 3)
            return "Object Lab save rejected: rotation must be 0-3.";
        if (x < 0 || x > 16383 || y < 0 || y > 16383 || plane < 0 || plane > 3)
            return "Object Lab save rejected: source tile is outside the Matrix3 world range.";

        ObjectDefinitions definition = ObjectDefinitions.getObjectDefinitions(id);
        String name = definition == null ? "id-" + id : clean(definition.name, "id-" + id);
        String normalizedTag = normalizeTag(tag);
        String row = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                + "\t" + id + "\t" + sanitize(name) + "\t" + type + "\t" + rotation
                + "\t" + normalizedTag + "\t" + x + "\t" + y + "\t" + plane
                + "\t" + sanitize(notes);

        try {
            Files.createDirectories(CATALOG.getParent());
            if (!Files.exists(CATALOG) || Files.size(CATALOG) == 0L) {
                Files.write(CATALOG, java.util.Arrays.asList(HEADER, row),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(CATALOG, java.util.Arrays.asList(row),
                        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
            return "Object Lab saved " + name + " (" + id + ") type=" + type
                    + " rot=" + rotation + " tag=" + normalizedTag
                    + " to Server/data/construction/object_lab_catalog.tsv.";
        } catch (IOException ex) {
            return "Object Lab save failed: " + ex.getMessage();
        }
    }

    public static synchronized List<String> listRecent(int maximum) {
        List<String> output = new ArrayList<String>();
        if (!Files.exists(CATALOG)) {
            output.add("Object Lab catalog is empty.");
            return output;
        }
        try {
            List<String> lines = Files.readAllLines(CATALOG, StandardCharsets.UTF_8);
            int wanted = Math.max(1, Math.min(maximum, 20));
            int shown = 0;
            output.add("Object Lab recent catalog entries:");
            for (int i = lines.size() - 1; i >= 1 && shown < wanted; i--) {
                String[] parts = lines.get(i).split("\\t", -1);
                if (parts.length < 10)
                    continue;
                output.add("#" + (shown + 1)
                        + " id=" + parts[1] + " name=" + parts[2]
                        + " type=" + parts[3] + " rot=" + parts[4]
                        + " tag=" + parts[5]
                        + " source=" + parts[6] + "," + parts[7] + "," + parts[8]
                        + (parts[9].length() == 0 ? "" : " notes=" + parts[9]));
                shown++;
            }
            if (shown == 0)
                output.add("Object Lab catalog contains no readable entries.");
        } catch (IOException ex) {
            output.add("Object Lab catalog read failed: " + ex.getMessage());
        }
        return output;
    }

    private static String normalizeTag(String tag) {
        if (tag == null)
            return "UNKNOWN";
        String value = tag.trim().toUpperCase();
        return value.length() == 0 || !value.matches("[A-Z0-9_\\-]{1,40}")
                ? "UNKNOWN" : value;
    }

    private static String sanitize(String value) {
        return value == null ? ""
                : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String clean(String value, String fallback) {
        if (value == null)
            return fallback;
        String trimmed = value.trim();
        return trimmed.length() == 0 || "null".equalsIgnoreCase(trimmed)
                ? fallback : trimmed;
    }
}
