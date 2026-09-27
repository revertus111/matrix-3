package com.rs.game.player.content.construction;

import java.io.Serializable;

/**
 * Persistent virtual worker work-zone anchor.
 * Coordinates are settlement-plot relative; no dynamic-world coordinates are saved.
 */
public final class SettlementRallyPoint implements Serializable {

    private static final long serialVersionUID = 7842147281904493210L;

    public static final int DEFAULT_RADIUS = 8;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 24;

    private final long rallyId;
    private String name;
    private int plotX;
    private int plotY;
    private int plane;
    private int radius = DEFAULT_RADIUS;

    public SettlementRallyPoint(long rallyId, String name,
            int plotX, int plotY, int plane, int radius) {
        this.rallyId = rallyId;
        this.name = name;
        this.plotX = plotX;
        this.plotY = plotY;
        this.plane = plane;
        this.radius = clampRadius(radius);
    }

    public long getRallyId() {
        return rallyId;
    }

    public String getName() {
        return name == null || name.trim().isEmpty()
                ? "Rally #" + rallyId : name;
    }

    public int getPlotX() {
        return plotX;
    }

    public int getPlotY() {
        return plotY;
    }

    public int getPlane() {
        return plane;
    }

    public int getRadius() {
        return clampRadius(radius);
    }

    public void setRadius(int radius) {
        this.radius = clampRadius(radius);
    }

    public boolean normalize() {
        if (rallyId <= 0L
                || !SettlementState.isValidPlotLocation(plotX, plotY, plane)) {
            return false;
        }
        if (name == null || name.trim().isEmpty()) {
            name = "Rally #" + rallyId;
        }
        radius = clampRadius(radius);
        return true;
    }

    public boolean contains(int x, int y, int targetPlane) {
        if (targetPlane != plane) {
            return false;
        }
        long dx = x - plotX;
        long dy = y - plotY;
        long r = getRadius();
        return dx * dx + dy * dy <= r * r;
    }

    private static int clampRadius(int value) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, value));
    }
}
