package com.leonardo.traductorleo;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class GoogleTranslateClient {
    private static final int MAX_CACHE = 300;

    private final Map<String, String> cache = new LinkedHashMap<String, String>(MAX_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE;
        }
    };

    String translateToSpanish(String source) {
        String text = source == null ? "" : source.trim();
        if (text.isEmpty()) {
            return "";
        }

        synchronized (cache) {
            String cached = cache.get(text);
            if (cached != null) {
                return cached;
            }
        }

        HttpURLConnection connection = null;
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
            URL url = new URL(
                    "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=es&dt=t&q=" + encoded
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6500);
            connection.setReadTimeout(6500);
            connection.setRequestProperty("User-Agent", "TraductorLeo/1.0 Android");
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null || status < 200 || status >= 300) {
                return "";
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONArray root = new JSONArray(body.toString());
            JSONArray segments = root.optJSONArray(0);
            if (segments == null) {
                return "";
            }
            StringBuilder translated = new StringBuilder();
            for (int i = 0; i < segments.length(); i++) {
                JSONArray segment = segments.optJSONArray(i);
                if (segment != null) {
                    translated.append(segment.optString(0, ""));
                }
            }
            String result = translated.toString().trim();
            if (!result.isEmpty()) {
                synchronized (cache) {
                    cache.put(text, result);
                }
            }
            return result;
        } catch (Exception ignored) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
