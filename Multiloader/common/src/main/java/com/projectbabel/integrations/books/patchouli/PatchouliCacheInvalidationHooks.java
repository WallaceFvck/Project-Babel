package com.projectbabel.integrations.books.patchouli;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.cache.CacheInvalidationBus;
import com.projectbabel.core.cache.CacheInvalidationReason;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cache invalidation bridge for Patchouli preload state. */
public final class PatchouliCacheInvalidationHooks {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private PatchouliCacheInvalidationHooks() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        CacheInvalidationBus.register("patchouli", reason -> {
            if (reason != CacheInvalidationReason.CACHE_CLEARED) return;
            if (!ProjectBabelCommon.platform().mods().isLoaded("patchouli")) return;

            PatchouliBookPreloader.resetPreloadState();
        });
    }
}
