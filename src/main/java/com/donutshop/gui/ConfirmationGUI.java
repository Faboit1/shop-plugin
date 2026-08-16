package com.donutshop.gui;

import com.donutshop.DonutShop;
import com.donutshop.config.ConfigManager;
import com.donutshop.economy.EconomyManager;
import com.donutshop.util.ItemBuilder;
import com.donutshop.util.NumberFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationGUI implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 27;

    private static final int SLOT_REMOVE_64 = 9;
    private static final int SLOT_REMOVE_10 = 10;
    private static final int SLOT_REMOVE_1 = 11;
    private static final int SLOT_ITEM = 13;
    private static final int SLOT_ADD_1 = 15;
    private static final int SLOT_ADD_10 = 16;
    private static final int SLOT_ADD_64 = 17;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_COST_INFO = 22;
    private static final int SLOT_CONFIRM = 26;

    private static final Map<UUID, ConfirmationData> playerData = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public ConfirmationGUI(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void open(Player player, ConfigManager.ShopItem shopItem,
                     ConfigManager.CategoryConfig returnCategory, int returnPage) {
        UUID uuid = player.getUniqueId();

        ConfirmationData data = playerData.get(uuid);
        if (data == null || !isSameItem(data.shopItem, shopItem)) {
            data = new ConfirmationData();
            data.shopItem = shopItem;
            data.returnCategory = returnCategory;
            data.returnPage = returnPage;
            data.amount = 1;
        } else {
            data.returnCategory = returnCategory;
            data.returnPage = returnPage;
        }
        playerData.put(uuid, data);

        refreshInventory(player, data);
    }

    private boolean isSameItem(ConfigManager.ShopItem a, ConfigManager.ShopItem b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getMaterial(), b.getMaterial())) return false;
        if (!Objects.equals(a.getName(), b.getName())) return false;
        if (Double.compare(a.getBuyPrice(), b.getBuyPrice()) != 0) return false;
        return Objects.equals(a.getType(), b.getType());
    }

    private void refreshInventory(Player player, ConfirmationData data) {
        String itemDisplayName = getItemDisplayName(data.shopItem);
        String title = "<white>ʙᴜʏ " + itemDisplayName;

        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, MiniMessage.miniMessage().deserialize(title));

        // Fill with filler
        Material fillerMat;
        try {
            fillerMat = Material.valueOf(configManager.getDefaultFillerMaterial());
        } catch (IllegalArgumentException ignored) {
            fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        }
        ItemStack filler = new ItemBuilder(fillerMat).rawName(" ").build();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        Material itemMat = Material.valueOf(data.shopItem.getMaterial());
        int amount = data.amount;

        // Item display in center — use custom name if set
        ItemBuilder itemBuilder = new ItemBuilder(itemMat);
        if (data.shopItem.getName() != null && !data.shopItem.getName().isEmpty()) {
            itemBuilder.rawName(data.shopItem.getName());
        } else {
            itemBuilder.rawName("<white>" + itemDisplayName);
        }
        itemBuilder.rawLore(List.of("", "<gray>ᴀᴍᴏᴜɴᴛ: <white>" + amount));
        itemBuilder.amount(Math.min(amount, itemMat.getMaxStackSize()));
        inv.setItem(SLOT_ITEM, itemBuilder.build());

        // Remove/Add buttons
        inv.setItem(SLOT_REMOVE_1, buildButton(configManager.getConfirmDecrease1()));
        inv.setItem(SLOT_REMOVE_10, buildButton(configManager.getConfirmDecrease10()));
        inv.setItem(SLOT_REMOVE_64, buildButton(configManager.getConfirmDecrease64()));
        inv.setItem(SLOT_ADD_1, buildButton(configManager.getConfirmIncrease1()));
        inv.setItem(SLOT_ADD_10, buildButton(configManager.getConfirmIncrease10()));
        inv.setItem(SLOT_ADD_64, buildButton(configManager.getConfirmIncrease64()));

        // Back button
        ConfigManager.NavigationConfig navBack = configManager.getNavBack();
        ItemBuilder backBuilder = new ItemBuilder(parseMaterial(navBack.getMaterial(), Material.TIPPED_ARROW))
                .rawName(navBack.getName());
        if (navBack.getPotionType() != null) {
            try {
                backBuilder.potionType(PotionType.valueOf(navBack.getPotionType()));
            } catch (IllegalArgumentException ignored) {}
        }
        inv.setItem(SLOT_BACK, backBuilder.build());

        // Cost info — use per-category currency symbol
        String currencySymbol = resolveCurrencySymbol(data.returnCategory);
        double totalCost = data.shopItem.getBuyPrice() * amount;
        ConfigManager.ButtonConfig costInfoBtn = configManager.getConfirmCostInfo();
        inv.setItem(SLOT_COST_INFO, new ItemBuilder(parseMaterial(costInfoBtn.getMaterial(), Material.PAPER))
                .rawName(costInfoBtn.getName())
                .rawLore(List.of("", "<italic><gray>ᴄᴏsᴛ: <green>" + currencySymbol +
                        NumberFormatter.format(totalCost) + "</italic>"))
                .build());

        // Confirm button
        ConfigManager.ButtonConfig confirmBtn = configManager.getConfirmConfirmBtn();
        inv.setItem(SLOT_CONFIRM, new ItemBuilder(parseMaterial(confirmBtn.getMaterial(), Material.LIME_STAINED_GLASS_PANE))
                .rawName(confirmBtn.getName())
                .rawLore(List.of("", "<gray>ᴄʟɪᴄᴋ ᴛᴏ ʙᴜʏ <white>" + amount + "x " + itemDisplayName))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmationGUI)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        ConfirmationData data = playerData.get(uuid);
        if (data == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        switch (slot) {
            case SLOT_REMOVE_64:
                data.amount = Math.max(1, data.amount - 64);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
                break;
            case SLOT_REMOVE_10:
                data.amount = Math.max(1, data.amount - 10);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
                break;
            case SLOT_REMOVE_1:
                data.amount = Math.max(1, data.amount - 1);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
                break;
            case SLOT_ADD_1:
                data.amount = Math.min(64, data.amount + 1);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
                break;
            case SLOT_ADD_10:
                data.amount = Math.min(64, data.amount + 10);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
                break;
            case SLOT_ADD_64:
                data.amount = Math.min(64, data.amount + 64);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
                break;
            case SLOT_BACK:
                playSound(player, configManager.getSoundNavigate());
                playerData.remove(uuid);
                CategoryGUI categoryGUI = new CategoryGUI(plugin, configManager);
                categoryGUI.open(player, data.returnPage, data.returnCategory);
                break;
            case SLOT_CONFIRM:
                handleConfirmPurchase(player, data);
                break;
            default:
                break;
        }
    }

    private void handleConfirmPurchase(Player player, ConfirmationData data) {
        EconomyManager economy = ((DonutShop) plugin).getEconomyForCategory(data.returnCategory);
        if (economy == null || !economy.isReady()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Economy is not available!"));
            return;
        }

        ConfigManager.ShopItem shopItem = data.shopItem;
        int amount = data.amount;
        String currencySymbol = resolveCurrencySymbol(data.returnCategory);

        if (shopItem.getBuyPrice() < 0) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>This item cannot be purchased!"));
            return;
        }

        double totalCost = shopItem.getBuyPrice() * amount;

        if (!economy.has(player, totalCost)) {
            playSound(player, configManager.getSoundError());
            String msg = configManager.getMessage("not-enough-money");
            if (msg.isEmpty()) msg = "<red>You don't have enough money!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        if (!shopItem.isCommandItem()) {
            // Material item purchase
            Material mat = Material.valueOf(shopItem.getMaterial());

            if (!economy.withdraw(player, totalCost)) {
                playSound(player, configManager.getSoundError());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed!"));
                return;
            }

            ItemStack itemStack = new ItemStack(mat, amount);
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);

            if (!leftover.isEmpty()) {
                int notAdded = 0;
                for (ItemStack left : leftover.values()) {
                    notAdded += left.getAmount();
                }
                double refund = shopItem.getBuyPrice() * notAdded;
                economy.deposit(player, refund);
                amount -= notAdded;
                totalCost -= refund;

                if (amount <= 0) {
                    playSound(player, configManager.getSoundError());
                    String msg = configManager.getMessage("inventory-full");
                    if (msg.isEmpty()) msg = "<red>Your inventory is full!";
                    player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                    return;
                }
            }

            playSound(player, configManager.getSoundBuy());
            String itemName = getItemDisplayName(shopItem);
            String msg = configManager.getMessage("buy-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", itemName)
                    .replace("{price}", currencySymbol + NumberFormatter.format(totalCost));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        } else {
            // Command item purchase
            if (!economy.withdraw(player, totalCost)) {
                playSound(player, configManager.getSoundError());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed!"));
                return;
            }

            try {
                for (int i = 0; i < amount; i++) {
                    for (String cmd : shopItem.getCommands()) {
                        String processed = cmd
                                .replace("{player}", player.getName())
                                .replace("%player%", player.getName());
                        Bukkit.getCommandMap().dispatch(plugin.getServer().getConsoleSender(), processed);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to dispatch command for shop item: " + e.getMessage());
                economy.deposit(player, totalCost);
                playSound(player, configManager.getSoundError());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Purchase failed! You have been refunded."));
                refreshInventory(player, data);
                return;
            }

            playSound(player, configManager.getSoundBuy());
            String itemName = getItemDisplayName(shopItem);
            String msg = configManager.getMessage("buy-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", itemName)
                    .replace("{price}", currencySymbol + NumberFormatter.format(totalCost));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }

        refreshInventory(player, data);
    }

    private String resolveCurrencySymbol(ConfigManager.CategoryConfig category) {
        if (category != null && category.hasCurrencyOverride() && category.getCurrencySymbol() != null) {
            return category.getCurrencySymbol();
        }
        return configManager.getCurrencySymbol();
    }

    private String getItemDisplayName(ConfigManager.ShopItem shopItem) {
        if (shopItem.getName() != null && !shopItem.getName().isEmpty()) {
            return stripColors(shopItem.getName());
        }
        return formatMaterialName(Material.valueOf(shopItem.getMaterial()));
    }

    private String stripColors(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-orA-FK-Or]", "")
                   .replaceAll("<[^>]+>", "");
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    private ItemStack buildButton(ConfigManager.ButtonConfig btn) {
        return new ItemBuilder(parseMaterial(btn.getMaterial(), Material.STONE))
                .rawName(btn.getName())
                .build();
    }

    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        StringBuilder formatted = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (capitalize && Character.isLetter(c)) {
                formatted.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                formatted.append(c);
                if (c == ' ') capitalize = true;
            }
        }
        return formatted.toString();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmationGUI)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Runnable cleanup = () -> playerData.remove(uuid);
        event.getPlayer().getScheduler().runDelayed(plugin, task -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof ConfirmationGUI)) {
                cleanup.run();
            }
        }, cleanup, 1);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    private static class ConfirmationData {
        ConfigManager.ShopItem shopItem;
        ConfigManager.CategoryConfig returnCategory;
        int returnPage;
        int amount = 1;
    }
}
