package com.rs.game.player.content.construction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves stable logistics endpoint references back onto the existing
 * persistent settlement owners.
 *
 * This is an adapter layer only. Chests still own SettlementStorageContainer,
 * machines still own SettlementMachineBuffer and conveyors still own their
 * payloads. Missing/deleted owners resolve to null and therefore block safely.
 */
public final class SettlementLogisticsEndpointResolver {

    public static final String CHEST_INPUT = "INPUT";
    public static final String CHEST_OUTPUT = "OUTPUT";
    public static final String SAWMILL_INPUT_LOGS = "INPUT_LOGS";
    public static final String SAWMILL_OUTPUT_PLANKS = "OUTPUT_PLANKS";
    public static final String CONVEYOR_OUTPUT = "OUTPUT";

    /*
     * PIO-1 anchors the first piece ports to the placed-piece anchor so stable
     * identity/rotation/ownership can exist before final prefab geometry lands.
     * PIO-3 may move the Sawmill local offsets without changing these port keys.
     */
    private static final SettlementLogisticsPortDefinition CHEST_INPUT_DEFINITION =
            new SettlementLogisticsPortDefinition(
                    CHEST_INPUT,
                    SettlementLogisticsEndpoint.Direction.INPUT,
                    0, 0,
                    SettlementLogisticsEndpoint.Facing.NORTH);
    private static final SettlementLogisticsPortDefinition CHEST_OUTPUT_DEFINITION =
            new SettlementLogisticsPortDefinition(
                    CHEST_OUTPUT,
                    SettlementLogisticsEndpoint.Direction.OUTPUT,
                    0, 0,
                    SettlementLogisticsEndpoint.Facing.SOUTH);
    private static final SettlementLogisticsPortDefinition SAWMILL_INPUT_DEFINITION =
            new SettlementLogisticsPortDefinition(
                    SAWMILL_INPUT_LOGS,
                    SettlementLogisticsEndpoint.Direction.INPUT,
                    0, 0,
                    SettlementLogisticsEndpoint.Facing.WEST,
                    SettlementFactoryItem.NORMAL_LOGS.getItemId());
    private static final SettlementLogisticsPortDefinition SAWMILL_OUTPUT_DEFINITION =
            new SettlementLogisticsPortDefinition(
                    SAWMILL_OUTPUT_PLANKS,
                    SettlementLogisticsEndpoint.Direction.OUTPUT,
                    0, 0,
                    SettlementLogisticsEndpoint.Facing.EAST,
                    SettlementFactoryItem.PLANKS.getItemId());

    private SettlementLogisticsEndpointResolver() {
    }

    public static SettlementLogisticsEndpoint resolve(
            SettlementState state, SettlementLogisticsEndpointRef ref) {
        if (state == null || ref == null) {
            return null;
        }
        state.normalize();
        switch (ref.getOwnerType()) {
        case PIECE:
            return resolvePieceEndpoint(state, ref);
        case CONVEYOR:
            return resolveConveyorEndpoint(state, ref);
        default:
            return null;
        }
    }

    public static List<SettlementLogisticsPortDefinition> getPortDefinitions(
            SettlementPlacedPiece piece) {
        if (piece == null) {
            return Collections.emptyList();
        }
        SettlementBuildPiece definition =
                SettlementBuildPiece.forKey(piece.getDefinitionKey());
        if (definition == SettlementBuildPiece.BASIC_STORAGE_CHEST) {
            List<SettlementLogisticsPortDefinition> ports =
                    new ArrayList<SettlementLogisticsPortDefinition>(2);
            ports.add(CHEST_INPUT_DEFINITION);
            ports.add(CHEST_OUTPUT_DEFINITION);
            return ports;
        }
        if (definition == SettlementBuildPiece.WOODEN_WORKBENCH) {
            List<SettlementLogisticsPortDefinition> ports =
                    new ArrayList<SettlementLogisticsPortDefinition>(2);
            ports.add(SAWMILL_INPUT_DEFINITION);
            ports.add(SAWMILL_OUTPUT_DEFINITION);
            return ports;
        }
        return Collections.emptyList();
    }

    private static SettlementLogisticsEndpoint resolvePieceEndpoint(
            SettlementState state, SettlementLogisticsEndpointRef ref) {
        SettlementPlacedPiece piece = findPiece(state, ref.getOwnerId());
        if (piece == null) {
            return null;
        }
        SettlementBuildPiece definition =
                SettlementBuildPiece.forKey(piece.getDefinitionKey());
        if (definition == SettlementBuildPiece.BASIC_STORAGE_CHEST) {
            SettlementStorageContainer container =
                    state.findStorageContainer(piece.getPieceId());
            if (container == null) {
                return null;
            }
            if (CHEST_INPUT.equals(ref.getPortKey())) {
                return new StorageEndpoint(
                        ref, piece, CHEST_INPUT_DEFINITION, container);
            }
            if (CHEST_OUTPUT.equals(ref.getPortKey())) {
                return new StorageEndpoint(
                        ref, piece, CHEST_OUTPUT_DEFINITION, container);
            }
            return null;
        }
        if (definition == SettlementBuildPiece.WOODEN_WORKBENCH) {
            SettlementMachineBuffer buffer =
                    state.findMachineBuffer(piece.getPieceId());
            if (buffer == null) {
                return null;
            }
            if (SAWMILL_INPUT_LOGS.equals(ref.getPortKey())) {
                return new MachineEndpoint(
                        ref, piece, SAWMILL_INPUT_DEFINITION, buffer,
                        SettlementFactoryItem.NORMAL_LOGS.getItemId());
            }
            if (SAWMILL_OUTPUT_PLANKS.equals(ref.getPortKey())) {
                return new MachineEndpoint(
                        ref, piece, SAWMILL_OUTPUT_DEFINITION, buffer,
                        SettlementFactoryItem.PLANKS.getItemId());
            }
        }
        return null;
    }

    private static SettlementLogisticsEndpoint resolveConveyorEndpoint(
            SettlementState state, SettlementLogisticsEndpointRef ref) {
        if (!CONVEYOR_OUTPUT.equals(ref.getPortKey())) {
            return null;
        }
        SettlementConveyorRun run = state.findConveyorRun(ref.getOwnerId());
        if (run == null || !run.isValid()) {
            return null;
        }
        return new ConveyorOutputEndpoint(ref, run);
    }

    private static SettlementPlacedPiece findPiece(
            SettlementState state, long pieceId) {
        if (pieceId <= 0L) {
            return null;
        }
        for (SettlementPlacedPiece piece : state.snapshotPieces()) {
            if (piece != null && piece.getPieceId() == pieceId) {
                return piece;
            }
        }
        return null;
    }

    private abstract static class PieceEndpoint
            implements SettlementLogisticsEndpoint {

        private final SettlementLogisticsEndpointRef ref;
        private final SettlementPlacedPiece piece;
        private final SettlementLogisticsPortDefinition definition;

        private PieceEndpoint(SettlementLogisticsEndpointRef ref,
                SettlementPlacedPiece piece,
                SettlementLogisticsPortDefinition definition) {
            this.ref = ref;
            this.piece = piece;
            this.definition = definition;
        }

        @Override
        public SettlementLogisticsEndpointRef getRef() {
            return ref;
        }

        @Override
        public Direction getDirection() {
            return definition.getDirection();
        }

        @Override
        public int getPlotX() {
            return definition.resolvePlotX(piece);
        }

        @Override
        public int getPlotY() {
            return definition.resolvePlotY(piece);
        }

        @Override
        public int getPlane() {
            return piece.getPlane();
        }

        @Override
        public Facing getFacing() {
            return definition.resolveFacing(piece);
        }

        @Override
        public boolean supportsItem(int itemId) {
            return definition.supportsItem(itemId);
        }
    }

    private static final class StorageEndpoint extends PieceEndpoint {

        private final SettlementStorageContainer container;

        private StorageEndpoint(SettlementLogisticsEndpointRef ref,
                SettlementPlacedPiece piece,
                SettlementLogisticsPortDefinition definition,
                SettlementStorageContainer container) {
            super(ref, piece, definition);
            this.container = container;
        }

        @Override
        public boolean supportsItem(int itemId) {
            if (getDirection() == Direction.INPUT) {
                return super.supportsItem(itemId) && container.acceptsItem(itemId);
            }
            return itemId >= 0;
        }

        @Override
        public long getAvailableAmount(int itemId) {
            return getDirection() == Direction.OUTPUT && itemId >= 0
                    ? container.getItemAmount(itemId) : 0L;
        }

        @Override
        public long getAvailableCapacity(int itemId) {
            return getDirection() == Direction.INPUT && supportsItem(itemId)
                    ? container.getAvailableCapacityForItem(itemId) : 0L;
        }

        @Override
        public boolean canExtract(int itemId, int amount) {
            return amount > 0 && supportsItem(itemId)
                    && getAvailableAmount(itemId) >= amount;
        }

        @Override
        public int extract(int itemId, int amount) {
            return canExtract(itemId, amount)
                    ? container.removeItem(itemId, amount) : 0;
        }

        @Override
        public boolean canAccept(int itemId, int amount) {
            return amount > 0 && supportsItem(itemId)
                    && getAvailableCapacity(itemId) >= amount;
        }

        @Override
        public int accept(int itemId, int amount) {
            return canAccept(itemId, amount)
                    ? container.addItem(itemId, amount) : 0;
        }
    }

    private static final class MachineEndpoint extends PieceEndpoint {

        private final SettlementMachineBuffer buffer;
        private final int itemId;

        private MachineEndpoint(SettlementLogisticsEndpointRef ref,
                SettlementPlacedPiece piece,
                SettlementLogisticsPortDefinition definition,
                SettlementMachineBuffer buffer,
                int itemId) {
            super(ref, piece, definition);
            this.buffer = buffer;
            this.itemId = itemId;
        }

        @Override
        public long getAvailableAmount(int requestedItemId) {
            if (getDirection() != Direction.OUTPUT || requestedItemId != itemId) {
                return 0L;
            }
            return buffer.getOutputAmount(itemId);
        }

        @Override
        public long getAvailableCapacity(int requestedItemId) {
            if (getDirection() != Direction.INPUT || requestedItemId != itemId) {
                return 0L;
            }
            return buffer.getInputCapacityForItem(itemId);
        }

        @Override
        public boolean canExtract(int requestedItemId, int amount) {
            return amount > 0 && requestedItemId == itemId
                    && getAvailableAmount(requestedItemId) >= amount;
        }

        @Override
        public int extract(int requestedItemId, int amount) {
            return canExtract(requestedItemId, amount)
                    ? buffer.removeOutput(itemId, amount) : 0;
        }

        @Override
        public boolean canAccept(int requestedItemId, int amount) {
            return amount > 0 && requestedItemId == itemId
                    && getAvailableCapacity(requestedItemId) >= amount;
        }

        @Override
        public int accept(int requestedItemId, int amount) {
            return canAccept(requestedItemId, amount)
                    ? buffer.addInput(itemId, amount) : 0;
        }
    }

    private static final class ConveyorOutputEndpoint
            implements SettlementLogisticsEndpoint {

        private final SettlementLogisticsEndpointRef ref;
        private final SettlementConveyorRun run;

        private ConveyorOutputEndpoint(
                SettlementLogisticsEndpointRef ref, SettlementConveyorRun run) {
            this.ref = ref;
            this.run = run;
        }

        @Override
        public SettlementLogisticsEndpointRef getRef() {
            return ref;
        }

        @Override
        public Direction getDirection() {
            return Direction.OUTPUT;
        }

        @Override
        public int getPlotX() {
            return run.getEndPlotX();
        }

        @Override
        public int getPlotY() {
            return run.getEndPlotY();
        }

        @Override
        public int getPlane() {
            return run.getPlane();
        }

        @Override
        public Facing getFacing() {
            int fromX = run.isStraight() ? run.getStartPlotX() : run.getBendPlotX();
            int fromY = run.isStraight() ? run.getStartPlotY() : run.getBendPlotY();
            int dx = run.getEndPlotX() - fromX;
            int dy = run.getEndPlotY() - fromY;
            if (dx > 0) {
                return Facing.EAST;
            }
            if (dx < 0) {
                return Facing.WEST;
            }
            if (dy > 0) {
                return Facing.NORTH;
            }
            return Facing.SOUTH;
        }

        @Override
        public boolean supportsItem(int itemId) {
            SettlementConveyorPayload payload = physicalFrontPayload();
            return payload != null && payload.getItemId() == itemId;
        }

        @Override
        public long getAvailableAmount(int itemId) {
            SettlementConveyorPayload payload = physicalFrontPayload();
            return payload != null && payload.getItemId() == itemId
                    ? payload.getAmount() : 0L;
        }

        @Override
        public long getAvailableCapacity(int itemId) {
            return 0L;
        }

        @Override
        public boolean canExtract(int itemId, int amount) {
            SettlementConveyorPayload payload = physicalFrontPayload();
            return payload != null
                    && payload.getItemId() == itemId
                    && payload.getAmount() == amount;
        }

        @Override
        public int extract(int itemId, int amount) {
            SettlementConveyorPayload payload = physicalFrontPayload();
            if (payload == null
                    || payload.getItemId() != itemId
                    || payload.getAmount() != amount) {
                return 0;
            }
            return run.removePayload(payload.getPayloadId()) ? amount : 0;
        }

        @Override
        public boolean canAccept(int itemId, int amount) {
            return false;
        }

        @Override
        public int accept(int itemId, int amount) {
            return 0;
        }

        private SettlementConveyorPayload physicalFrontPayload() {
            SettlementConveyorPayload payload = run.getFrontPayloadAtOutput();
            return payload != null && payload.isPhysicalInventoryOwned()
                    ? payload : null;
        }
    }
}
