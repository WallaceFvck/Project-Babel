package com.projectbabel.mixin.vanilla;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.tooltip.TooltipTranslationController;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Intercepta ItemStack#getTooltipLines() para traduzir o corpo do tooltip.
 *
 * A decisão de linha 0, encantamentos, linhas já processadas e texto genérico
 * agora fica centralizada em TooltipTranslationController. Este mixin é apenas
 * o hook de entrada para tooltips de item.
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
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        List<Component> original = cir.getReturnValue();
        List<Component> translated = TooltipTranslationController.translateItemTooltipLines(original, flag);
        if (translated != original) cir.setReturnValue(translated);
    }
}
