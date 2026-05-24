package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors the reference ViewQuestPanel hook, but auto-preloads instead of adding a button. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel", remap = false)
public abstract class FTBViewQuestPanelMixin {

    @Inject(method = "setViewedQuest(Ldev/ftb/mods/ftbquests/quest/Quest;)V", at = @At("HEAD"), require = 0, remap = false)
    private void projectbabel$preloadViewedQuest(@Coerce Object quest, CallbackInfo ci) {
        FTBQuestAutoTranslator.onQuestViewed(quest);
    }


    @Inject(method = "onClosed", at = @At("HEAD"), require = 0, remap = false)
    private void projectbabel$closed(CallbackInfo ci) {
        // no state to clear per panel; cached translations stay available globally
    }
}
