package com.donutshop.economy;

import com.donutshop.config.ConfigManager;
import com.donutshop.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class EconomyManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ConfigManager.CurrencyConfig defaultCurrency;
    private Economy vaultEconomy;
    private Object coinsEnginePlugin;
    private Method getCurrencyManagerMethod;
    private Method getCoinsEngineCurrencyMethod;
    private Method getBalanceMethod;
    private Method addBalanceMethod;
    private Method removeBalanceMethod;
    private final Map<String, Object> coinsEngineCurrencies = new ConcurrentHashMap<>();
    private Object excellentEconomyApi;
    private Method excellentEconomyGetCurrencyMethod;
    private Method excellentEconomyGetBalanceMethod;
    private Method excellentEconomyDepositMethod;
    private Method excellentEconomyWithdrawMethod;
    private Method excellentEconomyFormatMethod;
    private final Map<String, Object> excellentEconomyCurrencies = new ConcurrentHashMap<>();
    private String provider = "none";

    public EconomyManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.defaultCurrency = configManager.getDefaultCurrencyConfig();
        setup(defaultCurrency.getProvider());
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
            if (setupCoinsEngine(configManager.getCoinsEngineCurrency())) {
                provider = "coinsengine";
                log.info("Using CoinsEngine as economy provider.");
                return;
            }
            log.warning("CoinsEngine not found, trying ExcellentEconomy...");
            if (setupExcellentEconomy(configManager.getExcellentEconomyCurrency())) {
                provider = "excellenteconomy";
                log.info("Using ExcellentEconomy as economy provider.");
                return;
            }
        } else if (preferred.equalsIgnoreCase("coinsengine")) {
            if (setupCoinsEngine(configManager.getCoinsEngineCurrency())) {
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
            log.warning("Vault not found, trying ExcellentEconomy...");
            if (setupExcellentEconomy(configManager.getExcellentEconomyCurrency())) {
                provider = "excellenteconomy";
                log.info("Using ExcellentEconomy as economy provider.");
                return;
            }
        } else if (preferred.equalsIgnoreCase("excellenteconomy")) {
            if (setupExcellentEconomy(configManager.getExcellentEconomyCurrency())) {
                provider = "excellenteconomy";
                log.info("Using ExcellentEconomy as economy provider.");
                return;
            }
            log.warning("ExcellentEconomy not found, trying Vault...");
            if (setupVault()) {
                provider = "vault";
                log.info("Using Vault as economy provider.");
                return;
            }
            log.warning("Vault not found, trying CoinsEngine...");
            if (setupCoinsEngine(configManager.getCoinsEngineCurrency())) {
                provider = "coinsengine";
                log.info("Using CoinsEngine as economy provider.");
                return;
            }
        } else {
            if (setupVault()) { provider = "vault"; log.info("Using Vault."); return; }
            if (setupCoinsEngine(configManager.getCoinsEngineCurrency())) { provider = "coinsengine"; log.info("Using CoinsEngine."); return; }
            if (setupExcellentEconomy(configManager.getExcellentEconomyCurrency())) { provider = "excellenteconomy"; log.info("Using ExcellentEconomy."); return; }
        }

        log.severe("No economy provider found! Install Vault, CoinsEngine, or ExcellentEconomy.");
    }

    private boolean setupVault() {
        if (vaultEconomy != null) {
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        vaultEconomy = rsp.getProvider();
        return true;
    }

    private boolean setupCoinsEngine(String currencyId) {
        try {
            if (coinsEnginePlugin != null && getCoinsEngineCurrency(currencyId) != null) {
                return true;
            }

            Plugin cePlugin = Bukkit.getPluginManager().getPlugin("CoinsEngine");
            if (cePlugin == null) return false;

            coinsEnginePlugin = cePlugin;

            if (getCurrencyManagerMethod == null) {
                getCurrencyManagerMethod = cePlugin.getClass().getMethod("getCurrencyManager");
                Object currencyManager = getCurrencyManagerMethod.invoke(cePlugin);
                getCoinsEngineCurrencyMethod = currencyManager.getClass().getMethod("getCurrency", String.class);
                Object currency = resolveCoinsEngineCurrency(currencyId);
                if (currency == null) {
                    plugin.getLogger().warning("CoinsEngine currency '" + currencyId + "' not found!");
                    return false;
                }
                Class<?> currencyClass = currency.getClass();
                Class<?> currencyInterface = findCurrencyInterface(currencyClass);
                Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
                getBalanceMethod = apiClass.getMethod("getBalance", Player.class, currencyInterface);
                try {
                    addBalanceMethod = apiClass.getMethod("addBalance", Player.class, currencyInterface, double.class);
                    removeBalanceMethod = apiClass.getMethod("removeBalance", Player.class, currencyInterface, double.class);
                } catch (NoSuchMethodException e) {
                    addBalanceMethod = apiClass.getMethod("give", Player.class, currencyInterface, double.class);
                    removeBalanceMethod = apiClass.getMethod("take", Player.class, currencyInterface, double.class);
                }
            }

            if (getCoinsEngineCurrency(currencyId) == null) {
                plugin.getLogger().warning("CoinsEngine currency '" + currencyId + "' not found!");
                return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into CoinsEngine: " + e.getMessage());
            return false;
        }
    }

    private Object getCoinsEngineCurrency(String currencyId) throws Exception {
        Object currency = coinsEngineCurrencies.get(currencyId);
        if (currency != null) {
            return currency;
        }
        currency = resolveCoinsEngineCurrency(currencyId);
        if (currency != null) {
            coinsEngineCurrencies.put(currencyId, currency);
        }
        return currency;
    }

    private Object resolveCoinsEngineCurrency(String currencyId) throws Exception {
        if (coinsEnginePlugin == null || getCurrencyManagerMethod == null || getCoinsEngineCurrencyMethod == null) {
            return null;
        }
        Object currencyManager = getCurrencyManagerMethod.invoke(coinsEnginePlugin);
        return getCoinsEngineCurrencyMethod.invoke(currencyManager, currencyId);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean setupExcellentEconomy(String currencyId) {
        try {
            if (excellentEconomyApi == null) {
                if (Bukkit.getPluginManager().getPlugin("ExcellentEconomy") == null) {
                    return false;
                }
                Class<?> apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) apiClass);
                if (rsp == null) {
                    return false;
                }
                excellentEconomyApi = rsp.getProvider();
                excellentEconomyGetCurrencyMethod = apiClass.getMethod("getCurrency", String.class);
                excellentEconomyGetBalanceMethod = apiClass.getMethod("getBalance", Player.class, String.class);
                excellentEconomyDepositMethod = apiClass.getMethod("deposit", Player.class, String.class, double.class);
                excellentEconomyWithdrawMethod = apiClass.getMethod("withdraw", Player.class, String.class, double.class);

                Class<?> currencyClass = Class.forName("su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency");
                excellentEconomyFormatMethod = currencyClass.getMethod("format", double.class);
            }

            if (getExcellentEconomyCurrency(currencyId) == null) {
                plugin.getLogger().warning("ExcellentEconomy currency '" + currencyId + "' not found!");
                return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into ExcellentEconomy: " + e.getMessage());
            return false;
        }
    }

    private Object getExcellentEconomyCurrency(String currencyId) throws Exception {
        Object currency = excellentEconomyCurrencies.get(currencyId);
        if (currency != null) {
            return currency;
        }
        if (excellentEconomyApi == null || excellentEconomyGetCurrencyMethod == null) {
            return null;
        }
        currency = excellentEconomyGetCurrencyMethod.invoke(excellentEconomyApi, currencyId);
        if (currency != null) {
            excellentEconomyCurrencies.put(currencyId, currency);
        }
        return currency;
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

    private String getEffectiveProvider(ConfigManager.CurrencyConfig currency) {
        String requested = currency.getProvider();
        if (requested.equalsIgnoreCase("auto") || requested.equalsIgnoreCase("default")) {
            return provider;
        }
        return requested;
    }

    private String getEffectiveCurrencyId(ConfigManager.CurrencyConfig currency) {
        if (currency.getId() != null && !currency.getId().isBlank()) {
            return currency.getId();
        }
        String providerName = getEffectiveProvider(currency);
        if (providerName.equalsIgnoreCase("coinsengine")) {
            return configManager.getCoinsEngineCurrency();
        }
        if (providerName.equalsIgnoreCase("excellenteconomy")) {
            return configManager.getExcellentEconomyCurrency();
        }
        return defaultCurrency.getId();
    }

    public double getBalance(Player player) {
        return getBalance(player, defaultCurrency);
    }

    public double getBalance(Player player, ConfigManager.CurrencyConfig currency) {
        String providerName = getEffectiveProvider(currency);
        String currencyId = getEffectiveCurrencyId(currency);
        if (providerName.equalsIgnoreCase("vault")) {
            if (setupVault() && vaultEconomy != null) {
                return vaultEconomy.getBalance(player);
            }
            return 0;
        }
        if (providerName.equalsIgnoreCase("coinsengine")) {
            try {
                if (!setupCoinsEngine(currencyId)) {
                    return 0;
                }
                Object result = getBalanceMethod.invoke(null, player, getCoinsEngineCurrency(currencyId));
                return ((Number) result).doubleValue();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get CoinsEngine balance: " + e.getMessage());
                return 0;
            }
        }
        if (providerName.equalsIgnoreCase("excellenteconomy")) {
            try {
                if (!setupExcellentEconomy(currencyId)) {
                    return 0;
                }
                Object result = excellentEconomyGetBalanceMethod.invoke(excellentEconomyApi, player, currencyId);
                return ((Number) result).doubleValue();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get ExcellentEconomy balance: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    public boolean withdraw(Player player, double amount) {
        return withdraw(player, defaultCurrency, amount);
    }

    public boolean withdraw(Player player, ConfigManager.CurrencyConfig currency, double amount) {
        String providerName = getEffectiveProvider(currency);
        String currencyId = getEffectiveCurrencyId(currency);
        if (providerName.equalsIgnoreCase("vault")) {
            return setupVault() && vaultEconomy != null
                    && vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        }
        if (providerName.equalsIgnoreCase("coinsengine")) {
            try {
                if (!setupCoinsEngine(currencyId)) {
                    return false;
                }
                removeBalanceMethod.invoke(null, player, getCoinsEngineCurrency(currencyId), amount);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to withdraw from CoinsEngine: " + e.getMessage());
                return false;
            }
        }
        if (providerName.equalsIgnoreCase("excellenteconomy")) {
            try {
                return setupExcellentEconomy(currencyId)
                        && (boolean) excellentEconomyWithdrawMethod.invoke(excellentEconomyApi, player, currencyId, amount);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to withdraw from ExcellentEconomy: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public boolean deposit(Player player, double amount) {
        return deposit(player, defaultCurrency, amount);
    }

    public boolean deposit(Player player, ConfigManager.CurrencyConfig currency, double amount) {
        String providerName = getEffectiveProvider(currency);
        String currencyId = getEffectiveCurrencyId(currency);
        if (providerName.equalsIgnoreCase("vault")) {
            return setupVault() && vaultEconomy != null
                    && vaultEconomy.depositPlayer(player, amount).transactionSuccess();
        }
        if (providerName.equalsIgnoreCase("coinsengine")) {
            try {
                if (!setupCoinsEngine(currencyId)) {
                    return false;
                }
                addBalanceMethod.invoke(null, player, getCoinsEngineCurrency(currencyId), amount);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit to CoinsEngine: " + e.getMessage());
                return false;
            }
        }
        if (providerName.equalsIgnoreCase("excellenteconomy")) {
            try {
                return setupExcellentEconomy(currencyId)
                        && (boolean) excellentEconomyDepositMethod.invoke(excellentEconomyApi, player, currencyId, amount);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit to ExcellentEconomy: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public boolean has(Player player, double amount) {
        return has(player, defaultCurrency, amount);
    }

    public boolean has(Player player, ConfigManager.CurrencyConfig currency, double amount) {
        return getBalance(player, currency) >= amount;
    }

    public String getProviderName() {
        return provider;
    }

    public boolean isReady() {
        return isReady(defaultCurrency);
    }

    public boolean isReady(ConfigManager.CurrencyConfig currency) {
        String providerName = getEffectiveProvider(currency);
        String currencyId = getEffectiveCurrencyId(currency);
        if (providerName.equalsIgnoreCase("vault")) {
            return setupVault();
        }
        if (providerName.equalsIgnoreCase("coinsengine")) {
            return setupCoinsEngine(currencyId);
        }
        if (providerName.equalsIgnoreCase("excellenteconomy")) {
            return setupExcellentEconomy(currencyId);
        }
        return false;
    }

    public String formatBalance(double amount) {
        return formatBalance(amount, defaultCurrency);
    }

    public String formatBalance(double amount, ConfigManager.CurrencyConfig currency) {
        String providerName = getEffectiveProvider(currency);
        String currencyId = getEffectiveCurrencyId(currency);
        if (providerName.equalsIgnoreCase("vault") && setupVault() && vaultEconomy != null) {
            return vaultEconomy.format(amount);
        }
        if (providerName.equalsIgnoreCase("excellenteconomy")) {
            try {
                if (setupExcellentEconomy(currencyId)) {
                    Object excellentCurrency = getExcellentEconomyCurrency(currencyId);
                    if (excellentCurrency != null) {
                        return String.valueOf(excellentEconomyFormatMethod.invoke(excellentCurrency, amount));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to format ExcellentEconomy amount: " + e.getMessage());
            }
        }
        return currency.getSymbol() + NumberFormatter.format(amount);
    }
}
