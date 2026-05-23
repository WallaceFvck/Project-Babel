package com.projectbabel.translation;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class CreatePonderTranslator {

    private static final String[] LOCALIZATION_CLASSES = {
        "com.simibubi.create.foundation.ponder.PonderLocalization",
        "net.createmod.ponder.foundation.PonderLocalization"
    };

    private CreatePonderTranslator() {}

    public static String translateBakedText(String text) {
        if (!canTranslate() || text == null || text.isEmpty()) return text;

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            String cached = TranslationPipeline.translateStringCacheOnly(text);
            if (cached != null && !cached.equals(text)) {
                ProjectBabelDebug.info(ProjectBabelDebug.PONDER, "cache hit text={} translated={}", truncate(text, 70), truncate(cached, 70));
                return rememberTranslated(text, cached);
            }

            ProjectBabelDebug.info(ProjectBabelDebug.PONDER, "blocking ponder text={}", truncate(text, 80));
            String translated = TranslationPipeline.translateStringBlocking(text);
            if (translated == null || translated.equals(text)) return text;
            return rememberTranslated(text, translated);
        } catch (Exception ignored) {
            return text;
        }
    }

    public static String translateShared(ResourceLocation id, String returned) {
        return translateLocalization(returned, readMapString("SHARED", id));
    }

    public static String translateTag(ResourceLocation id, String returned) {
        return translateLocalization(returned, readCouple("TAG", id, true));
    }

    public static String translateTagDescription(ResourceLocation id, String returned) {
        return translateLocalization(returned, readCouple("TAG", id, false));
    }

    public static String translateChapter(ResourceLocation id, String returned) {
        return translateLocalization(returned, readMapString("CHAPTER", id));
    }

    public static String translateSpecific(ResourceLocation id, String key, String returned) {
        return translateLocalization(returned, readSpecific(id, key));
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

    private static String readMapString(String fieldName, ResourceLocation id) {
        Object map = readStaticField(fieldName);
        if (!(map instanceof Map<?, ?> values)) return null;
        Object value = values.get(id);
        return value instanceof String text ? text : null;
    }

    private static String readCouple(String fieldName, ResourceLocation id, boolean first) {
        Object map = readStaticField(fieldName);
        if (!(map instanceof Map<?, ?> values)) return null;

        Object couple = values.get(id);
        if (couple == null) return null;

        try {
            Method method = couple.getClass().getMethod(first ? "getFirst" : "getSecond");
            Object value = method.invoke(couple);
            return value instanceof String text ? text : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readSpecific(ResourceLocation id, String key) {
        Object map = readStaticField("SPECIFIC");
        if (!(map instanceof Map<?, ?> values)) return null;

        Object nested = values.get(id);
        if (!(nested instanceof Map<?, ?> specific)) return null;

        Object value = specific.get(key);
        return value instanceof String text ? text : null;
    }

    private static Object readStaticField(String fieldName) {
        for (String className : LOCALIZATION_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        return null;
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
        return AutoTranslateConfig.isEnabled()
            && LanguageDetector.shouldModBeActive()
            && isLoaded();
    }

    private static boolean isLoaded() {
        try {
            ModList mods = ModList.get();
            return mods.isLoaded("create")
                || mods.isLoaded("createponder")
                || mods.isLoaded("ponder");
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
