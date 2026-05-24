package com.projectbabel.integrations.registry;

import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Conservative client-thread screen refresh utility for optional integrations.
 */
public final class UiRefreshService {

    private static final String[] COMMON_REFRESH_METHODS = {
        "refresh",
        "reload",
        "rebuild",
        "rebuildWidgets",
        "rebuildDocument",
        "rebuildPage",
        "reloadPage",
        "beginDisplayPages",
        "loadPage",
        "updatePage",
        "init"
    };

    private UiRefreshService() {}

    public static void requestCurrentScreenRefresh(
        String integrationName,
        String reason,
        String... classNameNeedles
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            Screen screen = mc.screen;
            if (screen == null || !matches(screen, classNameNeedles)) return;

            boolean refreshed = invokeScreenInit(screen, mc);
            for (String methodName : COMMON_REFRESH_METHODS) {
                refreshed |= ReflectionAccess.tryInvokeNoArg(screen, methodName);
            }

            if (refreshed) {
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] {} refresh solicitado{}{}{}.",
                    integrationName,
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            }
        });
    }

    public static boolean matches(Screen screen, String... classNameNeedles) {
        if (screen == null || classNameNeedles == null || classNameNeedles.length == 0) return false;
        String name = screen.getClass().getName().toLowerCase(Locale.ROOT);
        for (String needle : classNameNeedles) {
            if (needle != null && !needle.isBlank() && name.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
}
