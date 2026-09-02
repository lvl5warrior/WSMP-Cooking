package com.warriorssmp.cooking.model;

import org.bukkit.Material;

/**
 * A handful of resources are tracked as their BLOCK form (e.g. CARROTS/POTATOES,
 * the growing-crop materials used to detect BlockBreakEvent) but have a separate
 * ITEM form for anything that needs to construct an ItemStack — GUI icons, giving
 * a physical item, etc. Most materials (ores, logs, wheat) double as both and need
 * no mapping; this only covers the ones that don't.
 */
public final class IconUtil {

    private IconUtil() {}

    public static Material safeIcon(Material material) {
        if (material.isItem()) return material;
        return switch (material) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case WHEAT -> Material.WHEAT; // shares the same ID, kept for clarity
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case COCOA -> Material.COCOA_BEANS;
            case CAVE_VINES, CAVE_VINES_PLANT -> Material.GLOW_BERRIES;
            case TWISTING_VINES_PLANT -> Material.TWISTING_VINES;
            case WEEPING_VINES_PLANT -> Material.WEEPING_VINES;
            case CHORUS_FLOWER, CHORUS_PLANT -> Material.CHORUS_FRUIT;
            default -> Material.PAPER; // safe fallback so a future block-only entry never crashes a menu
        };
    }
}
