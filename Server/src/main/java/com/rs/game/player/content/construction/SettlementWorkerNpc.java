package com.rs.game.player.content.construction;

import com.rs.game.Animation;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;

/**
 * Transient Matrix3 NPC projection for one persistent settlement worker.
 *
 * Persistent policy/needs remain in SettlementWorkerState. This runtime
 * projection owns only current movement/action/carry/recovery state and is
 * discarded with the settlement instance.
 */
public final class SettlementWorkerNpc extends NPC {

    private static final long serialVersionUID = 4457138259614473421L;

    private static final int GATHER_TICKS = 3;
    private static final int CARRY_CAPACITY = 1;
    private static final int EAT_TICKS = 2;
    private static final int DRINK_TICKS = 2;
    private static final int REST_TICKS = 4;

    private enum WorkState {
        IDLE,
        MOVING_TO_RESOURCE,
        GATHERING,
        MOVING_TO_STORAGE,
        HAULING,
        MOVING_HOME_FOR_NEED,
        EATING,
        DRINKING,
        RESTING
    }

    private final long workerId;
    private final SettlementInstance settlement;
    private final SettlementWorkerState workerState;

    private WorkState workState = WorkState.IDLE;
    private SettlementResourceNode targetNode;
    private SettlementResource carriedResource;
    private int carriedAmount;
    private int gatherTicksRemaining;
    private int nextGatherIndex;
    private SettlementWorkerNeed activeNeed;
    private int recoveryTicksRemaining;
    private String statusDetail = "No allowed gathering job.";

    public SettlementWorkerNpc(SettlementInstance settlement,
            SettlementWorkerDefinition definition,
            SettlementWorkerState state,
            WorldTile tile) {
        super(definition.getNpcId(), tile, -1, false, true);
        this.settlement = settlement;
        this.workerState = state;
        this.workerId = state.getWorkerId();
        setName(state.getName());
        setRandomWalk(0);
        setCantInteract(true);
        setNoDistanceCheck(true);
        setForceAgressive(false);
    }

    @Override
    public void processNPC() {
        super.processNPC();
        if (hasFinished() || settlement == null || workerState == null) {
            return;
        }
        processSettlementWork();
    }

    private void processSettlementWork() {
        if (!settlement.isLoaded()) {
            idle("Settlement runtime unavailable.");
            return;
        }

        if (carriedAmount > 0
                && workerState.isJobAllowed(SettlementWorkerJob.HAUL)) {
            processCarriedResource();
            return;
        }

        if (gatherTicksRemaining > 0) {
            processGathering();
            return;
        }

        if (processNeeds()) {
            return;
        }

        if (carriedAmount > 0) {
            processCarriedResource();
            return;
        }

        if (targetNode != null) {
            SettlementWorkerJob job = findGatherJob(targetNode.getResource());
            if (job == null || !workerState.isJobAllowed(job)
                    || !settlement.isStarterResourceNodeAvailable(targetNode)
                    || !settlement.hasWorkerStorageSpace(workerId, targetNode.getResource())) {
                clearTarget();
            }
        }

        if (targetNode == null) {
            targetNode = selectNextGatherNode();
            if (targetNode == null) {
                idle(hasAllowedGatheringJob()
                        ? (hasAllowedGatheringStorageSpace()
                                ? "No allowed resource node is currently available."
                                : "Allowed resource storage is full.")
                        : "No allowed gathering job.");
                return;
            }
        }

        WorldTile routeTarget = settlement.getWorkerNodeRouteTarget(targetNode);
        if (routeTarget == null) {
            idle("Resource target unavailable for " + targetNode.getResource().getDisplayName() + ".");
            clearTarget();
            return;
        }

        workState = WorkState.MOVING_TO_RESOURCE;
        statusDetail = "Moving to " + targetNode.getResource().getDisplayName() + " node.";
        if (walkToward(routeTarget, "No Path to " + targetNode.getResource().getDisplayName() + " node.")) {
            beginGathering();
        }
    }

    private void processGathering() {
        if (targetNode == null) {
            gatherTicksRemaining = 0;
            idle("Gather target was lost.");
            return;
        }
        SettlementWorkerJob job = findGatherJob(targetNode.getResource());
        if (job == null || !workerState.isJobAllowed(job)) {
            gatherTicksRemaining = 0;
            clearTarget();
            idle("Gather job disabled.");
            return;
        }
        if (!settlement.isStarterResourceNodeAvailable(targetNode)) {
            gatherTicksRemaining = 0;
            clearTarget();
            idle("Resource node unavailable.");
            return;
        }
        if (!settlement.hasWorkerStorageReservation(
                workerId, targetNode.getResource(), CARRY_CAPACITY)) {
            String fullResource = targetNode.getResource().getDisplayName();
            gatherTicksRemaining = 0;
            clearTarget();
            idle(fullResource + " storage reservation was lost.");
            return;
        }

        gatherTicksRemaining--;
        if (gatherTicksRemaining > 0) {
            return;
        }

        carriedResource = targetNode.getResource();
        carriedAmount = CARRY_CAPACITY;
        workerState.applyWorkCycleCost();
        workerState.addSkillXp(job.getSkill(), job.getWorkerXp());
        nextGatherIndex = (targetNode.ordinal() + 1) % SettlementResourceNode.values().length;
        statusDetail = "Gathered " + carriedResource.getDisplayName() + "; awaiting haul.";
        targetNode = null;
        workState = WorkState.IDLE;
    }

    private void processCarriedResource() {
        if (!workerState.isJobAllowed(SettlementWorkerJob.HAUL)) {
            settlement.releaseWorkerStorageReservation(workerId);
            resetWalkSteps();
            workState = WorkState.IDLE;
            statusDetail = "Haul disabled; holding " + carriedResource.getDisplayName() + ".";
            return;
        }
        if (!settlement.reserveWorkerStorage(
                workerId, carriedResource, Math.max(1, carriedAmount))) {
            resetWalkSteps();
            workState = WorkState.IDLE;
            statusDetail = carriedResource.getDisplayName()
                    + " storage full/reserved; holding "
                    + carriedResource.getDisplayName() + ".";
            return;
        }

        WorldTile storage = settlement.getWorkerStorageTile(workerState);
        if (storage == null) {
            idle("No valid storage access tile.");
            return;
        }

        workState = WorkState.MOVING_TO_STORAGE;
        statusDetail = "Hauling " + carriedResource.getDisplayName() + " to storage.";
        if (!walkToward(storage, "No Path to storage.")) {
            return;
        }

        workState = WorkState.HAULING;
        long added = settlement.depositWorkerResource(
                workerId, carriedResource, carriedAmount);
        if (added <= 0L) {
            resetWalkSteps();
            idle("Storage unavailable; holding " + carriedResource.getDisplayName() + ".");
            return;
        }

        settlement.recordWorkerDepositProgress(workerState, added);
        carriedAmount -= (int) added;
        if (carriedAmount <= 0) {
            String deposited = carriedResource.getDisplayName();
            carriedResource = null;
            carriedAmount = 0;
            workState = WorkState.IDLE;
            statusDetail = "Deposited " + deposited + ".";
        }
    }

    private boolean processNeeds() {
        if (recoveryTicksRemaining > 0) {
            recoveryTicksRemaining--;
            if (recoveryTicksRemaining <= 0) {
                SettlementWorkerNeed recovered = activeNeed;
                activeNeed = null;
                workState = WorkState.IDLE;
                statusDetail = recovered == null
                        ? "Recovery complete."
                        : recovered.getDisplayName() + " recovered; resuming work.";
            }
            return true;
        }

        if (activeNeed == null) {
            SettlementWorkerNeed next = getCriticalNeed();
            if (next == null) {
                return false;
            }
            activeNeed = next;
            clearTarget();
        }

        WorldTile home = settlement.getWorkerStorageTile(workerState);
        if (home == null) {
            idle("No valid home tile for " + activeNeed.getDisplayName() + " recovery.");
            return true;
        }

        workState = WorkState.MOVING_HOME_FOR_NEED;
        statusDetail = "Returning home for " + activeNeed.getDisplayName() + ".";
        if (!walkToward(home, "No Path home for " + activeNeed.getDisplayName() + ".")) {
            return true;
        }

        switch (activeNeed) {
        case HUNGER:
            if (settlement.consumeWorkerResource(SettlementResource.FOOD, 1L) != 1L) {
                workState = WorkState.IDLE;
                statusDetail = "No Food; work stopped at Hunger "
                        + workerState.getNeed(SettlementWorkerNeed.HUNGER) + ".";
                return true;
            }
            workerState.recoverFromMeal();
            workState = WorkState.EATING;
            recoveryTicksRemaining = EAT_TICKS;
            statusDetail = "Eating 1 Food.";
            return true;
        case THIRST:
            if (!settlement.hasBasicWorkerWaterSupply()) {
                workState = WorkState.IDLE;
                statusDetail = "No Water; work stopped at Thirst "
                        + workerState.getNeed(SettlementWorkerNeed.THIRST) + ".";
                return true;
            }
            workerState.recoverFromDrink();
            workState = WorkState.DRINKING;
            recoveryTicksRemaining = DRINK_TICKS;
            statusDetail = "Drinking from starter shelter water supply.";
            return true;
        case ENERGY:
            workerState.recoverFromRest();
            workState = WorkState.RESTING;
            recoveryTicksRemaining = REST_TICKS;
            statusDetail = "Resting at home.";
            return true;
        default:
            activeNeed = null;
            return false;
        }
    }

    private SettlementWorkerNeed getCriticalNeed() {
        if (workerState.needsFood()) {
            return SettlementWorkerNeed.HUNGER;
        }
        if (workerState.needsWater()) {
            return SettlementWorkerNeed.THIRST;
        }
        if (workerState.needsRest()) {
            return SettlementWorkerNeed.ENERGY;
        }
        return null;
    }

    private void beginGathering() {
        if (targetNode == null) {
            idle("Gather target was lost.");
            return;
        }
        SettlementResource resource = targetNode.getResource();
        if (!settlement.reserveWorkerStorage(workerId, resource, CARRY_CAPACITY)) {
            clearTarget();
            idle(resource.getDisplayName() + " storage is full or reserved.");
            return;
        }

        resetWalkSteps();
        workState = WorkState.GATHERING;
        gatherTicksRemaining = GATHER_TICKS;
        statusDetail = "Gathering " + resource.getDisplayName() + ".";
        setNextAnimation(new Animation(targetNode.getAnimationId()));
    }

    private SettlementResourceNode selectNextGatherNode() {
        SettlementResourceNode[] nodes = SettlementResourceNode.values();
        if (nodes.length == 0) {
            return null;
        }
        for (int offset = 0; offset < nodes.length; offset++) {
            int index = (nextGatherIndex + offset) % nodes.length;
            SettlementResourceNode node = nodes[index];
            SettlementWorkerJob job = findGatherJob(node.getResource());
            if (job != null && workerState.isJobAllowed(job)
                    && settlement.hasWorkerStorageSpace(workerId, node.getResource())
                    && settlement.isStarterResourceNodeAvailable(node)) {
                return node;
            }
        }
        return null;
    }

    private boolean hasAllowedGatheringJob() {
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (job.isGatheringJob() && workerState.isJobAllowed(job)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllowedGatheringStorageSpace() {
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (job.isGatheringJob() && workerState.isJobAllowed(job)
                    && settlement.hasWorkerStorageSpace(workerId, job.getResource())) {
                return true;
            }
        }
        return false;
    }

    private SettlementWorkerJob findGatherJob(SettlementResource resource) {
        if (resource == null) {
            return null;
        }
        for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
            if (job.isGatheringJob() && job.getResource() == resource) {
                return job;
            }
        }
        return null;
    }

    private boolean walkToward(WorldTile target, String noPathReason) {
        if (target == null) {
            return false;
        }
        if (getPlane() == target.getPlane() && getX() == target.getX() && getY() == target.getY()) {
            resetWalkSteps();
            return true;
        }
        if (!hasWalkSteps()) {
            boolean routed = calcFollow(target, 25, true, true);
            if (!routed) {
                statusDetail = noPathReason;
                return false;
            }
            if (!hasWalkSteps()) {
                return true;
            }
        }
        return false;
    }

    private void clearTarget() {
        resetWalkSteps();
        if (carriedAmount <= 0) {
            settlement.releaseWorkerStorageReservation(workerId);
        }
        targetNode = null;
        gatherTicksRemaining = 0;
    }

    private void idle(String reason) {
        workState = WorkState.IDLE;
        statusDetail = reason;
    }

    public long getWorkerId() {
        return workerId;
    }

    public String getRuntimeWorkSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("state=").append(workState);
        summary.append(" | ").append(statusDetail);
        summary.append(" | carried=");
        if (carriedResource == null || carriedAmount <= 0) {
            summary.append("none");
        } else {
            summary.append(carriedResource.getDisplayName()).append(" x").append(carriedAmount);
        }
        if (targetNode != null) {
            summary.append(" | target=").append(targetNode.getKey());
        }
        summary.append(" | ").append(workerState.getNeedsSummary());
        summary.append(" | Skills: ").append(workerState.getSkillsSummary());
        return summary.toString();
    }
}
