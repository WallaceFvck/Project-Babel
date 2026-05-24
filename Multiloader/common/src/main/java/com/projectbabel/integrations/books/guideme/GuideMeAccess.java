package com.projectbabel.integrations.books.guideme;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.integrations.access.ReflectionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Semantic reflection boundary for GuideME and AE2's shaded guidebook. */
public final class GuideMeAccess {
    private static final String[] REFRESH_METHODS = {
        "refresh", "reload", "rebuild", "rebuildWidgets", "rebuildDocument",
        "rebuildPage", "reloadPage", "loadPage", "updatePage", "init"
    };

    private GuideMeAccess() {}

    public static boolean collectWithParser(ResourceLocation id, String source, Set<String> fragments) {
        if (collectWithAe2GuideParser(id, source, fragments)) return true;
        return collectWithStandaloneGuideMeParser(id, source, fragments);
    }

    private static boolean collectWithAe2GuideParser(ResourceLocation id, String source, Set<String> fragments) {
        try {
            Class<?> compilerClass = ReflectionAccess.classForName("appeng.client.guidebook.compiler.PageCompiler");
            Method parse = ReflectionAccess.findDeclaredMethod(compilerClass, "parse", String.class, ResourceLocation.class, String.class);
            Object parsed = ReflectionAccess.invoke(parse, null, "projectbabel", id, source);
            Object root = ReflectionAccess.invokeNoArg(parsed, "getAstRoot");
            collectAstText(root, fragments, Collections.newSetFromMap(new IdentityHashMap<>()));
            return root != null;
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.debug(
                "[projectbabel] AE2 GuideME parser indisponivel para {}: {}",
                id,
                e.getMessage()
            );
            return false;
        }
    }

    private static boolean collectWithStandaloneGuideMeParser(ResourceLocation id, String source, Set<String> fragments) {
        try {
            Class<?> compilerClass = ReflectionAccess.classForName("guideme.compiler.PageCompiler");
            Method parse = ReflectionAccess.findDeclaredMethod(
                compilerClass,
                "parse",
                String.class,
                String.class,
                ResourceLocation.class,
                String.class
            );
            Object parsed = ReflectionAccess.invoke(parse, null, "projectbabel", "en_us", id, source);
            Object root = ReflectionAccess.invokeNoArg(parsed, "getAstRoot");
            collectAstText(root, fragments, Collections.newSetFromMap(new IdentityHashMap<>()));
            return root != null;
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.debug(
                "[projectbabel] GuideME parser indisponivel para {}: {}",
                id,
                e.getMessage()
            );
            return false;
        }
    }

    private static void collectAstText(Object node, Set<String> fragments, Set<Object> seen) {
        if (node == null || !seen.add(node)) return;
        String className = node.getClass().getName();
        if ("guideme.libs.mdast.model.MdAstText".equals(className)
            || "appeng.libs.mdast.model.MdAstText".equals(className)) {
            String value = literalValue(node);
            if (value != null && !value.isBlank()) fragments.add(value.strip());
        }
        Iterable<?> children = children(node);
        if (children != null) {
            for (Object child : children) collectAstText(child, fragments, seen);
        }
    }

    private static String literalValue(Object node) {
        Object value = ReflectionAccess.invokeNoArg(node, "value");
        if (value instanceof String text) return text;
        value = ReflectionAccess.readField(node, "value");
        return value instanceof String text ? text : null;
    }

    private static Iterable<?> children(Object node) {
        Object value = ReflectionAccess.invokeNoArg(node, "children");
        return value instanceof Iterable<?> iterable ? iterable : null;
    }

    public static boolean isAvailable() {
        try {
            if (ProjectBabelCommon.platform().mods().isLoaded("guideme") || ProjectBabelCommon.platform().mods().isLoaded("ae2")) return true;
            return ReflectionAccess.classExists("guideme.document.flow.LytFlowText")
                || ReflectionAccess.classExists("guideme.document.block.LytDocument")
                || ReflectionAccess.classExists("guideme.compiler.PageCompiler")
                || ReflectionAccess.classExists("appeng.client.guidebook.document.flow.LytFlowText")
                || ReflectionAccess.classExists("appeng.client.guidebook.document.block.LytDocument")
                || ReflectionAccess.classExists("appeng.client.guidebook.compiler.PageCompiler");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean refreshCurrentScreen(Minecraft mc) {
        Screen screen = mc == null ? null : mc.screen;
        if (screen == null) return false;
        if (!looksLikeGuideMeScreen(screen)) return false;
        boolean refreshed = ReflectionAccess.invokeScreenInit(screen, mc);
        refreshed |= ReflectionAccess.invokeAnyNoArg(screen, REFRESH_METHODS);
        return refreshed;
    }

    private static boolean looksLikeGuideMeScreen(Screen screen) {
        String name = screen.getClass().getName().toLowerCase();
        return name.contains("guideme")
            || name.contains("ae2guide")
            || name.contains("guidebook")
            || name.contains("guide_screen")
            || name.contains("guide");
    }
}
