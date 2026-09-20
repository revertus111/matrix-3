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

            int expectedTotalCapacity = 0;
            for (SettlementResource resource : SettlementResource.values()) {
                require(state.getStorageCapacity(resource)
                        == resource.getStarterStorageCapacity(),
                        "starter storage capacity mismatch for " + resource.getKey());
                require(state.getStorageRemaining(resource)
                        == resource.getStarterStorageCapacity(),
                        "starter storage remaining mismatch for " + resource.getKey());
                expectedTotalCapacity += resource.getStarterStorageCapacity();
            }
            require(state.getTotalStorageCapacity() == expectedTotalCapacity,
                    "aggregate starter storage capacity mismatch");
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

            long woodCapacity = state.getStorageCapacity(SettlementResource.WOOD);
            require(state.addResource(SettlementResource.WOOD, woodCapacity + 50L)
                    == woodCapacity,
                    "Wood storage capacity clamp failed");
            require(state.getStorageRemaining(SettlementResource.WOOD) == 0L,
                    "Wood storage should be full");
            require(!state.hasStorageSpace(SettlementResource.WOOD),
                    "Wood storage incorrectly reports free space");
            require(state.addResource(SettlementResource.WOOD, 1L) == 0L,
                    "full Wood storage accepted extra Wood");

            long foodBefore = state.getResourceAmount(SettlementResource.FOOD);
            require(state.hasStorageSpace(SettlementResource.FOOD),
                    "Food storage incorrectly reports full");
            require(state.addResource(SettlementResource.FOOD, 1L) == 1L,
                    "full Wood storage incorrectly blocked Food");
            require(state.getResourceAmount(SettlementResource.FOOD) == foodBefore + 1L,
                    "Food amount did not increase independently");
            require(state.getStorageRemaining(SettlementResource.FOOD) > 0L,
                    "Food storage unexpectedly full");

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

            return "PASS: per-resource storage add/remove/capacity isolation + 4 starter-node definitions.";
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
