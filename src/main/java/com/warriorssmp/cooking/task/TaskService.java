package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.data.PlayerGatherData;
import com.warriorssmp.cooking.economy.EconomyService;
import com.warriorssmp.cooking.model.GatherTask;
import com.warriorssmp.cooking.model.GatherTier;
import com.warriorssmp.cooking.model.PointsUtil;
import com.warriorssmp.cooking.model.ResourceDef;
import com.warriorssmp.cooking.model.XpTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TaskService {

    private final CookingPlugin plugin;
    private final GatherConfig config;
    private final DataStore dataStore;
    private final EconomyService economy;
    private final PremiumService premium;
    private final Random random = new Random();

    public TaskService(CookingPlugin plugin, GatherConfig config, DataStore dataStore, EconomyService economy, PremiumService premium) {
        this.plugin = plugin;
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
        this.premium = premium;
    }

    public int levelOf(PlayerGatherData data) {
        return XpTable.levelForXp(data.totalXp);
    }

    /**
     * Picks the tier a new task should be generated from: usually the player's
     * current tier, but with a chance (per the design doc's level-banded table)
     * of rolling one tier higher — boosted further if "Better Tasks" is active.
     * Tier 7 has no normal resource pool (Legendary Requests cover it instead),
     * so this never rolls into or lands on an empty-resource tier.
     */
    public GatherTier rollTaskTier(PlayerGatherData data, Player player) {
        int level = levelOf(data);
        GatherTier currentTier = config.tierForLevel(level);
        if (currentTier.resources().isEmpty()) {
            GatherTier fallback = config.tier(currentTier.number() - 1);
            currentTier = fallback != null ? fallback : currentTier;
        }

        double chance = XpTable.higherTierChance(level, currentTier.number(), currentTier.minLevel());
        if (data.betterTasksExpiry > System.currentTimeMillis()) {
            chance += 0.05;
        }

        GatherTier nextTier = config.tier(currentTier.number() + 1);
        if (nextTier == null || nextTier.resources().isEmpty()) return currentTier;

        if (nextTier.premium() && !premium.isPremium(player)) {
            return currentTier;
        }

        return random.nextDouble() < chance ? nextTier : currentTier;
    }

    /** Generates a brand new task for the player and sets it as their active task. */
    public GatherTask generateTask(Player player, PlayerGatherData data) {
        GatherTier tier = rollTaskTier(data, player);
        List<ResourceDef> pool = new ArrayList<>();
        for (ResourceDef def : tier.resources()) {
            if (!data.blockedResources.contains(def.material())) {
                pool.add(def);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(tier.resources()); // fall back if everything in the tier is blocked
        }

        ResourceDef chosen = pool.get(random.nextInt(pool.size()));
        int amount = chosen.rollAmount(random);
        GatherTask task = new GatherTask(chosen.material(), tier.number(), amount, 0);
        data.activeTask = task;
        return task;
    }

    /** Ensures the player has an active task, generating one if needed. */
    public GatherTask ensureTask(Player player, PlayerGatherData data) {
        if (data.activeTask == null) {
            return generateTask(player, data);
        }
        return data.activeTask;
    }

    public boolean skipTask(Player player, PlayerGatherData data) {
        long cost = Math.round(config.skipCost());
        if (data.points < cost) {
            player.sendMessage("§cYou need " + PointsUtil.format(cost) + " to skip your task.");
            return false;
        }
        data.points -= cost;
        if (data.activeTask != null) {
            data.history.add(new PlayerGatherData.TaskHistoryEntry(
                    data.activeTask.displayName(), 0, 0, -cost, true, System.currentTimeMillis()));
        }
        generateTask(player, data);
        return true;
    }

    /** Call whenever a dish is cooked (crafted or smelted); grants small passive
     *  XP on every qualifying cook (not just contract turn-in), rolls the
     *  milestone buff, updates task progress if the material matches the active
     *  task, and shows one combined action bar. */
    public void addProgress(Player player, PlayerGatherData data, org.bukkit.Material material, int amount) {
        int tier = config.tierOfMaterial(material);
        long xpGained = tier >= 1 ? grantPassiveXp(player, data, tier, amount) : 0;

        if (tier >= 1) {
            rollMilestoneBuff(player, data, amount);
        }

        GatherTask task = data.activeTask;
        boolean taskMatched = task != null && task.material() == material;
        if (taskMatched) {
            task.addProgress(amount);
            data.lifetimeResourcesGathered += amount;
            if (task.isComplete()) {
                completeTask(player, data);
                return; // completeTask already sends its own message; skip the passive bar this swing
            }
        }

        showXpActionBar(player, data, xpGained, taskMatched ? task : null);
    }

    /** Small XP-per-block table (separate from the much larger task-completion
     *  XP) — scales with tier so higher-tier resources feel worth more even
     *  outside of finishing a task. */
    private static final int[] XP_PER_BLOCK_TIER = {0, 1, 2, 3, 4, 5, 6, 8};

    /** Two-tier XP buff, strongest-active-wins per the design doc: the large
     *  buff (+25%) takes priority while active, falling back to whatever's
     *  left of the small buff (+10%) once it expires. Both run on independent
     *  timers rather than stacking additively. */
    private double xpBuffMultiplier(PlayerGatherData data) {
        long now = System.currentTimeMillis();
        if (data.xpBoostLargeExpiry > now) return 1.25;
        if (data.xpBoostExpiry > now) return 1.10;
        return 1.0;
    }

    private long grantPassiveXp(Player player, PlayerGatherData data, int tierNumber, int amount) {
        long perBlock = tierNumber >= 0 && tierNumber < XP_PER_BLOCK_TIER.length ? XP_PER_BLOCK_TIER[tierNumber] : 1;
        long xpGain = Math.round(perBlock * amount * xpBuffMultiplier(data));

        int levelBefore = levelOf(data);
        data.totalXp += xpGain;
        int levelAfter = levelOf(data);
        if (levelAfter > levelBefore) {
            announceLevelUp(player, levelAfter);
        }
        return xpGain;
    }

    /** The "how much XP did I just get, and how much more to level up" popup —
     *  fires on every qualifying cook, not just contract turn-in. Folds the
     *  task progress bar into the same line when a task also updated, since an
     *  action bar can only show one message at a time. */
    private void showXpActionBar(Player player, PlayerGatherData data, long xpGained, GatherTask taskForDisplay) {
        int level = levelOf(data);
        long xpIntoLevel = data.totalXp - XpTable.xpForLevel(level);
        long xpForNextLevel = XpTable.xpForNextLevel(level) - XpTable.xpForLevel(level);
        long xpRemaining = Math.max(0, xpForNextLevel - xpIntoLevel);

        int barLength = 15;
        double fraction = xpForNextLevel > 0 ? (double) xpIntoLevel / xpForNextLevel : 1.0;
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * barLength);
        String xpBar = "§b" + "█".repeat(filled) + "§7" + "░".repeat(barLength - filled);

        String xpPart = level >= XpTable.MAX_LEVEL
                ? "§b+" + xpGained + " XP §7(MAX LEVEL)"
                : "§b+" + xpGained + " XP §7(" + xpRemaining + " to next level) " + xpBar;

        if (taskForDisplay != null) {
            int taskBarLength = 15;
            double taskFraction = (double) taskForDisplay.progress() / taskForDisplay.required();
            int taskFilled = (int) Math.round(Math.max(0, Math.min(1, taskFraction)) * taskBarLength);
            String taskBar = "§a" + "█".repeat(taskFilled) + "§7" + "░".repeat(taskBarLength - taskFilled);
            player.sendActionBar(Component.text("§e" + taskForDisplay.displayName() + " §7"
                    + taskForDisplay.progress() + "/" + taskForDisplay.required() + " " + taskBar + "  §f| " + xpPart));
        } else {
            player.sendActionBar(Component.text(xpPart));
        }
    }

    private void completeTask(Player player, PlayerGatherData data) {
        GatherTask task = data.activeTask;
        if (task == null) return;

        GatherTier tier = config.tier(task.tier());
        int baseCoins = tier != null ? tier.baseCoins() : 1;

        data.streak++;
        double streakMult = config.streakMultiplier(data.streak);

        // XP intentionally does NOT scale with the tier's points yield — leveling
        // pace stays tied to tier number alone, independent of any future points
        // rebalancing. (40/80/160/280/480/800/2400 XP for tiers 1-7.)
        double xpGain = xpForTier(tier != null ? tier.number() : task.tier()) * xpBuffMultiplier(data);
        double pointsGain = baseCoins * (1 + streakMult);

        if (data.pointBoostExpiry > System.currentTimeMillis()) {
            pointsGain *= 1.10;
        }

        int levelBefore = levelOf(data);
        data.totalXp += Math.round(xpGain);
        int levelAfter = levelOf(data);

        long pointsRounded = Math.round(pointsGain);
        data.points += pointsRounded;
        data.lastTaskCompletedAt = System.currentTimeMillis();
        data.lifetimeTasksCompleted++;

        data.history.add(new PlayerGatherData.TaskHistoryEntry(
                task.displayName(), task.required(), Math.round(xpGain), pointsRounded, false, System.currentTimeMillis()));

        player.sendMessage("§a§lTASK COMPLETE §7— §f" + task.displayName()
                + " §7(+" + Math.round(xpGain) + " XP, +" + PointsUtil.format(pointsRounded) + ")");

        if (levelAfter > levelBefore) {
            announceLevelUp(player, levelAfter);
        }

        double newMult = config.streakMultiplier(data.streak);
        if (newMult > streakMult) {
            player.sendMessage("§6§lSTREAK MILESTONE! §7Streak " + data.streak + " — +" + (int) (newMult * 100) + "% Points");
        }

        data.activeTask = null;
        generateTask(player, data);
    }

    /** Level-up notification — a title popup plus sound, separate from the plain
     *  chat message task completion already sends, so it stands out on its own. */
    private void announceLevelUp(Player player, int newLevel) {
        GatherTier tier = config.tierForLevel(newLevel);
        player.showTitle(Title.title(
                Component.text("§6§lLEVEL UP!"),
                Component.text("§eChef Level " + newLevel + " §7— " + tier.display()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage("§6§lLEVEL UP! §7You are now Chef Level §f" + newLevel + " §7(" + tier.display() + "§7)");
    }

    private static final int[] XP_PER_TIER = {0, 40, 80, 160, 280, 480, 800, 2400};

    private double xpForTier(int tierNumber) {
        if (tierNumber < 1 || tierNumber >= XP_PER_TIER.length) return 40;
        return XP_PER_TIER[tierNumber];
    }

    /** Milestone buff — every 70th catch (lifetime counter) rolls a 25% chance
     *  at a free 5-minute Cooking Buff, the earned counterpart to the paid one.
     *  Written as free-to-play per the design doc's default. */
    private static final int MILESTONE_INTERVAL = 70;
    private static final double MILESTONE_CHANCE = 0.25;
    private static final long MILESTONE_DURATION_MS = 5 * 60_000L;

    private void rollMilestoneBuff(Player player, PlayerGatherData data, int amount) {
        int before = data.dishesTowardMilestone;
        data.dishesTowardMilestone += amount;
        int milestonesPassed = (data.dishesTowardMilestone / MILESTONE_INTERVAL) - (before / MILESTONE_INTERVAL);
        for (int i = 0; i < milestonesPassed; i++) {
            if (random.nextDouble() < MILESTONE_CHANCE) {
                long now = System.currentTimeMillis();
                long base = Math.max(data.xpBoostExpiry, now);
                data.xpBoostExpiry = base + MILESTONE_DURATION_MS;
                player.sendMessage("§b🍲 Milestone Buff! §7+10% Cooking XP for 5 minutes.");
            }
        }
    }

    public GatherConfig config() {
        return config;
    }
}
