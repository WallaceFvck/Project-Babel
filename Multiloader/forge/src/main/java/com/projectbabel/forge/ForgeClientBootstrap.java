package com.projectbabel.forge;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.integrations.registry.IntegrationRegistry;
import com.projectbabel.core.cache.CoreCacheInvalidationHooks;
import com.projectbabel.integrations.books.guideme.GuideMeCacheInvalidationHooks;
import com.projectbabel.integrations.books.modonomicon.ModonomiconCacheInvalidationHooks;
import com.projectbabel.integrations.books.patchouli.PatchouliCacheInvalidationHooks;
import com.projectbabel.integrations.ftbquests.FTBQuestCacheInvalidationHooks;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centraliza inicialização client-side do Project Babel.
 *
 * Mantém o construtor do @Mod pequeno e evita espalhar bootstrap de sistemas
 * globais em handlers de eventos Forge. Este método deve ser chamado durante
 * FMLClientSetupEvent#enqueueWork.
 */
public final class ForgeClientBootstrap {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private ForgeClientBootstrap() {}

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        CoreCacheInvalidationHooks.register();
        FTBQuestCacheInvalidationHooks.register();
        PatchouliCacheInvalidationHooks.register();
        ModonomiconCacheInvalidationHooks.register();
        GuideMeCacheInvalidationHooks.register();

        TranslationManager.getInstance();
        IntegrationRegistry.bootstrapClient();
        UniversalTermsDictionary.getInstance().ensureLoadedAsync();

        ProjectBabelCommon.LOGGER.info("[projectbabel] Pronto. Engines=Google/Lingva {}->{}",
            ProjectBabelCommon.config().getSourceLang(),
            LanguageDetector.getTargetLanguageForApi());
    }
}
