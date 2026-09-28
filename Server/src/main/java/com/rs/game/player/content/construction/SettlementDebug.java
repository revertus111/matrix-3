package com.rs.game.player.content.construction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.EnumSet;

/**
 * Transient debug event bus for one live settlement instance.
 *
 * Debug state is intentionally not persisted. Runtime systems emit concise
 * state/decision events here; output sinks decide whether to print, speak or
 * export them without taking ownership of gameplay behavior.
 */
public final class SettlementDebug {

    public enum Category {
        WORKER_ACTIONS("worker-actions", "Worker Actions"),
        WORKER_SPEECH("worker-speech", "Worker Speech"),
        PATHING("pathing", "Pathing"),
        LOGISTICS("logistics", "Logistics"),
        STORAGE("storage", "Storage"),
        RESERVATIONS("reservations", "Reservations"),
        PROCESSING("processing", "Processing"),
        RAILS("rails", "Rails"),
        RTS("rts", "RTS"),
        PERSISTENCE("persistence", "Persistence"),
        PERFORMANCE("performance", "Performance");

        private final String key;
        private final String displayName;

        Category(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Category forKey(String key) {
            if (key == null) {
                return null;
            }
            String normalized = key.trim().toLowerCase().replace('_', '-');
            for (Category category : values()) {
                if (category.key.equals(normalized)
                        || category.name().toLowerCase().replace('_', '-').equals(normalized)) {
                    return category;
                }
            }
            return null;
        }
    }

    private static final int MAX_EVENTS = 2000;
    private static final File EXPORT_DIRECTORY =
            new File("data/construction/debug");

    private final EnumSet<Category> enabledCategories =
            EnumSet.noneOf(Category.class);
    private final ArrayDeque<String> events = new ArrayDeque<String>();

    private boolean masterEnabled;

    public SettlementDebug() {
        /*
         * Useful default category preset, but the master switch is deliberately
         * OFF until a developer enables it from the Construction debug panel.
         */
        enabledCategories.add(Category.WORKER_ACTIONS);
        enabledCategories.add(Category.WORKER_SPEECH);
        enabledCategories.add(Category.LOGISTICS);
        enabledCategories.add(Category.STORAGE);
    }

    public synchronized boolean isMasterEnabled() {
        return masterEnabled;
    }

    public synchronized boolean isEnabled(Category category) {
        return masterEnabled
                && category != null
                && enabledCategories.contains(category);
    }

    public synchronized String setMasterEnabled(boolean enabled) {
        masterEnabled = enabled;
        return "Construction debug master=" + (enabled ? "ON" : "OFF") + ".";
    }

    public synchronized String setCategoryEnabled(Category category, boolean enabled) {
        if (category == null) {
            return "Unknown Construction debug category.";
        }
        if (enabled) {
            enabledCategories.add(category);
        } else {
            enabledCategories.remove(category);
        }
        return category.getDisplayName() + "=" + (enabled ? "ON" : "OFF") + ".";
    }

    public synchronized String getStatus() {
        StringBuilder out = new StringBuilder();
        out.append("Construction Debug master=")
                .append(masterEnabled ? "ON" : "OFF")
                .append(" | categories=");
        boolean first = true;
        for (Category category : Category.values()) {
            if (!enabledCategories.contains(category)) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            out.append(category.getKey());
            first = false;
        }
        if (first) {
            out.append("none");
        }
        out.append(" | buffered=").append(events.size());
        return out.toString();
    }

    public synchronized void record(String actor, String message, Category... categories) {
        if (!masterEnabled || message == null || message.trim().length() == 0) {
            return;
        }

        boolean categoryEnabled = false;
        StringBuilder tags = new StringBuilder();
        if (categories != null) {
            for (Category category : categories) {
                if (category == null || category == Category.WORKER_SPEECH) {
                    continue;
                }
                if (tags.length() > 0) {
                    tags.append(',');
                }
                tags.append(category.getKey());
                if (enabledCategories.contains(category)) {
                    categoryEnabled = true;
                }
            }
        }
        if (!categoryEnabled) {
            return;
        }

        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        StringBuilder line = new StringBuilder();
        line.append('[').append(timestamp).append("] [")
                .append(tags.length() == 0 ? "debug" : tags)
                .append(']');
        if (actor != null && actor.trim().length() > 0) {
            line.append(" [").append(actor.trim()).append(']');
        }
        line.append(' ').append(message.trim());

        events.addLast(line.toString());
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
        System.out.println("[ConstructionDebug] " + line);
    }

    public synchronized String clear() {
        int cleared = events.size();
        events.clear();
        return "Construction debug buffer cleared (" + cleared + " event"
                + (cleared == 1 ? "" : "s") + ").";
    }

    public synchronized String export() {
        if (!EXPORT_DIRECTORY.exists() && !EXPORT_DIRECTORY.mkdirs()) {
            return "Construction debug export failed: could not create "
                    + EXPORT_DIRECTORY.getPath() + ".";
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File output = new File(
                EXPORT_DIRECTORY, "settlement_debug_" + stamp + ".txt");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(output));
            writer.write(getStatus());
            writer.newLine();
            writer.write("------------------------------------------------------------");
            writer.newLine();
            for (String event : events) {
                writer.write(event);
                writer.newLine();
            }
        } catch (IOException ex) {
            return "Construction debug export failed: " + ex.getMessage();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
        return "Construction debug exported " + events.size()
                + " event(s) to " + output.getPath() + ".";
    }
}
