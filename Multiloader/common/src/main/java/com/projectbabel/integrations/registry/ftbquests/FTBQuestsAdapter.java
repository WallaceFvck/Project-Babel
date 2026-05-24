package com.projectbabel.integrations.registry.ftbquests;

import com.projectbabel.integrations.registry.IntegrationContext;
import com.projectbabel.integrations.registry.ModIntegrationAdapter;
import com.projectbabel.integrations.registry.PreloadMode;
import com.projectbabel.integrations.registry.UiRefreshService;
import com.projectbabel.integrations.ftbquests.FTBQuestChapterPreloader;
import com.projectbabel.integrations.ftbquests.FTBQuestFirstOpenTracker;
import com.projectbabel.integrations.ftbquests.FTBQuestSidebarPreloader;

import java.util.List;

/** Adapter that groups all FTB Quests runtime/preload hooks. */
public final class FTBQuestsAdapter implements ModIntegrationAdapter {

    @Override
    public String id() {
        return "ftbquests";
    }

    @Override
    public String displayName() {
        return "FTB Quests";
    }

    @Override
    public List<String> modIds() {
        return List.of("ftbquests");
    }

    @Override
    public void bootstrap(IntegrationContext context) {
        if (context.shouldTranslateMods()) {
            requestWorldPreload();
        }
    }

    @Override
    public void resetRuntimeState() {
        FTBQuestFirstOpenTracker.reset();
        FTBQuestChapterPreloader.reset();
        FTBQuestSidebarPreloader.reset();
    }

    @Override
    public void requestWorldPreload() {
        FTBQuestSidebarPreloader.requestWorldPreload();
    }

    @Override
    public void preloadForScreen(Object screen, PreloadMode mode) {
        if (screen == null) return;
        if (mode == PreloadMode.BLOCKING_OPEN) {
            FTBQuestSidebarPreloader.preloadForScreenBlocking(screen);
            return;
        }
        FTBQuestSidebarPreloader.requestPreloadForScreen(screen);
        FTBQuestChapterPreloader.requestPreloadSelectedChapter(screen);
    }

    @Override
    public void refreshOpenUi(String reason) {
        UiRefreshService.requestCurrentScreenRefresh(
            displayName(),
            reason,
            "ftbquests",
            "questscreen",
            "viewquestpanel"
        );
    }
}
