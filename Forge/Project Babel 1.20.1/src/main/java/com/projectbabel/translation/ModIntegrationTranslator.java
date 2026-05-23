package com.projectbabel.translation;

import com.projectbabel.config.AutoTranslateConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public final class ModIntegrationTranslator {

    private ModIntegrationTranslator() {}

    public static boolean isAnyLoaded(String... modIds) {
        if (modIds == null || modIds.length == 0) return true;
        try {
            ModList modList = ModList.get();
            for (String modId : modIds) {
                if (modId != null && !modId.isBlank() && modList.isLoaded(modId)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String translateString(String text, String... modIds) {
        if (!canTranslate(modIds) || text == null || text.isEmpty()) return text;
        try {
            String translated = TranslationPipeline.translateString(text);
            translated = TextFormatUtils.collapseExactDuplicateTranslation(text, translated);
            translated = TextFormatUtils.collapseRepeatedTranslation(translated);
            if (translated != null && !translated.equals(text)) {
                TranslationSkipRegistry.skipText(translated);
            }
            return translated;
        } catch (Exception ignored) {
            return text;
        }
    }

    public static Component translateComponent(Component component, String... modIds) {
        if (!canTranslate(modIds) || component == null) return component;
        try {
            return TranslationPipeline.translateComponentTree(component);
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

    public static List<Component> translateComponentList(List<Component> components, String... modIds) {
        if (!canTranslate(modIds) || components == null || components.isEmpty()) return components;

        List<Component> translated = new ArrayList<>(components.size());
        boolean changed = false;
        for (Component component : components) {
            Component next = translateComponent(component, modIds);
            translated.add(next);
            changed |= next != component;
        }
        return changed ? translated : components;
    }

    public static void translateComponentListInPlace(List<Component> components, String... modIds) {
        if (!canTranslate(modIds) || components == null || components.isEmpty()) return;

        for (int i = 0; i < components.size(); i++) {
            Component original = components.get(i);
            Component translated = translateComponent(original, modIds);
            if (translated != original) components.set(i, translated);
        }
    }

    private static boolean canTranslate(String... modIds) {
        return AutoTranslateConfig.isEnabled()
            && LanguageDetector.shouldModBeActive()
            && isAnyLoaded(modIds);
    }
}
