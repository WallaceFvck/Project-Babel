package com.projectbabel.translation.engine;

import com.projectbabel.ProjectBabelMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve hostnames via DNS over HTTPS (DoH).
 * Contorna bloqueios de DNS de ISPs sem precisar alterar configurações do sistema.
 * Implementação adaptada de anoubios/ftb-quest-translator.
 */
public class DoHResolver {

    private static final String CLOUDFLARE = "https://cloudflare-dns.com/dns-query";
    private static final String GOOGLE_DOH = "https://dns.google/resolve";
    private static final long   TTL_MS     = 10 * 60 * 1000L; // 10 minutos

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String, CachedEntry> CACHE = new ConcurrentHashMap<>();

    /** Resolve um hostname para IP. Retorna null se falhar. */
    public static String resolve(String hostname) {
        CachedEntry cached = CACHE.get(hostname);
        if (cached != null && !cached.expired()) return cached.ip;

        String ip = query(CLOUDFLARE, hostname);
        if (ip == null) ip = query(GOOGLE_DOH, hostname);
        if (ip != null) CACHE.put(hostname, new CachedEntry(ip));
        return ip;
    }

    public static void clearCache() { CACHE.clear(); }

    private static String query(String server, String hostname) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server + "?name=" + hostname + "&type=A"))
                .header("Accept", "application/dns-json")
                .timeout(Duration.ofSeconds(4))
                .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.has("Answer")) {
                    for (JsonElement el : json.getAsJsonArray("Answer")) {
                        JsonObject ans = el.getAsJsonObject();
                        if (ans.has("type") && ans.get("type").getAsInt() == 1) {
                            return ans.get("data").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.debug("[projectbabel] DoH {}: {}", server.contains("cloudflare") ? "CF" : "Google", e.getMessage());
        }
        return null;
    }

    private record CachedEntry(String ip, long ts) {
        CachedEntry(String ip) { this(ip, System.currentTimeMillis()); }
        boolean expired() { return System.currentTimeMillis() - ts > TTL_MS; }
    }
}
