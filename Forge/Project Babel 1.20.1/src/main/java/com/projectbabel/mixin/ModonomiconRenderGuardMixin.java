package com.projectbabel.mixin;

import com.projectbabel.translation.RenderingGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modonomicon already wrapped markdown text before page render. Late Font/GFX
 * translation would change measured widths without rerunning the markdown flow.
 */
@Pseudo
@Mixin(targets = "com.klikli_dev.modonomicon.client.render.page.BookPageRenderer", remap = false)
public abstract class ModonomiconRenderGuardMixin {

    @Inject(
        method = "renderBookTextHolder(Lnet/minecraft/client/gui/GuiGraphics;Lcom/klikli_dev/modonomicon/book/BookTextHolder;Lnet/minecraft/client/gui/Font;III)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private static void projectbabel$enterRenderGuard(CallbackInfo ci) {
        RenderingGuard.enter();
    }

    @Inject(
        method = "renderBookTextHolder(Lnet/minecraft/client/gui/GuiGraphics;Lcom/klikli_dev/modonomicon/book/BookTextHolder;Lnet/minecraft/client/gui/Font;III)V",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private static void projectbabel$exitRenderGuard(CallbackInfo ci) {
        RenderingGuard.exit();
    }
}
