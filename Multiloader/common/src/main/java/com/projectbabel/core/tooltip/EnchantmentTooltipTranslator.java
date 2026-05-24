package com.projectbabel.core.tooltip;

import com.projectbabel.minecraft.tooltip.EnchantmentDescriptionTranslator;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Tooltip-specific enchantment translation path.
 *
 * The underlying EnchantmentDescriptionTranslator handles async/cache behavior.
 * This wrapper keeps tooltip sanitization, especially duplicate roman numeral
 * cleanup, out of the mixins/events.
 */
public final class EnchantmentTooltipTranslator {

    private EnchantmentTooltipTranslator() {}

    public static Component translateDescription(Component original) {
        Component translated = EnchantmentDescriptionTranslator.translate(original);
        return collapseRomanDuplicate(translated == null ? original : translated);
    }

    public static Component collapseRomanDuplicate(Component component) {
        if (component == null) return null;

        String text = component.getString();
        String collapsed = TextFormatUtils.collapseDuplicateTrailingRoman(text);
        if (collapsed == null || collapsed.equals(text)) return component;

        Style style = component.getStyle();
        Component rebuilt = Component.literal(collapsed).withStyle(style == null ? Style.EMPTY : style);
        TranslationSkipRegistry.skip(rebuilt);
        return rebuilt;
    }
}
