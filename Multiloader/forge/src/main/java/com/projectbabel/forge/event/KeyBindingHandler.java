package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.ui.cache.TranslationCacheScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Consumo de keybinds client-side.
 *
 * Forge recomenda consumir KeyMapping#consumeClick durante ClientTickEvent,
 * em vez de depender de InputEvent.Key, pois o clique pode vir de teclado,
 * mouse ou outra fonte de input registrada pelo próprio KeyMapping.
 */
@Mod.EventBusSubscriber(modid = ProjectBabelCommon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KeyBindingHandler {

    private KeyBindingHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            drainOpenMenuClicks();
            return;
        }

        while (KeyBindings.KEY_OPEN_MENU.consumeClick()) {
            if (!(mc.screen instanceof TranslationCacheScreen)) {
                mc.setScreen(new TranslationCacheScreen(mc.screen));
            }
        }
    }

    private static void drainOpenMenuClicks() {
        while (KeyBindings.KEY_OPEN_MENU.consumeClick()) {
            // Descarta cliques acumulados antes do mundo/player estar pronto.
        }
    }
}
