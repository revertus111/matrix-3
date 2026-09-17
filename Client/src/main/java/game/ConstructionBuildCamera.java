package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Read-only Construction camera diagnostic.
 *
 * The first native Free Build attempt proved that writing the generic renderer
 * camera globals is not sufficient to reproduce the working Matrix3 Alt
 * free-camera. Until runtime evidence identifies the real camera/input owner,
 * this class must observe camera state only and never mutate it.
 */
public final class ConstructionBuildCamera {

    private static final int REFRESH_MS = 100;

    private static volatile boolean active;
    private static volatile boolean altDown;
    private static volatile boolean wDown;
    private static volatile boolean aDown;
    private static volatile boolean sDown;
    private static volatile boolean dDown;
    private static volatile boolean qDown;
    private static volatile boolean eDown;
    private static volatile String lastKey = "-";

    private static volatile int renderMode = -1;
    private static volatile int renderX;
    private static volatile int renderY;
    private static volatile int renderZ;
    private static volatile boolean objectCameraPath;
    private static volatile boolean renderObserved;

    private static volatile int baselineRenderX;
    private static volatile int baselineRenderY;
    private static volatile int baselineRenderZ;
    private static volatile boolean baselineCaptured;
    private static volatile long lastRenderObservationMillis;

    private static boolean inputListenerInstalled;
    private static Timer debugTimer;
    private static JWindow debugWindow;
    private static JTextArea debugArea;
    private static Window debugOwner;

    private ConstructionBuildCamera() {
    }

    /**
     * During this diagnostic checkpoint, "requested" means the Construction
     * camera trace is active. It does not mean Construction owns camera motion.
     */
    public static boolean isRequested() {
        return active;
    }

    public static String enter() {
        if (active) {
            return "Construction camera diagnostic is already active.";
        }
        active = true;
        baselineCaptured = false;
        renderObserved = false;
        lastRenderObservationMillis = 0L;
        clearKeyState();
        ensureInputListener();
        startDebugTimer();
        return "Construction camera diagnostic active. Use the normal Alt free-camera and watch CAM DEBUG.";
    }

    public static String exit() {
        if (!active) {
            return "Construction camera diagnostic is not active.";
        }
        active = false;
        clearKeyState();
        stopDebugTimer();
        hideDebugWindow();
        return "Construction camera diagnostic closed.";
    }

    /**
     * Intentionally no-op while the camera owner is under runtime trace.
     */
    public static void stopMovement() {
        // Read-only diagnostic: do not mutate Matrix3 camera/input state.
    }

    /**
     * Called from the real viewport render seam with the XYZ that will actually
     * be submitted to Class523.method6240(...).
     *
     * verified-static: mode 1 uses the Class411 camera object path; other normal
     * modes render from the generic camera globals. The special incoming-camera
     * path is also reported as an object/special path.
     */
    public static void observeRenderCamera(int mode, int x, int y, int z, boolean objectPath) {
        if (!active) {
            return;
        }
        renderMode = mode;
        renderX = x;
        renderY = y;
        renderZ = z;
        objectCameraPath = objectPath;
        lastRenderObservationMillis = System.currentTimeMillis();
        renderObserved = true;

        if (!baselineCaptured) {
            baselineRenderX = x;
            baselineRenderY = y;
            baselineRenderZ = z;
            baselineCaptured = true;
        }
    }

    private static synchronized void ensureInputListener() {
        if (inputListenerInstalled) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override
                public void eventDispatched(AWTEvent event) {
                    if (!active || !(event instanceof KeyEvent)) {
                        return;
                    }
                    KeyEvent key = (KeyEvent) event;
                    Canvas canvas = Class584.aCanvas7745;
                    if (canvas == null || key.getSource() != canvas) {
                        return;
                    }

                    boolean pressed = key.getID() == KeyEvent.KEY_PRESSED;
                    boolean released = key.getID() == KeyEvent.KEY_RELEASED;
                    if (!pressed && !released) {
                        return;
                    }

                    boolean value = pressed;
                    switch (key.getKeyCode()) {
                        case KeyEvent.VK_ALT:
                            altDown = value;
                            break;
                        case KeyEvent.VK_W:
                            wDown = value;
                            break;
                        case KeyEvent.VK_A:
                            aDown = value;
                            break;
                        case KeyEvent.VK_S:
                            sDown = value;
                            break;
                        case KeyEvent.VK_D:
                            dDown = value;
                            break;
                        case KeyEvent.VK_Q:
                            qDown = value;
                            break;
                        case KeyEvent.VK_E:
                            eDown = value;
                            break;
                        default:
                            break;
                    }

                    if (pressed) {
                        lastKey = KeyEvent.getKeyText(key.getKeyCode());
                    }
                    // Diagnostic only: never consume Matrix3 input.
                }
            }, AWTEvent.KEY_EVENT_MASK);
            inputListenerInstalled = true;
        } catch (RuntimeException ex) {
            active = false;
        }
    }

    private static void startDebugTimer() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    startDebugTimer();
                }
            });
            return;
        }
        if (debugTimer == null) {
            debugTimer = new Timer(REFRESH_MS, e -> refreshDebugWindow());
            debugTimer.setCoalesce(true);
        }
        if (!debugTimer.isRunning()) {
            debugTimer.start();
        }
        refreshDebugWindow();
    }

    private static void stopDebugTimer() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    stopDebugTimer();
                }
            });
            return;
        }
        if (debugTimer != null) {
            debugTimer.stop();
        }
    }

    private static void refreshDebugWindow() {
        if (!active) {
            hideDebugWindow();
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            hideDebugWindow();
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(canvas);
        if (owner == null) {
            hideDebugWindow();
            return;
        }
        ensureDebugWindow(owner);

        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            hideDebugWindow();
            return;
        }

        int width = Math.min(1040, Math.max(540, canvas.getWidth() - 20));
        int height = 78;
        int x = canvasLocation.x + Math.max(10, (canvas.getWidth() - width) / 2);
        int y = canvasLocation.y + Math.max(8, canvas.getHeight() - height - 12);
        debugWindow.setBounds(x, y, width, height);

        int mode = Class18.anInt143 * 625220759;
        int gx = Class36.anInt387 * 386814715;
        int gy = Class572_Sub13_Sub2.anInt11451 * -1094666305;
        int gz = Class49.anInt490 * -999214779;
        int pitch = Class455.anInt5187 * 1406555935;
        int yaw = Class406.anInt4765 * 426389501;

        String source;
        if (objectCameraPath && renderMode == 1) {
            source = "CLASS411";
        } else if (objectCameraPath) {
            source = "SPECIAL";
        } else {
            source = "GLOBALS";
        }

        long age = lastRenderObservationMillis == 0L
                ? -1L
                : System.currentTimeMillis() - lastRenderObservationMillis;

        int dx = baselineCaptured ? renderX - baselineRenderX : 0;
        int dy = baselineCaptured ? renderY - baselineRenderY : 0;
        int dz = baselineCaptured ? renderZ - baselineRenderZ : 0;

        String line1 = "CAM DEBUG [READ ONLY] mode=" + mode
                + " renderMode=" + renderMode + " source=" + source
                + " ALT=" + onOff(altDown)
                + " W=" + onOff(wDown) + " A=" + onOff(aDown)
                + " S=" + onOff(sDown) + " D=" + onOff(dDown)
                + " Q=" + onOff(qDown) + " E=" + onOff(eDown)
                + " lastKey=" + lastKey;

        String line2 = renderObserved
                ? "renderXYZ=" + renderX + "," + renderY + "," + renderZ
                        + "  deltaFromOpen=" + signed(dx) + "," + signed(dy) + "," + signed(dz)
                        + "  sampleAgeMs=" + age
                : "renderXYZ=WAITING FOR VIEWPORT SAMPLE";

        String line3 = "genericXYZ=" + gx + "," + gy + "," + gz
                + "  pitch=" + pitch + " yaw=" + yaw
                + "  (Construction is not writing any camera values)";

        debugArea.setText(line1 + "\n" + line2 + "\n" + line3);
        if (!debugWindow.isVisible()) {
            debugWindow.setVisible(true);
        }
    }

    private static void ensureDebugWindow(Window owner) {
        if (debugWindow != null && debugOwner == owner) {
            return;
        }
        if (debugWindow != null) {
            debugWindow.dispose();
        }

        debugOwner = owner;
        debugArea = new JTextArea(3, 100);
        debugArea.setEditable(false);
        debugArea.setFocusable(false);
        debugArea.setFont(new Font("Monospaced", Font.BOLD, 12));
        debugArea.setForeground(new Color(109, 220, 255));
        debugArea.setBackground(new Color(13, 20, 28));
        debugArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        debugWindow = new JWindow(owner);
        debugWindow.setFocusableWindowState(false);
        debugWindow.setAutoRequestFocus(false);
        debugWindow.getContentPane().add(debugArea);
    }

    private static void hideDebugWindow() {
        if (debugWindow != null && debugWindow.isVisible()) {
            debugWindow.setVisible(false);
        }
    }

    private static String onOff(boolean value) {
        return value ? "DOWN" : "-";
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static void clearKeyState() {
        altDown = false;
        wDown = false;
        aDown = false;
        sDown = false;
        dDown = false;
        qDown = false;
        eDown = false;
        lastKey = "-";
    }
}
