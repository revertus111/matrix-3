package game;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Construction-only client camera. This bypasses the incompatible legacy
 * Orb-of-Oculus interface and drives Matrix3's verified render-camera globals
 * directly while the Construction palette is open.
 */
public final class ConstructionBuildCamera {

    private static final int TICK_MS = 16;
    private static final int CAMERA_MODE_ENCODE = 711307203;
    private static final int FREE_BUILD_MODE = 5;
    private static final int PITCH_MIN = 1024;
    private static final int PITCH_MAX = 3072;
    private static final float BASE_SPEED = 1536.0F;
    private static final float FAST_SPEED = 4096.0F;
    private static final float PRECISE_SPEED = 512.0F;
    private static final float ACCELERATION = 7000.0F;
    private static final float VERTICAL_SPEED = 1400.0F;
    private static final int MOUSE_YAW_SCALE = 12;
    private static final int MOUSE_PITCH_SCALE = 8;

    private static volatile boolean active;
    private static volatile boolean forward;
    private static volatile boolean backward;
    private static volatile boolean left;
    private static volatile boolean right;
    private static volatile boolean up;
    private static volatile boolean down;
    private static volatile boolean fast;
    private static volatile boolean precise;
    private static volatile boolean rightDragging;

    private static float cameraX;
    private static float cameraY;
    private static float cameraZ;
    private static float velocityX;
    private static float velocityY;
    private static float velocityZ;
    private static int pitch;
    private static int yaw;
    private static int lastMouseX;
    private static int lastMouseY;
    private static long lastTickNanos;

    private static int previousModeRaw;
    private static int previousXRaw;
    private static int previousYRaw;
    private static int previousZRaw;
    private static int previousPitchRaw;
    private static int previousYawRaw;

    private static boolean inputListenerInstalled;
    private static Timer timer;

    private ConstructionBuildCamera() {
    }

    public static boolean isRequested() {
        return active;
    }

    public static String enter() {
        if (active) {
            return "Construction Free Build camera is already active.";
        }
        if (Class584.aCanvas7745 == null) {
            return "Construction Free Build camera is waiting for the game canvas.";
        }

        previousModeRaw = Class18.anInt143;
        previousXRaw = Class36.anInt387;
        previousYRaw = Class572_Sub13_Sub2.anInt11451;
        previousZRaw = Class49.anInt490;
        previousPitchRaw = Class455.anInt5187;
        previousYawRaw = Class406.anInt4765;

        cameraX = Class36.anInt387 * 386814715;
        cameraY = Class572_Sub13_Sub2.anInt11451 * -1094666305;
        cameraZ = Class49.anInt490 * -999214779;
        pitch = Class455.anInt5187 * 1406555935;
        yaw = Class406.anInt4765 * 426389501;
        velocityX = velocityY = velocityZ = 0.0F;
        clearKeys();
        lastTickNanos = System.nanoTime();

        // verified-static: modes 1/2/4/6 have dedicated update paths in
        // Class343.method4302; other modes render from the camera globals below.
        Class18.anInt143 = CAMERA_MODE_ENCODE * FREE_BUILD_MODE;
        active = true;
        ensureInputListener();
        startTimer();
        applyCameraGlobals();
        return "Construction Free Build camera active.";
    }

    public static String exit() {
        if (!active) {
            return "Construction build camera is not active.";
        }
        active = false;
        clearKeys();
        rightDragging = false;
        velocityX = velocityY = velocityZ = 0.0F;
        stopTimer();

        Class18.anInt143 = previousModeRaw;
        Class36.anInt387 = previousXRaw;
        Class572_Sub13_Sub2.anInt11451 = previousYRaw;
        Class49.anInt490 = previousZRaw;
        Class455.anInt5187 = previousPitchRaw;
        Class406.anInt4765 = previousYawRaw;
        return "Construction Free Build camera closed.";
    }

    public static void stopMovement() {
        velocityX = velocityY = velocityZ = 0.0F;
        forward = backward = left = right = up = down = false;
    }

    private static synchronized void ensureInputListener() {
        if (inputListenerInstalled) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (!active) {
                    return;
                }
                if (event instanceof KeyEvent) {
                    handleKey((KeyEvent) event);
                } else if (event instanceof MouseEvent) {
                    handleMouse((MouseEvent) event);
                }
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        inputListenerInstalled = true;
    }

    private static void handleKey(KeyEvent event) {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || event.getSource() != canvas) {
            return;
        }
        boolean pressed = event.getID() == KeyEvent.KEY_PRESSED;
        boolean released = event.getID() == KeyEvent.KEY_RELEASED;
        if (!pressed && !released) {
            return;
        }
        boolean value = pressed;
        switch (event.getKeyCode()) {
            case KeyEvent.VK_W:
                forward = value;
                break;
            case KeyEvent.VK_S:
                backward = value;
                break;
            case KeyEvent.VK_A:
                left = value;
                break;
            case KeyEvent.VK_D:
                right = value;
                break;
            case KeyEvent.VK_E:
                up = value;
                break;
            case KeyEvent.VK_Q:
                down = value;
                break;
            case KeyEvent.VK_SHIFT:
                fast = value;
                break;
            case KeyEvent.VK_CONTROL:
                precise = value;
                break;
            default:
                return;
        }
        event.consume();
    }

    private static void handleMouse(MouseEvent event) {
        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || event.getSource() != canvas) {
            return;
        }
        if (event.getID() == MouseEvent.MOUSE_PRESSED) {
            if (event.getButton() == MouseEvent.BUTTON3) {
                rightDragging = true;
                lastMouseX = event.getXOnScreen();
                lastMouseY = event.getYOnScreen();
                event.consume();
            } else if (event.getButton() == MouseEvent.BUTTON1) {
                stopMovement();
                // Paint-mode placement uses Matrix3's already-resolved hover tile
                // and consumes the click so the player does not also Walk Here.
                if (ConstructionPlacementController.placeHoveredFromBuildCamera()) {
                    event.consume();
                }
            }
        } else if (event.getID() == MouseEvent.MOUSE_RELEASED && event.getButton() == MouseEvent.BUTTON3) {
            rightDragging = false;
            event.consume();
        } else if (event.getID() == MouseEvent.MOUSE_DRAGGED && rightDragging) {
            int x = event.getXOnScreen();
            int y = event.getYOnScreen();
            int dx = x - lastMouseX;
            int dy = y - lastMouseY;
            lastMouseX = x;
            lastMouseY = y;
            yaw = (yaw + dx * MOUSE_YAW_SCALE) & 0x3fff;
            pitch += dy * MOUSE_PITCH_SCALE;
            if (pitch < PITCH_MIN) {
                pitch = PITCH_MIN;
            } else if (pitch > PITCH_MAX) {
                pitch = PITCH_MAX;
            }
            event.consume();
        }
    }

    private static void startTimer() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    startTimer();
                }
            });
            return;
        }
        if (timer == null) {
            timer = new Timer(TICK_MS, e -> tick());
            timer.setCoalesce(true);
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private static void stopTimer() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    stopTimer();
                }
            });
            return;
        }
        if (timer != null) {
            timer.stop();
        }
    }

    private static void tick() {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        float dt = (now - lastTickNanos) / 1000000000.0F;
        lastTickNanos = now;
        if (dt <= 0.0F || dt > 0.05F) {
            dt = 0.016F;
        }

        float speed = precise ? PRECISE_SPEED : (fast ? FAST_SPEED : BASE_SPEED);
        float radians = (float) (yaw * (Math.PI * 2.0 / 16384.0));
        float forwardX = (float) Math.sin(radians);
        float forwardZ = -(float) Math.cos(radians);
        float rightX = (float) Math.cos(radians);
        float rightZ = (float) Math.sin(radians);

        float inputForward = (forward ? 1.0F : 0.0F) - (backward ? 1.0F : 0.0F);
        float inputRight = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
        float targetX = (forwardX * inputForward + rightX * inputRight) * speed;
        float targetZ = (forwardZ * inputForward + rightZ * inputRight) * speed;
        float verticalInput = (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F);
        float targetY = verticalInput * VERTICAL_SPEED;

        velocityX = approach(velocityX, targetX, ACCELERATION * dt);
        velocityZ = approach(velocityZ, targetZ, ACCELERATION * dt);
        velocityY = approach(velocityY, targetY, ACCELERATION * dt);

        cameraX += velocityX * dt;
        cameraZ += velocityZ * dt;
        cameraY += velocityY * dt;
        applyCameraGlobals();
    }

    private static float approach(float current, float target, float delta) {
        if (current < target) {
            return Math.min(current + delta, target);
        }
        if (current > target) {
            return Math.max(current - delta, target);
        }
        return current;
    }

    private static void applyCameraGlobals() {
        Class36.anInt387 = ((int) cameraX) * 70707251;
        Class572_Sub13_Sub2.anInt11451 = ((int) cameraY) * -371247041;
        Class49.anInt490 = ((int) cameraZ) * -114706035;
        Class455.anInt5187 = pitch * 58615007;
        Class406.anInt4765 = yaw * 1259382101;
    }

    private static void clearKeys() {
        forward = backward = left = right = up = down = false;
        fast = precise = false;
    }
}
