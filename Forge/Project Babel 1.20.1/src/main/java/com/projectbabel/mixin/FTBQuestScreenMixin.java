package com.projectbabel.mixin;

import com.projectbabel.translation.FTBQuestChapterPreloader;
import com.projectbabel.translation.FTBQuestFirstOpenTracker;
import com.projectbabel.translation.FTBQuestSidebarPreloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen", remap = false)
public abstract class FTBQuestScreenMixin {

    @Inject(
        method = "onInit()Z",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$preloadSidebarTabs(CallbackInfoReturnable<Boolean> cir) {
        FTBQuestSidebarPreloader.preloadForScreenBlocking(this);
    }

    @Inject(
        method = "onInit()Z",
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private void projectbabel$preloadInitialChapter(CallbackInfoReturnable<Boolean> cir) {
        FTBQuestChapterPreloader.requestPreloadSelectedChapter(this);
    }

    @Inject(
        method = "viewQuest(Ldev/ftb/mods/ftbquests/quest/Quest;)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$preloadFirstOpen(@Coerce Object quest, CallbackInfo ci) {
        FTBQuestFirstOpenTracker.preloadForUiOpen(quest);
    }

    @Inject(
        method = "selectChapter(Ldev/ftb/mods/ftbquests/quest/Chapter;)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$preloadSelectedChapter(@Coerce Object chapter, CallbackInfo ci) {
        FTBQuestChapterPreloader.requestPreload(chapter);
    }
}
