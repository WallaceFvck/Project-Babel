package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen-level warm-up only. Actual text replacement happens in the quest getters. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen", remap = false)
public abstract class FTBQuestScreenMixin {

    @Inject(method = "init", at = @At("TAIL"), require = 0, remap = false)
    private void projectbabel$preloadOnInit(CallbackInfo ci) {
        FTBQuestAutoTranslator.onScreenOpened(this);
    }

    @Inject(method = "addWidgets", at = @At("TAIL"), require = 0, remap = false)
    private void projectbabel$preloadOnAddWidgets(CallbackInfo ci) {
        FTBQuestAutoTranslator.onScreenOpened(this);
    }

    @Inject(method = "viewQuest(Ldev/ftb/mods/ftbquests/quest/Quest;)V", at = @At("HEAD"), require = 0, remap = false)
    private void projectbabel$preloadQuest(@Coerce Object quest, CallbackInfo ci) {
        FTBQuestAutoTranslator.onQuestViewed(quest);
    }

    @Inject(method = "selectChapter(Ldev/ftb/mods/ftbquests/quest/Chapter;)V", at = @At("HEAD"), require = 0, remap = false)
    private void projectbabel$preloadChapter(@Coerce Object chapter, CallbackInfo ci) {
        FTBQuestAutoTranslator.onChapterSelected(chapter);
    }
}
