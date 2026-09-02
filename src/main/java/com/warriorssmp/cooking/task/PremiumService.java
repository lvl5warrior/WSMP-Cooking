package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.CookingPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Premium (Tier 3-7 access) is granted if ANY of the following is true:
 *   - the player is server op
 *   - the player has the "premiumcooking" permission (e.g. from a rank plugin)
 *   - the player's UUID is in this plugin's own granted-list (managed from the
 *     in-game Admin Panel, independent of any permissions plugin)
 */
public final class PremiumService {

    private final CookingPlugin plugin;
    private final File file;
    private final Set<UUID> granted = new HashSet<>();

    public PremiumService(CookingPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "premium.yml");
        load();
    }

    public boolean isPremium(Player player) {
        return player.isOp() || player.hasPermission("premiumcooking") || granted.contains(player.getUniqueId());
    }

    public boolean isManuallyGranted(UUID uuid) {
        return granted.contains(uuid);
    }

    public void grant(UUID uuid) {
        granted.add(uuid);
        save();
    }

    public void revoke(UUID uuid) {
        granted.remove(uuid);
        save();
    }

    public Set<UUID> grantedUuids() {
        return granted;
    }

    private void load() {
        granted.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String s : yml.getStringList("granted")) {
            try {
                granted.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (UUID u : granted) list.add(u.toString());
        yml.set("granted", list);
        try {
            yml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save premium.yml: " + e.getMessage());
        }
    }

    public String describeStatus(OfflinePlayer target) {
        if (target.isOp()) return "§6OP";
        if (target.getPlayer() != null && target.getPlayer().hasPermission("premiumcooking")) return "§dPermission";
        if (granted.contains(target.getUniqueId())) return "§aGranted";
        return "§7None";
    }
}
