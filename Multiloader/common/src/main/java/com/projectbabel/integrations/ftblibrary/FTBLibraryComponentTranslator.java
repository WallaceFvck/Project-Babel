package com.projectbabel.integrations.ftblibrary;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.network.chat.Component;

import com.projectbabel.core.text.ComponentTemplateTranslator;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.pipeline.TranslationTriageManager;
/**
 * Helpers for FTBLibrary widgets. FTBLibrary measures/wraps several widgets while
 * setting their text, so the translation must happen before setText/setTitle/parser
 * stores cached FormattedCharSequence arrays.
 */
public final class FTBLibraryComponentTranslator {

    private FTBLibraryComponentTranslator() {}

    public static Component translateComponentBeforeLayout(Component original) {
        if (!canTranslate() || original == null) return original;

        if (TranslationSkipRegistry.shouldSkipIdentity(original) || TranslationSkipRegistry.shouldSkip(original)) return original;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            if (ComponentTemplateTranslator.isCandidate(original)) {
                Component cached = ComponentTemplateTranslator.translateCacheOnly(original);
                if (cached != original) return cached;

                Component blocking = ComponentTemplateTranslator.translateBlocking(original);
                if (blocking != original) {
                    TranslationTriageManager.getInstance().warmComponent(original, blocking);
                    return blocking;
                }
                return original;
            }

            Component cached = TranslationPipeline.translateComponent(original, TranslationContext.compatMod().asCacheOnly());
            if (cached != original) return cached;
            if (!TranslationPipeline.needsExternalTranslation(original)) return original;

            Component blocking = TranslationPipeline.translateComponent(original, TranslationContext.compatMod().asBlocking());
            if (blocking != original) {
                TranslationTriageManager.getInstance().warmComponent(original, blocking);
                return blocking;
            }
        } catch (Throwable t) {
            ProjectBabelDebug.info(ProjectBabelDebug.QUESTS, "FTBLibrary component translation failed: {}", t.toString());
        }

        return original;
    }

    public static String translateStringBeforeLayout(String original) {
        if (!canTranslate() || original == null || original.isBlank()) return original;

        if (TranslationSkipRegistry.shouldSkip(original)) return original;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            String cached = TranslationPipeline.translateString(original, TranslationContext.compatMod().asCacheOnly());
            if (cached != null && !cached.equals(original)) {
                return remember(original, cached);
            }

            String blocking = TranslationPipeline.translateString(original, TranslationContext.compatMod().asBlocking());
            if (blocking != null && !blocking.equals(original)) {
                return remember(original, blocking);
            }
        } catch (Throwable t) {
            ProjectBabelDebug.info(ProjectBabelDebug.QUESTS, "FTBLibrary string translation failed: {}", t.toString());
        }

        return original;
    }

    private static String remember(String original, String translated) {
        translated = TextFormatUtils.collapseExactDuplicateTranslation(original, translated);
        translated = TextFormatUtils.collapseRepeatedTranslation(translated);
        if (translated != null && !translated.equals(original)) {
            TranslationSkipRegistry.skipText(translated);
            return translated;
        }
        return original;
    }

    private static boolean canTranslate() {
        try {
            return ProjectBabelCommon.config().isEnabled()
                && ProjectBabelCommon.platform().mods().isLoaded("ftblibrary")
                && LanguageDetector.shouldModBeActive();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
