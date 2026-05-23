package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.screen.TranslationCacheScreen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache de traducoes otimizado para muitas leituras concorrentes.
 *
 * O modelo anterior usava LinkedHashMap accessOrder=true. Na pratica, cada
 * get() virava uma escrita estrutural e todos os workers disputavam o mesmo
 * write lock. Aqui o caminho de leitura e lock-free; a eviccao e FIFO
 * aproximada, suficiente para limitar memoria sem travar preload/render.
 */
public class TranslationCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE = "projectbabel_cache.json";

    private final int maxSize;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> targetTextIndex = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap.KeySetView<String, Boolean> translatedValues = ConcurrentHashMap.newKeySet();

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public TranslationCache() {
        this.maxSize = Math.max(256, AutoTranslateConfig.getCacheSize());
        if (AutoTranslateConfig.isCachePersist()) loadFromDisk();
    }

    public String get(String text, String sourceLang, String targetLang) {
        String result = cache.get(buildKey(text, sourceLang, targetLang));
        if (result != null) hits.incrementAndGet();
        else misses.incrementAndGet();
        if (result == null) return null;
        result = TextFormatUtils.collapseExactDuplicateTranslation(text, result);
        result = TextFormatUtils.collapseRepeatedTranslation(result);
        cache.put(buildKey(text, sourceLang, targetLang), result);
        return TextFormatUtils.preserveTrailingRomanNumeral(text, result);
    }

    public String getAnySource(String text, String targetLang) {
        if (text == null || text.isBlank() || targetLang == null || targetLang.isBlank()) return null;

        String key = targetTextIndex.get(buildTargetTextKey(text, targetLang));
        if (key == null) {
            misses.incrementAndGet();
            return null;
        }

        String result = cache.get(key);
        if (result == null || result.isBlank()) {
            targetTextIndex.remove(buildTargetTextKey(text, targetLang), key);
            misses.incrementAndGet();
            return null;
        }

        hits.incrementAndGet();
        result = TextFormatUtils.collapseExactDuplicateTranslation(text, result);
        result = TextFormatUtils.collapseRepeatedTranslation(result);
        result = TextFormatUtils.preserveTrailingRomanNumeral(text, result);
        cache.put(key, result);
        return result;
    }

    public void put(String text, String sourceLang, String targetLang, String translation) {
        if (translation == null || translation.isBlank()) return;

        translation = TextFormatUtils.postProcess(translation);
        translation = TextFormatUtils.collapseExactDuplicateTranslation(text, translation);
        translation = TextFormatUtils.collapseRepeatedTranslation(translation);
        translation = TextFormatUtils.preserveTrailingRomanNumeral(text, translation);
        if (translation == null || translation.isBlank()) return;

        String key = buildKey(text, sourceLang, targetLang);
        String previous = cache.put(key, translation);
        targetTextIndex.put(buildTargetTextKey(text, targetLang), key);
        if (previous == null) {
            insertionOrder.addLast(key);
        } else {
            translatedValues.remove(normalizeTranslatedValue(previous));
        }

        translatedValues.add(normalizeTranslatedValue(translation));
        TranslationDictionary.getInstance().register(text, translation);
        pruneIfNeeded();
    }

    public boolean contains(String text, String sourceLang, String targetLang) {
        return cache.containsKey(buildKey(text, sourceLang, targetLang));
    }

    public void removeByKey(String key) {
        if (key == null || key.isBlank()) return;

        String removed = cache.remove(key);
        if (removed != null) {
            translatedValues.remove(normalizeTranslatedValue(removed));
            String original = extractOriginalFromKey(key);
            String target = extractTargetFromKey(key);
            if (original != null && target != null) {
                targetTextIndex.remove(buildTargetTextKey(original, target), key);
            }
        }
        TranslationPipeline.clearContextCache();
        saveToDisk();
    }

    public void updateByKey(String key, String translation) {
        if (key == null || key.isBlank() || translation == null || translation.isBlank()) return;

        translation = TextFormatUtils.postProcess(translation);
        String original = extractOriginalFromKey(key);
        translation = TextFormatUtils.collapseExactDuplicateTranslation(original, translation);
        translation = TextFormatUtils.collapseRepeatedTranslation(translation);
        translation = TextFormatUtils.preserveTrailingRomanNumeral(original, translation);
        if (translation == null || translation.isBlank()) return;

        String previous = cache.put(key, translation);
        if (original != null) {
            String target = extractTargetFromKey(key);
            if (target != null) {
                targetTextIndex.put(buildTargetTextKey(original, target), key);
            }
        }
        if (previous == null) {
            insertionOrder.addLast(key);
        } else {
            translatedValues.remove(normalizeTranslatedValue(previous));
        }

        translatedValues.add(normalizeTranslatedValue(translation));

        if (original != null) {
            TranslationDictionary.getInstance().register(original, translation);
        }

        pruneIfNeeded();
        TranslationPipeline.clearContextCache();
        saveToDisk();
    }

    public boolean isAlreadyTranslated(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = normalizeTranslatedValue(text);
        if (translatedValues.contains(normalized)) return true;

        String collapsed = TextFormatUtils.collapseRepeatedTranslation(text);
        return collapsed != null
            && !collapsed.equals(text)
            && translatedValues.contains(normalizeTranslatedValue(collapsed));
    }

    public void clear() {
        cache.clear();
        targetTextIndex.clear();
        insertionOrder.clear();
        translatedValues.clear();
        hits.set(0);
        misses.set(0);
        TranslationDictionary.getInstance().clear();
        UniversalTermsDictionary.getInstance().reloadAsync();
        TranslationPipeline.clearContextCache();
        TranslationSkipRegistry.clear();
        FTBQuestFirstOpenTracker.reset();
        FTBQuestChapterPreloader.reset();
        FTBQuestSidebarPreloader.reset();
        FTBQuestSidebarPreloader.requestWorldPreload();
        PatchouliBookPreloader.resetPreloadState();
        ModonomiconBookPreloader.resetPreloadState();
        GuideMePreloader.reset();
        GuideMePreloader.requestWorldPreload();
        saveToDisk();
        ProjectBabelMod.LOGGER.info("[projectbabel] Cache e dicionario limpos.");
    }

    public int size() {
        return cache.size();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total * 100.0;
    }

    public Map<String, String> getSnapshot() {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>(cache.size());
        for (String key : insertionOrder) {
            String value = cache.get(key);
            if (value != null) snapshot.put(key, value);
        }
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            snapshot.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return snapshot;
    }

    public List<TranslationCacheScreen.CacheEntry> getAllEntries() {
        Map<String, String> snapshot = getSnapshot();
        List<TranslationCacheScreen.CacheEntry> result = new ArrayList<>(snapshot.size());
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            int firstPipe = key.indexOf('|');
            int secondPipe = key.indexOf('|', firstPipe + 1);
            if (secondPipe >= 0) {
                result.add(new TranslationCacheScreen.CacheEntry(
                    key,
                    key.substring(secondPipe + 1),
                    entry.getValue()
                ));
            }
        }
        Collections.reverse(result);
        return result;
    }

    public void saveToDisk() {
        if (!AutoTranslateConfig.isCachePersist()) return;
        Path path = getCachePath();
        if (path == null) return;

        Map<String, String> snapshot = getSnapshot();
        Thread thread = new Thread(() -> {
            try {
                Files.writeString(path, GSON.toJson(snapshot));
                ProjectBabelMod.LOGGER.info("[projectbabel] Cache salvo: {} entradas.", snapshot.size());
            } catch (IOException e) {
                ProjectBabelMod.LOGGER.error("[projectbabel] Falha ao salvar cache: {}", e.getMessage());
            }
        }, "projectbabel-CacheSave");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadFromDisk() {
        Path path = getCachePath();
        if (path == null || !Files.exists(path)) return;

        try {
            String json = Files.readString(path);
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(json, type);
            if (loaded == null) return;

            int dictEntries = 0;
            boolean sanitizedAny = false;
            for (Map.Entry<String, String> entry : loaded.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null || value.isBlank()) continue;
                String originalValue = value;

                int secondPipe = key.indexOf('|', key.indexOf('|') + 1);
                String original = secondPipe >= 0 ? key.substring(secondPipe + 1) : null;

                value = TextFormatUtils.postProcess(value);
                value = TextFormatUtils.collapseExactDuplicateTranslation(original, value);
                value = TextFormatUtils.collapseRepeatedTranslation(value);
                value = TextFormatUtils.preserveTrailingRomanNumeral(original, value);
                if (value == null || value.isBlank()) continue;
                sanitizedAny |= !value.equals(originalValue);

                cache.put(key, value);
                if (original != null) {
                    String target = extractTargetFromKey(key);
                    if (target != null) {
                        targetTextIndex.put(buildTargetTextKey(original, target), key);
                    }
                }
                insertionOrder.addLast(key);
                translatedValues.add(normalizeTranslatedValue(value));

                if (secondPipe >= 0) {
                    TranslationDictionary.getInstance().register(original, value);
                    dictEntries++;
                }
            }

            pruneIfNeeded();
            ProjectBabelMod.LOGGER.info(
                "[projectbabel] Cache carregado: {} entradas, {} pares no dicionario.",
                loaded.size(),
                dictEntries
            );
            if (sanitizedAny) {
                saveToDisk();
            }
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.warn("[projectbabel] Nao foi possivel carregar cache: {}", e.getMessage());
        }
    }

    private Path getCachePath() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null ? null : mc.gameDirectory.toPath().resolve(CACHE_FILE);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildKey(String text, String sourceLang, String targetLang) {
        return sourceLang + "|" + targetLang + "|" + text;
    }

    private String extractOriginalFromKey(String key) {
        int firstPipe = key.indexOf('|');
        int secondPipe = key.indexOf('|', firstPipe + 1);
        return secondPipe >= 0 ? key.substring(secondPipe + 1) : null;
    }

    private void pruneIfNeeded() {
        while (cache.size() > maxSize) {
            String key = insertionOrder.pollFirst();
            if (key == null) {
                cache.clear();
                targetTextIndex.clear();
                translatedValues.clear();
                return;
            }

            String removed = cache.remove(key);
            if (removed != null) {
                translatedValues.remove(normalizeTranslatedValue(removed));
                String original = extractOriginalFromKey(key);
                String target = extractTargetFromKey(key);
                if (original != null && target != null) {
                    targetTextIndex.remove(buildTargetTextKey(original, target), key);
                }
            }
        }
    }

    private String extractTargetFromKey(String key) {
        int firstPipe = key.indexOf('|');
        int secondPipe = key.indexOf('|', firstPipe + 1);
        return firstPipe >= 0 && secondPipe > firstPipe
            ? key.substring(firstPipe + 1, secondPipe)
            : null;
    }

    private String buildTargetTextKey(String text, String targetLang) {
        return targetLang + "|" + text;
    }

    private static String normalizeTranslatedValue(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }
}
