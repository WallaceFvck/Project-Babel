package com.projectbabel.mixin.compat.create;

import com.projectbabel.integrations.generic.ModIntegrationTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(
    targets = {
        "com.simibubi.create.foundation.ponder.PonderTooltipHandler",
        "net.createmod.ponder.foundation.PonderTooltipHandler"
    },
    remap = false
)
public abstract class CreatePonderTooltipMixin {

    /** create-ponder-fabric 0.0.2b: addToTooltip(ItemStack, List<Component>) */
    @Inject(
        method = {
            "addToTooltip(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V",
            "addToTooltip(Lnet/minecraft/class_1799;Ljava/util/List;)V"
        },
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private static void projectbabel$translatePonderTooltipStackFirst(
        ItemStack stack,
        List<Component> tooltip,
        CallbackInfo ci
    ) {
        translate(tooltip);
    }

    /** Older/newer Create variants: addToTooltip(List<Component>, ItemStack). */
    @Inject(
        method = {
            "addToTooltip(Ljava/util/List;Lnet/minecraft/world/item/ItemStack;)V",
            "addToTooltip(Ljava/util/List;Lnet/minecraft/class_1799;)V"
        },
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private static void projectbabel$translatePonderTooltipListFirst(
        List<Component> tooltip,
        ItemStack stack,
        CallbackInfo ci
    ) {
        translate(tooltip);
    }

    private static void translate(List<Component> tooltip) {
        ModIntegrationTranslator.translateComponentListInPlaceCacheOnly(tooltip, "create", "createponder", "ponder");
    }
}
