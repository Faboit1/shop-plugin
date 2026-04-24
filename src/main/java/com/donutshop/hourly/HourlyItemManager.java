package com.donutshop.hourly;

import com.donutshop.DonutShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

/**
 * Manages the hourly rotating shop: loads the item pool from hourly-items.yml,
 * picks a weighted-random selection each hour (exactly at the top of the hour),
 * and broadcasts rare-item announcements to all online players.
 */
public class HourlyItemManager {

    private final DonutShop plugin;

    private List<HourlyItem> itemPool = new ArrayList<>();
    private List<HourlyItem> currentItems = new ArrayList<>();
    private BukkitTask scheduledTask;

    private static final long MILLIS_PER_HOUR = 3_600_000L;
        this.plugin = plugin;
    }

    /** Initial startup: load items, pick first set, schedule hourly refresh. */
    public void start() {
        loadItems();
        refresh();
        scheduleHourlyRefresh();
    }

    /** Reload after /shop reload: re-read files, re-pick, reschedule. */
    public void reload() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
        loadItems();
        refresh();
        scheduleHourlyRefresh();
    }

    /** Cancel the scheduled task on shutdown. */
    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    // ── Item pool loading ─────────────────────────────────────

    private void loadItems() {
        File file = new File(plugin.getDataFolder(), "hourly-items.yml");
        if (!file.exists()) {
            plugin.saveResource("hourly-items.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        itemPool.clear();

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("[HourlyShop] No 'items' section found in hourly-items.yml");
            return;
        }

        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(id);
            if (sec == null) continue;

            String type = sec.getString("type", "material");
            String material = sec.getString("material", "STONE");
            String name = sec.getString("name", id);
            List<String> lore = sec.getStringList("lore");
            int weight = sec.getInt("weight", 100);
            double cost = parseCost(sec.getString("cost", "-1"));

            // Accept either a single "command" key or a "commands" list
            List<String> commands;
            if (sec.isList("commands")) {
                commands = sec.getStringList("commands");
            } else if (sec.contains("command")) {
                String cmd = sec.getString("command", "");
                commands = cmd.isEmpty() ? Collections.emptyList() : Collections.singletonList(cmd);
            } else {
                commands = Collections.emptyList();
            }

            itemPool.add(new HourlyItem(id, type, material, name, lore, weight, cost, commands));
        }

        plugin.getLogger().info("[HourlyShop] Loaded " + itemPool.size() + " items into the hourly pool.");
    }

    // ── Item selection & announcements ────────────────────────

    /**
     * Pick a new set of items from the pool and, if any are rare, broadcast the announcement.
     */
    public void refresh() {
        if (itemPool.isEmpty()) {
            currentItems = Collections.emptyList();
            return;
        }

        int count = Math.min(plugin.getConfigManager().getHourlyShopItemCount(), itemPool.size());
        currentItems = selectWeightedRandom(itemPool, count);

        plugin.getLogger().info("[HourlyShop] Items refreshed: " +
                currentItems.stream().map(HourlyItem::getId).reduce((a, b) -> a + ", " + b).orElse("(none)"));

        announceRareItems();
    }

    private void announceRareItems() {
        int totalWeight = itemPool.stream().mapToInt(HourlyItem::getWeight).sum();
        if (totalWeight <= 0) return;

        double rareThreshold = plugin.getConfigManager().getHourlyRareThresholdPercent();
        List<HourlyItem> rareItems = new ArrayList<>();
        for (HourlyItem item : currentItems) {
            double pct = 100.0 * item.getWeight() / totalWeight;
            if (pct <= rareThreshold) {
                rareItems.add(item);
            }
        }

        if (rareItems.isEmpty()) return;

        // Build the comma-separated names string (strip color codes)
        StringJoiner names = new StringJoiner(", ");
        for (HourlyItem item : rareItems) {
            names.add(stripColors(item.getName()));
        }

        String announcementTemplate = plugin.getConfigManager().getHourlyRareAnnouncement();
        String announcement = announcementTemplate.replace("{names}", names.toString());

        // Support legacy &-codes in the announcement
        Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(announcement);

        String soundName = plugin.getConfigManager().getHourlyRareSound();
        float volume = (float) plugin.getConfigManager().getHourlyRareSoundVolume();
        float pitch = (float) plugin.getConfigManager().getHourlyRareSoundPitch();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            if (soundName != null && !soundName.isEmpty() && !soundName.equalsIgnoreCase("NONE")) {
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    // ── Scheduling ────────────────────────────────────────────

    private void scheduleHourlyRefresh() {
        long now = System.currentTimeMillis();
        // Calculate milliseconds until the start of the next full hour
        long nextHourMs = (now / MILLIS_PER_HOUR + 1) * MILLIS_PER_HOUR;
        long delayMs = nextHourMs - now;
        long delayTicks = Math.max(1L, delayMs / 50L);    // 1 tick = 50 ms
        long periodTicks = 72_000L;                         // 1 hour = 3600 s * 20 ticks/s

        scheduledTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refresh,
                delayTicks,
                periodTicks
        );
    }

    // ── Weighted random selection without replacement ──────────

    private List<HourlyItem> selectWeightedRandom(List<HourlyItem> pool, int count) {
        List<HourlyItem> result = new ArrayList<>();
        List<HourlyItem> available = new ArrayList<>(pool);
        Random random = new Random();

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            int totalWeight = available.stream().mapToInt(HourlyItem::getWeight).sum();
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            for (Iterator<HourlyItem> it = available.iterator(); it.hasNext(); ) {
                HourlyItem item = it.next();
                cumulative += item.getWeight();
                if (roll < cumulative) {
                    result.add(item);
                    it.remove();
                    break;
                }
            }
        }
        return result;
    }

    // ── Utilities ─────────────────────────────────────────────

    /** Strip legacy &-codes and MiniMessage tags from a string for plain-text display. */
    private String stripColors(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-orA-FK-Or]", "")
                   .replaceAll("<[^>]+>", "");
    }

    /**
     * Parse a cost string such as "5000000", "5m", "50k", or "2b".
     * Returns -1 for free / unparseable values.
     */
    public static double parseCost(String cost) {
        if (cost == null || cost.isEmpty()) return -1;
        cost = cost.trim().toLowerCase();
        if (cost.equals("-1") || cost.equals("free")) return -1;
        try {
            if (cost.endsWith("b")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000_000_000;
            if (cost.endsWith("m")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000_000;
            if (cost.endsWith("k")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000;
            return Double.parseDouble(cost);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Accessors ─────────────────────────────────────────────

    /** The items currently on offer in the hourly shop. */
    public List<HourlyItem> getCurrentItems() {
        return Collections.unmodifiableList(currentItems);
    }

    /** The full weighted pool loaded from hourly-items.yml. */
    public List<HourlyItem> getItemPool() {
        return Collections.unmodifiableList(itemPool);
    }
}
