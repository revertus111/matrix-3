package game.console;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

public final class ConsolePreferences {

    private static final int SETTINGS_VERSION = 1;
    private static final long SAVE_DELAY_MS = 650L;

    private static final String KEY_VERSION = "version";
    private static final String KEY_WINDOW_X = "window.x";
    private static final String KEY_WINDOW_Y = "window.y";
    private static final String KEY_WINDOW_WIDTH = "window.width";
    private static final String KEY_WINDOW_HEIGHT = "window.height";
    private static final String KEY_WINDOW_MAXIMIZED = "window.maximized";
    private static final String KEY_CONSOLE_OPEN = "console.open";
    private static final String KEY_CONSOLE_WIDTH = "console.width";
    private static final String KEY_ACTIVE_PANEL = "console.activePanel";

    private final Preferences preferences = Preferences.userNodeForPackage(ConsolePreferences.class).node("workspace");
    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Matrix3-ClientConsolePreferences");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final Object saveLock = new Object();
    private ScheduledFuture<?> pendingSave;
    private Snapshot pendingSnapshot;
    private Rectangle lastNormalBounds;
    private boolean restoring;

    public void restore(JFrame frame, ClientConsoleShell shell) {
        restoring = true;
        try {
            if (preferences.getInt(KEY_VERSION, 0) != SETTINGS_VERSION) {
                applyDefaults(frame, shell);
                return;
            }

            shell.setConsoleOpen(preferences.getBoolean(KEY_CONSOLE_OPEN, true));
            shell.setConsoleWidth(preferences.getInt(KEY_CONSOLE_WIDTH, ClientConsoleShell.DEFAULT_CONSOLE_WIDTH));

            Rectangle defaults = defaultBounds();
            Rectangle requested = new Rectangle(
                    preferences.getInt(KEY_WINDOW_X, defaults.x),
                    preferences.getInt(KEY_WINDOW_Y, defaults.y),
                    preferences.getInt(KEY_WINDOW_WIDTH, defaults.width),
                    preferences.getInt(KEY_WINDOW_HEIGHT, defaults.height));
            Rectangle safeBounds = clampToAvailableScreens(requested);
            lastNormalBounds = new Rectangle(safeBounds);
            frame.setBounds(safeBounds);

            if (preferences.getBoolean(KEY_WINDOW_MAXIMIZED, false)) {
                frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
            } else {
                frame.setExtendedState(Frame.NORMAL);
            }
        } catch (RuntimeException ex) {
            System.err.println("Client Console preferences were invalid; restoring defaults.");
            ex.printStackTrace();
            applyDefaults(frame, shell);
        } finally {
            restoring = false;
        }
    }

    public void install(final JFrame frame, final ClientConsoleShell shell) {
        if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            lastNormalBounds = new Rectangle(frame.getBounds());
        }

        shell.setLayoutChangedListener(() -> requestSave(frame, shell));
        shell.setResetLayoutAction(() -> reset(frame, shell));

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                captureNormalBounds(frame);
                requestSave(frame, shell);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                captureNormalBounds(frame);
                requestSave(frame, shell);
            }
        });

        WindowStateListener stateListener = e -> requestSave(frame, shell);
        frame.addWindowStateListener(stateListener);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveNow(frame, shell);
                saver.shutdownNow();
            }
        });
    }

    private void captureNormalBounds(JFrame frame) {
        if (restoring) {
            return;
        }
        if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            lastNormalBounds = new Rectangle(frame.getBounds());
        }
    }

    private void requestSave(JFrame frame, ClientConsoleShell shell) {
        if (restoring) {
            return;
        }

        Snapshot snapshot = capture(frame, shell);
        synchronized (saveLock) {
            pendingSnapshot = snapshot;
            if (pendingSave != null) {
                pendingSave.cancel(false);
            }
            pendingSave = saver.schedule(new Runnable() {
                @Override
                public void run() {
                    Snapshot snapshotToWrite;
                    synchronized (saveLock) {
                        snapshotToWrite = pendingSnapshot;
                        pendingSnapshot = null;
                        pendingSave = null;
                    }
                    if (snapshotToWrite != null) {
                        writeSnapshot(snapshotToWrite);
                    }
                }
            }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void saveNow(JFrame frame, ClientConsoleShell shell) {
        Snapshot snapshot = capture(frame, shell);
        synchronized (saveLock) {
            if (pendingSave != null) {
                pendingSave.cancel(false);
                pendingSave = null;
            }
            pendingSnapshot = null;
        }
        writeSnapshot(snapshot);
    }

    private Snapshot capture(JFrame frame, ClientConsoleShell shell) {
        Rectangle bounds = lastNormalBounds != null ? new Rectangle(lastNormalBounds) : new Rectangle(frame.getBounds());
        boolean maximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
        return new Snapshot(
                clampToAvailableScreens(bounds),
                maximized,
                shell.isConsoleOpen(),
                shell.getConsoleWidth(),
                shell.getActivePanelId());
    }

    private void writeSnapshot(Snapshot snapshot) {
        try {
            preferences.putInt(KEY_VERSION, SETTINGS_VERSION);
            preferences.putInt(KEY_WINDOW_X, snapshot.bounds.x);
            preferences.putInt(KEY_WINDOW_Y, snapshot.bounds.y);
            preferences.putInt(KEY_WINDOW_WIDTH, snapshot.bounds.width);
            preferences.putInt(KEY_WINDOW_HEIGHT, snapshot.bounds.height);
            preferences.putBoolean(KEY_WINDOW_MAXIMIZED, snapshot.maximized);
            preferences.putBoolean(KEY_CONSOLE_OPEN, snapshot.consoleOpen);
            preferences.putInt(KEY_CONSOLE_WIDTH, snapshot.consoleWidth);
            preferences.put(KEY_ACTIVE_PANEL, snapshot.activePanelId);
            preferences.flush();
        } catch (BackingStoreException | RuntimeException ex) {
            System.err.println("Unable to save Client Console preferences.");
            ex.printStackTrace();
        }
    }

    private void reset(JFrame frame, ClientConsoleShell shell) {
        restoring = true;
        try {
            try {
                preferences.clear();
            } catch (BackingStoreException ex) {
                System.err.println("Unable to clear Client Console preferences.");
                ex.printStackTrace();
            }
            applyDefaults(frame, shell);
        } finally {
            restoring = false;
        }
        requestSave(frame, shell);
    }

    private void applyDefaults(JFrame frame, ClientConsoleShell shell) {
        shell.setConsoleOpen(true);
        shell.setConsoleWidth(ClientConsoleShell.DEFAULT_CONSOLE_WIDTH);
        Rectangle defaults = clampToAvailableScreens(defaultBounds());
        lastNormalBounds = new Rectangle(defaults);
        frame.setExtendedState(Frame.NORMAL);
        frame.setBounds(defaults);
    }

    private Rectangle defaultBounds() {
        Rectangle usable = getUsableBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice());
        int width = Math.min(1024, usable.width);
        int height = Math.min(768, usable.height);
        width = Math.max(Math.min(ClientConsoleShell.getMinimumFrameSize().width, usable.width), width);
        height = Math.max(Math.min(ClientConsoleShell.getMinimumFrameSize().height, usable.height), height);
        int x = usable.x + Math.max(0, (usable.width - width) / 2);
        int y = usable.y + Math.max(0, (usable.height - height) / 2);
        return new Rectangle(x, y, width, height);
    }

    private Rectangle clampToAvailableScreens(Rectangle requested) {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice bestDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        long bestIntersectionArea = 0L;

        for (GraphicsDevice device : devices) {
            Rectangle usable = getUsableBounds(device);
            Rectangle intersection = usable.intersection(requested);
            long area = intersection.isEmpty() ? 0L : (long) intersection.width * (long) intersection.height;
            if (area > bestIntersectionArea) {
                bestIntersectionArea = area;
                bestDevice = device;
            }
        }

        Rectangle usable = getUsableBounds(bestDevice);
        int minWidth = Math.min(ClientConsoleShell.getMinimumFrameSize().width, usable.width);
        int minHeight = Math.min(ClientConsoleShell.getMinimumFrameSize().height, usable.height);
        int width = Math.max(minWidth, Math.min(requested.width, usable.width));
        int height = Math.max(minHeight, Math.min(requested.height, usable.height));
        int x = Math.max(usable.x, Math.min(requested.x, usable.x + usable.width - width));
        int y = Math.max(usable.y, Math.min(requested.y, usable.y + usable.height - height));
        return new Rectangle(x, y, width, height);
    }

    private Rectangle getUsableBounds(GraphicsDevice device) {
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        Rectangle bounds = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private static final class Snapshot {
        private final Rectangle bounds;
        private final boolean maximized;
        private final boolean consoleOpen;
        private final int consoleWidth;
        private final String activePanelId;

        private Snapshot(Rectangle bounds, boolean maximized, boolean consoleOpen, int consoleWidth, String activePanelId) {
            this.bounds = bounds;
            this.maximized = maximized;
            this.consoleOpen = consoleOpen;
            this.consoleWidth = consoleWidth;
            this.activePanelId = activePanelId;
        }
    }
}
