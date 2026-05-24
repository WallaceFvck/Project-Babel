package com.projectbabel.fabric.event;

import com.projectbabel.ProjectBabelCommon;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import com.projectbabel.ui.cache.TranslationCacheScreen;
/**
 * Um único keybind abre o menu completo do projectbabel.
 * Todas as opções ficam dentro da tela TranslationCacheScreen.
 */
public class KeyBindings {

    private static final String CATEGORY = "key.categories.projectbabel";

    /** H — abre o menu do projectbabel (único keybind) */
    public static final KeyMapping KEY_OPEN_MENU = new KeyMapping(
        "key.projectbabel.open_menu",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        KeyBindingHelper.registerKeyBinding(KEY_OPEN_MENU);
        ProjectBabelCommon.LOGGER.info("[projectbabel] Keybind Fabric registrado: H → Abrir Menu.");
    }
}
