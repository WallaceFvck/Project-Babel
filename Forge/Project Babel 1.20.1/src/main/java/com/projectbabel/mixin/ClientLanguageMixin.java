package com.projectbabel.mixin;

import com.projectbabel.translation.TranslationTriageManager;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientLanguage.class, priority = 1200)
public abstract class ClientLanguageMixin {

    @Inject(
        method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void projectbabel$getOrDefault(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String resolved = cir.getReturnValue();
        String translated = TranslationTriageManager.getInstance().triageLanguageLookup(key, resolved);
        if (translated != null && !translated.equals(resolved)) {
            cir.setReturnValue(translated);
        }
    }
}
