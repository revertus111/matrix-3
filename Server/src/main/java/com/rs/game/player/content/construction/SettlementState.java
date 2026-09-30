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

    private static final int CURRENT_SCHEMA_VERSION = 16;

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
    private Map<Long, SettlementStorageContainer> storageContainers =
            new HashMap<Long, SettlementStorageContainer>();
    private Map<Long, SettlementMachineBuffer> machineBuffers =
            new HashMap<Long, SettlementMachineBuffer>();
    private long nextConveyorRunId = 1L;
    private List<SettlementConveyorRun> conveyorRuns =
            new ArrayList<SettlementConveyorRun>();
    private Set<String> savedBuildTiles = new HashSet<String>();
    private Map<Long, SettlementRallyPoint> rallyPoints =
            new HashMap<Long, SettlementRallyPoint>();
    private long nextRallyPointId = 1L;
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
        if (storageContainers == null) {
            storageContainers = new HashMap<Long, SettlementStorageContainer>();
        }
        if (machineBuffers == null) {
            machineBuffers = new HashMap<Long, SettlementMachineBuffer>();
        }
        if (conveyorRuns == null) {
            conveyorRuns = new ArrayList<SettlementConveyorRun>();
        }
        if (savedBuildTiles == null) {
            savedBuildTiles = new HashSet<String>();
        }
        Iterator<String> savedTileIterator = savedBuildTiles.iterator();
        while (savedTileIterator.hasNext()) {
            if (!isValidSavedBuildTileKey(savedTileIterator.next())) {
                savedTileIterator.remove();
            }
        }
        Set<Long> liveStoragePieceIds = new HashSet<Long>();
        Set<Long> liveMachinePieceIds = new HashSet<Long>();
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition == null) {
                continue;
            }
            Long pieceId = Long.valueOf(piece.getPieceId());
            if (definition.getRole() == SettlementBuildRole.STORAGE) {
                liveStoragePieceIds.add(pieceId);
                SettlementStorageContainer container = storageContainers.get(pieceId);
                if (container == null) {
                    container = new SettlementStorageContainer(piece.getPieceId());
                    storageContainers.put(pieceId, container);
                }
                container.normalize();
            } else if (definition.getRole() == SettlementBuildRole.WORKSTATION) {
                liveMachinePieceIds.add(pieceId);
                SettlementMachineBuffer buffer = machineBuffers.get(pieceId);
                if (buffer == null) {
                    buffer = new SettlementMachineBuffer(piece.getPieceId());
                    machineBuffers.put(pieceId, buffer);
                }
                buffer.normalize();
            }
        }
        Iterator<Map.Entry<Long, SettlementStorageContainer>> storageIterator =
                storageContainers.entrySet().iterator();
        while (storageIterator.hasNext()) {
            Map.Entry<Long, SettlementStorageContainer> entry = storageIterator.next();
            if (entry.getKey() == null || entry.getValue() == null
                    || !liveStoragePieceIds.contains(entry.getKey())) {
                storageIterator.remove();
            }
        }
        Iterator<Map.Entry<Long, SettlementMachineBuffer>> machineIterator =
                machineBuffers.entrySet().iterator();
        while (machineIterator.hasNext()) {
            Map.Entry<Long, SettlementMachineBuffer> entry = machineIterator.next();
            if (entry.getKey() == null || entry.getValue() == null
                    || !liveMachinePieceIds.contains(entry.getKey())) {
                machineIterator.remove();
            }
        }

        if (rallyPoints == null) {
            rallyPoints = new HashMap<Long, SettlementRallyPoint>();
        }
        long highestRallyId = 0L;
        Iterator<Map.Entry<Long, SettlementRallyPoint>> rallyIterator =
                rallyPoints.entrySet().iterator();
        while (rallyIterator.hasNext()) {
            Map.Entry<Long, SettlementRallyPoint> entry = rallyIterator.next();
            SettlementRallyPoint rally = entry.getValue();
            if (entry.getKey() == null || rally == null
                    || entry.getKey().longValue() != rally.getRallyId()
                    || !rally.normalize()) {
                rallyIterator.remove();
                continue;
            }
            if (rally.getRallyId() > highestRallyId) {
                highestRallyId = rally.getRallyId();
            }
        }
        if (nextRallyPointId <= highestRallyId) {
            nextRallyPointId = highestRallyId + 1L;
        }
        if (nextRallyPointId <= 0L) {
            nextRallyPointId = 1L;
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
            if (worker.getRallyPointId() > 0L
                    && !rallyPoints.containsKey(Long.valueOf(worker.getRallyPointId()))) {
                worker.clearRallyPoint();
            }
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
        Set<Long> liveConveyorRunIds = new HashSet<Long>();
        long highestConveyorRunId = 0L;
        Iterator<SettlementConveyorRun> conveyorIterator = conveyorRuns.iterator();
        while (conveyorIterator.hasNext()) {
            SettlementConveyorRun run = conveyorIterator.next();
            if (run == null || !run.normalize()
                    || !liveConveyorRunIds.add(Long.valueOf(run.getRunId()))) {
                conveyorIterator.remove();
                continue;
            }
            if (run.getRunId() > highestConveyorRunId) {
                highestConveyorRunId = run.getRunId();
            }
        }
        if (nextConveyorRunId <= highestConveyorRunId) {
            nextConveyorRunId = highestConveyorRunId + 1L;
        }
        if (nextConveyorRunId <= 0L) {
            nextConveyorRunId = 1L;
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

    public synchronized int getConveyorRunCount() {
        normalize();
        return conveyorRuns.size();
    }

    public synchronized List<SettlementConveyorRun> snapshotConveyorRuns() {
        normalize();
        return new ArrayList<SettlementConveyorRun>(conveyorRuns);
    }

    public synchronized SettlementConveyorRun findConveyorRun(long runId) {
        normalize();
        if (runId <= 0L) {
            for (SettlementConveyorRun run : conveyorRuns) {
                if (run != null && run.isValid()) {
                    return run;
                }
            }
            return null;
        }
        for (SettlementConveyorRun run : conveyorRuns) {
            if (run != null && run.getRunId() == runId) {
                return run;
            }
        }
        return null;
    }

    public synchronized SettlementConveyorRun addConveyorRun(
            int startPlotX, int startPlotY,
            int endPlotX, int endPlotY, int plane) {
        normalize();
        if (!isValidPlotLocation(startPlotX, startPlotY, plane)
                || !isValidPlotLocation(endPlotX, endPlotY, plane)
                || (startPlotX == endPlotX && startPlotY == endPlotY)) {
            return null;
        }
        SettlementConveyorRun run = new SettlementConveyorRun(
                nextConveyorRunId++, startPlotX, startPlotY,
                endPlotX, endPlotY, plane);
        conveyorRuns.add(run);
        return run;
    }

    public synchronized boolean removeConveyorRun(long runId) {
        normalize();
        if (runId <= 0L) {
            return false;
        }
        Iterator<SettlementConveyorRun> iterator = conveyorRuns.iterator();
        while (iterator.hasNext()) {
            SettlementConveyorRun run = iterator.next();
            if (run != null && run.getRunId() == runId) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized int clearConveyorRuns() {
        normalize();
        int removed = conveyorRuns.size();
        conveyorRuns.clear();
        return removed;
    }

    public synchronized boolean saveBuildTile(int plotX, int plotY, int plane) {
        normalize();
        if (!isValidPlotLocation(plotX, plotY, plane)) {
            return false;
        }
        return savedBuildTiles.add(savedBuildTileKey(plotX, plotY, plane));
    }

    public synchronized boolean unsaveBuildTile(int plotX, int plotY, int plane) {
        normalize();
        if (!isValidPlotLocation(plotX, plotY, plane)) {
            return false;
        }
        return savedBuildTiles.remove(savedBuildTileKey(plotX, plotY, plane));
    }

    public synchronized boolean isBuildTileSaved(int plotX, int plotY, int plane) {
        normalize();
        return isValidPlotLocation(plotX, plotY, plane)
                && savedBuildTiles.contains(savedBuildTileKey(plotX, plotY, plane));
    }

    public synchronized int getSavedBuildTileCount() {
        normalize();
        return savedBuildTiles.size();
    }

    public synchronized SettlementRallyPoint findOrCreateRallyPoint(
            int plotX, int plotY, int plane) {
        normalize();
        if (!isValidPlotLocation(plotX, plotY, plane)) {
            return null;
        }
        for (SettlementRallyPoint rally : rallyPoints.values()) {
            if (rally != null && rally.getPlotX() == plotX
                    && rally.getPlotY() == plotY && rally.getPlane() == plane) {
                return rally;
            }
        }
        long rallyId = nextRallyPointId++;
        SettlementRallyPoint rally = new SettlementRallyPoint(
                rallyId, "Rally #" + rallyId,
                plotX, plotY, plane, SettlementRallyPoint.DEFAULT_RADIUS);
        rallyPoints.put(Long.valueOf(rallyId), rally);
        return rally;
    }

    public synchronized SettlementRallyPoint findRallyPoint(long rallyId) {
        normalize();
        return rallyId > 0L
                ? rallyPoints.get(Long.valueOf(rallyId)) : null;
    }

    public synchronized List<SettlementRallyPoint> snapshotRallyPoints() {
        normalize();
        return new ArrayList<SettlementRallyPoint>(rallyPoints.values());
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
        if (definition.getRole() == SettlementBuildRole.STORAGE) {
            storageContainers.put(Long.valueOf(piece.getPieceId()),
                    new SettlementStorageContainer(piece.getPieceId()));
        } else if (definition.getRole() == SettlementBuildRole.WORKSTATION) {
            machineBuffers.put(Long.valueOf(piece.getPieceId()),
                    new SettlementMachineBuffer(piece.getPieceId()));
        }
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
        if (definition.getRole() == SettlementBuildRole.STORAGE) {
            storageContainers.put(Long.valueOf(duplicate.getPieceId()),
                    new SettlementStorageContainer(duplicate.getPieceId()));
        } else if (definition.getRole() == SettlementBuildRole.WORKSTATION) {
            machineBuffers.put(Long.valueOf(duplicate.getPieceId()),
                    new SettlementMachineBuffer(duplicate.getPieceId()));
        }
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
        if (definition != null && definition.getRole() == SettlementBuildRole.STORAGE) {
            SettlementStorageContainer container =
                    storageContainers.get(Long.valueOf(pieceId));
            if (container != null && !container.isEmpty()) {
                return null;
            }
        }
        if (definition != null && definition.getRole() == SettlementBuildRole.WORKSTATION) {
            SettlementMachineBuffer buffer =
                    machineBuffers.get(Long.valueOf(pieceId));
            if (buffer != null && !buffer.isEmpty()) {
                return null;
            }
        }
        SettlementPlacedPiece removed = pieces.remove(index);
        if (definition != null && definition.getRole() == SettlementBuildRole.STORAGE) {
            storageContainers.remove(Long.valueOf(pieceId));
        } else if (definition != null
                && definition.getRole() == SettlementBuildRole.WORKSTATION) {
            machineBuffers.remove(Long.valueOf(pieceId));
        }
        return removed;
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
            if (!resource.isStarterResource()) {
                continue;
            }
            if (getProgressionResourceAmount(resource) < STARTER_SHELTER_RESOURCE_EACH
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
            if (!resource.isStarterResource()) {
                continue;
            }
            status.append(", ").append(resource.getDisplayName()).append("=")
                    .append(getProgressionResourceAmount(resource)).append("/")
                    .append(STARTER_SHELTER_RESOURCE_EACH);
        }
        return status.toString();
    }

    public synchronized SettlementStorageContainer findStorageContainer(long pieceId) {
        normalize();
        SettlementStorageContainer container =
                storageContainers.get(Long.valueOf(pieceId));
        if (container != null) {
            container.normalize();
        }
        return container;
    }

    public synchronized List<SettlementStorageContainer> snapshotStorageContainers() {
        normalize();
        return new ArrayList<SettlementStorageContainer>(storageContainers.values());
    }

    public synchronized SettlementMachineBuffer findMachineBuffer(long pieceId) {
        normalize();
        SettlementMachineBuffer buffer = machineBuffers.get(Long.valueOf(pieceId));
        if (buffer != null) {
            buffer.normalize();
        }
        return buffer;
    }

    public synchronized List<SettlementMachineBuffer> snapshotMachineBuffers() {
        normalize();
        return new ArrayList<SettlementMachineBuffer>(machineBuffers.values());
    }

    public synchronized int getPhysicalStorageContainerCount() {
        normalize();
        return storageContainers.size();
    }

    public synchronized long getPhysicalItemAmount(int itemId) {
        normalize();
        if (itemId < 0) {
            return 0L;
        }
        long total = 0L;
        for (SettlementStorageContainer container : storageContainers.values()) {
            if (container == null) {
                continue;
            }
            total += container.getItemAmount(itemId);
        }
        return total;
    }

    public synchronized long removePhysicalItem(int itemId, long amount) {
        normalize();
        if (itemId < 0 || amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        for (SettlementStorageContainer container : storageContainers.values()) {
            if (container == null || remaining <= 0L) {
                continue;
            }
            int request = (int) Math.min((long) Integer.MAX_VALUE, remaining);
            int removed = container.removeItem(itemId, request);
            remaining -= removed;
        }
        return amount - remaining;
    }

    /**
     * Transitional progression view while legacy resource counters are migrated
     * one chain at a time. A migrated factory item is real physical inventory;
     * pre-migration saved counter stock remains spendable so old settlements do
     * not lose value.
     */
    public synchronized long getProgressionResourceAmount(SettlementResource resource) {
        normalize();
        if (resource == null) {
            return 0L;
        }
        long total = getResourceAmount(resource);
        SettlementFactoryItem mapping = SettlementFactoryItem.forResource(resource);
        if (mapping != null) {
            total += getPhysicalItemAmount(mapping.getItemId());
        }
        return total;
    }

    public synchronized long consumeProgressionResource(
            SettlementResource resource, long amount) {
        normalize();
        if (resource == null || amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        long legacyAvailable = getResourceAmount(resource);
        if (legacyAvailable > 0L) {
            long removedLegacy = removeResource(
                    resource, Math.min(legacyAvailable, remaining));
            remaining -= removedLegacy;
        }
        SettlementFactoryItem mapping = SettlementFactoryItem.forResource(resource);
        if (remaining > 0L && mapping != null) {
            remaining -= removePhysicalItem(mapping.getItemId(), remaining);
        }
        return amount - remaining;
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

    private String savedBuildTileKey(int plotX, int plotY, int plane) {
        return plotX + "," + plotY + "," + plane;
    }

    private boolean isValidSavedBuildTileKey(String key) {
        if (key == null) {
            return false;
        }
        String[] values = key.split(",");
        if (values.length != 3) {
            return false;
        }
        try {
            return isValidPlotLocation(
                    Integer.parseInt(values[0]),
                    Integer.parseInt(values[1]),
                    Integer.parseInt(values[2]));
        } catch (NumberFormatException ex) {
            return false;
        }
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
