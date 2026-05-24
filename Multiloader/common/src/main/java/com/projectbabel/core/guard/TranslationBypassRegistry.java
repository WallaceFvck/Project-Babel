package com.projectbabel.core.guard;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived bypass registry for the generic render hooks.
 *
 * This is intentionally not the persistent output guard. The output guard lives
 * in translation.cache and remembers translated values coming from the cache.
 * This registry only protects freshly rebuilt Components/texts for a few render
 * frames so Font/GuiGraphics hooks do not translate their own output again.
 */
public final class TranslationBypassRegistry {

    private static final TranslationBypassRegistry INSTANCE = new TranslationBypassRegistry();

    private static final long DEFAULT_TEXT_TTL_MS = 3_000L;
    private static final int MAX_TEXT_ENTRIES = 4_096;

    private final Map<Component, BypassReason> componentIdentities =
        Collections.synchronizedMap(new WeakHashMap<>());
    private final ConcurrentHashMap<String, TextEntry> transientTexts = new ConcurrentHashMap<>();

    private TranslationBypassRegistry() {}

    public static TranslationBypassRegistry getInstance() {
        return INSTANCE;
    }

    public void markComponent(Component component, BypassReason reason) {
        if (component == null) return;
        componentIdentities.put(component, normalizeReason(reason));
        markText(component.getString(), reason, DEFAULT_TEXT_TTL_MS);
    }

    public void markText(String text, BypassReason reason) {
        markText(text, reason, DEFAULT_TEXT_TTL_MS);
    }

    public void markText(String text, BypassReason reason, long ttlMs) {
        if (text == null || text.isBlank()) return;
        sweepIfNeeded();
        long safeTtl = ttlMs <= 0L ? DEFAULT_TEXT_TTL_MS : ttlMs;
        transientTexts.put(text, new TextEntry(
            System.currentTimeMillis() + safeTtl,
            normalizeReason(reason)
        ));
    }

    public boolean shouldBypass(Component component) {
        if (component == null) return false;
        if (shouldBypassIdentity(component)) return true;
        return shouldBypassText(component.getString());
    }

    public boolean shouldBypassIdentity(Component component) {
        return component != null && componentIdentities.containsKey(component);
    }

    public boolean shouldBypassText(String text) {
        if (text == null || text.isBlank()) return false;

        TextEntry entry = transientTexts.get(text);
        if (entry == null) return false;

        if (entry.expiresAt < System.currentTimeMillis()) {
            transientTexts.remove(text, entry);
            return false;
        }
        return true;
    }

    public BypassReason reasonFor(Component component) {
        if (component == null) return null;
        BypassReason reason = componentIdentities.get(component);
        if (reason != null) return reason;
        return reasonForText(component.getString());
    }

    public BypassReason reasonForText(String text) {
        if (text == null || text.isBlank()) return null;
        TextEntry entry = transientTexts.get(text);
        if (entry == null) return null;
        if (entry.expiresAt < System.currentTimeMillis()) {
            transientTexts.remove(text, entry);
            return null;
        }
        return entry.reason;
    }

    public void clear() {
        componentIdentities.clear();
        transientTexts.clear();
    }

    private void sweepIfNeeded() {
        if (transientTexts.size() < MAX_TEXT_ENTRIES) return;

        long now = System.currentTimeMillis();
        transientTexts.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        if (transientTexts.size() >= MAX_TEXT_ENTRIES) {
            transientTexts.clear();
        }
    }

    private static BypassReason normalizeReason(BypassReason reason) {
        return reason == null ? BypassReason.LEGACY : reason;
    }

    private static final class TextEntry {
        final long expiresAt;
        final BypassReason reason;

        TextEntry(long expiresAt, BypassReason reason) {
            this.expiresAt = expiresAt;
            this.reason = reason;
        }
    }
}
