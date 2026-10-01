package com.rs.game.player.content.construction;

import java.io.Serializable;

/**
 * Persistent plot-relative state for one gatherable settlement resource node.
 * Depleted nodes remain saved at zero so re-entry/relog cannot regenerate them.
 */
public final class SettlementResourceNodeState implements Serializable {

    private static final long serialVersionUID = -7027567920128978746L;

    private long nodeId;
    private String definitionKey;
    private int plotX;
    private int plotY;
    private int plane;
    private long maxAmount;
    private long remainingAmount;

    public SettlementResourceNodeState(long nodeId, String definitionKey,
            int plotX, int plotY, int plane, long maxAmount, long remainingAmount) {
        this.nodeId = nodeId;
        this.definitionKey = definitionKey;
        this.plotX = plotX;
        this.plotY = plotY;
        this.plane = plane;
        this.maxAmount = maxAmount;
        this.remainingAmount = remainingAmount;
    }

    public boolean normalize() {
        SettlementResourceNode definition = getDefinition();
        if (nodeId <= 0L || definition == null
                || !SettlementState.isValidPlotLocation(plotX, plotY, plane)) return false;
        if (maxAmount <= 0L) maxAmount = definition.getDefaultStartingAmount();
        if (remainingAmount < 0L) remainingAmount = 0L;
        else if (remainingAmount > maxAmount) remainingAmount = maxAmount;
        return true;
    }

    public long getNodeId() { return nodeId; }
    public String getDefinitionKey() { return definitionKey; }
    public String getKey() { return definitionKey; }
    public SettlementResourceNode getDefinition() { return SettlementResourceNode.forKey(definitionKey); }
    public SettlementResource getResource() {
        SettlementResourceNode d = getDefinition();
        return d == null ? null : d.getResource();
    }
    public SettlementResourceNode.SourceKind getSourceKind() {
        SettlementResourceNode d = getDefinition();
        return d == null ? null : d.getSourceKind();
    }
    public int getRuntimeId() {
        SettlementResourceNode d = getDefinition();
        return d == null ? -1 : d.getRuntimeId();
    }
    public int getObjectType() {
        SettlementResourceNode d = getDefinition();
        return d == null ? -1 : d.getObjectType();
    }
    public int getAnimationId() {
        SettlementResourceNode d = getDefinition();
        return d == null ? -1 : d.getAnimationId();
    }
    public int getPlotX() { return plotX; }
    public int getPlotY() { return plotY; }
    public int getPlane() { return plane; }
    public long getMaxAmount() { return maxAmount; }
    public long getRemainingAmount() { return remainingAmount; }
    public boolean isDepleted() { return remainingAmount <= 0L; }

    long harvest(long amount) {
        if (amount <= 0L || remainingAmount <= 0L) return 0L;
        long harvested = Math.min(amount, remainingAmount);
        remainingAmount -= harvested;
        return harvested;
    }
}
