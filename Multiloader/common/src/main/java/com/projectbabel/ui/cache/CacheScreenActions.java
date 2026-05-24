package com.projectbabel.ui.cache;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.integrations.books.guideme.GuideMePreloader;
import com.projectbabel.integrations.books.modonomicon.ModonomiconBookPreloader;
import com.projectbabel.integrations.books.patchouli.PatchouliBookPreloader;
import com.projectbabel.integrations.ftbquests.FTBQuestChapterPreloader;
import com.projectbabel.integrations.ftbquests.FTBQuestFirstOpenTracker;
import com.projectbabel.integrations.ftbquests.FTBQuestSidebarPreloader;

/** Commands triggered by cache-screen buttons. */
public final class CacheScreenActions {
    private CacheScreenActions() {}

    public static void saveUniversalLocalPath(String path) {
        ProjectBabelCommon.config().setUniversalTermsLocalPath(path == null ? "" : path);
        ProjectBabelCommon.config().setUniversalTermsRemote(false);
        UniversalTermsDictionary.getInstance().reloadAsync();
    }

    public static void saveLanguageConfig(String sourceLang, String targetLang) {
        ProjectBabelCommon.config().setSourceLang(sourceLang);
        ProjectBabelCommon.config().setTargetLang(targetLang);
        restartTranslationState();
        TranslationManager.getInstance().resetEngineFallback();
        UniversalTermsDictionary.getInstance().reloadAsync();
    }

    public static void toggleTargetModeAndRestartPreloads() {
        ProjectBabelCommon.config().setFollowClientLanguage(!ProjectBabelCommon.config().isFollowClientLanguage());
        restartTranslationState();
    }

    public static void clearCacheAndRestartPreloads() {
        TranslationManager.getInstance().getCache().clear();
        TranslationPipeline.clearContextCache();
        TranslationSkipRegistry.clear();
        resetFeaturePreloads();
        UniversalTermsDictionary.getInstance().reloadAsync();
    }

    public static void restartTranslationState() {
        TranslationPipeline.clearContextCache();
        resetFeaturePreloads();
    }

    private static void resetFeaturePreloads() {
        FTBQuestFirstOpenTracker.reset();
        FTBQuestChapterPreloader.reset();
        FTBQuestSidebarPreloader.reset();
        FTBQuestSidebarPreloader.requestWorldPreload();
        PatchouliBookPreloader.resetPreloadState();
        ModonomiconBookPreloader.resetPreloadState();
        GuideMePreloader.reset();
        GuideMePreloader.requestWorldPreload();
    }
}
