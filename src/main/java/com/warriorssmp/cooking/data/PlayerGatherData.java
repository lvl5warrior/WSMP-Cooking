package com.warriorssmp.cooking.data;

import com.warriorssmp.cooking.model.GatherTask;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerGatherData {

    public final UUID uuid;
    public long totalXp = 0;
    public long points = 0;
    public int streak = 0;
    public long lastTaskCompletedAt = 0;
    public GatherTask activeTask = null;
    public final Set<Material> blockedResources = new HashSet<>();
    public final List<TaskHistoryEntry> history = new ArrayList<>();
    public final Set<Integer> purchasedOneTimeItems = new HashSet<>();
    public boolean masterTeleportUnlocked = false;
    public long lastMasterTeleport = 0;
    public long lastGuideBookAt = 0;
    public long luckyStrikeBoostExpiry = 0; // reserved/unused; Perfect Dish odds boost for premium is a flat passive bonus, not a timed buff
    /** Two-tier XP buff per the design doc: small = +10%/15min (75pts, or free
     *  via the milestone roll), large = +25%/1hr (400pts). Strongest active
     *  buff wins and both run on independent timers rather than stacking. */
    public long xpBoostExpiry = 0; // small tier
    public long xpBoostLargeExpiry = 0; // large tier
    public long pointBoostExpiry = 0;
    public long betterTasksExpiry = 0;
    public int lifetimeTasksCompleted = 0;
    public int lifetimeResourcesGathered = 0;
    public int lifetimeLuckyStrikes = 0;
    public int lifetimeLegendaryCompleted = 0;
    /** Every 70th tracked dish (lifetime, doesn't reset on logout) rolls a 25%
     *  chance at a free 5-minute small XP buff — the earned counterpart to the
     *  paid one. */
    public int dishesTowardMilestone = 0;
    public boolean hasCookingTitle = false;

    /** Recipe Book — every distinct dish discovered. Most entries are keyed by
     *  material name; Suspicious Stew (one material regardless of effect) is
     *  keyed as "SUSPICIOUS_STEW:<EFFECT_NAME>" so each flower's effect counts
     *  as its own Recipe Book entry, matching the design doc's "collection
     *  tier" intent for Tier 4. */
    public final Set<String> recipeBookDiscovered = new HashSet<>();
    public boolean recipeBookRewardClaimed = false;
    /** Lifetime count of each entry ever cooked, shown on the Recipe Book page. */
    public final Map<String, Integer> recipeBookCookCounts = new HashMap<>();

    /** requestId -> rolled target amount for the current cycle. */
    public final Map<String, Integer> legendaryTarget = new HashMap<>();
    /** requestId -> current progress toward legendaryTarget. */
    public final Map<String, Integer> legendaryProgress = new HashMap<>();
    /** requestId -> timestamp when that request becomes available again (0 = available now). */
    public final Map<String, Long> legendaryReadyAt = new HashMap<>();

    // Unused in this plugin (the "Commission" is a single-item bulk Legendary
    // Request, not a 3-part structure) — kept for architectural consistency
    // with the other WSMP skill plugins that do use this.
    public int triadAncientDebrisProgress = 0;
    public int triadStemsProgress = 0;
    public int triadChorusProgress = 0;
    public long triadReadyAt = 0;

    public PlayerGatherData(UUID uuid) {
        this.uuid = uuid;
    }

    /** Wipes every stat/progress field back to a brand-new player's state.
     *  Used by the Admin Panel's "Reset Player" button. */
    public void resetAll() {
        totalXp = 0;
        points = 0;
        streak = 0;
        lastTaskCompletedAt = 0;
        activeTask = null;
        blockedResources.clear();
        history.clear();
        purchasedOneTimeItems.clear();
        masterTeleportUnlocked = false;
        lastMasterTeleport = 0;
        lastGuideBookAt = 0;
        luckyStrikeBoostExpiry = 0;
        xpBoostExpiry = 0;
        xpBoostLargeExpiry = 0;
        pointBoostExpiry = 0;
        betterTasksExpiry = 0;
        lifetimeTasksCompleted = 0;
        lifetimeResourcesGathered = 0;
        lifetimeLuckyStrikes = 0;
        lifetimeLegendaryCompleted = 0;
        dishesTowardMilestone = 0;
        hasCookingTitle = false;
        recipeBookDiscovered.clear();
        recipeBookRewardClaimed = false;
        recipeBookCookCounts.clear();
        legendaryTarget.clear();
        legendaryProgress.clear();
        legendaryReadyAt.clear();
        triadAncientDebrisProgress = 0;
        triadStemsProgress = 0;
        triadChorusProgress = 0;
        triadReadyAt = 0;
    }

    public record TaskHistoryEntry(String materialName, int amount, long xpGained,
                                    double coinsGained, boolean skipped, long timestamp) {}
}
