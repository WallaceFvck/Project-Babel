package com.projectbabel.mixin.vanilla;

import com.projectbabel.debug.MixinHitCounter;
import com.projectbabel.minecraft.render.RenderFallbackTranslator;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Final Font render fallback.
 *
 * This layer is intentionally cache-only: it may apply translations already
 * present in memory/disk cache, but it must never schedule API calls, block the
 * render thread, or run heavy language detection. Layout-sensitive integrations
 * translate before their own wrapping/layout passes instead.
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
        String translated = RenderFallbackTranslator.translateString(text);
        if (translated != text) MixinHitCounter.hit();
        return translated;
    }

    @ModifyVariable(
        method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$drawInBatchComponent(Component original) {
        Component translated = RenderFallbackTranslator.translateComponent(original);
        if (translated != original) MixinHitCounter.hit();
        return translated;
    }
}
