package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Um único keybind abre o menu completo do projectbabel.
 * Todas as opções ficam dentro da tela TranslationCacheScreen.
 */
@Mod.EventBusSubscriber(modid = ProjectBabelCommon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KeyBindings {

    private static final String CATEGORY = "key.categories.projectbabel";

    private KeyBindings() {}

    /** H — abre o menu do projectbabel (único keybind) */
    public static final KeyMapping KEY_OPEN_MENU = new KeyMapping(
        "key.projectbabel.open_menu",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_OPEN_MENU);
        ProjectBabelCommon.LOGGER.info("[projectbabel] Keybind registrado: H → Abrir Menu.");
    }
}
