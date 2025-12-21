package com.telegramnotify;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private String token;
    private String chatId;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        token = getConfig().getString("telegram.token");
        chatId = getConfig().getString("telegram.chat_id");

        if (token == null || chatId == null) {
            getLogger().severe("Token o Chat ID no configurados");
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TelegramNotify activado");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("notifications.join", true)) return;
        
        String template = getConfig().getString("messages.join", "✅ *{player}* entró al servidor");
        String msg = template.replace("{player}", e.getPlayer().getName());
        sendAsync(msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!getConfig().getBoolean("notifications.quit", true)) return;
        
        String template = getConfig().getString("messages.quit", "❌ *{player}* salió del servidor");
        String msg = template.replace("{player}", e.getPlayer().getName());
        sendAsync(msg);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!getConfig().getBoolean("notifications.death", true)) return;
        
        String template = getConfig().getString("messages.death", "💀 *{player}* murió");
        String msg = template.replace("{player}", e.getPlayer().getName());
        sendAsync(msg);
    }

    private void sendAsync(String msg) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            TelegramUtil.sendMessage(token, chatId, msg);
        });
    }
}
