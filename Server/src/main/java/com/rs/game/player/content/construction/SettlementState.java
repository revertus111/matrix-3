package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Player-owned persistent settlement data.
 *
 * Only plot-relative data is saved here. Runtime instance bounds and projected
 * Matrix3 world coordinates belong to SettlementInstance.
 */
public final class SettlementState implements Serializable {

    private static final long serialVersionUID = -3303744987274861157L;

    public static final int PLOT_TILES = 64;
    public static final int PLOT_PLANE = 0;

    private static final int CURRENT_SCHEMA_VERSION = 9;

    /**
     * Legacy shared-cap field value retained only for Java-save compatibility.
     * Storage authority is now per SettlementResource through
     * getStorageCapacity(SettlementResource).
     */
    @Deprecated
    public static final int STARTER_STORAGE_CAPACITY = 200;

    public static final int STARTER_SHELTER_WALLS = 4;
    public static final int STARTER_SHELTER_FLOORS = 4;
    public static final int STARTER_SHELTER_DOORS = 1;
    public static final long STARTER_SHELTER_RESOURCE_EACH = 1L;
    public static final int STARTER_SHELTER_POPULATION_CAPACITY = 2;
    public static final int HOUSING_CAPACITY_PER_BED = 1;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private long nextPieceId = 1L;
    private List<SettlementPlacedPiece> pieces = new ArrayList<SettlementPlacedPiece>();
    private Map<String, Long> resources = new HashMap<String, Long>();
    /**
     * Legacy serialized shared-cap field. Kept so existing player saves remain
     * deserializable; active storage capacity is resource-specific.
     */
    private int storageCapacity = STARTER_STORAGE_CAPACITY;
    private Set<String> completedMilestones = new HashSet<String>();
    private long nextWorkerId = 1L;
    private List<SettlementWorkerState> workers = new ArrayList<SettlementWorkerState>();
    private int housingBedCount;

    public synchronized void normalize() {
        if (schemaVersion <= 0) {
            schemaVersion = 1;
        }
        if (pieces == null) {
            pieces = new ArrayList<SettlementPlacedPiece>();
        }
        if (resources == null) {
            resources = new HashMap<String, Long>();
        }
        if (storageCapacity <= 0) {
            storageCapacity = STARTER_STORAGE_CAPACITY;
        }
        if (completedMilestones == null) {
            completedMilestones = new HashSet<String>();
        }
        Iterator<String> milestoneIterator = completedMilestones.iterator();
        while (milestoneIterator.hasNext()) {
            if (SettlementMilestone.forKey(milestoneIterator.next()) == null) {
                milestoneIterator.remove();
            }
        }
        if (workers == null) {
            workers = new ArrayList<SettlementWorkerState>();
        }
        if (housingBedCount < 0) {
            housingBedCount = 0;
        }
        int maxBeds = getMaximumHousingBedCount();
        if (housingBedCount > maxBeds) {
            housingBedCount = maxBeds;
        }
        long highestWorkerId = 0L;
        Set<Long> workerIds = new HashSet<Long>();
        Iterator<SettlementWorkerState> workerIterator = workers.iterator();
        while (workerIterator.hasNext()) {
            SettlementWorkerState worker = workerIterator.next();
            SettlementWorkerDefinition definition = worker == null
                    ? null : SettlementWorkerDefinition.forKey(worker.getDefinitionKey());
            if (worker == null || definition == null || worker.getWorkerId() <= 0L
                    || !workerIds.add(Long.valueOf(worker.getWorkerId()))) {
                workerIterator.remove();
                continue;
            }
            worker.normalize(definition);
            if (worker.getWorkerId() > highestWorkerId) {
                highestWorkerId = worker.getWorkerId();
            }
        }
        if (nextWorkerId <= highestWorkerId) {
            nextWorkerId = highestWorkerId + 1L;
        }
        if (nextWorkerId <= 0L) {
            nextWorkerId = 1L;
        }
        Iterator<Map.Entry<String, Long>> resourceIterator = resources.entrySet().iterator();
        while (resourceIterator.hasNext()) {
            Map.Entry<String, Long> entry = resourceIterator.next();
            SettlementResource resource = SettlementResource.forKey(entry.getKey());
            Long amount = entry.getValue();
            if (resource == null || amount == null || amount.longValue() <= 0L) {
                resourceIterator.remove();
            }
        }
        schemaVersion = CURRENT_SCHEMA_VERSION;

        long highestId = 0L;
        for (SettlementPlacedPiece piece : pieces) {
            if (piece != null && piece.getPieceId() > highestId) {
                highestId = piece.getPieceId();
            }
        }
        if (nextPieceId <= highestId) {
            nextPieceId = highestId + 1L;
        }
        if (nextPieceId <= 0L) {
            nextPieceId = 1L;
        }
    }

    public synchronized int size() {
        normalize();
        return pieces.size();
    }

    public synchronized List<SettlementPlacedPiece> snapshotPieces() {
        normalize();
        return new ArrayList<SettlementPlacedPiece>(pieces);
    }

    public synchronized SettlementPlacedPiece place(SettlementBuildPiece definition,
            int plotX, int plotY, int plane, int rotation) {
        normalize();
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), -1L)) {
            return null;
        }
        if (definition.getRole() == SettlementBuildRole.BED && !addHousingBed()) {
            return null;
        }
        SettlementPlacedPiece piece = new SettlementPlacedPiece(
                nextPieceId++, definition.getKey(), plotX, plotY, plane, rotation);
        pieces.add(piece);
        return piece;
    }

    public synchronized SettlementPlacedPiece findRailAt(int plotX, int plotY, int plane) {
        normalize();
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getRole() == SettlementBuildRole.RAIL) {
                return piece;
            }
        }
        return null;
    }

    public synchronized SettlementPlacedPiece find(int objectId, int plotX, int plotY, int plane) {
        normalize();
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getObjectId() == objectId) {
                return piece;
            }
        }
        return null;
    }

    public synchronized SettlementPlacedPiece move(long pieceId, int plotX, int plotY, int plane) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), pieceId)) {
            return null;
        }
        SettlementPlacedPiece moved = current.withPosition(plotX, plotY, plane);
        pieces.set(index, moved);
        return moved;
    }

    public synchronized SettlementPlacedPiece rotate(long pieceId, int delta) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0 || (delta != -1 && delta != 1)) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementPlacedPiece rotated = current.withRotation((current.getRotation() + delta) & 0x3);
        pieces.set(index, rotated);
        return rotated;
    }

    public synchronized SettlementPlacedPiece duplicate(long pieceId, int plotX, int plotY, int plane) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), -1L)) {
            return null;
        }
        if (definition.getRole() == SettlementBuildRole.BED && !addHousingBed()) {
            return null;
        }
        SettlementPlacedPiece duplicate = new SettlementPlacedPiece(
                nextPieceId++, current.getDefinitionKey(), plotX, plotY, plane, current.getRotation());
        pieces.add(duplicate);
        return duplicate;
    }

    public synchronized SettlementPlacedPiece remove(long pieceId) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece piece = pieces.get(index);
        SettlementBuildPiece definition = piece == null
                ? null : SettlementBuildPiece.forKey(piece.getDefinitionKey());
        if (definition != null && definition.getRole() == SettlementBuildRole.BED
                && !removeHousingBed()) {
            return null;
        }
        return pieces.remove(index);
    }

    public synchronized int getWorkerCount() {
        normalize();
        return workers.size();
    }

    public synchronized List<SettlementWorkerState> snapshotWorkers() {
        normalize();
        return new ArrayList<SettlementWorkerState>(workers);
    }

    public synchronized SettlementWorkerState findWorker(long workerId) {
        normalize();
        for (SettlementWorkerState worker : workers) {
            if (worker != null && worker.getWorkerId() == workerId) {
                return worker;
            }
        }
        return null;
    }

    public synchronized SettlementWorkerState getStarterWorker() {
        normalize();
        String starterKey = SettlementWorkerDefinition.STARTER_SETTLER.getKey();
        for (SettlementWorkerState worker : workers) {
            if (worker != null && starterKey.equals(worker.getDefinitionKey())) {
                return worker;
            }
        }
        return null;
    }

    public synchronized SettlementWorkerState ensureStarterWorker() {
        normalize();
        if (!isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return null;
        }

        SettlementWorkerDefinition definition = SettlementWorkerDefinition.STARTER_SETTLER;
        for (SettlementWorkerState worker : workers) {
            if (worker != null && definition.getKey().equals(worker.getDefinitionKey())) {
                return worker;
            }
        }

        SettlementWorkerState worker = new SettlementWorkerState(
                nextWorkerId++,
                definition.getKey(),
                definition.getDisplayName(),
                definition.getArrivalPlotX(),
                definition.getArrivalPlotY(),
                definition.getArrivalPlane());
        workers.add(worker);
        return worker;
    }

    /**
     * Phase-2 population owner.
     *
     * The completed starter shelter supports two workers. Persistent housing
     * beds extend this same owner by one worker each. Visual bed placement will
     * later mutate this owner through the normal Construction transaction path;
     * the capacity state itself is intentionally independent of provisional art.
     */
    public synchronized int getPopulationCapacity() {
        normalize();
        if (!completedMilestones.contains(SettlementMilestone.STARTER_SHELTER.getKey())) {
            return 0;
        }
        return STARTER_SHELTER_POPULATION_CAPACITY
                + (housingBedCount * HOUSING_CAPACITY_PER_BED);
    }

    public synchronized int getHousingBedCount() {
        normalize();
        return housingBedCount;
    }

    public synchronized boolean canAddHousingBed() {
        normalize();
        return completedMilestones.contains(SettlementMilestone.STARTER_SHELTER.getKey())
                && housingBedCount < getMaximumHousingBedCount();
    }

    public synchronized boolean addHousingBed() {
        if (!canAddHousingBed()) {
            return false;
        }
        housingBedCount++;
        return true;
    }

    public synchronized boolean canRemoveHousingBed() {
        normalize();
        if (housingBedCount <= 0) {
            return false;
        }
        int nextCapacity = STARTER_SHELTER_POPULATION_CAPACITY
                + ((housingBedCount - 1) * HOUSING_CAPACITY_PER_BED);
        return workers.size() <= nextCapacity;
    }

    public synchronized boolean removeHousingBed() {
        if (!canRemoveHousingBed()) {
            return false;
        }
        housingBedCount--;
        return true;
    }

    public synchronized String getHousingSummary() {
        normalize();
        return "beds=" + housingBedCount
                + " | capacity=" + getPopulationCapacity()
                + " | " + getPopulationSummary();
    }

    public synchronized boolean canRecruitAdditionalWorker() {
        normalize();
        if (!completedMilestones.contains(SettlementMilestone.STARTER_SHELTER.getKey())
                || workers.size() >= getPopulationCapacity()) {
            return false;
        }
        return findAvailableRecruitedHomePlotX() >= 0;
    }

    public synchronized SettlementWorkerState recruitAdditionalWorker() {
        normalize();
        if (!canRecruitAdditionalWorker()) {
            return null;
        }

        SettlementWorkerDefinition definition = SettlementWorkerDefinition.RECRUITED_SETTLER;
        int homePlotX = findAvailableRecruitedHomePlotX();
        if (homePlotX < 0) {
            return null;
        }
        SettlementWorkerState worker = new SettlementWorkerState(
                nextWorkerId++,
                definition.getKey(),
                definition.getDisplayName(),
                homePlotX,
                definition.getArrivalPlotY(),
                definition.getArrivalPlane());
        workers.add(worker);
        return worker;
    }

    public synchronized boolean hasWorkerHomeAt(int plotX, int plotY, int plane) {
        normalize();
        for (SettlementWorkerState worker : workers) {
            if (worker != null
                    && worker.getHomePlotX() == plotX
                    && worker.getHomePlotY() == plotY
                    && worker.getHomePlane() == plane) {
                return true;
            }
        }
        return false;
    }

    private int findAvailableRecruitedHomePlotX() {
        SettlementWorkerDefinition definition = SettlementWorkerDefinition.RECRUITED_SETTLER;
        int y = definition.getArrivalPlotY();
        int plane = definition.getArrivalPlane();
        for (int x = definition.getArrivalPlotX(); x < PLOT_TILES; x += 2) {
            if (!hasWorkerHomeAtInternal(x, y, plane) && !hasPlacedPieceAtInternal(x, y, plane)) {
                return x;
            }
        }
        return -1;
    }

    private int getMaximumHousingBedCount() {
        SettlementWorkerDefinition definition = SettlementWorkerDefinition.RECRUITED_SETTLER;
        int recruitSlots = 0;
        for (int x = definition.getArrivalPlotX(); x < PLOT_TILES; x += 2) {
            recruitSlots++;
        }
        return Math.max(0, recruitSlots - 1);
    }

    private boolean hasWorkerHomeAtInternal(int plotX, int plotY, int plane) {
        for (SettlementWorkerState worker : workers) {
            if (worker != null
                    && worker.getHomePlotX() == plotX
                    && worker.getHomePlotY() == plotY
                    && worker.getHomePlane() == plane) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPlacedPieceAtInternal(int plotX, int plotY, int plane) {
        for (SettlementPlacedPiece piece : pieces) {
            if (piece != null
                    && piece.getPlotX() == plotX
                    && piece.getPlotY() == plotY
                    && piece.getPlane() == plane) {
                return true;
            }
        }
        return false;
    }

    public synchronized String getPopulationSummary() {
        normalize();
        int capacity = getPopulationCapacity();
        String recruitment;
        if (!completedMilestones.contains(SettlementMilestone.STARTER_SHELTER.getKey())) {
            recruitment = "LOCKED: complete starter shelter";
        } else if (workers.size() >= capacity) {
            recruitment = "FULL";
        } else if (canRecruitAdditionalWorker()) {
            recruitment = "READY";
        } else {
            recruitment = "UNAVAILABLE";
        }
        return "workers=" + workers.size() + "/" + capacity
                + " | recruitment=" + recruitment;
    }

    public synchronized int countPieces(SettlementBuildRole role) {
        normalize();
        if (role == null) {
            return 0;
        }
        int count = 0;
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getRole() == role) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean isMilestoneComplete(SettlementMilestone milestone) {
        normalize();
        return milestone != null && completedMilestones.contains(milestone.getKey());
    }

    public synchronized boolean meetsStarterShelterRequirements() {
        normalize();
        if (countPieces(SettlementBuildRole.WALL) < STARTER_SHELTER_WALLS
                || countPieces(SettlementBuildRole.FLOOR) < STARTER_SHELTER_FLOORS
                || countPieces(SettlementBuildRole.DOOR) < STARTER_SHELTER_DOORS) {
            return false;
        }
        for (SettlementResource resource : SettlementResource.values()) {
            if (getResourceAmount(resource) < STARTER_SHELTER_RESOURCE_EACH
                    || getStorageCapacity(resource) < resource.getStarterStorageCapacity()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true only when this call newly completes the milestone.
     */
    public synchronized boolean tryCompleteStarterShelterMilestone() {
        normalize();
        if (isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)
                || !meetsStarterShelterRequirements()) {
            return false;
        }
        completedMilestones.add(SettlementMilestone.STARTER_SHELTER.getKey());
        return true;
    }

    public synchronized String getStarterShelterStatus() {
        normalize();
        int walls = countPieces(SettlementBuildRole.WALL);
        int floors = countPieces(SettlementBuildRole.FLOOR);
        int doors = countPieces(SettlementBuildRole.DOOR);

        StringBuilder status = new StringBuilder();
        status.append(isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)
                ? "COMPLETE" : "INCOMPLETE");
        status.append(" | walls=").append(walls).append("/").append(STARTER_SHELTER_WALLS);
        status.append(", floors=").append(floors).append("/").append(STARTER_SHELTER_FLOORS);
        status.append(", doors=").append(doors).append("/").append(STARTER_SHELTER_DOORS);
        for (SettlementResource resource : SettlementResource.values()) {
            status.append(", ").append(resource.getDisplayName()).append("=")
                    .append(getResourceAmount(resource)).append("/")
                    .append(STARTER_SHELTER_RESOURCE_EACH);
        }
        return status.toString();
    }

    public synchronized long getResourceAmount(SettlementResource resource) {
        normalize();
        if (resource == null) {
            return 0L;
        }
        Long amount = resources.get(resource.getKey());
        return amount == null ? 0L : amount.longValue();
    }

    public synchronized long getTotalStoredResources() {
        normalize();
        long total = 0L;
        for (Long amount : resources.values()) {
            if (amount != null && amount.longValue() > 0L) {
                total += amount.longValue();
            }
        }
        return total;
    }

    /**
     * Resource-specific storage owner.
     *
     * The starter profile is definition-owned by SettlementResource. Future
     * storage buildings should extend capacity through this single method rather
     * than adding another storage counter.
     */
    public synchronized int getStorageCapacity(SettlementResource resource) {
        normalize();
        return resource == null ? 0 : resource.getStarterStorageCapacity();
    }

    public synchronized long getStorageRemaining(SettlementResource resource) {
        normalize();
        if (resource == null) {
            return 0L;
        }
        return Math.max(0L,
                (long) getStorageCapacity(resource) - getResourceAmount(resource));
    }

    public synchronized boolean hasStorageSpace(SettlementResource resource) {
        return getStorageRemaining(resource) > 0L;
    }

    public synchronized int getTotalStorageCapacity() {
        normalize();
        int total = 0;
        for (SettlementResource resource : SettlementResource.values()) {
            total += getStorageCapacity(resource);
        }
        return total;
    }

    /**
     * Aggregate compatibility/readback helper. Runtime hauling must use the
     * resource-specific remaining-capacity overload instead.
     */
    public synchronized long getStorageRemaining() {
        normalize();
        long remaining = 0L;
        for (SettlementResource resource : SettlementResource.values()) {
            remaining += getStorageRemaining(resource);
        }
        return remaining;
    }

    /**
     * Aggregate compatibility/readback helper.
     */
    public synchronized int getStorageCapacity() {
        return getTotalStorageCapacity();
    }

    public synchronized long addResource(SettlementResource resource, long amount) {
        normalize();
        if (resource == null || amount <= 0L) {
            return 0L;
        }
        long accepted = Math.min(amount, getStorageRemaining(resource));
        if (accepted <= 0L) {
            return 0L;
        }
        long next = getResourceAmount(resource) + accepted;
        resources.put(resource.getKey(), Long.valueOf(next));
        return accepted;
    }

    public synchronized long removeResource(SettlementResource resource, long amount) {
        normalize();
        if (resource == null || amount <= 0L) {
            return 0L;
        }
        long current = getResourceAmount(resource);
        long removed = Math.min(current, amount);
        long next = current - removed;
        if (next <= 0L) {
            resources.remove(resource.getKey());
        } else {
            resources.put(resource.getKey(), Long.valueOf(next));
        }
        return removed;
    }

    public synchronized String getResourceSummary() {
        normalize();
        StringBuilder summary = new StringBuilder();
        for (SettlementResource resource : SettlementResource.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(resource.getDisplayName()).append("=")
                    .append(getResourceAmount(resource)).append("/")
                    .append(getStorageCapacity(resource));
        }
        summary.append(" | total=").append(getTotalStoredResources())
                .append("/").append(getTotalStorageCapacity());
        return summary.toString();
    }

    private int indexOf(long pieceId) {
        for (int index = 0; index < pieces.size(); index++) {
            SettlementPlacedPiece piece = pieces.get(index);
            if (piece != null && piece.getPieceId() == pieceId) {
                return index;
            }
        }
        return -1;
    }

    public static boolean isValidPlotLocation(int plotX, int plotY, int plane) {
        return plane == PLOT_PLANE
                && plotX >= 0 && plotX < PLOT_TILES
                && plotY >= 0 && plotY < PLOT_TILES;
    }

    private boolean isOccupied(int plotX, int plotY, int plane, int objectType, long ignoredPieceId) {
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPieceId() == ignoredPieceId
                    || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getObjectType() == objectType) {
                return true;
            }
        }
        return false;
    }
}
