package com.projectbabel.mixin.compat.ftblibrary;

import com.projectbabel.integrations.ftblibrary.FTBLibraryComponentTranslator;
import com.projectbabel.integrations.ftblibrary.FTBLibraryAccess;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Translates FTBLibrary button titles before later draws/tooltips see them. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.SimpleTextButton", remap = false)
public abstract class FTBLibrarySimpleTextButtonMixin {

    @Inject(
        method = {
            "<init>(Ldev/ftb/mods/ftblibrary/ui/Panel;Lnet/minecraft/network/chat/Component;Ldev/ftb/mods/ftblibrary/icon/Icon;)V",
            "<init>(Ldev/ftb/mods/ftblibrary/ui/Panel;Lnet/minecraft/class_2561;Ldev/ftb/mods/ftblibrary/icon/Icon;)V"
        },
        at = @At("TAIL"),
        require = 0,
        remap = false
    )
    private void projectbabel$translateCtorTitle(CallbackInfo ci) {
        translateCurrentTitleAndResize();
    }

    @ModifyVariable(
        method = {
            "setTitle(Lnet/minecraft/network/chat/Component;)Ldev/ftb/mods/ftblibrary/ui/SimpleTextButton;",
            "setTitle(Lnet/minecraft/class_2561;)Ldev/ftb/mods/ftblibrary/ui/SimpleTextButton;",
            "setTitle(Lnet/minecraft/network/chat/Component;)Ldev/ftb/mods/ftblibrary/ui/Button;",
            "setTitle(Lnet/minecraft/class_2561;)Ldev/ftb/mods/ftblibrary/ui/Button;"
        },
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0,
        remap = false
    )
    private Component projectbabel$translateSetTitle(Component title) {
        return FTBLibraryComponentTranslator.translateComponentBeforeLayout(title);
    }

    private void translateCurrentTitleAndResize() {
        Object self = this;
        try {
            Component component = FTBLibraryAccess.getTitle(self);
            if (component == null) return;

            Component translated = FTBLibraryComponentTranslator.translateComponentBeforeLayout(component);
            if (translated == component) return;

            FTBLibraryAccess.setTitle(self, translated);
            FTBLibraryAccess.resizeToTitle(self, translated);
        } catch (Throwable ignored) {
        }
    }
}
