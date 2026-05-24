package com.projectbabel.fabric.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.api.TranslationContext;
import com.projectbabel.ui.cache.TranslationCacheScreen;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.pipeline.TranslationPipeline;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScreenWidgetTranslationHandler {

    private static boolean registered = false;

    private ScreenWidgetTranslationHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> onScreenTick());
    }

    private static void onScreenTick() {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null || screen instanceof TranslationCacheScreen) return;

        for (GuiEventListener child : screen.children()) {
            translateWidgetTree(child);
        }
    }

    private static void translateWidgetTree(GuiEventListener listener) {
        if (listener instanceof AbstractWidget widget) {
            Component original = widget.getMessage();
            Component translated = TranslationPipeline.translateComponent(original, TranslationContext.screenWidgetTick());
            if (translated != original) widget.setMessage(translated);
        }

        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                translateWidgetTree(child);
            }
        }
    }
}
