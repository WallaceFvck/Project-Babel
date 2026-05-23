package com.projectbabel.translation.engine;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.TranslationExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Engine Lingva — proxy do Google Translate, sem API key.
 * Usa InstanceListManager para lista dinâmica de instâncias (atualizada via Gist).
 */
public class LingvaEngine {

    private final AtomicInteger consecutiveFails = new AtomicInteger(0);
    private final HttpClient    httpClient;

    public LingvaEngine() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(TranslationExecutors.network())
            .build();
    }

    public CompletableFuture<String> translate(String text, String src, String tgt) {
        return tryInstance(text, src, tgt, 0);
    }

    private CompletableFuture<String> tryInstance(String text, String src, String tgt, int attempt) {
        List<String> instances = InstanceListManager.getLingvaInstances();
        if (attempt >= instances.size()) {
            consecutiveFails.incrementAndGet();
            return CompletableFuture.failedFuture(
                new RuntimeException("Lingva: todas as " + instances.size() + " instâncias falharam"));
        }

        String base    = instances.get(attempt);
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        String url     = base + "/api/v1/" + src + "/" + tgt + "/" + encoded;

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .timeout(Duration.ofMillis(AutoTranslateConfig.getRequestTimeoutMs()))
            .GET().build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .handle((resp, ex) -> {
                if (ex != null || resp == null || resp.statusCode() != 200) return null;
                try {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!json.has("translation")) return null;
                    String result = json.get("translation").getAsString();
                    if (result == null || result.isBlank()) return null;
                    consecutiveFails.set(0);
                    return result;
                } catch (Exception e) { return null; }
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                return tryInstance(text, src, tgt, attempt + 1);
            });
    }

    public void resetFailures()  { consecutiveFails.set(0); }
}
