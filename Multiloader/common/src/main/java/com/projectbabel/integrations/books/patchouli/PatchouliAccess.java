package com.projectbabel.integrations.books.patchouli;

import com.projectbabel.integrations.access.ReflectionAccess;
import com.projectbabel.integrations.books.BookIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Semantic reflection boundary for Patchouli internals. */
public final class PatchouliAccess {
    private static final String[] REGISTRY_CLASSES = {
        "vazkii.patchouli.client.book.ClientBookRegistry",
        "vazkii.patchouli.common.book.BookRegistry",
        "vazkii.patchouli.common.book.BookRegistryImpl"
    };
    private static final String[] INSTANCE_FIELDS = {"INSTANCE", "instance"};
    private static final String[] RELOAD_METHODS = {"reload", "reloadBooks", "reloadContents", "loadBooks"};
    private static final String[] SCREEN_REFRESH_METHODS = {
        "rebuildWidgets", "refresh", "reload", "rebuild", "rebuildPage",
        "reloadPage", "loadPage", "updatePage", "init"
    };

    private PatchouliAccess() {}

    public static Object bookFromScreen(Object screen) {
        return ReflectionAccess.readField(screen, "book");
    }

    public static BookIdentity resolveBookIdentity(Object book) {
        Object id = ReflectionAccess.readField(book, "id");
        if (id instanceof ResourceLocation location) {
            return new BookIdentity(location.getNamespace(), location.getPath(), location.toString());
        }
        if (id instanceof String text) {
            ResourceLocation location = ResourceLocation.tryParse(text);
            if (location != null) return new BookIdentity(location.getNamespace(), location.getPath(), location.toString());
        }
        return null;
    }

    public static boolean reloadRegistries() {
        boolean invoked = false;
        for (String className : REGISTRY_CLASSES) {
            Class<?> type = ReflectionAccess.classForName(className);
            if (type == null) continue;
            invoked |= invokeReload(type, null);
            Object instance = findInstance(type);
            if (instance != null) invoked |= invokeReload(type, instance);
        }
        return invoked;
    }

    public static boolean refreshCurrentScreen(Minecraft mc) {
        Screen screen = mc == null ? null : mc.screen;
        if (screen == null) return false;
        if (!screen.getClass().getName().toLowerCase().contains("patchouli")) return false;
        boolean refreshed = ReflectionAccess.invokeScreenInit(screen, mc);
        refreshed |= ReflectionAccess.invokeAnyNoArg(screen, SCREEN_REFRESH_METHODS);
        return refreshed;
    }

    private static Object findInstance(Class<?> type) {
        for (String fieldName : INSTANCE_FIELDS) {
            Object value = ReflectionAccess.readStaticField(type, fieldName);
            if (value != null) return value;
        }
        Method method = ReflectionAccess.findMethod(type, "getInstance", 0);
        return ReflectionAccess.isStatic(method) ? ReflectionAccess.invoke(method, null) : null;
    }

    private static boolean invokeReload(Class<?> type, Object target) {
        boolean invoked = false;
        for (String methodName : RELOAD_METHODS) {
            Method method = ReflectionAccess.findMethod(type, methodName, 0);
            if (method == null) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (target == null && !isStatic) continue;
            ReflectionAccess.invoke(method, isStatic ? null : target);
            invoked = true;
        }
        return invoked;
    }
}
