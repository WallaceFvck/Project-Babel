package com.projectbabel.mixin.compat.modonomicon;

import com.projectbabel.integrations.books.modonomicon.ModonomiconBookPreloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Translates Modonomicon markdown before CommonMark parses and lays out text.
 *
 * Modonomicon has both render(String) and render(String, Style). Some pages call
 * the one-arg overload directly before it delegates to the styled overload, so
 * both entry points are guarded. The preloader preserves CommonMark tokens and
 * only translates visible text fragments.
 */
@Pseudo
@Mixin(targets = "com.klikli_dev.modonomicon.client.gui.book.markdown.BookTextRenderer", remap = false)
public abstract class ModonomiconBookTextRendererMixin {

    @ModifyVariable(
        method = "render(Ljava/lang/String;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private String projectbabel$translateBeforeMarkdownParsePlain(String text) {
        return ModonomiconBookPreloader.translateForMarkdown(this, text);
    }

    @ModifyVariable(
        method = "render(Ljava/lang/String;Lnet/minecraft/network/chat/Style;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private String projectbabel$translateBeforeMarkdownParseStyled(String text) {
        return ModonomiconBookPreloader.translateForMarkdown(this, text);
    }
    @ModifyVariable(
        method = "render(Ljava/lang/String;Lnet/minecraft/class_2583;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private String projectbabel$translateBeforeMarkdownParseStyledIntermediary(String text) {
        return ModonomiconBookPreloader.translateForMarkdown(this, text);
    }

}
