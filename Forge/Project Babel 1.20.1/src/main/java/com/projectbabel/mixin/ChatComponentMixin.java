package com.projectbabel.mixin;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.TranslationPipeline;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ChatComponent.class, priority = 900)
public abstract class ChatComponentMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$addMessage(Component message) {
        return translateChat(message);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$addSignedMessage(Component message) {
        return translateChat(message);
    }

    private Component translateChat(Component message) {
        if (!AutoTranslateConfig.isTranslateChat()) return message;
        return TranslationPipeline.translateComponentTree(message);
    }
}
