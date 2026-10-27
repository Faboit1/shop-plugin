package com.donutshop.commands;

import com.donutshop.economy.EconomyManager;
import com.donutshop.halloween.HalloweenEvent;
import com.donutshop.util.NumberFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HalloweenCommand implements CommandExecutor {

    private final HalloweenEvent event;

    public HalloweenCommand(HalloweenEvent event) {
        this.event = event;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MiniMessage mm = MiniMessage.miniMessage();
        if (!event.isEnabled()) {
            sender.sendMessage(mm.deserialize("<gray>The Halloween event is not running right now."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command."));
            return true;
        }

        EconomyManager econ = event.getEconomy();
        String symbol = event.getCurrencySymbol();
        if (econ != null) {
            player.sendMessage(mm.deserialize("<#FF7518>" + symbol + " <white>You have <#FF7518>"
                    + NumberFormatter.format(econ.getBalance(player)) + " " + symbol
                    + " <gray>· next one in <white>" + event.formatCountdown(player)));
        }
        event.showDialog(player);
        return true;
    }
}
