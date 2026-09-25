package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.rs.game.player.Player;
import com.rs.game.player.Skills;

/**
 * Bundle 2.2 multi-worker targeting + persistence final gate.
 *
 * The disposable self-test exercises the real per-worker state owners. Live
 * capture/check is read-only and process-local; it never adds fields to Player
 * or SettlementState.
 */
public final class SettlementBundle22FinalGate {

    private static final Map<String, Snapshot> SNAPSHOTS =
            new ConcurrentHashMap<String, Snapshot>();

    private SettlementBundle22FinalGate() {
    }

    public static String runSelfTest() {
        String stage = "population";
        try {
            String resources = SettlementResourceSelfTest.run();
            require(resources != null && resources.startsWith("PASS:"),
                    "resource-storage dependency -> " + resources);

            String reservations = SettlementInstance.runWorkerStorageReservationSelfTest();
            require(reservations != null && reservations.startsWith("PASS:"),
                    "storage-reservation dependency -> " + reservations);

            String population = SettlementPopulationCheck.runSelfTest();
            require(population != null && population.startsWith("PASS:"),
                    "population dependency -> " + population);

            SettlementState state = createTwoWorkerState();
            List<SettlementWorkerState> workers = state.snapshotWorkers();
            require(workers.size() == 2, "expected two disposable workers");

            SettlementWorkerState first = state.getStarterWorker();
            SettlementWorkerState second = null;
            for (SettlementWorkerState worker : workers) {
                if (worker != null && worker != first) {
                    second = worker;
                    break;
                }
            }
            require(first != null && second != null, "worker identities missing");
            require(first.getWorkerId() != second.getWorkerId(),
                    "worker ids are not unique");

            stage = "targeting";
            first.setPaused(true);
            second.setPaused(false);

            for (SettlementWorkerRolePreset preset :
                    SettlementWorkerRolePreset.values()) {
                preset.applyTo(second);
                require(SettlementWorkerRolePreset.findMatching(second) == preset,
                        "preset round-trip failed for " + preset.getKey());
            }

            SettlementWorkerRolePreset.FORAGER.applyTo(first);
            SettlementWorkerRolePreset.LUMBERJACK.applyTo(second);

            require(first.isPaused() && !second.isPaused(),
                    "role preset changed independent pause state");
            require(SettlementWorkerRolePreset.findMatching(first)
                    == SettlementWorkerRolePreset.FORAGER,
                    "Worker #1 Forager preset did not match");
            require(SettlementWorkerRolePreset.findMatching(second)
                    == SettlementWorkerRolePreset.LUMBERJACK,
                    "Worker #2 Lumberjack preset did not match");

            require(first.isJobAllowed(SettlementWorkerJob.GATHER_FOOD)
                    && first.isJobAllowed(SettlementWorkerJob.HAUL),
                    "Worker #1 Forager jobs were not enabled");
            require(!first.isJobAllowed(SettlementWorkerJob.GATHER_WOOD)
                    && !first.isJobAllowed(SettlementWorkerJob.GATHER_STONE)
                    && !first.isJobAllowed(SettlementWorkerJob.GATHER_BASIC_ORE),
                    "Worker #1 Forager preset left unrelated gather jobs enabled");
            require(second.isJobAllowed(SettlementWorkerJob.GATHER_WOOD)
                    && second.isJobAllowed(SettlementWorkerJob.HAUL),
                    "Worker #2 Lumberjack jobs were not enabled");
            require(!second.isJobAllowed(SettlementWorkerJob.GATHER_FOOD)
                    && !second.isJobAllowed(SettlementWorkerJob.GATHER_STONE)
                    && !second.isJobAllowed(SettlementWorkerJob.GATHER_BASIC_ORE),
                    "Worker #2 Lumberjack preset left unrelated gather jobs enabled");

            first.setNeed(SettlementWorkerNeed.HUNGER, 33);
            second.setNeed(SettlementWorkerNeed.HUNGER, 11);
            require(first.getNeed(SettlementWorkerNeed.HUNGER) == 33,
                    "Worker #1 need targeting failed");
            require(second.getNeed(SettlementWorkerNeed.HUNGER) == 11,
                    "Worker #2 need targeting failed");

            first.addSkillXp(SettlementWorkerSkill.FOOD_GATHERING, 12L);
            second.addSkillXp(SettlementWorkerSkill.WOODCUTTING, 24L);
            require(first.getSkillXp(SettlementWorkerSkill.FOOD_GATHERING) == 12L,
                    "Worker #1 progression targeting failed");
            require(second.getSkillXp(SettlementWorkerSkill.WOODCUTTING) == 24L,
                    "Worker #2 progression targeting failed");

            stage = "serialize";
            SettlementState restored = deserialize(serialize(state));
            restored.normalize();
            SettlementWorkerState restoredFirst = restored.findWorker(first.getWorkerId());
            SettlementWorkerState restoredSecond = restored.findWorker(second.getWorkerId());
            require(restoredFirst != null && restoredSecond != null,
                    "target workers missing after serialization");
            require(restoredFirst.isJobAllowed(SettlementWorkerJob.GATHER_FOOD)
                    && restoredFirst.isJobAllowed(SettlementWorkerJob.HAUL)
                    && !restoredFirst.isJobAllowed(SettlementWorkerJob.GATHER_WOOD),
                    "Worker #1 job policy changed after serialization");
            require(restoredSecond.isJobAllowed(SettlementWorkerJob.GATHER_WOOD)
                    && restoredSecond.isJobAllowed(SettlementWorkerJob.HAUL)
                    && !restoredSecond.isJobAllowed(SettlementWorkerJob.GATHER_FOOD),
                    "Worker #2 job policy changed after serialization");
            require(restoredFirst.isPaused() && !restoredSecond.isPaused(),
                    "independent pause state changed after serialization");
            require(restoredFirst.getNeed(SettlementWorkerNeed.HUNGER) == 33
                    && restoredSecond.getNeed(SettlementWorkerNeed.HUNGER) == 11,
                    "independent needs changed after serialization");
            require(restoredFirst.getSkillXp(SettlementWorkerSkill.FOOD_GATHERING) == 12L
                    && restoredSecond.getSkillXp(SettlementWorkerSkill.WOODCUTTING) == 24L,
                    "independent progression changed after serialization");

            return "PASS: per-resource storage isolation + in-flight reservation race protection + all worker role presets + worker-id targeting + independent pause/jobs/needs/progression + two-worker serialization.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
        }
    }

    public static String prepareAndCapture(Player player) {
        if (player == null) {
            return "FAIL: player unavailable.";
        }
        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the loaded settlement first.";
        }

        SettlementState state = player.getSettlementState();
        if (state.getWorkerCount() != 2 || active.getActiveWorkerCount() != 2) {
            return "NOT READY: Bundle 2.2 baseline requires saved=2/runtime=2.";
        }

        for (SettlementWorkerState worker : state.snapshotWorkers()) {
            if (worker == null) {
                continue;
            }
            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                worker.setJobAllowed(job, false);
            }
            worker.resetNeeds();
        }

        String result = capture(player);
        if (result != null && result.startsWith("BASELINE SAVED:")) {
            return "PREPARED + " + result;
        }
        return result;
    }

    public static String capture(Player player) {
        if (player == null) {
            return "FAIL: player unavailable.";
        }
        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the loaded settlement first.";
        }

        SettlementState state = player.getSettlementState();
        if (state.getWorkerCount() != 2 || active.getActiveWorkerCount() != 2) {
            return "NOT READY: Bundle 2.2 baseline requires saved=2/runtime=2.";
        }
        for (SettlementWorkerState worker : state.snapshotWorkers()) {
            if (worker == null) {
                continue;
            }
            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                if (worker.isJobAllowed(job)) {
                    return "NOT READY: disable all jobs for Worker #"
                            + worker.getWorkerId() + " before capture.";
                }
            }
            if (worker.needsFood() || worker.needsWater() || worker.needsRest()) {
                return "NOT READY: reset critical needs for Worker #"
                        + worker.getWorkerId() + " before capture.";
            }
        }

        String selfTest = runSelfTest();
        if (selfTest == null || !selfTest.startsWith("PASS:")) {
            return "FAIL: Bundle 2.2 self-test -> " + selfTest;
        }

        Snapshot snapshot = Snapshot.capture(player);
        SNAPSHOTS.put(key(player), snapshot);
        return "BASELINE SAVED: workers=2 | Construction XP="
                + snapshot.constructionXp + " | " + snapshot.workerIds + ".";
    }

    public static String check(Player player) {
        if (player == null) {
            return "FAIL: player unavailable.";
        }
        Snapshot baseline = SNAPSHOTS.get(key(player));
        if (baseline == null) {
            return "NOT READY: no Bundle 2.2 baseline exists for this login.";
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if (active == null || !active.isLoaded()) {
            return "NOT READY: enter the loaded settlement before checking.";
        }
        if (active.getActiveWorkerCount() != 2) {
            return "FAIL: runtime worker count is "
                    + active.getActiveWorkerCount() + ", expected 2.";
        }

        Snapshot current = Snapshot.capture(player);
        StringBuilder mismatch = new StringBuilder();
        if (!current.workerIds.equals(baseline.workerIds)) {
            addMismatch(mismatch, "worker ids changed");
        }
        if (!current.workerDefinitions.equals(baseline.workerDefinitions)) {
            addMismatch(mismatch, "worker definitions changed");
        }
        if (!current.workerPaused.equals(baseline.workerPaused)) {
            addMismatch(mismatch, "worker pause state changed");
        }
        if (!current.workerJobs.equals(baseline.workerJobs)) {
            addMismatch(mismatch, "Allowed Jobs changed");
        }
        if (!current.workerNeeds.equals(baseline.workerNeeds)) {
            addMismatch(mismatch, "worker needs changed");
        }
        if (!current.workerSkills.equals(baseline.workerSkills)) {
            addMismatch(mismatch, "worker progression changed");
        }
        if (current.constructionXp != baseline.constructionXp) {
            addMismatch(mismatch, "Construction XP changed");
        }

        if (mismatch.length() > 0) {
            return "FAIL: " + mismatch.toString() + ".";
        }
        return "PASS: two-worker identities + pause + jobs + needs + progression + Construction XP survived rebuild; saved=2/runtime=2.";
    }

    private static SettlementState createTwoWorkerState() {
        SettlementState state = new SettlementState();
        state.normalize();
        for (int index = 0; index < SettlementState.STARTER_SHELTER_WALLS; index++) {
            require(state.place(SettlementBuildPiece.WOOD_FENCE_TEST,
                    30 + index, 30, SettlementState.PLOT_PLANE, index & 0x3) != null,
                    "failed shelter wall");
        }
        for (int index = 0; index < SettlementState.STARTER_SHELTER_FLOORS; index++) {
            require(state.place(SettlementBuildPiece.FLOOR_DECORATION,
                    30 + index, 30, SettlementState.PLOT_PLANE, 0) != null,
                    "failed shelter floor");
        }
        require(state.place(SettlementBuildPiece.BASIC_DOOR,
                34, 30, SettlementState.PLOT_PLANE, 0) != null,
                "failed shelter door");
        for (SettlementResource resource : SettlementResource.values()) {
            if (!resource.isStarterResource()) {
                continue;
            }
            require(state.addResource(resource,
                    SettlementState.STARTER_SHELTER_RESOURCE_EACH)
                    == SettlementState.STARTER_SHELTER_RESOURCE_EACH,
                    "failed shelter resource");
        }
        require(state.tryCompleteStarterShelterMilestone(),
                "starter shelter milestone failed");
        require(state.ensureStarterWorker() != null,
                "starter worker failed");
        require(state.recruitAdditionalWorker() != null,
                "Worker #2 recruitment failed");
        return state;
    }

    private static byte[] serialize(SettlementState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        try {
            output.writeObject(state);
            output.flush();
            return bytes.toByteArray();
        } finally {
            output.close();
        }
    }

    private static SettlementState deserialize(byte[] encoded) throws Exception {
        ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(encoded));
        try {
            return (SettlementState) input.readObject();
        } finally {
            input.close();
        }
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

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private static final class Snapshot {
        private final List<Long> workerIds;
        private final Map<Long, String> workerDefinitions;
        private final Map<Long, Boolean> workerPaused;
        private final Map<Long, Set<String>> workerJobs;
        private final Map<Long, String> workerNeeds;
        private final Map<Long, Map<String, Long>> workerSkills;
        private final long constructionXp;

        private Snapshot(List<Long> workerIds,
                Map<Long, String> workerDefinitions,
                Map<Long, Boolean> workerPaused,
                Map<Long, Set<String>> workerJobs,
                Map<Long, String> workerNeeds,
                Map<Long, Map<String, Long>> workerSkills,
                long constructionXp) {
            this.workerIds = workerIds;
            this.workerDefinitions = workerDefinitions;
            this.workerPaused = workerPaused;
            this.workerJobs = workerJobs;
            this.workerNeeds = workerNeeds;
            this.workerSkills = workerSkills;
            this.constructionXp = constructionXp;
        }

        private static Snapshot capture(Player player) {
            List<Long> ids = new ArrayList<Long>();
            Map<Long, String> definitions = new HashMap<Long, String>();
            Map<Long, Boolean> paused = new HashMap<Long, Boolean>();
            Map<Long, Set<String>> jobs = new HashMap<Long, Set<String>>();
            Map<Long, String> needs = new HashMap<Long, String>();
            Map<Long, Map<String, Long>> skills =
                    new HashMap<Long, Map<String, Long>>();

            for (SettlementWorkerState worker :
                    player.getSettlementState().snapshotWorkers()) {
                if (worker == null) {
                    continue;
                }
                Long id = Long.valueOf(worker.getWorkerId());
                ids.add(id);
                definitions.put(id, worker.getDefinitionKey());
                paused.put(id, Boolean.valueOf(worker.isPaused()));
                jobs.put(id, new HashSet<String>(worker.snapshotAllowedJobKeys()));
                needs.put(id, worker.getNeedsSummary());

                Map<String, Long> skillXp = new HashMap<String, Long>();
                for (SettlementWorkerSkill skill : SettlementWorkerSkill.values()) {
                    skillXp.put(skill.getKey(),
                            Long.valueOf(worker.getSkillXp(skill)));
                }
                skills.put(id, skillXp);
            }
            Collections.sort(ids);
            return new Snapshot(
                    ids,
                    definitions,
                    paused,
                    jobs,
                    needs,
                    skills,
                    (long) player.getSkills().getXp(Skills.CONSTRUCTION));
        }
    }
}
