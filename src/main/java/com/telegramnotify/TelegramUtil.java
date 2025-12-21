package com.telegramnotify;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TelegramUtil {

    public static void sendMessage(String token, String chatId, String message) {
        try {
            String urlStr = "https://api.telegram.org/bot" + token +
                    "/sendMessage?chat_id=" + chatId +
                    "&parse_mode=Markdown&text=" +
                    URLEncoder.encode(message, "UTF-8");

            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.getInputStream().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
