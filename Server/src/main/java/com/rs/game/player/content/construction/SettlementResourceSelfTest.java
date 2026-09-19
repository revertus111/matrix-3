package com.rs.game.player.content.construction;

import java.util.HashSet;
import java.util.Set;

/**
 * Disposable Bundle 1.3 confidence test for settlement-only resource storage
 * and starter node definitions. It never reads or mutates a Player save.
 */
public final class SettlementResourceSelfTest {

    private SettlementResourceSelfTest() {
    }

    public static String run() {
        String stage = "storage";
        try {
            SettlementState state = new SettlementState();
            state.normalize();

            require(state.getStorageCapacity() == SettlementState.STARTER_STORAGE_CAPACITY,
                    "starter storage capacity mismatch");
            require(state.getTotalStoredResources() == 0L, "new storage is not empty");

            for (SettlementResource resource : SettlementResource.values()) {
                require(state.addResource(resource, 10L) == 10L,
                        "failed to add " + resource.getKey());
                require(state.getResourceAmount(resource) == 10L,
                        "stored amount mismatch for " + resource.getKey());
            }
            require(state.getTotalStoredResources() == 40L, "total storage mismatch");

            require(state.removeResource(SettlementResource.WOOD, 4L) == 4L,
                    "wood removal failed");
            require(state.getResourceAmount(SettlementResource.WOOD) == 6L,
                    "wood removal amount mismatch");
            require(state.removeResource(SettlementResource.WOOD, 100L) == 6L,
                    "bounded wood removal failed");
            require(state.getResourceAmount(SettlementResource.WOOD) == 0L,
                    "wood should be empty");

            long remaining = state.getStorageRemaining();
            require(remaining > 0L, "storage unexpectedly full");
            require(state.addResource(SettlementResource.STONE, remaining + 50L) == remaining,
                    "storage capacity clamp failed");
            require(state.getTotalStoredResources() == state.getStorageCapacity(),
                    "storage did not reach exact capacity");
            require(state.addResource(SettlementResource.FOOD, 1L) == 0L,
                    "full storage accepted extra resource");

            stage = "nodes";
            Set<String> tiles = new HashSet<String>();
            Set<String> keys = new HashSet<String>();
            for (SettlementResourceNode node : SettlementResourceNode.values()) {
                require(node != null, "null resource node");
                require(node.getResource() != null, "node has no resource");
                require(node.getRuntimeId() >= 0, "node has invalid runtime id");
                require(SettlementState.isValidPlotLocation(
                        node.getPlotX(), node.getPlotY(), SettlementState.PLOT_PLANE),
                        "node lies outside settlement plot");
                require(keys.add(node.getKey()), "duplicate node key " + node.getKey());
                String tileKey = node.getPlotX() + ":" + node.getPlotY();
                require(tiles.add(tileKey), "duplicate node plot tile " + tileKey);
                require(SettlementResourceNode.occupiesPlotTile(
                        node.getPlotX(), node.getPlotY(), SettlementState.PLOT_PLANE),
                        "reserved node tile lookup failed");
            }

            require(SettlementResourceNode.values().length == SettlementResource.values().length,
                    "starter node/resource count mismatch");

            return "PASS: storage add/remove/capacity + 4 starter-node definitions.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
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
