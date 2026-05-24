package com.projectbabel.core.cache;

/**
 * Describes why translation-derived state must be invalidated.
 *
 * Cache listeners use this to decide whether they should only clear cheap
 * in-memory views or fully reset a mod integration/preloader.
 */
public enum CacheInvalidationReason {
    /** The whole persisted/runtime translation cache was cleared. */
    CACHE_CLEARED,

    /** One cache entry was edited in-place. */
    ENTRY_UPDATED,

    /** One cache entry was removed. */
    ENTRY_REMOVED
}
