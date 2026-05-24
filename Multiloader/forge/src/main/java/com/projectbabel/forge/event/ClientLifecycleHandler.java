package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.ui.overlay.TranslationOverlay;
import com.projectbabel.core.service.TranslationManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos client-side de ciclo de vida e HUD que não pertencem ao handler de keybind.
 */
@Mod.EventBusSubscriber(modid = ProjectBabelCommon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientLifecycleHandler {

    private ClientLifecycleHandler() {}

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!ProjectBabelCommon.config().isShowHudIndicator()) {
            return;
        }
        TranslationOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        ProjectBabelCommon.LOGGER.info("[projectbabel] Jogo fechando — salvando cache...");
        TranslationManager.getInstance().shutdown();
    }
}
