package com.projectbabel.core.service;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.api.TranslationRequest;
import com.projectbabel.api.TranslationResult;
import com.projectbabel.api.TranslationService;
import com.projectbabel.api.TranslationSurface;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.schedule.PreloadAcceleration;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.schedule.TranslationPriority;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Adapter from the new explicit TranslationService contract to the current
 * TranslationPipeline implementation. This lets the codebase migrate gradually.
 */
public final class ProjectBabelTranslationService implements TranslationService {

    @Override
    public Component translate(Component component, TranslationContext context) {
        if (component == null) return null;
        TranslationContext resolved = resolve(context);
        if (resolved.cacheOnly() || !resolved.allowScheduling()) {
            return TranslationPipeline.translateComponentTreeCacheOnly(component);
        }
        if (resolved.allowBlocking()) {
            return TranslationPipeline.translateComponentTreeBlocking(component);
        }
        return TranslationPipeline.translateComponentTree(component);
    }

    @Override
    public String translate(String text, TranslationContext context) {
        if (text == null || text.isEmpty()) return text;
        TranslationContext resolved = resolve(context);
        if (resolved.cacheOnly() || !resolved.allowScheduling()) {
            return TranslationPipeline.translateStringCacheOnly(text);
        }
        if (resolved.allowBlocking()) {
            return TranslationPipeline.translateStringBlocking(text);
        }
        return TranslationPipeline.translateString(text);
    }

    @Override
    public TranslationResult<Component> translateComponentResult(Component component, TranslationContext context) {
        TranslationContext resolved = resolve(context);
        Component translated = translate(component, resolved);
        return TranslationResult.of(component, translated, resolved);
    }

    @Override
    public TranslationResult<String> translateStringResult(String text, TranslationContext context) {
        TranslationContext resolved = resolve(context);
        String translated = translate(text, resolved);
        return TranslationResult.of(text, translated, resolved);
    }

    @Override
    public CompletableFuture<Void> preload(Collection<TranslationRequest> requests) {
        if (requests == null || requests.isEmpty()) return CompletableFuture.completedFuture(null);
        Executor executor = PreloadAcceleration.isActive()
            ? TranslationExecutors.preload()
            : TranslationExecutors.triage(TranslationPriority.LOW);
        return CompletableFuture.runAsync(() -> {
            for (TranslationRequest request : requests) {
                if (request == null) continue;
                if (request.isComponentRequest()) {
                    translate(request.component(), request.context());
                } else if (request.isTextRequest()) {
                    translate(request.text(), request.context());
                }
            }
        }, executor);
    }

    private static TranslationContext resolve(TranslationContext context) {
        return Objects.requireNonNullElseGet(context, () -> TranslationContext.interactive(TranslationSurface.UNKNOWN));
    }
}
