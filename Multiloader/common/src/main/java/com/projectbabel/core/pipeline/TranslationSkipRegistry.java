package com.projectbabel.core.pipeline;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived skip list for text that must survive the generic Font/GuiGraphics
 * mixins without being translated, such as custom anvil item names.
 */
public final class TranslationSkipRegistry {

    private static final long TEXT_TTL_MS = 3_000L;
    private static final int MAX_TEXT_ENTRIES = 2_048;

    private static final Map<Component, Boolean> COMPONENTS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final ConcurrentHashMap<String, Long> TEXTS =
        new ConcurrentHashMap<>();

    private TranslationSkipRegistry() {}

    public static void skip(Component component) {
        if (component == null) return;

        COMPONENTS.put(component, Boolean.TRUE);

        String text = component.getString();
        skipText(text);
    }

    public static void skipText(String text) {
        if (text == null || text.isBlank()) return;
        if (TEXTS.size() >= MAX_TEXT_ENTRIES) {
            TEXTS.clear();
        }
        TEXTS.put(text, System.currentTimeMillis() + TEXT_TTL_MS);
    }

    public static boolean shouldSkip(Component component) {
        if (component == null) return false;
        if (COMPONENTS.containsKey(component)) return true;
        return shouldSkip(component.getString());
    }

    public static boolean shouldSkipIdentity(Component component) {
        return component != null && COMPONENTS.containsKey(component);
    }

    public static String reasonFor(Component component) {
        if (component == null) return "none";
        if (COMPONENTS.containsKey(component)) return "component_identity";

        String text = component.getString();
        if (text == null || text.isBlank()) return "none";

        Long expiresAt = TEXTS.get(text);
        if (expiresAt == null) return "none";

        if (expiresAt < System.currentTimeMillis()) {
            TEXTS.remove(text, expiresAt);
            return "expired_text";
        }

        return "text_ttl";
    }

    public static boolean shouldSkip(String text) {
        if (text == null || text.isBlank()) return false;

        Long expiresAt = TEXTS.get(text);
        if (expiresAt == null) return false;

        if (expiresAt < System.currentTimeMillis()) {
            TEXTS.remove(text, expiresAt);
            return false;
        }

        return true;
    }

    public static void clear() {
        COMPONENTS.clear();
        TEXTS.clear();
    }
}
