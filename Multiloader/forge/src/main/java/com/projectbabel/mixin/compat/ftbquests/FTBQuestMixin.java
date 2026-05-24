package com.projectbabel.mixin.compat.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestFirstOpenTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.Quest", remap = false)
public abstract class FTBQuestMixin {

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void babel$translateDescription(CallbackInfoReturnable<List<Component>> cir) {
        List<Component> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<Component> translated = null;
        for (int i = 0; i < original.size(); i++) {
            Component component = original.get(i);
            Component result = translatePreservingStyle(component);
            if (result != component) {
                if (translated == null) translated = new ArrayList<>(original);
                translated.set(i, result);
            }
        }

        if (translated != null) cir.setReturnValue(translated);
    }

    @Inject(method = "getRawDescription", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void babel$translateRawDescription(CallbackInfoReturnable<List<String>> cir) {
        List<String> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<String> translated = null;
        for (int i = 0; i < original.size(); i++) {
            String line = original.get(i);
            String result = FTBQuestFirstOpenTracker.translate(this, line);
            if (result != line && result != null && !result.equals(line)) {
                if (translated == null) translated = new ArrayList<>(original);
                translated.set(i, result);
            }
        }

        if (translated != null) cir.setReturnValue(translated);
    }

    @Inject(method = "getSubtitle", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void babel$translateSubtitle(CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) {
            Component original = cir.getReturnValue();
            Component translated = translatePreservingStyle(original);
            if (translated != original) cir.setReturnValue(translated);
        }
    }

    @Inject(method = "getRawSubtitle", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void babel$translateRawSubtitle(CallbackInfoReturnable<String> cir) {
        String original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        String translated = FTBQuestFirstOpenTracker.translate(this, original);
        if (translated != original && translated != null && !translated.equals(original)) {
            cir.setReturnValue(translated);
        }
    }

    private Component translatePreservingStyle(Component component) {
        if (component == null) return null;
        return FTBQuestFirstOpenTracker.translate(this, component);
    }
}
