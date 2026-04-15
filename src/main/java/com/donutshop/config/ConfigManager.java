package com.donutshop.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;

    private String economyProvider;
    private String coinsEngineCurrency;
    private String currencySymbol;
    private boolean useSmallCaps;

    private final Map<String, String> messages = new LinkedHashMap<>();

    private String mainMenuTitle;
    private int mainMenuSize;
    private boolean mainMenuFillerEnabled;
    private String mainMenuFillerMaterial;
    private String mainMenuFillerName;

    private final Map<String, CategoryConfig> categories = new LinkedHashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        loadSettings(config);
        loadMessages(config);
        loadMainMenu(config);
        loadCategories(config);
    }

    // ── Settings ──────────────────────────────────────────────

    private void loadSettings(FileConfiguration config) {
        economyProvider = config.getString("settings.economy-provider", "auto");
        coinsEngineCurrency = config.getString("settings.coinsengine-currency", "coins");
        currencySymbol = config.getString("settings.currency-symbol", "$");
        useSmallCaps = config.getBoolean("settings.use-small-caps", true);
    }

    // ── Messages ──────────────────────────────────────────────

    private void loadMessages(FileConfiguration config) {
        messages.clear();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            messages.put(key, section.getString(key, ""));
        }
    }

    // ── Main Menu ─────────────────────────────────────────────

    private void loadMainMenu(FileConfiguration config) {
        mainMenuTitle = config.getString("main-menu.title", "Shop");
        mainMenuSize = config.getInt("main-menu.size", 54);
        mainMenuFillerEnabled = config.getBoolean("main-menu.filler.enabled", true);
        mainMenuFillerMaterial = config.getString("main-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        mainMenuFillerName = config.getString("main-menu.filler.name", " ");
    }

    // ── Categories ────────────────────────────────────────────

    private void loadCategories(FileConfiguration config) {
        categories.clear();

        ConfigurationSection menuCats = config.getConfigurationSection("main-menu.categories");
        if (menuCats == null) return;

        for (String id : menuCats.getKeys(false)) {
            ConfigurationSection iconSection = menuCats.getConfigurationSection(id);
            if (iconSection == null) continue;

            CategoryConfig cat = new CategoryConfig();
            cat.id = id;

            // Main-menu icon properties
            cat.slot = iconSection.getInt("slot", 0);
            cat.iconMaterial = iconSection.getString("material", "STONE");
            cat.iconName = iconSection.getString("name", id);
            cat.iconLore = iconSection.getStringList("lore");
            cat.iconGlow = iconSection.getBoolean("glow", false);
            cat.iconCustomModelData = iconSection.getInt("custom-model-data", -1);

            // Category GUI properties (from categories.<id> section)
            ConfigurationSection catSection = config.getConfigurationSection("categories." + id);
            if (catSection != null) {
                cat.guiTitle = catSection.getString("title", id);
                cat.guiSize = catSection.getInt("size", 54);
                cat.fillerEnabled = catSection.getBoolean("filler.enabled", true);
                cat.fillerMaterial = catSection.getString("filler.material", "BLACK_STAINED_GLASS_PANE");
                cat.fillerName = catSection.getString("filler.name", " ");

                cat.items = loadItems(catSection.getConfigurationSection("items"));
            } else {
                cat.guiTitle = id;
                cat.guiSize = 54;
                cat.fillerEnabled = true;
                cat.fillerMaterial = "BLACK_STAINED_GLASS_PANE";
                cat.fillerName = " ";
                cat.items = Collections.emptyList();
            }

            categories.put(id, cat);
        }
    }

    private List<ShopItem> loadItems(ConfigurationSection section) {
        if (section == null) return Collections.emptyList();

        List<ShopItem> items = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(key);
            if (itemSec == null) continue;

            ShopItem item = new ShopItem();
            item.material = itemSec.getString("material", "STONE");
            item.name = itemSec.getString("name", null);
            item.lore = itemSec.contains("lore") ? itemSec.getStringList("lore") : null;
            item.buyPrice = itemSec.getDouble("buy-price", -1);
            item.sellPrice = itemSec.getDouble("sell-price", -1);
            item.slot = itemSec.getInt("slot", -1);
            item.amount = itemSec.getInt("amount", 1);
            item.customModelData = itemSec.getInt("custom-model-data", -1);

            items.add(item);
        }
        return items;
    }

    // ── Accessors ─────────────────────────────────────────────

    public String getEconomyProvider() {
        return economyProvider;
    }

    public String getCoinsEngineCurrency() {
        return coinsEngineCurrency;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public boolean useSmallCaps() {
        return useSmallCaps;
    }

    public String getMessage(String key) {
        String prefix = messages.getOrDefault("prefix", "");
        String msg = messages.getOrDefault(key, "");
        return msg.replace("{prefix}", prefix);
    }

    public String getMainMenuTitle() {
        return mainMenuTitle;
    }

    public int getMainMenuSize() {
        return mainMenuSize;
    }

    public boolean isMainMenuFillerEnabled() {
        return mainMenuFillerEnabled;
    }

    public String getMainMenuFillerMaterial() {
        return mainMenuFillerMaterial;
    }

    public String getMainMenuFillerName() {
        return mainMenuFillerName;
    }

    public Map<String, CategoryConfig> getCategories() {
        return Collections.unmodifiableMap(categories);
    }

    public CategoryConfig getCategory(String id) {
        return categories.get(id);
    }

    // ── Inner data classes ────────────────────────────────────

    public static class CategoryConfig {
        private String id;
        private int slot;
        private String iconMaterial;
        private String iconName;
        private List<String> iconLore;
        private boolean iconGlow;
        private int iconCustomModelData = -1;
        private String guiTitle;
        private int guiSize;
        private String fillerMaterial;
        private String fillerName;
        private boolean fillerEnabled;
        private List<ShopItem> items;

        public String getId() { return id; }
        public int getSlot() { return slot; }
        public String getIconMaterial() { return iconMaterial; }
        public String getIconName() { return iconName; }
        public List<String> getIconLore() { return iconLore; }
        public boolean isIconGlow() { return iconGlow; }
        public int getIconCustomModelData() { return iconCustomModelData; }
        public String getGuiTitle() { return guiTitle; }
        public int getGuiSize() { return guiSize; }
        public String getFillerMaterial() { return fillerMaterial; }
        public String getFillerName() { return fillerName; }
        public boolean isFillerEnabled() { return fillerEnabled; }
        public List<ShopItem> getItems() { return items; }
    }

    public static class ShopItem {
        private String material;
        private String name;
        private List<String> lore;
        private double buyPrice = -1;
        private double sellPrice = -1;
        private int slot = -1;
        private int amount = 1;
        private int customModelData = -1;

        public String getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public double getBuyPrice() { return buyPrice; }
        public double getSellPrice() { return sellPrice; }
        public int getSlot() { return slot; }
        public int getAmount() { return amount; }
        public int getCustomModelData() { return customModelData; }
    }
}
