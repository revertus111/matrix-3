package com.rs.game.player.content.construction;

/**
 * Server-authoritative atomic resource conversion for settlement processing.
 *
 * This owner deliberately has no world-object or NPC dependency. Workstations
 * and workers will call this transaction after their own placement/range/job
 * validation, keeping storage mutation centralized and testable.
 */
public final class SettlementProcessingTransaction {

    private static final int MAX_CYCLES_PER_TRANSACTION = 100000;

    private SettlementProcessingTransaction() {
    }

    public static Result apply(SettlementState state,
            SettlementProcessingRecipe recipe, int cycles) {
        if (state == null) {
            return Result.fail("Settlement state is unavailable.");
        }
        if (recipe == null) {
            return Result.fail("Unknown settlement processing recipe.");
        }
        if (cycles <= 0 || cycles > MAX_CYCLES_PER_TRANSACTION) {
            return Result.fail("Processing cycles must be between 1 and "
                    + MAX_CYCLES_PER_TRANSACTION + ".");
        }

        final long inputNeeded;
        final long outputProduced;
        try {
            inputNeeded = Math.multiplyExact(recipe.getInputAmount(), (long) cycles);
            outputProduced = Math.multiplyExact(recipe.getOutputAmount(), (long) cycles);
        } catch (ArithmeticException overflow) {
            return Result.fail("Processing amount is too large.");
        }

        synchronized (state) {
            if (state.getResourceAmount(recipe.getInputResource()) < inputNeeded) {
                return Result.fail("Need " + inputNeeded + " "
                        + recipe.getInputResource().getDisplayName()
                        + " for " + cycles + " cycle(s).");
            }
            if (state.getStorageRemaining(recipe.getOutputResource()) < outputProduced) {
                return Result.fail("Need " + outputProduced + " free "
                        + recipe.getOutputResource().getDisplayName()
                        + " storage for " + cycles + " cycle(s).");
            }

            long removed = state.removeResource(recipe.getInputResource(), inputNeeded);
            if (removed != inputNeeded) {
                if (removed > 0L) {
                    state.addResource(recipe.getInputResource(), removed);
                }
                return Result.fail("Processing inputs changed before conversion completed.");
            }

            long added = state.addResource(recipe.getOutputResource(), outputProduced);
            if (added != outputProduced) {
                if (added > 0L) {
                    state.removeResource(recipe.getOutputResource(), added);
                }
                state.addResource(recipe.getInputResource(), removed);
                return Result.fail("Processing output storage changed before conversion completed.");
            }

            return Result.success(recipe, cycles, removed, added);
        }
    }

    public static final class Result {
        private final boolean success;
        private final String message;
        private final SettlementProcessingRecipe recipe;
        private final int cycles;
        private final long inputConsumed;
        private final long outputProduced;

        private Result(boolean success, String message,
                SettlementProcessingRecipe recipe, int cycles,
                long inputConsumed, long outputProduced) {
            this.success = success;
            this.message = message;
            this.recipe = recipe;
            this.cycles = cycles;
            this.inputConsumed = inputConsumed;
            this.outputProduced = outputProduced;
        }

        private static Result fail(String message) {
            return new Result(false, message, null, 0, 0L, 0L);
        }

        private static Result success(SettlementProcessingRecipe recipe,
                int cycles, long inputConsumed, long outputProduced) {
            return new Result(true, null, recipe, cycles, inputConsumed, outputProduced);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public SettlementProcessingRecipe getRecipe() {
            return recipe;
        }

        public int getCycles() {
            return cycles;
        }

        public long getInputConsumed() {
            return inputConsumed;
        }

        public long getOutputProduced() {
            return outputProduced;
        }

        public String getSummary() {
            if (!success || recipe == null) {
                return message == null ? "Processing failed." : message;
            }
            return recipe.getDisplayName() + " x" + cycles
                    + ": consumed " + inputConsumed + " "
                    + recipe.getInputResource().getDisplayName()
                    + ", produced " + outputProduced + " "
                    + recipe.getOutputResource().getDisplayName() + ".";
        }
    }
}
