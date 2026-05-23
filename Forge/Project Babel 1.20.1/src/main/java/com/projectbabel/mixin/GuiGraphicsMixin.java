package com.projectbabel.mixin;

import com.projectbabel.translation.RenderingGuard;
import com.projectbabel.translation.TextFormatUtils;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import com.projectbabel.event.MixinHealthCheck;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepta GuiGraphics#drawString para textos do tipo String e Component.
 *
 * NÃO ativa RenderingGuard — o FontMixin também traduz de forma independente.
 * A dupla tradução é evitada pelo isAlreadyTranslated() do cache: quando um
 * texto traduzido chega novamente, o cache reconhece e não reprocessa.
 */
@Mixin(value = GuiGraphics.class, priority = 1100)
public abstract class GuiGraphicsMixin {

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private String projectbabel$drawStringShadow(String text) {
        if (RenderingGuard.isActive()) return text;
        MixinHealthCheck.mixinHitCounter.incrementAndGet();
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private String projectbabel$drawString(String text) {
        if (RenderingGuard.isActive()) return text;
        MixinHealthCheck.mixinHitCounter.incrementAndGet();
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private String projectbabel$drawCenteredString(String text) {
        if (RenderingGuard.isActive()) return text;
        MixinHealthCheck.mixinHitCounter.incrementAndGet();
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private Component projectbabel$drawStringComponentShadow(Component original) {
        if (RenderingGuard.isActive()) return original;
        MixinHealthCheck.mixinHitCounter.incrementAndGet();
        return translateComponent(original);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private Component projectbabel$drawStringComponent(Component original) {
        if (RenderingGuard.isActive()) return original;
        MixinHealthCheck.mixinHitCounter.incrementAndGet();
        return translateComponent(original);
    }

    private String translateString(String text) {
        try {
            String translated = TranslationPipeline.translateString(text);
            translated = TextFormatUtils.collapseExactDuplicateTranslation(text, translated);
            translated = TextFormatUtils.collapseRepeatedTranslation(translated);
            if (translated != null && !translated.equals(text)) {
                TranslationSkipRegistry.skipText(translated);
            }
            return translated;
        } catch (Exception e) {
            return text;
        }
    }

    private Component translateComponent(Component original) {
        try {
            return TranslationPipeline.translateComponentTree(original);
        } catch (Exception e) {
            return original;
        }
    }
}
