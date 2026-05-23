package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * GuideME lays out markdown into render objects once. If the cache was empty
 * when a page was first built, the page must be rebuilt after translations land;
 * otherwise the first opened page keeps the original text until the world is
 * reloaded. This helper performs a conservative client-thread refresh only when
 * a GuideME/AE2 guide screen is currently open.
 */
public final class GuideMeReloadHelper {

    private static final String[] REFRESH_METHODS = {
        "refresh",
        "reload",
        "rebuild",
        "rebuildWidgets",
        "rebuildDocument",
        "rebuildPage",
        "reloadPage",
        "loadPage",
        "updatePage",
        "init"
    };

    private GuideMeReloadHelper() {}

    public static void requestRefresh(String reason) {
        if (!isGuideMeLoaded()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            boolean refreshed = refreshCurrentScreen(mc);
            if (refreshed) {
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] GuideME refresh solicitado{}{}{}.",
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            }
        });
    }

    private static boolean refreshCurrentScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return false;
        if (!looksLikeGuideMeScreen(screen)) return false;

        boolean refreshed = false;
        refreshed |= invokeScreenInit(screen, mc);
        for (String methodName : REFRESH_METHODS) {
            refreshed |= invokeNoArg(screen, methodName);
        }
        return refreshed;
    }

    private static boolean looksLikeGuideMeScreen(Screen screen) {
        String name = screen.getClass().getName().toLowerCase();
        return name.contains("guideme")
            || name.contains("ae2guide")
            || name.contains("guidebook")
            || name.contains("guide_screen")
            || name.contains("guide");
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
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0) return false;
                method.setAccessible(true);
                method.invoke(target);
                return true;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isGuideMeLoaded() {
        try {
            return ModList.get().isLoaded("guideme");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
