package com.projectbabel.mixin.compat.jade;

import com.projectbabel.integrations.generic.ModIntegrationTranslator;
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
        return ModIntegrationTranslator.translateStringCacheOnly(text, "jade", "waila", "wthit", "theoneprobe");
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
        return ModIntegrationTranslator.translateFormattedTextCacheOnly(text, "jade", "waila", "wthit", "theoneprobe");
    }
}
