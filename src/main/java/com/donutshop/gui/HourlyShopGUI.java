package com.donutshop.gui;

import com.donutshop.DonutShop;
import com.donutshop.config.ConfigManager;
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
                int bought = manager.getPurchaseCount(player.getUniqueId(), hourlyItem.getId());
                List<String> barrierLore = new ArrayList<>();
                barrierLore.add("");
                barrierLore.add("<red>ᴘᴜʀᴄʜᴀsᴇ ʟɪᴍɪᴛ ʀᴇᴀᴄʜᴇᴅ");
                barrierLore.add("<gray>ʙᴏᴜɢʜᴛ: <red>" + bought
                        + "<gray>/" + hourlyItem.getPurchaseLimit());
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
                loreLines.add("<gray>ʙᴏᴜɢʜᴛ: <yellow>" + bought + "<gray>/" + hourlyItem.getPurchaseLimit());
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
        playSound(player, configManager.getSoundOpenMenu());
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

        // If the item limit is already reached, do nothing (it shows as a barrier)
        if (plugin.getHourlyItemManager().isAtPurchaseLimit(player.getUniqueId(), hourlyItem)) {
            playSound(player, configManager.getSoundError());
            return;
        }

        // Open the multi-buy confirmation GUI
        playSound(player, configManager.getSoundNavigate());
        plugin.getHourlyConfirmationGUI().open(player, hourlyItem);
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

    @Override
    public Inventory getInventory() {
        return null; // Not used directly
    }
}
