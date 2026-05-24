package com.projectbabel.mixin.vanilla;

import com.projectbabel.debug.MixinHitCounter;
import com.projectbabel.minecraft.render.RenderFallbackTranslator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * GuiGraphics draw fallback.
 *
 * This mixin only consumes already-cached translations. Semantic hooks such as
 * tooltip, chat, quest, book, widget, and compat adapters are responsible for
 * scheduling/preloading real translation work before render.
 */
@Mixin(value = GuiGraphics.class, priority = 1100)
public abstract class GuiGraphicsMixin {

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private String projectbabel$drawStringShadow(String text) {
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private String projectbabel$drawString(String text) {
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private String projectbabel$drawCenteredString(String text) {
        return translateString(text);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private Component projectbabel$drawStringComponentShadow(Component original) {
        return translateComponent(original);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0
    )
    private Component projectbabel$drawStringComponent(Component original) {
        return translateComponent(original);
    }

    private String translateString(String text) {
        String translated = RenderFallbackTranslator.translateString(text);
        if (translated != text) MixinHitCounter.hit();
        return translated;
    }

    private Component translateComponent(Component original) {
        Component translated = RenderFallbackTranslator.translateComponent(original);
        if (translated != original) MixinHitCounter.hit();
        return translated;
    }
}
