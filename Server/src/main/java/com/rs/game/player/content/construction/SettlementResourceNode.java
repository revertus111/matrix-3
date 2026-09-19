package com.rs.game.player.content.construction;

/**
 * Fixed Phase-1 starter resource nodes inside the settlement plot.
 *
 * Runtime visuals are provisional cache assets; plot ownership and resource
 * identity are stable. Nodes deposit directly into settlement storage.
 */
public enum SettlementResourceNode {

    WOOD_TREE("wood-tree", SettlementResource.WOOD, SourceKind.OBJECT, 1276, 10, 8, 8, 879),
    FOOD_SPOT("food-spot", SettlementResource.FOOD, SourceKind.NPC, 327, -1, 12, 8, 621),
    STONE_OUTCROP("stone-outcrop", SettlementResource.STONE, SourceKind.OBJECT, 11933, 10, 16, 8, 625),
    ORE_OUTCROP("ore-outcrop", SettlementResource.BASIC_ORE, SourceKind.OBJECT, 11936, 10, 20, 8, 625);

    public enum SourceKind {
        OBJECT,
        NPC
    }

    private final String key;
    private final SettlementResource resource;
    private final SourceKind sourceKind;
    private final int runtimeId;
    private final int objectType;
    private final int plotX;
    private final int plotY;
    private final int animationId;

    SettlementResourceNode(String key, SettlementResource resource,
            SourceKind sourceKind, int runtimeId, int objectType,
            int plotX, int plotY, int animationId) {
        this.key = key;
        this.resource = resource;
        this.sourceKind = sourceKind;
        this.runtimeId = runtimeId;
        this.objectType = objectType;
        this.plotX = plotX;
        this.plotY = plotY;
        this.animationId = animationId;
    }

    public String getKey() {
        return key;
    }

    public SettlementResource getResource() {
        return resource;
    }

    public SourceKind getSourceKind() {
        return sourceKind;
    }

    public int getRuntimeId() {
        return runtimeId;
    }

    public int getObjectType() {
        return objectType;
    }

    public int getPlotX() {
        return plotX;
    }

    public int getPlotY() {
        return plotY;
    }

    public int getAnimationId() {
        return animationId;
    }

    public boolean occupies(int x, int y, int plane) {
        return plane == SettlementState.PLOT_PLANE && plotX == x && plotY == y;
    }

    public static SettlementResourceNode forObject(int id, int plotX, int plotY, int plane) {
        for (SettlementResourceNode node : values()) {
            if (node.sourceKind == SourceKind.OBJECT
                    && node.runtimeId == id && node.occupies(plotX, plotY, plane)) {
                return node;
            }
        }
        return null;
    }

    public static SettlementResourceNode forNpc(int id, int plotX, int plotY, int plane) {
        for (SettlementResourceNode node : values()) {
            if (node.sourceKind == SourceKind.NPC
                    && node.runtimeId == id && node.occupies(plotX, plotY, plane)) {
                return node;
            }
        }
        return null;
    }

    public static boolean occupiesPlotTile(int plotX, int plotY, int plane) {
        for (SettlementResourceNode node : values()) {
            if (node.occupies(plotX, plotY, plane)) {
                return true;
            }
        }
        return false;
    }
}
