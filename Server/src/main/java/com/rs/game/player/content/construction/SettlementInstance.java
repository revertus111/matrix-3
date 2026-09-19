package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.rs.executor.GameExecutorManager;
import com.rs.game.Region;
import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;
import com.rs.game.map.MapBuilder;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.game.player.controllers.Controller;
import com.rs.game.player.controllers.SettlementControler;
import com.rs.utils.Logger;

/**
 * Transient runtime projection of a player's persistent SettlementState.
 *
 * Dynamic bounds/world coordinates live only here. Saved settlement identity is
 * always plot-relative in SettlementState.
 */
public final class SettlementInstance {

    public static final int PLOT_TILES = SettlementState.PLOT_TILES;
    public static final int PLOT_CHUNKS = PLOT_TILES / 8;
    public static final int PLOT_PLANE = SettlementState.PLOT_PLANE;

    private static final int ENTRY_OFFSET = PLOT_TILES / 2;
    private static final double PASSIVE_CONSTRUCTION_XP_PER_RESOURCE = 1.0;

    private final Player player;
    private final SettlementState state;
    private final WorldTile returnTile;

    private volatile int[] boundChunks;
    private volatile boolean loaded;
    private volatile boolean destroyed;

    private final List<WorldObject> starterResourceObjects = new ArrayList<WorldObject>();
    private final List<NPC> starterResourceNpcs = new ArrayList<NPC>();
    private final List<SettlementWorkerNpc> workerNpcs = new ArrayList<SettlementWorkerNpc>();

    private SettlementInstance(Player player, SettlementState state, WorldTile returnTile) {
        this.player = player;
        this.state = state;
        this.returnTile = returnTile;
    }

    public static String enter(Player player) {
        if (player == null) {
            return "Settlement entry requires a player.";
        }
        if (getActive(player) != null) {
            return "You are already inside your settlement.";
        }

        SettlementState state = player.getSettlementState();
        state.normalize();

        WorldTile returnTile = new WorldTile(player.getX(), player.getY(), player.getPlane());
        final SettlementInstance instance = new SettlementInstance(player, state, returnTile);

        player.getControlerManager().startControler("SettlementControler", instance);
        if (getActive(player) != instance) {
            return "Settlement controller could not be started.";
        }

        player.lock();
        player.getPackets().sendGameMessage("Preparing your Construction settlement...");

        GameExecutorManager.slowExecutor.execute(new Runnable() {
            @Override
            public void run() {
                instance.load();
            }
        });
        return "Settlement instance loading.";
    }

    public static SettlementInstance getActive(Player player) {
        if (player == null || player.getControlerManager() == null) {
            return null;
        }
        Controller controller = player.getControlerManager().getControler();
        if (!(controller instanceof SettlementControler)) {
            return null;
        }
        return ((SettlementControler) controller).getInstance();
    }

    private void load() {
        try {
            int[] allocated = MapBuilder.findEmptyChunkBound(PLOT_CHUNKS, PLOT_CHUNKS);
            if (allocated == null || allocated.length < 2 || allocated[0] < 0 || allocated[1] < 0) {
                failLoad("No free dynamic map area was available for the settlement.");
                return;
            }
            boundChunks = allocated;

            for (int chunkX = 0; chunkX < PLOT_CHUNKS; chunkX++) {
                for (int chunkY = 0; chunkY < PLOT_CHUNKS; chunkY++) {
                    MapBuilder.copyChunk(
                            HouseConstants.LAND[0],
                            HouseConstants.LAND[1],
                            0,
                            boundChunks[0] + chunkX,
                            boundChunks[1] + chunkY,
                            PLOT_PLANE,
                            0);
                    for (int plane = 1; plane < 4; plane++) {
                        MapBuilder.cutChunk(boundChunks[0] + chunkX, boundChunks[1] + chunkY, plane);
                    }
                }
            }

            final WorldTile entryTile = getEntryTile();
            final Region region = World.getRegion(entryTile.getRegionId(), true);
            World.executeAfterLoadRegion(region.getRegionId(), 2400, new Runnable() {
                @Override
                public void run() {
                    finishLoad(entryTile);
                }
            });
        } catch (Throwable e) {
            Logger.handle(e);
            failLoad("Settlement instance failed to load.");
        }
    }

    private void finishLoad(WorldTile entryTile) {
        if (destroyed || boundChunks == null) {
            return;
        }

        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            spawnProjectedPiece(piece);
        }
        spawnStarterResourceNodes();
        checkStarterShelterMilestone();
        ensureStarterWorkerRuntime();

        player.setForceNextMapLoadRefresh(true);
        player.loadMapRegions();
        player.lock(2);
        player.setNextWorldTile(entryTile);
        loaded = true;
        player.getPackets().sendGameMessage(
                "Settlement loaded: " + state.size() + " saved build piece" + (state.size() == 1 ? "." : "s."));
    }

    private void failLoad(String message) {
        player.unlock();
        player.getPackets().sendGameMessage(message);
        leaveToReturn();
    }

    public String placeDevelopmentPiece(int objectId, int objectType, int rotation, WorldTile worldTile) {
        if (!loaded) {
            return "Settlement is still loading.";
        }
        if (!containsWorldTile(worldTile)) {
            return "That tile is outside the active settlement plot.";
        }

        SettlementBuildPiece definition = SettlementBuildPiece.forObject(objectId, objectType);
        if (definition == null) {
            return "That object is not an approved settlement build definition.";
        }

        int plotX = toPlotX(worldTile.getX());
        int plotY = toPlotY(worldTile.getY());
        if (isReservedInfrastructureTile(plotX, plotY, worldTile.getPlane())) {
            return "That tile is reserved for settlement infrastructure.";
        }
        SettlementPlacedPiece saved = state.place(
                definition, plotX, plotY, worldTile.getPlane(), rotation);
        if (saved == null) {
            return "That settlement slot is already occupied.";
        }

        spawnProjectedPiece(saved);
        checkStarterShelterMilestone();
        return "Settlement placed " + definition.getDisplayName()
                + " at plot " + plotX + ", " + plotY + ".";
    }

    /**
     * Server-authoritative player Construction placement.
     */
    public String placePlayerPiece(String definitionKey, int rotation, WorldTile worldTile) {
        if (!loaded) {
            return "Settlement is still loading.";
        }
        if (!containsWorldTile(worldTile)) {
            return "That tile is outside the active settlement plot.";
        }
        if (rotation < 0 || rotation > 3) {
            return "Construction rotation must be 0-3.";
        }

        SettlementBuildPiece definition = SettlementBuildPiece.forKey(definitionKey);
        if (definition == null) {
            return "That is not an approved settlement build piece.";
        }

        int plotX = toPlotX(worldTile.getX());
        int plotY = toPlotY(worldTile.getY());
        if (isReservedInfrastructureTile(plotX, plotY, worldTile.getPlane())) {
            return "That tile is reserved for settlement infrastructure.";
        }

        SettlementResource resource = definition.getBuildResource();
        long cost = definition.getBuildCost();
        SettlementPlacedPiece saved;
        synchronized (state) {
            if (resource == null || cost <= 0L || state.getResourceAmount(resource) < cost) {
                return "You need " + cost + " " + (resource == null ? "material" : resource.getDisplayName())
                        + " in settlement storage to build " + definition.getDisplayName() + ".";
            }

            saved = state.place(definition, plotX, plotY, worldTile.getPlane(), rotation);
            if (saved == null) {
                return "That settlement slot is already occupied.";
            }

            long consumed = state.removeResource(resource, cost);
            if (consumed != cost) {
                state.remove(saved.getPieceId());
                return "Settlement materials changed before placement could complete.";
            }
        }

        spawnProjectedPiece(saved);
        checkStarterShelterMilestone();

        double xp = definition.getConstructionXp();
        if (xp > 0.0) {
            player.getSkills().addXp(Skills.CONSTRUCTION, xp, true);
        }

        return "Built " + definition.getDisplayName() + " for " + cost + " "
                + resource.getDisplayName() + " and earned " + (long) xp + " Construction XP.";
    }

    public String moveDevelopmentPiece(int objectId, WorldTile source, WorldTile destination) {
        if (!loaded || !containsWorldTile(source) || !containsWorldTile(destination)) {
            return "Move target must stay inside the active settlement plot.";
        }

        SettlementPlacedPiece current = findSavedPiece(objectId, source);
        if (current == null) {
            return "That object is not owned by the persistent settlement.";
        }

        int destinationPlotX = toPlotX(destination.getX());
        int destinationPlotY = toPlotY(destination.getY());
        if (isReservedInfrastructureTile(
                destinationPlotX, destinationPlotY, destination.getPlane())) {
            return "That destination is reserved for settlement infrastructure.";
        }

        SettlementPlacedPiece moved = state.move(
                current.getPieceId(),
                destinationPlotX,
                destinationPlotY,
                destination.getPlane());
        if (moved == null) {
            return "The destination settlement slot is occupied or invalid.";
        }

        removeProjectedPiece(current);
        spawnProjectedPiece(moved);
        return "Settlement piece moved.";
    }

    public String duplicateDevelopmentPiece(int objectId, WorldTile source, WorldTile destination) {
        if (!loaded || !containsWorldTile(source) || !containsWorldTile(destination)) {
            return "Duplicate target must stay inside the active settlement plot.";
        }

        SettlementPlacedPiece current = findSavedPiece(objectId, source);
        if (current == null) {
            return "That object is not owned by the persistent settlement.";
        }

        int destinationPlotX = toPlotX(destination.getX());
        int destinationPlotY = toPlotY(destination.getY());
        if (isReservedInfrastructureTile(
                destinationPlotX, destinationPlotY, destination.getPlane())) {
            return "That destination is reserved for settlement infrastructure.";
        }

        SettlementPlacedPiece duplicate = state.duplicate(
                current.getPieceId(),
                destinationPlotX,
                destinationPlotY,
                destination.getPlane());
        if (duplicate == null) {
            return "The destination settlement slot is occupied or invalid.";
        }

        spawnProjectedPiece(duplicate);
        checkStarterShelterMilestone();
        return "Settlement piece duplicated.";
    }

    public String rotateDevelopmentPiece(int objectId, WorldTile source, int delta) {
        if (!loaded || !containsWorldTile(source)) {
            return "Rotate target must be inside the active settlement plot.";
        }

        SettlementPlacedPiece current = findSavedPiece(objectId, source);
        if (current == null) {
            return "That object is not owned by the persistent settlement.";
        }

        SettlementPlacedPiece rotated = state.rotate(current.getPieceId(), delta);
        if (rotated == null) {
            return "Settlement piece could not be rotated.";
        }

        removeProjectedPiece(current);
        spawnProjectedPiece(rotated);
        return "Settlement piece rotated.";
    }

    public String deleteDevelopmentPiece(int objectId, WorldTile source) {
        if (!loaded || !containsWorldTile(source)) {
            return "Delete target must be inside the active settlement plot.";
        }

        SettlementPlacedPiece current = findSavedPiece(objectId, source);
        if (current == null) {
            return "That object is not owned by the persistent settlement.";
        }

        SettlementPlacedPiece removed = state.remove(current.getPieceId());
        if (removed == null) {
            return "Settlement piece could not be removed.";
        }

        removeProjectedPiece(removed);
        return "Settlement piece removed.";
    }

    public boolean handleStarterResourceObjectClick(WorldObject object) {
        if (!loaded || object == null || !containsWorldTile(object)) {
            return false;
        }
        SettlementResourceNode node = SettlementResourceNode.forObject(
                object.getId(),
                toPlotX(object.getX()),
                toPlotY(object.getY()),
                object.getPlane());
        if (node == null) {
            return false;
        }
        player.getActionManager().setAction(new SettlementGatherAction(this, node));
        return true;
    }

    public boolean handleStarterResourceNpcClick(NPC npc) {
        if (!loaded || npc == null || boundChunks == null || npc.getPlane() != PLOT_PLANE) {
            return false;
        }
        int plotX = toPlotX(npc.getX());
        int plotY = toPlotY(npc.getY());
        if (plotX < 0 || plotX >= PLOT_TILES || plotY < 0 || plotY >= PLOT_TILES) {
            return false;
        }
        SettlementResourceNode node = SettlementResourceNode.forNpc(
                npc.getId(), plotX, plotY, npc.getPlane());
        if (node == null) {
            return false;
        }
        player.getActionManager().setAction(new SettlementGatherAction(this, node));
        return true;
    }

    public boolean isStarterResourceNodeAvailable(SettlementResourceNode node) {
        if (!loaded || destroyed || node == null || boundChunks == null) {
            return false;
        }

        if (node.getSourceKind() == SettlementResourceNode.SourceKind.OBJECT) {
            WorldTile tile = new WorldTile(
                    toWorldX(node.getPlotX()),
                    toWorldY(node.getPlotY()),
                    PLOT_PLANE);
            WorldObject live = World.getObjectWithType(tile, node.getObjectType());
            return live != null && live.getId() == node.getRuntimeId();
        }

        for (NPC npc : starterResourceNpcs) {
            if (npc != null && !npc.hasFinished()
                    && npc.getId() == node.getRuntimeId()
                    && toPlotX(npc.getX()) == node.getPlotX()
                    && toPlotY(npc.getY()) == node.getPlotY()
                    && npc.getPlane() == PLOT_PLANE) {
                return true;
            }
        }
        return false;
    }

    public long gatherStarterResource(SettlementResourceNode node) {
        if (!isStarterResourceNodeAvailable(node)) {
            return 0L;
        }
        long added = state.addResource(node.getResource(), 1L);
        if (added > 0L) {
            checkStarterShelterMilestone();
        }
        return added;
    }

    private void checkStarterShelterMilestone() {
        if (!state.tryCompleteStarterShelterMilestone()) {
            return;
        }
        player.getPackets().sendGameMessage(
                "<col=3CB371>Starter shelter milestone complete!</col> "
                        + "Your settlement is ready to attract its first worker.");
        System.out.println("[Settlement] Starter shelter milestone completed for "
                + player.getUsername() + ".");
        ensureStarterWorkerRuntime();
    }

    private void ensureStarterWorkerRuntime() {
        if (destroyed || boundChunks == null
                || !state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return;
        }

        int beforeCount = state.getWorkerCount();
        SettlementWorkerState worker = state.ensureStarterWorker();
        if (worker == null) {
            return;
        }

        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished() && npc.getWorkerId() == worker.getWorkerId()) {
                return;
            }
        }

        SettlementWorkerDefinition definition = SettlementWorkerDefinition.forKey(
                worker.getDefinitionKey());
        if (definition == null
                || !SettlementState.isValidPlotLocation(
                        worker.getHomePlotX(), worker.getHomePlotY(), worker.getHomePlane())) {
            return;
        }

        WorldTile tile = new WorldTile(
                toWorldX(worker.getHomePlotX()),
                toWorldY(worker.getHomePlotY()),
                worker.getHomePlane());
        SettlementWorkerNpc npc = new SettlementWorkerNpc(this, definition, worker, tile);
        workerNpcs.add(npc);

        if (state.getWorkerCount() > beforeCount) {
            player.getPackets().sendGameMessage(
                    "<col=3CB371>A settler has arrived.</col> "
                            + worker.getName() + " is ready for work.");
            System.out.println("[Settlement] Worker #" + worker.getWorkerId()
                    + " arrived for " + player.getUsername() + ".");
        }
    }

    public int getActiveWorkerCount() {
        int count = 0;
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished()) {
                count++;
            }
        }
        return count;
    }

    public boolean hasActiveWorker(long workerId) {
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished() && npc.getWorkerId() == workerId) {
                return true;
            }
        }
        return false;
    }

    public String getWorkerAiSummary(long workerId) {
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished() && npc.getWorkerId() == workerId) {
                return "Worker #" + workerId + " " + npc.getRuntimeWorkSummary();
            }
        }
        return "Worker #" + workerId + " runtime NPC is not active.";
    }

    public WorldTile getWorkerNodeRouteTarget(SettlementResourceNode node) {
        if (!loaded || destroyed || boundChunks == null || node == null) {
            return null;
        }

        if (node.getSourceKind() == SettlementResourceNode.SourceKind.OBJECT) {
            WorldTile tile = new WorldTile(
                    toWorldX(node.getPlotX()),
                    toWorldY(node.getPlotY()),
                    PLOT_PLANE);
            WorldObject live = World.getObjectWithType(tile, node.getObjectType());
            if (live != null && live.getId() == node.getRuntimeId()) {
                return live;
            }
            return null;
        }

        for (NPC npc : starterResourceNpcs) {
            if (npc != null && !npc.hasFinished()
                    && npc.getId() == node.getRuntimeId()
                    && toPlotX(npc.getX()) == node.getPlotX()
                    && toPlotY(npc.getY()) == node.getPlotY()
                    && npc.getPlane() == PLOT_PLANE) {
                return npc;
            }
        }
        return null;
    }

    public WorldTile getWorkerStorageTile(SettlementWorkerState worker) {
        if (!loaded || destroyed || boundChunks == null || worker == null
                || !SettlementState.isValidPlotLocation(
                        worker.getHomePlotX(), worker.getHomePlotY(), worker.getHomePlane())) {
            return null;
        }
        return new WorldTile(
                toWorldX(worker.getHomePlotX()),
                toWorldY(worker.getHomePlotY()),
                worker.getHomePlane());
    }

    public long getWorkerStorageRemaining() {
        return loaded && !destroyed ? state.getStorageRemaining() : 0L;
    }

    public long depositWorkerResource(SettlementResource resource, long amount) {
        if (!loaded || destroyed || resource == null || amount <= 0L) {
            return 0L;
        }
        return state.addResource(resource, amount);
    }

    public long consumeWorkerResource(SettlementResource resource, long amount) {
        if (!loaded || destroyed || resource == null || amount <= 0L) {
            return 0L;
        }
        return state.removeResource(resource, amount);
    }

    /**
     * A productive worker earns personal Hauling XP and modest player
     * Construction XP only when real output successfully enters settlement
     * storage. Idle/waiting/blocked workers therefore produce no passive XP.
     */
    public void recordWorkerDepositProgress(SettlementWorkerState worker, long depositedAmount) {
        if (!loaded || destroyed || worker == null || depositedAmount <= 0L) {
            return;
        }
        SettlementWorkerJob haul = SettlementWorkerJob.HAUL;
        worker.addSkillXp(haul.getSkill(), haul.getWorkerXp() * depositedAmount);
        player.getSkills().addXp(
                Skills.CONSTRUCTION,
                PASSIVE_CONSTRUCTION_XP_PER_RESOURCE * depositedAmount,
                true);
    }

    /**
     * Phase-1 provisional water support. The completed starter shelter provides
     * a basic local drinking source until physical wells/barrels/tanks replace
     * this seam in the broader settlement production phase.
     */
    public boolean hasBasicWorkerWaterSupply() {
        return loaded && !destroyed
                && state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER);
    }

    private boolean isReservedInfrastructureTile(int plotX, int plotY, int plane) {
        return SettlementResourceNode.occupiesPlotTile(plotX, plotY, plane)
                || SettlementWorkerDefinition.isReservedArrivalTile(plotX, plotY, plane);
    }

    private void spawnStarterResourceNodes() {
        if (boundChunks == null || destroyed) {
            return;
        }
        removeStarterResourceNodes();

        for (SettlementResourceNode node : SettlementResourceNode.values()) {
            WorldTile tile = new WorldTile(
                    toWorldX(node.getPlotX()),
                    toWorldY(node.getPlotY()),
                    PLOT_PLANE);
            if (node.getSourceKind() == SettlementResourceNode.SourceKind.OBJECT) {
                WorldObject object = new WorldObject(
                        node.getRuntimeId(),
                        node.getObjectType(),
                        0,
                        tile.getX(),
                        tile.getY(),
                        tile.getPlane());
                World.spawnObject(object);
                starterResourceObjects.add(object);
            } else {
                NPC npc = World.spawnNPC(
                        node.getRuntimeId(),
                        tile,
                        -1,
                        false,
                        true);
                if (npc != null) {
                    starterResourceNpcs.add(npc);
                }
            }
        }
    }

    private void removeStarterResourceNodes() {
        for (WorldObject resourceObject : starterResourceObjects) {
            if (resourceObject == null) {
                continue;
            }
            WorldTile tile = new WorldTile(
                    resourceObject.getX(),
                    resourceObject.getY(),
                    resourceObject.getPlane());
            WorldObject live = World.getObjectWithType(tile, resourceObject.getType());
            if (live != null && live.getId() == resourceObject.getId()) {
                World.removeObject(live);
            }
        }
        starterResourceObjects.clear();

        for (NPC npc : starterResourceNpcs) {
            if (npc != null && !npc.hasFinished()) {
                npc.finish();
            }
        }
        starterResourceNpcs.clear();
    }

    private void removeSettlementWorkers() {
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished()) {
                npc.finish();
            }
        }
        workerNpcs.clear();
    }

    public boolean containsWorldTile(WorldTile tile) {
        if (tile == null || boundChunks == null || tile.getPlane() != PLOT_PLANE) {
            return false;
        }
        int plotX = toPlotX(tile.getX());
        int plotY = toPlotY(tile.getY());
        return plotX >= 0 && plotX < PLOT_TILES && plotY >= 0 && plotY < PLOT_TILES;
    }

    public int getSavedPieceCount() {
        return state.size();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public WorldTile getEntryTile() {
        if (boundChunks == null) {
            return null;
        }
        return new WorldTile(
                boundChunks[0] * 8 + ENTRY_OFFSET,
                boundChunks[1] * 8 + ENTRY_OFFSET,
                PLOT_PLANE);
    }

    public void leaveToReturn() {
        if (destroyed) {
            return;
        }
        player.getControlerManager().removeControlerWithoutCheck();
        player.setForceNextMapLoadRefresh(true);
        player.setNextWorldTile(returnTile);
        destroy();
    }

    public void leaveForLogout() {
        if (destroyed) {
            return;
        }
        player.getControlerManager().removeControlerWithoutCheck();
        player.setLocation(returnTile);
        destroy();
    }

    public void leaveForTeleport() {
        if (destroyed) {
            return;
        }
        player.getControlerManager().removeControlerWithoutCheck();
        destroy();
    }

    private SettlementPlacedPiece findSavedPiece(int objectId, WorldTile worldTile) {
        return state.find(
                objectId,
                toPlotX(worldTile.getX()),
                toPlotY(worldTile.getY()),
                worldTile.getPlane());
    }

    private void spawnProjectedPiece(SettlementPlacedPiece piece) {
        if (piece == null || boundChunks == null
                || !SettlementState.isValidPlotLocation(
                        piece.getPlotX(), piece.getPlotY(), piece.getPlane())) {
            return;
        }
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
        if (definition == null) {
            return;
        }

        World.spawnObject(new WorldObject(
                definition.getObjectId(),
                definition.getObjectType(),
                piece.getRotation(),
                toWorldX(piece.getPlotX()),
                toWorldY(piece.getPlotY()),
                piece.getPlane()));
    }

    private void removeProjectedPiece(SettlementPlacedPiece piece) {
        if (piece == null || boundChunks == null) {
            return;
        }
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
        if (definition == null) {
            return;
        }

        WorldTile tile = new WorldTile(
                toWorldX(piece.getPlotX()),
                toWorldY(piece.getPlotY()),
                piece.getPlane());
        WorldObject live = World.getObjectWithType(tile, definition.getObjectType());
        if (live != null && live.getId() == definition.getObjectId()) {
            World.removeObject(live);
        }
    }

    private int toPlotX(int worldX) {
        return worldX - boundChunks[0] * 8;
    }

    private int toPlotY(int worldY) {
        return worldY - boundChunks[1] * 8;
    }

    private int toWorldX(int plotX) {
        return boundChunks[0] * 8 + plotX;
    }

    private int toWorldY(int plotY) {
        return boundChunks[1] * 8 + plotY;
    }

    private void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        loaded = false;
        removeSettlementWorkers();
        removeStarterResourceNodes();

        final int[] bounds = boundChunks;
        boundChunks = null;
        if (bounds == null) {
            return;
        }

        GameExecutorManager.slowExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    MapBuilder.destroyMap(bounds[0], bounds[1], PLOT_CHUNKS, PLOT_CHUNKS);
                } catch (Throwable e) {
                    Logger.handle(e);
                }
            }
        }, 1200L, TimeUnit.MILLISECONDS);
    }
}
