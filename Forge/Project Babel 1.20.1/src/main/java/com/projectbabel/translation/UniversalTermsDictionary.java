package com.projectbabel.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Glossario de termos fixos do Project Babel.
 *
 * Fonte fixa: ingles. O alvo e escolhido pelo idioma atual do mod/cliente.
 *
 * O formato novo permite um unico arquivo para varios idiomas:
 * {
 *   "source_language": "en",
 *   "targets": {
 *     "pt": { "Spawn": "Ponto de nascimento" },
 *     "es": { "Spawn": "Punto de aparicion" }
 *   }
 * }
 *
 * Tambem mantem compatibilidade com o formato antigo:
 * { "target_language": "pt_BR", "terms": { "Spawn": "Ponto de nascimento" } }
 *
 * A consulta e propositalmente conservadora: lookup exato case-insensitive.
 * Nao substitui palavras dentro de frases, evitando traducoes aleatorias.
 */
public final class UniversalTermsDictionary {

    private static final String DEFAULT_LOCAL_FILE = "projectbabel_universal_terms.json";
    private static final String SOURCE_LANGUAGE = "en";

    private static final String FALLBACK_JSON = """
        {
          "schema": 2,
          "name": "Project Babel - Universal Terms",
          "description": "Fixed glossary used before the online translator. Source is always English. Add any target language under targets.",
          "source_language": "en",
          "updated_at": "2026-05-23",
          "targets": {
            "pt": {
              "Spawn": "Ponto de nascimento",
              "Spawn Point": "Ponto de nascimento",
              "Respawn": "Renascer",
              "Respawn Point": "Ponto de renascimento",
              "Spawner": "Gerador",
              "Mob Spawner": "Gerador de mobs",
              "Waypoint": "Ponto de referencia"
            },
            "es": {
              "Spawn": "Punto de aparicion",
              "Spawn Point": "Punto de aparicion",
              "Respawn": "Reaparecer",
              "Respawn Point": "Punto de reaparicion",
              "Spawner": "Generador",
              "Mob Spawner": "Generador de criaturas",
              "Waypoint": "Punto de ruta"
            }
          }
        }
        """;

    private static final UniversalTermsDictionary INSTANCE = new UniversalTermsDictionary();

    /** Key format: targetLang + "|" + normalizedEnglishTerm */
    private final ConcurrentHashMap<String, String> exactTerms = new ConcurrentHashMap<>();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private volatile String loadedSignature = "";
    private volatile String lastStatus = "Desligado";
    private volatile long lastLoadEpochMs = 0L;
    private final Object blockingLoadLock = new Object();

    private UniversalTermsDictionary() {}

    public static UniversalTermsDictionary getInstance() {
        return INSTANCE;
    }

    public void ensureLoadedAsync() {
        if (!AutoTranslateConfig.isUniversalTermsEnabled()) return;
        String signature = sourceSignature();
        if (!exactTerms.isEmpty() && signature.equals(loadedSignature)) return;
        reloadAsync();
    }

    public void reloadAsync() {
        if (!AutoTranslateConfig.isUniversalTermsEnabled()) {
            exactTerms.clear();
            loadedSignature = "";
            lastStatus = "Desligado";
            return;
        }
        if (!loading.compareAndSet(false, true)) return;

        TranslationExecutors.io().execute(() -> {
            String signature = sourceSignature();
            try {
                LoadResult result = AutoTranslateConfig.isUniversalTermsRemote()
                    ? loadRemote(normalizeRemoteUrl(AutoTranslateConfig.getUniversalTermsRemoteUrl()))
                    : loadLocal(resolveLocalPath(), true);

                exactTerms.clear();
                exactTerms.putAll(result.entries());
                loadedSignature = signature;
                lastLoadEpochMs = System.currentTimeMillis();
                lastStatus = result.status();

                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Glossario universal carregado: {} termos ({})",
                    exactTerms.size(), result.status()
                );
            } catch (Exception e) {
                Map<String, String> fallback = parseEntries(FALLBACK_JSON);
                exactTerms.clear();
                exactTerms.putAll(fallback);
                loadedSignature = signature;
                lastLoadEpochMs = System.currentTimeMillis();
                lastStatus = "Falha na fonte, usando interno multilingue";
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] Falha ao carregar glossario universal: {}. Usando {} termos internos.",
                    e.getMessage(), fallback.size()
                );
            } finally {
                loading.set(false);
            }
        });
    }

    /**
     * Consulta uma entrada exata do glossario.
     *
     * O glossario so deve ser aplicado quando a origem e ingles. Se o pipeline
     * informar outra origem, nao retorna nada para evitar sobrescrever texto ja
     * traduzido em portugues/espanhol/etc.
     */
    public String lookupExact(String text, String sourceLang, String targetLang) {
        if (!AutoTranslateConfig.isUniversalTermsEnabled()) return null;
        if (text == null || text.isBlank()) return null;

        String src = normalizeLanguage(sourceLang, AutoTranslateConfig.getSourceLang());
        // O glossario tem chaves em ingles; por isso o lookup exato no texto e
        // mais confiavel que a deteccao de idioma para termos curtos como Spawn.

        String tgt = normalizeLanguage(targetLang, LanguageDetector.getTargetLanguageForApi());
        if (tgt.isBlank() || SOURCE_LANGUAGE.equals(tgt)) return null;

        ensureLoadedForLookup();
        String normalizedText = normalizeTerm(text);
        String result = exactTerms.get(termKey(tgt, normalizedText));
        if (result != null) {
            ProjectBabelMod.LOGGER.debug(
                "[projectbabel] UniversalTerms hit: '{}' -> '{}' [{} -> {}]",
                text, result, src, tgt
            );
            return result;
        }

        // Compatibilidade com codigos regionais, caso alguma chamada passe pt_br/es_es.
        int separator = tgt.indexOf('_');
        if (separator > 0) {
            result = exactTerms.get(termKey(tgt.substring(0, separator), normalizedText));
        }
        return result;
    }

    /** Compatibilidade com chamadas antigas. Usa origem en e alvo atual do cliente. */
    public String lookupExact(String text) {
        return lookupExact(text, SOURCE_LANGUAGE, LanguageDetector.getTargetLanguageForApi());
    }

    public int size() {
        return exactTerms.size();
    }

    public boolean isLoading() {
        return loading.get();
    }

    public String statusSummary() {
        if (!AutoTranslateConfig.isUniversalTermsEnabled()) return "Glossario OFF";
        String source = AutoTranslateConfig.isUniversalTermsRemote() ? "Web" : "Local";
        String target = normalizeLanguage(LanguageDetector.getTargetLanguageForApi(), AutoTranslateConfig.getTargetLang());
        String load = loading.get() ? "carregando" : (exactTerms.isEmpty() ? "vazio" : exactTerms.size() + " termos");
        return source + " • en>" + target + " • " + load;
    }

    public String lastStatus() {
        return lastStatus;
    }

    public long lastLoadEpochMs() {
        return lastLoadEpochMs;
    }

    public Path resolveLocalPath() {
        String configured = AutoTranslateConfig.getUniversalTermsLocalPath();
        if (configured == null || configured.isBlank()) configured = DEFAULT_LOCAL_FILE;
        Path path = Path.of(configured);
        if (path.isAbsolute()) return path.normalize();

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve(path).normalize();
            }
        } catch (Exception ignored) {
        }
        return path.toAbsolutePath().normalize();
    }

    public void createOrOpenLocalFile() {
        TranslationExecutors.io().execute(() -> {
            try {
                Path path = resolveLocalPath();
                ensureLocalFileExists(path);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(path.toFile());
                }
            } catch (Exception e) {
                lastStatus = "Nao foi possivel abrir arquivo local";
                ProjectBabelMod.LOGGER.warn("[projectbabel] Nao foi possivel abrir dicionario local: {}", e.getMessage());
            }
        });
    }


    /**
     * Garante uma tentativa real de carga antes do primeiro lookup.
     *
     * O carregamento assíncrono é bom para preload, mas no primeiro texto curto
     * (ex.: "Spawn") ele podia ainda estar vazio e o pipeline caía direto na API.
     * Aqui fazemos uma carga síncrona apenas quando o glossário está vazio ou a
     * fonte mudou. Depois disso os lookups continuam lock-free pelo ConcurrentHashMap.
     */
    private void ensureLoadedForLookup() {
        if (!AutoTranslateConfig.isUniversalTermsEnabled()) return;
        String signature = sourceSignature();
        if (!exactTerms.isEmpty() && signature.equals(loadedSignature)) return;

        synchronized (blockingLoadLock) {
            signature = sourceSignature();
            if (!exactTerms.isEmpty() && signature.equals(loadedSignature)) return;
            if (loading.get()) return;
            loading.set(true);
            try {
                LoadResult result = AutoTranslateConfig.isUniversalTermsRemote()
                    ? loadRemote(normalizeRemoteUrl(AutoTranslateConfig.getUniversalTermsRemoteUrl()))
                    : loadLocal(resolveLocalPath(), true);

                exactTerms.clear();
                exactTerms.putAll(result.entries());
                loadedSignature = signature;
                lastLoadEpochMs = System.currentTimeMillis();
                lastStatus = result.status();
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Glossario universal carregado no primeiro lookup: {} termos ({})",
                    exactTerms.size(), result.status()
                );
            } catch (Exception e) {
                Map<String, String> fallback = parseEntries(FALLBACK_JSON);
                exactTerms.clear();
                exactTerms.putAll(fallback);
                loadedSignature = signature;
                lastLoadEpochMs = System.currentTimeMillis();
                lastStatus = "Falha na fonte, usando interno multilingue";
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] Falha ao carregar glossario universal no lookup: {}. Usando {} termos internos.",
                    e.getMessage(), fallback.size()
                );
            } finally {
                loading.set(false);
            }
        }
    }

    private LoadResult loadRemote(String urlText) throws IOException {
        urlText = normalizeRemoteUrl(urlText);
        if (urlText == null || urlText.isBlank()) throw new IOException("URL remota vazia");
        URL url = URI.create(urlText.trim()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(Math.min(6000, Math.max(1000, AutoTranslateConfig.getRequestTimeoutMs())));
        connection.setReadTimeout(Math.min(8000, Math.max(2000, AutoTranslateConfig.getRequestTimeoutMs() + 2000)));
        connection.setRequestProperty("User-Agent", "ProjectBabel/UniversalTerms");
        connection.setInstanceFollowRedirects(true);

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }

        String json = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> entries = parseEntries(json);
        if (entries.isEmpty()) throw new IOException("dicionario remoto vazio");
        return new LoadResult(entries, "Web " + code + " • " + Instant.now());
    }

    private LoadResult loadLocal(Path path, boolean createIfMissing) throws IOException {
        if (createIfMissing) ensureLocalFileExists(path);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, String> entries = parseEntries(json);
        if (entries.isEmpty()) throw new IOException("dicionario local vazio");
        return new LoadResult(entries, "Local • " + path.getFileName());
    }

    private void ensureLocalFileExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(path)) {
            Files.writeString(path, FALLBACK_JSON, StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseEntries(String json) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return result;

        JsonObject object = root.getAsJsonObject();
        String source = metadataString(object, "source_language", SOURCE_LANGUAGE);
        if (!isEnglishSource(normalizeLanguage(source, SOURCE_LANGUAGE))) {
            ProjectBabelMod.LOGGER.warn("[projectbabel] Glossario ignorado: source_language precisa ser en, recebido '{}'", source);
            return result;
        }

        // Formato novo: { "targets": { "pt": {...}, "es": {...} } }
        JsonObject targets = firstObject(object, "targets", "languages", "translations_by_language", "terms_by_language");
        if (targets != null) {
            for (Map.Entry<String, JsonElement> targetEntry : targets.entrySet()) {
                if (targetEntry.getValue() == null || !targetEntry.getValue().isJsonObject()) continue;
                String target = normalizeTargetLanguage(targetEntry.getKey());
                if (target.isBlank() || SOURCE_LANGUAGE.equals(target)) continue;
                collectTermMap(result, target, targetEntry.getValue().getAsJsonObject());
            }
        }

        // Formato alternativo: { "terms": { "Spawn": { "pt": "...", "es": "..." } } }
        JsonObject terms = firstObject(object, "terms", "entries");
        if (terms != null) {
            String targetFromMetadata = normalizeTargetLanguage(metadataString(object, "target_language", ""));
            collectTermsObject(result, targetFromMetadata, terms);
        }

        // Formato legado completamente plano: { "Spawn": "Ponto de nascimento" }
        String targetFromMetadata = normalizeTargetLanguage(metadataString(object, "target_language", ""));
        if (!targetFromMetadata.isBlank()) {
            collectTermMap(result, targetFromMetadata, object);
        }

        return result;
    }

    private void collectTermsObject(Map<String, String> result, String targetFromMetadata, JsonObject terms) {
        for (Map.Entry<String, JsonElement> entry : terms.entrySet()) {
            String english = entry.getKey();
            if (isMetadataKey(english)) continue;
            JsonElement value = entry.getValue();
            if (value == null) continue;

            if (value.isJsonPrimitive()) {
                if (!targetFromMetadata.isBlank()) {
                    putEntry(result, targetFromMetadata, english, value.getAsString());
                }
                continue;
            }

            if (value.isJsonObject()) {
                JsonObject translations = value.getAsJsonObject();
                for (Map.Entry<String, JsonElement> targetEntry : translations.entrySet()) {
                    JsonElement translated = targetEntry.getValue();
                    if (translated == null || !translated.isJsonPrimitive()) continue;
                    String target = normalizeTargetLanguage(targetEntry.getKey());
                    putEntry(result, target, english, translated.getAsString());
                }
            }
        }
    }

    private void collectTermMap(Map<String, String> result, String target, JsonObject terms) {
        if (target == null || target.isBlank() || SOURCE_LANGUAGE.equals(target)) return;
        for (Map.Entry<String, JsonElement> entry : terms.entrySet()) {
            String english = entry.getKey();
            if (isMetadataKey(english)) continue;
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive()) continue;
            putEntry(result, target, english, value.getAsString());
        }
    }

    private void putEntry(Map<String, String> result, String target, String english, String translated) {
        if (target == null || target.isBlank() || SOURCE_LANGUAGE.equals(target)) return;
        if (english == null || english.isBlank()) return;
        if (translated == null || translated.isBlank()) return;
        result.put(termKey(target, normalizeTerm(english)), translated.strip());
    }

    private JsonObject firstObject(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonObject()) {
                return object.getAsJsonObject(key);
            }
        }
        return null;
    }

    private String metadataString(JsonObject object, String key, String fallback) {
        try {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                String value = object.get(key).getAsString();
                if (value != null && !value.isBlank()) return value;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private boolean isMetadataKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.equals("schema")
            || lower.equals("name")
            || lower.equals("version")
            || lower.equals("target_language")
            || lower.equals("source_language")
            || lower.equals("updated_at")
            || lower.equals("description")
            || lower.equals("targets")
            || lower.equals("languages")
            || lower.equals("translations_by_language")
            || lower.equals("terms_by_language")
            || lower.equals("terms")
            || lower.equals("entries");
    }


    private String normalizeRemoteUrl(String urlText) {
        if (urlText == null) return "";
        String url = urlText.trim();
        // Usuários costumam copiar a URL do GitHub /blob/. O mod precisa do raw.
        if (url.startsWith("https://github.com/") && url.contains("/blob/")) {
            String raw = url.replace("https://github.com/", "https://raw.githubusercontent.com/");
            raw = raw.replace("/blob/", "/");
            int query = raw.indexOf('?');
            return query >= 0 ? raw.substring(0, query) : raw;
        }
        return url;
    }

    private String sourceSignature() {
        return AutoTranslateConfig.isUniversalTermsRemote()
            ? "remote|" + normalizeRemoteUrl(AutoTranslateConfig.getUniversalTermsRemoteUrl())
            : "local|" + resolveLocalPath();
    }

    private boolean isEnglishSource(String language) {
        return SOURCE_LANGUAGE.equals(normalizeLanguage(language, SOURCE_LANGUAGE));
    }

    private boolean isEnglishLikeSource(String language) {
        String normalized = normalizeLanguage(language, "");
        return normalized.isBlank()
            || SOURCE_LANGUAGE.equals(normalized)
            || "auto".equals(normalized)
            || "unknown".equals(normalized);
    }

    private String normalizeTargetLanguage(String language) {
        return normalizeLanguage(language, "");
    }

    private String normalizeLanguage(String language, String fallback) {
        if (language == null || language.isBlank()) return fallback;
        String normalized = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int separator = normalized.indexOf('_');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private String termKey(String target, String normalizedTerm) {
        return target + "|" + normalizedTerm;
    }

    private String normalizeTerm(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record LoadResult(Map<String, String> entries, String status) {}
}
