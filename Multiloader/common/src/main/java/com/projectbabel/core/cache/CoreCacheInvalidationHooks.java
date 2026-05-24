package com.projectbabel.core.cache;

import com.projectbabel.core.dictionary.TranslationDictionary;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

/** Registers core listeners for volatile state derived from TranslationCache. */
public final class CoreCacheInvalidationHooks {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CoreCacheInvalidationHooks() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        CacheInvalidationBus.register("core", reason -> {
            switch (reason) {
                case CACHE_CLEARED -> {
                    TranslationDictionary.getInstance().clear();
                    UniversalTermsDictionary.getInstance().reloadAsync();
                    TranslationPipeline.clearContextCache();
                    TranslationSkipRegistry.clear();
                }
                case ENTRY_UPDATED, ENTRY_REMOVED -> TranslationPipeline.clearContextCache();
            }
        });
    }
}
