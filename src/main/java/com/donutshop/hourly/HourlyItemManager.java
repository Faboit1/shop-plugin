package com.donutshop.hourly;

import com.donutshop.DonutShop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the hourly rotating shop: loads the item pool from hourly-items.yml,
 * picks a weighted-random selection each hour (exactly at the top of the hour),
 * resolving any configured cost ranges for the current rotation.
 */
public class HourlyItemManager {

    private final DonutShop plugin;

    private List<HourlyItem> itemPool = new ArrayList<>();
    private List<HourlyItem> currentItems = new ArrayList<>();
    private BukkitTask scheduledTask;

    /** Tracks how many times each player has purchased each item in the current hour. */
    private final Map<UUID, Map<String, Integer>> playerPurchaseCounts = new ConcurrentHashMap<>();

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    public HourlyItemManager(DonutShop plugin) {
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
            CostRange costRange = parseCostRange(sec.getString("cost", "-1"));

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

            itemPool.add(new HourlyItem(id, type, material, name, lore, weight, costRange.min(), costRange.min(),
                    costRange.max(), commands,
                    sec.getInt("purchaselimit", -1)));
        }

        plugin.getLogger().info("[HourlyShop] Loaded " + itemPool.size() + " items into the hourly pool.");
    }

    // ── Item selection ────────────────────────────────────────

    /**
     * Pick a new set of items from the pool, resolve their hourly costs,
     * and reset per-player purchase counts for the new hour.
     */
    public void refresh() {
        if (itemPool.isEmpty()) {
            currentItems = Collections.emptyList();
            return;
        }

        int count = Math.min(plugin.getConfigManager().getHourlyShopItemCount(), itemPool.size());
        currentItems = selectWeightedRandom(itemPool, count).stream()
                .map(this::resolveCurrentCost)
                .toList();

        // Reset purchase counts for the new rotation
        playerPurchaseCounts.clear();

        plugin.getLogger().info("[HourlyShop] Items refreshed: " +
                currentItems.stream().map(HourlyItem::getId).reduce((a, b) -> a + ", " + b).orElse("(none)"));

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

    private HourlyItem resolveCurrentCost(HourlyItem item) {
        if (!item.hasVariableCost()) {
            return item;
        }

        double min = item.getMinCost();
        double max = item.getMaxCost();
        if (isWholeNumber(min) && isWholeNumber(max)) {
            long rolled = ThreadLocalRandom.current().nextLong((long) min, (long) max + 1);
            return item.withCost(rolled);
        }

        return item.withCost(ThreadLocalRandom.current().nextDouble(min, max));
    }

    /**
     * Parse a cost string such as "5000000", "5m", "50k", "2b", "1t",
     * or a range such as "10k-100k".
     * Returns a free-item range for empty or unparseable values.
     */
    private CostRange parseCostRange(String cost) {
        if (cost == null || cost.isEmpty()) return new CostRange(-1, -1);
        cost = cost.trim().toLowerCase();
        if (cost.equals("-1") || cost.equals("free")) return new CostRange(-1, -1);

        String[] parts = cost.split("\\s*-\\s*", 2);
        if (parts.length == 2) {
            double min = parseSingleCost(parts[0]);
            double max = parseSingleCost(parts[1]);
            if (min >= 0 && max >= 0) {
                return new CostRange(Math.min(min, max), Math.max(min, max));
            }
            plugin.getLogger().warning("[HourlyShop] Invalid cost range '" + cost + "' - treating as free.");
            return new CostRange(-1, -1);
        }

        double fixedCost = parseSingleCost(cost);
        return fixedCost >= 0 ? new CostRange(fixedCost, fixedCost) : new CostRange(-1, -1);
    }

    private static double parseSingleCost(String cost) {
        try {
            if (cost.endsWith("t")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000_000_000_000L;
            if (cost.endsWith("b")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000_000_000;
            if (cost.endsWith("m")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000_000;
            if (cost.endsWith("k")) return Double.parseDouble(cost.substring(0, cost.length() - 1)) * 1_000;
            return Double.parseDouble(cost);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isWholeNumber(double value) {
        return Math.floor(value) == value;
    }

    private record CostRange(double min, double max) {}

    // ── Accessors ─────────────────────────────────────────────

    /** The items currently on offer in the hourly shop. */
    public List<HourlyItem> getCurrentItems() {
        return Collections.unmodifiableList(currentItems);
    }

    /** The full weighted pool loaded from hourly-items.yml. */
    public List<HourlyItem> getItemPool() {
        return Collections.unmodifiableList(itemPool);
    }

    // ── Purchase-limit tracking ───────────────────────────────

    /** Returns how many times the given player has bought this item in the current hour. */
    public int getPurchaseCount(UUID playerUuid, String itemId) {
        Map<String, Integer> counts = playerPurchaseCounts.get(playerUuid);
        return counts != null ? counts.getOrDefault(itemId, 0) : 0;
    }

    /** Records one purchase of the given item for the player. */
    public void incrementPurchaseCount(UUID playerUuid, String itemId) {
        playerPurchaseCounts
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .merge(itemId, 1, Integer::sum);
    }

    /** Records {@code amount} purchases of the given item for the player in a single operation. */
    public void addPurchaseCount(UUID playerUuid, String itemId, int amount) {
        if (amount <= 0) return;
        playerPurchaseCounts
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .merge(itemId, amount, Integer::sum);
    }

    /** Returns true if the player has reached (or exceeded) this item's purchase limit. */
    public boolean isAtPurchaseLimit(UUID playerUuid, HourlyItem item) {
        int limit = item.getPurchaseLimit();
        if (limit < 0) return false;
        return getPurchaseCount(playerUuid, item.getId()) >= limit;
    }
}
