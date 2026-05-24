package com.projectbabel.integrations.ftbquests;

import net.minecraft.network.chat.Component;

/** Compatibility facade. The rewritten implementation is FTBQuestAutoTranslator. */
public final class FTBQuestFirstOpenTracker {
    private FTBQuestFirstOpenTracker() {}

    public static boolean preloadFirstOpen(Object quest) {
        FTBQuestAutoTranslator.preloadQuest(quest);
        return true;
    }

    public static void preloadForUiOpen(Object quest) {
        FTBQuestAutoTranslator.preloadQuest(quest);
    }

    public static Component translate(Object quest, Component component) {
        return FTBQuestAutoTranslator.translateComponentForFtbUi(component);
    }

    public static String translate(Object quest, String text) {
        return text;
    }

    public static String translateRawForFtbGetter(Object owner, String text) {
        return text;
    }

    public static void reset() {
        FTBQuestAutoTranslator.reset();
    }

    public static String keyOf(Object quest) {
        return quest == null ? null : quest.getClass().getName() + '@' + System.identityHashCode(quest);
    }
}
