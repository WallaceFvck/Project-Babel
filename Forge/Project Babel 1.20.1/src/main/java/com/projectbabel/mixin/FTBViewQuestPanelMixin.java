package com.projectbabel.mixin;

import com.projectbabel.translation.FTBQuestFirstOpenTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel", remap = false)
public abstract class FTBViewQuestPanelMixin {

    @Inject(
        method = "setViewedQuest(Ldev/ftb/mods/ftbquests/quest/Quest;)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$preloadViewedQuest(@Coerce Object quest, CallbackInfo ci) {
        FTBQuestFirstOpenTracker.preloadForUiOpen(quest);
    }
}
