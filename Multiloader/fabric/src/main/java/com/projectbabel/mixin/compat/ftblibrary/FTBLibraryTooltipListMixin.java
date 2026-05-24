package com.projectbabel.mixin.compat.ftblibrary;

import com.projectbabel.integrations.ftblibrary.FTBLibraryComponentTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.TooltipList", remap = false)
public abstract class FTBLibraryTooltipListMixin {

    @ModifyVariable(
        method = {
            "add(Lnet/minecraft/network/chat/Component;)V",
            "add(Lnet/minecraft/class_2561;)V"
        },
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0,
        remap = false
    )
    private Component projectbabel$translateTooltipLine(Component component) {
        return FTBLibraryComponentTranslator.translateComponentBeforeLayout(component);
    }
}
