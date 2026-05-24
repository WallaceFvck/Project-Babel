package com.projectbabel.core.tooltip;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.api.TranslationContext;
import com.projectbabel.api.TranslationSurface;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.service.TranslationServices;
import com.projectbabel.core.text.LanguageDetector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Single platform-neutral owner for tooltip translation decisions.
 *
 * Platform mixins/events should only collect the tooltip list and call this
 * controller. This keeps title skipping, enchantment-description handling,
 * generic line translation and roman numeral cleanup consistent across Forge
 * and Fabric.
 */
public final class TooltipTranslationController {

    private TooltipTranslationController() {}

    /**
     * Item tooltips treat line 0 as the item title. The title is handled by
     * ItemStack#getHoverName(), so this path skips it and marks it as handled.
     */
    public static List<Component> translateItemTooltipLines(List<Component> lines, TooltipFlag flag) {
        return translateLines(lines, true, flag, TranslationContext.tooltip());
    }

    /**
     * Custom mod tooltips may not have an item-title first line, so every line is
     * eligible for translation unless the skip registry says it was handled.
     */
    public static List<Component> translateCustomTooltipLines(List<Component> lines) {
        return translateLines(lines, false, null, TranslationContext.tooltip());
    }

    /**
     * Cache-only variant for render paths that must never enqueue extra work.
     */
    public static List<Component> translateCustomTooltipLinesCacheOnly(List<Component> lines) {
        return translateLines(lines, false, null, TranslationContext.cacheOnly(TranslationSurface.TOOLTIP));
    }

    /** Applies the translated item tooltip back into a mutable event list. */
    public static boolean replaceItemTooltipLinesInPlace(List<Component> lines, TooltipFlag flag) {
        List<Component> translated = translateItemTooltipLines(lines, flag);
        if (translated == lines) return false;

        lines.clear();
        lines.addAll(translated);
        return true;
    }

    /**
     * Pre-warms an item name without duplicating the renamed-item guard in every
     * caller. The first rendered tooltip line remains skipped by the item path.
     */
    public static void prewarmItemName(ItemStack stack) {
        if (!canRun() || stack == null || stack.isEmpty()) return;

        Component name = stack.getHoverName();
        if (name == null) return;

        if (stack.hasCustomHoverName() && !ProjectBabelCommon.config().isTranslateRenamedItems()) {
            TranslationSkipRegistry.skip(name);
            return;
        }

        Component translated = TranslationServices.translation().translate(name, TranslationContext.itemName());
        TranslationSkipRegistry.skip(translated == null ? name : translated);
    }

    /**
     * Pre-warms tooltip body lines using the same classification rules used by
     * actual rendering. Title line handling matches item tooltip rendering.
     */
    public static void prewarmItemTooltipLines(List<Component> lines, TooltipFlag flag) {
        if (lines == null || lines.size() <= 1) return;
        translateLines(lines, true, flag, TranslationContext.tooltip());
    }

    private static List<Component> translateLines(
        List<Component> lines,
        boolean firstLineIsItemTitle,
        TooltipFlag flag,
        TranslationContext context
    ) {
        if (!canRun()) return lines;
        if (lines == null || lines.isEmpty()) return lines;

        List<Component> translated = new ArrayList<>(lines.size());
        boolean changed = false;

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            TooltipLineKind kind = TooltipLineClassifier.classify(line, i, firstLineIsItemTitle);
            Component result = translateLine(line, kind, context);
            translated.add(result);
            changed |= result != line;
        }

        return changed ? translated : lines;
    }

    private static Component translateLine(Component line, TooltipLineKind kind, TranslationContext context) {
        if (kind == TooltipLineKind.NULL) return null;
        if (line == null) return null;

        switch (kind) {
            case ITEM_TITLE -> {
                TranslationSkipRegistry.skip(line);
                return line;
            }
            case EMPTY, ALREADY_HANDLED -> {
                return line;
            }
            case ENCHANTMENT_DESCRIPTION -> {
                return EnchantmentTooltipTranslator.translateDescription(line);
            }
            case GENERIC_TEXT -> {
                Component translated = TranslationServices.translation().translate(
                    line,
                    context == null ? TranslationContext.tooltip() : context
                );
                return EnchantmentTooltipTranslator.collapseRomanDuplicate(translated == null ? line : translated);
            }
            default -> {
                return line;
            }
        }
    }

    private static boolean canRun() {
        return ProjectBabelCommon.config().isEnabled() && LanguageDetector.shouldModBeActive();
    }
}
