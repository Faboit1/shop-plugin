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
    private Object coinsEngineCurrency;
    private String currencyName;
    private Method getBalanceMethod;
    private Method addBalanceMethod;
    private Method removeBalanceMethod;

    private boolean useExcellentEconomy;
    private Object eeCurrency;
    private Method eeGetBalanceMethod;
    private Method eeAddBalanceMethod;
    private Method eeRemoveBalanceMethod;
    private String eeCurrencyName;

    private String provider = "none";

    public EconomyManager(Plugin plugin, String preferredProvider, String ceCurrencyName) {
        this(plugin, preferredProvider, ceCurrencyName, "money");
    }

    public EconomyManager(Plugin plugin, String preferredProvider, String ceCurrencyName, String eeCurrencyName) {
        this.plugin = plugin;
        this.currencyName = ceCurrencyName;
        this.eeCurrencyName = eeCurrencyName;
        setup(preferredProvider);
    }

    private void setup(String preferred) {
        Logger log = plugin.getLogger();

        if (preferred.equalsIgnoreCase("vault")) {
            if (setupVault()) { provider = "vault"; log.info("Using Vault as economy provider."); return; }
            log.warning("Vault not found, trying CoinsEngine...");
            if (setupCoinsEngine()) { provider = "coinsengine"; log.info("Using CoinsEngine as economy provider."); return; }
            log.warning("CoinsEngine not found, trying ExcellentEconomy...");
            if (setupExcellentEconomy()) { provider = "excellenteconomy"; log.info("Using ExcellentEconomy as economy provider."); return; }
        } else if (preferred.equalsIgnoreCase("coinsengine")) {
            if (setupCoinsEngine()) { provider = "coinsengine"; log.info("Using CoinsEngine as economy provider."); return; }
            log.warning("CoinsEngine not found, trying Vault...");
            if (setupVault()) { provider = "vault"; log.info("Using Vault as economy provider."); return; }
        } else if (preferred.equalsIgnoreCase("excellenteconomy")) {
            if (setupExcellentEconomy()) { provider = "excellenteconomy"; log.info("Using ExcellentEconomy as economy provider."); return; }
            log.warning("ExcellentEconomy not found, trying Vault...");
            if (setupVault()) { provider = "vault"; log.info("Using Vault as economy provider."); return; }
            log.warning("Vault not found, trying CoinsEngine...");
            if (setupCoinsEngine()) { provider = "coinsengine"; log.info("Using CoinsEngine as economy provider."); return; }
        } else {
            if (setupVault()) { provider = "vault"; log.info("Using Vault."); return; }
            if (setupCoinsEngine()) { provider = "coinsengine"; log.info("Using CoinsEngine."); return; }
            if (setupExcellentEconomy()) { provider = "excellenteconomy"; log.info("Using ExcellentEconomy."); return; }
        }

        log.severe("No economy provider found! Install Vault, CoinsEngine, or ExcellentEconomy.");
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

            Method getCurrencyManagerMethod = cePlugin.getClass().getMethod("getCurrencyManager");
            Object currencyManager = getCurrencyManagerMethod.invoke(cePlugin);

            Method getCurrencyMethod = currencyManager.getClass().getMethod("getCurrency", String.class);
            coinsEngineCurrency = getCurrencyMethod.invoke(currencyManager, currencyName);

            if (coinsEngineCurrency == null) {
                plugin.getLogger().warning("CoinsEngine currency '" + currencyName + "' not found!");
                return false;
            }

            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Class<?> currencyInterface = findInterface(coinsEngineCurrency.getClass(), "Currency");

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

    private boolean setupExcellentEconomy() {
        try {
            Plugin eePlugin = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
            if (eePlugin == null) return false;

            Method getCurrencyManagerMethod = eePlugin.getClass().getMethod("getCurrencyManager");
            Object currencyManager = getCurrencyManagerMethod.invoke(eePlugin);

            Method getCurrencyMethod = currencyManager.getClass().getMethod("getCurrency", String.class);
            eeCurrency = getCurrencyMethod.invoke(currencyManager, eeCurrencyName);

            if (eeCurrency == null) {
                plugin.getLogger().warning("ExcellentEconomy currency '" + eeCurrencyName + "' not found!");
                return false;
            }

            Class<?> currencyInterface = findInterface(eeCurrency.getClass(), "Currency");

            eeGetBalanceMethod = findMethodByName(currencyInterface, "getBalance", Player.class);
            if (eeGetBalanceMethod == null) {
                eeGetBalanceMethod = findMethodByName(eeCurrency.getClass(), "getBalance", Player.class);
            }

            try {
                eeAddBalanceMethod = findMethodByName(currencyInterface, "give", Player.class, double.class);
                eeRemoveBalanceMethod = findMethodByName(currencyInterface, "take", Player.class, double.class);
            } catch (Exception ignored) {}

            if (eeAddBalanceMethod == null) {
                eeAddBalanceMethod = findMethodByName(eeCurrency.getClass(), "give", Player.class, double.class);
            }
            if (eeRemoveBalanceMethod == null) {
                eeRemoveBalanceMethod = findMethodByName(eeCurrency.getClass(), "take", Player.class, double.class);
            }

            if (eeGetBalanceMethod == null || eeAddBalanceMethod == null || eeRemoveBalanceMethod == null) {
                // Try static API class pattern (same as CoinsEngine - NightExpress shared API style)
                try {
                    Class<?> apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
                    eeGetBalanceMethod = apiClass.getMethod("getBalance", Player.class, currencyInterface);
                    try {
                        eeAddBalanceMethod = apiClass.getMethod("addBalance", Player.class, currencyInterface, double.class);
                        eeRemoveBalanceMethod = apiClass.getMethod("removeBalance", Player.class, currencyInterface, double.class);
                    } catch (NoSuchMethodException e2) {
                        eeAddBalanceMethod = apiClass.getMethod("give", Player.class, currencyInterface, double.class);
                        eeRemoveBalanceMethod = apiClass.getMethod("take", Player.class, currencyInterface, double.class);
                    }
                } catch (Exception ignored) {}
            }

            if (eeGetBalanceMethod == null || eeAddBalanceMethod == null || eeRemoveBalanceMethod == null) {
                plugin.getLogger().warning("ExcellentEconomy API methods not found for currency '" + eeCurrencyName + "'!");
                return false;
            }

            useExcellentEconomy = true;
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into ExcellentEconomy: " + e.getMessage());
            return false;
        }
    }

    private Class<?> findInterface(Class<?> clazz, String simpleName) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.getSimpleName().equals(simpleName)) return iface;
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return findInterface(superclass, simpleName);
        }
        return clazz;
    }

    private Method findMethodByName(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
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
        if (provider.equals("excellenteconomy") && useExcellentEconomy) {
            try {
                Object result;
                if (eeGetBalanceMethod.getParameterCount() == 2) {
                    result = eeGetBalanceMethod.invoke(null, player, eeCurrency);
                } else {
                    result = eeGetBalanceMethod.invoke(eeCurrency, player);
                }
                return ((Number) result).doubleValue();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get ExcellentEconomy balance: " + e.getMessage());
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
        if (provider.equals("excellenteconomy") && useExcellentEconomy) {
            try {
                if (eeRemoveBalanceMethod.getParameterCount() == 3
                        && eeRemoveBalanceMethod.getDeclaringClass().getSimpleName().contains("API")) {
                    eeRemoveBalanceMethod.invoke(null, player, eeCurrency, amount);
                } else {
                    eeRemoveBalanceMethod.invoke(eeCurrency, player, amount);
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to withdraw from ExcellentEconomy: " + e.getMessage());
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
        if (provider.equals("excellenteconomy") && useExcellentEconomy) {
            try {
                if (eeAddBalanceMethod.getParameterCount() == 3
                        && eeAddBalanceMethod.getDeclaringClass().getSimpleName().contains("API")) {
                    eeAddBalanceMethod.invoke(null, player, eeCurrency, amount);
                } else {
                    eeAddBalanceMethod.invoke(eeCurrency, player, amount);
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit to ExcellentEconomy: " + e.getMessage());
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
