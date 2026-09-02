package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.model.GatherTier;
import com.warriorssmp.cooking.model.ResourceDef;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class GatherConfig {

    private final CookingPlugin plugin;
    private final TreeMap<Integer, GatherTier> tiers = new TreeMap<>();
    private final Map<Integer, Double> luckyStrikeChance = new LinkedHashMap<>();
    private final TreeMap<Integer, Double> streakBonus = new TreeMap<>();
    private final Map<String, ShopItem> shopItems = new LinkedHashMap<>();
    private final Map<String, LegendaryRequestDef> legendaryRequests = new LinkedHashMap<>();
    private TriadTrialDef triadTrial;

    private double skipCost;
    private double taskBlockCost;
    private double masterTeleportCost;
    private int masterTeleportCooldownMinutes;

    public GatherConfig(CookingPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Config authors write '&' color codes (easier to type); Minecraft's renderer
     *  only auto-colors the '§' section-sign form, so this converts before use. */
    private static String translateColors(String raw) {
        if (raw == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void load() {
        tiers.clear();
        luckyStrikeChance.clear();
        streakBonus.clear();
        shopItems.clear();
        legendaryRequests.clear();
        triadTrial = null;

        var cfg = plugin.getConfig();

        ConfigurationSection settings = cfg.getConfigurationSection("settings");
        if (settings != null) {
            skipCost = settings.getDouble("skip-cost", 50.0);
            taskBlockCost = settings.getDouble("task-block-cost", 200.0);
            masterTeleportCost = settings.getDouble("master-teleport-cost", 2200.0);
            masterTeleportCooldownMinutes = settings.getInt("master-teleport-cooldown-minutes", 30);
        }

        ConfigurationSection tiersSection = cfg.getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                int number;
                try {
                    number = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection ts = tiersSection.getConfigurationSection(key);
                if (ts == null) continue;

                GatherTier tier = new GatherTier(
                        number,
                        ts.getString("name", "Tier " + number),
                        translateColors(ts.getString("display", "&fTier " + number)),
                        ts.getString("difficulty", ""),
                        ts.getInt("min-level", 1),
                        ts.getInt("base-coins", 1),
                        ts.getBoolean("premium", false)
                );

                ConfigurationSection resSection = ts.getConfigurationSection("resources");
                if (resSection != null) {
                    for (String matName : resSection.getKeys(false)) {
                        ConfigurationSection rs = resSection.getConfigurationSection(matName);
                        if (rs == null) continue;
                        Material material = Material.matchMaterial(matName);
                        if (material == null) {
                            plugin.getLogger().warning("Unknown material in config: " + matName);
                            continue;
                        }
                        ResourceDef.GatherType type;
                        try {
                            type = ResourceDef.GatherType.valueOf(rs.getString("type", "COOKING").toUpperCase());
                        } catch (IllegalArgumentException e) {
                            type = ResourceDef.GatherType.COOKING;
                        }
                        tier.addResource(new ResourceDef(
                                material,
                                number,
                                type,
                                rs.getInt("level", tier.minLevel()),
                                rs.getInt("min-amount", 10),
                                rs.getInt("max-amount", 20),
                                rs.getBoolean("can-double", true)
                        ));
                    }
                }
                tiers.put(number, tier);
            }
        }

        ConfigurationSection lsSection = cfg.getConfigurationSection("lucky-strike");
        if (lsSection != null) {
            for (String key : lsSection.getKeys(false)) {
                luckyStrikeChance.put(Integer.parseInt(key), lsSection.getDouble(key));
            }
        }

        ConfigurationSection streakSection = cfg.getConfigurationSection("streak-bonus");
        if (streakSection != null) {
            for (String key : streakSection.getKeys(false)) {
                streakBonus.put(Integer.parseInt(key), streakSection.getDouble(key));
            }
        }

        ConfigurationSection shopSection = cfg.getConfigurationSection("shop");
        if (shopSection != null) {
            for (String key : shopSection.getKeys(false)) {
                ConfigurationSection ss = shopSection.getConfigurationSection(key);
                if (ss == null) continue;
                Material mat = Material.matchMaterial(ss.getString("material", "STONE"));
                if (mat == null) mat = Material.STONE;
                shopItems.put(key, new ShopItem(
                        key,
                        translateColors(ss.getString("display", key)),
                        mat,
                        ss.getDouble("cost", 0),
                        ss.getInt("limit", -1),
                        ss.getBoolean("premium", false),
                        translateColors(ss.getString("description", ""))
                ));
            }
        }

        ConfigurationSection legendarySection = cfg.getConfigurationSection("legendary-requests");
        if (legendarySection != null) {
            for (String key : legendarySection.getKeys(false)) {
                if (key.equals("triad_trial")) {
                    ConfigurationSection ts2 = legendarySection.getConfigurationSection(key);
                    if (ts2 == null) continue;
                    triadTrial = new TriadTrialDef(
                            translateColors(ts2.getString("display", "&dThe Triad Trial")),
                            ts2.getInt("ancient-debris-amount", 6),
                            ts2.getInt("stems-amount", 40),
                            ts2.getInt("chorus-amount", 30),
                            ts2.getInt("yield", 300),
                            ts2.getLong("cooldown-hours", 168) * 3_600_000L
                    );
                    continue;
                }
                ConfigurationSection ls = legendarySection.getConfigurationSection(key);
                if (ls == null) continue;
                Material mat = Material.matchMaterial(ls.getString("material", "STONE"));
                if (mat == null) continue;
                legendaryRequests.put(key, new LegendaryRequestDef(
                        key,
                        translateColors(ls.getString("display", key)),
                        mat,
                        ls.getInt("min-amount", 1),
                        ls.getInt("max-amount", 1),
                        ls.getInt("yield", 1),
                        ls.getLong("cooldown-hours", 12) * 3_600_000L
                ));
            }
        }
    }

    /** Returns the tier number a material belongs to, or -1 if it isn't tracked. */
    public int tierOfMaterial(org.bukkit.Material material) {
        for (GatherTier t : tiers.values()) {
            for (ResourceDef def : t.resources()) {
                if (def.material() == material) return t.number();
            }
        }
        return -1;
    }

    /** Whether Perfect Dish output-doubling is allowed for this material — false
     *  for gold/diamond/netherite recipes so doubling can never duplicate them.
     *  Defaults true (safe) if the material isn't tracked at all. */
    public boolean canDouble(org.bukkit.Material material) {
        for (GatherTier t : tiers.values()) {
            for (ResourceDef def : t.resources()) {
                if (def.material() == material) return def.canDouble();
            }
        }
        return true;
    }

    public GatherTier tier(int number) {
        return tiers.get(number);
    }

    public List<GatherTier> allTiers() {
        return new ArrayList<>(tiers.values());
    }

    /** Highest tier the player's level currently unlocks. */
    public GatherTier tierForLevel(int level) {
        GatherTier result = tiers.get(1);
        for (GatherTier t : tiers.values()) {
            if (level >= t.minLevel()) {
                result = t;
            }
        }
        return result;
    }

    public double luckyStrikeChance(int tier) {
        return luckyStrikeChance.getOrDefault(tier, 0.0);
    }

    /** Highest applicable streak multiplier bonus (e.g. 0.40 for a streak of 30). */
    public double streakMultiplier(int streak) {
        Map.Entry<Integer, Double> floor = streakBonus.floorEntry(streak);
        return floor == null ? 0.0 : floor.getValue();
    }

    public int nextStreakMilestone(int streak) {
        Map.Entry<Integer, Double> higher = streakBonus.higherEntry(streak);
        if (higher != null) return higher.getKey();
        Integer first = streakBonus.isEmpty() ? null : streakBonus.firstKey();
        return first == null ? 5 : first;
    }

    public Map<String, ShopItem> shopItems() {
        return shopItems;
    }

    public ShopItem shopItem(String id) {
        return shopItems.get(id);
    }

    public Map<String, LegendaryRequestDef> legendaryRequests() {
        return legendaryRequests;
    }

    public LegendaryRequestDef legendaryRequest(String id) {
        return legendaryRequests.get(id);
    }

    public TriadTrialDef triadTrial() {
        return triadTrial;
    }

    public double skipCost() {
        return skipCost;
    }

    public double taskBlockCost() {
        return taskBlockCost;
    }

    public double masterTeleportCost() {
        return masterTeleportCost;
    }

    public int masterTeleportCooldownMinutes() {
        return masterTeleportCooldownMinutes;
    }

    public record ShopItem(String id, String display, Material material, double cost,
                            int limit, boolean premium, String description) {}

    public record LegendaryRequestDef(String id, String display, Material material,
                                       int minAmount, int maxAmount, int yield, long cooldownMillis) {}

    public record TriadTrialDef(String display, int ancientDebrisAmount, int stemsAmount,
                                 int chorusAmount, int yield, long cooldownMillis) {}
}
