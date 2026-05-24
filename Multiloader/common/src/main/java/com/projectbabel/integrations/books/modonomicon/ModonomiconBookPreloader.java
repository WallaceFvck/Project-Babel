package com.projectbabel.integrations.books.modonomicon;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.schedule.PreloadAcceleration;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationTriageManager;
/**
 * Modonomicon renders markdown into component lists before page drawing.
 * Translating after that point breaks wrapping and links, so this preloads and
 * translates only markdown text fragments before the CommonMark parser runs.
 */
public final class ModonomiconBookPreloader {

    private static final long PRELOAD_WAIT_TIMEOUT_MS = 90_000L;
    private static final long OPEN_SCREEN_WAIT_MS = 180L;
    private static final int MAX_RETRY_PASSES = 2;
    private static final String RESOURCE_ROOT = "modonomicon/books";

    private static final Pattern MARKDOWN_TOKEN = Pattern.compile(
        "!?\\[[^\\]]*]\\([^)]*\\)|`[^`]*`|<[^>]+>|\\{[A-Za-z0-9_@:\\./\\-]+[^}]*\\}|\\$\\([^)]*\\)"
    );
    private static final Pattern BLOCK_PREFIX = Pattern.compile(
        "^(\\s{0,3}(?:#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+[.)]\\s+))(.+)$"
    );
    private static final Pattern TECHNICAL_TOKEN = Pattern.compile(
        "^[a-z0-9_.-]+(?::[a-z0-9_./-]+)?$"
    );
    private static final Pattern INLINE_MARKUP = Pattern.compile(
        "(\\*\\*|__|~~|\\*|_)([^\\r\\n]+?)\\1"
    );

    private static final BookPreloadAdapter ADAPTER = new ModonomiconAdapter();
    private static final Set<String> EXHAUSTED_FRAGMENTS = ConcurrentHashMap.newKeySet();

    private ModonomiconBookPreloader() {}

    public static void preloadBookForScreen(Object screen) {
        if (!isModonomiconLoaded()) return;

        Object book = ModonomiconAccess.bookFromScreen(screen);
        requestBookPreload(book, OPEN_SCREEN_WAIT_MS, false);
    }

    public static String translateForMarkdown(Object renderer, String text) {
        if (text == null || text.isEmpty()) return text;
        if (!isModonomiconLoaded()) return TranslationPipeline.translateString(text, TranslationContext.book(true));

        Object book = ModonomiconAccess.bookFromRenderer(renderer);
        requestBookPreload(book, 0L, false);

        String cached = translateMarkdown(text, TranslationMode.CACHE_ONLY);
        if (!cached.equals(text)) return cached;
        if (!needsExternalMarkdown(text)) return text;
        if (hasAnyExhaustedFragment(text)) return text;
        if (isPreloading()) {
            ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Modonomicon markdown miss while preload active; forcing current page text={}", truncate(text, 80));
            String translated = translateMarkdown(text, TranslationMode.BLOCKING);
            if (!translated.equals(text)) return translated;

            enqueueMarkdownFragments(text);
            return text;
        }

        ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Modonomicon blocking markdown text={}", truncate(text, 80));
        return translateMarkdown(text, TranslationMode.BLOCKING);
    }

    public static boolean isPreloading() {
        return BookPreloadCoordinator.isPreloading(ModonomiconAdapter.ID);
    }

    public static void resetPreloadState() {
        BookPreloadCoordinator.reset(ModonomiconAdapter.ID);
        EXHAUSTED_FRAGMENTS.clear();
    }

    private static boolean requestBookPreload(Object book, long waitMs, boolean warnOnTimeout) {
        return BookPreloadCoordinator.request(ADAPTER, book, waitMs, warnOnTimeout);
    }

    private static boolean runBookPreload(BookPreloadContext context, BookIdentity identity, String targetLang, Object book) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            ResourceManager manager = mc.getResourceManager();
            Map<ResourceLocation, Resource> resources = manager.listResources(
                RESOURCE_ROOT,
                path -> path.getPath().endsWith(".json") && belongsToBook(path, identity)
            );

            if (resources.isEmpty()) {
                context.markReady();
                return true;
            }

            LinkedHashSet<String> fragments = new LinkedHashSet<>(4096);
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8))) {

                        JsonElement json = JsonParser.parseReader(reader);
                        extract(json, "", fragments);
                    } catch (Exception e) {
                        ProjectBabelCommon.LOGGER.debug(
                            "[projectbabel] Modonomicon preload ignorou {}: {}",
                            entry.getKey(),
                            e.getMessage()
                        );
                    }
                }
            }

            int queued = enqueueFragments(fragments);
            ProjectBabelDebug.info(
                ProjectBabelDebug.BOOKS,
                "Modonomicon book={} files={} fragments={} queued={}",
                identity.id(),
                resources.size(),
                fragments.size(),
                queued
            );
            ProjectBabelCommon.LOGGER.info(
                "[projectbabel] Modonomicon livro {}: {} textos enfileirados ({} arquivos, alvo {}).",
                identity.id(),
                queued,
                resources.size(),
                targetLang
            );

            if (queued > 0) {
                boolean complete = TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                if (!complete) {
                    ProjectBabelCommon.LOGGER.warn(
                        "[projectbabel] Modonomicon livro {} ainda nao terminou apos {}s (fila={}, pendentes={}).",
                        identity.id(),
                        PRELOAD_WAIT_TIMEOUT_MS / 1000L,
                        TranslationManager.getInstance().getQueuedCount(),
                        TranslationManager.getInstance().getPendingCount()
                    );
                }
            }

            RetryResult retry = retryMissingFragments(fragments);
            if (retry.missing() > 0) {
                int exhausted = rememberExhaustedFragments(fragments);
                ProjectBabelCommon.LOGGER.warn(
                    "[projectbabel] Modonomicon livro {} ainda tem {} textos sem cache apos {} retries (forcados={}). Marcando {} textos como esgotados nesta sessao; nao tentara novamente.",
                    identity.id(),
                    retry.missing(),
                    MAX_RETRY_PASSES,
                    retry.forced(),
                    exhausted
                );
            } else if (retry.forced() > 0) {
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] Modonomicon livro {}: {} falhas recuperadas antes da abertura.",
                    identity.id(),
                    retry.forced()
                );
            }

            context.markReady();
            ModonomiconReloadHelper.requestPrerender(book, "preload do livro " + identity.id());
            return true;
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.warn(
                "[projectbabel] Modonomicon preload do livro {} falhou: {}",
                identity.id(),
                e.getMessage()
            );
            return false;
        }
    }

    private static void extract(JsonElement element, String key, Set<String> fragments) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isString()) return;
            if (!isTextField(key)) return;

            String text = resolveI18n(primitive.getAsString());
            collectMarkdownFragments(text, fragments);
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                extract(child, key, fragments);
            }
            return;
        }

        JsonObject obj = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String childKey = entry.getKey();
            if (isTechnicalField(childKey)) continue;
            extract(entry.getValue(), childKey, fragments);
        }
    }

    private static boolean isTextField(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("title")
            || normalized.matches("title\\d*")
            || normalized.equals("text")
            || normalized.equals("name")
            || normalized.endsWith("_name")
            || normalized.equals("description")
            || normalized.equals("tooltip")
            || normalized.equals("subtitle")
            || normalized.equals("body")
            || normalized.equals("header")
            || normalized.endsWith("_text")
            || normalized.endsWith("_title")
            || normalized.endsWith("_description");
    }

    private static boolean isTechnicalField(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("type")
            || normalized.equals("condition")
            || normalized.equals("conditions")
            || normalized.equals("item")
            || normalized.equals("items")
            || normalized.equals("icon")
            || normalized.equals("anchor")
            || normalized.equals("category")
            || normalized.equals("entry")
            || normalized.equals("parent")
            || normalized.equals("book")
            || normalized.equals("book_id")
            || normalized.equals("command")
            || normalized.equals("recipe")
            || normalized.equals("recipes")
            || normalized.equals("advancement")
            || normalized.equals("x")
            || normalized.equals("y");
    }

    private static int enqueueFragments(Set<String> fragments) {
        int queued = 0;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            for (String fragment : fragments) {
                if (!shouldTranslateFragment(fragment)) continue;
                if (EXHAUSTED_FRAGMENTS.contains(exhaustedKey(fragment))) continue;
                TranslationPipeline.translateString(fragment, TranslationContext.book(false));
                queued++;
            }
        }
        return queued;
    }

    private static void enqueueMarkdownFragments(String text) {
        LinkedHashSet<String> fragments = new LinkedHashSet<>();
        collectMarkdownFragments(text, fragments);
        enqueueFragments(fragments);
    }

    private static RetryResult retryMissingFragments(Set<String> fragments) {
        int forced = 0;
        int missing = 0;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            for (int pass = 0; pass < MAX_RETRY_PASSES; pass++) {
                List<String> retryTexts = collectRetryFragments(fragments);
                missing = retryTexts.size();
                if (missing == 0) break;

                List<CompletableFuture<Boolean>> futures = new ArrayList<>(retryTexts.size());
                for (String text : retryTexts) {
                    futures.add(CompletableFuture.supplyAsync(
                        () -> translateRetryFragment(text),
                        TranslationExecutors.preload()
                    ));
                }

                int changed = waitRetryBatch(futures);
                forced += changed;
                if (changed == 0) pauseBeforeRetry();
            }

            missing = 0;
            for (String text : fragments) {
                if (needsRetry(text)) missing++;
            }
        }

        return new RetryResult(forced, missing);
    }

    private static List<String> collectRetryFragments(Set<String> fragments) {
        List<String> retryTexts = new ArrayList<>();
        for (String text : fragments) {
            if (needsRetry(text)) retryTexts.add(text);
        }
        return retryTexts;
    }

    private static boolean translateRetryFragment(String text) {
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

    private static int rememberExhaustedFragments(Set<String> fragments) {
        int remembered = 0;
        for (String text : fragments) {
            if (!needsRetry(text)) continue;
            EXHAUSTED_FRAGMENTS.add(exhaustedKey(text));
            remembered++;
        }
        return remembered;
    }

    private static boolean needsRetry(String text) {
        if (!shouldTranslateFragment(text)) return false;
        if (EXHAUSTED_FRAGMENTS.contains(exhaustedKey(text))) return false;

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

    private static String translateMarkdown(String text, TranslationMode mode) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        boolean changed = false;

        int start = 0;
        while (start < text.length()) {
            int end = findLineEnd(text, start);
            boolean hasNewline = end < text.length();
            String line = text.substring(start, end);
            LineResult result = translateMarkdownLine(line, mode);
            out.append(result.text());
            if (hasNewline) out.append(text.charAt(end));
            changed |= result.changed();
            start = hasNewline ? end + 1 : end;
        }

        if (!changed) return text;
        return out.toString();
    }

    private static LineResult translateMarkdownLine(String line, TranslationMode mode) {
        if (line == null || line.isBlank()) return new LineResult(line, false);

        String prefix = "";
        String body = line;
        Matcher prefixMatcher = BLOCK_PREFIX.matcher(line);
        if (prefixMatcher.matches()) {
            prefix = prefixMatcher.group(1);
            body = prefixMatcher.group(2);
        }

        StringBuilder out = new StringBuilder(line.length() + 16);
        out.append(prefix);
        boolean changed = false;
        int start = 0;
        Matcher matcher = MARKDOWN_TOKEN.matcher(body);
        while (matcher.find()) {
            SegmentResult before = translateSegment(body.substring(start, matcher.start()), mode);
            out.append(before.text());
            changed |= before.changed();
            out.append(matcher.group());
            start = matcher.end();
        }

        SegmentResult tail = translateSegment(body.substring(start), mode);
        out.append(tail.text());
        changed |= tail.changed();

        if (!changed) return new LineResult(line, false);
        return new LineResult(out.toString(), true);
    }

    private static SegmentResult translateSegment(String segment, TranslationMode mode) {
        if (segment == null || segment.isEmpty()) return new SegmentResult(segment, false);

        StringBuilder out = null;
        boolean changed = false;
        int cursor = 0;
        Matcher matcher = INLINE_MARKUP.matcher(segment);
        while (matcher.find()) {
            SegmentResult before = translatePlainFragment(segment.substring(cursor, matcher.start()), mode);
            if (out != null || before.changed()) {
                if (out == null) out = new StringBuilder(segment.length() + 16).append(segment, 0, cursor);
                out.append(before.text());
            }
            changed |= before.changed();

            String delimiter = matcher.group(1);
            String inner = matcher.group(2);
            SegmentResult translatedInner = translatePlainFragment(inner, mode);
            if (out != null || translatedInner.changed()) {
                if (out == null) out = new StringBuilder(segment.length() + 16).append(segment, 0, matcher.start());
                out.append(delimiter).append(translatedInner.text()).append(delimiter);
            }
            changed |= translatedInner.changed();
            cursor = matcher.end();
        }

        SegmentResult tail = translatePlainFragment(segment.substring(cursor), mode);
        if (out == null && !changed && !tail.changed()) return new SegmentResult(segment, false);
        if (out == null) out = new StringBuilder(segment.length() + 16).append(segment, 0, cursor);
        out.append(tail.text());
        return new SegmentResult(out.toString(), true);
    }

    private static SegmentResult translatePlainFragment(String fragment, TranslationMode mode) {
        if (!shouldTranslateFragment(fragment)) return new SegmentResult(fragment, false);
        if (EXHAUSTED_FRAGMENTS.contains(exhaustedKey(fragment))) return new SegmentResult(fragment, false);

        String translated;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            translated = switch (mode) {
                case CACHE_ONLY -> TranslationPipeline.translateString(fragment, TranslationContext.book(false).asCacheOnly());
                case BLOCKING -> TranslationPipeline.translateString(fragment, TranslationContext.book(true));
                case ASYNC -> TranslationPipeline.translateString(fragment, TranslationContext.book(false));
            };
        }

        if (translated == null || translated.equals(fragment)) return new SegmentResult(fragment, false);
        return new SegmentResult(translated, true);
    }

    private static void collectMarkdownFragments(String text, Set<String> fragments) {
        if (text == null || text.isBlank()) return;

        int start = 0;
        while (start < text.length()) {
            int end = findLineEnd(text, start);
            collectMarkdownLineFragments(text.substring(start, end), fragments);
            start = end < text.length() ? end + 1 : end;
        }
    }

    private static void collectMarkdownLineFragments(String line, Set<String> fragments) {
        if (line == null || line.isBlank()) return;

        String body = line;
        Matcher prefixMatcher = BLOCK_PREFIX.matcher(line);
        if (prefixMatcher.matches()) {
            body = prefixMatcher.group(2);
        }

        int start = 0;
        Matcher matcher = MARKDOWN_TOKEN.matcher(body);
        while (matcher.find()) {
            addFragment(body.substring(start, matcher.start()), fragments);
            start = matcher.end();
        }
        addFragment(body.substring(start), fragments);
    }

    private static void addFragment(String fragment, Set<String> fragments) {
        if (fragment == null || fragment.isEmpty()) return;

        int cursor = 0;
        Matcher matcher = INLINE_MARKUP.matcher(fragment);
        while (matcher.find()) {
            addPlainFragment(fragment.substring(cursor, matcher.start()), fragments);
            addPlainFragment(matcher.group(2), fragments);
            cursor = matcher.end();
        }
        addPlainFragment(fragment.substring(cursor), fragments);
    }

    private static void addPlainFragment(String fragment, Set<String> fragments) {
        if (!shouldTranslateFragment(fragment)) return;
        fragments.add(fragment);
    }

    private static int findLineEnd(String text, int start) {
        int index = text.indexOf('\n', start);
        return index < 0 ? text.length() : index;
    }

    private static boolean needsExternalMarkdown(String text) {
        LinkedHashSet<String> fragments = new LinkedHashSet<>();
        collectMarkdownFragments(text, fragments);
        for (String fragment : fragments) {
            if (needsRetry(fragment)) return true;
        }
        return false;
    }

    private static boolean hasAnyExhaustedFragment(String text) {
        LinkedHashSet<String> fragments = new LinkedHashSet<>();
        collectMarkdownFragments(text, fragments);
        for (String fragment : fragments) {
            if (EXHAUSTED_FRAGMENTS.contains(exhaustedKey(fragment))) return true;
        }
        return false;
    }

    private static boolean shouldTranslateFragment(String text) {
        if (text == null) return false;

        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null) return false;
        plain = plain.replace("*", "").replace("_", "").replace("~", "").strip();
        if (plain.length() < 3) return false;
        if (!TextFilter.shouldTranslate(plain)) return false;
        if (looksTechnical(plain)) return false;

        return true;
    }

    private static boolean looksTechnical(String text) {
        if (text.indexOf(' ') >= 0) return false;
        if (text.startsWith("#") || text.startsWith("@")) return true;
        return TECHNICAL_TOKEN.matcher(text).matches()
            && (text.indexOf(':') >= 0 || text.indexOf('/') >= 0 || text.indexOf('.') >= 0 || text.indexOf('_') >= 0);
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

    private static BookIdentity resolveBookIdentity(Object book) {
        return ModonomiconAccess.resolveBookIdentity(book);
    }

    private static boolean belongsToBook(ResourceLocation resource, BookIdentity identity) {
        if (resource == null || identity == null) return false;
        if (!resource.getNamespace().equals(identity.namespace())) return false;

        String path = resource.getPath().replace('\\', '/');
        if (!path.startsWith(RESOURCE_ROOT + "/")) return false;

        String rest = path.substring((RESOURCE_ROOT + "/").length());
        for (String candidate : identity.pathCandidates()) {
            if (rest.startsWith(candidate + "/")) return true;
            if (rest.equals(candidate + ".json")) return true;
        }
        return false;
    }


    private static String exhaustedKey(String text) {
        return LanguageDetector.getTargetLanguageForApi() + '|' + text;
    }

    private static boolean isModonomiconLoaded() {
        try {
            return ProjectBabelCommon.platform().mods().isLoaded("modonomicon");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private enum TranslationMode {
        CACHE_ONLY,
        BLOCKING,
        ASYNC
    }

    private record LineResult(String text, boolean changed) {}

    private record SegmentResult(String text, boolean changed) {}

    private record RetryResult(int forced, int missing) {}


    private static final class ModonomiconAdapter implements BookPreloadAdapter {
        private static final String ID = "Modonomicon";

        @Override
        public String integrationId() {
            return ID;
        }

        @Override
        public BookIdentity resolveIdentity(Object source) {
            return ModonomiconBookPreloader.resolveBookIdentity(source);
        }

        @Override
        public boolean runPreload(BookPreloadContext context, BookIdentity identity, String targetLanguage, Object source) {
            return ModonomiconBookPreloader.runBookPreload(context, identity, targetLanguage, source);
        }
    }

}
