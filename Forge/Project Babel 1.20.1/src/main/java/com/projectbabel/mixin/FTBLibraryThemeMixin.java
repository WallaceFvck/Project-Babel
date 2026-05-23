package com.projectbabel.mixin;

import com.projectbabel.translation.RenderingGuard;
import com.projectbabel.translation.TextFormatUtils;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cobre os caminhos de desenho da FTBLibrary.
 *
 * O FTB Teams usa a tela de config da FTBLibrary para renderizar Team Properties.
 * A maioria dos nomes de grupos/entradas passa por Theme#drawString(Object, int, int)
 * ou Theme#drawString(Object, int, int, int), enquanto a versão anterior do Project
 * Babel só interceptava a sobrecarga com Color4I. Por isso textos como
 * "Team Properties", "Basic Team Properties", "Display Name" e botões como
 * "Accept"/"Cancel" podiam escapar até relogar ou nunca cair no pipeline certo.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.Theme", remap = false)
public abstract class FTBLibraryThemeMixin {

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;IILdev/ftb/mods/ftblibrary/icon/Color4I;I)I",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0,
        remap = false
    )
    private Object projectbabel$drawStringObjectColor(Object text) {
        return translateObject(text);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;III)I",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0,
        remap = false
    )
    private Object projectbabel$drawStringObjectFlags(Object text) {
        return translateObject(text);
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)I",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        require = 0,
        remap = false
    )
    private Object projectbabel$drawStringObjectSimple(Object text) {
        return translateObject(text);
    }

    private Object translateObject(Object text) {
        if (text == null || RenderingGuard.isActive()) return text;

        try {
            if (text instanceof Component component) {
                return TranslationPipeline.translateComponentTree(component);
            }

            if (text instanceof String string) {
                String translated = TranslationPipeline.translateString(string);
                translated = TextFormatUtils.collapseExactDuplicateTranslation(string, translated);
                translated = TextFormatUtils.collapseRepeatedTranslation(translated);
                if (translated != null && !translated.equals(string)) {
                    TranslationSkipRegistry.skipText(translated);
                }
                return translated;
            }
        } catch (Exception ignored) {
        }

        return text;
    }
}
