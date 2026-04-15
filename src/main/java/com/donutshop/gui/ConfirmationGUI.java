package com.donutshop.gui;

import com.donutshop.DonutShop;
import com.donutshop.config.ConfigManager;
import com.donutshop.economy.EconomyManager;
import com.donutshop.util.ItemBuilder;
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

    // Layout slots
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
        if (data == null || !data.shopItem.getMaterial().equals(shopItem.getMaterial())) {
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

    private void refreshInventory(Player player, ConfirmationData data) {
        String materialName = formatMaterialName(Material.valueOf(data.shopItem.getMaterial()));
        String title = "<white>ʙᴜʏ " + materialName;

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
        int maxStack = itemMat.getMaxStackSize();
        int amount = data.amount;

        // Item display in center
        inv.setItem(SLOT_ITEM, new ItemBuilder(itemMat)
                .rawName("<white>" + materialName)
                .rawLore(List.of("", "<gray>ᴀᴍᴏᴜɴᴛ: <white>" + amount))
                .amount(Math.min(amount, maxStack))
                .build());

        // Remove buttons (red) - only show if removing wouldn't go to 0 or below
        if (amount - 1 >= 1) {
            inv.setItem(SLOT_REMOVE_1, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .rawName("<red>-1").build());
        }
        if (amount - 10 >= 1) {
            inv.setItem(SLOT_REMOVE_10, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .rawName("<red>-10").build());
        }
        if (amount - 64 >= 1) {
            inv.setItem(SLOT_REMOVE_64, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .rawName("<red>-64").build());
        }

        // Add buttons (lime) - only show if adding wouldn't exceed max stack
        if (amount + 1 <= maxStack) {
            inv.setItem(SLOT_ADD_1, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .rawName("<green>+1").build());
        }
        if (amount + 10 <= maxStack) {
            inv.setItem(SLOT_ADD_10, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .rawName("<green>+10").build());
        }
        if (amount + 64 <= maxStack) {
            inv.setItem(SLOT_ADD_64, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .rawName("<green>+64").build());
        }

        // Back button (tipped arrow of healing)
        ConfigManager.NavigationConfig navBack = configManager.getNavBack();
        ItemBuilder backBuilder = new ItemBuilder(Material.TIPPED_ARROW)
                .rawName("<gray>Back")
                .potionType(PotionType.HEALING);
        inv.setItem(SLOT_BACK, backBuilder.build());

        // Cost info
        String currencySymbol = configManager.getCurrencySymbol();
        double totalCost = data.shopItem.getBuyPrice() * amount;
        inv.setItem(SLOT_COST_INFO, new ItemBuilder(Material.PAPER)
                .rawName("<gray>ᴛᴏᴛᴀʟ ᴄᴏsᴛ")
                .rawLore(List.of("", "<italic><gray>ᴄᴏsᴛ: <green>" + currencySymbol +
                        String.format("%.2f", totalCost) + "</italic>"))
                .build());

        // Confirm button
        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .rawName("<green>ᴄᴏɴғɪʀᴍ ᴘᴜʀᴄʜᴀsᴇ")
                .rawLore(List.of("", "<gray>ᴄʟɪᴄᴋ ᴛᴏ ʙᴜʏ <white>" + amount + "x " + materialName))
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

        Material itemMat = Material.valueOf(data.shopItem.getMaterial());
        int maxStack = itemMat.getMaxStackSize();

        switch (slot) {
            case SLOT_REMOVE_64:
                if (data.amount - 64 >= 1) {
                    data.amount -= 64;
                    playSound(player, configManager.getSoundNavigate());
                    refreshInventory(player, data);
                }
                break;
            case SLOT_REMOVE_10:
                if (data.amount - 10 >= 1) {
                    data.amount -= 10;
                    playSound(player, configManager.getSoundNavigate());
                    refreshInventory(player, data);
                }
                break;
            case SLOT_REMOVE_1:
                if (data.amount - 1 >= 1) {
                    data.amount -= 1;
                    playSound(player, configManager.getSoundNavigate());
                    refreshInventory(player, data);
                }
                break;
            case SLOT_ADD_1:
                if (data.amount + 1 <= maxStack) {
                    data.amount += 1;
                    playSound(player, configManager.getSoundNavigate());
                    refreshInventory(player, data);
                }
                break;
            case SLOT_ADD_10:
                if (data.amount + 10 <= maxStack) {
                    data.amount += 10;
                    playSound(player, configManager.getSoundNavigate());
                    refreshInventory(player, data);
                }
                break;
            case SLOT_ADD_64:
                if (data.amount + 64 <= maxStack) {
                    data.amount += 64;
                    playSound(player, configManager.getSoundNavigate());
                    refreshInventory(player, data);
                }
                break;
            case SLOT_BACK:
                playSound(player, configManager.getSoundNavigate());
                playerData.remove(uuid);
                player.closeInventory();
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
        EconomyManager economy = ((DonutShop) plugin).getEconomyManager();
        if (economy == null || !economy.isReady()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Economy is not available!"));
            return;
        }

        ConfigManager.ShopItem shopItem = data.shopItem;
        int amount = data.amount;
        String currencySymbol = configManager.getCurrencySymbol();

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
        String materialName = formatMaterialName(mat);
        String msg = configManager.getMessage("buy-success")
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", materialName)
                .replace("{price}", currencySymbol + String.format("%.2f", totalCost));
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));

        // Stay open - refresh the confirmation GUI (don't close)
        refreshInventory(player, data);
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof ConfirmationGUI)) {
                playerData.remove(uuid);
            }
        }, 1L);
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
