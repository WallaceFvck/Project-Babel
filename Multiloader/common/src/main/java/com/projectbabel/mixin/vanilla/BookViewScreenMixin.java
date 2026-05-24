package com.projectbabel.mixin.vanilla;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.pipeline.TranslationPipeline;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepta a tela de livro escrito vanilla (BookViewScreen).
 *
 * Traduz o FormattedText completo do parágrafo ANTES de o Minecraft
 * fragmentá-lo em linhas. Isso garante que a quebra de linha seja
 * recalculada corretamente para o texto traduzido.
 *
 * Sem RenderingGuard aqui: o isAlreadyTranslated() do cache evita que
 * FontMixin retraduz o resultado desta camada.
 */
@Mixin(value = BookViewScreen.class, priority = 1000)
public abstract class BookViewScreenMixin {

    @ModifyVariable(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At("STORE"),
        ordinal = 0,
        require = 0
    )
    private FormattedText projectbabel$translateBookText(FormattedText original) {
        if (original == null) return null;
        if (!ProjectBabelCommon.config().isEnabled()) return original;
        if (!LanguageDetector.shouldModBeActive()) return original;

        String text = original.getString();
        String translated = TranslationPipeline.translateString(text);
        if (translated.equals(text)) return original;

        return FormattedText.of(translated);
    }
}
