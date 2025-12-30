package com.telegramnotifications;

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

        // Registrar comando siempre para permitir recarga si la config inicial es inválida
        PluginCommand command = getCommand("telereload");
        if (command != null) {
            command.setExecutor(new ReloadCommand(this));
        } else {
            getLogger().severe("El comando telereload no está definido en plugin.yml");
        }

        if (!configSnapshot.isValid()) {
            getLogger().severe("==============================================");
            getLogger().severe("Token o Chat ID no configurados");
            getLogger().severe("Por favor, configura el archivo config.yml");
            getLogger().severe("Y usa /telereload para aplicar los cambios");
            getLogger().severe("==============================================");
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        startWorker();
        getLogger().info("TelegramNotifications activado");
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

        String playerName = TelegramUtil.escapeMarkdown(e.getPlayer().getName());
        String msg = cfg.joinMessage().replace("{player}", playerName);
        sendAsync(msg, cfg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid() || !cfg.notifyQuit()) return;

        String playerName = TelegramUtil.escapeMarkdown(e.getPlayer().getName());
        String msg = cfg.quitMessage().replace("{player}", playerName);
        sendAsync(msg, cfg);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid() || !cfg.notifyDeath()) return;

        String playerName = TelegramUtil.escapeMarkdown(e.getEntity().getName());
        String deathCause = playerName + " murió";
        if (e.deathMessage() != null) {
            deathCause = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(e.deathMessage());
            // No escapamos deathCause porque puede contener el nombre ya escapado o ser un mensaje complejo
            // Pero el nombre del jugador en el mensaje de muerte de Bukkit suele ser el nombre real.
            // Para ser seguros, podríamos intentar escapar el mensaje de muerte completo, 
            // pero eso podría romper si Bukkit ya pone algo que parece markdown.
            // Sin embargo, PlainTextComponentSerializer quita colores pero no escapa markdown.
            deathCause = TelegramUtil.escapeMarkdown(deathCause);
        }

        String location = String.format("%d, %d, %d", 
            e.getEntity().getLocation().getBlockX(),
            e.getEntity().getLocation().getBlockY(),
            e.getEntity().getLocation().getBlockZ());
        String world = e.getEntity().getWorld().getName();

        String msg = cfg.deathMessage()
            .replace("{player}", playerName)
            .replace("{death_message}", deathCause)
            .replace("{location}", location)
            .replace("{world}", world)
            .replace("{x}", String.valueOf(e.getEntity().getLocation().getBlockX()))
            .replace("{y}", String.valueOf(e.getEntity().getLocation().getBlockY()))
            .replace("{z}", String.valueOf(e.getEntity().getLocation().getBlockZ()));
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

        // Procesa lo pendiente de forma rápida antes de cerrar
        if (!sendQueue.isEmpty()) {
            getLogger().info("Enviando mensajes pendientes (" + sendQueue.size() + ")...");
            // Limitamos a 5 mensajes para no bloquear el apagado del servidor demasiado tiempo
            for (int i = 0; i < 5; i++) {
                String msg = sendQueue.poll();
                if (msg == null) break;
                TelegramUtil.sendMessage(getLogger(), configSnapshot.token(), configSnapshot.chatId(), msg);
            }
        }
    }

    private void drainQueue() {
        ConfigSnapshot cfg = configSnapshot;
        if (!cfg.isValid()) return;

        // Procesar 1 mensaje por tick (máximo 20/seg) para respetar límites de Telegram
        String msg = sendQueue.poll();
        if (msg != null) {
            TelegramUtil.sendMessage(getLogger(), cfg.token(), cfg.chatId(), msg);
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
