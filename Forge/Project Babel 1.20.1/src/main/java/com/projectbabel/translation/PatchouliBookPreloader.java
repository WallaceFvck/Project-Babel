package com.projectbabel.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.projectbabel.ProjectBabelMod;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PatchouliBookPreloader {

    private static final long PRELOAD_WAIT_TIMEOUT_MS = 90_000L;
    private static final long OPEN_SCREEN_WAIT_MS = 180L;
    private static final int MAX_RETRY_PASSES = 2;
    private static final Set<String> READY_BOOKS = ConcurrentHashMap.newKeySet();
    private static final Set<String> EXHAUSTED_TEXTS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, CompletableFuture<Boolean>> RUNNING_BOOKS =
        new ConcurrentHashMap<>();
    private static final AtomicBoolean REFRESH_AFTER_IDLE_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicLong GENERATION = new AtomicLong(0L);

    private PatchouliBookPreloader() {
    }

    public static void preloadBookForScreen(Object screen) {
        Object book = readField(screen, "book");
        requestBookPreload(book, OPEN_SCREEN_WAIT_MS, false);
    }

    public static Component translateForParse(Component original) {
        if (original == null) return null;

        Component cached = TranslationPipeline.translateComponentTreeCacheOnly(original);
        if (cached != original) return cached;
        if (!TranslationPipeline.needsExternalTranslation(original)) return original;
        if (isExhausted(original)) return original;
        if (isPreloading()) {
            ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Patchouli parse miss while preload active text={}", truncate(original.getString(), 80));
            TranslationPipeline.translateComponentTree(original);
            requestRefreshAfterIdle("miss de layout Patchouli");
            return original;
        }

        ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Patchouli blocking parse text={}", truncate(original.getString(), 80));
        return TranslationPipeline.translateComponentTreeBlocking(original);
    }

    public static boolean preloadBookBlocking(Object book) {
        return requestBookPreload(book, PRELOAD_WAIT_TIMEOUT_MS + 5_000L, true);
    }

    public static boolean isPreloading() {
        return !RUNNING_BOOKS.isEmpty();
    }

    private static boolean requestBookPreload(Object book, long waitMs, boolean warnOnTimeout) {
        BookIdentity identity = resolveBookIdentity(book);
        if (identity == null) {
            ProjectBabelMod.LOGGER.debug("[projectbabel] Patchouli book preload ignorado: livro nao identificado.");
            return false;
        }

        String targetLang = LanguageDetector.getTargetLanguageForApi();
        String preloadKey = targetLang + ":" + identity.id();
        if (READY_BOOKS.contains(preloadKey)) return true;

        long generation = GENERATION.get();
        CompletableFuture<Boolean> future = RUNNING_BOOKS.computeIfAbsent(preloadKey,
            ignored -> CompletableFuture.supplyAsync(
                () -> PreloadAcceleration.supply(() -> runBookPreload(preloadKey, identity, targetLang, generation)),
                TranslationExecutors.preload()
            ));

        try {
            return Boolean.TRUE.equals(future.get(Math.max(0L, waitMs), TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            if (warnOnTimeout) {
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] Patchouli preload do livro {} nao terminou a tempo: {}",
                    identity.id(), e.getMessage());
            }
            return false;
        }
    }

    public static void resetPreloadState() {
        GENERATION.incrementAndGet();
        READY_BOOKS.clear();
        RUNNING_BOOKS.clear();
        EXHAUSTED_TEXTS.clear();
        REFRESH_AFTER_IDLE_SCHEDULED.set(false);
    }

    private static void requestRefreshAfterIdle(String reason) {
        if (!REFRESH_AFTER_IDLE_SCHEDULED.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                PatchouliReloadHelper.requestReload(reason);
            } finally {
                REFRESH_AFTER_IDLE_SCHEDULED.set(false);
            }
        }, TranslationExecutors.preload());
    }

    private static boolean runBookPreload(String preloadKey, BookIdentity identity, String targetLang, long generation) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            ResourceManager manager = mc.getResourceManager();
            Map<ResourceLocation, Resource> resources = manager.listResources(
                "patchouli_books",
                path -> path.getPath().endsWith(".json") && belongsToBook(path, identity)
            );

            if (resources.isEmpty()) {
                ProjectBabelMod.LOGGER.debug(
                    "[projectbabel] Patchouli preload: nenhum JSON encontrado para livro {}.",
                    identity.id());
                if (GENERATION.get() != generation) return false;
                READY_BOOKS.add(preloadKey);
                return true;
            }

            LinkedHashSet<String> texts = new LinkedHashSet<>(4096);
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8))) {

                        JsonElement json = JsonParser.parseReader(reader);
                        extract(json, texts);
                    } catch (Exception e) {
                        ProjectBabelMod.LOGGER.debug("[projectbabel] Patchouli preload falhou em {}: {}",
                            entry.getKey(), e.getMessage());
                    }
                }
            }

            int queued = enqueueTexts(texts);
            ProjectBabelDebug.info(
                ProjectBabelDebug.BOOKS,
                "Patchouli book={} files={} texts={} queued={}",
                identity.id(),
                resources.size(),
                texts.size(),
                queued
            );
            ProjectBabelMod.LOGGER.info(
                "[projectbabel] Patchouli livro {}: {} textos enfileirados ({} arquivos, alvo {}).",
                identity.id(), queued, resources.size(), targetLang);

            if (queued > 0) {
                boolean complete = TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                if (!complete) {
                    ProjectBabelMod.LOGGER.warn(
                        "[projectbabel] Patchouli livro {} ainda nao terminou apos {}s (fila={}, pendentes={}).",
                        identity.id(),
                        PRELOAD_WAIT_TIMEOUT_MS / 1000L,
                        TranslationManager.getInstance().getQueuedCount(),
                        TranslationManager.getInstance().getPendingCount());
                }
            }

            RetryResult retry = retryMissingTexts(texts);
            if (retry.missing() > 0) {
                int exhausted = rememberExhaustedTexts(texts);
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] Patchouli livro {} ainda tem {} textos sem cache apos {} retries (forcados={}). Marcando {} textos como esgotados nesta sessao; nao tentara novamente.",
                    identity.id(),
                    retry.missing(),
                    MAX_RETRY_PASSES,
                    retry.forced(),
                    exhausted
                );
                if (GENERATION.get() != generation) return false;
                READY_BOOKS.add(preloadKey);
                PatchouliReloadHelper.requestReload("preload parcial do livro " + identity.id());
                return true;
            }

            if (retry.forced() > 0) {
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Patchouli livro {}: {} falhas recuperadas antes da abertura.",
                    identity.id(),
                    retry.forced()
                );
            }

            if (GENERATION.get() != generation) return false;
            READY_BOOKS.add(preloadKey);
            PatchouliReloadHelper.requestReload("preload do livro " + identity.id());
            return true;
        } finally {
            if (GENERATION.get() == generation) {
                RUNNING_BOOKS.remove(preloadKey);
            }
        }
    }

    private static BookIdentity resolveBookIdentity(Object book) {
        if (book == null) return null;

        Object id = readField(book, "id");
        if (id instanceof ResourceLocation location) {
            return new BookIdentity(location.getNamespace(), location.getPath(), location.toString());
        }

        if (id instanceof String text) {
            ResourceLocation location = ResourceLocation.tryParse(text);
            if (location != null) {
                return new BookIdentity(location.getNamespace(), location.getPath(), location.toString());
            }
        }

        return null;
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }

        return null;
    }

    private static boolean belongsToBook(ResourceLocation resource, BookIdentity identity) {
        if (resource == null || identity == null) return false;
        if (!resource.getNamespace().equals(identity.namespace())) return false;

        String path = resource.getPath().replace('\\', '/');
        if (!path.startsWith("patchouli_books/")) return false;

        String rest = path.substring("patchouli_books/".length());
        for (String candidate : identity.pathCandidates()) {
            if (rest.startsWith(candidate + "/")) return true;
        }

        return false;
    }

    private static void extract(JsonElement element, Set<String> texts) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                String text = resolveI18n(primitive.getAsString());
                if (shouldTranslate(text)) {
                    texts.add(text);
                }
            }
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                extract(child, texts);
            }
            return;
        }

        JsonObject obj = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            extract(e.getValue(), texts);
        }
    }

    private static int enqueueTexts(Set<String> texts) {
        int queued = 0;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            for (String text : texts) {
                if (!shouldTranslate(text)) continue;
                TranslationPipeline.translateString(text);
                queued++;
            }
        }
        return queued;
    }

    private static RetryResult retryMissingTexts(Set<String> texts) {
        int forced = 0;
        int missing = 0;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            for (int pass = 0; pass < MAX_RETRY_PASSES; pass++) {
                List<String> retryTexts = collectRetryTexts(texts);
                missing = retryTexts.size();
                if (missing == 0) break;

                List<CompletableFuture<Boolean>> futures = new ArrayList<>(retryTexts.size());
                for (String text : retryTexts) {
                    futures.add(CompletableFuture.supplyAsync(
                        () -> translateRetryText(text),
                        TranslationExecutors.preload()
                    ));
                }

                int changed = waitRetryBatch(futures);
                forced += changed;
                boolean changedThisPass = changed > 0;
                if (!changedThisPass) pauseBeforeRetry();
            }

            missing = 0;
            for (String text : texts) {
                if (needsRetry(text)) missing++;
            }
        }

        return new RetryResult(forced, missing);
    }

    private static int rememberExhaustedTexts(Set<String> texts) {
        int remembered = 0;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            for (String text : texts) {
                if (!needsRetry(text)) continue;
                EXHAUSTED_TEXTS.add(exhaustedKey(text));
                remembered++;
            }
        }
        return remembered;
    }

    private static boolean isExhausted(Component component) {
        if (component == null) return false;
        String text = component.getString();
        return text != null && EXHAUSTED_TEXTS.contains(exhaustedKey(text));
    }

    private static String exhaustedKey(String text) {
        return LanguageDetector.getTargetLanguageForApi() + '|' + text;
    }

    private static List<String> collectRetryTexts(Set<String> texts) {
        List<String> retryTexts = new ArrayList<>();
        for (String text : texts) {
            if (needsRetry(text)) retryTexts.add(text);
        }
        return retryTexts;
    }

    private static boolean translateRetryText(String text) {
        String translated;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            translated = TranslationPipeline.translateStringBlocking(text);
        }
        if (translated == null || translated.equals(text)) return false;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            TranslationTriageManager.getInstance().warmText(text, translated);
        }
        return true;
    }

    private static int waitRetryBatch(List<CompletableFuture<Boolean>> futures) {
        int changed = 0;
        for (CompletableFuture<Boolean> future : futures) {
            try {
                if (Boolean.TRUE.equals(future.get(PRELOAD_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))) {
                    changed++;
                }
            } catch (Exception ignored) {
            }
        }
        return changed;
    }

    private static boolean needsRetry(String text) {
        if (!shouldTranslate(text)) return false;

        String cached = TranslationPipeline.translateStringCacheOnly(text);
        if (cached != null && !cached.equals(text)) return false;

        if (TranslationManager.getInstance().isAlreadyTranslatedValue(text)) return false;

        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null) return false;
        plain = plain.strip();
        if (plain.isBlank()) return false;

        String detected = LanguageDetector.detectLanguage(plain);
        return !LanguageDetector.getTargetLanguageForApi().equals(detected);
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean shouldTranslate(String text) {
        if (text == null) return false;

        text = text.strip();
        if (text.isBlank() || text.length() < 3) return false;
        if (text.startsWith("patchouli.")) return false;
        if (text.startsWith("item.")) return false;
        if (text.startsWith("block.")) return false;

        return true;
    }

    private static String resolveI18n(String text) {
        if (text == null) return null;

        String key = text.strip();
        if (key.isBlank()) return text;

        try {
            if (I18n.exists(key)) {
                String translated = I18n.get(key);
                if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                    return translated;
                }
            }
        } catch (Exception ignored) {
        }

        return text;
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private record RetryResult(int forced, int missing) {}

    private record BookIdentity(String namespace, String path, String id) {
        Set<String> pathCandidates() {
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            addCandidate(candidates, path);

            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < path.length()) {
                addCandidate(candidates, path.substring(slash + 1));
            }

            return candidates;
        }

        private static void addCandidate(Set<String> candidates, String value) {
            if (value == null || value.isBlank()) return;
            candidates.add(value);
        }
    }
}
