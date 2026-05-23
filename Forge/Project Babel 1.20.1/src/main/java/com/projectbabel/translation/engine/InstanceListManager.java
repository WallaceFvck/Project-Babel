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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Busca lista atualizada de instâncias Lingva de um Gist público.
 * Adaptado de anoubios/ftb-quest-translator.
 *
 * Benefício: se instâncias hardcoded caírem, o Gist é atualizado
 * e todos os usuários do mod se beneficiam automaticamente.
 */
public class InstanceListManager {

    // Gist com lista atual de instâncias Lingva (mantido pela comunidade)
    private static final String GIST_URL =
        "https://gist.githubusercontent.com/anoubios/ftq-instances/raw/instances.json";

    // Fallback hardcoded se o Gist não estiver disponível
    private static final List<String> HARDCODED = List.of(
        "https://lingva.ml",
        "https://translate.plausibility.cloud",
        "https://lingva.lunar.icu",
        "https://translate.projectsegfau.lt",
        "https://translate.igna.wtf",
        "https://lingva.garudalinux.org"
    );

    private static final long REFRESH_MS = 30 * 60 * 1000L; // 30 min

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private static volatile List<String> cached     = null;
    private static volatile long         lastFetch  = 0;
    private static volatile boolean      fetching   = false;

    /** Retorna lista de instâncias (do cache ou hardcoded enquanto carrega). */
    public static List<String> getLingvaInstances() {
        long now = System.currentTimeMillis();
        if (cached == null || now - lastFetch > REFRESH_MS) {
            if (!fetching) {
                fetching = true;
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "projectbabel-InstanceFetch");
                    t.setDaemon(true);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }).submit(() -> {
                    try { fetchFromGist(); } finally { fetching = false; }
                });
            }
        }
        return cached != null ? cached : HARDCODED;
    }

    private static void fetchFromGist() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GIST_URL))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.has("lingva")) {
                    List<String> instances = new ArrayList<>();
                    for (JsonElement el : json.getAsJsonArray("lingva")) {
                        String url = el.getAsString().trim().replaceAll("/$", "");
                        if (!url.isEmpty()) instances.add(url);
                    }
                    if (!instances.isEmpty()) {
                        cached    = List.copyOf(instances);
                        lastFetch = System.currentTimeMillis();
                        ProjectBabelMod.LOGGER.info("[projectbabel] {} instâncias Lingva carregadas do Gist.", instances.size());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.debug("[projectbabel] Gist fetch: {}", e.getMessage());
        }
        if (cached == null) {
            cached    = HARDCODED;
            lastFetch = System.currentTimeMillis();
        }
    }
}
