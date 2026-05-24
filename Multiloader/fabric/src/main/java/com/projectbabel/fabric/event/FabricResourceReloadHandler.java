package com.projectbabel.fabric.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.platform.reload.ProjectBabelReloadBus;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FabricResourceReloadHandler {
    private static final ResourceLocation ID = new ResourceLocation(ProjectBabelCommon.MOD_ID, "client_resource_reload");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private FabricResourceReloadHandler() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ID;
                }

                @Override
                public void onResourceManagerReload(ResourceManager manager) {
                    ProjectBabelReloadBus.fireClientResourcesReload(manager);
                }
            }
        );
    }
}
