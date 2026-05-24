package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import com.projectbabel.fabric.integrations.ftbquests.FTBQuestFabricLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.ClientQuestFile", remap = false)
public abstract class FTBClientQuestFileMixin {

    @Inject(method = "syncFromServer(Ldev/ftb/mods/ftbquests/quest/BaseQuestFile;)V", at = @At("TAIL"), require = 0, remap = false)
    private static void projectbabel$afterQuestFileSync(@Coerce Object serverFile, CallbackInfo ci) {
        FTBQuestFabricLifecycle.onClientQuestFileSynced();
    }

    @Inject(method = "refreshGui", at = @At("TAIL"), require = 0, remap = false)
    private void projectbabel$afterFtbRefreshGui(CallbackInfo ci) {
        FTBQuestAutoTranslator.preloadFile(this);
    }
}
