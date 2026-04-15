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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CategoryGUI implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 54;

    private static final int[] INNER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = INNER_SLOTS.length;

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

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
        Material fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        String fillerName = " ";
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

        // Navigation items
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).rawName("<gray>ʙᴀᴄᴋ").build());

        if (page > 0) {
            inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW).rawName("<gray>ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ").build());
        }

        inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .rawName("<gray>ᴘᴀɢᴇ " + (page + 1) + "/" + totalPages).build());

        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW).rawName("<gray>ɴᴇxᴛ ᴘᴀɢᴇ").build());
        }

        inv.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER).rawName("<red>ᴄʟᴏsᴇ").build());

        player.openInventory(inv);
    }

    private ItemStack buildShopItemStack(ConfigManager.ShopItem shopItem, String currencySymbol) {
        Material mat = Material.valueOf(shopItem.getMaterial());
        ItemBuilder builder = new ItemBuilder(mat);

        if (shopItem.getName() != null) {
            builder.rawName(shopItem.getName());
        }

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (shopItem.getBuyPrice() >= 0) {
            lore.add("<gray>ʙᴜʏ ᴘʀɪᴄᴇ: <green>" + currencySymbol + String.format("%.2f", shopItem.getBuyPrice()));
        } else {
            lore.add("<gray>ʙᴜʏ ᴘʀɪᴄᴇ: <red>ᴄᴀɴɴᴏᴛ ʙᴜʏ");
        }

        if (shopItem.getSellPrice() >= 0) {
            lore.add("<gray>sᴇʟʟ ᴘʀɪᴄᴇ: <green>" + currencySymbol + String.format("%.2f", shopItem.getSellPrice()));
        } else {
            lore.add("<gray>sᴇʟʟ ᴘʀɪᴄᴇ: <red>ᴄᴀɴɴᴏᴛ sᴇʟʟ");
        }

        lore.add("");
        lore.add("<yellow>ʟᴇғᴛ-ᴄʟɪᴄᴋ <gray>ᴛᴏ ʙᴜʏ <white>1");
        lore.add("<yellow>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <gray>ᴛᴏ sᴇʟʟ <white>1");
        lore.add("<yellow>sʜɪғᴛ + ʟᴇғᴛ <gray>ᴛᴏ ʙᴜʏ <white>64");
        lore.add("<yellow>sʜɪғᴛ + ʀɪɢʜᴛ <gray>ᴛᴏ sᴇʟʟ <white>64");
        lore.add("<yellow>ᴍɪᴅᴅʟᴇ-ᴄʟɪᴄᴋ <gray>ᴛᴏ sᴇʟʟ ᴀʟʟ");

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

        // Navigation handling
        if (slot == SLOT_BACK) {
            player.closeInventory();
            ((DonutShop) plugin).getShopGUI().open(player);
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        ConfigManager.CategoryConfig cat = playerCategories.get(uuid);
        if (cat == null) return;
        int currentPage = storedPages.getOrDefault(uuid, 0);

        if (slot == SLOT_PREV && currentPage > 0) {
            open(player, currentPage - 1, cat);
            return;
        }

        if (slot == SLOT_NEXT) {
            int totalPages = Math.max(1, (int) Math.ceil((double) cat.getItems().size() / ITEMS_PER_PAGE));
            if (currentPage < totalPages - 1) {
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
                    "<red>ᴇᴄᴏɴᴏᴍʏ ɪs ɴᴏᴛ ᴀᴠᴀɪʟᴀʙʟᴇ!"));
            return;
        }

        ClickType clickType = event.getClick();
        String currencySymbol = configManager.getCurrencySymbol();

        if (clickType == ClickType.LEFT) {
            handleBuy(player, shopItem, 1, economy, currencySymbol);
        } else if (clickType == ClickType.RIGHT) {
            handleSell(player, shopItem, 1, economy, currencySymbol);
        } else if (clickType == ClickType.SHIFT_LEFT) {
            handleBuy(player, shopItem, 64, economy, currencySymbol);
        } else if (clickType == ClickType.SHIFT_RIGHT) {
            handleSell(player, shopItem, 64, economy, currencySymbol);
        } else if (clickType == ClickType.MIDDLE) {
            handleSellAll(player, shopItem, economy, currencySymbol);
        }
    }

    private void handleBuy(Player player, ConfigManager.ShopItem shopItem, int amount,
                           EconomyManager economy, String currencySymbol) {
        if (shopItem.getBuyPrice() < 0) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>ᴛʜɪs ɪᴛᴇᴍ ᴄᴀɴɴᴏᴛ ʙᴇ ᴘᴜʀᴄʜᴀsᴇᴅ!"));
            return;
        }

        double totalCost = shopItem.getBuyPrice() * amount;

        if (!economy.has(player, totalCost)) {
            String msg = configManager.getMessage("not-enough-money");
            if (msg.isEmpty()) msg = "<red>ʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴇɴᴏᴜɢʜ ᴍᴏɴᴇʏ!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        Material mat = Material.valueOf(shopItem.getMaterial());

        // Withdraw first, then give items
        if (!economy.withdraw(player, totalCost)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>ᴛʀᴀɴsᴀᴄᴛɪᴏɴ ғᴀɪʟᴇᴅ!"));
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
                String msg = configManager.getMessage("inventory-full");
                if (msg.isEmpty()) msg = "<red>ʏᴏᴜʀ ɪɴᴠᴇɴᴛᴏʀʏ ɪs ғᴜʟʟ!";
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                return;
            }
        }

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
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>ᴛʜɪs ɪᴛᴇᴍ ᴄᴀɴɴᴏᴛ ʙᴇ sᴏʟᴅ!"));
            return;
        }

        Material mat = Material.valueOf(shopItem.getMaterial());
        int playerHas = countItems(player, mat);

        if (playerHas <= 0) {
            String msg = configManager.getMessage("not-enough-items");
            if (msg.isEmpty()) msg = "<red>ʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴇɴᴏᴜɢʜ ɪᴛᴇᴍs!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        int toSell = Math.min(amount, playerHas);
        double totalEarnings = shopItem.getSellPrice() * toSell;

        removeItems(player, mat, toSell);
        economy.deposit(player, totalEarnings);

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
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>ᴛʜɪs ɪᴛᴇᴍ ᴄᴀɴɴᴏᴛ ʙᴇ sᴏʟᴅ!"));
            return;
        }

        Material mat = Material.valueOf(shopItem.getMaterial());
        int playerHas = countItems(player, mat);

        if (playerHas <= 0) {
            String msg = configManager.getMessage("not-enough-items");
            if (msg.isEmpty()) msg = "<red>ʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴇɴᴏᴜɢʜ ɪᴛᴇᴍs!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        double totalEarnings = shopItem.getSellPrice() * playerHas;

        removeItems(player, mat, playerHas);
        economy.deposit(player, totalEarnings);

        String materialName = formatMaterialName(mat);
        String msg = configManager.getMessage("sell-success")
                .replace("{amount}", String.valueOf(playerHas))
                .replace("{item}", materialName)
                .replace("{price}", currencySymbol + String.format("%.2f", totalEarnings));
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
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
