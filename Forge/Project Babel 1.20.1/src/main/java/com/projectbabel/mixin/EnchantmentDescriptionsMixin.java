package com.projectbabel.mixin;

import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.darkhax.enchdesc.DescriptionManager", remap = false)
public abstract class EnchantmentDescriptionsMixin {

    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void projectbabel$getDescription(
        Enchantment enchantment,
        CallbackInfoReturnable<MutableComponent> cir
    ) {
        MutableComponent original = cir.getReturnValue();
        if (original == null) return;

        boolean enchantmentDescription = TranslationPipeline.isEnchantmentDescriptionComponent(original);
        Component translated = TranslationPipeline.translateEnchantmentDescription(original);
        if (translated == original && !enchantmentDescription) {
            translated = TranslationPipeline.translateComponentTree(original);
        }
        EnchantmentTranslationDebug.enchantmentDescription(enchantment, original, translated);
        if (translated == original) return;
        if (translated == null || translated.getString().equals(original.getString())) return;
        TranslationSkipRegistry.skip(translated);

        if (translated instanceof MutableComponent mutable) {
            cir.setReturnValue(mutable);
        } else {
            cir.setReturnValue(Component.literal(translated.getString()).withStyle(translated.getStyle()));
        }
    }
}
