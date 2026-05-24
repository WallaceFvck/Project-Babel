package com.projectbabel.integrations.books.modonomicon;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.integrations.access.ReflectionAccess;
import com.projectbabel.integrations.books.BookIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/** Semantic reflection boundary for Modonomicon internals. */
public final class ModonomiconAccess {
    private static final String RENDERER_CLASS =
        "com.klikli_dev.modonomicon.client.gui.book.markdown.BookTextRenderer";

    private ModonomiconAccess() {}

    public static Object bookFromScreen(Object screen) {
        Object book = ReflectionAccess.invokeNoArg(screen, "getBook");
        if (book != null) return book;
        Object entry = ReflectionAccess.readField(screen, "entry");
        return ReflectionAccess.invokeNoArg(entry, "getBook");
    }

    public static Object bookFromRenderer(Object renderer) {
        return ReflectionAccess.readField(renderer, "book");
    }

    public static BookIdentity resolveBookIdentity(Object book) {
        Object id = ReflectionAccess.invokeNoArg(book, "getId");
        if (id == null) id = ReflectionAccess.readField(book, "id");
        if (id instanceof ResourceLocation location) {
            return new BookIdentity(location.getNamespace(), location.getPath(), location.toString());
        }
        if (id instanceof String text) {
            ResourceLocation location = ResourceLocation.tryParse(text);
            if (location != null) return new BookIdentity(location.getNamespace(), location.getPath(), location.toString());
        }
        return null;
    }

    public static boolean prerenderBook(Object book) {
        Class<?> rendererClass = ReflectionAccess.classForName(RENDERER_CLASS);
        Object renderer = ReflectionAccess.newInstanceMatchingSingleArg(rendererClass, book);
        if (renderer == null) return false;

        Method prerender = ReflectionAccess.findDeclaredMethod(book.getClass(), "prerenderMarkdown", rendererClass);
        Object result = ReflectionAccess.invoke(prerender, book, renderer);
        return prerender != null || result != null;
    }

    public static boolean refreshCurrentScreen(Minecraft mc) {
        Screen screen = mc == null ? null : mc.screen;
        if (screen == null) return false;
        if (!screen.getClass().getName().toLowerCase().contains("modonomicon")) return false;
        if (ReflectionAccess.invokeScreenInit(screen, mc)) return true;
        return ReflectionAccess.invokeAnyNoArg(screen, "beginDisplayPages", "refresh", "init");
    }
}
