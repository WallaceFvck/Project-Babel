package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class ModonomiconReloadHelper {

    private static final String RENDERER_CLASS =
        "com.klikli_dev.modonomicon.client.gui.book.markdown.BookTextRenderer";

    private ModonomiconReloadHelper() {}

    public static void requestPrerender(Object book, String reason) {
        if (book == null || !isModonomiconLoaded()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            boolean prerendered = prerenderBook(book);
            boolean screenRefreshed = refreshCurrentScreen(mc);
            if (prerendered || screenRefreshed) {
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Modonomicon refresh solicitado{}{}{}.",
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            }
        });
    }

    private static boolean prerenderBook(Object book) {
        try {
            Class<?> rendererClass = Class.forName(RENDERER_CLASS);
            Object renderer = newRenderer(rendererClass, book);
            if (renderer == null) return false;

            Method prerender = book.getClass().getMethod("prerenderMarkdown", rendererClass);
            prerender.setAccessible(true);
            prerender.invoke(book, renderer);
            return true;
        } catch (Throwable e) {
            ProjectBabelMod.LOGGER.debug(
                "[projectbabel] Modonomicon prerender falhou: {}",
                e.getMessage()
            );
            return false;
        }
    }

    private static Object newRenderer(Class<?> rendererClass, Object book) {
        for (Constructor<?> constructor : rendererClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 1 || !parameters[0].isAssignableFrom(book.getClass())) continue;

            try {
                constructor.setAccessible(true);
                return constructor.newInstance(book);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean refreshCurrentScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return false;
        if (!screen.getClass().getName().toLowerCase().contains("modonomicon")) return false;

        if (invokeScreenInit(screen, mc)) return true;
        if (invokeNoArg(screen, "beginDisplayPages")) return true;
        if (invokeNoArg(screen, "refresh")) return true;
        return invokeNoArg(screen, "init");
    }

    private static boolean invokeScreenInit(Screen screen, Minecraft mc) {
        try {
            Method method = Screen.class.getDeclaredMethod(
                "init", Minecraft.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(
                screen,
                mc,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0) return false;
            method.setAccessible(true);
            method.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isModonomiconLoaded() {
        try {
            return ModList.get().isLoaded("modonomicon");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
