package com.warriorssmp.cooking;

import com.warriorssmp.cooking.command.GatherAdminCommand;
import com.warriorssmp.cooking.command.GatherCommand;
import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.economy.EconomyService;
import com.warriorssmp.cooking.listener.GatherListener;
import com.warriorssmp.cooking.menu.MenuManager;
import com.warriorssmp.cooking.task.GatherConfig;
import com.warriorssmp.cooking.task.LeaderboardService;
import com.warriorssmp.cooking.task.LegendaryRequestService;
import com.warriorssmp.cooking.task.LuckyStrikeService;
import com.warriorssmp.cooking.task.MasterNpcService;
import com.warriorssmp.cooking.task.PremiumService;
import com.warriorssmp.cooking.task.ShopService;
import com.warriorssmp.cooking.task.TaskService;
import org.bukkit.plugin.java.JavaPlugin;

public final class CookingPlugin extends JavaPlugin {

    private static CookingPlugin instance;

    private GatherConfig gatherConfig;
    private DataStore dataStore;
    private EconomyService economyService;
    private TaskService taskService;
    private LuckyStrikeService luckyStrikeService;
    private MasterNpcService masterNpcService;
    private LegendaryRequestService legendaryRequestService;
    private LeaderboardService leaderboardService;
    private ShopService shopService;
    private PremiumService premiumService;
    private MenuManager menuManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.gatherConfig = new GatherConfig(this);
        this.dataStore = new DataStore(this);
        this.economyService = new EconomyService(this);
        economyService.setupEconomy(); // best-effort; Points no longer depend on Vault, but this stays wired for future use

        this.premiumService = new PremiumService(this);
        this.taskService = new TaskService(this, gatherConfig, dataStore, economyService, premiumService);
        this.luckyStrikeService = new LuckyStrikeService(this, gatherConfig, dataStore, economyService, premiumService);
        this.masterNpcService = new MasterNpcService(this, gatherConfig, dataStore, economyService);
        this.legendaryRequestService = new LegendaryRequestService(gatherConfig, dataStore, economyService);
        this.leaderboardService = new LeaderboardService(dataStore);
        this.shopService = new ShopService(gatherConfig, dataStore, economyService, premiumService);
        this.menuManager = new MenuManager(this);

        getServer().getPluginManager().registerEvents(new GatherListener(this), this);
        getServer().getPluginManager().registerEvents(menuManager, this);

        getCommand("cookmenu").setExecutor(new GatherCommand(this));
        getCommand("cooktask").setExecutor(new GatherCommand(this));
        getCommand("cookleaderboards").setExecutor(new GatherCommand(this));
        getCommand("cookbuffs").setExecutor(new GatherCommand(this));
        getCommand("cookshop").setExecutor(new GatherCommand(this));

        GatherAdminCommand adminCommand = new GatherAdminCommand(this);
        getCommand("cookshopadmin").setExecutor(adminCommand);
        getCommand("cookmenuadmin").setExecutor(adminCommand);
        getCommand("cookeditor").setExecutor(adminCommand);
        getCommand("cookmaster").setExecutor(adminCommand);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.warriorssmp.cooking.CookingPlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        // Periodic autosave so leaderboard/admin lookups for offline players stay
        // reasonably fresh, and progress survives a crash between disconnects.
        getServer().getScheduler().runTaskTimerAsynchronously(this, dataStore::saveAll, 20L * 60 * 5, 20L * 60 * 5);

        logStartupSummary();
    }

    /**
     * Prints a clearly-sectioned summary of what actually loaded from config.yml,
     * so a bad edit (wrong material name, missing section, etc.) shows up as an
     * obvious gap in this log instead of a silent in-game failure you'd only
     * catch by testing every menu after a redeploy.
     */
    public void logStartupSummary() {
        java.util.logging.Logger log = getLogger();
        log.info("==================================================");
        log.info("  WSMP-Cooking (Cooking) — Config Summary");
        log.info("==================================================");

        log.info("[Tiers]");
        for (var tier : gatherConfig.allTiers()) {
            log.info("  " + org.bukkit.ChatColor.stripColor(tier.display()) + " (Lv" + tier.minLevel() + "+): "
                    + tier.resources().size() + " resource(s), " + tier.baseCoins() + " points/task"
                    + (tier.premium() ? " [premium]" : " [free]"));
        }
        if (gatherConfig.allTiers().isEmpty()) {
            log.warning("  No tiers loaded at all — check the 'tiers:' section in config.yml!");
        }

        log.info("[Shop]");
        log.info("  " + gatherConfig.shopItems().size() + " item(s) loaded");

        log.info("[Legendary Requests]");
        log.info("  " + gatherConfig.legendaryRequests().size() + " request(s) loaded"
                + (gatherConfig.triadTrial() != null ? ", Triad Trial loaded" : " (no Triad Trial defined — expected in a single-skill plugin)"));

        log.info("[Economy]");
        log.info("  Points: in-plugin currency, not tied to Vault");
        log.info("  Vault:  " + (economyService.isHooked() ? "hooked (unused by core loop; reserved for future use)" : "not found (optional)"));

        log.info("[Premium]");
        log.info("  " + premiumService.grantedUuids().size() + " player(s) manually granted premium via the Admin Panel");
        log.info("  Server operators always count as premium automatically");

        log.info("==================================================");
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.saveAll();
        }
    }

    public static CookingPlugin get() {
        return instance;
    }

    public GatherConfig gatherConfig() {
        return gatherConfig;
    }

    public DataStore dataStore() {
        return dataStore;
    }

    public EconomyService economy() {
        return economyService;
    }

    public TaskService taskService() {
        return taskService;
    }

    public LuckyStrikeService luckyStrikeService() {
        return luckyStrikeService;
    }

    public MasterNpcService masterNpcService() {
        return masterNpcService;
    }

    public LegendaryRequestService legendaryRequestService() {
        return legendaryRequestService;
    }

    public LeaderboardService leaderboardService() {
        return leaderboardService;
    }

    public ShopService shopService() {
        return shopService;
    }

    public PremiumService premiumService() {
        return premiumService;
    }

    public MenuManager menuManager() {
        return menuManager;
    }
}
