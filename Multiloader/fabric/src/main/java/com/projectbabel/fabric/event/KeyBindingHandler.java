package com.projectbabel.fabric.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.ui.overlay.TranslationOverlay;
import com.projectbabel.ui.cache.TranslationCacheScreen;
import com.projectbabel.core.service.TranslationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

public class KeyBindingHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> onRenderGuiOverlay(guiGraphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onGameShuttingDown());
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (KeyBindings.KEY_OPEN_MENU.consumeClick()) {
            if (!(mc.screen instanceof TranslationCacheScreen)) {
                mc.setScreen(new TranslationCacheScreen(mc.screen));
            }
        }
    }

    private static void onRenderGuiOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (!ProjectBabelCommon.config().isShowHudIndicator()) return;
        TranslationOverlay.render(guiGraphics);
    }

    private static void onGameShuttingDown() {
        ProjectBabelCommon.LOGGER.info("[projectbabel] Jogo fechando — salvando cache...");
        TranslationManager.getInstance().shutdown();
    }
}
