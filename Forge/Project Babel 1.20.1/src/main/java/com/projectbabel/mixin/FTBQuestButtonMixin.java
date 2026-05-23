package com.projectbabel.mixin;

import com.projectbabel.translation.FTBQuestFirstOpenTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.QuestButton", remap = false)
public abstract class FTBQuestButtonMixin {

    private static final ConcurrentHashMap<Class<?>, Method> GET_LINES_METHODS = new ConcurrentHashMap<>();

    @Inject(method = "addMouseOverText", at = @At("TAIL"), remap = false, require = 0)
    private void projectbabel$translateMouseOverText(@Coerce Object tooltip, CallbackInfo ci) {
        if (tooltip == null) return;

        List<Component> lines = getLines(tooltip);
        if (lines == null || lines.isEmpty()) return;

        for (int i = 0; i < lines.size(); i++) {
            Component original = lines.get(i);
            Component translated = FTBQuestFirstOpenTracker.translate(null, original);
            if (translated != original) {
                lines.set(i, translated);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Component> getLines(Object tooltip) {
        try {
            Method method = GET_LINES_METHODS.computeIfAbsent(
                tooltip.getClass(),
                FTBQuestButtonMixin::findGetLinesMethod
            );
            if (method == null) return null;
            Object value = method.invoke(tooltip);
            return value instanceof List<?> list ? (List<Component>) list : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findGetLinesMethod(Class<?> type) {
        try {
            Method method;
            try {
                method = type.getMethod("getLines");
            } catch (NoSuchMethodException e) {
                method = type.getDeclaredMethod("getLines");
            }
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return null;
        }
    }
}
