package com.projectbabel.mixin.compat.ftblibrary;

import com.projectbabel.core.pipeline.TranslationPipeline;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.TooltipList", remap = false)
public abstract class FTBLibraryTooltipListMixin {

    @ModifyVariable(
        method = "add(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private Component projectbabel$addTooltip(Component component) {
        return TranslationPipeline.translateComponentTree(component);
    }
}
