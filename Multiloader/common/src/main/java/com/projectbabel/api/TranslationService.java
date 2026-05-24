package com.projectbabel.api;

import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Public facade for translation callers inside Project Babel.
 * New code should use this instead of calling triage/cache/manager directly.
 */
public interface TranslationService {
    Component translate(Component component, TranslationContext context);

    String translate(String text, TranslationContext context);

    TranslationResult<Component> translateComponentResult(Component component, TranslationContext context);

    TranslationResult<String> translateStringResult(String text, TranslationContext context);

    CompletableFuture<Void> preload(Collection<TranslationRequest> requests);
}
