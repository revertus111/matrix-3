package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Disposable confidence test for the starter shelter milestone.
 *
 * It exercises the real SettlementState owner and never touches a Player save.
 */
public final class SettlementShelterSelfTest {

    private SettlementShelterSelfTest() {
    }

    public static String run() {
        String stage = "setup";
        try {
            SettlementState state = new SettlementState();
            state.normalize();

            require(!state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER),
                    "new settlement already has starter shelter milestone");
            require(!state.meetsStarterShelterRequirements(),
                    "new settlement unexpectedly meets shelter requirements");

            stage = "build";
            for (int index = 0; index < SettlementState.STARTER_SHELTER_WALLS; index++) {
                require(state.place(
                        SettlementBuildPiece.WOOD_FENCE_TEST,
                        30 + index, 30, SettlementState.PLOT_PLANE, index & 0x3) != null,
                        "failed to place required wall " + index);
            }
            for (int index = 0; index < SettlementState.STARTER_SHELTER_FLOORS; index++) {
                require(state.place(
                        SettlementBuildPiece.FLOOR_DECORATION,
                        30 + index, 30, SettlementState.PLOT_PLANE, 0) != null,
                        "failed to place required floor " + index);
            }
            require(state.place(
                    SettlementBuildPiece.BASIC_DOOR,
                    34, 30, SettlementState.PLOT_PLANE, 0) != null,
                    "failed to place required doorway");

            require(!state.meetsStarterShelterRequirements(),
                    "build pieces completed milestone without starter resources");
            require(!state.tryCompleteStarterShelterMilestone(),
                    "milestone completed before resource requirements");

            stage = "resources";
            for (SettlementResource resource : SettlementResource.values()) {
                if (!resource.isStarterResource()) {
                    continue;
                }
                require(state.addResource(
                        resource, SettlementState.STARTER_SHELTER_RESOURCE_EACH)
                        == SettlementState.STARTER_SHELTER_RESOURCE_EACH,
                        "failed to add required " + resource.getKey());
            }

            require(state.meetsStarterShelterRequirements(),
                    "qualifying settlement does not meet shelter requirements");
            require(state.tryCompleteStarterShelterMilestone(),
                    "qualifying settlement did not newly complete milestone");
            require(state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER),
                    "milestone flag missing after completion");
            require(!state.tryCompleteStarterShelterMilestone(),
                    "completed milestone fired twice");

            stage = "latch";
            SettlementPlacedPiece wall = state.find(
                    SettlementBuildPiece.WOOD_FENCE_TEST.getObjectId(),
                    30, 30, SettlementState.PLOT_PLANE);
            require(wall != null && state.remove(wall.getPieceId()) != null,
                    "failed to remove qualifying wall");
            require(state.removeResource(SettlementResource.FOOD, 1L) == 1L,
                    "failed to remove qualifying food");
            require(!state.meetsStarterShelterRequirements(),
                    "reduced settlement still meets requirements unexpectedly");
            require(state.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER),
                    "completed milestone was revoked");

            stage = "serialize";
            byte[] encoded = serialize(state);
            SettlementState restored = deserialize(encoded);
            restored.normalize();
            require(restored.isMilestoneComplete(SettlementMilestone.STARTER_SHELTER),
                    "completed milestone did not survive serialization");

            return "PASS: shelter thresholds + one-time latch + serialization.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
        }
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
            Object value = input.readObject();
            require(value instanceof SettlementState,
                    "deserialized value is not SettlementState");
            return (SettlementState) value;
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
