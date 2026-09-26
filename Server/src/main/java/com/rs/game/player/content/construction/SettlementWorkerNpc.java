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
    private static final int PROCESS_TICKS = 4;
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
        MOVING_TO_PROCESSING_STORAGE,
        MOVING_TO_WORKSTATION,
        PROCESSING,
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
    private boolean emergencyFoodForage;
    private WorldTile manualMoveTarget;
    private SettlementResourceNode manualGatherNode;
    private boolean manualGatherActive;
    private boolean manualHaulActive;
    private SettlementProcessingRecipe manualProcessingRecipe;
    private long manualProcessingWorkstationPieceId = -1L;
    private SettlementProcessingRecipe processingRecipe;
    private long processingWorkstationPieceId = -1L;
    private boolean processingVisitedStorage;
    private int processingTicksRemaining;
    private boolean processingManual;
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

        if (workerState.isPaused()) {
            if (gatherTicksRemaining > 0 || targetNode != null) {
                clearTarget();
            }
            if (processingRecipe != null) {
                clearProcessingWork();
            }
            if (manualProcessingRecipe != null) {
                clearManualProcessingOrder();
            }
            if (carriedAmount > 0) {
                settlement.releaseWorkerStorageReservation(workerId);
                resetWalkSteps();
            }
            if (processNeeds()) {
                return;
            }
            workState = WorkState.IDLE;
            statusDetail = carriedAmount > 0 && carriedResource != null
                    ? "Paused; holding " + carriedResource.getDisplayName() + "."
                    : "Paused.";
            return;
        }

        if (manualMoveTarget != null) {
            processManualMoveOrder();
            return;
        }

        if (manualGatherNode != null && carriedAmount <= 0 && gatherTicksRemaining <= 0) {
            processManualGatherOrder();
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

        if (manualProcessingRecipe != null) {
            processManualProcessingOrder();
            return;
        }

        if (processingRecipe != null) {
            processProcessingWork();
            return;
        }

        boolean processWoodAllowed =
                workerState.isJobAllowed(SettlementWorkerJob.PROCESS_WOOD);
        if (processWoodAllowed
                && settlement.canProcessRecipe(SettlementProcessingRecipe.SAW_PLANKS)
                && beginProcessingWork(SettlementProcessingRecipe.SAW_PLANKS)) {
            processProcessingWork();
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
                        : processWoodAllowed
                                ? "Process Wood waiting for 2 Wood, Plank storage, or an available workbench."
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
        if (walkToward(routeTarget,
                "No Path to " + targetNode.getResource().getDisplayName() + " node.", 1)) {
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
        if (job == null || (!manualGatherActive && !workerState.isJobAllowed(job))) {
            gatherTicksRemaining = 0;
            manualGatherActive = false;
            clearTarget();
            idle("Gather job disabled.");
            return;
        }
        if (!settlement.isStarterResourceNodeAvailable(targetNode)) {
            gatherTicksRemaining = 0;
            manualGatherActive = false;
            clearTarget();
            idle("Resource node unavailable.");
            return;
        }
        if (!settlement.hasWorkerStorageReservation(
                workerId, targetNode.getResource(), CARRY_CAPACITY)) {
            String fullResource = targetNode.getResource().getDisplayName();
            gatherTicksRemaining = 0;
            manualGatherActive = false;
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
        if (manualGatherActive) {
            manualHaulActive = true;
        }
        workerState.applyWorkCycleCost();
        workerState.addSkillXp(job.getSkill(), job.getWorkerXp());
        nextGatherIndex = (targetNode.ordinal() + 1) % SettlementResourceNode.values().length;
        statusDetail = "Gathered " + carriedResource.getDisplayName() + "; awaiting haul.";
        targetNode = null;
        manualGatherActive = false;
        workState = WorkState.IDLE;
    }

    private void processCarriedResource() {
        if (!manualHaulActive && !workerState.isJobAllowed(SettlementWorkerJob.HAUL)) {
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
            if (emergencyFoodForage && carriedResource == SettlementResource.FOOD) {
                emergencyFoodForage = false;
            }
            carriedResource = null;
            carriedAmount = 0;
            manualHaulActive = false;
            workState = WorkState.IDLE;
            statusDetail = "Deposited " + deposited + ".";
        }
    }

    private boolean processNeeds() {
        if (emergencyFoodForage) {
            if (canEmergencyForageFood()) {
                activeNeed = null;
                recoveryTicksRemaining = 0;
                return false;
            }
            emergencyFoodForage = false;
        }

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
            clearProcessingWork();
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
                if (canEmergencyForageFood()) {
                    activeNeed = null;
                    emergencyFoodForage = true;
                    workState = WorkState.IDLE;
                    statusDetail = "No stored Food; emergency foraging.";
                    return false;
                }
                workState = WorkState.IDLE;
                statusDetail = "No Food; work stopped at Hunger "
                        + workerState.getNeed(SettlementWorkerNeed.HUNGER) + ".";
                return true;
            }
            emergencyFoodForage = false;
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

    public void assignManualMoveOrder(WorldTile target) {
        if (target == null) {
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        manualGatherNode = null;
        manualGatherActive = false;
        clearTarget();
        manualMoveTarget = new WorldTile(target.getX(), target.getY(), target.getPlane());
        resetWalkSteps();
        workState = WorkState.MOVING_TO_RESOURCE;
        statusDetail = "Manual order: moving to " + target.getX() + "," + target.getY() + ".";
    }

    public void assignManualGatherOrder(SettlementResourceNode node) {
        if (node == null) {
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        manualMoveTarget = null;
        manualGatherActive = false;
        clearTarget();
        manualGatherNode = node;
        resetWalkSteps();
        workState = WorkState.MOVING_TO_RESOURCE;
        statusDetail = "Manual order: moving to " + node.getResource().getDisplayName() + " node.";
    }

    private void processManualMoveOrder() {
        if (manualMoveTarget == null) {
            return;
        }
        WorldTile destination = manualMoveTarget;
        workState = WorkState.MOVING_TO_RESOURCE;
        statusDetail = "Manual order: moving to selected tile.";
        if (!walkToward(destination, "No Path to manual destination.", 0)) {
            return;
        }
        manualMoveTarget = null;
        resetWalkSteps();
        workState = WorkState.IDLE;
        statusDetail = "Manual move complete; resuming worker policy.";
    }

    private void processManualGatherOrder() {
        if (manualGatherNode == null) {
            return;
        }
        SettlementResourceNode node = manualGatherNode;
        if (!settlement.isStarterResourceNodeAvailable(node)) {
            manualGatherNode = null;
            idle("Manual resource target unavailable.");
            return;
        }
        WorldTile routeTarget = settlement.getWorkerNodeRouteTarget(node);
        if (routeTarget == null) {
            manualGatherNode = null;
            idle("Manual resource target unavailable.");
            return;
        }

        workState = WorkState.MOVING_TO_RESOURCE;
        statusDetail = "Manual order: moving to " + node.getResource().getDisplayName() + " node.";
        if (!walkToward(routeTarget,
                "No Path to manual " + node.getResource().getDisplayName() + " node.", 1)) {
            return;
        }

        targetNode = node;
        manualGatherNode = null;
        manualGatherActive = true;
        beginGathering();
    }

    public void assignManualProcessingOrder(
            SettlementProcessingRecipe recipe, long workstationPieceId) {
        if (recipe == null || workstationPieceId <= 0L) {
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        manualMoveTarget = null;
        manualGatherNode = null;
        manualGatherActive = false;
        clearTarget();
        manualProcessingRecipe = recipe;
        manualProcessingWorkstationPieceId = workstationPieceId;
        if (carriedAmount > 0) {
            manualHaulActive = true;
        }
        resetWalkSteps();
        workState = WorkState.IDLE;
        statusDetail = "Manual order: process at selected workstation.";
    }

    private void processManualProcessingOrder() {
        SettlementProcessingRecipe recipe = manualProcessingRecipe;
        long requestedPieceId = manualProcessingWorkstationPieceId;
        if (recipe == null || requestedPieceId <= 0L) {
            clearManualProcessingOrder();
            return;
        }
        if (!settlement.canProcessRecipe(recipe)) {
            clearManualProcessingOrder();
            idle("Manual Process Wood blocked by input or output storage.");
            return;
        }
        if (!beginProcessingWork(recipe, requestedPieceId, true)) {
            idle("Manual Process Wood waiting for the selected workstation.");
            return;
        }
        manualProcessingRecipe = null;
        manualProcessingWorkstationPieceId = -1L;
        processProcessingWork();
    }

    private void clearManualProcessingOrder() {
        manualProcessingRecipe = null;
        manualProcessingWorkstationPieceId = -1L;
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

    private boolean beginProcessingWork(SettlementProcessingRecipe recipe) {
        return beginProcessingWork(recipe, -1L, false);
    }

    private boolean beginProcessingWork(
            SettlementProcessingRecipe recipe, long preferredPieceId, boolean manual) {
        long workstationPieceId =
                settlement.reserveProcessingWorkstation(workerId, recipe, preferredPieceId);
        if (workstationPieceId <= 0L) {
            return false;
        }
        processingRecipe = recipe;
        processingWorkstationPieceId = workstationPieceId;
        processingVisitedStorage = false;
        processingTicksRemaining = 0;
        processingManual = manual;
        resetWalkSteps();
        workState = WorkState.MOVING_TO_PROCESSING_STORAGE;
        statusDetail = (manual ? "Manual Process Wood: collecting " : "Process Wood: collecting ")
                + recipe.getInputAmount() + " "
                + recipe.getInputResource().getDisplayName() + " from storage.";
        return true;
    }

    private void processProcessingWork() {
        SettlementProcessingRecipe recipe = processingRecipe;
        if (recipe == null) {
            return;
        }
        SettlementWorkerJob job = SettlementWorkerJob.PROCESS_WOOD;
        if (!processingManual && !workerState.isJobAllowed(job)) {
            clearProcessingWork();
            idle("Process Wood disabled.");
            return;
        }

        if (!processingVisitedStorage) {
            if (!settlement.canProcessRecipe(recipe)) {
                clearProcessingWork();
                idle("Process Wood blocked by input or output storage.");
                return;
            }
            WorldTile storage = settlement.getWorkerStorageTile(workerState);
            if (storage == null) {
                clearProcessingWork();
                idle("No valid processing storage access tile.");
                return;
            }
            workState = WorkState.MOVING_TO_PROCESSING_STORAGE;
            statusDetail = "Collecting " + recipe.getInputAmount() + " "
                    + recipe.getInputResource().getDisplayName() + " from storage.";
            if (!walkToward(storage, "No Path to processing storage.")) {
                return;
            }
            processingVisitedStorage = true;
            resetWalkSteps();
            statusDetail = "Inputs collected; moving to Wooden workbench.";
            return;
        }

        WorldTile workstation =
                settlement.getProcessingWorkstationTile(processingWorkstationPieceId);
        if (workstation == null) {
            clearProcessingWork();
            idle("Wooden workbench is no longer available.");
            return;
        }

        if (processingTicksRemaining <= 0) {
            workState = WorkState.MOVING_TO_WORKSTATION;
            statusDetail = "Moving to Wooden workbench.";
            if (!walkToward(workstation, "No Path to Wooden workbench.", 1)) {
                return;
            }
            resetWalkSteps();
            workState = WorkState.PROCESSING;
            processingTicksRemaining = PROCESS_TICKS;
            statusDetail = "Processing " + recipe.getSummary() + ".";
            return;
        }

        workState = WorkState.PROCESSING;
        processingTicksRemaining--;
        if (processingTicksRemaining > 0) {
            return;
        }

        SettlementProcessingTransaction.Result result =
                settlement.processWorkerRecipe(workerState, recipe);
        clearProcessingWork();
        workState = WorkState.IDLE;
        if (result == null || !result.isSuccess()) {
            statusDetail = "Processing blocked: "
                    + (result == null ? "settlement runtime unavailable."
                            : result.getSummary());
            return;
        }

        workerState.applyWorkCycleCost();
        workerState.addSkillXp(job.getSkill(), job.getWorkerXp());
        statusDetail = result.getSummary() + " Worker Crafting XP +"
                + job.getWorkerXp() + ".";
    }

    private void clearProcessingWork() {
        if (processingWorkstationPieceId > 0L) {
            settlement.releaseProcessingWorkstation(workerId);
        }
        processingRecipe = null;
        processingWorkstationPieceId = -1L;
        processingVisitedStorage = false;
        processingTicksRemaining = 0;
        processingManual = false;
        resetWalkSteps();
    }

    private SettlementResourceNode selectNextGatherNode() {
        SettlementResourceNode[] nodes = SettlementResourceNode.values();
        if (nodes.length == 0) {
            return null;
        }
        if (emergencyFoodForage) {
            for (SettlementResourceNode node : nodes) {
                if (node.getResource() == SettlementResource.FOOD
                        && workerState.isJobAllowed(SettlementWorkerJob.GATHER_FOOD)
                        && settlement.hasWorkerStorageSpace(workerId, SettlementResource.FOOD)
                        && settlement.isStarterResourceNodeAvailable(node)) {
                    return node;
                }
            }
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

    private boolean canEmergencyForageFood() {
        if (workerState.isPaused()
                || !workerState.needsFood()
                || !workerState.isJobAllowed(SettlementWorkerJob.GATHER_FOOD)
                || !workerState.isJobAllowed(SettlementWorkerJob.HAUL)
                || !settlement.hasWorkerStorageSpace(workerId, SettlementResource.FOOD)) {
            return false;
        }
        for (SettlementResourceNode node : SettlementResourceNode.values()) {
            if (node.getResource() == SettlementResource.FOOD
                    && settlement.isStarterResourceNodeAvailable(node)) {
                return true;
            }
        }
        return false;
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
        return walkToward(target, noPathReason, 0);
    }

    private boolean walkToward(WorldTile target, String noPathReason, int interactionRange) {
        if (target == null) {
            return false;
        }
        if (isWithinInteractionRange(target, interactionRange)) {
            resetWalkSteps();
            return true;
        }
        if (!hasWalkSteps()) {
            boolean routed = calcFollow(target, 25, true, true);
            if (!routed) {
                statusDetail = noPathReason;
                return false;
            }
            /*
             * calcFollow(...) may legitimately return success without queuing
             * steps when its own follow heuristics consider a target reached.
             * Settlement gathering must not treat that as arrival unless the
             * worker is physically inside the explicit interaction range.
             */
            if (!hasWalkSteps() && !isWithinInteractionRange(target, interactionRange)) {
                statusDetail = noPathReason;
                return false;
            }
        }
        return isWithinInteractionRange(target, interactionRange);
    }

    private boolean isWithinInteractionRange(WorldTile target, int range) {
        if (target == null || getPlane() != target.getPlane()) {
            return false;
        }
        int allowed = Math.max(0, range);
        return Math.abs(getX() - target.getX()) <= allowed
                && Math.abs(getY() - target.getY()) <= allowed;
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
        summary.append(" | paused=").append(workerState.isPaused() ? "YES" : "NO");
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
        if (manualMoveTarget != null) {
            summary.append(" | manualMove=")
                    .append(manualMoveTarget.getX()).append(',').append(manualMoveTarget.getY());
        }
        if (manualGatherNode != null || manualGatherActive) {
            summary.append(" | manualGather=")
                    .append(manualGatherNode != null ? manualGatherNode.getKey()
                            : targetNode != null ? targetNode.getKey() : "active");
        }
        if (manualProcessingRecipe != null) {
            summary.append(" | manualProcessing=").append(manualProcessingRecipe.getKey())
                    .append("@piece#").append(manualProcessingWorkstationPieceId);
        }
        if (processingRecipe != null) {
            summary.append(" | processing=").append(processingRecipe.getKey())
                    .append("@piece#").append(processingWorkstationPieceId)
                    .append(processingManual ? " [manual]" : " [policy]");
        }
        summary.append(" | ").append(workerState.getNeedsSummary());
        summary.append(" | Skills: ").append(workerState.getSkillsSummary());
        return summary.toString();
    }
}
