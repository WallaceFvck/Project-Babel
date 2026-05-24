package com.projectbabel.mixin.vanilla;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.minecraft.chat.ChatTranslationHelper;
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

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;Z)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0
    )
    private Component projectbabel$addInternalMessage(Component message) {
        return translateChat(message);
    }

    private Component translateChat(Component message) {
        if (!ProjectBabelCommon.config().isTranslateChat()) return message;
        return ChatTranslationHelper.translateIncoming(message);
    }
}
