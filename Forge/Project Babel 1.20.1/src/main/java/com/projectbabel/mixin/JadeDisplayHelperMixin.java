package com.projectbabel.mixin;

import com.projectbabel.translation.ModIntegrationTranslator;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "snownee.jade.overlay.DisplayHelper", remap = false)
public abstract class JadeDisplayHelperMixin {

    @ModifyVariable(
        method = "drawText(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;FFI)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0,
        remap = false
    )
    private String projectbabel$drawString(String text) {
        return ModIntegrationTranslator.translateString(text, "jade", "waila", "wthit", "theoneprobe");
    }

    @ModifyVariable(
        method = "drawText(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/network/chat/FormattedText;FFI)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0,
        remap = false
    )
    private FormattedText projectbabel$drawFormattedText(FormattedText text) {
        return ModIntegrationTranslator.translateFormattedText(text, "jade", "waila", "wthit", "theoneprobe");
    }
}
