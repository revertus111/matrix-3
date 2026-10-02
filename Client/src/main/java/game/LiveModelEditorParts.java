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

    enum ConveyorRole {
        UNASSIGNED,
        BELT_SURFACE,
        START_CAP,
        END_CAP,
        FIXED_DETAIL,
        REPEAT_DETAIL,
        SUPPORT,
        SCALE_POSITION,
        IGNORE;

        static ConveyorRole fromName(String value) {
            if (value == null) return UNASSIGNED;
            try {
                return ConveyorRole.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return UNASSIGNED;
            }
        }
    }

    static final class ConveyorRecipePart {
        final int sourcePart;
        final ConveyorRole role;
        final int scaleX, scaleY, scaleZ;
        final int moveX, moveY, moveZ, yaw;

        ConveyorRecipePart(int sourcePart, ConveyorRole role,
                int scaleX, int scaleY, int scaleZ,
                int moveX, int moveY, int moveZ, int yaw) {
            this.sourcePart = sourcePart;
            this.role = role;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.moveX = moveX;
            this.moveY = moveY;
            this.moveZ = moveZ;
            this.yaw = yaw;
        }
    }

    private static final int MAX_UNDO = 64;
    private static final short HIGHLIGHT_COLOUR = (short) 0xffff;

    private Source source;
    private final Map<String, Source> auxiliarySources = new LinkedHashMap<String, Source>();
    private final List<PartState> originals = new ArrayList<PartState>();
    private final List<PartState> duplicates = new ArrayList<PartState>();
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<Snapshot>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<Snapshot>();
    private final LinkedHashSet<Integer> selection = new LinkedHashSet<Integer>();
    private final Map<Integer, int[]> gestureStarts = new LinkedHashMap<Integer, int[]>();
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
        auxiliarySources.clear();
        originals.clear();
        duplicates.clear();
        undo.clear();
        redo.clear();
        selection.clear();
        gestureStarts.clear();
        selected = -1;
        hovered = -1;
        isolate = false;
        gestureActive = false;
        revision++;
        geometryRevision++;
        if (loaded == null) return 0;
        for (int i = 0; i < loaded.components.length; i++) {
            PartState state = new PartState(i);
            pinSourceRecipe(state, loaded);
            originals.add(state);
        }
        return loaded.components.length;
    }

    synchronized void clear() {
        source = null;
        auxiliarySources.clear();
        originals.clear();
        duplicates.clear();
        undo.clear();
        redo.clear();
        selection.clear();
        gestureStarts.clear();
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
    synchronized int[] getSelectedIndices() { return selectionArray(); }
    synchronized int getSelectedCount() { return selection.size(); }
    synchronized boolean isSelected(int index) { return selection.contains(Integer.valueOf(index)); }
    synchronized int getHovered() { return hovered; }
    synchronized boolean isIsolate() { return isolate; }

    synchronized String[] getConveyorRoleOptions() {
        ConveyorRole[] roles = ConveyorRole.values();
        String[] values = new String[roles.length];
        for (int i = 0; i < roles.length; i++) values[i] = roles[i].name();
        return values;
    }

    synchronized String getSelectedConveyorRoleName() {
        if (selection.isEmpty()) return ConveyorRole.UNASSIGNED.name();
        ConveyorRole shared = null;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null) continue;
            if (shared == null) shared = state.conveyorRole;
            else if (shared != state.conveyorRole) return "MIXED";
        }
        return shared == null ? ConveyorRole.UNASSIGNED.name() : shared.name();
    }

    synchronized boolean setSelectedConveyorRole(String roleName) {
        if (selection.isEmpty()) return false;
        ConveyorRole role = ConveyorRole.fromName(roleName);
        boolean changed = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && state.conveyorRole != role) {
                changed = true;
                break;
            }
        }
        if (!changed) return false;
        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null) state.conveyorRole = role;
        }
        revision++;
        return true;
    }

    synchronized ConveyorRecipePart[] buildConveyorRecipe() {
        if (source == null) return new ConveyorRecipePart[0];
        List<ConveyorRecipePart> recipe = new ArrayList<ConveyorRecipePart>();
        for (PartState state : originals) {
            if (state.deleted || state.hidden || state.replacementObjectId >= 0) {
                continue;
            }

            ConveyorRole role = state.conveyorRole;
            if (role == ConveyorRole.UNASSIGNED) {
                /*
                 * Backward-compatible project-v4 behavior: the user's existing
                 * authored assembly is exactly the set of non-default edited
                 * source components. Preserve those as FIXED_DETAIL until the
                 * author explicitly assigns a procedural role. Untouched
                 * sawmill components remain excluded.
                 */
                boolean authored = state.scaleX != 100 || state.scaleY != 100
                        || state.scaleZ != 100 || state.moveX != 0
                        || state.moveY != 0 || state.moveZ != 0 || state.yaw != 0;
                if (!authored) continue;
                role = ConveyorRole.FIXED_DETAIL;
            }

            recipe.add(new ConveyorRecipePart(state.sourcePart, role,
                    state.scaleX, state.scaleY, state.scaleZ,
                    state.moveX, state.moveY, state.moveZ, state.yaw));
        }
        return recipe.toArray(new ConveyorRecipePart[recipe.size()]);
    }

    synchronized int getFaceCount(int index) {
        PartState state = stateAt(index);
        Component component = componentFor(state);
        return component == null ? Integer.MAX_VALUE : component.faces.length;
    }

    /**
     * Read-only reverse-engineering trace for source-object animation.
     *
     * Compares the decoded Class159 skin/face groups and textured faces against
     * the transform groups referenced by the real AnimationDefinition frames.
     * This does not mutate editor state or cache geometry.
     */
    synchronized String sourceAnimationDiagnostics(ObjectDefinitions definition,
            AnimationDefinition animation) {
        StringBuilder out = new StringBuilder(4096);
        out.append("=== LIVE MODEL SOURCE ANIM TRACE ===\n");
        out.append("object=").append(source == null ? -1 : source.objectId)
                .append(" type=").append(source == null ? -1 : source.objectType)
                .append(" models=").append(source == null
                        ? "[]" : Arrays.toString(source.modelIds)).append('\n');

        int[] animationIds = definition == null ? null : definition.method6053((byte) 0);
        out.append("objectAnimationIds=")
                .append(animationIds == null ? "[]" : Arrays.toString(animationIds))
                .append('\n');

        if (source == null) {
            out.append("classification=NO_SOURCE\n");
            return out.toString();
        }

        Class159 raw = source.decode();
        if (raw == null) {
            out.append("classification=SOURCE_DECODE_FAILED\n");
            return out.toString();
        }

        LinkedHashSet<Integer> sourceVertexGroups = new LinkedHashSet<Integer>();
        int skinnedVertices = 0;
        if (raw.anIntArray1813 != null) {
            for (int i = 0; i < raw.anInt1791 && i < raw.anIntArray1813.length; i++) {
                int group = raw.anIntArray1813[i];
                if (group >= 0) {
                    skinnedVertices++;
                    sourceVertexGroups.add(Integer.valueOf(group));
                }
            }
        }

        LinkedHashSet<Integer> sourceFaceGroups = new LinkedHashSet<Integer>();
        if (raw.anIntArray1780 != null) {
            for (int i = 0; i < raw.anInt1778 && i < raw.anIntArray1780.length; i++) {
                int group = raw.anIntArray1780[i];
                if (group >= 0) sourceFaceGroups.add(Integer.valueOf(group));
            }
        }

        LinkedHashSet<Integer> sourceTextures = new LinkedHashSet<Integer>();
        int texturedFaces = 0;
        if (raw.faceTextures != null) {
            for (int i = 0; i < raw.anInt1778 && i < raw.faceTextures.length; i++) {
                int texture = raw.faceTextures[i];
                if (texture >= 0) {
                    texturedFaces++;
                    sourceTextures.add(Integer.valueOf(texture));
                }
            }
        }

        out.append("raw vertices=").append(raw.anInt1791)
                .append(" faces=").append(raw.anInt1778)
                .append(" skinnedVertices=").append(skinnedVertices)
                .append(" vertexSkinGroups=").append(sourceVertexGroups)
                .append(" faceGroups=").append(sourceFaceGroups)
                .append(" texturedFaces=").append(texturedFaces)
                .append(" textures=").append(sourceTextures)
                .append('\n');

        LinkedHashSet<Integer> sequenceSpatialGroups = new LinkedHashSet<Integer>();
        LinkedHashSet<Integer> sequenceOtherGroups = new LinkedHashSet<Integer>();
        Map<Integer, Integer> transformTypeCounts = new LinkedHashMap<Integer, Integer>();
        int sequenceFrames = animation == null || animation.anIntArray1544 == null
                ? 0 : animation.anIntArray1544.length;
        int loadedFrames = 0;

        if (animation != null && animation.anIntArray1544 != null
                && animation.aClass92_1559 != null) {
            for (int packedFrame : animation.anIntArray1544) {
                Class572_Sub12_Sub7 frameSet =
                        animation.aClass92_1559.method1522(
                                packedFrame >>> 16, -1457820512);
                if (frameSet == null || frameSet.aClass99Array11373 == null) continue;
                int frameIndex = packedFrame & 0xffff;
                if (frameIndex < 0 || frameIndex >= frameSet.aClass99Array11373.length) continue;
                Class99 frame = frameSet.aClass99Array11373[frameIndex];
                if (frame == null || frame.aClass572_Sub23_1269 == null) continue;
                loadedFrames++;

                Class572_Sub23 skeleton = frame.aClass572_Sub23_1269;
                for (int i = 0; i < frame.anInt1271; i++) {
                    int slot = frame.aShortArray1272[i] & 0xffff;
                    if (slot < 0 || slot >= skeleton.anIntArray9196.length
                            || slot >= skeleton.anIntArrayArray9197.length) continue;
                    int type = skeleton.anIntArray9196[slot];
                    Integer previous = transformTypeCounts.get(Integer.valueOf(type));
                    transformTypeCounts.put(Integer.valueOf(type),
                            Integer.valueOf(previous == null ? 1 : previous.intValue() + 1));
                    int[] groups = skeleton.anIntArrayArray9197[slot];
                    if (groups == null) continue;
                    LinkedHashSet<Integer> target = type >= 1 && type <= 3
                            ? sequenceSpatialGroups : sequenceOtherGroups;
                    for (int group : groups) {
                        if (group >= 0) target.add(Integer.valueOf(group));
                    }
                }
            }
        }

        out.append("sequence frames=").append(sequenceFrames)
                .append(" loadedFrames=").append(loadedFrames)
                .append(" transformTypes=").append(transformTypeCounts)
                .append(" spatialGroups=").append(sequenceSpatialGroups)
                .append(" otherGroups=").append(sequenceOtherGroups)
                .append('\n');

        boolean anySpatialOverlap = false;
        boolean anyOtherOverlap = false;
        for (int part = 0; part < source.components.length; part++) {
            Component component = source.components[part];
            LinkedHashSet<Integer> vertexGroups = new LinkedHashSet<Integer>();
            if (raw.anIntArray1813 != null) {
                for (int vertex : component.vertices) {
                    if (vertex < 0 || vertex >= raw.anIntArray1813.length) continue;
                    int group = raw.anIntArray1813[vertex];
                    if (group >= 0) vertexGroups.add(Integer.valueOf(group));
                }
            }

            LinkedHashSet<Integer> faceGroups = new LinkedHashSet<Integer>();
            LinkedHashSet<Integer> textures = new LinkedHashSet<Integer>();
            int partTexturedFaces = 0;
            for (int face : component.faces) {
                if (raw.anIntArray1780 != null
                        && face >= 0 && face < raw.anIntArray1780.length) {
                    int group = raw.anIntArray1780[face];
                    if (group >= 0) faceGroups.add(Integer.valueOf(group));
                }
                if (raw.faceTextures != null
                        && face >= 0 && face < raw.faceTextures.length) {
                    int texture = raw.faceTextures[face];
                    if (texture >= 0) {
                        partTexturedFaces++;
                        textures.add(Integer.valueOf(texture));
                    }
                }
            }

            LinkedHashSet<Integer> spatialOverlap =
                    intersection(vertexGroups, sequenceSpatialGroups);
            LinkedHashSet<Integer> otherVertexOverlap =
                    intersection(vertexGroups, sequenceOtherGroups);
            LinkedHashSet<Integer> otherFaceOverlap =
                    intersection(faceGroups, sequenceOtherGroups);
            if (!spatialOverlap.isEmpty()) anySpatialOverlap = true;
            if (!otherVertexOverlap.isEmpty() || !otherFaceOverlap.isEmpty())
                anyOtherOverlap = true;

            out.append("part ").append(part)
                    .append(" verts=").append(component.vertices.length)
                    .append(" faces=").append(component.faces.length)
                    .append(" vertexGroups=").append(vertexGroups)
                    .append(" faceGroups=").append(faceGroups)
                    .append(" texturedFaces=").append(partTexturedFaces)
                    .append(" textures=").append(textures)
                    .append(" spatialOverlap=").append(spatialOverlap)
                    .append(" otherVertexOverlap=").append(otherVertexOverlap)
                    .append(" otherFaceOverlap=").append(otherFaceOverlap)
                    .append('\n');
        }

        String classification;
        if (animation == null) {
            classification = texturedFaces > 0
                    ? "NO_SEQUENCE_DEFINITION + TEXTURED_GEOMETRY_PRESENT"
                    : "NO_SEQUENCE_DEFINITION";
        } else if (sequenceFrames > 0 && loadedFrames == 0) {
            classification = "SEQUENCE_FRAME_DATA_WAIT";
        } else if (anySpatialOverlap) {
            classification = "SEQUENCE_SPATIAL_GROUP_OVERLAP";
        } else if (anyOtherOverlap) {
            classification = "SEQUENCE_NONSPATIAL_GROUP_OVERLAP";
        } else if (texturedFaces > 0) {
            classification = "NO_SEQUENCE_GROUP_OVERLAP + TEXTURED_GEOMETRY_PRESENT";
        } else {
            classification = "NO_SEQUENCE_GROUP_OVERLAP";
        }
        out.append("classification=").append(classification).append('\n');
        return out.toString();
    }

    private static LinkedHashSet<Integer> intersection(
            LinkedHashSet<Integer> a, LinkedHashSet<Integer> b) {
        LinkedHashSet<Integer> out = new LinkedHashSet<Integer>();
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return out;
        for (Integer value : a) {
            if (b.contains(value)) out.add(value);
        }
        return out;
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
            Component component = componentFor(state);
            labels[i] = component == null ? "Part " + i + "  [source unavailable]"
                    : "Part " + i + "  (" + component.faces.length + " faces, "
                            + component.vertices.length + " verts)" + suffix(state);
        }
        for (int i = 0; i < duplicates.size(); i++) {
            PartState state = duplicates.get(i);
            Component component = componentFor(state);
            Source owner = sourceFor(state);
            String prefix = state.sourceObjectId >= 0
                    ? "Asset #" + state.sourceObjectId + " Part " + state.sourcePart
                    : "Copy " + i + " -> Part " + state.sourcePart;
            labels[originals.size() + i] = component == null
                    ? prefix + "  [source unavailable]" + suffix(state)
                    : prefix + "  (" + component.faces.length + " faces, "
                            + component.vertices.length + " verts)"
                            + (owner != null && owner != source ? " [imported]" : "")
                            + suffix(state);
        }
        return labels;
    }

    synchronized int[] getSelectedTransform() {
        PartState state = selectedState();
        if (state == null) return new int[] { 100, 100, 100, 0, 0, 0, 0 };
        return new int[] { state.scaleX, state.scaleY, state.scaleZ,
                state.moveX, state.moveY, state.moveZ, state.yaw };
    }

    /**
     * Shared pivot for the current Part/Multi selection in source model space.
     *
     * Per-part scale/yaw rotate around the component center, so the edited
     * center is simply the detected source centroid plus that part's move delta.
     * Multi uses the arithmetic mean of those edited centers.
     */
    synchronized int[] getSelectionPivot() {
        if (source == null || selection.isEmpty()) return null;
        long x = 0L, y = 0L, z = 0L;
        int count = 0;
        for (Integer key : selection) {
            PartState state = stateAt(key.intValue());
            if (state == null || state.deleted) continue;
            Component component = componentFor(state);
            if (component == null) continue;
            x += component.centerX + state.moveX;
            y += component.centerY + state.moveY;
            z += component.centerZ + state.moveZ;
            count++;
        }
        if (count == 0) return null;
        return new int[] {
                (int) Math.round(x / (double) count),
                (int) Math.round(y / (double) count),
                (int) Math.round(z / (double) count)
        };
    }

    synchronized boolean select(int index) {
        if (index < 0 || index >= getPartCount()) return clearSelection();
        LinkedHashSet<Integer> next = new LinkedHashSet<Integer>();
        addIndexWithGroup(next, index);
        boolean changed = !selection.equals(next) || selected != index;
        selection.clear();
        selection.addAll(next);
        selected = index;
        if (changed) revision++;
        return true;
    }

    synchronized boolean setSelection(int[] indices) {
        LinkedHashSet<Integer> next = new LinkedHashSet<Integer>();
        int nextPrimary = -1;
        if (indices != null) {
            for (int index : indices) {
                if (index >= 0 && index < getPartCount()) {
                    addIndexWithGroup(next, index);
                    nextPrimary = index;
                }
            }
        }
        boolean changed = !selection.equals(next) || selected != nextPrimary;
        selection.clear();
        selection.addAll(next);
        selected = nextPrimary;
        if (changed) revision++;
        return changed;
    }

    synchronized boolean addSelection(int index) {
        if (index < 0 || index >= getPartCount()) return false;
        int before = selection.size();
        addIndexWithGroup(selection, index);
        boolean changed = selection.size() != before;
        if (selected != index) {
            selected = index;
            changed = true;
        }
        if (changed) revision++;
        return true;
    }

    synchronized boolean toggleSelection(int index) {
        if (index < 0 || index >= getPartCount()) return false;
        PartState state = stateAt(index);
        if (state == null) return false;
        LinkedHashSet<Integer> affected = new LinkedHashSet<Integer>();
        addIndexWithGroup(affected, index);
        boolean remove = selection.contains(Integer.valueOf(index));
        if (remove) {
            selection.removeAll(affected);
            if (selected == index || !selection.contains(Integer.valueOf(selected))) {
                selected = lastSelectionIndex();
            }
        } else {
            selection.addAll(affected);
            selected = index;
        }
        revision++;
        return true;
    }

    synchronized boolean selectAll() {
        LinkedHashSet<Integer> next = new LinkedHashSet<Integer>();
        for (int i = 0; i < getPartCount(); i++) {
            PartState state = stateAt(i);
            if (state != null && !state.deleted) next.add(Integer.valueOf(i));
        }
        int nextPrimary = next.isEmpty() ? -1 : next.iterator().next().intValue();
        boolean changed = !selection.equals(next) || selected != nextPrimary;
        selection.clear();
        selection.addAll(next);
        selected = nextPrimary;
        if (changed) revision++;
        return changed;
    }

    synchronized boolean clearSelection() {
        if (selection.isEmpty() && selected == -1) return false;
        selection.clear();
        selected = -1;
        revision++;
        return true;
    }

    synchronized boolean groupSelected() {
        int editable = 0;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && !state.deleted) editable++;
        }
        if (editable < 2) return false;

        int groupId = nextGroupId();
        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && !state.deleted) state.groupId = groupId;
        }
        revision++;
        return true;
    }

    synchronized boolean ungroupSelected() {
        boolean changed = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && state.groupId > 0) {
                changed = true;
                break;
            }
        }
        if (!changed) return false;

        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null) state.groupId = 0;
        }
        revision++;
        return true;
    }

    synchronized int getSelectedGroupId() {
        return isSelectionSingleGroup() ? selectedState().groupId : 0;
    }

    synchronized boolean setSelectedTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        return setSelectedTransform(sx, sy, sz, mx, my, mz, yaw, false);
    }

    synchronized boolean setSelectedTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw, boolean groupScale) {
        PartState primary = selectedState();
        if (primary == null || primary.deleted || selection.isEmpty()) return false;
        int[] target = sanitizeTransform(sx, sy, sz, mx, my, mz, yaw);
        int[] current = transformOf(primary);
        if (sameTransform(primary, target)) return false;
        pushUndo();
        boolean changed = applySelectionDelta(
                transformDelta(current, target), null, groupScale);
        if (changed) markGeometryChanged();
        return changed;
    }

    synchronized boolean resetSelectedTransforms() {
        if (selection.isEmpty()) return false;
        boolean changed = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && !state.deleted
                    && (state.scaleX != 100 || state.scaleY != 100 || state.scaleZ != 100
                    || state.moveX != 0 || state.moveY != 0 || state.moveZ != 0 || state.yaw != 0)) {
                changed = true;
                break;
            }
        }
        if (!changed) return false;
        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted) continue;
            state.scaleX = state.scaleY = state.scaleZ = 100;
            state.moveX = state.moveY = state.moveZ = state.yaw = 0;
        }
        markGeometryChanged();
        return true;
    }

    synchronized boolean beginGesture() {
        PartState primary = selectedState();
        if (selection.isEmpty() || primary == null || primary.deleted) return false;
        if (!gestureActive) {
            pushUndo(false);
            gestureStarts.clear();
            for (Integer index : selection) {
                PartState state = stateAt(index.intValue());
                if (state != null && !state.deleted) gestureStarts.put(index, transformOf(state));
            }
            gestureActive = !gestureStarts.isEmpty();
        }
        return gestureActive;
    }

    synchronized boolean updateGestureTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        return updateGestureTransform(sx, sy, sz, mx, my, mz, yaw, false);
    }

    synchronized boolean updateGestureTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw, boolean groupScale) {
        if (!gestureActive) return false;
        int[] primaryStart = gestureStarts.get(Integer.valueOf(selected));
        if (primaryStart == null) return false;
        int[] target = sanitizeTransform(sx, sy, sz, mx, my, mz, yaw);
        boolean changed = applySelectionDelta(
                transformDelta(primaryStart, target), gestureStarts, groupScale);
        if (changed) {
            redo.clear();
            markGeometryChanged();
        }
        return changed;
    }

    /**
     * Translate the current Part/Multi selection so the requested X/Z bounds
     * anchor lands on the nearest logical tile-grid intersection.
     *
     * anchorX/anchorZ use -1=min edge, 0=center, +1=max edge.
     * Geometry size/yaw are untouched; every selected part receives the same
     * move delta so relative spacing is preserved and the edit stays undoable.
     */
    synchronized boolean snapSelectionToGrid(int gridUnits, int anchorX, int anchorZ) {
        if (source == null || selection.isEmpty() || gridUnits <= 0) return false;
        double[] extents = selectionExtents();
        if (extents == null) return false;

        int ax = clamp(anchorX, -1, 1);
        int az = clamp(anchorZ, -1, 1);
        double anchorValueX = ax < 0 ? extents[0]
                : ax > 0 ? extents[3] : (extents[0] + extents[3]) * 0.5;
        double anchorValueZ = az < 0 ? extents[2]
                : az > 0 ? extents[5] : (extents[2] + extents[5]) * 0.5;

        double snappedX = Math.round(anchorValueX / gridUnits) * (double) gridUnits;
        double snappedZ = Math.round(anchorValueZ / gridUnits) * (double) gridUnits;
        int deltaX = (int) Math.round(snappedX - anchorValueX);
        int deltaZ = (int) Math.round(snappedZ - anchorValueZ);
        if (deltaX == 0 && deltaZ == 0) return false;

        pushUndo();
        boolean changed = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted) continue;
            int[] values = sanitizeTransform(
                    state.scaleX, state.scaleY, state.scaleZ,
                    state.moveX + deltaX, state.moveY, state.moveZ + deltaZ,
                    state.yaw);
            if (!sameTransform(state, values)) {
                applyTransform(state, values);
                changed = true;
            }
        }
        if (changed) markGeometryChanged();
        return changed;
    }

    /**
     * Current transformed selection dimensions in source-model units:
     * {sizeX, sizeY, sizeZ}. Hidden parts still count; deleted parts do not.
     */
    synchronized int[] getSelectionBounds() {
        double[] extents = selectionExtents();
        if (extents == null) return null;
        return new int[] {
                Math.max(1, (int) Math.ceil(extents[3] - extents[0])),
                Math.max(1, (int) Math.ceil(extents[4] - extents[1])),
                Math.max(1, (int) Math.ceil(extents[5] - extents[2]))
        };
    }

    /**
     * Transformed selection extents in source-model coordinates:
     * {minX, minY, minZ, maxX, maxY, maxZ}.
     *
     * This mirrors transformVertices(...): scaling retains the legacy
     * average-vertex centroid while yaw uses the cached component bounds center.
     */
    private double[] selectionExtents() {
        if (source == null || selection.isEmpty()) return null;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean found = false;

        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted || !validSourcePart(state)) continue;
            Source owner = sourceFor(state);
            Component component = componentFor(state);
            Class159 raw = owner == null ? null : owner.decode();
            if (component == null || raw == null || component.vertices.length == 0) continue;

            double scaleCx = 0.0, scaleCy = 0.0, scaleCz = 0.0;
            boolean validVertices = true;
            for (int vertex : component.vertices) {
                if (!hasVertexCoordinates(raw, vertex)) {
                    validVertices = false;
                    break;
                }
                scaleCx += raw.anIntArray1782[vertex];
                scaleCy += raw.anIntArray1777[vertex];
                scaleCz += raw.anIntArray1797[vertex];
            }
            if (!validVertices) continue;
            scaleCx /= component.vertices.length;
            scaleCy /= component.vertices.length;
            scaleCz /= component.vertices.length;

            double radians = Math.toRadians(state.yaw);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            for (int vertex : component.vertices) {
                double scaledX = scaleCx
                        + (raw.anIntArray1782[vertex] - scaleCx) * state.scaleX / 100.0;
                double scaledY = scaleCy
                        + (raw.anIntArray1777[vertex] - scaleCy) * state.scaleY / 100.0;
                double scaledZ = scaleCz
                        + (raw.anIntArray1797[vertex] - scaleCz) * state.scaleZ / 100.0;

                double x = scaledX - component.centerX;
                double z = scaledZ - component.centerZ;
                double rx = x * cos + z * sin;
                double rz = z * cos - x * sin;
                double tx = component.centerX + rx + state.moveX;
                double ty = scaledY + state.moveY;
                double tz = component.centerZ + rz + state.moveZ;

                if (tx < minX) minX = tx;
                if (tx > maxX) maxX = tx;
                if (ty < minY) minY = ty;
                if (ty > maxY) maxY = ty;
                if (tz < minZ) minZ = tz;
                if (tz > maxZ) maxZ = tz;
                found = true;
            }
        }

        return found ? new double[] {
                minX, minY, minZ, maxX, maxY, maxZ
        } : null;
    }

    synchronized void endGesture() {
        gestureActive = false;
        gestureStarts.clear();
    }

    synchronized boolean toggleSelectedHidden() {
        if (selection.isEmpty()) return false;
        boolean hide = false;
        boolean hasEditable = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted) continue;
            hasEditable = true;
            if (!state.hidden) hide = true;
        }
        if (!hasEditable) return false;
        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && !state.deleted) state.hidden = hide;
        }
        markGeometryChanged();
        return true;
    }

    synchronized boolean deleteSelected() {
        if (selection.isEmpty()) return false;
        boolean changed = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && !state.deleted) { changed = true; break; }
        }
        if (!changed) return false;
        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted) continue;
            state.deleted = true;
            state.hidden = true;
        }
        markGeometryChanged();
        return true;
    }

    synchronized boolean duplicateSelected() {
        if (selection.isEmpty()) return false;
        int[] sourceSelection = selectionArray();
        pushUndo();
        LinkedHashSet<Integer> created = new LinkedHashSet<Integer>();
        Map<Integer, Integer> copiedGroups = new LinkedHashMap<Integer, Integer>();
        for (int index : sourceSelection) {
            PartState state = stateAt(index);
            if (state == null || state.deleted) continue;
            PartState copy = state.copy();
            copy.hidden = false;
            copy.deleted = false;
            copy.moveX = clamp(copy.moveX + 128, -4096, 4096);
            if (copy.groupId > 0) {
                Integer mapped = copiedGroups.get(Integer.valueOf(copy.groupId));
                if (mapped == null) {
                    mapped = Integer.valueOf(nextGroupId());
                    copiedGroups.put(Integer.valueOf(copy.groupId), mapped);
                }
                copy.groupId = mapped.intValue();
            }
            duplicates.add(copy);
            created.add(Integer.valueOf(originals.size() + duplicates.size() - 1));
        }
        if (created.isEmpty()) return false;
        selection.clear();
        selection.addAll(created);
        selected = lastSelectionIndex();
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
        if (source == null || replacementObjectId < 0 || selection.isEmpty()) return false;
        PartState state = selectedState();
        if (state == null || state.deleted) return false;
        Component selectedComponent = componentFor(state);
        if (selectedComponent == null) return false;
        pushUndo();
        boolean changed = false;
        if (allMatching) {
            String signature = selectedComponent.signature;
            for (PartState candidate : originals) {
                if (!candidate.deleted && componentFor(candidate) != null
                        && signature.equals(componentFor(candidate).signature)) {
                    candidate.replacementObjectId = replacementObjectId;
                    candidate.replacementObjectType = replacementObjectType;
                    candidate.hidden = false;
                    changed = true;
                }
            }
            for (PartState candidate : duplicates) {
                Component candidateComponent = componentFor(candidate);
                if (!candidate.deleted && candidateComponent != null
                        && signature.equals(candidateComponent.signature)) {
                    candidate.replacementObjectId = replacementObjectId;
                    candidate.replacementObjectType = replacementObjectType;
                    candidate.hidden = false;
                    changed = true;
                }
            }
        } else {
            for (Integer index : selection) {
                PartState candidate = stateAt(index.intValue());
                if (candidate == null || candidate.deleted) continue;
                candidate.replacementObjectId = replacementObjectId;
                candidate.replacementObjectType = replacementObjectType;
                candidate.hidden = false;
                changed = true;
            }
        }
        if (changed) markGeometryChanged();
        return changed;
    }

    synchronized boolean clearSelectedReplacement() {
        if (selection.isEmpty()) return false;
        boolean changed = false;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && state.replacementObjectId >= 0) { changed = true; break; }
        }
        if (!changed) return false;
        pushUndo();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null) continue;
            state.replacementObjectId = -1;
            state.replacementObjectType = 10;
        }
        markGeometryChanged();
        return true;
    }

    synchronized boolean undo() {
        if (undo.isEmpty()) return false;
        pushHistory(redo, snapshotCurrent());
        restoreSnapshot(undo.removeLast());
        markGeometryChanged();
        return true;
    }

    synchronized boolean redo() {
        if (redo.isEmpty()) return false;
        pushHistory(undo, snapshotCurrent());
        restoreSnapshot(redo.removeLast());
        markGeometryChanged();
        return true;
    }

    synchronized Class159 buildMainRaw() {
        if (source == null) return null;
        Class159 raw = source.decode();
        if (raw == null) return null;
        ensureFaceAlpha(raw);
        for (int i = 0; i < originals.size(); i++) {
            PartState state = originals.get(i);
            if (!validSourcePart(state)) continue;
            Component component = componentFor(state);
            if (component == null) continue;
            boolean highlighted = isHighlighted(i);
            boolean visible = !state.hidden && !state.deleted
                    && state.replacementObjectId < 0
                    && (!isolate || selection.contains(Integer.valueOf(i)));
            if (!visible) {
                hideFaces(raw, component);
                continue;
            }
            transformVertices(raw, component, state);
            if (highlighted) highlightFaces(raw, component);
        }
        return raw;
    }

    synchronized List<Class159> buildDuplicateRaws() {
        if (source == null || duplicates.isEmpty()) return Collections.emptyList();
        List<Class159> raws = new ArrayList<Class159>();
        for (int i = 0; i < duplicates.size(); i++) {
            int combinedIndex = originals.size() + i;
            PartState state = duplicates.get(i);
            boolean highlighted = isHighlighted(combinedIndex);
            if (state.hidden || state.deleted || state.replacementObjectId >= 0
                    || (isolate && !selection.contains(Integer.valueOf(combinedIndex)))
                    || !validSourcePart(state)) continue;
            Source owner = sourceFor(state);
            Component component = componentFor(state);
            Class159 sourceRaw = owner == null ? null : owner.decode();
            if (sourceRaw == null || component == null) continue;
            Class159 raw = componentOnlyRaw(sourceRaw, component, state);
            if (raw == null) continue;
            if (highlighted) highlightAllFaces(raw);
            raws.add(raw);
        }
        return raws;
    }

    synchronized List<ReplacementRaw> buildReplacementRaws() {
        if (source == null) return Collections.emptyList();
        List<ReplacementRaw> raws = new ArrayList<ReplacementRaw>();
        int total = getPartCount();
        for (int index = 0; index < total; index++) {
            PartState state = stateAt(index);
            boolean highlighted = isHighlighted(index);
            if (state == null || state.hidden || state.deleted || state.replacementObjectId < 0
                    || (isolate && !selection.contains(Integer.valueOf(index)))
                    || !validSourcePart(state)) continue;
            Component target = componentFor(state);
            Class159 raw = target == null ? null : replacementRaw(state, target);
            if (raw == null) continue;
            if (highlighted) highlightAllFaces(raw);
            raws.add(new ReplacementRaw(index, state.replacementObjectId,
                    state.replacementObjectType, raw));
        }
        return raws;
    }

    synchronized List<PickRaw> buildPickRaws() {
        if (source == null) return Collections.emptyList();
        List<PickRaw> raws = new ArrayList<PickRaw>();
        int total = getPartCount();
        for (int index = 0; index < total; index++) {
            PartState state = stateAt(index);
            if (state == null || state.hidden || state.deleted
                    || (isolate && !selection.contains(Integer.valueOf(index)))
                    || !validSourcePart(state)) continue;
            if (state.replacementObjectId >= 0) {
                Component target = componentFor(state);
                Class159 replacement = target == null ? null : replacementRaw(state, target);
                if (replacement != null) {
                    raws.add(new PickRaw(index, state.replacementObjectId,
                            state.replacementObjectType, replacement));
                }
            } else {
                Source owner = sourceFor(state);
                Component sourceComponent = componentFor(state);
                Class159 ownerRaw = owner == null ? null : owner.decode();
                Class159 component = ownerRaw == null || sourceComponent == null
                        ? null : componentOnlyRaw(ownerRaw, sourceComponent, state);
                if (component != null) {
                    raws.add(new PickRaw(index,
                            owner == null ? source.objectId : owner.objectId,
                            owner == null ? source.objectType : owner.objectType,
                            component));
                }
            }
        }
        return raws;
    }

    synchronized String projectJsonFields() {
        StringBuilder out = new StringBuilder();
        out.append("  \"partSelected\": ").append(selected).append(",\n");
        out.append("  \"partSelection\": ").append(intArrayJson(selectionArray())).append(",\n");
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
        selection.clear();
        int[] loadedSelection = readIntArray(json, "partSelection");
        for (int index : loadedSelection) {
            if (index >= 0 && index < getPartCount()) addIndexWithGroup(selection, index);
        }
        if (selection.isEmpty() && selected >= 0) addIndexWithGroup(selection, selected);
        if (selected < 0 && !selection.isEmpty()) selected = lastSelectionIndex();
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
        byte[][] bytes = loadModelBytes(definition, modelIds);
        if (bytes == null) return null;
        Source candidate = new Source(objectId, objectType, modelIds, bytes,
                cloneShorts(definition.aShortArray5613),
                effectiveRecolorTargets(definition),
                cloneShorts(definition.aShortArray5618),
                cloneShorts(definition.aShortArray5617));
        Class159 raw = candidate.decode();
        if (raw == null || raw.anInt1791 <= 0 || raw.anInt1778 <= 0) return null;
        candidate.components = detectComponents(raw);
        populateComponentBounds(raw, candidate.components);
        return candidate;
    }

    private static byte[][] loadModelBytes(ObjectDefinitions definition, int[] modelIds) {
        if (definition == null || definition.aClass518_5608 == null
                || modelIds == null || modelIds.length == 0) return null;
        byte[][] bytes = new byte[modelIds.length][];
        for (int i = 0; i < modelIds.length; i++) {
            bytes[i] = definition.aClass518_5608.method6136(modelIds[i], 49248435);
            if (bytes[i] == null) return null;
        }
        return bytes;
    }

    private static short[] effectiveRecolorTargets(ObjectDefinitions definition) {
        if (definition == null || definition.aShortArray5613 == null) return null;
        short[] targets = new short[definition.aShortArray5613.length];
        for (int i = 0; i < targets.length; i++) {
            if (definition.aByteArray5615 != null && i < definition.aByteArray5615.length) {
                targets[i] = ObjectDefinitions.aShortArray5606[
                        definition.aByteArray5615[i] & 0xff];
            } else if (definition.aShortArray5621 != null
                    && i < definition.aShortArray5621.length) {
                targets[i] = definition.aShortArray5621[i];
            } else {
                targets[i] = definition.aShortArray5613[i];
            }
        }
        return targets;
    }

    private static short[] cloneShorts(short[] values) {
        return values == null ? null : values.clone();
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
        if (!autoFitReplacement(raw, target)) return null;
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
            int b = raw.aShortArray1787[face] & 0xffff;
            int c = raw.aShortArray1789[face] & 0xffff;
            if (a >= vertices || b >= vertices || c >= vertices) continue;
            int root = find(parent, a);
            Builder builder = byRoot.get(Integer.valueOf(root));
            if (builder == null) {
                builder = new Builder();
                byRoot.put(Integer.valueOf(root), builder);
            }
            builder.faces.add(Integer.valueOf(face));
            builder.vertices.add(Integer.valueOf(a));
            builder.vertices.add(Integer.valueOf(b));
            builder.vertices.add(Integer.valueOf(c));
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
            boolean found = false;
            for (int vertex : component.vertices) {
                if (!hasVertexCoordinates(raw, vertex)) continue;
                int x = raw.anIntArray1782[vertex];
                int y = raw.anIntArray1777[vertex];
                int z = raw.anIntArray1797[vertex];
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
                found = true;
            }
            if (!found) {
                component.centerX = component.centerY = component.centerZ = 0;
                component.sizeX = component.sizeY = component.sizeZ = 1;
            } else {
                component.centerX = (minX + maxX) / 2;
                component.centerY = (minY + maxY) / 2;
                component.centerZ = (minZ + maxZ) / 2;
                component.sizeX = Math.max(1, maxX - minX);
                component.sizeY = Math.max(1, maxY - minY);
                component.sizeZ = Math.max(1, maxZ - minZ);
            }
            int[] sorted = { component.sizeX, component.sizeY, component.sizeZ };
            Arrays.sort(sorted);
            component.signature = component.faces.length + ":" + component.vertices.length
                    + ":" + sorted[0] + ":" + sorted[1] + ":" + sorted[2];
        }
    }

    private static void transformVertices(Class159 raw, Component component, PartState state) {
        if (component.vertices.length == 0) return;

        /*
         * Preserve the editor's existing per-part scale semantics (scale around
         * the average vertex centroid), but rotate the resulting geometry around
         * the cached bounds center used by the ROT gizmo and Multi pivot math.
         * This keeps old scaled projects stable at yaw=0 while preventing yaw
         * from making asymmetric parts orbit away from the visible pivot.
         */
        long scaleCx = 0L, scaleCy = 0L, scaleCz = 0L;
        for (int vertex : component.vertices) {
            if (!hasVertexCoordinates(raw, vertex)) return;
            scaleCx += raw.anIntArray1782[vertex];
            scaleCy += raw.anIntArray1777[vertex];
            scaleCz += raw.anIntArray1797[vertex];
        }
        scaleCx /= component.vertices.length;
        scaleCy /= component.vertices.length;
        scaleCz /= component.vertices.length;

        double radians = Math.toRadians(state.yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int vertex : component.vertices) {
            double scaledX = scaleCx
                    + (raw.anIntArray1782[vertex] - scaleCx) * state.scaleX / 100.0;
            double scaledY = scaleCy
                    + (raw.anIntArray1777[vertex] - scaleCy) * state.scaleY / 100.0;
            double scaledZ = scaleCz
                    + (raw.anIntArray1797[vertex] - scaleCz) * state.scaleZ / 100.0;

            double x = scaledX - component.centerX;
            double z = scaledZ - component.centerZ;
            double rx = x * cos + z * sin;
            double rz = z * cos - x * sin;

            raw.anIntArray1782[vertex] =
                    (int) Math.round(component.centerX + rx + state.moveX);
            raw.anIntArray1777[vertex] =
                    (int) Math.round(scaledY + state.moveY);
            raw.anIntArray1797[vertex] =
                    (int) Math.round(component.centerZ + rz + state.moveZ);
        }
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
            if (!hasVertexCoordinates(raw, vertex)) continue;
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

    private static boolean autoFitReplacement(Class159 raw, Component target) {
        if (raw == null || target == null || raw.anInt1791 <= 0
                || raw.anIntArray1782 == null || raw.anIntArray1782.length < raw.anInt1791
                || raw.anIntArray1777 == null || raw.anIntArray1777.length < raw.anInt1791
                || raw.anIntArray1797 == null || raw.anIntArray1797.length < raw.anInt1791) {
            return false;
        }
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
        return true;
    }

    private static Class159 componentOnlyRaw(Class159 sourceRaw,
            Component component, PartState state) {
        if (sourceRaw == null || component == null
                || sourceRaw.anInt1791 <= 0 || sourceRaw.anInt1778 < 0) return null;
        for (int vertex : component.vertices) {
            if (!hasVertexCoordinates(sourceRaw, vertex)) return null;
        }
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
            if (sourceRaw.anIntArray1813 != null && old < sourceRaw.anIntArray1813.length
                    && raw.anIntArray1813 != null && i < raw.anIntArray1813.length)
                raw.anIntArray1813[i] = sourceRaw.anIntArray1813[old];
        }

        for (int i = 0; i < component.faces.length; i++) {
            int face = component.faces[i];
            if (!hasFaceIndices(sourceRaw, face)) return null;
            int sourceA = sourceRaw.aShortArray1786[face] & 0xffff;
            int sourceB = sourceRaw.aShortArray1787[face] & 0xffff;
            int sourceD = sourceRaw.aShortArray1789[face] & 0xffff;
            if (sourceA >= map.length || sourceB >= map.length || sourceD >= map.length)
                return null;
            int a = map[sourceA];
            int b = map[sourceB];
            int d = map[sourceD];
            if (a < 0 || b < 0 || d < 0
                    || a >= raw.anInt1775 || b >= raw.anInt1775 || d >= raw.anInt1775) {
                return null;
            }
            raw.aShortArray1786[i] = (short) a;
            raw.aShortArray1787[i] = (short) b;
            raw.aShortArray1789[i] = (short) d;
            if (raw.faceColours != null && i < raw.faceColours.length)
                raw.faceColours[i] = sourceRaw.faceColours != null
                        && face < sourceRaw.faceColours.length ? sourceRaw.faceColours[face] : 0;
            if (raw.faceAlpha != null && i < raw.faceAlpha.length)
                raw.faceAlpha[i] = sourceRaw.faceAlpha != null
                        && face < sourceRaw.faceAlpha.length ? sourceRaw.faceAlpha[face] : 0;
            if (raw.faceTextures != null && i < raw.faceTextures.length)
                raw.faceTextures[i] = -1;
            if (raw.faceTextureIndexes != null && i < raw.faceTextureIndexes.length)
                raw.faceTextureIndexes[i] = -1;
            if (raw.aByteArray1792 != null && i < raw.aByteArray1792.length)
                raw.aByteArray1792[i] = sourceRaw.aByteArray1792 != null
                        && face < sourceRaw.aByteArray1792.length
                        ? sourceRaw.aByteArray1792[face] : 0;
            if (raw.aByteArray1799 != null && i < raw.aByteArray1799.length)
                raw.aByteArray1799[i] = sourceRaw.aByteArray1799 != null
                        && face < sourceRaw.aByteArray1799.length
                        ? sourceRaw.aByteArray1799[face] : 0;
            if (raw.anIntArray1780 != null && i < raw.anIntArray1780.length)
                raw.anIntArray1780[i] = sourceRaw.anIntArray1780 != null
                        && face < sourceRaw.anIntArray1780.length
                        ? sourceRaw.anIntArray1780[face] : 0;
        }

        PartState local = state.copy();
        Component localComponent = new Component(sequence(component.faces.length),
                sequence(component.vertices.length));
        /*
         * componentOnlyRaw keeps the copied vertices in the source component's
         * original coordinate space. Preserve that component's cached bounds so
         * duplicate rotation uses the same pivot as the original part instead of
         * the new compact Component's default 0,0,0 center.
         */
        localComponent.centerX = component.centerX;
        localComponent.centerY = component.centerY;
        localComponent.centerZ = component.centerZ;
        localComponent.sizeX = component.sizeX;
        localComponent.sizeY = component.sizeY;
        localComponent.sizeZ = component.sizeZ;
        localComponent.signature = component.signature;
        transformVertices(raw, localComponent, local);
        return raw;
    }

    private static int[] sequence(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = i;
        return values;
    }

    private static void hideFaces(Class159 raw, Component component) {
        if (raw == null || component == null || raw.faceAlpha == null) return;
        for (int face : component.faces) {
            if (face >= 0 && face < raw.faceAlpha.length)
                raw.faceAlpha[face] = (byte) 0xff;
        }
    }

    private static void highlightFaces(Class159 raw, Component component) {
        if (raw == null || component == null) return;
        for (int face : component.faces) {
            if (face < 0 || face >= raw.anInt1778) continue;
            if (raw.faceAlpha != null && face < raw.faceAlpha.length)
                raw.faceAlpha[face] = 0;
            if (raw.faceColours != null && face < raw.faceColours.length)
                raw.faceColours[face] = HIGHLIGHT_COLOUR;
            if (raw.faceTextures != null && face < raw.faceTextures.length)
                raw.faceTextures[face] = (short) -1;
            if (raw.faceTextureIndexes != null && face < raw.faceTextureIndexes.length)
                raw.faceTextureIndexes[face] = (short) -1;
        }
    }

    private static void highlightAllFaces(Class159 raw) {
        if (raw == null || raw.anInt1778 < 0) return;
        ensureFaceAlpha(raw);
        for (int face = 0; face < raw.anInt1778; face++) {
            if (raw.faceAlpha != null && face < raw.faceAlpha.length)
                raw.faceAlpha[face] = 0;
            if (raw.faceColours != null && face < raw.faceColours.length)
                raw.faceColours[face] = HIGHLIGHT_COLOUR;
            if (raw.faceTextures != null && face < raw.faceTextures.length)
                raw.faceTextures[face] = (short) -1;
            if (raw.faceTextureIndexes != null && face < raw.faceTextureIndexes.length)
                raw.faceTextureIndexes[face] = (short) -1;
        }
    }

    private static void ensureFaceAlpha(Class159 raw) {
        if (raw.faceAlpha == null || raw.faceAlpha.length < raw.anInt1778)
            raw.faceAlpha = new byte[raw.anInt1778];
    }

    private static boolean hasVertexCoordinates(Class159 raw, int vertex) {
        return raw != null && vertex >= 0 && vertex < raw.anInt1791
                && raw.anIntArray1782 != null && vertex < raw.anIntArray1782.length
                && raw.anIntArray1777 != null && vertex < raw.anIntArray1777.length
                && raw.anIntArray1797 != null && vertex < raw.anIntArray1797.length;
    }

    private static boolean hasFaceIndices(Class159 raw, int face) {
        return raw != null && face >= 0 && face < raw.anInt1778
                && raw.aShortArray1786 != null && face < raw.aShortArray1786.length
                && raw.aShortArray1787 != null && face < raw.aShortArray1787.length
                && raw.aShortArray1789 != null && face < raw.aShortArray1789.length;
    }

    private Source sourceFor(PartState state) {
        if (source == null || state == null) return null;
        boolean pinned = state.sourceModelIds != null && state.sourceModelIds.length > 0;
        if (pinned) {
            if (sameRecipe(state, source)) return source;
            String key = sourceRecipeKey(state);
            Source owner = auxiliarySources.get(key);
            if (owner != null) return owner;
            owner = loadPinnedSource(state);
            if (owner != null) auxiliarySources.put(key, owner);
            return owner;
        }
        if (state.sourceObjectId < 0
                || (state.sourceObjectId == source.objectId
                && state.sourceObjectType == source.objectType)) {
            return source;
        }
        String key = state.sourceObjectId + ":" + state.sourceObjectType;
        Source owner = auxiliarySources.get(key);
        if (owner != null) return owner;
        owner = resolveSource(state.sourceObjectId, state.sourceObjectType);
        if (owner != null) auxiliarySources.put(key, owner);
        return owner;
    }

    private Source loadPinnedSource(PartState state) {
        if (state == null || state.sourceModelIds == null
                || state.sourceModelIds.length == 0) return null;
        Class613 region = client.aClass613_8605;
        if (region == null) return null;
        Class639_Sub16 definitions = region.method7288(0);
        if (definitions == null) return null;
        int lookupId = state.sourceObjectId >= 0 ? state.sourceObjectId : source.objectId;
        try {
            ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(
                    lookupId, -1356282071);
            byte[][] bytes = loadModelBytes(definition, state.sourceModelIds);
            if (bytes == null) return null;
            Source candidate = new Source(lookupId, state.sourceObjectType,
                    state.sourceModelIds.clone(), bytes,
                    cloneShorts(state.sourceRecolorFrom),
                    cloneShorts(state.sourceRecolorTo),
                    cloneShorts(state.sourceRetextureFrom),
                    cloneShorts(state.sourceRetextureTo));
            Class159 raw = candidate.decode();
            if (raw == null || raw.anInt1791 <= 0 || raw.anInt1778 <= 0) return null;
            candidate.components = detectComponents(raw);
            populateComponentBounds(raw, candidate.components);
            return candidate;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void pinSourceRecipe(PartState state, Source owner) {
        if (state == null || owner == null) return;
        state.sourceObjectId = owner.objectId;
        state.sourceObjectType = owner.objectType;
        state.sourceModelIds = owner.modelIds.clone();
        state.sourceRecolorFrom = cloneShorts(owner.recolorFrom);
        state.sourceRecolorTo = cloneShorts(owner.recolorTo);
        state.sourceRetextureFrom = cloneShorts(owner.retextureFrom);
        state.sourceRetextureTo = cloneShorts(owner.retextureTo);
    }

    private static boolean sameRecipe(PartState state, Source owner) {
        return state != null && owner != null
                && state.sourceObjectId == owner.objectId
                && state.sourceObjectType == owner.objectType
                && Arrays.equals(state.sourceModelIds, owner.modelIds)
                && Arrays.equals(state.sourceRecolorFrom, owner.recolorFrom)
                && Arrays.equals(state.sourceRecolorTo, owner.recolorTo)
                && Arrays.equals(state.sourceRetextureFrom, owner.retextureFrom)
                && Arrays.equals(state.sourceRetextureTo, owner.retextureTo);
    }

    private static String sourceRecipeKey(PartState state) {
        return state.sourceObjectId + ":" + state.sourceObjectType
                + ":" + Arrays.toString(state.sourceModelIds)
                + ":" + Arrays.toString(state.sourceRecolorFrom)
                + ">" + Arrays.toString(state.sourceRecolorTo)
                + ":" + Arrays.toString(state.sourceRetextureFrom)
                + ">" + Arrays.toString(state.sourceRetextureTo);
    }

    private Component componentFor(PartState state) {
        Source owner = sourceFor(state);
        if (owner == null || state == null
                || state.sourcePart < 0 || state.sourcePart >= owner.components.length) {
            return null;
        }
        return owner.components[state.sourcePart];
    }

    private boolean validSourcePart(PartState state) {
        return componentFor(state) != null;
    }

    synchronized String selectionAssetJson() {
        if (source == null || selection.isEmpty()) return null;
        ArrayList<Integer> exported = new ArrayList<Integer>();
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state != null && !state.deleted) exported.add(index);
        }
        if (exported.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"format\": \"matrix3-live-model-selection\",\n");
        out.append("  \"version\": 2,\n");
        out.append("  \"sourceObjectId\": ").append(source.objectId).append(",\n");
        out.append("  \"sourceObjectType\": ").append(source.objectType).append(",\n");
        out.append("  \"sourceModelIds\": ").append(intArrayJson(source.modelIds)).append(",\n");
        int[] exportedIndices = new int[exported.size()];
        for (int i = 0; i < exported.size(); i++) exportedIndices[i] = exported.get(i).intValue();
        out.append("  \"selectedPartIndices\": ").append(intArrayJson(exportedIndices)).append(",\n");
        out.append("  \"parts\": [\n");
        for (int i = 0; i < exported.size(); i++) {
            int index = exported.get(i).intValue();
            if (i > 0) out.append(",\n");
            if (index < originals.size()) appendState(out, originals.get(index), index, false);
            else appendState(out, duplicates.get(index - originals.size()), index - originals.size(), true);
        }
        out.append("\n  ]\n}\n");
        return out.toString();
    }

    synchronized int importSelectionAssetJson(String json) {
        if (source == null || json == null) return 0;
        if (!"matrix3-live-model-selection".equals(
                readString(json, "format", ""))) return 0;
        int version = readInt(json, "version", 1);
        if (version < 1 || version > 2) return 0;

        int fallbackObjectId = readInt(json, "sourceObjectId", -1);
        int fallbackObjectType = readInt(json, "sourceObjectType", 10);
        Matcher parts = Pattern.compile("\\\"parts\\\"\\s*:\\s*\\[(.*?)\\]",
                Pattern.DOTALL).matcher(json);
        if (!parts.find()) return 0;

        ArrayList<PartState> staged = new ArrayList<PartState>();
        Map<Integer, Integer> groupMap = new LinkedHashMap<Integer, Integer>();
        int nextGroup = nextGroupId();
        Matcher object = Pattern.compile("\\{([^}]*)\\}").matcher(parts.group(1));
        while (object.find()) {
            String body = object.group(1);
            int sourcePart = readInt(body, "sourcePart",
                    readInt(body, "index", -1));
            int donorObjectId = readInt(body, "sourceObjectId", fallbackObjectId);
            if (donorObjectId < 0) donorObjectId = fallbackObjectId;
            int donorObjectType = readInt(body, "sourceObjectType", fallbackObjectType);
            if (donorObjectId < 0 || sourcePart < 0) continue;

            PartState state = new PartState(sourcePart);
            readState(body, state);
            state.sourceObjectId = donorObjectId;
            state.sourceObjectType = donorObjectType;
            state.hidden = false;
            state.deleted = false;
            if (!validSourcePart(state)) continue;

            if (state.groupId > 0) {
                Integer mapped = groupMap.get(Integer.valueOf(state.groupId));
                if (mapped == null) {
                    mapped = Integer.valueOf(nextGroup++);
                    groupMap.put(Integer.valueOf(state.groupId), mapped);
                }
                state.groupId = mapped.intValue();
            }
            staged.add(state);
        }
        if (staged.isEmpty()) return 0;

        pushUndo();
        selection.clear();
        for (PartState state : staged) {
            duplicates.add(state);
            selection.add(Integer.valueOf(originals.size() + duplicates.size() - 1));
        }
        selected = lastSelectionIndex();
        markGeometryChanged();
        return staged.size();
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
        pushUndo(true);
    }

    private void pushUndo(boolean clearRedo) {
        if (clearRedo) redo.clear();
        pushHistory(undo, snapshotCurrent());
    }

    private Snapshot snapshotCurrent() {
        return new Snapshot(originals, duplicates, selected, selectionArray(), isolate);
    }

    private void pushHistory(ArrayDeque<Snapshot> history, Snapshot snapshot) {
        if (history.size() >= MAX_UNDO) history.removeFirst();
        history.addLast(snapshot);
    }

    private void restoreSnapshot(Snapshot snapshot) {
        originals.clear();
        for (PartState state : snapshot.originals) originals.add(state.copy());
        duplicates.clear();
        for (PartState state : snapshot.duplicates) duplicates.add(state.copy());
        selected = snapshot.selected;
        selection.clear();
        for (int index : snapshot.selection) {
            if (index >= 0 && index < getPartCount()) selection.add(Integer.valueOf(index));
        }
        if (selected >= 0 && !selection.contains(Integer.valueOf(selected))) {
            selected = lastSelectionIndex();
        }
        hovered = -1;
        isolate = snapshot.isolate;
        gestureActive = false;
        gestureStarts.clear();
    }

    private void addIndexWithGroup(LinkedHashSet<Integer> target, int index) {
        PartState state = stateAt(index);
        if (state == null || state.deleted) return;
        if (state.groupId <= 0) {
            target.add(Integer.valueOf(index));
            return;
        }
        for (int i = 0; i < getPartCount(); i++) {
            PartState member = stateAt(i);
            if (member != null && !member.deleted && member.groupId == state.groupId) {
                target.add(Integer.valueOf(i));
            }
        }
    }

    private int nextGroupId() {
        int max = 0;
        for (PartState state : originals) if (state.groupId > max) max = state.groupId;
        for (PartState state : duplicates) if (state.groupId > max) max = state.groupId;
        return max == Integer.MAX_VALUE ? 1 : max + 1;
    }

    private boolean isSelectionSingleGroup() {
        if (selection.size() < 2) return false;
        PartState primary = selectedState();
        if (primary == null || primary.groupId <= 0) return false;
        int groupId = primary.groupId;
        int activeMembers = 0;
        for (int i = 0; i < getPartCount(); i++) {
            PartState state = stateAt(i);
            if (state == null || state.deleted || state.groupId != groupId) continue;
            activeMembers++;
            if (!selection.contains(Integer.valueOf(i))) return false;
        }
        return activeMembers == selection.size();
    }

    private int[] selectionArray() {
        int[] values = new int[selection.size()];
        int i = 0;
        for (Integer index : selection) values[i++] = index.intValue();
        return values;
    }

    private int lastSelectionIndex() {
        int last = -1;
        for (Integer index : selection) last = index.intValue();
        return last;
    }

    private boolean isHighlighted(int index) {
        return hovered >= 0 ? hovered == index : selection.contains(Integer.valueOf(index));
    }

    private static int[] transformOf(PartState state) {
        return new int[] { state.scaleX, state.scaleY, state.scaleZ,
                state.moveX, state.moveY, state.moveZ, state.yaw };
    }

    private static int[] transformDelta(int[] from, int[] to) {
        return new int[] {
                to[0] - from[0], to[1] - from[1], to[2] - from[2],
                to[3] - from[3], to[4] - from[4], to[5] - from[5],
                normalizeSignedDegrees(to[6] - from[6])
        };
    }

    private boolean applySelectionDelta(int[] delta, Map<Integer, int[]> starts,
            boolean groupScale) {
        boolean changed = false;
        int[] primaryBase = starts == null
                ? transformOf(selectedState()) : starts.get(Integer.valueOf(selected));
        boolean scaleAroundPivot = (groupScale || isSelectionSingleGroup())
                && primaryBase != null
                && (delta[0] != 0 || delta[1] != 0 || delta[2] != 0);
        boolean sharedYawPivot = selection.size() > 1 && delta[6] != 0;
        double[] pivot3D = (scaleAroundPivot || sharedYawPivot)
                ? selectionPivot3DForTransforms(starts) : null;

        double scaleRatioX = scaleAroundPivot
                ? (primaryBase[0] + delta[0]) / (double) Math.max(1, primaryBase[0]) : 1.0;
        double scaleRatioY = scaleAroundPivot
                ? (primaryBase[1] + delta[1]) / (double) Math.max(1, primaryBase[1]) : 1.0;
        double scaleRatioZ = scaleAroundPivot
                ? (primaryBase[2] + delta[2]) / (double) Math.max(1, primaryBase[2]) : 1.0;

        double radians = sharedYawPivot ? Math.toRadians(delta[6]) : 0.0;
        double sin = sharedYawPivot ? Math.sin(radians) : 0.0;
        double cos = sharedYawPivot ? Math.cos(radians) : 1.0;

        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted) continue;
            int[] base = starts == null ? transformOf(state) : starts.get(index);
            if (base == null) continue;

            int scaleX = scaleAroundPivot
                    ? (int) Math.round(base[0] * scaleRatioX) : base[0] + delta[0];
            int scaleY = scaleAroundPivot
                    ? (int) Math.round(base[1] * scaleRatioY) : base[1] + delta[1];
            int scaleZ = scaleAroundPivot
                    ? (int) Math.round(base[2] * scaleRatioZ) : base[2] + delta[2];

            int moveX = base[3] + delta[3];
            int moveY = base[4] + delta[4];
            int moveZ = base[5] + delta[5];
            if (pivot3D != null && source != null) {
                Component component = componentFor(state);
                if (component == null) continue;
                double centerX = component.centerX + base[3];
                double centerY = component.centerY + base[4];
                double centerZ = component.centerZ + base[5];

                if (scaleAroundPivot) {
                    double scaledX = pivot3D[0] + (centerX - pivot3D[0]) * scaleRatioX;
                    double scaledY = pivot3D[1] + (centerY - pivot3D[1]) * scaleRatioY;
                    double scaledZ = pivot3D[2] + (centerZ - pivot3D[2]) * scaleRatioZ;
                    moveX += (int) Math.round(scaledX - centerX);
                    moveY += (int) Math.round(scaledY - centerY);
                    moveZ += (int) Math.round(scaledZ - centerZ);
                    centerX = scaledX;
                    centerY = scaledY;
                    centerZ = scaledZ;
                }

                if (sharedYawPivot) {
                    double dx = centerX - pivot3D[0];
                    double dz = centerZ - pivot3D[2];
                    double rotatedX = pivot3D[0] + dx * cos + dz * sin;
                    double rotatedZ = pivot3D[2] + dz * cos - dx * sin;
                    moveX += (int) Math.round(rotatedX - centerX);
                    moveZ += (int) Math.round(rotatedZ - centerZ);
                }
            }

            int[] values = sanitizeTransform(
                    scaleX, scaleY, scaleZ,
                    moveX, moveY, moveZ,
                    base[6] + delta[6]);
            if (!sameTransform(state, values)) {
                applyTransform(state, values);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Returns the shared XYZ center represented by either the active gesture's
     * frozen start transforms or the current authoring transforms.
     */
    private double[] selectionPivot3DForTransforms(Map<Integer, int[]> starts) {
        if (source == null || selection.isEmpty()) return null;
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (Integer index : selection) {
            PartState state = stateAt(index.intValue());
            if (state == null || state.deleted) continue;
            int[] base = starts == null ? transformOf(state) : starts.get(index);
            if (base == null) continue;
            Component component = componentFor(state);
            if (component == null) continue;
            x += component.centerX + base[3];
            y += component.centerY + base[4];
            z += component.centerZ + base[5];
            count++;
        }
        return count == 0 ? null : new double[] {
                x / count, y / count, z / count
        };
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
        if (state.groupId > 0)
            suffix.append("  [group #").append(state.groupId).append(']');
        if (state.conveyorRole != ConveyorRole.UNASSIGNED)
            suffix.append("  [").append(state.conveyorRole.name()).append(']');
        return suffix.toString();
    }

    private static void appendState(StringBuilder out, PartState state, int index, boolean duplicate) {
        out.append("    {");
        if (duplicate)
            out.append("\"copy\": ").append(index).append(", \"sourcePart\": ").append(state.sourcePart);
        else
            out.append("\"index\": ").append(index);
        out.append(", \"sourceObjectId\": ").append(state.sourceObjectId)
                .append(", \"sourceObjectType\": ").append(state.sourceObjectType)
                .append(", \"scaleX\": ").append(state.scaleX)
                .append(", \"scaleY\": ").append(state.scaleY)
                .append(", \"scaleZ\": ").append(state.scaleZ)
                .append(", \"moveX\": ").append(state.moveX)
                .append(", \"moveY\": ").append(state.moveY)
                .append(", \"moveZ\": ").append(state.moveZ)
                .append(", \"yaw\": ").append(state.yaw)
                .append(", \"groupId\": ").append(state.groupId)
                .append(", \"hidden\": ").append(state.hidden)
                .append(", \"deleted\": ").append(state.deleted)
                .append(", \"replacementObjectId\": ").append(state.replacementObjectId)
                .append(", \"replacementObjectType\": ").append(state.replacementObjectType)
                .append(", \"conveyorRole\": \"").append(state.conveyorRole.name()).append("\"")
                .append("}");
    }

    private static void readState(String body, PartState state) {
        state.sourceObjectId = readInt(body, "sourceObjectId", -1);
        state.sourceObjectType = readInt(body, "sourceObjectType", 10);
        state.scaleX = clamp(readInt(body, "scaleX", 100), 10, 400);
        state.scaleY = clamp(readInt(body, "scaleY", 100), 10, 400);
        state.scaleZ = clamp(readInt(body, "scaleZ", 100), 10, 400);
        state.moveX = clamp(readInt(body, "moveX", 0), -4096, 4096);
        state.moveY = clamp(readInt(body, "moveY", 0), -4096, 4096);
        state.moveZ = clamp(readInt(body, "moveZ", 0), -4096, 4096);
        state.yaw = normalizeDegrees(readInt(body, "yaw", 0));
        state.groupId = Math.max(0, readInt(body, "groupId", 0));
        state.hidden = readBoolean(body, "hidden", false);
        state.deleted = readBoolean(body, "deleted", false);
        state.replacementObjectId = readInt(body, "replacementObjectId", -1);
        state.replacementObjectType = readInt(body, "replacementObjectType", 10);
        state.conveyorRole = ConveyorRole.fromName(
                readString(body, "conveyorRole", ConveyorRole.UNASSIGNED.name()));
    }

    private static String readString(String text, String key, String fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
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

    private static int[] readIntArray(String text, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\[([^]]*)\\]").matcher(text);
        if (!matcher.find()) return new int[0];
        String body = matcher.group(1).trim();
        if (body.length() == 0) return new int[0];
        String[] tokens = body.split(",");
        int[] values = new int[tokens.length];
        int count = 0;
        for (String token : tokens) {
            try { values[count++] = Integer.parseInt(token.trim()); }
            catch (NumberFormatException ignored) {}
        }
        return count == values.length ? values : Arrays.copyOf(values, count);
    }

    private static String intArrayJson(int[] values) {
        StringBuilder out = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(", ");
                out.append(values[i]);
            }
        }
        return out.append(']').toString();
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

    private static int normalizeSignedDegrees(int value) {
        int normalized = value % 360;
        if (normalized > 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized;
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
        int sourceObjectId = -1;
        int sourceObjectType = 10;
        int[] sourceModelIds = new int[0];
        short[] sourceRecolorFrom, sourceRecolorTo;
        short[] sourceRetextureFrom, sourceRetextureTo;
        int scaleX = 100, scaleY = 100, scaleZ = 100;
        int moveX, moveY, moveZ, yaw;
        int groupId;
        boolean hidden, deleted;
        int replacementObjectId = -1;
        int replacementObjectType = 10;
        ConveyorRole conveyorRole = ConveyorRole.UNASSIGNED;

        PartState(int sourcePart) { this.sourcePart = sourcePart; }

        PartState copy() {
            PartState copy = new PartState(sourcePart);
            copy.sourceObjectId = sourceObjectId;
            copy.sourceObjectType = sourceObjectType;
            copy.sourceModelIds = sourceModelIds == null ? new int[0] : sourceModelIds.clone();
            copy.sourceRecolorFrom = cloneShorts(sourceRecolorFrom);
            copy.sourceRecolorTo = cloneShorts(sourceRecolorTo);
            copy.sourceRetextureFrom = cloneShorts(sourceRetextureFrom);
            copy.sourceRetextureTo = cloneShorts(sourceRetextureTo);
            copy.scaleX = scaleX; copy.scaleY = scaleY; copy.scaleZ = scaleZ;
            copy.moveX = moveX; copy.moveY = moveY; copy.moveZ = moveZ; copy.yaw = yaw;
            copy.groupId = groupId;
            copy.hidden = hidden; copy.deleted = deleted;
            copy.replacementObjectId = replacementObjectId;
            copy.replacementObjectType = replacementObjectType;
            copy.conveyorRole = conveyorRole;
            return copy;
        }
    }

    private static final class Snapshot {
        final List<PartState> originals = new ArrayList<PartState>();
        final List<PartState> duplicates = new ArrayList<PartState>();
        final int selected;
        final int[] selection;
        final boolean isolate;
        Snapshot(List<PartState> originalStates, List<PartState> duplicateStates,
                int selected, int[] selection, boolean isolate) {
            for (PartState state : originalStates) originals.add(state.copy());
            for (PartState state : duplicateStates) duplicates.add(state.copy());
            this.selected = selected;
            this.selection = selection == null ? new int[0] : selection.clone();
            this.isolate = isolate;
        }
    }

    private static final class Source {
        final int objectId;
        final int objectType;
        final int[] modelIds;
        final byte[][] bytes;
        final short[] recolorFrom, recolorTo;
        final short[] retextureFrom, retextureTo;
        Component[] components = new Component[0];

        Source(int objectId, int objectType, int[] modelIds, byte[][] bytes,
                short[] recolorFrom, short[] recolorTo,
                short[] retextureFrom, short[] retextureTo) {
            this.objectId = objectId;
            this.objectType = objectType;
            this.modelIds = modelIds;
            this.bytes = bytes;
            this.recolorFrom = recolorFrom;
            this.recolorTo = recolorTo;
            this.retextureFrom = retextureFrom;
            this.retextureTo = retextureTo;
        }

        Class159 decode() {
            Class159[] raws = new Class159[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                raws[i] = new Class159(bytes[i]);
                if (raws[i].anInt1773 < 13) raws[i].method2567(2);
            }
            Class159 raw = raws.length == 1 ? raws[0] : new Class159(raws, raws.length);
            applyVisualRecipe(raw, recolorFrom, recolorTo, retextureFrom, retextureTo);
            return raw;
        }

        private static void applyVisualRecipe(Class159 raw,
                short[] recolorFrom, short[] recolorTo,
                short[] retextureFrom, short[] retextureTo) {
            if (raw == null) return;
            if (raw.faceColours != null && recolorFrom != null && recolorTo != null) {
                int count = Math.min(recolorFrom.length, recolorTo.length);
                for (int face = 0; face < raw.faceColours.length; face++) {
                    for (int i = 0; i < count; i++) {
                        if (raw.faceColours[face] == recolorFrom[i]) {
                            raw.faceColours[face] = recolorTo[i];
                            break;
                        }
                    }
                }
            }
            if (raw.faceTextures != null && retextureFrom != null && retextureTo != null) {
                int count = Math.min(retextureFrom.length, retextureTo.length);
                for (int face = 0; face < raw.faceTextures.length; face++) {
                    for (int i = 0; i < count; i++) {
                        if (raw.faceTextures[face] == retextureFrom[i]) {
                            raw.faceTextures[face] = retextureTo[i];
                            break;
                        }
                    }
                }
            }
        }
    }
}
