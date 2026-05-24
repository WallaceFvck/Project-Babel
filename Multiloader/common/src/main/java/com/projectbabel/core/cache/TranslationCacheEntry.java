package com.projectbabel.core.cache;

/** Immutable projection used by UI and diagnostics when listing cache contents. */
public record TranslationCacheEntry(String key, String original, String translated) {}
