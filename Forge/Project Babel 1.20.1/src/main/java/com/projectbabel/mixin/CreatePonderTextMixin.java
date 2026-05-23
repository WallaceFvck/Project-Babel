package com.projectbabel.mixin;

import com.projectbabel.translation.CreatePonderTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Pseudo
@Mixin(
    targets = {
        "com.simibubi.create.foundation.ponder.element.TextWindowElement",
        "net.createmod.ponder.foundation.element.TextWindowElement"
    },
    remap = false
)
public abstract class CreatePonderTextMixin {

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"),
        require = 0,
        remap = false
    )
    private Object projectbabel$translatePonderText(Supplier<?> supplier) {
        Object value = supplier.get();
        if (value instanceof String text) {
            return CreatePonderTranslator.translateBakedText(text);
        }
        return value;
    }
}
