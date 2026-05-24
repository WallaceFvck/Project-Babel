package com.projectbabel.integrations.ftbquests;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Compatibility facade kept for older mixins/classes. New logic lives in FTBQuestAutoTranslator. */
public final class FTBQuestRawTextBridge {
    private FTBQuestRawTextBridge() {}

    public static Component translatedTitle(Object owner) {
        return FTBQuestAutoTranslator.translatedTitle(owner);
    }

    public static Component translatedSubtitle(Object quest) {
        return FTBQuestAutoTranslator.translatedSubtitle(quest);
    }

    public static List<Component> translatedDescription(Object quest) {
        return FTBQuestAutoTranslator.translatedDescription(quest);
    }

    public static boolean hasUsableRawTitle(Object owner) { return false; }
    public static boolean hasUsableRawSubtitle(Object quest) { return false; }
    public static boolean hasUsableRawDescription(Object quest) { return false; }
}
