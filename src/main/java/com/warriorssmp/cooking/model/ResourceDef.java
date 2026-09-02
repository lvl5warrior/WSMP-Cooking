package com.warriorssmp.cooking.model;

import org.bukkit.Material;

public final class ResourceDef {

    public enum GatherType { MINING, WOODCUTTING, FARMING, FISHING, COOKING, PROCESSED }

    private final Material material;
    private final int tier;
    private final GatherType type;
    private final int requiredLevel;
    private final int minAmount;
    private final int maxAmount;
    /** Perfect Dish output-doubling is item duplication — gold/diamond/netherite
     *  recipes (Golden Carrot, Golden Apple, Glistering Melon Slice) must have
     *  this off, or a Perfect roll quietly duplicates gold ingots/nuggets. */
    private final boolean canDouble;

    public ResourceDef(Material material, int tier, GatherType type, int requiredLevel, int minAmount, int maxAmount, boolean canDouble) {
        this.material = material;
        this.tier = tier;
        this.type = type;
        this.requiredLevel = requiredLevel;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.canDouble = canDouble;
    }

    public Material material() {
        return material;
    }

    public int tier() {
        return tier;
    }

    public GatherType type() {
        return type;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public int minAmount() {
        return minAmount;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public boolean canDouble() {
        return canDouble;
    }

    public int rollAmount(java.util.random.RandomGenerator rng) {
        if (maxAmount <= minAmount) return minAmount;
        return minAmount + rng.nextInt(maxAmount - minAmount + 1);
    }

    public String displayName() {
        String raw = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
