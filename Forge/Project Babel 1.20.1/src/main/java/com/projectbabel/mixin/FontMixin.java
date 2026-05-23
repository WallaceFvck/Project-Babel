package com.projectbabel.mixin;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.RenderingGuard;
import com.projectbabel.translation.TextFormatUtils;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import com.projectbabel.event.MixinHealthCheck;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepta Font#drawInBatch — camada mais próxima do render real.
 *
 * Esta é a camada que captura o texto do Patchouli, pois o Patchouli passa
 * seus Components direto para Font sem necessariamente passar por GuiGraphics.
 *
 * Dupla tradução (GuiGraphics + Font para o mesmo texto) é prevenida pelo
 * isAlreadyTranslated() do cache: o texto traduzido é registrado como output
 * e ao chegar novamente como input é reconhecido e ignorado.
 */
@Mixin(value = Font.class, priority = 1100)
public abstract class FontMixin {

    @ModifyVariable(
        method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private String projectbabel$drawInBatchString(String text) {
        if (RenderingGuard.isActive()) return text;
        MixinHealthCheck.mixinHitCounter.incrementAndGet();
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$drawInBatchComponent(Component original) {
        if (original == null) return null;
        if (RenderingGuard.isActive()) return original;
        if (!AutoTranslateConfig.isEnabled()) return original;
        if (!LanguageDetector.shouldModBeActive()) return original;
        try {
            Component translated = TranslationPipeline.translateComponentTree(original);
            if (translated == original) return original;
            MixinHealthCheck.mixinHitCounter.incrementAndGet();
            return translated;
        } catch (Exception e) {
            return original;
        }
    }

    private String translateString(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!AutoTranslateConfig.isEnabled()) return text;
        if (!LanguageDetector.shouldModBeActive()) return text;
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
}
