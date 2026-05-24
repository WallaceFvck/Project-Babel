package com.projectbabel.fabric.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.minecraft.tooltip.EnchantmentTooltipTranslator;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;

import java.util.List;

import com.projectbabel.mixin.vanilla.TooltipMixin;
public final class DynamicTooltipTranslationHandler {

    private static boolean registered = false;

    private DynamicTooltipTranslationHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> onItemTooltip(lines));
    }

    private static void onItemTooltip(List<Component> lines) {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        // Não mutar aqui. Este callback pode rodar antes de outros mods anexarem
        // linhas, e mexer no nome do encantamento cedo demais impede mods como
        // Enchantment Descriptions de encontrar a linha original. O TooltipMixin
        // traduz no RETURN de ItemStack#getTooltipLines, quando a lista já está final.
        EnchantmentTooltipTranslator.prewarmTooltipLines(lines);
    }
}
