package com.projectbabel.integrations.create;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.resources.ResourceLocation;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
public final class CreatePonderTranslator {


    private CreatePonderTranslator() {}

    public static String translateBakedText(String text) {
        if (!canTranslate() || text == null || text.isEmpty()) return text;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            String cached = TranslationPipeline.translateString(text, TranslationContext.compatMod().asCacheOnly());
            if (cached != null && !cached.equals(text)) {
                ProjectBabelDebug.info(ProjectBabelDebug.PONDER, "cache hit text={} translated={}", truncate(text, 70), truncate(cached, 70));
                return rememberTranslated(text, cached);
            }

            ProjectBabelDebug.info(ProjectBabelDebug.PONDER, "blocking ponder text={}", truncate(text, 80));
            String translated = TranslationPipeline.translateString(text, TranslationContext.compatMod().asBlocking());
            if (translated == null || translated.equals(text)) return text;
            return rememberTranslated(text, translated);
        } catch (Exception ignored) {
            return text;
        }
    }

    public static String translateShared(ResourceLocation id, String returned) {
        return translateLocalization(returned, CreatePonderAccess.shared(id));
    }

    public static String translateTag(ResourceLocation id, String returned) {
        return translateLocalization(returned, CreatePonderAccess.tag(id));
    }

    public static String translateTagDescription(ResourceLocation id, String returned) {
        return translateLocalization(returned, CreatePonderAccess.tagDescription(id));
    }

    public static String translateChapter(ResourceLocation id, String returned) {
        return translateLocalization(returned, CreatePonderAccess.chapter(id));
    }

    public static String translateSpecific(ResourceLocation id, String key, String returned) {
        return translateLocalization(returned, CreatePonderAccess.specific(id, key));
    }

    private static String translateLocalization(String returned, String fallback) {
        if (!canTranslate()) return returned;

        String source = chooseSource(returned, fallback);
        if (source == null || source.isBlank()) return returned;

        String translated = translateBakedText(source);
        ProjectBabelDebug.info(
            ProjectBabelDebug.PONDER,
            "localization source={} returned={} fallback={}",
            truncate(source, 60),
            truncate(returned, 60),
            truncate(fallback, 60)
        );
        if (translated == null || translated.isBlank()) {
            return source;
        }

        if (!translated.equals(source)) return translated;
        return isLocalizationKey(returned) && isUsefulFallback(fallback, returned) ? fallback : returned;
    }

    private static String chooseSource(String returned, String fallback) {
        if (isLocalizationKey(returned) && isUsefulFallback(fallback, returned)) {
            return fallback;
        }
        if (returned == null || returned.isBlank()) return fallback;
        return returned;
    }

    private static String rememberTranslated(String original, String translated) {
        translated = TextFormatUtils.collapseExactDuplicateTranslation(original, translated);
        translated = TextFormatUtils.collapseRepeatedTranslation(translated);
        if (translated != null && !translated.equals(original)) {
            TranslationSkipRegistry.skipText(translated);
        }
        return translated == null ? original : translated;
    }


    private static boolean isUsefulFallback(String fallback, String returned) {
        return fallback != null
            && !fallback.isBlank()
            && !fallback.equals(returned)
            && !isLocalizationKey(fallback);
    }

    private static boolean isLocalizationKey(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.indexOf(' ') >= 0) return false;
        return text.startsWith("create.ponder.")
            || text.startsWith("ponder.")
            || text.startsWith("createponder.")
            || text.contains(".ponder.");
    }

    private static boolean canTranslate() {
        return ProjectBabelCommon.config().isEnabled()
            && LanguageDetector.shouldModBeActive()
            && isLoaded();
    }

    private static boolean isLoaded() {
        try {
            return ProjectBabelCommon.platform().mods().isLoaded("create")
                || ProjectBabelCommon.platform().mods().isLoaded("createponder")
                || ProjectBabelCommon.platform().mods().isLoaded("ponder");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
