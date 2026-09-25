package com.rs.game.player.content.construction;

/**
 * Stable settlement processing recipes.
 *
 * Recipes own resource conversion semantics only. Physical workstation
 * placement, worker movement and production scheduling bind to these keys
 * without duplicating input/output math.
 */
public enum SettlementProcessingRecipe {

    SAW_PLANKS("saw-planks", "Saw Planks",
            SettlementResource.WOOD, 2L,
            SettlementResource.PLANKS, 1L);

    private final String key;
    private final String displayName;
    private final SettlementResource inputResource;
    private final long inputAmount;
    private final SettlementResource outputResource;
    private final long outputAmount;

    SettlementProcessingRecipe(String key, String displayName,
            SettlementResource inputResource, long inputAmount,
            SettlementResource outputResource, long outputAmount) {
        this.key = key;
        this.displayName = displayName;
        this.inputResource = inputResource;
        this.inputAmount = inputAmount;
        this.outputResource = outputResource;
        this.outputAmount = outputAmount;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SettlementResource getInputResource() {
        return inputResource;
    }

    public long getInputAmount() {
        return inputAmount;
    }

    public SettlementResource getOutputResource() {
        return outputResource;
    }

    public long getOutputAmount() {
        return outputAmount;
    }

    public String getSummary() {
        return inputAmount + " " + inputResource.getDisplayName()
                + " -> " + outputAmount + " " + outputResource.getDisplayName();
    }

    public static SettlementProcessingRecipe forKey(String key) {
        if (key == null) {
            return null;
        }
        for (SettlementProcessingRecipe recipe : values()) {
            if (recipe.key.equals(key)) {
                return recipe;
            }
        }
        return null;
    }
}
