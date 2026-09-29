package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.List;

import com.rs.game.Animation;
import com.rs.game.ForceTalk;
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
    private static final long DEBUG_SPEECH_MIN_INTERVAL_MS = 2000L;
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
        MOVING_TO_PROCESSING_OUTPUT_STORAGE,
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
    private int carriedItemId = -1;
    private int carriedItemAmount;
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
    private long manualTakeStoragePieceId = -1L;
    private long manualDeliverStoragePieceId = -1L;
    private SettlementProcessingRecipe processingRecipe;
    private long processingWorkstationPieceId = -1L;
    private boolean processingInputDelivered;
    private int processingOutputRemaining;
    private int processingTicksRemaining;
    private boolean processingManual;
    private String statusDetail = "No allowed gathering job.";
    private WorkState lastDebugWorkState;
    private String lastDebugStatusDetail;
    private long lastDebugSpeechMillis;

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
        /*
         * Settlement workers use soft unit collision: Matrix3 still clips
         * walls/objects, but other NPCs do not invalidate an in-progress path.
         * SettlementInstance separately reserves final approach/rest tiles so
         * workers do not permanently stack.
         */
        setIntelligentRouteFinder(true);
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
        emitDebugTransition();
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
            if (processingRecipe != null
                    && carriedItemAmount <= 0 && processingOutputRemaining <= 0) {
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
            if (carriedItemAmount > 0) {
                statusDetail = "Paused; holding "
                        + getFactoryItemDisplayName(carriedItemId)
                        + " x" + carriedItemAmount + ".";
            } else {
                statusDetail = carriedAmount > 0 && carriedResource != null
                        ? "Paused; holding " + carriedResource.getDisplayName() + "."
                        : "Paused.";
            }
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

        if (manualDeliverStoragePieceId > 0L) {
            processManualDeliverOrder();
            return;
        }

        if (manualTakeStoragePieceId > 0L) {
            processManualTakeOrder();
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

        if (carriedItemAmount > 0 && processingRecipe == null) {
            idle("Holding " + getFactoryItemDisplayName(carriedItemId)
                    + " x" + carriedItemAmount + "; use Deliver Here.");
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

        if (processingRecipe != null && processingManual) {
            processProcessingWork();
            return;
        }

        if (workerState.getBehaviorMode()
                == SettlementWorkerBehaviorMode.DIRECT_ORDERS_ONLY) {
            if (processingRecipe != null && !processingManual) {
                if (hasPhysicalProcessingPayload()) {
                    processProcessingWork();
                    return;
                }
                clearProcessingWork();
            }
            if (targetNode != null && !manualGatherActive) {
                clearTarget();
            }
            idle("Direct Orders Only; waiting for an RTS order.");
            return;
        }

        if (workerState.getBehaviorMode()
                == SettlementWorkerBehaviorMode.RALLY_RESTRICTED
                && workerState.getRallyPointId() <= 0L) {
            if (processingRecipe != null && !processingManual) {
                if (hasPhysicalProcessingPayload()) {
                    processProcessingWork();
                    return;
                }
                clearProcessingWork();
            }
            if (targetNode != null && !manualGatherActive) {
                clearTarget();
            }
            idle("Rally Restricted; assign a rally point.");
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
                    || !settlement.isWorkerNodeAllowedByRally(workerState, targetNode)
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
                                ? "Process Wood waiting for recipe input/output or an available workbench."
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
                workerId, carriedResource, Math.max(1, carriedAmount),
                manualHaulActive)) {
            resetWalkSteps();
            workState = WorkState.IDLE;
            statusDetail = carriedResource.getDisplayName()
                    + " storage full/reserved; holding "
                    + carriedResource.getDisplayName() + ".";
            return;
        }

        WorldTile storage = settlement.getWorkerHaulStorageTile(
                workerId, workerState, carriedResource);
        if (storage == null) {
            settlement.releaseWorkerStorageReservation(workerId);
            idle("No valid storage access tile.");
            return;
        }

        workState = WorkState.MOVING_TO_STORAGE;
        statusDetail = "Hauling " + carriedResource.getDisplayName()
                + (settlement.usesPhysicalWorkerStorage(carriedResource)
                        ? " to physical storage." : " to storage.");
        if (!walkToward(storage, "No Path to storage.",
                settlement.getWorkerStorageInteractionRange(carriedResource))) {
            return;
        }

        workState = WorkState.HAULING;
        long added = settlement.depositWorkerResource(
                workerId, carriedResource, carriedAmount, manualHaulActive);
        if (added <= 0L) {
            resetWalkSteps();
            idle("Storage unavailable; holding " + carriedResource.getDisplayName() + ".");
            return;
        }

        settlement.recordWorkerDepositProgress(workerState, added);
        settlement.releaseWorkerDestination(workerId);
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
            if (processingRecipe != null && hasPhysicalProcessingPayload()) {
                statusDetail = "Finishing physical logistics payload before "
                        + next.getDisplayName() + " recovery.";
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
        if (hasPhysicalProcessingPayload()) {
            statusDetail = "Finish current physical logistics payload before moving.";
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        clearManualLogisticsOrders();
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
        if (hasPhysicalProcessingPayload()) {
            statusDetail = "Finish current physical logistics payload before gathering.";
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        clearManualLogisticsOrders();
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

    public void assignManualTakeOrder(long storagePieceId) {
        if (storagePieceId <= 0L) {
            return;
        }
        if (carriedItemAmount > 0 || carriedAmount > 0 || hasPhysicalProcessingPayload()) {
            statusDetail = "Take From Here blocked; deliver the current payload first.";
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        clearManualLogisticsOrders();
        manualMoveTarget = null;
        manualGatherNode = null;
        manualGatherActive = false;
        clearTarget();
        manualTakeStoragePieceId = storagePieceId;
        resetWalkSteps();
        workState = WorkState.IDLE;
        statusDetail = "Manual order: Take From Here at chest#" + storagePieceId + ".";
    }

    public void assignManualDeliverOrder(long storagePieceId) {
        if (storagePieceId <= 0L) {
            return;
        }
        boolean hasPayload = carriedItemAmount > 0 || carriedAmount > 0
                || processingRecipe != null && processingOutputRemaining > 0;
        if (!hasPayload) {
            statusDetail = "Deliver Here blocked; worker is not carrying a physical payload.";
            return;
        }
        settlement.releaseWorkerItemDestinationReservation(workerId);
        manualDeliverStoragePieceId = storagePieceId;
        manualTakeStoragePieceId = -1L;
        resetWalkSteps();
        statusDetail = "Manual order: Deliver Here to chest#" + storagePieceId + ".";
    }

    private void processManualTakeOrder() {
        long pieceId = manualTakeStoragePieceId;
        if (pieceId <= 0L) {
            return;
        }
        if (carriedItemAmount > 0 || carriedAmount > 0) {
            manualTakeStoragePieceId = -1L;
            idle("Take From Here stopped; worker is already carrying a payload.");
            return;
        }
        int itemId = settlement.getFirstWorkerWithdrawableItemId(workerId, pieceId);
        if (itemId < 0) {
            manualTakeStoragePieceId = -1L;
            settlement.releaseWorkerItemSourceReservation(workerId);
            idle("Take From Here blocked: selected chest is empty or unavailable.");
            return;
        }
        if (!settlement.reserveWorkerItemSourceAt(
                workerId, pieceId, itemId, CARRY_CAPACITY)) {
            idle("Take From Here waiting: selected chest item is reserved.");
            return;
        }
        WorldTile source = settlement.getPhysicalStorageTile(pieceId);
        if (source == null) {
            manualTakeStoragePieceId = -1L;
            settlement.releaseWorkerItemSourceReservation(workerId);
            idle("Take From Here blocked: selected chest is unavailable.");
            return;
        }
        workState = WorkState.MOVING_TO_PROCESSING_STORAGE;
        statusDetail = "Take From Here: moving to chest for "
                + getFactoryItemDisplayName(itemId) + ".";
        if (!walkToward(source, "No Path to Take From Here chest.", 1)) {
            return;
        }
        int removed = settlement.withdrawWorkerItemSourceAt(
                workerId, pieceId, itemId, CARRY_CAPACITY);
        if (removed <= 0) {
            manualTakeStoragePieceId = -1L;
            idle("Take From Here failed: chest contents changed.");
            return;
        }
        carriedItemId = itemId;
        carriedItemAmount = removed;
        manualTakeStoragePieceId = -1L;
        settlement.releaseWorkerDestination(workerId);
        resetWalkSteps();
        workState = WorkState.IDLE;
        statusDetail = "Take From Here complete; holding "
                + getFactoryItemDisplayName(itemId) + " x" + removed
                + ". Use Deliver Here.";
    }

    private void processManualDeliverOrder() {
        long pieceId = manualDeliverStoragePieceId;
        if (pieceId <= 0L) {
            return;
        }

        /*
         * If this order redirected machine output before the worker picked up
         * the next unit, collect one physical output item first and keep the
         * exact destination order active.
         */
        if (carriedItemAmount <= 0 && carriedAmount <= 0
                && processingRecipe != null && processingOutputRemaining > 0) {
            SettlementFactoryItem output = SettlementFactoryItem.forResource(
                    processingRecipe.getOutputResource());
            WorldTile workstation =
                    settlement.getProcessingWorkstationTile(processingWorkstationPieceId);
            if (output == null || workstation == null) {
                manualDeliverStoragePieceId = -1L;
                idle("Deliver Here blocked: machine output is unavailable.");
                return;
            }
            int take = Math.min(CARRY_CAPACITY, processingOutputRemaining);
            workState = WorkState.MOVING_TO_WORKSTATION;
            statusDetail = "Deliver Here: collecting "
                    + output.getDisplayName() + " from machine output.";
            if (!walkToward(workstation, "No Path to machine output.", 1)) {
                return;
            }
            int removed = settlement.collectMachineOutput(
                    processingWorkstationPieceId, output.getItemId(), take);
            if (removed <= 0) {
                processingOutputRemaining = 0;
                manualDeliverStoragePieceId = -1L;
                idle("Deliver Here stopped: machine output is empty.");
                return;
            }
            carriedItemId = output.getItemId();
            carriedItemAmount = removed;
            resetWalkSteps();
            return;
        }

        int itemId;
        int amount;
        boolean resourceCargo = false;
        if (carriedItemAmount > 0) {
            itemId = carriedItemId;
            amount = carriedItemAmount;
        } else if (carriedAmount > 0 && carriedResource != null) {
            SettlementFactoryItem mapping =
                    SettlementFactoryItem.forResource(carriedResource);
            if (mapping == null) {
                manualDeliverStoragePieceId = -1L;
                idle("Deliver Here blocked: "
                        + carriedResource.getDisplayName()
                        + " is not migrated to physical item logistics yet.");
                return;
            }
            itemId = mapping.getItemId();
            amount = carriedAmount;
            resourceCargo = true;
        } else {
            manualDeliverStoragePieceId = -1L;
            idle("Deliver Here stopped; worker has no physical payload.");
            return;
        }

        if (!settlement.reserveWorkerItemDestinationAt(
                workerId, pieceId, itemId, amount)) {
            idle("Deliver Here waiting: selected chest rejects the item, is full, or is reserved.");
            return;
        }
        WorldTile destination = settlement.getPhysicalStorageTile(pieceId);
        if (destination == null) {
            settlement.releaseWorkerItemDestinationReservation(workerId);
            idle("Deliver Here blocked: selected chest is unavailable.");
            return;
        }
        workState = WorkState.MOVING_TO_STORAGE;
        statusDetail = "Deliver Here: hauling "
                + getFactoryItemDisplayName(itemId) + " to selected chest.";
        if (!walkToward(destination, "No Path to Deliver Here chest.", 1)) {
            return;
        }
        int added = settlement.depositWorkerItemToStorageAt(
                workerId, pieceId, itemId, amount);
        if (added <= 0) {
            idle("Deliver Here waiting: selected chest changed before delivery.");
            return;
        }

        settlement.releaseWorkerDestination(workerId);
        resetWalkSteps();

        if (resourceCargo) {
            settlement.recordWorkerDepositProgress(workerState, added);
            carriedAmount -= added;
            if (carriedAmount <= 0) {
                carriedResource = null;
                carriedAmount = 0;
                manualHaulActive = false;
                manualDeliverStoragePieceId = -1L;
                workState = WorkState.IDLE;
                statusDetail = "Deliver Here complete.";
            }
            return;
        }

        carriedItemAmount -= added;
        if (processingRecipe != null) {
            SettlementFactoryItem output = SettlementFactoryItem.forResource(
                    processingRecipe.getOutputResource());
            if (output != null && output.getItemId() == itemId) {
                processingOutputRemaining =
                        Math.max(0, processingOutputRemaining - added);
            }
        }
        if (carriedItemAmount <= 0) {
            carriedItemId = -1;
            carriedItemAmount = 0;
        }

        if (processingRecipe != null && processingOutputRemaining > 0) {
            workState = WorkState.IDLE;
            statusDetail = "Deliver Here: continuing remaining machine output.";
            return;
        }

        manualDeliverStoragePieceId = -1L;
        if (processingRecipe != null) {
            String completed = processingRecipe.getDisplayName()
                    + " output delivered to selected chest.";
            clearProcessingWork();
            workState = WorkState.IDLE;
            statusDetail = completed;
        } else {
            workState = WorkState.IDLE;
            statusDetail = "Deliver Here complete.";
        }
    }

    private void clearManualLogisticsOrders() {
        manualTakeStoragePieceId = -1L;
        manualDeliverStoragePieceId = -1L;
        settlement.releaseWorkerItemSourceReservation(workerId);
        settlement.releaseWorkerItemDestinationReservation(workerId);
    }

    public void assignManualProcessingOrder(
            SettlementProcessingRecipe recipe, long workstationPieceId) {
        if (recipe == null || workstationPieceId <= 0L) {
            return;
        }
        if (hasPhysicalProcessingPayload()) {
            statusDetail = "Finish current physical logistics payload before changing machines.";
            return;
        }
        clearProcessingWork();
        clearManualProcessingOrder();
        clearManualLogisticsOrders();
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
        if (!settlement.reserveWorkerStorage(
                workerId, resource, CARRY_CAPACITY, manualGatherActive)) {
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
        processingInputDelivered = false;
        processingOutputRemaining = 0;
        processingTicksRemaining = 0;
        processingManual = manual;
        resetWalkSteps();
        workState = WorkState.MOVING_TO_PROCESSING_STORAGE;
        statusDetail = (manual ? "Manual Process Wood: " : "Process Wood: ")
                + "checking physical machine input/output.";
        return true;
    }

    private void processProcessingWork() {
        SettlementProcessingRecipe recipe = processingRecipe;
        if (recipe == null) {
            return;
        }
        SettlementWorkerJob job = SettlementWorkerJob.PROCESS_WOOD;
        if (!processingManual && !workerState.isJobAllowed(job)
                && !hasPhysicalProcessingPayload()) {
            clearProcessingWork();
            idle("Process Wood disabled.");
            return;
        }

        SettlementFactoryItem inputMapping =
                SettlementFactoryItem.forResource(recipe.getInputResource());
        SettlementFactoryItem outputMapping =
                SettlementFactoryItem.forResource(recipe.getOutputResource());
        if (inputMapping == null || outputMapping == null) {
            clearProcessingWork();
            idle("Process Wood blocked: physical recipe item mapping unavailable.");
            return;
        }
        int inputItemId = inputMapping.getItemId();
        int outputItemId = outputMapping.getItemId();

        WorldTile workstation =
                settlement.getProcessingWorkstationTile(processingWorkstationPieceId);
        if (workstation == null) {
            if (carriedItemAmount > 0) {
                if (!settlement.reserveWorkerItemDestination(
                        workerId, carriedItemId, carriedItemAmount, true)) {
                    idle("Processing workstation unavailable; holding "
                            + getFactoryItemDisplayName(carriedItemId)
                            + " until recovery storage is available.");
                    return;
                }
                WorldTile recovery = settlement.getWorkerItemDestinationTile(workerId);
                if (recovery == null) {
                    settlement.releaseWorkerItemDestinationReservation(workerId);
                    idle("Processing workstation unavailable; recovery storage unavailable.");
                    return;
                }
                workState = WorkState.MOVING_TO_PROCESSING_OUTPUT_STORAGE;
                statusDetail = "Recovering carried "
                        + getFactoryItemDisplayName(carriedItemId)
                        + " to physical storage.";
                if (!walkToward(recovery, "No Path to recovery storage.", 1)) {
                    return;
                }
                int itemId = carriedItemId;
                int added = settlement.depositWorkerItem(
                        workerId, itemId, carriedItemAmount, true);
                if (added > 0) {
                    carriedItemAmount -= added;
                    if (carriedItemAmount <= 0) {
                        carriedItemId = -1;
                        carriedItemAmount = 0;
                        clearProcessingWork();
                        workState = WorkState.IDLE;
                        statusDetail = "Recovered physical payload after workstation loss.";
                    }
                }
                return;
            }
            clearProcessingWork();
            idle("Processing workstation is no longer available; machine buffer preserved.");
            return;
        }

        /*
         * A machine may already contain output from an interrupted/blocked
         * cycle. Drain that first so output backpressure can recover without a
         * second invisible inventory owner.
         */
        if (processingOutputRemaining <= 0 && carriedItemAmount <= 0
                && settlement.getMachineOutputAmount(
                        processingWorkstationPieceId, outputItemId) > 0L) {
            processingOutputRemaining = (int) Math.min(
                    (long) Integer.MAX_VALUE,
                    settlement.getMachineOutputAmount(
                            processingWorkstationPieceId, outputItemId));
        }

        if (carriedItemAmount > 0 && carriedItemId == outputItemId) {
            int payload = Math.min(carriedItemAmount, CARRY_CAPACITY);
            if (!settlement.reserveWorkerItemDestination(
                    workerId, outputItemId, payload, processingManual)) {
                settlement.releaseWorkerItemDestinationReservation(workerId);
                idle("Machine output blocked: holding "
                        + outputMapping.getDisplayName()
                        + "; no destination currently accepts it.");
                return;
            }
            WorldTile destination = settlement.getWorkerItemDestinationTile(workerId);
            if (destination == null) {
                settlement.releaseWorkerItemDestinationReservation(workerId);
                idle("Machine output blocked: destination storage unavailable.");
                return;
            }
            workState = WorkState.MOVING_TO_PROCESSING_OUTPUT_STORAGE;
            statusDetail = "Hauling " + outputMapping.getDisplayName()
                    + " from machine output to physical storage.";
            if (!walkToward(destination, "No Path to machine-output storage.", 1)) {
                return;
            }
            int added = settlement.depositWorkerItem(
                    workerId, outputItemId, payload, processingManual);
            if (added <= 0) {
                idle("Machine output destination changed; holding "
                        + outputMapping.getDisplayName() + ".");
                return;
            }
            carriedItemAmount -= added;
            processingOutputRemaining = Math.max(0, processingOutputRemaining - added);
            if (carriedItemAmount <= 0) {
                carriedItemId = -1;
                carriedItemAmount = 0;
            }
            settlement.releaseWorkerDestination(workerId);
            resetWalkSteps();
            if (processingOutputRemaining <= 0) {
                String completed = recipe.getDisplayName()
                        + " physical cycle complete; output stored.";
                clearProcessingWork();
                workState = WorkState.IDLE;
                statusDetail = completed;
            }
            return;
        }

        if (processingOutputRemaining > 0) {
            int take = Math.min(CARRY_CAPACITY, processingOutputRemaining);
            if (!settlement.reserveWorkerItemDestination(
                    workerId, outputItemId, take, processingManual)) {
                settlement.releaseWorkerItemDestinationReservation(workerId);
                clearProcessingWork();
                idle("Machine output waiting: no physical storage accepts "
                        + outputMapping.getDisplayName() + ".");
                return;
            }
            workState = WorkState.MOVING_TO_WORKSTATION;
            statusDetail = "Collecting " + outputMapping.getDisplayName()
                    + " from machine output.";
            if (!walkToward(workstation, "No Path to processing workstation.", 1)) {
                return;
            }
            int removed = settlement.collectMachineOutput(
                    processingWorkstationPieceId, outputItemId, take);
            if (removed <= 0) {
                settlement.releaseWorkerItemDestinationReservation(workerId);
                processingOutputRemaining = 0;
                resetWalkSteps();
                return;
            }
            carriedItemId = outputItemId;
            carriedItemAmount = removed;
            resetWalkSteps();
            return;
        }

        long machineInput = settlement.getMachineInputAmount(
                processingWorkstationPieceId, inputItemId);
        if (!processingInputDelivered && machineInput < recipe.getInputAmount()) {
            int missing = (int) Math.min(
                    (long) CARRY_CAPACITY,
                    recipe.getInputAmount() - machineInput);

            if (carriedItemAmount > 0 && carriedItemId == inputItemId) {
                workState = WorkState.MOVING_TO_WORKSTATION;
                statusDetail = "Delivering " + inputMapping.getDisplayName()
                        + " to machine input.";
                if (!walkToward(workstation, "No Path to processing workstation.", 1)) {
                    return;
                }
                int added = settlement.depositWorkerItemToMachine(
                        processingWorkstationPieceId, inputItemId, carriedItemAmount);
                if (added <= 0) {
                    idle("Machine input full; holding "
                            + inputMapping.getDisplayName() + ".");
                    return;
                }
                carriedItemAmount -= added;
                if (carriedItemAmount <= 0) {
                    carriedItemId = -1;
                    carriedItemAmount = 0;
                }
                settlement.releaseWorkerDestination(workerId);
                resetWalkSteps();
                machineInput = settlement.getMachineInputAmount(
                        processingWorkstationPieceId, inputItemId);
                processingInputDelivered = machineInput >= recipe.getInputAmount();
                statusDetail = processingInputDelivered
                        ? "Machine input delivered."
                        : "Machine still needs physical input.";
                return;
            }

            if (!settlement.reserveWorkerItemSource(
                    workerId, inputItemId, missing, processingManual)) {
                clearProcessingWork();
                idle("Process Wood waiting: no eligible physical source has "
                        + inputMapping.getDisplayName() + ".");
                return;
            }
            WorldTile source = settlement.getWorkerItemSourceTile(workerId);
            if (source == null) {
                settlement.releaseWorkerItemSourceReservation(workerId);
                clearProcessingWork();
                idle("Process Wood blocked: source storage unavailable.");
                return;
            }
            workState = WorkState.MOVING_TO_PROCESSING_STORAGE;
            statusDetail = "Collecting " + missing + " "
                    + inputMapping.getDisplayName() + " from physical storage.";
            if (!walkToward(source, "No Path to processing source storage.", 1)) {
                return;
            }
            int removed = settlement.withdrawWorkerItemSource(
                    workerId, inputItemId, missing);
            if (removed != missing) {
                clearProcessingWork();
                idle("Process Wood source changed before pickup.");
                return;
            }
            carriedItemId = inputItemId;
            carriedItemAmount = removed;
            resetWalkSteps();
            statusDetail = "Carrying " + inputMapping.getDisplayName()
                    + " to machine input.";
            return;
        }
        processingInputDelivered = true;

        if (processingTicksRemaining <= 0) {
            workState = WorkState.MOVING_TO_WORKSTATION;
            statusDetail = "Moving to processing workstation.";
            if (!walkToward(workstation, "No Path to processing workstation.", 1)) {
                return;
            }
            resetWalkSteps();
            workState = WorkState.PROCESSING;
            processingTicksRemaining = PROCESS_TICKS;
            statusDetail = "Processing " + recipe.getSummary()
                    + " from machine-local input.";
            return;
        }

        workState = WorkState.PROCESSING;
        processingTicksRemaining--;
        if (processingTicksRemaining > 0) {
            return;
        }

        SettlementProcessingTransaction.Result result =
                settlement.processWorkerRecipe(
                        workerState, recipe, processingWorkstationPieceId);
        if (result == null || !result.isSuccess()) {
            String blocked = result == null
                    ? "settlement runtime unavailable." : result.getSummary();
            clearProcessingWork();
            workState = WorkState.IDLE;
            statusDetail = "Processing blocked: " + blocked;
            return;
        }

        workerState.applyWorkCycleCost();
        workerState.addSkillXp(job.getSkill(), job.getWorkerXp());
        processingInputDelivered = false;
        processingTicksRemaining = 0;
        processingOutputRemaining = (int) Math.min(
                (long) Integer.MAX_VALUE, result.getOutputProduced());
        statusDetail = result.getSummary() + " Machine output ready; Worker Crafting XP +"
                + job.getWorkerXp() + ".";
    }

    private void clearProcessingWork() {
        settlement.releaseWorkerItemSourceReservation(workerId);
        settlement.releaseWorkerItemDestinationReservation(workerId);
        if (processingWorkstationPieceId > 0L) {
            settlement.releaseProcessingWorkstation(workerId);
        }
        processingRecipe = null;
        processingWorkstationPieceId = -1L;
        processingInputDelivered = false;
        processingOutputRemaining = 0;
        processingTicksRemaining = 0;
        processingManual = false;
        resetWalkSteps();
    }

    private boolean hasPhysicalProcessingPayload() {
        return carriedItemAmount > 0 || processingOutputRemaining > 0;
    }

    private String getFactoryItemDisplayName(int itemId) {
        SettlementFactoryItem item = SettlementFactoryItem.forItemId(itemId);
        return item == null ? "item #" + itemId : item.getDisplayName();
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
                    && settlement.isWorkerNodeAllowedByRally(workerState, node)
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

        /*
         * Do not reserve the path itself. Workers may cross through one another
         * while travelling. Only the final stand/interaction tile is unique.
         */
        WorldTile destination =
                settlement.reserveWorkerDestination(workerId, target, interactionRange);
        if (destination == null) {
            resetWalkSteps();
            statusDetail = noPathReason + " No free worker approach tile.";
            return false;
        }

        if (getPlane() == destination.getPlane()
                && getX() == destination.getX()
                && getY() == destination.getY()) {
            resetWalkSteps();
            return true;
        }

        if (!hasWalkSteps()) {
            boolean routed = calcFollow(destination, 25, true, true);
            if (!routed) {
                settlement.releaseWorkerDestination(workerId);
                statusDetail = noPathReason;
                return false;
            }
            if (!hasWalkSteps()
                    && (getPlane() != destination.getPlane()
                            || getX() != destination.getX()
                            || getY() != destination.getY())) {
                settlement.releaseWorkerDestination(workerId);
                statusDetail = noPathReason;
                return false;
            }
        }
        return getPlane() == destination.getPlane()
                && getX() == destination.getX()
                && getY() == destination.getY();
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

    private void emitDebugTransition() {
        SettlementDebug debug = settlement.getDebug();
        if (debug == null) {
            return;
        }
        if (lastDebugWorkState == workState
                && (lastDebugStatusDetail == null
                        ? statusDetail == null
                        : lastDebugStatusDetail.equals(statusDetail))) {
            return;
        }

        lastDebugWorkState = workState;
        lastDebugStatusDetail = statusDetail;

        String detail = statusDetail == null ? "" : statusDetail;
        String lower = detail.toLowerCase();
        List<SettlementDebug.Category> categories =
                new ArrayList<SettlementDebug.Category>();
        categories.add(SettlementDebug.Category.WORKER_ACTIONS);

        if (workState.name().startsWith("MOVING_")
                || lower.contains("path")) {
            categories.add(SettlementDebug.Category.PATHING);
        }
        if (workState == WorkState.MOVING_TO_STORAGE
                || workState == WorkState.HAULING
                || workState == WorkState.MOVING_TO_PROCESSING_STORAGE
                || workState == WorkState.MOVING_TO_PROCESSING_OUTPUT_STORAGE
                || lower.contains("storage")
                || lower.contains("chest")
                || lower.contains("haul")
                || lower.contains("carry")
                || lower.contains("deliver here")
                || lower.contains("take from here")) {
            categories.add(SettlementDebug.Category.LOGISTICS);
        }
        if (lower.contains("storage")
                || lower.contains("chest")
                || lower.contains("deposit")
                || lower.contains("withdraw")) {
            categories.add(SettlementDebug.Category.STORAGE);
        }
        if (lower.contains("reserv")) {
            categories.add(SettlementDebug.Category.RESERVATIONS);
        }
        if (workState == WorkState.MOVING_TO_WORKSTATION
                || workState == WorkState.PROCESSING
                || lower.contains("process")
                || lower.contains("machine")
                || lower.contains("workstation")) {
            categories.add(SettlementDebug.Category.PROCESSING);
        }
        if (lower.startsWith("manual order:")
                || lower.contains("take from here")
                || lower.contains("deliver here")) {
            categories.add(SettlementDebug.Category.RTS);
        }

        debug.record(
                "Worker#" + workerId,
                workState + " | " + detail,
                categories.toArray(
                        new SettlementDebug.Category[categories.size()]));

        if (debug.isEnabled(SettlementDebug.Category.WORKER_SPEECH)
                && detail.length() > 0) {
            long now = System.currentTimeMillis();
            if (now - lastDebugSpeechMillis >= DEBUG_SPEECH_MIN_INTERVAL_MS) {
                String speech = detail.length() > 80
                        ? detail.substring(0, 77) + "..." : detail;
                setNextForceTalk(new ForceTalk(speech));
                lastDebugSpeechMillis = now;
            }
        }
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
        if (carriedItemAmount > 0) {
            summary.append(getFactoryItemDisplayName(carriedItemId))
                    .append(" x").append(carriedItemAmount);
        } else if (carriedResource == null || carriedAmount <= 0) {
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
        if (manualTakeStoragePieceId > 0L) {
            summary.append(" | manualTake=chest#").append(manualTakeStoragePieceId);
        }
        if (manualDeliverStoragePieceId > 0L) {
            summary.append(" | manualDeliver=chest#").append(manualDeliverStoragePieceId);
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
