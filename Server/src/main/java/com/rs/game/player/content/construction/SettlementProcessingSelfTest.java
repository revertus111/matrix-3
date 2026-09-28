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
            require(state.getResourceAmount(SettlementResource.WOOD) == 7L,
                    "Wood input was not consumed exactly");
            require(state.getResourceAmount(SettlementResource.PLANKS) == 6L,
                    "Planks output was not produced exactly");

            stage = "input-rollback";
            long woodBefore = state.getResourceAmount(SettlementResource.WOOD);
            long planksBefore = state.getResourceAmount(SettlementResource.PLANKS);
            SettlementProcessingTransaction.Result noInput =
                    SettlementProcessingTransaction.apply(state, recipe, 8);
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

            stage = "physical-machine";
            SettlementFactoryItem logs =
                    SettlementFactoryItem.forResource(SettlementResource.WOOD);
            SettlementFactoryItem planks =
                    SettlementFactoryItem.forResource(SettlementResource.PLANKS);
            require(logs != null && logs.getItemId() == 1511,
                    "physical Logs mapping mismatch");
            require(planks != null && planks.getItemId() == 960,
                    "physical Planks mapping mismatch");

            SettlementMachineBuffer machine = new SettlementMachineBuffer(999L);
            require(machine.addInput(logs.getItemId(), 3) == 3,
                    "could not seed physical machine input");
            SettlementProcessingTransaction.Result physical =
                    SettlementProcessingTransaction.apply(
                            machine, recipe, logs.getItemId(), planks.getItemId(), 2);
            require(physical.isSuccess(),
                    "physical 2-cycle conversion failed: " + physical.getSummary());
            require(machine.getInputAmount(logs.getItemId()) == 1L,
                    "physical machine input was not consumed exactly");
            require(machine.getOutputAmount(planks.getItemId()) == 4L,
                    "physical machine output was not produced exactly");

            stage = "physical-output-rollback";
            long freePhysicalOutput = machine.getOutputCapacityForItem(planks.getItemId());
            require(freePhysicalOutput <= Integer.MAX_VALUE,
                    "physical output capacity exceeds test limits");
            if (freePhysicalOutput > 0L) {
                require(machine.addOutput(
                        planks.getItemId(), (int) freePhysicalOutput)
                        == (int) freePhysicalOutput,
                        "could not fill physical output buffer");
            }
            long physicalInputBefore = machine.getInputAmount(logs.getItemId());
            SettlementProcessingTransaction.Result physicalBlocked =
                    SettlementProcessingTransaction.apply(
                            machine, recipe, logs.getItemId(), planks.getItemId(), 1);
            require(!physicalBlocked.isSuccess(),
                    "full physical output conversion unexpectedly succeeded");
            require(machine.getInputAmount(logs.getItemId()) == physicalInputBefore,
                    "full physical output failure consumed machine input");

            stage = "starter-boundary";
            require(!SettlementResource.PLANKS.isStarterResource(),
                    "Planks became a starter shelter requirement");

            return "PASS: saw-planks legacy + physical machine conversion, rollback, mappings and starter boundary.";
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
