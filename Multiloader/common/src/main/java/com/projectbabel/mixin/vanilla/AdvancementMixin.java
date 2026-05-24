package com.projectbabel.mixin.vanilla;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.pipeline.TranslationPipeline;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepta DisplayInfo#getTitle() e #getDescription() para traduzir
 * nomes e descrições de conquistas (advancements).
 *
 * Descritores completos para evitar warning do annotationProcessor.
 */
@Mixin(value = DisplayInfo.class, priority = 900)
public abstract class AdvancementMixin {

    @Inject(
        method = "getTitle()Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void projectbabel$translateTitle(CallbackInfoReturnable<Component> cir) {
        translateComponent(cir);
    }

    @Inject(
        method = "getDescription()Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void projectbabel$translateDescription(CallbackInfoReturnable<Component> cir) {
        translateComponent(cir);
    }

    private void translateComponent(CallbackInfoReturnable<Component> cir) {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        Component original = cir.getReturnValue();
        if (original == null) return;

        Component translated = TranslationPipeline.translateComponentTree(original);
        if (translated != original) cir.setReturnValue(translated);
    }
}
