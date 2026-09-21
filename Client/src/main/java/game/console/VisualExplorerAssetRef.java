package game.console;

/**
 * Small shared identity passed between Visual Explorer modules.
 *
 * It deliberately carries identity/context only. Editing, previewing and cache
 * ownership remain with the module or existing Matrix3 specialist authority.
 */
public final class VisualExplorerAssetRef {

    public enum Kind {
        INTERFACE,
        INTERFACE_COMPONENT,
        SPRITE,
        HOVER_ACTION,
        CURSOR
    }

    private final Kind kind;
    private final int primaryId;
    private final int secondaryId;
    private final String source;

    private VisualExplorerAssetRef(Kind kind, int primaryId, int secondaryId, String source) {
        this.kind = kind;
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
        this.source = source == null ? "" : source;
    }

    public static VisualExplorerAssetRef interfaceAsset(int interfaceId, String source) {
        return new VisualExplorerAssetRef(Kind.INTERFACE, interfaceId, -1, source);
    }

    public static VisualExplorerAssetRef component(int interfaceId, int componentId, String source) {
        return new VisualExplorerAssetRef(Kind.INTERFACE_COMPONENT, interfaceId, componentId, source);
    }

    public static VisualExplorerAssetRef sprite(int spriteId, String source) {
        return new VisualExplorerAssetRef(Kind.SPRITE, spriteId, -1, source);
    }

    public static VisualExplorerAssetRef hoverAction(int actionId, String source) {
        return new VisualExplorerAssetRef(Kind.HOVER_ACTION, actionId, -1, source);
    }

    public static VisualExplorerAssetRef cursor(int cursorId, String source) {
        return new VisualExplorerAssetRef(Kind.CURSOR, cursorId, -1, source);
    }

    public Kind getKind() {
        return kind;
    }

    public int getPrimaryId() {
        return primaryId;
    }

    public int getSecondaryId() {
        return secondaryId;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return kind + " " + primaryId
                + (secondaryId < 0 ? "" : ":" + secondaryId)
                + (source.length() == 0 ? "" : " from " + source);
    }
}
