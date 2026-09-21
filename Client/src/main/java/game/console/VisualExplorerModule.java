package game.console;

import java.util.EnumSet;

import javax.swing.JComponent;

/**
 * Common module contract for the viewer-now/editor-later Visual Explorer.
 */
public interface VisualExplorerModule {

    enum Capability {
        BROWSE,
        PREVIEW,
        INSPECT,
        REFERENCES,
        EDIT,
        SAVE
    }

    interface Navigator {
        void openAsset(VisualExplorerAssetRef asset);
    }

    String getModuleId();

    String getTitle();

    JComponent getComponent();

    EnumSet<Capability> getCapabilities();

    boolean supports(VisualExplorerAssetRef asset);

    void openAsset(VisualExplorerAssetRef asset);
}
