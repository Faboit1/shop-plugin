package com.donutshop;

import com.donutshop.commands.ShopCommand;
import com.donutshop.config.ConfigManager;
import com.donutshop.economy.EconomyManager;
import com.donutshop.gui.CategoryGUI;
import com.donutshop.gui.ConfirmationGUI;
import com.donutshop.gui.HourlyShopGUI;
import com.donutshop.gui.ShopGUI;
import com.donutshop.hourly.HourlyItemManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class DonutShop extends JavaPlugin {

    private static DonutShop instance;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private ShopGUI shopGUI;
    private HourlyItemManager hourlyItemManager;
    private HourlyShopGUI hourlyShopGUI;

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
        getServer().getScheduler().runTaskLater(this, () -> {
            economyManager = new EconomyManager(
                this,
                configManager.getEconomyProvider(),
                configManager.getCoinsEngineCurrency()
            );
            
            if (!economyManager.isReady()) {
                getLogger().severe("No economy provider found! The shop will not work.");
                getLogger().severe("Please install Vault (with an economy plugin) or CoinsEngine.");
            }
        }, 1L);
        
        // Initialize GUI
        shopGUI = new ShopGUI(this, configManager);
        
        // Initialize hourly shop
        hourlyItemManager = new HourlyItemManager(this);
        hourlyShopGUI = new HourlyShopGUI(this, configManager);
        hourlyItemManager.start();
        
        // Register events
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager().registerEvents(new CategoryGUI(this, configManager), this);
        getServer().getPluginManager().registerEvents(new ConfirmationGUI(this, configManager), this);
        getServer().getPluginManager().registerEvents(hourlyShopGUI, this);
        
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
    
    public void reload() {
        configManager.reload();
        // Re-create economy manager with potentially new settings
        economyManager = new EconomyManager(
            this,
            configManager.getEconomyProvider(),
            configManager.getCoinsEngineCurrency()
        );
        // Reload hourly shop (re-reads hourly-items.yml and reschedules)
        if (hourlyItemManager != null) {
            hourlyItemManager.reload();
        }
        // ShopGUI, CategoryGUI, ConfirmationGUI, and HourlyShopGUI hold references to
        // configManager and read it dynamically, so they do not need recreation on reload.
    }
}
