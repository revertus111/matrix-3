package com.rs.game.player.content.construction;

import com.rs.game.Animation;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;

/**
 * Transient Matrix3 NPC projection for one persistent settlement worker.
 *
 * Persistent policy remains in SettlementWorkerState. This runtime projection
 * owns only the worker's current movement/action/carry state and is discarded
 * with the settlement instance.
 */
public final class SettlementWorkerNpc extends NPC {

    private static final long serialVersionUID = 4457138259614473421L;

    private static final int GATHER_TICKS = 3;
    private static final int CARRY_CAPACITY = 1;

    private enum WorkState {
        IDLE,
        MOVING_TO_RESOURCE,
        GATHERING,
        MOVING_TO_STORAGE,
        HAULING
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

        if (carriedAmount > 0) {
            processCarriedResource();
            return;
        }

        if (gatherTicksRemaining > 0) {
            processGathering();
            return;
        }

        if (targetNode != null) {
            SettlementWorkerJob job = findGatherJob(targetNode.getResource());
            if (job == null || !workerState.isJobAllowed(job)
                    || !settlement.isStarterResourceNodeAvailable(targetNode)) {
                clearTarget();
            }
        }

        if (targetNode == null) {
            targetNode = selectNextGatherNode();
            if (targetNode == null) {
                idle(hasAllowedGatheringJob()
                        ? "No allowed resource node is currently available."
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

        gatherTicksRemaining--;
        if (gatherTicksRemaining > 0) {
            return;
        }

        carriedResource = targetNode.getResource();
        carriedAmount = CARRY_CAPACITY;
        nextGatherIndex = (targetNode.ordinal() + 1) % SettlementResourceNode.values().length;
        statusDetail = "Gathered " + carriedResource.getDisplayName() + "; awaiting haul.";
        targetNode = null;
        workState = WorkState.IDLE;
    }

    private void processCarriedResource() {
        if (!workerState.isJobAllowed(SettlementWorkerJob.HAUL)) {
            resetWalkSteps();
            workState = WorkState.IDLE;
            statusDetail = "Haul disabled; holding " + carriedResource.getDisplayName() + ".";
            return;
        }
        if (settlement.getWorkerStorageRemaining() <= 0L) {
            resetWalkSteps();
            workState = WorkState.IDLE;
            statusDetail = "Storage full; holding " + carriedResource.getDisplayName() + ".";
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
        long added = settlement.depositWorkerResource(carriedResource, carriedAmount);
        if (added <= 0L) {
            idle("Storage full; holding " + carriedResource.getDisplayName() + ".");
            return;
        }

        carriedAmount -= (int) added;
        if (carriedAmount <= 0) {
            String deposited = carriedResource.getDisplayName();
            carriedResource = null;
            carriedAmount = 0;
            workState = WorkState.IDLE;
            statusDetail = "Deposited " + deposited + ".";
        }
    }

    private void beginGathering() {
        resetWalkSteps();
        workState = WorkState.GATHERING;
        gatherTicksRemaining = GATHER_TICKS;
        statusDetail = "Gathering " + targetNode.getResource().getDisplayName() + ".";
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
            /*
             * Matrix3's intelligent calcFollow uses ObjectStrategy / EntityStrategy
             * for live targets. A successful route with zero queued steps means
             * the strategy already considers this tile interaction-ready.
             * Do not replace that footprint/access result with anchor-tile distance.
             */
            if (!hasWalkSteps()) {
                return true;
            }
        }
        return false;
    }

    private void clearTarget() {
        resetWalkSteps();
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
        return summary.toString();
    }
}
