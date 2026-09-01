package game.console.bosslabs;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
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
        setContentPane(new BossLabsPanel());
        setMinimumSize(new java.awt.Dimension(900, 620));
        setSize(1180, 780);
        setLocationRelativeTo(findVisibleOwner());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (instance == BossLabsWindow.this) {
                    instance = null;
                }
            }
        });
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
