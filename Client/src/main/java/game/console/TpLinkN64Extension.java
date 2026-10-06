package game.console;

import java.awt.Component;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;

/** Adds the TP Link workspace to the established N64 panel without changing Mario. */
final class TpLinkN64Extension {

    private TpLinkN64Extension() {
    }

    static JComponent create() {
        N64Panel panel = new N64Panel();
        JTabbedPane tabs = findTopLevelTabs(panel);
        if (tabs != null) {
            tabs.addTab("TP Link", new TpLinkPanel());
        } else {
            System.err.println("[TP Workbench] N64 game tabs not found; TP tab unavailable");
        }
        return panel;
    }

    private static JTabbedPane findTopLevelTabs(N64Panel panel) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JTabbedPane) {
                return (JTabbedPane) component;
            }
        }
        return null;
    }
}
