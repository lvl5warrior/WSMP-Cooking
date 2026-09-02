package com.warriorssmp.cooking.command;

import com.warriorssmp.cooking.CookingPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GatherCommand implements CommandExecutor {

    private final CookingPlugin plugin;

    public GatherCommand(CookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "cookmenu" -> plugin.menuManager().openMainMenu(player);
            case "cooktask" -> plugin.menuManager().openTaskDetails(player);
            case "cookleaderboards" -> plugin.menuManager().openLeaderboardHub(player);
            case "cookbuffs" -> plugin.menuManager().openBuffs(player);
            case "cookshop" -> {
                if (!isNearChef(player)) {
                    var loc = plugin.masterNpcService().masterLocation();
                    if (loc == null) {
                        player.sendMessage("§cThe Chef hasn't been placed yet — ask an admin.");
                    } else {
                        player.sendMessage("§cYou need to be near the Chef to open the shop.");
                    }
                    return true;
                }
                plugin.menuManager().openShop(player, 1);
            }
            default -> {return false;}
        }
        return true;
    }

    /** /cookshop is proximity-gated to the Chef NPC, per the design doc —
     *  same rule the NPC right-click path already satisfies just by existing. */
    private boolean isNearChef(Player player) {
        var loc = plugin.masterNpcService().masterLocation();
        if (loc == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().equals(player.getWorld())) return false;
        double radius = plugin.getConfig().getDouble("settings.npc-shop-radius", 5.0);
        return loc.distanceSquared(player.getLocation()) <= radius * radius;
    }
}
