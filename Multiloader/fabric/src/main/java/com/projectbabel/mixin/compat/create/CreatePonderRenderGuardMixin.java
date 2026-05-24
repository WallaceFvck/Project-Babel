package com.projectbabel.mixin.compat.create;

import com.projectbabel.core.guard.RenderingGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ponder measures and wraps TextWindowElement.bakedText before drawing. The
 * generic Font/GuiGraphics hooks must not translate it again after wrapping.
 */
@Pseudo
@Mixin(
    targets = {
        "com.simibubi.create.foundation.ponder.element.TextWindowElement",
        "net.createmod.ponder.foundation.element.TextWindowElement"
    },
    remap = false
)
public abstract class CreatePonderRenderGuardMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void projectbabel$enterRenderGuard(CallbackInfo ci) {
        RenderingGuard.enter();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void projectbabel$exitRenderGuard(CallbackInfo ci) {
        RenderingGuard.exit();
    }
}
