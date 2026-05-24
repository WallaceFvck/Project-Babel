package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.pipeline.TranslationPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectBabelCommon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChatSyncHandler {

    private ChatSyncHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientChat(ClientChatReceivedEvent event) {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!ProjectBabelCommon.config().isTranslateChat()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        Component original = event.getMessage();
        if (original == null) return;

        String plain = original.getString();
        if (plain == null || !TextFilter.shouldTranslate(plain)) return;

        Component cached = TranslationPipeline.translateComponentTree(original);
        if (cached != original) {
            event.setMessage(cached);
            return;
        }

        if (!TranslationPipeline.needsExternalTranslation(original)) return;

        event.setCanceled(true);
        TranslationPipeline.translateComponentTreeBlockingAsync(original)
            .thenAccept(translated -> replay(event, translated == null ? original : translated))
            .exceptionally(error -> {
                replay(event, original);
                return null;
            });
    }

    private static void replay(ClientChatReceivedEvent event, Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            if (event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) {
                mc.gui.setOverlayMessage(message, false);
            } else {
                mc.gui.getChat().addMessage(message);
            }
        });
    }
}
