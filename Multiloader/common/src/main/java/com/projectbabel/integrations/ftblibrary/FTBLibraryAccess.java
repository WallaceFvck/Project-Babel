package com.projectbabel.integrations.ftblibrary;

import com.projectbabel.integrations.access.ReflectionAccess;
import net.minecraft.network.chat.Component;

/** Semantic reflection boundary for FTB Library UI widgets. */
public final class FTBLibraryAccess {
    private FTBLibraryAccess() {}

    public static Component getTitle(Object button) {
        Object current = ReflectionAccess.invokeNoArg(button, "getTitle");
        return current instanceof Component component ? component : null;
    }

    public static void setTitle(Object button, Component title) {
        ReflectionAccess.invoke(
            ReflectionAccess.findMethod(button == null ? null : button.getClass(), "setTitle", 1),
            button,
            title
        );
    }

    public static void resizeToTitle(Object button, Component title) {
        if (button == null || title == null) return;
        Object gui = ReflectionAccess.invokeNoArg(button, "getGui");
        Object theme = ReflectionAccess.invokeNoArg(gui, "getTheme");
        Object width = ReflectionAccess.invoke(
            ReflectionAccess.findMethod(theme == null ? null : theme.getClass(), "getStringWidth", 1),
            theme,
            title
        );
        if (!(width instanceof Number number)) return;
        boolean hasIcon = Boolean.TRUE.equals(ReflectionAccess.invokeNoArg(button, "hasIcon"));
        ReflectionAccess.invoke(
            ReflectionAccess.findMethod(button.getClass(), "setWidth", 1),
            button,
            number.intValue() + (hasIcon ? 28 : 8)
        );
    }
}
