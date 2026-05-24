package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.registry.IntegrationRegistry;
import com.projectbabel.integrations.registry.PreloadMode;
import com.projectbabel.integrations.ftbquests.FTBQuestChapterPreloader;
import com.projectbabel.integrations.ftbquests.FTBQuestFirstOpenTracker;
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
        IntegrationRegistry.getInstance().preloadForScreen("ftbquests", this, PreloadMode.BLOCKING_OPEN);
    }

    @Inject(
        method = "onInit()Z",
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private void projectbabel$preloadInitialChapter(CallbackInfoReturnable<Boolean> cir) {
        IntegrationRegistry.getInstance().preloadForScreen("ftbquests", this, PreloadMode.ASYNC);
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
