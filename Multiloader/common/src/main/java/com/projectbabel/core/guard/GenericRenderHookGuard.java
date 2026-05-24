package com.projectbabel.core.guard;

import com.projectbabel.core.guard.RenderingGuard;
import com.projectbabel.core.service.TranslationManager;
import net.minecraft.network.chat.Component;

/**
 * Shared decision point for generic Font/GuiGraphics/Theme hooks.
 *
 * These hooks are intentionally broad, so they need a cheap and consistent way
 * to avoid translating Project Babel UI, pre-laid-out guide text, freshly
 * generated Components, and known translated outputs.
 */
public final class GenericRenderHookGuard {

    private GenericRenderHookGuard() {}

    public static boolean shouldBypass(String text) {
        if (RenderingGuard.isActive()) return true;
        if (TranslationBypassRegistry.getInstance().shouldBypassText(text)) return true;
        if (isKnownOutput(text)) {
            TranslationBypassRegistry.getInstance().markText(text, BypassReason.KNOWN_OUTPUT);
            return true;
        }
        return false;
    }

    public static boolean shouldBypass(Component component) {
        if (component == null) return false;
        if (RenderingGuard.isActive()) return true;
        if (TranslationBypassRegistry.getInstance().shouldBypass(component)) return true;
        String text = component.getString();
        if (isKnownOutput(text)) {
            TranslationBypassRegistry.getInstance().markComponent(component, BypassReason.KNOWN_OUTPUT);
            return true;
        }
        return false;
    }

    public static void markGenerated(Component component) {
        TranslationBypassRegistry.getInstance().markComponent(component, BypassReason.GENERATED_TRANSLATION);
    }

    public static void markGeneratedText(String text) {
        TranslationBypassRegistry.getInstance().markText(text, BypassReason.GENERATED_TRANSLATION);
    }

    private static boolean isKnownOutput(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            return TranslationManager.getInstance().isAlreadyTranslatedValue(text);
        } catch (Exception ignored) {
            return false;
        }
    }
}
