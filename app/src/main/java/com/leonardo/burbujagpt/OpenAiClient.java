package com.leonardo.burbujagpt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenAiClient {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-5.1-mini";

    public static String ask(String token, String prompt) throws Exception {
        URL url = new URL(ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("input", prompt);
        body.put("instructions", "Responde en español, claro y directo. Si falta contexto, dilo sin inventar.");
        body.put("max_output_tokens", 700);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String raw = readStream(stream);
        conn.disconnect();

        if (code < 200 || code >= 300) return parseError(raw, code);
        return parseText(raw);
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private static String parseError(String raw, int code) {
        try {
            JSONObject obj = new JSONObject(raw);
            if (obj.has("error")) {
                JSONObject err = obj.getJSONObject("error");
                return "Error API " + code + ": " + err.optString("message", raw);
            }
        } catch (Exception ignored) {}
        return "Error API " + code + ": " + raw;
    }

    private static String parseText(String raw) throws Exception {
        JSONObject obj = new JSONObject(raw);
        if (obj.has("output_text")) {
            String direct = obj.optString("output_text", "").trim();
            if (!direct.isEmpty()) return direct;
        }

        StringBuilder out = new StringBuilder();
        JSONArray output = obj.optJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) continue;
                    String text = part.optString("text", "");
                    String refusal = part.optString("refusal", "");
                    if (!text.isEmpty()) out.append(text).append('\n');
                    if (!refusal.isEmpty()) out.append(refusal).append('\n');
                }
            }
        }

        String parsed = out.toString().trim();
        return parsed.isEmpty() ? raw : parsed;
    }
}
