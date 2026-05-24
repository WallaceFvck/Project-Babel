package com.projectbabel.core.cache;

@FunctionalInterface
public interface CacheInvalidationListener {
    void onCacheInvalidated(CacheInvalidationReason reason);
}
