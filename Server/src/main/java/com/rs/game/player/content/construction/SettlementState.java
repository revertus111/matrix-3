package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    private static final int CURRENT_SCHEMA_VERSION = 22;
    private static final int RESOURCE_WORLD_GENERATOR_VERSION = 2;
    private static final int BIOME_GENERATOR_VERSION = 1;
    private static final int CHUNK_OWNERSHIP_VERSION = 2;

    public static final int PLOT_CHUNK_SIZE = 8;
    public static final int PLOT_CHUNKS = PLOT_TILES / PLOT_CHUNK_SIZE;
    public static final int FOUNDATION_CHUNK_SPAN = 4;
    public static final int FOUNDATION_CENTER_PLOT = PLOT_TILES / 2;
    public static final int FOUNDATION_MIN_CHUNK =
            (FOUNDATION_CENTER_PLOT / PLOT_CHUNK_SIZE) - (FOUNDATION_CHUNK_SPAN / 2);
    public static final int FOUNDATION_MAX_CHUNK =
            FOUNDATION_MIN_CHUNK + FOUNDATION_CHUNK_SPAN - 1;

    private static final int LEGACY_STARTER_CHUNK_MIN = 3;
    private static final int LEGACY_STARTER_CHUNK_MAX = 4;
    private static final int RESOURCE_MIN_SPACING = 3;

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
    private long nextResourceNodeId = 1L;
    private List<SettlementResourceNodeState> resourceNodes =
            new ArrayList<SettlementResourceNodeState>();
    private long resourceWorldSeed;
    private int resourceWorldGeneratorVersion;
    private int biomeGeneratorVersion;
    private Set<String> generatedResourceChunks = new HashSet<String>();
    private int chunkOwnershipVersion;
    private Set<String> unlockedChunks = new HashSet<String>();
    private transient boolean fullPlotTesting;
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
        if (resourceNodes == null) {
            resourceNodes = new ArrayList<SettlementResourceNodeState>();
        }
        if (schemaVersion < 19) {
            repairLegacyResourceNodeMigration();
        }
        if (generatedResourceChunks == null) {
            generatedResourceChunks = new HashSet<String>();
        }
        ensureResourceWorldSeed();
        if (biomeGeneratorVersion <= 0) {
            biomeGeneratorVersion = BIOME_GENERATOR_VERSION;
        }
        if (resourceWorldGeneratorVersion < 1) {
            generateInitialResourceWorldV1();
            resourceWorldGeneratorVersion = 1;
        }
        if (resourceWorldGeneratorVersion < RESOURCE_WORLD_GENERATOR_VERSION) {
            generateFoundationResourceWorldV2();
            resourceWorldGeneratorVersion = RESOURCE_WORLD_GENERATOR_VERSION;
        }
        if (unlockedChunks == null) {
            unlockedChunks = new HashSet<String>();
        }
        if (workers == null) {
            workers = new ArrayList<SettlementWorkerState>();
        }
        if (chunkOwnershipVersion < CHUNK_OWNERSHIP_VERSION) {
            initializeFoundationTerritoryV2();
            chunkOwnershipVersion = CHUNK_OWNERSHIP_VERSION;
        }
        long highestResourceNodeId = 0L;
        Set<Long> resourceNodeIds = new HashSet<Long>();
        Iterator<SettlementResourceNodeState> resourceNodeIterator = resourceNodes.iterator();
        while (resourceNodeIterator.hasNext()) {
            SettlementResourceNodeState node = resourceNodeIterator.next();
            if (node == null || !node.normalize()
                    || !resourceNodeIds.add(Long.valueOf(node.getNodeId()))) {
                resourceNodeIterator.remove();
                continue;
            }
            if (node.getNodeId() > highestResourceNodeId) highestResourceNodeId = node.getNodeId();
        }
        if (nextResourceNodeId <= highestResourceNodeId) nextResourceNodeId = highestResourceNodeId + 1L;
        if (nextResourceNodeId <= 0L) nextResourceNodeId = 1L;
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
        return addConveyorRun(startPlotX, startPlotY,
                endPlotX, endPlotY, plane, SettlementConveyorRun.ROUTE_AUTO);
    }

    public synchronized SettlementConveyorRun addConveyorRun(
            int startPlotX, int startPlotY,
            int endPlotX, int endPlotY, int plane, int routeAxis) {
        normalize();
        if (!isValidPlotLocation(startPlotX, startPlotY, plane)
                || !isValidPlotLocation(endPlotX, endPlotY, plane)
                || (startPlotX == endPlotX && startPlotY == endPlotY)
                || !isConveyorRouteUnlockedInternal(
                        startPlotX, startPlotY, endPlotX, endPlotY, plane, routeAxis)) {
            return null;
        }
        SettlementConveyorRun run = new SettlementConveyorRun(
                nextConveyorRunId++, startPlotX, startPlotY,
                endPlotX, endPlotY, plane, routeAxis);
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
        if (!isValidPlotLocation(plotX, plotY, plane)
                || !isPlotTileUnlockedInternal(plotX, plotY, plane)) {
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
        if (!isValidPlotLocation(plotX, plotY, plane)
                || !isPlotTileUnlockedInternal(plotX, plotY, plane)) {
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
                || !isPlotTileUnlockedInternal(plotX, plotY, plane)
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
                || !isPlotTileUnlockedInternal(plotX, plotY, plane)
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
                || !isPlotTileUnlockedInternal(plotX, plotY, plane)
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
            if (isPlotTileUnlockedInternal(x, y, plane)
                    && !hasWorkerHomeAtInternal(x, y, plane)
                    && !hasPlacedPieceAtInternal(x, y, plane)) {
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

    /**
     * Schema-v19 repair for the first finite-resource migration.
     *
     * Java deserialization gives newly added primitive fields their zero value.
     * The original v18 migration therefore assigned node id 0 to the first
     * starter definition (the tree), and normalize() correctly removed it as
     * invalid. Repair old/v18 saves once by establishing a positive id counter
     * and restoring only starter definitions that have no persisted record.
     *
     * A depleted node still has a persisted record with remainingAmount=0, so
     * this repair never respawns a legitimately depleted source.
     */
    private void repairLegacyResourceNodeMigration() {
        long highestResourceNodeId = 0L;
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node != null && node.getNodeId() > highestResourceNodeId) {
                highestResourceNodeId = node.getNodeId();
            }
        }
        if (nextResourceNodeId <= highestResourceNodeId) {
            nextResourceNodeId = highestResourceNodeId + 1L;
        }
        if (nextResourceNodeId <= 0L) {
            nextResourceNodeId = 1L;
        }

        for (SettlementResourceNode definition : SettlementResourceNode.values()) {
            boolean present = false;
            for (SettlementResourceNodeState node : resourceNodes) {
                if (node != null
                        && definition.getKey().equals(node.getDefinitionKey())) {
                    present = true;
                    break;
                }
            }
            if (present) {
                continue;
            }
            resourceNodes.add(new SettlementResourceNodeState(
                    nextResourceNodeId++,
                    definition.getKey(),
                    definition.getPlotX(),
                    definition.getPlotY(),
                    PLOT_PLANE,
                    definition.getDefaultStartingAmount(),
                    definition.getDefaultStartingAmount()));
        }
    }

    private void ensureResourceWorldSeed() {
        if (resourceWorldSeed != 0L) {
            return;
        }
        long seedBase = System.nanoTime()
                ^ (System.currentTimeMillis() << 21)
                ^ serialVersionUID;
        resourceWorldSeed = mixResourceSeed(seedBase, 17, 29, 43L);
        if (resourceWorldSeed == 0L) {
            resourceWorldSeed = 1L;
        }
    }

    private void generateInitialResourceWorldV1() {
        generatedResourceChunks.clear();

        SettlementResourceNode[] guaranteed = {
                SettlementResourceNode.WOOD_TREE,
                SettlementResourceNode.FOOD_SPOT,
                SettlementResourceNode.STONE_OUTCROP,
                SettlementResourceNode.ORE_OUTCROP
        };
        int[][] starterChunks = {
                { LEGACY_STARTER_CHUNK_MIN, LEGACY_STARTER_CHUNK_MIN },
                { LEGACY_STARTER_CHUNK_MAX, LEGACY_STARTER_CHUNK_MIN },
                { LEGACY_STARTER_CHUNK_MIN, LEGACY_STARTER_CHUNK_MAX },
                { LEGACY_STARTER_CHUNK_MAX, LEGACY_STARTER_CHUNK_MAX }
        };

        for (int index = 0; index < guaranteed.length; index++) {
            int chunkX = starterChunks[index][0];
            int chunkY = starterChunks[index][1];
            SettlementResourceNode definition = guaranteed[index];
            SettlementResourceNodeState existing =
                    findFirstResourceNodeByDefinitionInternal(definition.getKey());
            int[] placement = findResourcePlacementInChunkInternal(
                    chunkX, chunkY,
                    mixResourceSeed(resourceWorldSeed, chunkX, chunkY, index + 101L),
                    existing == null ? -1L : existing.getNodeId());
            if (placement == null) {
                placement = findResourcePlacementInStarterAreaInternal(
                        mixResourceSeed(resourceWorldSeed, chunkX, chunkY, index + 211L),
                        existing == null ? -1L : existing.getNodeId());
            }

            if (placement != null) {
                if (existing != null) {
                    existing.relocate(placement[0], placement[1], PLOT_PLANE);
                } else {
                    resourceNodes.add(new SettlementResourceNodeState(
                            nextResourceNodeId++,
                            definition.getKey(),
                            placement[0],
                            placement[1],
                            PLOT_PLANE,
                            definition.getDefaultStartingAmount(),
                            definition.getDefaultStartingAmount()));
                }
            }
        }

        for (int chunkX = LEGACY_STARTER_CHUNK_MIN; chunkX <= LEGACY_STARTER_CHUNK_MAX; chunkX++) {
            for (int chunkY = LEGACY_STARTER_CHUNK_MIN; chunkY <= LEGACY_STARTER_CHUNK_MAX; chunkY++) {
                generateResourceChunkV1Internal(chunkX, chunkY, true);
            }
        }
    }

    private void generateFoundationResourceWorldV2() {
        Iterator<SettlementResourceNodeState> nodeIterator = resourceNodes.iterator();
        while (nodeIterator.hasNext()) {
            SettlementResourceNodeState node = nodeIterator.next();
            if (node == null || !isFoundationChunk(
                    node.getPlotX() / PLOT_CHUNK_SIZE,
                    node.getPlotY() / PLOT_CHUNK_SIZE)) {
                nodeIterator.remove();
            }
        }

        Iterator<String> generatedIterator = generatedResourceChunks.iterator();
        while (generatedIterator.hasNext()) {
            int[] chunk = parseResourceChunkKey(generatedIterator.next());
            if (chunk == null || !isFoundationChunk(chunk[0], chunk[1])) {
                generatedIterator.remove();
            }
        }

        for (int chunkX = FOUNDATION_MIN_CHUNK; chunkX <= FOUNDATION_MAX_CHUNK; chunkX++) {
            for (int chunkY = FOUNDATION_MIN_CHUNK; chunkY <= FOUNDATION_MAX_CHUNK; chunkY++) {
                generateResourceChunkV1Internal(chunkX, chunkY, false);
            }
        }
    }

    public synchronized boolean ensureResourceChunkGenerated(int chunkX, int chunkY) {
        normalize();
        return generateResourceChunkV1Internal(chunkX, chunkY, false);
    }

    public synchronized SettlementBiome getBiomeAtPlot(int plotX, int plotY) {
        normalize();
        if (!isValidPlotLocation(plotX, plotY, PLOT_PLANE)) {
            return null;
        }
        return sampleBiomeInternal(plotX, plotY);
    }

    public synchronized long getResourceWorldSeed() {
        normalize();
        return resourceWorldSeed;
    }

    public synchronized int getGeneratedResourceChunkCount() {
        normalize();
        return generatedResourceChunks.size();
    }

    private boolean generateResourceChunkV1Internal(
            int chunkX, int chunkY, boolean starterChunk) {
        if (!isValidChunk(chunkX, chunkY)) {
            return false;
        }
        String key = resourceChunkKey(chunkX, chunkY);
        if (generatedResourceChunks.contains(key)) {
            return true;
        }

        Random random = new Random(
                mixResourceSeed(resourceWorldSeed, chunkX, chunkY, 0x52535731L));
        int nodeCount;
        if (starterChunk) {
            nodeCount = 1 + (random.nextInt(100) < 35 ? 1 : 0);
        } else {
            nodeCount = random.nextInt(100) < 70 ? 1 : 0;
            if (random.nextInt(100) < 25) {
                nodeCount++;
            }
        }

        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            addGeneratedResourceNodeInChunkInternal(
                    chunkX, chunkY, random, nodeIndex);
        }
        generatedResourceChunks.add(key);
        return true;
    }

    private void addGeneratedResourceNodeInChunkInternal(
            int chunkX, int chunkY, Random random, int nodeIndex) {
        for (int attempt = 0; attempt < 32; attempt++) {
            int plotX = (chunkX * PLOT_CHUNK_SIZE) + 1 + random.nextInt(PLOT_CHUNK_SIZE - 2);
            int plotY = (chunkY * PLOT_CHUNK_SIZE) + 1 + random.nextInt(PLOT_CHUNK_SIZE - 2);
            if (!isResourcePlacementValidInternal(plotX, plotY, PLOT_PLANE, -1L)) {
                continue;
            }
            SettlementBiome biome = sampleBiomeInternal(plotX, plotY);
            SettlementResourceNode definition = biome.pickResourceDefinition(random);
            resourceNodes.add(new SettlementResourceNodeState(
                    nextResourceNodeId++,
                    definition.getKey(),
                    plotX,
                    plotY,
                    PLOT_PLANE,
                    definition.getDefaultStartingAmount(),
                    definition.getDefaultStartingAmount()));
            return;
        }
    }

    private int[] findResourcePlacementInChunkInternal(
            int chunkX, int chunkY, long seed, long ignoredNodeId) {
        if (!isValidChunk(chunkX, chunkY)) {
            return null;
        }
        Random random = new Random(seed);
        for (int attempt = 0; attempt < 48; attempt++) {
            int plotX = (chunkX * PLOT_CHUNK_SIZE) + 1 + random.nextInt(PLOT_CHUNK_SIZE - 2);
            int plotY = (chunkY * PLOT_CHUNK_SIZE) + 1 + random.nextInt(PLOT_CHUNK_SIZE - 2);
            if (isResourcePlacementValidInternal(
                    plotX, plotY, PLOT_PLANE, ignoredNodeId)) {
                return new int[] { plotX, plotY };
            }
        }
        return null;
    }

    private int[] findResourcePlacementInStarterAreaInternal(
            long seed, long ignoredNodeId) {
        Random random = new Random(seed);
        int minTile = LEGACY_STARTER_CHUNK_MIN * PLOT_CHUNK_SIZE;
        int maxTileExclusive = (LEGACY_STARTER_CHUNK_MAX + 1) * PLOT_CHUNK_SIZE;
        for (int attempt = 0; attempt < 96; attempt++) {
            int plotX = minTile + 1 + random.nextInt(maxTileExclusive - minTile - 2);
            int plotY = minTile + 1 + random.nextInt(maxTileExclusive - minTile - 2);
            if (isResourcePlacementValidInternal(
                    plotX, plotY, PLOT_PLANE, ignoredNodeId)) {
                return new int[] { plotX, plotY };
            }
        }
        return null;
    }

    private boolean isResourcePlacementValidInternal(
            int plotX, int plotY, int plane, long ignoredNodeId) {
        if (!isValidPlotLocation(plotX, plotY, plane)
                || hasPlacedPieceAtInternal(plotX, plotY, plane)
                || savedBuildTiles.contains(savedBuildTileKey(plotX, plotY, plane))
                || SettlementWorkerDefinition.isReservedArrivalTile(plotX, plotY, plane)
                || isConveyorTileInternal(plotX, plotY, plane)) {
            return false;
        }
        if (workers != null) {
            for (SettlementWorkerState worker : workers) {
                if (worker != null
                        && worker.getHomePlotX() == plotX
                        && worker.getHomePlotY() == plotY
                        && worker.getHomePlane() == plane) {
                    return false;
                }
            }
        }
        int spacingSquared = RESOURCE_MIN_SPACING * RESOURCE_MIN_SPACING;
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node == null || node.getNodeId() == ignoredNodeId
                    || node.getPlane() != plane) {
                continue;
            }
            int dx = node.getPlotX() - plotX;
            int dy = node.getPlotY() - plotY;
            if ((dx * dx) + (dy * dy) < spacingSquared) {
                return false;
            }
        }
        return true;
    }

    private boolean isConveyorTileInternal(int plotX, int plotY, int plane) {
        for (SettlementConveyorRun run : conveyorRuns) {
            if (run == null || run.getPlane() != plane) {
                continue;
            }
            int bendX = run.getBendPlotX();
            int bendY = run.getBendPlotY();
            if (isPointOnAxisAlignedSegment(
                    plotX, plotY,
                    run.getStartPlotX(), run.getStartPlotY(),
                    bendX, bendY)
                    || isPointOnAxisAlignedSegment(
                            plotX, plotY,
                            bendX, bendY,
                            run.getEndPlotX(), run.getEndPlotY())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPointOnAxisAlignedSegment(
            int x, int y, int startX, int startY, int endX, int endY) {
        if (startX == endX) {
            return x == startX
                    && y >= Math.min(startY, endY)
                    && y <= Math.max(startY, endY);
        }
        if (startY == endY) {
            return y == startY
                    && x >= Math.min(startX, endX)
                    && x <= Math.max(startX, endX);
        }
        return false;
    }

    private SettlementResourceNodeState findFirstResourceNodeByDefinitionInternal(
            String definitionKey) {
        if (definitionKey == null) {
            return null;
        }
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node != null && definitionKey.equals(node.getDefinitionKey())) {
                return node;
            }
        }
        return null;
    }

    private SettlementBiome sampleBiomeInternal(int plotX, int plotY) {
        if (biomeGeneratorVersion == BIOME_GENERATOR_VERSION) {
            return SettlementBiome.sampleV1(resourceWorldSeed, plotX, plotY);
        }
        return SettlementBiome.MEADOW;
    }

    private static boolean isValidChunk(int chunkX, int chunkY) {
        return chunkX >= 0 && chunkX < PLOT_CHUNKS
                && chunkY >= 0 && chunkY < PLOT_CHUNKS;
    }

    private static String resourceChunkKey(int chunkX, int chunkY) {
        return chunkX + ":" + chunkY;
    }

    private static int[] parseResourceChunkKey(String key) {
        if (key == null) {
            return null;
        }
        String[] values = key.split(":");
        if (values.length != 2) {
            return null;
        }
        try {
            int chunkX = Integer.parseInt(values[0]);
            int chunkY = Integer.parseInt(values[1]);
            return isValidChunk(chunkX, chunkY)
                    ? new int[] { chunkX, chunkY } : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isFoundationChunk(int chunkX, int chunkY) {
        return chunkX >= FOUNDATION_MIN_CHUNK && chunkX <= FOUNDATION_MAX_CHUNK
                && chunkY >= FOUNDATION_MIN_CHUNK && chunkY <= FOUNDATION_MAX_CHUNK;
    }

    private static long mixResourceSeed(
            long seed, int chunkX, int chunkY, long salt) {
        long value = seed ^ salt;
        value ^= ((long) chunkX * 0x9E3779B97F4A7C15L);
        value ^= ((long) chunkY * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private void initializeFoundationTerritoryV2() {
        unlockedChunks.clear();
        for (int chunkX = FOUNDATION_MIN_CHUNK; chunkX <= FOUNDATION_MAX_CHUNK; chunkX++) {
            for (int chunkY = FOUNDATION_MIN_CHUNK; chunkY <= FOUNDATION_MAX_CHUNK; chunkY++) {
                addUnlockedChunkInternal(chunkX, chunkY);
            }
        }
    }

    public synchronized boolean isChunkUnlocked(int chunkX, int chunkY) {
        normalize();
        return isChunkUnlockedInternal(chunkX, chunkY);
    }

    public synchronized boolean isPlotTileUnlocked(int plotX, int plotY, int plane) {
        normalize();
        return isPlotTileUnlockedInternal(plotX, plotY, plane);
    }

    public synchronized void setFullPlotTesting(boolean enabled) {
        fullPlotTesting = enabled;
    }

    public synchronized boolean isFullPlotTesting() {
        return fullPlotTesting;
    }

    public synchronized boolean canUnlockAdjacentChunk(int chunkX, int chunkY) {
        normalize();
        return canUnlockAdjacentChunkInternal(chunkX, chunkY);
    }

    public synchronized boolean unlockAdjacentChunk(int chunkX, int chunkY) {
        normalize();
        if (!isValidChunk(chunkX, chunkY)) {
            return false;
        }
        if (isChunkUnlockedInternal(chunkX, chunkY)) {
            return true;
        }
        if (!canUnlockAdjacentChunkInternal(chunkX, chunkY)) {
            return false;
        }
        addUnlockedChunkInternal(chunkX, chunkY);
        generateResourceChunkV1Internal(chunkX, chunkY, false);
        return true;
    }

    public synchronized int getUnlockedChunkCount() {
        normalize();
        return unlockedChunks.size();
    }

    private boolean canUnlockAdjacentChunkInternal(int chunkX, int chunkY) {
        if (!isValidChunk(chunkX, chunkY)
                || isChunkUnlockedInternal(chunkX, chunkY)) {
            return false;
        }
        return isChunkUnlockedInternal(chunkX - 1, chunkY)
                || isChunkUnlockedInternal(chunkX + 1, chunkY)
                || isChunkUnlockedInternal(chunkX, chunkY - 1)
                || isChunkUnlockedInternal(chunkX, chunkY + 1);
    }

    private boolean isChunkUnlockedInternal(int chunkX, int chunkY) {
        return isValidChunk(chunkX, chunkY)
                && unlockedChunks.contains(resourceChunkKey(chunkX, chunkY));
    }

    private boolean isPlotTileUnlockedInternal(int plotX, int plotY, int plane) {
        return isValidPlotLocation(plotX, plotY, plane)
                && (fullPlotTesting || isChunkUnlockedInternal(
                        plotX / PLOT_CHUNK_SIZE,
                        plotY / PLOT_CHUNK_SIZE));
    }

    private void addUnlockedChunkInternal(int chunkX, int chunkY) {
        if (isValidChunk(chunkX, chunkY)) {
            unlockedChunks.add(resourceChunkKey(chunkX, chunkY));
        }
    }

    private void unlockChunkPathInternal(int targetChunkX, int targetChunkY) {
        if (!isValidChunk(targetChunkX, targetChunkY)) {
            return;
        }
        int chunkX = LEGACY_STARTER_CHUNK_MAX;
        int chunkY = LEGACY_STARTER_CHUNK_MAX;
        addUnlockedChunkInternal(chunkX, chunkY);
        while (chunkX != targetChunkX) {
            chunkX += targetChunkX > chunkX ? 1 : -1;
            addUnlockedChunkInternal(chunkX, chunkY);
        }
        while (chunkY != targetChunkY) {
            chunkY += targetChunkY > chunkY ? 1 : -1;
            addUnlockedChunkInternal(chunkX, chunkY);
        }
    }

    private void unlockSegmentChunksInternal(
            int startX, int startY, int endX, int endY) {
        int x = startX;
        int y = startY;
        unlockChunkPathInternal(x / PLOT_CHUNK_SIZE, y / PLOT_CHUNK_SIZE);
        while (x != endX || y != endY) {
            if (x != endX) {
                x += endX > x ? 1 : -1;
            } else if (y != endY) {
                y += endY > y ? 1 : -1;
            }
            unlockChunkPathInternal(x / PLOT_CHUNK_SIZE, y / PLOT_CHUNK_SIZE);
        }
    }

    private int[] parseSavedBuildTileInternal(String key) {
        if (key == null) {
            return null;
        }
        String[] values = key.split(",");
        if (values.length != 3) {
            return null;
        }
        try {
            int plotX = Integer.parseInt(values[0]);
            int plotY = Integer.parseInt(values[1]);
            int plane = Integer.parseInt(values[2]);
            if (!isValidPlotLocation(plotX, plotY, plane)) {
                return null;
            }
            return new int[] { plotX, plotY, plane };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isConveyorRouteUnlockedInternal(
            int startX, int startY, int endX, int endY,
            int plane, int routeAxis) {
        if (!isPlotTileUnlockedInternal(startX, startY, plane)
                || !isPlotTileUnlockedInternal(endX, endY, plane)) {
            return false;
        }
        int resolved = SettlementConveyorRun.resolveRouteAxis(
                routeAxis, startX, startY, endX, endY);
        int bendX = resolved == SettlementConveyorRun.ROUTE_X_FIRST
                ? endX : startX;
        int bendY = resolved == SettlementConveyorRun.ROUTE_X_FIRST
                ? startY : endY;
        return isAxisAlignedSegmentUnlockedInternal(
                startX, startY, bendX, bendY, plane)
                && isAxisAlignedSegmentUnlockedInternal(
                        bendX, bendY, endX, endY, plane);
    }

    private boolean isAxisAlignedSegmentUnlockedInternal(
            int startX, int startY, int endX, int endY, int plane) {
        int x = startX;
        int y = startY;
        if (!isPlotTileUnlockedInternal(x, y, plane)) {
            return false;
        }
        while (x != endX || y != endY) {
            if (x != endX) {
                x += endX > x ? 1 : -1;
            } else if (y != endY) {
                y += endY > y ? 1 : -1;
            }
            if (!isPlotTileUnlockedInternal(x, y, plane)) {
                return false;
            }
        }
        return true;
    }

    public synchronized List<SettlementResourceNodeState> snapshotResourceNodes() {
        normalize();
        return new ArrayList<SettlementResourceNodeState>(resourceNodes);
    }

    public synchronized List<SettlementResourceNodeState> snapshotActiveResourceNodes() {
        normalize();
        List<SettlementResourceNodeState> active = new ArrayList<SettlementResourceNodeState>();
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node != null && !node.isDepleted()) active.add(node);
        }
        return active;
    }

    public synchronized SettlementResourceNodeState findResourceNode(long nodeId) {
        normalize();
        return findResourceNodeInternal(nodeId);
    }

    public synchronized SettlementResourceNodeState findResourceNode(
            SettlementResourceNode.SourceKind sourceKind,
            int runtimeId, int plotX, int plotY, int plane) {
        normalize();
        if (sourceKind == null) return null;
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node == null || node.isDepleted()
                    || node.getSourceKind() != sourceKind
                    || node.getRuntimeId() != runtimeId
                    || node.getPlotX() != plotX || node.getPlotY() != plotY
                    || node.getPlane() != plane) continue;
            return node;
        }
        return null;
    }

    public synchronized boolean hasActiveResourceNodeAt(int plotX, int plotY, int plane) {
        normalize();
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node != null && !node.isDepleted()
                    && node.getPlotX() == plotX && node.getPlotY() == plotY
                    && node.getPlane() == plane) return true;
        }
        return false;
    }

    public synchronized long harvestResourceNode(long nodeId, long amount) {
        normalize();
        if (amount <= 0L) return 0L;
        SettlementResourceNodeState node = findResourceNodeInternal(nodeId);
        return node == null ? 0L : node.harvest(amount);
    }

    public synchronized long harvestResourceNodeToStorage(long nodeId, long amount) {
        normalize();
        if (amount <= 0L) return 0L;
        SettlementResourceNodeState node = findResourceNodeInternal(nodeId);
        SettlementResourceNode definition = node == null ? null : node.getDefinition();
        if (definition == null || node.isDepleted()) return 0L;
        long accepted = Math.min(amount,
                Math.min(node.getRemainingAmount(), getStorageRemaining(definition.getResource())));
        if (accepted <= 0L) return 0L;
        long harvested = node.harvest(accepted);
        if (harvested <= 0L) return 0L;
        SettlementResource resource = definition.getResource();
        resources.put(resource.getKey(), Long.valueOf(getResourceAmount(resource) + harvested));
        return harvested;
    }

    private SettlementResourceNodeState findResourceNodeInternal(long nodeId) {
        if (nodeId <= 0L) return null;
        for (SettlementResourceNodeState node : resourceNodes) {
            if (node != null && node.getNodeId() == nodeId) return node;
        }
        return null;
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
