package com.projectbabel.integrations.books.patchouli;

import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;


public final class PatchouliReloadHelper {


    private PatchouliReloadHelper() {}

    public static void requestReload(String reason) {
        if (!ProjectBabelCommon.platform().mods().isLoaded("patchouli")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            boolean registryReloaded = PatchouliAccess.reloadRegistries();
            boolean screenRefreshed = PatchouliAccess.refreshCurrentScreen(mc);
            if (registryReloaded || screenRefreshed) {
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] Patchouli reload solicitado{}{}{}.",
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            } else {
                ProjectBabelCommon.LOGGER.debug(
                    "[projectbabel] Patchouli reload: nenhum alvo conhecido encontrado.");
            }
        });
    }

}
