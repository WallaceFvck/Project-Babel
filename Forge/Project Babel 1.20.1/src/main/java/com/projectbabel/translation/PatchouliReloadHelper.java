package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PatchouliReloadHelper {

    private static final String[] REGISTRY_CLASSES = {
        "vazkii.patchouli.client.book.ClientBookRegistry",
        "vazkii.patchouli.common.book.BookRegistry",
        "vazkii.patchouli.common.book.BookRegistryImpl"
    };

    private static final String[] INSTANCE_FIELDS = {
        "INSTANCE",
        "instance"
    };

    private static final String[] RELOAD_METHODS = {
        "reload",
        "reloadBooks",
        "reloadContents",
        "loadBooks"
    };

    private static final String[] SCREEN_REFRESH_METHODS = {
        "rebuildWidgets",
        "refresh",
        "reload",
        "rebuild",
        "rebuildPage",
        "reloadPage",
        "loadPage",
        "updatePage",
        "init"
    };

    private PatchouliReloadHelper() {}

    public static void requestReload(String reason) {
        if (!ModList.get().isLoaded("patchouli")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            boolean registryReloaded = reloadRegistries();
            boolean screenRefreshed = refreshCurrentScreen(mc);
            if (registryReloaded || screenRefreshed) {
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Patchouli reload solicitado{}{}{}.",
                    reason == null || reason.isBlank() ? "" : " (",
                    reason == null || reason.isBlank() ? "" : reason,
                    reason == null || reason.isBlank() ? "" : ")"
                );
            } else {
                ProjectBabelMod.LOGGER.debug(
                    "[projectbabel] Patchouli reload: nenhum alvo conhecido encontrado.");
            }
        });
    }

    private static boolean reloadRegistries() {
        boolean invoked = false;
        for (String className : REGISTRY_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                invoked |= invokeReload(type, null);

                Object instance = findInstance(type);
                if (instance != null) {
                    invoked |= invokeReload(type, instance);
                }
            } catch (Throwable ignored) {
            }
        }
        return invoked;
    }

    private static Object findInstance(Class<?> type) {
        for (String fieldName : INSTANCE_FIELDS) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }

        try {
            Method method = type.getDeclaredMethod("getInstance");
            if (!Modifier.isStatic(method.getModifiers())) return null;
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeReload(Class<?> type, Object target) {
        boolean invoked = false;
        for (String methodName : RELOAD_METHODS) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0) continue;
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                if (target == null && !isStatic) continue;
                method.setAccessible(true);
                method.invoke(isStatic ? null : target);
                invoked = true;
            } catch (Throwable ignored) {
            }
        }
        return invoked;
    }

    private static boolean refreshCurrentScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return false;
        if (!screen.getClass().getName().toLowerCase().contains("patchouli")) return false;

        boolean refreshed = false;
        refreshed |= invokeScreenInit(screen, mc);
        for (String methodName : SCREEN_REFRESH_METHODS) {
            refreshed |= invokeNoArg(screen, methodName);
        }
        return refreshed;
    }

    private static boolean invokeScreenInit(Screen screen, Minecraft mc) {
        try {
            Method method = Screen.class.getDeclaredMethod(
                "init", Minecraft.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(
                screen,
                mc,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeNoArg(Object target, String methodName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0) return false;
                method.setAccessible(true);
                method.invoke(target);
                return true;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }
}
