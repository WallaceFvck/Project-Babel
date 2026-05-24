package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestFirstOpenTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.QuestObjectBase", remap = false)
public abstract class FTBQuestObjectMixin {

    @Inject(method = "getTitle", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void projectbabel$translateTitle(CallbackInfoReturnable<Component> cir) {
        Component originalComponent = cir.getReturnValue();
        if (originalComponent == null) {
            return;
        }

        try {
            Component result = FTBQuestFirstOpenTracker.translate(this, originalComponent);
            if (result != originalComponent) cir.setReturnValue(result);
        } catch (Throwable ignored) {
        }
    }
}
