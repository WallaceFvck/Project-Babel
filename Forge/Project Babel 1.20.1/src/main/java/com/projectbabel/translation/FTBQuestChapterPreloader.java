package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class FTBQuestChapterPreloader {

    private static final long CHAPTER_PRELOAD_TIMEOUT_MS = 120_000L;
    private static final ConcurrentHashMap.KeySetView<String, Boolean> READY =
        ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> RUNNING =
        ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> RUNNING_QUESTS =
        ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE =
        new ConcurrentHashMap<>();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> MISSING_METHODS =
        ConcurrentHashMap.newKeySet();

    private FTBQuestChapterPreloader() {}

    public static void requestPreload(Object chapter) {
        if (chapter == null || !ModList.get().isLoaded("ftbquests")) return;

        String key = LanguageDetector.getTargetLanguageForApi() + ':' + keyOf(chapter);
        if (READY.contains(key)) return;
        if (!RUNNING.add(key)) return;

        List<Object> quests = collectQuests(chapter);
        List<String> questKeys = markRunningQuests(quests);
        ProjectBabelDebug.info(
            ProjectBabelDebug.QUESTS,
            "chapter preload requested key={} quests={}",
            key,
            quests.size()
        );

        CompletableFuture.runAsync(() -> {
            ChapterPreloadResult result = ChapterPreloadResult.EMPTY;
            try {
                result = PreloadAcceleration.supply(() -> preloadChapter(quests));
                ProjectBabelDebug.info(
                    ProjectBabelDebug.QUESTS,
                    "chapter preload result ready={} failed={}",
                    result.ready(),
                    result.failed()
                );
                if (result.failed() == 0) {
                    READY.add(key);
                    ProjectBabelMod.LOGGER.info(
                        "[projectbabel] FTB Quests chapter preload concluido: {} quests aquecidas.",
                        result.ready()
                    );
                } else {
                    ProjectBabelMod.LOGGER.warn(
                        "[projectbabel] FTB Quests chapter preload parcial: {} prontas, {} falharam. A aba tentara novamente.",
                        result.ready(),
                        result.failed()
                    );
                }
            } catch (Exception e) {
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] FTB Quests chapter preload falhou: {}",
                    e.getMessage()
                );
            } finally {
                RUNNING.remove(key);
                for (String questKey : questKeys) {
                    RUNNING_QUESTS.remove(questKey);
                }
            }
        }, TranslationExecutors.preload());
    }

    public static void requestPreloadSelectedChapter(Object screen) {
        if (screen == null || !ModList.get().isLoaded("ftbquests")) return;
        requestPreload(readField(screen, "selectedChapter"));
    }

    public static void reset() {
        READY.clear();
        RUNNING.clear();
        RUNNING_QUESTS.clear();
    }

    public static boolean isPreloading() {
        return !RUNNING.isEmpty();
    }

    public static boolean isQuestPreloading(Object quest) {
        String questKey = FTBQuestFirstOpenTracker.keyOf(quest);
        return questKey != null && RUNNING_QUESTS.contains(questKey);
    }

    public static boolean shouldAvoidUiBlocking(Object quest) {
        return isPreloading() || isQuestPreloading(quest);
    }

    private static List<Object> collectQuests(Object chapter) {
        Object quests = invokeNoArg(chapter, "getQuests");
        if (!(quests instanceof Collection<?> collection) || collection.isEmpty()) return List.of();

        List<Object> result = new ArrayList<>(collection.size());
        for (Object quest : collection) {
            if (quest != null) result.add(quest);
        }
        return result;
    }

    private static List<String> markRunningQuests(List<Object> quests) {
        if (quests.isEmpty()) return List.of();

        List<String> keys = new ArrayList<>(quests.size());
        for (Object quest : quests) {
            String questKey = FTBQuestFirstOpenTracker.keyOf(quest);
            if (questKey == null) continue;
            RUNNING_QUESTS.add(questKey);
            keys.add(questKey);
        }
        return keys;
    }

    private static ChapterPreloadResult preloadChapter(List<Object> quests) {
        if (quests.isEmpty()) return ChapterPreloadResult.EMPTY;

        List<CompletableFuture<Boolean>> futures = new ArrayList<>(quests.size());
        long deadline = System.currentTimeMillis() + CHAPTER_PRELOAD_TIMEOUT_MS;

        for (Object quest : quests) {
            futures.add(CompletableFuture.supplyAsync(
                () -> FTBQuestFirstOpenTracker.preloadFirstOpen(quest),
                TranslationExecutors.preload()
            ));
        }

        int ready = 0;
        int failed = 0;
        for (CompletableFuture<Boolean> future : futures) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                failed += futures.size() - ready - failed;
                break;
            }

            try {
                if (Boolean.TRUE.equals(future.get(remaining, TimeUnit.MILLISECONDS))) {
                    ready++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        if (ready + failed < futures.size()) {
            failed += futures.size() - ready - failed;
        }

        for (CompletableFuture<Boolean> future : futures) {
            if (!future.isDone()) {
                future.cancel(false);
            }
        }

        return new ChapterPreloadResult(ready, failed);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = findNoArgMethod(target.getClass(), methodName);
            if (method == null) return null;
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }

        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        String key = type.getName() + '#' + methodName;
        if (MISSING_METHODS.contains(key)) return null;

        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        try {
            Method method;
            try {
                method = type.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                method = type.getDeclaredMethod(methodName);
            }
            method.setAccessible(true);
            METHOD_CACHE.put(key, method);
            return method;
        } catch (Exception e) {
            MISSING_METHODS.add(key);
            return null;
        }
    }

    private static String keyOf(Object chapter) {
        Object code = invokeNoArg(chapter, "getCodeString");
        if (code instanceof String string && !string.isBlank()) return string;
        Object id = invokeNoArg(chapter, "getId");
        if (id != null) return chapter.getClass().getName() + ':' + id;
        return chapter.getClass().getName() + '@' + System.identityHashCode(chapter);
    }

    private record ChapterPreloadResult(int ready, int failed) {
        private static final ChapterPreloadResult EMPTY = new ChapterPreloadResult(0, 0);
    }
}
