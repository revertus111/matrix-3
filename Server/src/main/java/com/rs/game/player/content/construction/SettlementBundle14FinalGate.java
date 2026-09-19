package com.rs.game.player.content.construction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.rs.game.player.Player;
import com.rs.game.player.Skills;

/**
 * Developer-only runtime checkpoint for the final Bundle 1.4 persistence gate.
 *
 * This helper never mutates player or settlement state. It stores only a
 * process-local comparison snapshot keyed by username so the same baseline can
 * be checked before/after settlement rebuild and normal logout/relog.
 */
public final class SettlementBundle14FinalGate {

    private static final Map<String, Snapshot> SNAPSHOTS =
            new ConcurrentHashMap<String, Snapshot>();

    private SettlementBundle14FinalGate() {
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
        if (worker == null) {
            return "NOT READY: Worker #1 does not exist.";
        }
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (worker.isJobAllowed(job)) {
                return "NOT READY: disable all Worker #1 jobs before capturing the stable baseline.";
            }
        }
        if (worker.needsFood() || worker.needsWater() || worker.needsRest()) {
            return "NOT READY: let Worker #1 recover or Reset Needs before capturing the baseline.";
        }

        Snapshot snapshot = Snapshot.capture(player, worker);
        SNAPSHOTS.put(key(player), snapshot);
        return "BASELINE SAVED: Worker #" + worker.getWorkerId()
                + " | " + worker.getNeedsSummary()
                + " | " + worker.getSkillsSummary()
                + " | Construction XP=" + snapshot.constructionXp + ".";
    }

    public static String check(Player player) {
        if (player == null) {
            return "FAIL: player unavailable.";
        }
        Snapshot baseline = SNAPSHOTS.get(key(player));
        if (baseline == null) {
            return "NOT READY: no Bundle 1.4 baseline exists for this login; capture one first.";
        }

        SettlementWorkerState worker = player.getSettlementState().getStarterWorker();
        if (worker == null) {
            return "FAIL: Worker #1 is missing after the baseline.";
        }

        StringBuilder mismatch = new StringBuilder();
        if (worker.getWorkerId() != baseline.workerId) {
            addMismatch(mismatch, "worker id " + worker.getWorkerId()
                    + " != " + baseline.workerId);
        }
        if (!safeEquals(worker.getDefinitionKey(), baseline.definitionKey)) {
            addMismatch(mismatch, "definition changed");
        }
        if (!worker.snapshotAllowedJobKeys().equals(baseline.allowedJobs)) {
            addMismatch(mismatch, "Allowed Jobs changed");
        }

        checkNeed(worker, SettlementWorkerNeed.HUNGER, baseline.hunger, mismatch);
        checkNeed(worker, SettlementWorkerNeed.THIRST, baseline.thirst, mismatch);
        checkNeed(worker, SettlementWorkerNeed.ENERGY, baseline.energy, mismatch);

        for (SettlementWorkerSkill skill : SettlementWorkerSkill.values()) {
            Long expected = baseline.skillXp.get(skill.getKey());
            long expectedXp = expected == null ? 0L : expected.longValue();
            long actualXp = worker.getSkillXp(skill);
            if (actualXp != expectedXp) {
                addMismatch(mismatch, skill.getDisplayName() + " XP "
                        + actualXp + " != " + expectedXp);
            }
        }

        long constructionXp = (long) player.getSkills().getXp(Skills.CONSTRUCTION);
        if (constructionXp != baseline.constructionXp) {
            addMismatch(mismatch, "Construction XP " + constructionXp
                    + " != " + baseline.constructionXp);
        }

        if (mismatch.length() > 0) {
            return "FAIL: " + mismatch.toString() + ".";
        }
        return "PASS: baseline unchanged | Worker #" + worker.getWorkerId()
                + " | " + worker.getNeedsSummary()
                + " | " + worker.getSkillsSummary()
                + " | Construction XP=" + constructionXp + ".";
    }

    private static void checkNeed(SettlementWorkerState worker, SettlementWorkerNeed need,
            int expected, StringBuilder mismatch) {
        int actual = worker.getNeed(need);
        if (actual != expected) {
            addMismatch(mismatch, need.getDisplayName() + " " + actual + " != " + expected);
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

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static final class Snapshot {
        private final long workerId;
        private final String definitionKey;
        private final Set<String> allowedJobs;
        private final int hunger;
        private final int thirst;
        private final int energy;
        private final Map<String, Long> skillXp;
        private final long constructionXp;

        private Snapshot(long workerId, String definitionKey, Set<String> allowedJobs,
                int hunger, int thirst, int energy, Map<String, Long> skillXp,
                long constructionXp) {
            this.workerId = workerId;
            this.definitionKey = definitionKey;
            this.allowedJobs = allowedJobs;
            this.hunger = hunger;
            this.thirst = thirst;
            this.energy = energy;
            this.skillXp = skillXp;
            this.constructionXp = constructionXp;
        }

        private static Snapshot capture(Player player, SettlementWorkerState worker) {
            Map<String, Long> skillXp = new HashMap<String, Long>();
            for (SettlementWorkerSkill skill : SettlementWorkerSkill.values()) {
                skillXp.put(skill.getKey(), Long.valueOf(worker.getSkillXp(skill)));
            }
            return new Snapshot(
                    worker.getWorkerId(),
                    worker.getDefinitionKey(),
                    new HashSet<String>(worker.snapshotAllowedJobKeys()),
                    worker.getNeed(SettlementWorkerNeed.HUNGER),
                    worker.getNeed(SettlementWorkerNeed.THIRST),
                    worker.getNeed(SettlementWorkerNeed.ENERGY),
                    skillXp,
                    (long) player.getSkills().getXp(Skills.CONSTRUCTION));
        }
    }
}
