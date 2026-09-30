package game.console;

import game.Class584;
import game.DevDefinitionBridge;
import game.DevModeBridge.DevTarget;
import game.DevModeBridge.TargetType;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Compact owner-only hover inspector layered over the live Matrix3 canvas.
 *
 * Target ownership stays in DevModeBridge: this overlay only displays targets
 * already resolved by Matrix3's normal scene/menu path. It never performs its
 * own scene pick and never mutates world/cache state.
 */
public final class LiveInspectOverlay {

    private static final int WIDTH = 360;
    private static final int MARGIN = 10;
    private static final long STALE_HOVER_NANOS = 180000000L;

    private static volatile boolean enabled;
    private static volatile boolean locked;
    private static volatile DevTarget hoverTarget;
    private static volatile DevTarget lockedTarget;
    private static volatile long lastPointerMoveNanos;
    private static volatile long lastObservedNanos;

    private static JWindow window;
    private static Window owner;
    private static Timer refreshTimer;

    private static final JLabel stateLabel = valueLabel();
    private static final JLabel typeLabel = valueLabel();
    private static final JLabel nameLabel = valueLabel();
    private static final JLabel idLabel = valueLabel();
    private static final JLabel modelsLabel = valueLabel();
    private static final JLabel animationsLabel = valueLabel();
    private static final JLabel tileLabel = valueLabel();
    private static final JLabel runtimeLabel = valueLabel();

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
            locked = false;
            hoverTarget = null;
            lockedTarget = null;
        } else {
            lastPointerMoveNanos = System.nanoTime();
            ensureRefreshTimer();
        }
        refreshSoon();
    }

    public static boolean toggleLock() {
        if (!enabled) {
            return false;
        }
        if (locked) {
            locked = false;
            lockedTarget = null;
            refreshSoon();
            return false;
        }
        DevTarget target = hoverTarget;
        if (target == null) {
            return false;
        }
        lockedTarget = target;
        locked = true;
        refreshSoon();
        return true;
    }

    public static void pointerMoved() {
        if (enabled && !locked) {
            lastPointerMoveNanos = System.nanoTime();
        }
    }

    public static void observeTarget(DevTarget target) {
        if (!enabled || locked || target == null) {
            return;
        }
        lastObservedNanos = System.nanoTime();
        if (!sameTarget(hoverTarget, target)) {
            hoverTarget = target;
            refreshSoon();
        }
    }

    public static boolean copyCurrentToClipboard() {
        final DevTarget target = getCurrentTarget();
        if (!enabled || target == null) {
            return false;
        }
        final String text = buildCopyText(target);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static DevTarget getCurrentTarget() {
        return locked ? lockedTarget : hoverTarget;
    }

    private static void ensureRefreshTimer() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (refreshTimer == null) {
                    refreshTimer = new Timer(100, e -> refreshWindow());
                    refreshTimer.setCoalesce(true);
                }
                if (!refreshTimer.isRunning()) {
                    refreshTimer.start();
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static void refreshSoon() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshWindow();
            }
        });
    }

    private static void refreshWindow() {
        if (!enabled) {
            if (window != null) {
                window.setVisible(false);
            }
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
            return;
        }

        if (!locked && hoverTarget != null
                && lastPointerMoveNanos > lastObservedNanos
                && System.nanoTime() - lastPointerMoveNanos >= STALE_HOVER_NANOS) {
            hoverTarget = null;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            if (window != null) {
                window.setVisible(false);
            }
            return;
        }

        ensureWindow(canvas);
        if (window == null) {
            return;
        }

        DevTarget target = getCurrentTarget();
        updateLabels(target);

        Point screen;
        try {
            screen = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            window.setVisible(false);
            return;
        }

        window.pack();
        int width = Math.min(WIDTH, Math.max(260, canvas.getWidth() - MARGIN * 2));
        int height = window.getPreferredSize().height;
        int x = screen.x + Math.max(MARGIN, canvas.getWidth() - width - MARGIN);
        int y = screen.y + MARGIN;
        window.setBounds(x, y, width, height);
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    private static void ensureWindow(Canvas canvas) {
        Window newOwner = SwingUtilities.getWindowAncestor(canvas);
        if (newOwner == null) {
            return;
        }
        if (window != null && owner == newOwner) {
            return;
        }
        if (window != null) {
            window.dispose();
        }
        owner = newOwner;
        window = new JWindow(owner);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.getContentPane().setLayout(new BorderLayout());
        window.getContentPane().add(buildPanel(), BorderLayout.CENTER);
    }

    private static JPanel buildPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ConsoleTheme.WINDOW);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ConsoleTheme.ACCENT_DARK),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        panel.setPreferredSize(new Dimension(WIDTH, 0));

        JLabel title = new JLabel("LIVE INSPECT");
        title.setFont(ConsoleTheme.SECTION_FONT);
        title.setForeground(ConsoleTheme.TEXT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(2));

        stateLabel.setForeground(ConsoleTheme.ACCENT);
        panel.add(stateLabel);
        panel.add(Box.createVerticalStrut(7));

        panel.add(row("Type", typeLabel));
        panel.add(row("Name", nameLabel));
        panel.add(row("Definition ID", idLabel));
        panel.add(row("Model IDs", modelsLabel));
        panel.add(row("Animation IDs", animationsLabel));
        panel.add(row("World tile", tileLabel));
        panel.add(row("Runtime", runtimeLabel));
        panel.add(Box.createVerticalStrut(7));

        JLabel shortcuts = new JLabel("F9 lock/unlock   Ctrl+C copy all   F10 close");
        shortcuts.setFont(ConsoleTheme.SMALL_FONT);
        shortcuts.setForeground(ConsoleTheme.MUTED_TEXT);
        panel.add(shortcuts);
        return panel;
    }

    private static JPanel row(String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));

        JLabel label = new JLabel(key);
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.MUTED_TEXT);

        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(ConsoleTheme.SMALL_FONT);
        label.setForeground(ConsoleTheme.TEXT);
        return label;
    }

    private static void updateLabels(DevTarget target) {
        stateLabel.setText(locked ? "LOCKED" : "HOVER");
        if (target == null) {
            typeLabel.setText("-");
            nameLabel.setText("Move cursor over an NPC or object");
            idLabel.setText("-");
            modelsLabel.setText("-");
            animationsLabel.setText("-");
            tileLabel.setText("-");
            runtimeLabel.setText("-");
            return;
        }

        typeLabel.setText(target.getType().getDisplayName());
        nameLabel.setText(target.getName());
        idLabel.setText(target.getId() >= 0 ? Integer.toString(target.getId()) : "Unresolved");
        tileLabel.setText(target.getWorldX() + ", " + target.getWorldY() + ", " + target.getPlane());
        runtimeLabel.setText(target.getRuntimeIndex() >= 0
                ? "NPC index " + target.getRuntimeIndex()
                : "Scene object");

        if (target.getType() == TargetType.OBJECT && target.getId() >= 0) {
            int[] models = DevDefinitionBridge.getObjectModelIds(target.getId());
            DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfoAny(target.getId());
            int[] animations = info == null ? new int[0] : info.getAnimationIds();
            modelsLabel.setText(displayIds(models, 7));
            animationsLabel.setText(displayIds(animations, 7));
        } else {
            modelsLabel.setText("-");
            animationsLabel.setText("-");
        }
    }

    private static String buildCopyText(DevTarget target) {
        StringBuilder out = new StringBuilder(256);
        out.append("Matrix3 Live Inspect\n");
        out.append("State: ").append(locked ? "LOCKED" : "HOVER").append('\n');
        out.append("Type: ").append(target.getType().getDisplayName()).append('\n');
        out.append("Name: ").append(target.getName()).append('\n');
        out.append("Definition ID: ").append(target.getId()).append('\n');

        if (target.getType() == TargetType.OBJECT && target.getId() >= 0) {
            int[] models = DevDefinitionBridge.getObjectModelIds(target.getId());
            DevDefinitionBridge.DefinitionInfo info = DevDefinitionBridge.getObjectInfoAny(target.getId());
            int[] animations = info == null ? new int[0] : info.getAnimationIds();
            out.append("Model IDs: ").append(joinIds(models)).append('\n');
            out.append("Animation IDs: ").append(joinIds(animations)).append('\n');
        } else {
            out.append("Model IDs: -\n");
            out.append("Animation IDs: -\n");
        }

        out.append("World Tile: ")
                .append(target.getWorldX()).append(", ")
                .append(target.getWorldY()).append(", ")
                .append(target.getPlane()).append('\n');
        out.append("Runtime: ").append(target.getRuntimeIndex() >= 0
                ? "NPC index " + target.getRuntimeIndex()
                : "Scene object");
        return out.toString();
    }

    private static String displayIds(int[] ids, int max) {
        if (ids == null || ids.length == 0) {
            return "None / unresolved";
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
            return "None / unresolved";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) out.append(", ");
            out.append(ids[i]);
        }
        return out.toString();
    }

    private static boolean sameTarget(DevTarget a, DevTarget b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getType() == b.getType()
                && a.getId() == b.getId()
                && a.getWorldX() == b.getWorldX()
                && a.getWorldY() == b.getWorldY()
                && a.getPlane() == b.getPlane()
                && a.getRuntimeIndex() == b.getRuntimeIndex();
    }
}
