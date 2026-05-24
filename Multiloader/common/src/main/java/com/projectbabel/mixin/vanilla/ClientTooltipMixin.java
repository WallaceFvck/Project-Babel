package com.projectbabel.mixin.vanilla;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.tooltip.TooltipTranslationController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Intercepta GuiGraphics#renderTooltip.
 *
 * Tooltips de item continuam sendo tratados em TooltipMixin. Este mixin apenas
 * pré-aquece o nome do item e cobre tooltips customizados de mods que não passam
 * por ItemStack#getTooltipLines().
 */
@Mixin(value = GuiGraphics.class, priority = 800)
public abstract class ClientTooltipMixin {

    @Inject(
        method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
        at = @At("HEAD"),
        require = 0
    )
    private void projectbabel$renderTooltipItemStack(
        net.minecraft.client.gui.Font font,
        ItemStack stack, int x, int y,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;
        TooltipTranslationController.prewarmItemName(stack);
    }

    @ModifyVariable(
        method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0
    )
    private List<Component> projectbabel$renderTooltipListOptional(List<Component> lines) {
        return TooltipTranslationController.translateCustomTooltipLines(lines);
    }

    @ModifyVariable(
        method = "renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0
    )
    private List<Component> projectbabel$renderComponentTooltip(List<Component> lines) {
        return TooltipTranslationController.translateCustomTooltipLines(lines);
    }
}
