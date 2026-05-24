package com.projectbabel.mixin.compat.ftblibrary;

import com.projectbabel.integrations.ftblibrary.FTBLibraryComponentTranslator;
import com.projectbabel.minecraft.render.LayoutSensitiveScreenGuard;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * FTBLibrary widgets cache wrapping/height inside TextField#setText.
 * Translate before this method receives the Component, not in Theme/Font render.
 *
 * IMPORTANT: the target mod is remap=false, so method descriptors must include
 * both named dev descriptors and runtime/intermediary descriptors for Minecraft
 * parameter types.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.TextField", remap = false)
public abstract class FTBLibraryTextFieldMixin {

    @ModifyVariable(
        method = {
            "setText(Lnet/minecraft/network/chat/Component;)Ldev/ftb/mods/ftblibrary/ui/TextField;",
            "setText(Lnet/minecraft/class_2561;)Ldev/ftb/mods/ftblibrary/ui/TextField;"
        },
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0,
        remap = false
    )
    private Component projectbabel$translateComponentBeforeFtbLayout(Component original) {
        if (original == null) return null;
        if (!LayoutSensitiveScreenGuard.isFtbQuestScreenOpen() && !isFtbQuestConstructionStack()) return original;
        return FTBLibraryComponentTranslator.translateComponentBeforeLayout(original);
    }

    @ModifyVariable(
        method = "setText(Ljava/lang/String;)Ldev/ftb/mods/ftblibrary/ui/TextField;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0,
        remap = false
    )
    private String projectbabel$translateStringBeforeFtbLayout(String original) {
        if (original == null) return null;
        if (!LayoutSensitiveScreenGuard.isFtbQuestScreenOpen() && !isFtbQuestConstructionStack()) return original;
        return FTBLibraryComponentTranslator.translateStringBeforeLayout(original);
    }

    private static boolean isFtbQuestConstructionStack() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String name = element.getClassName();
            if (name != null && name.startsWith("dev.ftb.mods.ftbquests.client.gui.quests.")) {
                return true;
            }
        }
        return false;
    }
}
