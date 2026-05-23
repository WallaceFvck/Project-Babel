package com.projectbabel.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.projectbabel.ProjectBabelMod;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Set<String> READY_BOOKS = ConcurrentHashMap.newKeySet();
    private static final Set<String> EXHAUSTED_FRAGMENTS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, CompletableFuture<Boolean>> RUNNING_BOOKS =
        new ConcurrentHashMap<>();

    private ModonomiconBookPreloader() {}

    public static void preloadBookForScreen(Object screen) {
        if (!isModonomiconLoaded()) return;

        Object book = invokeNoArg(screen, "getBook");
        if (book == null) {
            Object entry = readField(screen, "entry");
            book = invokeNoArg(entry, "getBook");
        }
        requestBookPreload(book, OPEN_SCREEN_WAIT_MS, false);
    }

    public static String translateForMarkdown(Object renderer, String text) {
        if (text == null || text.isEmpty()) return text;
        if (!isModonomiconLoaded()) return TranslationPipeline.translateStringBlocking(text);

        Object book = readField(renderer, "book");
        requestBookPreload(book, 0L, false);

        String cached = translateMarkdown(text, TranslationMode.CACHE_ONLY);
        if (!cached.equals(text)) return cached;
        if (!needsExternalMarkdown(text)) return text;
        if (hasAnyExhaustedFragment(text)) return text;
        if (isPreloading()) {
            ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Modonomicon markdown miss while preload active text={}", truncate(text, 80));
            enqueueMarkdownFragments(text);
            return text;
        }

        ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "Modonomicon blocking markdown text={}", truncate(text, 80));
        return translateMarkdown(text, TranslationMode.BLOCKING);
    }

    public static boolean isPreloading() {
        return !RUNNING_BOOKS.isEmpty();
    }

    public static void resetPreloadState() {
        READY_BOOKS.clear();
        RUNNING_BOOKS.clear();
        EXHAUSTED_FRAGMENTS.clear();
    }

    private static boolean requestBookPreload(Object book, long waitMs, boolean warnOnTimeout) {
        BookIdentity identity = resolveBookIdentity(book);
        if (identity == null) return false;

        String targetLang = LanguageDetector.getTargetLanguageForApi();
        String preloadKey = targetLang + ":" + identity.id();
        if (READY_BOOKS.contains(preloadKey)) return true;

        CompletableFuture<Boolean> future = RUNNING_BOOKS.computeIfAbsent(preloadKey,
            ignored -> CompletableFuture.supplyAsync(
                () -> PreloadAcceleration.supply(() -> runBookPreload(preloadKey, identity, targetLang, book)),
                TranslationExecutors.preload()
            ));

        if (waitMs <= 0L) {
            if (!future.isDone()) return false;
            try {
                return Boolean.TRUE.equals(future.getNow(Boolean.FALSE));
            } catch (Exception ignored) {
                return false;
            }
        }

        try {
            return Boolean.TRUE.equals(future.get(Math.max(0L, waitMs), TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            if (warnOnTimeout) {
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] Modonomicon preload do livro {} nao terminou a tempo: {}",
                    identity.id(),
                    e.getMessage()
                );
            }
            return false;
        }
    }

    private static boolean runBookPreload(String preloadKey, BookIdentity identity, String targetLang, Object book) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            ResourceManager manager = mc.getResourceManager();
            Map<ResourceLocation, Resource> resources = manager.listResources(
                RESOURCE_ROOT,
                path -> path.getPath().endsWith(".json") && belongsToBook(path, identity)
            );

            if (resources.isEmpty()) {
                READY_BOOKS.add(preloadKey);
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
                        ProjectBabelMod.LOGGER.debug(
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
            ProjectBabelMod.LOGGER.info(
                "[projectbabel] Modonomicon livro {}: {} textos enfileirados ({} arquivos, alvo {}).",
                identity.id(),
                queued,
                resources.size(),
                targetLang
            );

            if (queued > 0) {
                boolean complete = TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                if (!complete) {
                    ProjectBabelMod.LOGGER.warn(
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
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] Modonomicon livro {} ainda tem {} textos sem cache apos {} retries (forcados={}). Marcando {} textos como esgotados nesta sessao; nao tentara novamente.",
                    identity.id(),
                    retry.missing(),
                    MAX_RETRY_PASSES,
                    retry.forced(),
                    exhausted
                );
            } else if (retry.forced() > 0) {
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Modonomicon livro {}: {} falhas recuperadas antes da abertura.",
                    identity.id(),
                    retry.forced()
                );
            }

            READY_BOOKS.add(preloadKey);
            ModonomiconReloadHelper.requestPrerender(book, "preload do livro " + identity.id());
            return true;
        } finally {
            RUNNING_BOOKS.remove(preloadKey);
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
            || normalized.equals("text")
            || normalized.equals("name")
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
                TranslationPipeline.translateString(fragment);
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
        if (!shouldTranslateFragment(segment)) return new SegmentResult(segment, false);
        if (EXHAUSTED_FRAGMENTS.contains(exhaustedKey(segment))) return new SegmentResult(segment, false);

        String translated;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            translated = switch (mode) {
                case CACHE_ONLY -> TranslationPipeline.translateStringCacheOnly(segment);
                case BLOCKING -> TranslationPipeline.translateStringBlocking(segment);
                case ASYNC -> TranslationPipeline.translateString(segment);
            };
        }

        if (translated == null || translated.equals(segment)) return new SegmentResult(segment, false);
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
        if (book == null) return null;

        Object id = invokeNoArg(book, "getId");
        if (id == null) id = readField(book, "id");

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

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0) return null;
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static String exhaustedKey(String text) {
        return LanguageDetector.getTargetLanguageForApi() + '|' + text;
    }

    private static boolean isModonomiconLoaded() {
        try {
            return ModList.get().isLoaded("modonomicon");
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
