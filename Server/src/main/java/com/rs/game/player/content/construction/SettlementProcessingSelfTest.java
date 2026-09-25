package com.rs.game.player.content.construction;

/**
 * Disposable Phase-3 processing-core confidence test.
 *
 * Uses only a fresh SettlementState and never reads or mutates a Player save.
 */
public final class SettlementProcessingSelfTest {

    private SettlementProcessingSelfTest() {
    }

    public static String run() {
        String stage = "setup";
        try {
            SettlementState state = new SettlementState();
            state.normalize();
            SettlementProcessingRecipe recipe = SettlementProcessingRecipe.SAW_PLANKS;

            require(SettlementProcessingRecipe.forKey("saw-planks") == recipe,
                    "recipe key lookup failed");
            require(state.addResource(SettlementResource.WOOD, 10L) == 10L,
                    "could not seed Wood");

            stage = "success";
            SettlementProcessingTransaction.Result success =
                    SettlementProcessingTransaction.apply(state, recipe, 3);
            require(success.isSuccess(), "3-cycle conversion failed: " + success.getSummary());
            require(state.getResourceAmount(SettlementResource.WOOD) == 4L,
                    "Wood input was not consumed exactly");
            require(state.getResourceAmount(SettlementResource.PLANKS) == 3L,
                    "Planks output was not produced exactly");

            stage = "input-rollback";
            long woodBefore = state.getResourceAmount(SettlementResource.WOOD);
            long planksBefore = state.getResourceAmount(SettlementResource.PLANKS);
            SettlementProcessingTransaction.Result noInput =
                    SettlementProcessingTransaction.apply(state, recipe, 3);
            require(!noInput.isSuccess(), "insufficient-input conversion unexpectedly succeeded");
            require(state.getResourceAmount(SettlementResource.WOOD) == woodBefore,
                    "failed input check mutated Wood");
            require(state.getResourceAmount(SettlementResource.PLANKS) == planksBefore,
                    "failed input check mutated Planks");

            stage = "output-rollback";
            long remaining = state.getStorageRemaining(SettlementResource.PLANKS);
            require(state.addResource(SettlementResource.PLANKS, remaining) == remaining,
                    "could not fill Planks storage");
            require(state.addResource(SettlementResource.WOOD, 2L) == 2L,
                    "could not seed final Wood");
            woodBefore = state.getResourceAmount(SettlementResource.WOOD);
            SettlementProcessingTransaction.Result noOutput =
                    SettlementProcessingTransaction.apply(state, recipe, 1);
            require(!noOutput.isSuccess(), "full-output conversion unexpectedly succeeded");
            require(state.getResourceAmount(SettlementResource.WOOD) == woodBefore,
                    "full-output failure consumed Wood");
            require(state.getResourceAmount(SettlementResource.PLANKS)
                    == state.getStorageCapacity(SettlementResource.PLANKS),
                    "full-output failure changed Planks");

            stage = "starter-boundary";
            require(!SettlementResource.PLANKS.isStarterResource(),
                    "Planks became a starter shelter requirement");

            return "PASS: saw-planks atomic conversion + input/output rollback + starter-resource boundary.";
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
