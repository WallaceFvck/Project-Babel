package com.projectbabel.mixin.compat.ae2;

import com.projectbabel.integrations.generic.ModIntegrationTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "appeng.client.gui.AEBaseScreen", remap = false)
public abstract class AE2BaseScreenMixin {

    @ModifyVariable(
        method = "drawTooltip(Lnet/minecraft/client/gui/GuiGraphics;IILjava/util/List;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 4,
        require = 0,
        remap = false
    )
    private List<Component> projectbabel$drawTooltip(List<Component> tooltip) {
        return ModIntegrationTranslator.translateComponentList(tooltip, "ae2", "appliedenergistics2");
    }

    @ModifyVariable(
        method = "drawTooltipWithHeader(Lnet/minecraft/client/gui/GuiGraphics;IILjava/util/List;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 4,
        require = 0,
        remap = false
    )
    private List<Component> projectbabel$drawTooltipWithHeader(List<Component> tooltip) {
        return ModIntegrationTranslator.translateComponentList(tooltip, "ae2", "appliedenergistics2");
    }

    @ModifyVariable(
        method = "setTextContent(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0,
        remap = false
    )
    private Component projectbabel$setTextContent(Component text) {
        return ModIntegrationTranslator.translateComponent(text, "ae2", "appliedenergistics2");
    }

    @Inject(
        method = "getGuiDisplayName(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void projectbabel$getGuiDisplayName(
        Component name,
        CallbackInfoReturnable<Component> cir
    ) {
        cir.setReturnValue(ModIntegrationTranslator.translateComponent(cir.getReturnValue(), "ae2", "appliedenergistics2"));
    }
}
