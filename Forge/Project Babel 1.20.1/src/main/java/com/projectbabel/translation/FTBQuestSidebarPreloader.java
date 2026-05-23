package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Very small FTB Quests sidebar warm-up.
 *
 * IMPORTANT: this class must not change the normal FTB Quests translation path.
 * It only preloads values that come from the quest data fields named "title" and
 * "group" so the left chapter/sidebar navigation has cache hits immediately
 * after a cache wipe or on the first world load. Descriptions, subtitles, tasks,
 * rewards and quest bodies are intentionally left to the existing flow.
 */
public final class FTBQuestSidebarPreloader {

    private static final long WORLD_PRELOAD_RETRY_MS = 500L;
    private static final int WORLD_PRELOAD_ATTEMPTS = 40;
    private static final int BLOCKING_SCREEN_TIMEOUT_MS = 6_000;

    private static final ConcurrentHashMap.KeySetView<String, Boolean> READY =
        ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> RUNNING =
        ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, CompletableFuture<TitleGroupPreloadResult>> RUNNING_FUTURES =
        new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong(0L);
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> MISSING_METHODS =
        ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap.KeySetView<String, Boolean> MISSING_FIELDS =
        ConcurrentHashMap.newKeySet();

    private FTBQuestSidebarPreloader() {}

    public static void reset() {
        GENERATION.incrementAndGet();
        READY.clear();
        RUNNING.clear();
        RUNNING_FUTURES.clear();
    }

    public static void requestWorldPreload() {
        if (!ModList.get().isLoaded("ftbquests")) return;

        CompletableFuture.runAsync(() -> {
            for (int attempt = 0; attempt < WORLD_PRELOAD_ATTEMPTS; attempt++) {
                Object file = currentClientQuestFile();
                if (file != null) {
                    requestPreload(file, true);
                    return;
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(WORLD_PRELOAD_RETRY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, TranslationExecutors.preload());
    }

    public static void requestPreloadForScreen(Object screen) {
        Object file = readField(screen, "file");
        if (file != null) requestPreload(file, true);
    }

    /**
     * A short blocking pass while QuestScreen is being initialized. It warms only
     * title/group values, so it should not steal control from the existing quest
     * description/task/reward translator.
     */
    public static void preloadForScreenBlocking(Object screen) {
        Object file = readField(screen, "file");
        if (file == null || !ModList.get().isLoaded("ftbquests")) return;

        String key = keyOf(file);
        if (READY.contains(key)) {
            // The translation cache is ready, but FTB Quests may still have old
            // title components cached in memory from before the cache wipe. Clear
            // only the title/group owners before the screen builds its sidebar.
            invalidateTitleGroupCachedDataNow(file, collectTitleAndGroupEntries(file));
            return;
        }

        try {
            TitleGroupPreloadResult result = startPreload(file, true)
                .get(BLOCKING_SCREEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result.entries() > 0) {
                READY.add(key);
                invalidateTitleGroupCachedDataNow(file, result);
            }
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.warn(
                "[projectbabel] FTB Quests title/group preload inicial falhou/expirou: {}",
                e.getMessage()
            );
        }
    }

    private static void requestPreload(Object file, boolean refreshAfter) {
        if (file == null || !ModList.get().isLoaded("ftbquests")) return;
        if (READY.contains(keyOf(file))) return;
        startPreload(file, refreshAfter);
    }

    private static CompletableFuture<TitleGroupPreloadResult> startPreload(Object file, boolean refreshAfter) {
        String key = keyOf(file);
        long generation = GENERATION.get();

        CompletableFuture<TitleGroupPreloadResult> existing = RUNNING_FUTURES.get(key);
        if (existing != null) return existing;

        CompletableFuture<TitleGroupPreloadResult> created = CompletableFuture.supplyAsync(() -> {
            try {
                return preloadTitleAndGroup(file);
            } catch (Exception e) {
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] FTB Quests title/group preload falhou: {}",
                    e.getMessage()
                );
                return TitleGroupPreloadResult.EMPTY;
            }
        }, TranslationExecutors.preload());

        CompletableFuture<TitleGroupPreloadResult> previous = RUNNING_FUTURES.putIfAbsent(key, created);
        if (previous != null) return previous;

        RUNNING.add(key);
        created.whenComplete((result, error) -> {
            RUNNING.remove(key);
            RUNNING_FUTURES.remove(key, created);

            if (error != null || result == null || generation != GENERATION.get()) return;

            if (result.entries() > 0) {
                READY.add(key);
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] FTB Quests title/group preload concluido: {} entradas, {} traducoes aquecidas.",
                    result.entries(),
                    result.warmed()
                );
                invalidateTitleGroupCachedDataOnClient(file, result, refreshAfter);
            }
        });

        return created;
    }

    private static TitleGroupPreloadResult preloadTitleAndGroup(Object file) {
        TitleGroupEntries entries = collectTitleAndGroupEntries(file);
        if (entries.values.isEmpty()) return TitleGroupPreloadResult.EMPTY;

        int warmed = 0;
        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            for (Object value : entries.values) {
                if (warmValue(value)) warmed++;
            }
        }

        return new TitleGroupPreloadResult(
            entries.values.size(),
            warmed,
            new ArrayList<>(entries.objectsToRefresh)
        );
    }

    private static boolean warmValue(Object value) {
        if (value instanceof Component component) {
            Component translated = ComponentTemplateTranslator.isCandidate(component)
                ? ComponentTemplateTranslator.translateBlocking(component)
                : TranslationPipeline.translateComponentTreeBlocking(component);
            if (translated != component) {
                TranslationTriageManager.getInstance().warmComponent(component, translated);
                return true;
            }
            return false;
        }

        if (value instanceof String string) {
            String translated = TranslationPipeline.translateStringBlocking(string);
            if (translated != null && !translated.equals(string)) {
                TranslationTriageManager.getInstance().warmText(string, translated);
                return true;
            }
            return false;
        }

        if (value instanceof Collection<?> collection) {
            boolean changed = false;
            for (Object entry : collection) {
                changed |= warmValue(entry);
            }
            return changed;
        }

        return false;
    }

    private static TitleGroupEntries collectTitleAndGroupEntries(Object file) {
        TitleGroupEntries entries = new TitleGroupEntries();
        Object teamData = readField(file, "selfTeamData");

        Set<Object> groups = new HashSet<>();
        Object defaultGroup = invokeNoArg(file, "getDefaultChapterGroup");
        if (defaultGroup != null) groups.add(defaultGroup);
        groups.addAll(collectChapterGroups(file));

        for (Object group : groups) {
            if (group == null) continue;
            collectOnlyTitleAndGroup(group, entries);

            Collection<?> chapters = visibleChapters(group, teamData);
            for (Object chapter : chapters) {
                collectOnlyTitleAndGroup(chapter, entries);
            }
        }

        return entries;
    }

    private static void collectOnlyTitleAndGroup(Object object, TitleGroupEntries entries) {
        if (object == null) return;
        entries.objectsToRefresh.add(object);

        collectNamedTextValue(object, "title", entries);
        collectNamedTextValue(object, "group", entries);
    }

    private static void collectNamedTextValue(Object object, String name, TitleGroupEntries entries) {
        Object value = readField(object, name);
        if (value != null) {
            addNamedValue(value, entries);
            return;
        }

        // FTB Quests versions may expose the saved data through raw accessors.
        // Do not call getTitle() here: that method is mixin-translated by the
        // normal runtime path and may preload subtitles/descriptions/tasks.
        value = invokeNoArg(object, rawAccessorName(name));
        if (value != null) addNamedValue(value, entries);
    }

    private static void addNamedValue(Object value, TitleGroupEntries entries) {
        if (value == null) return;
        if (value instanceof String || value instanceof Component || value instanceof Collection<?>) {
            entries.add(value);
            return;
        }

        // If the "group" value is an object reference, warm only its own title
        // field/accessor. Do not scan arbitrary fields, otherwise unrelated quest
        // content can enter the sidebar preload again.
        Object nestedTitle = readField(value, "title");
        if (nestedTitle != null) {
            entries.add(nestedTitle);
            entries.objectsToRefresh.add(value);
            return;
        }

        nestedTitle = invokeNoArg(value, "getRawTitle");
        if (nestedTitle != null) {
            entries.add(nestedTitle);
            entries.objectsToRefresh.add(value);
        }
    }

    private static String rawAccessorName(String name) {
        if ("title".equals(name)) return "getRawTitle";
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> collectChapterGroups(Object file) {
        List<Object> groups = new ArrayList<>();

        Method forAll = findMethod(file.getClass(), "forAllChapterGroups", 1);
        if (forAll != null) {
            try {
                Consumer<Object> consumer = group -> {
                    if (group != null) groups.add(group);
                };
                forAll.invoke(file, consumer);
            } catch (Exception ignored) {
            }
        }

        if (!groups.isEmpty()) return groups;

        Object value = invokeNoArg(file, "getChapterGroups");
        if (value instanceof Collection<?> collection) {
            return (Collection<Object>) collection;
        }

        value = invokeNoArg(file, "getGroups");
        if (value instanceof Collection<?> collection) {
            return (Collection<Object>) collection;
        }

        return List.of();
    }

    private static Collection<?> visibleChapters(Object group, Object teamData) {
        Object value = invokeOneArg(group, "getVisibleChapters", teamData);
        if (value instanceof Collection<?> collection) return collection;

        value = invokeNoArg(group, "getChapters");
        if (value instanceof Collection<?> collection) return collection;

        return List.of();
    }

    private static void invalidateTitleGroupCachedDataNow(Object file, TitleGroupEntries entries) {
        if (entries == null) return;
        clearCachedData(file);
        for (Object object : entries.objectsToRefresh) {
            clearCachedData(object);
        }
    }

    private static void invalidateTitleGroupCachedDataNow(Object file, TitleGroupPreloadResult result) {
        if (result == null) return;
        clearCachedData(file);
        for (Object object : result.objectsToRefresh()) {
            clearCachedData(object);
        }
    }

    private static void invalidateTitleGroupCachedDataOnClient(
        Object file,
        TitleGroupPreloadResult result,
        boolean refreshAfter
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            try {
                invalidateTitleGroupCachedDataNow(file, result);
                if (refreshAfter) {
                    refreshOpenQuestScreenNow(file);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void refreshOpenQuestScreen(Object file) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> refreshOpenQuestScreenNow(file));
    }

    private static void refreshOpenQuestScreenNow(Object file) {
        try {
            // Newer FTB Quests exposes a client-level GUI refresh. This is the
            // closest equivalent to the "relog fixes it" path without actually
            // disconnecting from the world.
            invokeNoArg(file, "refreshGui");

            Minecraft mc = Minecraft.getInstance();
            Object screen = mc == null ? null : mc.screen;
            if (screen == null) return;
            if (!screen.getClass().getName().equals("dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen")) return;

            Object screenFile = readField(screen, "file");
            if (screenFile != file) return;

            invokeNoArg(screen, "refreshChapterPanel");
            invokeNoArg(screen, "refreshQuestPanel");
            invokeNoArg(screen, "refreshViewQuestPanel");
            invokeNoArg(readField(screen, "chapterPanel"), "refreshWidgets");
            invokeNoArg(readField(screen, "questPanel"), "refreshWidgets");
            invokeNoArg(readField(screen, "viewQuestPanel"), "refreshWidgets");
            invokeNoArg(screen, "alignWidgets");
        } catch (Throwable ignored) {
        }
    }

    private static Object currentClientQuestFile() {
        try {
            Class<?> type = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
            Method exists = findMethod(type, "exists", 0);
            if (exists != null && !Boolean.TRUE.equals(exists.invoke(null))) return null;
            Method getInstance = findMethod(type, "getInstance", 0);
            return getInstance == null ? null : getInstance.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clearCachedData(Object object) {
        invokeNoArg(object, "clearCachedData");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = findMethod(target.getClass(), methodName, 0);
            if (method == null) return null;
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeOneArg(Object target, String methodName, Object arg) {
        if (target == null) return null;
        try {
            Method method = findMethod(target.getClass(), methodName, 1);
            if (method == null) return null;
            return method.invoke(target, arg);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) return null;
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        String key = type.getName() + '#' + fieldName;
        if (MISSING_FIELDS.contains(key)) return null;

        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (Exception ignored) {
                current = current.getSuperclass();
            }
        }

        MISSING_FIELDS.add(key);
        return null;
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        String key = type.getName() + '#' + methodName + '/' + parameterCount;
        if (MISSING_METHODS.contains(key)) return null;

        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName)
                    && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    METHOD_CACHE.put(key, method);
                    return method;
                }
            }
            current = current.getSuperclass();
        }

        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)
                && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            }
        }

        MISSING_METHODS.add(key);
        return null;
    }

    private static String keyOf(Object file) {
        return LanguageDetector.getTargetLanguageForApi() + ':' + file.getClass().getName() + '@' + System.identityHashCode(file);
    }

    private static final class TitleGroupEntries {
        private final List<Object> values = new ArrayList<>();
        private final Set<Object> objectsToRefresh = new HashSet<>();
        private final Set<String> seenText = new HashSet<>();

        private void add(Object value) {
            if (value == null) return;
            if (value instanceof String string) {
                if (!string.isBlank() && seenText.add("S:" + string)) values.add(string);
            } else if (value instanceof Component component) {
                String text = component.getString();
                if (text != null && !text.isBlank() && seenText.add("C:" + text)) values.add(component);
            } else if (value instanceof Collection<?> collection) {
                for (Object entry : collection) add(entry);
            }
        }
    }

    private record TitleGroupPreloadResult(int entries, int warmed, List<Object> objectsToRefresh) {
        private static final TitleGroupPreloadResult EMPTY = new TitleGroupPreloadResult(0, 0, List.of());
    }
}
