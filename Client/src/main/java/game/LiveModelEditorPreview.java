package game;

import java.util.List;

/**
 * Live Model Editor runtime clone + mesh-part authoring preview.
 *
 * Bundle 1 proved private in-world whole-model transforms. Bundle 2 decodes the
 * selected definition's source Class159 geometry, finds connected components,
 * applies developer part edits to fresh decoded copies, then builds renderer
 * Models through Matrix3's existing renderer. Shared cache geometry is never
 * mutated and this path performs no cache/server-world writes.
 */
public final class LiveModelEditorPreview {

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int EDIT_TRANSFORM_FLAGS = 0x0f;
    private static final int EDIT_MODEL_FLAGS = BASE_MODEL_FLAGS | EDIT_TRANSFORM_FLAGS;
    private static final int RAW_BUILD_FLAGS = EDIT_MODEL_FLAGS | 0x1f01f | 0x80000;
    private static final int OBJECT_SIZE_X_DECODE = -876498849;
    private static final int OBJECT_SIZE_Y_DECODE = 1922784011;

    private static final int GIZMO_AXIS_SAMPLE_UNITS = 256;
    private static final int GIZMO_AXIS_PIXELS = 56;
    private static final int GIZMO_HIT_RADIUS = 7;
    private static final int GIZMO_PIVOT_RADIUS = 7;
    private static final int ROTATE_RING_RADIUS = 46;
    private static final int ROTATE_RING_HIT_TOLERANCE = 7;
    private static final int ROTATE_RING_SEGMENTS = 48;
    private static final int GIZMO_X_COLOR = 0xffe05555;
    private static final int GIZMO_Y_COLOR = 0xff55c86a;
    private static final int GIZMO_Z_COLOR = 0xff5689e8;
    private static final int GIZMO_HOVER_COLOR = 0xffffd166;
    private static final int GIZMO_ACTIVE_COLOR = 0xffffffff;
    private static final int GIZMO_PIVOT_COLOR = 0xffe9e3d5;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();
    private static final LiveModelEditorParts PARTS = new LiveModelEditorParts();

    private static volatile boolean active;
    private static volatile int objectId = -1;
    private static volatile String objectName = "Object";
    private static volatile int objectType = 10;
    private static volatile int objectRotation;
    private static volatile int sourceX;
    private static volatile int sourceY;
    private static volatile int plane;
    private static volatile int previewOffsetX;
    private static volatile int previewOffsetY;

    private static volatile int scaleXPercent = 100;
    private static volatile int scaleYPercent = 100;
    private static volatile int scaleZPercent = 100;
    private static volatile int translateX;
    private static volatile int translateY;
    private static volatile int translateZ;
    private static volatile int yawDegrees;

    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "IDLE";
    private static volatile int modelRevision;

    public enum TransformMode {
        MOVE, ROTATE, SCALE
    }

    public enum AxisConstraint {
        FREE, X, Y, Z
    }

    public enum SelectionMode {
        WHOLE, PART, MULTI
    }

    private static Class106 cachedRenderer;
    private static int cachedRevision = Integer.MIN_VALUE;
    private static Model cachedMainModel;
    private static Model[] cachedDuplicateModels = new Model[0];
    private static Model[] cachedReplacementModels = new Model[0];

    private static Class106 cachedPickRenderer;
    private static int cachedPickRevision = Integer.MIN_VALUE;
    private static Model[] cachedPickModels = new Model[0];
    private static int[] cachedPickIndices = new int[0];
    private static int lastPartRenderFaultRevision = Integer.MIN_VALUE;
    private static String lastPartRenderFault = "";

    private static volatile TransformMode transformMode = TransformMode.MOVE;
    private static volatile AxisConstraint axisConstraint = AxisConstraint.FREE;
    private static volatile SelectionMode selectionMode = SelectionMode.PART;
    private static volatile boolean transformSnapEnabled;
    private static volatile int moveSnapStep = 16;
    private static volatile int angleSnapDegrees = 15;
    private static volatile boolean pointerInside;
    private static volatile int pointerX;
    private static volatile int pointerY;
    private static volatile int worldHoveredPart = -1;
    private static volatile boolean dragging;
    private static volatile boolean draggingWhole;
    private static volatile int dragStartX;
    private static volatile int dragStartY;
    private static volatile AxisConstraint gizmoHoveredAxis;
    private static volatile AxisConstraint gizmoActiveAxis;
    private static volatile GizmoScreenState gizmoScreen = GizmoScreenState.hidden();
    private static double dragGizmoDirX;
    private static double dragGizmoDirY;
    private static double dragRotateStartAngle;
    private static int[] dragStartTransform = new int[] { 100, 100, 100, 0, 0, 0, 0 };
    private static int[] dragStartWholeTransform = new int[] { 100, 100, 100, 0, 0, 0, 0 };

    private LiveModelEditorPreview() {
    }

    public static void show(String name, int id, int type, int rotation,
            int worldX, int worldY, int worldPlane,
            int tileOffsetX, int tileOffsetY,
            int sxPercent, int syPercent, int szPercent,
            int moveX, int moveY, int moveZ, int yaw) {
        int previousId = objectId;
        int previousType = objectType;
        objectName = name == null || name.trim().length() == 0 ? "Object" : name;
        objectId = id;
        objectType = clamp(type, 0, 22);
        objectRotation = rotation & 0x3;
        sourceX = worldX;
        sourceY = worldY;
        plane = clamp(worldPlane, 0, 3);
        previewOffsetX = clamp(tileOffsetX, -12, 12);
        previewOffsetY = clamp(tileOffsetY, -12, 12);
        scaleXPercent = clamp(sxPercent, 10, 400);
        scaleYPercent = clamp(syPercent, 10, 400);
        scaleZPercent = clamp(szPercent, 10, 400);
        translateX = clamp(moveX, -4096, 4096);
        translateY = clamp(moveY, -4096, 4096);
        translateZ = clamp(moveZ, -4096, 4096);
        yawDegrees = normalizeDegrees(yaw);
        if (previousId != objectId || previousType != objectType) PARTS.clear();
        invalidateModels();
        active = id >= 0;
        status = active ? describe("READY") : "INVALID object id";
    }

    public static void hide() {
        active = false;
        dragging = false;
        draggingWhole = false;
        pointerInside = false;
        worldHoveredPart = -1;
        gizmoHoveredAxis = null;
        gizmoActiveAxis = null;
        gizmoScreen = GizmoScreenState.hidden();
        PARTS.endGesture();
        PARTS.hover(-1);
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
    }

    public static boolean isActive() { return active; }
    public static boolean isEditSessionActive() { return active && objectId >= 0; }
    public static int getObjectId() { return objectId; }
    public static String getStatus() { return status; }

    public static boolean matchesSource(int id, int worldX, int worldY, int worldPlane) {
        return objectId == id && sourceX == worldX && sourceY == worldY && plane == worldPlane;
    }

    /**
     * Developer render suppression for exactly the source object being edited.
     *
     * The server/world object is left registered so clipping, interactions and
     * persistence remain authoritative. Only its normal scene model is skipped
     * while the private editable replacement is active.
     */
    public static boolean shouldSuppressSceneObject(Interface65 object, Class456_Sub1 node) {
        if (!isEditSessionActive() || object == null || node == null) {
            return false;
        }

        int id;
        try {
            id = object.method136(0);
        } catch (RuntimeException ex) {
            return false;
        }
        if (id != objectId || (node.aByte9009 & 0xff) != plane) {
            return false;
        }

        Class613 region = client.aClass613_8605;
        if (region == null) {
            return false;
        }
        Class497 sceneBase = region.method7280((byte) -102);
        if (sceneBase == null) {
            return false;
        }

        int localTargetX = sourceX - sceneBase.localX * -2109597897;
        int localTargetY = sourceY - sceneBase.localY * 417324155;

        if (node instanceof Class456_Sub1_Sub2) {
            Class456_Sub1_Sub2 area = (Class456_Sub1_Sub2) node;
            int minX = Math.min(area.aShort11503, area.aShort11499);
            int maxX = Math.max(area.aShort11503, area.aShort11499);
            int minY = Math.min(area.aShort11500, area.aShort11502);
            int maxY = Math.max(area.aShort11500, area.aShort11502);
            return localTargetX >= minX && localTargetX <= maxX
                    && localTargetY >= minY && localTargetY <= maxY;
        }

        Class238 transform = node.method5394();
        if (transform == null || transform.aClass240_2647 == null) {
            return false;
        }
        int localX = (int) transform.aClass240_2647.aFloat2653 >> 9;
        int localY = (int) transform.aClass240_2647.aFloat2657 >> 9;
        return localX == localTargetX && localY == localTargetY;
    }

    public static int initializeParts() {
        ObjectDefinitions definition = currentDefinition();
        if (definition == null) {
            status = "PARTS WAIT object definitions";
            return 0;
        }
        int count = PARTS.ensureSource(definition, objectId, objectType);
        invalidateModels();
        status = count > 0 ? "PARTS READY " + count + " connected components"
                : "PARTS unavailable for object type " + objectType;
        return count;
    }

    public static String[] getPartLabels() { return PARTS.getLabels(); }
    public static int getSelectedPart() { return PARTS.getSelected(); }
    public static int[] getSelectedParts() { return PARTS.getSelectedIndices(); }
    public static int getSelectedPartCount() { return PARTS.getSelectedCount(); }
    public static int getHoveredPart() { return PARTS.getHovered(); }
    public static int[] getSelectedPartTransform() { return PARTS.getSelectedTransform(); }
    public static int[] getWholeTransform() {
        return new int[] { scaleXPercent, scaleYPercent, scaleZPercent,
                translateX, translateY, translateZ, yawDegrees };
    }
    public static TransformMode getTransformMode() { return transformMode; }
    public static AxisConstraint getAxisConstraint() { return axisConstraint; }
    public static SelectionMode getSelectionMode() { return selectionMode; }
    public static boolean isTransformSnapEnabled() { return transformSnapEnabled; }
    public static int getMoveSnapStep() { return moveSnapStep; }
    public static int getAngleSnapDegrees() { return angleSnapDegrees; }
    public static int getWorldHoveredPart() { return worldHoveredPart; }

    public static void setTransformSnapEnabled(boolean enabled) {
        transformSnapEnabled = enabled;
        status = enabled
                ? "SNAP ON move=" + moveSnapStep + " angle=" + angleSnapDegrees
                : "SNAP OFF (Ctrl = temporary snap)";
    }

    public static void setMoveSnapStep(int step) {
        moveSnapStep = clamp(step, 1, 512);
    }

    public static void setAngleSnapDegrees(int degrees) {
        angleSnapDegrees = clamp(degrees, 1, 90);
    }

    public static void setTransformMode(TransformMode mode) {
        if (mode != null) {
            transformMode = mode;
            status = "EDIT " + mode + " axis=" + axisConstraint;
        }
    }

    public static void setAxisConstraint(AxisConstraint axis) {
        if (axis != null) {
            axisConstraint = axis;
            status = "EDIT " + transformMode + " axis=" + axisConstraint;
        }
    }

    public static void setSelectionMode(SelectionMode mode) {
        if (mode == null) return;
        selectionMode = mode;
        if (mode == SelectionMode.WHOLE) {
            PARTS.hover(-1);
            PARTS.selectAll();
        } else if (mode == SelectionMode.PART) {
            int primary = PARTS.getSelected();
            if (primary >= 0) PARTS.select(primary);
            else if (PARTS.getPartCount() > 0) PARTS.select(0);
        }
        invalidateVisualModels();
        status = "SELECT " + selectionMode + " | " + transformMode + " " + axisConstraint;
    }

    public static boolean setPartSelection(int[] indices) {
        boolean changed = PARTS.setSelection(indices);
        if (changed) invalidateVisualModels();
        return changed;
    }

    public static boolean selectAllParts() {
        boolean changed = PARTS.selectAll();
        if (changed) invalidateVisualModels();
        return changed;
    }

    public static boolean clearPartSelection() {
        boolean changed = PARTS.clearSelection();
        if (changed) invalidateVisualModels();
        return changed;
    }

    public static boolean resetSelectedPartTransforms() {
        boolean changed = PARTS.resetSelectedTransforms();
        if (changed) invalidateGeometryModels();
        return changed;
    }

    public static void pointerMoved(int x, int y) {
        pointerInside = true;
        pointerX = x;
        pointerY = y;
        if (!dragging && !draggingWhole) {
            gizmoHoveredAxis = hitTransformGizmo(x, y);
        }
    }

    public static void pointerExited() {
        pointerInside = false;
        worldHoveredPart = -1;
        gizmoHoveredAxis = null;
        if (!dragging && PARTS.hover(-1)) invalidateVisualModels();
    }

    public static boolean beginPointerDrag(int x, int y, boolean additive, boolean toggle) {
        pointerMoved(x, y);

        AxisConstraint gizmoHit = hitTransformGizmo(x, y);
        if (gizmoHit != null && canTransformCurrentSelection()) {
            dragStartX = x;
            dragStartY = y;
            gizmoActiveAxis = gizmoHit;
            axisConstraint = gizmoHit;
            if (transformMode == TransformMode.MOVE
                    || transformMode == TransformMode.SCALE) {
                captureGizmoDragDirection(gizmoHit);
            } else if (transformMode == TransformMode.ROTATE) {
                captureRotateDragStart(x, y);
            }

            if (selectionMode == SelectionMode.WHOLE) {
                draggingWhole = true;
                dragging = false;
                dragStartWholeTransform = getWholeTransform();
                status = "GIZMO " + transformMode + " WHOLE axis=" + axisConstraint;
                invalidateVisualModels();
                return true;
            }

            dragStartTransform = PARTS.getSelectedTransform();
            dragging = PARTS.beginGesture();
            draggingWhole = false;
            if (dragging) {
                status = "GIZMO " + transformMode + " " + selectionMode + " "
                        + PARTS.getSelectedCount() + " part(s) axis=" + axisConstraint;
                invalidateVisualModels();
                return true;
            }
            gizmoActiveAxis = null;
        }

        gizmoActiveAxis = null;
        int hit = worldHoveredPart;
        if (hit < 0) hit = PARTS.getHovered();
        if (hit < 0) return false;

        dragStartX = x;
        dragStartY = y;

        if (selectionMode == SelectionMode.WHOLE) {
            draggingWhole = true;
            dragging = false;
            dragStartWholeTransform = getWholeTransform();
            status = "DRAG " + transformMode + " WHOLE axis=" + axisConstraint;
            invalidateVisualModels();
            return true;
        }

        if (selectionMode == SelectionMode.MULTI) {
            if (toggle) {
                PARTS.toggleSelection(hit);
            } else if (additive || !PARTS.isSelected(hit)) {
                PARTS.addSelection(hit);
            }
            if (!PARTS.isSelected(hit)) {
                invalidateVisualModels();
                return false;
            }
        } else if (!PARTS.select(hit)) {
            return false;
        }

        dragStartTransform = PARTS.getSelectedTransform();
        dragging = PARTS.beginGesture();
        draggingWhole = false;
        if (dragging) {
            invalidateVisualModels();
            status = "DRAG " + transformMode + " " + selectionMode + " "
                    + PARTS.getSelectedCount() + " part(s) axis=" + axisConstraint;
        }
        return dragging;
    }

    public static boolean dragPointerTo(int x, int y, boolean snapModifier) {
        if (!dragging && !draggingWhole) return false;
        pointerX = x;
        pointerY = y;
        int dx = x - dragStartX;
        int dy = y - dragStartY;
        int amountX = -dx * 4;
        int amountY = dy * 4;
        int gizmoAxisAmount = transformMode == TransformMode.MOVE
                && gizmoActiveAxis != null
                && gizmoActiveAxis != AxisConstraint.FREE
                ? (int) Math.round((dx * dragGizmoDirX + dy * dragGizmoDirY) * 4.0)
                : 0;
        int gizmoYawDelta = transformMode == TransformMode.ROTATE
                && gizmoActiveAxis == AxisConstraint.Y
                ? rotateGizmoDeltaDegrees(x, y)
                : 0;
        int gizmoScaleDelta = transformMode == TransformMode.SCALE
                && gizmoActiveAxis != null
                ? scaleGizmoDelta(dx, dy)
                : 0;
        int[] cameraGroundDrag = transformMode == TransformMode.MOVE
                && axisConstraint == AxisConstraint.FREE
                ? ConstructionBuildCamera.mapScreenDragToGround(dx, dy, 4)
                : null;

        if (draggingWhole) {
            int sx = dragStartWholeTransform[0], sy = dragStartWholeTransform[1],
                    sz = dragStartWholeTransform[2];
            int mx = dragStartWholeTransform[3], my = dragStartWholeTransform[4],
                    mz = dragStartWholeTransform[5];
            int yaw = dragStartWholeTransform[6];

            if (transformMode == TransformMode.MOVE) {
                if (gizmoActiveAxis == AxisConstraint.X) mx += gizmoAxisAmount;
                else if (gizmoActiveAxis == AxisConstraint.Y) my += gizmoAxisAmount;
                else if (gizmoActiveAxis == AxisConstraint.Z) mz += gizmoAxisAmount;
                else if (axisConstraint == AxisConstraint.X) mx += amountX;
                else if (axisConstraint == AxisConstraint.Y) my += amountY;
                else if (axisConstraint == AxisConstraint.Z) mz += amountY;
                else if (cameraGroundDrag != null) {
                    mx += cameraGroundDrag[0];
                    mz += cameraGroundDrag[1];
                } else {
                    mx += amountX;
                    mz += amountY;
                }
            } else if (transformMode == TransformMode.ROTATE) {
                yaw += gizmoActiveAxis == AxisConstraint.Y ? gizmoYawDelta : -dx;
            } else {
                int delta = gizmoActiveAxis != null
                        ? gizmoScaleDelta : (dx - dy) / 2;
                AxisConstraint scaleAxis = gizmoActiveAxis != null
                        ? gizmoActiveAxis : axisConstraint;
                if (scaleAxis == AxisConstraint.X) sx += delta;
                else if (scaleAxis == AxisConstraint.Y) sy += delta;
                else if (scaleAxis == AxisConstraint.Z) sz += delta;
                else {
                    sx += delta;
                    sy += delta;
                    sz += delta;
                }
            }

            boolean snapActive = transformSnapEnabled ? !snapModifier : snapModifier;
            if (snapActive) {
                if (transformMode == TransformMode.MOVE) {
                    if (axisConstraint == AxisConstraint.X)
                        mx = snapFromDragStart(dragStartWholeTransform[3], mx, moveSnapStep);
                    else if (axisConstraint == AxisConstraint.Y)
                        my = snapFromDragStart(dragStartWholeTransform[4], my, moveSnapStep);
                    else if (axisConstraint == AxisConstraint.Z)
                        mz = snapFromDragStart(dragStartWholeTransform[5], mz, moveSnapStep);
                    else {
                        mx = snapFromDragStart(dragStartWholeTransform[3], mx, moveSnapStep);
                        mz = snapFromDragStart(dragStartWholeTransform[5], mz, moveSnapStep);
                    }
                } else if (transformMode == TransformMode.ROTATE) {
                    yaw = snapYawFromDragStart(
                            dragStartWholeTransform[6], yaw, angleSnapDegrees);
                }
            }

            sx = clamp(sx, 10, 400);
            sy = clamp(sy, 10, 400);
            sz = clamp(sz, 10, 400);
            mx = clamp(mx, -4096, 4096);
            my = clamp(my, -4096, 4096);
            mz = clamp(mz, -4096, 4096);
            yaw = normalizeDegrees(yaw);
            boolean changed = sx != scaleXPercent || sy != scaleYPercent || sz != scaleZPercent
                    || mx != translateX || my != translateY || mz != translateZ || yaw != yawDegrees;
            scaleXPercent = sx;
            scaleYPercent = sy;
            scaleZPercent = sz;
            translateX = mx;
            translateY = my;
            translateZ = mz;
            yawDegrees = yaw;
            if (changed) invalidateGeometryModels();
            return changed;
        }

        int sx = dragStartTransform[0], sy = dragStartTransform[1], sz = dragStartTransform[2];
        int mx = dragStartTransform[3], my = dragStartTransform[4], mz = dragStartTransform[5];
        int yaw = dragStartTransform[6];

        if (transformMode == TransformMode.MOVE) {
            if (gizmoActiveAxis == AxisConstraint.X) mx += gizmoAxisAmount;
            else if (gizmoActiveAxis == AxisConstraint.Y) my += gizmoAxisAmount;
            else if (gizmoActiveAxis == AxisConstraint.Z) mz += gizmoAxisAmount;
            else if (axisConstraint == AxisConstraint.X) mx += amountX;
            else if (axisConstraint == AxisConstraint.Y) my += amountY;
            else if (axisConstraint == AxisConstraint.Z) mz += amountY;
            else if (cameraGroundDrag != null) {
                mx += cameraGroundDrag[0];
                mz += cameraGroundDrag[1];
            } else {
                mx += amountX;
                mz += amountY;
            }
        } else if (transformMode == TransformMode.ROTATE) {
            yaw += gizmoActiveAxis == AxisConstraint.Y ? gizmoYawDelta : -dx;
        } else {
            int delta = gizmoActiveAxis != null
                    ? gizmoScaleDelta : (dx - dy) / 2;
            AxisConstraint scaleAxis = gizmoActiveAxis != null
                    ? gizmoActiveAxis : axisConstraint;
            if (scaleAxis == AxisConstraint.X) sx += delta;
            else if (scaleAxis == AxisConstraint.Y) sy += delta;
            else if (scaleAxis == AxisConstraint.Z) sz += delta;
            else {
                sx += delta;
                sy += delta;
                sz += delta;
            }
        }

        boolean snapActive = transformSnapEnabled ? !snapModifier : snapModifier;
        if (snapActive) {
            if (transformMode == TransformMode.MOVE) {
                if (axisConstraint == AxisConstraint.X)
                    mx = snapFromDragStart(dragStartTransform[3], mx, moveSnapStep);
                else if (axisConstraint == AxisConstraint.Y)
                    my = snapFromDragStart(dragStartTransform[4], my, moveSnapStep);
                else if (axisConstraint == AxisConstraint.Z)
                    mz = snapFromDragStart(dragStartTransform[5], mz, moveSnapStep);
                else {
                    mx = snapFromDragStart(dragStartTransform[3], mx, moveSnapStep);
                    mz = snapFromDragStart(dragStartTransform[5], mz, moveSnapStep);
                }
            } else if (transformMode == TransformMode.ROTATE) {
                yaw = snapYawFromDragStart(dragStartTransform[6], yaw, angleSnapDegrees);
            }
        }

        boolean changed = PARTS.updateGestureTransform(sx, sy, sz, mx, my, mz, yaw);
        if (changed) invalidateGeometryModels();
        return changed;
    }

    public static void endPointerDrag() {
        if (dragging) {
            PARTS.endGesture();
            dragging = false;
            invalidateGeometryModels();
        }
        if (draggingWhole) {
            draggingWhole = false;
            invalidateGeometryModels();
        }
        gizmoActiveAxis = null;
        gizmoHoveredAxis = hitTransformGizmo(pointerX, pointerY);
    }

    public static boolean replaceSelectedWithConstructionPiece(
            ConstructionPlacementController.BuildPiece piece, boolean allMatching) {
        if (piece == null) return false;
        boolean changed = PARTS.replaceSelected(piece.getObjectId(), piece.getObjectType(), allMatching);
        if (changed) {
            invalidateGeometryModels();
            status = (allMatching ? "REPLACED MATCHING PARTS with " : "REPLACED PART with ")
                    + piece.getDisplayName() + " #" + piece.getObjectId();
        }
        return changed;
    }

    public static boolean clearSelectedReplacement() {
        boolean changed = PARTS.clearSelectedReplacement();
        if (changed) {
            invalidateGeometryModels();
            status = "RESTORED selected source component.";
        }
        return changed;
    }

    public static void previewPart(int index) {
        if (PARTS.hover(index)) invalidateVisualModels();
    }

    public static void clearPartPreview() {
        if (PARTS.hover(-1)) invalidateVisualModels();
    }

    public static boolean selectPart(int index) {
        boolean changed = PARTS.select(index);
        invalidateVisualModels();
        return changed;
    }

    public static String getSelectionAssetJson() {
        return PARTS.selectionAssetJson();
    }

    public static boolean setSelectedPartTransform(int sx, int sy, int sz,
            int mx, int my, int mz, int yaw) {
        boolean changed = PARTS.setSelectedTransform(sx, sy, sz, mx, my, mz, yaw);
        if (changed) invalidateGeometryModels();
        return changed;
    }

    public static boolean toggleSelectedPartHidden() {
        boolean changed = PARTS.toggleSelectedHidden();
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean deleteSelectedPart() {
        boolean changed = PARTS.deleteSelected();
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean duplicateSelectedPart() {
        boolean changed = PARTS.duplicateSelected();
        if (changed) invalidateModels();
        return changed;
    }

    public static boolean showAllParts() {
        boolean changed = PARTS.showAll();
        if (changed) invalidateModels();
        return changed;
    }

    public static void toggleIsolatePart() {
        PARTS.toggleIsolate();
        invalidateGeometryModels();
    }

    public static boolean isPartIsolated() { return PARTS.isIsolate(); }

    public static boolean undoPartEdit() {
        boolean changed = PARTS.undo();
        if (changed) invalidateModels();
        return changed;
    }

    public static String getPartProjectJsonFields() { return PARTS.projectJsonFields(); }

    public static void loadPartProjectJson(String json) {
        PARTS.loadProjectJson(json);
        invalidateGeometryModels();
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!active || objectId < 0 || scene == null || renderer == null) return;
        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) return;
        lastRenderedCycle = cycle;

        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) return;

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            status = "WAIT scene/object definitions";
            return;
        }

        int worldX = sourceX + previewOffsetX;
        int worldY = sourceY + previewOffsetY;
        int localX = worldX - sceneBase.localX * -2109597897;
        int localY = worldY - sceneBase.localY * 417324155;
        int renderPlane = plane;
        if (renderPlane < 0 || renderPlane >= scene.aClass174Array5838.length) {
            status = "SKIP invalid plane " + renderPlane;
            return;
        }

        Class174 ground = scene.aClass174Array5838[renderPlane];
        if (ground == null) {
            status = "WAIT terrain plane " + renderPlane;
            return;
        }

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(objectId, -1356282071);
        if (definition == null) {
            status = "UNKNOWN object id " + objectId;
            return;
        }

        int renderRotation = objectRotation & 0x3;
        int sizeX = definition.sizeX * OBJECT_SIZE_X_DECODE;
        int sizeY = definition.sizeY * OBJECT_SIZE_Y_DECODE;
        if ((renderRotation & 0x1) != 0) {
            int swap = sizeX; sizeX = sizeY; sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            status = "SKIP runtime clone outside active scene";
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = renderPlane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[renderPlane + 1] : null;

        if (PARTS.isReadyFor(objectId, objectType)) {
            renderPartModels(renderer, definition, ground, upperGround,
                    sceneX, sceneY, sceneZ, renderRotation);
            updateGizmoProjection(renderer, definition,
                    sceneX, sceneY, sceneZ, renderRotation);
            status = describe("DRAW PARTS") + " count=" + PARTS.getPartCount()
                    + " at=" + worldX + "," + worldY + "," + renderPlane;
            return;
        }

        Class647 built = definition.method6057(renderer, EDIT_MODEL_FLAGS,
                objectType, renderRotation, ground, upperGround,
                sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null || !(built.anObject8324 instanceof Model)) {
            status = "MODEL_NULL id=" + objectId + " type=" + objectType + " rot=" + renderRotation;
            return;
        }
        Model model = ((Model) built.anObject8324).method1351((byte) 0, EDIT_MODEL_FLAGS, true);
        if (model == null) {
            status = "CLONE_NULL id=" + objectId;
            return;
        }
        applyWholeTransforms(model);
        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        updateGizmoProjection(renderer, definition,
                sceneX, sceneY, sceneZ, renderRotation);
        status = describe("DRAW") + " at=" + worldX + "," + worldY + "," + renderPlane;
    }

    /**
     * Draws the projected Move gizmo after the developer 3D preview pass.
     *
     * verified-static:
     * - Class106.method1792 projects a 3D renderer-space point to screen x/y.
     * - Class106.method1730 is the renderer-native 2D line primitive.
     * - Class106.method1725 is the renderer-native filled rectangle primitive.
     */
    static void renderGizmoOverlay(Class106 renderer) {
        if (!active || renderer == null) return;
        GizmoScreenState state = gizmoScreen;
        if (!state.visible) return;

        try {
            int pivotColor = gizmoActiveAxis == AxisConstraint.FREE
                    ? GIZMO_ACTIVE_COLOR
                    : gizmoHoveredAxis == AxisConstraint.FREE
                            ? GIZMO_HOVER_COLOR : GIZMO_PIVOT_COLOR;

            if (transformMode == TransformMode.MOVE) {
                drawGizmoAxis(renderer, state.centerX, state.centerY,
                        state.xX, state.xY, AxisConstraint.X, GIZMO_X_COLOR);
                drawGizmoAxis(renderer, state.centerX, state.centerY,
                        state.yX, state.yY, AxisConstraint.Y, GIZMO_Y_COLOR);
                drawGizmoAxis(renderer, state.centerX, state.centerY,
                        state.zX, state.zY, AxisConstraint.Z, GIZMO_Z_COLOR);
            } else if (transformMode == TransformMode.ROTATE) {
                drawRotateGizmo(renderer, state);
            } else if (transformMode == TransformMode.SCALE) {
                drawScaleGizmo(renderer, state);
            }

            int pivotSize = transformMode == TransformMode.SCALE ? 11 : 9;
            int pivotHalf = pivotSize / 2;
            renderer.method1725(state.centerX - pivotHalf, state.centerY - pivotHalf,
                    pivotSize, pivotSize, pivotColor, 1);
        } catch (RuntimeException ex) {
            gizmoScreen = GizmoScreenState.hidden();
            gizmoHoveredAxis = null;
            gizmoActiveAxis = null;
            System.err.println("[LiveModelEditor] GIZMO RENDER SKIP "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static void renderPartModels(Class106 renderer, ObjectDefinitions definition,
            Class174 ground, Class174 upperGround, int sceneX, int sceneY, int sceneZ,
            int rotation) {
        int revision = modelRevision * 31 + PARTS.getRevision();
        if (cachedRenderer != renderer || cachedRevision != revision) {
            cachedMainModel = null;
            cachedDuplicateModels = new Model[0];
            cachedReplacementModels = new Model[0];

            try {
                Class159 mainRaw = PARTS.buildMainRaw();
                cachedMainModel = mainRaw == null ? null
                        : safeBuildDefinitionModel("main", -1, renderer,
                                definition, definition, mainRaw, rotation,
                                ground, upperGround, sceneX, sceneY, sceneZ);

                List<Class159> duplicateRaws = PARTS.buildDuplicateRaws();
                cachedDuplicateModels = new Model[duplicateRaws.size()];
                for (int i = 0; i < duplicateRaws.size(); i++) {
                    cachedDuplicateModels[i] = safeBuildDefinitionModel(
                            "duplicate", i, renderer, definition, definition,
                            duplicateRaws.get(i), rotation,
                            ground, upperGround, sceneX, sceneY, sceneZ);
                }

                List<LiveModelEditorParts.ReplacementRaw> replacements = PARTS.buildReplacementRaws();
                cachedReplacementModels = new Model[replacements.size()];
                for (int i = 0; i < replacements.size(); i++) {
                    LiveModelEditorParts.ReplacementRaw replacement = replacements.get(i);
                    ObjectDefinitions material = definitionFor(replacement.objectId);
                    cachedReplacementModels[i] = material == null ? null
                            : safeBuildDefinitionModel("replacement", replacement.partIndex,
                                    renderer, material, definition, replacement.raw, rotation,
                                    ground, upperGround, sceneX, sceneY, sceneZ);
                }
            } catch (RuntimeException ex) {
                recordPartRenderFault("authoring-raw", -1, revision, ex);
            }

            cachedRenderer = renderer;
            cachedRevision = revision;
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        if (cachedMainModel != null) cachedMainModel.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        for (Model model : cachedDuplicateModels)
            if (model != null) model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        for (Model model : cachedReplacementModels)
            if (model != null) model.method1375(TRANSFORM, RENDER_BOUNDS, 0);

        if (!dragging && !draggingWhole) updateWorldPick(renderer, definition, ground, upperGround,
                sceneX, sceneY, sceneZ, rotation);
    }

    private static void updateWorldPick(Class106 renderer, ObjectDefinitions sourceDefinition,
            Class174 ground, Class174 upperGround, int sceneX, int sceneY, int sceneZ,
            int rotation) {
        if (!pointerInside) {
            if (worldHoveredPart != -1) {
                worldHoveredPart = -1;
                if (PARTS.hover(-1)) invalidateVisualModels();
            }
            return;
        }

        if (hitTransformGizmo(pointerX, pointerY) != null) {
            if (worldHoveredPart != -1) worldHoveredPart = -1;
            if (PARTS.hover(-1)) invalidateVisualModels();
            return;
        }

        int pickRevision = modelRevision * 31 + PARTS.getGeometryRevision();
        if (cachedPickRenderer != renderer || cachedPickRevision != pickRevision) {
            List<LiveModelEditorParts.PickRaw> picks = PARTS.buildPickRaws();
            cachedPickModels = new Model[picks.size()];
            cachedPickIndices = new int[picks.size()];
            for (int i = 0; i < picks.size(); i++) {
                LiveModelEditorParts.PickRaw pick = picks.get(i);
                ObjectDefinitions material = definitionFor(pick.materialObjectId);
                if (material == null) material = sourceDefinition;
                cachedPickModels[i] = safeBuildDefinitionModel("pick", pick.partIndex,
                        renderer, material, sourceDefinition, pick.raw, rotation,
                        ground, upperGround, sceneX, sceneY, sceneZ);
                cachedPickIndices[i] = pick.partIndex;
            }
            cachedPickRenderer = renderer;
            cachedPickRevision = pickRevision;
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        int best = -1;
        int bestFaces = Integer.MAX_VALUE;
        for (int i = 0; i < cachedPickModels.length; i++) {
            Model model = cachedPickModels[i];
            if (model != null && model.method1376(pointerX, pointerY, TRANSFORM, false, 0)) {
                int partIndex = cachedPickIndices[i];
                int faces = PARTS.getFaceCount(partIndex);
                if (faces < bestFaces) {
                    bestFaces = faces;
                    best = partIndex;
                }
            }
        }

        if (best != worldHoveredPart) {
            worldHoveredPart = best;
            int previewPart = selectionMode == SelectionMode.WHOLE ? -1 : best;
            if (PARTS.hover(previewPart)) invalidateVisualModels();
        }
    }

    private static Model safeBuildDefinitionModel(String stage, int partIndex,
            Class106 renderer, ObjectDefinitions materialDefinition,
            ObjectDefinitions spatialDefinition, Class159 raw, int rotation,
            Class174 ground, Class174 upperGround,
            int sceneX, int sceneY, int sceneZ) {
        String invalid = validateRawForRenderer(raw);
        if (invalid != null) {
            recordPartRenderFault(stage + "-invalid-" + invalid,
                    partIndex, modelRevision * 31 + PARTS.getRevision(), null);
            return null;
        }
        try {
            return buildDefinitionModel(renderer, materialDefinition, spatialDefinition,
                    raw, rotation, ground, upperGround, sceneX, sceneY, sceneZ);
        } catch (RuntimeException ex) {
            recordPartRenderFault(stage, partIndex,
                    modelRevision * 31 + PARTS.getRevision(), ex);
            return null;
        }
    }

    private static String validateRawForRenderer(Class159 raw) {
        if (raw == null) return "null";
        int vertices = raw.anInt1791;
        int faces = raw.anInt1778;
        if (vertices <= 0 || faces < 0) return "counts";
        if (raw.anIntArray1782 == null || raw.anIntArray1782.length < vertices
                || raw.anIntArray1777 == null || raw.anIntArray1777.length < vertices
                || raw.anIntArray1797 == null || raw.anIntArray1797.length < vertices)
            return "vertex-tables";
        if (raw.aShortArray1786 == null || raw.aShortArray1786.length < faces
                || raw.aShortArray1787 == null || raw.aShortArray1787.length < faces
                || raw.aShortArray1789 == null || raw.aShortArray1789.length < faces
                || raw.faceColours == null || raw.faceColours.length < faces)
            return "face-tables";

        int maxVertex = -1;
        for (int face = 0; face < faces; face++) {
            int a = raw.aShortArray1786[face] & 0xffff;
            int b = raw.aShortArray1787[face] & 0xffff;
            int c = raw.aShortArray1789[face] & 0xffff;
            if (a >= vertices || b >= vertices || c >= vertices)
                return "face-" + face + "-vertex";
            if (a > maxVertex) maxVertex = a;
            if (b > maxVertex) maxVertex = b;
            if (c > maxVertex) maxVertex = c;
        }

        int usedVertices = maxVertex + 1;
        if (usedVertices <= 0 && faces > 0) return "used-vertices";
        if (raw.anInt1775 < usedVertices || raw.anInt1775 > vertices)
            raw.anInt1775 = usedVertices;

        if (raw.faceAlpha != null && raw.faceAlpha.length < faces) return "face-alpha";
        if (raw.faceTextures != null && raw.faceTextures.length < faces) return "face-textures";
        if (raw.faceTextureIndexes != null && raw.faceTextureIndexes.length < faces)
            return "face-texture-index";
        if (raw.aByteArray1792 != null && raw.aByteArray1792.length < faces)
            return "face-render-type";
        if (raw.aByteArray1799 != null && raw.aByteArray1799.length < faces)
            return "face-priority";
        if (raw.anIntArray1780 != null && raw.anIntArray1780.length < faces)
            return "face-skin";
        if (raw.anIntArray1813 != null && raw.anIntArray1813.length < vertices)
            return "vertex-skin";
        if (raw.aShortArray1781 != null && raw.aShortArray1781.length < vertices)
            return "vertex-mask";
        if (raw.aShortArray1800 != null && raw.aShortArray1800.length < faces)
            return "face-mask";
        return null;
    }

    private static void recordPartRenderFault(String stage, int partIndex,
            int revision, RuntimeException ex) {
        String type = ex == null ? "validation" : ex.getClass().getSimpleName();
        String message = "PART RENDER SKIP stage=" + stage
                + " part=" + partIndex + " rev=" + revision + " cause=" + type;
        status = message;
        if (lastPartRenderFaultRevision != revision
                || !message.equals(lastPartRenderFault)) {
            lastPartRenderFaultRevision = revision;
            lastPartRenderFault = message;
            System.err.println("[LiveModelEditor] " + message);
            if (ex != null)
                System.err.println("[LiveModelEditor] "
                        + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    private static Model buildDefinitionModel(Class106 renderer,
            ObjectDefinitions materialDefinition, ObjectDefinitions spatialDefinition,
            Class159 raw, int rotation, Class174 ground, Class174 upperGround,
            int sceneX, int sceneY, int sceneZ) {
        int ambient = materialDefinition.anInt5638 * 1878786655 + 64;
        int contrast = -69277109 * materialDefinition.anInt5639 + 850;
        Model model = renderer.method1755(raw, RAW_BUILD_FLAGS,
                materialDefinition.aClass518_5608.anInt5751 * 1583875953, ambient, contrast);
        if (model == null) return null;

        if (spatialDefinition.aBool5647) model.method1359();
        int rot = rotation & 0x3;
        if (rot == 1) model.method1412(4096);
        else if (rot == 2) model.method1412(8192);
        else if (rot == 3) model.method1412(12288);

        if (materialDefinition.aShortArray5613 != null
                && materialDefinition.aShortArray5621 != null) {
            int recolours = Math.min(materialDefinition.aShortArray5613.length,
                    materialDefinition.aShortArray5621.length);
            for (int i = 0; i < recolours; i++) {
                short replacement = materialDefinition.aShortArray5621[i];
                if (materialDefinition.aByteArray5615 != null
                        && i < materialDefinition.aByteArray5615.length) {
                    replacement = ObjectDefinitions.aShortArray5606[
                            materialDefinition.aByteArray5615[i] & 0xff];
                }
                model.method1393(materialDefinition.aShortArray5613[i], replacement);
            }
        }
        if (materialDefinition.aShortArray5618 != null
                && materialDefinition.aShortArray5617 != null) {
            int retextures = Math.min(materialDefinition.aShortArray5618.length,
                    materialDefinition.aShortArray5617.length);
            for (int i = 0; i < retextures; i++)
                model.method1494(materialDefinition.aShortArray5618[i],
                        materialDefinition.aShortArray5617[i]);
        }
        if (materialDefinition.aByte5666 != 0)
            model.method1396(materialDefinition.aByte5616, materialDefinition.aByte5681,
                    materialDefinition.aByte5622, materialDefinition.aByte5666 & 0xff);

        int dsx = spatialDefinition.anInt5646 * 898312795;
        int dsy = spatialDefinition.anInt5634 * 1899990883;
        int dsz = spatialDefinition.anInt5641 * 1427207859;
        if (dsx != 128 || dsy != 128 || dsz != 128) model.method1464(dsx, dsy, dsz);

        int dmx = spatialDefinition.anInt5652 * -865773249;
        int dmy = spatialDefinition.anInt5653 * -955267449;
        int dmz = spatialDefinition.anInt5654 * -504975083;
        if (dmx != 0 || dmy != 0 || dmz != 0) model.method1358(dmx, dmy, dmz);

        if (spatialDefinition.aByte5628 != 0)
            model.method1463(spatialDefinition.aByte5628,
                    spatialDefinition.anInt5629 * -1793366483,
                    ground, upperGround, sceneX, sceneY, sceneZ);

        int extraX = spatialDefinition.anInt5655 * 1281867755;
        int extraY = spatialDefinition.anInt5673 * -1496350233;
        int extraZ = spatialDefinition.anInt5657 * -2114564345;
        if (extraX != 0 || extraY != 0 || extraZ != 0)
            model.method1358(extraX, extraY, extraZ);

        applyWholeTransforms(model);
        model.method1450(EDIT_MODEL_FLAGS);
        return model;
    }

    private static ObjectDefinitions definitionFor(int id) {
        Class613 region = client.aClass613_8605;
        if (region == null || id < 0) return null;
        Class639_Sub16 definitions = region.method7288(0);
        if (definitions == null) return null;
        try {
            return (ObjectDefinitions) definitions.getDefinition(id, -1356282071);
        } catch (RuntimeException ex) {
            return null;
        }
    }


    private static void updateGizmoProjection(Class106 renderer,
            ObjectDefinitions definition, int sceneX, int sceneY, int sceneZ,
            int rotation) {
        if (renderer == null || definition == null) {
            gizmoScreen = GizmoScreenState.hidden();
            gizmoHoveredAxis = null;
            return;
        }

        int[] localPivot;
        if (selectionMode == SelectionMode.WHOLE) {
            localPivot = new int[] { 0, 0, 0 };
        } else {
            localPivot = PARTS.getSelectionPivot();
            if (localPivot == null) {
                gizmoScreen = GizmoScreenState.hidden();
                gizmoHoveredAxis = null;
                return;
            }
        }

        double[] pivot = transformAuthoringPoint(definition, rotation,
                localPivot[0], localPivot[1], localPivot[2]);
        double[][] ends = new double[3][];
        if (selectionMode == SelectionMode.WHOLE) {
            ends[0] = new double[] { pivot[0] + GIZMO_AXIS_SAMPLE_UNITS, pivot[1], pivot[2] };
            ends[1] = new double[] { pivot[0], pivot[1] + GIZMO_AXIS_SAMPLE_UNITS, pivot[2] };
            ends[2] = new double[] { pivot[0], pivot[1], pivot[2] + GIZMO_AXIS_SAMPLE_UNITS };
        } else {
            ends[0] = transformAuthoringPoint(definition, rotation,
                    localPivot[0] + GIZMO_AXIS_SAMPLE_UNITS, localPivot[1], localPivot[2]);
            ends[1] = transformAuthoringPoint(definition, rotation,
                    localPivot[0], localPivot[1] + GIZMO_AXIS_SAMPLE_UNITS, localPivot[2]);
            ends[2] = transformAuthoringPoint(definition, rotation,
                    localPivot[0], localPivot[1], localPivot[2] + GIZMO_AXIS_SAMPLE_UNITS);
        }

        float[] center = project(renderer,
                sceneX + pivot[0], sceneY + pivot[1], sceneZ + pivot[2]);
        float[] x = project(renderer,
                sceneX + ends[0][0], sceneY + ends[0][1], sceneZ + ends[0][2]);
        float[] y = project(renderer,
                sceneX + ends[1][0], sceneY + ends[1][1], sceneZ + ends[1][2]);
        float[] z = project(renderer,
                sceneX + ends[2][0], sceneY + ends[2][1], sceneZ + ends[2][2]);

        if (center == null) {
            gizmoScreen = GizmoScreenState.hidden();
            gizmoHoveredAxis = null;
            return;
        }

        int[] xEnd = fixedAxisEndpoint(center, x);
        int[] yEnd = fixedAxisEndpoint(center, y);
        int[] zEnd = fixedAxisEndpoint(center, z);
        gizmoScreen = new GizmoScreenState(true,
                Math.round(center[0]), Math.round(center[1]),
                xEnd[0], xEnd[1], yEnd[0], yEnd[1], zEnd[0], zEnd[1],
                xEnd[2] != 0, yEnd[2] != 0, zEnd[2] != 0);
        if (!dragging && !draggingWhole && pointerInside) {
            gizmoHoveredAxis = hitTransformGizmo(pointerX, pointerY);
        }
    }

    /**
     * Mirrors the source-definition transform order used by buildDefinitionModel
     * closely enough to anchor the authoring pivot/axes to the rendered model.
     * Definition model mirroring (aBool5647) and terrain contour deformation
     * are intentionally not guessed here; the runtime gate must confirm the
     * reference assets remain aligned before either seam is promoted.
     */
    private static double[] transformAuthoringPoint(ObjectDefinitions definition,
            int rotation, double x, double y, double z) {
        int rot = rotation & 0x3;
        if (rot != 0) {
            double radians = Math.toRadians(rot * 90.0);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            double rx = x * cos + z * sin;
            double rz = z * cos - x * sin;
            x = rx;
            z = rz;
        }

        int dsx = definition.anInt5646 * 898312795;
        int dsy = definition.anInt5634 * 1899990883;
        int dsz = definition.anInt5641 * 1427207859;
        x = x * dsx / 128.0;
        y = y * dsy / 128.0;
        z = z * dsz / 128.0;

        x += definition.anInt5652 * -865773249;
        y += definition.anInt5653 * -955267449;
        z += definition.anInt5654 * -504975083;
        x += definition.anInt5655 * 1281867755;
        y += definition.anInt5673 * -1496350233;
        z += definition.anInt5657 * -2114564345;

        x = x * scaleXPercent / 100.0;
        y = y * scaleYPercent / 100.0;
        z = z * scaleZPercent / 100.0;

        if (yawDegrees != 0) {
            double radians = Math.toRadians(yawDegrees);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            double rx = x * cos + z * sin;
            double rz = z * cos - x * sin;
            x = rx;
            z = rz;
        }

        return new double[] {
                x + translateX,
                y + translateY,
                z + translateZ
        };
    }

    private static float[] project(Class106 renderer, double x, double y, double z) {
        float[] out = new float[3];
        renderer.method1792((float) x, (float) y, (float) z, out);
        if (Float.isNaN(out[0]) || Float.isNaN(out[1]) || Float.isNaN(out[2])
                || Float.isInfinite(out[0]) || Float.isInfinite(out[1])) {
            return null;
        }
        return out;
    }

    private static int[] fixedAxisEndpoint(float[] center, float[] sample) {
        if (center == null || sample == null) {
            return new int[] { Math.round(center == null ? 0.0F : center[0]),
                    Math.round(center == null ? 0.0F : center[1]), 0 };
        }
        double dx = sample[0] - center[0];
        double dy = sample[1] - center[1];
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 2.0) {
            return new int[] { Math.round(center[0]), Math.round(center[1]), 0 };
        }
        return new int[] {
                (int) Math.round(center[0] + dx / length * GIZMO_AXIS_PIXELS),
                (int) Math.round(center[1] + dy / length * GIZMO_AXIS_PIXELS),
                1
        };
    }

    private static void drawGizmoAxis(Class106 renderer,
            int x1, int y1, int x2, int y2,
            AxisConstraint axis, int baseColor) {
        GizmoScreenState state = gizmoScreen;
        boolean valid = axis == AxisConstraint.X ? state.xValid
                : axis == AxisConstraint.Y ? state.yValid : state.zValid;
        if (!valid) return;

        int color = gizmoActiveAxis == axis ? GIZMO_ACTIVE_COLOR
                : gizmoHoveredAxis == axis ? GIZMO_HOVER_COLOR : baseColor;
        renderer.method1730(x1, y1, x2, y2, color, 1);

        int dx = x2 - x1;
        int dy = y2 - y1;
        if (Math.abs(dx) >= Math.abs(dy)) {
            renderer.method1730(x1, y1 - 1, x2, y2 - 1, color, 1);
            renderer.method1730(x1, y1 + 1, x2, y2 + 1, color, 1);
        } else {
            renderer.method1730(x1 - 1, y1, x2 - 1, y2, color, 1);
            renderer.method1730(x1 + 1, y1, x2 + 1, y2, color, 1);
        }
        renderer.method1725(x2 - 4, y2 - 4, 9, 9, color, 1);
    }



    private static void drawScaleGizmo(Class106 renderer, GizmoScreenState state) {
        drawScaleAxis(renderer, state.centerX, state.centerY,
                state.xX, state.xY, AxisConstraint.X, GIZMO_X_COLOR, state.xValid);
        drawScaleAxis(renderer, state.centerX, state.centerY,
                state.yX, state.yY, AxisConstraint.Y, GIZMO_Y_COLOR, state.yValid);
        drawScaleAxis(renderer, state.centerX, state.centerY,
                state.zX, state.zY, AxisConstraint.Z, GIZMO_Z_COLOR, state.zValid);
    }

    private static void drawScaleAxis(Class106 renderer,
            int x1, int y1, int x2, int y2,
            AxisConstraint axis, int baseColor, boolean valid) {
        if (!valid) return;
        int color = gizmoActiveAxis == axis ? GIZMO_ACTIVE_COLOR
                : gizmoHoveredAxis == axis ? GIZMO_HOVER_COLOR : baseColor;
        renderer.method1730(x1, y1, x2, y2, color, 1);
        renderer.method1730(x1 + 1, y1, x2 + 1, y2, color, 1);
        renderer.method1725(x2 - 5, y2 - 5, 11, 11, color, 1);
    }

    private static int scaleGizmoDelta(int dx, int dy) {
        if (gizmoActiveAxis == AxisConstraint.FREE) {
            // Right/up grows; left/down shrinks. Deliberately screen-relative
            // because the center handle represents uniform scale, not a world axis.
            return (dx - dy) / 2;
        }
        return (int) Math.round(
                (dx * dragGizmoDirX + dy * dragGizmoDirY) / 2.0);
    }

    private static void drawRotateGizmo(Class106 renderer, GizmoScreenState state) {
        int color = gizmoActiveAxis == AxisConstraint.Y ? GIZMO_ACTIVE_COLOR
                : gizmoHoveredAxis == AxisConstraint.Y
                        ? GIZMO_HOVER_COLOR : GIZMO_Y_COLOR;
        int[] previous = rotateRingPoint(state, 0.0);
        for (int i = 1; i <= ROTATE_RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / ROTATE_RING_SEGMENTS;
            int[] next = rotateRingPoint(state, angle);
            renderer.method1730(previous[0], previous[1], next[0], next[1], color, 1);
            previous = next;
        }
    }

    private static int[] rotateRingPoint(GizmoScreenState state, double angle) {
        double xvx = state.xValid ? state.xX - state.centerX : ROTATE_RING_RADIUS;
        double xvy = state.xValid ? state.xY - state.centerY : 0.0;
        double zvx = state.zValid ? state.zX - state.centerX : 0.0;
        double zvy = state.zValid ? state.zY - state.centerY : ROTATE_RING_RADIUS;

        double xLength = Math.sqrt(xvx * xvx + xvy * xvy);
        double zLength = Math.sqrt(zvx * zvx + zvy * zvy);
        if (xLength < 1.0) {
            xvx = ROTATE_RING_RADIUS;
            xvy = 0.0;
            xLength = ROTATE_RING_RADIUS;
        }
        if (zLength < 1.0) {
            zvx = 0.0;
            zvy = ROTATE_RING_RADIUS;
            zLength = ROTATE_RING_RADIUS;
        }

        double xScale = ROTATE_RING_RADIUS / xLength;
        double zScale = ROTATE_RING_RADIUS / zLength;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new int[] {
                (int) Math.round(state.centerX + cos * xvx * xScale + sin * zvx * zScale),
                (int) Math.round(state.centerY + cos * xvy * xScale + sin * zvy * zScale)
        };
    }

    private static AxisConstraint hitTransformGizmo(int x, int y) {
        if (transformMode == TransformMode.MOVE) return hitMoveGizmo(x, y);
        if (transformMode == TransformMode.ROTATE) return hitRotateGizmo(x, y);
        if (transformMode == TransformMode.SCALE) return hitScaleGizmo(x, y);
        return null;
    }

    private static AxisConstraint hitScaleGizmo(int x, int y) {
        if (transformMode != TransformMode.SCALE) return null;
        return hitAxisGizmo(x, y, GIZMO_PIVOT_RADIUS + 2, GIZMO_HIT_RADIUS + 2);
    }

    private static AxisConstraint hitRotateGizmo(int x, int y) {
        GizmoScreenState state = gizmoScreen;
        if (!state.visible) return null;

        int[] previous = rotateRingPoint(state, 0.0);
        double best = Double.MAX_VALUE;
        for (int i = 1; i <= ROTATE_RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / ROTATE_RING_SEGMENTS;
            int[] next = rotateRingPoint(state, angle);
            double distance = pointSegmentDistance(
                    x, y, previous[0], previous[1], next[0], next[1]);
            if (distance < best) best = distance;
            previous = next;
        }
        return best <= ROTATE_RING_HIT_TOLERANCE ? AxisConstraint.Y : null;
    }

    private static void captureRotateDragStart(int x, int y) {
        GizmoScreenState state = gizmoScreen;
        dragRotateStartAngle = Math.atan2(y - state.centerY, x - state.centerX);
    }

    private static int rotateGizmoDeltaDegrees(int x, int y) {
        GizmoScreenState state = gizmoScreen;
        double current = Math.atan2(y - state.centerY, x - state.centerX);
        double delta = current - dragRotateStartAngle;
        while (delta > Math.PI) delta -= Math.PI * 2.0;
        while (delta < -Math.PI) delta += Math.PI * 2.0;
        return (int) Math.round(Math.toDegrees(delta));
    }

    private static AxisConstraint hitMoveGizmo(int x, int y) {
        if (transformMode != TransformMode.MOVE) return null;
        return hitAxisGizmo(x, y, GIZMO_PIVOT_RADIUS, GIZMO_HIT_RADIUS);
    }

    private static AxisConstraint hitAxisGizmo(int x, int y,
            int pivotRadius, int hitRadius) {
        GizmoScreenState state = gizmoScreen;
        if (!state.visible) return null;

        int cx = state.centerX;
        int cy = state.centerY;
        int pdx = x - cx;
        int pdy = y - cy;
        if (pdx * pdx + pdy * pdy <= pivotRadius * pivotRadius) {
            return AxisConstraint.FREE;
        }

        AxisConstraint best = null;
        double bestDistance = hitRadius + 1.0;
        if (state.xValid) {
            double distance = pointSegmentDistance(x, y, cx, cy, state.xX, state.xY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = AxisConstraint.X;
            }
        }
        if (state.yValid) {
            double distance = pointSegmentDistance(x, y, cx, cy, state.yX, state.yY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = AxisConstraint.Y;
            }
        }
        if (state.zValid) {
            double distance = pointSegmentDistance(x, y, cx, cy, state.zX, state.zY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = AxisConstraint.Z;
            }
        }
        return bestDistance <= hitRadius ? best : null;
    }

    private static double pointSegmentDistance(int px, int py,
            int x1, int y1, int x2, int y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0) {
            double ex = px - x1;
            double ey = py - y1;
            return Math.sqrt(ex * ex + ey * ey);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;
        double qx = x1 + t * dx;
        double qy = y1 + t * dy;
        double ex = px - qx;
        double ey = py - qy;
        return Math.sqrt(ex * ex + ey * ey);
    }

    private static boolean canTransformCurrentSelection() {
        return selectionMode == SelectionMode.WHOLE || PARTS.getSelectedCount() > 0;
    }

    private static void captureGizmoDragDirection(AxisConstraint axis) {
        GizmoScreenState state = gizmoScreen;
        int ex = axis == AxisConstraint.X ? state.xX
                : axis == AxisConstraint.Y ? state.yX
                : axis == AxisConstraint.Z ? state.zX : state.centerX;
        int ey = axis == AxisConstraint.X ? state.xY
                : axis == AxisConstraint.Y ? state.yY
                : axis == AxisConstraint.Z ? state.zY : state.centerY;
        double dx = ex - state.centerX;
        double dy = ey - state.centerY;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) {
            dragGizmoDirX = 0.0;
            dragGizmoDirY = 0.0;
        } else {
            dragGizmoDirX = dx / length;
            dragGizmoDirY = dy / length;
        }
    }

    private static final class GizmoScreenState {
        final boolean visible;
        final int centerX;
        final int centerY;
        final int xX;
        final int xY;
        final int yX;
        final int yY;
        final int zX;
        final int zY;
        final boolean xValid;
        final boolean yValid;
        final boolean zValid;

        GizmoScreenState(boolean visible,
                int centerX, int centerY,
                int xX, int xY, int yX, int yY, int zX, int zY,
                boolean xValid, boolean yValid, boolean zValid) {
            this.visible = visible;
            this.centerX = centerX;
            this.centerY = centerY;
            this.xX = xX;
            this.xY = xY;
            this.yX = yX;
            this.yY = yY;
            this.zX = zX;
            this.zY = zY;
            this.xValid = xValid;
            this.yValid = yValid;
            this.zValid = zValid;
        }

        static GizmoScreenState hidden() {
            return new GizmoScreenState(false,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    false, false, false);
        }
    }

    private static void applyWholeTransforms(Model model) {
        int sx = percentToModelScale(scaleXPercent);
        int sy = percentToModelScale(scaleYPercent);
        int sz = percentToModelScale(scaleZPercent);
        if (sx != 128 || sy != 128 || sz != 128) model.method1464(sx, sy, sz);
        int yawUnits = yawDegrees * 16384 / 360 & 0x3fff;
        if (yawUnits != 0) model.method1412(yawUnits);
        if (translateX != 0 || translateY != 0 || translateZ != 0)
            model.method1358(translateX, translateY, translateZ);
    }

    private static ObjectDefinitions currentDefinition() {
        return definitionFor(objectId);
    }

    private static void invalidateVisualModels() {
        cachedRevision = Integer.MIN_VALUE;
        cachedMainModel = null;
        cachedDuplicateModels = new Model[0];
        cachedReplacementModels = new Model[0];
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    private static void invalidateGeometryModels() {
        modelRevision++;
        invalidateVisualModels();
        cachedPickRevision = Integer.MIN_VALUE;
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    private static void invalidateModels() {
        invalidateGeometryModels();
    }

    private static String describe(String state) {
        return state + " " + objectName + " id=" + objectId
                + " type=" + objectType + " rot=" + objectRotation
                + " scale=" + scaleXPercent + "/" + scaleYPercent + "/" + scaleZPercent
                + " move=" + translateX + "/" + translateY + "/" + translateZ
                + " yaw=" + yawDegrees;
    }

    private static int percentToModelScale(int percent) {
        return Math.max(1, percent * 128 / 100);
    }

    private static int normalizeDegrees(int value) {
        int normalized = value % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    private static int snapToStep(int value, int step) {
        if (step <= 1) return value;
        return Math.round((float) value / (float) step) * step;
    }

    private static int snapFromDragStart(int start, int current, int step) {
        return start + snapToStep(current - start, step);
    }

    private static int snapYawFromDragStart(int start, int current, int step) {
        int delta = normalizeSignedDegrees(current - start);
        return normalizeDegrees(start + snapToStep(delta, step));
    }

    private static int normalizeSignedDegrees(int value) {
        int normalized = value % 360;
        if (normalized > 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }
}
