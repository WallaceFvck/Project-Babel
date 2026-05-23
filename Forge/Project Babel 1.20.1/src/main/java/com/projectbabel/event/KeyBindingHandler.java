package com.projectbabel.event;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.overlay.TranslationOverlay;
import com.projectbabel.screen.TranslationCacheScreen;
import com.projectbabel.translation.TranslationManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectBabelMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KeyBindingHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (KeyBindings.KEY_OPEN_MENU.consumeClick()) {
            if (!(mc.screen instanceof TranslationCacheScreen)) {
                mc.setScreen(new TranslationCacheScreen(mc.screen));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!AutoTranslateConfig.isShowHudIndicator()) return;
        TranslationOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        ProjectBabelMod.LOGGER.info("[projectbabel] Jogo fechando — salvando cache...");
        TranslationManager.getInstance().shutdown();
    }
}
