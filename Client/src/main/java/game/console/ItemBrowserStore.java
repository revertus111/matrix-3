package game.console;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Client Console-owned persistence for item favorites, categories, and presets.
 */
public final class ItemBrowserStore {

    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_NAME = "name";
    private static final String KEY_ITEMS = "items";

    private final Preferences root = Preferences.userNodeForPackage(ConsolePreferences.class)
            .node("workspace").node("itemBrowser");
    private final Preferences categoryRoot = root.node("categories");
    private final Preferences presetRoot = root.node("presets");

    private final Set<Integer> favorites = new LinkedHashSet<Integer>();
    private final Map<String, LinkedHashSet<Integer>> categories =
            new LinkedHashMap<String, LinkedHashSet<Integer>>();
    private final Map<String, LinkedHashMap<Integer, Integer>> presets =
            new LinkedHashMap<String, LinkedHashMap<Integer, Integer>>();

    public ItemBrowserStore() {
        load();
    }

    public synchronized boolean isFavorite(int itemId) {
        return favorites.contains(Integer.valueOf(itemId));
    }

    public synchronized boolean toggleFavorite(int itemId) {
        Integer id = Integer.valueOf(itemId);
        boolean favorite;
        if (favorites.contains(id)) {
            favorites.remove(id);
            favorite = false;
        } else {
            favorites.add(id);
            favorite = true;
        }
        saveFavorites();
        return favorite;
    }

    public synchronized Set<Integer> getFavorites() {
        return new LinkedHashSet<Integer>(favorites);
    }

    public synchronized List<String> getCategories() {
        return new ArrayList<String>(categories.keySet());
    }

    public synchronized boolean createCategory(String rawName) {
        String name = normalizeName(rawName);
        if (name == null || categories.containsKey(name)) {
            return false;
        }
        categories.put(name, new LinkedHashSet<Integer>());
        saveCategory(name);
        return true;
    }

    public synchronized boolean categoryContains(String category, int itemId) {
        Set<Integer> ids = categories.get(category);
        return ids != null && ids.contains(Integer.valueOf(itemId));
    }

    public synchronized void setCategoryMembership(String category, int itemId, boolean included) {
        LinkedHashSet<Integer> ids = categories.get(category);
        if (ids == null) {
            return;
        }
        if (included) {
            ids.add(Integer.valueOf(itemId));
        } else {
            ids.remove(Integer.valueOf(itemId));
        }
        saveCategory(category);
    }

    public synchronized Set<Integer> getCategoryItems(String category) {
        Set<Integer> ids = categories.get(category);
        return ids == null ? Collections.<Integer>emptySet() : new LinkedHashSet<Integer>(ids);
    }

    public synchronized List<String> getPresets() {
        return new ArrayList<String>(presets.keySet());
    }

    public synchronized boolean createPreset(String rawName) {
        String name = normalizeName(rawName);
        if (name == null || presets.containsKey(name)) {
            return false;
        }
        presets.put(name, new LinkedHashMap<Integer, Integer>());
        savePreset(name);
        return true;
    }

    public synchronized void addToPreset(String preset, int itemId, int amount) {
        LinkedHashMap<Integer, Integer> items = presets.get(preset);
        if (items == null) {
            return;
        }
        items.put(Integer.valueOf(itemId), Integer.valueOf(Math.max(1, amount)));
        savePreset(preset);
    }

    public synchronized void removeFromPreset(String preset, int itemId) {
        LinkedHashMap<Integer, Integer> items = presets.get(preset);
        if (items == null) {
            return;
        }
        items.remove(Integer.valueOf(itemId));
        savePreset(preset);
    }

    public synchronized Map<Integer, Integer> getPresetItems(String preset) {
        Map<Integer, Integer> items = presets.get(preset);
        return items == null
                ? Collections.<Integer, Integer>emptyMap()
                : new LinkedHashMap<Integer, Integer>(items);
    }

    private void load() {
        favorites.clear();
        categories.clear();
        presets.clear();
        parseIdSet(root.get(KEY_FAVORITES, ""), favorites);

        try {
            for (String child : categoryRoot.childrenNames()) {
                Preferences node = categoryRoot.node(child);
                String name = normalizeName(node.get(KEY_NAME, decodeNodeName(child)));
                if (name == null) {
                    continue;
                }
                LinkedHashSet<Integer> ids = new LinkedHashSet<Integer>();
                parseIdSet(node.get(KEY_ITEMS, ""), ids);
                categories.put(name, ids);
            }
            for (String child : presetRoot.childrenNames()) {
                Preferences node = presetRoot.node(child);
                String name = normalizeName(node.get(KEY_NAME, decodeNodeName(child)));
                if (name == null) {
                    continue;
                }
                presets.put(name, parsePreset(node.get(KEY_ITEMS, "")));
            }
        } catch (BackingStoreException | RuntimeException ex) {
            System.err.println("Unable to load Client Console Item Browser preferences.");
            ex.printStackTrace();
        }
    }

    private void saveFavorites() {
        root.put(KEY_FAVORITES, joinIds(favorites));
        flush(root);
    }

    private void saveCategory(String name) {
        Preferences node = categoryRoot.node(encodeNodeName(name));
        node.put(KEY_NAME, name);
        node.put(KEY_ITEMS, joinIds(categories.get(name)));
        flush(node);
    }

    private void savePreset(String name) {
        Preferences node = presetRoot.node(encodeNodeName(name));
        node.put(KEY_NAME, name);
        node.put(KEY_ITEMS, joinPreset(presets.get(name)));
        flush(node);
    }

    private void flush(Preferences preferences) {
        try {
            preferences.flush();
        } catch (BackingStoreException | RuntimeException ex) {
            System.err.println("Unable to save Client Console Item Browser preferences.");
            ex.printStackTrace();
        }
    }

    private static String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        if (name.length() == 0) {
            return null;
        }
        if (name.length() > 48) {
            name = name.substring(0, 48).trim();
        }
        return name.length() == 0 ? null : name;
    }

    private static void parseIdSet(String value, Set<Integer> output) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            try {
                output.add(Integer.valueOf(Integer.parseInt(part.trim())));
            } catch (NumberFormatException ex) {
                // Ignore one stale entry without invalidating the whole store.
            }
        }
    }

    private static String joinIds(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Integer id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id.intValue());
        }
        return builder.toString();
    }

    private static LinkedHashMap<Integer, Integer> parsePreset(String value) {
        LinkedHashMap<Integer, Integer> items = new LinkedHashMap<Integer, Integer>();
        if (value == null || value.trim().length() == 0) {
            return items;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            try {
                int id = Integer.parseInt(pair[0].trim());
                int amount = Integer.parseInt(pair[1].trim());
                if (id >= 0 && amount > 0) {
                    items.put(Integer.valueOf(id), Integer.valueOf(amount));
                }
            } catch (NumberFormatException ex) {
                // Ignore one stale entry without invalidating the whole preset.
            }
        }
        return items;
    }

    private static String joinPreset(Map<Integer, Integer> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey().intValue())
                    .append(':')
                    .append(Math.max(1, entry.getValue().intValue()));
        }
        return builder.toString();
    }

    private static String encodeNodeName(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeNodeName(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
