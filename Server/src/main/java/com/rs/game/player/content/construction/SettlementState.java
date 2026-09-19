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

    private static final int CURRENT_SCHEMA_VERSION = 5;
    public static final int STARTER_STORAGE_CAPACITY = 200;

    public static final int STARTER_SHELTER_WALLS = 4;
    public static final int STARTER_SHELTER_FLOORS = 4;
    public static final int STARTER_SHELTER_DOORS = 1;
    public static final long STARTER_SHELTER_RESOURCE_EACH = 1L;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private long nextPieceId = 1L;
    private List<SettlementPlacedPiece> pieces = new ArrayList<SettlementPlacedPiece>();
    private Map<String, Long> resources = new HashMap<String, Long>();
    private int storageCapacity = STARTER_STORAGE_CAPACITY;
    private Set<String> completedMilestones = new HashSet<String>();
    private long nextWorkerId = 1L;
    private List<SettlementWorkerState> workers = new ArrayList<SettlementWorkerState>();

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
        SettlementPlacedPiece piece = new SettlementPlacedPiece(
                nextPieceId++, definition.getKey(), plotX, plotY, plane, rotation);
        pieces.add(piece);
        return piece;
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
        SettlementPlacedPiece duplicate = new SettlementPlacedPiece(
                nextPieceId++, current.getDefinitionKey(), plotX, plotY, plane, current.getRotation());
        pieces.add(duplicate);
        return duplicate;
    }

    public synchronized SettlementPlacedPiece remove(long pieceId) {
        normalize();
        int index = indexOf(pieceId);
        return index < 0 ? null : pieces.remove(index);
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
            if (getResourceAmount(resource) < STARTER_SHELTER_RESOURCE_EACH) {
                return false;
            }
        }
        return getStorageCapacity() >= STARTER_STORAGE_CAPACITY;
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

    public synchronized int getStorageCapacity() {
        normalize();
        return storageCapacity;
    }

    public synchronized long getStorageRemaining() {
        normalize();
        return Math.max(0L, (long) storageCapacity - getTotalStoredResources());
    }

    public synchronized long addResource(SettlementResource resource, long amount) {
        normalize();
        if (resource == null || amount <= 0L) {
            return 0L;
        }
        long accepted = Math.min(amount, getStorageRemaining());
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
                    .append(getResourceAmount(resource));
        }
        summary.append(" | total=").append(getTotalStoredResources())
                .append("/").append(storageCapacity);
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
