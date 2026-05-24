package com.projectbabel.integrations.books.patchouli;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.integrations.books.BookIdentity;
import com.projectbabel.integrations.books.BookPreloadAdapter;
import com.projectbabel.integrations.books.BookPreloadContext;
import com.projectbabel.integrations.books.BookPreloadCoordinator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import com.projectbabel.core.text.BookMarkupTranslator;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.schedule.PreloadAcceleration;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationTriageManager;
public final class PatchouliBookPreloader {

    private static final long PRELOAD_WAIT_TIMEOUT_MS = 90_000L;
    private static final long OPEN_SCREEN_WAIT_MS = 180L;
    private static final int MAX_RETRY_PASSES = 2;
    private static final BookPreloadAdapter ADAPTER = new PatchouliAdapter();
    private static final Set<String> EXHAUSTED_TEXTS = ConcurrentHashMap.newKeySet();

    private PatchouliBookPreloader() {
    }

    public static void preloadBookForScreen(Object screen) {
        Object book = PatchouliAccess.bookFromScreen(screen);
        requestBookPreload(book, OPEN_SCREEN_WAIT_MS, false);
    }

    public static Component translateForParse(Component original) {
        if (original == null) return null;

        // Patchouli chama esse parser enquanto uma Screen de livro está aberta.
        // O filtro genérico de telas bloqueia tradução em menus para evitar ruído,
        // então o hook pré-layout de livros precisa bypassar esse filtro de forma
        // explícita e curta. Sem isso, o parse blocking devolve sempre o original.
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            // Patchouli parses its own formatting commands during BookTextParser#parse
            // and calculates line wrapping immediately after.  Translating the whole
            // component here can move/delete tokens such as $(bold), $(br), $(), links
            // and page commands, corrupting the parsed spans. Translate only visible
            // text segments outside Patchouli command tokens, before the layout pass.
            Component cached = BookMarkupTranslator.translatePatchouliComponentCacheOnly(original);
            if (cached != original) return cached;
            if (!BookMarkupTranslator.hasTranslatablePatchouliText(original)) return original;
            if (isExhausted(original)) return original;

            if (isPreloading()) {
                ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Patchouli parse miss while preload active; forcing current page text={}", truncate(original.getString(), 80));
                Component translated = BookMarkupTranslator.translatePatchouliComponentBlocking(original);
                if (translated != original) return translated;

                BookMarkupTranslator.translatePatchouliComponentAsync(original);
                requestRefreshAfterIdle("miss de layout Patchouli");
                return original;
            }

            ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Patchouli blocking parse text={}", truncate(original.getString(), 80));
            return BookMarkupTranslator.translatePatchouliComponentBlocking(original);
        }
    }

    public static boolean preloadBookBlocking(Object book) {
        return requestBookPreload(book, PRELOAD_WAIT_TIMEOUT_MS + 5_000L, true);
    }

    public static boolean isPreloading() {
        return BookPreloadCoordinator.isPreloading(PatchouliAdapter.ID);
    }

    private static boolean requestBookPreload(Object book, long waitMs, boolean warnOnTimeout) {
        return BookPreloadCoordinator.request(ADAPTER, book, waitMs, warnOnTimeout);
    }

    public static void resetPreloadState() {
        BookPreloadCoordinator.reset(PatchouliAdapter.ID);
        EXHAUSTED_TEXTS.clear();
    }

    private static void requestRefreshAfterIdle(String reason) {
        BookPreloadCoordinator.requestRefreshAfterIdle(
            PatchouliAdapter.ID,
            PRELOAD_WAIT_TIMEOUT_MS,
            reason,
            () -> PatchouliReloadHelper.requestReload(reason)
        );
    }

    private static boolean runBookPreload(BookPreloadContext context, BookIdentity identity, String targetLang) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            ResourceManager manager = mc.getResourceManager();
            Map<ResourceLocation, Resource> resources = manager.listResources(
                "patchouli_books",
                path -> path.getPath().endsWith(".json") && belongsToBook(path, identity)
            );

            if (resources.isEmpty()) {
                ProjectBabelCommon.LOGGER.debug(
                    "[projectbabel] Patchouli preload: nenhum JSON encontrado para livro {}.",
                    identity.id());
                if (!context.isCurrentGeneration()) return false;
                context.markReady();
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
                        ProjectBabelCommon.LOGGER.debug("[projectbabel] Patchouli preload falhou em {}: {}",
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
            ProjectBabelCommon.LOGGER.info(
                "[projectbabel] Patchouli livro {}: {} textos enfileirados ({} arquivos, alvo {}).",
                identity.id(), queued, resources.size(), targetLang);

            if (queued > 0) {
                boolean complete = TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                if (!complete) {
                    ProjectBabelCommon.LOGGER.warn(
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
                ProjectBabelCommon.LOGGER.warn(
                    "[projectbabel] Patchouli livro {} ainda tem {} textos sem cache apos {} retries (forcados={}). Marcando {} textos como esgotados nesta sessao; nao tentara novamente.",
                    identity.id(),
                    retry.missing(),
                    MAX_RETRY_PASSES,
                    retry.forced(),
                    exhausted
                );
                if (!context.isCurrentGeneration()) return false;
                context.markReady();
                PatchouliReloadHelper.requestReload("preload parcial do livro " + identity.id());
                return true;
            }

            if (retry.forced() > 0) {
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] Patchouli livro {}: {} falhas recuperadas antes da abertura.",
                    identity.id(),
                    retry.forced()
                );
            }

            if (!context.isCurrentGeneration()) return false;
            context.markReady();
            PatchouliReloadHelper.requestReload("preload do livro " + identity.id());
            return true;
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.warn(
                "[projectbabel] Patchouli preload do livro {} falhou: {}",
                identity.id(),
                e.getMessage()
            );
            return false;
        }
    }

    private static BookIdentity resolveBookIdentity(Object book) {
        return PatchouliAccess.resolveBookIdentity(book);
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
                BookMarkupTranslator.collectPatchouliTextSegments(text, texts);
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
                TranslationPipeline.translateString(text, TranslationContext.book(false));
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
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        collectExhaustionSegments(component, segments);
        return !segments.isEmpty() && segments.stream().allMatch(text -> EXHAUSTED_TEXTS.contains(exhaustedKey(text)));
    }

    private static void collectExhaustionSegments(Component component, Set<String> segments) {
        if (component == null) return;
        BookMarkupTranslator.collectPatchouliTextSegments(component.getString(), segments);
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
            translated = TranslationPipeline.translateString(text, TranslationContext.book(true));
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

        String cached = TranslationPipeline.translateString(text, TranslationContext.book(false).asCacheOnly());
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


    private static final class PatchouliAdapter implements BookPreloadAdapter {
        private static final String ID = "Patchouli";

        @Override
        public String integrationId() {
            return ID;
        }

        @Override
        public BookIdentity resolveIdentity(Object source) {
            return PatchouliBookPreloader.resolveBookIdentity(source);
        }

        @Override
        public boolean runPreload(BookPreloadContext context, BookIdentity identity, String targetLanguage, Object source) {
            return PatchouliBookPreloader.runBookPreload(context, identity, targetLanguage);
        }
    }

}
