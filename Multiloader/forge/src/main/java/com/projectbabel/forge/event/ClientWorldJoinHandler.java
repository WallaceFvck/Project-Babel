package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.debug.ProjectBabelDebug;
import com.projectbabel.integrations.registry.IntegrationRegistry;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectBabelCommon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        IntegrationRegistry.getInstance().resetRuntimeStateAll();
        IntegrationRegistry.getInstance().requestWorldPreloadAll();
    }
}
