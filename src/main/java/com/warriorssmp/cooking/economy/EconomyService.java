package com.warriorssmp.cooking.economy;

import com.warriorssmp.cooking.CookingPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Thin wrapper around Vault. There is no separate "Coins" balance — every
 * Points do not use Vault at all in this split, but the wrapper stays wired for future use.
 * WSMP-SimpleSell already uses, per the design decision to share one economy
 * across every skill.
 */
public final class EconomyService {

    private final CookingPlugin plugin;
    private Economy economy;

    public EconomyService(CookingPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    public boolean isHooked() {
        return economy != null;
    }

    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return;
        economy.depositPlayer(player, amount);
    }

    /** Returns true if the withdrawal succeeded (i.e. the player could afford it). */
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0) return true;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}
