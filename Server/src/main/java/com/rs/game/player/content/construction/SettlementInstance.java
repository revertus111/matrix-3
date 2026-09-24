package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    // V1 presentation placeholder until a dedicated mine-cart NPC/model is accepted.
    private static final int RAIL_CART_NPC_ID = 1;
    private static final long RAIL_WOOD_PAYLOAD = 1L;

    private final Player player;
    private final SettlementState state;
    private final WorldTile returnTile;

    private volatile int[] boundChunks;
    private volatile boolean loaded;
    private volatile boolean destroyed;

    private final List<WorldObject> starterResourceObjects = new ArrayList<WorldObject>();
    private final List<NPC> starterResourceNpcs = new ArrayList<NPC>();
    private final List<SettlementWorkerNpc> workerNpcs = new ArrayList<SettlementWorkerNpc>();
    private final List<SettlementRailCartNpc> railCarts = new ArrayList<SettlementRailCartNpc>();
    private final List<Long> radialSelectedWorkerIds = new ArrayList<Long>();
    private boolean radialPlayerSelected;
    private final WorkerStorageReservationBook workerStorageReservations =
            new WorkerStorageReservationBook();

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
        // Settlement RTS can pan well beyond vanilla's 14-tile NPC update radius.
        // Use the protocol's existing large-scene NPC packet (8-bit offsets / 64-tile
        // local radius) for the lifetime of the settlement.
        player.setLargeSceneView(true);
        player.getLocalNPCUpdate().reset();
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
        refreshRailLogistics();
        ensureSettlementWorkersRuntime();

        player.setForceNextMapLoadRefresh(true);
        player.loadMapRegions();
        player.lock(2);
        player.setNextWorldTile(entryTile);
        loaded = true;
        player.getPackets().sendGameMessage(
                "Settlement loaded: " + state.size() + " saved build piece" + (state.size() == 1 ? "." : "s."));

        /*
         * setNextWorldTile/loadMapRegions queues the client's dynamic-region rebuild.
         * The RTS signal must not arrive in that same server tick or the already-
         * proven detached camera starts against the outgoing scene and the viewport
         * goes flat. Fire the existing RTS entry only after the player's two-tick
         * settlement transfer lock has elapsed.
         */
        GameExecutorManager.slowExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && loaded && getActive(player) == SettlementInstance.this) {
                    player.getPackets().sendCSVarInteger(2835, 1);
                }
            }
        }, 1800L, TimeUnit.MILLISECONDS);
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

        /*
         * Rail Auto-Connect V1: dragging a new route onto an existing rail is
         * an idempotent connection operation, not an occupied-slot failure.
         * The existing persistent rail remains the owner of that tile; the
         * remainder of the queued A->B route can continue building normally.
         *
         * We intentionally do not replace perpendicular/branch visuals here.
         * T/cross/switch art needs an accepted cache asset before it can be
         * represented honestly.
         */
        if (definition.getRole() == SettlementBuildRole.RAIL) {
            SettlementPlacedPiece existingRail = state.findRailAt(
                    plotX, plotY, worldTile.getPlane());
            if (existingRail != null) {
                SettlementBuildPiece existingDefinition =
                        SettlementBuildPiece.forKey(existingRail.getDefinitionKey());
                if (existingDefinition != null
                        && existingDefinition.getObjectId() == definition.getObjectId()
                        && existingRail.getRotation() == rotation) {
                    refreshRailLogistics();
                    return "Rail auto-connected to existing track.";
                }

                /*
                 * DIAGNOSTIC SAFETY FREEZE:
                 * Never mutate a previously good rail while continuation
                 * geometry is under investigation. Identical rails above are
                 * idempotent; any different component/rotation on an occupied
                 * rail tile is reported and left untouched.
                 */
                return "Rail debug conflict: existing track preserved.";
            }
        }

        SettlementPlayerBuildTransaction.Result result =
                SettlementPlayerBuildTransaction.apply(
                        state,
                        definition,
                        plotX,
                        plotY,
                        worldTile.getPlane(),
                        rotation,
                        isReservedInfrastructureTile(plotX, plotY, worldTile.getPlane()));
        if (!result.isSuccess()) {
            return result.getMessage();
        }

        SettlementPlacedPiece saved = result.getPlacedPiece();
        spawnProjectedPiece(saved);
        checkStarterShelterMilestone();
        if (definition.getRole() == SettlementBuildRole.RAIL
                || definition.getRole() == SettlementBuildRole.RAIL_LOADER
                || definition.getRole() == SettlementBuildRole.RAIL_UNLOADER) {
            refreshRailLogistics();
        }

        double xp = result.getConstructionXp();
        if (xp > 0.0) {
            player.getSkills().addXp(Skills.CONSTRUCTION, xp, true);
        }

        String built;
        if (result.getResource() == null || result.getCost() <= 0L) {
            built = "Built " + definition.getDisplayName() + " for free and earned "
                    + (long) xp + " Construction XP.";
        } else {
            built = "Built " + definition.getDisplayName() + " for " + result.getCost() + " "
                    + result.getResource().getDisplayName() + " and earned "
                    + (long) xp + " Construction XP.";
        }
        return definition.getRole() == SettlementBuildRole.BED
                ? built + " Housing: " + state.getHousingSummary()
                : built;
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

    public String undoLastBuild() {
        if (!loaded) {
            return "Settlement is still loading.";
        }
        java.util.List<SettlementPlacedPiece> pieces = state.snapshotPieces();
        if (pieces.isEmpty()) {
            return "Nothing to undo.";
        }
        SettlementPlacedPiece last = pieces.get(pieces.size() - 1);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(last.getDefinitionKey());
        SettlementPlacedPiece removed = state.remove(last.getPieceId());
        if (removed == null) {
            return definition != null && definition.getRole() == SettlementBuildRole.BED
                    && !state.canRemoveHousingBed()
                    ? "The last build is a bed required by the current settlement population."
                    : "Last settlement build could not be undone.";
        }
        removeProjectedPiece(removed);
        refreshRailLogistics();
        return "Undid last settlement build.";
    }

    public String clearPlayerBuilds() {
        if (!loaded) {
            return "Settlement is still loading.";
        }
        int removedCount = 0;
        java.util.List<SettlementPlacedPiece> pieces = state.snapshotPieces();
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SettlementPlacedPiece piece = pieces.get(i);
            SettlementPlacedPiece removed = state.remove(piece.getPieceId());
            if (removed != null) {
                removeProjectedPiece(removed);
                removedCount++;
            }
        }
        refreshRailLogistics();
        return removedCount == 0
                ? "No removable settlement builds found."
                : "Removed " + removedCount + " settlement build(s).";
    }

    public String eraseRailPiece(int objectId, WorldTile source) {
        if (!loaded || source == null || !containsWorldTile(source)) {
            return "Rail edit target must be inside the active settlement plot.";
        }
        SettlementPlacedPiece current = findSavedPiece(objectId, source);
        if (current == null) {
            return "Rail edit skipped: old rail piece already absent.";
        }
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || definition.getRole() != SettlementBuildRole.RAIL) {
            return "Rail edit refused: target is not a persistent rail.";
        }
        SettlementPlacedPiece removed = state.remove(current.getPieceId());
        if (removed == null) {
            return "Rail edit could not remove the old rail piece.";
        }
        removeProjectedPiece(removed);
        refreshRailLogistics();
        return "Rail edit removed old route piece.";
    }

    public String eraseDevelopmentTile(WorldTile source) {
        if (!loaded || source == null || !containsWorldTile(source)) {
            return "Erase target must be inside the active settlement plot.";
        }
        int plotX = toPlotX(source.getX());
        int plotY = toPlotY(source.getY());
        int removedCount = 0;
        java.util.List<SettlementPlacedPiece> pieces = state.snapshotPieces();
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SettlementPlacedPiece piece = pieces.get(i);
            if (piece == null || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != source.getPlane()) {
                continue;
            }
            SettlementPlacedPiece removed = state.remove(piece.getPieceId());
            if (removed != null) {
                removeProjectedPiece(removed);
                removedCount++;
            }
        }
        if (removedCount > 0) {
            refreshRailLogistics();
        }
        return removedCount == 0
                ? "No removable settlement build found on that tile."
                : "Erased " + removedCount + " settlement build(s) from that tile.";
    }

    public String deleteDevelopmentPiece(int objectId, WorldTile source) {
        if (!loaded || !containsWorldTile(source)) {
            return "Delete target must be inside the active settlement plot.";
        }

        SettlementPlacedPiece current = findSavedPiece(objectId, source);
        if (current == null) {
            return "That object is not owned by the persistent settlement.";
        }

        SettlementBuildPiece definition =
                SettlementBuildPiece.forKey(current.getDefinitionKey());
        SettlementPlacedPiece removed = state.remove(current.getPieceId());
        if (removed == null) {
            if (definition != null && definition.getRole() == SettlementBuildRole.BED
                    && !state.canRemoveHousingBed()) {
                return "That bed is required by the current settlement population and cannot be removed.";
            }
            return "Settlement piece could not be removed.";
        }

        removeProjectedPiece(removed);
        return definition != null && definition.getRole() == SettlementBuildRole.BED
                ? "Settlement bed removed. " + state.getHousingSummary()
                : "Settlement piece removed.";
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
        ensureSettlementWorkersRuntime();
    }

    private void ensureSettlementWorkersRuntime() {
        if (destroyed || boundChunks == null
                || !state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return;
        }

        int beforeCount = state.getWorkerCount();
        SettlementWorkerState starter = state.ensureStarterWorker();
        if (starter == null) {
            return;
        }
        boolean starterArrived = state.getWorkerCount() > beforeCount;

        for (SettlementWorkerState worker : state.snapshotWorkers()) {
            spawnWorkerRuntime(worker);
        }

        if (starterArrived) {
            player.getPackets().sendGameMessage(
                    "<col=3CB371>A settler has arrived.</col> "
                            + starter.getName() + " is ready for work.");
            System.out.println("[Settlement] Worker #" + starter.getWorkerId()
                    + " arrived for " + player.getUsername() + ".");
        }
    }

    private boolean spawnWorkerRuntime(SettlementWorkerState worker) {
        if (worker == null || destroyed || boundChunks == null) {
            return false;
        }
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished()
                    && npc.getWorkerId() == worker.getWorkerId()) {
                return true;
            }
        }

        workerStorageReservations.release(worker.getWorkerId());

        SettlementWorkerDefinition definition = SettlementWorkerDefinition.forKey(
                worker.getDefinitionKey());
        if (definition == null
                || !SettlementState.isValidPlotLocation(
                        worker.getHomePlotX(), worker.getHomePlotY(), worker.getHomePlane())) {
            return false;
        }

        WorldTile tile = new WorldTile(
                toWorldX(worker.getHomePlotX()),
                toWorldY(worker.getHomePlotY()),
                worker.getHomePlane());
        SettlementWorkerNpc npc = new SettlementWorkerNpc(this, definition, worker, tile);
        workerNpcs.add(npc);
        return true;
    }

    public String recruitAdditionalWorker() {
        if (!loaded || destroyed) {
            return "Enter the loaded settlement before recruiting a worker.";
        }
        if (!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER)) {
            return "Complete the starter shelter before recruiting another worker.";
        }
        if (!state.canRecruitAdditionalWorker()) {
            return "Recruitment unavailable: " + state.getPopulationSummary() + ".";
        }

        SettlementWorkerState worker = state.recruitAdditionalWorker();
        if (worker == null) {
            return "Recruitment failed: " + state.getPopulationSummary() + ".";
        }
        if (!spawnWorkerRuntime(worker)) {
            return "Worker #" + worker.getWorkerId()
                    + " was saved but its runtime projection could not be created.";
        }

        player.getPackets().sendGameMessage(
                "<col=3CB371>A new settler has joined your settlement.</col>");
        System.out.println("[Settlement] Worker #" + worker.getWorkerId()
                + " recruited for " + player.getUsername() + ".");
        return "Recruited Worker #" + worker.getWorkerId() + " " + worker.getName()
                + ". " + state.getPopulationSummary() + ".";
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

    /**
     * Resolve client-selected runtime NPC indexes back to this settlement's
     * authoritative persistent worker records.
     *
     * NPC indexes are transient and are never persisted as worker identity.
     * Only live SettlementWorkerNpc instances owned by this active instance are
     * accepted, so arbitrary/non-settlement NPC indexes cannot mutate workers.
     */
    public synchronized List<SettlementWorkerState> resolveRuntimeWorkerSelection(int[] npcIndexes) {
        List<SettlementWorkerState> selected = new ArrayList<SettlementWorkerState>();
        if (!loaded || destroyed || npcIndexes == null || npcIndexes.length == 0) {
            return selected;
        }

        java.util.HashSet<Long> seenWorkerIds = new java.util.HashSet<Long>();
        for (int npcIndex : npcIndexes) {
            if (npcIndex < 0) {
                continue;
            }
            for (SettlementWorkerNpc npc : workerNpcs) {
                if (npc == null || npc.hasFinished() || npc.getIndex() != npcIndex) {
                    continue;
                }
                long workerId = npc.getWorkerId();
                if (!seenWorkerIds.add(Long.valueOf(workerId))) {
                    break;
                }
                SettlementWorkerState worker = state.findWorker(workerId);
                if (worker != null) {
                    selected.add(worker);
                }
                break;
            }
        }
        return selected;
    }

    /**
     * Server-owned transient radial selection.
     *
     * Runtime NPC indexes are accepted only long enough to resolve the active
     * SettlementWorkerNpc projections. The committed selection is then stored
     * as persistent worker IDs for the lifetime of this SettlementInstance, so
     * later batch actions never trust stale client NPC indexes.
     */
    public synchronized String setRuntimeWorkerSelection(int[] npcIndexes) {
        return setRuntimeSelection(npcIndexes, false);
    }

    public synchronized String setRuntimeSelection(int[] npcIndexes, boolean playerSelected) {
        List<SettlementWorkerState> resolved = resolveRuntimeWorkerSelection(npcIndexes);
        radialSelectedWorkerIds.clear();
        radialPlayerSelected = playerSelected;
        for (SettlementWorkerState worker : resolved) {
            radialSelectedWorkerIds.add(Long.valueOf(worker.getWorkerId()));
        }
        if (radialSelectedWorkerIds.isEmpty() && !radialPlayerSelected) {
            return "Radial selection cleared; no active units were inside the drag.";
        }
        StringBuilder message = new StringBuilder("Radial selection committed: ");
        if (!radialSelectedWorkerIds.isEmpty()) {
            message.append(formatWorkerIds(resolved));
        }
        if (radialPlayerSelected) {
            if (!radialSelectedWorkerIds.isEmpty()) {
                message.append(" + ");
            }
            message.append("self");
        }
        return message.append('.').toString();
    }

    public synchronized void clearRuntimeWorkerSelection() {
        radialSelectedWorkerIds.clear();
        radialPlayerSelected = false;
    }

    public synchronized boolean isRuntimePlayerSelected() {
        return radialPlayerSelected;
    }

    public synchronized List<SettlementWorkerState> snapshotRuntimeWorkerSelection() {
        List<SettlementWorkerState> selected = new ArrayList<SettlementWorkerState>();
        java.util.Iterator<Long> iterator = radialSelectedWorkerIds.iterator();
        while (iterator.hasNext()) {
            Long workerId = iterator.next();
            SettlementWorkerState worker = workerId == null
                    ? null : state.findWorker(workerId.longValue());
            if (worker == null || !hasActiveWorker(worker.getWorkerId())) {
                iterator.remove();
                continue;
            }
            selected.add(worker);
        }
        return selected;
    }

    public synchronized String orderRuntimeSelectionMove(WorldTile destination) {
        if (!loaded || destroyed || boundChunks == null || destination == null) {
            return "RTS move order unavailable; settlement runtime is not ready.";
        }
        int plotX = toPlotX(destination.getX());
        int plotY = toPlotY(destination.getY());
        if (!SettlementState.isValidPlotLocation(plotX, plotY, destination.getPlane())) {
            return "RTS move target is outside the active settlement plot.";
        }

        List<SettlementWorkerState> selected = snapshotRuntimeWorkerSelection();
        if (selected.isEmpty()) {
            return "No server-owned radial worker selection is active.";
        }

        int ordered = 0;
        for (SettlementWorkerState worker : selected) {
            SettlementWorkerNpc npc = findActiveWorkerNpc(worker.getWorkerId());
            if (npc == null) {
                continue;
            }
            npc.assignManualMoveOrder(destination);
            ordered++;
        }
        return "RTS move order: " + ordered + " worker(s) -> "
                + destination.getX() + "," + destination.getY() + "," + destination.getPlane() + ".";
    }

    public synchronized String orderRuntimeSelectionGather(
            int objectId, int worldX, int worldY, int plane) {
        if (!loaded || destroyed || boundChunks == null) {
            return "RTS gather order unavailable; settlement runtime is not ready.";
        }
        int plotX = toPlotX(worldX);
        int plotY = toPlotY(worldY);
        SettlementResourceNode node =
                SettlementResourceNode.forObject(objectId, plotX, plotY, plane);
        if (node == null || !isStarterResourceNodeAvailable(node)) {
            return "RTS gather target is not an active settlement resource node.";
        }

        List<SettlementWorkerState> selected = snapshotRuntimeWorkerSelection();
        if (selected.isEmpty()) {
            return "No server-owned radial worker selection is active.";
        }

        int ordered = 0;
        for (SettlementWorkerState worker : selected) {
            SettlementWorkerNpc npc = findActiveWorkerNpc(worker.getWorkerId());
            if (npc == null) {
                continue;
            }
            npc.assignManualGatherOrder(node);
            ordered++;
        }
        return "RTS gather order: " + ordered + " worker(s) -> "
                + node.getResource().getDisplayName() + " node.";
    }

    private SettlementWorkerNpc findActiveWorkerNpc(long workerId) {
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc != null && !npc.hasFinished() && npc.getWorkerId() == workerId) {
                return npc;
            }
        }
        return null;
    }

    private static String formatWorkerIds(List<SettlementWorkerState> workers) {
        StringBuilder ids = new StringBuilder();
        if (workers != null) {
            for (SettlementWorkerState worker : workers) {
                if (worker == null) {
                    continue;
                }
                if (ids.length() > 0) {
                    ids.append(',');
                }
                ids.append('#').append(worker.getWorkerId());
            }
        }
        return ids.length() == 0 ? "none" : ids.toString();
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

    public long getWorkerStorageRemaining(SettlementResource resource) {
        return loaded && !destroyed ? state.getStorageRemaining(resource) : 0L;
    }

    public synchronized long getWorkerStorageAvailable(long workerId, SettlementResource resource) {
        if (!loaded || destroyed || resource == null) {
            return 0L;
        }
        return workerStorageReservations.getAvailable(
                workerId, resource, state.getStorageRemaining(resource));
    }

    public boolean hasWorkerStorageSpace(long workerId, SettlementResource resource) {
        return getWorkerStorageAvailable(workerId, resource) > 0L;
    }

    public synchronized boolean reserveWorkerStorage(
            long workerId, SettlementResource resource, long amount) {
        if (!loaded || destroyed || resource == null || amount <= 0L) {
            return false;
        }
        return workerStorageReservations.reserve(
                workerId, resource, amount, state.getStorageRemaining(resource));
    }

    public synchronized boolean hasWorkerStorageReservation(
            long workerId, SettlementResource resource, long amount) {
        return workerStorageReservations.has(workerId, resource, amount);
    }

    public synchronized void releaseWorkerStorageReservation(long workerId) {
        workerStorageReservations.release(workerId);
    }

    public synchronized long depositWorkerResource(
            long workerId, SettlementResource resource, long amount) {
        if (!loaded || destroyed || resource == null || amount <= 0L) {
            return 0L;
        }
        if (!workerStorageReservations.has(workerId, resource, amount)
                && !workerStorageReservations.reserve(
                        workerId, resource, amount, state.getStorageRemaining(resource))) {
            return 0L;
        }

        workerStorageReservations.release(workerId);
        return state.addResource(resource, amount);
    }

    private synchronized void refreshRailLogistics() {
        if (!loaded || destroyed || boundChunks == null) {
            return;
        }
        removeRailCarts();

        SettlementRailNetwork.Route route = SettlementRailNetwork.findLoaderToUnloader(state);
        if (!route.isValid()) {
            return;
        }
        if (state.getResourceAmount(SettlementResource.WOOD) < RAIL_WOOD_PAYLOAD) {
            return;
        }

        List<WorldTile> worldRoute = new ArrayList<WorldTile>();
        for (SettlementPlacedPiece rail : route.getRails()) {
            worldRoute.add(new WorldTile(
                    toWorldX(rail.getPlotX()), toWorldY(rail.getPlotY()), rail.getPlane()));
        }
        SettlementPlacedPiece unloader = route.getUnloader();
        worldRoute.add(new WorldTile(
                toWorldX(unloader.getPlotX()), toWorldY(unloader.getPlotY()), unloader.getPlane()));

        SettlementPlacedPiece loader = route.getLoader();
        WorldTile start = new WorldTile(
                toWorldX(loader.getPlotX()), toWorldY(loader.getPlotY()), loader.getPlane());
        SettlementRailCartNpc cart = new SettlementRailCartNpc(
                this, RAIL_CART_NPC_ID, start, worldRoute);
        railCarts.add(cart);
        player.getPackets().sendGameMessage(
                "<col=3CB371>Rail Logistics:</col> Wood cart dispatched.");
    }

    public synchronized void completeRailWoodShipment(SettlementRailCartNpc cart) {
        if (cart == null || !railCarts.remove(cart)) {
            return;
        }
        /*
         * V1 uses existing settlement Wood as the payload source and returns it
         * through storage at the destination. This proves physical transport
         * without inventing a second inventory owner before processing machines.
         */
        long removed = state.removeResource(SettlementResource.WOOD, RAIL_WOOD_PAYLOAD);
        if (removed == RAIL_WOOD_PAYLOAD) {
            state.addResource(SettlementResource.WOOD, removed);
            player.getPackets().sendGameMessage(
                    "<col=3CB371>Rail Logistics:</col> Wood shipment reached the Unloader.");
        }
        if (!cart.hasFinished()) {
            cart.finish();
        }
    }

    public synchronized void failRailWoodShipment(SettlementRailCartNpc cart, String reason) {
        if (cart != null) {
            railCarts.remove(cart);
            if (!cart.hasFinished()) {
                cart.finish();
            }
        }
        if (reason != null) {
            player.getPackets().sendGameMessage("<col=FF8C00>Rail Logistics:</col> " + reason);
        }
    }

    private void removeRailCarts() {
        for (SettlementRailCartNpc cart : railCarts) {
            if (cart != null && !cart.hasFinished()) {
                cart.finish();
            }
        }
        railCarts.clear();
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
                || SettlementWorkerDefinition.isReservedArrivalTile(plotX, plotY, plane)
                || state.hasWorkerHomeAt(plotX, plotY, plane);
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
        workerStorageReservations.clear();
    }

    public static String runWorkerStorageReservationSelfTest() {
        try {
            WorkerStorageReservationBook book = new WorkerStorageReservationBook();

            if (!book.reserve(1L, SettlementResource.WOOD, 1L, 1L)) {
                return "FAIL: Worker #1 could not reserve the final Wood slot.";
            }
            if (book.reserve(2L, SettlementResource.WOOD, 1L, 1L)) {
                return "FAIL: Worker #2 overbooked the final Wood slot.";
            }
            if (!book.reserve(2L, SettlementResource.FOOD, 1L, 1L)) {
                return "FAIL: Wood reservation incorrectly blocked Food.";
            }
            if (book.getAvailable(2L, SettlementResource.WOOD, 1L) != 0L) {
                return "FAIL: Worker #2 still sees reserved Wood capacity.";
            }
            if (book.getAvailable(1L, SettlementResource.WOOD, 1L) != 1L) {
                return "FAIL: Worker #1 cannot see its own reserved Wood slot.";
            }

            book.release(1L);
            if (!book.reserve(2L, SettlementResource.WOOD, 1L, 1L)) {
                return "FAIL: released Wood slot was not reusable.";
            }
            if (!book.has(2L, SettlementResource.WOOD, 1L)) {
                return "FAIL: Worker #2 reservation was not retained.";
            }

            book.clear();
            if (book.getAvailable(1L, SettlementResource.WOOD, 1L) != 1L) {
                return "FAIL: reservation clear did not restore availability.";
            }

            return "PASS: final-slot reservation prevents same-resource overbooking without cross-resource blocking.";
        } catch (Throwable failure) {
            String message = failure.getMessage();
            return "FAIL: " + (message == null ? failure.getClass().getSimpleName() : message);
        }
    }

    private static final class WorkerStorageReservationBook {
        private final Map<Long, WorkerStorageReservation> byWorker =
                new HashMap<Long, WorkerStorageReservation>();

        private synchronized long getAvailable(
                long workerId, SettlementResource resource, long storageRemaining) {
            if (resource == null || storageRemaining <= 0L) {
                return 0L;
            }
            long reservedByOthers = 0L;
            for (Map.Entry<Long, WorkerStorageReservation> entry : byWorker.entrySet()) {
                WorkerStorageReservation reservation = entry.getValue();
                if (reservation == null || reservation.resource != resource
                        || entry.getKey().longValue() == workerId) {
                    continue;
                }
                reservedByOthers += reservation.amount;
            }
            return Math.max(0L, storageRemaining - reservedByOthers);
        }

        private synchronized boolean reserve(
                long workerId, SettlementResource resource, long amount, long storageRemaining) {
            if (workerId <= 0L || resource == null || amount <= 0L) {
                return false;
            }

            WorkerStorageReservation existing = byWorker.get(Long.valueOf(workerId));
            if (existing != null && existing.resource == resource && existing.amount >= amount) {
                return true;
            }

            byWorker.remove(Long.valueOf(workerId));
            if (getAvailable(workerId, resource, storageRemaining) < amount) {
                if (existing != null) {
                    byWorker.put(Long.valueOf(workerId), existing);
                }
                return false;
            }

            byWorker.put(Long.valueOf(workerId),
                    new WorkerStorageReservation(resource, amount));
            return true;
        }

        private synchronized boolean has(
                long workerId, SettlementResource resource, long amount) {
            WorkerStorageReservation reservation = byWorker.get(Long.valueOf(workerId));
            return reservation != null
                    && reservation.resource == resource
                    && reservation.amount >= amount;
        }

        private synchronized void release(long workerId) {
            byWorker.remove(Long.valueOf(workerId));
        }

        private synchronized void clear() {
            byWorker.clear();
        }
    }

    private static final class WorkerStorageReservation {
        private final SettlementResource resource;
        private final long amount;

        private WorkerStorageReservation(SettlementResource resource, long amount) {
            this.resource = resource;
            this.amount = amount;
        }
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
        player.getPackets().sendCSVarInteger(2835, 0);
        player.getControlerManager().removeControlerWithoutCheck();
        player.setForceNextMapLoadRefresh(true);
        player.setNextWorldTile(returnTile);
        destroy();
    }

    public void leaveForLogout() {
        if (destroyed) {
            return;
        }
        player.getPackets().sendCSVarInteger(2835, 0);
        player.getControlerManager().removeControlerWithoutCheck();
        player.setLocation(returnTile);
        destroy();
    }

    public void leaveForTeleport() {
        if (destroyed) {
            return;
        }
        player.getPackets().sendCSVarInteger(2835, 0);
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
        removeRailCarts();
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
