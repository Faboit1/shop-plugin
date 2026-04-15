package com.donutshop.commands;

import com.donutshop.DonutShop;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final DonutShop plugin;

    public ShopCommand(DonutShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("donutshop.reload")) {
                String msg = plugin.getConfigManager().getMessage("no-permission");
                sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                return true;
            }
            plugin.reload();
            String msg = plugin.getConfigManager().getMessage("shop-reloaded");
            sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>ᴛʜɪs ᴄᴏᴍᴍᴀɴᴅ ᴄᴀɴ ᴏɴʟʏ ʙᴇ ᴜsᴇᴅ ʙʏ ᴘʟᴀʏᴇʀs!"));
            return true;
        }
        
        if (!player.hasPermission("donutshop.use")) {
            String msg = plugin.getConfigManager().getMessage("no-permission");
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return true;
        }
        
        // If a category argument is provided, open that category directly
        if (args.length > 0) {
            String categoryId = args[0].toLowerCase();
            var cat = plugin.getConfigManager().getCategory(categoryId);
            if (cat != null) {
                var categoryGUI = new com.donutshop.gui.CategoryGUI(plugin, plugin.getConfigManager());
                categoryGUI.open(player, 0, cat);
                return true;
            }
        }
        
        // Open main shop menu
        plugin.getShopGUI().open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("reload".startsWith(partial) && sender.hasPermission("donutshop.reload")) {
                completions.add("reload");
            }
            // Add category names
            for (String catId : plugin.getConfigManager().getCategories().keySet()) {
                if (catId.toLowerCase().startsWith(partial)) {
                    completions.add(catId);
                }
            }
        }
        return completions;
    }
}
