package com.projectbabel.integrations.books.modonomicon;

import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;


public final class ModonomiconReloadHelper {


    private ModonomiconReloadHelper() {}

    public static void requestPrerender(Object book, String reason) {
        if (book == null || !isModonomiconLoaded()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            boolean prerendered = ModonomiconAccess.prerenderBook(book);
            boolean screenRefreshed = ModonomiconAccess.refreshCurrentScreen(mc);
            if (prerendered || screenRefreshed) {
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] Modonomicon refresh solicitado{}{}{}.",
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            }
        });
    }


    private static boolean isModonomiconLoaded() {
        try {
            return ProjectBabelCommon.platform().mods().isLoaded("modonomicon");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
