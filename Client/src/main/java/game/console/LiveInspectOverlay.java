package game.console;

import game.Class261;
import game.DevDefinitionBridge;
import game.DevModeBridge;
import game.DevTimeController;
import game.Model;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

/**
 * Owner-only live scene inspection state.
 *
 * Target ownership stays in DevModeBridge: this class only records targets
 * already resolved by Matrix3's normal scene/menu/render paths. It never
 * performs its own scene pick and never mutates world/cache state. Presentation
 * lives in LiveInspectPanel so inspection no longer requires a floating window
 * over the game canvas.
 */
public final class LiveInspectOverlay {

    private static final int PRIORITY_TILE = 10;
    private static final int PRIORITY_GROUND_ITEM = 20;
    private static final int PRIORITY_ENTITY = 30;
    private static final int PRIORITY_SPOT_ANIMATION = 40;
    private static final int PRIORITY_PROJECTILE = 50;

    private static volatile boolean enabled;
    private static volatile boolean locked;
    private static volatile Snapshot hoverTarget;
    private static volatile Snapshot lockedTarget;
    private static volatile long pointerGeneration;
    private static volatile long observedGeneration = -1L;
    private static volatile int observedPriority = -1;
    private static volatile int pointerX = -1;
    private static volatile int pointerY = -1;

    private LiveInspectOverlay() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isLocked() {
        return locked;
    }

    public static boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            DevTimeController.reset();
            locked = false;
            hoverTarget = null;
            lockedTarget = null;
            observedGeneration = -1L;
            observedPriority = -1;
            pointerX = -1;
            pointerY = -1;
        } else {
            pointerGeneration++;
        }
    }

    public static boolean toggleLock() {
        if (!enabled) {
            return false;
        }
        if (locked) {
            locked = false;
            lockedTarget = null;
            return false;
        }
        Snapshot target = hoverTarget;
        if (target == null) {
            return false;
        }
        lockedTarget = target;
        locked = true;
        return true;
    }

    /**
     * Marks a new pointer sample so multiple Matrix3 menu entries produced for
     * the same cursor position can be ranked without tile entries overwriting
     * an entity/item target. The current target is intentionally not cleared
     * here; Matrix3 may resolve the scene target on the following game tick.
     */
    public static void pointerMoved(int x, int y) {
        if (enabled && !locked) {
            pointerX = x;
            pointerY = y;
            pointerGeneration++;
            observedPriority = -1;
        }
    }

    /**
     * Narrow renderer-side hit test used only by visual families that Matrix3
     * intentionally excludes from ordinary menu picking (spot animations and
     * projectiles). The real renderer Model owns the hit-test math.
     */
    public static boolean isPointerOverModel(Model model, Class261 transform) {
        if (!enabled || locked || model == null || transform == null
                || pointerX < 0 || pointerY < 0) {
            return false;
        }
        try {
            return model.method1376(pointerX, pointerY, transform, false, 0);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static void observeTarget(DevTarget target) {
        if (!enabled || locked || target == null) {
            return;
        }

        Snapshot current = hoverTarget;
        if (current != null && current.matches(target)) {
            observe(current, PRIORITY_ENTITY);
            return;
        }

        int[] models = new int[0];
        int[] animations = new int[0];
        String runtime;
        if (target.getType() == TargetType.OBJECT && target.getId() >= 0) {
            models = DevDefinitionBridge.getObjectModelIds(target.getId());
            DevDefinitionBridge.DefinitionInfo info =
                    DevDefinitionBridge.getObjectInfoAny(target.getId());
            animations = info == null ? new int[0] : info.getAnimationIds();
            runtime = "Scene object";
        } else {
            runtime = target.getRuntimeIndex() >= 0
                    ? "NPC index " + target.getRuntimeIndex()
                    : "NPC";
        }

        observe(new Snapshot(
                target.getType().getDisplayName(),
                target.getName(),
                target.getId(),
                target.getWorldX(),
                target.getWorldY(),
                target.getPlane(),
                runtime,
                models,
                animations,
                target),
                PRIORITY_ENTITY);
    }

    public static void observeGroundItem(int itemId, String name, int worldX, int worldY, int plane) {
        if (!enabled || locked) {
            return;
        }
        observe(new Snapshot(
                "Ground Item",
                name == null || name.length() == 0 ? "Item" : name,
                itemId,
                worldX,
                worldY,
                plane,
                "Scene ground item",
                new int[0],
                new int[0],
                null),
                PRIORITY_GROUND_ITEM);
    }

    public static void observeSpotAnimation(int graphicsId, int modelId, int animationId,
            int worldX, int worldY, int plane) {
        if (!enabled || locked) {
            return;
        }
        observe(new Snapshot(
                "GFX / SpotAnim",
                "Graphics " + graphicsId,
                graphicsId,
                worldX,
                worldY,
                plane,
                "Scene spot animation",
                ids(modelId),
                ids(animationId),
                null),
                PRIORITY_SPOT_ANIMATION);
    }

    public static void observeProjectile(int graphicsId, int modelId, int animationId,
            int worldX, int worldY, int plane) {
        if (!enabled || locked) {
            return;
        }
        observe(new Snapshot(
                "Projectile",
                "Graphics " + graphicsId,
                graphicsId,
                worldX,
                worldY,
                plane,
                "Scene projectile",
                ids(modelId),
                ids(animationId),
                null),
                PRIORITY_PROJECTILE);
    }

    public static void observeTile(int worldX, int worldY, int plane) {
        if (!enabled || locked) {
            return;
        }
        observe(new Snapshot(
                "Tile",
                "World tile",
                -1,
                worldX,
                worldY,
                plane,
                "Scene tile",
                new int[0],
                new int[0],
                null),
                PRIORITY_TILE);
    }

    public static String armCurrentObjectPlacement() {
        final Snapshot target = getCurrentTarget();
        if (!enabled || target == null || !"Object".equals(target.type) || target.definitionId < 0) {
            return null;
        }
        return DevModeBridge.armLiveObjectPlacementAt(
                target.definitionId, 0, target.worldX, target.worldY, target.plane);
    }

    public static boolean openCurrentTool() {
        final Snapshot target = getCurrentTarget();
        if (!enabled || target == null) {
            return false;
        }
        if ("Object".equals(target.type) && target.routeTarget != null) {
            LiveModelEditorWindow.open(target.routeTarget);
            return true;
        }
        if ("NPC".equals(target.type) && target.routeTarget != null) {
            DevInspectorWindow.open(target.routeTarget, true);
            return true;
        }
        return false;
    }

    public static boolean copyCurrentToClipboard() {
        final Snapshot target = getCurrentTarget();
        if (!enabled || target == null) {
            return false;
        }
        final String text = buildCopyText(target);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Immutable presentation snapshot consumed by the Client Console. This keeps
     * Swing layout concerns out of the live scene inspection owner.
     */
    public static DisplayState getDisplayState() {
        final boolean enabledNow = enabled;
        final boolean lockedNow = locked;
        final Snapshot target = lockedNow ? lockedTarget : hoverTarget;

        if (target == null) {
            return new DisplayState(
                    enabledNow,
                    lockedNow,
                    false,
                    enabledNow ? (lockedNow ? "LOCKED" : "HOVER") : "OFF",
                    "-",
                    enabledNow ? "Move cursor over the world" : "Live Inspect is disabled",
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    DevTimeController.getStatusText(),
                    "-",
                    "-",
                    "-",
                    "-",
                    false,
                    false);
        }

        return new DisplayState(
                enabledNow,
                lockedNow,
                true,
                lockedNow ? "LOCKED" : "HOVER",
                target.type,
                target.name,
                target.definitionId >= 0 ? Integer.toString(target.definitionId) : "-",
                displayIds(target.modelIds, 8),
                displayIds(target.animationIds, 8),
                relationshipText(target),
                routeText(target),
                DevTimeController.getStatusText(),
                target.worldX + ", " + target.worldY + ", " + target.plane,
                regionText(target.worldX, target.worldY),
                chunkText(target.worldX, target.worldY),
                target.runtime,
                canOpenTool(target),
                "Object".equals(target.type) && target.definitionId >= 0);
    }

    private static boolean canOpenTool(Snapshot target) {
        return target != null && target.routeTarget != null
                && ("Object".equals(target.type) || "NPC".equals(target.type));
    }

    private static void observe(Snapshot target, int priority) {
        long generation = pointerGeneration;
        if (observedGeneration != generation) {
            observedGeneration = generation;
            observedPriority = priority;
        } else if (priority < observedPriority) {
            return;
        } else {
            observedPriority = priority;
        }

        if (!target.equalsIdentity(hoverTarget)) {
            hoverTarget = target;
        }
    }

    private static Snapshot getCurrentTarget() {
        return locked ? lockedTarget : hoverTarget;
    }

    private static String buildCopyText(Snapshot target) {
        StringBuilder out = new StringBuilder(320);
        out.append("Matrix3 Live Inspect\n");
        out.append("State: ").append(locked ? "LOCKED" : "HOVER").append('\n');
        out.append("Type: ").append(target.type).append('\n');
        out.append("Name: ").append(target.name).append('\n');
        out.append("Definition ID: ")
                .append(target.definitionId >= 0 ? Integer.toString(target.definitionId) : "-")
                .append('\n');
        out.append("Model IDs: ").append(joinIds(target.modelIds)).append('\n');
        out.append("Animation IDs: ").append(joinIds(target.animationIds)).append('\n');
        out.append("Relationship: ").append(relationshipText(target)).append('\n');
        out.append("Open Route: ").append(routeText(target)).append('\n');
        out.append("Visual Time: ").append(DevTimeController.getStatusText()).append('\n');
        out.append("World Tile: ")
                .append(target.worldX).append(", ")
                .append(target.worldY).append(", ")
                .append(target.plane).append('\n');
        out.append("Region: ").append(regionText(target.worldX, target.worldY)).append('\n');
        out.append("Chunk: ").append(chunkText(target.worldX, target.worldY)).append('\n');
        out.append("Runtime: ").append(target.runtime);
        return out.toString();
    }

    private static String relationshipText(Snapshot target) {
        if (target == null) return "-";
        if ("Object".equals(target.type)) {
            return "Object " + target.definitionId + " -> Model(s) " + joinIds(target.modelIds)
                    + " -> Animation(s) " + joinIds(target.animationIds);
        }
        if ("GFX / SpotAnim".equals(target.type) || "Projectile".equals(target.type)) {
            return "Graphics " + target.definitionId + " -> Model " + joinIds(target.modelIds)
                    + " -> Animation " + joinIds(target.animationIds);
        }
        if ("Ground Item".equals(target.type)) {
            return "Item " + target.definitionId + " -> Item definition";
        }
        if ("NPC".equals(target.type)) {
            return "NPC " + target.definitionId + " -> " + target.runtime;
        }
        if ("Tile".equals(target.type)) {
            return "Tile -> Region " + regionText(target.worldX, target.worldY)
                    + " -> Chunk " + chunkText(target.worldX, target.worldY);
        }
        return "-";
    }

    private static String routeText(Snapshot target) {
        if (target == null) return "-";
        if ("Object".equals(target.type)) {
            return "O -> Live Place (auto type " + DevDefinitionBridge.getPreferredObjectType(target.definitionId)
                    + ")   F8 -> Live Model Editor";
        }
        if ("NPC".equals(target.type)) return "F8 -> Dev Inspector";
        if ("Tile".equals(target.type)) return "Tile inspection only";
        if ("Ground Item".equals(target.type)) return "No direct item editor route yet";
        if ("GFX / SpotAnim".equals(target.type) || "Projectile".equals(target.type))
            return "Specialist visual route not verified yet";
        return "-";
    }

    private static String regionText(int worldX, int worldY) {
        int regionId = (worldX >> 6 << 8) | worldY >> 6;
        return regionId + "  local " + (worldX & 63) + ", " + (worldY & 63);
    }

    private static String chunkText(int worldX, int worldY) {
        return (worldX >> 3) + ", " + (worldY >> 3)
                + "  local " + (worldX & 7) + ", " + (worldY & 7);
    }

    private static int[] ids(int value) {
        return value >= 0 ? new int[] { value } : new int[0];
    }

    private static String displayIds(int[] ids, int max) {
        if (ids == null || ids.length == 0) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        int count = Math.min(ids.length, max);
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(", ");
            out.append(ids[i]);
        }
        if (ids.length > count) {
            out.append(" +").append(ids.length - count);
        }
        return out.toString();
    }

    private static String joinIds(int[] ids) {
        if (ids == null || ids.length == 0) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) out.append(", ");
            out.append(ids[i]);
        }
        return out.toString();
    }

    public static final class DisplayState {
        private final boolean enabled;
        private final boolean locked;
        private final boolean hasTarget;
        private final String state;
        private final String type;
        private final String name;
        private final String definitionId;
        private final String modelIds;
        private final String animationIds;
        private final String relationship;
        private final String route;
        private final String visualTime;
        private final String worldTile;
        private final String region;
        private final String chunk;
        private final String runtime;
        private final boolean canOpenTool;
        private final boolean canPlaceObject;

        private DisplayState(boolean enabled, boolean locked, boolean hasTarget,
                String state, String type, String name, String definitionId,
                String modelIds, String animationIds, String relationship,
                String route, String visualTime, String worldTile, String region,
                String chunk, String runtime, boolean canOpenTool,
                boolean canPlaceObject) {
            this.enabled = enabled;
            this.locked = locked;
            this.hasTarget = hasTarget;
            this.state = state;
            this.type = type;
            this.name = name;
            this.definitionId = definitionId;
            this.modelIds = modelIds;
            this.animationIds = animationIds;
            this.relationship = relationship;
            this.route = route;
            this.visualTime = visualTime;
            this.worldTile = worldTile;
            this.region = region;
            this.chunk = chunk;
            this.runtime = runtime;
            this.canOpenTool = canOpenTool;
            this.canPlaceObject = canPlaceObject;
        }

        public boolean isEnabled() { return enabled; }
        public boolean isLocked() { return locked; }
        public boolean hasTarget() { return hasTarget; }
        public String getState() { return state; }
        public String getType() { return type; }
        public String getName() { return name; }
        public String getDefinitionId() { return definitionId; }
        public String getModelIds() { return modelIds; }
        public String getAnimationIds() { return animationIds; }
        public String getRelationship() { return relationship; }
        public String getRoute() { return route; }
        public String getVisualTime() { return visualTime; }
        public String getWorldTile() { return worldTile; }
        public String getRegion() { return region; }
        public String getChunk() { return chunk; }
        public String getRuntime() { return runtime; }
        public boolean canOpenTool() { return canOpenTool; }
        public boolean canPlaceObject() { return canPlaceObject; }
    }

    private static final class Snapshot {
        private final String type;
        private final String name;
        private final int definitionId;
        private final int worldX;
        private final int worldY;
        private final int plane;
        private final String runtime;
        private final int[] modelIds;
        private final int[] animationIds;
        private final DevTarget routeTarget;

        private Snapshot(String type, String name, int definitionId,
                int worldX, int worldY, int plane, String runtime,
                int[] modelIds, int[] animationIds, DevTarget routeTarget) {
            this.type = type;
            this.name = name;
            this.definitionId = definitionId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.runtime = runtime;
            this.modelIds = modelIds == null ? new int[0] : modelIds.clone();
            this.animationIds = animationIds == null ? new int[0] : animationIds.clone();
            this.routeTarget = routeTarget;
        }

        private boolean matches(DevTarget target) {
            if (target == null) return false;
            return type.equals(target.getType().getDisplayName())
                    && definitionId == target.getId()
                    && worldX == target.getWorldX()
                    && worldY == target.getWorldY()
                    && plane == target.getPlane()
                    && (target.getType() != TargetType.NPC
                        || runtime.equals("NPC index " + target.getRuntimeIndex()));
        }

        private boolean equalsIdentity(Snapshot other) {
            if (other == null) return false;
            return definitionId == other.definitionId
                    && worldX == other.worldX
                    && worldY == other.worldY
                    && plane == other.plane
                    && type.equals(other.type)
                    && name.equals(other.name)
                    && runtime.equals(other.runtime)
                    && Arrays.equals(modelIds, other.modelIds)
                    && Arrays.equals(animationIds, other.animationIds);
        }
    }
}
