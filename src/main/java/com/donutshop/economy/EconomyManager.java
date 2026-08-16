package com.donutshop.economy;

import com.donutshop.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
    private Object eeApiObject;

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

            getBalanceMethod = findPlayerMethod(apiClass, "getBalance", currencyInterface);
            if (getBalanceMethod == null) {
                plugin.getLogger().warning("CoinsEngine getBalance method not found!");
                return false;
            }

            addBalanceMethod = findPlayerDoubleMethod(apiClass, "addBalance", currencyInterface);
            removeBalanceMethod = findPlayerDoubleMethod(apiClass, "removeBalance", currencyInterface);
            if (addBalanceMethod == null || removeBalanceMethod == null) {
                addBalanceMethod = findPlayerDoubleMethod(apiClass, "give", currencyInterface);
                removeBalanceMethod = findPlayerDoubleMethod(apiClass, "take", currencyInterface);
            }

            if (addBalanceMethod == null || removeBalanceMethod == null) {
                plugin.getLogger().warning("CoinsEngine give/take methods not found!");
                return false;
            }

            useCoinsEngine = true;
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into CoinsEngine: " + e.getMessage());
            return false;
        }
    }

    private boolean setupExcellentEconomy() {
        Plugin eePlugin = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
        if (eePlugin == null) return false;

        // Strategy 1: Original API — getCurrencyManager() on plugin class
        try {
            Method getCurrencyManagerMethod = eePlugin.getClass().getMethod("getCurrencyManager");
            Object currencyManager = getCurrencyManagerMethod.invoke(eePlugin);

            Method getCurrencyMethod = currencyManager.getClass().getMethod("getCurrency", String.class);
            eeCurrency = getCurrencyMethod.invoke(currencyManager, eeCurrencyName);

            if (eeCurrency != null) {
                Class<?> currencyInterface = findInterface(eeCurrency.getClass(), "Currency");

                eeGetBalanceMethod = findPlayerMethod(currencyInterface, "getBalance");
                if (eeGetBalanceMethod == null) {
                    eeGetBalanceMethod = findPlayerMethod(eeCurrency.getClass(), "getBalance");
                }

                eeAddBalanceMethod = findPlayerDoubleMethod(currencyInterface, "give");
                if (eeAddBalanceMethod == null) {
                    eeAddBalanceMethod = findPlayerDoubleMethod(currencyInterface, "addBalance");
                }
                eeRemoveBalanceMethod = findPlayerDoubleMethod(currencyInterface, "take");
                if (eeRemoveBalanceMethod == null) {
                    eeRemoveBalanceMethod = findPlayerDoubleMethod(currencyInterface, "removeBalance");
                }

                if (eeAddBalanceMethod == null) {
                    eeAddBalanceMethod = findPlayerDoubleMethod(eeCurrency.getClass(), "give");
                    if (eeAddBalanceMethod == null)
                        eeAddBalanceMethod = findPlayerDoubleMethod(eeCurrency.getClass(), "addBalance");
                }
                if (eeRemoveBalanceMethod == null) {
                    eeRemoveBalanceMethod = findPlayerDoubleMethod(eeCurrency.getClass(), "take");
                    if (eeRemoveBalanceMethod == null)
                        eeRemoveBalanceMethod = findPlayerDoubleMethod(eeCurrency.getClass(), "removeBalance");
                }

                if (eeGetBalanceMethod == null || eeAddBalanceMethod == null || eeRemoveBalanceMethod == null) {
                    try {
                        Class<?> apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");

                        if (eeGetBalanceMethod == null)
                            eeGetBalanceMethod = findPlayerMethod(apiClass, "getBalance", currencyInterface);

                        if (eeAddBalanceMethod == null) {
                            eeAddBalanceMethod = findPlayerDoubleMethod(apiClass, "addBalance", currencyInterface);
                            if (eeAddBalanceMethod == null)
                                eeAddBalanceMethod = findPlayerDoubleMethod(apiClass, "give", currencyInterface);
                        }
                        if (eeRemoveBalanceMethod == null) {
                            eeRemoveBalanceMethod = findPlayerDoubleMethod(apiClass, "removeBalance", currencyInterface);
                            if (eeRemoveBalanceMethod == null)
                                eeRemoveBalanceMethod = findPlayerDoubleMethod(apiClass, "take", currencyInterface);
                        }
                    } catch (ClassNotFoundException ignored) {}
                }

                if (eeGetBalanceMethod == null || eeAddBalanceMethod == null || eeRemoveBalanceMethod == null) {
                    for (Method m : eeCurrency.getClass().getMethods()) {
                        if (eeGetBalanceMethod == null && m.getName().equals("getBalance") && m.getParameterCount() == 1
                                && isPlayerParam(m.getParameterTypes()[0])) {
                            eeGetBalanceMethod = m;
                        }
                        if (eeAddBalanceMethod == null && (m.getName().equals("give") || m.getName().equals("addBalance"))
                                && m.getParameterCount() == 2 && isPlayerParam(m.getParameterTypes()[0])) {
                            eeAddBalanceMethod = m;
                        }
                        if (eeRemoveBalanceMethod == null && (m.getName().equals("take") || m.getName().equals("removeBalance"))
                                && m.getParameterCount() == 2 && isPlayerParam(m.getParameterTypes()[0])) {
                            eeRemoveBalanceMethod = m;
                        }
                    }
                }

                if (eeGetBalanceMethod != null && eeAddBalanceMethod != null && eeRemoveBalanceMethod != null) {
                    plugin.getLogger().info("ExcellentEconomy hooked: getBalance=" + eeGetBalanceMethod
                            + ", give=" + eeAddBalanceMethod + ", take=" + eeRemoveBalanceMethod);
                    useExcellentEconomy = true;
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().info("ExcellentEconomy original API not available (" + e.getMessage() + "), trying Folia fork API...");
        }

        // Strategy 2: Folia fork API — getAPI() on plugin or ServicesManager
        try {
            Object api = null;
            try {
                Method getApiMethod = eePlugin.getClass().getMethod("getAPI");
                api = getApiMethod.invoke(eePlugin);
            } catch (Exception ignored) {}

            if (api == null) {
                try {
                    Class<?> apiInterface = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
                    RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(apiInterface);
                    if (rsp != null) {
                        api = rsp.getProvider();
                    }
                } catch (Exception ignored) {}
            }

            if (api != null) {
                Class<?> apiClass = api.getClass();

                Method getBal = findPlayerMethod(apiClass, "getBalance", String.class);
                Method dep = findPlayerDoubleMethod(apiClass, "deposit", String.class);
                Method wit = findPlayerDoubleMethod(apiClass, "withdraw", String.class);

                if (getBal != null && dep != null && wit != null) {
                    eeApiObject = api;
                    eeGetBalanceMethod = getBal;
                    eeAddBalanceMethod = dep;
                    eeRemoveBalanceMethod = wit;
                    useExcellentEconomy = true;
                    plugin.getLogger().info("ExcellentEconomy (Folia) hooked via API: getBalance=" + getBal
                            + ", deposit=" + dep + ", withdraw=" + wit);
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into ExcellentEconomy Folia API: " + e.getMessage());
        }

        plugin.getLogger().warning("Failed to hook into ExcellentEconomy: no compatible API found for currency '" + eeCurrencyName + "'");
        return false;
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

    private boolean isPlayerParam(Class<?> paramType) {
        return paramType.isAssignableFrom(Player.class);
    }

    private Method findPlayerMethod(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name, Player.class);
        } catch (NoSuchMethodException ignored) {}
        try {
            return clazz.getMethod(name, OfflinePlayer.class);
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private Method findPlayerMethod(Class<?> clazz, String name, Class<?> extraParam) {
        try {
            return clazz.getMethod(name, Player.class, extraParam);
        } catch (NoSuchMethodException ignored) {}
        try {
            return clazz.getMethod(name, OfflinePlayer.class, extraParam);
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private Method findPlayerDoubleMethod(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name, Player.class, double.class);
        } catch (NoSuchMethodException ignored) {}
        try {
            return clazz.getMethod(name, OfflinePlayer.class, double.class);
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private Method findPlayerDoubleMethod(Class<?> clazz, String name, Class<?> extraParam) {
        try {
            return clazz.getMethod(name, Player.class, extraParam, double.class);
        } catch (NoSuchMethodException ignored) {}
        try {
            return clazz.getMethod(name, OfflinePlayer.class, extraParam, double.class);
        } catch (NoSuchMethodException ignored) {}
        return null;
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
                if (eeApiObject != null) {
                    result = eeGetBalanceMethod.invoke(eeApiObject, player, eeCurrencyName);
                } else if (eeGetBalanceMethod.getParameterCount() == 2) {
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
                if (eeApiObject != null) {
                    Object result = eeRemoveBalanceMethod.invoke(eeApiObject, player, eeCurrencyName, amount);
                    return !(result instanceof Boolean) || (Boolean) result;
                } else if (eeRemoveBalanceMethod.getParameterCount() == 3
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
                if (eeApiObject != null) {
                    Object result = eeAddBalanceMethod.invoke(eeApiObject, player, eeCurrencyName, amount);
                    return !(result instanceof Boolean) || (Boolean) result;
                } else if (eeAddBalanceMethod.getParameterCount() == 3
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
