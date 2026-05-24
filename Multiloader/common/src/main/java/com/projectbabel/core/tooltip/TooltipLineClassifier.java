package com.projectbabel.core.tooltip;

import com.projectbabel.minecraft.tooltip.EnchantmentDescriptionTranslator;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;

/**
 * Central tooltip line classifier.
 *
 * This class intentionally does not translate anything. It only decides which
 * specialized path a line should take, so item tooltips, custom mod tooltips and
 * Forge ItemTooltipEvent share the same rules.
 */
public final class TooltipLineClassifier {

    private TooltipLineClassifier() {}

    public static TooltipLineKind classify(Component line, int index, boolean firstLineIsItemTitle) {
        if (line == null) return TooltipLineKind.NULL;
        if (index == 0 && firstLineIsItemTitle) return TooltipLineKind.ITEM_TITLE;

        String text = line.getString();
        if (text == null || text.isBlank()) return TooltipLineKind.EMPTY;

        if (TranslationSkipRegistry.shouldSkip(line)) {
            return TooltipLineKind.ALREADY_HANDLED;
        }

        if (EnchantmentDescriptionTranslator.isDescriptionComponent(line)) {
            return TooltipLineKind.ENCHANTMENT_DESCRIPTION;
        }

        return TooltipLineKind.GENERIC_TEXT;
    }
}
