package com.telegramnotifications;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

public final class TelegramUtil {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private TelegramUtil() {
    }

    /**
     * Envía un mensaje a Telegram de forma síncrona.
     * Debe ser llamado desde un hilo asíncrono.
     */
    public static void sendMessage(Logger logger, String token, String chatId, String message) {
        sendMessageWithRetries(logger, token, chatId, message, 0);
    }

    private static void sendMessageWithRetries(Logger logger, String token, String chatId, String message, int attempt) {
        try {
            String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&parse_mode=Markdown"
                    + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                logger.fine("Mensaje Telegram enviado exitosamente");
            } else if (status == 404 || status == 401) {
                logger.severe("Token o Chat ID inválido (Error " + status + "). Verifica el config.yml.");
            } else if (status == 429) {
                logger.warning("Rate limit de Telegram alcanzado (429). Reintentando...");
                retryWithBackoff(logger, token, chatId, message, attempt);
            } else if (status >= 400 && status < 500) {
                logger.warning("Error de cliente Telegram: " + status + ". Revisa el formato del mensaje.");
            } else {
                logger.warning("Error de servidor Telegram: " + status);
                retryWithBackoff(logger, token, chatId, message, attempt);
            }
        } catch (java.net.http.HttpTimeoutException e) {
            logger.warning("Timeout conectando a Telegram (intento " + (attempt + 1) + "/" + MAX_RETRIES + ")");
            retryWithBackoff(logger, token, chatId, message, attempt);
        } catch (Exception e) {
            logger.warning("Error enviando mensaje Telegram: " + e.getMessage());
            retryWithBackoff(logger, token, chatId, message, attempt);
        }
    }

    private static void retryWithBackoff(Logger logger, String token, String chatId, String message, int attempt) {
        if (attempt >= MAX_RETRIES) {
            logger.severe("Mensaje descartado después de " + MAX_RETRIES + " reintentos");
            return;
        }

        long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        sendMessageWithRetries(logger, token, chatId, message, attempt + 1);
    }

    /**
     * Escapa caracteres especiales para Markdown de Telegram (V1).
     */
    public static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("`", "\\` ");
    }
}
