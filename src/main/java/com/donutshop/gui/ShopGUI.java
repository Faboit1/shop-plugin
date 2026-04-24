package com.donutshop.gui;

import com.donutshop.DonutShop;
import com.donutshop.config.ConfigManager;
import com.donutshop.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.Map;

public class ShopGUI implements InventoryHolder, Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public ShopGUI(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void open(Player player) {
        int size = configManager.getMainMenuSize();
        String title = configManager.getMainMenuTitle();

        Inventory inv = Bukkit.createInventory(this, size, MiniMessage.miniMessage().deserialize(title));

        // Fill with filler (black glass panes)
        if (configManager.isMainMenuFillerEnabled()) {
            Material fillerMat = Material.valueOf(configManager.getMainMenuFillerMaterial());
            String fillerName = configManager.getMainMenuFillerName();
            ItemStack filler = new ItemBuilder(fillerMat).rawName(fillerName).build();
            for (int i = 0; i < size; i++) {
                inv.setItem(i, filler);
            }
        }

        // Place category icons
        Map<String, ConfigManager.CategoryConfig> categories = configManager.getCategories();
        for (Map.Entry<String, ConfigManager.CategoryConfig> entry : categories.entrySet()) {
            ConfigManager.CategoryConfig cat = entry.getValue();
            Material iconMat = Material.valueOf(cat.getIconMaterial());
            ItemBuilder builder = new ItemBuilder(iconMat).rawName(cat.getIconName());
            if (cat.getIconLore() != null && !cat.getIconLore().isEmpty()) {
                builder.rawLore(cat.getIconLore());
            }
            if (cat.isIconGlow()) {
                builder.glow();
            }
            if (cat.getIconCustomModelData() > 0) {
                builder.customModelData(cat.getIconCustomModelData());
            }
            if (cat.getSlot() >= 0 && cat.getSlot() < size) {
                inv.setItem(cat.getSlot(), builder.build());
            }
        }

        // Place the hourly shop button (placed after categories so it is never overwritten)
        if (configManager.isHourlyShopEnabled()) {
            int featuredSlot = configManager.getHourlyShopFeaturedSlot();
            if (featuredSlot >= 0 && featuredSlot < size) {
                Material buttonMat;
                try {
                    buttonMat = Material.valueOf(configManager.getHourlyShopButtonMaterial());
                } catch (IllegalArgumentException ignored) {
                    buttonMat = Material.CLOCK;
                }
                ItemBuilder buttonBuilder = new ItemBuilder(buttonMat)
                        .rawName(configManager.getHourlyShopButtonName());
                List<String> buttonLore = configManager.getHourlyShopButtonLore();
                if (buttonLore != null && !buttonLore.isEmpty()) {
                    buttonBuilder.rawLore(buttonLore);
                }
                if (configManager.isHourlyShopButtonGlow()) {
                    buttonBuilder.glow();
                }
                inv.setItem(featuredSlot, buttonBuilder.build());
            }
        }

        player.openInventory(inv);
        playSound(player, configManager.getSoundOpenMenu());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGUI)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        // Route clicks on the hourly shop button
        if (configManager.isHourlyShopEnabled() && slot == configManager.getHourlyShopFeaturedSlot()) {
            ((DonutShop) plugin).getHourlyShopGUI().open(player);
            return;
        }

        // Check if clicked slot matches a category
        Map<String, ConfigManager.CategoryConfig> categories = configManager.getCategories();
        for (Map.Entry<String, ConfigManager.CategoryConfig> entry : categories.entrySet()) {
            ConfigManager.CategoryConfig cat = entry.getValue();
            if (cat.getSlot() == slot) {
                playSound(player, configManager.getSoundNavigate());
                // Open category GUI
                CategoryGUI categoryGUI = new CategoryGUI(plugin, configManager);
                categoryGUI.open(player, 0, cat);
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used directly
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }
}
