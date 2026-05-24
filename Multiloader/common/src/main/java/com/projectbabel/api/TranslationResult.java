package com.projectbabel.api;

/** Result wrapper for new code paths that need to know whether a replacement happened. */
public record TranslationResult<T>(
    T original,
    T translated,
    boolean changed,
    TranslationContext context
) {
    public static <T> TranslationResult<T> unchanged(T value, TranslationContext context) {
        return new TranslationResult<>(value, value, false, context);
    }

    public static <T> TranslationResult<T> of(T original, T translated, TranslationContext context) {
        return new TranslationResult<>(original, translated, original != translated && !safeEquals(original, translated), context);
    }

    private static boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
