package com.projectbabel.integrations.ftbquests;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.cache.CacheInvalidationBus;
import com.projectbabel.core.cache.CacheInvalidationReason;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cache invalidation bridge for FTB Quests-derived preload state. */
public final class FTBQuestCacheInvalidationHooks {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private FTBQuestCacheInvalidationHooks() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        CacheInvalidationBus.register("ftbquests", reason -> {
            if (reason != CacheInvalidationReason.CACHE_CLEARED) return;
            if (!ProjectBabelCommon.platform().mods().isLoaded("ftbquests")) return;

            FTBQuestFirstOpenTracker.reset();
            FTBQuestChapterPreloader.reset();
            FTBQuestSidebarPreloader.reset();
            FTBQuestSidebarPreloader.requestWorldPreload();
        });
    }
}
