package com.projectbabel.mixin;

import com.projectbabel.translation.GuideMePreloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * GuideME compiles ae2guide markdown into LytFlowText before computing line
 * wraps. Translating here keeps translated glyph widths in the layout pass,
 * avoiding the same overlap/wrap problem that Patchouli had.
 */
@Pseudo
@Mixin(targets = "guideme.document.flow.LytFlowText", remap = false)
public abstract class GuideMeFlowTextMixin {

    @ModifyVariable(
        method = "setText(Ljava/lang/String;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private String projectbabel$translateBeforeGuideMeLayout(String text) {
        return GuideMePreloader.translateForLayout(text);
    }
}
