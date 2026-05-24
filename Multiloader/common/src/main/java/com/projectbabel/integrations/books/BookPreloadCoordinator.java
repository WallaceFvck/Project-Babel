package com.projectbabel.integrations.books;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.schedule.PreloadAcceleration;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.text.LanguageDetector;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared state machine for book-like integrations.
 * Patchouli, Modonomicon and GuideME previously duplicated READY/RUNNING/generation/refresh logic.
 */
public final class BookPreloadCoordinator {
    private static final ConcurrentHashMap<String, State> STATES = new ConcurrentHashMap<>();

    private BookPreloadCoordinator() {}

    public static boolean request(BookPreloadAdapter adapter, Object source, long waitMs, boolean warnOnTimeout) {
        if (adapter == null) return false;

        BookIdentity identity = adapter.resolveIdentity(source);
        if (identity == null) {
            ProjectBabelCommon.LOGGER.debug(
                "[projectbabel] {} preload ignorado: livro/escopo nao identificado.",
                adapter.integrationId()
            );
            return false;
        }

        State state = state(adapter.integrationId());
        String targetLang = LanguageDetector.getTargetLanguageForApi();
        String preloadKey = targetLang + ':' + identity.id();
        if (state.ready.contains(preloadKey)) return true;

        CompletableFuture<Boolean> future = state.running.computeIfAbsent(preloadKey, key -> {
            long generation = state.generation.get();
            CompletableFuture<Boolean> active = CompletableFuture.supplyAsync(
                () -> PreloadAcceleration.supply(() -> {
                    BookPreloadContext context = new BookPreloadContext(
                        adapter.integrationId(), key, generation, new StateHandle(state)
                    );
                    boolean success = adapter.runPreload(context, identity, targetLang, source);
                    if (success && context.isCurrentGeneration()) context.markReady();
                    return success;
                }),
                TranslationExecutors.preload()
            );
            active.whenComplete((ignored, error) -> state.running.remove(key, active));
            return active;
        });

        try {
            return Boolean.TRUE.equals(future.get(Math.max(0L, waitMs), TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            if (warnOnTimeout) {
                ProjectBabelCommon.LOGGER.warn(
                    "[projectbabel] {} preload de {} nao terminou a tempo: {}",
                    adapter.integrationId(), identity.id(), e.getMessage()
                );
            }
            return false;
        }
    }

    public static void reset(String integrationId) {
        State state = state(integrationId);
        state.generation.incrementAndGet();
        state.ready.clear();
        state.running.clear();
        state.refreshAfterIdleScheduled.set(false);
    }

    public static boolean isPreloading(String integrationId) {
        return !state(integrationId).running.isEmpty();
    }

    public static void markReady(String integrationId, String preloadKey) {
        State state = state(integrationId);
        state.ready.add(preloadKey);
    }

    public static boolean isCurrentGeneration(String integrationId, long generation) {
        return state(integrationId).generation.get() == generation;
    }

    public static void requestRefreshAfterIdle(String integrationId, long waitTimeoutMs, String reason, Runnable refresher) {
        State state = state(integrationId);
        if (!state.refreshAfterIdleScheduled.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                TranslationManager.getInstance().waitForIdle(waitTimeoutMs);
                refresher.run();
            } finally {
                state.refreshAfterIdleScheduled.set(false);
            }
        }, TranslationExecutors.preload());
    }

    private static State state(String integrationId) {
        String key = integrationId == null || integrationId.isBlank() ? "books" : integrationId;
        return STATES.computeIfAbsent(key, ignored -> new State());
    }

    private static final class State {
        private final Set<String> ready = ConcurrentHashMap.newKeySet();
        private final ConcurrentHashMap<String, CompletableFuture<Boolean>> running = new ConcurrentHashMap<>();
        private final AtomicLong generation = new AtomicLong(0L);
        private final AtomicBoolean refreshAfterIdleScheduled = new AtomicBoolean(false);
    }

    static final class StateHandle {
        private final State state;

        private StateHandle(State state) {
            this.state = state;
        }

        boolean isCurrentGeneration(long generation) {
            return state.generation.get() == generation;
        }

        void markReady(String preloadKey, long generation) {
            if (isCurrentGeneration(generation)) state.ready.add(preloadKey);
        }
    }
}
