package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.data.PlayerGatherData;
import com.warriorssmp.cooking.economy.EconomyService;
import com.warriorssmp.cooking.model.IconUtil;
import com.warriorssmp.cooking.model.PointsUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Map;
import java.util.Random;

/**
 * Perfect Dish — the design doc's version of Lucky Strike. Since vanilla food
 * has no buffs to extend, a Perfect roll pays out in points and (usually)
 * doubled output instead. Doubling output is item duplication, though — on
 * bread that's harmless, but on a Golden Apple it duplicates eight gold
 * ingots. Recipes with gold/diamond/netherite (see GatherConfig#canDouble,
 * driven by "can-double: false" in config.yml) skip the duplication entirely
 * and get a bigger points bonus instead, so the economy can't leak through
 * a cooking mechanic.
 */
public final class LuckyStrikeService {

    private final CookingPlugin plugin;
    private final GatherConfig config;
    private final DataStore dataStore;
    private final EconomyService economy;
    private final PremiumService premium;
    private final Random random = new Random();

    public LuckyStrikeService(CookingPlugin plugin, GatherConfig config, DataStore dataStore, EconomyService economy, PremiumService premium) {
        this.plugin = plugin;
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
        this.premium = premium;
    }

    public boolean roll(Player player, PlayerGatherData data, Material material, int tierOfMaterial) {
        double chance = config.luckyStrikeChance(tierOfMaterial);

        // Boosted Perfect Dish odds is a passive premium perk, not a purchasable
        // timed buff, per the design doc.
        if (premium.isPremium(player)) {
            chance *= 1.10;
        }

        if (random.nextDouble() >= chance) {
            return false;
        }

        data.lifetimeLuckyStrikes++;
        String resourceName = IconUtil.safeIcon(material).name().toLowerCase().replace('_', ' ');
        boolean canDouble = config.canDouble(material);
        long bonusPoints = tierOfMaterial * (canDouble ? 5L : 15L); // no-double dishes pay more to compensate

        data.points += bonusPoints;

        String rewardText;
        if (canDouble) {
            ItemStack bonus = new ItemStack(IconUtil.safeIcon(material), 1);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(bonus);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            rewardText = "Output Doubled • +" + PointsUtil.format(bonusPoints);
        } else {
            rewardText = "+" + PointsUtil.format(bonusPoints) + " (no duplication — gold recipe)";
        }

        player.sendActionBar(Component.text("§e🌟 PERFECT DISH! §7" + rewardText));
        player.showTitle(Title.title(
                Component.text("§e🌟 PERFECT DISH!"),
                Component.text("§7Your " + resourceName + " came out Perfect!"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))
        ));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);

        return true;
    }
}
