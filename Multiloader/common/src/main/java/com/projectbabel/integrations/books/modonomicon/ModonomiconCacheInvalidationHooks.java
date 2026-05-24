package com.projectbabel.integrations.books.modonomicon;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.cache.CacheInvalidationBus;
import com.projectbabel.core.cache.CacheInvalidationReason;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cache invalidation bridge for Modonomicon preload state. */
public final class ModonomiconCacheInvalidationHooks {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private ModonomiconCacheInvalidationHooks() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        CacheInvalidationBus.register("modonomicon", reason -> {
            if (reason != CacheInvalidationReason.CACHE_CLEARED) return;
            if (!ProjectBabelCommon.platform().mods().isLoaded("modonomicon")) return;

            ModonomiconBookPreloader.resetPreloadState();
        });
    }
}
