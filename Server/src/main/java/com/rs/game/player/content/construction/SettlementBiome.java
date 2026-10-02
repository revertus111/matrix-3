package com.rs.game.player.content.construction;

import java.util.Random;

/**
 * Deterministic, continuous settlement biome field.
 *
 * Biomes are sampled from smooth low-frequency fields instead of assigning a
 * random biome independently to each chunk. This keeps neighboring chunks
 * spatially coherent and leaves terrain/water projection as a later layer.
 */
public enum SettlementBiome {

    FOREST("Forest", 55, 25, 12, 8),
    MEADOW("Meadow", 35, 40, 15, 10),
    ROCKY("Rocky", 15, 10, 40, 35),
    WETLAND("Wetland", 40, 45, 10, 5);

    private final String displayName;
    private final int woodWeight;
    private final int foodWeight;
    private final int stoneWeight;
    private final int oreWeight;

    SettlementBiome(String displayName, int woodWeight, int foodWeight,
            int stoneWeight, int oreWeight) {
        this.displayName = displayName;
        this.woodWeight = woodWeight;
        this.foodWeight = foodWeight;
        this.stoneWeight = stoneWeight;
        this.oreWeight = oreWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SettlementResourceNode pickResourceDefinition(Random random) {
        if (random == null) {
            return SettlementResourceNode.WOOD_TREE;
        }
        int total = woodWeight + foodWeight + stoneWeight + oreWeight;
        int roll = random.nextInt(total);
        if (roll < woodWeight) {
            return SettlementResourceNode.WOOD_TREE;
        }
        roll -= woodWeight;
        if (roll < foodWeight) {
            return SettlementResourceNode.FOOD_SPOT;
        }
        roll -= foodWeight;
        if (roll < stoneWeight) {
            return SettlementResourceNode.STONE_OUTCROP;
        }
        return SettlementResourceNode.ORE_OUTCROP;
    }

    public static SettlementBiome sampleV1(long seed, int plotX, int plotY) {
        double moisture = smoothField(
                seed ^ 0x4D595DF4D0F33173L, plotX, plotY, 22.0D);
        double elevation = smoothField(
                seed ^ 0x6A09E667F3BCC909L, plotX, plotY, 26.0D);

        if (elevation > 0.66D) {
            return ROCKY;
        }
        if (moisture > 0.70D) {
            return WETLAND;
        }
        if (moisture > 0.48D) {
            return FOREST;
        }
        return MEADOW;
    }

    private static double smoothField(
            long seed, int plotX, int plotY, double scale) {
        double gx = plotX / scale;
        double gy = plotY / scale;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double tx = smoothStep(gx - x0);
        double ty = smoothStep(gy - y0);

        double a = hash01(seed, x0, y0);
        double b = hash01(seed, x1, y0);
        double c = hash01(seed, x0, y1);
        double d = hash01(seed, x1, y1);

        double top = lerp(a, b, tx);
        double bottom = lerp(c, d, tx);
        return lerp(top, bottom, ty);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - (2.0D * value));
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }

    private static double hash01(long seed, int x, int y) {
        long value = seed;
        value ^= ((long) x * 0x9E3779B97F4A7C15L);
        value ^= ((long) y * 0xC2B2AE3D27D4EB4FL);
        value = mix64(value);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
