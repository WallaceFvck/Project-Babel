package com.projectbabel.integrations.generic;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.ProjectBabelCommon;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.ArrayList;
import java.util.List;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
public final class ModIntegrationTranslator {

    private ModIntegrationTranslator() {}

    public static boolean isAnyLoaded(String... modIds) {
        if (modIds == null || modIds.length == 0) return true;
        try {
            for (String modId : modIds) {
                if (modId != null && !modId.isBlank() && ProjectBabelCommon.platform().mods().isLoaded(modId)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String translateString(String text, String... modIds) {
        return translateStringWithContext(text, TranslationContext.compatMod(), modIds);
    }

    public static String translateStringCacheOnly(String text, String... modIds) {
        return translateStringWithContext(text, TranslationContext.compatMod().asCacheOnly(), modIds);
    }

    private static String translateStringWithContext(String text, TranslationContext context, String... modIds) {
        if (!canTranslate(modIds) || text == null || text.isEmpty()) return text;
        try {
            String translated = TranslationPipeline.translateString(text, context);
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

    public static Component translateComponent(Component component, String... modIds) {
        return translateComponentWithContext(component, TranslationContext.compatMod(), modIds);
    }

    public static Component translateComponentCacheOnly(Component component, String... modIds) {
        return translateComponentWithContext(component, TranslationContext.compatMod().asCacheOnly(), modIds);
    }

    private static Component translateComponentWithContext(Component component, TranslationContext context, String... modIds) {
        if (!canTranslate(modIds) || component == null) return component;
        try {
            Component translated = TranslationPipeline.translateComponent(component, context);
            return translated == null ? component : translated;
        } catch (Exception ignored) {
            return component;
        }
    }

    public static FormattedText translateFormattedText(FormattedText text, String... modIds) {
        if (text instanceof Component component) {
            return translateComponent(component, modIds);
        }
        return text;
    }

    public static FormattedText translateFormattedTextCacheOnly(FormattedText text, String... modIds) {
        if (text instanceof Component component) {
            return translateComponentCacheOnly(component, modIds);
        }
        return text;
    }

    public static List<Component> translateComponentList(List<Component> components, String... modIds) {
        return translateComponentListWithContext(components, TranslationContext.compatMod(), modIds);
    }

    public static List<Component> translateComponentListCacheOnly(List<Component> components, String... modIds) {
        return translateComponentListWithContext(components, TranslationContext.compatMod().asCacheOnly(), modIds);
    }

    private static List<Component> translateComponentListWithContext(
        List<Component> components,
        TranslationContext context,
        String... modIds
    ) {
        if (!canTranslate(modIds) || components == null || components.isEmpty()) return components;

        List<Component> translated = new ArrayList<>(components.size());
        boolean changed = false;
        for (Component component : components) {
            Component next = translateComponentWithContext(component, context, modIds);
            translated.add(next);
            changed |= next != component;
        }
        return changed ? translated : components;
    }

    public static void translateComponentListInPlace(List<Component> components, String... modIds) {
        translateComponentListInPlaceWithContext(components, TranslationContext.compatMod(), modIds);
    }

    public static void translateComponentListInPlaceCacheOnly(List<Component> components, String... modIds) {
        translateComponentListInPlaceWithContext(components, TranslationContext.compatMod().asCacheOnly(), modIds);
    }

    private static void translateComponentListInPlaceWithContext(
        List<Component> components,
        TranslationContext context,
        String... modIds
    ) {
        if (!canTranslate(modIds) || components == null || components.isEmpty()) return;

        for (int i = 0; i < components.size(); i++) {
            Component original = components.get(i);
            Component translated = translateComponentWithContext(original, context, modIds);
            if (translated != original) components.set(i, translated);
        }
    }

    private static boolean canTranslate(String... modIds) {
        return ProjectBabelCommon.config().isEnabled()
            && LanguageDetector.shouldModBeActive()
            && isAnyLoaded(modIds);
    }
}
