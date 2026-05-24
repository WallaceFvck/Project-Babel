package com.projectbabel.mixin.compat.guideme;

import com.projectbabel.core.guard.RenderingGuard;
import com.projectbabel.core.guard.RenderGuardReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once GuideME has laid out a page, late generic Font/GuiGraphics translation
 * can change rendered widths without recomputing the layout. Guard document
 * rendering so only the pre-layout translation path touches ae2guide text.
 */
@Pseudo
@Mixin(targets = "guideme.document.block.LytDocument", remap = false)
public abstract class GuideMeRenderGuardMixin {

    @Inject(
        method = "render(Lguideme/render/RenderContext;)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$enterRenderGuard(CallbackInfo ci) {
        RenderingGuard.enter(RenderGuardReason.PRELAID_OUT_CONTENT);
    }

    @Inject(
        method = "render(Lguideme/render/RenderContext;)V",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void projectbabel$exitRenderGuard(CallbackInfo ci) {
        RenderingGuard.exit();
    }

    @Inject(
        method = "renderBatch(Lguideme/render/RenderContext;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void projectbabel$enterRenderBatchGuard(CallbackInfo ci) {
        RenderingGuard.enter(RenderGuardReason.PRELAID_OUT_CONTENT);
    }

    @Inject(
        method = "renderBatch(Lguideme/render/RenderContext;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void projectbabel$exitRenderBatchGuard(CallbackInfo ci) {
        RenderingGuard.exit();
    }
}
