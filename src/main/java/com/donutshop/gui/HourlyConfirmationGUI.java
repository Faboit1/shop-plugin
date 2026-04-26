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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-buy confirmation GUI for hourly shop items.
 * Mirrors the layout of {@link ConfirmationGUI} but is aware of per-item purchase limits.
 */
public class HourlyConfirmationGUI implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 27;

    /** Maximum quantity selectable per purchase (mirrors the single-stack cap in ConfirmationGUI). */
    private static final int MAX_BULK_AMOUNT = 64;

    private static final int SLOT_REMOVE_64 = 9;
    private static final int SLOT_REMOVE_10 = 10;
    private static final int SLOT_REMOVE_1  = 11;
    private static final int SLOT_ITEM      = 13;
    private static final int SLOT_ADD_1     = 15;
    private static final int SLOT_ADD_10    = 16;
    private static final int SLOT_ADD_64    = 17;
    private static final int SLOT_BACK      = 18;
    private static final int SLOT_COST_INFO = 22;
    private static final int SLOT_CONFIRM   = 26;

    private static final Map<UUID, HourlyConfirmData> playerData = new ConcurrentHashMap<>();

    private final DonutShop plugin;
    private final ConfigManager configManager;

    public HourlyConfirmationGUI(DonutShop plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    // ── Open ──────────────────────────────────────────────────

    public void open(Player player, HourlyItem hourlyItem) {
        UUID uuid = player.getUniqueId();
        HourlyConfirmData data = playerData.get(uuid);
        if (data == null || !data.hourlyItem.getId().equals(hourlyItem.getId())) {
            data = new HourlyConfirmData(hourlyItem, 1);
        }
        playerData.put(uuid, data);
        refreshInventory(player, data);
    }

    // ── Inventory builder ──────────────────────────────────────

    private void refreshInventory(Player player, HourlyConfirmData data) {
        HourlyItem item = data.hourlyItem;

        // Clamp amount to the remaining purchase allowance
        int max = maxAmount(player, item);
        if (max <= 0) {
            // Limit already reached — bounce back to the hourly shop
            playerData.remove(player.getUniqueId());
            plugin.getHourlyShopGUI().open(player);
            return;
        }
        data.amount = Math.max(1, Math.min(data.amount, max));

        String itemDisplayName = stripColors(item.getName());
        String title = "<white>ʙᴜʏ " + itemDisplayName;
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE,
                MiniMessage.miniMessage().deserialize(title));

        // Filler
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

        int amount = data.amount;
        Material mat = parseMaterial(item.getMaterial(), Material.STONE);

        // Centre item display
        List<String> centerLore = new ArrayList<>();
        centerLore.add("");
        centerLore.add("<gray>ᴀᴍᴏᴜɴᴛ: <white>" + amount);
        if (item.getPurchaseLimit() > 0) {
            int bought = plugin.getHourlyItemManager().getPurchaseCount(player.getUniqueId(), item.getId());
            centerLore.add("<gray>ʙᴏᴜɢʜᴛ: <yellow>" + bought + "<gray>/" + item.getPurchaseLimit());
        }
        inv.setItem(SLOT_ITEM, buildItemStack(mat, item.getName(), centerLore, amount));

        // Amount buttons
        inv.setItem(SLOT_REMOVE_64, buildButton(configManager.getConfirmDecrease64()));
        inv.setItem(SLOT_REMOVE_10, buildButton(configManager.getConfirmDecrease10()));
        inv.setItem(SLOT_REMOVE_1,  buildButton(configManager.getConfirmDecrease1()));
        inv.setItem(SLOT_ADD_1,     buildButton(configManager.getConfirmIncrease1()));
        inv.setItem(SLOT_ADD_10,    buildButton(configManager.getConfirmIncrease10()));
        inv.setItem(SLOT_ADD_64,    buildButton(configManager.getConfirmIncrease64()));

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

        // Cost info
        String currencySymbol = configManager.getCurrencySymbol();
        double unitCost = item.getCost();
        double totalCost = unitCost > 0 ? unitCost * amount : 0;
        ConfigManager.ButtonConfig costInfoBtn = configManager.getConfirmCostInfo();
        List<String> costLore = new ArrayList<>();
        costLore.add("");
        costLore.add("<italic><gray>ᴄᴏsᴛ: <green>"
                + (unitCost > 0 ? currencySymbol + NumberFormatter.format(totalCost) : "FREE")
                + "</italic>");
        inv.setItem(SLOT_COST_INFO, new ItemBuilder(parseMaterial(costInfoBtn.getMaterial(), Material.PAPER))
                .rawName(costInfoBtn.getName())
                .rawLore(costLore)
                .build());

        // Confirm button
        ConfigManager.ButtonConfig confirmBtn = configManager.getConfirmConfirmBtn();
        inv.setItem(SLOT_CONFIRM, new ItemBuilder(parseMaterial(confirmBtn.getMaterial(), Material.LIME_STAINED_GLASS_PANE))
                .rawName(confirmBtn.getName())
                .rawLore(List.of("", "<gray>ᴄʟɪᴄᴋ ᴛᴏ ʙᴜʏ <white>" + amount + "x " + itemDisplayName))
                .build());

        player.openInventory(inv);
    }

    // ── Click handling ─────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HourlyConfirmationGUI)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        HourlyConfirmData data = playerData.get(uuid);
        if (data == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        int max = maxAmount(player, data.hourlyItem);

        switch (slot) {
            case SLOT_REMOVE_64 -> {
                data.amount = Math.max(1, data.amount - MAX_BULK_AMOUNT);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
            }
            case SLOT_REMOVE_10 -> {
                data.amount = Math.max(1, data.amount - 10);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
            }
            case SLOT_REMOVE_1 -> {
                data.amount = Math.max(1, data.amount - 1);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
            }
            case SLOT_ADD_1 -> {
                data.amount = Math.min(max, data.amount + 1);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
            }
            case SLOT_ADD_10 -> {
                data.amount = Math.min(max, data.amount + 10);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
            }
            case SLOT_ADD_64 -> {
                data.amount = Math.min(max, data.amount + MAX_BULK_AMOUNT);
                playSound(player, configManager.getSoundNavigate());
                refreshInventory(player, data);
            }
            case SLOT_BACK -> {
                playSound(player, configManager.getSoundNavigate());
                playerData.remove(uuid);
                plugin.getHourlyShopGUI().open(player);
            }
            case SLOT_CONFIRM -> handleConfirmPurchase(player, data);
        }
    }

    // ── Purchase logic ─────────────────────────────────────────

    private void handleConfirmPurchase(Player player, HourlyConfirmData data) {
        EconomyManager economy = plugin.getEconomyManager();
        if (economy == null || !economy.isReady()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Economy is not available!"));
            return;
        }

        HourlyItemManager manager = plugin.getHourlyItemManager();
        HourlyItem item = data.hourlyItem;

        // Re-check the remaining purchase allowance and clamp the requested amount
        int max = maxAmount(player, item);
        if (max <= 0) {
            playSound(player, configManager.getSoundError());
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>ʏᴏᴜ ʜᴀᴠᴇ ʀᴇᴀᴄʜᴇᴅ ᴛʜᴇ ᴘᴜʀᴄʜᴀsᴇ ʟɪᴍɪᴛ ꜰᴏʀ ᴛʜɪs ɪᴛᴇᴍ!"));
            playerData.remove(player.getUniqueId());
            plugin.getHourlyShopGUI().open(player);
            return;
        }
        int amount = Math.min(data.amount, max);

        String currencySymbol = configManager.getCurrencySymbol();
        double unitCost = item.getCost();
        double totalCost = unitCost > 0 ? unitCost * amount : 0;

        // Check funds
        if (totalCost > 0 && !economy.has(player, totalCost)) {
            playSound(player, configManager.getSoundError());
            String msg = configManager.getMessage("not-enough-money");
            if (msg.isEmpty()) msg = "<red>ʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴇɴᴏᴜɢʜ ᴍᴏɴᴇʏ!";
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        if (!item.isCommandItem()) {
            // ── Material item ──────────────────────────────
            Material mat = parseMaterial(item.getMaterial(), null);
            if (mat == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid item configuration!"));
                return;
            }

            if (totalCost > 0 && !economy.withdraw(player, totalCost)) {
                playSound(player, configManager.getSoundError());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed!"));
                return;
            }

            ItemStack itemStack = new ItemStack(mat, amount);
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);

            int given = amount;
            if (!leftover.isEmpty()) {
                int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                if (unitCost > 0) {
                    double refund = unitCost * notAdded;
                    economy.deposit(player, refund);
                    totalCost -= refund;
                }
                given -= notAdded;

                if (given <= 0) {
                    playSound(player, configManager.getSoundError());
                    String msg = configManager.getMessage("inventory-full");
                    if (msg.isEmpty()) msg = "<red>ʏᴏᴜʀ ɪɴᴠᴇɴᴛᴏʀʏ ɪs ꜰᴜʟʟ!";
                    player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                    return;
                }
            }

            manager.addPurchaseCount(player.getUniqueId(), item.getId(), given);
            playSound(player, configManager.getSoundBuy());
            String itemName = mat.name().replace('_', ' ').toLowerCase();
            String msg = configManager.getMessage("buy-success")
                    .replace("{amount}", String.valueOf(given))
                    .replace("{item}", itemName)
                    .replace("{price}", currencySymbol + NumberFormatter.format(totalCost));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));

        } else {
            // ── Command item ───────────────────────────────
            if (totalCost > 0 && !economy.withdraw(player, totalCost)) {
                playSound(player, configManager.getSoundError());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Transaction failed!"));
                return;
            }

            for (int i = 0; i < amount; i++) {
                for (String cmd : item.getCommands()) {
                    String processed = cmd
                            .replace("{player}", player.getName())
                            .replace("%player%", player.getName());
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processed);
                }
            }
            manager.addPurchaseCount(player.getUniqueId(), item.getId(), amount);
            playSound(player, configManager.getSoundBuy());
            String itemName = stripColors(item.getName());
            String msg = configManager.getMessage("buy-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", itemName)
                    .replace("{price}", currencySymbol + NumberFormatter.format(totalCost));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }

        // After purchase: if limit is now fully reached, return to hourly shop; otherwise refresh
        if (maxAmount(player, item) <= 0) {
            playerData.remove(player.getUniqueId());
            plugin.getHourlyShopGUI().open(player);
        } else {
            data.amount = 1;
            refreshInventory(player, data);
        }
    }

    // ── Close cleanup ──────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof HourlyConfirmationGUI)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof HourlyConfirmationGUI)) {
                playerData.remove(uuid);
            }
        }, 1L);
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Returns how many more of this item the player is allowed to buy.
     * Capped at {@link #MAX_BULK_AMOUNT}; returns {@link #MAX_BULK_AMOUNT} when there is no limit.
     */
    private int maxAmount(Player player, HourlyItem item) {
        int limit = item.getPurchaseLimit();
        if (limit > 0) {
            int bought = plugin.getHourlyItemManager().getPurchaseCount(player.getUniqueId(), item.getId());
            return Math.max(0, Math.min(MAX_BULK_AMOUNT, limit - bought));
        }
        return MAX_BULK_AMOUNT;
    }

    private ItemStack buildItemStack(Material mat, String rawName, List<String> loreLines, int amount) {
        // The ItemStack count is clamped to the material's max-stack-size for a valid display item;
        // the actual quantity given on purchase (handled separately) is not bound by this visual cap.
        ItemStack stack = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        Component nameComponent;
        if (rawName != null && rawName.contains("&")) {
            nameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
                    .decoration(TextDecoration.ITALIC, false);
        } else {
            nameComponent = MiniMessage.miniMessage().deserialize("<!italic>" + (rawName != null ? rawName : ""));
        }
        meta.displayName(nameComponent);

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(MiniMessage.miniMessage().deserialize("<!italic>" + line));
        }
        meta.lore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildButton(ConfigManager.ButtonConfig btn) {
        return new ItemBuilder(parseMaterial(btn.getMaterial(), Material.STONE))
                .rawName(btn.getName())
                .build();
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

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    // ── Data class ─────────────────────────────────────────────

    private static class HourlyConfirmData {
        final HourlyItem hourlyItem;
        int amount;

        HourlyConfirmData(HourlyItem hourlyItem, int amount) {
            this.hourlyItem = hourlyItem;
            this.amount = amount;
        }
    }
}
