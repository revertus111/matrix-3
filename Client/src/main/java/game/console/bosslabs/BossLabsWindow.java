package game.console.bosslabs;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import game.console.ConsoleTheme;

/**
 * External Matrix3 developer window for BossLabs.
 *
 * The window owns presentation/lifecycle only. Boss combat and world behavior
 * remain server-owned.
 */
public final class BossLabsWindow extends JFrame {

    private static final long serialVersionUID = 5631579546363869002L;
    private static BossLabsWindow instance;

    private final BossLabsPanel bossLabsPanel;
    private final BossLabsTestingPanel testingPanel;

    public static void open() {
        Runnable opener = new Runnable() {
            @Override
            public void run() {
                if (instance == null || !instance.isDisplayable()) {
                    instance = new BossLabsWindow();
                }
                instance.setVisible(true);
                instance.setExtendedState(instance.getExtendedState() & ~Frame.ICONIFIED);
                instance.toFront();
                instance.requestFocus();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            opener.run();
        } else {
            SwingUtilities.invokeLater(opener);
        }
    }

    private BossLabsWindow() {
        super("Matrix3 - BossLabs");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(ConsoleTheme.WINDOW);
        bossLabsPanel = new BossLabsPanel();
        testingPanel = new BossLabsTestingPanel();
        if (!replaceTestingTab(bossLabsPanel, testingPanel))
            throw new IllegalStateException("BossLabs Testing tab was not found.");
        BossLabsClientBridge.setTestingListener(testingPanel);
        setContentPane(bossLabsPanel);
        setMinimumSize(new java.awt.Dimension(900, 620));
        setSize(1180, 780);
        setLocationRelativeTo(findVisibleOwner());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                BossLabsClientBridge.clearTestingListener(testingPanel);
                bossLabsPanel.disposeBridge();
                if (instance == BossLabsWindow.this) {
                    instance = null;
                }
            }
        });
    }

    /**
     * Keeps the established BossLabsPanel tab shell stable while replacing only
     * its original disabled Testing placeholder with the live testing panel.
     */
    private static boolean replaceTestingTab(Component component, JComponent replacement) {
        if (component instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) component;
            for (int index = 0; index < tabs.getTabCount(); index++) {
                if ("Testing".equals(tabs.getTitleAt(index))) {
                    tabs.setComponentAt(index, replacement);
                    return true;
                }
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (replaceTestingTab(child, replacement))
                    return true;
            }
        }
        return false;
    }

    private static Window findVisibleOwner() {
        for (Window window : Window.getWindows()) {
            if (window.isVisible() && window instanceof JFrame) {
                return window;
            }
        }
        return null;
    }
}
