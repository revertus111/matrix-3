package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Persistent settlement worker identity/state.
 *
 * Runtime NPC/world coordinates do not belong here. Home coordinates are
 * settlement-plot relative and survive dynamic-instance rebuilds.
 */
public final class SettlementWorkerState implements Serializable {

    private static final long serialVersionUID = 2699219744107299506L;

    private final long workerId;
    private final String definitionKey;
    private String name;
    private int homePlotX;
    private int homePlotY;
    private int homePlane;
    private Set<String> allowedJobs = new HashSet<String>();

    public SettlementWorkerState(long workerId, String definitionKey, String name,
            int homePlotX, int homePlotY, int homePlane) {
        this.workerId = workerId;
        this.definitionKey = definitionKey;
        this.name = name;
        this.homePlotX = homePlotX;
        this.homePlotY = homePlotY;
        this.homePlane = homePlane;
    }

    public long getWorkerId() {
        return workerId;
    }

    public String getDefinitionKey() {
        return definitionKey;
    }

    public String getName() {
        return name;
    }

    public int getHomePlotX() {
        return homePlotX;
    }

    public int getHomePlotY() {
        return homePlotY;
    }

    public int getHomePlane() {
        return homePlane;
    }

    public boolean isJobAllowed(SettlementWorkerJob job) {
        normalizeJobs();
        return job != null && allowedJobs.contains(job.getKey());
    }

    public void setJobAllowed(SettlementWorkerJob job, boolean allowed) {
        normalizeJobs();
        if (job == null) {
            return;
        }
        if (allowed) {
            allowedJobs.add(job.getKey());
        } else {
            allowedJobs.remove(job.getKey());
        }
    }

    public Set<String> snapshotAllowedJobKeys() {
        normalizeJobs();
        return new HashSet<String>(allowedJobs);
    }

    public String getAllowedJobsSummary() {
        normalizeJobs();
        StringBuilder summary = new StringBuilder();
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(job.getDisplayName()).append("=")
                    .append(isJobAllowed(job) ? "ON" : "OFF");
        }
        return summary.toString();
    }

    void normalize(SettlementWorkerDefinition definition) {
        if (definition == null) {
            return;
        }
        if (name == null || name.trim().isEmpty()) {
            name = definition.getDisplayName();
        }
        if (!SettlementState.isValidPlotLocation(homePlotX, homePlotY, homePlane)) {
            homePlotX = definition.getArrivalPlotX();
            homePlotY = definition.getArrivalPlotY();
            homePlane = definition.getArrivalPlane();
        }
        normalizeJobs();
    }

    private void normalizeJobs() {
        if (allowedJobs == null) {
            allowedJobs = new HashSet<String>();
        }
        Iterator<String> iterator = allowedJobs.iterator();
        while (iterator.hasNext()) {
            if (SettlementWorkerJob.forKey(iterator.next()) == null) {
                iterator.remove();
            }
        }
    }
}
