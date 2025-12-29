package com.telegramnotify;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final Main plugin;

    public ReloadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("telegramnotify.reload")) {
            sender.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        plugin.reloadConfig();
        plugin.loadConfigValues();
        sender.sendMessage("§a✓ Configuración de TelegramNotify recargada.");
        return true;
    }
}
