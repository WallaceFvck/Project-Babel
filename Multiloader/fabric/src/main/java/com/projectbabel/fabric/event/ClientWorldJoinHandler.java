package com.projectbabel.fabric.event;

import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.debug.ProjectBabelDebug;
import com.projectbabel.integrations.ftbquests.FTBQuestChapterPreloader;
import com.projectbabel.integrations.ftbquests.FTBQuestFirstOpenTracker;
import com.projectbabel.integrations.ftbquests.FTBQuestSidebarPreloader;
import com.projectbabel.integrations.books.guideme.GuideMePreloader;
import com.projectbabel.integrations.books.modonomicon.ModonomiconBookPreloader;
import com.projectbabel.integrations.books.patchouli.PatchouliBookPreloader;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientWorldJoinHandler {

    private static boolean registered = false;

    private ClientWorldJoinHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onClientLoggedIn());
    }

    private static void onClientLoggedIn() {
        TranslationManager.getInstance().resetRuntimeStateForWorldJoin();
        TranslationSkipRegistry.clear();
        TranslationPipeline.clearContextCache();
        ProjectBabelDebug.resetSessionCounters();
        EnchantmentTranslationDebug.onWorldJoin();
        UniversalTermsDictionary.getInstance().ensureLoadedAsync();

        FabricLoader loader = FabricLoader.getInstance();

        if (loader.isModLoaded("patchouli")) {
            PatchouliBookPreloader.resetPreloadState();
        }

        if (loader.isModLoaded("modonomicon")) {
            ModonomiconBookPreloader.resetPreloadState();
        }

        if (loader.isModLoaded("ftbquests")) {
            FTBQuestFirstOpenTracker.reset();
            FTBQuestChapterPreloader.reset();
            FTBQuestSidebarPreloader.reset();
            FTBQuestSidebarPreloader.requestWorldPreload();
        }

        if (GuideMePreloader.isGuideMeAvailable()) {
            GuideMePreloader.reset();
            GuideMePreloader.requestWorldPreload();
        }
    }
}
