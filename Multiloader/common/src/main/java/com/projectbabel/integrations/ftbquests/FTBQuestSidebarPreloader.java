package com.projectbabel.integrations.ftbquests;

/** Compatibility facade. */
public final class FTBQuestSidebarPreloader {
    private FTBQuestSidebarPreloader() {}

    public static void reset() {}

    public static void requestWorldPreload() {
        FTBQuestAutoTranslator.preloadCurrentClientFile();
    }

    public static void requestPreloadForScreen(Object screen) {
        FTBQuestAutoTranslator.onScreenOpened(screen);
    }

    public static void requestPreloadForFile(Object file, boolean refreshAfter) {
        FTBQuestAutoTranslator.preloadFile(file);
    }

    public static void preloadForScreenBlocking(Object screen) {
        FTBQuestAutoTranslator.onScreenOpened(screen);
    }

    public static Object currentClientQuestFile() {
        return FTBQuestAutoTranslator.currentClientQuestFile();
    }
}
