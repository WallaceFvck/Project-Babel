package com.projectbabel.mixin.compat.ftblibrary;

import com.projectbabel.integrations.ftblibrary.FTBLibraryComponentTranslator;
import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * FTBLibrary parses its own formatted strings into Components. Translating the
 * raw string before this parser can corrupt formatting codes/substitutions, so
 * translate the parsed Component instead.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.TextComponentParser", remap = false)
public abstract class FTBLibraryTextComponentParserMixin {

    @Inject(
        method = {
            "parse(Ljava/lang/String;Ljava/util/function/Function;)Lnet/minecraft/network/chat/Component;",
            "parse(Ljava/lang/String;Ljava/util/function/Function;)Lnet/minecraft/class_2561;"
        },
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void projectbabel$translateParsedComponent(
        String text,
        Function<String, Component> substitutes,
        CallbackInfoReturnable<Component> cir
    ) {
        if (FTBQuestAutoTranslator.isParsingFtbRawText()) return;

        Component parsed = cir.getReturnValue();
        if (parsed != null) {
            cir.setReturnValue(FTBLibraryComponentTranslator.translateComponentBeforeLayout(parsed));
        }
    }
}
