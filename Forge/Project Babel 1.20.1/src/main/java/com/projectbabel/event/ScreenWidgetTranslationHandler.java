package com.projectbabel.event;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.screen.TranslationCacheScreen;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.TranslationPipeline;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectBabelMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScreenWidgetTranslationHandler {

    private ScreenWidgetTranslationHandler() {}

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (!AutoTranslateConfig.isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        if (event.getScreen() instanceof TranslationCacheScreen) return;

        for (GuiEventListener child : event.getScreen().children()) {
            translateWidgetTree(child);
        }
    }

    private static void translateWidgetTree(GuiEventListener listener) {
        if (listener instanceof AbstractWidget widget) {
            Component original = widget.getMessage();
            Component translated = TranslationPipeline.translateComponentTree(original);
            if (translated != original) widget.setMessage(translated);
        }

        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                translateWidgetTree(child);
            }
        }
    }
}
