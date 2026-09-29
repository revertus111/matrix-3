package com.rs.game.player.content.construction;

import java.io.Serializable;

/**
 * Persistent server-owned identity for one continuous Construction conveyor.
 *
 * Coordinates are settlement-plot relative. Runtime world coordinates are
 * projected by SettlementInstance and must never be serialized here.
 */
public final class SettlementConveyorRun implements Serializable {

    private static final long serialVersionUID = 4208233036847147384L;

    private final long runId;
    private final int startPlotX;
    private final int startPlotY;
    private final int endPlotX;
    private final int endPlotY;
    private final int plane;

    public SettlementConveyorRun(long runId,
            int startPlotX, int startPlotY,
            int endPlotX, int endPlotY, int plane) {
        this.runId = runId;
        this.startPlotX = startPlotX;
        this.startPlotY = startPlotY;
        this.endPlotX = endPlotX;
        this.endPlotY = endPlotY;
        this.plane = plane;
    }

    public long getRunId() {
        return runId;
    }

    public int getStartPlotX() {
        return startPlotX;
    }

    public int getStartPlotY() {
        return startPlotY;
    }

    public int getEndPlotX() {
        return endPlotX;
    }

    public int getEndPlotY() {
        return endPlotY;
    }

    public int getPlane() {
        return plane;
    }

    public boolean isValid() {
        return runId > 0L
                && SettlementState.isValidPlotLocation(startPlotX, startPlotY, plane)
                && SettlementState.isValidPlotLocation(endPlotX, endPlotY, plane)
                && (startPlotX != endPlotX || startPlotY != endPlotY);
    }

    public double getLengthTiles() {
        double dx = endPlotX - startPlotX;
        double dy = endPlotY - startPlotY;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
