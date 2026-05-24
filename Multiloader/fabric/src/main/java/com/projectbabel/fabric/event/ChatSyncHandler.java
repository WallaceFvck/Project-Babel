package com.projectbabel.fabric.event;

import com.projectbabel.mixin.vanilla.ChatComponentMixin;
/**
 * No Forge este handler usava ClientChatReceivedEvent para cancelar e reinjetar
 * mensagens assíncronas. No Fabric 1.20.1, o caminho seguro sem depender de
 * assinaturas instáveis da API de mensagem é o ChatComponentMixin, que já
 * intercepta ChatComponent#addMessage e traduz o componente antes do render.
 */
public final class ChatSyncHandler {
    private ChatSyncHandler() {}
    public static void register() {}
}
