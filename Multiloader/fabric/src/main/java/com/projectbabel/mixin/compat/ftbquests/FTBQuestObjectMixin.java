package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same injection point used by the reference mod: QuestObjectBase#getTitle HEAD.
 * Titles are replaced only when a translated raw string is available; otherwise
 * FTB Quests continues building its original cached component normally.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.QuestObjectBase", remap = false)
public abstract class FTBQuestObjectMixin {

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void projectbabel$translateFtbTitle(CallbackInfoReturnable<Component> cir) {
        Component translated = FTBQuestAutoTranslator.translatedTitle(this);
        if (translated != null) cir.setReturnValue(translated);
    }
}
