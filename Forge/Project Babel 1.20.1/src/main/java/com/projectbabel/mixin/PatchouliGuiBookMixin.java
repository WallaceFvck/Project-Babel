package com.projectbabel.mixin;

import com.projectbabel.translation.PatchouliBookPreloader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prepares only the Patchouli book that is being opened.
 */
@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.gui.GuiBook", remap = false)
public abstract class PatchouliGuiBookMixin extends Screen {

    protected PatchouliGuiBookMixin(Component title) {
        super(title);
    }

    @Inject(method = {"m_7856_()V", "init()V"}, at = @At("HEAD"), remap = false, require = 0)
    private void projectbabel$onBookOpen(CallbackInfo ci) {
        PatchouliBookPreloader.preloadBookForScreen(this);
    }
}
