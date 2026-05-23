package com.projectbabel.mixin;

import com.projectbabel.translation.ModonomiconBookPreloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Translates Modonomicon markdown before CommonMark parses and lays out text.
 */
@Pseudo
@Mixin(targets = "com.klikli_dev.modonomicon.client.gui.book.markdown.BookTextRenderer", remap = false)
public abstract class ModonomiconBookTextRendererMixin {

    @ModifyVariable(
        method = "render(Ljava/lang/String;Lnet/minecraft/network/chat/Style;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private String projectbabel$translateBeforeMarkdownParse(String text) {
        return ModonomiconBookPreloader.translateForMarkdown(this, text);
    }
}
