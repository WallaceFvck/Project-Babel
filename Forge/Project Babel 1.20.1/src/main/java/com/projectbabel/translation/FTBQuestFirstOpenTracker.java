package com.projectbabel.translation;

import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class FTBQuestFirstOpenTracker {

    private static final long FIRST_OPEN_TIMEOUT_MS = 60_000L;
    private static final long PENDING_WAIT_STEP_MS = 25L;
    private static final ConcurrentHashMap<String, State> QUEST_STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> MISSING_METHODS =
        ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> PRELOADING = ThreadLocal.withInitial(() -> false);

    private enum State {
        PENDING,
        READY,
        FAILED_RETRYABLE
    }

    private FTBQuestFirstOpenTracker() {}

    public static boolean preloadFirstOpen(Object quest) {
        return PreloadAcceleration.supply(() -> preloadFirstOpenInternal(quest));
    }

    private static boolean preloadFirstOpenInternal(Object quest) {
        String key = keyOf(quest);
        if (key == null) return false;
        if (PRELOADING.get()) return false;

        State state = QUEST_STATES.get(key);
        if (state == State.READY) return true;
        if (state == State.PENDING) {
            awaitPending(key);
            return QUEST_STATES.get(key) == State.READY;
        }
        if (!claim(key)) {
            awaitPending(key);
            return QUEST_STATES.get(key) == State.READY;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        PRELOADING.set(true);
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            clearQuestCachedData(quest);
            collectBlockingTranslations(invokeNoArg(quest, "getRawTitle"), futures);
            collectBlockingTranslations(invokeNoArg(quest, "getRawSubtitle"), futures);
            collectBlockingTranslations(invokeNoArg(quest, "getRawDescription"), futures);
            collectBlockingTranslations(invokeNoArg(quest, "getTitle"), futures);
            collectBlockingTranslations(invokeNoArg(quest, "getSubtitle"), futures);
            collectBlockingTranslations(invokeNoArg(quest, "getDescription"), futures);
            collectNestedQuestObjects(invokeNoArg(quest, "getTasks"), futures);
            collectNestedQuestObjects(invokeNoArg(quest, "getRewards"), futures);
        }

        boolean ready = futures.isEmpty();
        try {
            if (!ready) {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(FIRST_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                ready = true;
            }
        } catch (Exception ignored) {
            ready = false;
        } finally {
            PRELOADING.remove();
            QUEST_STATES.put(key, ready ? State.READY : State.FAILED_RETRYABLE);
            if (ready) {
                clearQuestCachedData(quest);
            }
        }

        return ready;
    }

    public static void preloadForUiOpen(Object quest) {
        if (FTBQuestChapterPreloader.shouldAvoidUiBlocking(quest)) return;
        preloadFirstOpen(quest);
    }

    public static Component translate(Object quest, Component component) {
        if (component == null) return null;
        boolean avoidBlocking = FTBQuestChapterPreloader.shouldAvoidUiBlocking(quest);
        if (quest != null && !PRELOADING.get() && !avoidBlocking) {
            preloadFirstOpen(quest);
        }
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            boolean templateCandidate = ComponentTemplateTranslator.isCandidate(component);
            if (templateCandidate) {
                Component templated = ComponentTemplateTranslator.translateCacheOnly(component);
                if (templated != component) return templated;
            } else {
                Component cached = TranslationPipeline.translateComponentTreeCacheOnly(component);
                if (cached != component) return cached;
            }
            if (!PRELOADING.get() && !avoidBlocking) {
                Component blocking = templateCandidate
                    ? ComponentTemplateTranslator.translateBlocking(component)
                    : TranslationPipeline.translateComponentTreeBlocking(component);
                if (blocking != component) {
                    TranslationTriageManager.getInstance().warmComponent(component, blocking);
                    return blocking;
                }
            }
            if (templateCandidate) return component;
            return TranslationPipeline.translateComponentTree(component);
        }
    }

    public static String translate(Object quest, String text) {
        if (text == null || text.isEmpty()) return text;
        boolean avoidBlocking = FTBQuestChapterPreloader.shouldAvoidUiBlocking(quest);
        if (quest != null && !PRELOADING.get() && !avoidBlocking) {
            preloadFirstOpen(quest);
        }
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            String cached = TranslationPipeline.translateStringCacheOnly(text);
            if (cached != null && !cached.equals(text)) return cached;
            if (!PRELOADING.get() && !avoidBlocking) {
                String blocking = TranslationPipeline.translateStringBlocking(text);
                if (blocking != null && !blocking.equals(text)) {
                    TranslationTriageManager.getInstance().warmText(text, blocking);
                    return blocking;
                }
            }
            return TranslationPipeline.translateString(text);
        }
    }

    public static void reset() {
        QUEST_STATES.clear();
    }

    private static boolean claim(String key) {
        State current = QUEST_STATES.putIfAbsent(key, State.PENDING);
        if (current == null) return true;
        if (current == State.READY || current == State.PENDING) return false;
        return QUEST_STATES.replace(key, State.FAILED_RETRYABLE, State.PENDING);
    }

    private static void awaitPending(String key) {
        long deadline = System.currentTimeMillis() + FIRST_OPEN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            State state = QUEST_STATES.get(key);
            if (state != State.PENDING) return;
            try {
                Thread.sleep(PENDING_WAIT_STEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        QUEST_STATES.replace(key, State.PENDING, State.FAILED_RETRYABLE);
    }

    private static void collectBlockingTranslations(
        Object value,
        List<CompletableFuture<Void>> futures
    ) {
        if (value instanceof Component component) {
            futures.add(translateAndWarm(component));
        } else if (value instanceof String string) {
            futures.add(translateAndWarm(string));
        } else if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry instanceof Component component) {
                    futures.add(translateAndWarm(component));
                } else if (entry instanceof String string) {
                    futures.add(translateAndWarm(string));
                }
            }
        }
    }

    private static void collectNestedQuestObjects(
        Object value,
        List<CompletableFuture<Void>> futures
    ) {
        if (!(value instanceof Collection<?> collection)) return;

        for (Object entry : collection) {
            collectBlockingTranslations(invokeNoArg(entry, "getTitle"), futures);
            collectBlockingTranslations(invokeNoArg(entry, "getSubtitle"), futures);
            collectBlockingTranslations(invokeNoArg(entry, "getDescription"), futures);
            collectBlockingTranslations(invokeNoArg(entry, "getAltTitle"), futures);
            collectBlockingTranslations(invokeNoArg(entry, "getButtonText"), futures);
        }
    }

    private static CompletableFuture<Void> translateAndWarm(Component component) {
        return CompletableFuture.supplyAsync(() -> {
                try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                    return ComponentTemplateTranslator.isCandidate(component)
                        ? ComponentTemplateTranslator.translateBlocking(component)
                        : TranslationPipeline.translateComponentTreeBlocking(component);
                }
            }, TranslationExecutors.preload())
            .thenAccept(translated -> {
                try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                    TranslationTriageManager.getInstance().warmComponent(component, translated);
                }
            });
    }

    private static CompletableFuture<Void> translateAndWarm(String text) {
        return CompletableFuture
            .supplyAsync(() -> {
                try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                    return TranslationPipeline.translateStringBlocking(text);
                }
            }, TranslationExecutors.preload())
            .thenAccept(translated -> {
                try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                    TranslationTriageManager.getInstance().warmText(text, translated);
                }
            });
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

    private static void clearQuestCachedData(Object quest) {
        invokeNoArg(quest, "clearCachedData");
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

    public static String keyOf(Object quest) {
        if (quest == null) return null;
        Object code = invokeNoArg(quest, "getCodeString");
        if (code instanceof String string && !string.isBlank()) return string;
        Object id = invokeNoArg(quest, "getId");
        if (id != null) return quest.getClass().getName() + ':' + id;
        return quest.getClass().getName() + '@' + System.identityHashCode(quest);
    }
}
