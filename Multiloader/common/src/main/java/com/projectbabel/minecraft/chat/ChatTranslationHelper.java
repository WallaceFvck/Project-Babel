package com.projectbabel.minecraft.chat;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.api.TranslationContext;
import net.minecraft.network.chat.Component;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
/**
 * Chat messages are different from most GUI text: once ChatComponent stores a
 * line, it is not rebuilt on the next frame. The normal render triage path only
 * warms cache on a miss, so the first received chat line would remain in the
 * history untranslated forever. This helper therefore uses cache-first and then
 * a bounded synchronous translation path for chat insertion only.
 */
public final class ChatTranslationHelper {

    private ChatTranslationHelper() {}

    public static Component translateIncoming(Component message) {
        if (message == null) return null;
        if (!ProjectBabelCommon.config().isTranslateChat()) return message;
        if (!ProjectBabelCommon.config().isEnabled()) return message;
        if (!LanguageDetector.shouldModBeActive()) return message;
        if (TranslationSkipRegistry.shouldSkipIdentity(message)) return message;

        try {
            Component cached = TranslationPipeline.translateComponent(message, TranslationContext.chat().asCacheOnly());
            if (isChanged(message, cached)) {
                TranslationSkipRegistry.skip(cached);
                return cached;
            }

            if (!TranslationPipeline.needsExternalTranslation(message)) {
                return message;
            }

            Component translated;
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                translated = TranslationPipeline.translateComponent(message, TranslationContext.chat());
            }

            if (isChanged(message, translated)) {
                TranslationSkipRegistry.skip(translated);
                return translated;
            }
        } catch (Throwable ignored) {
            return message;
        }

        return message;
    }

    private static boolean isChanged(Component original, Component translated) {
        if (translated == null || translated == original || original == null) return false;
        String before = original.getString();
        String after = translated.getString();
        return before != null && after != null && !before.equals(after);
    }
}
