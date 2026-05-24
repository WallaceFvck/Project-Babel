package com.projectbabel.integrations.books.guideme;

import com.projectbabel.core.cache.CacheInvalidationBus;
import com.projectbabel.core.cache.CacheInvalidationReason;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cache invalidation bridge for GuideME/AE2 Guide preload state. */
public final class GuideMeCacheInvalidationHooks {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private GuideMeCacheInvalidationHooks() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        CacheInvalidationBus.register("guideme", reason -> {
            if (reason != CacheInvalidationReason.CACHE_CLEARED) return;
            if (!GuideMePreloader.isGuideMeAvailable()) return;

            GuideMePreloader.reset();
            GuideMePreloader.requestWorldPreload();
        });
    }
}
