package game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Developer-only connected-mesh state for the Live Model Editor.
 *
 * Class159 is Matrix3's decoded model geometry before renderer-specific Model
 * construction. Every edited build starts from freshly decoded source bytes, so
 * cached/shared cache geometry is never mutated.
 */
final class LiveModelEditorParts {

    private static final int MAX_UNDO = 64;
    private static final short HIGHLIGHT_COLOUR = (short) 0xffff;

    private Source source;
    private final List<PartState> originals = new ArrayList<PartState>();
    private final List<PartState> duplicates = new ArrayList<PartState>();
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<Snapshot>();
    private int selected = -1;
    private int hovered = -1;
    private boolean isolate;
    private int revision;

    synchronized int ensureSource(ObjectDefinitions definition, int objectId, int objectType) {
        if (source != null && source.objectId == objectId && source.objectType == objectType)
            return source.components.length;
        Source loaded = loadSource(definition, objectId, objectType);
        source = loaded;
        originals.clear();
        duplicates.clear();
        undo.clear();
        selected = -1;
        hovered = -1;
        isolate = false;
        revision++;
        if (loaded == null) return 0;
        for (int i = 0; i < loaded.components.length; i++) originals.add(new PartState(i));
        return loaded.components.length;
    }

    synchronized void clear() {
        source = null;
        originals.clear();
        duplicates.clear();
        undo.clear();
        selected = -1;
        hovered = -1;
        isolate = false;
        revision++;
    }

    synchronized boolean isReadyFor(int objectId, int objectType) {
        return source != null && source.objectId == objectId && source.objectType == objectType;
    }

    synchronized int getRevision() { return revision; }
    synchronized int getPartCount() { return originals.size() + duplicates.size(); }
    synchronized int getSelected() { return selected; }
    synchronized int getHovered() { return hovered; }
    synchronized boolean isIsolate() { return isolate; }

    synchronized boolean hover(int index) {
        int next = index >= 0 && index < getPartCount() ? index : -1;
        if (hovered == next) return false;
        hovered = next;
        revision++;
        return true;
    }

    synchronized String[] getLabels() {
        if (source == null) return new String[0];
        String[] labels = new String[getPartCount()];
        for (int i = 0; i < originals.size(); i++) {
            PartState state = originals.get(i);
            Component component = source.components[state.sourcePart];
            labels[i] = "Part " + i + "  (" + component.faces.length + " faces, "
                    + component.vertices.length + " verts)" + suffix(state);
        }
        for (int i = 0; i < duplicates.size(); i++) {
            PartState state = duplicates.get(i);
            Component component = source.components[state.sourcePart];
            labels[originals.size() + i] = "Copy " + i + " -> Part " + state.sourcePart
                    + "  (" + component.faces.length + " faces)" + suffix(state);
        }
        return labels;
    }

    synchronized int[] getSelectedTransform() {
        PartState state = selectedState();
        if (state == null) return new int[] { 100, 100, 100, 0, 0, 0, 0 };
        return new int[] { state.scaleX, state.scaleY, state.scaleZ,
                state.moveX, state.moveY, state.moveZ, state.yaw };
    }

    synchronized boolean select(int index) {
        if (index < 0 || index >= getPartCount()) {
            selected = -1;
            revision++;
            return false;
        }
        if (selected != index) {
            selected = index;
            revision++;
        }
        return true;
    }

    synchronized boolean setSelectedTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        sx = clamp(sx, 10, 400);
        sy = clamp(sy, 10, 400);
        sz = clamp(sz, 10, 400);
        mx = clamp(mx, -4096, 4096);
        my = clamp(my, -4096, 4096);
        mz = clamp(mz, -4096, 4096);
        yaw = normalizeDegrees(yaw);
        if (state.scaleX == sx && state.scaleY == sy && state.scaleZ == sz
                && state.moveX == mx && state.moveY == my && state.moveZ == mz
                && state.yaw == yaw) return false;
        pushUndo();
        state.scaleX = sx; state.scaleY = sy; state.scaleZ = sz;
        state.moveX = mx; state.moveY = my; state.moveZ = mz; state.yaw = yaw;
        revision++;
        return true;
    }

    synchronized boolean toggleSelectedHidden() {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        pushUndo();
        state.hidden = !state.hidden;
        revision++;
        return true;
    }

    synchronized boolean deleteSelected() {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        pushUndo();
        state.deleted = true;
        state.hidden = true;
        revision++;
        return true;
    }

    synchronized boolean duplicateSelected() {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        pushUndo();
        PartState copy = state.copy();
        copy.hidden = false;
        copy.deleted = false;
        copy.moveX = clamp(copy.moveX + 128, -4096, 4096);
        duplicates.add(copy);
        selected = originals.size() + duplicates.size() - 1;
        revision++;
        return true;
    }

    synchronized boolean showAll() {
        boolean changed = false;
        for (PartState state : originals) if (state.hidden && !state.deleted) changed = true;
        for (PartState state : duplicates) if (state.hidden && !state.deleted) changed = true;
        if (!changed) return false;
        pushUndo();
        for (PartState state : originals) if (!state.deleted) state.hidden = false;
        for (PartState state : duplicates) if (!state.deleted) state.hidden = false;
        revision++;
        return true;
    }

    synchronized void toggleIsolate() {
        isolate = !isolate;
        revision++;
    }

    synchronized boolean undo() {
        if (undo.isEmpty()) return false;
        Snapshot snapshot = undo.removeLast();
        originals.clear();
        for (PartState state : snapshot.originals) originals.add(state.copy());
        duplicates.clear();
        for (PartState state : snapshot.duplicates) duplicates.add(state.copy());
        selected = snapshot.selected;
        isolate = snapshot.isolate;
        revision++;
        return true;
    }

    synchronized Class159 buildMainRaw() {
        if (source == null) return null;
        Class159 raw = source.decode();
        if (raw == null) return null;
        ensureFaceAlpha(raw);
        int highlight = hovered >= 0 ? hovered : selected;
        for (int i = 0; i < originals.size(); i++) {
            PartState state = originals.get(i);
            Component component = source.components[state.sourcePart];
            boolean visible = !state.hidden && !state.deleted && (!isolate || highlight == i);
            if (!visible) {
                hideFaces(raw, component);
                continue;
            }
            transformVertices(raw, component, state);
            if (highlight == i) highlightFaces(raw, component);
        }
        return raw;
    }

    synchronized List<Class159> buildDuplicateRaws() {
        if (source == null || duplicates.isEmpty()) return Collections.emptyList();
        List<Class159> raws = new ArrayList<Class159>();
        int highlight = hovered >= 0 ? hovered : selected;
        for (int i = 0; i < duplicates.size(); i++) {
            int combinedIndex = originals.size() + i;
            PartState state = duplicates.get(i);
            if (state.hidden || state.deleted || (isolate && highlight != combinedIndex)) continue;
            Class159 raw = source.decode();
            if (raw == null) continue;
            ensureFaceAlpha(raw);
            Component keep = source.components[state.sourcePart];
            for (int part = 0; part < source.components.length; part++)
                if (part != state.sourcePart) hideFaces(raw, source.components[part]);
            transformVertices(raw, keep, state);
            if (highlight == combinedIndex) highlightFaces(raw, keep);
            raws.add(raw);
        }
        return raws;
    }

    synchronized String projectJsonFields() {
        StringBuilder out = new StringBuilder();
        out.append("  \"partSelected\": ").append(selected).append(",\n");
        out.append("  \"partIsolate\": ").append(isolate).append(",\n");
        out.append("  \"partStates\": [\n");
        for (int i = 0; i < originals.size(); i++) {
            if (i > 0) out.append(",\n");
            appendState(out, originals.get(i), i, false);
        }
        out.append("\n  ],\n");
        out.append("  \"partDuplicates\": [\n");
        for (int i = 0; i < duplicates.size(); i++) {
            if (i > 0) out.append(",\n");
            appendState(out, duplicates.get(i), i, true);
        }
        out.append("\n  ]");
        return out.toString();
    }

    synchronized void loadProjectJson(String json) {
        if (source == null || json == null) return;
        pushUndo();
        Matcher states = Pattern.compile("\\\"partStates\\\"\\s*:\\s*\\[(.*?)\\]",
                Pattern.DOTALL).matcher(json);
        if (states.find()) {
            Matcher object = Pattern.compile("\\{([^}]*)\\}").matcher(states.group(1));
            while (object.find()) {
                String body = object.group(1);
                int index = readInt(body, "index", -1);
                if (index >= 0 && index < originals.size()) readState(body, originals.get(index));
            }
        }
        duplicates.clear();
        Matcher copies = Pattern.compile("\\\"partDuplicates\\\"\\s*:\\s*\\[(.*?)\\]",
                Pattern.DOTALL).matcher(json);
        if (copies.find()) {
            Matcher object = Pattern.compile("\\{([^}]*)\\}").matcher(copies.group(1));
            while (object.find()) {
                String body = object.group(1);
                int sourcePart = readInt(body, "sourcePart", -1);
                if (sourcePart >= 0 && sourcePart < source.components.length) {
                    PartState state = new PartState(sourcePart);
                    readState(body, state);
                    duplicates.add(state);
                }
            }
        }
        selected = readInt(json, "partSelected", -1);
        if (selected < -1 || selected >= getPartCount()) selected = -1;
        hovered = -1;
        isolate = readBoolean(json, "partIsolate", false);
        revision++;
    }

    private Source loadSource(ObjectDefinitions definition, int objectId, int objectType) {
        if (definition == null || definition.aByteArray5644 == null
                || definition.anIntArrayArray5611 == null) return null;
        int group = -1;
        for (int i = 0; i < definition.aByteArray5644.length; i++) {
            if ((definition.aByteArray5644[i] & 0xff) == objectType) {
                group = i;
                break;
            }
        }
        if (group < 0 || group >= definition.anIntArrayArray5611.length
                || definition.anIntArrayArray5611[group] == null
                || definition.anIntArrayArray5611[group].length == 0) return null;

        int[] modelIds = definition.anIntArrayArray5611[group].clone();
        byte[][] bytes = new byte[modelIds.length][];
        for (int i = 0; i < modelIds.length; i++) {
            bytes[i] = definition.aClass518_5608.method6136(modelIds[i], 49248435);
            if (bytes[i] == null) return null;
        }
        Source candidate = new Source(objectId, objectType, modelIds, bytes);
        Class159 raw = candidate.decode();
        if (raw == null || raw.anInt1791 <= 0 || raw.anInt1778 <= 0) return null;
        candidate.components = detectComponents(raw);
        return candidate;
    }

    private static Component[] detectComponents(Class159 raw) {
        int vertices = raw.anInt1791;
        int[] parent = new int[vertices];
        for (int i = 0; i < vertices; i++) parent[i] = i;
        for (int face = 0; face < raw.anInt1778; face++) {
            int a = raw.aShortArray1786[face] & 0xffff;
            int b = raw.aShortArray1787[face] & 0xffff;
            int c = raw.aShortArray1789[face] & 0xffff;
            if (a < vertices && b < vertices && c < vertices) {
                union(parent, a, b);
                union(parent, a, c);
            }
        }
        Map<Integer, Builder> byRoot = new LinkedHashMap<Integer, Builder>();
        for (int face = 0; face < raw.anInt1778; face++) {
            int a = raw.aShortArray1786[face] & 0xffff;
            if (a >= vertices) continue;
            int root = find(parent, a);
            Builder builder = byRoot.get(Integer.valueOf(root));
            if (builder == null) {
                builder = new Builder();
                byRoot.put(Integer.valueOf(root), builder);
            }
            builder.faces.add(Integer.valueOf(face));
            builder.vertices.add(Integer.valueOf(a));
            builder.vertices.add(Integer.valueOf(raw.aShortArray1787[face] & 0xffff));
            builder.vertices.add(Integer.valueOf(raw.aShortArray1789[face] & 0xffff));
        }
        List<Builder> builders = new ArrayList<Builder>(byRoot.values());
        Collections.sort(builders, new Comparator<Builder>() {
            @Override
            public int compare(Builder a, Builder b) {
                int byFaces = Integer.compare(b.faces.size(), a.faces.size());
                if (byFaces != 0) return byFaces;
                return Integer.compare(a.faces.get(0).intValue(), b.faces.get(0).intValue());
            }
        });
        Component[] components = new Component[builders.size()];
        for (int i = 0; i < builders.size(); i++) {
            Builder builder = builders.get(i);
            components[i] = new Component(toInts(builder.faces), toInts(builder.vertices));
        }
        return components;
    }

    private static void transformVertices(Class159 raw, Component component, PartState state) {
        if (component.vertices.length == 0) return;
        long cx = 0, cy = 0, cz = 0;
        for (int vertex : component.vertices) {
            cx += raw.anIntArray1782[vertex];
            cy += raw.anIntArray1777[vertex];
            cz += raw.anIntArray1797[vertex];
        }
        cx /= component.vertices.length;
        cy /= component.vertices.length;
        cz /= component.vertices.length;
        double radians = Math.toRadians(state.yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int vertex : component.vertices) {
            double x = (raw.anIntArray1782[vertex] - cx) * state.scaleX / 100.0;
            double y = (raw.anIntArray1777[vertex] - cy) * state.scaleY / 100.0;
            double z = (raw.anIntArray1797[vertex] - cz) * state.scaleZ / 100.0;
            double rx = x * cos + z * sin;
            double rz = z * cos - x * sin;
            raw.anIntArray1782[vertex] = (int) Math.round(cx + rx + state.moveX);
            raw.anIntArray1777[vertex] = (int) Math.round(cy + y + state.moveY);
            raw.anIntArray1797[vertex] = (int) Math.round(cz + rz + state.moveZ);
        }
    }

    private static void hideFaces(Class159 raw, Component component) {
        for (int face : component.faces) raw.faceAlpha[face] = (byte) 0xff;
    }

    private static void highlightFaces(Class159 raw, Component component) {
        for (int face : component.faces) {
            raw.faceAlpha[face] = 0;
            raw.faceColours[face] = HIGHLIGHT_COLOUR;
            if (raw.faceTextures != null) raw.faceTextures[face] = (short) -1;
            if (raw.faceTextureIndexes != null) raw.faceTextureIndexes[face] = (short) -1;
        }
    }

    private static void ensureFaceAlpha(Class159 raw) {
        if (raw.faceAlpha == null || raw.faceAlpha.length < raw.anInt1778)
            raw.faceAlpha = new byte[raw.anInt1778];
    }

    private PartState selectedState() {
        if (selected < 0) return null;
        if (selected < originals.size()) return originals.get(selected);
        int copy = selected - originals.size();
        return copy >= 0 && copy < duplicates.size() ? duplicates.get(copy) : null;
    }

    private void pushUndo() {
        if (undo.size() >= MAX_UNDO) undo.removeFirst();
        undo.addLast(new Snapshot(originals, duplicates, selected, isolate));
    }

    private static String suffix(PartState state) {
        if (state.deleted) return "  [deleted]";
        if (state.hidden) return "  [hidden]";
        return "";
    }

    private static void appendState(StringBuilder out, PartState state, int index, boolean duplicate) {
        out.append("    {");
        if (duplicate)
            out.append("\"copy\": ").append(index).append(", \"sourcePart\": ").append(state.sourcePart);
        else
            out.append("\"index\": ").append(index);
        out.append(", \"scaleX\": ").append(state.scaleX)
                .append(", \"scaleY\": ").append(state.scaleY)
                .append(", \"scaleZ\": ").append(state.scaleZ)
                .append(", \"moveX\": ").append(state.moveX)
                .append(", \"moveY\": ").append(state.moveY)
                .append(", \"moveZ\": ").append(state.moveZ)
                .append(", \"yaw\": ").append(state.yaw)
                .append(", \"hidden\": ").append(state.hidden)
                .append(", \"deleted\": ").append(state.deleted)
                .append("}");
    }

    private static void readState(String body, PartState state) {
        state.scaleX = clamp(readInt(body, "scaleX", 100), 10, 400);
        state.scaleY = clamp(readInt(body, "scaleY", 100), 10, 400);
        state.scaleZ = clamp(readInt(body, "scaleZ", 100), 10, 400);
        state.moveX = clamp(readInt(body, "moveX", 0), -4096, 4096);
        state.moveY = clamp(readInt(body, "moveY", 0), -4096, 4096);
        state.moveZ = clamp(readInt(body, "moveZ", 0), -4096, 4096);
        state.yaw = normalizeDegrees(readInt(body, "yaw", 0));
        state.hidden = readBoolean(body, "hidden", false);
        state.deleted = readBoolean(body, "deleted", false);
    }

    private static int readInt(String text, String key, int fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(-?\\d+)").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static boolean readBoolean(String text, String key, boolean fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) root = parent[root];
        while (parent[value] != value) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }

    private static int[] toInts(Iterable<Integer> values) {
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (Integer value : values) list.add(value);
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i).intValue();
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static int normalizeDegrees(int value) {
        int normalized = value % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    private static final class Builder {
        final List<Integer> faces = new ArrayList<Integer>();
        final LinkedHashSet<Integer> vertices = new LinkedHashSet<Integer>();
    }

    private static final class Component {
        final int[] faces;
        final int[] vertices;
        Component(int[] faces, int[] vertices) {
            this.faces = faces;
            this.vertices = vertices;
        }
    }

    private static final class PartState {
        final int sourcePart;
        int scaleX = 100, scaleY = 100, scaleZ = 100;
        int moveX, moveY, moveZ, yaw;
        boolean hidden, deleted;
        PartState(int sourcePart) { this.sourcePart = sourcePart; }
        PartState copy() {
            PartState copy = new PartState(sourcePart);
            copy.scaleX = scaleX; copy.scaleY = scaleY; copy.scaleZ = scaleZ;
            copy.moveX = moveX; copy.moveY = moveY; copy.moveZ = moveZ; copy.yaw = yaw;
            copy.hidden = hidden; copy.deleted = deleted;
            return copy;
        }
    }

    private static final class Snapshot {
        final List<PartState> originals = new ArrayList<PartState>();
        final List<PartState> duplicates = new ArrayList<PartState>();
        final int selected;
        final boolean isolate;
        Snapshot(List<PartState> originalStates, List<PartState> duplicateStates,
                int selected, boolean isolate) {
            for (PartState state : originalStates) originals.add(state.copy());
            for (PartState state : duplicateStates) duplicates.add(state.copy());
            this.selected = selected;
            this.isolate = isolate;
        }
    }

    private static final class Source {
        final int objectId;
        final int objectType;
        final int[] modelIds;
        final byte[][] bytes;
        Component[] components = new Component[0];
        Source(int objectId, int objectType, int[] modelIds, byte[][] bytes) {
            this.objectId = objectId;
            this.objectType = objectType;
            this.modelIds = modelIds;
            this.bytes = bytes;
        }
        Class159 decode() {
            Class159[] raws = new Class159[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                raws[i] = new Class159(bytes[i]);
                if (raws[i].anInt1773 < 13) raws[i].method2567(2);
            }
            return raws.length == 1 ? raws[0] : new Class159(raws, raws.length);
        }
    }
}
