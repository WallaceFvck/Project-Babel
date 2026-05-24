package com.projectbabel.api;

/**
 * Declares where a translation request came from. This makes pipeline behavior
 * explicit instead of hiding render/chat/book/quest policy inside call sites.
 */
public enum TranslationSurface {
    UNKNOWN,
    RENDER_FALLBACK,
    SCREEN_WIDGET,
    TOOLTIP,
    ITEM_NAME,
    CHAT,
    BOOK,
    QUEST,
    LANGUAGE_LOOKUP,
    ADVANCEMENT,
    OVERLAY,
    PRELOAD,
    CACHE_UI,
    COMPAT_MOD
}
