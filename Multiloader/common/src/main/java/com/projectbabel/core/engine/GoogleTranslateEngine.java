package com.projectbabel.core.engine;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Engine Google Translate — scraper público, sem API key, sem limite declarado.
 *
 * Estratégia de 3 camadas (do mod de referência anoubios/ftb-quest-translator):
 *   1. Direto: translate.googleapis.com/translate_a/single?client=gtx
 *   2. DoH bypass: resolve o IP via Cloudflare/Google DNS-over-HTTPS, conecta
 *      diretamente com SNI correto — contorna bloqueios de DNS locais/ISP
 *   3. Proxy: api.codetabs.com como proxy HTTP
 *
 * Google GT é a engine de melhor qualidade disponível sem API key.
 * Suporta ~130 idiomas, sem rate limit documentado para uso moderado.
 */
public class GoogleTranslateEngine {

    private static final String API_URL =
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s";
    private static final String GOOGLE_HOST = "translate.googleapis.com";
    private static final String PROXY_URL   = "https://api.codetabs.com/v1/proxy/?quest=";

    private final HttpClient    httpClient;
    private final AtomicInteger consecutiveFails = new AtomicInteger(0);
    private final AtomicLong    blockedUntilMs   = new AtomicLong(0);

    public GoogleTranslateEngine() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(TranslationExecutors.network())
            .build();
    }

    public CompletableFuture<String> translate(String text, String src, String tgt) {
        if (text == null || text.isBlank()) return CompletableFuture.completedFuture(text);

        long blocked = blockedUntilMs.get();
        if (blocked > System.currentTimeMillis()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("Google: cooldown (~" + (blocked - System.currentTimeMillis())/1000 + "s)"));
        }

        String encoded  = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String directUrl = String.format(API_URL, src, tgt, encoded);

        return CompletableFuture.supplyAsync(() -> {
            // Camada 1: direto
            try {
                String result = fetchDirect(directUrl);
                if (result != null) { consecutiveFails.set(0); return result; }
            } catch (Exception e) {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Google direto: {}", e.getMessage());
            }

            // Camada 2: DoH bypass (contorna bloqueios de DNS do ISP)
            try {
                String result = translateViaDoH(text, src, tgt);
                if (result != null) { consecutiveFails.set(0); return result; }
            } catch (Exception e) {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Google DoH: {}", e.getMessage());
            }

            // Camada 3: proxy
            try {
                String proxyUrl = PROXY_URL + URLEncoder.encode(directUrl, StandardCharsets.UTF_8);
                String result = fetchDirect(proxyUrl);
                if (result != null) { consecutiveFails.set(0); return result; }
            } catch (Exception e) {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Google proxy: {}", e.getMessage());
            }

            int f = consecutiveFails.incrementAndGet();
            if (f >= ProjectBabelCommon.config().getFailureThreshold()) {
                blockedUntilMs.set(System.currentTimeMillis() + 30_000L);
            }
            throw new RuntimeException("Google: todas as camadas falharam");
        }, TranslationExecutors.network());
    }

    private String fetchDirect(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .timeout(Duration.ofMillis(ProjectBabelCommon.config().getRequestTimeoutMs()))
            .GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) return parseResponse(resp.body());
        if (resp.statusCode() == 429) {
            blockedUntilMs.set(System.currentTimeMillis() + 60_000L);
            throw new RuntimeException("Google HTTP 429");
        }
        return null;
    }

    /**
     * DoH (DNS over HTTPS) bypass — resolve o IP real do host via Cloudflare/Google DNS,
     * depois abre socket SSL diretamente com SNI correto.
     * Contorna bloqueios de DNS locais sem depender do DNS do sistema.
     * Implementação adaptada de anoubios/ftb-quest-translator.
     */
    private String translateViaDoH(String text, String src, String tgt) throws Exception {
        String ip = DoHResolver.resolve(GOOGLE_HOST);
        if (ip == null) throw new Exception("DoH falhou para " + GOOGLE_HOST);

        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String path    = "/translate_a/single?client=gtx&sl=" + src + "&tl=" + tgt + "&dt=t&q=" + encoded;
        if (path.length() > 8000) throw new Exception("URL muito longa para DoH");

        // InetAddress com hostname (para SNI) mas IP resolvido via DoH (bypassa DNS do sistema)
        String[] ipParts = ip.split("\\.");
        byte[]   addr    = new byte[4];
        for (int i = 0; i < 4; i++) addr[i] = (byte) Integer.parseInt(ipParts[i]);
        java.net.InetAddress address = java.net.InetAddress.getByAddress(GOOGLE_HOST, addr);

        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
        ctx.init(null, null, null);
        javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) ctx.getSocketFactory().createSocket(address, 443);
        socket.setSoTimeout(10000);

        try {
            socket.startHandshake();
            String httpRequest =
                "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + GOOGLE_HOST + "\r\n" +
                "User-Agent: Mozilla/5.0\r\n" +
                "Accept: */*\r\n" +
                "Connection: close\r\n\r\n";
            socket.getOutputStream().write(httpRequest.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String statusLine = reader.readLine();
            if (statusLine == null) throw new Exception("Sem resposta do socket DoH");
            int statusCode = Integer.parseInt(statusLine.split(" ")[1]);

            boolean chunked = false;
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().contains("transfer-encoding: chunked")) chunked = true;
            }

            StringBuilder body = new StringBuilder();
            if (chunked) {
                String chunkLine;
                while ((chunkLine = reader.readLine()) != null) {
                    int chunkSize;
                    try { chunkSize = Integer.parseInt(chunkLine.trim(), 16); }
                    catch (NumberFormatException e) { continue; }
                    if (chunkSize == 0) break;
                    char[] buf = new char[chunkSize];
                    int totalRead = 0;
                    while (totalRead < chunkSize) {
                        int r = reader.read(buf, totalRead, chunkSize - totalRead);
                        if (r == -1) break;
                        totalRead += r;
                    }
                    body.append(buf, 0, totalRead);
                    reader.readLine();
                }
            } else {
                char[] buf = new char[4096];
                int read;
                while ((read = reader.read(buf)) != -1) body.append(buf, 0, read);
            }

            if (statusCode == 200) {
                String result = parseResponse(body.toString());
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Google DoH OK via {}", ip);
                return result;
            }
            throw new Exception("DoH retornou HTTP " + statusCode);
        } finally {
            socket.close();
        }
    }

    /**
     * Parseia a resposta JSON do Google Translate (formato gtx).
     * A resposta é um array aninhado: [[["tradução", "original", null, null, 3], ...], ...]
     * Usa parser lenient para tolerar respostas malformadas.
     */
    private String parseResponse(String json) {
        try {
            // Encontra o fim do JSON para remover lixo do chunked encoding
            json = json.trim();
            int depth = 0, jsonEnd = -1;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if      (c == '[')  depth++;
                else if (c == ']') { depth--; if (depth == 0) { jsonEnd = i + 1; break; } }
                else if (c == '"') { i++; while (i < json.length() && json.charAt(i) != '"') { if (json.charAt(i) == '\\') i++; i++; } }
            }
            if (jsonEnd > 0 && jsonEnd < json.length()) json = json.substring(0, jsonEnd);

            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            JsonArray root      = JsonParser.parseReader(reader).getAsJsonArray();
            JsonArray sentences = root.get(0).getAsJsonArray();
            StringBuilder sb    = new StringBuilder();
            for (int i = 0; i < sentences.size(); i++) {
                var sentence = sentences.get(i);
                if (sentence.isJsonArray()) {
                    JsonArray arr = sentence.getAsJsonArray();
                    if (arr.size() > 0 && !arr.get(0).isJsonNull()) sb.append(arr.get(0).getAsString());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.debug("[projectbabel] Google parse: {}", e.getMessage());
            return null;
        }
    }

    public void resetFailures()  { consecutiveFails.set(0); blockedUntilMs.set(0); }
}
