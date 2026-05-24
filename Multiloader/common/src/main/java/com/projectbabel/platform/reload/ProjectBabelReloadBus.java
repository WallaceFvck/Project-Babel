package com.projectbabel.platform.reload;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.integrations.books.guideme.GuideMePreloader;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Platform-neutral client resource reload bus.
 * Forge and Fabric only fire their native reload events into this class; integrations stay common.
 */
public final class ProjectBabelReloadBus {
    private static final List<ResourceReloadBridge> CLIENT_RESOURCE_RELOAD_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized;

    private ProjectBabelReloadBus() {}

    public static void initCommonListeners() {
        if (initialized) return;
        initialized = true;

        registerClientResourceReload(ProjectBabelReloadBus::reloadGuideMe);
    }

    public static void registerClientResourceReload(ResourceReloadBridge listener) {
        CLIENT_RESOURCE_RELOAD_LISTENERS.add(listener);
    }

    public static void fireClientResourcesReload(ResourceManager resourceManager) {
        for (ResourceReloadBridge listener : CLIENT_RESOURCE_RELOAD_LISTENERS) {
            try {
                listener.onClientResourcesReload(resourceManager);
            } catch (Throwable t) {
                ProjectBabelCommon.LOGGER.warn("[projectbabel] Falha em listener de reload de recursos.", t);
            }
        }
    }

    private static void reloadGuideMe(ResourceManager resourceManager) {
        if (!GuideMePreloader.isGuideMeAvailable()) return;

        GuideMePreloader.reset();
        GuideMePreloader.requestWorldPreload();
        ProjectBabelCommon.LOGGER.debug("[projectbabel] GuideME preload agendado apos reload de recursos.");
    }
}
