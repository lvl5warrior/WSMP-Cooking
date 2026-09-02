package com.warriorssmp.cooking.listener;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.data.PlayerGatherData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

/**
 * Cooking spans two different vanilla actions, unlike the other skill
 * plugins: some dishes are CRAFTED (Cake, Cookies, Mushroom Stew, Golden
 * Apple...) and some are SMELTED (Cooked Beef, Baked Potato, Dried Kelp...
 * via Furnace, Smoker, or Campfire). Both hooks feed the same progress
 * pipeline. Campfire cooking specifically isn't tracked — vanilla doesn't
 * fire an extraction event for it the way furnaces/smokers do (cooked items
 * just pop off as physical item entities), so campfire-cooked dishes won't
 * count toward contracts. Furnace and Smoker are unaffected and cover the
 * same recipes either way.
 */
public final class GatherListener implements Listener {

    private final CookingPlugin plugin;

    public GatherListener(CookingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() == Material.AIR) return;

        handleCook(player, result, recipeBookKeyFor(result));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Material material = event.getItemType();
        int amount = event.getItemAmount();
        if (amount <= 0) return;

        ItemStack result = new ItemStack(material, amount);
        handleCook(player, result, material.name());
    }

    private void handleCook(Player player, ItemStack result, String recipeBookKey) {
        Material material = result.getType();
        int amount = result.getAmount();

        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());

        // Legendary Requests (Tier 7) track independently of the normal tier pool.
        plugin.legendaryRequestService().addProgress(player, data, material, amount);

        int tier = plugin.gatherConfig().tierOfMaterial(material);
        if (tier == -1) return; // not a tracked dish

        // Recipe Book — every distinct dish (or, for Suspicious Stew, every
        // distinct effect) discovered gets logged here, independent of tasks/tiers.
        data.recipeBookDiscovered.add(recipeBookKey);
        data.recipeBookCookCounts.merge(recipeBookKey, amount, Integer::sum);

        // Perfect Dish rolls on every relevant cook, independent of the active task.
        plugin.luckyStrikeService().roll(player, data, material, tier);
        plugin.taskService().addProgress(player, data, material, amount);
    }

    /** Suspicious Stew is one Material regardless of which flower made it — the
     *  effect is stored in the item's meta, so read it to give each flower its
     *  own Recipe Book entry (matching the design doc's "collection tier" for
     *  Tier 4). Everything else is just keyed by material name. */
    private String recipeBookKeyFor(ItemStack stack) {
        if (stack.getType() == Material.SUSPICIOUS_STEW && stack.getItemMeta() instanceof SuspiciousStewMeta meta) {
            for (PotionEffect effect : meta.getCustomEffects()) {
                return "SUSPICIOUS_STEW:" + effect.getType().getName();
            }
        }
        return stack.getType().name();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Pre-warm the cache so menus open instantly.
        plugin.dataStore().get(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.dataStore().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractMasterNpc(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        NamespacedKey npcKey = new NamespacedKey(plugin, "chef_npc");
        if (Boolean.TRUE.equals(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            plugin.menuManager().openMasterMenu(event.getPlayer());
        }
    }
}
