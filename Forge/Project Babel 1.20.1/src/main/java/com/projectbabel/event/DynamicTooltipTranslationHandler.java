package com.projectbabel.event;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.TextFilter;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ProjectBabelMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DynamicTooltipTranslationHandler {

    private DynamicTooltipTranslationHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!AutoTranslateConfig.isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        List<Component> lines = event.getToolTip();
        if (lines == null || lines.isEmpty()) return;

        List<Component> translated = new ArrayList<>(lines.size());
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line == null) {
                translated.add(null);
                continue;
            }

            if (i == 0) {
                TranslationSkipRegistry.skip(line);
                translated.add(line);
                continue;
            }

            boolean enchantmentDescription = TranslationPipeline.isEnchantmentDescriptionComponent(line);
            Component result = TranslationPipeline.translateEnchantmentDescription(line);
            if (result == line && !enchantmentDescription) {
                result = TranslationPipeline.translateComponentTree(line);
            }
            result = TranslationPipeline.collapseTooltipRomanDuplicate(result);
            EnchantmentTranslationDebug.tooltipLine(i, null, line, result);
            translated.add(result);
            changed |= result != line;
        }

        if (changed) {
            lines.clear();
            lines.addAll(translated);
        }
    }
}
