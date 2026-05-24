package com.projectbabel.minecraft.tooltip;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.ProjectBabelCommon;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
/**
 * Camada única para tooltips de itens e encantamentos.
 *
 * Objetivo:
 * - deixar o Minecraft e os outros mods montarem a tooltip original primeiro;
 * - só depois traduzir cada linha, preservando ordem, quantidade de linhas,
 *   estilos e siblings;
 * - nunca transformar uma tooltip complexa em um único literal quando ela tem
 *   filhos, porque é assim que descrições de encantamento/lore de muitos mods
 *   acabam desaparecendo;
 * - manter nomes de encantamento e descrições independentes: traduzir o nome
 *   não pode impedir que a descrição seja adicionada logo abaixo.
 */
public final class EnchantmentTooltipTranslator {

    private static final ThreadLocal<Integer> TOOLTIP_BUILD_DEPTH = ThreadLocal.withInitial(() -> 0);

    private EnchantmentTooltipTranslator() {}

    public static void beginTooltipBuild() {
        TOOLTIP_BUILD_DEPTH.set(TOOLTIP_BUILD_DEPTH.get() + 1);
    }

    public static void endTooltipBuild() {
        int depth = TOOLTIP_BUILD_DEPTH.get() - 1;
        if (depth <= 0) {
            TOOLTIP_BUILD_DEPTH.remove();
        } else {
            TOOLTIP_BUILD_DEPTH.set(depth);
        }
    }

    public static boolean isBuildingTooltip() {
        return TOOLTIP_BUILD_DEPTH.get() > 0;
    }

    public static List<Component> translateTooltipLines(ItemStack stack, TooltipFlag flag, List<Component> original) {
        if (!canRun()) return original;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return original;
        if (original == null || original.isEmpty()) return original;

        List<Component> result = new ArrayList<>(original.size());
        boolean changed = false;

        for (int i = 0; i < original.size(); i++) {
            Component line = original.get(i);
            if (line == null) {
                result.add(null);
                continue;
            }

            Component translated;
            if (i == 0 && stack != null && stack.hasCustomHoverName() && !ProjectBabelCommon.config().isTranslateRenamedItems()) {
                TranslationSkipRegistry.skip(line);
                translated = line;
            } else {
                translated = translateTooltipLine(line, i == 0);
            }

            translated = collapseRomanOnlyWhenSafe(translated);

            result.add(translated);
            changed |= translated != line;
        }

        return changed ? result : original;
    }

    public static void translateMutableTooltipLines(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return;
        List<Component> translated = translateTooltipLines(null, null, lines);
        if (translated == lines) return;
        lines.clear();
        lines.addAll(translated);
    }

    public static void prewarmTooltipLines(List<Component> lines) {
        if (!canRun() || lines == null || lines.isEmpty()) return;
        for (Component line : lines) {
            prewarm(line);
        }
    }

    public static void prewarm(Component component) {
        if (component == null || !canRun()) return;
        if (TranslationSkipRegistry.shouldSkipIdentity(component)) return;
        // translateTooltipLine schedules async work on cache misses and returns
        // immediately on the render thread.
        translateTooltipLine(component, false);
    }

    public static Component translateEnchantmentDescription(Component original) {
        if (original == null || !canRun()) return original;
        if (!isEnchantmentDescriptionComponent(original)) return original;
        return translateTooltipLine(original, false);
    }

    public static Component translateEnchantmentProviderOutput(Component original) {
        if (original == null || !canRun()) return original;
        // Used by optional provider-level mixins, such as Enchantment Descriptions.
        // It is intentionally conservative: no list mutation, no flattening.
        return translateTooltipLine(original, false);
    }

    public static boolean isEnchantmentDescriptionComponent(Component component) {
        if (component == null) return false;

        if (component.getContents() instanceof TranslatableContents translatable) {
            if (isDescriptionKey(translatable.getKey())) return true;
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent && isEnchantmentDescriptionComponent(argComponent)) {
                    return true;
                }
                if (arg instanceof String string && looksLikeDescriptionKey(string)) {
                    return true;
                }
            }
        }

        for (Component sibling : component.getSiblings()) {
            if (isEnchantmentDescriptionComponent(sibling)) return true;
        }

        return false;
    }

    public static boolean isEnchantmentRelated(Component component) {
        if (component == null) return false;
        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key != null && key.startsWith("enchantment.")) return true;
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent && isEnchantmentRelated(argComponent)) return true;
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (isEnchantmentRelated(sibling)) return true;
        }
        return false;
    }

    private static Component translateTooltipLine(Component line, boolean firstLine) {
        if (line == null) return null;
        if (TranslationSkipRegistry.shouldSkipIdentity(line)) return line;

        Component translated = translateComponentPreservingTree(line);
        if (translated == null) return line;

        if (translated != line && !safeReplacement(line, translated)) {
            return line;
        }

        if (translated != line) {
            TranslationSkipRegistry.skip(translated);
            return translated;
        }

        // Cache miss: keep the original line this frame. Do not mark the original
        // text as permanently skipped, because the async cache may be filled right
        // after this frame.
        if (firstLine && isEnchantmentRelated(line)) {
            TranslationPipeline.translateComponent(line, TranslationContext.tooltip());
        }
        return line;
    }

    private static Component translateComponentPreservingTree(Component original) {
        if (original == null) return null;

        if (original.getContents() instanceof LiteralContents literal) {
            return translateLiteralPreservingSiblings(original, literal);
        }

        if (original.getContents() instanceof TranslatableContents translatable) {
            return translateTranslatablePreservingSiblings(original, translatable);
        }

        // Unknown component contents: only translate siblings. Replacing the whole
        // line with getString() is the main thing that makes descriptions/lore vanish.
        if (!original.getSiblings().isEmpty()) {
            MutableComponent rebuilt = null;
            for (Component sibling : original.getSiblings()) {
                Component translatedSibling = translateComponentPreservingTree(sibling);
                if (translatedSibling != sibling && rebuilt == null) {
                    rebuilt = Component.empty().withStyle(styleOf(original));
                }
                if (rebuilt != null) rebuilt.append(translatedSibling);
            }
            return rebuilt == null ? original : rebuilt;
        }

        return TranslationPipeline.translateComponent(original, TranslationContext.tooltip());
    }

    private static Component translateLiteralPreservingSiblings(Component original, LiteralContents literal) {
        String text = literal.text();
        String translatedText = TranslationPipeline.translateString(text, TranslationContext.tooltip());
        boolean textChanged = translatedText != null && !translatedText.equals(text);

        Component[] translatedSiblings = translateSiblings(original);
        if (!textChanged && translatedSiblings == null) return original;

        MutableComponent rebuilt = Component.literal(textChanged ? translatedText : text).withStyle(styleOf(original));
        appendSiblings(rebuilt, translatedSiblings, original);
        return rebuilt;
    }

    private static Component translateTranslatablePreservingSiblings(Component original, TranslatableContents translatable) {
        String key = translatable.getKey();
        boolean descriptionKey = isDescriptionKey(key);

        // Let the generic pipeline do the hard work for translation keys. It already
        // respects native lang files and schedules async cache fills. We only reject
        // unsafe replacements that would remove siblings.
        Component translated = TranslationPipeline.translateComponent(original, TranslationContext.tooltip());
        if (translated != null && translated != original && safeReplacement(original, translated)) {
            return translated;
        }

        Object[] args = translatable.getArgs();
        Object[] translatedArgs = null;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Object translatedArg = translateArgument(arg);
            if (translatedArg != arg) {
                if (translatedArgs == null) translatedArgs = args.clone();
                translatedArgs[i] = translatedArg;
            }
        }

        Component[] translatedSiblings = translateSiblings(original);
        if (translatedArgs == null && translatedSiblings == null) return original;

        MutableComponent rebuilt = Component.translatable(
            key,
            translatedArgs == null ? args : translatedArgs
        ).withStyle(styleOf(original));
        appendSiblings(rebuilt, translatedSiblings, original);

        // Description lines are safe to mark after a real tree rebuild. This prevents
        // the late Font/GuiGraphics hooks from running a second pass over the same line.
        if (descriptionKey) TranslationSkipRegistry.skip(rebuilt);
        return rebuilt;
    }

    private static Object translateArgument(Object arg) {
        if (arg instanceof Component component) {
            return translateComponentPreservingTree(component);
        }
        if (arg instanceof String string) {
            String translated = TranslationPipeline.translateString(string, TranslationContext.tooltip());
            return translated == null || translated.equals(string) ? arg : translated;
        }
        return arg;
    }

    private static Component[] translateSiblings(Component original) {
        if (original.getSiblings().isEmpty()) return null;

        Component[] translatedSiblings = null;
        for (int i = 0; i < original.getSiblings().size(); i++) {
            Component sibling = original.getSiblings().get(i);
            Component translatedSibling = translateComponentPreservingTree(sibling);
            if (translatedSibling != sibling) {
                if (translatedSiblings == null) {
                    translatedSiblings = original.getSiblings().toArray(Component[]::new);
                }
                translatedSiblings[i] = translatedSibling;
            }
        }
        return translatedSiblings;
    }

    private static void appendSiblings(MutableComponent rebuilt, Component[] translatedSiblings, Component original) {
        if (translatedSiblings == null) {
            for (Component sibling : original.getSiblings()) rebuilt.append(sibling);
            return;
        }
        for (Component sibling : translatedSiblings) rebuilt.append(sibling);
    }

    private static Component collapseRomanOnlyWhenSafe(Component component) {
        if (component == null) return null;
        if (!component.getSiblings().isEmpty()) return component;
        return TranslationPipeline.collapseTooltipRomanDuplicate(component);
    }

    private static boolean safeReplacement(Component original, Component translated) {
        if (original == null || translated == null) return false;
        if (original.getSiblings().isEmpty()) return true;
        // Replacements with siblings are fine. Replacements without siblings would
        // flatten/erase appended lore, descriptions or styled fragments.
        return !translated.getSiblings().isEmpty();
    }

    private static Style styleOf(Component component) {
        Style style = component.getStyle();
        return style == null ? Style.EMPTY : style;
    }

    private static boolean canRun() {
        return ProjectBabelCommon.config().isEnabled() && LanguageDetector.shouldModBeActive();
    }

    private static boolean isDescriptionKey(String key) {
        if (key == null || key.isBlank()) return false;
        return key.startsWith("enchantment.")
            && (key.endsWith(".desc") || key.endsWith(".description") || key.endsWith(".tooltip"));
    }

    private static boolean looksLikeDescriptionKey(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("enchantment.")
            && (lower.endsWith(".desc") || lower.endsWith(".description") || lower.endsWith(".tooltip"));
    }
}
