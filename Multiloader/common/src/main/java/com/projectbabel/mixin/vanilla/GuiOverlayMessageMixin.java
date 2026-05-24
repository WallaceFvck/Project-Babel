package com.projectbabel.mixin.vanilla;

import com.projectbabel.core.pipeline.TranslationPipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Gui.class, priority = 900)
public abstract class GuiOverlayMessageMixin {

    @ModifyVariable(
        method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$overlayMessage(Component message) {
        return TranslationPipeline.translateComponentTree(message);
    }

    @ModifyVariable(
        method = "setTitle(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$title(Component title) {
        return TranslationPipeline.translateComponentTree(title);
    }

    @ModifyVariable(
        method = "setSubtitle(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$subtitle(Component subtitle) {
        return TranslationPipeline.translateComponentTree(subtitle);
    }
}
