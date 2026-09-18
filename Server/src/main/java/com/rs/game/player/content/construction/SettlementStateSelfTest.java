package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Disposable deterministic confidence test for the persistent settlement-state
 * owner. It never reads or mutates a Player's real SettlementState.
 */
public final class SettlementStateSelfTest {

    private SettlementStateSelfTest() {
    }

    public static String run() {
        String stage = "create";
        try {
            SettlementState state = new SettlementState();
            state.normalize();
            require(state.size() == 0, "new state is not empty");

            stage = "place";
            SettlementPlacedPiece wall = state.place(
                    SettlementBuildPiece.WOOD_FENCE_TEST, 10, 10, 0, 0);
            require(wall != null, "wall placement failed");
            require(state.size() == 1, "wall placement count mismatch");

            stage = "occupancy";
            SettlementPlacedPiece blockedWall = state.place(
                    SettlementBuildPiece.BASIC_DOOR, 10, 10, 0, 0);
            require(blockedWall == null, "same-layer overlap was accepted");

            SettlementPlacedPiece floor = state.place(
                    SettlementBuildPiece.FLOOR_DECORATION, 10, 10, 0, 0);
            require(floor != null, "compatible floor/wall coexistence failed");
            require(state.size() == 2, "coexistence count mismatch");

            stage = "rotate";
            SettlementPlacedPiece rotated = state.rotate(wall.getPieceId(), 1);
            require(rotated != null && rotated.getRotation() == 1,
                    "rotation mutation failed");

            stage = "move";
            SettlementPlacedPiece moved = state.move(wall.getPieceId(), 11, 10, 0);
            require(moved != null && moved.getPlotX() == 11 && moved.getPlotY() == 10,
                    "move mutation failed");

            stage = "duplicate";
            SettlementPlacedPiece duplicate = state.duplicate(wall.getPieceId(), 12, 10, 0);
            require(duplicate != null, "duplicate mutation failed");
            require(duplicate.getPieceId() != wall.getPieceId(),
                    "duplicate reused stable piece id");
            require(duplicate.getRotation() == 1,
                    "duplicate did not preserve rotation");
            require(state.size() == 3, "duplicate count mismatch");

            SettlementPlacedPiece blockedMove = state.move(
                    wall.getPieceId(), 12, 10, 0);
            require(blockedMove == null, "move into occupied same-layer slot was accepted");

            stage = "delete";
            SettlementPlacedPiece removedFloor = state.remove(floor.getPieceId());
            require(removedFloor != null, "delete mutation failed");
            require(state.size() == 2, "delete count mismatch");

            stage = "serialize";
            byte[] encoded = serialize(state);
            require(encoded.length > 0, "serialization produced no data");

            SettlementState restored = deserialize(encoded);
            restored.normalize();
            require(restored.size() == 2, "restored count mismatch");

            SettlementPlacedPiece restoredWall = restored.find(
                    SettlementBuildPiece.WOOD_FENCE_TEST.getObjectId(), 11, 10, 0);
            require(restoredWall != null, "moved wall missing after restore");
            require(restoredWall.getRotation() == 1, "wall rotation changed after restore");

            SettlementPlacedPiece restoredDuplicate = restored.find(
                    SettlementBuildPiece.WOOD_FENCE_TEST.getObjectId(), 12, 10, 0);
            require(restoredDuplicate != null, "duplicate missing after restore");
            require(restoredDuplicate.getPieceId() != restoredWall.getPieceId(),
                    "restored stable ids collided");

            SettlementPlacedPiece removedFloorAfterRestore = restored.find(
                    SettlementBuildPiece.FLOOR_DECORATION.getObjectId(), 10, 10, 0);
            require(removedFloorAfterRestore == null, "deleted floor returned after restore");

            return "PASS: place/occupancy/rotate/move/duplicate/delete/serialization.";
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
            require(value instanceof SettlementState, "deserialized value is not SettlementState");
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
