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
    private final SettlementDebug debug;
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
    private final WorkerPhysicalStorageReservationBook physicalWorkerStorageReservations =
            new WorkerPhysicalStorageReservationBook();
    private final WorkerPhysicalStorageReservationBook physicalWorkerSourceReservations =
            new WorkerPhysicalStorageReservationBook();
    // One worker owns one physical processing station at a time.
    private final Map<Long, Long> processingWorkstationReservations =
            new HashMap<Long, Long>();
    /*
     * Settlement workers use soft NPC collision while travelling, so their
     * final/interaction positions need a separate transient owner. Paths may
     * cross; reserved destination tiles may not.
     */
    private final Map<Long, WorkerDestinationReservation> workerDestinationReservations =
            new HashMap<Long, WorkerDestinationReservation>();
    private static final int[][] WORKER_DESTINATION_OFFSETS = {
        { 0, 0 },
        { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 },
        { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };
    // Short-lived server-owned staging for packet-safe atomic rail endpoint edits.
    private final List<int[]> pendingRailOld = new ArrayList<int[]>();
    private final List<String[]> pendingRailNew = new ArrayList<String[]>();

    private SettlementInstance(Player player, SettlementState state, WorldTile returnTile) {
        this.player = player;
        this.state = state;
        this.debug = new SettlementDebug();
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

    public SettlementDebug getDebug() {
        return debug;
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

    public String setBuildTileSaved(WorldTile source, boolean saved) {
        if (!loaded || source == null || !containsWorldTile(source)) {
            return "Saved build tile must be inside the active settlement plot.";
        }
        int plotX = toPlotX(source.getX());
        int plotY = toPlotY(source.getY());
        boolean changed = saved
                ? state.saveBuildTile(plotX, plotY, source.getPlane())
                : state.unsaveBuildTile(plotX, plotY, source.getPlane());
        if (saved) {
            return changed
                    ? "Saved build tile " + plotX + ", " + plotY
                            + ". Clear All will preserve builds on this tile."
                    : "Build tile " + plotX + ", " + plotY + " is already saved.";
        }
        return changed
                ? "Unsaved build tile " + plotX + ", " + plotY
                        + ". Clear All may remove builds on this tile."
                : "Build tile " + plotX + ", " + plotY + " was not saved.";
    }

    public String clearPlayerBuilds() {
        if (!loaded) {
            return "Settlement is still loading.";
        }
        int removedCount = 0;
        int preservedCount = 0;
        java.util.List<SettlementPlacedPiece> pieces = state.snapshotPieces();
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SettlementPlacedPiece piece = pieces.get(i);
            if (piece == null) {
                continue;
            }
            if (state.isBuildTileSaved(
                    piece.getPlotX(), piece.getPlotY(), piece.getPlane())) {
                preservedCount++;
                continue;
            }
            SettlementPlacedPiece removed = state.remove(piece.getPieceId());
            if (removed != null) {
                removeProjectedPiece(removed);
                removedCount++;
            }
        }
        refreshRailLogistics();
        if (removedCount == 0) {
            return preservedCount > 0
                    ? "No removable settlement builds found. Preserved "
                            + preservedCount + " build(s) on saved tile(s)."
                    : "No removable settlement builds found.";
        }
        return "Removed " + removedCount + " settlement build(s)"
                + (preservedCount > 0
                        ? "; preserved " + preservedCount + " build(s) on saved tile(s)."
                        : ".");
    }

    public synchronized String beginRailRouteReplacement() {
        pendingRailOld.clear();
        pendingRailNew.clear();
        return "Rail route replacement staging started.";
    }

    public synchronized String stageOldRailRoutePiece(int objectId, int worldX, int worldY, int plane) {
        if (pendingRailOld.size() >= 256) {
            return "Rail old-route staging exceeds 256 pieces.";
        }
        pendingRailOld.add(new int[] { objectId, worldX, worldY, plane });
        return null;
    }

    public synchronized String stageNewRailRoutePiece(
            String key, int worldX, int worldY, int plane, int rotation) {
        if (pendingRailNew.size() >= 256) {
            return "Rail new-route staging exceeds 256 pieces.";
        }
        pendingRailNew.add(new String[] { key, Integer.toString(worldX), Integer.toString(worldY),
                Integer.toString(plane), Integer.toString(rotation) });
        return null;
    }

    public synchronized String commitRailRouteReplacement() {
        int oldCount = pendingRailOld.size();
        int newCount = pendingRailNew.size();
        if (oldCount <= 0 && newCount <= 0) {
            pendingRailOld.clear();
            pendingRailNew.clear();
            return "Rail network delta staging is empty.";
        }
        // Initial Rail Network commit is intentionally add-only. The same
        // atomic replacement owner is used for first placement and later deltas
        // so preview -> release cannot fall back to prototype per-piece builds.
        int[] oldIds = new int[oldCount], oldXs = new int[oldCount],
                oldYs = new int[oldCount], oldPlanes = new int[oldCount];
        for (int i = 0; i < oldCount; i++) {
            int[] value = pendingRailOld.get(i);
            oldIds[i] = value[0]; oldXs[i] = value[1]; oldYs[i] = value[2]; oldPlanes[i] = value[3];
        }
        String[] newKeys = new String[newCount];
        int[] newXs = new int[newCount], newYs = new int[newCount],
                newPlanes = new int[newCount], newRotations = new int[newCount];
        for (int i = 0; i < newCount; i++) {
            String[] value = pendingRailNew.get(i);
            newKeys[i] = value[0];
            newXs[i] = Integer.parseInt(value[1]); newYs[i] = Integer.parseInt(value[2]);
            newPlanes[i] = Integer.parseInt(value[3]); newRotations[i] = Integer.parseInt(value[4]);
        }
        pendingRailOld.clear();
        pendingRailNew.clear();
        return replaceRailRoute(oldIds, oldXs, oldYs, oldPlanes,
                newKeys, newXs, newYs, newPlanes, newRotations);
    }

    /**
     * Atomically replaces one authored rail route. Validation happens against a
     * simulated post-removal footprint before live SettlementState/world objects
     * are touched, so an edit cannot leave a half-erased/half-built route.
     */
    public synchronized String replaceRailRoute(
            int[] oldObjectIds, int[] oldWorldXs, int[] oldWorldYs, int[] oldPlanes,
            String[] newKeys, int[] newWorldXs, int[] newWorldYs, int[] newPlanes, int[] newRotations) {
        if (!loaded || oldObjectIds == null || oldWorldXs == null || oldWorldYs == null || oldPlanes == null
                || newKeys == null || newWorldXs == null || newWorldYs == null
                || newPlanes == null || newRotations == null) {
            return "Rail route edit data is incomplete.";
        }
        int oldCount = oldObjectIds.length;
        int newCount = newKeys.length;
        if (oldWorldXs.length != oldCount || oldWorldYs.length != oldCount || oldPlanes.length != oldCount
                || newWorldXs.length != newCount || newWorldYs.length != newCount
                || newPlanes.length != newCount || newRotations.length != newCount) {
            return "Rail route edit data lengths do not match.";
        }
        if (oldCount > 256 || newCount > 256 || (oldCount <= 0 && newCount <= 0)) {
            return "Rail network edit exceeds the 256-piece delta limit or is empty.";
        }

        java.util.List<SettlementPlacedPiece> oldPieces =
                new java.util.ArrayList<SettlementPlacedPiece>();
        for (int i = 0; i < oldCount; i++) {
            WorldTile tile = new WorldTile(oldWorldXs[i], oldWorldYs[i], oldPlanes[i]);
            if (!containsWorldTile(tile)) {
                return "Old rail route contains a tile outside the active plot.";
            }
            SettlementPlacedPiece existing = findSavedPiece(oldObjectIds[i], tile);
            if (existing == null) {
                /*
                 * Rail Network V1 sends physical deltas. A prior curve/straight
                 * conversion may already have removed this visual piece, so a
                 * missing requested removal is an idempotent no-op rather than
                 * a reason to reject the rest of the network delta.
                 */
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(existing.getDefinitionKey());
            if (definition == null || definition.getRole() != SettlementBuildRole.RAIL) {
                return "Rail route edit refused a non-rail old piece.";
            }
            if (!oldPieces.contains(existing)) {
                oldPieces.add(existing);
            }
        }

        java.util.List<SettlementPlacedPiece> snapshot = state.snapshotPieces();
        for (int i = 0; i < newCount; i++) {
            WorldTile tile = new WorldTile(newWorldXs[i], newWorldYs[i], newPlanes[i]);
            if (!containsWorldTile(tile) || newRotations[i] < 0 || newRotations[i] > 3) {
                return "New rail route contains an invalid tile or rotation.";
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(newKeys[i]);
            if (definition == null || definition.getRole() != SettlementBuildRole.RAIL) {
                return "New rail route contains an unapproved rail piece.";
            }
            int plotX = toPlotX(tile.getX());
            int plotY = toPlotY(tile.getY());
            for (SettlementPlacedPiece occupied : snapshot) {
                if (occupied == null || oldPieces.contains(occupied)
                        || occupied.getPlotX() != plotX || occupied.getPlotY() != plotY
                        || occupied.getPlane() != tile.getPlane()) {
                    continue;
                }
                SettlementBuildPiece occupiedDefinition =
                        SettlementBuildPiece.forKey(occupied.getDefinitionKey());
                if (occupiedDefinition != null
                        && occupiedDefinition.getObjectType() == definition.getObjectType()) {
                    return "Rail route edit blocked by another persistent build.";
                }
            }
        }

        java.util.List<SettlementPlacedPiece> removed =
                new java.util.ArrayList<SettlementPlacedPiece>();
        for (SettlementPlacedPiece oldPiece : oldPieces) {
            SettlementPlacedPiece value = state.remove(oldPiece.getPieceId());
            if (value != null) {
                removed.add(value);
                removeProjectedPiece(value);
            }
        }

        java.util.List<SettlementPlacedPiece> placed =
                new java.util.ArrayList<SettlementPlacedPiece>();
        for (int i = 0; i < newCount; i++) {
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(newKeys[i]);
            SettlementPlacedPiece value = state.place(definition,
                    toPlotX(newWorldXs[i]), toPlotY(newWorldYs[i]), newPlanes[i], newRotations[i]);
            if (value == null) {
                for (SettlementPlacedPiece added : placed) {
                    SettlementPlacedPiece rollback = state.remove(added.getPieceId());
                    if (rollback != null) {
                        removeProjectedPiece(rollback);
                    }
                }
                for (SettlementPlacedPiece restore : removed) {
                    SettlementBuildPiece restoreDefinition =
                            SettlementBuildPiece.forKey(restore.getDefinitionKey());
                    SettlementPlacedPiece restored = state.place(restoreDefinition,
                            restore.getPlotX(), restore.getPlotY(), restore.getPlane(), restore.getRotation());
                    spawnProjectedPiece(restored);
                }
                refreshRailLogistics();
                return "Rail route edit rolled back because replacement placement failed.";
            }
            placed.add(value);
            spawnProjectedPiece(value);
        }
        refreshRailLogistics();
        return "Rail route replaced atomically: " + removed.size()
                + " old, " + placed.size() + " new piece(s).";
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

    public boolean handleStorageObjectClick(WorldObject object) {
        if (!loaded || object == null || !containsWorldTile(object)) {
            return false;
        }
        SettlementPlacedPiece piece = state.find(
                object.getId(),
                toPlotX(object.getX()),
                toPlotY(object.getY()),
                object.getPlane());
        if (piece == null) {
            return false;
        }
        SettlementBuildPiece definition =
                SettlementBuildPiece.forKey(piece.getDefinitionKey());
        if (definition == null || definition.getRole() != SettlementBuildRole.STORAGE) {
            return false;
        }
        SettlementStorageInterface.openChest(player, piece.getPieceId());
        return true;
    }

    public synchronized String configurePhysicalStorage(
            WorldTile source, String setting, String value) {
        if (!loaded || source == null || !containsWorldTile(source)) {
            return "Storage settings target must be inside the active settlement plot.";
        }
        SettlementPlacedPiece piece = findSavedPiece(
                SettlementBuildPiece.BASIC_STORAGE_CHEST.getObjectId(), source);
        if (piece == null) {
            return "Storage settings target is not a persistent Wooden chest.";
        }
        SettlementStorageContainer container =
                state.findStorageContainer(piece.getPieceId());
        if (container == null) {
            return "That physical storage container is unavailable.";
        }

        String key = setting == null ? "" : setting.trim().toLowerCase();
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if ("status".equals(key)) {
            return "Storage policy: " + container.getLogisticsPolicySummary();
        }
        if ("mode".equals(key)) {
            SettlementStorageMode mode;
            try {
                mode = SettlementStorageMode.valueOf(normalized.toUpperCase());
            } catch (IllegalArgumentException ex) {
                return "Unknown storage mode: " + value + ".";
            }
            container.setMode(mode);
        } else if ("priority".equals(key)) {
            if ("up".equals(normalized)) {
                container.setLogisticsPriority(container.getLogisticsPriority() + 1);
            } else if ("down".equals(normalized)) {
                container.setLogisticsPriority(container.getLogisticsPriority() - 1);
            } else if ("reset".equals(normalized)) {
                container.setLogisticsPriority(0);
            } else {
                return "Storage priority must be up, down, or reset.";
            }
        } else if ("deposit".equals(key)) {
            if (!"on".equals(normalized) && !"off".equals(normalized)) {
                return "Worker deposit access must be on or off.";
            }
            container.setWorkerDepositEnabled("on".equals(normalized));
        } else if ("withdraw".equals(key)) {
            if (!"on".equals(normalized) && !"off".equals(normalized)) {
                return "Worker withdraw access must be on or off.";
            }
            container.setWorkerWithdrawEnabled("on".equals(normalized));
        } else if ("filter".equals(key)) {
            if ("any".equals(normalized)) {
                container.clearItemFilters();
            } else if ("logs".equals(normalized)) {
                container.clearItemFilters();
                container.setItemFilter(
                        SettlementFactoryItem.NORMAL_LOGS.getItemId(), true);
            } else if ("planks".equals(normalized)) {
                container.clearItemFilters();
                container.setItemFilter(
                        SettlementFactoryItem.PLANKS.getItemId(), true);
            } else {
                return "Storage filter must be any, logs, or planks.";
            }
        } else {
            return "Unknown storage policy setting: " + setting + ".";
        }

        releaseAllPhysicalStorageReservations();
        return "Storage policy updated: " + container.getLogisticsPolicySummary();
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

    public synchronized WorldTile reserveWorkerDestination(
            long workerId, WorldTile target, int interactionRange) {
        if (!loaded || destroyed || boundChunks == null
                || workerId <= 0L || target == null
                || target.getPlane() != PLOT_PLANE) {
            return null;
        }

        boolean centerBlocked = target instanceof WorldObject || target instanceof NPC;
        int spreadRadius = Math.max(1, interactionRange);
        WorkerDestinationReservation existing =
                workerDestinationReservations.get(Long.valueOf(workerId));
        if (existing != null
                && existing.matches(target, interactionRange, centerBlocked)
                && isWorkerDestinationTileValid(
                        workerId, existing.tile, false)) {
            return new WorldTile(existing.tile);
        }

        workerDestinationReservations.remove(Long.valueOf(workerId));

        /*
         * If this worker is already standing on a valid approach tile, keep it
         * there instead of shuffling around the same target.
         */
        WorldTile current = findActiveWorkerTile(workerId);
        if (current != null
                && Math.abs(current.getX() - target.getX()) <= spreadRadius
                && Math.abs(current.getY() - target.getY()) <= spreadRadius
                && !(centerBlocked
                        && current.getX() == target.getX()
                        && current.getY() == target.getY())
                && isWorkerDestinationTileValid(workerId, current, true)) {
            workerDestinationReservations.put(Long.valueOf(workerId),
                    new WorkerDestinationReservation(
                            target, interactionRange, centerBlocked, current));
            return new WorldTile(current);
        }

        for (int[] offset : WORKER_DESTINATION_OFFSETS) {
            int dx = offset[0];
            int dy = offset[1];
            if (Math.abs(dx) > spreadRadius || Math.abs(dy) > spreadRadius) {
                continue;
            }
            if (centerBlocked && dx == 0 && dy == 0) {
                continue;
            }

            WorldTile candidate = new WorldTile(
                    target.getX() + dx,
                    target.getY() + dy,
                    target.getPlane());
            if (!isWorkerDestinationTileValid(workerId, candidate, true)) {
                continue;
            }

            workerDestinationReservations.put(Long.valueOf(workerId),
                    new WorkerDestinationReservation(
                            target, interactionRange, centerBlocked, candidate));
            return candidate;
        }
        return null;
    }

    public synchronized void releaseWorkerDestination(long workerId) {
        workerDestinationReservations.remove(Long.valueOf(workerId));
    }

    private WorldTile findActiveWorkerTile(long workerId) {
        SettlementWorkerNpc npc = findActiveWorkerNpc(workerId);
        return npc == null
                ? null : new WorldTile(npc.getX(), npc.getY(), npc.getPlane());
    }

    private boolean isWorkerDestinationTileValid(
            long workerId, WorldTile tile, boolean checkCurrentOccupancy) {
        if (tile == null || !containsWorldTile(tile)
                || !World.isFloorFree(tile.getPlane(), tile.getX(), tile.getY(), 1)) {
            return false;
        }

        for (Map.Entry<Long, WorkerDestinationReservation> entry
                : workerDestinationReservations.entrySet()) {
            if (entry.getKey().longValue() == workerId) {
                continue;
            }
            WorkerDestinationReservation reservation = entry.getValue();
            if (reservation != null && sameTile(reservation.tile, tile)) {
                return false;
            }
        }

        if (!checkCurrentOccupancy) {
            return true;
        }

        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc == null || npc.hasFinished() || npc.getWorkerId() == workerId) {
                continue;
            }
            if (npc.getPlane() == tile.getPlane()
                    && npc.getX() == tile.getX()
                    && npc.getY() == tile.getY()) {
                return false;
            }
        }
        for (NPC npc : starterResourceNpcs) {
            if (npc != null && !npc.hasFinished()
                    && npc.getPlane() == tile.getPlane()
                    && npc.getX() == tile.getX()
                    && npc.getY() == tile.getY()) {
                return false;
            }
        }
        for (SettlementRailCartNpc npc : railCarts) {
            if (npc != null && !npc.hasFinished()
                    && npc.getPlane() == tile.getPlane()
                    && npc.getX() == tile.getX()
                    && npc.getY() == tile.getY()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTile(WorldTile a, WorldTile b) {
        return a != null && b != null
                && a.getPlane() == b.getPlane()
                && a.getX() == b.getX()
                && a.getY() == b.getY();
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
        return orderRuntimeSelectionGather(
                SettlementResourceNode.SourceKind.OBJECT, objectId, worldX, worldY, plane);
    }

    public synchronized String orderRuntimeSelectionGather(
            SettlementResourceNode.SourceKind sourceKind,
            int runtimeId, int worldX, int worldY, int plane) {
        if (!loaded || destroyed || boundChunks == null || sourceKind == null) {
            return "RTS gather order unavailable; settlement runtime is not ready.";
        }
        int plotX = toPlotX(worldX);
        int plotY = toPlotY(worldY);
        SettlementResourceNode node = sourceKind == SettlementResourceNode.SourceKind.NPC
                ? SettlementResourceNode.forNpc(runtimeId, plotX, plotY, plane)
                : SettlementResourceNode.forObject(runtimeId, plotX, plotY, plane);
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

    public synchronized String orderRuntimeSelectionProcessWood(
            int objectId, int worldX, int worldY, int plane) {
        if (!loaded || destroyed || boundChunks == null) {
            return "RTS processing order unavailable; settlement runtime is not ready.";
        }
        WorldTile clickedTile = new WorldTile(worldX, worldY, plane);
        if (!containsWorldTile(clickedTile)) {
            return "RTS processing target is outside the active settlement plot.";
        }
        SettlementPlacedPiece piece = findSavedPiece(objectId, clickedTile);
        if (piece == null) {
            return "RTS processing target is not a persistent settlement workstation.";
        }
        SettlementBuildPiece definition =
                SettlementBuildPiece.forKey(piece.getDefinitionKey());
        SettlementProcessingRecipe recipe = SettlementProcessingRecipe.SAW_PLANKS;
        if (!isWorkstationForRecipe(definition, recipe)) {
            return "RTS processing target cannot process Wood.";
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
            npc.assignManualProcessingOrder(recipe, piece.getPieceId());
            ordered++;
        }
        return "RTS Process Wood order: " + ordered
                + " worker(s) -> " + definition.getDisplayName() + ".";
    }

    public synchronized String orderRuntimeSelectionTakeFromStorage(
            int objectId, int worldX, int worldY, int plane) {
        if (!loaded || destroyed || boundChunks == null) {
            return "RTS Take From Here unavailable; settlement runtime is not ready.";
        }
        WorldTile clickedTile = new WorldTile(worldX, worldY, plane);
        if (!containsWorldTile(clickedTile)) {
            return "RTS Take From Here target is outside the active settlement plot.";
        }
        SettlementPlacedPiece piece = findSavedPiece(objectId, clickedTile);
        if (!isPhysicalStoragePiece(piece)) {
            return "RTS Take From Here target is not a persistent physical chest.";
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
            npc.assignManualTakeOrder(piece.getPieceId());
            ordered++;
        }
        return "RTS Take From Here: " + ordered + " worker(s) -> chest#"
                + piece.getPieceId() + ".";
    }

    public synchronized String orderRuntimeSelectionDeliverToStorage(
            int objectId, int worldX, int worldY, int plane) {
        if (!loaded || destroyed || boundChunks == null) {
            return "RTS Deliver Here unavailable; settlement runtime is not ready.";
        }
        WorldTile clickedTile = new WorldTile(worldX, worldY, plane);
        if (!containsWorldTile(clickedTile)) {
            return "RTS Deliver Here target is outside the active settlement plot.";
        }
        SettlementPlacedPiece piece = findSavedPiece(objectId, clickedTile);
        if (!isPhysicalStoragePiece(piece)) {
            return "RTS Deliver Here target is not a persistent physical chest.";
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
            npc.assignManualDeliverOrder(piece.getPieceId());
            ordered++;
        }
        return "RTS Deliver Here: " + ordered + " worker(s) -> chest#"
                + piece.getPieceId() + ".";
    }

    public synchronized String setRuntimeSelectionBehavior(
            SettlementWorkerBehaviorMode mode) {
        if (mode == null) {
            return "Worker behavior mode is invalid.";
        }
        List<SettlementWorkerState> selected = snapshotRuntimeWorkerSelection();
        if (selected.isEmpty()) {
            return "No server-owned radial worker selection is active.";
        }
        for (SettlementWorkerState worker : selected) {
            worker.setBehaviorMode(mode);
        }
        releaseAllPhysicalStorageReservations();
        return "Worker behavior set to " + mode.getDisplayName()
                + " for " + formatWorkerIds(selected) + ".";
    }

    public synchronized String setRuntimeNpcBehavior(
            int runtimeNpcIndex, SettlementWorkerBehaviorMode mode) {
        if (mode == null) {
            return "Worker behavior mode is invalid.";
        }
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc == null || npc.hasFinished() || npc.getIndex() != runtimeNpcIndex) {
                continue;
            }
            SettlementWorkerState worker = state.findWorker(npc.getWorkerId());
            if (worker == null) {
                break;
            }
            worker.setBehaviorMode(mode);
            releaseAllPhysicalStorageReservations();
            return "Worker #" + worker.getWorkerId()
                    + " behavior=" + mode.getDisplayName() + ".";
        }
        return "That runtime NPC is not an active settlement worker.";
    }

    public synchronized String assignRuntimeSelectionRally(WorldTile destination) {
        if (!loaded || destroyed || boundChunks == null || destination == null
                || !containsWorldTile(destination)) {
            return "Rally target must be inside the active settlement plot.";
        }
        List<SettlementWorkerState> selected = snapshotRuntimeWorkerSelection();
        if (selected.isEmpty()) {
            return "No server-owned radial worker selection is active.";
        }
        SettlementRallyPoint rally = state.findOrCreateRallyPoint(
                toPlotX(destination.getX()),
                toPlotY(destination.getY()),
                destination.getPlane());
        if (rally == null) {
            return "Unable to create that rally work zone.";
        }
        for (SettlementWorkerState worker : selected) {
            worker.setRallyPointId(rally.getRallyId());
            worker.setBehaviorMode(SettlementWorkerBehaviorMode.RALLY_RESTRICTED);
        }
        releaseAllPhysicalStorageReservations();
        return "Assigned " + formatWorkerIds(selected)
                + " to " + rally.getName()
                + " radius=" + rally.getRadius() + ".";
    }

    public synchronized String clearRuntimeSelectionRally() {
        List<SettlementWorkerState> selected = snapshotRuntimeWorkerSelection();
        if (selected.isEmpty()) {
            return "No server-owned radial worker selection is active.";
        }
        for (SettlementWorkerState worker : selected) {
            worker.clearRallyPoint();
            if (worker.getBehaviorMode()
                    == SettlementWorkerBehaviorMode.RALLY_RESTRICTED) {
                worker.setBehaviorMode(SettlementWorkerBehaviorMode.AUTONOMOUS);
            }
        }
        releaseAllPhysicalStorageReservations();
        return "Cleared rally assignment for " + formatWorkerIds(selected)
                + "; behavior returned to Autonomous.";
    }

    public boolean isWorkerNodeAllowedByRally(
            SettlementWorkerState worker, SettlementResourceNode node) {
        return node != null && isWorkerPlotAllowedByRally(
                worker, node.getPlotX(), node.getPlotY(), SettlementState.PLOT_PLANE);
    }

    private boolean isWorkerPieceAllowedByRally(
            SettlementWorkerState worker, SettlementPlacedPiece piece) {
        return piece != null && isWorkerPlotAllowedByRally(
                worker, piece.getPlotX(), piece.getPlotY(), piece.getPlane());
    }

    private boolean isWorkerPlotAllowedByRally(
            SettlementWorkerState worker, int plotX, int plotY, int plane) {
        if (worker == null
                || worker.getBehaviorMode()
                        != SettlementWorkerBehaviorMode.RALLY_RESTRICTED) {
            return true;
        }
        SettlementRallyPoint rally =
                state.findRallyPoint(worker.getRallyPointId());
        return rally != null && rally.contains(plotX, plotY, plane);
    }

    public synchronized String replaceRuntimeSelectionAllowedJobs(
            java.util.Set<SettlementWorkerJob> jobs) {
        if (jobs == null) {
            return "Allowed Jobs replacement is invalid.";
        }
        List<SettlementWorkerState> selected = snapshotRuntimeWorkerSelection();
        if (selected.isEmpty()) {
            return "No server-owned radial worker selection is active.";
        }
        for (SettlementWorkerState worker : selected) {
            replaceAllowedJobs(worker, jobs);
        }
        return "Allowed Jobs replaced for radial selection "
                + formatWorkerIds(selected) + ".";
    }

    public synchronized String replaceRuntimeNpcAllowedJobs(
            int runtimeNpcIndex, java.util.Set<SettlementWorkerJob> jobs) {
        if (jobs == null) {
            return "Allowed Jobs replacement is invalid.";
        }
        for (SettlementWorkerNpc npc : workerNpcs) {
            if (npc == null || npc.hasFinished() || npc.getIndex() != runtimeNpcIndex) {
                continue;
            }
            SettlementWorkerState worker = state.findWorker(npc.getWorkerId());
            if (worker == null) {
                break;
            }
            replaceAllowedJobs(worker, jobs);
            return "Allowed Jobs replaced for Worker #" + worker.getWorkerId() + ".";
        }
        return "That runtime NPC is not an active settlement worker.";
    }

    private static void replaceAllowedJobs(
            SettlementWorkerState worker, java.util.Set<SettlementWorkerJob> jobs) {
        if (worker == null) {
            return;
        }
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            worker.setJobAllowed(job, jobs.contains(job));
        }
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

    public boolean canProcessRecipe(SettlementProcessingRecipe recipe) {
        if (!loaded || destroyed || recipe == null) {
            return false;
        }
        SettlementFactoryItem input = SettlementFactoryItem.forResource(recipe.getInputResource());
        SettlementFactoryItem output = SettlementFactoryItem.forResource(recipe.getOutputResource());
        if (input != null && output != null) {
            long availableInput = 0L;
            for (SettlementStorageContainer container : state.snapshotStorageContainers()) {
                if (container == null || !container.isWorkerWithdrawEnabled()
                        || !container.acceptsItem(input.getItemId())) {
                    continue;
                }
                availableInput += container.getItemAmount(input.getItemId());
                if (availableInput >= recipe.getInputAmount()) {
                    break;
                }
            }
            for (SettlementPlacedPiece piece : state.snapshotPieces()) {
                if (piece == null) {
                    continue;
                }
                SettlementBuildPiece definition =
                        SettlementBuildPiece.forKey(piece.getDefinitionKey());
                if (!isWorkstationForRecipe(definition, recipe)) {
                    continue;
                }
                SettlementMachineBuffer buffer = state.findMachineBuffer(piece.getPieceId());
                if (buffer == null) {
                    continue;
                }
                if (buffer.getOutputAmount(output.getItemId()) > 0L) {
                    return true;
                }
                long machineInput = buffer.getInputAmount(input.getItemId());
                long missingInput = Math.max(0L,
                        recipe.getInputAmount() - machineInput);
                if (availableInput >= missingInput
                        && machineInput
                                + buffer.getInputCapacityForItem(input.getItemId())
                                >= recipe.getInputAmount()
                        && buffer.getOutputCapacityForItem(output.getItemId())
                                >= recipe.getOutputAmount()) {
                    return true;
                }
            }
            return false;
        }
        return state.getResourceAmount(recipe.getInputResource()) >= recipe.getInputAmount()
                && state.getStorageRemaining(recipe.getOutputResource()) >= recipe.getOutputAmount();
    }

    public synchronized long reserveProcessingWorkstation(
            long workerId, SettlementProcessingRecipe recipe) {
        return reserveProcessingWorkstation(workerId, recipe, -1L);
    }

    public synchronized long reserveProcessingWorkstation(
            long workerId, SettlementProcessingRecipe recipe, long preferredPieceId) {
        if (!canProcessRecipe(recipe) || workerId <= 0L) {
            return -1L;
        }
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (piece == null
                    || preferredPieceId > 0L && piece.getPieceId() != preferredPieceId) {
                continue;
            }
            SettlementBuildPiece definition =
                    SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (!isWorkstationForRecipe(definition, recipe)) {
                continue;
            }
            SettlementFactoryItem input =
                    SettlementFactoryItem.forResource(recipe.getInputResource());
            SettlementFactoryItem output =
                    SettlementFactoryItem.forResource(recipe.getOutputResource());
            if (input != null && output != null) {
                SettlementMachineBuffer buffer = state.findMachineBuffer(piece.getPieceId());
                if (buffer == null) {
                    continue;
                }
                boolean hasPendingOutput =
                        buffer.getOutputAmount(output.getItemId()) > 0L;
                if (!hasPendingOutput
                        && (buffer.getInputAmount(input.getItemId())
                                + buffer.getInputCapacityForItem(input.getItemId())
                                < recipe.getInputAmount()
                        || buffer.getOutputCapacityForItem(output.getItemId())
                                < recipe.getOutputAmount())) {
                    continue;
                }
            }
            if (preferredPieceId <= 0L) {
                SettlementWorkerState policyWorker = state.findWorker(workerId);
                if (!isWorkerPieceAllowedByRally(policyWorker, piece)) {
                    continue;
                }
            }
            Long owner = processingWorkstationReservations.get(
                    Long.valueOf(piece.getPieceId()));
            if (owner != null && owner.longValue() != workerId) {
                continue;
            }
            if (getProcessingWorkstationTile(piece.getPieceId()) == null) {
                continue;
            }
            processingWorkstationReservations.put(
                    Long.valueOf(piece.getPieceId()), Long.valueOf(workerId));
            return piece.getPieceId();
        }
        return -1L;
    }

    public synchronized void releaseProcessingWorkstation(long workerId) {
        java.util.Iterator<Map.Entry<Long, Long>> iterator =
                processingWorkstationReservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            Long owner = entry.getValue();
            if (owner != null && owner.longValue() == workerId) {
                iterator.remove();
            }
        }
    }

    public WorldTile getProcessingWorkstationTile(long pieceId) {
        if (!loaded || destroyed || boundChunks == null || pieceId <= 0L) {
            return null;
        }
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (piece == null || piece.getPieceId() != pieceId) {
                continue;
            }
            SettlementBuildPiece definition =
                    SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition == null
                    || definition.getRole() != SettlementBuildRole.WORKSTATION) {
                return null;
            }
            WorldTile tile = new WorldTile(
                    toWorldX(piece.getPlotX()),
                    toWorldY(piece.getPlotY()),
                    piece.getPlane());
            WorldObject live = World.getObjectWithType(tile, definition.getObjectType());
            return live != null && live.getId() == definition.getObjectId()
                    ? live : null;
        }
        return null;
    }

    public SettlementProcessingTransaction.Result processWorkerRecipe(
            SettlementWorkerState worker, SettlementProcessingRecipe recipe) {
        return processWorkerRecipe(worker, recipe, -1L);
    }

    public SettlementProcessingTransaction.Result processWorkerRecipe(
            SettlementWorkerState worker, SettlementProcessingRecipe recipe,
            long workstationPieceId) {
        if (!loaded || destroyed || worker == null || recipe == null) {
            return null;
        }
        SettlementFactoryItem input = SettlementFactoryItem.forResource(recipe.getInputResource());
        SettlementFactoryItem output = SettlementFactoryItem.forResource(recipe.getOutputResource());
        if (input != null && output != null && workstationPieceId > 0L) {
            SettlementMachineBuffer buffer = state.findMachineBuffer(workstationPieceId);
            return SettlementProcessingTransaction.apply(
                    buffer, recipe, input.getItemId(), output.getItemId(), 1);
        }
        return SettlementProcessingTransaction.apply(state, recipe, 1);
    }

    private boolean isWorkstationForRecipe(
            SettlementBuildPiece definition, SettlementProcessingRecipe recipe) {
        return definition == SettlementBuildPiece.WOODEN_WORKBENCH
                && recipe == SettlementProcessingRecipe.SAW_PLANKS;
    }

    public boolean usesPhysicalWorkerStorage(SettlementResource resource) {
        return SettlementFactoryItem.forResource(resource) != null;
    }

    public int getWorkerStorageInteractionRange(SettlementResource resource) {
        return usesPhysicalWorkerStorage(resource) ? 1 : 0;
    }


    public synchronized boolean reserveWorkerItemSource(
            long workerId, int itemId, long amount, boolean ignoreRally) {
        if (!loaded || destroyed || workerId <= 0L || itemId < 0 || amount <= 0L) {
            return false;
        }
        SettlementWorkerState policyWorker = state.findWorker(workerId);
        WorkerPhysicalStorageReservation existing =
                physicalWorkerSourceReservations.get(workerId);
        if (existing != null && existing.itemId == itemId) {
            SettlementStorageContainer existingContainer =
                    state.findStorageContainer(existing.pieceId);
            SettlementPlacedPiece existingPiece = findPieceById(existing.pieceId);
            if (existingContainer != null
                    && (ignoreRally || isWorkerPieceAllowedByRally(policyWorker, existingPiece))
                    && existingContainer.isWorkerWithdrawEnabled()
                    && existingContainer.acceptsItem(itemId)
                    && getPhysicalStorageObject(existing.pieceId) != null) {
                long reservedByOthers =
                        physicalWorkerSourceReservations.getReservedByOthers(
                                workerId, existing.pieceId, itemId);
                long available = Math.max(0L,
                        existingContainer.getItemAmount(itemId) - reservedByOthers);
                if (available >= amount) {
                    physicalWorkerSourceReservations.reserve(
                            workerId, existing.pieceId, itemId, amount);
                    return true;
                }
            }
        }

        physicalWorkerSourceReservations.release(workerId);
        WorldTile origin = findActiveWorkerTile(workerId);
        SettlementPlacedPiece bestPiece = null;
        long bestScore = Long.MIN_VALUE;
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (!isPhysicalStoragePiece(piece)
                    || !ignoreRally && !isWorkerPieceAllowedByRally(policyWorker, piece)) {
                continue;
            }
            WorldObject live = getPhysicalStorageObject(piece.getPieceId());
            if (live == null) {
                continue;
            }
            SettlementStorageContainer container =
                    state.findStorageContainer(piece.getPieceId());
            if (container == null || !container.isWorkerWithdrawEnabled()
                    || !container.acceptsItem(itemId)) {
                continue;
            }
            long reservedByOthers =
                    physicalWorkerSourceReservations.getReservedByOthers(
                            workerId, piece.getPieceId(), itemId);
            long available = Math.max(0L,
                    container.getItemAmount(itemId) - reservedByOthers);
            if (available < amount) {
                continue;
            }
            int distance = origin == null ? 0
                    : Math.abs(origin.getX() - live.getX())
                            + Math.abs(origin.getY() - live.getY());
            long score = ((long) container.getLogisticsPriority() * 100000L)
                    + getStorageModeWithdrawBonus(container.getMode())
                    + getStorageModeWithdrawPressure(container, itemId)
                    - ((long) distance * 100L)
                    - (reservedByOthers * 2500L);
            if (bestPiece == null || score > bestScore
                    || score == bestScore && piece.getPieceId() < bestPiece.getPieceId()) {
                bestPiece = piece;
                bestScore = score;
            }
        }
        if (bestPiece == null) {
            return false;
        }
        physicalWorkerSourceReservations.reserve(
                workerId, bestPiece.getPieceId(), itemId, amount);
        return true;
    }

    public WorldTile getWorkerItemSourceTile(long workerId) {
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerSourceReservations.get(workerId);
        return reservation == null ? null : getPhysicalStorageObject(reservation.pieceId);
    }

    public synchronized int withdrawWorkerItemSource(
            long workerId, int itemId, int amount) {
        if (itemId < 0 || amount <= 0) {
            return 0;
        }
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerSourceReservations.get(workerId);
        if (reservation == null || reservation.itemId != itemId
                || reservation.amount < amount) {
            return 0;
        }
        SettlementStorageContainer container =
                state.findStorageContainer(reservation.pieceId);
        if (container == null || !container.isWorkerWithdrawEnabled()) {
            physicalWorkerSourceReservations.release(workerId);
            return 0;
        }
        int removed = container.removeItem(itemId, amount);
        physicalWorkerSourceReservations.release(workerId);
        if (removed > 0) {
            SettlementStorageInterface.refreshOpenChest(player, reservation.pieceId);
        }
        return removed;
    }

    public synchronized void releaseWorkerItemSourceReservation(long workerId) {
        physicalWorkerSourceReservations.release(workerId);
    }

    public synchronized int getFirstWorkerWithdrawableItemId(
            long workerId, long pieceId) {
        SettlementStorageContainer container = state.findStorageContainer(pieceId);
        if (container == null || !container.isWorkerWithdrawEnabled()
                || getPhysicalStorageObject(pieceId) == null) {
            return -1;
        }
        for (com.rs.game.item.Item item : container.snapshotItems()) {
            if (item == null || item.getAmount() <= 0) {
                continue;
            }
            long reservedByOthers =
                    physicalWorkerSourceReservations.getReservedByOthers(
                            workerId, pieceId, item.getId());
            if (item.getAmount() - reservedByOthers > 0L) {
                return item.getId();
            }
        }
        return -1;
    }

    public synchronized boolean reserveWorkerItemSourceAt(
            long workerId, long pieceId, int itemId, long amount) {
        if (workerId <= 0L || pieceId <= 0L || itemId < 0 || amount <= 0L) {
            return false;
        }
        SettlementStorageContainer container = state.findStorageContainer(pieceId);
        if (container == null || !container.isWorkerWithdrawEnabled()
                || getPhysicalStorageObject(pieceId) == null) {
            return false;
        }
        long reservedByOthers =
                physicalWorkerSourceReservations.getReservedByOthers(
                        workerId, pieceId, itemId);
        if (container.getItemAmount(itemId) - reservedByOthers < amount) {
            return false;
        }
        physicalWorkerSourceReservations.reserve(workerId, pieceId, itemId, amount);
        return true;
    }

    public WorldTile getPhysicalStorageTile(long pieceId) {
        return getPhysicalStorageObject(pieceId);
    }

    public synchronized int withdrawWorkerItemSourceAt(
            long workerId, long pieceId, int itemId, int amount) {
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerSourceReservations.get(workerId);
        if (reservation == null || reservation.pieceId != pieceId
                || reservation.itemId != itemId || reservation.amount < amount) {
            return 0;
        }
        SettlementStorageContainer container = state.findStorageContainer(pieceId);
        if (container == null || !container.isWorkerWithdrawEnabled()) {
            physicalWorkerSourceReservations.release(workerId);
            return 0;
        }
        int removed = container.removeItem(itemId, amount);
        physicalWorkerSourceReservations.release(workerId);
        if (removed > 0) {
            SettlementStorageInterface.refreshOpenChest(player, pieceId);
        }
        return removed;
    }

    public synchronized boolean reserveWorkerItemDestination(
            long workerId, int itemId, long amount, boolean ignoreRally) {
        return reserveBestPhysicalWorkerItemDestination(
                workerId, itemId, amount, ignoreRally);
    }

    public WorldTile getWorkerItemDestinationTile(long workerId) {
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerStorageReservations.get(workerId);
        return reservation == null ? null : getPhysicalStorageObject(reservation.pieceId);
    }

    public synchronized int depositWorkerItem(
            long workerId, int itemId, int amount, boolean ignoreRally) {
        if (!reserveBestPhysicalWorkerItemDestination(
                workerId, itemId, amount, ignoreRally)) {
            return 0;
        }
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerStorageReservations.get(workerId);
        if (reservation == null) {
            return 0;
        }
        SettlementStorageContainer container =
                state.findStorageContainer(reservation.pieceId);
        if (container == null || !container.isWorkerDepositEnabled()) {
            physicalWorkerStorageReservations.release(workerId);
            return 0;
        }
        int added = container.addItem(itemId, amount);
        physicalWorkerStorageReservations.release(workerId);
        if (added > 0) {
            SettlementStorageInterface.refreshOpenChest(player, reservation.pieceId);
        }
        return added;
    }

    public synchronized void releaseWorkerItemDestinationReservation(long workerId) {
        physicalWorkerStorageReservations.release(workerId);
    }

    public synchronized boolean reserveWorkerItemDestinationAt(
            long workerId, long pieceId, int itemId, long amount) {
        if (workerId <= 0L || pieceId <= 0L || itemId < 0 || amount <= 0L) {
            return false;
        }
        SettlementStorageContainer container = state.findStorageContainer(pieceId);
        if (container == null || !container.isWorkerDepositEnabled()
                || !container.acceptsItem(itemId)
                || getPhysicalStorageObject(pieceId) == null) {
            return false;
        }
        long reservedByOthers =
                physicalWorkerStorageReservations.getReservedByOthers(
                        workerId, pieceId, itemId);
        long available = Math.max(0L,
                container.getAvailableCapacityForItem(itemId) - reservedByOthers);
        if (available < amount) {
            return false;
        }
        physicalWorkerStorageReservations.reserve(
                workerId, pieceId, itemId, amount);
        return true;
    }

    public synchronized int depositWorkerItemToStorageAt(
            long workerId, long pieceId, int itemId, int amount) {
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerStorageReservations.get(workerId);
        if (reservation == null || reservation.pieceId != pieceId
                || reservation.itemId != itemId || reservation.amount < amount) {
            return 0;
        }
        SettlementStorageContainer container = state.findStorageContainer(pieceId);
        if (container == null || !container.isWorkerDepositEnabled()
                || !container.acceptsItem(itemId)) {
            physicalWorkerStorageReservations.release(workerId);
            return 0;
        }
        int added = container.addItem(itemId, amount);
        physicalWorkerStorageReservations.release(workerId);
        if (added > 0) {
            SettlementStorageInterface.refreshOpenChest(player, pieceId);
        }
        return added;
    }

    public long getMachineInputAmount(long pieceId, int itemId) {
        SettlementMachineBuffer buffer = state.findMachineBuffer(pieceId);
        return buffer == null ? 0L : buffer.getInputAmount(itemId);
    }

    public long getMachineOutputAmount(long pieceId, int itemId) {
        SettlementMachineBuffer buffer = state.findMachineBuffer(pieceId);
        return buffer == null ? 0L : buffer.getOutputAmount(itemId);
    }

    public int depositWorkerItemToMachine(long pieceId, int itemId, int amount) {
        SettlementMachineBuffer buffer = state.findMachineBuffer(pieceId);
        return buffer == null ? 0 : buffer.addInput(itemId, amount);
    }

    public int collectMachineOutput(long pieceId, int itemId, int amount) {
        SettlementMachineBuffer buffer = state.findMachineBuffer(pieceId);
        return buffer == null ? 0 : buffer.removeOutput(itemId, amount);
    }

    private boolean reserveBestPhysicalWorkerItemDestination(
            long workerId, int itemId, long amount, boolean ignoreRally) {
        if (workerId <= 0L || itemId < 0 || amount <= 0L) {
            return false;
        }

        SettlementWorkerState policyWorker = state.findWorker(workerId);
        WorkerPhysicalStorageReservation existing =
                physicalWorkerStorageReservations.get(workerId);
        if (existing != null && existing.itemId == itemId) {
            SettlementStorageContainer existingContainer =
                    state.findStorageContainer(existing.pieceId);
            SettlementPlacedPiece existingPiece = findPieceById(existing.pieceId);
            if (existingContainer != null
                    && (ignoreRally || isWorkerPieceAllowedByRally(policyWorker, existingPiece))
                    && existingContainer.isWorkerDepositEnabled()
                    && existingContainer.acceptsItem(itemId)
                    && getPhysicalStorageObject(existing.pieceId) != null) {
                long reservedByOthers =
                        physicalWorkerStorageReservations.getReservedByOthers(
                                workerId, existing.pieceId, itemId);
                long available = Math.max(0L,
                        existingContainer.getAvailableCapacityForItem(itemId)
                                - reservedByOthers);
                if (available >= amount) {
                    physicalWorkerStorageReservations.reserve(
                            workerId, existing.pieceId, itemId, amount);
                    return true;
                }
            }
        }

        physicalWorkerStorageReservations.release(workerId);
        WorldTile origin = findActiveWorkerTile(workerId);
        SettlementPlacedPiece bestPiece = null;
        long bestScore = Long.MIN_VALUE;
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (!isPhysicalStoragePiece(piece)
                    || !ignoreRally && !isWorkerPieceAllowedByRally(policyWorker, piece)) {
                continue;
            }
            WorldObject live = getPhysicalStorageObject(piece.getPieceId());
            if (live == null) {
                continue;
            }
            SettlementStorageContainer container =
                    state.findStorageContainer(piece.getPieceId());
            if (container == null || !container.isWorkerDepositEnabled()
                    || !container.acceptsItem(itemId)) {
                continue;
            }
            long reservedByOthers =
                    physicalWorkerStorageReservations.getReservedByOthers(
                            workerId, piece.getPieceId(), itemId);
            long available = Math.max(0L,
                    container.getAvailableCapacityForItem(itemId) - reservedByOthers);
            if (available < amount) {
                continue;
            }
            int distance = origin == null ? 0
                    : Math.abs(origin.getX() - live.getX())
                            + Math.abs(origin.getY() - live.getY());
            long score = ((long) container.getLogisticsPriority() * 100000L)
                    + getStorageModeDepositBonus(container.getMode())
                    + getStorageModeDepositPressure(container, itemId)
                    - ((long) distance * 100L)
                    - (reservedByOthers * 2500L);
            if (bestPiece == null || score > bestScore
                    || score == bestScore && piece.getPieceId() < bestPiece.getPieceId()) {
                bestPiece = piece;
                bestScore = score;
            }
        }
        if (bestPiece == null) {
            return false;
        }
        physicalWorkerStorageReservations.reserve(
                workerId, bestPiece.getPieceId(), itemId, amount);
        return true;
    }

    public WorldTile getWorkerHaulStorageTile(long workerId,
            SettlementWorkerState worker, SettlementResource resource) {
        if (!usesPhysicalWorkerStorage(resource)) {
            return getWorkerStorageTile(worker);
        }
        WorkerPhysicalStorageReservation reservation =
                physicalWorkerStorageReservations.get(workerId);
        if (reservation == null) {
            return null;
        }
        return getPhysicalStorageObject(reservation.pieceId);
    }

    public long getWorkerStorageRemaining(SettlementResource resource) {
        if (!loaded || destroyed || resource == null) {
            return 0L;
        }
        return usesPhysicalWorkerStorage(resource)
                ? getPhysicalWorkerStorageAvailable(-1L, resource)
                : state.getStorageRemaining(resource);
    }

    public synchronized long getWorkerStorageAvailable(
            long workerId, SettlementResource resource) {
        if (!loaded || destroyed || resource == null) {
            return 0L;
        }
        if (usesPhysicalWorkerStorage(resource)) {
            return getPhysicalWorkerStorageAvailable(workerId, resource);
        }
        return workerStorageReservations.getAvailable(
                workerId, resource, state.getStorageRemaining(resource));
    }

    public boolean hasWorkerStorageSpace(long workerId, SettlementResource resource) {
        return getWorkerStorageAvailable(workerId, resource) > 0L;
    }

    public synchronized boolean reserveWorkerStorage(
            long workerId, SettlementResource resource, long amount) {
        return reserveWorkerStorage(workerId, resource, amount, false);
    }

    public synchronized boolean reserveWorkerStorage(
            long workerId, SettlementResource resource, long amount,
            boolean ignoreRally) {
        if (!loaded || destroyed || resource == null || amount <= 0L) {
            return false;
        }
        if (usesPhysicalWorkerStorage(resource)) {
            return reserveBestPhysicalWorkerStorage(
                    workerId, resource, amount, ignoreRally);
        }
        return workerStorageReservations.reserve(
                workerId, resource, amount, state.getStorageRemaining(resource));
    }

    public synchronized boolean hasWorkerStorageReservation(
            long workerId, SettlementResource resource, long amount) {
        if (usesPhysicalWorkerStorage(resource)) {
            SettlementFactoryItem mapping = SettlementFactoryItem.forResource(resource);
            WorkerPhysicalStorageReservation reservation =
                    physicalWorkerStorageReservations.get(workerId);
            return mapping != null && reservation != null
                    && reservation.itemId == mapping.getItemId()
                    && reservation.amount >= amount;
        }
        return workerStorageReservations.has(workerId, resource, amount);
    }

    public synchronized void releaseWorkerStorageReservation(long workerId) {
        workerStorageReservations.release(workerId);
        physicalWorkerStorageReservations.release(workerId);
    }

    public synchronized long depositWorkerResource(
            long workerId, SettlementResource resource, long amount) {
        return depositWorkerResource(workerId, resource, amount, false);
    }

    public synchronized long depositWorkerResource(
            long workerId, SettlementResource resource, long amount,
            boolean ignoreRally) {
        if (!loaded || destroyed || resource == null || amount <= 0L) {
            return 0L;
        }

        SettlementFactoryItem mapping = SettlementFactoryItem.forResource(resource);
        if (mapping != null) {
            if (!reserveBestPhysicalWorkerStorage(
                    workerId, resource, amount, ignoreRally)) {
                return 0L;
            }
            WorkerPhysicalStorageReservation reservation =
                    physicalWorkerStorageReservations.get(workerId);
            if (reservation == null) {
                return 0L;
            }
            SettlementStorageContainer container =
                    state.findStorageContainer(reservation.pieceId);
            if (container == null || !container.isWorkerDepositEnabled()) {
                physicalWorkerStorageReservations.release(workerId);
                return 0L;
            }
            int request = (int) Math.min((long) Integer.MAX_VALUE, amount);
            int added = container.addItem(mapping.getItemId(), request);
            physicalWorkerStorageReservations.release(workerId);
            if (added > 0) {
                SettlementStorageInterface.refreshOpenChest(player, reservation.pieceId);
            }
            return added;
        }

        if (!workerStorageReservations.has(workerId, resource, amount)
                && !workerStorageReservations.reserve(
                        workerId, resource, amount, state.getStorageRemaining(resource))) {
            return 0L;
        }

        workerStorageReservations.release(workerId);
        return state.addResource(resource, amount);
    }

    private long getPhysicalWorkerStorageAvailable(
            long workerId, SettlementResource resource) {
        SettlementFactoryItem mapping = SettlementFactoryItem.forResource(resource);
        if (mapping == null) {
            return 0L;
        }
        long total = 0L;
        int itemId = mapping.getItemId();
        SettlementWorkerState policyWorker =
                workerId > 0L ? state.findWorker(workerId) : null;
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (!isPhysicalStoragePiece(piece) || getPhysicalStorageObject(piece.getPieceId()) == null
                    || workerId > 0L && !isWorkerPieceAllowedByRally(policyWorker, piece)) {
                continue;
            }
            SettlementStorageContainer container =
                    state.findStorageContainer(piece.getPieceId());
            if (container == null || !container.isWorkerDepositEnabled()
                    || !container.acceptsItem(itemId)) {
                continue;
            }
            long reservedByOthers =
                    physicalWorkerStorageReservations.getReservedByOthers(
                            workerId, piece.getPieceId(), itemId);
            long available = Math.max(0L,
                    container.getAvailableCapacityForItem(itemId) - reservedByOthers);
            total += available;
            if (total < 0L) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    private boolean reserveBestPhysicalWorkerStorage(
            long workerId, SettlementResource resource, long amount,
            boolean ignoreRally) {
        SettlementFactoryItem mapping = SettlementFactoryItem.forResource(resource);
        if (mapping == null || workerId <= 0L || amount <= 0L) {
            return false;
        }
        int itemId = mapping.getItemId();

        SettlementWorkerState policyWorker = state.findWorker(workerId);
        WorkerPhysicalStorageReservation existing =
                physicalWorkerStorageReservations.get(workerId);
        if (existing != null && existing.itemId == itemId) {
            SettlementStorageContainer existingContainer =
                    state.findStorageContainer(existing.pieceId);
            SettlementPlacedPiece existingPiece =
                    findPieceById(existing.pieceId);
            if (existingContainer != null
                    && (ignoreRally || isWorkerPieceAllowedByRally(policyWorker, existingPiece))
                    && existingContainer.isWorkerDepositEnabled()
                    && existingContainer.acceptsItem(itemId)
                    && getPhysicalStorageObject(existing.pieceId) != null) {
                long reservedByOthers =
                        physicalWorkerStorageReservations.getReservedByOthers(
                                workerId, existing.pieceId, itemId);
                long available = Math.max(0L,
                        existingContainer.getAvailableCapacityForItem(itemId)
                                - reservedByOthers);
                if (available >= amount) {
                    physicalWorkerStorageReservations.reserve(
                            workerId, existing.pieceId, itemId, amount);
                    return true;
                }
            }
        }

        physicalWorkerStorageReservations.release(workerId);
        WorldTile origin = findActiveWorkerTile(workerId);
        SettlementPlacedPiece bestPiece = null;
        long bestScore = Long.MIN_VALUE;

        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (!isPhysicalStoragePiece(piece)
                    || !ignoreRally && !isWorkerPieceAllowedByRally(policyWorker, piece)) {
                continue;
            }
            WorldObject live = getPhysicalStorageObject(piece.getPieceId());
            if (live == null) {
                continue;
            }
            SettlementStorageContainer container =
                    state.findStorageContainer(piece.getPieceId());
            if (container == null || !container.isWorkerDepositEnabled()
                    || !container.acceptsItem(itemId)) {
                continue;
            }

            long reservedByOthers =
                    physicalWorkerStorageReservations.getReservedByOthers(
                            workerId, piece.getPieceId(), itemId);
            long available = Math.max(0L,
                    container.getAvailableCapacityForItem(itemId) - reservedByOthers);
            if (available < amount) {
                continue;
            }

            int distance = origin == null ? 0
                    : Math.abs(origin.getX() - live.getX())
                            + Math.abs(origin.getY() - live.getY());
            long score = ((long) container.getLogisticsPriority() * 100000L)
                    + getStorageModeDepositBonus(container.getMode())
                    + getStorageModeDepositPressure(container, itemId)
                    - ((long) distance * 100L)
                    - (reservedByOthers * 2500L);
            if (bestPiece == null || score > bestScore
                    || score == bestScore && piece.getPieceId() < bestPiece.getPieceId()) {
                bestPiece = piece;
                bestScore = score;
            }
        }

        if (bestPiece == null) {
            return false;
        }
        physicalWorkerStorageReservations.reserve(
                workerId, bestPiece.getPieceId(), itemId, amount);
        return true;
    }

    private SettlementPlacedPiece findPieceById(long pieceId) {
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (piece != null && piece.getPieceId() == pieceId) {
                return piece;
            }
        }
        return null;
    }

    private void releaseAllPhysicalStorageReservations() {
        physicalWorkerStorageReservations.clear();
        physicalWorkerSourceReservations.clear();
    }

    private boolean isPhysicalStoragePiece(SettlementPlacedPiece piece) {
        if (piece == null) {
            return false;
        }
        SettlementBuildPiece definition =
                SettlementBuildPiece.forKey(piece.getDefinitionKey());
        return definition != null && definition.getRole() == SettlementBuildRole.STORAGE;
    }

    private WorldObject getPhysicalStorageObject(long pieceId) {
        if (!loaded || destroyed || boundChunks == null || pieceId <= 0L) {
            return null;
        }
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (piece == null || piece.getPieceId() != pieceId || !isPhysicalStoragePiece(piece)) {
                continue;
            }
            SettlementBuildPiece definition =
                    SettlementBuildPiece.forKey(piece.getDefinitionKey());
            WorldTile tile = new WorldTile(
                    toWorldX(piece.getPlotX()), toWorldY(piece.getPlotY()), piece.getPlane());
            WorldObject live = World.getObjectWithType(tile, definition.getObjectType());
            return live != null && live.getId() == definition.getObjectId() ? live : null;
        }
        return null;
    }

    private long getStorageModeDepositBonus(SettlementStorageMode mode) {
        if (mode == null) {
            return 0L;
        }
        switch (mode) {
        case REQUEST:
            return 30000L;
        case BUFFER:
            return 20000L;
        case STORAGE:
            return 10000L;
        case SUPPLY:
        default:
            return 0L;
        }
    }


    private long getStorageModeWithdrawBonus(SettlementStorageMode mode) {
        if (mode == null) {
            return 0L;
        }
        switch (mode) {
        case SUPPLY:
            return 30000L;
        case BUFFER:
            return 20000L;
        case STORAGE:
            return 10000L;
        case REQUEST:
        default:
            return 0L;
        }
    }


    private long getStorageModeDepositPressure(
            SettlementStorageContainer container, int itemId) {
        if (container == null || itemId < 0) {
            return 0L;
        }
        long amount = container.getItemAmount(itemId);
        long free = container.getAvailableCapacityForItem(itemId);
        long total = amount + free;
        if (total <= 0L) {
            return 0L;
        }
        long fillPermille = Math.min(1000L, (amount * 1000L) / total);
        switch (container.getMode()) {
        case REQUEST:
            return (1000L - fillPermille) * 10L;
        case BUFFER:
            return fillPermille < 500L
                    ? (500L - fillPermille) * 12L : 0L;
        default:
            return 0L;
        }
    }

    private long getStorageModeWithdrawPressure(
            SettlementStorageContainer container, int itemId) {
        if (container == null || itemId < 0) {
            return 0L;
        }
        long amount = container.getItemAmount(itemId);
        long free = container.getAvailableCapacityForItem(itemId);
        long total = amount + free;
        if (total <= 0L) {
            return 0L;
        }
        long fillPermille = Math.min(1000L, (amount * 1000L) / total);
        switch (container.getMode()) {
        case SUPPLY:
            return fillPermille * 10L;
        case BUFFER:
            return fillPermille > 500L
                    ? (fillPermille - 500L) * 12L : 0L;
        default:
            return 0L;
        }
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
        physicalWorkerStorageReservations.clear();
        physicalWorkerSourceReservations.clear();
        workerDestinationReservations.clear();
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

    private static final class WorkerPhysicalStorageReservationBook {
        private final Map<Long, WorkerPhysicalStorageReservation> byWorker =
                new HashMap<Long, WorkerPhysicalStorageReservation>();

        private synchronized WorkerPhysicalStorageReservation get(long workerId) {
            return byWorker.get(Long.valueOf(workerId));
        }

        private synchronized long getReservedByOthers(
                long workerId, long pieceId, int itemId) {
            long reserved = 0L;
            for (Map.Entry<Long, WorkerPhysicalStorageReservation> entry
                    : byWorker.entrySet()) {
                WorkerPhysicalStorageReservation value = entry.getValue();
                if (value == null || entry.getKey().longValue() == workerId
                        || value.pieceId != pieceId || value.itemId != itemId) {
                    continue;
                }
                reserved += value.amount;
            }
            return reserved;
        }

        private synchronized void reserve(
                long workerId, long pieceId, int itemId, long amount) {
            byWorker.put(Long.valueOf(workerId),
                    new WorkerPhysicalStorageReservation(pieceId, itemId, amount));
        }

        private synchronized void release(long workerId) {
            byWorker.remove(Long.valueOf(workerId));
        }

        private synchronized void clear() {
            byWorker.clear();
        }
    }

    private static final class WorkerPhysicalStorageReservation {
        private final long pieceId;
        private final int itemId;
        private final long amount;

        private WorkerPhysicalStorageReservation(
                long pieceId, int itemId, long amount) {
            this.pieceId = pieceId;
            this.itemId = itemId;
            this.amount = amount;
        }
    }

    private static final class WorkerDestinationReservation {
        private final int anchorX;
        private final int anchorY;
        private final int plane;
        private final int interactionRange;
        private final boolean centerBlocked;
        private final WorldTile tile;

        private WorkerDestinationReservation(
                WorldTile target, int interactionRange,
                boolean centerBlocked, WorldTile tile) {
            this.anchorX = target.getX();
            this.anchorY = target.getY();
            this.plane = target.getPlane();
            this.interactionRange = interactionRange;
            this.centerBlocked = centerBlocked;
            this.tile = new WorldTile(tile);
        }

        private boolean matches(
                WorldTile target, int interactionRange, boolean centerBlocked) {
            return target != null
                    && anchorX == target.getX()
                    && anchorY == target.getY()
                    && plane == target.getPlane()
                    && this.interactionRange == interactionRange
                    && this.centerBlocked == centerBlocked;
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
        player.getActionbar().endConstructionMode();
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
