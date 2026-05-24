package com.projectbabel.mixin.compat.patchouli;

import com.projectbabel.integrations.books.patchouli.PatchouliBookPreloader;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Translates Patchouli text after Patchouli resolves I18n keys and before it
 * parses spans/commands and wraps lines.
 */
@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.text.BookTextParser", remap = false)
public abstract class PatchouliTextRendererMixin {

    @ModifyVariable(
        method = {
            "parse(Lnet/minecraft/network/chat/Component;)Ljava/util/List;",
            "parse(Lnet/minecraft/class_2561;)Ljava/util/List;"
        },
        at = @At("HEAD"),
        argsOnly = true,
        index = 1,
        require = 0,
        remap = false
    )
    private Component projectbabel$translateBeforeParse(Component original) {
        return PatchouliBookPreloader.translateForParse(original);
    }
}
