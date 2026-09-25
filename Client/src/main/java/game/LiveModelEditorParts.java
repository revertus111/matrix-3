package game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
    private boolean gestureActive;
    private int revision;
    private int geometryRevision;

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
        gestureActive = false;
        revision++;
        geometryRevision++;
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
        gestureActive = false;
        revision++;
        geometryRevision++;
    }

    synchronized boolean isReadyFor(int objectId, int objectType) {
        return source != null && source.objectId == objectId && source.objectType == objectType;
    }

    synchronized int getRevision() { return revision; }
    synchronized int getGeometryRevision() { return geometryRevision; }
    synchronized int getPartCount() { return originals.size() + duplicates.size(); }
    synchronized int getSelected() { return selected; }
    synchronized int getHovered() { return hovered; }
    synchronized boolean isIsolate() { return isolate; }

    synchronized int getFaceCount(int index) {
        PartState state = stateAt(index);
        if (state == null || source == null) return Integer.MAX_VALUE;
        return source.components[state.sourcePart].faces.length;
    }

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
            if (selected != -1) {
                selected = -1;
                revision++;
            }
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
        int[] values = sanitizeTransform(sx, sy, sz, mx, my, mz, yaw);
        if (sameTransform(state, values)) return false;
        pushUndo();
        applyTransform(state, values);
        markGeometryChanged();
        return true;
    }

    synchronized boolean beginGesture() {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        if (!gestureActive) {
            pushUndo();
            gestureActive = true;
        }
        return true;
    }

    synchronized boolean updateGestureTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        if (!gestureActive) return false;
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        int[] values = sanitizeTransform(sx, sy, sz, mx, my, mz, yaw);
        if (sameTransform(state, values)) return false;
        applyTransform(state, values);
        markGeometryChanged();
        return true;
    }

    synchronized void endGesture() {
        gestureActive = false;
    }

    synchronized boolean toggleSelectedHidden() {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        pushUndo();
        state.hidden = !state.hidden;
        markGeometryChanged();
        return true;
    }

    synchronized boolean deleteSelected() {
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        pushUndo();
        state.deleted = true;
        state.hidden = true;
        markGeometryChanged();
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
        markGeometryChanged();
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
        markGeometryChanged();
        return true;
    }

    synchronized void toggleIsolate() {
        isolate = !isolate;
        markGeometryChanged();
    }

    synchronized boolean replaceSelected(int replacementObjectId, int replacementObjectType,
            boolean allMatching) {
        if (source == null || replacementObjectId < 0) return false;
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        Component selectedComponent = source.components[state.sourcePart];
        pushUndo();
        boolean changed = false;
        if (allMatching) {
            String signature = selectedComponent.signature;
            for (PartState candidate : originals) {
                if (!candidate.deleted
                        && signature.equals(source.components[candidate.sourcePart].signature)) {
                    candidate.replacementObjectId = replacementObjectId;
                    candidate.replacementObjectType = replacementObjectType;
                    candidate.hidden = false;
                    changed = true;
                }
            }
            for (PartState candidate : duplicates) {
                if (!candidate.deleted
                        && signature.equals(source.components[candidate.sourcePart].signature)) {
                    candidate.replacementObjectId = replacementObjectId;
                    candidate.replacementObjectType = replacementObjectType;
                    candidate.hidden = false;
                    changed = true;
                }
            }
        } else {
            state.replacementObjectId = replacementObjectId;
            state.replacementObjectType = replacementObjectType;
            state.hidden = false;
            changed = true;
        }
        if (changed) markGeometryChanged();
        return changed;
    }

    synchronized boolean clearSelectedReplacement() {
        PartState state = selectedState();
        if (state == null || state.replacementObjectId < 0) return false;
        pushUndo();
        state.replacementObjectId = -1;
        state.replacementObjectType = 10;
        markGeometryChanged();
        return true;
    }

    synchronized boolean undo() {
        if (undo.isEmpty()) return false;
        Snapshot snapshot = undo.removeLast();
        originals.clear();
        for (PartState state : snapshot.originals) originals.add(state.copy());
        duplicates.clear();
        for (PartState state : snapshot.duplicates) duplicates.add(state.copy());
        selected = snapshot.selected;
        hovered = -1;
        isolate = snapshot.isolate;
        gestureActive = false;
        markGeometryChanged();
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
            boolean visible = !state.hidden && !state.deleted
                    && state.replacementObjectId < 0
                    && (!isolate || highlight == i);
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
            if (state.hidden || state.deleted || state.replacementObjectId >= 0
                    || (isolate && highlight != combinedIndex)) continue;
            Class159 raw = componentOnlyRaw(source.decode(),
                    source.components[state.sourcePart], state);
            if (raw == null) continue;
            if (highlight == combinedIndex) highlightAllFaces(raw);
            raws.add(raw);
        }
        return raws;
    }

    synchronized List<ReplacementRaw> buildReplacementRaws() {
        if (source == null) return Collections.emptyList();
        List<ReplacementRaw> raws = new ArrayList<ReplacementRaw>();
        int highlight = hovered >= 0 ? hovered : selected;
        int total = getPartCount();
        for (int index = 0; index < total; index++) {
            PartState state = stateAt(index);
            if (state == null || state.hidden || state.deleted || state.replacementObjectId < 0
                    || (isolate && highlight != index)) continue;
            Class159 raw = replacementRaw(state, source.components[state.sourcePart]);
            if (raw == null) continue;
            if (highlight == index) highlightAllFaces(raw);
            raws.add(new ReplacementRaw(index, state.replacementObjectId,
                    state.replacementObjectType, raw));
        }
        return raws;
    }

    synchronized List<PickRaw> buildPickRaws() {
        if (source == null) return Collections.emptyList();
        List<PickRaw> raws = new ArrayList<PickRaw>();
        int total = getPartCount();
        int highlight = hovered >= 0 ? hovered : selected;
        for (int index = 0; index < total; index++) {
            PartState state = stateAt(index);
            if (state == null || state.hidden || state.deleted
                    || (isolate && highlight != index)) continue;
            if (state.replacementObjectId >= 0) {
                Class159 replacement = replacementRaw(state,
                        source.components[state.sourcePart]);
                if (replacement != null) {
                    raws.add(new PickRaw(index, state.replacementObjectId,
                            state.replacementObjectType, replacement));
                }
            } else {
                Class159 component = componentOnlyRaw(source.decode(),
                        source.components[state.sourcePart], state);
                if (component != null) {
                    raws.add(new PickRaw(index, source.objectId, source.objectType, component));
                }
            }
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
        gestureActive = false;
        markGeometryChanged();
    }

    private Source loadSource(ObjectDefinitions definition, int objectId, int objectType) {
        if (definition == null || definition.anIntArrayArray5611 == null) return null;
        int group = findModelGroup(definition, objectType);
        if (group < 0) return null;

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
        populateComponentBounds(raw, candidate.components);
        return candidate;
    }

    private static int findModelGroup(ObjectDefinitions definition, int objectType) {
        if (definition == null || definition.anIntArrayArray5611 == null) return -1;
        if (definition.aByteArray5644 == null) {
            return definition.anIntArrayArray5611.length > 0
                    && definition.anIntArrayArray5611[0] != null
                    && definition.anIntArrayArray5611[0].length > 0 ? 0 : -1;
        }
        for (int i = 0; i < definition.aByteArray5644.length
                && i < definition.anIntArrayArray5611.length; i++) {
            if ((definition.aByteArray5644[i] & 0xff) == objectType
                    && definition.anIntArrayArray5611[i] != null
                    && definition.anIntArrayArray5611[i].length > 0) {
                return i;
            }
        }
        return -1;
    }

    private Source resolveSource(int objectId, int objectType) {
        Class613 region = client.aClass613_8605;
        if (region == null) return null;
        Class639_Sub16 definitions = region.method7288(0);
        if (definitions == null) return null;
        try {
            ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(
                    objectId, -1356282071);
            return loadSource(definition, objectId, objectType);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Class159 replacementRaw(PartState state, Component target) {
        Source replacement = resolveSource(state.replacementObjectId,
                state.replacementObjectType);
        if (replacement == null) return null;
        Class159 raw = replacement.decode();
        if (raw == null || raw.anInt1791 <= 0) return null;
        autoFitReplacement(raw, target);
        transformAllAround(raw, target.centerX, target.centerY, target.centerZ, state);
        return raw;
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

    private static void populateComponentBounds(Class159 raw, Component[] components) {
        for (Component component : components) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int vertex : component.vertices) {
                int x = raw.anIntArray1782[vertex];
                int y = raw.anIntArray1777[vertex];
                int z = raw.anIntArray1797[vertex];
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }
            component.centerX = (minX + maxX) / 2;
            component.centerY = (minY + maxY) / 2;
            component.centerZ = (minZ + maxZ) / 2;
            component.sizeX = Math.max(1, maxX - minX);
            component.sizeY = Math.max(1, maxY - minY);
            component.sizeZ = Math.max(1, maxZ - minZ);
            int[] sorted = { component.sizeX, component.sizeY, component.sizeZ };
            Arrays.sort(sorted);
            component.signature = component.faces.length + ":" + component.vertices.length
                    + ":" + sorted[0] + ":" + sorted[1] + ":" + sorted[2];
        }
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
        transformVerticesAround(raw, component.vertices, cx, cy, cz, state);
    }

    private static void transformAllAround(Class159 raw, int cx, int cy, int cz,
            PartState state) {
        int[] vertices = new int[raw.anInt1791];
        for (int i = 0; i < vertices.length; i++) vertices[i] = i;
        transformVerticesAround(raw, vertices, cx, cy, cz, state);
    }

    private static void transformVerticesAround(Class159 raw, int[] vertices,
            long cx, long cy, long cz, PartState state) {
        double radians = Math.toRadians(state.yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int vertex : vertices) {
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

    private static void autoFitReplacement(Class159 raw, Component target) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < raw.anInt1791; i++) {
            int x = raw.anIntArray1782[i], y = raw.anIntArray1777[i], z = raw.anIntArray1797[i];
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        int cx = (minX + maxX) / 2;
        int cy = (minY + maxY) / 2;
        int cz = (minZ + maxZ) / 2;
        int sx = Math.max(1, maxX - minX);
        int sy = Math.max(1, maxY - minY);
        int sz = Math.max(1, maxZ - minZ);

        boolean rotateXZ = (target.sizeX >= target.sizeZ) != (sx >= sz);
        int sourceLongest = Math.max(sy, Math.max(sx, sz));
        int targetLongest = Math.max(target.sizeY, Math.max(target.sizeX, target.sizeZ));
        double scale = targetLongest / (double) Math.max(1, sourceLongest);

        for (int i = 0; i < raw.anInt1791; i++) {
            double x = raw.anIntArray1782[i] - cx;
            double y = raw.anIntArray1777[i] - cy;
            double z = raw.anIntArray1797[i] - cz;
            if (rotateXZ) {
                double swap = x;
                x = z;
                z = -swap;
            }
            raw.anIntArray1782[i] = (int) Math.round(target.centerX + x * scale);
            raw.anIntArray1777[i] = (int) Math.round(target.centerY + y * scale);
            raw.anIntArray1797[i] = (int) Math.round(target.centerZ + z * scale);
        }
    }

    private static Class159 componentOnlyRaw(Class159 sourceRaw,
            Component component, PartState state) {
        if (sourceRaw == null || component == null) return null;
        int[] map = new int[sourceRaw.anInt1791];
        Arrays.fill(map, -1);
        Class159 raw = new Class159(component.vertices.length, component.faces.length, 0);
        raw.anInt1773 = sourceRaw.anInt1773;
        raw.anInt1791 = component.vertices.length;
        // OpenGLModel sizes its per-vertex face-reference table from anInt1775.
        // Decoded Class159 models leave this as the highest referenced vertex + 1.
        // A component-only remap uses every copied vertex, so the used range is
        // exactly the compact vertex count.
        raw.anInt1775 = component.vertices.length;
        raw.anInt1778 = component.faces.length;

        for (int i = 0; i < component.vertices.length; i++) {
            int old = component.vertices[i];
            map[old] = i;
            raw.anIntArray1782[i] = sourceRaw.anIntArray1782[old];
            raw.anIntArray1777[i] = sourceRaw.anIntArray1777[old];
            raw.anIntArray1797[i] = sourceRaw.anIntArray1797[old];
            if (sourceRaw.anIntArray1813 != null && old < sourceRaw.anIntArray1813.length)
                raw.anIntArray1813[i] = sourceRaw.anIntArray1813[old];
        }

        for (int i = 0; i < component.faces.length; i++) {
            int face = component.faces[i];
            int a = map[sourceRaw.aShortArray1786[face] & 0xffff];
            int b = map[sourceRaw.aShortArray1787[face] & 0xffff];
            int d = map[sourceRaw.aShortArray1789[face] & 0xffff];
            if (a < 0 || b < 0 || d < 0
                    || a >= raw.anInt1775 || b >= raw.anInt1775 || d >= raw.anInt1775) {
                return null;
            }
            raw.aShortArray1786[i] = (short) a;
            raw.aShortArray1787[i] = (short) b;
            raw.aShortArray1789[i] = (short) d;
            raw.faceColours[i] = sourceRaw.faceColours == null ? 0 : sourceRaw.faceColours[face];
            raw.faceAlpha[i] = sourceRaw.faceAlpha == null ? 0 : sourceRaw.faceAlpha[face];
            raw.faceTextures[i] = -1;
            raw.faceTextureIndexes[i] = -1;
            raw.aByteArray1792[i] = sourceRaw.aByteArray1792 == null ? 0 : sourceRaw.aByteArray1792[face];
            raw.aByteArray1799[i] = sourceRaw.aByteArray1799 == null ? 0 : sourceRaw.aByteArray1799[face];
            raw.anIntArray1780[i] = sourceRaw.anIntArray1780 == null ? 0 : sourceRaw.anIntArray1780[face];
        }

        PartState local = state.copy();
        Component localComponent = new Component(sequence(component.faces.length),
                sequence(component.vertices.length));
        transformVertices(raw, localComponent, local);
        return raw;
    }

    private static int[] sequence(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = i;
        return values;
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

    private static void highlightAllFaces(Class159 raw) {
        ensureFaceAlpha(raw);
        for (int face = 0; face < raw.anInt1778; face++) {
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
        return stateAt(selected);
    }

    private PartState stateAt(int index) {
        if (index < 0) return null;
        if (index < originals.size()) return originals.get(index);
        int copy = index - originals.size();
        return copy >= 0 && copy < duplicates.size() ? duplicates.get(copy) : null;
    }

    private void pushUndo() {
        if (undo.size() >= MAX_UNDO) undo.removeFirst();
        undo.addLast(new Snapshot(originals, duplicates, selected, isolate));
    }

    private void markGeometryChanged() {
        revision++;
        geometryRevision++;
    }

    private static String suffix(PartState state) {
        StringBuilder suffix = new StringBuilder();
        if (state.deleted) suffix.append("  [deleted]");
        else if (state.hidden) suffix.append("  [hidden]");
        if (state.replacementObjectId >= 0)
            suffix.append("  [replace #").append(state.replacementObjectId).append(']');
        return suffix.toString();
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
                .append(", \"replacementObjectId\": ").append(state.replacementObjectId)
                .append(", \"replacementObjectType\": ").append(state.replacementObjectType)
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
        state.replacementObjectId = readInt(body, "replacementObjectId", -1);
        state.replacementObjectType = readInt(body, "replacementObjectType", 10);
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

    private static int[] sanitizeTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        return new int[] {
                clamp(sx, 10, 400), clamp(sy, 10, 400), clamp(sz, 10, 400),
                clamp(mx, -4096, 4096), clamp(my, -4096, 4096),
                clamp(mz, -4096, 4096), normalizeDegrees(yaw)
        };
    }

    private static boolean sameTransform(PartState state, int[] values) {
        return state.scaleX == values[0] && state.scaleY == values[1]
                && state.scaleZ == values[2] && state.moveX == values[3]
                && state.moveY == values[4] && state.moveZ == values[5]
                && state.yaw == values[6];
    }

    private static void applyTransform(PartState state, int[] values) {
        state.scaleX = values[0]; state.scaleY = values[1]; state.scaleZ = values[2];
        state.moveX = values[3]; state.moveY = values[4]; state.moveZ = values[5];
        state.yaw = values[6];
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

    static final class PickRaw {
        final int partIndex;
        final int materialObjectId;
        final int materialObjectType;
        final Class159 raw;
        PickRaw(int partIndex, int materialObjectId, int materialObjectType, Class159 raw) {
            this.partIndex = partIndex;
            this.materialObjectId = materialObjectId;
            this.materialObjectType = materialObjectType;
            this.raw = raw;
        }
    }

    static final class ReplacementRaw {
        final int partIndex;
        final int objectId;
        final int objectType;
        final Class159 raw;
        ReplacementRaw(int partIndex, int objectId, int objectType, Class159 raw) {
            this.partIndex = partIndex;
            this.objectId = objectId;
            this.objectType = objectType;
            this.raw = raw;
        }
    }

    private static final class Builder {
        final List<Integer> faces = new ArrayList<Integer>();
        final LinkedHashSet<Integer> vertices = new LinkedHashSet<Integer>();
    }

    private static final class Component {
        final int[] faces;
        final int[] vertices;
        int centerX, centerY, centerZ;
        int sizeX, sizeY, sizeZ;
        String signature = "";
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
        int replacementObjectId = -1;
        int replacementObjectType = 10;

        PartState(int sourcePart) { this.sourcePart = sourcePart; }

        PartState copy() {
            PartState copy = new PartState(sourcePart);
            copy.scaleX = scaleX; copy.scaleY = scaleY; copy.scaleZ = scaleZ;
            copy.moveX = moveX; copy.moveY = moveY; copy.moveZ = moveZ; copy.yaw = yaw;
            copy.hidden = hidden; copy.deleted = deleted;
            copy.replacementObjectId = replacementObjectId;
            copy.replacementObjectType = replacementObjectType;
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
