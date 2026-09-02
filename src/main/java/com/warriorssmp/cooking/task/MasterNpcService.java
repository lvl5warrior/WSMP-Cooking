package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.data.PlayerGatherData;
import com.warriorssmp.cooking.economy.EconomyService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles the Chef NPC's location and its teleport perk. Home
 * teleports were removed from this system entirely per the redesign — the
 * Chef teleport (purchased from the shop) is the only teleport
 * that remains.
 */
public final class MasterNpcService {

    private final CookingPlugin plugin;
    private final GatherConfig config;
    private final DataStore dataStore;
    private final EconomyService economy;

    private Location masterLocation;

    public MasterNpcService(CookingPlugin plugin, GatherConfig config, DataStore dataStore, EconomyService economy) {
        this.plugin = plugin;
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
    }

    public void setMasterLocation(Location loc) {
        this.masterLocation = loc.clone();
    }

    public Location masterLocation() {
        return masterLocation;
    }

    public boolean teleportToMaster(Player player, PlayerGatherData data) {
        if (!data.masterTeleportUnlocked) {
            player.sendMessage("§cUnlock the Chef teleport from the Chef Shop first.");
            return false;
        }
        if (masterLocation == null) {
            player.sendMessage("§cThe Chef hasn't been placed yet — ask an admin to run /cookmaster set.");
            return false;
        }
        long cooldownMs = config.masterTeleportCooldownMinutes() * 60_000L;
        long remaining = (data.lastMasterTeleport + cooldownMs) - System.currentTimeMillis();
        if (remaining > 0) {
            player.sendMessage("§cChef teleport is on cooldown for " + formatDuration(remaining) + ".");
            return false;
        }
        data.lastMasterTeleport = System.currentTimeMillis();
        player.teleport(masterLocation);
        player.sendMessage("§aTeleported to the Chef.");
        return true;
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
