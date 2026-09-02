package com.warriorssmp.cooking.menu;

import com.warriorssmp.cooking.CookingPlugin;
import com.warriorssmp.cooking.data.PlayerGatherData;
import com.warriorssmp.cooking.model.GatherTask;
import com.warriorssmp.cooking.model.GatherTier;
import com.warriorssmp.cooking.model.IconUtil;
import com.warriorssmp.cooking.model.PointsUtil;
import com.warriorssmp.cooking.model.ResourceDef;
import com.warriorssmp.cooking.model.XpTable;
import com.warriorssmp.cooking.task.GatherConfig;
import com.warriorssmp.cooking.task.LeaderboardService;
import com.warriorssmp.cooking.task.ShopService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and routes every Chef GUI page. Buttons carry their action in a
 * PersistentDataContainer tag rather than relying on display-name matching, so
 * clicks still resolve correctly even if you retheme item names/lore later.
 */
public final class MenuManager implements Listener {

    private final CookingPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey dataKey;

    public MenuManager(CookingPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "mine_action");
        this.dataKey = new NamespacedKey(plugin, "mine_data");
    }

    // ---------------------------------------------------------------- holder

    private record Holder(String menu, String context) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }

    private Inventory inv(int size, String title, String menu, String context) {
        return Bukkit.createInventory(new Holder(menu, context), size, Component.text(title));
    }

    // ---------------------------------------------------------------- item builder

    private ItemStack item(Material mat, String name, List<String> lore, String action, String data) {
        ItemStack stack = new ItemStack(IconUtil.safeIcon(mat));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComp = new ArrayList<>();
            for (String line : lore) loreComp.add(Component.text(line));
            meta.lore(loreComp);
        }
        // GUI icons are decorative — hide any vanilla attack-damage/attribute/enchant
        // tooltips the underlying material would normally show (e.g. tools/weapons).
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (data != null) meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", null, null, null);
    }

    private ItemStack backButton(String toMenu) {
        return item(Material.ARROW, "§7◀ Back", null, "nav", toMenu);
    }

    private ItemStack closeButton() {
        return item(Material.BARRIER, "§cClose", null, "close", null);
    }

    private String bar(double fraction, int length) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        return "§a" + "█".repeat(filled) + "§7" + "░".repeat(length - filled);
    }

    private String typeIcon(ResourceDef.GatherType type) {
        return switch (type) {
            case MINING -> "⛏";
            case WOODCUTTING -> "🪓";
            case FARMING -> "🌾";
            case FISHING -> "🎣";
            case COOKING -> "🍳";
            case PROCESSED -> "🔥";
        };
    }

    // ---------------------------------------------------------------- GUIDE BOOK

    /**
     * Rewritten to use BookMeta's long-standing plain-String API (setTitle/
     * setAuthor/setPages) instead of the newer Adventure Component overloads —
     * those are more likely to have shifted between Paper API versions, which
     * would throw silently (Bukkit swallows exceptions inside click handlers
     * by default, so "nothing happens" is exactly what a hidden exception here
     * would look like). Wrapped in try/catch so if this still fails, it now
     * surfaces a real error to both the player and the console instead of
     * doing nothing.
     */
    private void openGuideBook(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        long cooldownMs = plugin.getConfig().getLong("settings.guide-book-cooldown-hours", 24) * 3_600_000L;
        long remaining = (data.lastGuideBookAt + cooldownMs) - System.currentTimeMillis();
        if (remaining > 0) {
            long hours = remaining / 3_600_000L;
            long minutes = (remaining / 60_000L) % 60;
            player.sendMessage("§cYou can request the Guide Book again in " + hours + "h " + minutes + "m.");
            return;
        }

        try {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            ItemMeta rawMeta = book.getItemMeta();
            if (!(rawMeta instanceof org.bukkit.inventory.meta.BookMeta meta)) {
                player.sendMessage("§cCouldn't create the guide book — WRITTEN_BOOK has no BookMeta on this server. Tell an admin to check console.");
                plugin.getLogger().severe("Guide Book: ItemMeta for WRITTEN_BOOK was not a BookMeta instance (was " + rawMeta + ").");
                return;
            }

            meta.setTitle("Chef Guide");
            meta.setAuthor("Chef");
            meta.setPages(
                    "§6§lCHEF GUIDE\n\n§7Welcome, Chef! This book covers everything about the skill: leveling, contracts, Perfect Dish, Points, the shop, and Legendary Requests.\n\n§8Flip the page \u27a1",
                    "§6§lLeveling & Tiers\n\n§7Complete contracts to earn XP and level up. There are 7 tiers, each unlocking harder dishes. Tiers 3-7 require §dpremiumcooking§7.",
                    "§6§lContracts\n\n§7The Chef hands you a contract: cook a set amount of one dish. Craft or smelt tracked dishes to make progress - watch the action bar for a live count. Furnace and Smoker both count; Campfire doesn't.",
                    "§6§lPerfect Dish\n\n§7Every relevant cook has a small chance to come out Perfect, usually doubling your output plus bonus Points. Gold recipes (Golden Carrot, Golden Apple, Glistering Melon Slice) never double - they pay a bigger Points bonus instead, so Perfect Dish can never duplicate gold.",
                    "§6§lPoints & Shop\n\n§7Points are earned from contracts and Legendary Requests - not real money. Spend them at the Chef Shop on potions, ingredient bundles, XP buffs, and the Cooking Title. Check your balance from the main menu.",
                    "§6§lLegendary Requests & Recipe Book\n\n§7At Tier 7, ask the Chef for The Commission Special Request. Separately, the Recipe Book tracks every distinct dish you've cooked - including a separate entry for each Suspicious Stew flower effect - complete it from the main menu for a big one-time reward.",
                    "§6§lTips\n\n§7\u2022 Blocking a dish removes it from your contract pool\n§7\u2022 Streaks pay out starting at 3 contracts and top out at +300%\n§7\u2022 Better Tasks gives a shot at higher tiers\n§7\u2022 Only the Chef can open Special Requests"
            );

            book.setItemMeta(meta);

            var leftover = player.getInventory().addItem(book);
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
            player.sendMessage("§aCheck your inventory — the Chef Guide book has been added. Right-click it to read.");
            data.lastGuideBookAt = System.currentTimeMillis();
        } catch (Exception e) {
            player.sendMessage("§cSomething went wrong creating the guide book — an admin needs to check the console for the error.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create/give the Guide Book", e);
        }
    }

    // ---------------------------------------------------------------- ADMIN PANEL (in-game editor)

    public void openAdminPanel(Player admin) {
        Inventory gui = inv(36, "🛠 Admin Panel", "admin_panel", null);
        gui.setItem(10, item(Material.REDSTONE, "§cReload Config", List.of("§7Reload config.yml"), "admin_reload", null));
        gui.setItem(11, item(Material.EXPERIENCE_BOTTLE, "§bGlobal XP Boost", List.of("§730 min, all online players"), "admin_buff", "xp:30"));
        gui.setItem(12, item(Material.EMERALD, "§bGlobal Point Boost", List.of("§730 min, all online players"), "admin_buff", "pointboost:30"));
        gui.setItem(13, item(Material.NETHER_STAR, "§bGlobal Better Tasks", List.of("§730 min, all online players"), "admin_buff", "bettertasks:30"));
        gui.setItem(15, item(Material.PLAYER_HEAD, "§eView/Edit Players", List.of("§7Browse players", "§7View level, points, active task"), "nav", "admin_players"));
        gui.setItem(16, item(Material.NETHER_STAR, "§d👑 Premium Members", List.of("§7View, grant, or revoke premium"), "nav", "admin_premium"));
        gui.setItem(19, item(Material.GOLD_INGOT, "§6💰 Edit Shop Prices", List.of("§7Left-click: +100  §7Shift-left: +1000", "§7Right-click: -100  §7Shift-right: -1000"), "nav", "admin_shop_edit"));
        gui.setItem(20, item(Material.EXPERIENCE_BOTTLE, "§6📊 Edit Tier Yields", List.of("§7Points earned per task, per tier"), "nav", "admin_tier_edit"));
        gui.setItem(21, item(Material.COMPARATOR, "§6⚙ Edit Settings", List.of("§7Skip cost, block cost, teleport cost"), "nav", "admin_settings_edit"));
        gui.setItem(31, closeButton());
        for (int i = 0; i < 36; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    public void openAdminPlayerList(Player admin) {
        Inventory gui = inv(54, "🛠 Online Players", "admin_players", null);
        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            gui.setItem(slot++, item(Material.PLAYER_HEAD, "§f" + p.getName(), List.of("§7Click to view/edit"), "nav", "admin_player_view:" + p.getUniqueId()));
        }
        gui.setItem(49, backButton("admin_panel"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    public void openAdminPlayerView(Player admin, java.util.UUID target) {
        PlayerGatherData data = plugin.dataStore().get(target);
        int level = plugin.taskService().levelOf(data);
        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null) name = target.toString().substring(0, 8);

        Inventory gui = inv(36, "🛠 " + name, "admin_player_view", target.toString());

        gui.setItem(4, item(Material.RAW_IRON, "§6Level " + level,
                List.of("§7XP: " + data.totalXp, "§7Points: " + data.points, "§7Streak: " + data.streak), null, null));

        gui.setItem(10, item(Material.LIME_DYE, "§a+1 Level", null, "admin_adjust", "level_up:" + target));
        gui.setItem(11, item(Material.RED_DYE, "§c-1 Level", null, "admin_adjust", "level_down:" + target));
        gui.setItem(13, item(Material.LIME_DYE, "§a+100 Points", null, "admin_adjust", "points_up:" + target));
        gui.setItem(14, item(Material.RED_DYE, "§c-100 Points", null, "admin_adjust", "points_down:" + target));

        GatherTask task = data.activeTask;
        gui.setItem(19, item(Material.WRITABLE_BOOK, "§eActive Task",
                List.of(task == null ? "§7None" : "§7" + task.displayName() + " " + task.progress() + "/" + task.required()),
                null, null));
        gui.setItem(21, item(Material.ARROW, "§6Force New Task", List.of("§7Rerolls their current task"), "admin_adjust", "force_task:" + target));
        gui.setItem(23, item(Material.BARRIER, "§cClear Task", List.of("§7Removes their active task entirely"), "admin_adjust", "clear_task:" + target));

        gui.setItem(31, item(Material.TNT, "§4§lReset Player (ALL progress)",
                List.of("§cWipes level, XP, points, streak,", "§ctask, blocks, purchases, boosts,", "§cand Legendary Request progress.",
                        "§c§lThis cannot be undone — click to confirm"),
                "admin_adjust", "reset_player:" + target));

        gui.setItem(27, backButton("admin_players"));
        for (int i = 0; i < 36; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- ADMIN: PREMIUM MEMBERS

    public void openAdminPremiumList(Player admin) {
        Inventory gui = inv(54, "👑 Premium Members", "admin_premium", null);

        java.util.Set<java.util.UUID> shown = new java.util.LinkedHashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) shown.add(p.getUniqueId());
        shown.addAll(plugin.premiumService().grantedUuids());

        int slot = 0;
        for (java.util.UUID uuid : shown) {
            if (slot >= 45) break;
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            String name = target.getName() != null ? target.getName() : uuid.toString().substring(0, 8);
            String status = plugin.premiumService().describeStatus(target);
            boolean manual = plugin.premiumService().isManuallyGranted(uuid);

            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + status);
            if (manual) {
                lore.add("§cClick to revoke manual grant");
            } else if (target.isOp() || (target.getPlayer() != null && target.getPlayer().hasPermission("premiumcooking"))) {
                lore.add("§7Already premium via OP/permission");
                lore.add("§7(manual grant would be redundant)");
            } else {
                lore.add("§aClick to grant premium");
            }
            gui.setItem(slot++, item(Material.PLAYER_HEAD, "§f" + name, lore, "admin_toggle_premium", uuid.toString()));
        }

        gui.setItem(49, backButton("admin_panel"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- ADMIN: EDIT SHOP / TIERS / SETTINGS

    public void openAdminShopEdit(Player admin) {
        Inventory gui = inv(54, "💰 Edit Shop Prices", "admin_shop_edit", null);
        int slot = 0;
        for (var entry : plugin.gatherConfig().shopItems().entrySet()) {
            if (slot >= 45) break;
            String id = entry.getKey();
            GatherConfig.ShopItem shopItem = entry.getValue();
            gui.setItem(slot++, item(shopItem.material(), "§f" + shopItem.display(),
                    List.of("§7Cost: §f" + PointsUtil.formatShort((long) shopItem.cost()),
                            "§7Left: +100 §7Shift-left: +1000",
                            "§7Right: -100 §7Shift-right: -1000"),
                    "admin_shop_price", id));
        }
        gui.setItem(49, backButton("admin_panel"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    public void openAdminTierEdit(Player admin) {
        Inventory gui = inv(27, "📊 Edit Tier Yields", "admin_tier_edit", null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int t = 1; t <= 7; t++) {
            GatherTier tier = plugin.gatherConfig().tier(t);
            if (tier == null) continue;
            gui.setItem(slots[t - 1], item(Material.EXPERIENCE_BOTTLE, tier.display(),
                    List.of("§7Points/task: §f" + tier.baseCoins(),
                            "§7Left: +10 §7Shift-left: +100",
                            "§7Right: -10 §7Shift-right: -100"),
                    "admin_tier_yield", String.valueOf(t)));
        }
        gui.setItem(22, backButton("admin_panel"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    public void openAdminSettingsEdit(Player admin) {
        Inventory gui = inv(27, "⚙ Edit Settings", "admin_settings_edit", null);
        gui.setItem(11, item(Material.ARROW, "§fTask Skip Cost",
                List.of("§7Current: " + PointsUtil.formatShort((long) plugin.gatherConfig().skipCost()),
                        "§7Left: +10 §7Shift-left: +100", "§7Right: -10 §7Shift-right: -100"),
                "admin_setting", "skip-cost"));
        gui.setItem(13, item(Material.BARRIER, "§fTask Block Cost",
                List.of("§7Current: " + PointsUtil.formatShort((long) plugin.gatherConfig().taskBlockCost()),
                        "§7Left: +10 §7Shift-left: +100", "§7Right: -10 §7Shift-right: -100"),
                "admin_setting", "task-block-cost"));
        gui.setItem(15, item(Material.ENDER_PEARL, "§fMaster Teleport Cost",
                List.of("§7Current: " + PointsUtil.formatShort((long) plugin.gatherConfig().masterTeleportCost()),
                        "§7Left: +10 §7Shift-left: +100", "§7Right: -10 §7Shift-right: -100"),
                "admin_setting", "master-teleport-cost"));
        gui.setItem(22, backButton("admin_panel"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    private long clickDelta(org.bukkit.event.inventory.ClickType click, long small, long big) {
        return switch (click) {
            case LEFT -> small;
            case SHIFT_LEFT -> big;
            case RIGHT -> -small;
            case SHIFT_RIGHT -> -big;
            default -> 0;
        };
    }

    // ---------------------------------------------------------------- MAIN MENU

    public void openMainMenu(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        int level = plugin.taskService().levelOf(data);
        GatherTier tier = plugin.gatherConfig().tierForLevel(level);
        long xpIntoLevel = data.totalXp - XpTable.xpForLevel(level);
        long xpForNext = XpTable.xpForNextLevel(level) - XpTable.xpForLevel(level);
        double streakMult = plugin.gatherConfig().streakMultiplier(data.streak);
        int milestone = plugin.gatherConfig().nextStreakMilestone(data.streak);

        Inventory gui = inv(54, "🍳 Chef", "main", null);

        gui.setItem(4, item(Material.RAW_IRON, "§6Chef Level: " + level,
                List.of(tier.display() + " §7— " + tier.difficulty(),
                        "§7XP: " + data.totalXp + " / " + XpTable.xpForNextLevel(level),
                        xpForNext > 0 ? bar((double) xpIntoLevel / xpForNext, 20) : "§aMAX LEVEL"),
                null, null));

        gui.setItem(13, item(Material.BLAZE_POWDER, "§6🔥 Streak",
                List.of("§7Current Streak: §f" + data.streak,
                        streakMult > 0 ? "§a+" + (int) (streakMult * 100) + "% Points" : "§7No active bonus",
                        "§7Next Milestone: " + milestone + " Tasks",
                        bar((double) data.streak / milestone, 18) + " " + data.streak + "/" + milestone),
                null, null));

        gui.setItem(20, item(Material.WRITABLE_BOOK, "§e📜 Active Task", List.of("§7View your current task"), "nav", "task_details"));
        gui.setItem(22, item(Material.MAP, "§b📈 Level Unlocks", List.of("§7See what's next"), "nav", "level_unlocks"));
        gui.setItem(24, item(Material.CHEST, "§d📦 Catch Database", List.of("§7Browse all tiers"), "nav", "resource_db"));
        gui.setItem(29, item(Material.FURNACE, "§7🔥 Kitchen Requirements", List.of("§7What's needed per tier"), "nav", "tool_requirements"));
        gui.setItem(31, item(Material.POTION, "§a✨ Buffs", List.of("§7Your active boosts"), "nav", "buffs"));
        gui.setItem(33, item(Material.WRITTEN_BOOK, "§b📖 Guide Book", List.of("§7Everything about Cooking, in one place", "§724 hour cooldown per book"), "open_guide", null));
        gui.setItem(35, item(Material.CAKE, "§b📖 Recipe Book", List.of("§7Your dish collection log",
                "§7Discovered: " + data.recipeBookDiscovered.size() + "/" + totalRecipeBookCount()), "nav", "recipebook"));

        gui.setItem(40, item(Material.EMERALD, "§a💰 Points: " + PointsUtil.formatShort(data.points),
                List.of("§7Earned from completing tasks", "§7and Legendary Requests", "§7Spend them at the Chef Shop"),
                null, null));

        gui.setItem(49, item(Material.GOLDEN_APPLE, "§6🏆 Leaderboard", List.of("§7View the top Chef players"), "nav", "leaderboard_hub"));
        gui.setItem(53, closeButton());

        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());

        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- RESOURCE DATABASE

    public void openResourceDatabase(Player player) {
        Inventory gui = inv(27, "📦 Resource Database", "resource_db", null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        Material[] icons = {Material.BREAD, Material.MUSHROOM_STEW, Material.CAKE,
                Material.SUSPICIOUS_STEW, Material.GOLDEN_APPLE, Material.GOLDEN_CARROT, Material.NETHER_STAR};

        for (int t = 1; t <= 7; t++) {
            GatherTier tier = plugin.gatherConfig().tier(t);
            if (tier == null) continue;
            String targetMenu = t == 7 ? "special_requests" : "resource_db_tier:" + t;
            gui.setItem(slots[t - 1], item(icons[t - 1], tier.display() + " §7— " + tier.difficulty(),
                    List.of("§7Requires Level " + tier.minLevel(),
                            t == 7 ? "§7Yield: Legendary Requests" : "§7Yield Points: " + tier.baseCoins(),
                            tier.premium() ? "§d*premiumcooking" : "§aFree"),
                    "nav", targetMenu));
        }
        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openResourceTier(Player player, int tierNumber) {
        GatherTier tier = plugin.gatherConfig().tier(tierNumber);
        if (tier == null) return;
        Inventory gui = inv(54, tier.display() + " Resources", "resource_db_tier", String.valueOf(tierNumber));
        int slot = 10;
        for (ResourceDef def : tier.resources()) {
            if (slot > 43) break;
            gui.setItem(slot++, item(def.material(), "§f" + typeIcon(def.type()) + " " + def.displayName(),
                    List.of("§7Requires Level " + def.requiredLevel(),
                            "§7Task Size: " + def.minAmount() + "–" + def.maxAmount()),
                    null, null));
        }
        gui.setItem(49, backButton("resource_db"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- LEVEL UNLOCKS

    public void openLevelUnlocks(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        int level = plugin.taskService().levelOf(data);
        GatherTier tier = plugin.gatherConfig().tierForLevel(level);

        Inventory gui = inv(27, "📈 Level Unlocks", "level_unlocks", null);

        if (level >= XpTable.MAX_LEVEL) {
            gui.setItem(13, item(Material.NETHER_STAR, "§6§lLEVEL 99 — CHEF",
                    List.of("§7⭐ 13,000,000 Total XP ⭐",
                            "§d👑 100% Legendary Request Pool",
                            "§7You have mastered Cooking."),
                    null, null));
        } else if (tier.number() == 7) {
            gui.setItem(13, item(Material.NETHER_STAR, "§d👑 Tier 7 — Legendary",
                    List.of("§7Unlocked at Level " + tier.minLevel(),
                            "§a✓ Legendary Requests Unlocked",
                            "§7Check §fSpecial Requests §7from the main menu"),
                    null, null));
        } else {
            double chance = XpTable.higherTierChance(level, tier.number(), tier.minLevel());
            GatherTier next = plugin.gatherConfig().tier(tier.number() + 1);
            gui.setItem(11, item(Material.EXPERIENCE_BOTTLE, "§6" + tier.display() + " — " + tier.difficulty(),
                    List.of("§7Unlocked at Level " + tier.minLevel(),
                            "§a✓ " + tier.display() + " Tasks",
                            "§e🎲 Higher Tier Chance: " + (int) (chance * 100) + "%"),
                    null, null));
            if (next != null) {
                gui.setItem(15, item(Material.MAP, "§b" + next.display() + " (Next Tier)",
                        List.of("§7Unlocks at Level " + next.minLevel(),
                                next.premium() ? "§d*premiumcooking required" : "§aFree"),
                        null, null));
            }
        }
        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- TOOL REQUIREMENTS

    public void openToolRequirements(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        int level = plugin.taskService().levelOf(data);

        Inventory gui = inv(27, "🔥 Kitchen Requirements", "tool_requirements", null);
        List<String> lines = new ArrayList<>();
        lines.add("§7Your Chef Level: §f" + level);
        lines.add(" ");
        lines.add(level >= 1 && level < 25 ? "§a✓ Furnace / Campfire §7(current)" : "§7Furnace / Campfire — Levels 1-24");
        lines.add(level >= 25 && level < 40 ? "§a✓ Smoker recommended §7(current)" : (level >= 40 ? "§7Smoker recommended — Levels 25-39" : "§7🔒 Smoker recommended — Level 25"));
        lines.add(level >= 40 ? "§a✓ No station requirement §7(current)" : "§7🔒 No station requirement — Level 40");
        lines.add(" ");
        lines.add("§7Smoker cooks faster and counts toward");
        lines.add("§7contracts identically to a furnace.");
        lines.add(" ");
        lines.add("§7Level 40+ is about sourcing ingredients,");
        lines.add("§7not equipment — there's no vanilla station");
        lines.add("§7above a Smoker to escalate into.");
        lines.add(" ");
        lines.add("§cNote: Campfire-cooked dishes aren't tracked");
        lines.add("§c(no vanilla extraction event for them) — use");
        lines.add("§ca Furnace or Smoker for contract progress.");

        gui.setItem(13, item(Material.FURNACE, "§fKitchen Progression", lines, null, null));
        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- BUFFS

    public void openBuffs(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(27, "✨ Buffs", "buffs", null);

        gui.setItem(10, buffIcon(Material.EXPERIENCE_BOTTLE, "XP Boost", data.xpBoostExpiry, "+10% Chef XP"));
        gui.setItem(12, buffIcon(Material.EMERALD, "Point Boost", data.pointBoostExpiry, "+10% Chef Points"));
        gui.setItem(14, buffIcon(Material.NETHER_STAR, "Better Tasks", data.betterTasksExpiry, "+5% Higher-Tier chance"));
        gui.setItem(16, buffIcon(Material.PRISMARINE_CRYSTALS, "Cooking Buff", data.luckyStrikeBoostExpiry,
                "+10% Rare-Catch chance — paid (Chef Shop) or earned every 70 catches (25% roll)"));

        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    private ItemStack buffIcon(Material mat, String name, long expiry, String effect) {
        long remaining = expiry - System.currentTimeMillis();
        boolean active = remaining > 0;
        List<String> lore = new ArrayList<>();
        lore.add(effect);
        if (active) {
            long minutes = remaining / 60000;
            long seconds = (remaining / 1000) % 60;
            lore.add("§aACTIVE §7— " + minutes + "m " + seconds + "s remaining");
        } else {
            lore.add("§cINACTIVE §7— purchase from the Chef Shop");
        }
        return item(mat, (active ? "§a" : "§c") + name, lore, null, null);
    }

    // ---------------------------------------------------------------- RECIPE BOOK

    /** Total number of distinct Recipe Book entries. Suspicious Stew is one
     *  Material regardless of which flower made it, but each discovered effect
     *  counts as its own entry — since the actual flower-to-effect mapping
     *  varies a little by game version (per the design doc), the expected
     *  count is a config value the admin can tune rather than a hardcoded
     *  list, and it grows on its own as new effects get discovered anyway. */
    private int totalRecipeBookCount() {
        int total = 0;
        int expectedStewEffects = plugin.getConfig().getInt("settings.expected-suspicious-stew-effects", 8);
        for (GatherTier tier : plugin.gatherConfig().allTiers()) {
            for (ResourceDef def : tier.resources()) {
                total += def.material() == Material.SUSPICIOUS_STEW ? expectedStewEffects : 1;
            }
        }
        return total;
    }

    public void openRecipeBook(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        int total = totalRecipeBookCount();
        int discovered = data.recipeBookDiscovered.size();

        Inventory gui = inv(54, "📖 Recipe Book", "recipebook", null);

        int slot = 0;
        for (GatherTier tier : plugin.gatherConfig().allTiers()) {
            for (ResourceDef def : tier.resources()) {
                if (slot >= 45) break;
                if (def.material() == Material.SUSPICIOUS_STEW) {
                    // Show each discovered effect as its own entry; one extra
                    // "???" placeholder hints there's more to find.
                    boolean anyFound = false;
                    for (String key : data.recipeBookDiscovered) {
                        if (!key.startsWith("SUSPICIOUS_STEW:")) continue;
                        if (slot >= 45) break;
                        anyFound = true;
                        String effectName = key.substring("SUSPICIOUS_STEW:".length());
                        int caught = data.recipeBookCookCounts.getOrDefault(key, 0);
                        gui.setItem(slot++, item(Material.SUSPICIOUS_STEW, "§a✓ Suspicious Stew (" + effectName + ")",
                                List.of("§7" + tier.display() + " §7— cooked §f" + caught + "§7 total"), null, null));
                    }
                    if (slot < 45) {
                        gui.setItem(slot++, item(Material.BARRIER, "§7❓ Suspicious Stew (???)",
                                List.of(anyFound ? "§7More flower effects to find" : "§7Not yet discovered"), null, null));
                    }
                    continue;
                }
                boolean found = data.recipeBookDiscovered.contains(def.material().name());
                int caught = data.recipeBookCookCounts.getOrDefault(def.material().name(), 0);
                gui.setItem(slot++, item(found ? def.material() : Material.BARRIER,
                        (found ? "§a✓ " : "§7❓ ") + (found ? def.displayName() : "???"),
                        List.of(found ? "§7" + tier.display() + " §7— cooked §f" + caught + "§7 total" : "§7Not yet discovered"),
                        null, null));
            }
        }

        gui.setItem(49, item(Material.CAKE, "§bDiscovered: " + discovered + "/" + total,
                List.of(bar((double) discovered / Math.max(1, total), 20)),
                null, null));

        boolean complete = discovered >= total && total > 0;
        if (complete && !data.recipeBookRewardClaimed) {
            gui.setItem(51, item(Material.CHEST, "§6§lClaim Completion Reward!",
                    List.of("§7You've discovered every dish.", "§aClick to claim your reward!"),
                    "claim_recipebook", null));
        } else if (data.recipeBookRewardClaimed) {
            gui.setItem(51, item(Material.CHEST, "§7Reward Already Claimed", null, null, null));
        }

        gui.setItem(45, backButton("main"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- SPECIAL REQUESTS (Tier 7)

    public void openSpecialRequests(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(36, "🎯 Special Requests", "special_requests", null);

        int slot = 10;
        for (var def : plugin.gatherConfig().legendaryRequests().values()) {
            boolean available = plugin.legendaryRequestService().isAvailable(data, def.id());
            List<String> lore = new ArrayList<>();
            if (available) {
                int target = plugin.legendaryRequestService().ensureTarget(data, def);
                int progress = data.legendaryProgress.getOrDefault(def.id(), 0);
                lore.add("§7Cook " + target + " " + def.material().name());
                lore.add(bar((double) progress / target, 18) + " " + progress + "/" + target);
                lore.add("§7Yield: " + com.warriorssmp.cooking.model.PointsUtil.format(def.yield()));
            } else {
                long remaining = data.legendaryReadyAt.getOrDefault(def.id(), 0L) - System.currentTimeMillis();
                lore.add("§cON COOLDOWN");
                lore.add("§7Ready in: " + formatDuration(remaining));
            }
            gui.setItem(slot, item(def.material(), def.display(), lore, null, null));
            slot += slot == 11 ? 2 : 1;
        }

        GatherConfig.TriadTrialDef triad = plugin.gatherConfig().triadTrial();
        if (triad != null) {
            List<String> lore = new ArrayList<>();
            if (plugin.legendaryRequestService().isTriadAvailable(data)) {
                lore.add("§7⛏ Ancient Debris: " + data.triadAncientDebrisProgress + "/" + triad.ancientDebrisAmount()
                        + (data.triadAncientDebrisProgress >= triad.ancientDebrisAmount() ? " ✓" : ""));
                lore.add("§7🪓 Crimson/Warped Stems: " + data.triadStemsProgress + "/" + triad.stemsAmount()
                        + (data.triadStemsProgress >= triad.stemsAmount() ? " ✓" : ""));
                lore.add("§7🌾 Chorus Fruit: " + data.triadChorusProgress + "/" + triad.chorusAmount()
                        + (data.triadChorusProgress >= triad.chorusAmount() ? " ✓" : ""));
                lore.add("§7Yield: " + com.warriorssmp.cooking.model.PointsUtil.format(triad.yield()));
            } else {
                long remaining = data.triadReadyAt - System.currentTimeMillis();
                lore.add("§cON COOLDOWN");
                lore.add("§7Ready in: " + formatDuration(remaining));
            }
            gui.setItem(22, item(Material.NETHER_STAR, triad.display(), lore, null, null));
        }

        PlayerGatherData self = data;
        boolean tpUnlocked = self.masterTeleportUnlocked;
        gui.setItem(31, item(Material.ENDER_PEARL, (tpUnlocked ? "§d" : "§7") + "Teleport to Chef",
                List.of(tpUnlocked ? "§aClick to teleport" : "§cPurchase from Chef Shop"),
                tpUnlocked ? "master_tp" : null, null));

        gui.setItem(27, backButton("main"));
        for (int i = 0; i < 36; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "now";
        long totalMinutes = ms / 60000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "h " + minutes + "m";
    }

    // ---------------------------------------------------------------- TASK DETAILS / HISTORY

    public void openTaskDetails(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        GatherTask task = plugin.taskService().ensureTask(player, data);
        GatherTier tier = plugin.gatherConfig().tier(task.tier());

        Inventory gui = inv(27, "📜 Active Task", "task_details", null);

        List<String> lore = new ArrayList<>();
        lore.add("§7Progress: " + task.progress() + " / " + task.required());
        lore.add(bar((double) task.progress() / task.required(), 18));
        lore.add(" ");
        lore.add("§7Tier: " + tier.display());
        lore.add("§7Yield Points: " + tier.baseCoins());

        gui.setItem(13, item(task.material(), "§e" + task.displayName(), lore, null, null));
        gui.setItem(21, item(Material.BOOK, "§6📜 Task History", List.of("§7View your last completed tasks"), "nav", "task_history"));
        gui.setItem(23, item(Material.ARROW, "§cSkip Task", List.of("§7Cost: " + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(plugin.gatherConfig().skipCost()))), "skip_task", null));
        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openTaskHistory(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(54, "📜 Task History", "task_history", null);

        int slot = 0;
        int start = Math.max(0, data.history.size() - 20);
        for (int i = data.history.size() - 1; i >= start && slot < 45; i--) {
            var entry = data.history.get(i);
            List<String> lore = new ArrayList<>();
            if (entry.skipped()) {
                lore.add("§6🔄 SKIPPED");
                lore.add("§c" + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(entry.coinsGained())));
            } else {
                lore.add("§a✓ COMPLETED");
                lore.add("§7" + entry.amount() + " cooked • +" + entry.xpGained() + " XP • +" + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(entry.coinsGained())));
            }
            gui.setItem(slot++, item(entry.skipped() ? Material.ARROW : Material.CHEST,
                    "§f" + entry.materialName(), lore, null, null));
        }

        gui.setItem(49, backButton("task_details"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- LEADERBOARDS

    public void openLeaderboardHub(Player player) {
        Inventory gui = inv(27, "🏆 Chef Leaderboard", "leaderboard_hub", null);
        gui.setItem(10, item(Material.NETHER_STAR, "§e⭐ Chef XP", null, "nav", "leaderboard:XP"));
        gui.setItem(11, item(Material.BLAZE_POWDER, "§6🔥 Longest Streak", null, "nav", "leaderboard:STREAK"));
        gui.setItem(12, item(Material.WRITABLE_BOOK, "§b🍳 Contracts Completed", null, "nav", "leaderboard:TASKS_COMPLETED"));
        gui.setItem(13, item(Material.CHEST, "§d📦 Dishes Cooked", null, "nav", "leaderboard:RESOURCES_GATHERED"));
        gui.setItem(14, item(Material.EMERALD, "§a⭐ Lucky Strikes", null, "nav", "leaderboard:LUCKY_STRIKES"));
        gui.setItem(15, item(Material.NETHER_STAR, "§d👑 Legendary Requests", null, "nav", "leaderboard:LEGENDARY_REQUESTS"));
        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openLeaderboard(Player player, LeaderboardService.Board board) {
        Inventory gui = inv(54, "🏆 " + boardTitle(board), "leaderboard", board.name());
        List<PlayerGatherData> top = plugin.leaderboardService().top(board, 10);

        String[] medals = {"§6🥇", "§7🥈", "§c🥉"};
        int slot = 10;
        for (int i = 0; i < top.size(); i++) {
            PlayerGatherData d = top.get(i);
            String rankLabel = i < 3 ? medals[i] : "§7#" + (i + 1);
            String name = Bukkit.getOfflinePlayer(d.uuid).getName();
            if (name == null) name = d.uuid.toString().substring(0, 8);
            gui.setItem(slot++, item(Material.PLAYER_HEAD, rankLabel + " " + name,
                    List.of(boardLine(board, d)), null, null));
            if (slot == 17) slot = 19; // skip a row gap for readability
        }

        PlayerGatherData self = plugin.dataStore().get(player.getUniqueId());
        int rank = plugin.leaderboardService().rankOf(board, player.getUniqueId());
        gui.setItem(49, item(Material.PLAYER_HEAD, "§eYour Rank: #" + (rank < 0 ? "?" : rank),
                List.of(boardLine(board, self)), null, null));

        gui.setItem(45, backButton("leaderboard_hub"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    private String boardTitle(LeaderboardService.Board board) {
        return switch (board) {
            case XP -> "Chef XP";
            case STREAK -> "Longest Streak";
            case TASKS_COMPLETED -> "Tasks Completed";
            case RESOURCES_GATHERED -> "Dishes Cooked";
            case LUCKY_STRIKES -> "Lucky Strikes";
            case LEGENDARY_REQUESTS -> "Legendary Requests Completed";
        };
    }

    private String boardLine(LeaderboardService.Board board, PlayerGatherData d) {
        int level = plugin.taskService().levelOf(d);
        return switch (board) {
            case XP -> "§7Level " + level + " • " + d.totalXp + " XP";
            case STREAK -> "§7Streak: " + d.streak;
            case TASKS_COMPLETED -> "§7Tasks: " + d.lifetimeTasksCompleted;
            case RESOURCES_GATHERED -> "§7Cooked: " + d.lifetimeResourcesGathered;
            case LUCKY_STRIKES -> "§7Lucky Strikes: " + d.lifetimeLuckyStrikes;
            case LEGENDARY_REQUESTS -> "§7Completed: " + d.lifetimeLegendaryCompleted;
        };
    }

    // ---------------------------------------------------------------- CHEF HUB / NEW TASK

    public void openMasterMenu(Player player) {
        Inventory gui = inv(27, "🧑\u200D🍳 Chef", "master", null);
        gui.setItem(10, item(Material.EMERALD, "§6🪙 Chef Shop", List.of("§7Spend your Points"), "nav", "shop:1"));
        gui.setItem(12, item(Material.FURNACE, "§a🍳 Start Task", List.of("§7Receive your next Chef task"), "nav", "new_task"));
        gui.setItem(14, item(Material.BARRIER, "§c🚫 Task Block", List.of("§7Block or manage unwanted tasks"), "nav", "block_menu"));
        gui.setItem(16, item(Material.NETHER_STAR, "§d🎯 Special Requests", List.of("§7Tier 7 Legendary Requests", "§7Only available here"), "nav", "special_requests"));
        gui.setItem(22, closeButton());
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openNewTaskConfirm(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        int level = plugin.taskService().levelOf(data);
        GatherTier tier = plugin.gatherConfig().tierForLevel(level);

        Inventory gui = inv(27, "🍳 New Chef Task", "new_task", null);
        gui.setItem(13, item(Material.PAPER, "§eReady for your next task?",
                List.of("§7Chef Level: " + level, "§7Current Tier: " + tier.display(),
                        " ", "§7Your next task is randomly picked from your available pool."),
                null, null));
        gui.setItem(11, item(Material.FURNACE, "§a🍳 Accept", List.of("§7FREE"), "accept_task", null));
        gui.setItem(15, item(Material.ARROW, "§6🔄 Skip", List.of("§7" + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(plugin.gatherConfig().skipCost()))), "skip_task_new", null));
        gui.setItem(22, closeButton());
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- SHOP

    public void openShop(Player player, int page) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(54, "🪙 Chef Shop", "shop", String.valueOf(page));

        int slot = 0;
        for (var entry : plugin.gatherConfig().shopItems().entrySet()) {
            if (slot >= 45) break;
            String id = entry.getKey();
            GatherConfig.ShopItem shopItem = entry.getValue();
            boolean owned = shopItem.limit() > 0 && data.purchasedOneTimeItems.contains(id.hashCode());
            boolean locked = shopItem.premium() && !plugin.premiumService().isPremium(player);

            List<String> lore = new ArrayList<>();
            lore.add("§7" + shopItem.description());
            lore.add(" ");
            lore.add("§7Cost: §f" + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(shopItem.cost())));
            lore.add(shopItem.limit() > 0 ? "§7Limit: 1 time" : "§7No Limit");
            if (shopItem.premium()) lore.add("§d*premiumcooking");
            if (owned) lore.add("§aALREADY OWNED");
            if (locked) lore.add("§cRequires premiumcooking");

            gui.setItem(slot++, item(shopItem.material(), (locked ? "§7" : "§f") + shopItem.display(),
                    lore, owned || locked ? null : "buy", id));
        }

        gui.setItem(49, backButton("master"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- BLOCK MENU

    public void openBlockMenu(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(27, "🚫 Task Block", "block_menu", null);
        gui.setItem(11, item(Material.BARRIER, "§c🚫 Block a Resource",
                List.of("§7Cost: " + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(plugin.gatherConfig().taskBlockCost()))), "nav", "block_tier_select"));
        gui.setItem(15, item(Material.BOOK, "§f📋 Blocked Resources",
                List.of("§7Currently blocked: " + data.blockedResources.size()), "nav", "blocked_list"));
        gui.setItem(22, backButton("master"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openBlockTierSelect(Player player) {
        Inventory gui = inv(27, "🚫 Block a Resource", "block_tier_select", null);
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int t = 1; t <= 6; t++) {
            GatherTier tier = plugin.gatherConfig().tier(t);
            if (tier == null) continue;
            gui.setItem(slots[t - 1], item(Material.PAPER, tier.display(), null, "nav", "block_resource_list:" + t));
        }
        gui.setItem(22, backButton("block_menu"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openBlockResourceList(Player player, int tierNumber) {
        GatherTier tier = plugin.gatherConfig().tier(tierNumber);
        if (tier == null) return;
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(54, "🚫 Block — " + tier.rawName(), "block_resource_list", String.valueOf(tierNumber));

        int slot = 10;
        for (ResourceDef def : tier.resources()) {
            if (slot > 43) break;
            boolean blocked = data.blockedResources.contains(def.material());
            gui.setItem(slot++, item(def.material(), (blocked ? "§c" : "§f") + typeIcon(def.type()) + " " + def.displayName(),
                    List.of(blocked ? "§cAlready blocked — click to unblock" : "§7Cost: " + com.warriorssmp.cooking.model.PointsUtil.format(Math.round(plugin.gatherConfig().taskBlockCost()))),
                    "toggle_block", def.material().name()));
        }
        gui.setItem(49, backButton("block_tier_select"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openBlockedList(Player player) {
        PlayerGatherData data = plugin.dataStore().get(player.getUniqueId());
        Inventory gui = inv(54, "📋 Blocked Resources", "blocked_list", null);
        int slot = 0;
        for (Material m : data.blockedResources) {
            if (slot >= 45) break;
            gui.setItem(slot++, item(m, "§c" + m.name(), List.of("§7Click to unblock"), "toggle_block", m.name()));
        }
        gui.setItem(49, backButton("block_menu"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- CLICK ROUTING

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String data = meta.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (action == null) return;

        Player player = (Player) event.getWhoClicked();

        if ((action.startsWith("admin_") || (action.equals("nav") && data != null && data.startsWith("admin_")))
                && !player.hasPermission("cooking.admin")) {
            player.sendMessage("§cYou don't have permission for that.");
            return;
        }

        try {
            dispatchClick(player, event, action, data);
        } catch (Exception e) {
            player.sendMessage("§cSomething went wrong handling that click — an admin needs to check the console.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unhandled error in menu action '" + action + "' (data='" + data + "')", e);
        }
    }

    private void dispatchClick(Player player, InventoryClickEvent event, String action, String data) {
        switch (action) {
            case "close" -> player.closeInventory();
            case "nav" -> navigate(player, data);
            case "open_guide" -> openGuideBook(player);
            case "claim_recipebook" -> {
                PlayerGatherData pd = plugin.dataStore().get(player.getUniqueId());
                int total = totalRecipeBookCount();
                if (pd.recipeBookRewardClaimed) {
                    player.sendMessage("§cYou've already claimed the Recipe Book reward.");
                } else if (pd.recipeBookDiscovered.size() < total) {
                    player.sendMessage("§cKeep cooking — you haven't discovered every dish yet.");
                } else {
                    pd.recipeBookRewardClaimed = true;
                    pd.points += 5000;
                    player.sendMessage("§6§lRECIPE BOOK COMPLETE! §7You've cooked everything — +5,000 Points!");
                }
                openRecipeBook(player);
            }
            case "admin_reload" -> {
                plugin.reloadConfig();
                plugin.gatherConfig().load();
                plugin.logStartupSummary();
                player.sendMessage("§aChef config reloaded — check console for a full summary.");
                openAdminPanel(player);
            }
            case "admin_buff" -> {
                String[] buffParts = data.split(":", 2);
                long minutes = Long.parseLong(buffParts[1]);
                long expiry = System.currentTimeMillis() + minutes * 60_000L;
                int affected = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    PlayerGatherData pd = plugin.dataStore().get(online.getUniqueId());
                    switch (buffParts[0]) {
                        case "xp" -> pd.xpBoostExpiry = Math.max(pd.xpBoostExpiry, expiry);
                        case "pointboost" -> pd.pointBoostExpiry = Math.max(pd.pointBoostExpiry, expiry);
                        case "bettertasks" -> pd.betterTasksExpiry = Math.max(pd.betterTasksExpiry, expiry);
                        default -> {}
                    }
                    affected++;
                }
                player.sendMessage("§aGave " + buffParts[0] + " buff (" + minutes + "m) to " + affected + " online player(s).");
            }
            case "admin_adjust" -> {
                String[] adjustParts = data.split(":", 2);
                java.util.UUID targetUuid = java.util.UUID.fromString(adjustParts[1]);
                PlayerGatherData targetData = plugin.dataStore().get(targetUuid);
                Player targetPlayer = Bukkit.getPlayer(targetUuid);
                switch (adjustParts[0]) {
                    case "level_up" -> {
                        int newLevel = Math.min(99, plugin.taskService().levelOf(targetData) + 1);
                        targetData.totalXp = com.warriorssmp.cooking.model.XpTable.xpForLevel(newLevel);
                    }
                    case "level_down" -> {
                        int newLevel = Math.max(1, plugin.taskService().levelOf(targetData) - 1);
                        targetData.totalXp = com.warriorssmp.cooking.model.XpTable.xpForLevel(newLevel);
                    }
                    case "points_up" -> targetData.points += 100;
                    case "points_down" -> targetData.points = Math.max(0, targetData.points - 100);
                    case "force_task" -> {
                        if (targetPlayer != null) plugin.taskService().generateTask(targetPlayer, targetData);
                    }
                    case "clear_task" -> targetData.activeTask = null;
                    case "reset_player" -> {
                        targetData.resetAll();
                        player.sendMessage("§cReset all progress for " + (targetPlayer != null ? targetPlayer.getName() : targetUuid) + ".");
                    }
                    default -> {}
                }
                openAdminPlayerView(player, targetUuid);
            }
            case "admin_toggle_premium" -> {
                java.util.UUID targetUuid = java.util.UUID.fromString(data);
                if (plugin.premiumService().isManuallyGranted(targetUuid)) {
                    plugin.premiumService().revoke(targetUuid);
                    player.sendMessage("§cRevoked manual premium grant.");
                } else {
                    plugin.premiumService().grant(targetUuid);
                    player.sendMessage("§aGranted premium.");
                }
                openAdminPremiumList(player);
            }
            case "admin_shop_price" -> {
                long delta = clickDelta(event.getClick(), 100, 1000);
                if (delta != 0) {
                    String path = "shop." + data + ".cost";
                    double current = plugin.getConfig().getDouble(path, 0);
                    plugin.getConfig().set(path, Math.max(0, current + delta));
                    plugin.saveConfig();
                    plugin.gatherConfig().load();
                }
                openAdminShopEdit(player);
            }
            case "admin_tier_yield" -> {
                long delta = clickDelta(event.getClick(), 10, 100);
                if (delta != 0) {
                    String path = "tiers." + data + ".base-coins";
                    int current = plugin.getConfig().getInt(path, 0);
                    plugin.getConfig().set(path, Math.max(0, (int) (current + delta)));
                    plugin.saveConfig();
                    plugin.gatherConfig().load();
                }
                openAdminTierEdit(player);
            }
            case "admin_setting" -> {
                long delta = clickDelta(event.getClick(), 10, 100);
                if (delta != 0) {
                    String path = "settings." + data;
                    double current = plugin.getConfig().getDouble(path, 0);
                    plugin.getConfig().set(path, Math.max(0, current + delta));
                    plugin.saveConfig();
                    plugin.gatherConfig().load();
                }
                openAdminSettingsEdit(player);
            }
            case "master_tp" -> plugin.masterNpcService().teleportToMaster(player, plugin.dataStore().get(player.getUniqueId()));
            case "skip_task", "skip_task_new" -> {
                PlayerGatherData pd = plugin.dataStore().get(player.getUniqueId());
                if (plugin.taskService().skipTask(player, pd)) {
                    openTaskDetails(player);
                }
            }
            case "accept_task" -> {
                player.closeInventory();
                player.sendMessage("§aTask accepted! Check §e/cooktask §afor details.");
            }
            case "buy" -> {
                PlayerGatherData pd = plugin.dataStore().get(player.getUniqueId());
                ShopService.Result result = plugin.shopService().purchase(player, pd, data);
                switch (result) {
                    case SUCCESS -> player.sendMessage("§aPurchased!");
                    case INSUFFICIENT_FUNDS -> player.sendMessage("§cYou can't afford that.");
                    case ALREADY_OWNED -> player.sendMessage("§cYou already own that.");
                    case NO_PERMISSION -> player.sendMessage("§cThat requires premiumcooking.");
                    case UNKNOWN_ITEM -> player.sendMessage("§cThat item no longer exists.");
                }
                openShop(player, 1);
            }
            case "toggle_block" -> {
                PlayerGatherData pd = plugin.dataStore().get(player.getUniqueId());
                Material m = Material.matchMaterial(data);
                if (m == null) return;
                if (pd.blockedResources.contains(m)) {
                    pd.blockedResources.remove(m);
                    player.sendMessage("§aUnblocked " + m.name() + ".");
                } else {
                    long cost = Math.round(plugin.gatherConfig().taskBlockCost());
                    if (pd.points < cost) {
                        player.sendMessage("§cYou need " + com.warriorssmp.cooking.model.PointsUtil.format(cost) + " to block a resource.");
                        return;
                    }
                    pd.points -= cost;
                    pd.blockedResources.add(m);
                    player.sendMessage("§aBlocked " + m.name() + " from your task pool.");
                }
                openBlockedList(player);
            }
            default -> {}
        }
    }

    private void navigate(Player player, String target) {
        if (target == null) return;
        String[] parts = target.split(":", 2);
        String menu = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;

        switch (menu) {
            case "main" -> openMainMenu(player);
            case "resource_db" -> openResourceDatabase(player);
            case "resource_db_tier" -> openResourceTier(player, Integer.parseInt(arg));
            case "level_unlocks" -> openLevelUnlocks(player);
            case "tool_requirements" -> openToolRequirements(player);
            case "buffs" -> openBuffs(player);
            case "special_requests" -> openSpecialRequests(player);
            case "recipebook" -> openRecipeBook(player);
            case "task_details" -> openTaskDetails(player);
            case "task_history" -> openTaskHistory(player);
            case "leaderboard_hub" -> openLeaderboardHub(player);
            case "leaderboard" -> openLeaderboard(player, LeaderboardService.Board.valueOf(arg));
            case "master" -> openMasterMenu(player);
            case "new_task" -> openNewTaskConfirm(player);
            case "shop" -> openShop(player, arg == null ? 1 : Integer.parseInt(arg));
            case "block_menu" -> openBlockMenu(player);
            case "block_tier_select" -> openBlockTierSelect(player);
            case "block_resource_list" -> openBlockResourceList(player, Integer.parseInt(arg));
            case "blocked_list" -> openBlockedList(player);
            case "admin_panel" -> openAdminPanel(player);
            case "admin_players" -> openAdminPlayerList(player);
            case "admin_player_view" -> openAdminPlayerView(player, java.util.UUID.fromString(arg));
            case "admin_premium" -> openAdminPremiumList(player);
            case "admin_shop_edit" -> openAdminShopEdit(player);
            case "admin_tier_edit" -> openAdminTierEdit(player);
            case "admin_settings_edit" -> openAdminSettingsEdit(player);
            default -> {}
        }
    }
}
