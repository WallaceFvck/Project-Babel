package com.projectbabel.mixin;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.TextFilter;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
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
        if (!AutoTranslateConfig.isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        Component original = cir.getReturnValue();
        if (original == null) return;

        ItemStack stack = (ItemStack) (Object) this;
        if (stack.hasCustomHoverName() && !AutoTranslateConfig.isTranslateRenamedItems()) {
            TranslationSkipRegistry.skip(original);
            return;
        }

        Component translated = TranslationPipeline.collapseTooltipRomanDuplicate(
            TranslationPipeline.translateComponentTree(original));
        TranslationSkipRegistry.skip(translated == null ? original : translated);
        if (translated != original) cir.setReturnValue(translated);
    }
}
