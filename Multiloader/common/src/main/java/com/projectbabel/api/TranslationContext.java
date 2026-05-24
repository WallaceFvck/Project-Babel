package com.projectbabel.api;

import java.util.Objects;

/**
 * Policy object for every translation request.
 *
 * Important flags:
 * - cacheOnly: do not call network/engine paths.
 * - allowScheduling: async cache fill may be scheduled.
 * - allowBlocking: caller can wait for the result. Never use this from render.
 * - preserveLayout: avoid replacements that can affect layout-sensitive screens.
 */
public record TranslationContext(
    TranslationSurface surface,
    String namespace,
    boolean allowBlocking,
    boolean allowScheduling,
    boolean cacheOnly,
    boolean preserveLayout,
    int priority
) {
    public static final String DEFAULT_NAMESPACE = "literal";

    public TranslationContext {
        surface = surface == null ? TranslationSurface.UNKNOWN : surface;
        namespace = normalizeNamespace(namespace);
    }

    public static TranslationContext interactive(TranslationSurface surface) {
        return new TranslationContext(surface, DEFAULT_NAMESPACE, false, true, false, false, 50);
    }

    public static TranslationContext blocking(TranslationSurface surface) {
        return new TranslationContext(surface, DEFAULT_NAMESPACE, true, true, false, false, 80);
    }

    public static TranslationContext cacheOnly(TranslationSurface surface) {
        return new TranslationContext(surface, DEFAULT_NAMESPACE, false, false, true, true, 10);
    }

    public static TranslationContext renderFallback() {
        return cacheOnly(TranslationSurface.RENDER_FALLBACK);
    }

    public static TranslationContext screenWidgetTick() {
        return cacheOnly(TranslationSurface.SCREEN_WIDGET);
    }

    public static TranslationContext tooltip() {
        return interactive(TranslationSurface.TOOLTIP).withPriority(70);
    }

    public static TranslationContext itemName() {
        return interactive(TranslationSurface.ITEM_NAME).withPriority(65);
    }

    public static TranslationContext chat() {
        return blocking(TranslationSurface.CHAT).withPriority(90);
    }

    public static TranslationContext book(boolean blocking) {
        return blocking
            ? blocking(TranslationSurface.BOOK).withPriority(45)
            : interactive(TranslationSurface.BOOK).withPriority(35);
    }

    public static TranslationContext quest(boolean blocking) {
        return blocking
            ? blocking(TranslationSurface.QUEST).withPriority(75)
            : interactive(TranslationSurface.QUEST).withPriority(55);
    }


    public static TranslationContext advancement() {
        return interactive(TranslationSurface.ADVANCEMENT).withPriority(50);
    }

    public static TranslationContext overlay() {
        return interactive(TranslationSurface.OVERLAY).withPriority(70);
    }

    public static TranslationContext languageLookup() {
        return interactive(TranslationSurface.LANGUAGE_LOOKUP).withPriority(30);
    }

    public static TranslationContext compatMod() {
        return interactive(TranslationSurface.COMPAT_MOD).withPriority(55);
    }

    public static TranslationContext preload(TranslationSurface surface) {
        return new TranslationContext(surface == null ? TranslationSurface.PRELOAD : surface, DEFAULT_NAMESPACE, false, true, false, false, 20);
    }

    public TranslationContext withNamespace(String namespace) {
        return new TranslationContext(surface, namespace, allowBlocking, allowScheduling, cacheOnly, preserveLayout, priority);
    }

    public TranslationContext withPriority(int priority) {
        return new TranslationContext(surface, namespace, allowBlocking, allowScheduling, cacheOnly, preserveLayout, priority);
    }

    public TranslationContext asCacheOnly() {
        return new TranslationContext(surface, namespace, false, false, true, true, priority);
    }

    public TranslationContext asBlocking() {
        return new TranslationContext(surface, namespace, true, true, false, preserveLayout, priority);
    }

    public TranslationContext withoutScheduling() {
        return new TranslationContext(surface, namespace, allowBlocking, false, true, true, priority);
    }

    public boolean canSchedule() {
        return allowScheduling && !cacheOnly;
    }

    public boolean canBlock() {
        return allowBlocking && !cacheOnly;
    }

    private static String normalizeNamespace(String namespace) {
        String value = Objects.toString(namespace, DEFAULT_NAMESPACE).trim();
        return value.isEmpty() ? DEFAULT_NAMESPACE : value;
    }
}
