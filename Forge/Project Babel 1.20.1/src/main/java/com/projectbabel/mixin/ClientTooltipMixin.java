package com.projectbabel.mixin;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepta GuiGraphics#renderTooltip — caminho real dos tooltips em 1.20.1.
 *
 * Em 1.20.1, o fluxo de tooltip é:
 *   Screen.renderTooltip() → GuiGraphics.renderTooltip(Font, List<ClientTooltipComponent>, int, int)
 *
 * REMOVIDO: o target anterior era Screen.renderTooltip(Font, List<Component>, Optional, int, int)
 * que NÃO existe no 1.20.1 — causava erro de compilação com o annotationProcessor ativo.
 *
 * NOTA: tooltips de item já são cobertos pelo TooltipMixin (getTooltipLines).
 * Este mixin cobre tooltips customizados de mods que não passam por getTooltipLines.
 * Como usa require=0, é seguro mesmo se a assinatura variar entre modpacks.
 */
@Mixin(value = GuiGraphics.class, priority = 800)
public abstract class ClientTooltipMixin {

    /**
     * GuiGraphics.renderTooltip(Font, ItemStack, int, int) — tooltip de item via ItemStack direto.
     * Intercepta antes da renderização para garantir que o nome do item seja traduzido.
     * require=0: não crasha se não encontrar.
     */
    @Inject(
        method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
        at = @At("HEAD"),
        require = 0
    )
    private void projectbabel$renderTooltipItemStack(
        net.minecraft.client.gui.Font font,
        ItemStack stack, int x, int y,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        // Pre-warm: garante que o nome do item já está sendo traduzido
        // antes da renderização do tooltip, para aparecer na próxima frame
        if (!AutoTranslateConfig.isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (stack == null || stack.isEmpty()) return;

        if (stack.hasCustomHoverName() && !AutoTranslateConfig.isTranslateRenamedItems()) {
            TranslationSkipRegistry.skip(stack.getHoverName());
        } else {
            TranslationPipeline.translateComponentTree(stack.getHoverName());
        }

        // Não chama stack.getTooltipLines() aqui: o próprio renderTooltip fará isso em seguida.
        // Gerar o tooltip duas vezes por frame era especialmente caro em livros com descrições de encantamento.
    }

    @ModifyVariable(
        method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0
    )
    private List<Component> projectbabel$renderTooltipListOptional(List<Component> lines) {
        return translateTooltipLines(lines);
    }

    @ModifyVariable(
        method = "renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0
    )
    private List<Component> projectbabel$renderComponentTooltip(List<Component> lines) {
        return translateTooltipLines(lines);
    }

    private List<Component> translateTooltipLines(List<Component> lines) {
        if (!AutoTranslateConfig.isEnabled()) return lines;
        if (!LanguageDetector.shouldModBeActive()) return lines;
        if (lines == null || lines.isEmpty()) return lines;

        List<Component> translated = new ArrayList<>(lines.size());
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            boolean enchantmentDescription = TranslationPipeline.isEnchantmentDescriptionComponent(line);
            Component result = TranslationPipeline.translateEnchantmentDescription(line);
            if (result != line) {
                result = TranslationPipeline.collapseTooltipRomanDuplicate(result);
                EnchantmentTranslationDebug.tooltipLine(i, null, line, result);
                translated.add(result);
                changed = true;
                continue;
            }
            if (enchantmentDescription || TranslationSkipRegistry.shouldSkip(line)) {
                EnchantmentTranslationDebug.tooltipLine(i, null, line, line);
                translated.add(line);
                continue;
            }
            result = TranslationPipeline.collapseTooltipRomanDuplicate(
                TranslationPipeline.translateComponentTree(line));
            EnchantmentTranslationDebug.tooltipLine(i, null, line, result);
            translated.add(result);
            changed |= result != line;
        }
        return changed ? translated : lines;
    }

}
