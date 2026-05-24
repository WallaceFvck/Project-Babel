package com.projectbabel.integrations.registry;

/**
 * Lightweight descriptor used by integration adapters when they expose text
 * sources to the preload layer. It is intentionally generic so legacy
 * integrations can migrate without exposing mod-specific objects upstream.
 */
public record TranslationSource(
    String integrationId,
    String sourceId,
    Object owner,
    Object value
) {
    public boolean hasValue() {
        return value != null;
    }
}
