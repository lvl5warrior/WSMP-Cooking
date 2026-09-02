package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.data.PlayerGatherData;
import com.warriorssmp.cooking.economy.EconomyService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ShopService {

    private final GatherConfig config;
    private final DataStore dataStore;
    private final EconomyService economy;
    private final PremiumService premium;

    public ShopService(GatherConfig config, DataStore dataStore, EconomyService economy, PremiumService premium) {
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
        this.premium = premium;
    }

    public enum Result {SUCCESS, NO_PERMISSION, ALREADY_OWNED, INSUFFICIENT_FUNDS, UNKNOWN_ITEM}

    public Result purchase(Player player, PlayerGatherData data, String itemId) {
        GatherConfig.ShopItem item = config.shopItem(itemId);
        if (item == null) return Result.UNKNOWN_ITEM;

        if (item.premium() && !premium.isPremium(player)) {
            return Result.NO_PERMISSION;
        }

        int key = itemId.hashCode();
        if (item.limit() > 0 && data.purchasedOneTimeItems.contains(key)) {
            return Result.ALREADY_OWNED;
        }

        long cost = Math.round(item.cost());
        if (data.points < cost) {
            return Result.INSUFFICIENT_FUNDS;
        }
        data.points -= cost;

        applyEffect(player, data, itemId);

        if (item.limit() > 0) {
            data.purchasedOneTimeItems.add(key);
        }

        return Result.SUCCESS;
    }

    private void applyEffect(Player player, PlayerGatherData data, String itemId) {
        long now = System.currentTimeMillis();
        switch (itemId) {
            case "task_skip" -> {} // handled by TaskService.skipTask directly, not routed through here
            // Two-tier XP buff, strongest-active-wins (see TaskService#xpBuffMultiplier).
            case "xp_boost" -> data.xpBoostExpiry = Math.max(data.xpBoostExpiry, now) + 15 * 60_000L;
            case "xp_boost_large" -> data.xpBoostLargeExpiry = Math.max(data.xpBoostLargeExpiry, now) + 3_600_000L;
            case "point_boost" -> data.pointBoostExpiry = Math.max(data.pointBoostExpiry, now) + 3_600_000L;
            case "better_tasks" -> data.betterTasksExpiry = Math.max(data.betterTasksExpiry, now) + 3_600_000L;
            case "master_teleport" -> data.masterTeleportUnlocked = true;
            case "cooking_title" -> {
                data.hasCookingTitle = true;
                player.sendMessage("§6§lYou unlocked the [Chef] title! §7(Chat-only for now — full TAB integration isn't built yet.)");
            }
            case "swiftness_potion" -> givePotion(player, PotionEffectType.SPEED, 3 * 60 * 20, 0);
            case "night_vision_potion" -> givePotion(player, PotionEffectType.NIGHT_VISION, 3 * 60 * 20, 0);
            case "fire_resistance_potion" -> givePotion(player, PotionEffectType.FIRE_RESISTANCE, 3 * 60 * 20, 0);
            case "regeneration_potion" -> givePotion(player, PotionEffectType.REGENERATION, 45 * 20, 0);
            case "flower_bundle" -> giveBundle(player, Material.POPPY, 4, Material.DANDELION, 4, Material.BLUE_ORCHID, 4, Material.ALLIUM, 4);
            case "cocoa_beans_bundle" -> player.getInventory().addItem(new ItemStack(Material.COCOA_BEANS, 32));
            case "honey_bottle_bundle" -> player.getInventory().addItem(new ItemStack(Material.HONEY_BOTTLE, 8));
            default -> {
                // Anything else (fallback) — give one of the configured item.
                GatherConfig.ShopItem item = config.shopItem(itemId);
                if (item != null) {
                    player.getInventory().addItem(new ItemStack(
                            com.warriorssmp.cooking.model.IconUtil.safeIcon(item.material())));
                }
            }
        }
    }

    private void givePotion(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, durationTicks, amplifier), true);
        potion.setItemMeta(meta);
        player.getInventory().addItem(potion);
    }

    /** Gives a mix of items totalling the "bundle" — used for the Tier 4 flower
     *  bundle so it's a genuine mix rather than 16 of one flower. */
    private void giveBundle(Player player, Object... materialAmountPairs) {
        for (int i = 0; i < materialAmountPairs.length; i += 2) {
            Material material = (Material) materialAmountPairs[i];
            int amount = (Integer) materialAmountPairs[i + 1];
            player.getInventory().addItem(new ItemStack(material, amount));
        }
    }
}
