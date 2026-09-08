package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-thread bridge for the Client Console Interface Editor.
 *
 * Swing only queues requests and reads immutable snapshots. All Matrix3
 * InterfaceDefinitions reads/writes and open-interface discovery happen from
 * the normal client cycle.
 */
public final class ClientConsoleInterfaceBridge {

    private static final int MAX_COMPONENT_PROBE = 2048;
    private static final int MAX_ACTIONS_PER_CYCLE = 16;

    private static final int BASE_X_ENCODE = 1043259307;
    private static final int BASE_Y_ENCODE = -1914502065;
    private static final int BASE_WIDTH_ENCODE = 1256520373;
    private static final int BASE_HEIGHT_ENCODE = 200498991;

    private static final int RUNTIME_X_ENCODE = -1222476983;
    private static final int RUNTIME_Y_ENCODE = -314551123;
    private static final int RUNTIME_WIDTH_ENCODE = -628102339;
    private static final int RUNTIME_HEIGHT_ENCODE = -2088867597;

    private static final int SPRITE_ENCODE = -249108765;

    private static final int ROOT_INTERFACE_DECODE = -507155049;
    private static final int OPEN_INTERFACE_ID_DECODE = 493419501;
    private static final long OPEN_INTERFACE_PARENT_DECODE = 381237825124074065L;

    private static final AtomicInteger REQUESTED_INTERFACE = new AtomicInteger(-1);
    private static final Queue<EditorAction> ACTION_QUEUE = new ConcurrentLinkedQueue<EditorAction>();

    /** Client-thread only. */
    private static final Map<Integer, OverrideState> LIVE_OVERRIDES = new HashMap<Integer, OverrideState>();
    /** Client-thread only. */
    private static final Set<Integer> PREVIOUS_OPEN_INTERFACES = new HashSet<Integer>();

    private static volatile InterfaceSnapshot latestSnapshot = InterfaceSnapshot.empty();
    private static volatile InterfaceCatalogSnapshot latestCatalog = InterfaceCatalogSnapshot.empty();
    private static volatile String latestStatus = "Enter an interface ID to begin.";
    private static long snapshotSequence;
    private static long catalogSequence;
    private static int activeInterfaceId = -1;

    private ClientConsoleInterfaceBridge() {
    }

    public static void requestSnapshot(int interfaceId) {
        if (interfaceId < 0 || interfaceId > 65535) {
            latestStatus = "Interface ID must be between 0 and 65535.";
            return;
        }
        REQUESTED_INTERFACE.set(interfaceId);
    }

    public static InterfaceSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    public static InterfaceCatalogSnapshot getLatestCatalog() {
        return latestCatalog;
    }

    public static String getLatestStatus() {
        return latestStatus;
    }

    public static String queueApply(ComponentOverride override) {
        if (override == null) {
            return "No component values were provided.";
        }
        ACTION_QUEUE.offer(EditorAction.apply(override));
        return null;
    }

    public static String queueResetComponent(int interfaceId, int componentId) {
        if (!isValidTarget(interfaceId, componentId)) {
            return "Invalid interface/component target.";
        }
        ACTION_QUEUE.offer(EditorAction.resetComponent(interfaceId, componentId));
        return null;
    }

    public static String queueResetInterface(int interfaceId) {
        if (interfaceId < 0 || interfaceId > 65535) {
            return "Invalid interface target.";
        }
        ACTION_QUEUE.offer(EditorAction.resetInterface(interfaceId));
        return null;
    }

    /** Called from the normal Matrix3 client cycle. */
    public static void flushInterfaceEditorRequests() {
        for (int processed = 0; processed < MAX_ACTIONS_PER_CYCLE; processed++) {
            EditorAction action = ACTION_QUEUE.poll();
            if (action == null) {
                break;
            }
            process(action);
        }

        applyLiveOverrides();
        refreshInterfaceCatalog();

        int requestedInterface = REQUESTED_INTERFACE.getAndSet(-1);
        if (requestedInterface >= 0) {
            latestSnapshot = buildSnapshot(requestedInterface);
        }
    }

    private static void refreshInterfaceCatalog() {
        int rootInterfaceId = client.anInt8790 * ROOT_INTERFACE_DECODE;
        int totalInterfaceCount = Class534.aClass83Array5975 == null ? 0 : Class534.aClass83Array5975.length;

        List<OpenInterfaceSnapshot> open = new ArrayList<OpenInterfaceSnapshot>();
        Set<Integer> currentOpenIds = new HashSet<Integer>();

        if (rootInterfaceId >= 0 && rootInterfaceId <= 65535) {
            currentOpenIds.add(Integer.valueOf(rootInterfaceId));
            open.add(new OpenInterfaceSnapshot(rootInterfaceId, -1, true,
                    getLoadedComponentCount(rootInterfaceId), false));
        }

        if (client.aClass676_8760 != null) {
            for (Object value : client.aClass676_8760) {
                if (!(value instanceof Class572_Sub29)) {
                    continue;
                }
                Class572_Sub29 node = (Class572_Sub29) value;
                int interfaceId = node.anInt9301 * OPEN_INTERFACE_ID_DECODE;
                if (interfaceId < 0 || interfaceId > 65535 || interfaceId == rootInterfaceId) {
                    continue;
                }
                int parentHash = (int) (node.hash * OPEN_INTERFACE_PARENT_DECODE);
                currentOpenIds.add(Integer.valueOf(interfaceId));
                open.add(new OpenInterfaceSnapshot(interfaceId, parentHash, false,
                        getLoadedComponentCount(interfaceId), false));
            }
        }

        int newlyOpened = pickMostSubstantialNewInterface(open);
        if (newlyOpened >= 0) {
            activeInterfaceId = newlyOpened;
        } else if (!currentOpenIds.contains(Integer.valueOf(activeInterfaceId))) {
            activeInterfaceId = pickMostSubstantialOpenInterface(open, rootInterfaceId);
        }
        if (activeInterfaceId < 0 && rootInterfaceId >= 0) {
            activeInterfaceId = rootInterfaceId;
        }

        List<OpenInterfaceSnapshot> marked = new ArrayList<OpenInterfaceSnapshot>(open.size());
        for (OpenInterfaceSnapshot entry : open) {
            marked.add(new OpenInterfaceSnapshot(entry.interfaceId, entry.parentHash,
                    entry.root, entry.componentCount, entry.interfaceId == activeInterfaceId));
        }
        Collections.sort(marked, new Comparator<OpenInterfaceSnapshot>() {
            @Override
            public int compare(OpenInterfaceSnapshot left, OpenInterfaceSnapshot right) {
                if (left.active != right.active)
                    return left.active ? -1 : 1;
                if (left.root != right.root)
                    return left.root ? -1 : 1;
                if (left.componentCount != right.componentCount)
                    return right.componentCount - left.componentCount;
                return left.interfaceId - right.interfaceId;
            }
        });

        InterfaceCatalogSnapshot candidate = new InterfaceCatalogSnapshot(
                0L, rootInterfaceId, activeInterfaceId, totalInterfaceCount,
                marked.toArray(new OpenInterfaceSnapshot[marked.size()]));
        if (!candidate.sameContent(latestCatalog)) {
            catalogSequence++;
            latestCatalog = new InterfaceCatalogSnapshot(
                    catalogSequence, rootInterfaceId, activeInterfaceId, totalInterfaceCount,
                    marked.toArray(new OpenInterfaceSnapshot[marked.size()]));
        }

        PREVIOUS_OPEN_INTERFACES.clear();
        PREVIOUS_OPEN_INTERFACES.addAll(currentOpenIds);
    }

    private static int pickMostSubstantialNewInterface(List<OpenInterfaceSnapshot> open) {
        int bestInterfaceId = -1;
        int bestComponentCount = -1;
        for (OpenInterfaceSnapshot entry : open) {
            if (entry.root || PREVIOUS_OPEN_INTERFACES.contains(Integer.valueOf(entry.interfaceId))) {
                continue;
            }
            if (entry.componentCount > bestComponentCount) {
                bestInterfaceId = entry.interfaceId;
                bestComponentCount = entry.componentCount;
            }
        }
        return bestInterfaceId;
    }

    private static int pickMostSubstantialOpenInterface(List<OpenInterfaceSnapshot> open, int rootInterfaceId) {
        int bestInterfaceId = -1;
        int bestComponentCount = -1;
        for (OpenInterfaceSnapshot entry : open) {
            if (entry.interfaceId == rootInterfaceId || entry.root) {
                continue;
            }
            if (entry.componentCount > bestComponentCount) {
                bestInterfaceId = entry.interfaceId;
                bestComponentCount = entry.componentCount;
            }
        }
        return bestInterfaceId;
    }

    private static int getLoadedComponentCount(int interfaceId) {
        if (Class534.aClass83Array5975 == null || interfaceId < 0
                || interfaceId >= Class534.aClass83Array5975.length) {
            return 0;
        }
        Class83 group = Class534.aClass83Array5975[interfaceId];
        if (group == null || group.aClass73Array1081 == null) {
            return 0;
        }
        return group.aClass73Array1081.length;
    }

    private static void process(EditorAction action) {
        if (action.type == EditorAction.APPLY) {
            applyOverrideAction(action.override);
            return;
        }
        if (action.type == EditorAction.RESET_COMPONENT) {
            resetComponent(action.interfaceId, action.componentId);
            return;
        }
        if (action.type == EditorAction.RESET_INTERFACE) {
            resetInterface(action.interfaceId);
        }
    }

    private static void applyOverrideAction(ComponentOverride override) {
        InterfaceDefinitions definition = getComponent(override.interfaceId, override.componentId);
        if (definition == null) {
            latestStatus = "Interface " + override.interfaceId + ":" + override.componentId + " is not loaded.";
            return;
        }

        int hash = componentHash(override.interfaceId, override.componentId);
        OverrideState state = LIVE_OVERRIDES.get(Integer.valueOf(hash));
        if (state == null) {
            state = new OverrideState(OriginalState.capture(definition), override);
            LIVE_OVERRIDES.put(Integer.valueOf(hash), state);
        } else {
            state.override = override;
        }

        applyOverride(definition, override);
        latestStatus = "Live override active for " + override.interfaceId + ":" + override.componentId + ".";
        requestSnapshot(override.interfaceId);
    }

    private static void applyLiveOverrides() {
        for (Map.Entry<Integer, OverrideState> entry : LIVE_OVERRIDES.entrySet()) {
            int hash = entry.getKey().intValue();
            InterfaceDefinitions definition = getComponent(hash >>> 16, hash & 0xffff);
            if (definition != null) {
                applyOverride(definition, entry.getValue().override);
            }
        }
    }

    private static void applyOverride(InterfaceDefinitions definition, ComponentOverride override) {
        definition.anInt819 = override.baseX * BASE_X_ENCODE;
        definition.anInt793 = override.baseY * BASE_Y_ENCODE;
        definition.anInt760 = override.baseWidth * BASE_WIDTH_ENCODE;
        definition.anInt761 = override.baseHeight * BASE_HEIGHT_ENCODE;

        definition.aByte756 = (byte) override.xAlignment;
        definition.aByte757 = (byte) override.yAlignment;
        definition.aByte811 = (byte) override.widthAlignment;
        definition.aByte755 = (byte) override.heightAlignment;

        if (override.pinRuntimeGeometry) {
            definition.anInt762 = override.runtimeX * RUNTIME_X_ENCODE;
            definition.anInt842 = override.runtimeY * RUNTIME_Y_ENCODE;
            definition.anInt764 = override.runtimeWidth * RUNTIME_WIDTH_ENCODE;
            definition.anInt765 = override.runtimeHeight * RUNTIME_HEIGHT_ENCODE;
        }

        if (override.overrideText) {
            definition.aString829 = override.text == null ? "" : override.text;
        }
        if (override.overrideSprite) {
            definition.anInt783 = override.spriteId * SPRITE_ENCODE;
        }

        Class555.method6575(definition, (short) 0);
    }

    private static void resetComponent(int interfaceId, int componentId) {
        int hash = componentHash(interfaceId, componentId);
        OverrideState state = LIVE_OVERRIDES.remove(Integer.valueOf(hash));
        if (state == null) {
            latestStatus = "No live override exists for " + interfaceId + ":" + componentId + ".";
            requestSnapshot(interfaceId);
            return;
        }

        InterfaceDefinitions definition = getComponent(interfaceId, componentId);
        if (definition != null) {
            state.original.restore(definition);
            Class555.method6575(definition, (short) 0);
        }
        latestStatus = "Reset " + interfaceId + ":" + componentId + " to its pre-editor values.";
        requestSnapshot(interfaceId);
    }

    private static void resetInterface(int interfaceId) {
        int restored = 0;
        Iterator<Map.Entry<Integer, OverrideState>> iterator = LIVE_OVERRIDES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, OverrideState> entry = iterator.next();
            int hash = entry.getKey().intValue();
            if ((hash >>> 16) != interfaceId) {
                continue;
            }

            InterfaceDefinitions definition = getComponent(interfaceId, hash & 0xffff);
            if (definition != null) {
                entry.getValue().original.restore(definition);
                Class555.method6575(definition, (short) 0);
            }
            iterator.remove();
            restored++;
        }
        latestStatus = "Reset " + restored + " live override" + (restored == 1 ? "" : "s")
                + " on interface " + interfaceId + ".";
        requestSnapshot(interfaceId);
    }

    private static InterfaceSnapshot buildSnapshot(int interfaceId) {
        List<ComponentSnapshot> components = new ArrayList<ComponentSnapshot>();
        int boundary = -1;

        for (int componentId = 0; componentId < MAX_COMPONENT_PROBE; componentId++) {
            InterfaceDefinitions definition;
            try {
                definition = Class512.method6083(componentHash(interfaceId, componentId), (short) 3691);
            } catch (ArrayIndexOutOfBoundsException e) {
                boundary = componentId;
                break;
            } catch (RuntimeException e) {
                latestStatus = "Interface " + interfaceId + " read failed at component " + componentId + ": "
                        + e.getClass().getSimpleName();
                return new InterfaceSnapshot(interfaceId, nextSequence(), componentId,
                        components.toArray(new ComponentSnapshot[components.size()]), latestStatus);
            }

            if (definition != null) {
                components.add(ComponentSnapshot.capture(interfaceId, componentId, definition,
                        LIVE_OVERRIDES.containsKey(Integer.valueOf(componentHash(interfaceId, componentId)))));
            }
        }

        if (boundary < 0) {
            boundary = MAX_COMPONENT_PROBE;
        }

        String message = "Loaded interface " + interfaceId + " - " + components.size()
                + " component" + (components.size() == 1 ? "" : "s")
                + " (boundary " + boundary + ").";
        latestStatus = message;
        return new InterfaceSnapshot(interfaceId, nextSequence(), boundary,
                components.toArray(new ComponentSnapshot[components.size()]), message);
    }

    private static long nextSequence() {
        snapshotSequence++;
        return snapshotSequence;
    }

    private static InterfaceDefinitions getComponent(int interfaceId, int componentId) {
        if (!isValidTarget(interfaceId, componentId)) {
            return null;
        }
        try {
            return Class512.method6083(componentHash(interfaceId, componentId), (short) 3691);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isValidTarget(int interfaceId, int componentId) {
        return interfaceId >= 0 && interfaceId <= 65535 && componentId >= 0 && componentId <= 65535;
    }

    private static int componentHash(int interfaceId, int componentId) {
        return (interfaceId << 16) | (componentId & 0xffff);
    }

    public static final class ComponentOverride {
        private final int interfaceId;
        private final int componentId;
        private final int baseX;
        private final int baseY;
        private final int baseWidth;
        private final int baseHeight;
        private final int runtimeX;
        private final int runtimeY;
        private final int runtimeWidth;
        private final int runtimeHeight;
        private final int xAlignment;
        private final int yAlignment;
        private final int widthAlignment;
        private final int heightAlignment;
        private final boolean pinRuntimeGeometry;
        private final boolean overrideText;
        private final String text;
        private final boolean overrideSprite;
        private final int spriteId;

        public ComponentOverride(int interfaceId, int componentId,
                int baseX, int baseY, int baseWidth, int baseHeight,
                int runtimeX, int runtimeY, int runtimeWidth, int runtimeHeight,
                int xAlignment, int yAlignment, int widthAlignment, int heightAlignment,
                boolean pinRuntimeGeometry,
                boolean overrideText, String text,
                boolean overrideSprite, int spriteId) {
            this.interfaceId = interfaceId;
            this.componentId = componentId;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
            this.runtimeX = runtimeX;
            this.runtimeY = runtimeY;
            this.runtimeWidth = runtimeWidth;
            this.runtimeHeight = runtimeHeight;
            this.xAlignment = xAlignment;
            this.yAlignment = yAlignment;
            this.widthAlignment = widthAlignment;
            this.heightAlignment = heightAlignment;
            this.pinRuntimeGeometry = pinRuntimeGeometry;
            this.overrideText = overrideText;
            this.text = text;
            this.overrideSprite = overrideSprite;
            this.spriteId = spriteId;
        }
    }

    public static final class InterfaceCatalogSnapshot {
        private final long sequence;
        private final int rootInterfaceId;
        private final int activeInterfaceId;
        private final int totalInterfaceCount;
        private final OpenInterfaceSnapshot[] openInterfaces;

        private InterfaceCatalogSnapshot(long sequence, int rootInterfaceId, int activeInterfaceId,
                int totalInterfaceCount, OpenInterfaceSnapshot[] openInterfaces) {
            this.sequence = sequence;
            this.rootInterfaceId = rootInterfaceId;
            this.activeInterfaceId = activeInterfaceId;
            this.totalInterfaceCount = totalInterfaceCount;
            this.openInterfaces = openInterfaces;
        }

        private static InterfaceCatalogSnapshot empty() {
            return new InterfaceCatalogSnapshot(0L, -1, -1, 0, new OpenInterfaceSnapshot[0]);
        }

        public static InterfaceCatalogSnapshot emptyForUi() {
            return empty();
        }

        public long getSequence() {
            return sequence;
        }

        public int getRootInterfaceId() {
            return rootInterfaceId;
        }

        public int getActiveInterfaceId() {
            return activeInterfaceId;
        }

        public int getTotalInterfaceCount() {
            return totalInterfaceCount;
        }

        public OpenInterfaceSnapshot[] getOpenInterfaces() {
            return openInterfaces.clone();
        }

        public OpenInterfaceSnapshot findOpen(int interfaceId) {
            for (OpenInterfaceSnapshot entry : openInterfaces) {
                if (entry.interfaceId == interfaceId)
                    return entry;
            }
            return null;
        }

        private boolean sameContent(InterfaceCatalogSnapshot other) {
            if (other == null || rootInterfaceId != other.rootInterfaceId
                    || activeInterfaceId != other.activeInterfaceId
                    || totalInterfaceCount != other.totalInterfaceCount
                    || openInterfaces.length != other.openInterfaces.length) {
                return false;
            }
            for (int index = 0; index < openInterfaces.length; index++) {
                OpenInterfaceSnapshot left = openInterfaces[index];
                OpenInterfaceSnapshot right = other.openInterfaces[index];
                if (left.interfaceId != right.interfaceId
                        || left.parentHash != right.parentHash
                        || left.root != right.root
                        || left.componentCount != right.componentCount
                        || left.active != right.active) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class OpenInterfaceSnapshot {
        private final int interfaceId;
        private final int parentHash;
        private final boolean root;
        private final int componentCount;
        private final boolean active;

        private OpenInterfaceSnapshot(int interfaceId, int parentHash, boolean root,
                int componentCount, boolean active) {
            this.interfaceId = interfaceId;
            this.parentHash = parentHash;
            this.root = root;
            this.componentCount = componentCount;
            this.active = active;
        }

        public int getInterfaceId() {
            return interfaceId;
        }

        public int getParentHash() {
            return parentHash;
        }

        public boolean isRoot() {
            return root;
        }

        public int getComponentCount() {
            return componentCount;
        }

        public boolean isActive() {
            return active;
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder();
            if (active)
                text.append("● ");
            text.append(interfaceId);
            text.append(root ? "  ROOT" : "  OPEN");
            if (componentCount > 0)
                text.append("  ").append(componentCount).append(" comps");
            return text.toString();
        }
    }

    public static final class InterfaceSnapshot {
        private final int interfaceId;
        private final long sequence;
        private final int componentBoundary;
        private final ComponentSnapshot[] components;
        private final String status;

        private InterfaceSnapshot(int interfaceId, long sequence, int componentBoundary,
                ComponentSnapshot[] components, String status) {
            this.interfaceId = interfaceId;
            this.sequence = sequence;
            this.componentBoundary = componentBoundary;
            this.components = components;
            this.status = status;
        }

        private static InterfaceSnapshot empty() {
            return new InterfaceSnapshot(-1, 0L, 0, new ComponentSnapshot[0],
                    "Enter an interface ID to begin.");
        }

        public int getInterfaceId() {
            return interfaceId;
        }

        public long getSequence() {
            return sequence;
        }

        public int getComponentBoundary() {
            return componentBoundary;
        }

        public ComponentSnapshot[] getComponents() {
            return components.clone();
        }

        public String getStatus() {
            return status;
        }

        public ComponentSnapshot findComponent(int componentId) {
            for (ComponentSnapshot component : components) {
                if (component.componentId == componentId) {
                    return component;
                }
            }
            return null;
        }
    }

    public static final class ComponentSnapshot {
        private final int interfaceId;
        private final int componentId;
        private final int type;
        private final int parentHash;
        private final int baseX;
        private final int baseY;
        private final int baseWidth;
        private final int baseHeight;
        private final int runtimeX;
        private final int runtimeY;
        private final int runtimeWidth;
        private final int runtimeHeight;
        private final int xAlignment;
        private final int yAlignment;
        private final int widthAlignment;
        private final int heightAlignment;
        private final int spriteId;
        private final int itemId;
        private final int staticChildren;
        private final int dynamicChildren;
        private final String text;
        private final String label;
        private final boolean overridden;

        private ComponentSnapshot(int interfaceId, int componentId, int type, int parentHash,
                int baseX, int baseY, int baseWidth, int baseHeight,
                int runtimeX, int runtimeY, int runtimeWidth, int runtimeHeight,
                int xAlignment, int yAlignment, int widthAlignment, int heightAlignment,
                int spriteId, int itemId, int staticChildren, int dynamicChildren,
                String text, String label, boolean overridden) {
            this.interfaceId = interfaceId;
            this.componentId = componentId;
            this.type = type;
            this.parentHash = parentHash;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
            this.runtimeX = runtimeX;
            this.runtimeY = runtimeY;
            this.runtimeWidth = runtimeWidth;
            this.runtimeHeight = runtimeHeight;
            this.xAlignment = xAlignment;
            this.yAlignment = yAlignment;
            this.widthAlignment = widthAlignment;
            this.heightAlignment = heightAlignment;
            this.spriteId = spriteId;
            this.itemId = itemId;
            this.staticChildren = staticChildren;
            this.dynamicChildren = dynamicChildren;
            this.text = text;
            this.label = label;
            this.overridden = overridden;
        }

        private static ComponentSnapshot capture(int interfaceId, int componentId,
                InterfaceDefinitions definition, boolean overridden) {
            String text = safe(definition.aString829);
            String label = firstNonEmpty(text,
                    safe(definition.aString849),
                    safe(definition.aString847),
                    safe(definition.aString748),
                    safe(definition.aString856),
                    safe(definition.aString747));

            return new ComponentSnapshot(
                    interfaceId,
                    componentId,
                    definition.anInt752 * -1285279191,
                    definition.anInt768 * -1604592419,
                    definition.anInt819 * 329065219,
                    definition.anInt793 * -885681489,
                    definition.anInt760 * 1473094557,
                    definition.anInt761 * 1647331279,
                    definition.anInt762 * 278882041,
                    definition.anInt842 * -1681379547,
                    definition.anInt764 * 669238293,
                    definition.anInt765 * 1360982075,
                    definition.aByte756,
                    definition.aByte757,
                    definition.aByte811,
                    definition.aByte755,
                    definition.anInt783 * 1554484939,
                    definition.nvmtheindexisotherone * 411192987,
                    definition.aClass73Array916 == null ? 0 : definition.aClass73Array916.length,
                    definition.aClass73Array917 == null ? 0 : definition.aClass73Array917.length,
                    text,
                    label,
                    overridden);
        }

        public int getInterfaceId() {
            return interfaceId;
        }

        public int getComponentId() {
            return componentId;
        }

        public int getType() {
            return type;
        }

        public int getParentHash() {
            return parentHash;
        }

        public int getParentComponentId() {
            return parentHash == -1 ? -1 : parentHash & 0xffff;
        }

        public int getBaseX() {
            return baseX;
        }

        public int getBaseY() {
            return baseY;
        }

        public int getBaseWidth() {
            return baseWidth;
        }

        public int getBaseHeight() {
            return baseHeight;
        }

        public int getRuntimeX() {
            return runtimeX;
        }

        public int getRuntimeY() {
            return runtimeY;
        }

        public int getRuntimeWidth() {
            return runtimeWidth;
        }

        public int getRuntimeHeight() {
            return runtimeHeight;
        }

        public int getXAlignment() {
            return xAlignment;
        }

        public int getYAlignment() {
            return yAlignment;
        }

        public int getWidthAlignment() {
            return widthAlignment;
        }

        public int getHeightAlignment() {
            return heightAlignment;
        }

        public int getSpriteId() {
            return spriteId;
        }

        public int getItemId() {
            return itemId;
        }

        public int getStaticChildren() {
            return staticChildren;
        }

        public int getDynamicChildren() {
            return dynamicChildren;
        }

        public String getText() {
            return text;
        }

        public String getLabel() {
            return label;
        }

        public boolean isOverridden() {
            return overridden;
        }

        public String getSearchText() {
            return (componentId + " " + type + " " + label).toLowerCase();
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String firstNonEmpty(String... values) {
            for (String value : values) {
                if (value != null && value.length() > 0) {
                    return value;
                }
            }
            return "";
        }
    }

    private static final class OriginalState {
        private final int anInt819;
        private final int anInt793;
        private final int anInt760;
        private final int anInt761;
        private final int anInt762;
        private final int anInt842;
        private final int anInt764;
        private final int anInt765;
        private final byte aByte756;
        private final byte aByte757;
        private final byte aByte811;
        private final byte aByte755;
        private final String text;
        private final int sprite;

        private OriginalState(InterfaceDefinitions definition) {
            anInt819 = definition.anInt819;
            anInt793 = definition.anInt793;
            anInt760 = definition.anInt760;
            anInt761 = definition.anInt761;
            anInt762 = definition.anInt762;
            anInt842 = definition.anInt842;
            anInt764 = definition.anInt764;
            anInt765 = definition.anInt765;
            aByte756 = definition.aByte756;
            aByte757 = definition.aByte757;
            aByte811 = definition.aByte811;
            aByte755 = definition.aByte755;
            text = definition.aString829;
            sprite = definition.anInt783;
        }

        private static OriginalState capture(InterfaceDefinitions definition) {
            return new OriginalState(definition);
        }

        private void restore(InterfaceDefinitions definition) {
            definition.anInt819 = anInt819;
            definition.anInt793 = anInt793;
            definition.anInt760 = anInt760;
            definition.anInt761 = anInt761;
            definition.anInt762 = anInt762;
            definition.anInt842 = anInt842;
            definition.anInt764 = anInt764;
            definition.anInt765 = anInt765;
            definition.aByte756 = aByte756;
            definition.aByte757 = aByte757;
            definition.aByte811 = aByte811;
            definition.aByte755 = aByte755;
            definition.aString829 = text;
            definition.anInt783 = sprite;
        }
    }

    private static final class OverrideState {
        private final OriginalState original;
        private ComponentOverride override;

        private OverrideState(OriginalState original, ComponentOverride override) {
            this.original = original;
            this.override = override;
        }
    }

    private static final class EditorAction {
        private static final int APPLY = 1;
        private static final int RESET_COMPONENT = 2;
        private static final int RESET_INTERFACE = 3;

        private final int type;
        private final int interfaceId;
        private final int componentId;
        private final ComponentOverride override;

        private EditorAction(int type, int interfaceId, int componentId, ComponentOverride override) {
            this.type = type;
            this.interfaceId = interfaceId;
            this.componentId = componentId;
            this.override = override;
        }

        private static EditorAction apply(ComponentOverride override) {
            return new EditorAction(APPLY, override.interfaceId, override.componentId, override);
        }

        private static EditorAction resetComponent(int interfaceId, int componentId) {
            return new EditorAction(RESET_COMPONENT, interfaceId, componentId, null);
        }

        private static EditorAction resetInterface(int interfaceId) {
            return new EditorAction(RESET_INTERFACE, interfaceId, -1, null);
        }
    }
}
