package com.projectbabel.integrations.ftbquests;

/** Compatibility facade. */
public final class FTBQuestUiRefreshHelper {
    private FTBQuestUiRefreshHelper() {}

    public static void requestRefreshForCurrentFile(String reason) {
        FTBQuestAutoTranslator.requestRefresh(FTBQuestAutoTranslator.currentClientQuestFile(), reason);
    }

    public static void requestRefreshForObject(Object object, String reason) {
        FTBQuestAutoTranslator.requestRefresh(object, reason);
    }

    public static void requestRefresh(Object file, String reason) {
        FTBQuestAutoTranslator.requestRefresh(file, reason);
    }

    public static void refreshNow(Object file) {
        FTBQuestAutoTranslator.refreshNow(file);
    }
}
