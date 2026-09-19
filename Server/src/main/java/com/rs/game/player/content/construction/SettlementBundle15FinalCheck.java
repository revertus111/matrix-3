package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.rs.game.player.Player;
import com.rs.game.player.Skills;

/**
 * Bundle 1.5 disposable transaction self-test plus process-local persistence gate.
 *
 * The self-test never reads/mutates a Player save. Baseline capture/check reads
 * real player state only and stores the comparison snapshot in process memory.
 */
public final class SettlementBundle15FinalCheck {

    private static final Map<String, Snapshot> SNAPSHOTS =
            new ConcurrentHashMap<String, Snapshot>();

    private SettlementBundle15FinalCheck() {
    }

    public static String runSelfTest() {
        String stage = "dependencies";
        try {
            requirePass(SettlementStateSelfTest.run(), "state self-test");
            requirePass(SettlementResourceSelfTest.run(), "resource self-test");
            requirePass(SettlementWorkerSelfTest.run(), "worker self-test");

            stage = "definitions";
            requireDefinition(SettlementBuildPiece.WOOD_FENCE_TEST, 1L, 4.0);
            requireDefinition(SettlementBuildPiece.FLOOR_DECORATION, 1L, 4.0);
            requireDefinition(SettlementBuildPiece.BASIC_DOOR, 2L, 8.0);

            SettlementState state = new SettlementState();
            state.normalize();

            stage = "insufficient";
            SettlementPlayerBuildTransaction.Result insufficient =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.WOOD_FENCE_TEST,
                            30, 30, SettlementState.PLOT_PLANE, 0, false);
            require(!insufficient.isSuccess(), "zero-Wood placement succeeded");
            require(state.size() == 0, "insufficient placement mutated pieces");
            require(state.getResourceAmount(SettlementResource.WOOD) == 0L,
                    "insufficient placement mutated Wood");

            require(state.addResource(SettlementResource.WOOD, 4L) == 4L,
                    "failed to seed disposable Wood");

            stage = "reserved";
            SettlementPlayerBuildTransaction.Result reserved =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.WOOD_FENCE_TEST,
                            SettlementResourceNode.WOOD_TREE.getPlotX(),
                            SettlementResourceNode.WOOD_TREE.getPlotY(),
                            SettlementState.PLOT_PLANE, 0, true);
            require(!reserved.isSuccess(), "reserved placement succeeded");
            require(state.size() == 0, "reserved placement mutated pieces");
            require(state.getResourceAmount(SettlementResource.WOOD) == 4L,
                    "reserved placement consumed Wood");

            stage = "invalid";
            SettlementPlayerBuildTransaction.Result invalid =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.WOOD_FENCE_TEST,
                            -1, 30, SettlementState.PLOT_PLANE, 0, false);
            require(!invalid.isSuccess(), "invalid plot placement succeeded");
            require(state.size() == 0, "invalid placement mutated pieces");
            require(state.getResourceAmount(SettlementResource.WOOD) == 4L,
                    "invalid placement consumed Wood");

            stage = "wall";
            SettlementPlayerBuildTransaction.Result wall =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.WOOD_FENCE_TEST,
                            30, 30, SettlementState.PLOT_PLANE, 0, false);
            require(wall.isSuccess(), "valid wall transaction failed");
            require(wall.getCost() == 1L && wall.getConstructionXp() == 4.0,
                    "wall transaction cost/xp mismatch");
            require(state.size() == 1, "wall transaction piece count mismatch");
            require(state.getResourceAmount(SettlementResource.WOOD) == 3L,
                    "wall transaction Wood mismatch");

            stage = "occupied";
            SettlementPlayerBuildTransaction.Result occupied =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.BASIC_DOOR,
                            30, 30, SettlementState.PLOT_PLANE, 0, false);
            require(!occupied.isSuccess(), "occupied same-layer placement succeeded");
            require(state.size() == 1, "occupied placement mutated pieces");
            require(state.getResourceAmount(SettlementResource.WOOD) == 3L,
                    "occupied placement consumed Wood");

            stage = "floor";
            SettlementPlayerBuildTransaction.Result floor =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.FLOOR_DECORATION,
                            30, 30, SettlementState.PLOT_PLANE, 0, false);
            require(floor.isSuccess(), "compatible floor transaction failed");
            require(floor.getCost() == 1L && floor.getConstructionXp() == 4.0,
                    "floor transaction cost/xp mismatch");
            require(state.size() == 2, "floor transaction piece count mismatch");
            require(state.getResourceAmount(SettlementResource.WOOD) == 2L,
                    "floor transaction Wood mismatch");

            stage = "door";
            SettlementPlayerBuildTransaction.Result door =
                    SettlementPlayerBuildTransaction.apply(
                            state, SettlementBuildPiece.BASIC_DOOR,
                            31, 30, SettlementState.PLOT_PLANE, 0, false);
            require(door.isSuccess(), "valid door transaction failed");
            require(door.getCost() == 2L && door.getConstructionXp() == 8.0,
                    "door transaction cost/xp mismatch");
            require(state.size() == 3, "door transaction piece count mismatch");
            require(state.getResourceAmount(SettlementResource.WOOD) == 0L,
                    "door transaction Wood mismatch");

            return "PASS: exact build costs + insufficient/reserved/invalid/occupied no-mutation + transaction ownership.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
        }
    }

    public static String capture(Player player) {
        if (player == null) {
            return "FAIL: player unavailable.";
        }
        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the loaded settlement first.";
        }

        SettlementWorkerState worker = player.getSettlementState().getStarterWorker();
        if (worker != null) {
            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                if (worker.isJobAllowed(job)) {
                    return "NOT READY: disable all Worker #1 jobs before capturing the build baseline.";
                }
            }
            if (worker.needsFood() || worker.needsWater() || worker.needsRest()) {
                return "NOT READY: reset/recover Worker #1 needs before capturing the build baseline.";
            }
        }

        String selfTest = runSelfTest();
        if (selfTest == null || !selfTest.startsWith("PASS:")) {
            return "FAIL: Bundle 1.5 self-test -> " + selfTest;
        }

        Snapshot snapshot = Snapshot.capture(player);
        SNAPSHOTS.put(key(player), snapshot);
        return "BASELINE SAVED: pieces=" + snapshot.pieces.size()
                + " | " + resourceSummary(snapshot.resources)
                + " | Construction XP=" + snapshot.constructionXp + ".";
    }

    public static String check(Player player) {
        if (player == null) {
            return "FAIL: player unavailable.";
        }
        Snapshot baseline = SNAPSHOTS.get(key(player));
        if (baseline == null) {
            return "NOT READY: no Bundle 1.5 baseline exists for this login; capture one first.";
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: re-enter the loaded settlement before checking the baseline.";
        }

        Snapshot current = Snapshot.capture(player);
        StringBuilder mismatch = new StringBuilder();

        if (!current.pieces.equals(baseline.pieces)) {
            addMismatch(mismatch, "saved build layout changed");
        }
        if (!current.resources.equals(baseline.resources)) {
            addMismatch(mismatch, "settlement resource totals changed");
        }
        if (current.constructionXp != baseline.constructionXp) {
            addMismatch(mismatch, "Construction XP " + current.constructionXp
                    + " != " + baseline.constructionXp);
        }

        if (mismatch.length() > 0) {
            return "FAIL: " + mismatch.toString() + ".";
        }
        return "PASS: exact build layout + settlement resource totals + Construction XP survived exit/re-entry; pieces="
                + current.pieces.size() + " | " + resourceSummary(current.resources)
                + " | Construction XP=" + current.constructionXp + ".";
    }

    private static void requireDefinition(SettlementBuildPiece piece,
            long cost, double xp) {
        require(piece != null, "missing build definition");
        require(piece.getBuildResource() == SettlementResource.WOOD,
                piece.getKey() + " does not use Wood");
        require(piece.getBuildCost() == cost,
                piece.getKey() + " cost mismatch");
        require(piece.getConstructionXp() == xp,
                piece.getKey() + " XP mismatch");
    }

    private static void requirePass(String result, String label) {
        require(result != null && result.startsWith("PASS:"),
                label + " -> " + result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void addMismatch(StringBuilder mismatch, String value) {
        if (mismatch.length() > 0) {
            mismatch.append("; ");
        }
        mismatch.append(value);
    }

    private static String key(Player player) {
        String username = player.getUsername();
        return username == null ? "" : username.toLowerCase();
    }

    private static String resourceSummary(Map<String, Long> resources) {
        StringBuilder summary = new StringBuilder();
        for (SettlementResource resource : SettlementResource.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            Long amount = resources.get(resource.getKey());
            summary.append(resource.getDisplayName()).append("=")
                    .append(amount == null ? 0L : amount.longValue());
        }
        return summary.toString();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private static final class Snapshot {
        private final List<String> pieces;
        private final Map<String, Long> resources;
        private final long constructionXp;

        private Snapshot(List<String> pieces, Map<String, Long> resources,
                long constructionXp) {
            this.pieces = pieces;
            this.resources = resources;
            this.constructionXp = constructionXp;
        }

        private static Snapshot capture(Player player) {
            SettlementState state = player.getSettlementState();

            List<String> pieces = new ArrayList<String>();
            for (SettlementPlacedPiece piece : state.snapshotPieces()) {
                if (piece == null) {
                    continue;
                }
                pieces.add(piece.getPieceId() + "|" + piece.getDefinitionKey()
                        + "|" + piece.getPlotX() + "|" + piece.getPlotY()
                        + "|" + piece.getPlane() + "|" + piece.getRotation());
            }
            Collections.sort(pieces);

            Map<String, Long> resources = new HashMap<String, Long>();
            for (SettlementResource resource : SettlementResource.values()) {
                resources.put(resource.getKey(),
                        Long.valueOf(state.getResourceAmount(resource)));
            }

            return new Snapshot(
                    pieces,
                    resources,
                    (long) player.getSkills().getXp(Skills.CONSTRUCTION));
        }
    }
}
