package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Derived rail topology for the current persistent settlement layout.
 *
 * V1 deliberately treats every physical RAIL component as a cardinally
 * connectable tile. The accepted three-piece curve is already persisted as
 * three neighboring RAIL pieces, so path ownership remains data-driven.
 */
public final class SettlementRailNetwork {

    private SettlementRailNetwork() {
    }

    public static Route findLoaderToUnloader(SettlementState state) {
        if (state == null) {
            return Route.fail("Settlement state unavailable.");
        }

        List<SettlementPlacedPiece> snapshot = state.snapshotPieces();
        List<SettlementPlacedPiece> loaders = byRole(snapshot, SettlementBuildRole.RAIL_LOADER);
        List<SettlementPlacedPiece> unloaders = byRole(snapshot, SettlementBuildRole.RAIL_UNLOADER);
        if (loaders.isEmpty()) {
            return Route.fail("Place a Rail Loader.");
        }
        if (unloaders.isEmpty()) {
            return Route.fail("Place a Rail Unloader.");
        }

        Map<String, SettlementPlacedPiece> rails = new HashMap<String, SettlementPlacedPiece>();
        for (SettlementPlacedPiece piece : snapshot) {
            SettlementBuildPiece definition = definition(piece);
            if (definition != null && definition.getRole() == SettlementBuildRole.RAIL) {
                rails.put(key(piece.getPlotX(), piece.getPlotY(), piece.getPlane()), piece);
            }
        }
        if (rails.isEmpty()) {
            return Route.fail("No persistent rail pieces are placed.");
        }

        for (SettlementPlacedPiece loader : loaders) {
            List<SettlementPlacedPiece> starts = adjacentRails(loader, rails);
            if (starts.isEmpty()) {
                continue;
            }
            for (SettlementPlacedPiece unloader : unloaders) {
                Set<String> goals = new HashSet<String>();
                for (SettlementPlacedPiece rail : adjacentRails(unloader, rails)) {
                    goals.add(key(rail.getPlotX(), rail.getPlotY(), rail.getPlane()));
                }
                if (goals.isEmpty()) {
                    continue;
                }
                List<SettlementPlacedPiece> path = breadthFirst(starts, goals, rails);
                if (!path.isEmpty()) {
                    return Route.success(loader, unloader, path);
                }
            }
        }
        return Route.fail("Loader and Unloader are not connected by cardinal rail tiles.");
    }

    private static List<SettlementPlacedPiece> breadthFirst(
            List<SettlementPlacedPiece> starts, Set<String> goals,
            Map<String, SettlementPlacedPiece> rails) {
        Queue<SettlementPlacedPiece> open = new LinkedList<SettlementPlacedPiece>();
        Map<String, String> previous = new HashMap<String, String>();
        Set<String> visited = new HashSet<String>();

        for (SettlementPlacedPiece start : starts) {
            String startKey = key(start.getPlotX(), start.getPlotY(), start.getPlane());
            open.add(start);
            visited.add(startKey);
            previous.put(startKey, null);
        }

        String found = null;
        while (!open.isEmpty()) {
            SettlementPlacedPiece current = open.remove();
            String currentKey = key(current.getPlotX(), current.getPlotY(), current.getPlane());
            if (goals.contains(currentKey)) {
                found = currentKey;
                break;
            }
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                String nextKey = key(current.getPlotX() + offset[0],
                        current.getPlotY() + offset[1], current.getPlane());
                SettlementPlacedPiece next = rails.get(nextKey);
                if (next != null && visited.add(nextKey)) {
                    previous.put(nextKey, currentKey);
                    open.add(next);
                }
            }
        }

        if (found == null) {
            return Collections.emptyList();
        }
        List<SettlementPlacedPiece> reversed = new ArrayList<SettlementPlacedPiece>();
        String cursor = found;
        while (cursor != null) {
            SettlementPlacedPiece piece = rails.get(cursor);
            if (piece != null) {
                reversed.add(piece);
            }
            cursor = previous.get(cursor);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static List<SettlementPlacedPiece> adjacentRails(
            SettlementPlacedPiece endpoint, Map<String, SettlementPlacedPiece> rails) {
        List<SettlementPlacedPiece> result = new ArrayList<SettlementPlacedPiece>();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            SettlementPlacedPiece rail = rails.get(key(endpoint.getPlotX() + offset[0],
                    endpoint.getPlotY() + offset[1], endpoint.getPlane()));
            if (rail != null) {
                result.add(rail);
            }
        }
        return result;
    }

    private static List<SettlementPlacedPiece> byRole(
            List<SettlementPlacedPiece> pieces, SettlementBuildRole role) {
        List<SettlementPlacedPiece> result = new ArrayList<SettlementPlacedPiece>();
        for (SettlementPlacedPiece piece : pieces) {
            SettlementBuildPiece definition = definition(piece);
            if (definition != null && definition.getRole() == role) {
                result.add(piece);
            }
        }
        return result;
    }

    private static SettlementBuildPiece definition(SettlementPlacedPiece piece) {
        return piece == null ? null : SettlementBuildPiece.forKey(piece.getDefinitionKey());
    }

    private static String key(int x, int y, int plane) {
        return x + ":" + y + ":" + plane;
    }

    public static final class Route {
        private final SettlementPlacedPiece loader;
        private final SettlementPlacedPiece unloader;
        private final List<SettlementPlacedPiece> rails;
        private final String failure;

        private Route(SettlementPlacedPiece loader, SettlementPlacedPiece unloader,
                List<SettlementPlacedPiece> rails, String failure) {
            this.loader = loader;
            this.unloader = unloader;
            this.rails = rails;
            this.failure = failure;
        }

        private static Route success(SettlementPlacedPiece loader,
                SettlementPlacedPiece unloader, List<SettlementPlacedPiece> rails) {
            return new Route(loader, unloader,
                    new ArrayList<SettlementPlacedPiece>(rails), null);
        }

        private static Route fail(String failure) {
            return new Route(null, null, Collections.<SettlementPlacedPiece>emptyList(), failure);
        }

        public boolean isValid() { return failure == null; }
        public String getFailure() { return failure; }
        public SettlementPlacedPiece getLoader() { return loader; }
        public SettlementPlacedPiece getUnloader() { return unloader; }
        public List<SettlementPlacedPiece> getRails() {
            return new ArrayList<SettlementPlacedPiece>(rails);
        }
    }
}
