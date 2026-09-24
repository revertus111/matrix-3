package com.rs.game.player.content.construction;

/**
 * Authoritative state transaction for one player Construction placement.
 *
 * Runtime projection and player XP remain SettlementInstance responsibilities.
 * This class owns only the persistent placement + settlement-material transaction
 * so the exact gameplay mutation can be exercised by a disposable self-test.
 */
public final class SettlementPlayerBuildTransaction {

    private SettlementPlayerBuildTransaction() {
    }

    public static Result apply(SettlementState state, SettlementBuildPiece definition,
            int plotX, int plotY, int plane, int rotation, boolean reservedInfrastructure) {
        if (state == null) {
            return Result.fail("Settlement state is unavailable.");
        }
        if (definition == null) {
            return Result.fail("That is not an approved settlement build piece.");
        }
        if (rotation < 0 || rotation > 3) {
            return Result.fail("Construction rotation must be 0-3.");
        }
        if (!SettlementState.isValidPlotLocation(plotX, plotY, plane)) {
            return Result.fail("That tile is outside the active settlement plot.");
        }
        if (reservedInfrastructure) {
            return Result.fail("That tile is reserved for settlement infrastructure.");
        }

        if (definition.getRole() == SettlementBuildRole.BED && !state.canAddHousingBed()) {
            return Result.fail("Complete the starter shelter and free housing capacity before placing another bed.");
        }

        SettlementResource resource = definition.getBuildResource();
        long cost = definition.getBuildCost();
        boolean freePlacement = definition.getRole() == SettlementBuildRole.RAIL
                && resource == null && cost == 0L;
        if (!freePlacement && (resource == null || cost <= 0L)) {
            return Result.fail("That build piece has no valid material cost.");
        }

        synchronized (state) {
            if (freePlacement) {
                SettlementPlacedPiece saved = state.place(
                        definition, plotX, plotY, plane, rotation);
                if (saved == null) {
                    return Result.fail("That settlement slot is already occupied.");
                }
                return Result.success(saved, null, 0L, definition.getConstructionXp());
            }

            if (state.getResourceAmount(resource) < cost) {
                return Result.fail("You need " + cost + " " + resource.getDisplayName()
                        + " in settlement storage to build " + definition.getDisplayName() + ".");
            }

            SettlementPlacedPiece saved = state.place(
                    definition, plotX, plotY, plane, rotation);
            if (saved == null) {
                return Result.fail("That settlement slot is already occupied.");
            }

            long consumed = state.removeResource(resource, cost);
            if (consumed != cost) {
                state.remove(saved.getPieceId());
                if (consumed > 0L) {
                    state.addResource(resource, consumed);
                }
                return Result.fail("Settlement materials changed before placement could complete.");
            }

            return Result.success(saved, resource, cost, definition.getConstructionXp());
        }
    }

    public static final class Result {
        private final boolean success;
        private final String message;
        private final SettlementPlacedPiece placedPiece;
        private final SettlementResource resource;
        private final long cost;
        private final double constructionXp;

        private Result(boolean success, String message, SettlementPlacedPiece placedPiece,
                SettlementResource resource, long cost, double constructionXp) {
            this.success = success;
            this.message = message;
            this.placedPiece = placedPiece;
            this.resource = resource;
            this.cost = cost;
            this.constructionXp = constructionXp;
        }

        private static Result fail(String message) {
            return new Result(false, message, null, null, 0L, 0.0);
        }

        private static Result success(SettlementPlacedPiece placedPiece,
                SettlementResource resource, long cost, double constructionXp) {
            return new Result(true, null, placedPiece, resource, cost, constructionXp);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public SettlementPlacedPiece getPlacedPiece() {
            return placedPiece;
        }

        public SettlementResource getResource() {
            return resource;
        }

        public long getCost() {
            return cost;
        }

        public double getConstructionXp() {
            return constructionXp;
        }
    }
}
