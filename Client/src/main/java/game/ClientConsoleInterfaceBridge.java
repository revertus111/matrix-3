package game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-thread bridge for the Client Console Interface Editor.
 *
 * Swing only queues requests and reads immutable snapshots. All Matrix3
 * InterfaceDefinitions reads/writes happen from the normal client cycle.
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

    private static final AtomicInteger REQUESTED_INTERFACE = new AtomicInteger(-1);
    private static final Queue<EditorAction> ACTION_QUEUE = new ConcurrentLinkedQueue<EditorAction>();

    /** Client-thread only. */
    private static final Map<Integer, OverrideState> LIVE_OVERRIDES = new HashMap<Integer, OverrideState>();

    private static volatile InterfaceSnapshot latestSnapshot = InterfaceSnapshot.empty();
    private static volatile String latestStatus = "Enter an interface ID to begin.";
    private static long snapshotSequence;

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

        int requestedInterface = REQUESTED_INTERFACE.getAndSet(-1);
        if (requestedInterface >= 0) {
            latestSnapshot = buildSnapshot(requestedInterface);
        }
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
