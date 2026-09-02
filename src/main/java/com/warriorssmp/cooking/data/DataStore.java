package com.warriorssmp.cooking.data;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.model.GatherTask;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DataStore {

    private final CookingPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerGatherData> cache = new HashMap<>();

    public DataStore(CookingPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
    }

    public PlayerGatherData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    /** For leaderboard scans / admin lookups — merges currently-loaded (online)
     *  player data with everyone saved to disk. Scanning disk alone missed any
     *  online player whose data hadn't been flushed yet, which is why the
     *  leaderboard could come back empty or miss people who were online. */
    public List<PlayerGatherData> allKnownPlayers() {
        java.util.Map<UUID, PlayerGatherData> merged = new HashMap<>(cache);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                try {
                    UUID uuid = UUID.fromString(f.getName().replace(".yml", ""));
                    merged.computeIfAbsent(uuid, this::load);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private PlayerGatherData load(UUID uuid) {
        PlayerGatherData data = new PlayerGatherData(uuid);
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) return data;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.totalXp = yml.getLong("total-xp", 0);
        data.points = yml.getLong("points", 0);
        data.streak = yml.getInt("streak", 0);
        data.lastTaskCompletedAt = yml.getLong("last-task-completed-at", 0);
        data.masterTeleportUnlocked = yml.getBoolean("master-teleport-unlocked", false);
        data.lastMasterTeleport = yml.getLong("last-master-teleport", 0);
        data.lastGuideBookAt = yml.getLong("last-guide-book-at", 0);
        data.luckyStrikeBoostExpiry = yml.getLong("lucky-strike-boost-expiry", 0);
        data.xpBoostExpiry = yml.getLong("xp-boost-expiry", 0);
        data.xpBoostLargeExpiry = yml.getLong("xp-boost-large-expiry", 0);
        data.pointBoostExpiry = yml.getLong("point-boost-expiry", 0);
        data.betterTasksExpiry = yml.getLong("better-tasks-expiry", 0);
        data.lifetimeTasksCompleted = yml.getInt("lifetime-tasks-completed", 0);
        data.lifetimeResourcesGathered = yml.getInt("lifetime-resources-gathered", 0);
        data.lifetimeLuckyStrikes = yml.getInt("lifetime-lucky-strikes", 0);
        data.lifetimeLegendaryCompleted = yml.getInt("lifetime-legendary-completed", 0);
        data.dishesTowardMilestone = yml.getInt("dishes-toward-milestone", 0);
        data.hasCookingTitle = yml.getBoolean("has-cooking-title", false);
        data.recipeBookRewardClaimed = yml.getBoolean("recipe-book-reward-claimed", false);
        data.recipeBookDiscovered.addAll(yml.getStringList("recipe-book-discovered"));
        var cookCountsSection = yml.getConfigurationSection("recipe-book-cook-counts");
        if (cookCountsSection != null) {
            for (String key : cookCountsSection.getKeys(false)) {
                data.recipeBookCookCounts.put(key, cookCountsSection.getInt(key, 0));
            }
        }

        for (String matName : yml.getStringList("blocked-resources")) {
            Material m = Material.matchMaterial(matName);
            if (m != null) data.blockedResources.add(m);
        }

        for (int i : yml.getIntegerList("purchased-one-time-items")) {
            data.purchasedOneTimeItems.add(i);
        }

        String taskMat = yml.getString("active-task.material");
        if (taskMat != null) {
            Material m = Material.matchMaterial(taskMat);
            if (m != null) {
                data.activeTask = new GatherTask(
                        m,
                        yml.getInt("active-task.tier", 1),
                        yml.getInt("active-task.required", 1),
                        yml.getInt("active-task.progress", 0)
                );
            }
        }

        var legendarySection = yml.getConfigurationSection("legendary");
        if (legendarySection != null) {
            for (String id : legendarySection.getKeys(false)) {
                data.legendaryTarget.put(id, yml.getInt("legendary." + id + ".target", 0));
                data.legendaryProgress.put(id, yml.getInt("legendary." + id + ".progress", 0));
                data.legendaryReadyAt.put(id, yml.getLong("legendary." + id + ".ready-at", 0));
            }
        }
        data.triadAncientDebrisProgress = yml.getInt("triad.ancient-debris-progress", 0);
        data.triadStemsProgress = yml.getInt("triad.stems-progress", 0);
        data.triadChorusProgress = yml.getInt("triad.chorus-progress", 0);
        data.triadReadyAt = yml.getLong("triad.ready-at", 0);

        List<Map<?, ?>> historyRaw = yml.getMapList("history");
        for (Map<?, ?> m : historyRaw) {
            data.history.add(new PlayerGatherData.TaskHistoryEntry(
                    String.valueOf(m.get("material")),
                    numberOrZero(m.get("amount")).intValue(),
                    numberOrZero(m.get("xp")).longValue(),
                    numberOrZero(m.get("coins")).doubleValue(),
                    Boolean.TRUE.equals(m.get("skipped")),
                    numberOrZero(m.get("timestamp")).longValue()
            ));
        }

        return data;
    }

    /** Map.getOrDefault() on a Map<?,?> can't take a plain literal default (generics
     *  capture issue) — this reads the raw value and falls back to 0 by hand instead. */
    private static Number numberOrZero(Object raw) {
        return raw instanceof Number number ? number : 0;
    }

    public void save(PlayerGatherData data) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("total-xp", data.totalXp);
        yml.set("points", data.points);
        yml.set("streak", data.streak);
        yml.set("last-task-completed-at", data.lastTaskCompletedAt);
        yml.set("master-teleport-unlocked", data.masterTeleportUnlocked);
        yml.set("last-master-teleport", data.lastMasterTeleport);
        yml.set("last-guide-book-at", data.lastGuideBookAt);
        yml.set("lucky-strike-boost-expiry", data.luckyStrikeBoostExpiry);
        yml.set("xp-boost-expiry", data.xpBoostExpiry);
        yml.set("xp-boost-large-expiry", data.xpBoostLargeExpiry);
        yml.set("point-boost-expiry", data.pointBoostExpiry);
        yml.set("better-tasks-expiry", data.betterTasksExpiry);
        yml.set("lifetime-tasks-completed", data.lifetimeTasksCompleted);
        yml.set("lifetime-resources-gathered", data.lifetimeResourcesGathered);
        yml.set("lifetime-lucky-strikes", data.lifetimeLuckyStrikes);
        yml.set("lifetime-legendary-completed", data.lifetimeLegendaryCompleted);
        yml.set("dishes-toward-milestone", data.dishesTowardMilestone);
        yml.set("has-cooking-title", data.hasCookingTitle);
        yml.set("recipe-book-reward-claimed", data.recipeBookRewardClaimed);
        yml.set("recipe-book-discovered", new ArrayList<>(data.recipeBookDiscovered));
        for (var entry : data.recipeBookCookCounts.entrySet()) {
            yml.set("recipe-book-cook-counts." + entry.getKey(), entry.getValue());
        }

        List<String> blocked = new ArrayList<>();
        for (Material m : data.blockedResources) blocked.add(m.name());
        yml.set("blocked-resources", blocked);
        yml.set("purchased-one-time-items", new ArrayList<>(data.purchasedOneTimeItems));

        if (data.activeTask != null) {
            yml.set("active-task.material", data.activeTask.material().name());
            yml.set("active-task.tier", data.activeTask.tier());
            yml.set("active-task.required", data.activeTask.required());
            yml.set("active-task.progress", data.activeTask.progress());
        }

        for (String id : data.legendaryTarget.keySet()) {
            yml.set("legendary." + id + ".target", data.legendaryTarget.getOrDefault(id, 0));
            yml.set("legendary." + id + ".progress", data.legendaryProgress.getOrDefault(id, 0));
            yml.set("legendary." + id + ".ready-at", data.legendaryReadyAt.getOrDefault(id, 0L));
        }
        for (String id : data.legendaryReadyAt.keySet()) {
            if (!data.legendaryTarget.containsKey(id)) {
                yml.set("legendary." + id + ".ready-at", data.legendaryReadyAt.get(id));
            }
        }
        yml.set("triad.ancient-debris-progress", data.triadAncientDebrisProgress);
        yml.set("triad.stems-progress", data.triadStemsProgress);
        yml.set("triad.chorus-progress", data.triadChorusProgress);
        yml.set("triad.ready-at", data.triadReadyAt);

        List<Map<String, Object>> historyOut = new ArrayList<>();
        int start = Math.max(0, data.history.size() - 50); // keep last 50 entries on disk
        for (int i = start; i < data.history.size(); i++) {
            PlayerGatherData.TaskHistoryEntry e = data.history.get(i);
            Map<String, Object> m = new HashMap<>();
            m.put("material", e.materialName());
            m.put("amount", e.amount());
            m.put("xp", e.xpGained());
            m.put("coins", e.coinsGained());
            m.put("skipped", e.skipped());
            m.put("timestamp", e.timestamp());
            historyOut.add(m);
        }
        yml.set("history", historyOut);

        try {
            yml.save(new File(folder, data.uuid + ".yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save Chef data for " + data.uuid + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (PlayerGatherData data : cache.values()) {
            save(data);
        }
    }

    public void unload(UUID uuid) {
        PlayerGatherData data = cache.remove(uuid);
        if (data != null) save(data);
    }
}
