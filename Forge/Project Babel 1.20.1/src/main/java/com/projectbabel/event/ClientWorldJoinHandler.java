package com.projectbabel.event;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.debug.ProjectBabelDebug;
import com.projectbabel.translation.FTBQuestChapterPreloader;
import com.projectbabel.translation.FTBQuestFirstOpenTracker;
import com.projectbabel.translation.FTBQuestSidebarPreloader;
import com.projectbabel.translation.GuideMePreloader;
import com.projectbabel.translation.ModonomiconBookPreloader;
import com.projectbabel.translation.PatchouliBookPreloader;
import com.projectbabel.translation.TranslationManager;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import com.projectbabel.translation.UniversalTermsDictionary;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectBabelMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWorldJoinHandler {

    private ClientWorldJoinHandler() {}

    @SubscribeEvent
    public static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        TranslationManager.getInstance().resetRuntimeStateForWorldJoin();
        TranslationSkipRegistry.clear();
        TranslationPipeline.clearContextCache();
        ProjectBabelDebug.resetSessionCounters();
        EnchantmentTranslationDebug.onWorldJoin();
        UniversalTermsDictionary.getInstance().ensureLoadedAsync();

        if (ModList.get().isLoaded("patchouli")) {
            PatchouliBookPreloader.resetPreloadState();
        }

        if (ModList.get().isLoaded("modonomicon")) {
            ModonomiconBookPreloader.resetPreloadState();
        }

        if (ModList.get().isLoaded("ftbquests")) {
            FTBQuestFirstOpenTracker.reset();
            FTBQuestChapterPreloader.reset();
            FTBQuestSidebarPreloader.reset();
            FTBQuestSidebarPreloader.requestWorldPreload();
        }

        if (ModList.get().isLoaded("guideme")) {
            GuideMePreloader.reset();
            GuideMePreloader.requestWorldPreload();
        }
    }
}
