package game;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import game.ClientConsoleInterfaceBridge.ComponentSnapshot;
import game.ClientConsoleInterfaceBridge.InterfaceSnapshot;

/**
 * Read-only visual overlay for the Client Console Interface Editor.
 *
 * Matrix3 interface state remains owned by InterfaceDefinitions and is read only
 * through the immutable snapshots already produced by ClientConsoleInterfaceBridge.
 * The overlay paints temporary AWT diagnostics over the game Canvas and consumes
 * mouse input only while the one-shot component picker is armed.
 */
public final class ClientConsoleInterfaceOverlay {

    private static final int OVERLAY_FRAME_MS = 33;
    private static final int MAX_PARENT_DEPTH = 64;

    private static final Color WIRE = new Color(67, 205, 230, 155);
    private static final Color SELECTED = new Color(255, 198, 66, 235);
    private static final Color LINK = new Color(137, 155, 190, 135);
    private static final Color LABEL_BACKGROUND = new Color(10, 13, 18, 210);
    private static final Color LABEL_TEXT = new Color(245, 247, 250, 240);
    private static final Color PICK_BACKGROUND = new Color(255, 198, 66, 225);
    private static final Color PICK_TEXT = new Color(20, 23, 28, 255);

    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final BasicStroke WIRE_STROKE = new BasicStroke(1.0F);
    private static final BasicStroke SELECTED_STROKE = new BasicStroke(2.0F);
    private static final BasicStroke LINK_STROKE = new BasicStroke(1.0F);

    private static final AtomicInteger PICKED_COMPONENT = new AtomicInteger(-1);

    private static volatile int interfaceId = -1;
    private static volatile int selectedComponentId = -1;
    private static volatile boolean visible;
    private static volatile boolean selectedOnly;
    private static volatile boolean showIds = true;
    private static volatile boolean showDimensions;
    private static volatile boolean showParentLinks;
    private static volatile boolean pickerArmed;

    private static volatile WireSnapshot latestWireSnapshot = WireSnapshot.empty();

    private static Timer paintTimer;
    private static boolean mouseListenerInstalled;

    private ClientConsoleInterfaceOverlay() {
    }

    public static void setState(int targetInterfaceId, int targetSelectedComponentId,
            boolean overlayVisible, boolean onlySelected, boolean ids,
            boolean dimensions, boolean parentLinks) {
        interfaceId = targetInterfaceId;
        selectedComponentId = targetSelectedComponentId;
        visible = overlayVisible && targetInterfaceId >= 0;
        selectedOnly = onlySelected;
        showIds = ids;
        showDimensions = dimensions;
        showParentLinks = parentLinks;

        if (!visible) {
            pickerArmed = false;
        }
        updateTimerState();
    }

    public static String armPicker() {
        if (!visible || interfaceId < 0) {
            return "Enable Wire Mesh and load an interface before picking a component.";
        }
        ensureMouseListener();
        PICKED_COMPONENT.set(-1);
        pickerArmed = true;
        updateTimerState();
        return null;
    }

    public static void cancelPicker() {
        pickerArmed = false;
    }

    public static boolean isPickerArmed() {
        return pickerArmed;
    }

    /** Returns one picked component ID, or -1 when there is no new pick. */
    public static int consumePickedComponent() {
        return PICKED_COMPONENT.getAndSet(-1);
    }

    private static void updateTimerState() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateTimerState();
                }
            });
            return;
        }

        if (visible) {
            ensureMouseListener();
            if (paintTimer == null) {
                paintTimer = new Timer(OVERLAY_FRAME_MS, e -> paintOverlay());
                paintTimer.setCoalesce(true);
            }
            if (!paintTimer.isRunning()) {
                paintTimer.start();
            }
        } else if (paintTimer != null) {
            paintTimer.stop();
        }
    }

    private static synchronized void ensureMouseListener() {
        if (mouseListenerInstalled) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (!pickerArmed || !(event instanceof MouseEvent)) {
                    return;
                }
                MouseEvent mouse = (MouseEvent) event;
                if (mouse.getID() != MouseEvent.MOUSE_PRESSED || mouse.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                Canvas canvas = Class584.aCanvas7745;
                if (canvas == null || mouse.getSource() != canvas) {
                    return;
                }

                // Picker mode owns only this diagnostic click so the game does not
                // also walk/interact while the user is selecting a UI component.
                mouse.consume();
                WireNode picked = hitTest(mouse.getX(), mouse.getY(), latestWireSnapshot);
                if (picked == null) {
                    return;
                }

                selectedComponentId = picked.componentId;
                PICKED_COMPONENT.set(picked.componentId);
                pickerArmed = false;
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
        mouseListenerInstalled = true;
    }

    private static void paintOverlay() {
        if (!visible || interfaceId < 0) {
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            return;
        }

        InterfaceSnapshot snapshot = ClientConsoleInterfaceBridge.getLatestSnapshot();
        if (snapshot.getInterfaceId() != interfaceId) {
            return;
        }

        WireSnapshot wireSnapshot = buildWireSnapshot(snapshot);
        latestWireSnapshot = wireSnapshot;

        Graphics graphics = canvas.getGraphics();
        if (!(graphics instanceof Graphics2D)) {
            if (graphics != null) {
                graphics.dispose();
            }
            return;
        }

        Graphics2D g = (Graphics2D) graphics;
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(LABEL_FONT);

            if (showParentLinks) {
                paintParentLinks(g, wireSnapshot);
            }

            for (WireNode node : wireSnapshot.nodes) {
                boolean selected = node.componentId == selectedComponentId;
                if (selectedOnly && !selected) {
                    continue;
                }
                paintNode(g, node, selected);
            }

            paintHeader(g, wireSnapshot);
        } finally {
            g.dispose();
        }
    }

    private static void paintParentLinks(Graphics2D g, WireSnapshot snapshot) {
        g.setColor(LINK);
        g.setStroke(LINK_STROKE);
        for (WireNode node : snapshot.nodes) {
            if (node.parentComponentId < 0) {
                continue;
            }
            WireNode parent = snapshot.byId.get(Integer.valueOf(node.parentComponentId));
            if (parent == null || node.width <= 0 || node.height <= 0
                    || parent.width <= 0 || parent.height <= 0) {
                continue;
            }
            if (selectedOnly && node.componentId != selectedComponentId
                    && parent.componentId != selectedComponentId) {
                continue;
            }
            g.drawLine(parent.centerX(), parent.centerY(), node.centerX(), node.centerY());
        }
    }

    private static void paintNode(Graphics2D g, WireNode node, boolean selected) {
        if (node.width <= 0 || node.height <= 0) {
            return;
        }

        g.setStroke(selected ? SELECTED_STROKE : WIRE_STROKE);
        g.setColor(selected ? SELECTED : WIRE);
        g.drawRect(node.x, node.y, Math.max(0, node.width - 1), Math.max(0, node.height - 1));

        if (!showIds && !showDimensions && !selected) {
            return;
        }

        StringBuilder label = new StringBuilder();
        if (showIds || selected) {
            label.append('#').append(node.componentId);
        }
        if (showDimensions || selected) {
            if (label.length() > 0) {
                label.append("  ");
            }
            label.append(node.width).append('x').append(node.height);
        }
        if (selected) {
            label.append("  @ ").append(node.x).append(',').append(node.y);
        }
        paintLabel(g, label.toString(), node.x + 2, Math.max(13, node.y + 13), selected);
    }

    private static void paintHeader(Graphics2D g, WireSnapshot snapshot) {
        String text = pickerArmed
                ? "PICK COMPONENT - click the UI component in game"
                : "Interface " + interfaceId + " - " + snapshot.nodes.length + " wire components";
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 12;
        int height = metrics.getHeight() + 6;
        g.setColor(pickerArmed ? PICK_BACKGROUND : LABEL_BACKGROUND);
        g.fillRoundRect(8, 8, width, height, 8, 8);
        g.setColor(pickerArmed ? PICK_TEXT : LABEL_TEXT);
        g.drawString(text, 14, 8 + metrics.getAscent() + 3);
    }

    private static void paintLabel(Graphics2D g, String text, int x, int baselineY, boolean selected) {
        if (text == null || text.length() == 0) {
            return;
        }
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 8;
        int height = metrics.getHeight() + 2;
        int top = baselineY - metrics.getAscent() - 2;
        g.setColor(LABEL_BACKGROUND);
        g.fillRect(x - 2, top, width, height);
        g.setColor(selected ? SELECTED : LABEL_TEXT);
        g.drawString(text, x + 2, baselineY);
    }

    private static WireSnapshot buildWireSnapshot(InterfaceSnapshot snapshot) {
        ComponentSnapshot[] components = snapshot.getComponents();
        Map<Integer, ComponentSnapshot> componentsById = new HashMap<Integer, ComponentSnapshot>();
        for (ComponentSnapshot component : components) {
            componentsById.put(Integer.valueOf(component.getComponentId()), component);
        }

        Map<Integer, WireNode> resolved = new HashMap<Integer, WireNode>();
        WireNode[] nodes = new WireNode[components.length];
        for (int index = 0; index < components.length; index++) {
            nodes[index] = resolveNode(components[index], componentsById, resolved, 0);
        }
        return new WireSnapshot(nodes, resolved);
    }

    private static WireNode resolveNode(ComponentSnapshot component,
            Map<Integer, ComponentSnapshot> componentsById,
            Map<Integer, WireNode> resolved, int depth) {
        Integer key = Integer.valueOf(component.getComponentId());
        WireNode cached = resolved.get(key);
        if (cached != null) {
            return cached;
        }

        int x = component.getRuntimeX();
        int y = component.getRuntimeY();
        int parentComponentId = -1;
        int resolvedDepth = depth;

        if (depth < MAX_PARENT_DEPTH && component.getParentHash() != -1
                && (component.getParentHash() >>> 16) == interfaceId) {
            parentComponentId = component.getParentComponentId();
            ComponentSnapshot parentComponent = componentsById.get(Integer.valueOf(parentComponentId));
            if (parentComponent != null && parentComponent.getComponentId() != component.getComponentId()) {
                WireNode parent = resolveNode(parentComponent, componentsById, resolved, depth + 1);
                x += parent.x;
                y += parent.y;
                resolvedDepth = parent.depth + 1;
            }
        }

        WireNode node = new WireNode(component.getComponentId(), parentComponentId,
                x, y, component.getRuntimeWidth(), component.getRuntimeHeight(), resolvedDepth);
        resolved.put(key, node);
        return node;
    }

    private static WireNode hitTest(int x, int y, WireSnapshot snapshot) {
        WireNode best = null;
        for (WireNode node : snapshot.nodes) {
            if (!node.contains(x, y)) {
                continue;
            }
            if (best == null || node.depth > best.depth
                    || (node.depth == best.depth && node.area() < best.area())
                    || (node.depth == best.depth && node.area() == best.area()
                            && node.componentId > best.componentId)) {
                best = node;
            }
        }
        return best;
    }

    private static final class WireSnapshot {
        private final WireNode[] nodes;
        private final Map<Integer, WireNode> byId;

        private WireSnapshot(WireNode[] nodes, Map<Integer, WireNode> byId) {
            this.nodes = nodes;
            this.byId = byId;
        }

        private static WireSnapshot empty() {
            return new WireSnapshot(new WireNode[0], new HashMap<Integer, WireNode>());
        }
    }

    private static final class WireNode {
        private final int componentId;
        private final int parentComponentId;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int depth;

        private WireNode(int componentId, int parentComponentId,
                int x, int y, int width, int height, int depth) {
            this.componentId = componentId;
            this.parentComponentId = parentComponentId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        private boolean contains(int px, int py) {
            return width > 0 && height > 0
                    && px >= x && py >= y
                    && px < x + width && py < y + height;
        }

        private int centerX() {
            return x + width / 2;
        }

        private int centerY() {
            return y + height / 2;
        }

        private long area() {
            return (long) Math.max(0, width) * (long) Math.max(0, height);
        }
    }
}
