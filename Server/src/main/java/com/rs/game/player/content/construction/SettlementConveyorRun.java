package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent server-owned identity and transport state for one continuous
 * Construction conveyor.
 *
 * Coordinates are settlement-plot relative. Runtime world coordinates are
 * projected by SettlementInstance and must never be serialized here.
 */
public final class SettlementConveyorRun implements Serializable {

    private static final long serialVersionUID = 4208233036847147384L;

    /**
     * V1 transport speed belongs to the conveyor, not to item visual profiles.
     * Future belt tiers can replace this with a persisted tier/speed owner.
     */
    public static final double BASIC_SPEED_TILES_PER_SECOND = 1.25;
    public static final double PAYLOAD_SPACING_TILES = 0.85;

    private static final double EPSILON = 0.0001;
    private static final int MAX_DEVELOPMENT_PAYLOADS = 128;

    private final long runId;
    private final int startPlotX;
    private final int startPlotY;
    private final int endPlotX;
    private final int endPlotY;
    private final int plane;

    /*
     * Added after the first persistent-run schema. These are intentionally
     * non-final so old Java-serialized saves deserialize with null/zero and can
     * be normalized safely.
     */
    private long nextPayloadId = 1L;
    private List<SettlementConveyorPayload> payloads =
            new ArrayList<SettlementConveyorPayload>();

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

    public double getSpeedTilesPerSecond() {
        return BASIC_SPEED_TILES_PER_SECOND;
    }

    public double getPayloadSpacingTiles() {
        return PAYLOAD_SPACING_TILES;
    }

    public boolean isValid() {
        return runId > 0L
                && SettlementState.isValidPlotLocation(startPlotX, startPlotY, plane)
                && SettlementState.isValidPlotLocation(endPlotX, endPlotY, plane)
                && (startPlotX != endPlotX || startPlotY != endPlotY);
    }

    public synchronized boolean normalize() {
        if (!isValid()) {
            return false;
        }
        if (payloads == null) {
            payloads = new ArrayList<SettlementConveyorPayload>();
        }

        double length = getLengthTiles();
        long highestPayloadId = 0L;
        Set<Long> ids = new HashSet<Long>();
        List<SettlementConveyorPayload> valid =
                new ArrayList<SettlementConveyorPayload>(payloads.size());
        for (SettlementConveyorPayload payload : payloads) {
            if (payload == null || !payload.normalize(length)
                    || !ids.add(Long.valueOf(payload.getPayloadId()))) {
                continue;
            }
            if (payload.getPayloadId() > highestPayloadId) {
                highestPayloadId = payload.getPayloadId();
            }
            valid.add(payload);
        }
        payloads = valid;
        sortFrontFirst(payloads);

        if (nextPayloadId <= highestPayloadId) {
            nextPayloadId = highestPayloadId + 1L;
        }
        if (nextPayloadId <= 0L) {
            nextPayloadId = 1L;
        }
        return true;
    }

    public double getLengthTiles() {
        double dx = endPlotX - startPlotX;
        double dy = endPlotY - startPlotY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public synchronized int getPayloadCount() {
        normalize();
        return payloads.size();
    }

    public synchronized List<SettlementConveyorPayload> snapshotPayloads() {
        normalize();
        return new ArrayList<SettlementConveyorPayload>(payloads);
    }

    /**
     * Production-shaped inlet: a payload may enter only when Point A has the
     * minimum spacing required by this conveyor.
     */
    public synchronized SettlementConveyorPayload addPayload(
            int itemId, int amount) {
        if (!normalize() || itemId < 0 || amount <= 0) {
            return null;
        }
        for (SettlementConveyorPayload payload : payloads) {
            if (payload != null
                    && payload.getDistanceTiles() < PAYLOAD_SPACING_TILES - EPSILON) {
                return null;
            }
        }

        SettlementConveyorPayload payload = new SettlementConveyorPayload(
                nextPayloadId++, itemId, amount, 0.0);
        payloads.add(payload);
        sortFrontFirst(payloads);
        return payload;
    }

    /**
     * True when Point A has enough spacing for one more payload.
     */
    public synchronized boolean isInletAvailable() {
        if (!normalize()) {
            return false;
        }
        for (SettlementConveyorPayload payload : payloads) {
            if (payload != null
                    && payload.getDistanceTiles() < PAYLOAD_SPACING_TILES - EPSILON) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the leading payload only when it is physically waiting at Point B.
     * Endpoint ownership/acceptance remains with SettlementInstance.
     */
    public synchronized SettlementConveyorPayload getFrontPayloadAtOutput() {
        if (!normalize() || payloads.isEmpty()) {
            return null;
        }
        sortFrontFirst(payloads);
        SettlementConveyorPayload front = payloads.get(0);
        return front != null
                && front.getDistanceTiles() >= getLengthTiles() - EPSILON
                ? front : null;
    }

    /**
     * Removes one server-owned payload after an endpoint has accepted ownership.
     */
    public synchronized boolean removePayload(long payloadId) {
        if (!normalize() || payloadId <= 0L) {
            return false;
        }
        java.util.Iterator<SettlementConveyorPayload> iterator = payloads.iterator();
        while (iterator.hasNext()) {
            SettlementConveyorPayload payload = iterator.next();
            if (payload != null && payload.getPayloadId() == payloadId) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized int clearPayloads() {
        normalize();
        int removed = payloads.size();
        payloads.clear();
        return removed;
    }

    /**
     * Developer-only deterministic fill that seeds the real persistent payload
     * list at legal spacing. It exists to make backpressure testing a one-click
     * operation; transport still runs through the same production tick method.
     */
    public synchronized int fillPayloadsForDevelopment(int[] itemIds) {
        if (!normalize() || itemIds == null || itemIds.length == 0) {
            return 0;
        }
        payloads.clear();

        double length = getLengthTiles();
        double maxDistance = Math.max(0.0, length - PAYLOAD_SPACING_TILES * 0.5);
        int count = 0;
        for (double distance = maxDistance;
                distance >= -EPSILON && count < MAX_DEVELOPMENT_PAYLOADS;
                distance -= PAYLOAD_SPACING_TILES) {
            int itemId = itemIds[count % itemIds.length];
            if (itemId < 0) {
                continue;
            }
            SettlementConveyorPayload payload = new SettlementConveyorPayload(
                    nextPayloadId++, itemId, 1, Math.max(0.0, distance));
            payloads.add(payload);
            count++;
        }
        sortFrontFirst(payloads);
        return count;
    }

    /**
     * Advances all payloads front-to-back while preserving minimum spacing.
     *
     * Point B is always a hard ownership boundary here. Payloads stop at the
     * endpoint and remain owned by this run until SettlementInstance performs a
     * real endpoint transfer and explicitly removes the accepted payload.
     */
    public synchronized boolean advancePayloads(double elapsedSeconds) {
        if (!normalize() || payloads.isEmpty() || elapsedSeconds <= 0.0) {
            return false;
        }

        sortFrontFirst(payloads);
        double length = getLengthTiles();
        double delta = getSpeedTilesPerSecond() * elapsedSeconds;
        double leaderDistance = Double.POSITIVE_INFINITY;
        boolean changed = false;

        for (SettlementConveyorPayload payload : payloads) {
            double oldDistance = payload.getDistanceTiles();
            double nextDistance = Math.min(length, oldDistance + delta);
            if (!Double.isInfinite(leaderDistance)) {
                nextDistance = Math.min(
                        nextDistance,
                        Math.max(0.0, leaderDistance - PAYLOAD_SPACING_TILES));
            }

            if (Math.abs(nextDistance - oldDistance) > EPSILON) {
                payload.setDistanceTiles(nextDistance);
                changed = true;
            }
            leaderDistance = nextDistance;
        }
        return changed;
    }

    private static void sortFrontFirst(List<SettlementConveyorPayload> values) {
        Collections.sort(values, new Comparator<SettlementConveyorPayload>() {
            @Override
            public int compare(
                    SettlementConveyorPayload a, SettlementConveyorPayload b) {
                int distance = Double.compare(
                        b.getDistanceTiles(), a.getDistanceTiles());
                if (distance != 0) {
                    return distance;
                }
                return Long.compare(a.getPayloadId(), b.getPayloadId());
            }
        });
    }
}
