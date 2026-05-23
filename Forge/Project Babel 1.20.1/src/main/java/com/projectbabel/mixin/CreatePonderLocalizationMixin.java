package com.projectbabel.mixin;

import com.projectbabel.translation.CreatePonderTranslator;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create/Ponder stores English scene text in PonderLocalization while runtime
 * lookups go through I18n keys. If the current client language lacks those
 * keys, I18n returns the key itself; use the registered English fallback and
 * translate it before Ponder bakes scene layout.
 */
@Pseudo
@Mixin(
    targets = {
        "com.simibubi.create.foundation.ponder.PonderLocalization",
        "net.createmod.ponder.foundation.PonderLocalization"
    },
    remap = false
)
public abstract class CreatePonderLocalizationMixin {

    @Inject(
        method = "getShared(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void projectbabel$translateShared(
        ResourceLocation id,
        CallbackInfoReturnable<String> cir
    ) {
        cir.setReturnValue(CreatePonderTranslator.translateShared(id, cir.getReturnValue()));
    }

    @Inject(
        method = "getTag(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void projectbabel$translateTag(
        ResourceLocation id,
        CallbackInfoReturnable<String> cir
    ) {
        cir.setReturnValue(CreatePonderTranslator.translateTag(id, cir.getReturnValue()));
    }

    @Inject(
        method = "getTagDescription(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void projectbabel$translateTagDescription(
        ResourceLocation id,
        CallbackInfoReturnable<String> cir
    ) {
        cir.setReturnValue(CreatePonderTranslator.translateTagDescription(id, cir.getReturnValue()));
    }

    @Inject(
        method = "getChapter(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void projectbabel$translateChapter(
        ResourceLocation id,
        CallbackInfoReturnable<String> cir
    ) {
        cir.setReturnValue(CreatePonderTranslator.translateChapter(id, cir.getReturnValue()));
    }

    @Inject(
        method = "getSpecific(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void projectbabel$translateSpecific(
        ResourceLocation id,
        String key,
        CallbackInfoReturnable<String> cir
    ) {
        cir.setReturnValue(CreatePonderTranslator.translateSpecific(id, key, cir.getReturnValue()));
    }
}
