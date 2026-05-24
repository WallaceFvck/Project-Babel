package com.projectbabel.minecraft.render;

import com.projectbabel.core.guard.RenderingGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Screens that pre-compute text layout must not be translated during Font/GuiGraphics render.
 * They get translated before their own wrapping/layout pass instead.
 */
public final class LayoutSensitiveScreenGuard {

    private LayoutSensitiveScreenGuard() {}

    public static boolean shouldSkipLateRenderTranslation() {
        if (RenderingGuard.isActive()) return true;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;
            Screen screen = mc.screen;
            if (screen == null) return false;
            return isLayoutSensitiveScreen(screen.getClass());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isLayoutSensitiveScreen(Class<?> type) {
        while (type != null) {
            String name = type.getName();
            if (name.startsWith("dev.ftb.mods.ftbquests.client.gui.quests.")) return true;
            if (name.startsWith("com.simibubi.create.foundation.ponder.ui.")) return true;
            if (name.startsWith("net.createmod.ponder.foundation.ui.")) return true;
            if (name.startsWith("vazkii.patchouli.client.book.gui.")) return true;
            if (name.startsWith("appeng.client.guidebook.screen.")) return true;
            if (name.startsWith("guideme.")) return true;
            if (name.startsWith("com.klikli_dev.modonomicon.client.gui.book.")) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    public static boolean isFtbQuestScreenOpen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.screen == null) return false;
            Class<?> type = mc.screen.getClass();
            while (type != null) {
                if (type.getName().startsWith("dev.ftb.mods.ftbquests.client.gui.quests.")) return true;
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
