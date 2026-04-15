package com.donutshop;

import com.donutshop.commands.ShopCommand;
import com.donutshop.config.ConfigManager;
import com.donutshop.economy.EconomyManager;
import com.donutshop.gui.CategoryGUI;
import com.donutshop.gui.ShopGUI;
import org.bukkit.plugin.java.JavaPlugin;

public class DonutShop extends JavaPlugin {

    private static DonutShop instance;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private ShopGUI shopGUI;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
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
        
        // Register events
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager().registerEvents(new CategoryGUI(this, configManager), this);
        
        // Register commands
        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);
        
        getLogger().info("DonutShop has been enabled!");
        getLogger().info("Economy provider: " + configManager.getEconomyProvider());
    }

    @Override
    public void onDisable() {
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
    
    public void reload() {
        reloadConfig();
        configManager.reload();
        // Re-create economy manager with potentially new settings
        economyManager = new EconomyManager(
            this,
            configManager.getEconomyProvider(),
            configManager.getCoinsEngineCurrency()
        );
        // Re-create shop GUI with new config
        shopGUI = new ShopGUI(this, configManager);
        // Re-register GUI events (the old ones will still fire but the new shopGUI reference is used)
        getServer().getPluginManager().registerEvents(shopGUI, this);
    }
}
