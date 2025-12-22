package com.telegramnotify;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private static final int MAX_QUEUE_SIZE = 256;

    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private volatile ConfigSnapshot configSnapshot;
    private int workerTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        if (!configSnapshot.isValid()) {
            getLogger().severe("Token o Chat ID no configurados");
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("telereload");
        if (command == null) {
            getLogger().severe("El comando telereload no está definido en plugin.yml");
        } else {
            command.setExecutor(new ReloadCommand(this));
        }

        startWorker();
        getLogger().info("TelegramNotify activado");
    }

    @Override
    public void onDisable() {
        stopWorker();
    }
    
    public void loadConfigValues() {
        ConfigSnapshot snapshot = new ConfigSnapshot(
                getConfig().getString("telegram.token", ""),
                getConfig().getString("telegram.chat_id", ""),
                getConfig().getBoolean("notifications.join", true),
                getConfig().getBoolean("notifications.quit", true),
                getConfig().getBoolean("notifications.death", true),
                getConfig().getBoolean("notifications.server_start", true),
                getConfig().getString("messages.join", "🟢 *{player}* entró al servidor"),
                getConfig().getString("messages.quit", "🔴 *{player}* salió del servidor"),
                getConfig().getString("messages.death", "✖️ *{player}* murió"),
                getConfig().getString("messages.server_start", "🔵 Servidor iniciado")
        );

        configSnapshot = snapshot;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid() || !cfg.notifyJoin()) return;

        String msg = cfg.joinMessage().replace("{player}", e.getPlayer().getName());
        sendAsync(msg, cfg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid() || !cfg.notifyQuit()) return;

        String msg = cfg.quitMessage().replace("{player}", e.getPlayer().getName());
        sendAsync(msg, cfg);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid() || !cfg.notifyDeath()) return;

        String msg = cfg.deathMessage().replace("{player}", e.getEntity().getName());
        sendAsync(msg, cfg);
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid() || !cfg.notifyServerStart()) return;

        sendAsync(cfg.serverStartMessage(), cfg);
    }

    private void sendAsync(String msg, ConfigSnapshot cfg) {
        if (!sendQueue.offer(msg)) {
            getLogger().warning("Cola de envios llena, descartando mensaje");
        }
    }

    private void startWorker() {
        if (workerTaskId != -1) return;
        workerTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::drainQueue, 1L, 1L).getTaskId();
    }

    private void stopWorker() {
        if (workerTaskId == -1) return;
        Bukkit.getScheduler().cancelTask(workerTaskId);
        workerTaskId = -1;

        // Procesa lo pendiente sin bloquear demasiado
        for (int i = 0; i < MAX_QUEUE_SIZE; i++) {
            String msg = sendQueue.poll();
            if (msg == null) break;
            TelegramUtil.sendMessage(configSnapshot.token(), configSnapshot.chatId(), msg);
        }
    }

    private void drainQueue() {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid()) return;

        for (int i = 0; i < 5; i++) { // pequeño throttle por tick
            String msg = sendQueue.poll();
            if (msg == null) break;
            TelegramUtil.sendMessage(cfg.token(), cfg.chatId(), msg);
        }
    }

    private record ConfigSnapshot(
            String token,
            String chatId,
            boolean notifyJoin,
            boolean notifyQuit,
            boolean notifyDeath,
            boolean notifyServerStart,
            String joinMessage,
            String quitMessage,
            String deathMessage,
            String serverStartMessage
    ) {
        boolean isValid() {
            return Objects.requireNonNullElse(token, "").trim().length() > 0
                    && Objects.requireNonNullElse(chatId, "").trim().length() > 0;
        }
    }
}
