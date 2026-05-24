package com.projectbabel.integrations.ftbquests;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.pipeline.TranslationTriageManager;
/**
 * Clean FTB Quests integration.
 *
 * The important rule is the same one used by the dedicated FTB Quests translator:
 * never rewrite the already-built Component tree. Read the raw FTB Quests text,
 * translate that raw string, then pass it back to TextUtils.parseRawText so FTB's
 * own markup parser keeps colors, links, pagebreaks, icons and hover/click data.
 */
public final class FTBQuestAutoTranslator {

    private static final String SOURCE = "ftbquests";
    private static final int MAX_CACHE_ENTRIES = 8192;

    private static final Map<String, String> RAW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<String>> RAW_PENDING = new ConcurrentHashMap<>();
    private static final Set<String> PRELOAD_PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> PRELOAD_READY = ConcurrentHashMap.newKeySet();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicBoolean REFRESH_PENDING = new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> PARSING_FTB_RAW = ThreadLocal.withInitial(() -> false);

    private FTBQuestAutoTranslator() {}

    public static boolean canRun() {
        try {
            return ProjectBabelCommon.config().isEnabled()
                && ProjectBabelCommon.platform().mods().isLoaded("ftbquests")
                && LanguageDetector.shouldModBeActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void reset() {
        GENERATION.incrementAndGet();
        RAW_CACHE.clear();
        RAW_PENDING.clear();
        PRELOAD_PENDING.clear();
        PRELOAD_READY.clear();
    }

    public static boolean isParsingFtbRawText() {
        return Boolean.TRUE.equals(PARSING_FTB_RAW.get());
    }

    public static Component translatedTitle(Object owner) {
        if (!canRun() || owner == null) return null;
        String raw = rawTitle(owner);
        if (!hasVisibleText(raw)) return null;
        String translated = translateRaw(owner, "title", raw, true);
        return changed(raw, translated) ? parseAndProtect(translated) : null;
    }

    public static Component translatedSubtitle(Object quest) {
        if (!canRun() || quest == null) return null;
        String raw = rawSubtitle(quest);
        if (!hasVisibleText(raw)) return null;
        String translated = translateRaw(quest, "subtitle", raw, true);
        return changed(raw, translated) ? parseAndProtect(translated) : null;
    }

    public static List<Component> translatedDescription(Object quest) {
        if (!canRun() || quest == null) return null;
        Object rawDescription = FTBQuestAccess.rawDescription(quest);
        if (!(rawDescription instanceof Collection<?> collection) || collection.isEmpty()) return null;

        List<Component> result = new ArrayList<>(collection.size());
        boolean anyChanged = false;
        int index = 0;

        for (Object entry : collection) {
            if (!(entry instanceof String raw)) continue;

            String line = raw;
            if (hasVisibleText(raw)) {
                String translated = translateRaw(quest, "description." + index, raw, true);
                if (changed(raw, translated)) {
                    line = translated;
                    anyChanged = true;
                }
            }

            Component component = parseAndProtect(line);
            result.add(component);
            index++;
        }

        return anyChanged ? result : null;
    }

    public static Component translateComponentForFtbUi(Component original) {
        if (!canRun() || original == null) return original;
        if (TranslationSkipRegistry.shouldSkipIdentity(original)) return original;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            Component cached = TranslationPipeline.translateComponent(original, TranslationContext.quest(false).asCacheOnly());
            if (cached != original) return protect(cached);

            if (!TranslationPipeline.needsExternalTranslation(original)) return original;

            Component blocking = TranslationPipeline.translateComponent(original, TranslationContext.quest(true));
            if (blocking != original) {
                TranslationTriageManager.getInstance().warmComponent(original, blocking);
                return protect(blocking);
            }

            return TranslationPipeline.translateComponent(original, TranslationContext.quest(false));
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static void preloadQuest(Object quest) {
        if (!canRun() || quest == null) return;
        String key = stateKey("quest", quest);
        preload(key, quest, () -> {
            warmRaw(quest, "title", rawTitle(quest));
            warmRaw(quest, "subtitle", rawSubtitle(quest));
            Object description = FTBQuestAccess.rawDescription(quest);
            if (description instanceof Collection<?> collection) {
                int i = 0;
                for (Object entry : collection) {
                    if (entry instanceof String raw) warmRaw(quest, "description." + i, raw);
                    i++;
                }
            }
            warmNestedQuestObjects(FTBQuestAccess.tasks(quest));
            warmNestedQuestObjects(FTBQuestAccess.rewards(quest));
        });
    }

    public static void preloadChapter(Object chapter) {
        if (!canRun() || chapter == null) return;
        String key = stateKey("chapter", chapter);
        preload(key, chapter, () -> {
            warmRaw(chapter, "title", rawTitle(chapter));
            Object quests = FTBQuestAccess.quests(chapter);
            if (quests instanceof Collection<?> collection) {
                for (Object quest : collection) preloadQuest(quest);
            }
        });
    }

    public static void preloadFile(Object file) {
        if (!canRun() || file == null) return;
        String key = stateKey("file", file);
        preload(key, file, () -> {
            Set<Object> visited = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            Object defaultGroup = FTBQuestAccess.defaultChapterGroup(file);
            collectAndWarmTree(defaultGroup, visited, 0);
            Object groups = FTBQuestAccess.chapterGroups(file);
            if (groups instanceof Collection<?> collection) {
                for (Object group : collection) collectAndWarmTree(group, visited, 0);
            }
            Object chapters = FTBQuestAccess.allChapters(file);
            if (chapters instanceof Collection<?> collection) {
                for (Object chapter : collection) collectAndWarmTree(chapter, visited, 0);
            }
            Object visibleChapters = FTBQuestAccess.visibleChapters(file);
            if (visibleChapters instanceof Collection<?> collection) {
                for (Object chapter : collection) collectAndWarmTree(chapter, visited, 0);
            }
        });
    }

    public static void preloadCurrentClientFile() {
        Object file = currentClientQuestFile();
        if (file != null) preloadFile(file);
    }

    public static void onScreenOpened(Object screen) {
        Object file = FTBQuestAccess.screenFile(screen);
        if (file != null) preloadFile(file);
        Object chapter = FTBQuestAccess.selectedChapter(screen);
        if (chapter != null) preloadChapter(chapter);
        Object quest = FTBQuestAccess.viewedQuest(screen);
        if (quest != null) preloadQuest(quest);
    }

    public static void onQuestViewed(Object quest) {
        if (quest == null) return;
        clearCachedData(quest);
        preloadQuest(quest);
    }

    public static void onChapterSelected(Object chapter) {
        if (chapter == null) return;
        clearCachedData(chapter);
        preloadChapter(chapter);
    }

    public static void onQuestFileSynced(Object file) {
        reset();
        Object actual = file != null ? file : currentClientQuestFile();
        if (actual != null) preloadFile(actual);
        requestRefresh(actual, "quest file synced");
    }

    public static Object currentClientQuestFile() {
        return FTBQuestAccess.currentClientQuestFile();
    }

    public static void requestRefresh(Object cause, String reason) {
        if (!canRun()) return;
        if (!REFRESH_PENDING.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                REFRESH_PENDING.set(false);
                return;
            }

            mc.execute(() -> {
                try {
                    refreshNow(cause);
                } catch (Throwable t) {
                    ProjectBabelCommon.LOGGER.debug("[projectbabel] FTB Quests refresh failed after {}: {}", reason, t.toString());
                } finally {
                    REFRESH_PENDING.set(false);
                }
            });
        }, TranslationExecutors.preload());
    }

    public static void refreshNow(Object cause) {
        Object file = cause;
        if (file == null || !isClientQuestFile(file)) {
            Object fromCause = FTBQuestAccess.questFileFrom(cause);
            file = fromCause != null ? fromCause : currentClientQuestFile();
        }

        clearCachedData(file);
        Minecraft mc = Minecraft.getInstance();
        Object screen = mc == null ? null : mc.screen;
        if (screen == null || !isFtbQuestScreen(screen)) return;

        Object screenFile = FTBQuestAccess.screenFile(screen);
        if (file != null && screenFile != null && file != screenFile) return;

        FTBQuestAccess.refreshQuestScreen(screen);
    }

    private static void preload(String key, Object refreshCause, Runnable work) {
        long generation = GENERATION.get();
        if (key == null || PRELOAD_READY.contains(key) || !PRELOAD_PENDING.add(key)) return;

        CompletableFuture.runAsync(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] FTB Quests preload failed: {}", t.toString());
            }
        }, TranslationExecutors.preload()).whenComplete((ok, error) -> {
            PRELOAD_PENDING.remove(key);
            if (generation == GENERATION.get() && error == null) {
                PRELOAD_READY.add(key);
                clearCachedData(refreshCause);
                requestRefresh(refreshCause, "preload finished");
            }
        });
    }

    private static void collectAndWarmTree(Object object, Set<Object> visited, int depth) {
        if (object == null || depth > 5 || !visited.add(object)) return;

        warmRaw(object, "title", rawTitle(object));
        warmRaw(object, "group", stringField(object, "group"));

        Object quests = FTBQuestAccess.quests(object);
        if (quests instanceof Collection<?> questCollection) {
            for (Object quest : questCollection) {
                warmRaw(quest, "title", rawTitle(quest));
                warmRaw(quest, "subtitle", rawSubtitle(quest));
            }
        }

        for (String method : List.of("getChapters", "getVisibleChapters", "getChapterGroups", "getChildren")) {
            Object children = FTBQuestAccess.childrenByMethod(object, method);
            if (children instanceof Collection<?> collection) {
                for (Object child : collection) collectAndWarmTree(child, visited, depth + 1);
            }
        }
    }

    private static void warmNestedQuestObjects(Object value) {
        if (!(value instanceof Collection<?> collection)) return;
        for (Object entry : collection) {
            warmRaw(entry, "title", rawTitle(entry));
            Object title = FTBQuestAccess.getTitle(entry);
            if (title instanceof Component component) translateComponentForFtbUi(component);
            Object altTitle = FTBQuestAccess.getAltTitle(entry);
            if (altTitle instanceof Component component) translateComponentForFtbUi(component);
            Object buttonText = FTBQuestAccess.getButtonText(entry);
            if (buttonText instanceof Component component) translateComponentForFtbUi(component);
        }
    }

    private static void warmRaw(Object owner, String field, String raw) {
        if (raw == null || raw.isEmpty()) return;

        String resolved = resolveLanguageReference(raw);
        if (!shouldTranslateRaw(resolved)) {
            if (changed(raw, resolved)) {
                remember(rawCacheKey(field, resolved), resolved, resolved);
            }
            return;
        }

        String cacheKey = rawCacheKey(field, resolved);
        if (RAW_CACHE.containsKey(cacheKey)) return;

        String translated = cacheOnlyTranslation(resolved);
        if (!changed(resolved, translated)) {
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                translated = TranslationPipeline.translateString(resolved, TranslationContext.quest(true));
            } catch (Throwable ignored) {
                translated = resolved;
            }
        }

        if (changed(resolved, translated)) {
            remember(cacheKey, resolved, sanitizeTranslation(resolved, translated));
        } else if (changed(raw, resolved)) {
            remember(cacheKey, resolved, resolved);
        }
    }

    private static String translateRaw(Object owner, String field, String raw, boolean scheduleRefresh) {
        if (raw == null || raw.isEmpty()) return raw;

        String resolved = resolveLanguageReference(raw);
        if (!shouldTranslateRaw(resolved)) {
            return changed(raw, resolved) ? resolved : raw;
        }

        String cacheKey = rawCacheKey(field, resolved);
        String cached = RAW_CACHE.get(cacheKey);
        if (cached != null) return cached;

        String cacheOnly = cacheOnlyTranslation(resolved);
        if (changed(resolved, cacheOnly)) {
            String sanitized = sanitizeTranslation(resolved, cacheOnly);
            remember(cacheKey, resolved, sanitized);
            return sanitized;
        }

        scheduleRawTranslation(owner, field, resolved, cacheKey, scheduleRefresh);
        return changed(raw, resolved) ? resolved : raw;
    }

    private static String cacheOnlyTranslation(String raw) {
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            return TranslationPipeline.translateString(raw, TranslationContext.quest(false).asCacheOnly());
        } catch (Throwable ignored) {
            return raw;
        }
    }

    private static void scheduleRawTranslation(Object owner, String field, String raw, String cacheKey, boolean scheduleRefresh) {
        RAW_PENDING.computeIfAbsent(cacheKey, ignored -> CompletableFuture.supplyAsync(() -> {
            try (TextFilter.ScreenFilterBypass bypass = TextFilter.bypassScreenFilter()) {
                return TranslationPipeline.translateString(raw, TranslationContext.quest(true));
            } catch (Throwable t) {
                return raw;
            }
        }, TranslationExecutors.preload()).whenComplete((translated, error) -> {
            RAW_PENDING.remove(cacheKey);
            if (error != null || !changed(raw, translated)) return;

            String sanitized = sanitizeTranslation(raw, translated);
            if (!changed(raw, sanitized)) return;

            remember(cacheKey, raw, sanitized);
            clearCachedData(owner);
            if (scheduleRefresh) requestRefresh(owner, "raw text translated");
        }));
    }

    private static void remember(String cacheKey, String original, String translated) {
        if (RAW_CACHE.size() >= MAX_CACHE_ENTRIES) RAW_CACHE.clear();
        RAW_CACHE.put(cacheKey, translated);
        TranslationTriageManager.getInstance().warmText(original, translated);
        TranslationSkipRegistry.skipText(translated);
    }

    private static String sanitizeTranslation(String original, String translated) {
        if (translated == null || translated.isBlank()) return original;
        String result = TextFormatUtils.collapseExactDuplicateTranslation(original, translated);
        result = TextFormatUtils.collapseRepeatedTranslation(result);
        result = TextFormatUtils.preserveEdgeWhitespace(original, result);
        return result == null || result.isBlank() ? original : result;
    }

    private static Component parseAndProtect(String raw) {
        Component component = parseRawText(raw);
        TranslationSkipRegistry.skipText(raw);
        return protect(component);
    }

    private static Component protect(Component component) {
        if (component != null) TranslationSkipRegistry.skip(component);
        return component;
    }

    private static Component parseRawText(String raw) {
        if (raw == null) return Component.empty();

        Boolean previous = PARSING_FTB_RAW.get();
        PARSING_FTB_RAW.set(true);
        try {
            return FTBQuestAccess.parseRawText(raw, FTBQuestAccess.registryLookup());
        } finally {
            PARSING_FTB_RAW.set(previous);
        }
    }

    /** Same key resolution rule as the reference mod: {some.lang.key} becomes I18n.get(key). */
    private static String resolveLanguageReference(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}") || trimmed.length() <= 2) return raw;

        String key = trimmed.substring(1, trimmed.length() - 1);
        if (key.startsWith("@") || key.contains(":") || key.startsWith("\"") || key.startsWith("[")) return raw;

        try {
            String resolved = I18n.get(key);
            if (resolved != null && !resolved.isBlank() && !resolved.equals(key)) {
                return TextFormatUtils.preserveEdgeWhitespace(raw, resolved);
            }
        } catch (Throwable ignored) {
        }
        return raw;
    }

    private static boolean shouldTranslateRaw(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String trimmed = raw.trim();
        if (trimmed.equals("{@pagebreak}")) return false;
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains(":")) return false;
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && (trimmed.contains("{") || trimmed.contains("\"") || trimmed.contains(":"))) return false;
        String stripped = TextFormatUtils.stripFormatting(raw).trim();
        return stripped.chars().anyMatch(Character::isLetter);
    }

    private static boolean hasVisibleText(String raw) {
        return raw != null && !raw.isBlank() && shouldTranslateRaw(resolveLanguageReference(raw));
    }

    private static boolean changed(String original, String translated) {
        return translated != null && original != null && !translated.equals(original);
    }

    private static String rawCacheKey(String field, String raw) {
        return LanguageDetector.getTargetLanguageForApi() + '\u0001' + SOURCE + '\u0001' + field + '\u0001' + raw;
    }

    private static String stateKey(String type, Object object) {
        return LanguageDetector.getTargetLanguageForApi() + ':' + type + ':' + objectKey(object);
    }

    private static String objectKey(Object object) {
        return FTBQuestAccess.objectKey(object);
    }

    private static String rawTitle(Object owner) {
        return FTBQuestAccess.rawTitle(owner);
    }

    private static String rawSubtitle(Object quest) {
        return FTBQuestAccess.rawSubtitle(quest);
    }

    private static String stringField(Object target, String name) {
        return FTBQuestAccess.stringField(target, name);
    }

    private static boolean isFtbQuestScreen(Object screen) {
        return FTBQuestAccess.isQuestScreen(screen);
    }

    private static boolean isClientQuestFile(Object object) {
        return FTBQuestAccess.isClientQuestFile(object);
    }

    private static void clearCachedData(Object object) {
        FTBQuestAccess.clearCachedData(object);
    }
}
