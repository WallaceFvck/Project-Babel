package com.projectbabel.mixin.compat.refinedstorage;

import com.projectbabel.integrations.generic.ModIntegrationTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.BaseScreen", remap = false)
public abstract class RefinedStorageBaseScreenMixin {

    @ModifyVariable(
        method = "renderString(Lnet/minecraft/client/gui/GuiGraphics;IILjava/lang/String;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 4,
        require = 0,
        remap = false
    )
    private String projectbabel$renderString(String text) {
        return ModIntegrationTranslator.translateString(text, "refinedstorage");
    }

    @ModifyVariable(
        method = "renderString(Lnet/minecraft/client/gui/GuiGraphics;IILjava/lang/String;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 4,
        require = 0,
        remap = false
    )
    private String projectbabel$renderColoredString(String text) {
        return ModIntegrationTranslator.translateString(text, "refinedstorage");
    }

    @ModifyVariable(
        method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;IILjava/lang/String;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 4,
        require = 0,
        remap = false
    )
    private String projectbabel$renderStringTooltip(String text) {
        return ModIntegrationTranslator.translateString(text, "refinedstorage");
    }

    @ModifyVariable(
        method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 5,
        require = 0,
        remap = false
    )
    private String projectbabel$renderItemStringTooltip(String text) {
        return ModIntegrationTranslator.translateString(text, "refinedstorage");
    }

    @ModifyVariable(
        method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/util/List;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 5,
        require = 0,
        remap = false
    )
    private List<Component> projectbabel$renderItemListTooltip(List<Component> tooltip) {
        return ModIntegrationTranslator.translateComponentList(tooltip, "refinedstorage");
    }
}
