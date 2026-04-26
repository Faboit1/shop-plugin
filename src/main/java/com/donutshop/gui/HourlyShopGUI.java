package com.donutshop.gui;

import com.donutshop.DonutShop;
import com.donutshop.config.ConfigManager;
import com.donutshop.economy.EconomyManager;
import com.donutshop.hourly.HourlyItem;
import com.donutshop.hourly.HourlyItemManager;
import com.donutshop.util.ItemBuilder;
import com.donutshop.util.NumberFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI that displays the 3 (configurable) current hourly shop items.
 * Supports both material items (gives the item) and command items (runs console commands).
 * Item names support legacy &-codes and MiniMessage format.
 */
public class HourlyShopGUI implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 27;
    private static final int BACK_BUTTON_SLOT = 18;

    // Per-player slot → HourlyItem mapping, populated when the GUI is opened
    private static final Map<UUID, Map<Integer, HourlyItem>> playerSlotMappings = new ConcurrentHashMap<>();

    private final DonutShop plugin;
    private final ConfigManager configManager;

    public HourlyShopGUI(DonutShop plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    // ── Open ──────────────────────────────────────────────────

    public void open(Player player) {
        openInternal(player, true);
    }

    /** Rebuilds the GUI inventory without playing the open-menu sound (used after purchases). */
    private void refreshForPlayer(Player player) {
        openInternal(player, false);
    }

    private void openInternal(Player player, boolean playOpenSound) {
        HourlyItemManager manager = plugin.getHourlyItemManager();
        List<HourlyItem> currentItems = manager.getCurrentItems();

        String title = configManager.getHourlyShopGuiTitle();
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE,
                MiniMessage.miniMessage().deserialize(title));

        // Filler
        Material fillerMat = parseMaterial(configManager.getMainMenuFillerMaterial(), Material.BLACK_STAINED_GLASS_PANE);
        ItemStack filler = new ItemBuilder(fillerMat).rawName(configManager.getMainMenuFillerName()).build();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Back button
        ConfigManager.NavigationConfig navBack = configManager.getNavBack();
        ItemBuilder backBuilder = new ItemBuilder(parseMaterial(navBack.getMaterial(), Material.ARROW))
                .rawName(navBack.getName());
        if (navBack.getPotionType() != null) {
            try {
                backBuilder.potionType(PotionType.valueOf(navBack.getPotionType()));
            } catch (IllegalArgumentException ignored) {}
        }
        inv.setItem(BACK_BUTTON_SLOT, backBuilder.build());

        // Hourly items
        List<Integer> itemSlots = configManager.getHourlyShopItemSlots();
        Map<Integer, HourlyItem> slotMapping = new HashMap<>();

        int totalWeight = manager.getItemPool().stream().mapToInt(HourlyItem::getWeight).sum();
        double rareThreshold = configManager.getHourlyRareThresholdPercent();
        String currencySymbol = configManager.getCurrencySymbol();

        if (currentItems.isEmpty()) {
            // Show an informational placeholder in the centre slot
            inv.setItem(13, new ItemBuilder(Material.BARRIER)
                    .rawName("<red>ɴᴏ ɪᴛᴇᴍs ᴀᴠᴀɪʟᴀʙʟᴇ")
                    .rawLore(List.of("", "<gray>ᴘʟᴇᴀsᴇ ᴄʜᴇᴄᴋ ʜᴏᴜʀʟʏ-ɪᴛᴇᴍs.ʏᴍʟ"))
                    .build());
        }

        for (int i = 0; i < currentItems.size() && i < itemSlots.size(); i++) {
            HourlyItem hourlyItem = currentItems.get(i);
            int slot = itemSlots.get(i);
            if (slot < 0 || slot >= GUI_SIZE) continue;

            boolean limitReached = manager.isAtPurchaseLimit(player.getUniqueId(), hourlyItem);

            if (limitReached) {
                // Show a barrier to indicate the purchase limit has been reached
                List<String> barrierLore = new ArrayList<>();
                barrierLore.add("");
                barrierLore.add("<red>ᴘᴜʀᴄʜᴀsᴇ ʟɪᴍɪᴛ ʀᴇᴀᴄʜᴇᴅ");
                barrierLore.add("<gray>ʏᴏᴜ ʜᴀᴠᴇ ʙᴏᴜɢʜᴛ " + hourlyItem.getPurchaseLimit()
                        + "/" + hourlyItem.getPurchaseLimit() + " ᴏꜰ ᴛʜɪs ɪᴛᴇᴍ.");
                inv.setItem(slot, buildItemStack(Material.BARRIER, hourlyItem.getName(), barrierLore));
                slotMapping.put(slot, hourlyItem);
                continue;
            }

            Material mat = parseMaterial(hourlyItem.getMaterial(), Material.STONE);

            // Build lore
            List<String> loreLines = new ArrayList<>(hourlyItem.getLore());
            loreLines.add("");
            if (hourlyItem.getCost() > 0) {
                loreLines.add("<gray>ᴄᴏsᴛ: <green>" + currencySymbol + NumberFormatter.format(hourlyItem.getCost()));
            } else {
                loreLines.add("<gray>ᴄᴏsᴛ: <green>FREE");
            }
            // Purchase limit badge
            if (hourlyItem.getPurchaseLimit() > 0) {
                int bought = manager.getPurchaseCount(player.getUniqueId(), hourlyItem.getId());
                int remaining = hourlyItem.getPurchaseLimit() - bought;
                loreLines.add("<gray>ʀᴇᴍᴀɪɴɪɴɢ: <yellow>" + remaining + "<gray>/" + hourlyItem.getPurchaseLimit());
            }
            // Rare badge
            if (totalWeight > 0 && 100.0 * hourlyItem.getWeight() / totalWeight <= rareThreshold) {
                loreLines.add("<light_purple><bold>★ RARE</bold></light_purple>");
            }
            loreLines.add("");
            loreLines.add("<yellow>ᴄʟɪᴄᴋ ᴛᴏ ʙᴜʏ!");

            inv.setItem(slot, buildItemStack(mat, hourlyItem.getName(), loreLines));
            slotMapping.put(slot, hourlyItem);
        }

        playerSlotMappings.put(player.getUniqueId(), slotMapping);
        if (playOpenSound) {
            playSound(player, configManager.getSoundOpenMenu());
        }
        player.openInventory(inv);
    }

    // ── Click handling ─────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HourlyShopGUI)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        if (slot == BACK_BUTTON_SLOT) {
            playSound(player, configManager.getSoundNavigate());
            playerSlotMappings.remove(player.getUniqueId());
            plugin.getShopGUI().open(player);
            return;
        }

        Map<Integer, HourlyItem> slotMapping = playerSlotMappings.get(player.getUniqueId());
        if (slotMapping == null) return;

        HourlyItem hourlyItem = slotMapping.get(slot);
        if (hourlyItem == null) return;

        handlePurchase(player, hourlyItem);
    }

    // ── Purchase logic ─────────────────────────────────────────

    private void handlePurchase(Player player, HourlyItem hourlyItem) {
        EconomyManager economy = plugin.getEconomyManager();
        if (economy == null || !economy.isReady()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Economy is not available!"));
            return;
        }

        HourlyItemManager manager = plugin.getHourlyItemManager();

        // Check purchase limit
        if (manager.isAtPurchaseLimit(player.getUniqueId(), hourlyItem)) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>ʏᴏᴜ ʜᴀᴠᴇ ʀᴇᴀᴄʜᴇᴅ ᴛʜᴇ ᴘᴜʀᴄʜᴀsᴇ ʟɪᴍɪᴛ ꜰᴏʀ ᴛʜɪs ɪᴛᴇᴍ!"));
            return;
        }

        String currencySymbol = configManager.getCurrencySymbol();

        if (!hourlyItem.isCommandItem()) {
            // Check inventory space BEFORE charging, to avoid refund flow
            Material mat = parseMaterial(hourlyItem.getMaterial(), null);
            if (mat == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid item configuration!"));
                return;
            }

            ItemStack itemStack = new ItemStack(mat, 1);
            if (!hasInventorySpace(player, itemStack)) {
                playSound(player, configManager.getSoundError());
                String msg = configManager.getMessage("inventory-full");
                if (msg.isEmpty()) msg = "<red>ʏᴏᴜʀ ɪɴᴠᴇɴᴛᴏʀʏ ɪs ғᴜʟʟ!";
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                return;
            }

            if (!chargePlayer(player, economy, hourlyItem)) return;

            player.getInventory().addItem(itemStack);
            manager.incrementPurchaseCount(player.getUniqueId(), hourlyItem.getId());

            playSound(player, configManager.getSoundBuy());
            String itemName = mat.name().replace('_', ' ').toLowerCase();
            String msg = configManager.getMessage("buy-success")
                    .replace("{amount}", "1")
                    .replace("{item}", itemName)
                    .replace("{price}", currencySymbol + NumberFormatter.format(hourlyItem.getCost()));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));

        } else {
            if (!chargePlayer(player, economy, hourlyItem)) return;

            // Dispatch each command from console with player placeholder
            for (String cmd : hourlyItem.getCommands()) {
                String processed = cmd
                        .replace("{player}", player.getName())
                        .replace("%player%", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processed);
            }
            manager.incrementPurchaseCount(player.getUniqueId(), hourlyItem.getId());
            playSound(player, configManager.getSoundBuy());
            String itemName = stripColors(hourlyItem.getName());
            String msg = configManager.getMessage("buy-success")
                    .replace("{amount}", "1")
                    .replace("{item}", itemName)
                    .replace("{price}", currencySymbol + NumberFormatter.format(hourlyItem.getCost()));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }

        // Refresh the GUI to reflect updated purchase counts / limit state
        refreshForPlayer(player);
    }

    /**
     * Validates that the player has enough funds and withdraws the cost.
     * Returns true if the charge succeeded (or item is free); false if it failed
     * (in which case an error message and sound are already sent to the player).
     */
    private boolean chargePlayer(Player player, EconomyManager economy, HourlyItem hourlyItem) {
        if (hourlyItem.getCost() <= 0) return true;
        if (!economy.has(player, hourlyItem.getCost())) {
            playSound(player, configManager.getSoundError());
            String msg = configManager.getMessage("not-enough-money");
            if (msg.isEmpty()) msg = "<red>ʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴇɴᴏᴜɢʜ ᴍᴏɴᴇʏ!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return false;
        }
        if (!economy.withdraw(player, hourlyItem.getCost())) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed!"));
            return false;
        }
        return true;
    }

    /**
     * Returns true if the player's inventory can accept at least one of the given item
     * (either via an empty slot or by stacking onto a partial stack).
     */
    private boolean hasInventorySpace(Player player, ItemStack item) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                return true;
            }
            if (stack.isSimilar(item) && stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Build an ItemStack with a display name that supports both legacy &-codes
     * and MiniMessage format, and a MiniMessage lore.
     */
    private ItemStack buildItemStack(Material mat, String rawName, List<String> loreLines) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        // Display name: detect legacy &-codes and convert; otherwise use MiniMessage
        Component nameComponent;
        if (rawName != null && rawName.contains("&")) {
            nameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
                    .decoration(TextDecoration.ITALIC, false);
        } else {
            nameComponent = MiniMessage.miniMessage().deserialize("<!italic>" + (rawName != null ? rawName : ""));
        }
        meta.displayName(nameComponent);

        // Lore via MiniMessage
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(MiniMessage.miniMessage().deserialize("<!italic>" + line));
        }
        meta.lore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String stripColors(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-orA-FK-Or]", "")
                   .replaceAll("<[^>]+>", "");
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used directly
    }
}
