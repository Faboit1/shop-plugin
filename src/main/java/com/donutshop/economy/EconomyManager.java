package com.donutshop.economy;

import com.donutshop.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class EconomyManager {

    private final Plugin plugin;
    private Economy vaultEconomy;
    private boolean useCoinsEngine;
    private Object coinsEnginePlugin;
    private Object coinsEngineCurrency;
    private String currencyName;
    private Method getBalanceMethod;
    private Method addBalanceMethod;
    private Method removeBalanceMethod;
    private String provider = "none";

    public EconomyManager(Plugin plugin, String preferredProvider, String ceCurrencyName) {
        this.plugin = plugin;
        this.currencyName = ceCurrencyName;
        setup(preferredProvider);
    }

    private void setup(String preferred) {
        Logger log = plugin.getLogger();

        if (preferred.equalsIgnoreCase("vault")) {
            if (setupVault()) {
                provider = "vault";
                log.info("Using Vault as economy provider.");
                return;
            }
            log.warning("Vault not found, trying CoinsEngine...");
            if (setupCoinsEngine()) {
                provider = "coinsengine";
                log.info("Using CoinsEngine as economy provider.");
                return;
            }
        } else if (preferred.equalsIgnoreCase("coinsengine")) {
            if (setupCoinsEngine()) {
                provider = "coinsengine";
                log.info("Using CoinsEngine as economy provider.");
                return;
            }
            log.warning("CoinsEngine not found, trying Vault...");
            if (setupVault()) {
                provider = "vault";
                log.info("Using Vault as economy provider.");
                return;
            }
        } else {
            if (setupVault()) { provider = "vault"; log.info("Using Vault."); return; }
            if (setupCoinsEngine()) { provider = "coinsengine"; log.info("Using CoinsEngine."); return; }
        }

        log.severe("No economy provider found! Install Vault or CoinsEngine.");
    }

    private boolean setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        vaultEconomy = rsp.getProvider();
        return true;
    }

    private boolean setupCoinsEngine() {
        try {
            Plugin cePlugin = Bukkit.getPluginManager().getPlugin("CoinsEngine");
            if (cePlugin == null) return false;

            coinsEnginePlugin = cePlugin;

            Method getCurrencyManagerMethod = cePlugin.getClass().getMethod("getCurrencyManager");
            Object currencyManager = getCurrencyManagerMethod.invoke(cePlugin);

            Method getCurrencyMethod = currencyManager.getClass().getMethod("getCurrency", String.class);
            coinsEngineCurrency = getCurrencyMethod.invoke(currencyManager, currencyName);

            if (coinsEngineCurrency == null) {
                plugin.getLogger().warning("CoinsEngine currency '" + currencyName + "' not found!");
                return false;
            }

            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Class<?> currencyClass = coinsEngineCurrency.getClass();

            Class<?> currencyInterface = findCurrencyInterface(currencyClass);

            getBalanceMethod = apiClass.getMethod("getBalance", Player.class, currencyInterface);
            try {
                addBalanceMethod = apiClass.getMethod("addBalance", Player.class, currencyInterface, double.class);
                removeBalanceMethod = apiClass.getMethod("removeBalance", Player.class, currencyInterface, double.class);
            } catch (NoSuchMethodException e) {
                addBalanceMethod = apiClass.getMethod("give", Player.class, currencyInterface, double.class);
                removeBalanceMethod = apiClass.getMethod("take", Player.class, currencyInterface, double.class);
            }

            useCoinsEngine = true;
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into CoinsEngine: " + e.getMessage());
            return false;
        }
    }

    private Class<?> findCurrencyInterface(Class<?> clazz) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.getSimpleName().equals("Currency")) return iface;
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return findCurrencyInterface(superclass);
        }
        return clazz;
    }

    public double getBalance(Player player) {
        if (provider.equals("vault") && vaultEconomy != null) {
            return vaultEconomy.getBalance(player);
        }
        if (provider.equals("coinsengine") && useCoinsEngine) {
            try {
                Object result = getBalanceMethod.invoke(null, player, coinsEngineCurrency);
                return ((Number) result).doubleValue();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get CoinsEngine balance: " + e.getMessage());
            }
        }
        return 0;
    }

    public boolean withdraw(Player player, double amount) {
        if (provider.equals("vault") && vaultEconomy != null) {
            return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        }
        if (provider.equals("coinsengine") && useCoinsEngine) {
            try {
                removeBalanceMethod.invoke(null, player, coinsEngineCurrency, amount);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to withdraw from CoinsEngine: " + e.getMessage());
            }
        }
        return false;
    }

    public boolean deposit(Player player, double amount) {
        if (provider.equals("vault") && vaultEconomy != null) {
            return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
        }
        if (provider.equals("coinsengine") && useCoinsEngine) {
            try {
                addBalanceMethod.invoke(null, player, coinsEngineCurrency, amount);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit to CoinsEngine: " + e.getMessage());
            }
        }
        return false;
    }

    public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    public String getProviderName() {
        return provider;
    }

    public boolean isReady() {
        return !provider.equals("none");
    }

    public String formatBalance(double amount) {
        if (provider.equals("vault") && vaultEconomy != null) {
            return vaultEconomy.format(amount);
        }
        return "$" + NumberFormatter.format(amount);
    }
}
