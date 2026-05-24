package com.projectbabel.mixin.compat.ae2;

import com.projectbabel.integrations.generic.ModIntegrationTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "appeng.client.gui.Tooltip", remap = false)
public abstract class AE2TooltipMixin {

    @Inject(method = "getContent", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void projectbabel$getContent(CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(ModIntegrationTranslator.translateComponentListCacheOnly(cir.getReturnValue(), "ae2", "appliedenergistics2"));
    }
}
