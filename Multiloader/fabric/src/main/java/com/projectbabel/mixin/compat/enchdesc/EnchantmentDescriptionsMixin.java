package com.projectbabel.mixin.compat.enchdesc;

import com.projectbabel.debug.EnchantmentTranslationDebug;
import com.projectbabel.minecraft.tooltip.EnchantmentTooltipTranslator;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compat opcional para o mod Enchantment Descriptions.
 *
 * A correção principal é feita no TooltipMixin, depois da tooltip inteira estar
 * montada. Este hook existe só para aquecer/cachear descrições quando o provider
 * é chamado fora de uma tooltip comum, sem interferir na ordem das linhas.
 */
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

        Component translated = EnchantmentTooltipTranslator.translateEnchantmentProviderOutput(original);
        EnchantmentTranslationDebug.enchantmentDescription(enchantment, original, translated);
        if (translated == null || translated == original || translated.getString().equals(original.getString())) return;

        TranslationSkipRegistry.skip(translated);
        if (translated instanceof MutableComponent mutable) {
            cir.setReturnValue(mutable);
        } else {
            cir.setReturnValue(Component.empty().append(translated));
        }
    }
}
