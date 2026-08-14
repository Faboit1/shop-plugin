package com.donutshop;

import com.donutshop.commands.ShopCommand;
import com.donutshop.config.ConfigManager;
import com.donutshop.economy.EconomyManager;
import com.donutshop.gui.CategoryGUI;
import com.donutshop.gui.ConfirmationGUI;
import com.donutshop.gui.HourlyConfirmationGUI;
import com.donutshop.gui.HourlyShopGUI;
import com.donutshop.gui.ShopGUI;
import com.donutshop.hourly.HourlyItemManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DonutShop extends JavaPlugin {

    private static DonutShop instance;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private final Map<String, EconomyManager> categoryEconomies = new ConcurrentHashMap<>();
    private ShopGUI shopGUI;
    private HourlyItemManager hourlyItemManager;
    private HourlyShopGUI hourlyShopGUI;
    private HourlyConfirmationGUI hourlyConfirmationGUI;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Auto-generate presets folder and preset.yml
        File presetsFolder = new File(getDataFolder(), "presets");
        if (!presetsFolder.exists()) {
            presetsFolder.mkdirs();
        }
        if (!new File(presetsFolder, "preset.yml").exists()) {
            saveResource("presets/preset.yml", false);
        }

        // Initialize config manager
        configManager = new ConfigManager(this);

        // Initialize economy (delayed by 1 tick to ensure other plugins are loaded)
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            economyManager = new EconomyManager(
                this,
                configManager.getEconomyProvider(),
                configManager.getCoinsEngineCurrency(),
                configManager.getExcellentEconomyCurrency()
            );

            if (!economyManager.isReady()) {
                getLogger().severe("No economy provider found! The shop will not work.");
                getLogger().severe("Please install Vault (with an economy plugin), CoinsEngine, or ExcellentEconomy.");
            }

            initCategoryEconomies();
        }, 1);

        // Initialize GUI
        shopGUI = new ShopGUI(this, configManager);

        // Initialize hourly shop
        hourlyItemManager = new HourlyItemManager(this);
        hourlyShopGUI = new HourlyShopGUI(this, configManager);
        hourlyConfirmationGUI = new HourlyConfirmationGUI(this, configManager);
        hourlyItemManager.start();

        // Register events
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager().registerEvents(new CategoryGUI(this, configManager), this);
        getServer().getPluginManager().registerEvents(new ConfirmationGUI(this, configManager), this);
        getServer().getPluginManager().registerEvents(hourlyShopGUI, this);
        getServer().getPluginManager().registerEvents(hourlyConfirmationGUI, this);

        // Register commands
        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        getLogger().info("DonutShop has been enabled!");
        getLogger().info("Economy provider: " + configManager.getEconomyProvider());
    }

    @Override
    public void onDisable() {
        if (hourlyItemManager != null) {
            hourlyItemManager.shutdown();
        }
        getLogger().info("DonutShop has been disabled!");
    }

    private void initCategoryEconomies() {
        categoryEconomies.clear();
        for (Map.Entry<String, ConfigManager.CategoryConfig> entry : configManager.getCategories().entrySet()) {
            ConfigManager.CategoryConfig cat = entry.getValue();
            if (cat.hasCurrencyOverride()) {
                String provider = cat.getCurrencyProvider();
                String currencyId = cat.getCurrencyId();
                EconomyManager catEcon = new EconomyManager(this, provider, currencyId, currencyId);
                if (catEcon.isReady()) {
                    categoryEconomies.put(entry.getKey(), catEcon);
                    getLogger().info("Category '" + entry.getKey() + "' using " + provider + " with currency '" + currencyId + "'");
                } else {
                    getLogger().warning("Category '" + entry.getKey() + "' economy provider '" + provider + "' not available, falling back to default.");
                }
            }
        }
    }

    public EconomyManager getEconomyForCategory(ConfigManager.CategoryConfig category) {
        if (category != null && category.hasCurrencyOverride()) {
            EconomyManager catEcon = categoryEconomies.get(category.getId());
            if (catEcon != null && catEcon.isReady()) {
                return catEcon;
            }
        }
        return economyManager;
    }

    public static DonutShop getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }

    public HourlyItemManager getHourlyItemManager() {
        return hourlyItemManager;
    }

    public HourlyShopGUI getHourlyShopGUI() {
        return hourlyShopGUI;
    }

    public HourlyConfirmationGUI getHourlyConfirmationGUI() {
        return hourlyConfirmationGUI;
    }

    public void reload() {
        configManager.reload();
        economyManager = new EconomyManager(
            this,
            configManager.getEconomyProvider(),
            configManager.getCoinsEngineCurrency(),
            configManager.getExcellentEconomyCurrency()
        );
        initCategoryEconomies();
        if (hourlyItemManager != null) {
            hourlyItemManager.reload();
        }
    }
}
