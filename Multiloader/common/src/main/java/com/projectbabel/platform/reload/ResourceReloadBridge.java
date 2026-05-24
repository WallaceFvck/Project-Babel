package com.projectbabel.platform.reload;

import net.minecraft.server.packs.resources.ResourceManager;

@FunctionalInterface
public interface ResourceReloadBridge {
    void onClientResourcesReload(ResourceManager resourceManager);
}
