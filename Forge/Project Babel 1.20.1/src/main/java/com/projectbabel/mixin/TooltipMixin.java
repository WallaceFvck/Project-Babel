package com.projectbabel.mixin;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.TextFilter;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepta ItemStack#getTooltipLines() para traduzir todas as linhas do tooltip:
 * nome, lore, encantamentos, atributos, durabilidade, etc.
 *
 * Descritor completo para evitar warning do annotationProcessor.
 * require=0: não crasha se a assinatura mudar em versões futuras.
 *
 * CONSOLIDADO com ItemStackMixin: agora este mixin cobre tanto o nome (linha 0)
 * quanto as descrições (linhas seguintes), eliminando a duplicação.
 */
@Mixin(value = ItemStack.class, priority = 900)
public abstract class TooltipMixin {

    @Inject(
        method = "getTooltipLines(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void projectbabel$translateTooltip(
        Player player, TooltipFlag flag,
        CallbackInfoReturnable<List<Component>> cir
    ) {
        if (!AutoTranslateConfig.isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        List<Component> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<Component> result = new ArrayList<>(original.size());
        boolean anyChanged = false;
        for (int i = 0; i < original.size(); i++) {
            Component line = original.get(i);
            if (line == null) { result.add(null); continue; }

            if (i == 0) {
                TranslationSkipRegistry.skip(line);
                result.add(line);
                continue;
            }

            boolean enchantmentDescription = TranslationPipeline.isEnchantmentDescriptionComponent(line);
            Component translated = TranslationPipeline.translateEnchantmentDescription(line);
            if (translated == line && !enchantmentDescription) {
                translated = TranslationPipeline.translateComponentTree(line);
            }
            translated = TranslationPipeline.collapseTooltipRomanDuplicate(translated);
            EnchantmentTranslationDebug.tooltipLine(i, flag, line, translated);
            if (translated != line) {
                result.add(translated);
                anyChanged = true;
            } else {
                result.add(line);
            }
        }

        if (anyChanged) cir.setReturnValue(result);
    }
}
