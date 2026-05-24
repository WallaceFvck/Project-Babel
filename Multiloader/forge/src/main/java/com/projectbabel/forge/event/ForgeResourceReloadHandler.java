package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.platform.reload.ProjectBabelReloadBus;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = ProjectBabelCommon.MOD_ID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ForgeResourceReloadHandler {
    private ForgeResourceReloadHandler() {}

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ProjectBabelReloadBus::fireClientResourcesReload);
    }
}
