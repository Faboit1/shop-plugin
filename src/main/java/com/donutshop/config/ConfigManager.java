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

    // Category GUI navigation defaults
    private NavigationConfig navBack;
    private NavigationConfig navPrev;
    private NavigationConfig navNext;
    private NavigationConfig navPageInfo;
    private NavigationConfig navClose;
    private List<String> itemLoreFormat;

    // Sound settings
    private String soundBuy;
    private String soundSell;
    private String soundError;
    private String soundOpenMenu;
    private String soundNavigate;

    // Transaction settings
    private int shiftClickAmount;
    private boolean middleClickSellAll;

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
        loadCategoryGUIDefaults(config);
        loadCategories(config);
    }

    // ── Settings ──────────────────────────────────────────────

    private void loadSettings(FileConfiguration config) {
        economyProvider = config.getString("settings.economy-provider", "auto");
        coinsEngineCurrency = config.getString("settings.coinsengine-currency", "coins");
        currencySymbol = config.getString("settings.currency-symbol", "$");
        useSmallCaps = config.getBoolean("settings.use-small-caps", true);

        // Sounds
        soundBuy = config.getString("settings.sounds.buy", "ENTITY_EXPERIENCE_ORB_PICKUP");
        soundSell = config.getString("settings.sounds.sell", "ENTITY_EXPERIENCE_ORB_PICKUP");
        soundError = config.getString("settings.sounds.error", "ENTITY_VILLAGER_NO");
        soundOpenMenu = config.getString("settings.sounds.open-menu", "UI_BUTTON_CLICK");
        soundNavigate = config.getString("settings.sounds.navigate", "UI_BUTTON_CLICK");

        // Transactions
        shiftClickAmount = config.getInt("settings.transactions.shift-click-amount", 64);
        middleClickSellAll = config.getBoolean("settings.transactions.middle-click-sell-all", true);
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
        mainMenuSize = config.getInt("main-menu.size", 27);
        mainMenuFillerEnabled = config.getBoolean("main-menu.filler.enabled", true);
        mainMenuFillerMaterial = config.getString("main-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        mainMenuFillerName = config.getString("main-menu.filler.name", " ");
    }

    // ── Category GUI Defaults ──────────────────────────────────

    private void loadCategoryGUIDefaults(FileConfiguration config) {
        navBack = loadNavConfig(config, "category-gui.navigation.back", 18, "ARROW", "<gray>Back");
        navPrev = loadNavConfig(config, "category-gui.navigation.previous-page", 21, "ARROW", "<gray>Previous Page");
        navNext = loadNavConfig(config, "category-gui.navigation.next-page", 23, "ARROW", "<gray>Next Page");
        navPageInfo = loadNavConfig(config, "category-gui.navigation.page-info", 22, "PAPER", "<gray>Page {page}/{total}");
        navClose = loadNavConfig(config, "category-gui.navigation.close", 26, "BARRIER", "<red>Close");

        itemLoreFormat = config.getStringList("category-gui.item-lore");
        if (itemLoreFormat.isEmpty()) {
            itemLoreFormat = new ArrayList<>();
            itemLoreFormat.add("");
            itemLoreFormat.add("<gray>Cost: <green>{cost}");
        }
    }

    private NavigationConfig loadNavConfig(FileConfiguration config, String path, int defaultSlot,
                                           String defaultMaterial, String defaultName) {
        NavigationConfig nav = new NavigationConfig();
        nav.enabled = config.getBoolean(path + ".enabled", true);
        nav.slot = config.getInt(path + ".slot", defaultSlot);
        nav.material = config.getString(path + ".material", defaultMaterial);
        nav.name = config.getString(path + ".name", defaultName);
        return nav;
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

            // Main-menu icon properties (using icon- prefixed keys)
            cat.slot = iconSection.getInt("slot", 0);
            cat.iconMaterial = iconSection.getString("icon-material", "STONE");
            cat.iconName = iconSection.getString("icon-name", id);
            cat.iconLore = iconSection.getStringList("icon-lore");
            cat.iconGlow = iconSection.getBoolean("icon-glow", false);
            cat.iconCustomModelData = iconSection.getInt("icon-custom-model-data", -1);

            // Category GUI properties (from categories.<id> section)
            ConfigurationSection catSection = config.getConfigurationSection("categories." + id);
            if (catSection != null) {
                cat.guiTitle = catSection.getString("title", id);
                cat.guiSize = catSection.getInt("size", 27);
                cat.fillerEnabled = catSection.getBoolean("filler.enabled", true);
                cat.fillerMaterial = catSection.getString("filler.material", "BLACK_STAINED_GLASS_PANE");
                cat.fillerName = catSection.getString("filler.name", " ");

                cat.items = loadItems(catSection);
            } else {
                cat.guiTitle = id;
                cat.guiSize = 27;
                cat.fillerEnabled = true;
                cat.fillerMaterial = "BLACK_STAINED_GLASS_PANE";
                cat.fillerName = " ";
                cat.items = Collections.emptyList();
            }

            categories.put(id, cat);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ShopItem> loadItems(ConfigurationSection catSection) {
        List<?> rawList = catSection.getList("items");
        if (rawList == null) return Collections.emptyList();

        List<ShopItem> items = new ArrayList<>();
        for (Object obj : rawList) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) obj;

            ShopItem item = new ShopItem();
            item.material = String.valueOf(map.getOrDefault("material", "STONE"));
            item.name = map.containsKey("name") ? String.valueOf(map.get("name")) : null;
            if (map.containsKey("lore") && map.get("lore") instanceof List) {
                item.lore = ((List<?>) map.get("lore")).stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.toList());
            }
            item.buyPrice = map.containsKey("buy-price") ? ((Number) map.get("buy-price")).doubleValue() : -1;
            item.sellPrice = map.containsKey("sell-price") ? ((Number) map.get("sell-price")).doubleValue() : -1;
            item.slot = map.containsKey("slot") ? ((Number) map.get("slot")).intValue() : -1;
            item.amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
            item.customModelData = map.containsKey("custom-model-data") ? ((Number) map.get("custom-model-data")).intValue() : -1;

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

    // ── Category GUI navigation accessors ─────────────────────

    public NavigationConfig getNavBack() { return navBack; }
    public NavigationConfig getNavPrev() { return navPrev; }
    public NavigationConfig getNavNext() { return navNext; }
    public NavigationConfig getNavPageInfo() { return navPageInfo; }
    public NavigationConfig getNavClose() { return navClose; }
    public List<String> getItemLoreFormat() { return itemLoreFormat; }

    // ── Sound accessors ───────────────────────────────────────

    public String getSoundBuy() { return soundBuy; }
    public String getSoundSell() { return soundSell; }
    public String getSoundError() { return soundError; }
    public String getSoundOpenMenu() { return soundOpenMenu; }
    public String getSoundNavigate() { return soundNavigate; }

    // ── Transaction accessors ─────────────────────────────────

    public int getShiftClickAmount() { return shiftClickAmount; }
    public boolean isMiddleClickSellAll() { return middleClickSellAll; }

    // ── Inner data classes ────────────────────────────────────

    public static class NavigationConfig {
        private boolean enabled = true;
        private int slot;
        private String material;
        private String name;

        public boolean isEnabled() { return enabled; }
        public int getSlot() { return slot; }
        public String getMaterial() { return material; }
        public String getName() { return name; }
    }

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
