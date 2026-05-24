package com.projectbabel.integrations.books.guideme;

import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;

/**
 * GuideME lays out markdown into render objects once. If the cache was empty
 * when a page was first built, the page must be rebuilt after translations land;
 * otherwise the first opened page keeps the original text until the world is
 * reloaded. This helper performs a conservative client-thread refresh only when
 * a GuideME/AE2 guide screen is currently open.
 */
public final class GuideMeReloadHelper {


    private GuideMeReloadHelper() {}

    public static void requestRefresh(String reason) {
        if (!isGuideMeLoaded()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            boolean refreshed = refreshCurrentScreen(mc);
            if (refreshed) {
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] GuideME refresh solicitado{}{}{}.",
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            }
        });
    }

    private static boolean refreshCurrentScreen(Minecraft mc) {
        return GuideMeAccess.refreshCurrentScreen(mc);
    }


    private static boolean isGuideMeLoaded() {
        return GuideMeAccess.isAvailable();
    }
}
