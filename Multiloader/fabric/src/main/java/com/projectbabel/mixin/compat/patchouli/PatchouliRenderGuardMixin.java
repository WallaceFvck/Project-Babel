package com.projectbabel.mixin.compat.patchouli;

import com.projectbabel.core.guard.RenderingGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Patchouli lays text out once in BookTextRenderer's constructor. Translating
 * individual words later from Font/GuiGraphics changes glyph widths without
 * recalculating positions, which causes overlaps and broken line wrapping.
 */
@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.gui.BookTextRenderer", remap = false)
public abstract class PatchouliRenderGuardMixin {

    @Inject(
        method = {
            "render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            "render(Lnet/minecraft/class_332;II)V"
        },
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$enterRenderGuard(CallbackInfo ci) {
        RenderingGuard.enter();
    }

    @Inject(
        method = {
            "render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            "render(Lnet/minecraft/class_332;II)V"
        },
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void projectbabel$exitRenderGuard(CallbackInfo ci) {
        RenderingGuard.exit();
    }
}
