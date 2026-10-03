package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compact player-facing RTS control interface.
 *
 * This panel is rendered by Matrix3's native InterfaceDefinitions/Class348
 * interface tree rather than a Swing/JWindow overlay. Its only host is NIS
 * root 1477 component 368, Matrix3's stock minigame-HUD slot. If that slot is
 * unavailable, too small, or already owns a mounted subinterface, RTS Control
 * yields instead of hijacking another RuneScape interface region.
 * Existing host children are preserved and restored exactly when RTS ownership
 * ends.
 *
 * V1 control groups remain settlement-session scoped. Runtime NPC indexes are
 * never persisted as worker identity; authoritative worker commands still flow
 * through the existing server-owned selection bridge.
 */
public final class ConstructionRtsControlOverlay {

    private enum Tab {
        UNITS("UNITS"),
        GROUPS("GROUPS"),
        CAMERA("CAMERA");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int ROOT_INTERFACE_ID = 1477;
    private static final int MINIGAME_HUD_COMPONENT_ID = 368;
    private static final int MINIGAME_HUD_UID =
            (ROOT_INTERFACE_ID << 16) | MINIGAME_HUD_COMPONENT_ID;
    private static final int FALLBACK_FONT_INTERFACE_ID = 316;
    private static final int SYNTHETIC_COMPONENT_BASE = 60000;

    private static final int GROUP_COUNT = 9;
    private static final long DOUBLE_TAP_MS = 400L;
    private static final long REFRESH_THROTTLE_MS = 50L;

    private static final int DEFAULT_WIDTH = 312;
    private static final int DEFAULT_HEIGHT = 140;
    private static final int MIN_WIDTH = 260;
    private static final int MIN_HEIGHT = 124;
    private static final int MAX_WIDTH = 380;
    private static final int MAX_HEIGHT = 180;

    private static final int TITLE_HEIGHT = 21;
    private static final int TAB_HEIGHT = 21;
    private static final int STATUS_HEIGHT = 17;
    private static final int PAD = 5;
    private static final int GAP = 3;

    // Matrix3 obfuscated field encoders. Each is the modular inverse of the
    // decode multiplier used by Class348/InterfaceDefinitions at render time.
    private static final int TYPE_ENCODE = -26899943;
    private static final int SELF_ID_ENCODE = 697947061;
    private static final int SELF_ID_DECODE = -1718435171;
    private static final int PARENT_ID_ENCODE = -374350987;
    private static final int X_ENCODE = -1222476983;
    private static final int Y_ENCODE = -314551123;
    private static final int WIDTH_ENCODE = -628102339;
    private static final int HEIGHT_ENCODE = -2088867597;
    private static final int COLOR_ENCODE = 2018397577;
    private static final int H_ALIGN_ENCODE = 835483205;
    private static final int V_ALIGN_ENCODE = -949518223;
    private static final int RECT_INDEX_DECODE = 1525996737;

    private static final int TYPE_CONTAINER = 0;
    private static final int TYPE_RECTANGLE = 3;
    private static final int TYPE_TEXT = 4;

    private static final int COLOR_PANEL = 0x181d23;
    private static final int COLOR_TITLE = 0x20262d;
    private static final int COLOR_CONTENT = 0x14191f;
    private static final int COLOR_BORDER = 0x5d6874;
    private static final int COLOR_INNER_BORDER = 0x303943;
    private static final int COLOR_TEXT = 0xeeeeee;
    private static final int COLOR_MUTED = 0xaeb5bc;
    private static final int COLOR_ACCENT = 0xc3a152;
    private static final int COLOR_TAB_ACTIVE = 0x404952;
    private static final int COLOR_BUTTON = 0x2d353e;
    private static final int COLOR_GROUP_SAVED = 0x3f4634;

    private static final int[][] groupNpcIndexes = new int[GROUP_COUNT][];
    private static final boolean[] groupPlayerSelected = new boolean[GROUP_COUNT];
    private static final boolean[] numberKeyDown = new boolean[GROUP_COUNT];

    private static volatile long lastRefreshMillis;
    private static volatile int lastRecallSlot = -1;
    private static volatile long lastRecallMillis;
    private static volatile String status = "L=GO  CTRL+L=SET  R=CLEAR";
    private static volatile Tab activeTab = Tab.GROUPS;

    private static volatile boolean inputListenerInstalled;
    private static volatile boolean moving;
    private static volatile boolean resizing;
    private static volatile int dragOffsetX;
    private static volatile int dragOffsetY;
    private static volatile int resizeStartMouseX;
    private static volatile int resizeStartMouseY;
    private static volatile int resizeStartWidth;
    private static volatile int resizeStartHeight;

    private static boolean geometryInitialized;
    private static int panelX = 4;
    private static int panelY = 4;
    private static int panelWidth = DEFAULT_WIDTH;
    private static int panelHeight = DEFAULT_HEIGHT;

    private static InterfaceDefinitions attachedHost;
    private static InterfaceDefinitions[] originalHostChildren;
    private static InterfaceDefinitions[] installedHostChildren;
    private static int nativeFontEncoded = Integer.MIN_VALUE;
    private static boolean treeDirty = true;
    private static int nextSyntheticComponent;

    private static volatile Rectangle hostScreenBounds = new Rectangle();
    private static volatile Rectangle panelBounds = new Rectangle();
    private static volatile Rectangle titleBounds = new Rectangle();
    private static volatile Rectangle resizeBounds = new Rectangle();
    private static final Rectangle[] tabBounds = new Rectangle[Tab.values().length];
    private static final Rectangle[] groupBounds = new Rectangle[GROUP_COUNT];
    private static volatile Rectangle unitsSelectAll = new Rectangle();
    private static volatile Rectangle unitsClear = new Rectangle();
    private static volatile Rectangle unitsFocus = new Rectangle();
    private static volatile Rectangle speedDown = new Rectangle();
    private static volatile Rectangle speedUp = new Rectangle();
    private static volatile Rectangle cameraFocus = new Rectangle();
    private static volatile Rectangle cameraCenter = new Rectangle();

    private static InterfaceDefinitions titleSelectionText;
    private static InterfaceDefinitions statusText;
    private static InterfaceDefinitions unitsInfoText;
    private static InterfaceDefinitions cameraSpeedText;
    private static InterfaceDefinitions cameraDragText;
    private static final InterfaceDefinitions[] groupText = new InterfaceDefinitions[GROUP_COUNT];
    private static final InterfaceDefinitions[] groupRect = new InterfaceDefinitions[GROUP_COUNT];

    private ConstructionRtsControlOverlay() {
    }

    static void refresh() {
        ensureInputListener();

        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < REFRESH_THROTTLE_MS) {
            return;
        }
        lastRefreshMillis = now;

        if (!ConstructionBuildCamera.isSettlementAutoMode()
                || !ConstructionBuildCamera.isRtsMode()) {
            detachNativeTree();
            return;
        }

        InterfaceDefinitions host = resolveNativeHost();
        if (host == null) {
            detachNativeTree();
            return;
        }
        if (nativeFontEncoded == Integer.MIN_VALUE) {
            nativeFontEncoded = resolveNativeFontEncoded();
            if (nativeFontEncoded == Integer.MIN_VALUE) {
                detachNativeTree();
                return;
            }
        }

        attachToHost(host);
        updateHostScreenBounds(host);
        if (!clampPanelGeometry(host)) {
            detachNativeTree();
            return;
        }

        if (treeDirty || installedHostChildren == null) {
            rebuildNativeTree();
        }
        updateDynamicComponents();
    }

    static boolean handleKey(KeyEvent event) {
        int slot = slotForKeyCode(event.getKeyCode());
        if (slot < 0) {
            return false;
        }

        if (event.getID() == KeyEvent.KEY_RELEASED) {
            numberKeyDown[slot] = false;
            if (isHotkeyContextAvailable()) {
                event.consume();
                return true;
            }
            return false;
        }
        if (event.getID() != KeyEvent.KEY_PRESSED || !isHotkeyContextAvailable()) {
            return false;
        }
        if (numberKeyDown[slot]) {
            event.consume();
            return true;
        }
        numberKeyDown[slot] = true;

        if (event.isControlDown()) {
            saveGroup(slot);
            lastRecallSlot = -1;
            lastRecallMillis = 0L;
        } else {
            long now = System.currentTimeMillis();
            boolean focus = slot == lastRecallSlot
                    && lastRecallMillis > 0L
                    && now - lastRecallMillis <= DOUBLE_TAP_MS;
            recallGroup(slot, focus);
            lastRecallSlot = slot;
            lastRecallMillis = now;
        }
        event.consume();
        return true;
    }

    static synchronized void resetForSettlementBoundary() {
        for (int i = 0; i < GROUP_COUNT; i++) {
            groupNpcIndexes[i] = null;
            groupPlayerSelected[i] = false;
            numberKeyDown[i] = false;
        }
        lastRecallSlot = -1;
        lastRecallMillis = 0L;
        status = "L=GO  CTRL+L=SET  R=CLEAR";
        activeTab = Tab.GROUPS;
        moving = false;
        resizing = false;
        detachNativeTree();
    }

    private static boolean isHotkeyContextAvailable() {
        return ConstructionBuildCamera.isSettlementAutoMode()
                && ConstructionBuildCamera.isRtsMode()
                && !ConstructionPaletteOverlay.isVisible()
                && !ConstructionPlacementController.isArmed()
                && !ConstructionPlacementController.isEraserMode()
                && !ConstructionWorkerJobsOverlay.isVisible()
                && !ConstructionStorageSettingsOverlay.isVisible()
                && !LiveModelEditorPreview.isEditSessionActive();
    }

    private static int slotForKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9) {
            return keyCode - KeyEvent.VK_1;
        }
        if (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return keyCode - KeyEvent.VK_NUMPAD1;
        }
        return -1;
    }

    private static synchronized void saveGroup(int slot) {
        int[] selected = ConstructionRadialSelection.snapshotCommittedWorkerNpcIndexes();
        boolean self = ConstructionRadialSelection.isLocalPlayerSelected();
        if (selected.length == 0 && !self) {
            groupNpcIndexes[slot] = null;
            groupPlayerSelected[slot] = false;
            status = "GROUP " + (slot + 1) + " CLEARED - NOTHING SELECTED";
        } else {
            groupNpcIndexes[slot] = Arrays.copyOf(selected, selected.length);
            groupPlayerSelected[slot] = self;
            status = "GROUP " + (slot + 1) + " SAVED: " + selected.length + " WORKER"
                    + (selected.length == 1 ? "" : "S") + (self ? " + SELF" : "");
        }
        updateDynamicComponents();
    }

    private static synchronized void clearGroup(int slot) {
        if (slot < 0 || slot >= GROUP_COUNT) {
            return;
        }
        groupNpcIndexes[slot] = null;
        groupPlayerSelected[slot] = false;
        if (lastRecallSlot == slot) {
            lastRecallSlot = -1;
            lastRecallMillis = 0L;
        }
        status = "GROUP " + (slot + 1) + " REMOVED";
        updateDynamicComponents();
    }

    private static synchronized void recallGroup(int slot, boolean focusCamera) {
        int[] saved = groupNpcIndexes[slot];
        boolean self = groupPlayerSelected[slot];
        if ((saved == null || saved.length == 0) && !self) {
            status = "GROUP " + (slot + 1) + " IS EMPTY";
            updateDynamicComponents();
            return;
        }

        int[] copy = saved == null ? new int[0] : Arrays.copyOf(saved, saved.length);
        boolean applied = ConstructionRadialSelection.applyControlGroupSelection(copy, self);
        if (!applied) {
            status = "GROUP " + (slot + 1) + " HAS NO ACTIVE UNITS";
            updateDynamicComponents();
            return;
        }

        status = "GROUP " + (slot + 1) + " SELECTED";
        if (focusCamera) {
            focusCurrentSelection("GROUP " + (slot + 1));
        }
        updateDynamicComponents();
    }

    private static void focusCurrentSelection(String source) {
        int[] center = ConstructionRadialSelection.getCommittedSelectionCenterLocalTile();
        if (center == null) {
            status = "NO ACTIVE SELECTION TO FOCUS";
            return;
        }
        if (ConstructionBuildCamera.focusRtsAtLocalTileFromControlGroup(center[0], center[1])) {
            status = source + " - CAMERA MOVING";
        }
    }

    private static void centerSettlement() {
        if (client.aClass613_8605 == null) {
            status = "SETTLEMENT SCENE NOT READY";
            return;
        }
        int width = client.aClass613_8605.method7347(-740581830);
        int height = client.aClass613_8605.method7278(277214477);
        if (width <= 0 || height <= 0) {
            status = "SETTLEMENT SCENE NOT READY";
            return;
        }
        if (ConstructionBuildCamera.focusRtsAtLocalTileFromControlGroup(width / 2, height / 2)) {
            status = "CAMERA MOVING TO SETTLEMENT CENTER";
        }
    }

    /**
     * verified-static: Matrix3 server InterfaceManager defines 1477:368 as the
     * MINIGAME_HUD component. Class348 renders mounted subinterfaces separately
     * from a host's aClass73Array917 children, so this client-owned RTS panel
     * must yield whenever a real subinterface is mounted there.
     */
    private static InterfaceDefinitions resolveNativeHost() {
        InterfaceDefinitions preferred = Class512.method6083(
                MINIGAME_HUD_UID, (short) -19231);
        if (isUsableHost(preferred) && !hasMountedSubInterface(preferred)) {
            return preferred;
        }
        return null;
    }

    private static boolean hasMountedSubInterface(InterfaceDefinitions candidate) {
        if (candidate == null || client.aClass676_8760 == null) {
            return false;
        }
        int uid = candidate.selfId * SELF_ID_DECODE;
        return client.aClass676_8760.get((long) uid) != null;
    }

    private static boolean isUsableHost(InterfaceDefinitions candidate) {
        return candidate != null
                && candidate.anInt752 * -1285279191 == TYPE_CONTAINER
                && decodeWidth(candidate) >= MIN_WIDTH + 8
                && decodeHeight(candidate) >= MIN_HEIGHT + 8;
    }

    private static int resolveNativeFontEncoded() {
        int encoded = scanNativeFont(ROOT_INTERFACE_ID);
        if (encoded != Integer.MIN_VALUE) {
            return encoded;
        }
        return scanNativeFont(FALLBACK_FONT_INTERFACE_ID);
    }

    private static int scanNativeFont(int interfaceId) {
        if (!Class569.method6760(interfaceId, null, -532744879)) {
            return Integer.MIN_VALUE;
        }
        Class83 group = Class534.aClass83Array5975[interfaceId];
        if (group == null || group.aClass73Array1081 == null) {
            return Integer.MIN_VALUE;
        }
        for (InterfaceDefinitions definition : group.aClass73Array1081) {
            if (definition != null
                    && definition.anInt752 * -1285279191 == TYPE_TEXT
                    && definition.anInt906 * 1036765709 != -1) {
                return definition.anInt906;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static void attachToHost(InterfaceDefinitions host) {
        if (attachedHost != host) {
            detachNativeTree();
            attachedHost = host;
            originalHostChildren = host.aClass73Array917;
            installedHostChildren = null;
            treeDirty = true;
            geometryInitialized = false;
        } else if (installedHostChildren != null
                && host.aClass73Array917 != installedHostChildren) {
            // A native script changed the host children while RTS was active.
            // Preserve that newer state and append our panel again rather than
            // silently taking ownership of the host's child list.
            originalHostChildren = host.aClass73Array917;
            installedHostChildren = null;
            treeDirty = true;
        }
    }

    private static void detachNativeTree() {
        if (attachedHost != null && installedHostChildren != null
                && attachedHost.aClass73Array917 == installedHostChildren) {
            attachedHost.aClass73Array917 = originalHostChildren;
        }
        attachedHost = null;
        originalHostChildren = null;
        installedHostChildren = null;
        clearComponentRefs();
        hostScreenBounds = new Rectangle();
        panelBounds = new Rectangle();
        treeDirty = true;
    }

    private static void updateHostScreenBounds(InterfaceDefinitions host) {
        int rectangleIndex = host.anInt927 * RECT_INDEX_DECODE;
        if (rectangleIndex < 0 || rectangleIndex >= client.aRectangleArray8708.length
                || client.aRectangleArray8708[rectangleIndex] == null) {
            return;
        }
        Rectangle bounds = client.aRectangleArray8708[rectangleIndex];
        hostScreenBounds = new Rectangle(bounds);
        panelBounds = new Rectangle(
                bounds.x + panelX, bounds.y + panelY, panelWidth, panelHeight);
    }

    private static boolean clampPanelGeometry(InterfaceDefinitions host) {
        int hostWidth = decodeWidth(host);
        int hostHeight = decodeHeight(host);
        if (hostWidth < MIN_WIDTH + 8 || hostHeight < MIN_HEIGHT + 8) {
            return false;
        }

        int maxWidth = Math.min(MAX_WIDTH, hostWidth - 8);
        int maxHeight = Math.min(MAX_HEIGHT, hostHeight - 8);
        if (!geometryInitialized) {
            panelWidth = Math.min(DEFAULT_WIDTH, maxWidth);
            panelHeight = Math.min(DEFAULT_HEIGHT, maxHeight);
            panelX = 4;
            panelY = 4;
            geometryInitialized = true;
            treeDirty = true;
        }

        int previousWidth = panelWidth;
        int previousHeight = panelHeight;
        int previousX = panelX;
        int previousY = panelY;

        panelWidth = clamp(panelWidth, Math.min(MIN_WIDTH, maxWidth), maxWidth);
        panelHeight = clamp(panelHeight, Math.min(MIN_HEIGHT, maxHeight), maxHeight);
        panelX = clamp(panelX, 4, Math.max(4, hostWidth - panelWidth - 4));
        panelY = clamp(panelY, 4, Math.max(4, hostHeight - panelHeight - 4));

        if (previousWidth != panelWidth || previousHeight != panelHeight
                || previousX != panelX || previousY != panelY) {
            treeDirty = true;
        }
        return true;
    }

    private static void rebuildNativeTree() {
        if (attachedHost == null || nativeFontEncoded == Integer.MIN_VALUE) {
            return;
        }
        clearComponentRefs();
        nextSyntheticComponent = SYNTHETIC_COMPONENT_BASE;
        List<InterfaceDefinitions> controls = new ArrayList<InterfaceDefinitions>(40);

        addRect(controls, 0, 0, panelWidth, panelHeight, COLOR_BORDER);
        addRect(controls, 1, 1, panelWidth - 2, panelHeight - 2, COLOR_PANEL);
        addRect(controls, 2, 2, panelWidth - 4, TITLE_HEIGHT - 2, COLOR_TITLE);
        addText(controls, "RTS CONTROL", 7, 4, panelWidth - 90, TITLE_HEIGHT - 5,
                COLOR_TEXT, 0);
        titleSelectionText = addText(controls, "", panelWidth - 86, 4, 78,
                TITLE_HEIGHT - 5, COLOR_ACCENT, 2);

        int tabY = TITLE_HEIGHT;
        int tabWidth = (panelWidth - 4) / 3;
        for (int i = 0; i < Tab.values().length; i++) {
            int x = 2 + i * tabWidth;
            int width = i == Tab.values().length - 1
                    ? panelWidth - 2 - x : tabWidth;
            tabBounds[i] = new Rectangle(x, tabY, width, TAB_HEIGHT);
            addRect(controls, x, tabY, width, TAB_HEIGHT,
                    activeTab == Tab.values()[i] ? COLOR_TAB_ACTIVE : COLOR_BUTTON);
            addText(controls, Tab.values()[i].label, x, tabY + 2, width,
                    TAB_HEIGHT - 3,
                    activeTab == Tab.values()[i] ? COLOR_ACCENT : COLOR_TEXT, 1);
        }

        int contentY = TITLE_HEIGHT + TAB_HEIGHT + 2;
        int footerY = panelHeight - STATUS_HEIGHT;
        int contentHeight = Math.max(1, footerY - contentY - 2);
        addRect(controls, 2, contentY, panelWidth - 4, contentHeight, COLOR_CONTENT);

        if (activeTab == Tab.GROUPS) {
            buildGroupsTab(controls, contentY, contentHeight);
        } else if (activeTab == Tab.UNITS) {
            buildUnitsTab(controls, contentY, contentHeight);
        } else {
            buildCameraTab(controls, contentY, contentHeight);
        }

        addRect(controls, 2, footerY, panelWidth - 4, STATUS_HEIGHT - 2, COLOR_TITLE);
        statusText = addText(controls, "", 7, footerY + 1,
                panelWidth - 27, STATUS_HEIGHT - 3, COLOR_MUTED, 0);
        addText(controls, "//", panelWidth - 20, footerY + 1, 14,
                STATUS_HEIGHT - 3, COLOR_ACCENT, 2);

        titleBounds = new Rectangle(0, 0, panelWidth, TITLE_HEIGHT);
        resizeBounds = new Rectangle(panelWidth - 18, panelHeight - 18, 18, 18);

        InterfaceDefinitions[] synthetic =
                controls.toArray(new InterfaceDefinitions[controls.size()]);
        int originalLength = originalHostChildren == null ? 0 : originalHostChildren.length;
        InterfaceDefinitions[] combined =
                new InterfaceDefinitions[originalLength + synthetic.length];
        if (originalLength > 0) {
            System.arraycopy(originalHostChildren, 0, combined, 0, originalLength);
        }
        System.arraycopy(synthetic, 0, combined, originalLength, synthetic.length);
        installedHostChildren = combined;
        attachedHost.aClass73Array917 = combined;
        treeDirty = false;
        updateDynamicComponents();
    }

    private static void buildGroupsTab(List<InterfaceDefinitions> controls,
            int contentY, int contentHeight) {
        int gridX = PAD;
        int gridY = contentY + 4;
        int gridWidth = panelWidth - PAD * 2;
        int gridHeight = Math.max(1, contentHeight - 8);
        int cellWidth = (gridWidth - GAP * 2) / 3;
        int cellHeight = (gridHeight - GAP * 2) / 3;

        for (int i = 0; i < GROUP_COUNT; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = gridX + col * (cellWidth + GAP);
            int y = gridY + row * (cellHeight + GAP);
            int width = col == 2 ? gridX + gridWidth - x : cellWidth;
            int height = row == 2 ? gridY + gridHeight - y : cellHeight;
            groupBounds[i] = new Rectangle(x, y, width, height);
            groupRect[i] = addRect(controls, x, y, width, height, COLOR_BUTTON);
            groupText[i] = addText(controls, "", x + 2, y + 1, width - 4,
                    Math.max(1, height - 2), COLOR_TEXT, 1);
        }
    }

    private static void buildUnitsTab(List<InterfaceDefinitions> controls,
            int contentY, int contentHeight) {
        unitsInfoText = addText(controls, "", PAD + 2, contentY + 5,
                panelWidth - PAD * 2 - 4, 18, COLOR_TEXT, 0);

        int y = contentY + Math.max(29, contentHeight / 2);
        int available = panelWidth - PAD * 2;
        int buttonWidth = (available - GAP * 2) / 3;
        int buttonHeight = Math.min(27, Math.max(22, contentY + contentHeight - y - 4));
        unitsSelectAll = new Rectangle(PAD, y, buttonWidth, buttonHeight);
        unitsClear = new Rectangle(PAD + buttonWidth + GAP, y, buttonWidth, buttonHeight);
        unitsFocus = new Rectangle(PAD + (buttonWidth + GAP) * 2, y,
                available - (buttonWidth + GAP) * 2, buttonHeight);
        addButton(controls, unitsSelectAll, "SELECT ALL");
        addButton(controls, unitsClear, "CLEAR");
        addButton(controls, unitsFocus, "FOCUS");
    }

    private static void buildCameraTab(List<InterfaceDefinitions> controls,
            int contentY, int contentHeight) {
        cameraSpeedText = addText(controls, "", PAD + 2, contentY + 4,
                115, 19, COLOR_TEXT, 0);
        speedDown = new Rectangle(PAD + 120, contentY + 3, 28, 21);
        speedUp = new Rectangle(PAD + 152, contentY + 3, 28, 21);
        addButton(controls, speedDown, "-");
        addButton(controls, speedUp, "+");
        cameraDragText = addText(controls, "MINIMAP DRAG: ON", PAD + 188,
                contentY + 4, panelWidth - PAD - 188, 19, COLOR_MUTED, 0);

        int y = contentY + Math.max(31, contentHeight / 2);
        int available = panelWidth - PAD * 2;
        int buttonWidth = (available - GAP) / 2;
        int buttonHeight = Math.min(27, Math.max(22, contentY + contentHeight - y - 4));
        cameraFocus = new Rectangle(PAD, y, buttonWidth, buttonHeight);
        cameraCenter = new Rectangle(PAD + buttonWidth + GAP, y,
                available - buttonWidth - GAP, buttonHeight);
        addButton(controls, cameraFocus, "FOCUS SELECTION");
        addButton(controls, cameraCenter, "CENTER SETTLEMENT");
    }

    private static void addButton(List<InterfaceDefinitions> controls,
            Rectangle bounds, String label) {
        addRect(controls, bounds.x, bounds.y, bounds.width, bounds.height, COLOR_BUTTON);
        addText(controls, label, bounds.x + 2, bounds.y + 1,
                Math.max(1, bounds.width - 4), Math.max(1, bounds.height - 2),
                COLOR_TEXT, 1);
    }

    private static InterfaceDefinitions addRect(List<InterfaceDefinitions> controls,
            int x, int y, int width, int height, int color) {
        InterfaceDefinitions definition = createBaseComponent(
                TYPE_RECTANGLE, x, y, width, height);
        definition.anInt918 = color * COLOR_ENCODE;
        definition.aBool779 = true;
        controls.add(definition);
        return definition;
    }

    private static InterfaceDefinitions addText(List<InterfaceDefinitions> controls,
            String text, int x, int y, int width, int height, int color, int align) {
        InterfaceDefinitions definition = createBaseComponent(
                TYPE_TEXT, x, y, width, height);
        definition.anInt906 = nativeFontEncoded;
        definition.aString829 = text == null ? "" : text;
        definition.anInt918 = color * COLOR_ENCODE;
        definition.anInt891 = align * H_ALIGN_ENCODE;
        definition.anInt832 = 1 * V_ALIGN_ENCODE;
        definition.aBool919 = true;
        controls.add(definition);
        return definition;
    }

    private static InterfaceDefinitions createBaseComponent(
            int type, int localX, int localY, int width, int height) {
        InterfaceDefinitions definition = new InterfaceDefinitions();
        int selfUid = (ROOT_INTERFACE_ID << 16)
                | (nextSyntheticComponent++ & 0xffff);
        int parentUid = attachedHost.selfId * SELF_ID_DECODE;
        definition.selfId = selfUid * SELF_ID_ENCODE;
        definition.anInt768 = parentUid * PARENT_ID_ENCODE;
        definition.anInt752 = type * TYPE_ENCODE;
        definition.anInt762 = (panelX + localX) * X_ENCODE;
        definition.anInt842 = (panelY + localY) * Y_ENCODE;
        definition.anInt764 = Math.max(1, width) * WIDTH_ENCODE;
        definition.anInt765 = Math.max(1, height) * HEIGHT_ENCODE;
        definition.anInt780 = 0;
        definition.anInt854 = 0;
        return definition;
    }

    private static synchronized void updateDynamicComponents() {
        int selected = ConstructionRadialSelection.getCommittedWorkerCount();
        int total = ConstructionRadialSelection.getTotalSettlementWorkerCount();
        boolean self = ConstructionRadialSelection.isLocalPlayerSelected();

        if (titleSelectionText != null) {
            titleSelectionText.aString829 = "SEL " + selected + "/" + total
                    + (self ? "+S" : "");
        }
        if (statusText != null) {
            statusText.aString829 = status;
        }
        if (unitsInfoText != null) {
            unitsInfoText.aString829 = "SELECTED " + selected + " / " + total
                    + " WORKERS   SELF: " + (self ? "YES" : "NO");
        }
        if (cameraSpeedText != null) {
            cameraSpeedText.aString829 = "SPEED " + ConstructionBuildCamera.getRtsMoveSpeedLabel();
        }
        if (cameraDragText != null) {
            cameraDragText.aString829 = "MINIMAP DRAG: ON";
        }
        for (int i = 0; i < GROUP_COUNT; i++) {
            int count = groupNpcIndexes[i] == null ? 0 : groupNpcIndexes[i].length;
            if (groupText[i] != null) {
                groupText[i].aString829 = (i + 1) + "  "
                        + (count <= 0 && !groupPlayerSelected[i]
                                ? "-" : count + "W" + (groupPlayerSelected[i] ? "+S" : ""));
            }
            if (groupRect[i] != null) {
                boolean saved = count > 0 || groupPlayerSelected[i];
                groupRect[i].anInt918 =
                        (saved ? COLOR_GROUP_SAVED : COLOR_BUTTON) * COLOR_ENCODE;
            }
        }
    }

    private static void clearComponentRefs() {
        titleSelectionText = null;
        statusText = null;
        unitsInfoText = null;
        cameraSpeedText = null;
        cameraDragText = null;
        for (int i = 0; i < GROUP_COUNT; i++) {
            groupText[i] = null;
            groupRect[i] = null;
            groupBounds[i] = new Rectangle();
        }
        for (int i = 0; i < tabBounds.length; i++) {
            tabBounds[i] = new Rectangle();
        }
        unitsSelectAll = new Rectangle();
        unitsClear = new Rectangle();
        unitsFocus = new Rectangle();
        speedDown = new Rectangle();
        speedUp = new Rectangle();
        cameraFocus = new Rectangle();
        cameraCenter = new Rectangle();
        titleBounds = new Rectangle();
        resizeBounds = new Rectangle();
    }

    private static void ensureInputListener() {
        if (inputListenerInstalled) {
            return;
        }
        synchronized (ConstructionRtsControlOverlay.class) {
            if (inputListenerInstalled) {
                return;
            }
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override
                public void eventDispatched(AWTEvent event) {
                    if (event instanceof MouseEvent) {
                        handleMouseEvent((MouseEvent) event);
                    }
                }
            }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
            inputListenerInstalled = true;
        }
    }

    private static void handleMouseEvent(MouseEvent event) {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || event.getSource() != canvas
                || attachedHost == null
                || !ConstructionBuildCamera.isSettlementAutoMode()
                || !ConstructionBuildCamera.isRtsMode()) {
            return;
        }
        Rectangle hostBounds = hostScreenBounds;
        if (hostBounds.width <= 0 || hostBounds.height <= 0) {
            return;
        }

        int id = event.getID();
        int hostX = event.getX() - hostBounds.x;
        int hostY = event.getY() - hostBounds.y;
        int localX = hostX - panelX;
        int localY = hostY - panelY;
        boolean inside = localX >= 0 && localY >= 0
                && localX < panelWidth && localY < panelHeight;

        if (id == MouseEvent.MOUSE_PRESSED && inside) {
            // Global RTS selection listens on the same heavyweight canvas. Clear
            // any transient selection press this UI click may have armed while
            // preserving committed workers/self.
            ConstructionRadialSelection.cancelTransientDragForMinimapFocus();

            if (event.getButton() == MouseEvent.BUTTON1
                    && resizeBounds.contains(localX, localY)) {
                resizing = true;
                resizeStartMouseX = hostX;
                resizeStartMouseY = hostY;
                resizeStartWidth = panelWidth;
                resizeStartHeight = panelHeight;
                event.consume();
                return;
            }
            if (event.getButton() == MouseEvent.BUTTON1
                    && titleBounds.contains(localX, localY)) {
                moving = true;
                dragOffsetX = localX;
                dragOffsetY = localY;
                event.consume();
                return;
            }

            for (int i = 0; i < tabBounds.length; i++) {
                if (tabBounds[i].contains(localX, localY)) {
                    if (event.getButton() == MouseEvent.BUTTON1) {
                        activeTab = Tab.values()[i];
                        status = activeTab == Tab.GROUPS
                                ? "L=GO  CTRL+L=SET  R=CLEAR"
                                : activeTab.label;
                        treeDirty = true;
                    }
                    event.consume();
                    return;
                }
            }

            if (activeTab == Tab.GROUPS) {
                for (int i = 0; i < GROUP_COUNT; i++) {
                    if (!groupBounds[i].contains(localX, localY)) {
                        continue;
                    }
                    if (event.getButton() == MouseEvent.BUTTON3) {
                        clearGroup(i);
                    } else if (event.getButton() == MouseEvent.BUTTON1) {
                        if (event.isControlDown()) {
                            saveGroup(i);
                        } else {
                            recallGroup(i, true);
                        }
                    }
                    event.consume();
                    return;
                }
            } else if (activeTab == Tab.UNITS
                    && event.getButton() == MouseEvent.BUTTON1) {
                if (unitsSelectAll.contains(localX, localY)) {
                    ConstructionRadialSelection.selectAllWorkers();
                    status = "ALL ACTIVE WORKERS SELECTED";
                } else if (unitsClear.contains(localX, localY)) {
                    ConstructionRadialSelection.clearCommittedRadius();
                    status = "SELECTION CLEARED";
                } else if (unitsFocus.contains(localX, localY)) {
                    focusCurrentSelection("SELECTION");
                } else {
                    event.consume();
                    return;
                }
                updateDynamicComponents();
                event.consume();
                return;
            } else if (activeTab == Tab.CAMERA
                    && event.getButton() == MouseEvent.BUTTON1) {
                if (speedDown.contains(localX, localY)) {
                    ConstructionBuildCamera.adjustRtsMoveSpeed(-1);
                    status = "CAMERA SPEED " + ConstructionBuildCamera.getRtsMoveSpeedLabel();
                } else if (speedUp.contains(localX, localY)) {
                    ConstructionBuildCamera.adjustRtsMoveSpeed(1);
                    status = "CAMERA SPEED " + ConstructionBuildCamera.getRtsMoveSpeedLabel();
                } else if (cameraFocus.contains(localX, localY)) {
                    focusCurrentSelection("SELECTION");
                } else if (cameraCenter.contains(localX, localY)) {
                    centerSettlement();
                } else {
                    event.consume();
                    return;
                }
                updateDynamicComponents();
                event.consume();
                return;
            }
            event.consume();
            return;
        }

        if (id == MouseEvent.MOUSE_DRAGGED) {
            if (moving) {
                panelX = hostX - dragOffsetX;
                panelY = hostY - dragOffsetY;
                clampPanelGeometry(attachedHost);
                treeDirty = true;
                event.consume();
                return;
            }
            if (resizing) {
                panelWidth = resizeStartWidth + hostX - resizeStartMouseX;
                panelHeight = resizeStartHeight + hostY - resizeStartMouseY;
                clampPanelGeometry(attachedHost);
                treeDirty = true;
                event.consume();
                return;
            }
        }

        if (id == MouseEvent.MOUSE_RELEASED && (moving || resizing)) {
            moving = false;
            resizing = false;
            event.consume();
        }
    }

    private static int decodeWidth(InterfaceDefinitions definition) {
        return definition.anInt764 * 669238293;
    }

    private static int decodeHeight(InterfaceDefinitions definition) {
        return definition.anInt765 * 1360982075;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
