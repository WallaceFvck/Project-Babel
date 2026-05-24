package com.projectbabel.api;

import net.minecraft.network.chat.Component;

/** A typed request accepted by TranslationService/preload paths. */
public record TranslationRequest(
    String text,
    Component component,
    TranslationContext context
) {
    public TranslationRequest {
        context = context == null ? TranslationContext.interactive(TranslationSurface.UNKNOWN) : context;
        if (text == null && component == null) {
            throw new IllegalArgumentException("TranslationRequest requires text or component");
        }
    }

    public static TranslationRequest text(String text, TranslationContext context) {
        return new TranslationRequest(text, null, context);
    }

    public static TranslationRequest component(Component component, TranslationContext context) {
        return new TranslationRequest(null, component, context);
    }

    public boolean isTextRequest() {
        return text != null;
    }

    public boolean isComponentRequest() {
        return component != null;
    }
}
