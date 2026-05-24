package com.projectbabel.minecraft.render;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.api.TranslationContext;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFormatUtils;
import net.minecraft.network.chat.Component;

/**
 * Strict render-thread fallback.
 *
 * Contract:
 * - never schedules network/API work;
 * - never blocks;
 * - only applies dictionary/cache values that are already available;
 * - skips layout-sensitive screens that must translate before wrapping/layout.
 */
public final class RenderFallbackTranslator {

    private RenderFallbackTranslator() {}

    public static String translateString(String text) {
        if (!canRun(text)) return text;
        try {
            String translated = TranslationPipeline.translateString(text, TranslationContext.renderFallback());
            translated = TextFormatUtils.collapseExactDuplicateTranslation(text, translated);
            translated = TextFormatUtils.collapseRepeatedTranslation(translated);
            if (translated != null && !translated.equals(text)) {
                TranslationSkipRegistry.skipText(translated);
            }
            return translated == null ? text : translated;
        } catch (Exception ignored) {
            return text;
        }
    }

    public static Component translateComponent(Component component) {
        if (component == null) return null;
        if (!canRun(component.getString())) return component;
        try {
            Component translated = TranslationPipeline.translateComponent(component, TranslationContext.renderFallback());
            return translated == null ? component : translated;
        } catch (Exception ignored) {
            return component;
        }
    }

    private static boolean canRun(String text) {
        if (text == null || text.isEmpty()) return false;
        if (LayoutSensitiveScreenGuard.shouldSkipLateRenderTranslation()) return false;
        return ProjectBabelCommon.config().isEnabled() && LanguageDetector.shouldModBeActive();
    }
}
