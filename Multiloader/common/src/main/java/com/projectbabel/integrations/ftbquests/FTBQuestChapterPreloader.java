package com.projectbabel.integrations.ftbquests;

/** Compatibility facade. */
public final class FTBQuestChapterPreloader {
    private FTBQuestChapterPreloader() {}

    public static void requestPreload(Object chapter) {
        FTBQuestAutoTranslator.preloadChapter(chapter);
    }

    public static void requestPreloadSelectedChapter(Object screen) {
        FTBQuestAutoTranslator.onScreenOpened(screen);
    }

    public static void reset() {}
    public static boolean isPreloading() { return false; }
    public static boolean isQuestPreloading(Object quest) { return false; }
    public static boolean shouldAvoidUiBlocking(Object quest) { return false; }
}
