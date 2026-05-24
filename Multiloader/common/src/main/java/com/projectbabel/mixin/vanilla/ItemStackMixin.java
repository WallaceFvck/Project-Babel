package com.projectbabel.mixin.vanilla;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepta ItemStack#getHoverName() — nome do item exibido no HUD, chat,
 * nome flutuante em entidades, e como primeira linha do tooltip.
 *
 * Complementa TooltipMixin: este cobre contextos fora do inventário (HUD,
 * chat, nome de mob com item). TooltipMixin cobre o tooltip completo.
 *
 * Descritor completo para o annotationProcessor.
 */
@Mixin(value = ItemStack.class, priority = 900)
public abstract class ItemStackMixin {

    @Inject(
        method = "getHoverName()Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void projectbabel$getHoverName(CallbackInfoReturnable<Component> cir) {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        Component original = cir.getReturnValue();
        if (original == null) return;

        ItemStack stack = (ItemStack) (Object) this;
        if (stack.hasCustomHoverName() && !ProjectBabelCommon.config().isTranslateRenamedItems()) {
            TranslationSkipRegistry.skip(original);
            return;
        }

        Component translated = TranslationPipeline.collapseTooltipRomanDuplicate(
            TranslationPipeline.translateComponentTree(original));
        TranslationSkipRegistry.skip(translated == null ? original : translated);
        if (translated != original) cir.setReturnValue(translated);
    }
}
