package com.projectbabel.minecraft.tooltip;

import net.minecraft.network.chat.Component;

import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationTriageManager;
/**
 * Compatibilidade para chamadas antigas. A lógica real agora vive em
 * EnchantmentTooltipTranslator, que trabalha na tooltip inteira depois que todos
 * os mods já anexaram suas linhas.
 */
public final class EnchantmentDescriptionTranslator {

    private EnchantmentDescriptionTranslator() {}

    public static Component translate(Component original) {
        return EnchantmentTooltipTranslator.translateEnchantmentDescription(original);
    }

    public static boolean isDescriptionComponent(Component component) {
        return EnchantmentTooltipTranslator.isEnchantmentDescriptionComponent(component);
    }

    public static void clear() {
        // Sem cache próprio. O cache fica no TranslationTriageManager/TranslationManager.
    }
}
