package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.data.PlayerGatherData;
import com.warriorssmp.cooking.economy.EconomyService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * Tier 7 doesn't use the normal random task pool — it's four rotating Legendary
 * Requests: three single-resource requests (Ancient Vein, Sculk Harvest, Void
 * Orchard) plus the Triad Trial, a fixed three-part objective spanning cooking,
 * woodcutting, and farming that isn't complete until all three are turned in.
 */
public final class LegendaryRequestService {

    private final GatherConfig config;
    private final DataStore dataStore;
    private final EconomyService economy;
    private final Random random = new Random();

    public LegendaryRequestService(GatherConfig config, DataStore dataStore, EconomyService economy) {
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
    }

    public boolean isAvailable(PlayerGatherData data, String requestId) {
        return data.legendaryReadyAt.getOrDefault(requestId, 0L) <= System.currentTimeMillis();
    }

    public boolean isTriadAvailable(PlayerGatherData data) {
        return data.triadReadyAt <= System.currentTimeMillis();
    }

    /** Rolls a fresh target amount for a simple request if it doesn't have one yet. */
    public int ensureTarget(PlayerGatherData data, GatherConfig.LegendaryRequestDef def) {
        Integer existing = data.legendaryTarget.get(def.id());
        if (existing != null) return existing;
        int span = Math.max(0, def.maxAmount() - def.minAmount());
        int target = def.minAmount() + (span == 0 ? 0 : random.nextInt(span + 1));
        data.legendaryTarget.put(def.id(), target);
        data.legendaryProgress.put(def.id(), 0);
        return target;
    }

    /**
     * Called from the block-break / entity-death listeners alongside normal task
     * and Lucky Strike progress — legendary requests track independently of the
     * player's active tier task, so the same resource can feed both at once.
     */
    public void addProgress(Player player, PlayerGatherData data, Material material, int amount) {
        for (GatherConfig.LegendaryRequestDef def : config.legendaryRequests().values()) {
            if (def.material() != material) continue;
            if (!isAvailable(data, def.id())) continue;

            int target = ensureTarget(data, def);
            int progress = data.legendaryProgress.getOrDefault(def.id(), 0) + amount;
            progress = Math.min(progress, target);
            data.legendaryProgress.put(def.id(), progress);

            if (progress >= target) {
                completeSimple(player, data, def);
            }
        }

        GatherConfig.TriadTrialDef triad = config.triadTrial();
        if (triad != null && isTriadAvailable(data)) {
            boolean isAncientDebris = material == Material.ANCIENT_DEBRIS;
            boolean isStem = material == Material.CRIMSON_STEM || material == Material.WARPED_STEM;
            boolean isChorus = material == Material.CHORUS_FLOWER;

            if (isAncientDebris) {
                data.triadAncientDebrisProgress = Math.min(triad.ancientDebrisAmount(), data.triadAncientDebrisProgress + amount);
            } else if (isStem) {
                data.triadStemsProgress = Math.min(triad.stemsAmount(), data.triadStemsProgress + amount);
            } else if (isChorus) {
                data.triadChorusProgress = Math.min(triad.chorusAmount(), data.triadChorusProgress + amount);
            }

            if (isAncientDebris || isStem || isChorus) {
                if (data.triadAncientDebrisProgress >= triad.ancientDebrisAmount()
                        && data.triadStemsProgress >= triad.stemsAmount()
                        && data.triadChorusProgress >= triad.chorusAmount()) {
                    completeTriad(player, data, triad);
                }
            }
        }
    }

    private void completeSimple(Player player, PlayerGatherData data, GatherConfig.LegendaryRequestDef def) {
        data.points += def.yield();
        data.lifetimeLegendaryCompleted++;
        data.legendaryTarget.remove(def.id());
        data.legendaryProgress.remove(def.id());
        data.legendaryReadyAt.put(def.id(), System.currentTimeMillis() + def.cooldownMillis());

        player.sendMessage(Component.text("§6§lLEGENDARY REQUEST COMPLETE §7— §f" + def.display()
                + " §7(+" + com.warriorssmp.cooking.model.PointsUtil.format(def.yield()) + ")"));
    }

    private void completeTriad(Player player, PlayerGatherData data, GatherConfig.TriadTrialDef triad) {
        data.points += triad.yield();
        data.lifetimeLegendaryCompleted++;
        data.triadAncientDebrisProgress = 0;
        data.triadStemsProgress = 0;
        data.triadChorusProgress = 0;
        data.triadReadyAt = System.currentTimeMillis() + triad.cooldownMillis();

        player.sendMessage(Component.text("§6§lTHE TRIAD TRIAL COMPLETE §7— §f(+"
                + com.warriorssmp.cooking.model.PointsUtil.format(triad.yield()) + ")"));
    }
}
