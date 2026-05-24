package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Uses the reference mod's stable hooks: Quest#getSubtitle and Quest#getDescription at HEAD. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.Quest", remap = false)
public abstract class FTBQuestMixin {

    @Inject(method = "getSubtitle", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void projectbabel$translateFtbSubtitle(CallbackInfoReturnable<Component> cir) {
        Component translated = FTBQuestAutoTranslator.translatedSubtitle(this);
        if (translated != null) cir.setReturnValue(translated);
    }

    @Inject(method = "getDescription", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void projectbabel$translateFtbDescription(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> translated = FTBQuestAutoTranslator.translatedDescription(this);
        if (translated != null && !translated.isEmpty()) cir.setReturnValue(translated);
    }
}
