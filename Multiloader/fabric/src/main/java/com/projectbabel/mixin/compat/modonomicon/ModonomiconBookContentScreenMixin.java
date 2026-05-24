package com.projectbabel.mixin.compat.modonomicon;

import com.projectbabel.integrations.books.modonomicon.ModonomiconBookPreloader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Warms only the Modonomicon book that is being opened.
 */
@Pseudo
@Mixin(targets = "com.klikli_dev.modonomicon.client.gui.book.BookContentScreen", remap = false)
public abstract class ModonomiconBookContentScreenMixin extends Screen {

    protected ModonomiconBookContentScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = {"m_7856_()V", "init()V", "method_25426()V"}, at = @At("HEAD"), remap = false, require = 0)
    private void projectbabel$onBookOpen(CallbackInfo ci) {
        ModonomiconBookPreloader.preloadBookForScreen(this);
    }
}
