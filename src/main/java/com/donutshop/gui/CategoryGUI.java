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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CategoryGUI implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 27;

    private static final int[] INNER_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17
    };
    private static final int ITEMS_PER_PAGE = INNER_SLOTS.length;

    private static final Map<UUID, Map<Integer, ConfigManager.ShopItem>> playerSlotMappings = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> storedPages = new ConcurrentHashMap<>();
    private static final Map<UUID, ConfigManager.CategoryConfig> playerCategories = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    /**
     * Constructor for event listener registration (no specific category).
     */
    public CategoryGUI(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Opens a specific category GUI for a player at the given page.
     */
    public void open(Player player, int page, ConfigManager.CategoryConfig category) {
        UUID uuid = player.getUniqueId();
        List<ConfigManager.ShopItem> items = category.getItems();
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        storedPages.put(uuid, page);
        playerCategories.put(uuid, category);

        String title = category.getGuiTitle();
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, MiniMessage.miniMessage().deserialize(title));

        // Fill entire inventory with filler panes
        Material fillerMat;
        String fillerName;
        try {
            fillerMat = Material.valueOf(configManager.getDefaultFillerMaterial());
        } catch (IllegalArgumentException ignored) {
            fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        }
        fillerName = configManager.getDefaultFillerName();
        if (category.isFillerEnabled()) {
            try {
                fillerMat = Material.valueOf(category.getFillerMaterial());
            } catch (IllegalArgumentException ignored) {}
            fillerName = category.getFillerName();
        }
        ItemStack filler = new ItemBuilder(fillerMat).rawName(fillerName).build();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Clear inner slots for item placement
        for (int slot : INNER_SLOTS) {
            inv.setItem(slot, null);
        }

        // Place items for this page
        Map<Integer, ConfigManager.ShopItem> slotMapping = new HashMap<>();
        String currencySymbol = configManager.getCurrencySymbol();

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
        List<ConfigManager.ShopItem> pageItems = items.subList(startIndex, endIndex);

        // Track which inner slots are used by specific-slot items
        Set<Integer> usedInnerSlots = new HashSet<>();
        List<ConfigManager.ShopItem> autoPlaceItems = new ArrayList<>();

        for (ConfigManager.ShopItem item : pageItems) {
            if (item.getSlot() >= 0) {
                int targetSlot = item.getSlot();
                if (targetSlot < GUI_SIZE) {
                    inv.setItem(targetSlot, buildShopItemStack(item, currencySymbol));
                    slotMapping.put(targetSlot, item);
                    usedInnerSlots.add(targetSlot);
                }
            } else {
                autoPlaceItems.add(item);
            }
        }

        // Auto-place remaining items in available inner slots
        int autoIndex = 0;
        for (ConfigManager.ShopItem item : autoPlaceItems) {
            while (autoIndex < INNER_SLOTS.length && usedInnerSlots.contains(INNER_SLOTS[autoIndex])) {
                autoIndex++;
            }
            if (autoIndex >= INNER_SLOTS.length) break;
            int targetSlot = INNER_SLOTS[autoIndex];
            inv.setItem(targetSlot, buildShopItemStack(item, currencySymbol));
            slotMapping.put(targetSlot, item);
            autoIndex++;
        }

        playerSlotMappings.put(uuid, slotMapping);

        // Navigation items (from config)
        ConfigManager.NavigationConfig navBack = configManager.getNavBack();
        ConfigManager.NavigationConfig navPrev = configManager.getNavPrev();
        ConfigManager.NavigationConfig navNext = configManager.getNavNext();
        ConfigManager.NavigationConfig navPageInfo = configManager.getNavPageInfo();
        ConfigManager.NavigationConfig navClose = configManager.getNavClose();

        if (navBack.isEnabled()) {
            ItemBuilder backBuilder = new ItemBuilder(Material.valueOf(navBack.getMaterial()))
                    .rawName(navBack.getName());
            if (navBack.getPotionType() != null) {
                try {
                    backBuilder.potionType(PotionType.valueOf(navBack.getPotionType()));
                } catch (IllegalArgumentException ignored) {}
            }
            inv.setItem(navBack.getSlot(), backBuilder.build());
        }

        if (page > 0 && navPrev.isEnabled()) {
            inv.setItem(navPrev.getSlot(), new ItemBuilder(Material.valueOf(navPrev.getMaterial()))
                    .rawName(navPrev.getName()).build());
        }

        if (navPageInfo.isEnabled()) {
            String pageInfoName = navPageInfo.getName()
                    .replace("{page}", String.valueOf(page + 1))
                    .replace("{total}", String.valueOf(totalPages));
            inv.setItem(navPageInfo.getSlot(), new ItemBuilder(Material.valueOf(navPageInfo.getMaterial()))
                    .rawName(pageInfoName).build());
        }

        if (page < totalPages - 1 && navNext.isEnabled()) {
            inv.setItem(navNext.getSlot(), new ItemBuilder(Material.valueOf(navNext.getMaterial()))
                    .rawName(navNext.getName()).build());
        }

        if (navClose.isEnabled()) {
            inv.setItem(navClose.getSlot(), new ItemBuilder(Material.valueOf(navClose.getMaterial()))
                    .rawName(navClose.getName()).build());
        }

        player.openInventory(inv);
    }

    private ItemStack buildShopItemStack(ConfigManager.ShopItem shopItem, String currencySymbol) {
        Material mat = Material.valueOf(shopItem.getMaterial());
        ItemBuilder builder = new ItemBuilder(mat);

        // Use normal formatting for item name (Title Case from material name)
        String displayName = formatMaterialName(mat);
        builder.rawName("<white>" + displayName);

        // Build lore from configurable format
        List<String> loreFormat = configManager.getItemLoreFormat();
        List<String> lore = new ArrayList<>();
        for (String line : loreFormat) {
            String costStr = shopItem.getBuyPrice() >= 0
                    ? currencySymbol + String.format("%.2f", shopItem.getBuyPrice())
                    : "<red>Not for sale";
            String sellStr = shopItem.getSellPrice() >= 0
                    ? currencySymbol + String.format("%.2f", shopItem.getSellPrice())
                    : "<red>Cannot sell";
            lore.add(line
                    .replace("{cost}", costStr)
                    .replace("{buy}", costStr)
                    .replace("{sell}", sellStr)
                    .replace("{item}", displayName));
        }

        builder.rawLore(lore);

        if (shopItem.getAmount() > 1) {
            builder.amount(shopItem.getAmount());
        }

        if (shopItem.getCustomModelData() > 0) {
            builder.customModelData(shopItem.getCustomModelData());
        }

        return builder.build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CategoryGUI)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        // Navigation handling (config-based slots)
        ConfigManager.NavigationConfig navBack = configManager.getNavBack();
        ConfigManager.NavigationConfig navClose = configManager.getNavClose();
        ConfigManager.NavigationConfig navPrev = configManager.getNavPrev();
        ConfigManager.NavigationConfig navNext = configManager.getNavNext();

        if (navBack.isEnabled() && slot == navBack.getSlot()) {
            playSound(player, configManager.getSoundNavigate());
            player.closeInventory();
            ((DonutShop) plugin).getShopGUI().open(player);
            return;
        }

        if (navClose.isEnabled() && slot == navClose.getSlot()) {
            player.closeInventory();
            return;
        }

        ConfigManager.CategoryConfig cat = playerCategories.get(uuid);
        if (cat == null) return;
        int currentPage = storedPages.getOrDefault(uuid, 0);

        if (navPrev.isEnabled() && slot == navPrev.getSlot() && currentPage > 0) {
            playSound(player, configManager.getSoundNavigate());
            open(player, currentPage - 1, cat);
            return;
        }

        if (navNext.isEnabled() && slot == navNext.getSlot()) {
            int totalPages = Math.max(1, (int) Math.ceil((double) cat.getItems().size() / ITEMS_PER_PAGE));
            if (currentPage < totalPages - 1) {
                playSound(player, configManager.getSoundNavigate());
                open(player, currentPage + 1, cat);
                return;
            }
        }

        // Shop item click handling
        Map<Integer, ConfigManager.ShopItem> slotMap = playerSlotMappings.get(uuid);
        if (slotMap == null) return;

        ConfigManager.ShopItem shopItem = slotMap.get(slot);
        if (shopItem == null) return;

        EconomyManager economy = ((DonutShop) plugin).getEconomyManager();
        if (economy == null || !economy.isReady()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Economy is not available!"));
            return;
        }

        ClickType clickType = event.getClick();
        String currencySymbol = configManager.getCurrencySymbol();
        int shiftAmount = configManager.getShiftClickAmount();

        if (clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT) {
            // Open confirmation GUI for buying
            if (shopItem.getBuyPrice() < 0) {
                playSound(player, configManager.getSoundError());
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>This item cannot be purchased!"));
                return;
            }
            player.closeInventory();
            ConfirmationGUI confirmGUI = new ConfirmationGUI(plugin, configManager);
            confirmGUI.open(player, shopItem, cat, currentPage);
        } else if (clickType == ClickType.RIGHT) {
            handleSell(player, shopItem, 1, economy, currencySymbol);
        } else if (clickType == ClickType.SHIFT_RIGHT) {
            handleSell(player, shopItem, shiftAmount, economy, currencySymbol);
        } else if (clickType == ClickType.MIDDLE && configManager.isMiddleClickSellAll()) {
            handleSellAll(player, shopItem, economy, currencySymbol);
        }
    }

    private void handleBuy(Player player, ConfigManager.ShopItem shopItem, int amount,
                           EconomyManager economy, String currencySymbol) {
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

        // Withdraw first, then give items
        if (!economy.withdraw(player, totalCost)) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed!"));
            return;
        }

        ItemStack itemStack = new ItemStack(mat, amount);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);

        if (!leftover.isEmpty()) {
            // Some items didn't fit — refund for those
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
    }

    private void handleSell(Player player, ConfigManager.ShopItem shopItem, int amount,
                            EconomyManager economy, String currencySymbol) {
        if (shopItem.getSellPrice() < 0) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>This item cannot be sold!"));
            return;
        }

        Material mat = Material.valueOf(shopItem.getMaterial());
        int playerHas = countItems(player, mat);

        if (playerHas <= 0) {
            playSound(player, configManager.getSoundError());
            String msg = configManager.getMessage("not-enough-items");
            if (msg.isEmpty()) msg = "<red>You don't have enough items!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        int toSell = Math.min(amount, playerHas);
        double totalEarnings = shopItem.getSellPrice() * toSell;

        removeItems(player, mat, toSell);
        economy.deposit(player, totalEarnings);

        playSound(player, configManager.getSoundSell());
        String materialName = formatMaterialName(mat);
        String msg = configManager.getMessage("sell-success")
                .replace("{amount}", String.valueOf(toSell))
                .replace("{item}", materialName)
                .replace("{price}", currencySymbol + String.format("%.2f", totalEarnings));
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    private void handleSellAll(Player player, ConfigManager.ShopItem shopItem,
                               EconomyManager economy, String currencySymbol) {
        if (shopItem.getSellPrice() < 0) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>This item cannot be sold!"));
            return;
        }

        Material mat = Material.valueOf(shopItem.getMaterial());
        int playerHas = countItems(player, mat);

        if (playerHas <= 0) {
            playSound(player, configManager.getSoundError());
            String msg = configManager.getMessage("not-enough-items");
            if (msg.isEmpty()) msg = "<red>You don't have enough items!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        double totalEarnings = shopItem.getSellPrice() * playerHas;

        removeItems(player, mat, playerHas);
        economy.deposit(player, totalEarnings);

        playSound(player, configManager.getSoundSell());
        String materialName = formatMaterialName(mat);
        String msg = configManager.getMessage("sell-success")
                .replace("{amount}", String.valueOf(playerHas))
                .replace("{item}", materialName)
                .replace("{price}", currencySymbol + String.format("%.2f", totalEarnings));
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound name in config — silently ignore
        }
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack is : player.getInventory().getStorageContents()) {
            if (is != null && is.getType() == material) {
                count += is.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is != null && is.getType() == material) {
                int stackAmount = is.getAmount();
                if (stackAmount <= remaining) {
                    remaining -= stackAmount;
                    contents[i] = null;
                } else {
                    is.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
            }
        }
        player.getInventory().setStorageContents(contents);
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
        if (!(event.getInventory().getHolder() instanceof CategoryGUI)) return;
        // Only clean up if not immediately re-opening (e.g. page navigation)
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof CategoryGUI)) {
                playerSlotMappings.remove(uuid);
                storedPages.remove(uuid);
                playerCategories.remove(uuid);
            }
        }, 1L);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
