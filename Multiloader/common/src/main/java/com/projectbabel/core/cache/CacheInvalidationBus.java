package com.projectbabel.core.cache;

import com.projectbabel.ProjectBabelCommon;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Small in-process event bus for cache invalidation.
 *
 * TranslationCache owns cache storage only. Systems that derive state from the
 * cache subscribe here instead of being called directly by the cache.
 */
public final class CacheInvalidationBus {

    private static final List<RegisteredListener> LISTENERS = new CopyOnWriteArrayList<>();

    private CacheInvalidationBus() {}

    public static void register(String owner, CacheInvalidationListener listener) {
        if (owner == null || owner.isBlank() || listener == null) return;
        for (RegisteredListener existing : LISTENERS) {
            if (existing.owner().equals(owner)) return;
        }
        LISTENERS.add(new RegisteredListener(owner, listener));
    }

    public static void unregister(String owner) {
        if (owner == null || owner.isBlank()) return;
        LISTENERS.removeIf(listener -> listener.owner().equals(owner));
    }

    public static void emit(CacheInvalidationReason reason) {
        Objects.requireNonNull(reason, "reason");
        for (RegisteredListener registered : LISTENERS) {
            try {
                registered.listener().onCacheInvalidated(reason);
            } catch (Throwable throwable) {
                ProjectBabelCommon.LOGGER.warn(
                    "[projectbabel] Falha ao notificar invalidação de cache para {}: {}",
                    registered.owner(),
                    throwable.getMessage()
                );
            }
        }
    }

    public static int listenerCount() {
        return LISTENERS.size();
    }

    private record RegisteredListener(String owner, CacheInvalidationListener listener) {}
}
