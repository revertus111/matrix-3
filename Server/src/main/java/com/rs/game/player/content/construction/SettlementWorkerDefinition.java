package com.rs.game.player.content.construction;

/**
 * Stable settlement worker definitions.
 *
 * Cache NPC ids are runtime presentation only; persistent state stores the
 * stable definition key.
 */
public enum SettlementWorkerDefinition {

    STARTER_SETTLER("starter-settler", "Settler", 1, 24, 12, SettlementState.PLOT_PLANE),
    RECRUITED_SETTLER("recruited-settler", "Settler", 1, 26, 12, SettlementState.PLOT_PLANE);

    private final String key;
    private final String displayName;
    private final int npcId;
    private final int arrivalPlotX;
    private final int arrivalPlotY;
    private final int arrivalPlane;

    SettlementWorkerDefinition(String key, String displayName, int npcId,
            int arrivalPlotX, int arrivalPlotY, int arrivalPlane) {
        this.key = key;
        this.displayName = displayName;
        this.npcId = npcId;
        this.arrivalPlotX = arrivalPlotX;
        this.arrivalPlotY = arrivalPlotY;
        this.arrivalPlane = arrivalPlane;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getNpcId() {
        return npcId;
    }

    public int getArrivalPlotX() {
        return arrivalPlotX;
    }

    public int getArrivalPlotY() {
        return arrivalPlotY;
    }

    public int getArrivalPlane() {
        return arrivalPlane;
    }

    public boolean occupiesArrivalTile(int plotX, int plotY, int plane) {
        return arrivalPlotX == plotX && arrivalPlotY == plotY && arrivalPlane == plane;
    }

    public static SettlementWorkerDefinition forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementWorkerDefinition definition : values()) {
            if (definition.key.equals(key)) {
                return definition;
            }
        }
        return null;
    }

    public static boolean isReservedArrivalTile(int plotX, int plotY, int plane) {
        for (SettlementWorkerDefinition definition : values()) {
            if (definition.occupiesArrivalTile(plotX, plotY, plane)) {
                return true;
            }
        }
        return false;
    }
}
