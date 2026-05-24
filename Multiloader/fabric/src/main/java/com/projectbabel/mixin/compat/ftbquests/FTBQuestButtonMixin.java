package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import com.projectbabel.integrations.ftbquests.FTBQuestAccess;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Tooltip fallback for task/reward/quest button lines that are not exposed as raw quest text. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.QuestButton", remap = false)
public abstract class FTBQuestButtonMixin {


    @Inject(method = "addMouseOverText", at = @At("TAIL"), require = 0, remap = false)
    private void projectbabel$translateMouseOverText(@Coerce Object tooltip, CallbackInfo ci) {
        List<Component> lines = getLines(tooltip);
        if (lines == null || lines.isEmpty()) return;

        for (int i = 0; i < lines.size(); i++) {
            Component original = lines.get(i);
            Component translated = FTBQuestAutoTranslator.translateComponentForFtbUi(original);
            if (translated != original) lines.set(i, translated);
        }
    }

    private static List<Component> getLines(Object tooltip) {
        return FTBQuestAccess.tooltipLines(tooltip);
    }
}
