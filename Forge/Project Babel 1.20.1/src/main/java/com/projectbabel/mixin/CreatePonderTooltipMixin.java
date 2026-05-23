package com.projectbabel.mixin;

import com.projectbabel.translation.ModIntegrationTranslator;
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

    @Inject(
        method = "addToTooltip(Ljava/util/List;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private static void projectbabel$translatePonderTooltip(
        List<Component> tooltip,
        ItemStack stack,
        CallbackInfo ci
    ) {
        ModIntegrationTranslator.translateComponentListInPlace(tooltip, "create", "createponder", "ponder");
    }

    @Inject(
        method = "addToTooltip(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V",
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private static void projectbabel$translateLegacyPonderTooltip(
        net.minecraftforge.event.entity.player.ItemTooltipEvent event,
        CallbackInfo ci
    ) {
        ModIntegrationTranslator.translateComponentListInPlace(
            event.getToolTip(),
            "create",
            "createponder",
            "ponder"
        );
    }
}
