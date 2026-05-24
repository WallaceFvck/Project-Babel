package com.projectbabel.fabric;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.cache.CoreCacheInvalidationHooks;
import com.projectbabel.integrations.books.guideme.GuideMeCacheInvalidationHooks;
import com.projectbabel.integrations.books.modonomicon.ModonomiconCacheInvalidationHooks;
import com.projectbabel.integrations.books.patchouli.PatchouliCacheInvalidationHooks;
import com.projectbabel.integrations.ftbquests.FTBQuestCacheInvalidationHooks;
import com.projectbabel.fabric.event.ChatSyncHandler;
import com.projectbabel.fabric.event.ClientWorldJoinHandler;
import com.projectbabel.fabric.event.DynamicTooltipTranslationHandler;
import com.projectbabel.fabric.event.KeyBindingHandler;
import com.projectbabel.fabric.event.KeyBindings;
import com.projectbabel.fabric.event.MixinHealthCheck;
import com.projectbabel.fabric.event.ScreenWidgetTranslationHandler;
import com.projectbabel.fabric.integrations.ftbquests.FTBQuestFabricLifecycle;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.fabric.event.FabricResourceReloadHandler;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import net.fabricmc.api.ClientModInitializer;

public class ProjectBabelFabric implements ClientModInitializer {


    @Override
    public void onInitializeClient() {
        FabricConfigBridge.load();
        ProjectBabelCommon.init(new FabricPlatformServices());

        CoreCacheInvalidationHooks.register();
        FTBQuestCacheInvalidationHooks.register();
        PatchouliCacheInvalidationHooks.register();
        ModonomiconCacheInvalidationHooks.register();
        GuideMeCacheInvalidationHooks.register();

        KeyBindings.register();
        KeyBindingHandler.register();
        DynamicTooltipTranslationHandler.register();
        ChatSyncHandler.register();
        FabricResourceReloadHandler.register();
        ClientWorldJoinHandler.register();
        FTBQuestFabricLifecycle.register();
        ScreenWidgetTranslationHandler.register();
        MixinHealthCheck.register();

        TranslationManager.getInstance();
        UniversalTermsDictionary.getInstance().ensureLoadedAsync();
        ProjectBabelCommon.LOGGER.info("[projectbabel] Fabric pronto. Engines=Google/Lingva {}->{}",
            ProjectBabelCommon.config().getSourceLang(),
            LanguageDetector.getTargetLanguageForApi());
    }
}
